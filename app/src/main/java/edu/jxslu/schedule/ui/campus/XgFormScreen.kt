package edu.jxslu.schedule.ui.campus

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.util.Log
import android.webkit.GeolocationPermissions
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import edu.jxslu.schedule.Graph
import edu.jxslu.schedule.data.jw.JwAutoLogin
import edu.jxslu.schedule.data.jw.JwUrls
import edu.jxslu.schedule.data.jw.unwrapJsString
import edu.jxslu.schedule.data.session.CasEnsureResult
import edu.jxslu.schedule.data.session.SessionStatus
import edu.jxslu.schedule.data.xg.XgForm
import edu.jxslu.schedule.data.xg.XgUrls
import edu.jxslu.schedule.ui.common.CasLoginDialog
import edu.jxslu.schedule.ui.common.handleWebViewRendererGone
import edu.jxslu.schedule.ui.common.launchWebViewExternal
import edu.jxslu.schedule.ui.common.parseWebViewFileChooserResult
import edu.jxslu.schedule.ui.common.rememberWebViewGeolocationBridge
import edu.jxslu.schedule.ui.common.shouldHandOffToSystem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "XgForm"

/**
 * 首页壳卡住的判定延时：`onPageFinished` 时 Vue 早该挂载完（占位会被替换），
 * 再等 2.5s 只是给慢机留余量；到时占位还在就把状态条换成「卡住了？」+ 一键清理。
 */
private const val HOME_SHELL_PROBE_DELAY_MS = 2_500L

/**
 * 学工首页壳的「正在加载中…」占位是否还在（`1` = Vue 没挂载上）。
 *
 * 首页 index.html 自带的 `#app .loading-box` 会在 Vue `mount()` 时被整块替换；
 * 它还在就说明 index.html 引用的静态资源没加载成功（旧缓存里的 JS 哈希 / 坏条目）。
 */
private const val HOME_SHELL_STUCK_JS =
    """(function(){try{var a=document.getElementById('app');return a&&a.querySelector('.loading-box')?'1':'0';}catch(e){return '0';}})()"""

/**
 * 学工表单窗口（DESIGN §3.15 / §4.26）：把学工系统的官方移动页装进一个窗口。
 *
 * 报修、请假共用这一页，差别只有 [form] 里的表单标识。填表、传附件、提交、查流程
 * 都由官方页面自己完成，App 不代劳：申请请求里带着一组服务端下发的动态值
 * （`pageEnc` / `traceId` / `nodeUniqueId`），复刻一遍的代价与失效风险都远大于收益。
 *
 * 起点是统一认证入口而不是表单页本身，顺序不能反：表单页匿名可访问，它的登录引导
 * 指向超星 passport（手机号 + 学习通密码），先开表单会把人带到一套他从没用过的账号上。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun XgFormScreen(form: XgForm, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // 会话层单例（DESIGN §4.27）：打开前准备好 CAS 会话，学工那一跳就是免密的
    val cas = remember { Graph.casSession(context) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }
    var statusNote by remember { mutableStateOf(XgUrls.statusHint(null, form.title)) }
    // 落在统一认证登录页时用保存的凭证自动登一次（DESIGN §4.27「落登录页自动填表」）。
    // 失败（结构变了 / 有验证码）就交给用户手登，反复试只会撞风控。
    var savedCas by remember { mutableStateOf(Graph.credentialVault(context).readCas()) }
    var autoLoginTried by remember { mutableStateOf(false) }
    // 前置登录（DESIGN §4.27，2026-09-28）：本地没有凭据时进窗先问一次，
    // 保存验证通过后 WebView 落 CAS 页由自动填表接管（与教务导入同一套闭环）
    var showCasLoginDialog by remember { mutableStateOf(false) }
    var casLoginDeclined by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (savedCas == null && !casLoginDeclined) showCasLoginDialog = true
    }
    // 直达表单页只尝试一次：落到学工首页之后还要再跳一层。只在「第一次落到学工域」时跳，
    // 之后用户点右上角回首页不会再被弹进表单。
    var directAttempted by remember { mutableStateOf(false) }

    // 学工首页壳的坏缓存自愈（2026-11-08 用户报障）：点右上角小房子回首页后停在
    // index.html 自带的「正在加载中…」，Vue 根本没挂载——缓存里 index.html 引用的
    // 静态资源没加载成功，普通 reload 还会命中同一份坏缓存，只有清缓存能恢复（用户实测）。
    // - [homeNoCacheLoad]：本次首页加载绕过缓存重取，onPageFinished 里还原，别让整窗都绕缓存；
    // - [showCacheHint]：探测到壳还卡着时，在状态条里显示「卡住了？…」+ 一键清理按钮，
    //   由用户点。

    var homeNoCacheLoad by remember { mutableStateOf(false) }
    var showCacheHint by remember { mutableStateOf(false) }
    // 导航代次：每次主 frame 开始导航 +1。卡住探测在 2.5s 后要复核这个值——
    // 期间用户重新加载了首页时，旧探测不能再拿新页面的 DOM 下结论
    // （否则新页面还在挂载的那一两秒会被误报「卡住了」）。
    var navToken by remember { mutableIntStateOf(0) }

    // 网页里点「上传附件」时，WebView 会把文件选择回调交出来，等系统选择器的结果。
    // 期间必须一直握着它：提前丢掉页面会永远停在「上传中」；而结果回来后又必须立刻
    // 清掉——同一个 callback 消费两次会抛 IllegalStateException。
    var fileCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val callback = fileCallback
        fileCallback = null
        callback?.onReceiveValue(parseWebViewFileChooserResult(result.resultCode, result.data))
    }

    // 请假 → 销假这步要网页定位（页面自己调 `navigator.geolocation`）。桥的实现在
    // ui/common/WebViewSupport.kt，与门户窗口共用：默认 WebChromeClient 不回填定位
    // 回调，页面永远等不到坐标。
    val geoBridge = rememberWebViewGeolocationBridge { granted ->
        statusNote = if (granted) {
            "已允许定位"
        } else {
            "定位权限被拒绝：网页需要位置信息，可在系统设置里允许后重试"
        }
    }

    // 主 frame 加载失败时的出口：Chromium 的错误页上面再垫一条「重试」，用户不用退出
    // 重进窗口（顶栏另有一枚「刷新」）。
    //
    // 不能只靠 onPageStarted 清错误：实测回调顺序可能是「错误 → onPageStarted →
    // onPageFinished」（见 JwImportScreen 同一处注释），在 start 里清会把刚报出来的错
    // 洗掉。所以按 URL 记住哪一次失败，onPageFinished 匹配到它时保留错误条。
    var loadError by remember { mutableStateOf<String?>(null) }
    var loadErrorUrl by remember { mutableStateOf<String?>(null) }

    // 与左上角箭头同语义：网页能后退就先回退，退不动了才关窗口。表单流程要连着走
    // 「首页 → 服务 → 列表 → 表单」好几层，直接关窗口会让用户以为自己的操作丢了。
    BackHandler(enabled = canGoBack) { webView?.goBack() }

    /**
     * 首页壳卡在「正在加载中…」时给出**手动出口**：等 [HOME_SHELL_PROBE_DELAY_MS]
     * 再探一次占位，还在就把状态条换成「卡住了？清理网页缓存重新加载试试」+ 一键按钮。
     *
     * 不在探测到卡住时自动清缓存重载：页面会在用户眼皮底下自己刷新一下，容易让人以为
     * 操作丢了；交给用户点（2026-11-08 用户口径）。
     *
     * 探测前复核 `wv.url` 仍是首页壳——用户可能已经点进表单，这时换提示会误导。
     */
    fun checkHomeShellStuck(wv: WebView) {
        val token = navToken
        scope.launch {
            delay(HOME_SHELL_PROBE_DELAY_MS)
            if (token != navToken || webView !== wv || !XgUrls.isHomeShell(wv.url)) return@launch
            wv.evaluateJavascript(HOME_SHELL_STUCK_JS) { raw ->
                if (token != navToken || !XgUrls.isHomeShell(wv.url)) return@evaluateJavascript
                val stuck = raw?.trim()?.removeSurrounding("\"") == "1"
                if (stuck) Log.w(TAG, "xg home shell stuck at loading; show cache-clear hint")
                // 卡住 → 显示提示；已经恢复 → 收掉上一次的提示
                showCacheHint = stuck
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(form.title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    // 刷新：页面断网/服务端 5xx 时的第一出口。之前只有「回首页」，
                    // 出错就只剩退出重进一条路（2026-11-08 审查）。
                    IconButton(onClick = {
                        loadError = null
                        loadErrorUrl = null
                        webView?.reload()
                    }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                    }
                    // 直达就没有网页历史可退。这张表单若被管理员改过（页面会提示
                    // 「表单信息不存在」），或者用户要去看已提交的流程，得有路回学工首页。
                    IconButton(onClick = {
                        val wv = webView ?: return@IconButton
                        // 首页壳的静态资源一旦被坏缓存拖住，普通 loadUrl 会继续命中同一份
                        // 缓存、永远停在「正在加载中…」。这一次重取绕过缓存（不删 cookie /
                        // localStorage，用户不用重新登录）；若壳仍然没挂载，状态条会出现
                        // 「卡住了？」+ 一键清理（[checkHomeShellStuck]）。
                        loadError = null
                        loadErrorUrl = null
                        showCacheHint = false
                        homeNoCacheLoad = true
                        wv.settings.cacheMode = WebSettings.LOAD_NO_CACHE
                        wv.loadUrl(XgUrls.HOME)
                    }) {
                        Icon(Icons.Filled.Home, contentDescription = "学工首页")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (progress in 1..99) {
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            // 状态条固定 48dp 高：卡住时多出一枚「清理并刷新」按钮，高度不变 →
            // WebView 不会被重新测量（MIUI 上 resize 可能整面刷白，见下面的 overlay 注释）。
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (showCacheHint) {
                        "卡住了？清理网页缓存重新加载试试"
                    } else {
                        statusNote
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (showCacheHint) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.weight(1f),
                )
                if (showCacheHint) {
                    TextButton(onClick = {
                        val wv = webView ?: return@TextButton
                        // 用户明确要求「清缓存」：清整库 HTTP 资源缓存（HTML + JS），
                        // 再用 no-cache 重取首页。**不动 cookie / localStorage**，
                        // 不用重新登录；整库清只影响各窗口下次重新下载资源。
                        showCacheHint = false
                        loadError = null
                        loadErrorUrl = null
                        homeNoCacheLoad = true
                        wv.settings.cacheMode = WebSettings.LOAD_NO_CACHE
                        wv.clearCache(true)
                        wv.loadUrl(XgUrls.HOME)
                    }) { Text("清理并刷新") }
                }
            }
            // 错误条必须是 **overlay**，不能放进 Column 当兄弟节点：它出现/消失会改变
            // WebView 的高度，MIUI 上硬件加速 surface 重建会把整面刷白（教务导入页的
            // 进度条踩过同一条）。overlay 不占布局，WebView 尺寸恒定。
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                AndroidView(
                    // weight 而不是 fillMaxSize：Column 里 fillMaxSize 请求的是父约束的整个高度，
                    // 会把上面那条状态文案挤出可视区（教务导入页也是这么处理的）。
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        // 套一层 FrameLayout 作壳：Compose 重排时摘挂的是壳，WebView 本体
                    // 不再被直接摘挂/重挂（与教务导入同一处理，见 JwImportScreen）。
                    FrameLayout(ctx).apply {
                        val wv = WebView(ctx).apply {
                            configureForXg(this)
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                ): Boolean {
                                    // 学校页面里的电话 / 邮件 / 拉起 App 的链接：WebView
                                    // 自己只会显示 ERR_UNKNOWN_URL_SCHEME，交给系统解析。
                                    // 只拦主 frame，iframe 里的非 http 链接不代发。
                                    val target = request?.url?.toString().orEmpty()
                                    if (request?.isForMainFrame == true &&
                                        shouldHandOffToSystem(target)
                                    ) {
                                        return launchWebViewExternal(context, target)
                                    }
                                    return false
                                }

                                override fun onPageStarted(
                                    view: WebView?,
                                    url: String?,
                                    favicon: Bitmap?,
                                ) {
                                    progress = 0
                                    canGoBack = view?.canGoBack() ?: false
                                    statusNote = XgUrls.statusHint(url, form.title)
                                    // 新导航作废旧探测（见 navToken 注释），并收掉上一轮的提示；
                                    // 若新页真的还卡着，onPageFinished 的探测会再把它亮起来。
                                    navToken++
                                    showCacheHint = false
                                    // **不要**在这里清 loadError：错误回调可能先于它到达
                                    // （见 loadError 旁的注释）。清理由 onPageFinished 按 URL 判定。
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    canGoBack = view?.canGoBack() ?: false
                                    statusNote = XgUrls.statusHint(url, form.title)
                                    // 失败页的 onPageFinished 也会到：匹配到失败 URL 时保留错误条，
                                    // 否则这一页确实加载成功了，把上一轮的错误收掉。
                                    if (loadErrorUrl != null && loadErrorUrl == url) {
                                        loadErrorUrl = null
                                    } else {
                                        loadErrorUrl = null
                                        loadError = null
                                    }
                                    // 首页的 LOAD_NO_CACHE 只用这一次：无论这一页成没成功都还原，
                                    // 别让整个窗口（表单页 / 后续翻页）都绕过缓存。
                                    if (homeNoCacheLoad) {
                                        homeNoCacheLoad = false
                                        view?.settings?.cacheMode = WebSettings.LOAD_DEFAULT
                                    }
                                    // 学工首页壳：查一下是不是卡在 index.html 的「正在加载中…」
                                    // （坏缓存导致 Vue 没挂载，用户实测清缓存能恢复）
                                    if (view != null && XgUrls.isHomeShell(url)) {
                                        checkHomeShellStuck(view)
                                    }
                                    // 停在统一认证登录页 → 用保存的凭证自动登一次。
                                    // 走「WebView 自己填表提交」而不是注入 cookie：注入的
                                    // cookie 在真机上不被采用（属性缺 SameSite，跨站跳转不带，
                                    // DESIGN §4.27），而填表提交的 cookie 由 CAS 亲自下发。
                                    if (!autoLoginTried && XgUrls.isCasHost(url)) {
                                        savedCas?.let { cred ->
                                            // 进程级节流（DESIGN §4.27）：页面级标记挡不住重开窗口。
                                            if (!SessionStatus.tryAcquireAutoLogin(System.currentTimeMillis())) {
                                                return@let
                                            }
                                            autoLoginTried = true
                                            view?.evaluateJavascript(
                                                JwAutoLogin.fillJs(cred.username, cred.password),
                                            ) { raw ->
                                                val r = unwrapJsString(raw)
                                                Log.d(TAG, "auto-login on CAS page: $r")
                                                if (r?.startsWith(JwAutoLogin.OK_PREFIX) != true) {
                                                    // 没找到表单不算一次登录尝试：退回共享节流，
                                                    // 别把别的窗口（教务网页核对）也压 5 分钟（2026-10-21）
                                                    SessionStatus.releaseAutoLoginAttempt()
                                                    statusNote = "自动登录没走通，请在下方页面手动登录一次"
                                                }
                                            }
                                        }
                                    }
                                    // 报修单/请假单本身匿名可访问，它的登录引导指向超星 passport，
                                    // 不是学校统一认证。所以顺序必须是：先走 /sfrz/ 让学工把
                                    // office 的会话 cookie 写好，再进表单页。
                                    if (!directAttempted && XgUrls.shouldEnterForm(url)) {
                                        directAttempted = true
                                        view?.loadUrl(form.applyUrl)
                                    }
                                }

                                override fun onReceivedSslError(
                                    view: WebView?,
                                    handler: SslErrorHandler?,
                                    error: SslError?,
                                ) {
                                    // 只放行学校域的证书错误（白名单唯一实现在
                                    // JwUrls.TRUSTED_SSL_HOSTS），其余一律取消。
                                    // 无条件 proceed 等于关掉整个 WebView 的传输层校验，
                                    // 而统一认证的登录表单就在这个 WebView 里。
                                    val host = error?.url?.let { JwUrls.hostOf(it) }
                                    if (host != null && host in JwUrls.TRUSTED_SSL_HOSTS) {
                                        handler?.proceed()
                                        statusNote = "已放行学校 HTTPS 证书"
                                    } else {
                                        handler?.cancel()
                                        Log.w(TAG, "ssl error cancelled: host=$host url=${error?.url}")
                                        loadErrorUrl = error?.url
                                        loadError = "证书校验失败，已阻止访问（$host）"
                                    }
                                }

                                override fun onReceivedError(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                    error: WebResourceError?,
                                ) {
                                    if (request?.isForMainFrame != true) return
                                    val desc = error?.description?.toString().orEmpty()
                                    Log.w(TAG, "onReceivedError ${error?.errorCode} $desc ${request.url}")
                                    loadErrorUrl = request.url.toString()
                                    loadError = "打不开学工页面：${desc.ifBlank { "网络错误" }}"
                                    statusNote = loadError!!
                                }

                                override fun onReceivedHttpError(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                    errorResponse: WebResourceResponse?,
                                ) {
                                    if (request?.isForMainFrame != true) return
                                    val code = errorResponse?.statusCode ?: return
                                    if (code < 400) return
                                    Log.w(TAG, "onReceivedHttpError $code ${request.url}")
                                    loadErrorUrl = request.url.toString()
                                    loadError = "学工返回 HTTP $code"
                                    statusNote = loadError!!
                                }

                                /**
                                 * 渲染进程被系统回收 / 崩溃：不处理会带走整个 App。
                                 * 统一走 [handleWebViewRendererGone]（返回 true + 重建窗口）。
                                 */
                                override fun onRenderProcessGone(
                                    view: WebView?,
                                    detail: RenderProcessGoneDetail?,
                                ): Boolean = handleWebViewRendererGone(context, TAG, detail)
                            }
                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    progress = newProgress
                                }

                                /**
                                 * 网页定位请求（请假 → 销假）。**必须回填 callback**：
                                 * 不回填页面就一直等；回填 false 页面拿到的是「用户拒绝」。
                                 */
                                override fun onGeolocationPermissionsShowPrompt(
                                    origin: String?,
                                    callback: GeolocationPermissions.Callback?,
                                ) {
                                    if (geoBridge.onShowPrompt(origin, callback)) {
                                        statusNote = "网页需要定位：请在系统弹窗里允许"
                                    }
                                }

                                /** 页面自己撤销了请求（离开页面 / 取消）：按契约不能再回填。 */
                                override fun onGeolocationPermissionsHidePrompt() {
                                    geoBridge.onHidePrompt()
                                }

                                override fun onShowFileChooser(
                                    view: WebView?,
                                    filePathCallback: ValueCallback<Array<Uri>>?,
                                    fileChooserParams: FileChooserParams?,
                                ): Boolean {
                                    if (filePathCallback == null || fileChooserParams == null) {
                                        return false
                                    }
                                    // 上一个还没消费就来了新的：把旧的按「取消」了结，
                                    // 否则页面永远等在那里（系统选择器不支持并发）。
                                    fileCallback?.onReceiveValue(null)
                                    fileCallback = filePathCallback
                                    return try {
                                        filePicker.launch(fileChooserParams.createIntent())
                                        true
                                    } catch (e: Exception) {
                                        // 设备上没有能处理该 Intent 的选择器时退回 false，
                                        // 让页面自己报「不支持上传」，而不是卡在打不开的选择器上
                                        Log.w(TAG, "file chooser unavailable", e)
                                        fileCallback = null
                                        false
                                    }
                                }
                            }
                            // 起点必须是统一认证入口，不能直接开表单页（理由见上面 onPageFinished）。
                            // 未登录时停在 CAS 登录页，登录完自动落到学工，再自动进表单。
                            //
                            // 会话准备放在 loadUrl 之前（DESIGN §4.27）：存过凭证时会把 CAS 登录
                            // 做完、cookie 注入 CookieManager；没凭证时什么都不做，退化成旧行为。
                            scope.launch {
                                // 与教务导入同口径（DESIGN §4.27）：先让页面出来，登录由页面上的
                                // 自动填表完成；这里只把 OkHttp 侧会话准备好、拿结果写状态条。
                                loadUrl(XgUrls.SSO_LOGIN)
                                when (val outcome = runCatching { cas.ensureValid() }.getOrNull()) {
                                    is CasEnsureResult.Failed -> statusNote = outcome.message
                                    CasEnsureResult.Suspended ->
                                        statusNote = "教务登录已停用，请在「我的」页更新账号密码"

                                    else -> Unit
                                }
                            }
                        }
                        addView(
                            wv,
                            FrameLayout.LayoutParams(
                                FrameLayout.LayoutParams.MATCH_PARENT,
                                FrameLayout.LayoutParams.MATCH_PARENT,
                            ),
                        )
                        webView = wv
                    }
                },
                // 页面离开组合时销毁 WebView：壳里可能挂着页面简历/附件选择器等
                // 引用，不销毁会一直持有 Activity。与教务导入窗口同一处理。
                onRelease = { shell ->
                    (0 until shell.childCount).mapNotNull { shell.getChildAt(it) as? WebView }
                        .forEach { wv ->
                            wv.stopLoading()
                            wv.destroy()
                        }
                },
                )

                // 主 frame 失败（断网 / 证书 / HTTP 4xx-5xx）：Chromium 的错误页只报一句
                // 英文，这里补一条可操作的中文 + 重试。顶栏的「刷新」是同一个出口。
                loadError?.let { message ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(start = 16.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = {
                            loadError = null
                            loadErrorUrl = null
                            webView?.reload()
                        }) { Text("重试") }
                    }
                }
            }
        }
    }

    if (showCasLoginDialog) {
        CasLoginDialog(
            cas = cas,
            vault = Graph.credentialVault(context),
            title = "登录学校统一认证",
            description = "报修、请假等学工服务需要统一认证账号。保存后 App 会自动登录，" +
                "不再需要每次输入。密码加密存本机，可随时在「我的 → 学校统一认证」退出并清除。",
            onSaved = {
                savedCas = Graph.credentialVault(context).readCas()
                showCasLoginDialog = false
                // 保存后立刻重载统一认证入口：WebView 可能已停在 CAS 登录页，
                // 凭据是刚存的，重载后自动填表接管提交（用户不必退出重进）
                // 清自动填表的 5 分钟进程级节流：自动填表失败一次就消耗窗口，
                // 用户刚验证保存的正确密码不该被它压住（对齐引导页 onCredentialsUpdated 口径）
                cas.onCredentialsUpdated()
                autoLoginTried = false
                webView?.loadUrl(XgUrls.SSO_LOGIN)
            },
            onSkip = {
                casLoginDeclined = true
                showCasLoginDialog = false
            },
        )
    }
}

/**
 * 学工页面的 WebView 配置。
 *
 * 与教务页的两处差别：
 * - **不开软件渲染**。教务页的 `LAYER_TYPE_SOFTWARE` 是 MIUI 合成层丢失的兜底
 *   （DESIGN §4.4），但学工是 Vue SPA，软件渲染会明显拖慢滚动。真机若出现
 *   「DOM 正常却整面白」，再按教务页那套加回来。
 * - `domStorageEnabled` 是**必须**的：学工平台把登录 token 放在 localStorage，
 *   关掉它页面会一直停在登录态判定上（不会报错，只是永远登不进去）。
 */
@SuppressLint("SetJavaScriptEnabled")
private fun configureForXg(webView: WebView) {
    webView.setBackgroundColor(android.graphics.Color.WHITE)
    webView.settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = true
        loadsImagesAutomatically = true

        // 统一认证的登录页是固定宽的桌面布局、没有 <meta viewport>；
        // 允许页面用自身 viewport 再整体缩放到屏幕，先保证「看得全」。
        // 学工自身的页面带 width=device-width，在这套设置下同样正常。
        useWideViewPort = true
        loadWithOverviewMode = true

        setSupportZoom(true)
        builtInZoomControls = true
        displayZoomControls = false
        textZoom = 100
        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        cacheMode = WebSettings.LOAD_DEFAULT
        mediaPlaybackRequiresUserGesture = false
    }
    android.webkit.CookieManager.getInstance().setAcceptCookie(true)
    // 学工的页面和接口不一定同域（表单引擎挂在 /office 下），第三方 cookie 要放行，
    // 否则登录态会话 cookie 带不过去。
    android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
}
