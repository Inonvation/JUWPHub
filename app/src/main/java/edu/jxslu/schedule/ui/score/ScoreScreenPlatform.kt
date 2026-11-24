package edu.jxslu.schedule.ui.score

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import edu.jxslu.schedule.JwImportActivity
import edu.jxslu.schedule.SubpageActivity
import edu.jxslu.schedule.SubpageScreen
import edu.jxslu.schedule.TranscriptActivity
import edu.jxslu.schedule.ui.jwvw.JwImportMode
import edu.jxslu.schedule.ui.reminder.ScoreAlertReminder
import kotlinx.coroutines.launch

/**
 * 成绩页与成绩设置页要宿主代劳的平台动作（iOS 移植 S10，2026-11-25）。
 *
 * ## 为什么在这一侧
 *
 * 两页已搬进 `shared/commonMain`，那里没有 `Context` / `Activity` /
 * `ActivityResultLauncher`，也拿不到 WorkManager（`ScoreAlertReminder` 是通知 + 周期任务）。
 * 按本仓已定的口径（见 `ui/week/WeekScreenPlatform.kt`），这些动作收成一组回调交给共享层；
 * 共享页面对 null 的处理是「入口整行不渲染」，所以这里的每个字段都必须是**真的能执行**的，
 * 不要为了凑参数塞空 lambda。
 *
 * 与课表页那组的差别：本组不需要文件读写，只有「跳窗口 / 申请权限 / 重排后台核对」三类。
 */
class ScoreScreenActions(
    /** 从教务导入成绩（顶栏「从教务导入」与空态「去导入」）。 */
    val openImport: () -> Unit,
    /** 成绩设置二级页。 */
    val openSettings: () -> Unit,
    /** 导出带章成绩单。 */
    val openTranscript: () -> Unit,
    /** 申请通知权限；拒绝时回调 `onDenied`，由共享页给一条提示。 */
    val requestNotifPermission: (onDenied: () -> Unit) -> Unit,
    /** 提醒开关 / 间隔改动后重排后台核对（清检查时刻 + 即时评估 + 周期任务）。 */
    val alertSettingsChanged: () -> Unit,
)

/**
 * 组合期一次性取齐本组动作。
 *
 * `remember` 只按 `context` 记忆：`openImport` 等闭包读的是 Activity 实例，换 Activity
 * 就是换一套（`SubpageActivity` 每次进页都是新实例，记忆键跟着换才不会拿旧实例去 startActivity）。
 */
@Composable
fun rememberScoreScreenActions(): ScoreScreenActions {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 权限回调里要跑的那一段：`launcher` 的回调在组合之外，读不到调用时刻的 lambda，
    // 只能先存一份（同 `WeekScreenPlatform` 的 `pendingPick` 手法）
    val pendingDenied = remember { mutableStateOf<(() -> Unit)?>(null) }

    // 开启提醒那一刻请求 POST_NOTIFICATIONS（API 33+），拒绝不阻塞开关本身
    // （口径同 CampusCardSettingsScreen：功能在系统设置里授权后自动生效）
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (!granted) pendingDenied.value?.invoke()
    }

    return remember(context) {
        ScoreScreenActions(
            openImport = { JwImportActivity.start(context, JwImportMode.Scores) },
            openSettings = { SubpageActivity.start(context, SubpageScreen.SCORE_SETTINGS) },
            openTranscript = { TranscriptActivity.start(context) },
            requestNotifPermission = { onDenied ->
                pendingDenied.value = onDenied
                maybeRequestNotifPermission(context, notifPermissionLauncher)
            },
            alertSettingsChanged = { scope.launch { ScoreAlertReminder.onSettingsChanged(context) } },
        )
    }
}

/**
 * 通知权限申请：API 33+ 且未授予才弹（模式同 CampusCardSettingsScreen）。
 *
 * 2026-09-30 起的 `internal` 可见性不变：问渠设置页（`ui/wenqu/WenquSettingsScreen.kt`）
 * 与考试设置页的开提醒也要同一份口径，别再复制一处。S10 搬迁时本函数从
 * `ScoreSettingsScreen.kt` 原样移到本文件（包名不变，调用点 import 不用改）。
 */
internal fun maybeRequestNotifPermission(
    context: Context,
    launcher: ActivityResultLauncher<String>,
) {
    if (Build.VERSION.SDK_INT >= 33 &&
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS,
        ) != PackageManager.PERMISSION_GRANTED
    ) {
        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
