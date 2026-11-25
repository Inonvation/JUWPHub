package edu.jxslu.schedule.ui.score

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
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
import edu.jxslu.schedule.data.jw.PtworkTranscript
import edu.jxslu.schedule.data.repo.TranscriptStore
import edu.jxslu.schedule.domain.TranscriptEntry
import edu.jxslu.schedule.domain.TranscriptHistory
import edu.jxslu.schedule.ui.common.AppCardRow
import edu.jxslu.schedule.ui.common.AppConfirmDialog
import edu.jxslu.schedule.ui.common.AppNoticeVisuals
import edu.jxslu.schedule.ui.common.AppSnackbarHost
import edu.jxslu.schedule.ui.common.EmptyHint
import edu.jxslu.schedule.ui.common.LoadingHint
import edu.jxslu.schedule.ui.common.NoticeTone
import edu.jxslu.schedule.ui.common.PageBackButton
import edu.jxslu.schedule.ui.common.showNotice
import edu.jxslu.schedule.ui.icon.AppIcons
import edu.jxslu.schedule.ui.jw.JwWebActionButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 最近导出（DESIGN §3.14 / §4.25）：`appDataDir()/transcripts/` 里的成绩单，
 * 每条可 打开 / 分享 / 删除，顶部可清空。
 *
 * 列表就是目录本身（文件名里带学期标签与导出时刻），没有第二套索引：
 * 不建 Room 表、不写 JSON 清单，就不会出现「记录还在、文件没了」的分叉。
 *
 * 定位是**最近导出**而不是档案库：最多 [TranscriptHistory.DEFAULT_KEEP] 份，超出的在下次导出时
 * 自动删掉。要长期留存请用「存到下载」或分享出去，页面底部把这句话写给了用户。
 *
 * ## 为什么函数名是 `TranscriptHistoryContent` 而不是 `...Screen`
 *
 * 这一页在 `:app` 的调用点是 `SubpageActivity`（`SubpageScreen.TRANSCRIPTS ->`），而那个文件
 * 正被另一条并行分支改着，本轮**一行都不许动**；它的调用是 `TranscriptHistoryScreen(onBack)`。
 * 所以：`shared` 里这份叫 `TranscriptHistoryContent`（平台动作是参数），`:app` 保留一个**同名
 * 同签名**的 `TranscriptHistoryScreen(onBack)` 宿主包装（`ui/score/TranscriptHosts.kt`），
 * 它把 Android 的打开/分享/网页入口接进来再转调本函数。两个 FQN 不同，SubpageActivity 一个字不用改。
 *
 * ## 平台动作（`design/ios-port.md` §11.2 ④）
 *
 * | 参数 | Android | iOS |
 * |---|---|---|
 * | [onOpenPdf] | `ACTION_VIEW` + FileProvider 临时授权 | null → 行点击退化成「只剩分享/删除」 |
 * | [onSharePdf] | `ShareIntents.shareFile`（`content://`） | null → 分享按钮不渲染 |
 * | [onOpenWebPage] | `JwImportActivity.startBrowse` | null → 顶栏图标不渲染 |
 *
 * `null` 一律是「本平台确实没有这条通道」，不是「以后补」的占位：给个点了没反应的行
 * 比没有更糟。列表本身的读 / 删不依赖任何平台能力（[TranscriptStore] 只认 okio）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranscriptHistoryContent(
    onBack: () -> Unit = {},
    /** 打开一份成绩单（参数是沙箱里的绝对路径）。null = 本平台没有交给系统阅读器的通道。 */
    onOpenPdf: ((path: String) -> Unit)? = null,
    /** 分享一份成绩单（同上）。null = 本平台没有可用的分享面板。 */
    onSharePdf: ((path: String) -> Unit)? = null,
    /** 在教务网页里核对成绩单入口。null = 本平台没有内置浏览窗口。 */
    onOpenWebPage: ((title: String, url: String) -> Unit)? = null,
) {
    val store = remember { TranscriptStore() }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    // null = 还在读盘（不区分「读取中」和「空」的话，首帧会先闪一下空态）
    var entries by remember { mutableStateOf<List<TranscriptEntry>?>(null) }
    var pendingDelete by remember { mutableStateOf<TranscriptEntry?>(null) }
    var confirmClear by remember { mutableStateOf(false) }

    fun notify(message: String, tone: NoticeTone) {
        scope.launch { snackbar.showNotice(AppNoticeVisuals(message, tone = tone)) }
    }

    fun refresh() {
        scope.launch { entries = withContext(Dispatchers.Default) { store.recent() } }
    }

    LaunchedEffect(Unit) { refresh() }

    /** 打开/分享前复核文件还在：列表与动作之间有「新导出触发保留策略」的时间差。 */
    fun useFile(entry: TranscriptEntry, action: (path: String) -> Unit) {
        val path = store.existingPath(entry.name)
        if (path == null) {
            notify("「${TranscriptHistory.labelOf(entry.name)}」已经不在了", NoticeTone.Warning)
            refresh()
            return
        }
        action(path)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("最近导出", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = { PageBackButton(onBack) },
                actions = {
                    // 没有内置浏览窗口的平台（iOS）整只图标不渲染
                    JwWebActionButton(
                        title = "成绩单",
                        url = PtworkTranscript.CAS_ENTRY,
                        onOpenWebPage = onOpenWebPage,
                    )
                    val list = entries
                    if (list != null && list.isNotEmpty()) {
                        TextButton(onClick = { confirmClear = true }) { Text("清空") }
                    }
                },
            )
        },
        snackbarHost = { AppSnackbarHost(snackbar) },
    ) { padding ->
        val list = entries
        when {
            list == null -> LoadingHint(
                title = "正在读取导出记录",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            )

            list.isEmpty() -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                verticalArrangement = Arrangement.Center,
            ) {
                EmptyHint(
                    title = "还没有导出记录",
                    body = "导出成绩单后会在这里留下最近 ${TranscriptHistory.DEFAULT_KEEP} 份，方便再次分享或另存。\n" +
                        "这里不是档案库，成绩单随时可以从教务处重新导出。",
                    actionLabel = "返回导出",
                    onAction = onBack,
                )
            }

            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(list, key = { it.name }) { entry ->
                    HistoryRow(
                        entry = entry,
                        onOpen = onOpenPdf?.let { open -> { useFile(entry) { path -> open(path) } } },
                        onShare = onSharePdf?.let { share -> { useFile(entry) { path -> share(path) } } },
                        onDelete = { pendingDelete = entry },
                    )
                }
                item {
                    Column {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "只保留最近 ${TranscriptHistory.DEFAULT_KEEP} 份，超出的会在下次导出时自动清理。" +
                                "要长期留存请「存到下载」或分享出去。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                        )
                    }
                }
            }
        }
    }

    pendingDelete?.let { entry ->
        // 骨架走 AppConfirmDialog（2026-11-06 统一）；`danger = true` 只染确认键颜色。
        // 删除动作与文案一字未改：删掉就要重新导出，这是用户唯一能感知的后果。
        AppConfirmDialog(
            title = "删除这份成绩单？",
            message = "「${TranscriptHistory.labelOf(entry.name)}」会从本机删除，删掉就要重新导出。",
            confirmLabel = "删除",
            onConfirm = {
                val name = entry.name
                pendingDelete = null
                scope.launch {
                    val ok = withContext(Dispatchers.Default) { store.delete(name) }
                    notify(if (ok) "已删除" else "删除失败", if (ok) NoticeTone.Success else NoticeTone.Error)
                    refresh()
                }
            },
            onDismiss = { pendingDelete = null },
            danger = true,
        )
    }

    if (confirmClear) {
        // 同上：清空是「本机全删」，danger 只改颜色，文案与计数口径（entries?.size ?: 0）不动。
        AppConfirmDialog(
            title = "清空全部导出记录？",
            message = "本机保存的 ${entries?.size ?: 0} 份成绩单都会被删除，需要时重新导出即可。",
            confirmLabel = "清空",
            onConfirm = {
                confirmClear = false
                scope.launch {
                    val removed = withContext(Dispatchers.Default) { store.deleteAll() }
                    notify("已清空 $removed 份", NoticeTone.Success)
                    refresh()
                }
            },
            onDismiss = { confirmClear = false },
            danger = true,
        )
    }
}

/**
 * 一条记录：标签 + 时间与体积；行本身点开（最常用），右侧两个动作按钮。
 *
 * 「打开」不再单独给按钮：行点击已经是打开，再放一个图标就是同一个动作两处入口。
 * [onOpen] / [onShare] 为 null 时那条路径整个不存在（`AppCardRow` 的 `onClick` 收 null
 * 就是不响应点击，不会变成一个点了没反应的假入口）。
 */
@Composable
private fun HistoryRow(
    entry: TranscriptEntry,
    onOpen: (() -> Unit)?,
    onShare: (() -> Unit)?,
    onDelete: () -> Unit,
) {
    AppCardRow(
        onClick = onOpen,
        onClickLabel = onOpen?.let { "打开 ${TranscriptHistory.labelOf(entry.name)}" },
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                TranscriptHistory.labelOf(entry.name),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "${TranscriptHistory.timeLabel(entry.modifiedAtMillis)} · " +
                    TranscriptHistory.sizeLabel(entry.sizeBytes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }
        // 两个动作按钮各自吃掉自己的点击：外面的行点击是「打开」，不能互相穿透
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(4.dp))
            onShare?.let { share ->
                IconButton(onClick = share) {
                    Icon(AppIcons.Share01, contentDescription = "分享", tint = MaterialTheme.colorScheme.primary)
                }
            }
            IconButton(onClick = onDelete) {
                Icon(AppIcons.Delete02, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}
