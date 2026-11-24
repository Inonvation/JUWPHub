package edu.jxslu.schedule.ui.exam

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import edu.jxslu.schedule.JwImportActivity
import edu.jxslu.schedule.SubpageActivity
import edu.jxslu.schedule.SubpageScreen
import edu.jxslu.schedule.data.jw.JwUrls
import edu.jxslu.schedule.ui.reminder.ScoreAlertReminder
import edu.jxslu.schedule.ui.score.maybeRequestNotifPermission
import kotlinx.coroutines.launch

/**
 * 考试页与考试设置页要宿主代劳的平台动作（iOS 移植 S10，2026-11-25）。
 *
 * 两页已搬进 `shared/commonMain`，那里拿不到 `Activity` / `JwImportActivity` /
 * `ScoreAlertReminder`（WorkManager + 通知）。按本仓口径（`ui/week/WeekScreenPlatform.kt`）
 * 收成一组回调；共享页面对 null 的处理是「入口整行不渲染」，所以这里每个字段都得真能执行。
 *
 * 教务直取链（`ExamSync`）**不在这里**：它由 `AppServices.examSync()` 提供，两端各自装配。
 */
class ExamScreenActions(
    /** 在教务网页核对考试安排（顶栏外链图标）。 */
    val openJwPage: () -> Unit,
    /** 考试设置二级页。 */
    val openSettings: () -> Unit,
    /** 打开导入窗口并直达「考试」那一段（设置页「导入考试安排到课表」卡）。 */
    val openImport: () -> Unit,
    /** 申请通知权限；拒绝时回调 `onDenied`，由共享页给一条提示。 */
    val requestNotifPermission: (onDenied: () -> Unit) -> Unit,
    /** 提醒开关 / 间隔改动后重排后台核对（清检查时刻 + 即时评估 + 周期任务）。 */
    val alertSettingsChanged: () -> Unit,
)

/** 组合期一次性取齐本组动作（`remember` 按 context 记忆，理由同 `rememberScoreScreenActions`）。 */
@Composable
fun rememberExamScreenActions(): ExamScreenActions {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 权限回调在组合之外，读不到调用时刻的 lambda，只能先存一份
    val pendingDenied = remember { mutableStateOf<(() -> Unit)?>(null) }
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (!granted) pendingDenied.value?.invoke()
    }

    return remember(context) {
        ExamScreenActions(
            openJwPage = { JwImportActivity.startBrowse(context, "考试安排", JwUrls.EXAM_QUERY) },
            openSettings = { SubpageActivity.start(context, SubpageScreen.EXAM_SETTINGS) },
            openImport = { JwImportActivity.start(context, startAtExam = true) },
            requestNotifPermission = { onDenied ->
                pendingDenied.value = onDenied
                maybeRequestNotifPermission(context, notifPermissionLauncher)
            },
            alertSettingsChanged = { scope.launch { ScoreAlertReminder.onSettingsChanged(context) } },
        )
    }
}
