package edu.jxslu.schedule.data.session

import edu.jxslu.schedule.data.jw.JwUrls
import edu.jxslu.schedule.data.net.SharedHttp
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.Parameters
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException

/**
 * iOS 侧的教务读取会话（iOS 移植 M2，2026-11-25）。
 *
 * ## 它替的是谁
 *
 * Android 侧的唯一实现是 `:app` 的 `CasSession`：OkHttp + CookieJar + 凭证保险箱 + 自动登录。
 * 那套东西 iOS 上一半用不了（OkHttp 不在、Android 保险箱不在），而且**根本不需要**——
 * iOS 的登录由 [edu.jxslu.schedule.ui.web.WebViewController] 里的 `WKWebView` 完成，
 * 人自己填账号密码。本类只做登录**之后**的两件事：
 *
 * | 方法 | 做什么 | 依据 |
 * |---|---|---|
 * | [ensureValid] | 看 `WebCookies` 里有没有 `JSESSIONID` | 有 = 登录过；没有 = 让人去登录页 |
 * | [fetchHtml] | 用那串 Cookie 发一次 GET | 拿回 HTML 交给共享层的解析器 |
 * | [hasCookies] | 同一个判据，**不发请求** | 报修同步据此决定要不要顺带探一次会话 |
 * | [postForm] | 用那串 Cookie 发一次表单 POST | 学工报修的「个人提交记录」是 POST 接口 |
 *
 * ## 为什么不做「自动登录填表」
 *
 * 统一身份认证有验证码、且要预热 `bzb_njw`。Android 侧的自动登录是靠**凭证保险箱 + 表单提交**
 * 实现的，而 iOS 没有保险箱（`EncryptedSharedPreferences` 是 Android 专有），
 * 硬做等于把学号密码明文落盘——这条线不碰。用户登录一次，Cookie 在 `WKWebsiteDataStore` 里
 * 由系统持有，App 只读不存。
 *
 * ## 会话失效怎么认
 *
 * 强智的失效表现有两种：HTTP 302 跳回 CAS（Ktor 跟随后会拿到登录页），或直接回一段
 * 「用户没有登录」。两个都判，判据是 [LOGGED_OUT_MARKERS]。**别只判状态码**：
 * 跳登录页是 200。
 *
 * 编译状态：**未在本机验证**（Windows 没有 Kotlin/Native），靠 CI 的 macOS 构建。
 * 本文件不含任何 ObjC 调用——只有 Ktor 与共享层类型，符号风险比 `ui/web` 那边低得多。
 */
class IosJwReadSession(
    private val http: HttpClient = SharedHttp.base,
) : JwReadSession {

    override suspend fun ensureValid(): CasEnsureResult {
        val header = WebCookies.headerFor(JwUrls.SCHEDULE_LIST)
        if (header == null || !header.contains(JSESSIONID, ignoreCase = true)) {
            return CasEnsureResult.NeedsManualLogin(
                "还没有登录教务：在「从教务导入」里登录一次，之后的抓取就不用再登",
            )
        }
        return CasEnsureResult.Ready()
    }

    override suspend fun fetchHtml(url: String): CasFetchResult {
        val cookie = WebCookies.headerFor(url)
            ?: return CasFetchResult.Failed("没有可用的教务会话，请先在导入页登录")
        return try {
            val response = http.get(url) {
                header(HttpHeaders.Cookie, cookie)
                // 教务对 UA 敏感：桌面 UA 拿到的页面结构才带 `li.courselists-item`
                // （`scripts/README.md` 的 DOM 规则就是按桌面版写的）。
                header(HttpHeaders.UserAgent, DESKTOP_UA)
            }
            val body = response.bodyAsText()
            when {
                !response.status.isSuccess() ->
                    CasFetchResult.Failed("教务返回 HTTP ${response.status.value}")
                isLoggedOut(body) ->
                    CasFetchResult.Failed("教务会话已失效，请重新登录一次")
                else -> CasFetchResult.Ok(body)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // 网络层异常一律收敛成一句话：调用方只需要「这次没拿到 + 为什么」
            CasFetchResult.Failed("网络异常：${e.message ?: e::class.simpleName}")
        }
    }

    /**
     * 进程内有没有教务会话（只读，一个请求都不发）。
     *
     * 判据与 [ensureValid] 同一条：`WebCookies` 里那个域的 Cookie 头带 `JSESSIONID`。
     * 共享层的报修同步用它决定「要不要顺带走一次 CAS 探测」——iOS 侧没有可自动登录的
     * 凭证，这里回 false 只会让它少一次必然失败的探测。
     *
     * **是 suspend**：iOS 读 Cookie 走 `WKWebsiteDataStore.getAllCookies`（异步回调），
     * 没有 Android `CookieManager` 那种同步读法。接口因此整体声明成 suspend，
     * Android 那份实现体不用改（只是多一个修饰符）。
     */
    override suspend fun hasCookies(): Boolean =
        WebCookies.headerFor(JwUrls.SCHEDULE_LIST)?.contains(JSESSIONID, ignoreCase = true) == true

    /**
     * 带会话 POST 表单（学工报修的「个人提交记录」列表用它）。
     *
     * 与 [fetchHtml] 逐条同口径：Cookie 头照带、UA 用桌面版、失效页与网络异常都收敛成
     * 一句原因。**没有重登重试**——iOS 侧没有凭证可重登，重试只会拿同一个失效 Cookie
     * 再打一次；调用方按「会话已失效，请重新登录一次」提示用户。
     */
    override suspend fun postForm(url: String, form: List<Pair<String, String>>): CasFetchResult {
        val cookie = WebCookies.headerFor(url)
            ?: return CasFetchResult.Failed("没有可用的会话，请先在导入页登录")
        return try {
            // 表单编码交 Ktor（`FormDataContent`，与 `YktClient` / `PowerClient` 同一手法）；
            // 不用 `submitForm` 是因为本仓已有一处 `Parameters.build` 的写法，别再引入第二种。
            val response = http.post(url) {
                header(HttpHeaders.Cookie, cookie)
                header(HttpHeaders.UserAgent, DESKTOP_UA)
                setBody(
                    FormDataContent(
                        Parameters.build { form.forEach { (name, value) -> append(name, value) } },
                    ),
                )
            }
            val body = response.bodyAsText()
            when {
                !response.status.isSuccess() ->
                    CasFetchResult.Failed("教务返回 HTTP ${response.status.value}")
                isLoggedOut(body) ->
                    CasFetchResult.Failed("会话已失效，请重新登录一次")
                else -> CasFetchResult.Ok(body)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            CasFetchResult.Failed("网络异常：${e.message ?: e::class.simpleName}")
        }
    }

    private fun isLoggedOut(body: String): Boolean {
        val head = body.take(SEARCH_HEAD_CHARS)
        return LOGGED_OUT_MARKERS.any { head.contains(it) }
    }

    private companion object {
        const val JSESSIONID = "JSESSIONID"

        /**
         * 只扫前 8KB：失效页的判据都在 `<head>` 与登录表单附近，
         * 而正常课表页有几十 KB——全串 `contains` 六次是白烧 CPU。
         */
        const val SEARCH_HEAD_CHARS = 8_000

        const val DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

        /** 强智失效页的几种文案 / 落点，与 `:app` 的 `CasSession` 同口径。 */
        val LOGGED_OUT_MARKERS = listOf(
            "用户没有登录",
            "请先登录",
            "id=\"loginForm\"",
            "cas/login",
        )
    }
}
