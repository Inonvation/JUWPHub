package edu.jxslu.schedule.data.ykt

import edu.jxslu.schedule.data.net.SharedHttp
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.Parameters
import kotlinx.coroutines.CancellationException

/**
 * 一卡通平台 HTTP 客户端（DESIGN §4.19）。只做请求与状态码归一，
 * 不做任何业务判断；业务错误码由 [YktRepository] 分类。
 *
 * 红线：**无任何日志拦截器**（密码/token/付款码不进 logcat）；响应文本一律先过
 * [YktModels.stripBom]。UA 与 H5 端一致（前端 axios 的浏览器 UA）。
 *
 * iOS 移植 P1（2026-11-24）：搬进 `:shared`，OkHttp → Ktor。三处必须跟着换：
 * - `followRedirects = false` 保留在建客户端处——充值下单（`thirdOrder`）靠 **302 Location**
 *   下发收银台 URL，必须拿到原始 302 而不是跟随后的落地页；
 * - `java.net.URLEncoder` 是 JVM 专有的，表单改由 Ktor 的 [FormDataContent] 编码（同一个
 *   `x-www-form-urlencoded` 口径，转义规则由 Ktor 负责）；
 * - `catch (java.io.IOException)` 换成 `catch (Exception)`（Ktor 的异常层次不同），
 *   统归一成 [YktException.Network]，与迁移前的对外行为一致。
 */
class YktClient private constructor(private val http: HttpClient) {

    companion object {
        const val BASE = "https://yktwx.juwp.edu.cn"

        /** 前端硬编码的 OAuth2 客户端凭据（仅标识客户端，非机密，所有人可见）。 */
        private const val BASIC =
            "Basic bW9iaWxlX3NlcnZpY2VfcGxhdGZvcm06bW9iaWxlX3NlcnZpY2VfcGxhdGZvcm1fc2VjcmV0"

        private const val UA =
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/120.0.0.0 Mobile Safari/537.36"

        private const val ACCEPT = "application/json, text/plain, */*"

        fun create(): YktClient = YktClient(
            // 原 OkHttp 侧：base 的 connect 15s / read 20s + callTimeout 30s，
            // 并关掉重定向跟随（见类注释）。
            SharedHttp.create(
                requestTimeoutSeconds = 30,
                connectTimeoutSeconds = 15,
                socketTimeoutSeconds = 20,
                followRedirects = false,
            ),
        )
    }

    /** 一次请求的原始结果：HTTP 状态码 + 响应头（常用）+ 响应文本（已剥 BOM）。 */
    data class Raw(
        val httpCode: Int,
        val text: String,
        /** 302 场景需要 Location；其余场景常为 null。 */
        val location: String? = null,
    )

    /**
     * GET。header 需要鉴权时传 [token]（拼 `synjones-auth: bearer <token>`）；
     * [absoluteUrl] 请求站外 URL（微信 H5 支付中间页）；
     * [referer] 覆盖默认 Referer（微信 checkmweb 硬校验 referer 为商户域名）。
     * 网络异常抛 [YktException.Network]；不自动重试。
     */
    suspend fun get(
        path: String,
        token: String? = null,
        absoluteUrl: String? = null,
        referer: String? = null,
    ): Raw = send {
        method = HttpMethod.Get
        url(absoluteUrl ?: (BASE + path))
        commonHeaders(referer ?: "$BASE/plat/login")
        if (token != null) header("synjones-auth", "bearer " + token)
    }

    /**
     * POST 表单（x-www-form-urlencoded）。登录专用：带 Basic 凭据与表单 Content-Type。
     */
    suspend fun postForm(path: String, form: Map<String, String>): Raw = send {
        url(BASE + path)
        commonHeaders("$BASE/plat/login")
        header("Authorization", BASIC)
        postFormBody(form)
    }

    /**
     * POST 表单（无 Basic 凭据）。充值下单/删单（`/charge/order/` 系列）用：
     * 前端是 HTML form 同步提交，不带 OAuth Basic 头，鉴权靠表单里的 `synjones-auth` 字段。
     */
    suspend fun postFormPlain(path: String, form: Map<String, String>, referer: String): Raw = send {
        url(BASE + path)
        commonHeaders(referer)
        postFormBody(form)
    }

    /**
     * POST 表单 + `synjones-auth` **header**（`/blade-pay/pay` 用：收银台 axios 把登录态
     * 放 header，放表单字段服务端不认，报「未获取到用户信息」，2026-09-21 实测）。
     */
    suspend fun postFormAuth(path: String, form: Map<String, String>, referer: String, token: String): Raw = send {
        url(BASE + path)
        commonHeaders(referer)
        header("synjones-auth", "bearer " + token)
        postFormBody(form)
    }

    /**
     * POST 签名表单（`/blade-pay/pay` 下单）。鉴权走 **header**（与 [postFormAuth] 同款），
     * 但不设 Referer（由调用方按场景给）。
     */
    suspend fun postSigned(path: String, token: String, form: Map<String, String>, referer: String): Raw =
        postFormAuth(path, form, referer, token)

    /** 三个端点共用的四个头。`synAccessSource: h5` 与服务端来源判定绑定，别省。 */
    private fun io.ktor.client.request.HttpRequestBuilder.commonHeaders(referer: String) {
        header("User-Agent", UA)
        header("Accept", ACCEPT)
        header("synAccessSource", "h5")
        header("Referer", referer)
    }

    /** 表单体：编码交给 Ktor（原先手写 `URLEncoder.encode`，那是 JVM 专有 API）。 */
    private fun io.ktor.client.request.HttpRequestBuilder.postFormBody(form: Map<String, String>) {
        method = HttpMethod.Post
        setBody(FormDataContent(Parameters.build { form.forEach { (k, v) -> append(k, v) } }))
    }

    /**
     * 发一次请求并归一成 [Raw]。
     *
     * 不判 `isSuccess`：一卡通有大量「HTTP 200 但业务码报错」的应答，也有「HTTP 400 带
     * 可读文案」的登录失败，状态码原样交给 [YktRepository] 分类——这里只把「连不上 / 超时」
     * 这类真正的基础设施失败换算成 [YktException.Network]。
     */
    private suspend fun send(build: io.ktor.client.request.HttpRequestBuilder.() -> Unit): Raw {
        val response: HttpResponse = try {
            http.request { build() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw YktException.Network(e)
        }
        return Raw(
            httpCode = response.status.value,
            text = YktModels.stripBom(response.bodyAsText()),
            location = response.headers["Location"],
        )
    }
}
