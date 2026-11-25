package edu.jxslu.schedule.data.jw

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import edu.jxslu.schedule.data.net.AppHttp
import edu.jxslu.schedule.data.session.CasLoginClassifier
import edu.jxslu.schedule.data.session.CasLoginResult
import edu.jxslu.schedule.data.session.CasTransport
import edu.jxslu.schedule.data.session.JwPageRules
import edu.jxslu.schedule.data.session.MemoryCookieJar
import edu.jxslu.schedule.data.session.SessionCookie
import okhttp3.Cookie
import okhttp3.Dns
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.Inet4Address
import java.net.InetAddress
import java.util.concurrent.TimeUnit

/**
 * 教务 HTTP 会话（DESIGN §4.17）：OkHttp 复刻 `scripts/jw_session.py` 的登录链路。
 *
 * 链路（2026-09 实测，脚本侧同源）：
 *   [1] 门户落地拿 CAS TGC：GET portal/cas/login_portal → 302 到 CAS 登录页
 *   [2] GET CAS 登录页拿 execution 字段
 *   [3] POST 用户名/密码/execution（302 有 Location = 登录成功）
 *   [4] 预热 https://jiaowu:81/（写 bzb_njw，缺了教务不认）
 *   [5] GET CAS ?service=jiaowu/sso.jsp（不跟随）→ 拿 sso.jsp?ticket=
 *   [6] 手动跟随 302 链落 xsMainV → 会话校验
 *
 * 每次检测都全量重登，不依赖上一次的会话保持——教务会话超时不构成障碍。
 * CAS（eapp2）与教务（jiaowu）的 cookie 在同一 CookieJar 按 host 分桶，互不污染。
 * 失败分类见 [JwHttpException]：凭证错单独计（连续失败会停用自动检测，防触发验证码）。
 */
class JwHttpSession private constructor(
    private val client: OkHttpClient,
    private val jar: MemoryCookieJar,
) : CasTransport {

    companion object {
        private const val TAG = "JwHttpSession"

        private const val CAS = JwUrls.CAS_BASE
        private const val PORTAL = JwUrls.PORTAL
        private const val JW_SSO_SERVICE = JwUrls.JW_SSO_SERVICE
        private const val JW_WARMUP = JwUrls.JW_WARMUP
        private const val STUDENT_HOME = JwUrls.STUDENT_HOME

        /**
         * 页面是不是登录页：既含 860 字节的提示页，也含就地渲染的登录页。
         *
         * 判据本体在 [JwPageRules]（iOS 的 `IosCasTransport` 用同一份）；这里保留 `internal`
         * 包装是因为 `JwHttpSessionTest` 直接钉这两条判据——它们错了的代价是「把一张登录页
         * 当会话有效」，比漏判更难发现。
         */
        internal fun looksLikeLoginPage(html: String): Boolean = JwPageRules.looksLikeLoginPage(html)

        /**
         * 强智的「账号在别处登录」应答（2026-10-06 用户实测）。
         *
         * 教务是**单会话**：同一学号在另一处登录后，先前那份会话对业务请求不再回数据，
         * 而是回一段 HTTP 200 的 JSON `{"flag1":3,"msgContent":"您的账号在其它地方登录"}`
         * （URL 不变、也不是登录页形态）。不认它的话 [fetchHtml] 会把这段 JSON 当页面数据
         * 交给解析器，最后表现成「导入成功，但某些数据静默缺失」——教材抓取就是这么丢的。
         *
         * 判据本体在 [JwPageRules.looksLikeSessionConflict]（两端共用）。
         */
        internal fun looksLikeSessionConflict(body: String): Boolean =
            JwPageRules.looksLikeSessionConflict(body)

        private const val MAX_REDIRECTS = 15

        private const val UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

        fun create(): JwHttpSession {
            // jar 单独持一份引用：会话要跨任务常驻（DESIGN §4.27），注入 WebView 与回灌
            // 都要从它取快照，不能再让 client 独占。
            val jar = MemoryCookieJar()
            return JwHttpSession(
                // 共享 base（AppHttp）派生：连接池/线程池/DNS 全进程一份；这里只覆盖
                // 会话件（cookieJar）与教务链路的 302 手动跟随、加长的读超时
                AppHttp.base.newBuilder()
                    .cookieJar(jar)
                    // 教务链路大量 302，统一手动跟随以便诊断落点
                    .followRedirects(false)
                    .followSslRedirects(false)
                    .connectTimeout(20, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .build(),
                jar,
            )
        }

        /**
         * IPv4 优先的 DNS：系统解析结果里只要有 A 记录，就把 v4 排到最前，
         * 让 OkHttp 先连 v4（连不上也好过连 v6 黑洞干等 TCP 超时）。
         *
         * 不丢弃 AAAA——若某域名只有 v6，仍能正常工作；纯逻辑、可 JVM 单测。
         */
        object Ipv4FirstDns : Dns {
            override fun lookup(hostname: String): List<InetAddress> =
                ipv4First(Dns.SYSTEM.lookup(hostname))
        }

        /** v4 排前、v6 保留原序殿后；无 v4 时原样返回（见 [Ipv4FirstDns]）。 */
        internal fun ipv4First(all: List<InetAddress>): List<InetAddress> {
            val v4 = all.filterIsInstance<Inet4Address>()
            if (v4.isEmpty()) return all
            return v4 + all.filterNot { it is Inet4Address }
        }
    }

    /**
     * 当前会话里的 cookie 快照，中性形态（`CasTransport` 在共享层，见 [SessionCookie]）。
     *
     * 两个方向都在这里转一次：OkHttp 的 `Cookie` ↔ [SessionCookie]。`MemoryCookieJar`
     * 本体一行没改——它对 host 分桶、按 path 回放的行为就是会话要保持的行为。
     */
    override fun cookies(): List<SessionCookie> = jar.snapshot().map { it.toSessionCookie() }

    /** 回灌：用户在 WebView 里手登之后，把 `CookieManager` 的 cookie 抄回会话。 */
    override fun adoptCookies(cookies: List<SessionCookie>) = jar.adopt(cookies.map { it.toOkHttpCookie() })

    /** 会话里有没有任何 cookie。判断「有没有可复用的登录态」用。 */
    override fun hasCookies(): Boolean = !jar.isEmpty()

    /** 清空会话（退出登录 / 用户更新密码后重登前调用）。 */
    override fun clearCookies() = jar.clear()

    /** 登录 + 会话校验。任一环节失败抛 [JwHttpException]，session 状态不可再用于取数。 */
    override suspend fun login(username: String, password: String) = withContext(Dispatchers.IO) {
        try {
            // [1][2] 门户落地（写 TGC）→ CAS 登录页拿 execution
            getFollowRedirects("$PORTAL/cas/login_portal")
            val loginUrl = "$CAS/cas/login?service=${urlEncode("$PORTAL/cas/login_portal")}"
            val loginPage = getFollowRedirects(loginUrl)
            val execution = Regex("name=\"execution\" value=\"([^\"]+)\"").find(loginPage)?.groupValues?.get(1)
                ?: throw JwHttpException.Protocol("CAS 登录页缺少 execution 字段（页面结构可能已变）")

            // [3] 提交凭证。结果**必须分四类**（DESIGN §4.27）：旧实现把「没有 Location」
            // 一律当凭证错，于是验证码页会把对的密码记成错的，累计到阈值还把账号停用。
            val postForm = FormBody.Builder()
                .add("username", username)
                .add("password", password)
                .add("execution", execution)
                .add("_eventId", "submit")
                .add("geolocation", "")
                .add("submit", "LOGIN")
                .build()
            val casLocation = client.newCall(
                Request.Builder()
                    .url(loginUrl)
                    .header("User-Agent", UA)
                    .post(postForm)
                    .build(),
            ).execute().use { r ->
                val body = runCatching { r.body?.string() }.getOrNull()
                when (val outcome = CasLoginClassifier.classify(r.code, r.header("Location"), body)) {
                    is CasLoginResult.Success -> outcome.location
                    is CasLoginResult.CredentialWrong -> throw JwHttpException.Credential(outcome.message)
                    is CasLoginResult.NeedCaptcha -> throw JwHttpException.Manual(outcome.message)
                    is CasLoginResult.Unknown -> throw JwHttpException.Manual(outcome.message)
                    is CasLoginResult.ServerError -> throw JwHttpException.Protocol(outcome.message)
                }
            }
            getFollowRedirects(casLocation)

            // [4] 预热教务域，写 bzb_njw；[5] 拿 sso ticket
            getFollowRedirects(JW_WARMUP)
            val ssoLocation = getNoRedirect("$CAS/cas/login?service=${urlEncode(JW_SSO_SERVICE)}")
                ?: throw JwHttpException.Protocol("取不到 SSO ticket（CAS 应答无重定向）")

            // [6] 教务侧跟随 302 链落到 xsMainV，再校验会话真的有效
            getFollowRedirects(ssoLocation)
            val home = getFollowRedirects(STUDENT_HOME)
            if (!JwPageRules.isUsableHome(home)) {
                // 每一环都可能独立坏掉（ticket 校验、预热 cookie、主页渲染），
                // 不落日志的话「教务会话无效」永远查不出是哪一环（2026-09-28 用户复现）。
                // 只记字节数与是否登录页形态，**不记任何 cookie 或页面内容**。
                Log.w(
                    TAG,
                    "login step6 home check failed: bytes=${home.length} " +
                        "looksLogin=${looksLikeLoginPage(home)} " +
                        "notLogged=${JwPageRules.NOT_LOGGED in home}",
                )
                throw JwHttpException.Protocol("教务登录没走通（CAS 已通过，教务侧会话未建立）。请在导入页手动登录一次，或稍后重试。")
            }
        } catch (e: IOException) {
            throw JwHttpException.Network(e)
        } catch (e: JwHttpException) {
            throw e
        } catch (e: kotlinx.coroutines.CancellationException) {
            // 取消（如调用方的总超时）必须原样抛出：吞掉它会变成 Protocol 异常，
            // 让上层的 withTimeoutOrNull 判不出超时，用户看到的是「链路异常」而非「超时」。
            throw e
        } catch (e: Exception) {
            throw JwHttpException.Protocol("登录链路异常：${e.message}")
        }
    }

    /**
     * 只校验会话还活着：GET 学生主页，按**字节数阈值 + 登录页特征**判定（DESIGN §4.27）。
     *
     * 比拉一次课表页（约 150KB）便宜得多，是 `CasSession.ensureValid` 的探针。
     * 网络层失败照抛 [JwHttpException.Network]，调用方据此区分「会话没了」与「网不通」。
     */
    override suspend fun verifySession(): Boolean = withContext(Dispatchers.IO) {
        try {
            val home = getFollowRedirects(STUDENT_HOME)
            JwPageRules.isUsableHome(home)
        } catch (e: IOException) {
            throw JwHttpException.Network(e)
        }
    }

    /**
     * 带会话取任意教务页面（DESIGN §3.3）。
     *
     * 给学籍卡这类「一个 GET 就够」的页面用——不必为它开一个 WebView 走注入 fetch。
     * **会话失效抛 [JwHttpException.SessionInvalid]**（页面里出现登录页，或账号被别处
     * 登录顶掉的那段 JSON），网络层失败抛 [JwHttpException.Network]。调用方据此决定
     * 要不要作废会话重登一次（见 `CasSession.fetchHtml`）。
     */
    override suspend fun fetchHtml(url: String): String = withContext(Dispatchers.IO) {
        try {
            val html = getFollowRedirects(url)
            if (looksLikeLoginPage(html)) {
                throw JwHttpException.SessionInvalid("教务会话已失效（页面退回登录页）")
            }
            if (looksLikeSessionConflict(html)) {
                throw JwHttpException.SessionInvalid("教务会话已被顶掉（账号在其它地方登录）")
            }
            html
        } catch (e: IOException) {
            throw JwHttpException.Network(e)
        }
    }

    /**
     * 带会话 POST 表单（2026-10-08 起；选课中心列表查询，见 [CasTransport.postForm]）。
     *
     * 判据与 [fetchHtml] 完全同款：登录页 / 被顶 JSON 抛 [JwHttpException.SessionInvalid]，
     * 网络层抛 [Network]、5xx 抛 [Protocol]。
     *
     * 重定向照 [getFollowRedirects] 的口径**手动跟随**，但降级为 GET——
     * 302 之后重发 POST 会丢原表单语义（教务在会话过期时可能把任意请求 302 到登录链）；
     * 单次请求内最多跟 [MAX_REDIRECTS] 跳。
     */
    override suspend fun postForm(url: String, form: List<Pair<String, String>>): String =
        withContext(Dispatchers.IO) {
            try {
                val body = FormBody.Builder().apply {
                    form.forEach { (name, value) -> add(name, value) }
                }.build()
                var current = url
                var request = Request.Builder().url(current).header("User-Agent", UA).post(body).build()
                repeat(MAX_REDIRECTS) {
                    client.newCall(request).execute().use { r ->
                        val location = r.header("Location")
                        if (r.code in 300..399 && location != null) {
                            current = current.toHttpUrl().resolve(location)?.toString()
                                ?: throw JwHttpException.Protocol("无法解析重定向地址：$location")
                            request = newGet(current)
                            return@use
                        }
                        if (r.code >= 500) throw JwHttpException.Protocol("$current 返回 HTTP ${r.code}")
                        val text = r.body?.string().orEmpty()
                        if (looksLikeLoginPage(text)) {
                            throw JwHttpException.SessionInvalid("教务会话已失效（页面退回登录页）")
                        }
                        if (looksLikeSessionConflict(text)) {
                            throw JwHttpException.SessionInvalid("教务会话已被顶掉（账号在其它地方登录）")
                        }
                        return@withContext text
                    }
                }
                throw JwHttpException.Protocol("重定向次数过多，链路可能已变")
            } catch (e: IOException) {
                throw JwHttpException.Network(e)
            }
        }

    private fun urlEncode(value: String): String =
        java.net.URLEncoder.encode(value, Charsets.UTF_8.name())

    private fun newGet(url: String): Request =
        Request.Builder().url(url).header("User-Agent", UA).get().build()

    private fun getFollowRedirects(url: String): String {
        var current = url
        repeat(MAX_REDIRECTS) {
            client.newCall(newGet(current)).execute().use { r ->
                val location = r.header("Location")
                if (r.code in 300..399 && location != null) {
                    // 以**当前请求 URL** 为基准解析 Location（可为绝对或相对）。
                    // 根因：此前写成 location.toHttpUrl().resolve(current)，参数反了——
                    // current 是绝对 URL，绝对 URL 解析结果就是它自己，于是原地打转
                    // MAX_REDIRECTS 轮后误报「重定向次数过多」，登录链路永远失败。
                    current = current.toHttpUrl().resolve(location)?.toString()
                        ?: throw JwHttpException.Protocol("无法解析重定向地址：$location")
                    return@use
                }
                if (r.code >= 500) throw JwHttpException.Protocol("$current 返回 HTTP ${r.code}")
                return r.body?.string().orEmpty()
            }
        }
        throw JwHttpException.Protocol("重定向次数过多，链路可能已变")
    }

    private fun getNoRedirect(url: String): String? =
        client.newCall(newGet(url)).execute().use { r ->
            if (r.code in 300..399) r.header("Location") else null
        }

    /**
     * 释放本会话占用的连接（2026-10-02 终审修正：**不再 shutdown 线程池**）。
     *
     * client 已改为共享池（[AppHttp.base]）派生——dispatcher 的 executorService 是
     * 全进程网络共享的，shutdown 掉会波及教务之外的所有链路（Qiekj/Qzxy/一卡通…；
     * OkHttp 对 shutdown 后到达的请求会重建 executor，所以是静默劣化不是崩溃）。
     * 当前此方法无人调用（会话单例进程存活期内复用），但留着就是定时炸弹。
     * 只清连接池：evictAll 的代价是各链路下次请求重连，可接受。
     */
    override fun shutdown() {
        runCatching { client.connectionPool.evictAll() }
    }
}

/**
 * OkHttp cookie → 中性形态（`CasTransport` 在 `shared/commonMain`，用不了 OkHttp 的类型）。
 *
 * 只搬五个字段：`MemoryCookieJar` 分桶靠 [SessionCookie.host]（OkHttp 的 `domain` 不带前导点，
 * 与 `adopt` 的分桶键同一口径），回放靠 path / secure。
 */
private fun Cookie.toSessionCookie(): SessionCookie = SessionCookie(
    name = name,
    value = value,
    host = domain.removePrefix("."),
    path = path,
    secure = secure,
)

/**
 * 中性形态 → OkHttp cookie（回灌方向）。
 *
 * 用 `hostOnlyDomain`：WebView 抄回来的是「某个 URL 下的 cookie」，本来就没有 Domain 属性，
 * 与 `WebViewCookieBridge.adopt` 搬迁前的构造逐字一致（那时也是 hostOnly + path `/`）。
 */
private fun SessionCookie.toOkHttpCookie(): Cookie {
    val builder = Cookie.Builder()
        .name(name)
        .value(value)
        .hostOnlyDomain(host)
        .path(path)
    if (secure) builder.secure()
    return builder.build()
}

