package edu.jxslu.schedule.ui.common

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.webkit.GeolocationPermissions
import android.webkit.RenderProcessGoneDetail
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import edu.jxslu.schedule.startActivityOutsideApp

/**
 * 学工 / 教务 / 门户三个内置 WebView 窗口的共用能力。
 *
 * 这三件事都属于「WebView 默认不做、但学校页面会用」的桥：
 * - **文件上传**（`<input type=file>`）：需要 `WebChromeClient.onShowFileChooser`；
 * - **网页定位**（`navigator.geolocation`）：需要宿主回填
 *   `onGeolocationPermissionsShowPrompt` 的 callback，且 App 持有运行时权限；
 * - **非 http(s) 链接**（`tel:` / `mailto:` / `weixin://` / `intent://`）：WebView 只会
 *   报 `ERR_UNKNOWN_URL_SCHEME`，必须交给系统。
 *
 * 收银台窗口（`PowerBankPayScreen`）只用到最后一项，其余两项与它无关。
 */

/**
 * 解析 WebView 文件选择器的结果。
 *
 * **不能用** `WebChromeClient.FileChooserParams.parseResult`：Chromium 的实现
 * （`AwContentsClient.parseFileChooserResult`）只读 `Intent.data`，不读 `ClipData`。
 * 表单页的附件控件是 `<input type="file" multiple>`，选择器在这种模式下会把选中的
 * 文件放进 `ClipData`——只选一张也一样（照片选择器、部分文件管理器都如此）。
 * 交给它解析等于把所有选择都当成「取消」，附件位置永远是空的。
 *
 * 返回 null = 用户取消；非 null = 选中的文件（可能多个）。
 */
fun parseWebViewFileChooserResult(resultCode: Int, data: Intent?): Array<Uri>? {
    if (resultCode != Activity.RESULT_OK || data == null) return null
    data.clipData?.let { clip ->
        val uris = (0 until clip.itemCount).mapNotNull { clip.getItemAt(it).uri }
        if (uris.isNotEmpty()) return uris.toTypedArray()
    }
    return data.data?.let { arrayOf(it) }
}

/**
 * 网页定位桥：把 `navigator.geolocation` 的请求接到 App 的运行时权限上。
 *
 * WebView 默认的 `WebChromeClient` 对定位请求**不回填 callback**，页面永远等不到坐标。
 * 这里收着挂起的请求：已有定位权限直接放行；没有就发起一次系统权限申请，结果回来
 * 统一回填（`retain = false`，不把授权持久化到 WebView）。
 *
 * 一个页面可能同时挂多个请求（不同 origin / 连续两次取点），全部收着，漏一个那页就
 * 一直挂着。页面撤销请求时按契约不能再回填（[onHidePrompt]）。
 *
 * 用法：在 WebChromeClient 里转发两个回调；用 [rememberWebViewGeolocationBridge]
 * 创建（它负责挂运行时权限 launcher）。
 */
class WebViewGeolocationBridge(private val context: Context) {

    private val pending = mutableListOf<Pair<String, GeolocationPermissions.Callback>>()

    /** 由 Compose 包装注入：发起系统运行时权限申请。 */
    var launchPermission: () -> Unit = {}

    /**
     * 处理一次网页定位请求。返回 true = 本次真的发起了系统权限申请
     * （调用方据此写「请在系统弹窗里允许」之类的状态条）。
     */
    fun onShowPrompt(origin: String?, callback: GeolocationPermissions.Callback?): Boolean {
        if (origin == null || callback == null) return false
        if (AppPermissions.locationGranted(context)) {
            callback.invoke(origin, true, false)
            return false
        }
        pending += origin to callback
        // 弹框已经在路上：这次请求跟着那一轮结果一起回填，别重复 launch
        // （系统不支持并发弹框）
        if (pending.size > 1) return false
        launchPermission()
        return true
    }

    /** 页面自己撤销了请求（离开页面 / 取消）：按契约不能再回填。 */
    fun onHidePrompt() {
        pending.clear()
    }

    /** 权限申请结果：把所有挂起的请求一起回填。 */
    fun grant(granted: Boolean) {
        pending.forEach { (origin, callback) -> callback.invoke(origin, granted, false) }
        pending.clear()
    }
}

/**
 * 创建 [WebViewGeolocationBridge] 并挂好运行时权限 launcher。
 *
 * [onResult] 收到授权结果（true = 精确或粗略任一已授予），调用方用它写状态条。
 */
@Composable
fun rememberWebViewGeolocationBridge(
    onResult: (granted: Boolean) -> Unit,
): WebViewGeolocationBridge {
    val context = LocalContext.current
    val bridge = remember { WebViewGeolocationBridge(context) }
    // 用 rememberUpdatedState：launcher 只创建一次，但 onResult 每次组合都可能是新 lambda
    val currentOnResult by rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { _ ->
        val granted = AppPermissions.locationGranted(context)
        bridge.grant(granted)
        currentOnResult(granted)
    }
    bridge.launchPermission = { launcher.launch(AppPermissions.location.toTypedArray()) }
    return bridge
}

/**
 * 非 http(s) 链接该不该交给系统。
 *
 * 排除 WebView 自己能处理 / 不能外发的路径（`about:` / `javascript:` / `data:` /
 * `blob:` / `file:`），其余（`tel:` / `mailto:` / `sms:` / `weixin://` /
 * `alipays://` / `intent://` / `market://` …）都交给系统解析——留在 WebView 里只会
 * 显示 `ERR_UNKNOWN_URL_SCHEME` 错误页，等于把入口做死。
 */
fun shouldHandOffToSystem(url: String?): Boolean {
    val u = url.orEmpty().trim()
    if (u.isEmpty()) return false
    val lower = u.lowercase()
    if (lower.startsWith("http://") || lower.startsWith("https://")) return false
    return !lower.startsWith("about:") &&
        !lower.startsWith("javascript:") &&
        !lower.startsWith("data:") &&
        !lower.startsWith("blob:") &&
        !lower.startsWith("file:")
}

/**
 * 把非 http(s) 链接交给系统（与收银台窗口同一实现）。
 *
 * 返回 true = 已接管（无论目标 App 能不能拉起）：让 WebView 别自己去加载它。
 * 拉不起来时静默——此时没有补救手段，再弹一句提示只是噪音。
 */
fun launchWebViewExternal(context: Context, target: String): Boolean {
    val intent = when {
        target.startsWith("intent://") ->
            runCatching { Intent.parseUri(target, Intent.URI_INTENT_SCHEME) }.getOrNull()

        else -> runCatching { Intent(Intent.ACTION_VIEW, Uri.parse(target)) }.getOrNull()
    } ?: return true
    runCatching { context.startActivityOutsideApp(intent) }
    return true
}

/**
 * WebView 渲染进程消失（内存压力 / 渲染器崩溃）时的统一处理。
 *
 * **必须返回 true**：SDK 文档写明，宿主不处理时渲染进程崩溃会带走整个 App、被系统
 * 杀掉时 App 会被一起杀（`WebViewClient.onRenderProcessGone`）。返回 true 之后这个
 * WebView 已经不可用，最省事且状态最干净的恢复方式是重建窗口：`Activity.recreate()`
 * 会销毁 Compose 树（`onRelease` 里 destroy WebView）并按首屏地址重新建一个。
 *
 * 用 `post` 推迟到回调返回之后再做，避免在 WebView 自己的回调栈里销毁 Activity。
 * 重建后回到窗口的入口页（统一认证 / 支付页），Cookie 与会话都在，用户不必重登。
 */
fun handleWebViewRendererGone(
    context: Context,
    tag: String,
    detail: RenderProcessGoneDetail?,
): Boolean {
    Log.w(tag, "webview renderer gone: didCrash=${detail?.didCrash()}")
    val activity = context as? Activity
    if (activity != null && !activity.isFinishing && !activity.isDestroyed) {
        activity.window?.decorView?.post {
            if (!activity.isFinishing && !activity.isDestroyed) {
                runCatching { activity.recreate() }
            }
        }
    }
    return true
}
