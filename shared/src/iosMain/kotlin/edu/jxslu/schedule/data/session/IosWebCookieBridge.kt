package edu.jxslu.schedule.data.session

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSDate
import platform.Foundation.NSHTTPCookie
import platform.Foundation.NSURL
import platform.WebKit.WKWebsiteDataStore
import platform.WebKit.WKWebsiteDataTypeCookies
import platform.WebKit.WKWebsiteDataTypeLocalStorage
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.coroutines.resume

/**
 * [WebCookieBridge] 的 iOS 实现：cookie 库换成 `WKWebsiteDataStore`（系统替我们保管的那份）。
 *
 * 与 Android 的 `WebViewCookieBridge` 一一对应，差别都来自平台：
 *
 * | 能力 | Android | iOS |
 * |---|---|---|
 * | [adopt] | `CookieManager.getCookie(url)`（同步） | `httpCookieStore.getAllCookies`（异步，只能主线程碰） |
 * | [hasAnyCookie] | 同步读 cookie 头 | **读不了同步**，见下 |
 * | [clearAll] | `removeAllCookies` + `WebStorage.deleteAllData` | `removeDataOfTypes`（cookie + localStorage） |
 *
 * ## 为什么 hasAnyCookie 是同步却只能在 iOS 上给「进程标记」
 *
 * 接口里的 `hasAnyCookie` 是同步的（Android 的 `CookieManager` 就是同步），而 iOS 读 cookie
 * 库只有异步回调——没有 `runBlocking` 之类的桥（那会卡死主线程，正是 `IosWebCookieSource`
 * 注释里那条闪退的成因）。所以这里回 [IosJwSessionFlag.hasSession]：它由**同一个探针**
 * 维护（`IosServices.warmUp` 冷启动探一次、每次抓取成功/失败顺手更新、导入页落到学生端立刻置真）。
 *
 * 代价与边界：这个标志是「这次进程里教务会话还活着吗」的缓存，不是 cookie 库的实时快照。
 * 冷启动还没探过时它是 false——此时 [CasSession] 会判「没有网页会话可用」，
 * 用户看到的是「在导入页登录一次」，而不是猜出来一个可能已死的会话。
 * **真实读取永远走 [adopt]**（异步、拿得到就别猜）。
 *
 * ## 未验
 *
 * 本文件在 Windows 上编不了，只过 CI 的 macOS 编译。`removeDataOfTypes` 是本次新引入的
 * ObjC 调用（另外三个 `WKWebsiteDataStore` 符号在 `IosWebCookieSource` 里已经跑通），
 * 若 CI 报 unresolved，改动收敛在本文件的 [clearAll]。
 */
@OptIn(ExperimentalForeignApi::class)
internal object IosWebCookieBridge : WebCookieBridge {

    override suspend fun adopt(urls: List<String>): List<SessionCookie> {
        val all = readAllCookies() ?: return emptyList()
        return urls.flatMap { url ->
            val parsed = NSURL.URLWithString(url) ?: return@flatMap emptyList()
            val host = parsed.host ?: return@flatMap emptyList()
            // 与 Android 同口径：domain 从 URL 推、path 记 `/`、secure 按 URL 协议推。
            // `getCookie(url)` 那边也没有 Domain/Path，两边抄回来的形状因此一致。
            val secure = url.startsWith("https://")
            all.filter { domainMatches(host, it.domain) }
                .mapNotNull { cookie ->
                    cookie.value?.let { value ->
                        SessionCookie(
                            name = cookie.name,
                            value = value,
                            host = host,
                            path = "/",
                            secure = secure,
                        )
                    }
                }
        }
    }

    override fun hasAnyCookie(urls: List<String>): Boolean = IosJwSessionFlag.hasSession

    /**
     * 清掉 WebView 的 cookie **与 localStorage**（退出登录 / 清缓存共用）。
     *
     * 必须两样一起清：只清 cookie 的话，学工平台那个存在 localStorage 里的 token
     * （`cx_qmx_token`）还留着，换账号后学工窗口可能带出上一位用户的数据
     * （Android 侧 `WebViewCookieBridge.clearAll` 是同一口径）。
     *
     * `modifiedSince` 传**纪元起点**：要清的是全部历史数据，不是「某时刻之后」。
     */
    override suspend fun clearAll() {
        val types = setOf(WKWebsiteDataTypeCookies, WKWebsiteDataTypeLocalStorage)
        suspendCancellableCoroutine { continuation ->
            // WebKit 对象只能在主线程碰（与 IosWebCookieSource 同一条纪律）
            dispatch_async(dispatch_get_main_queue()) {
                WKWebsiteDataStore.defaultDataStore().removeDataOfTypes(
                    types,
                    // Kotlin/Native 的 `NSDate` 只有「参考日期」构造器（2001-01-01 起算），
                    // 没有 `timeIntervalSince1970`（2026-11-25 CI 实测）。要清全部历史数据就传
                    // 一个远早于任何数据的参考时刻：-2e9 秒 ≈ 1938 年，比设备上任何 cookie 都早。
                    NSDate(timeIntervalSinceReferenceDate = -2.0e9),
                ) {
                    IosJwSessionFlag.markLost()
                    if (continuation.isActive) continuation.resume(Unit)
                }
            }
        }
    }

    /** 读一次整个 cookie 库；读不到（回调没回来 / 类型对不上）回 null。 */
    private suspend fun readAllCookies(): List<NSHTTPCookie>? =
        suspendCancellableCoroutine { continuation ->
            dispatch_async(dispatch_get_main_queue()) {
                WKWebsiteDataStore.defaultDataStore().httpCookieStore.getAllCookies { cookies ->
                    // `getAllCookies` 的入参在 Kotlin/Native 里是可空 `List<*>?`（ObjC 无泛型标注）
                    if (continuation.isActive) {
                        continuation.resume(cookies.orEmpty().filterIsInstance<NSHTTPCookie>())
                    }
                }
            }
        }

    /**
     * cookie 的 `domain`（可能带前导点）能否覆盖 [host]。
     *
     * 强智会往 `jiaowu.juwp.edu.cn` 与父域 `.juwp.edu.cn` 两处写 Cookie，既要精确匹配也要认后缀。
     * 与 `IosWebCookieSource` 里那份是同一判据——它已经在真机上跑通过，两边必须一致，
     * 否则「探针说没登录、抓取却能取到」这种自相矛盾就会出现。
     */
    private fun domainMatches(host: String, domain: String?): Boolean {
        val normalized = (domain ?: return false).removePrefix(".")
        if (normalized.isEmpty()) return false
        return host == normalized || host.endsWith(".$normalized")
    }
}
