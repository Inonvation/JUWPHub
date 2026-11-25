package edu.jxslu.schedule.ui.web

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.GeolocationPermissions
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import java.io.File

/**
 * [WebContentView] 的 Android 实现：`AndroidView` + 系统 `WebView`（iOS 移植，2026-11-24）。
 *
 * 这条 actual 是 `:app` 里 `JwImportScreen` 那套 WebView 配置的**最小可复用子集**——
 * 脚本、DOM 存储、桌面视口、混合内容、cookie 读取，加上 2026-11-25 扩缝的五件：
 * 附件上传、网页定位、非 http(s) 外链、下载、加载后注入。
 *
 * 现有 20 处内联 `AndroidView`（含 `JwImportScreen` 2,049 行那一张）**一行没动**，也
 * **不要**拿这个去替换它们：它们各自还压着进度条、`onRenderProcessGone` 重建、SSL 错误
 * 放行、桌面视口自愈这些本缝不承担的东西。本缝只负责「把能力交出去」。
 *
 * ## 三处不是「随便配配」的取值
 *
 * 1. `useWideViewPort` + `loadWithOverviewMode` 都是 `true`：教务登录页（`/jsxsd/`、`Logon.do`）
 *    **没有 `<meta viewport>`**，是 400–480px 的桌面布局。关掉这两项会把桌面布局硬塞进屏幕
 *    宽度，结果是错位、被裁，看着像「页面没渲染出来」——2026-10-08 真机踩过，见
 *    `app/.../ui/jwvw/JwImportScreen.kt` 的 `configureForJw`。
 * 2. `mixedContentMode = MIXED_CONTENT_COMPATIBILITY_MODE`：教务页有 http 图片/样式混在
 *    https 页面里，兼容模式放行这些被动资源，但**不放行** http 的脚本与 iframe。
 *    如果真机发现某块内容空着、日志里是 mixed content 被拦，再改成 `ALWAYS_ALLOW`
 *    （`JwImportScreen` 当前用的就是后者）——这是个一行改动，别提前放宽。
 * 3. `LAYER_TYPE_SOFTWARE`：MIUI/HyperOS 上教务课表页会出现「DOM 正常、`evaluateJavascript`
 *    能跑，但整面白屏」——死的是 GPU 合成层。软件渲染绕开它，是确定性的兜底；教务页是简单
 *    表格，CPU 绘制够用。这一条与 `JwImportScreen.configureForJw` 同源，搬页面时一起搬过来。
 *
 * ## 五个事件接到哪（全部读 `controller.onXxx` 的当前值）
 *
 * | 事件 | Android 侧入口 | `null` 时 |
 * |---|---|---|
 * | [WebViewController.onPickFiles] | `WebChromeClient.onShowFileChooser` | 返回 false，网页的上传按钮点了没反应（与今天一致） |
 * | [WebViewController.onGeolocationPrompt] | `onGeolocationPermissionsShowPrompt` / `…HidePrompt` | 已授予系统定位权限就放行，否则**立刻拒绝**（不让网页挂着） |
 * | [WebViewController.onExternalScheme] | `WebViewClient.shouldOverrideUrlLoading`（只主 frame） | 返回 false，交给 WebView 自己报 `ERR_UNKNOWN_URL_SCHEME` |
 * | [WebViewController.onDownload] | `WebView.setDownloadListener` | 什么都不做（今天就是这样，点了没反应） |
 * | [WebViewController.onPageReady] | `onPageFinished` 之后 `evaluateJavascript` | 不注入 |
 *
 * ## 附件上传：字节为什么要落成 `cacheDir` 里的文件
 *
 * WebView 的 `onReceiveValue` 只吃 `Uri`，而契约给的是字节（见 [WebPickedFile]）。
 * 落成 `cacheDir/webview_upload/<名字>.ext` 再 `Uri.fromFile`：
 * - 名字必须留（服务器按 filename 判后缀），所以不能拿随机串；
 * - 不能落外部存储 / 不进 `MediaStore`：这是网页临时附件，用户不该在相册里看到它；
 * - 目录在 [AndroidWebHost.detach] 时整个清掉，不留在用户缓存里越积越多。
 *
 * `file://` 这种 `Uri` 交给 WebView 上传**本机没在真机上验过**（`:app` 现役那几张页面
 * 走的是 `FileChooserParams.createIntent()` 直接让系统选、拿到的是 `content://`）。
 * 若真机上上传报「无法读取文件」，把 `Uri.fromFile` 换成 `FileProvider.getUriForFile`
 * （要新增一个 provider 声明，`androidx.core.content.FileProvider` 已在依赖里）。
 *
 * ## 销毁
 *
 * `WebView` 必须先脱离父容器再 `destroy()`（否则会留在 `AndroidView` 的容器里泄漏），
 * 并且要把 [WebViewController.impl] 清空——否则控制器还握着已销毁 WebView 的 lambda，
 * 之后任何 `loadUrl` 都会往一个 destroyed WebView 上打。
 */
@Composable
actual fun WebContentView(controller: WebViewController, url: String, modifier: Modifier) {
    val host = remember { AndroidWebHost() }
    AndroidView(
        factory = { context -> host.attach(context, controller) },
        modifier = modifier,
        // update 在视图创建完成时也会跑一次，所以「首次加载」不需要额外的 LaunchedEffect——
        // 那样反而会和 factory 抢时序（视图可能还没建好就被调用）。
        update = { view -> host.loadIfChanged(view, url) },
    )
    DisposableEffect(controller) {
        onDispose { host.detach(controller) }
    }
}

/** 原生视图与它的持有状态；只在 [WebContentView] 的组合范围内存在。 */
private class AndroidWebHost {

    private var webView: WebView? = null
    private var loadedUrl: String? = null
    private var installed: WebViewController.Impl? = null

    /** 网页挂着的定位请求。页面撤销（`…HidePrompt`）时要替宿主补一次 `false`，见契约。 */
    private val geolocations = mutableListOf<GeolocationRequest>()

    /** 建 WebView 并把 [controller] 接到它上面。仅在主线程调用（`AndroidView` 的 factory 保证）。 */
    @SuppressLint("SetJavaScriptEnabled")
    fun attach(context: Context, controller: WebViewController): WebView {
        val view = WebView(context)
        // 见文件头第 3 条：MIUI 上教务页会白屏，软件渲染绕开 GPU 合成层
        view.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        view.setBackgroundColor(Color.WHITE)
        view.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            // 见文件头第 1 条：教务页是桌面布局，靠这两项缩放才看得全
            useWideViewPort = true
            loadWithOverviewMode = true
            // 见文件头第 2 条
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        }
        view.webViewClient = object : WebViewClient() {
            /**
             * 外链交给宿主（收银台的 `alipays://`、门户里的 `tel:` / `weixin://`）。
             *
             * 只判主 frame：子 frame 里的广告位跳到 `market://` 不该把 App 顶出去。
             * 判据用共享层的 [shouldHandOffToSystem]，与 `:app` 的几个窗口同一份。
             */
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (!request.isForMainFrame) return false
                val target = request.url?.toString().orEmpty()
                val handler = controller.onExternalScheme ?: return false
                if (!shouldHandOffToSystem(target)) return false
                handler(target)
                // 无论宿主说管没管，都不许 WebView 自己去加载它——放行等于进错误页
                return true
            }

            override fun onPageFinished(view: WebView, url: String) {
                controller.onPageFinished?.invoke(url)
                // 评教那类页面：落地后注入「取 token 再跳」的脚本。空白脚本不注入，
                // 免得给每个页面白跑一次 evaluateJavascript。
                val script = controller.onPageReady?.invoke(url)
                if (!script.isNullOrBlank()) view.evaluateJavascript(script, null)
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                // 子资源（图片、脚本）失败不该打断登录流程：教务页常年有一两个 404 的图标，
                // 全报给用户等于每次都弹一条假故障
                if (!request.isForMainFrame) return
                controller.onError?.invoke(error.description?.toString().orEmpty().ifEmpty { "页面加载失败" })
            }
        }

        view.webChromeClient = object : WebChromeClient() {
            /**
             * 网页的 `<input type=file>`：把「选文件」这件事交给宿主，等它回字节。
             *
             * 与 `:app` 的 `JwImportScreen` 同一个坑，但方向不同：那边自己拉系统选择器，
             * 因此必须自己读 `ClipData`（`FileChooserParams.parseResult` 会丢多选，
             * 见 `ui/common/WebViewSupport.kt`）；这里不碰 Intent，选什么是宿主的事。
             */
            override fun onShowFileChooser(
                view: WebView,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?,
            ): Boolean {
                val picker = controller.onPickFiles
                if (picker == null || filePathCallback == null || fileChooserParams == null) return false
                val accept = fileChooserParams.acceptTypes
                    .orEmpty()
                    .filter { it.isNotBlank() }
                    .joinToString(",")
                val multiple = fileChooserParams.mode == FileChooserParams.MODE_OPEN_MULTIPLE
                val context = view.context
                picker.invoke(accept, multiple) { files ->
                    // 宿主的选文件是异步的（SAF 回调可能不在主线程）：喂 WebView 必须回主线程
                    onMainThread {
                        filePathCallback.onReceiveValue(stageUploads(context, files))
                    }
                }
                return true
            }

            /**
             * 网页定位（请假销假的位置签到、门户里的学习通签到）。
             *
             * 没装宿主回调时的默认动作**不是**「不回填」：那样页面会一直等坐标，
             * 用户只看到「拿不到定位」且没有任何出口。已授予系统定位权限就直接放行，
             * 没有就立刻拒绝——页面能自己给出「请允许定位」的提示。
             * 要真的弹系统授权框，宿主得自己挂 `onGeolocationPrompt`（`:app` 的
             * `WebViewGeolocationBridge` 就是干这个的）。
             */
            override fun onGeolocationPermissionsShowPrompt(
                origin: String?,
                callback: GeolocationPermissions.Callback?,
            ) {
                if (origin == null || callback == null) return
                val handler = controller.onGeolocationPrompt
                if (handler == null) {
                    callback.invoke(origin, locationGranted(view.context), false)
                    return
                }
                val request = GeolocationRequest(origin, callback)
                geolocations += request
                handler(origin) { granted ->
                    request.respond(granted)
                    geolocations.remove(request)
                }
            }

            /** 页面自己撤销了定位请求：按契约不能再回填，只能替宿主补一次 `false` 收尾。 */
            override fun onGeolocationPermissionsHidePrompt() {
                geolocations.forEach { it.respond(false) }
                geolocations.clear()
            }
        }

        // 下载（成绩单 PDF、`Content-Disposition: attachment` 的附件）。宿主没装就什么都不做。
        view.setDownloadListener(
            DownloadListener { url, _, contentDisposition, mimeType, _ ->
                val handler = controller.onDownload ?: return@DownloadListener
                val name = URLUtil.guessFileName(url, contentDisposition, mimeType)
                handler(url, name.takeIf { it.isNotBlank() }, mimeType?.takeIf { it.isNotBlank() }, null)
            },
        )

        val impl = WebViewController.Impl(
            load = { target -> view.loadUrl(target) },
            reload = { view.reload() },
            currentUrl = { view.url },
            evaluateJavascript = { script, onResult ->
                view.evaluateJavascript(script) { value -> onResult(value) }
            },
            // 只读：CookieManager 的全局策略一律不动（不 setAcceptCookie、不 setAcceptThirdPartyCookies），
            // 这是别的 WebView 窗口共用的一份状态，改它会牵连带 Login 的既有页面
            cookieHeader = { target, onResult -> onResult(CookieManager.getInstance().getCookie(target)) },
        )
        webView = view
        installed = impl
        controller.impl = impl
        return view
    }

    /** [url] 变了才重新加载。同值不重载：每次重组都刷一次会把用户正在填的登录表单清空。 */
    fun loadIfChanged(view: WebView, url: String) {
        if (loadedUrl == url) return
        loadedUrl = url
        view.loadUrl(url)
    }

    /** 脱离父容器 → 停加载 → 销毁 → 断开控制器。顺序不能换，见文件头「销毁」。 */
    fun detach(controller: WebViewController) {
        val view = webView ?: return
        (view.parent as? ViewGroup)?.removeView(view)
        view.stopLoading()
        view.destroy()
        geolocations.clear()
        webView = null
        loadedUrl = null
        // 网页临时附件不留在用户缓存里：下一次 attach 会重建目录
        runCatching { uploadDir(view.context).deleteRecursively() }
        if (controller.impl === installed) controller.impl = null
        installed = null
    }
}

/**
 * 网页挂着的一次定位请求。`respond` 只认第一次：宿主重复回、或页面撤销后又回，
 * 都不能再 `invoke` 原生 callback（WebView 会抛「callback already invoked」）。
 */
private class GeolocationRequest(
    private val origin: String,
    private val callback: GeolocationPermissions.Callback,
) {
    private var consumed = false

    fun respond(granted: Boolean) {
        if (consumed) return
        consumed = true
        // retain = false：不把这次授权持久化到 WebView（与 `:app` 的桥同一口径）
        callback.invoke(origin, granted, false)
    }
}

/** 定位是否可用：精确或粗略**任一**授权即可（与 `:app` 的 `AppPermissions.locationGranted` 同口径）。 */
private fun locationGranted(context: Context): Boolean =
    context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

/** 宿主的选文件回调可能在任意线程：喂 WebView 前统一切回主线程。 */
private fun onMainThread(block: () -> Unit) {
    if (Looper.myLooper() == Looper.getMainLooper()) block() else Handler(Looper.getMainLooper()).post(block)
}

/** 网页附件落盘的目录（见文件头「附件上传」）。 */
private fun uploadDir(context: Context): File = File(context.cacheDir, "webview_upload")

/**
 * 把宿主选中的字节落成 WebView 认识的 `Uri` 列表。
 *
 * 返回 `null` = 用户取消（`onReceiveValue(null)` 是 WebView 约定的「取消」语义）。
 */
private fun stageUploads(context: Context, files: List<WebPickedFile>): Array<Uri>? {
    if (files.isEmpty()) return null
    val dir = uploadDir(context).apply { mkdirs() }
    val uris = files.mapIndexedNotNull { index, file ->
        val target = File(dir, safeUploadName(index, file.name))
        runCatching { target.writeBytes(file.bytes) }.map { Uri.fromFile(target) }.getOrNull()
    }
    return if (uris.isEmpty()) null else uris.toTypedArray()
}

/**
 * 落地文件名：只保留 `[文件名].[扩展名]`，加上序号防重名。
 *
 * 服务器的附件校验常按后缀判类型，所以**不能**换成随机名；但也**不能**直接用网页给的名字：
 * 里面可能带 `/`、`..`，或者两个控件选到同名文件。
 */
private fun safeUploadName(index: Int, raw: String): String {
    val base = raw.substringAfterLast('/').substringAfterLast('\\').trim()
        .replace(Regex("[^A-Za-z0-9._\\-\\u4e00-\\u9fa5]"), "_")
        .take(60)
    val cleaned = base.trimStart('.').ifEmpty { "upload" }
    return "$index-$cleaned"
}
