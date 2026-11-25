package edu.jxslu.schedule.data.session

import edu.jxslu.schedule.data.jw.JwHttpException
import edu.jxslu.schedule.data.jw.JwUrls
import edu.jxslu.schedule.data.net.HttpSupport
import edu.jxslu.schedule.data.net.SharedHttp
import edu.jxslu.schedule.platform.Logger
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Cookie
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.Parameters
import io.ktor.http.URLBuilder
import io.ktor.http.encodeURLParameter
import io.ktor.http.takeFrom
import kotlinx.coroutines.CancellationException
import kotlinx.io.IOException

/**
 * [CasTransport] 的 iOS 实现：Ktor（Darwin / NSURLSession）+ 进程内 cookie 罐。
 *
 * ## 为什么要有它
 *
 * iOS 此前只有一条路：从 `WKWebsiteDataStore` 读 Cookie 发请求。用户报的现象
 * （「首启登录导入都成功、点返回课表又要登录」）就是这条路的边界——导入页一关，
 * 后续抓取拿到的是一份**没验过**的 cookie，过期就被判「还没有登录教务」，
 * 而 App 手里没有能重建会话的东西。这一层补的就是「凭证直登」：学号密码存在 Keychain，
 * 会话没了就自己重新登一次。
 *
 * ## 与 `:app` 的 `JwHttpSession` 逐条对齐
 *
 * | 环节 | OkHttp（Android） | 这里 |
 * |---|---|---|
 * | 门户落地 + CAS 登录页 | 手动跟 302 | 同（[follow]） |
 * | 提交凭证 | `FormBody` + 四分类 | `FormDataContent` + 同一个 [CasLoginClassifier] |
 * | `execution` 提取 | 同一条正则 | 同一条正则 |
 * | 预热 `:81` | `JwUrls.JW_WARMUP` | 同 |
 * | 拿 SSO ticket | 不跟随，只读 `Location` | 同（[noRedirect]） |
 * | 会话校验 | `JwPageRules.isUsableHome` | 同（判据在共享层，只有一份） |
 * | UA | 桌面版 | 同（教务按桌面版渲染 `li.courselists-item`） |
 * | 302 | **不自动跟随**，手动跟以便诊断落点 | `followRedirects = false` + [follow] |
 *
 * ## 为什么 cookie 罐由插件接管
 *
 * [HttpSupport.InMemoryCookieStorage] 是 Android `MemoryCookieJar` 的 Ktor 对应物
 * （按 host 分桶、不淘汰过期），`CasSession` 的「有 cookie 先探」与「手登回灌」都靠它。
 * 客户端用 [SharedHttp.create] 造，与仓里其它 iOS 客户端同一条路。
 *
 * ## 未验
 *
 * 本文件在 Windows 上编不了（Kotlin/Native 的 Apple 目标要 macOS），只过了 CI 的 macOS 编译。
 * **真机上还没跑过**，两处要留意：
 * 1. Darwin 引擎的 `NSHTTPCookieStorage` 可能另外存一份 cookie（本层的 jar 是权威来源）。
 *    若真机上出现「重复 Cookie 头 / 刚登录就判失效」，修法是在引擎上关掉它：
 *    `engine { configureSession { HTTPCookieStorage = null } }`；
 * 2. 手动跟随 302 的跳数与 OkHttp 一致（15），但整条 CAS 链是否落到同一个落点只能看真机
 *    日志里第 6 步的字节数（`login step6 home check failed`）。
 */
internal class IosCasTransport(
    private val jar: HttpSupport.InMemoryCookieStorage = HttpSupport.InMemoryCookieStorage(),
) : CasTransport {

    /**
     * 教务链路专用 client：**不自动跟随 302**（要拿原始 `Location` 判 CAS 结果），
     * 超时对齐 OkHttp 那份（建连 20s / 读 30s）。
     */
    private val http: HttpClient = SharedHttp.create(
        connectTimeoutSeconds = 20,
        socketTimeoutSeconds = 30,
        followRedirects = false,
        cookieStorage = jar,
    )

    override suspend fun login(username: String, password: String) {
        try {
            // [1][2] 门户落地（写 TGC）→ CAS 登录页拿 execution
            follow("${JwUrls.PORTAL}/cas/login_portal")
            val loginUrl = "${JwUrls.CAS_BASE}/cas/login?service=" +
                "${JwUrls.PORTAL}/cas/login_portal".encodeURLParameter()
            val loginPage = follow(loginUrl)
            val execution = Regex("name=\"execution\" value=\"([^\"]+)\"")
                .find(loginPage)?.groupValues?.get(1)
                ?: throw JwHttpException.Protocol("CAS 登录页缺少 execution 字段（页面结构可能已变）")

            // [3] 提交凭证。结果分四类（[CasLoginClassifier]）——旧实现把「没有 Location」
            // 一律当凭证错，验证码页会把对的密码记成错的，累计到阈值还把账号停用。
            val response = http.request(loginUrl) {
                method = HttpMethod.Post
                header(HttpHeaders.UserAgent, UA)
                setBody(FormDataContent(Parameters.build {
                    append("username", username)
                    append("password", password)
                    append("execution", execution)
                    append("_eventId", "submit")
                    append("geolocation", "")
                    append("submit", "LOGIN")
                }))
            }
            val body = runCatching { response.bodyAsText() }.getOrNull()
            val outcome = CasLoginClassifier.classify(
                response.status.value,
                response.headers[HttpHeaders.Location],
                body,
            )
            val casLocation = when (outcome) {
                is CasLoginResult.Success -> outcome.location
                is CasLoginResult.CredentialWrong -> throw JwHttpException.Credential(outcome.message)
                is CasLoginResult.NeedCaptcha -> throw JwHttpException.Manual(outcome.message)
                is CasLoginResult.Unknown -> throw JwHttpException.Manual(outcome.message)
                is CasLoginResult.ServerError -> throw JwHttpException.Protocol(outcome.message)
            }
            follow(casLocation)

            // [4] 预热教务域，写 bzb_njw；[5] 拿 sso ticket
            follow(JwUrls.JW_WARMUP)
            val ssoLocation = noRedirect(
                "${JwUrls.CAS_BASE}/cas/login?service=" + JwUrls.JW_SSO_SERVICE.encodeURLParameter(),
            ) ?: throw JwHttpException.Protocol("取不到 SSO ticket（CAS 应答无重定向）")

            // [6] 教务侧跟随 302 链落到 xsMainV，再校验会话真的有效
            follow(ssoLocation)
            val home = follow(JwUrls.STUDENT_HOME)
            if (!JwPageRules.isUsableHome(home)) {
                // 与 `JwHttpSession` 同口径：只记字节数与页面形态，**不记 cookie 与正文**
                Logger.w(
                    TAG,
                    "login step6 home check failed: bytes=${home.length} " +
                        "looksLogin=${JwPageRules.looksLikeLoginPage(home)} " +
                        "notLogged=${JwPageRules.NOT_LOGGED in home}",
                )
                throw JwHttpException.Protocol(
                    "教务登录没走通（CAS 已通过，教务侧会话未建立）。请在导入页手动登录一次，或稍后重试。",
                )
            }
        } catch (e: JwHttpException) {
            throw e
        } catch (e: CancellationException) {
            // 取消（调用方的总超时 / 页面销毁）必须原样抛：换成 Protocol 会让上层判不出超时
            throw e
        } catch (e: HttpRequestTimeoutException) {
            throw JwHttpException.Network(e)
        } catch (e: IOException) {
            throw JwHttpException.Network(e)
        } catch (e: Exception) {
            throw JwHttpException.Protocol("登录链路异常：${e.message}")
        }
    }

    /** 会话探针：GET 学生主页，判据与 Android 完全相同（[JwPageRules.isUsableHome]）。 */
    override suspend fun verifySession(): Boolean = try {
        JwPageRules.isUsableHome(follow(JwUrls.STUDENT_HOME))
    } catch (e: JwHttpException) {
        throw e
    } catch (e: CancellationException) {
        throw e
    } catch (e: HttpRequestTimeoutException) {
        throw JwHttpException.Network(e)
    } catch (e: IOException) {
        throw JwHttpException.Network(e)
    }

    override suspend fun fetchHtml(url: String): String {
        val html = try {
            follow(url)
        } catch (e: JwHttpException) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpRequestTimeoutException) {
            throw JwHttpException.Network(e)
        } catch (e: IOException) {
            throw JwHttpException.Network(e)
        }
        if (JwPageRules.looksLikeLoginPage(html)) {
            throw JwHttpException.SessionInvalid("教务会话已失效（页面退回登录页）")
        }
        if (JwPageRules.looksLikeSessionConflict(html)) {
            throw JwHttpException.SessionInvalid("教务会话已被顶掉（账号在其它地方登录）")
        }
        return html
    }

    /**
     * 带会话 POST 表单；302 之后**降级为 GET**（与 `JwHttpSession.postForm` 同口径）：
     * 重发 POST 会丢原表单语义，教务在会话过期时可能把任意请求 302 到登录链。
     */
    override suspend fun postForm(url: String, form: List<Pair<String, String>>): String {
        var current = url
        var fields: List<Pair<String, String>>? = form
        var hop = 0
        try {
            while (true) {
                val pending = fields
                val response = http.request(current) {
                    method = if (pending != null) HttpMethod.Post else HttpMethod.Get
                    header(HttpHeaders.UserAgent, UA)
                    if (pending != null) {
                        setBody(FormDataContent(Parameters.build {
                            pending.forEach { (name, value) -> append(name, value) }
                        }))
                    }
                }
                val value = response.status.value
                val location = response.headers[HttpHeaders.Location]
                if (value in 300..399 && location != null) {
                    if (hop++ >= MAX_REDIRECTS) {
                        throw JwHttpException.Protocol("重定向次数过多，链路可能已变")
                    }
                    // 丢弃重定向响应体，让引擎把连接还回池子后再发下一跳
                    runCatching { response.bodyAsText() }
                    current = resolve(current, location)
                    fields = null
                    continue
                }
                if (value >= 500) throw JwHttpException.Protocol("$current 返回 HTTP $value")
                val text = response.bodyAsText()
                if (JwPageRules.looksLikeLoginPage(text)) {
                    throw JwHttpException.SessionInvalid("教务会话已失效（页面退回登录页）")
                }
                if (JwPageRules.looksLikeSessionConflict(text)) {
                    throw JwHttpException.SessionInvalid("教务会话已被顶掉（账号在其它地方登录）")
                }
                return text
            }
        } catch (e: JwHttpException) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpRequestTimeoutException) {
            throw JwHttpException.Network(e)
        } catch (e: IOException) {
            throw JwHttpException.Network(e)
        }
    }

    override fun hasCookies(): Boolean = jar.size() > 0

    override fun cookies(): List<SessionCookie> = jar.snapshot().map {
        SessionCookie(
            name = it.name,
            value = it.value,
            host = it.domain.orEmpty(),
            path = it.path ?: "/",
            secure = it.secure,
        )
    }

    /**
     * 回灌：把 WebView 用出来的 cookie 塞进 jar。
     *
     * 同步入口是因为 [CasTransport.adoptCookies] 是同步的（Android 的 jar 也是同步写）；
     * 走 [HttpSupport.InMemoryCookieStorage.adopt]，与 OkHttp 那边「往 jar 里塞一批」同义。
     */
    override fun adoptCookies(cookies: List<SessionCookie>) = jar.adopt(
        cookies.map {
            Cookie(
                name = it.name,
                value = it.value,
                domain = it.host,
                path = it.path,
                secure = it.secure,
            )
        },
    )

    override fun clearCookies() = jar.close()

    /**
     * 释放本次会话的连接池。
     *
     * 与 Android 的 `connectionPool.evictAll()` 同一目的（**不要**去关全进程共享的池）：
     * 这里的 client 是本实例专属的（`SharedHttp.create` 每次一个实例），所以直接 close。
     * 只有进程退出时才调（`CasSession.shutdown`），别在任务结束时调。
     */
    override fun shutdown() {
        runCatching { http.close() }
    }

    // ---------------------------------------------------------------- 内部

    /**
     * GET 并手动跟随 302（`followRedirects = false` 时由我们自己处理）。
     *
     * 与 `JwHttpSession.getFollowRedirects` 逐条一致：Location 以**当前 URL** 为基准解析；
     * 3xx 无 Location 时退回正文；5xx 抛 `Protocol`；超过 [MAX_REDIRECTS] 抛 `Protocol`。
     */
    private suspend fun follow(url: String): String {
        var current = url
        var hop = 0
        while (true) {
            val response = http.request(current) {
                method = HttpMethod.Get
                header(HttpHeaders.UserAgent, UA)
            }
            val value = response.status.value
            val location = response.headers[HttpHeaders.Location]
            if (value in 300..399 && location != null) {
                if (hop++ >= MAX_REDIRECTS) {
                    throw JwHttpException.Protocol("重定向次数过多，链路可能已变")
                }
                runCatching { response.bodyAsText() }
                current = resolve(current, location)
                continue
            }
            if (value >= 500) throw JwHttpException.Protocol("$current 返回 HTTP $value")
            return response.bodyAsText()
        }
    }

    /** 只发一跳、只要 `Location`（CAS 拿 ticket 那一步）。 */
    private suspend fun noRedirect(url: String): String? {
        val response = http.request(url) {
            method = HttpMethod.Get
            header(HttpHeaders.UserAgent, UA)
        }
        return if (response.status.value in 300..399) {
            response.headers[HttpHeaders.Location]
        } else {
            null
        }
    }

    /**
     * 相对 Location 按当前 URL 解析（与 Ktor 内部跟重定向同一套解析）。
     *
     * 绝对地址直接透传，其余交给 `URLBuilder.takeFrom`——`TranscriptClient` 已经这么写，
     * 别在这里另发明一种。
     */
    private fun resolve(current: String, location: String): String =
        if (location.startsWith("http://") || location.startsWith("https://")) {
            location
        } else {
            URLBuilder(current).takeFrom(location).buildString()
        }

    private companion object {
        private const val TAG = "IosCasTransport"

        /** 与 `JwHttpSession` 同一串（教务按桌面版渲染页面结构）。 */
        private const val UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

        private const val MAX_REDIRECTS = 15
    }
}
