package edu.jxslu.schedule.data.session

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
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
     *
     * **必须切主队列**：`WKWebsiteDataStore` / `WKHTTPCookieStore` 是 WebKit 对象，只能在主线程
     * 上碰。调用方不一定在主线程——2026-11-25 晚冷启动探活就是从 `Dispatchers.Default` 进来的，
     * 结果 App 一启动就闪退。这条 dispatch 是唯一正确的修法（ObjC 的断言 Kotlin 的 try/catch
     * 兜不住）。
     */
    fun readCookieHeader(url: String, onResult: (String?) -> Unit) {
        val host: String? = NSURL.URLWithString(url)?.host
        dispatch_async(dispatch_get_main_queue()) {
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
     * Cookie 库现状的一句话（2026-11-25 晚补，**只用于诊断**）。
     *
     * 起因：真机上 WebView 明明登进了教务，`ensureValid` 却判「还没登录」。当时界面只说了
     * 「还没登录」，无法区分「库里一条都没有」和「有，但域名/名字对不上」——这两种的修法完全
     * 不同。所以把条数一起打出来，用户截一张图就能定位。
     *
     * 读库是异步回调，可能永不回来（极端情况），所以带超时；超时本身也是一条信息。
     */
    suspend fun describeStore(timeoutMs: Long = 3_000L): String {
        val all = withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine<List<NSHTTPCookie>> { cont ->
                // 同上：WebKit 对象只能在主线程碰
                dispatch_async(dispatch_get_main_queue()) {
                    WKWebsiteDataStore.defaultDataStore().httpCookieStore.getAllCookies { cookies ->
                        if (cont.isActive) {
                            cont.resume(cookies.orEmpty().filterIsInstance<NSHTTPCookie>())
                        }
                    }
                }
            }
        } ?: return "Cookie 库读取超时"
        val jw = all.filter { domainMatches(JW_COOKIE_HOST, it.domain) }
        if (jw.isEmpty()) return "Cookie 库里 ${all.size} 条，教务域 0 条"
        val names = jw.mapNotNull { it.name }.take(4).joinToString("、")
        return "Cookie 库里 ${all.size} 条，教务域 ${jw.size} 条（$names）"
    }

    /** 教务域的 host，只给 [describeStore] 统计用。 */
    private const val JW_COOKIE_HOST = "jiaowu.juwp.edu.cn"
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
