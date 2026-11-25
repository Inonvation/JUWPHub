package edu.jxslu.schedule.data.session

import android.webkit.CookieManager
import android.webkit.WebStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * [WebCookieBridge] 的 Android 实现（接口与「只回灌不注入」的理由见那个文件）。
 *
 * 搬迁史：本对象原先自带一份 `WebCookieBridge` 接口 + `List<okhttp3.Cookie>` 的签名，
 * 2026-11-25 随 `CasSession` 一起上移——接口进 `shared/commonMain`（两端共用），
 * 实现留在 `:app`，cookie 换成中性 [SessionCookie]。
 */
object WebViewCookieBridge : WebCookieBridge {

    /**
     * 从 `CookieManager` 抄回白名单域的 cookie。
     *
     * `getCookie(url)` 只给 `Cookie:` 头原文（`a=1; b=2`），没有 Domain / Path / Secure，
     * 所以 domain / secure 从 URL 推、path 记 `/`。这足以在原生侧匹配得上——
     * 需要的只是「带着它去请求同一个域」。
     */
    override suspend fun adopt(urls: List<String>): List<SessionCookie> = withContext(Dispatchers.Main) {
        val manager = CookieManager.getInstance()
        urls.flatMap { url ->
            val parsed = url.toHttpUrlOrNull() ?: return@flatMap emptyList()
            val header = runCatching { manager.getCookie(url) }.getOrNull()
            if (header.isNullOrBlank()) return@flatMap emptyList()
            header.split(';').mapNotNull { part ->
                val name = part.substringBefore('=', "").trim()
                if (name.isEmpty()) return@mapNotNull null
                val value = part.substringAfter('=', "").trim()
                SessionCookie(
                    name = name,
                    value = value,
                    host = parsed.host,
                    path = "/",
                    secure = parsed.isHttps,
                )
            }
        }
    }

    /**
     * 同步探测：这些域里有没有 cookie。**只读 `CookieManager`，一个请求都不发。**
     *
     * 给「我的」页状态卡用（DESIGN §3.16）：升级用户没存凭证，但 WebView 里可能还留着
     * 有效会话。不认这一点，状态卡就会出现「说未登录、点进导入却能用」的自相矛盾。
     *
     * **只认会话标识**（[SessionCookieRules]）：`bzb_njw` 这类站点标记也在 `CookieManager`
     * 里长期存着，把它们算成会话会让状态卡报出一个干不了任何事的「已登录」。
     *
     * 必须在有 Looper 的线程调用——组合期的主线程正合适。
     */
    override fun hasAnyCookie(urls: List<String>): Boolean {
        val manager = CookieManager.getInstance()
        return urls.any { url ->
            val header = runCatching { manager.getCookie(url) }.getOrNull()
            SessionCookieRules.hasSessionCookie(header)
        }
    }

    override suspend fun clearAll() = withContext(Dispatchers.Main) {
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        // 学工 token 在 localStorage（AppCacheCleaner 清缓存同款口径）：只清 cookie
        // 等于「退出」只退了一半，换账号后可能带出上一位用户的学工数据。
        WebStorage.getInstance().deleteAllData()
    }
}
