package edu.jxslu.schedule.data.session

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSHTTPCookie
import platform.Foundation.NSURL
import platform.WebKit.WKWebsiteDataStore
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.coroutines.resume

/**
 * iOS 侧的 Cookie 来源（iOS 移植 M2，2026-11-25）。
 *
 * ## 为什么不复用 `ui/web/WebViewController.getCookieHeader`
 *
 * 那个方法转调的是**活着的原生视图**——视图一销毁就回 null。而用户登录完、关掉导入页之后，
 * 课表页的「检测」仍然要读 Cookie：这时 WebView 早就没了，但 `WKWebsiteDataStore` 里的
 * 登录态还在（系统替我们保管）。所以 Cookie 必须直接从数据仓库读，不依赖任何视图存活。
 *
 * 这也是 `ui/web` 那边 `readCookieHeader` 现在改为**转调本文件**的原因：两份同义实现迟早分叉。
 *
 * ## 线程
 *
 * `getAllCookies` 的回调在任意线程；本文件统一切回主队列再交给 `onResult`
 * 与共享层那侧的回调约定一致（`WebViewController` 的两个回调都在主线程触发）。
 *
 * ## 编译状态
 *
 * **本机未验证**（Windows 无 Kotlin/Native）。用到的 `platform.WebKit` 符号：
 * `WKWebsiteDataStore.defaultDataStore()`、`httpCookieStore`、`getAllCookies`，
 * 以及 `NSHTTPCookie` 的 `name` / `value` / `domain`、`NSURL.URLWithString`。
 * 若 CI 报 unresolved，改动收敛在本文件。
 */
@OptIn(ExperimentalForeignApi::class)
object IosWebCookieSource : WebCookieSource {

    override suspend fun cookieHeader(url: String): String? =
        suspendCancellableCoroutine { continuation ->
            readCookieHeader(url) { header ->
                // 协程可能已被取消（页面关掉了）；resume 一个已取消的续体是安全的，但仍判一下更清楚
                if (continuation.isActive) continuation.resume(header)
            }
        }

    /**
     * 读 [url] 这个域下的 Cookie 并拼成 `"k=v; k2=v2"`。
     *
     * 没有任何 Cookie 时回 null（不是空串）：调用方靠 null 区分「没登录」。
     */
    fun readCookieHeader(url: String, onResult: (String?) -> Unit) {
        val host: String? = NSURL.URLWithString(url)?.host
        WKWebsiteDataStore.defaultDataStore().httpCookieStore.getAllCookies { cookies ->
            // `getAllCookies` 的入参在 Kotlin/Native 里是**可空**的 `List<*>?`
            // （ObjC 的 NSArray 没做非空标注），先 orEmpty 再过滤。
            val header = cookies.orEmpty()
                .filterIsInstance<NSHTTPCookie>()
                .filter { cookie -> host == null || domainMatches(host, cookie.domain) }
                .mapNotNull { cookie -> cookie.value?.let { value -> "${cookie.name}=$value" } }
                .joinToString("; ")
            val result: String? = if (header.isEmpty()) null else header
            dispatch_async(dispatch_get_main_queue()) { onResult(result) }
        }
    }
}

/**
 * cookie 的 `domain`（可能带前导点）能否覆盖 [host]。
 *
 * 强智会往 `jiaowu.juwp.edu.cn` 与父域 `.juwp.edu.cn` 两处写 Cookie，所以既要精确匹配
 * 也要认后缀；统一身份认证在 `eapp2.juwp.edu.cn`，同一套规则覆盖。
 */
private fun domainMatches(host: String, domain: String?): Boolean {
    val normalized = (domain ?: return false).removePrefix(".")
    if (normalized.isEmpty()) return false
    return host == normalized || host.endsWith(".$normalized")
}
