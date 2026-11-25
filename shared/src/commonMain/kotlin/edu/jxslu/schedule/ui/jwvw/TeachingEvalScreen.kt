package edu.jxslu.schedule.ui.jwvw

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import edu.jxslu.schedule.data.jw.JwAutoLogin
import edu.jxslu.schedule.data.jw.JwImportDiagnosis
import edu.jxslu.schedule.data.jw.JwTeachingEval
import edu.jxslu.schedule.data.jw.JwUrls
import edu.jxslu.schedule.data.jw.unwrapJsString
import edu.jxslu.schedule.data.session.SessionStatus
import edu.jxslu.schedule.domain.AppClock
import edu.jxslu.schedule.platform.Logger
import edu.jxslu.schedule.platform.Services
import edu.jxslu.schedule.platform.collectAsStateWithLifecycle
import edu.jxslu.schedule.ui.common.PageBackButton
import edu.jxslu.schedule.ui.icon.AppIcons
import edu.jxslu.schedule.ui.web.WebContentView
import edu.jxslu.schedule.ui.web.WebViewController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 学生评教窗口（DESIGN §4.44），共享层版本（2026-11-26 从 `:app` 的 `JwImportScreen` 搬出）。
 *
 * 这一页是「只当浏览器用」的第二例（第一例是选课）：进页先把用户送进统一认证，登录落地后
 * 注入 [JwTeachingEval.launchJs]（POST 取 token → **同窗口** `location.href` 到评教平台
 * `jiaowupj.juwp.edu.cn` 的 SPA），之后**全部交给用户在平台上操作**——App 不代评、不碰任何
 * 提交接口（与退课同一条红线）。所以本页没有底部按钮、没有导入动作、也没有任何写库路径。
 *
 * ## 为什么是单独一页而不是给 `JwImportScreen` 加一条分支
 *
 * `:app` 那张导入页 2,400 行，压着课表 / 考试 / 成绩 / 选课四套抽取与写库、学期切换、
 * 一键导入闸门、错误浮层一整套 Android `WebViewClient` 细节。评教一条都用不上——它要的
 * 只是「登录 → 落地 → 注入 → 让用户用」。把那一页整体上移会拖动十几种与本页无关的路径；
 * 只搬评教这一条链，本页 300 行以内能讲清，且走的是公共的 WebView 缝
 * （[WebViewController]，`design/ios-port.md` §11.4）。
 *
 * ## 平台动作全是可空回调（§11.2 ④）
 *
 * | 参数 | Android 传什么 | `null` 的含义 |
 * |---|---|---|
 * | [casFillScript] | 从保险箱读统一认证凭证、拼 [JwAutoLogin.fillJs] | **本平台不自动填表**：登录页要用户手输；顺带关掉「会话失效自动重登」 |
 * | [onJwSessionReady] | `CasSession.adoptFromWebView()`（把网页里那份 cookie 抄回会话层） | 不接管会话回灌：评教照样能用，只是结束之后取数可能还要再登一次 |
 *
 * 密码只在这条闭包里从保险箱直接拼进 JS，不出 `CredentialVault`，也不经共享层的
 * `AccountCredentials`（那边按设计只回学号）——与成绩单页同一口径。
 *
 * ## 与搬移前的 Android 窗口相比**少了**什么（读代码能定的都在这里）
 *
 * 1. **逐页历史回退**：返回键直接关窗口。缝里没有 `canGoBack` / `goBack()`，
 *    而 Android 侧那个文件本轮不许动（见 `design/ios-port.md` §11.3 的口径）。
 *    评教平台的导航都在自己的 SPA 里，退页需求远小于导入页，先关窗口。
 * 2. **HTTP 4xx/5xx 的具体诊断**：缝的 `onError` 只回一句话，拿不到状态码，
 *    所以本页的失败浮层只说「页面加载失败」+ 重试，不再区分 500 / 超时 / 证书。
 *    `JwImportDiagnosis` 里那套按错误码分档的文案仍在，等缝补上 `onReceivedHttpError` 再接。
 * 3. **VPN 提示**：`JwVpnDetector` 要 `Context`，共享层没有；诊断一律按「未开 VPN」拼文案。
 *
 * 三件都写在这张清单里而不是各自造兜底：造一个点了没反应的入口比没有更糟（§11.2 ④）。
 *
 * // shortcut: 没有网页历史回退与 HTTP 状态码诊断（缝缺 goBack / onReceivedHttpError）；
 * // 缝补上这两件之后，把第 1、2 条接回来，并把 `:app` 里那份评教分支彻底删掉。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeachingEvalScreen(
    onBack: () -> Unit,
    casFillScript: (() -> String)? = null,
    onJwSessionReady: (() -> Unit)? = null,
) {
    // 视图是围着句柄建的，必须留在组合里：每次重组 new 一个等于每次重组重建 WebView。
    val controller = remember { WebViewController() }
    val prefs = remember { Services.current.displayPrefs() }
    // 与导入窗口同一个偏好键（DESIGN §4.4）：两个窗口的「电脑版」是同一件事，不该各存一份。
    val desktopMode by prefs.jwDesktopMode.collectAsStateWithLifecycle(initialValue = true)
    val scope = rememberCoroutineScope()

    var statusNote by remember { mutableStateOf(TeachingEvalFlow.OPENING_NOTE) }
    var loadError by remember { mutableStateOf<String?>(null) }
    /** 当前页 URL 的**组合期副本**：控制器那个 `currentUrl` 不是 Compose 状态，读它不会触发重组。 */
    var currentUrl by remember { mutableStateOf("") }
    /** 注入代次（[TeachingEvalFlow.stillStuck] 的判据之一）：刷新重试会开新代次。 */
    var attempt by remember { mutableIntStateOf(0) }
    /** 本轮注入还挂着：落到平台 / 注入当场失败时清掉。 */
    var injectPending by remember { mutableStateOf(false) }
    /** 自动填表只做一次：失败就交给用户手登，反复试只会撞风控。 */
    var autoFillTried by remember { mutableStateOf(false) }
    /** 会话失效后的自动重登只做一次：真故障时不能打转。 */
    var reloginUsed by remember { mutableStateOf(false) }

    /**
     * 会话失效 / 被顶：先给一次自动重登（有本机凭证才做），否则铺错误浮层。
     *
     * 之所以要重登而不是让用户自己找登录入口：本窗口只有顶栏「刷新」，而登录页可能被
     * 教务**就地渲染在 `xsMainV` 上**（URL 不变），用户看不出该点哪里。回预热入口重走
     * 一遍认证，落地后由 [TeachingEvalFlow.isLanding] 再把注入发起来。
     *
     * 退出码与 `:app` 那条兜底一致：`retryUrl` 非空（都是 [JwUrls.SSO_WARMUP]），
     * 因为认证链上的失败**不能就地重放**（CAS `ticket=` 是一次性票据，重放恒定 500）。
     */
    fun onSessionLost(diagnosis: JwImportDiagnosis.Diagnosis) {
        injectPending = false
        if (!reloginUsed && casFillScript != null) {
            reloginUsed = true
            loadError = null
            statusNote = "教务会话已失效，正在自动重新登录…"
            controller.loadUrl(JwUrls.SSO_WARMUP)
            return
        }
        statusNote = diagnosis.title
        loadError = diagnosis.body
    }

    /**
     * 注入「取 token 再跳」并挂看门狗。
     *
     * 只许在落地页上调用（[TeachingEvalFlow.isLanding]）：评教没有静态目标页，注入靠的
     * 是教务主页里那份 jQuery 与同源 cookie。
     */
    fun launchEval() {
        if (injectPending) return
        injectPending = true
        attempt += 1
        val mine = attempt
        statusNote = TeachingEvalFlow.LANDING_NOTE
        controller.evaluateJavascript(JwTeachingEval.launchJs()) { raw ->
            val injection = TeachingEvalFlow.parseInjection(unwrapJsString(raw))
            Logger.d(TAG, "eval injection: $injection")
            if (!TeachingEvalFlow.isStarted(injection)) {
                injectPending = false
                statusNote = TeachingEvalFlow.injectFailureNote(injection)
            }
        }
        // 发起即返回：ajax 静默失败时不会有任何页面事件，只能靠这个看门狗收场
        scope.launch {
            delay(TeachingEvalFlow.TIMEOUT_MS)
            if (TeachingEvalFlow.stillStuck(mine, attempt, controller.currentUrl, injectPending)) {
                injectPending = false
                statusNote = TeachingEvalFlow.TIMEOUT_NOTE
            }
        }
    }

    /** 一页加载完成（主 frame）之后的分派：按落在哪一页决定下一步。 */
    fun onLoaded(url: String) {
        // 主 frame 加载成功 = 上一轮的错误条可以收了（失败页的完成回调也会到，
        // 但真失败时 onError 已经把浮层铺上了，这里只处理到达顺序）
        loadError = null
        currentUrl = url
        statusNote = TeachingEvalFlow.loadedNote(url)
        when {
            // 评教平台：这一跳完成，注入状态归零，剩下全是用户自己的操作
            JwTeachingEval.isPlatformUrl(url) -> injectPending = false

            // 统一认证登录页：用本机存的凭证自动登一次（有才做）
            JwUrls.isCasHost(url) -> {
                if (autoFillTried) return
                val script = casFillScript?.invoke() ?: return
                // 进程级节流（DESIGN §4.27）：密码改过而 App 还存着旧的时候，每开一次窗口撞一次 CAS
                if (!SessionStatus.tryAcquireAutoLogin(AppClock.nowMillis())) return
                autoFillTried = true
                controller.evaluateJavascript(script) { raw ->
                    val result = unwrapJsString(raw)
                    // 只记结果，**不记凭证**
                    Logger.d(TAG, "auto-login on CAS page: $result")
                    if (result?.startsWith(JwAutoLogin.OK_PREFIX) != true) {
                        // 没找到表单不算一次登录尝试：把进程级节流退回去，别的窗口仍可自动登录
                        SessionStatus.releaseAutoLoginAttempt()
                        statusNote = "自动登录没走通，请在下方页面手动登录一次"
                    }
                }
            }

            // 教务域：先过会话探针（未登录时登录页被就地渲染在这个 URL 上），
            // 会话真的可用才谈「落地注入」
            JwUrls.isJwHost(url) -> {
                controller.evaluateJavascript(JwSessionProbe.SCRIPT) { raw ->
                    val probe = unwrapJsString(raw)
                    // 无条件记探针原始值：下次复现能直接读出判定依据，而不是靠猜
                    Logger.d(TAG, "session probe: '$probe' @ ${JwTeachingEval.redactForLog(url)}")
                    when {
                        JwImportDiagnosis.looksLikeSessionConflict(probe) -> {
                            Logger.d(TAG, "session conflict at ${JwTeachingEval.redactForLog(url)}")
                            onSessionLost(JwImportDiagnosis.sessionConflict(vpnActive = false))
                        }

                        JwImportDiagnosis.looksLikeLoginPage(probe) -> {
                            Logger.d(TAG, "session lost at ${JwTeachingEval.redactForLog(url)}")
                            onSessionLost(JwImportDiagnosis.sessionLost(vpnActive = false))
                        }

                        else -> {
                            // 探针通过 = 教务会话真的可用。刚自动填过表的就算登录成功：
                            // 放开 5 分钟节流，下一次会话过期仍能立即自动登录
                            if (autoFillTried) SessionStatus.markAutoLoginSucceeded()
                            onJwSessionReady?.invoke()
                            if (TeachingEvalFlow.isLanding(url)) launchEval() else injectPending = false
                        }
                    }
                }
            }

            else -> Unit
        }
    }

    /** 顶栏「刷新」。错误态下（浮层已铺）语义与浮层那个「重试」相同。 */
    fun refresh() {
        injectPending = false
        reloginUsed = false
        if (loadError != null) {
            loadError = null
            statusNote = "重新打开统一身份认证…"
            // 认证链上的失败不能就地重放（ticket 一次性），回预热入口重走一遍
            controller.loadUrl(JwUrls.SSO_WARMUP)
        } else {
            statusNote = "重新加载中…"
            controller.reload()
        }
    }

    // 回调挂在**控制器**上而不是每次重组重装：控制器比这一屏活得久，装一次即可。
    // 内层读的是 remember 的 State 委托，读到的是当前值。
    remember(controller) {
        controller.onPageFinished = { url -> onLoaded(url) }
        controller.onError = { message ->
            // 缝只给一句话（没有状态码）：原样给用户 + 顶栏重试，不自造分档诊断
            loadError = message
            statusNote = "页面加载失败：可点「重试」重新走一次统一身份认证"
        }
        // 每页加载完成后的适配脚本（登录表单 / 课表页 / 电脑版视口）：判据唯一实现在 JwWebFit
        controller.onPageReady = { url -> JwWebFit.fitScript(url, desktopMode) }
    }

    // 顶栏切「电脑版 / 手机版」：当场重注入，不重载页面（选课页的 iframe 状态不会丢）。
    // 首次组合不重跑（lastDesktop 记着初值）：进窗时页面还没开始加载，每页的注入走 onPageReady。
    var lastDesktop by remember { mutableStateOf(desktopMode) }
    LaunchedEffect(desktopMode) {
        if (desktopMode == lastDesktop) return@LaunchedEffect
        lastDesktop = desktopMode
        controller.evaluateJavascript(
            if (desktopMode) JwWebFit.DESKTOP_JS else JwWebFit.DESKTOP_REVERT_JS,
        )
        statusNote = if (desktopMode) "已切换为电脑版；双指可放大缩小" else "已切换为手机版"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(TeachingEvalFlow.TITLE, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = currentUrl.ifBlank { "正在打开统一身份认证" },
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                        )
                    }
                },
                navigationIcon = {
                    // 左上角箭头 = **直接退出窗口**（与导入页同一语义）
                    PageBackButton(onBack)
                },
                actions = {
                    // 门户域不显示这枚 chip（JwWebFit 对门户不注入电脑版，留着它点下去没反应）。
                    // 评教窗口只走教务 → 评教平台，不会到门户，但判据仍按同一份口径写，
                    // 免得将来有人在别处复用它时踩同一个坑。
                    if (!JwUrls.isPortalHost(currentUrl)) {
                        JwDesktopModeChip(
                            desktop = desktopMode,
                            onToggle = {
                                val next = !desktopMode
                                scope.launch { prefs.setJwDesktopMode(next) }
                            },
                        )
                    }
                    IconButton(onClick = { refresh() }) {
                        Icon(AppIcons.Refresh, contentDescription = "刷新")
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
            // 状态条固定单行（与导入页同高、同理由）：提示出现 / 消失不能改变 WebView 高度
            // ——容器被 resize 会触发硬件 surface 重建，MIUI 上表现为整面刷白。
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 36.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = statusNote,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                WebContentView(
                    controller = controller,
                    url = JwUrls.SSO_WARMUP,
                    modifier = Modifier.fillMaxSize(),
                )
                // 错误浮层必须是 overlay：出现 / 消失不改变 WebView 尺寸（同上）
                loadError?.let { message ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("页面没有打开", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        )
                        Spacer(Modifier.height(16.dp))
                        TextButton(onClick = { refresh() }) { Text("重试") }
                    }
                }
            }
        }
    }
}

private const val TAG = "JwTeachingEval"
