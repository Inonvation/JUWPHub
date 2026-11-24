package edu.jxslu.schedule.ui.me

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import edu.jxslu.schedule.platform.Services
import edu.jxslu.schedule.platform.collectAsStateWithLifecycle
import edu.jxslu.schedule.ui.common.AppConfirmDialog
import edu.jxslu.schedule.ui.common.AppNoticeVisuals
import edu.jxslu.schedule.ui.common.AppSnackbarHost
import edu.jxslu.schedule.ui.common.ImportTargetDialogHost
import edu.jxslu.schedule.ui.common.NoticeTone
import edu.jxslu.schedule.ui.common.SettingsSection
import edu.jxslu.schedule.ui.common.importPreviewNote
import edu.jxslu.schedule.ui.common.showNotice
import edu.jxslu.schedule.ui.icon.AppIcons
import edu.jxslu.schedule.ui.platform.rememberFileSaver
import edu.jxslu.schedule.ui.platform.rememberTextFilePicker
import kotlinx.coroutines.launch

/**
 * 课表数据子页：导出 / 导入 JSON、剪贴板导入、清空。
 *
 * 从「我的 → 课表数据」进入（DESIGN §3.3），不再摊在设置主页上。
 *
 * ## iOS 移植 S17（2026-11-25）搬进 `commonMain`
 *
 * 选文件 / 另存原先靠 SAF 的 `rememberLauncherForActivityResult`，现在走
 * `ui/platform/FilePickers.kt` 的 expect/actual：Android 仍是 SAF，iOS 是
 * `UIDocumentPickerViewController`（另存落沙箱 `exports/`）。因此本页两端都在。
 * JSON 文本仍由 `MeViewModel` 产出，读写文件归这两条缝——共享层看不见 `Uri`。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataSettingsScreen(
    onBack: () -> Unit,
    /**
     * 「我的」与显示设置的共享 ViewModel，**由宿主创建**（Android `viewModel {}`、
     * iOS `remember {}`），见 `design/ios-port.md` §11.1。
     */
    viewModel: MeViewModel,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val oneShot by viewModel.oneShot.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showClearConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(oneShot) {
        when (val s = oneShot) {
            is OneShot.Message -> {
                // 清空等操作带撤销动作：Snackbar 给「撤销」，点了就回滚
                val result = snackbar.showNotice(
                    s.text,
                    actionLabel = if (s.undo != null) "撤销" else null,
                    duration = SnackbarDuration.Short,
                )
                if (result == SnackbarResult.ActionPerformed) s.undo?.invoke()
                viewModel.consumeOneShot()
            }
            is OneShot.ConfirmImport -> Unit
            null -> Unit
        }
    }

    // 落盘归两条平台缝：VM 只产出 / 接收 JSON 文本（与 `WeekViewModel.jsonText` 同口径，
    // 共享层看不见 Uri）。`false` / `null` 都是「用户取消或读写失败」，两端的文案一致。
    val jsonSaver = rememberFileSaver("application/json") { saved ->
        scope.launch {
            snackbar.showNotice(
                AppNoticeVisuals(
                    if (saved) "已导出课表 JSON" else "导出失败：写入被取消或出错",
                    tone = if (saved) NoticeTone.Success else NoticeTone.Error,
                ),
            )
        }
    }
    val pickJsonFile = rememberTextFilePicker { text -> viewModel.importFromText(text) }

    // 根因：迁到 SubpageActivity 独立窗口后没有外层 Scaffold 垫状态栏，
    // windowInsets 归零（嵌 NavHost 时期防双倍空白的老规避）会让顶栏顶进状态栏；
    // 现走 M3 默认——TopAppBar 自行消费状态栏，contentWindowInsets 管住手势条。
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("课表数据") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(AppIcons.ArrowLeft01, contentDescription = "返回")
                    }
                },
            )
        },
        snackbarHost = { AppSnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SettingsSection(
                title = "导入 / 导出",
                // 笔记/作业是用户自写数据，必须说清它们跟着备份走；图片附件不并进 JSON，
                // 不提前讲清楚，换机后用户会以为「备份坏了」（2026-11-06 审计 D2）。
                subtitle = "导出当前课表「${state.timetableName.ifBlank { "—" }}」，" +
                    "成绩、笔记、作业随文件一起备份（笔记里的图片不随备份）；导入时整体替换",
            ) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            // 读库失败与写入失败是两回事，各给一句：前者是「数据读不出来」，
                            // 后者是「用户取消或磁盘写不进」（后者的提示由 saver 回调给）
                            val text = runCatching { viewModel.exportJsonText() }.getOrElse { error ->
                                snackbar.showNotice(
                                    AppNoticeVisuals(
                                        "导出失败：${error.message}",
                                        tone = NoticeTone.Error,
                                    ),
                                )
                                return@launch
                            }
                            jsonSaver("juw_courses.json", text)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("导出 JSON…") }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { pickJsonFile() },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("从文件导入 JSON…") }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { viewModel.importFromClipboard() },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("从剪贴板导入") }
            }

            SettingsSection(
                title = "危险操作",
                subtitle = "清空当前课表全部课程；提示条消失前可点「撤销」恢复。",
            ) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { showClearConfirm = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("清空全部课程", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    (oneShot as? OneShot.ConfirmImport)?.let { confirm ->
        ImportTargetDialogHost(
            courses = confirm.preview.courses,
            title = "导入 JSON 课表",
            term = confirm.preview.term,
            // 文件带成绩段/学期作息段时必须让用户知情（DESIGN §4.3）。
            // 文案收口在 `ui/common/BackupImportText.kt`：两个导入入口共用一份，且必须按
            // 「覆盖 / 合并」两种模式分别说清后果（合并导入**不动**笔记与作业）——
            // 原先这里手拼的文案说合并模式也「整体替换笔记」，与行为相反（2026-11-06 复核）。
            note = importPreviewNote(confirm.preview),
            defaultMerge = false,
            repo = Services.current.scheduleRepository(),
            onConfirm = { target, merge -> viewModel.confirmImport(target, merge) },
            onDismiss = { viewModel.cancelPendingImport() },
        )
    }

    if (showClearConfirm) {
        AppConfirmDialog(
            title = "确认清空全部课程？",
            message = "将删除当前课表「${state.timetableName.ifBlank { "—" }}」中的全部课程。\n" +
                "清空后提示条消失前可撤销；请重新从教务导入或从 JSON 恢复。",
            confirmLabel = "清空",
            danger = true,
            onConfirm = {
                showClearConfirm = false
                viewModel.clearCourses()
            },
            onDismiss = { showClearConfirm = false },
        )
    }
}
