package edu.jxslu.schedule.ui.web

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
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

/**
 * [WebContentView] 的 Android 实现：`AndroidView` + 系统 `WebView`（iOS 移植，2026-11-24）。
 *
 * 这条 actual 是 `:app` 里 `JwImportScreen` 那套 WebView 配置的**最小可复用子集**——
 * 只保留教务导入真要用的：脚本、DOM 存储、桌面视口、混合内容、cookie 读取。
 * 现有 20 处内联 `AndroidView` 一行没动，也**不要**拿这个去替换它们：它们各自有不同的
 * 上传/定位/下载/进度条需求，那些能力这里刻意没有。
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
            override fun onPageFinished(view: WebView, url: String) {
                controller.onPageFinished?.invoke(url)
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                // 子资源（图片、脚本）失败不该打断登录流程：教务页常年有一两个 404 的图标，
                // 全报给用户等于每次都弹一条假故障
                if (!request.isForMainFrame) return
                controller.onError?.invoke(error.description?.toString().orEmpty().ifEmpty { "页面加载失败" })
            }
        }

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
        webView = null
        loadedUrl = null
        if (controller.impl === installed) controller.impl = null
        installed = null
    }
}
