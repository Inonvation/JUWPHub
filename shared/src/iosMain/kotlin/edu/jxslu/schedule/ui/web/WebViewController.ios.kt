@file:OptIn(ExperimentalForeignApi::class)

package edu.jxslu.schedule.ui.web

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import edu.jxslu.schedule.data.session.IosWebCookieSource
import edu.jxslu.schedule.data.session.IosWebFetch
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCSignatureOverride
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSError
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.WebKit.WKNavigation
import platform.WebKit.WKNavigationDelegateProtocol
import platform.WebKit.WKUserContentController
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/**
 * [WebContentView] 的 iOS 实现：`UIKitView` + `WKWebView`（iOS 移植，2026-11-24）。
 *
 * ## 一、CMP 1.7.3 的 `UIKitView` 到底是哪个包、什么签名（已核实，不是推断）
 *
 * 结论：**`androidx.compose.ui.viewinterop.UIKitView`**，签名是
 *
 * ```kotlin
 * @Composable
 * fun <T : UIView> UIKitView(
 *     factory: () -> T,
 *     modifier: Modifier = Modifier,
 *     update: (T) -> Unit = NoOp,
 *     onRelease: (T) -> Unit = NoOp,
 *     onReset: ((T) -> Unit)? = null,
 *     properties: UIKitInteropProperties = UIKitInteropProperties.Default,
 * )
 * ```
 *
 * 1.7.x 里**两个包都在、都是同一个 klib**，所以「挪过包」这个说法只对一半：
 *
 * | 包 | 状态 | 签名差异 |
 * |---|---|---|
 * | `androidx.compose.ui.viewinterop.UIKitView` | 新 API，1.7 起 | 多了 `onReset` / `properties`，没有 `background`/`onResize` |
 * | `androidx.compose.ui.interop.UIKitView` | 仍在，已 `@Deprecated`（`ReplaceWith` 指向新包） | 有 `background`/`onResize`/`interactive`/`accessibilityEnabled`，内部转调新包 |
 *
 * 新代码一律走 `viewinterop`；旧包只是因为同一 klib 里还留着包装函数才编得过。
 *
 * **怎么确认的**（本机是 Windows，Kotlin/Native 编不了，所以走「拿源码」这条路）：
 *
 * - Gradle 缓存里只有 iOS 变体的 **`.module` / `.pom`**，没有 klib、也没有 sources：
 *   `$env:GRADLE_USER_HOME` = `E:\DevCache\gradle`（不是 `~/.gradle`），
 *   `E:\DevCache\gradle\caches\modules-2\files-2.1\org.jetbrains.compose.ui\ui-uikitarm64\1.7.3\`
 *   下只有 `ui-uikitarm64-1.7.3.module` 与 `.pom`。`~/.gradle` 里只有 `ui-backhandler-android`，
 *   跟 CMP 的 iOS 产物无关；本机也没有 `~/.konan`（没有 Kotlin/Native 分发）。
 * - 那个 `.module` 的 `uikitArm64SourcesElements-published` 变体写明产物是
 *   `ui-uikitarm64-1.7.3-sources.jar`（728300 B）。从 Maven Central 取下来解包，得到
 *   `uikitMain/androidx/compose/ui/viewinterop/UIKitView.uikit.kt` 与同目录的
 *   `UIKitInteropProperties.uikit.kt`，上面那段签名是逐字抄的：
 *   `package androidx.compose.ui.viewinterop`，形参顺序 `factory, modifier, update, onRelease, onReset, properties`。
 * - 旁证：Kotlin 官方文档《Integration with the UIKit framework》的 WebView 示例也是
 *   `UIKitView(factory = { WKWebView()... }, modifier = ...)`，与源码一致。
 *
 * 本文件**只传 `factory` / `modifier` / `update`**：[`properties`] 的主构造器带
 * `@ExperimentalComposeUiApi`，而我们要的 `interactionMode`（默认可交互）与
 * `isNativeAccessibilityEnabled`（默认关）都用不着，不值得为一个 opt-in 冒编译风险。
 *
 * ## 二、这段代码本机**没有编译过**（Kotlin/Native 要 macOS）
 *
 * 用到的符号，按「风险从高到低」排（都没法在本机核对，因为本机既无 `.konan` 也无
 * `kotlin-native-prebuilt` 的 cinterop klib）：
 *
 * | 符号 | 写法 | 万一不对怎么改 |
 * |---|---|---|
 * | `NSURL.URLWithString` / `NSURLRequest.requestWithURL` | 类工厂（`+URLWithString:` / `+requestWithURL:`） | 换成构造器：`NSURL(string = ...)` / `NSURLRequest(uRL = ...)`。两处都已用「显式可空类型 + 判空」写，规避可空性差异 |
 * | `@ObjCSignatureOverride`（`kotlinx.cinterop`） | 标在 `didFailProvisionalNavigation` 上 | 两个失败回调在 Kotlin 里签名相同（`WKWebView, WKNavigation?, NSError`），必须靠它区分 ObjC selector。若这版不允许，**删掉 `didFailProvisionalNavigation` 那一段**即可，`onError` 退化为只报非 provisional 失败 |
 * | `platform.darwin.dispatch_async` / `dispatch_get_main_queue` | Cookie 回调切回主线程 | 换成 `NSOperationQueue.mainQueue.addOperationWithBlock { }` |
 * | `WKWebsiteDataStore.defaultDataStore().httpCookieStore.getAllCookies { }` | `cookies` 参数类型在不同版本是 `List<NSHTTPCookie>` 或 `List<*>` | 已用 `filterIsInstance<NSHTTPCookie>()`，两种都编得过 |
 * | `WKWebView(frame = …, configuration = …)` | `initWithFrame:configuration:` 的 Kotlin 构造器 | 换成 `WKWebView().apply { … }` 也行（`configuration` 是只读属性，就只能用构造器） |
 * | `webView.URL?.absoluteString` / `evaluateJavaScript(script) { result, error -> }` | 与官方文档示例同形 | — |
 *
 * ## 三、为什么 Cookie 回调要切主线程、为什么要按域过滤
 *
 * `WKHTTPCookieStore.getAllCookies` 的回调**在任意线程**执行（Apple 未承诺主线程），而
 * `onResult` 最终会流进共享层的 Ktor 会话与 Compose 状态，所以统一切回主队列再交出去。
 *
 * 过滤按 cookie 的 `domain` 与目标 URL 的 host 比对：WKWebView 的 cookie store 是**全 App
 * 一份**，把一卡通、学工、教务的 cookie 全塞进同一个 `Cookie` 头交给 Ktor，等于把 A 站的会话
 * 凭据发给 B 站。`NSHTTPCookie.domain` 可能带前导点（`.juwp.edu.cn`），比对前先去掉。
 *
 * ## 四、Android/iOS 有意的行为差异
 *
 * - **错误回调的覆盖面**：Android 的 `onReceivedError` 与 iOS 的 `didFailNavigation` /
 *   `didFailProvisionalNavigation` 都只报「主文档失败」这一档；两边都不会把子资源 404 报上来。
 * - **`evaluateJavascript` 的返回文本形状不同**（Android 是 JSON 字面量，iOS 是 `toString`），
 *   见 `WebViewController.evaluateJavascript` 的 KDoc。教务导入的脚本都自己 `JSON.stringify`，
 *   这条差异不影响使用。
 * - **混合内容**：iOS 的 WKWebView 没有 Android 那套 `mixedContentMode` 开关，https 页面里的
 *   http 资源由 WebKit 按 ATS 规则处理（Info.plist 的 `NSAppTransportSecurity` 需要在 P3 的壳里配）。
 *
 * ## 五、销毁
 *
 * `WKWebView.navigationDelegate` 是 **weak**，代理必须由本文件的 holder 强引用，否则代理
 * 会被立刻回收、回调一次都不来。离开组合时：停加载 → 摘代理 → 断开 [WebViewController.impl]，
 * 再把两个强引用清掉。
 */
@Composable
actual fun WebContentView(controller: WebViewController, url: String, modifier: Modifier) {
    val host = remember { IosWebHost() }
    UIKitView(
        factory = { host.attach(controller) },
        modifier = modifier,
        // update 在视图创建后也会跑一次，所以「首次加载」不需要额外副作用——那反而会和
        // factory 抢时序（视图还没建好就被调用，`loadUrl` 打到 null 上静默丢失）
        update = { webView -> host.loadIfChanged(webView, url) },
    )
    DisposableEffect(controller) {
        onDispose { host.detach(controller) }
    }
}

/** 原生视图、它的导航代理（必须强引用）与安装给控制器的实现组。 */
private class IosWebHost {

    private var webView: WKWebView? = null
    private var navigationDelegate: NavigationDelegate? = null
    private var loadedUrl: String? = null
    private var installed: WebViewController.Impl? = null

    /** 装给 [IosWebFetch] 的桥（留引用是为了在 [detach] 时只清自己装的那一份）。 */
    private var fetchBridge: ((String, (String?) -> Unit) -> Unit)? = null

    /** 建 WKWebView 并接上代理与控制器。Compose 在 iOS 上跑在主线程，符合 UIKit 要求。 */
    fun attach(controller: WebViewController): WKWebView {
        val configuration = WKWebViewConfiguration()
        // 现在没注册任何 message handler（JS 桥不在本缝范围内），但配置对象仍要显式给：
        // 后续要加 handler 时只改这里，不用再动构造流程
        configuration.userContentController = WKUserContentController()
        val view = WKWebView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0), configuration = configuration)
        val delegate = NavigationDelegate(controller)
        view.navigationDelegate = delegate
        navigationDelegate = delegate
        val impl = WebViewController.Impl(
            load = { target -> loadInto(view, target) },
            reload = { view.reload() },
            currentUrl = { view.URL?.absoluteString },
            evaluateJavascript = { script, onResult ->
                view.evaluateJavaScript(script) { result, _ -> onResult(result as? String) }
            },
            cookieHeader = { target, onResult -> readCookieHeader(target, onResult) },
        )
        webView = view
        installed = impl
        controller.impl = impl
        // 同源取页的桥（`IosWebFetch`）：教务导入优先走这条路，不再依赖 Cookie 提取。
        // 包一层 dispatch 是因为抓取链可能从后台协程发起，而 WKWebView 只能在主线程调。
        val bridge: (String, (String?) -> Unit) -> Unit = { script, onResult ->
            dispatch_async(dispatch_get_main_queue()) {
                view.evaluateJavaScript(script) { result, _ -> onResult(result as? String) }
            }
        }
        fetchBridge = bridge
        IosWebFetch.runScript = bridge
        return view
    }

    /** [url] 变了才重新加载：同值重载会把用户正在填的登录表单清空。 */
    fun loadIfChanged(view: WKWebView, url: String) {
        if (loadedUrl == url) return
        loadedUrl = url
        loadInto(view, url)
    }

    /** 停加载 → 摘代理 → 断开控制器。见文件头第五节。 */
    fun detach(controller: WebViewController) {
        val view = webView ?: return
        view.stopLoading()
        view.navigationDelegate = null
        // 只清自己装的那一份：另一个视图接管过桥时，别把它的拆掉
        val bridge = fetchBridge
        if (bridge != null && IosWebFetch.runScript === bridge) IosWebFetch.runScript = null
        fetchBridge = null
        webView = null
        navigationDelegate = null
        loadedUrl = null
        if (controller.impl === installed) controller.impl = null
        installed = null
    }

    private fun loadInto(view: WKWebView, url: String) {
        val target: NSURL? = NSURL.URLWithString(url)
        if (target == null) return
        val request: NSURLRequest? = NSURLRequest.requestWithURL(target)
        if (request == null) return
        view.loadRequest(request)
    }

    /**
     * 读某个 URL 的 Cookie 请求头。
     *
     * **实现已收拢到 `data/session/IosWebCookieSource`**：那个文件直接从
     * `WKWebsiteDataStore` 读，不依赖视图存活——用户关掉导入页之后课表页的「检测」还要靠它。
     * 这里只保留转发，避免两份同义实现各自漂移。
     */
    private fun readCookieHeader(url: String, onResult: (String?) -> Unit) {
        IosWebCookieSource.readCookieHeader(url, onResult)
    }
}

/**
 * 导航代理。**必须被强引用**（`navigationDelegate` 是 weak），持有者是 [IosWebHost]。
 *
 * 只实现三个方法：完成、非 provisional 失败、provisional 失败。后两个在 Kotlin 里签名一样
 * （`WKWebView, WKNavigation?, NSError`），靠 `@ObjCSignatureOverride` 才能并存——少了它就报
 * 「Conflicting overloads」。
 */
private class NavigationDelegate(
    private val controller: WebViewController,
) : NSObject(), WKNavigationDelegateProtocol {

    override fun webView(webView: WKWebView, didFinishNavigation: WKNavigation?) {
        controller.onPageFinished?.invoke(webView.URL?.absoluteString.orEmpty())
    }

    /**
     * 加载失败（已开始接收内容之后）。
     *
     * 与下面的 provisional 版**在 Kotlin 里是同一个签名**（`WKWebView, WKNavigation?, NSError`），
     * 所以两个 override 都必须标 `@ObjCSignatureOverride`——只标一个仍报 Conflicting overloads
     * （2026-11-25 CI 实测）。
     */
    @ObjCSignatureOverride
    override fun webView(webView: WKWebView, didFailNavigation: WKNavigation?, withError: NSError) {
        controller.onError?.invoke(withError.localizedDescription)
    }

    /** DNS / TLS / 连不上这类失败走的是 provisional 这一档，`didFailNavigation` 收不到。 */
    @ObjCSignatureOverride
    override fun webView(
        webView: WKWebView,
        didFailProvisionalNavigation: WKNavigation?,
        withError: NSError,
    ) {
        controller.onError?.invoke(withError.localizedDescription)
    }
}
