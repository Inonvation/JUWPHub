package edu.jxslu.schedule.ui.web

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 内置 WebView 的平台缝（iOS 移植，2026-11-24 立；2026-11-25 从「教务导入够用」扩到
 * 「学工表单 / 盖章成绩单 / 评教 / 收银台跑得起来」）。
 *
 * 四个函数（加载、读当前 URL、`reload()`、执行 JS、读 Cookie 头）是**动作**，
 * 由调用方发起；剩下五个是**事件**，由宿主装成可空回调，平台侧在对应的原生回调里转发。
 *
 * | 事件 | 学工表单 | 成绩单 | 评教 | 收银台 |
 * |---|---|---|---|---|
 * | [onPickFiles] 附件上传 | 需要 | — | — | — |
 * | [onGeolocationPrompt] 位置签到 | 需要 | — | — | — |
 * | [onDownload] PDF 另存 | — | 需要 | — | — |
 * | [onPageReady] 加载后注入 | — | — | 需要 | — |
 * | [onExternalScheme] 非 http(s) | — | — | — | 需要 |
 *
 * ## 可空回调 = 本平台 / 本调用方没有这项能力（`design/ios-port.md` §11.2 ④）
 *
 * 这条缝里 `null` 的含义要分两种读法，别混：
 *
 * - **能决定入口渲染的**（页面由我们画）：`null` → 入口整行不渲染。
 * - **决定不了入口的**（网页自己画 `<input type=file>`、自己发定位请求）：`null` →
 *   **平台侧必须给出一个确定结果**，不能让网页挂着：文件选择给「取消」，定位给「拒绝」，
 *   外链给「不拦」（保持现状，WebView 自己报 `ERR_UNKNOWN_URL_SCHEME`）。唯一例外是
 *   [onDownload]：`null` 时两端都保持现状（Android 的 `DownloadListener` 不设、iOS 允许导航），
 *   因为「宿主没装下载处理器」时并没有什么更合理的默认动作。
 *
 * ## 为什么是「普通 class + actual 侧安装一堆 lambda」，而不是 `expect class`
 *
 * `expect class WebViewController` 要求两个平台各写一份构造器与全部成员，Native 侧还要在
 * 两份中间产物之间对齐泛型与默认参数；任何一处对不上都是**链接期**报错，而本机是 Windows、
 * 编不了 Kotlin/Native，等于把唯一可能出错的地方放在唯一验不了的地方。仓库里
 * `ui/common/Haptics.kt` 已经立了先例：可持有一个普通 class，把实现侧能力做成可替换的 `var`，
 * 启动/组合时注入。
 * 这里沿用同一个形状——[impl] 由 actual 的 `WebContentView` 在创建原生视图时安装，
 * 视图销毁时清掉。于是 commonMain 只剩下纯 Kotlin 的转发，元数据编译门禁能完整覆盖它。
 *
 * 注意五个**事件**回调不进 [Impl]：它们由平台侧在原生回调里直接读
 * `controller.onXxx`（与既有的 [onPageFinished] / [onError] 同一个做法）。所以本轮扩缝
 * **没有动 [Impl] 的签名**，两个平台之间少一类要对着改的东西。
 *
 * ## 生命周期（调用方必须照做）
 *
 * 1. `remember { WebViewController() }` 持有，**不要每次重组都 new**：视图是围着句柄建的。
 * 2. 用 [WebContentView] 把它挂到组合里；`url` 变化会重新加载（同 URL 不重复加载）。
 * 3. WebView 离开组合时 actual 侧会销毁原生视图并把 [impl] 置空。**之后**再调 [loadUrl] /
 *    [reload] / [evaluateJavascript] 都是静默 no-op（[evaluateJavascript] 与 [getCookieHeader]
 *    会立刻回一个 null，免得调用方永远等不到回调）。
 * 4. 事件回调建议在 `remember { }` 里一次装好；平台侧每次事件都读当前值，中途换也生效。
 *
 * ## 线程
 *
 * [loadUrl] / [reload] / [evaluateJavascript] 必须在主线程调（两端 WebView 都要求如此）；
 * 本缝不做线程切换。全部回调（含五个事件）都在主线程触发。
 * [getCookieHeader] 的 `onResult` 在**两端都是**「异步或稍后」语义，调用方不要假设同步返回。
 */
class WebViewController {

    /**
     * 页面加载完成，参数是完成时的 URL。
     *
     * **不要用它判「登录成功」**：教务登录成功后是服务端跳转，`onPageFinished` 也会在登录页
     * 本身触发一次。判定交给调用方用 URL 特征（与 `:app` 的 `JwUrls` 同一套口径）。
     */
    var onPageFinished: ((url: String) -> Unit)? = null

    /** 主 frame 加载失败（断网、DNS、证书、HTTP 层错误），参数是给用户看的一句话。 */
    var onError: ((message: String) -> Unit)? = null

    /**
     * 网页里点了 `<input type=file>`：**宿主负责选文件**，选完把字节回给平台。
     *
     * 平台侧只做「原生文件选择器 ↔ WebView」的协议部分（Android 的
     * `ValueCallback<Array<Uri>>` + `onShowFileChooser`、iOS 的
     * `runOpenPanelWithParameters:`），选什么是宿主的事——两端的选文件 UI 差别太大
     * （SAF / `UIDocumentPickerViewController`），收进共享层只会变成两份平台分支。
     *
     * - `accept`：Android 取 `FileChooserParams.acceptTypes`（逗号分隔的 mime 列表）；
     *   **iOS 恒为空串**（`WKOpenPanelParameters` 不暴露 accept 过滤，WebKit 自己按
     *   `<input accept>` 校验）。宿主按自己的选择器能力尽力过滤即可。
     * - `allowMultiple`：`<input type=file multiple>`。
     * - `onResult`：**必须回**（回空表 = 用户取消）。平台侧已经保证只认第一次调用；
     *   宿主自己也要保证不重复回——网页那边的回调只能消费一次。
     *
     * `null` = 本调用方不支持上传：网页的按钮点了没反应（Android 的
     * `onShowFileChooser` 返回 false / iOS 的 `runOpenPanel` 立刻回「取消」）。
     *
     * 字节为什么要带文件名：附件上传的服务器端常常按 filename 判后缀与显示名，
     * 只给字节流等于把上传做废一半。
     */
    var onPickFiles: ((accept: String, allowMultiple: Boolean, onResult: (List<WebPickedFile>) -> Unit) -> Unit)? =
        null

    /**
     * 主 frame 要跳到**非 http(s)** 的地址（`alipays://` / `weixin://` / `tel:` /
     * `intent://` …）：交给宿主决定「用系统打开」还是「忽略」。
     *
     * 返回 `true` = 已接管，平台侧取消这次导航；`false` = 你没管它，平台侧照样取消
     * （网页不会自己去加载 `alipays:`，放行等于让它进错误页）。
     *
     * `null` = 不拦，**保持现状**：Android 由 WebView 自己报 `ERR_UNKNOWN_URL_SCHEME`，
     * iOS 由 WebKit 交给系统（`Info.plist` 没配 `LSApplicationQueriesSchemes` 时打不开）。
     * 判据用 [shouldHandOffToSystem]——两端同一份，别在平台侧另写。
     */
    var onExternalScheme: ((url: String) -> Boolean)? = null

    /**
     * 页面加载完成后要**自动注入**的脚本文本；返回 `null` 或空白 = 不注入。
     *
     * 评教那套要的就是这个：URL 里带 token 的 SPA 页面自己不会去取 token，
     * 得在落地之后注入一段「`$.ajax` 取 token → `location.href` 同窗口跳」的脚本。
     * 手动调 [evaluateJavascript] 的调用方**不要**同时开这个回调，否则同一段跑两遍。
     *
     * 注入点在两端都是主文档 `load` 之后（Android `onPageFinished`、iOS
     * `didFinishNavigation:`）。子 frame 完成不触发。
     */
    var onPageReady: ((url: String) -> String?)? = null

    /**
     * 网页要下载一个文件（盖章成绩单的 PDF、附件、`Content-Disposition: attachment`）。
     *
     * 两端的**时机不同**，`localPath` 因此不同：
     *
     * | | 何时回调 | `localPath` |
     * |---|---|---|
     * | Android | 下载**即将开始**（`DownloadListener.onDownloadStart`） | 恒 `null`：Android 侧没有下载器，宿主自己取 |
     * | iOS | 拦到不可展示的响应（`decidePolicyForNavigationResponse`）后立刻回调 | 恒 `null`：见「iOS 缺什么」 |
     *
     * `suggestedName` 是「服务器 / URL 透出来的文件名」，可能为 `null`
     * （Android 用 `URLUtil.guessFileName`，iOS 用 `NSURLResponse.suggestedFilename`）。
     *
     * `null` = 不拦下载，保持现状（Android 不设 `DownloadListener`：点了没反应；
     * iOS 允许导航：WKWebView 显示不了就留一页空白）。
     */
    var onDownload: ((url: String, suggestedName: String?, mimeType: String?, localPath: String?) -> Unit)? = null

    /**
     * 网页要定位（`navigator.geolocation`）：请假销假的位置签到、门户里的学习通签到。
     *
     * 平台侧把原生的定位授权回调转成 [respond]：
     *
     * - Android：`WebChromeClient.onGeolocationPermissionsShowPrompt`。`respond` 对应
     *   `callback.invoke(origin, granted, retain = false)`——**不**把授权持久化到 WebView。
     * - iOS：**给不出。** `WKUIDelegate` 在 Kotlin 2.1.21 的 `platform.WebKit` 绑定里
     *   **没有**任何定位授权 selector（2026-11-26 CI 实测的候选列表里只有
     *   `requestMediaCapturePermissionForOrigin` 与 `requestDeviceOrientationAndMotionPermissionForOrigin`，
     *   试写 `requestGeolocationPermissionForOrigin` 直接报 `'webView' overrides nothing`）。
     *   所以 `navigator.geolocation` 在 iOS 上一律拿不到坐标，这一栏恒为 `null`。
     *   需要定位的页面（销假签到那类）在 iOS 上得另想办法，别指望这条缝。
     *
     * 宿主拿到 [respond] 后**必须**在拿到结果时调一次（`true` / `false` 都行）；
     * 网页撤销请求（Android `onGeolocationPermissionsHidePrompt`）时平台侧会替宿主补一次
     * `false`，所以宿主不需要额外接「撤销」这条信号。
     *
     * `null` = 本调用方不参与定位授权，平台侧的默认动作：Android 已授予系统定位权限就
     * 直接放行、没有就**立刻拒绝**（比「网页永远等不到坐标」好，页面能自己给出提示）；
     * iOS 恒不回（见上）。
     */
    var onGeolocationPrompt: ((origin: String?, respond: (Boolean) -> Unit) -> Unit)? = null

    /**
     * 平台实现。`null` = 原生视图还没建好或已经销毁。
     *
     * `internal` 是刻意的：这是平台侧与控制器之间的私有协议，调用方只认上面那几个函数。
     */
    internal var impl: Impl? = null

    /** 当前 URL；视图未就绪时为 null（不是空串，调用方要能区分「还没加载」）。 */
    val currentUrl: String?
        get() = impl?.currentUrl?.invoke()

    /** 加载 [url]。视图未就绪时静默丢弃——那时也还没有用户手势能触发它。 */
    fun loadUrl(url: String) {
        impl?.load?.invoke(url)
    }

    /** 重新加载当前页面（教务页的「刷新」按钮）。 */
    fun reload() {
        impl?.reload?.invoke()
    }

    /**
     * 执行一段 JS，把结果回给 [onResult]。
     *
     * 返回值的形状**取决于平台**：Android 的 `evaluateJavascript` 给的是 JSON 字面量
     * （字符串带引号、`null` 就是字符串 `"null"`）；iOS 给的是 JS 值的 `toString`。
     * 教务导入的脚本都自己 `JSON.stringify` 再取，所以这条差异不影响使用；
     * 要写「严格按 JSON 解析结果」的调用方，得自己按平台名与 iOS 侧对齐。
     */
    fun evaluateJavascript(script: String, onResult: (String?) -> Unit = {}) {
        val target = impl ?: return onResult(null)
        target.evaluateJavascript(script, onResult)
    }

    /**
     * 读 [url] 这个域的 Cookie，拼成 HTTP 请求头的值：`"k=v; k2=v2"`，没有 Cookie 时给 null。
     *
     * 用途只有一个：把 WebView 里登出来的会话交给共享层的 Ktor 用（教务的
     * `JSESSIONID` / CAS 的 `TGC` 都在这条路上，签章系统的 `sid` 同理）。
     * **只读**——不往 WebView 写 Cookie：`design/04c-services.md` 已定案
     * 「WebView 的会话只能由 WebView 自己登出来，注入无效」，反过来回灌才是唯一方向。
     */
    fun getCookieHeader(url: String, onResult: (String?) -> Unit) {
        val target = impl ?: return onResult(null)
        target.cookieHeader(url, onResult)
    }

    /**
     * actual 侧安装的实现组。
     *
     * 用一组函数类型而不是接口：接口要求两个平台各写一个 class，而这里根本没有可复用的
     * 状态（状态都在原生视图里），多一层类型只是多一处要跟着改的签名。
     */
    internal class Impl(
        val load: (String) -> Unit,
        val reload: () -> Unit,
        val currentUrl: () -> String?,
        val evaluateJavascript: (String, (String?) -> Unit) -> Unit,
        val cookieHeader: (String, (String?) -> Unit) -> Unit,
    )
}

/**
 * 网页上传控件选到的一个文件：[bytes] 是全部内容，[name] 是建议文件名（含扩展名）。
 *
 * 为什么是一次性把字节读进内存：两端 WebView 的附件控件都要求「给文件」而不是「给路径」，
 * 而 Android 的 SAF 与 iOS 的文件选择器给出的是两种不可互通的东西（`content://` Uri /
 * 文件 `NSURL`）。字节是唯一能过这条缝的货币。
 * 代价是上传大文件（几十 MB 的扫描件）会整份驻留内存——学工表单的附件实测在几 MB 量级，
 * 真要传大文件那天，这条契约得换成「溅出到临时文件 + 只传路径」。
 */
class WebPickedFile(val name: String, val bytes: ByteArray)

/**
 * 把 [controller] 对应的原生 WebView 放进组合。
 *
 * - `url` 首次进入组合时加载，之后**只有值变化**才重新加载（同值不重载，否则每次重组都刷一次
 *   教务页，登录表单会被清空）。
 * - 离开组合时销毁原生视图；[controller] 可以被下一个 [WebContentView] 重新接管。
 * - 尺寸由 [modifier] 决定，两端都要求调用方给出确定尺寸（教务导入是整屏，一般 `fillMaxSize()`）。
 */
@Composable
expect fun WebContentView(controller: WebViewController, url: String, modifier: Modifier = Modifier)
