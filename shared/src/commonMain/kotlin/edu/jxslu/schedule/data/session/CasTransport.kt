package edu.jxslu.schedule.data.session

import edu.jxslu.schedule.data.jw.JwHttpException

/**
 * `CasSession` 对 HTTP 层的全部要求（DESIGN §4.27）。
 *
 * 两个实现，登录链逐条对齐（CAS → `sso.jsp` 预热 → 教务 SSO → 会话校验）：
 * - Android：`:app` 的 `JwHttpSession`（OkHttp + 手动跟随 302，`scripts/jw_session.py` 的 Kotlin 复刻）；
 * - iOS：`IosCasTransport`（Ktor + `HttpSupport.InMemoryCookieStorage`）。
 *
 * 抽接口同样为了可测：会话策略的每条分支都要能在 JVM 上跑，不联网。
 *
 * **cookie 的类型是 [SessionCookie]，不是 `okhttp3.Cookie`**（2026-11-25 搬进共享层时改）：
 * 本接口此前挂着一个 OkHttp 类型，于是它只能在 `:app` 里存在，`CasSession` 也只能跟着留在
 * 那边——iOS 因此只剩「读 WebView cookie」一条路（用户报的「关掉导入页就又要登录」就是这个）。
 */
interface CasTransport {

    /**
     * 完整登录（CAS → 教务 SSO → 会话校验）。失败抛 [JwHttpException]。
     *
     * 四分类（凭证错 / 验证码 / 认不出的页面 / 5xx）由传输层判定后落成对应的子类，
     * 只有 `Credential` 会被 [CasSession] 记成失败计数——判据见 [CasLoginClassifier]。
     */
    suspend fun login(username: String, password: String)

    /** 只探会话有效性（学生主页）。网络层失败抛 `Network`。 */
    suspend fun verifySession(): Boolean

    /** 带会话取一个教务页面。给学籍卡这类一次性抓取用（DESIGN §3.3 的班级来源）。 */
    suspend fun fetchHtml(url: String): String

    /**
     * 带会话 **POST 表单**（2026-10-08 起；选课中心的列表查询用它——教务那个接口是
     * POST + 旧版 DataTables 参数，见 `JwUrls.SELECTION_PLAN_LIST_API`）。
     *
     * [form] 是 `(name, value)` 对；`columns[0][data]` 这类键值由实现方统一编码。
     * 失败抛 [JwHttpException]（登录页 / 被顶 = `SessionInvalid`）——与 [fetchHtml] 同一套判据。
     */
    suspend fun postForm(url: String, form: List<Pair<String, String>>): String

    /** 进程内有没有任何会话 cookie（只读，不发请求）。 */
    fun hasCookies(): Boolean

    /**
     * 当前会话的 cookie 快照。
     *
     * 现状**无生产调用方**（WebView 注入已被真机证伪，回灌是唯一方向）；留着是因为它是
     * 「会话里到底有什么」的唯一自述口，诊断与单测要用。真接功能前先读
     * `design/04c-services.md` 里那段「不做 cookie 注入」的结论。
     */
    fun cookies(): List<SessionCookie>

    /** 回灌：用户在 WebView 里手登之后，把平台 CookieManager 的 cookie 抄回会话。 */
    fun adoptCookies(cookies: List<SessionCookie>)

    fun clearCookies()

    fun shutdown()
}
