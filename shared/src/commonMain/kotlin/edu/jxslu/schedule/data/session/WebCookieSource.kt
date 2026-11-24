package edu.jxslu.schedule.data.session

/**
 * 「从平台 WebView 里取 Cookie」的跨平台缝（iOS 移植 M2，2026-11-25）。
 *
 * ## 为什么必须有它
 *
 * 教务的登录态只能由 WebView 自己登出来（统一身份认证带验证码、带 `bzb_njw` 预热，
 * 代码注入填表在真机上被证伪过，见 `design/04c-services.md` 的结论）。所以共享层的抓取链
 * 拿不到「怎么登录」，只能拿到**登录之后**的那串 Cookie，再去发 HTTP。
 *
 * 这条缝就是那个交接点：平台侧（iOS 的 `WKWebsiteDataStore`、Android 的 `CookieManager`）
 * 把某个 URL 的 Cookie 头交出来，共享层用它拼 `Cookie:` 头。
 *
 * ## 为什么是「持有 + 启动时注入」而不是参数传递
 *
 * WebView 活在 UI 层，抓取链活在数据层，两者没有直接的构造关系；
 * 而 Cookie 是**随用户登录变化**的运行时状态，不能在装配期一次性取值。
 * 这与 `platform/AppServices`、`ui/common/ShareSettings` 同一套注入纪律。
 *
 * 反向（把 Cookie 灌回 WebView）**永远不做**：WebView 的会话只能由它自己产生，
 * 回灌是制造两条互相打架的会话。
 */
fun interface WebCookieSource {

    /**
     * 取 [url] 这个站点的 Cookie 请求头，形如 `"JSESSIONID=xxx; TGC=yyy"`。
     *
     * 没有 Cookie（还没登录 / 视图已销毁）时回 **null**，不要回空串——调用方靠 null
     * 区分「没登录」与「登录了但站点确实没给 Cookie」。
     */
    suspend fun cookieHeader(url: String): String?
}

/**
 * [WebCookieSource] 的注入点。
 *
 * 页面侧在创建原生 WebView 时安装、销毁时清空；抓取链在**每次请求前**读它，
 * 所以用户中途登录 / 退出都能立刻生效，不需要重启。
 */
object WebCookies {

    var source: WebCookieSource? = null

    /** 便利读法：没有安装来源时回 null（等价于「这个平台还没接 WebView」）。 */
    suspend fun headerFor(url: String): String? = source?.cookieHeader(url)
}
