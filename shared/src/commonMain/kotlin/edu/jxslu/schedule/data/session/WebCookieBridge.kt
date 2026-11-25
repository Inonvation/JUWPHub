package edu.jxslu.schedule.data.session

/**
 * WebView 会话 → 原生会话的**单向回灌**（DESIGN §4.27）。
 *
 * **只回灌，不注入。** 2026-09-24 真机定性：把原生登录拿到的 cookie 注入 `CookieManager`
 * 这条路走不通——cookie 确实写进去了（逐条读回验证 7/7 条落位），但 WebView 发请求时就是
 * 不带（属性缺 `SameSite=None`，跨站跳转不带）。登录交给 WebView 自己完成，
 * 这里只把它**用出来的**会话抄回原生侧的 jar。
 *
 * 抽接口是为了让 `CasSession.adoptFromWebView` 能在 JVM 单测里跑，也因为两端实现不同：
 * - Android：`WebViewCookieBridge`（`android.webkit.CookieManager`，读写要有 Looper）；
 * - iOS：`IosWebCookieBridge`（`WKWebsiteDataStore`，异步读、只能主线程碰）。
 */
interface WebCookieBridge {

    /** 从白名单域抄回 cookie；没有就是空集合。 */
    suspend fun adopt(urls: List<String>): List<SessionCookie>

    /**
     * 这些域里有没有**会话标识**（[SessionCookieRules]）。**只读本机 cookie 库，不发请求。**
     *
     * `adopt` 会把站点标记（`bzb_njw` 这类）一起抄回来，值不值得抄先由它判：
     * 抄一份没有会话的 cookie 只会让 `CasSession` 白探一次教务。
     */
    fun hasAnyCookie(urls: List<String> = CasSession.ADOPT_URLS): Boolean

    /**
     * 清掉 WebView 的 cookie **与 localStorage**（退出登录 / 清缓存共用）。
     *
     * 不清这里的代价：用户「退出登录并清除密码」后，WebView 里留着的手登会话仍然有效——
     * 导入页探针通过照常直进教务，状态卡也一直「已登录」，退出形同虚设。
     * 必须连 localStorage 一起清：学工平台把登录 token（`cx_qmx_token`）放在这个源下。
     */
    suspend fun clearAll()
}
