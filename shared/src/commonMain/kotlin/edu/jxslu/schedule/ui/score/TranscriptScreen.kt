package edu.jxslu.schedule.ui.score

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import edu.jxslu.schedule.data.jw.JwAutoLogin
import edu.jxslu.schedule.data.jw.JwUrls
import edu.jxslu.schedule.data.jw.PtworkTranscript
import edu.jxslu.schedule.data.jw.TranscriptClient
import edu.jxslu.schedule.data.jw.TranscriptException
import edu.jxslu.schedule.data.jw.TranscriptTerm
import edu.jxslu.schedule.data.jw.unwrapJsString
import edu.jxslu.schedule.data.repo.TranscriptStore
import edu.jxslu.schedule.data.session.SessionStatus
import edu.jxslu.schedule.data.session.WebCookies
import edu.jxslu.schedule.domain.AppClock
import edu.jxslu.schedule.domain.TranscriptHistory
import edu.jxslu.schedule.platform.AccountCredentials
import edu.jxslu.schedule.platform.Logger
import edu.jxslu.schedule.platform.Services
import edu.jxslu.schedule.ui.common.AppBackHandler
import edu.jxslu.schedule.ui.common.AppCard
import edu.jxslu.schedule.ui.common.AppCardRow
import edu.jxslu.schedule.ui.common.AppNoticeVisuals
import edu.jxslu.schedule.ui.common.AppSnackbarHost
import edu.jxslu.schedule.ui.common.CasLoginDialog
import edu.jxslu.schedule.ui.common.EmptyHint
import edu.jxslu.schedule.ui.common.InlineNoticeRow
import edu.jxslu.schedule.ui.common.LoadingHint
import edu.jxslu.schedule.ui.common.NoticeTone
import edu.jxslu.schedule.ui.common.PageBackButton
import edu.jxslu.schedule.ui.common.SectionHeader
import edu.jxslu.schedule.ui.common.showNotice
import edu.jxslu.schedule.ui.icon.AppIcons
import edu.jxslu.schedule.ui.web.WebContentView
import edu.jxslu.schedule.ui.web.WebViewController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.IOException

/** 成绩单的 MIME（打开 / 分享 / 另存共用）。 */
const val TRANSCRIPT_PDF_MIME: String = "application/pdf"

/**
 * 导出成绩单（DESIGN §3.14 / §4.25）：选学期 → 取教务处盖章 PDF → 打开 / 分享 / 另存。
 *
 * 与「成绩查询」页的分工：那里看的是**本地成绩库**（强智 `cjcx_list`），这里出的是
 * **教务处签章系统生成的正式单据**（含姓名学号证件照与教务处成绩专用章，评优评先交的就是它）。
 * 两者数据源不同，所以这一页不读本地成绩、也不往本地写。
 *
 * 会话按需授权：先拿 WebView 里已有的 `sid` 直接调接口（多数情况下一屏就够），失效时才把授权
 * WebView 顶上来（统一认证 TGC 还在时会免密，通常一两秒）。账号密码不落任何存储，
 * 与「教务导入」同一条纪律（DESIGN §4.4）。
 *
 * ## iOS 移植（2026-11-26）：搬进 `shared/commonMain`
 *
 * 平台动作一律按 `design/ios-port.md` §11.2 ④ 收成**可空回调**，`null` = 本平台没这条通道，
 * 对应入口整行不渲染（宁可少一个按钮，也不留一个点了没反应的）：
 *
 * | 动作 | Android（`ui/score/TranscriptHosts.kt`） | iOS |
 * |---|---|---|
 * | [onOpenPdf] 交给系统阅读器 | `ACTION_VIEW` + FileProvider 临时授权 | null → 不渲染（要 `QuickLook`，还没写） |
 * | [onSharePdf] 系统分享面板 | `ShareIntents.shareFile`（`content://`） | null → 不渲染 |
 * | [onSavePdf] 另存到用户自选位置 | SAF `CreateDocument` | null → 不渲染 |
 * | [onOpenHistory] 「最近导出」二级页 | `SubpageActivity.start(TRANSCRIPTS)` | null → 顶栏图标不渲染 |
 * | [casAutoFillScript] 落 CAS 页自动填表 | 凭证保险箱里的学号密码 | null → 只让用户手登 |
 *
 * 三件**没有**搬成回调、而是收进了共享层的东西：
 * - **落盘**：[TranscriptStore] 只认 `appDataDir()` + okio，两端同一份（路径与搬移前逐字相同）。
 * - **取 `sid`**：`WebCookies.headerFor(PtworkTranscript.COOKIE_URL)`——`data/session/WebCookieSource`
 *   是「WebView 自己登出来的会话」那条既有的缝（Android `CookieManager`、iOS `WKWebsiteDataStore`）。
 *   搬移前这一行是 `:app` 的 `TranscriptCookies`（`android.webkit`），已删。
 * - **PDF 字节**：走 [TranscriptClient] 的 Ktor 请求，**不经过 WebView 的下载**。所以本页
 *   **不装** `WebViewController.onDownload`：装了反而会让「网页里点了下载」这条我们根本
 *   不依赖的路多出一个半成品（`design/ios-port.md` §11.4 的 `null` 语义 = 保持现状）。
 *
 * ## 授权层丢掉的三件 Android 专有能力（`ui/web/WebViewController` 没有对应事件）
 *
 * 1. **SSL 白名单放行**（原 `onReceivedSslError` → `JwUrls.TRUSTED_SSL_HOSTS`）：缝里没有这个
 *    事件，WebView 回到「证书错就拒」。同一批域名 OkHttp / NSURLSession 用系统信任库能直连，
 *    所以预期不改变登录结果，但这是**未在真机上验过**的一条。
 * 2. **渲染进程被回收后不让 App 陪葬**（原 `onRenderProcessGone`）：缝没有该事件，落回系统默认。
 * 3. **debug 包的 `setWebContentsDebuggingEnabled`**：开发便利，release 从来不开。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranscriptScreen(
    onBack: () -> Unit = {},
    /** 打开导出好的 PDF（参数是沙箱里的绝对路径）。`null` = 本平台没有交给系统阅读器的通道。 */
    onOpenPdf: ((path: String) -> Unit)? = null,
    /** 分享 PDF（参数同上）。`null` = 本平台没有可用的分享面板。 */
    onSharePdf: ((path: String) -> Unit)? = null,
    /**
     * 另存到用户自选位置。回调**必须回** [onResult]：那是「写完了 / 用户取消 / 写失败」的唯一
     * 信号，页面据此给提示。`null` = 本平台没有等价物（iOS 要走 `UIDocumentPickerViewController`）。
     */
    onSavePdf: ((path: String, suggestedName: String, onResult: (TranscriptSaveOutcome) -> Unit) -> Unit)? = null,
    /** 「最近导出」二级页。`null` = 本平台没有二级页栈。 */
    onOpenHistory: (() -> Unit)? = null,
    /**
     * 停在统一认证登录页时注入的自动填表脚本；`null`/空白 = 不注入，用户自己手登。
     *
     * 为什么是宿主给而不是本页从凭证仓取：密码**永远不出实现类**（`platform/AccountServices.kt`
     * 的 `AccountCredentials` 只回学号），而自动填表需要明文密码拼 JS。所以这一跳只能由
     * 能把密码交给 WebView 的那一侧（Android 的 `CredentialVault`）自己拼好递进来；
     * iOS 侧不接 = `null`，用户在网页里手登一次，功能不减。
     */
    casAutoFillScript: (() -> String?)? = null,
) {
    val store = remember { TranscriptStore() }
    val client = remember { TranscriptClientHolder.instance }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    // 凭证侧能力（iOS / Android 都有；null 只发生在「本平台没有加密凭证存储」的口径下）
    val credentials = remember { Services.current.accountCredentials() }

    var stage by remember { mutableStateOf<TxStage>(TxStage.Checking) }
    var terms by remember { mutableStateOf<List<TranscriptTerm>>(emptyList()) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var authVisible by remember { mutableStateOf(false) }
    /** 会话不可用时只自动开一次授权层，避免「授权 → 立刻又失效 → 再授权」空转。 */
    var autoAuthTried by remember { mutableStateOf(false) }
    var savedName by remember { mutableStateOf<String?>(null) }
    var savedSize by remember { mutableStateOf(0L) }
    var savedTerms by remember { mutableStateOf<List<String>>(emptyList()) }

    fun notify(message: String, tone: NoticeTone) {
        scope.launch { snackbar.showNotice(AppNoticeVisuals(message, tone = tone)) }
    }

    /**
     * 会话不可用 → **自动开一次授权层**（里面有自动填表登录），不再把用户丢在
     * 「点去登录」上等他自己点。
     *
     * 只自动一次：授权回来仍然不可用就停在 `NeedLogin`，由用户决定要不要再来一遍
     * （反复自动重试等于拿错误密码一直撞，会触发风控）。
     */
    fun requireReauth(message: String) {
        stage = TxStage.NeedLogin(message)
        if (!autoAuthTried) {
            autoAuthTried = true
            authVisible = true
        }
    }

    /**
     * 落到「完成」页之后，文件仍可能被用户从「最近导出」里删掉。
     * 所有后续动作都先过这道闸：文件不在了就回就绪态，而不是让阅读器抛一个看不懂的错。
     */
    fun withSavedPath(action: (path: String, name: String) -> Unit) {
        val name = savedName
        val path = name?.let { store.existingPath(it) }
        if (name == null || path == null) {
            notify("成绩单文件已不在了，请重新导出", NoticeTone.Warning)
            stage = TxStage.Ready
            return
        }
        action(path, name)
    }

    fun loadTerms() {
        scope.launch {
            stage = TxStage.Checking
            // 必须留在主线程调：Android 的 `CookieManager` 要有 Looper（`WebCookieSource` 的
            // Android 实现就是 `CookieManager.getCookie`），缝本身不做线程切换
            val cookie = WebCookies.headerFor(PtworkTranscript.COOKIE_URL)
            if (cookie == null) {
                requireReauth("还没登录学校统一认证")
                return@launch
            }
            try {
                val list = client.fetchTerms(cookie)
                terms = list
                // 默认全选：完整成绩单是评优评先的常用形态，少选一个学期交上去比多选更糟
                selected = list.map { it.term }.toSet()
                stage = if (list.isEmpty()) TxStage.Empty else TxStage.Ready
            } catch (e: TranscriptException.SessionExpired) {
                requireReauth("统一认证会话已过期")
            } catch (e: CancellationException) {
                // 取消（用户返回关窗口）必须原样抛：被下面那条 catch 吃掉会变成「导出失败」，
                // 还会在已经销毁的组合上写状态（同 JwHttpSession 的教训）
                throw e
            } catch (e: Throwable) {
                stage = TxStage.Failed(friendlyMessage(e))
            }
        }
    }

    fun export() {
        // 按学期倒序取出勾选项：文件名的「等 N 个学期」标签取第一个（= 最新）
        val picked = terms.map { it.term }.filter { it in selected }
        if (picked.isEmpty()) {
            notify("先选至少一个学期", NoticeTone.Warning)
            return
        }
        scope.launch {
            stage = TxStage.Exporting
            try {
                val cookie = WebCookies.headerFor(PtworkTranscript.COOKIE_URL)
                    ?: throw TranscriptException.SessionExpired()
                val bytes = client.export(picked, cookie)
                val name = PtworkTranscript.defaultFileName(picked, AppClock.nowMillis())
                // 落盘走 Default（commonMain 取不到 Dispatchers.IO，见 TranscriptStore 的类注释）
                withContext(Dispatchers.Default) { store.save(bytes, name) }
                savedName = name
                savedSize = bytes.size.toLong()
                savedTerms = picked
                stage = TxStage.Done
            } catch (e: TranscriptException.SessionExpired) {
                requireReauth("统一认证会话已过期")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                stage = TxStage.Failed(friendlyMessage(e))
            }
        }
    }

    // 进页先探一次会话：有 sid 直接出学期列表，没有才请用户授权
    LaunchedEffect(Unit) { loadTerms() }

    // 授权层打开时，返回键先收授权层，不直接关窗口
    AppBackHandler(enabled = authVisible) { authVisible = false }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("导出成绩单", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = { PageBackButton(onBack) },
                actions = {
                    // 没有二级页栈的平台（iOS）整只图标不渲染，而不是点了没反应
                    onOpenHistory?.let { open ->
                        IconButton(onClick = open) {
                            Icon(AppIcons.History, contentDescription = "最近导出")
                        }
                    }
                },
            )
        },
        snackbarHost = { AppSnackbarHost(snackbar) },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (val current = stage) {
                TxStage.Checking -> LoadingHint("正在读取可选学期", Modifier.fillMaxSize())

                TxStage.Empty -> Column(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                ) {
                    EmptyHint(
                        title = "教务里没有可导出的成绩",
                        body = "签章系统按学期出单，当前查不到任何成绩记录。\n" +
                            "如果刚考完还没出分，等教务录入后再来。",
                        actionLabel = "重新读取",
                        onAction = { loadTerms() },
                    )
                }

                is TxStage.NeedLogin -> NeedLoginContent(
                    message = current.message,
                    onAuthorize = { authVisible = true },
                    onRetry = { loadTerms() },
                )

                TxStage.Ready -> ReadyContent(
                    terms = terms,
                    selected = selected,
                    onToggle = { term ->
                        selected = if (term in selected) selected - term else selected + term
                    },
                    onSelectAll = { selected = terms.map { it.term }.toSet() },
                    onClear = { selected = emptySet() },
                    onExport = { export() },
                )

                TxStage.Exporting -> LoadingHint(
                    title = "正在生成成绩单",
                    modifier = Modifier.fillMaxSize(),
                    subtitle = "教务处出单与签章通常需要几秒",
                )

                TxStage.Done -> DoneContent(
                    fileName = savedName,
                    sizeBytes = savedSize,
                    termLabel = termLabel(savedTerms),
                    onOpen = onOpenPdf?.let { open ->
                        { withSavedPath { path, _ -> open(path) } }
                    },
                    onShare = onSharePdf?.let { share ->
                        { withSavedPath { path, _ -> share(path) } }
                    },
                    onSave = onSavePdf?.let { save ->
                        {
                            withSavedPath { path, name ->
                                save(path, name) { outcome ->
                                    // 用户取消不出声：SAF 面板自己已经给过反馈，
                                    // 再补一条「已取消」只会多一次噪声（与搬移前同口径）
                                    val tone = when (outcome) {
                                        TranscriptSaveOutcome.Saved -> NoticeTone.Success
                                        TranscriptSaveOutcome.Cancelled -> null
                                        is TranscriptSaveOutcome.Failed -> NoticeTone.Error
                                    }
                                    val message = when (outcome) {
                                        TranscriptSaveOutcome.Saved -> "已保存到所选位置"
                                        TranscriptSaveOutcome.Cancelled -> null
                                        is TranscriptSaveOutcome.Failed -> "保存失败：${outcome.message}"
                                    }
                                    if (message != null && tone != null) notify(message, tone)
                                }
                            }
                        }
                    },
                    onExportAgain = { stage = TxStage.Ready },
                )

                is TxStage.Failed -> FailedContent(
                    message = current.message,
                    onRetry = { loadTerms() },
                )
            }

            if (authVisible) {
                AuthorizationOverlay(
                    modifier = Modifier.fillMaxSize(),
                    casAutoFillScript = casAutoFillScript,
                    credentials = credentials,
                    onAuthorized = {
                        authVisible = false
                        loadTerms()
                    },
                    onClose = { authVisible = false },
                )
            }
        }
    }
}

/**
 * 「另存到用户自选位置」的结果。
 *
 * 三档而不是一个布尔：用户点「取消」与真的写失败，用户该看到的话不一样
 * （前者是「已取消」，后者要给出原因）。同 `ui/platform/FilePickers` 的 `false` 语义
 * 不同的地方在于——那条缝只服务课表 JSON 导出，调用方本来就只说一句「保存失败」；
 * 这里原本就会把 `openOutputStream` 的异常原话带给用户，别在搬移中把这条信息丢掉。
 */
sealed interface TranscriptSaveOutcome {
    data object Saved : TranscriptSaveOutcome
    data object Cancelled : TranscriptSaveOutcome
    data class Failed(val message: String) : TranscriptSaveOutcome
}

/** 页面状态。学期与勾选分开存（它们跨状态存活，失败重试后仍保留）。 */
private sealed interface TxStage {
    data object Checking : TxStage
    data class NeedLogin(val message: String) : TxStage
    data object Empty : TxStage
    data object Ready : TxStage
    data object Exporting : TxStage
    data object Done : TxStage
    data class Failed(val message: String) : TxStage
}

/**
 * 进程内唯一的 [TranscriptClient]。
 *
 * 为什么不是每次进页 new 一个：它内部持有一个 Ktor client（独立连接池），
 * `SharedHttp.create` 的注释写得很清楚——「每调一次就是一个独立实例，模块内应该只建一次」。
 * 为什么没走 `AppServices`：那个接口的每个成员都要两端的装配根各补一行，而这里**没有任何
 * 平台差异**（`TranscriptClient` 只依赖 `TranscriptTransport` 那条缝），加进去只是多一处
 * 要跟着改的地方。`by lazy` 在 Native 上同样安全（`AppIcons` 全文件就是这个写法）。
 */
private object TranscriptClientHolder {
    val instance: TranscriptClient by lazy { TranscriptClient() }
}

@Composable
private fun NeedLoginContent(
    message: String,
    onAuthorize: () -> Unit,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            message,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "成绩单由教务处签章系统出具，需要一次统一认证登录。\n" +
                "已保存的账号密码会自动用于登录，失败时也能在这个网页里手动登录；" +
                "要撤销保存，去「我的」→ 教务 退出登录。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onAuthorize) { Text("去登录") }
        Spacer(Modifier.height(6.dp))
        TextButton(onClick = onRetry) { Text("已经登录过了，重试") }
    }
}

@Composable
private fun ReadyContent(
    terms: List<TranscriptTerm>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onSelectAll: () -> Unit,
    onClear: () -> Unit,
    onExport: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                AppCard {
                    Text(
                        "江西水利电力大学学生成绩表",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "由教务处签章系统出具，含证件照与教务处成绩专用章。" +
                            "多选学期会合并到同一张成绩表。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                    )
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "已选 ${selected.size} / ${terms.size} 个学期",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onSelectAll) { Text("全选") }
                    TextButton(onClick = onClear) { Text("全不选") }
                }
            }
            items(terms, key = { it.term }) { term ->
                TermRow(
                    term = term,
                    checked = term.term in selected,
                    onToggle = { onToggle(term.term) },
                )
            }
            item {
                Text(
                    "签章系统只提供 HTTP 明文通道（校内网），成绩单与登录票据都在明文里传输。" +
                        "导出的文件保存在本应用私有目录，不上传任何地方。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        Surface(tonalElevation = 3.dp) {
            Button(
                onClick = onExport,
                // 一个学期都没选就置灰：按钮文案已经写着「先选学期」，能点却只弹一句提示是自相矛盾
                enabled = selected.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .height(44.dp),
            ) {
                Text(if (selected.isEmpty()) "先选学期" else "导出成绩单（${selected.size} 个学期）")
            }
        }
    }
}

@Composable
private fun TermRow(term: TranscriptTerm, checked: Boolean, onToggle: () -> Unit) {
    AppCardRow(
        onClick = onToggle,
        onClickLabel = if (checked) "取消选择 ${term.term}" else "选择 ${term.term}",
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                term.term,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "${term.courseCount} 门课程",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
    }
}

/**
 * 「接下来」那块：三个动作**各自**按宿主给不给来决定渲不渲染。
 *
 * 一个都没有时整块（含下面那条说明）不出现——说明里写着「要长期留存请存到下载或分享出去」，
 * 而两个出口都不在时那句话就成了指路指向空气。
 */
@Composable
private fun DoneContent(
    fileName: String?,
    sizeBytes: Long,
    termLabel: String,
    onOpen: (() -> Unit)?,
    onShare: (() -> Unit)?,
    onSave: (() -> Unit)?,
    onExportAgain: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        AppCard {
            Text(
                "成绩单已生成",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            Text(termLabel, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(2.dp))
            Text(
                listOfNotNull(fileName, fileName?.let { TranscriptHistory.sizeLabel(sizeBytes) })
                    .joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }
        if (onOpen != null || onShare != null || onSave != null) {
            SectionHeader(title = "接下来")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                onOpen?.let { OutlinedButton(onClick = it, modifier = Modifier.weight(1f)) { Text("打开") } }
                onShare?.let { OutlinedButton(onClick = it, modifier = Modifier.weight(1f)) { Text("分享") } }
                onSave?.let {
                    OutlinedButton(onClick = it, modifier = Modifier.weight(1f)) { Text("存到下载") }
                }
            }
            Spacer(Modifier.height(10.dp))
            InlineNoticeRow(
                "文件在应用私有目录，只保留最近 ${TranscriptHistory.DEFAULT_KEEP} 份；" +
                    "要长期留存请「存到下载」或分享出去。",
                NoticeTone.Info,
            )
            Spacer(Modifier.height(16.dp))
        }
        TextButton(onClick = onExportAgain, modifier = Modifier.fillMaxWidth()) {
            Text("再导一份")
        }
    }
}

@Composable
private fun FailedContent(
    message: String,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        InlineNoticeRow(message, NoticeTone.Error)
        Spacer(Modifier.height(16.dp))
        // 只给「重试」：会话失效这条根本走不到这里（它在 loadTerms/export 里被单独分出来，
        // 直接进「待登录」态），所以不需要并列一个永远不出现的「重新登录」按钮
        OutlinedButton(onClick = onRetry) { Text("重试") }
    }
}

/** 自动登录结果只在日志里看，方便下次排查；不写 UI、不含凭证。 */
private const val AUTH_TAG = "PtworkAuth"

/**
 * 统一认证授权层。
 *
 * 只做一件事：把 CAS 那一跳走完，让签章系统在 WebView 里写下 `sid`。落点一到
 * 「签章系统域且不是它自己的登录页」就自动回调（判定见 [PtworkTranscript.isAuthorizedLanding]）。
 * 底部留一个手动「继续」按钮：自动识别万一失灵（页面结构变化），用户仍能往下走。
 *
 * ## 搬进共享层换掉的东西
 *
 * `AndroidView` + `WebView` + `FrameLayout` 壳 + `onRenderProcessGone` + SSL 白名单
 * → `ui/web/WebContentView` 这条缝（`design/ios-port.md` §11.4）。缝里没有的三件能力
 * （SSL 放行 / 渲染进程回收 / debug 远程调试）列在 [TranscriptScreen] 的 KDoc 里。
 *
 * 会话 Cookie 不再从这里读：`getCookieHeader` 只在原生视图活着时可用，而进页第一次探会话
 * 发生在授权层还没挂上之前。两侧读的是同一份 Cookie 库（Android `CookieManager`、
 * iOS `WKWebsiteDataStore`），所以走的是 `WebCookies` 那条缝而不是控制器。
 */
@Composable
private fun AuthorizationOverlay(
    modifier: Modifier = Modifier,
    casAutoFillScript: (() -> String?)?,
    credentials: AccountCredentials?,
    onAuthorized: () -> Unit,
    onClose: () -> Unit,
) {
    // 视图是围着句柄建的，必须留在组合里：每次重组 new 一个等于每次重组重建 WebView
    val controller = remember { WebViewController() }
    var problem by remember { mutableStateOf<String?>(null) }
    // 停在统一认证登录页时用保存的凭证自动登一次（DESIGN §4.25）。只试一次：
    // 失败（结构变了 / 有验证码）就交给用户手登，反复试只会撞风控。
    var hasSavedCredentials by remember { mutableStateOf(credentials?.readJwUsername() != null) }
    var autoLoginTried by remember { mutableStateOf(false) }
    // 前置登录（DESIGN §4.27，2026-09-28）：本地没有凭据时进授权层先问一次，
    // 保存验证通过后 WebView 落 CAS 页由自动填表接管（与教务导入同一套闭环）
    var showCasLoginDialog by remember { mutableStateOf(false) }
    var casLoginDeclined by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        // 没有加密凭证存储的平台（credentials == null）不问，问了也存不住
        if (credentials != null && !hasSavedCredentials && !casLoginDeclined) showCasLoginDialog = true
    }

    // 自动识别会连着触发多次（落地后还有后续页面加载与非主框架回调），只认第一次
    var fired by remember { mutableStateOf(false) }
    val authorizeOnce: () -> Unit = {
        if (!fired) {
            fired = true
            onAuthorized()
        }
    }

    remember(controller) {
        controller.onPageFinished = { url ->
            // 与教务导入 / 学工表单同一手法（DESIGN §4.27）：让 WebView 自己
            // 填表提交，cookie 由 CAS 亲自 Set-Cookie——把 OkHttp 拿到的 cookie
            // 注入 CookieManager 那条路在真机走不通（属性缺 SameSite）。
            if (!autoLoginTried && JwUrls.isCasHost(url)) {
                val script = casAutoFillScript?.invoke()
                if (!script.isNullOrBlank()) {
                    // 进程级节流（DESIGN §4.27）：页面级标记挡不住重开窗口。
                    if (SessionStatus.tryAcquireAutoLogin(AppClock.nowMillis())) {
                        autoLoginTried = true
                        controller.evaluateJavascript(script) { raw ->
                            val r = unwrapJsString(raw)
                            Logger.d(AUTH_TAG, "auto-login on CAS page: $r")
                            if (!r.startsWith(JwAutoLogin.OK_PREFIX)) {
                                // 没找到表单不算一次登录尝试：退回共享节流，
                                // 别把别的窗口（教务网页核对）也压 5 分钟（2026-10-21）
                                SessionStatus.releaseAutoLoginAttempt()
                                problem = "自动登录没走通，请在下方页面手动登录一次"
                            }
                        }
                    }
                }
            }
            if (PtworkTranscript.isAuthorizedLanding(url)) authorizeOnce()
        }
        controller.onError = { message ->
            // 缝只在主 frame 失败时回调（子资源 404 不打扰用户），所以这里不用再判一次
            problem = "登录页打不开：$message"
        }
    }

    Column(modifier = modifier.background(MaterialTheme.colorScheme.surface)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "登录学校统一认证",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "登录成功后会自动返回",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
            IconButton(onClick = onClose) {
                Icon(AppIcons.Cancel01, contentDescription = "关闭登录页")
            }
        }
        problem?.let {
            InlineNoticeRow(
                message = it,
                tone = NoticeTone.Warning,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        WebContentView(
            controller = controller,
            url = PtworkTranscript.CAS_ENTRY,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = {
                    problem = null
                    controller.reload()
                },
            ) { Text("重新加载") }
            Spacer(Modifier.weight(1f))
            Button(onClick = authorizeOnce) { Text("已登录，继续") }
        }
    }

    if (showCasLoginDialog && credentials != null) {
        CasLoginDialog(
            onVerify = { u, p -> credentials.verifyJw(u, p) },
            onSave = { u, p -> credentials.saveJw(u, p) },
            title = "登录学校统一认证",
            description = "导出盖章成绩单需要统一认证账号。保存后 App 会自动登录，" +
                "不再需要每次输入。密码加密存本机，可随时在「我的 → 学校统一认证」退出并清除。",
            onSaved = {
                hasSavedCredentials = credentials.readJwUsername() != null
                showCasLoginDialog = false
                // 保存后立刻重载统一认证入口：WebView 可能已停在 CAS 登录页，
                // 凭据是刚存的，重载后自动填表接管提交（用户不必退出重进）。
                // 自动填表的 5 分钟进程级节流已由 `verifyJw` 清掉（它的实现里第一步就是
                // `onCredentialsUpdated`），这里只需要放开页面级标记。
                autoLoginTried = false
                controller.loadUrl(PtworkTranscript.CAS_ENTRY)
            },
            onSkip = {
                casLoginDeclined = true
                showCasLoginDialog = false
            },
        )
    }
}

/** 把异常翻成用户能照着做点什么的话。 */
private fun friendlyMessage(e: Throwable): String = when (e) {
    is TranscriptException.NoContent -> e.message ?: "没有可导出的成绩"
    is TranscriptException.Network -> "网络不可达，检查是否开了 VPN 或代理"
    is TranscriptException.Protocol -> e.message ?: "签章系统接口变了"
    is TranscriptException.SessionExpired -> e.message ?: "需要重新登录统一认证"
    // 落盘失败走这条：最可能是存储空间不足，其次是被别的进程占着（很少见）
    is IOException -> "写入文件失败，检查存储空间后重试"
    else -> e.message ?: "导出失败"
}

/** 「2025-2026-2 等 3 个学期」。标签取第一个（调用方按学期倒序传）。 */
private fun termLabel(terms: List<String>): String = when {
    terms.isEmpty() -> "全部学期"
    terms.size == 1 -> terms.first()
    else -> "${terms.first()} 等 ${terms.size} 个学期"
}
