package edu.jxslu.schedule.data.jw

/**
 * 从 WebView 的 CookieManager 读签章系统的会话 Cookie。
 *
 * **为什么留在 `:app`**（iOS 移植 S9，2026-11-24）：这一行是 `android.webkit.CookieManager`，
 * `commonMain` 里没有对等物。签章系统的接口调用与解析（[TranscriptClient] /
 * [PtworkTranscript]）已经整份搬进共享层，只剩「`sid` 从哪来」这一个平台动作留在这里。
 *
 * iOS 要接这条链路时的升级条件（现在是**未实现**，不是漏了）：先有 WKWebView 的授权窗口，
 * 再用 `WKWebsiteDataStore.httpCookieStore.getAllCookies` 按
 * [PtworkTranscript.COOKIE_URL] 的域取出 `sid`，返回同样形态的 `sid=…` 请求头即可——
 * [TranscriptClient] 的入参就只是一个字符串，不需要改共享层任何代码。
 */
object TranscriptCookies {

    /**
     * 返回可直接当请求头用的 `sid=…`，未授权时为 null。
     *
     * HttpOnly 的 Cookie 这里也读得到：`CookieManager.getCookie` 给的是**该 URL 会发出的
     * Cookie 头原文**，不受脚本可见性限制。读不到只有一种解释——授权窗口还没走过一遍。
     */
    fun sessionHeader(): String? = runCatching {
        android.webkit.CookieManager.getInstance().getCookie(PtworkTranscript.COOKIE_URL)
    }.getOrNull()?.trim()?.takeIf { it.isNotEmpty() }
}
