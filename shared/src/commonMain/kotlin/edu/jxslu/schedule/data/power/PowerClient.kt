package edu.jxslu.schedule.data.power

import edu.jxslu.schedule.data.net.SharedHttp
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.Parameters
import io.ktor.http.contentType
import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject

/**
 * 缴费平台 HTTP 客户端（DESIGN §4.24）。只做请求与状态码归一。
 *
 * 红线：**无任何日志拦截器**（凭证/token 不进 logcat）；不做自动重试（失败即停，防撞风控）。
 * 登录端点在**根域** `/blade-auth/oauth/token`，业务接口在 `/charge/`——两者都在本类的
 * [BASE] 之下，路径由调用方给全。
 *
 * iOS 移植 P1（2026-11-24）：搬进 `:shared`，OkHttp → Ktor。四处必须跟着换：
 * - 超时搬进 [SharedHttp.create]，Ktor 没有独立的写超时（详见 [create] 的注释）；
 * - `java.net.URLEncoder` 是 JVM 专有的：表单改由 [FormDataContent] 编码，[pageUrl] 里
 *   那一处改 [encodeURLParameter]；
 * - `Dispatchers.IO` / `withContext` 删掉——Ktor 的请求本身就是挂起的，不占线程；
 * - `catch (java.io.IOException)` 换成先 `catch (CancellationException)` 原样抛出、再
 *   `catch (Exception)`，统归一成 [PowerException.Network]，与迁移前的对外行为一致。
 */
class PowerClient private constructor(private val http: HttpClient) {

    companion object {
        const val BASE = "https://charge.juwp.edu.cn"

        /** 前端 bundle 里的 OAuth2 客户端凭据（所有人可见，非机密）。 */
        private const val BASIC = "Basic Y2hhcmdlOmNoYXJnZV9zZWNyZXQ="

        /**
         * 该学校的登录方式（`GET /charge/logintype` 取值）：学号 + 查询密码。
         */
        private const val LOGIN_TYPE = "student-sno-queryPassword"

        private const val UA =
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/120.0.0.0 Mobile Safari/537.36"

        /**
         * 微信 UA：**只有取支付页链接那一条请求用它**（2026-09-28 实测）。
         *
         * 平台按请求 UA 把 `paystep=2 + 农行渠道` 分成三支，只有这一支给**手机版**收银台：
         *
         * | 请求 UA | 响应 |
         * |---------|------|
         * | 微信（含 `MicroMessenger`） | `200` + `paysubmit`＝`https://mobile.abchina.com/mpaynew/mpay/index?TOKEN=…`（**手机版收银台**：手机号 → 短信验证码 → 支付密码） |
         * | 桌面 / 其它非手机 UA | `200` + `paysubmit`＝`https://pay.abchina.com/EbusPerbankFront/PaymentModeNewAct?TOKEN=…`（网页版收银台：PC 布局、K 宝/安全控件提示、手机上会被裁） |
         * | 手机浏览器（含 `Mobile Safari` 且无 `MicroMessenger`） | `{"code":6230,"success":false,"data":null,"msg":"处理成功"}`——**空响应**，文案还骗人 |
         *
         * 用户历史上一直是在**微信里**付这笔电费（学校入口在微信里），拿到的就是手机版
         * 收银台；所以这里固定发微信 UA，让平台给同一条链接，再由 App 内嵌页打开。
         * 手机版页面**不依赖微信 JSAPI**（实测 `WeixinJSBridge`/`jweixin` 0 处），
         * 普通手机浏览器 UA 也能正常渲染（手机号 + 验证码表单齐全）。
         *
         * `body` 里的 `userAgent` / `isWX` 字段都不影响分支（三个字段各测一遍，差别只在
         * 请求头 UA）。
         *
         * **2026-11-06 起上表作废**：平台把农行那条换成学校自己的聚合收银台
         * （`yktpay.juwp.edu.cn/cashier/cashier`），微信 / 手机 / 桌面三个 UA 当天实测
         * 返回**同一份**响应（`paymentcashierStr`）。这里仍发微信 UA——它是历史验证过的
         * 那一份，且平台随时可能把分支加回来；解析侧已不再依赖它（见
         * [PowerPayModels.bankCashierUrlFrom]）。
         */
        const val MOBILE_CASHIER_UA =
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Version/4.0 Chrome/107.0.0.0 Mobile Safari/537.36 MicroMessenger/8.0.49.2600"

        /** 移动服务平台（ydfwpt）打开本平台的口径：token 走 URL，收银台回跳走 paymentUrl。 */
        private const val APPSOURSE = "ydfwpt"
        private const val PAYMENT_URL = "https://yktwx.juwp.edu.cn/plat"

        fun create(): PowerClient = PowerClient(
            // 共享池派生 + 单独放宽超时（2026-11-06）：
            // 共享 base 的 socket 超时 = 20s，但**缴费平台的收银台会话生成会慢到 20s 以上**
            // ——同一条 `paystep=2` 请求 PC 端 1.5s，手机上连续超时报「网络不给力」；
            // 平台侧的网关上限本身是 60s（慢到顶时回空响应）。20s 超时会把这类
            // 慢响应误报成网络故障，所以这一条链路放宽到读 45s / 总上限 60s。
            //
            // **写超时并入 socket**：原 OkHttp 那边是 read 45s / write 30s / call 60s 三道，
            // Ktor 的 HttpTimeout 只有 request / connect / socket 三个维度，没有独立的写
            // 闸门——写与读同受 socketTimeoutMillis 管，这里按 45s 一并覆盖。30s 那道写上限
            // 就此消失，不是漏填（写请求体最大也就几十字节，45s 用不完）。
            SharedHttp.create(
                requestTimeoutSeconds = 60,
                connectTimeoutSeconds = 15,
                socketTimeoutSeconds = 45,
            ),
        )

        /**
         * 平台页面深链（DESIGN §3.13）：把 App 现登的 token 放进 URL，前端按 `token` 参数
         * 直接登录（与微信小程序同一口径，2026-09-23 实测可用）。
         *
         * [route] 是前端路由（`#` 之后那段）：`/pays?id=181` = 房间电费缴费页，
         * `/bill` = 缴费账单（按月总支出）。无效路由会被前端打回首页，所以只给实测过的值。
         *
         * token 有效期 3599 秒，只在这一跳里出现，不落盘、不进日志。
         */
        fun pageUrl(token: String, route: String): String {
            // 原为 `URLEncoder.encode(PAYMENT_URL, "UTF-8")`（JVM 专有，commonMain 没有）。
            // encodeURLParameter 对 PAYMENT_URL 这个纯 ASCII 常量（无空格、无星号）输出逐字
            // 一致：`https%3A%2F%2Fyktwx.juwp.edu.cn%2Fplat`。两者只在「空格 → `%20` vs `+`」
            // 和「`*` 是否保留」上不同，这个常量都碰不到；换成带空格的值时要重新核对。
            val payment = PAYMENT_URL.encodeURLParameter()
            return "$BASE/?appsourse=$APPSOURSE&paymentUrl=$payment&token=$token#$route"
        }

        /** 房间电费缴费页（电费充值）。 */
        fun payPageUrl(token: String, feeItemId: Int): String = pageUrl(token, "/pays?id=$feeItemId")

        /** 缴费账单页（按月总支出，2026-09-23 实测）。 */
        fun billPageUrl(token: String): String = pageUrl(token, "/bill")
    }

    /** 一次请求的原始结果（HTTP 码 + 响应文本；平台业务码在文本里的 `code` 字段）。 */
    data class Raw(val httpCode: Int, val text: String)

    /** 登录：学号 + 查询密码 → access_token（有效期 3599 秒）。 */
    suspend fun login(username: String, password: String): Raw = send {
        url("$BASE/blade-auth/oauth/token")
        header("User-Agent", UA)
        header("Authorization", BASIC)
        formBody(
            mapOf(
                "username" to username,
                "password" to password,
                "grant_type" to "password",
                "scope" to "all",
                "logintype" to LOGIN_TYPE,
            ),
        )
    }

    /**
     * GET（`/charge/` 的业务查询）。
     *
     * 查询参数交给 Ktor 的 [parameter] 编码：与原先手写 `URLEncoder.encode(值)` 的差别是
     * **键也会被编码**、且空格走 `%20` 而非 `+`。现有调用方传的全是纯数字与 ASCII 短词
     * （`feeitemid` / `flag` / `paystatus` / `orderid` / `userAgent`），两种写法输出逐字相同。
     */
    suspend fun get(path: String, token: String, params: Map<String, String> = emptyMap()): Raw = send {
        method = HttpMethod.Get
        url(BASE + path)
        params.forEach { (k, v) -> parameter(k, v) }
        header("User-Agent", UA)
        header("Authorization", BASIC)
        header("synjones-auth", "bearer $token")
    }

    /** POST 表单（`/charge/`）。 */
    suspend fun postForm(path: String, token: String, fields: Map<String, String>): Raw = send {
        url(BASE + path)
        header("User-Agent", UA)
        header("Authorization", BASIC)
        header("synjones-auth", "bearer $token")
        formBody(fields)
    }

    /**
     * POST JSON body（`/charge/order/deleteOrder` 与 `/charge/sceneBind/add` 用）。
     *
     * 2026-09-24 实测：删单端点**只认 JSON body**——表单（无论带不带 SIGN）一律
     * `500 未知异常`；`Content-Type: application/json` + `{"orderid":…}` 即成功。
     * 2026-10-08 起场景绑定也走这里：官方前端对 `/sceneBind/add` 正是发 JSON
     * （`$api.post` 没带 `"qs"` 参数 → axios 默认应用 `application/json`），
     * 已用同房间重绑实测对齐。别把它改回表单。
     */
    suspend fun postJson(path: String, token: String, body: JsonObject): Raw = send {
        url(BASE + path)
        header("User-Agent", UA)
        header("Authorization", BASIC)
        header("synjones-auth", "bearer $token")
        method = HttpMethod.Post
        contentType(ContentType.Application.Json)
        setBody(body.toString())
    }

    /**
     * POST 签名表单（`/blade-pay/pay` 下单与支付）。鉴权走 **header**
     * （2026-09-23 实测：表单字段形式报「未知异常」），与 [login] 的 Basic 头不同。
     *
     * [userAgent] 默认移动端 UA；**取支付页链接那一跳必须传
     * [MOBILE_CASHIER_UA]**（分支表见该常量的注释，不传会拿到空的 `code=6230`）。
     */
    suspend fun postSigned(
        path: String,
        token: String,
        fields: Map<String, String>,
        userAgent: String = UA,
    ): Raw = send {
        url(BASE + path)
        header("User-Agent", userAgent)
        header("Authorization", BASIC)
        header("synjones-auth", "bearer $token")
        header("synAccessSource", "h5")
        formBody(fields)
    }

    /** 表单体：`x-www-form-urlencoded` 的转义交给 Ktor（原先手写 `URLEncoder.encode`）。 */
    private fun HttpRequestBuilder.formBody(fields: Map<String, String>) {
        method = HttpMethod.Post
        setBody(FormDataContent(Parameters.build { fields.forEach { (k, v) -> append(k, v) } }))
    }

    /**
     * 发一次请求并归一成 [Raw]。
     *
     * 响应体的读取也在 try 里：原先 OkHttp 的 `resp.body?.string()` 抛 IOException 同样
     * 归成 [PowerException.Network]，这个口径要保住。
     */
    private suspend fun send(block: HttpRequestBuilder.() -> Unit): Raw = try {
        val response = http.request { block() }
        Raw(response.status.value, response.bodyAsText().removePrefix("\uFEFF"))
    } catch (e: CancellationException) {
        // 协程取消不是网络故障，原样抛出去，别被下面的 catch 吞掉
        throw e
    } catch (e: Exception) {
        throw PowerException.Network(e)
    }
}
