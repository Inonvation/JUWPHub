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
 * iOS 侧的教务读取会话（iOS 移植 M2；2026-11-25 晚加「凭证直登」）。
 *
 * ## 它替的是谁
 *
 * Android 侧的唯一实现是 `CasSession`（OkHttp + cookie jar + 凭证保险箱）。iOS 这边
 * **用的就是同一个 `CasSession`**（共享层，2026-11-25 搬过去），只是换了三件平台物：
 * 传输层是 [IosCasTransport]（Ktor）、凭证落 Keychain（[IosCredentialVault]）、
 * cookie 库读的是 `WKWebsiteDataStore`（[IosWebCookieBridge]）。
 *
 * 本类只剩「iOS 独有」的那一层：**活着的 `WKWebView` 是最好的一条取数路径**
 * （Cookie / UA / Referer / TLS 全由 WebKit 带，见 [IosWebFetch]），它排在所有路径前面。
 *
 * ## [ensureValid] 的四档（顺序不能反）
 *
 * | 档 | 判据 | 结果 |
 * |---|---|---|
 * | ① | `WKWebView` 还活着（`IosWebFetch.available`） | `Ready`——用户可能刚在网页里登了另一个账号，先认它 |
 * | ② | 内存 / Ktor jar 里已有可用会话（含 10 分钟可信期） | `Ready` |
 * | ③ | Keychain 里存了学号密码 | 自动登录一次；成功 `Ready`，失败带原因 |
 * | ④ | 以上都没有 | `NeedsManualLogin`，文案里说明**为什么**（含 cookie 库现状） |
 *
 * ①②③④ 的后三档整段由 `CasSession` 负责（那三条策略、失败闸门、`Mutex` 就是它存在的理由，
 * 别在这里再写一遍）；本类只负责把 ① 插到最前面，并把 ④ 的 `NoCredential` 翻成
 * 带诊断信息的人工登录提示（原先那句「还没有登录教务」就是这一档）。
 *
 * ## 会话失效怎么认
 *
 * 强智的失效表现有两种：HTTP 302 跳回 CAS（跟随后会拿到登录页），或直接回一段
 * 「用户没有登录」。两个都判（[JwPageRules]）；**别只判状态码**：跳登录页是 200。
 *
 * ## 未验
 *
 * 本文件在 Windows 上编不了（Kotlin/Native 的 Apple 目标要 macOS），靠 CI 的 macOS 构建。
 */
class IosJwReadSession(
    /** 共享层那条 CAS 会话（装配在 `IosServices`：Ktor 传输 + Keychain 凭证 + WebKit cookie 桥）。 */
    private val cas: CasSession,
    /** 只给「网页 cookie 直发」那条兼容路径用；会话本身走 [cas] 的 jar。 */
    private val http: HttpClient = SharedHttp.base,
) : JwReadSession {

    override suspend fun ensureValid(): CasEnsureResult {
        // ① WebView 还活着时**不需要**任何 Cookie 判据：取数会在那个页面里同源做，
        //    登录态由 WebKit 自己带（见 IosWebFetch）。上一版在这里强判 JSESSIONID，
        //    结果真机上「页面明明已登录」却报「还没有登录教务」。
        if (IosWebFetch.available) {
            IosJwSessionFlag.markUsable()
            return CasEnsureResult.Ready()
        }
        // ②③④：jar 里已有会话 → 先探；存了凭证 → 自动登一次；都没有 → 转人工登录
        return when (val result = cas.ensureValid()) {
            is CasEnsureResult.Ready -> {
                IosJwSessionFlag.markUsable()
                result
            }
            CasEnsureResult.NoCredential -> {
                IosJwSessionFlag.markLost()
                // 这一档是「真的一点办法都没有」，所以把判断依据一起说出来：库里一条 cookie 都没有
                // 与「有 cookie 但域名/名字对不上」的修法完全不同（2026-11-25 用户报过只说
                // 「还没登录」无法定位）。
                CasEnsureResult.NeedsManualLogin(
                    "还没有登录教务：${IosWebCookieSource.describeStore()}。" +
                        "在「从教务导入」里登录一次，或到「我的 → 教务账户」保存学号密码（之后自动续登）",
                )
            }
            // 凭证错到停用 / 需要验证码 / 登录失败：原因已经在这几个分支的文案里，原样透出
            else -> result
        }
    }

    override suspend fun fetchHtml(url: String): CasFetchResult {
        // ① 优先：在已登录的 WebView 里同源取（唯一不依赖 Cookie 提取的路径）
        if (IosWebFetch.available && JwUrls.isJwHost(url)) {
            val got = IosWebFetch.fetch(url)
            if (got != null) {
                val (status, body) = got
                return when {
                    status !in 200..299 -> CasFetchResult.Failed("教务返回 HTTP $status")
                    isLoggedOut(body) -> {
                        IosJwSessionFlag.markLost()
                        CasFetchResult.Failed("教务会话已失效，请重新登录一次")
                    }
                    else -> {
                        IosJwSessionFlag.markUsable()
                        CasFetchResult.Ok(body)
                    }
                }
            }
            // 取不到（视图刚销毁 / 超时）就往下走，不直接失败
        }
        // ② 原生会话里已有 cookie：交给共享会话（它验会话，被顶时还能用凭证重登一次）
        if (cas.hasCookies()) return cas.fetchHtml(url)
        // ③ 只在网页里登过、还没回灌：直接带着那份 cookie 发（老路径，没有重登能力）
        val cookie = WebCookies.headerFor(url)
        if (cookie != null) {
            val viaCookie = fetchWithCookie(url, cookie)
            if (viaCookie is CasFetchResult.Ok) return viaCookie
            // 网页会话过期了但存过学号密码：让共享会话用凭证直登一次，别让用户看到
            // 「请重新登录」——那正是这次要修的那条链
            val viaCas = cas.fetchHtml(url)
            return if (viaCas is CasFetchResult.Ok) viaCas else viaCookie
        }
        // ④ 没有 WebView、没有网页 cookie：交给共享会话（有凭证就自动登录，没凭证它会回原因）
        return cas.fetchHtml(url)
    }

    /**
     * 进程内有没有教务会话（只读，一个请求都不发）。
     *
     * 判据放宽到「有 WebView（可同源取）**或** 原生 jar 里有 cookie **或** 教务域有非空
     * Cookie 头」：上一版强判 `JSESSIONID`，真机上因此误判成「没登录」。共享层的报修同步
     * 用它决定「要不要顺带走一次 CAS 探测」——多探一次最多多一个请求，少探一次会让功能永远静默。
     *
     * **是 suspend**：iOS 读 Cookie 走 `WKWebsiteDataStore.getAllCookies`（异步回调），
     * 没有 Android `CookieManager` 那种同步读法。
     */
    override suspend fun hasCookies(): Boolean {
        if (IosWebFetch.available) return true
        if (cas.hasCookies()) return true
        // 判据用共享层那条既有规则（`JSESSIONID` / `TGC` / `session_oa` / `sid` 都认），
        // 不再自己写一遍 `contains("JSESSIONID")`——那份白名单在 `SessionCookieRules` 里有单测钉着。
        return SessionCookieRules.hasSessionCookie(WebCookies.headerFor(JwUrls.SCHEDULE_LIST))
    }

    /**
     * 带会话 POST 表单（学工报修的「个人提交记录」列表用它）。
     *
     * 编排与 [fetchHtml] 同款：原生 jar 有会话就走共享会话，否则用网页 cookie 直发。
     */
    override suspend fun postForm(url: String, form: List<Pair<String, String>>): CasFetchResult {
        if (cas.hasCookies()) return cas.postForm(url, form)
        val cookie = WebCookies.headerFor(url)
            ?: return cas.postForm(url, form)
        val viaCookie = postWithCookie(url, form, cookie)
        if (viaCookie is CasFetchResult.Ok) return viaCookie
        val viaCas = cas.postForm(url, form)
        return if (viaCas is CasFetchResult.Ok) viaCas else viaCookie
    }

    /** 网页 cookie 直发 GET（无重登能力，失败原因照实带回）。 */
    private suspend fun fetchWithCookie(url: String, cookie: String): CasFetchResult = try {
        val response = http.get(url) {
            header(HttpHeaders.Cookie, cookie)
            // 教务对 UA 敏感：桌面 UA 拿到的页面结构才带 `li.courselists-item`
            // （`scripts/README.md` 的 DOM 规则就是按桌面版写的）。
            header(HttpHeaders.UserAgent, DESKTOP_UA)
        }
        val body = response.bodyAsText()
        when {
            !response.status.isSuccess() -> CasFetchResult.Failed("教务返回 HTTP ${response.status.value}")
            isLoggedOut(body) -> {
                IosJwSessionFlag.markLost()
                CasFetchResult.Failed("教务会话已失效，请重新登录一次")
            }
            else -> {
                IosJwSessionFlag.markUsable()
                CasFetchResult.Ok(body)
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        // 网络层异常一律收敛成一句话：调用方只需要「这次没拿到 + 为什么」
        CasFetchResult.Failed("网络异常：${e.message ?: e::class.simpleName}")
    }

    /** 网页 cookie 直发表单 POST（同 [fetchWithCookie] 的口径）。 */
    private suspend fun postWithCookie(
        url: String,
        form: List<Pair<String, String>>,
        cookie: String,
    ): CasFetchResult = try {
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
            !response.status.isSuccess() -> CasFetchResult.Failed("教务返回 HTTP ${response.status.value}")
            isLoggedOut(body) -> CasFetchResult.Failed("会话已失效，请重新登录一次")
            else -> CasFetchResult.Ok(body)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        CasFetchResult.Failed("网络异常：${e.message ?: e::class.simpleName}")
    }

    private fun isLoggedOut(body: String): Boolean {
        val head = body.take(SEARCH_HEAD_CHARS)
        return LOGGED_OUT_MARKERS.any { head.contains(it) }
    }

    private companion object {
        /**
         * 只扫前 8KB：失效页的判据都在 `<head>` 与登录表单附近，
         * 而正常课表页有几十 KB——全串 `contains` 六次是白烧 CPU。
         */
        const val SEARCH_HEAD_CHARS = 8_000

        /** 与 `IosCasTransport` / `JwHttpSession` 同一串桌面 UA。 */
        const val DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

        /** 强智失效页的几种文案 / 落点，与 `:app` 的 `JwHttpSession` 同口径。 */
        val LOGGED_OUT_MARKERS = listOf(
            "用户没有登录",
            "请先登录",
            "id=\"loginForm\"",
            "cas/login",
        )
    }
}
