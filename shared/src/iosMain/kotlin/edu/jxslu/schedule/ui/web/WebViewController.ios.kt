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
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.WebKit.WKFrameInfo
import platform.WebKit.WKNavigation
import platform.WebKit.WKNavigationAction
import platform.WebKit.WKNavigationActionPolicy
import platform.WebKit.WKNavigationDelegateProtocol
import platform.WebKit.WKNavigationResponse
import platform.WebKit.WKNavigationResponsePolicy
import platform.WebKit.WKOpenPanelParameters
import platform.WebKit.WKUIDelegateProtocol
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
 * `WKWebView.navigationDelegate` / `UIDelegate` 都是 **weak**，代理必须由本文件的 holder
 * 强引用，否则代理会被立刻回收、回调一次都不来。离开组合时：停加载 → 摘两个代理 →
 * 断开 [WebViewController.impl]，再把强引用清掉。
 *
 * ## 六、2026-11-25 扩缝：五个事件接到哪
 *
 * | 事件 | iOS 侧入口 | `null` 时 |
 * |---|---|---|
 * | [WebViewController.onPickFiles] | `WKUIDelegate` 的 `runOpenPanelWithParameters:` | 立刻回「取消」（网页的上传按钮点了没反应，与今天一致） |
 * | [WebViewController.onGeolocationPrompt] | **未接**：`navigator.geolocation` 要宿主持有 `CLLocationManager` 并自己弹权限，本文不动它 | 一律不响应（与今天一致） |
 * | [WebViewController.onExternalScheme] | `decidePolicyForNavigationAction:`（只主 frame） | `Allow`，交给 WebKit（`Info.plist` 没配 `LSApplicationQueriesSchemes` 时打不开） |
 * | [WebViewController.onDownload] | `decidePolicyForNavigationResponse:` 里判 `canShowMIMEType` | `Allow`，WKWebView 显示不了就留一页空白 |
 * | [WebViewController.onPageReady] | `didFinishNavigation:` 之后 `evaluateJavaScript` | 不注入 |
 *
 * 这些方法本机同样**没有编译过**（Windows 无 Kotlin/Native），符号按「最保守写法」选，
 * 万一与绑定不符，按下表改一行即可：
 *
 * | 符号 | 写法 | 万一不对怎么改 |
 * |---|---|---|
 * | `runOpenPanelWithParameters` 的 completion | `(List<NSURL>?) -> Unit` | 改成 `(List<*>?) -> Unit`（ObjC 轻量泛型在部分绑定里被擦成 `List<*>`） |
 * | `decisionHandler(WKNavigationActionPolicy…)` | `decisionHandler(WKNavigationActionPolicy.WKNavigationActionPolicyAllow)` | 若枚举被暴露成常量，改成裸 `WKNavigationActionPolicyAllow` |
 * | `NSData.dataWithBytes` + `NSData.writeToURL` | `usePinned { NSData.dataWithBytes(it.addressOf(0), size.toULong()) }` 再 `writeToURL(url, atomically = true)` | 换成 POSIX（`platform.posix.fopen` / `fwrite`），符号更稳但要多管一次关闭 |
 * | `WKFrameInfo.mainFrame` | `targetFrame?.mainFrame` | 换成 `targetFrame?.isMainFrame`（旧绑定里就是这个名字） |
 * | `WKNavigationResponse` 的主 frame 判定 | `response.forMainFrame`（ObjC 属性名 `forMainFrame`，getter 是 `isForMainFrame`） | 换成 `isForMainFrame`，或整条判据删掉（下载本来就都在主 frame） |
 *
 * ## 七、iOS 侧明确**没做**的（本轮范围外，别当成 bug）
 *
 * - **下载落盘 / 另存到「文件」App**：接下去要 `WKNavigationDownload`（iOS 14.5+）+
 *   `WKNavigationDownloadDelegateProtocol` 决定落盘路径，再要 `UIDocumentPickerViewController`
 *   才能让用户选「存到哪」。本轮只在 [WebViewController.onDownload] 里把 URL / 建议文件名 /
 *   mime 交出去（`localPath` 恒 `null`），**落盘交给宿主**：拿 `IosWebCookieSource` 读同一个域
 *   的 Cookie、用共享层 Ktor 取，再用 `platform/AppDirs` 写进沙箱。
 * - **定位**：需要 `CLLocationManager` + `Info.plist` 的 `NSLocationWhenInUseUsageDescription`
 *   + `WKUIDelegate` 那条授权回调，另算一轮。
 * - **JS 桥（`WKScriptMessageHandler`）**：`configuration.userContentController` 现在是空的，
 *   没有任何页面需要往原生发消息。
 * - **多窗口**（`createWebViewWithConfiguration:`）：教务 / 学工都是同窗口导航（评教那套
 *   明确不准用 `window.open`），不做。
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

    /** `<input type=file>` 的代理（`UIDelegate` 同样是 weak，必须强引用）。 */
    private var uiDelegate: UiDelegate? = null
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
        val panel = UiDelegate(controller)
        view.UIDelegate = panel
        uiDelegate = panel
        val impl = WebViewController.Impl(
            load = { target -> loadInto(view, target) },
            reload = { view.reload() },
            currentUrl = { view.URL?.absoluteString },
            evaluateJavascript = { script, onResult -> evaluate(view, script, onResult) },
            cookieHeader = { target, onResult -> readCookieHeader(target, onResult) },
        )
        webView = view
        installed = impl
        controller.impl = impl
        // 同源取页的桥（`IosWebFetch`）：教务导入优先走这条路，不再依赖 Cookie 提取。
        // 包一层 dispatch 是因为抓取链可能从后台协程发起，而 WKWebView 只能在主线程调。
        val bridge: (String, (String?) -> Unit) -> Unit = { script, onResult ->
            dispatch_async(dispatch_get_main_queue()) {
                evaluate(view, script, onResult)
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
        view.UIDelegate = null
        // 只清自己装的那一份：另一个视图接管过桥时，别把它的拆掉
        val bridge = fetchBridge
        if (bridge != null && IosWebFetch.runScript === bridge) IosWebFetch.runScript = null
        fetchBridge = null
        webView = null
        navigationDelegate = null
        uiDelegate = null
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

    /** 跑一段 JS 并把结果转成字符串；[IosWebFetch] 与 [WebViewController.evaluateJavascript] 共用这一份。 */
    private fun evaluate(view: WKWebView, script: String, onResult: (String?) -> Unit) {
        view.evaluateJavaScript(script) { result, _ -> onResult(result as? String) }
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
 * 五个方法：完成（含加载后注入）、两个失败、两个策略判定（外链 / 下载）。
 *
 * `didFailNavigation` 与 `didFailProvisionalNavigation` 在 Kotlin 里签名一样
 * （`WKWebView, WKNavigation?, NSError`），靠 `@ObjCSignatureOverride` 才能并存——少了它就报
 * 「Conflicting overloads」。
 */
private class NavigationDelegate(
    private val controller: WebViewController,
) : NSObject(), WKNavigationDelegateProtocol {

    override fun webView(webView: WKWebView, didFinishNavigation: WKNavigation?) {
        val url = webView.URL?.absoluteString.orEmpty()
        controller.onPageFinished?.invoke(url)
        // 加载后注入（评教那套「取 token 再跳」）。空白脚本不注入，免得每页白跑一次。
        val script = controller.onPageReady?.invoke(url)
        if (!script.isNullOrBlank()) webView.evaluateJavaScript(script, null)
    }

    /**
     * 外链交给宿主（收银台的 `alipays://`、门户里的 `tel:` / `weixin://`）。
     *
     * 只判主 frame：子 frame 里的广告位跳到 `market://` 不该把 App 顶出去。判据用共享层的
     * [shouldHandOffToSystem]，与 Android 那份是**同一个函数**。
     */
    override fun webView(
        webView: WKWebView,
        decidePolicyForNavigationAction: WKNavigationAction,
        decisionHandler: (WKNavigationActionPolicy) -> Unit,
    ) {
        val target = decidePolicyForNavigationAction.request.URL?.absoluteString.orEmpty()
        val isMainFrame = decidePolicyForNavigationAction.targetFrame?.mainFrame ?: false
        val handler = controller.onExternalScheme
        if (handler != null && isMainFrame && shouldHandOffToSystem(target)) {
            handler(target)
            // 与 Android 同一口径：无论宿主说管没管，都不许 WebView 自己去加载它
            decisionHandler(WKNavigationActionPolicy.WKNavigationActionPolicyCancel)
            return
        }
        decisionHandler(WKNavigationActionPolicy.WKNavigationActionPolicyAllow)
    }

    /**
     * 拦下载：WKWebView 显示不了这种响应（PDF、`Content-Disposition: attachment`）时交给宿主。
     *
     * 见文件头第七节：iOS 侧**没有**落盘，`localPath` 恒 `null`，宿主自己用 Ktor + Cookie 取。
     * 没装 [WebViewController.onDownload] 就放行，保持现状。
     */
    override fun webView(
        webView: WKWebView,
        decidePolicyForNavigationResponse: WKNavigationResponse,
        decisionHandler: (WKNavigationResponsePolicy) -> Unit,
    ) {
        val handler = controller.onDownload
        val response = decidePolicyForNavigationResponse.response
        if (handler != null && decidePolicyForNavigationResponse.forMainFrame &&
            !decidePolicyForNavigationResponse.canShowMIMEType
        ) {
            handler(
                webView.URL?.absoluteString.orEmpty(),
                response.suggestedFilename,
                response.mimeType,
                null,
            )
            decisionHandler(WKNavigationResponsePolicy.WKNavigationResponsePolicyCancel)
            return
        }
        decisionHandler(WKNavigationResponsePolicy.WKNavigationResponsePolicyAllow)
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

/**
 * `<input type=file>` 的代理（`runOpenPanelWithParameters:`）。**必须被强引用**。
 *
 * 只实现这一个方法：`runJavaScriptAlert` / `Confirm` / `TextInput` 都不接——学工与教务的页面
 * 弹窗走的是页面自己的 layui 层，原生 alert 一旦被拦截反而会静默吞掉页面的确认对话框。
 * 恰好这三个方法不与本方法冲突（第二参类型不同），所以不需要 `@ObjCSignatureOverride`。
 *
 * 选文件由宿主决定（见 [WebViewController.onPickFiles]）：本类只负责「字节 → 临时文件 →
 * WebKit 收得了的 `NSURL`」和「没有宿主 → 立刻回取消」。
 */
private class UiDelegate(
    private val controller: WebViewController,
) : NSObject(), WKUIDelegateProtocol {

    /** 同一页连点两次上传时别互相覆盖临时文件。 */
    private var counter = 0

    override fun webView(
        webView: WKWebView,
        runOpenPanelWithParameters: WKOpenPanelParameters,
        initiatedByFrame: WKFrameInfo,
        completionHandler: (List<NSURL>?) -> Unit,
    ) {
        val picker = controller.onPickFiles
        if (picker == null) {
            // 回 null = 用户取消：网页那边的 change 事件不会触发，但也不会永远挂着
            completionHandler(null)
            return
        }
        val allowMultiple = runOpenPanelWithParameters.allowsMultipleSelection
        // accept 恒为空串：WKOpenPanelParameters 不暴露 accept 过滤（见契约 KDoc）
        picker.invoke("", allowMultiple) { files ->
            val urls = files.mapIndexedNotNull { index, file ->
                stageUploadFile(++counter, index, file)
            }
            completionHandler(urls.ifEmpty { null })
        }
    }

    /**
     * 把宿主给的字节写进临时目录，交出 `file://` URL。
     *
     * 不建子目录：`NSTemporaryDirectory()` 本身就可写，少一处 `NSFileManager` 的目录创建
     * （那一步的 Kotlin 绑定要传 `error` 指针，符号面更大）。名字带序号防重名。
     */
    private fun stageUploadFile(batch: Int, index: Int, file: WebPickedFile): NSURL? {
        if (file.bytes.isEmpty()) return null
        val dir = NSTemporaryDirectory() ?: return null
        val path = dir.trimEnd('/') + "/webview-upload-$batch-$index-" + safeName(file.name)
        val written = file.bytes.usePinned { pinned ->
            NSData.dataWithBytes(pinned.addressOf(0), file.bytes.size.toULong())
                .writeToURL(NSURL.fileURLWithPath(path), atomically = true)
        }
        return if (written) NSURL.fileURLWithPath(path) else null
    }

    /** 只留文件名主体 + 后缀，去掉路径分隔符（网页给的名字里可能带 `/` 或 `..`）。 */
    private fun safeName(raw: String): String {
        val base = raw.substringAfterLast('/').substringAfterLast('\\').trim()
            .replace(Regex("[^A-Za-z0-9._\\-\\u4e00-\\u9fa5]"), "_")
            .take(60)
        return base.trimStart('.').ifEmpty { "upload" }
    }
}
