package edu.jxslu.schedule.ui.week

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import edu.jxslu.schedule.Graph
import edu.jxslu.schedule.data.calendar.CalendarSyncer
import edu.jxslu.schedule.domain.ScheduleExporter
import edu.jxslu.schedule.ui.common.AppPermissions
import edu.jxslu.schedule.ui.platform.CalendarSyncOutcome
import edu.jxslu.schedule.ui.platform.rememberFileSaver
import edu.jxslu.schedule.ui.platform.rememberTextFilePicker
import kotlinx.coroutines.launch

/**
 * 课表页要宿主代劳的那几件平台事（iOS 移植 M1，2026-11-25）。
 *
 * ## 四个字段里两个已经搬到共享层
 *
 * `WeekScreen` 在 `shared/commonMain`，那里没有 `Context` / `Uri` / `ActivityResultLauncher`。
 * 原来内联在页面里的四段平台代码被收成这组回调。其中**选文件与另存**两端都要，已经上移成
 * `ui/platform/FilePickers.kt` 的 expect/actual（iOS 移植 S8，2026-11-25）；这里只剩转发——
 * 对外形状（4 个字段、`MainActivity` 的调用点）保持不变，是为了让课表页与本文件一起演进时
 * 不用再动调用方。
 *
 * | 字段 | 现在在哪 |
 * |---|---|
 * | [pickJsonFile] | 转发 `rememberTextFilePicker`（SAF `OpenDocument`，iOS 是 `UIDocumentPickerViewController`） |
 * | [saveTextFile] | 转发 `rememberFileSaver`（SAF `CreateDocument(mime)`，iOS 落沙箱 `exports/`） |
 * | [syncCalendar] | **仍在本文件**：`CalendarSyncer` 吃 `Context` + `ContentResolver`，`AppPermissions` 是 Android 运行时权限，iOS 没有对应物（`CalendarSyncOutcome.Unsupported`） |
 * | [backgroundExists] | **仍在本文件**：`Graph.scheduleBackground` 还是 Android 实现 |
 *
 * 后两件留在这里是「这个平台确实没有等价能力」，不是还没搬完——把它们也做成 expect
 * 只会多一层必然返回 `Unsupported` / `false` 的空实现。
 *
 * ## 转发为什么还要多存两个「待办」变量
 *
 * 新缝的回调必须在**组合期**交给 launcher（`rememberLauncherForActivityResult` 只在建 launcher
 * 时收一次 `onResult`），而这里的回调是**每次点击**换一个的闭包（谁能拿到结果只有点击方知道）。
 * 所以本文件只留一个「最新一次点击的回调」槽，缝那边回调进来时读槽、清槽。
 */
class WeekScreenPlatformActions(
    /** 选一个文本文件；读不出来给 null。 */
    val pickJsonFile: (onText: (String?) -> Unit) -> Unit,
    /** 另存一段文本，按 mime 分派到两个不同的 saver 实例。 */
    val saveTextFile:
        (fileName: String, mimeType: String, text: String, onSaved: (Boolean) -> Unit) -> Unit,
    /** 申请日历权限并写入系统日历。 */
    val syncCalendar:
        (
            events: List<ScheduleExporter.CourseEvent>,
            reminderMinutes: Int,
            onResult: (CalendarSyncOutcome) -> Unit,
        ) -> Unit,
    /** 背景图文件是否还在磁盘上。 */
    val backgroundExists: (String) -> Boolean,
)

/** 待跑的一次日历同步：权限对话框返回后才知道该不该跑。 */
private class PendingCalendarSync(
    val events: List<ScheduleExporter.CourseEvent>,
    val reminderMinutes: Int,
    val onResult: (CalendarSyncOutcome) -> Unit,
)

@Composable
fun rememberWeekScreenPlatformActions(): WeekScreenPlatformActions {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // --- 选 JSON 文件：转发给共享层的缝，槽里放「这一次点击」的回调 ---
    var pendingPick by remember { mutableStateOf<((String?) -> Unit)?>(null) }
    val launchPicker = rememberTextFilePicker(
        onPicked = { text ->
            val callback = pendingPick
            pendingPick = null
            callback?.invoke(text)
        },
    )

    // --- 另存文本：mime 在建 launcher 时就固化，所以仍是两个实例（与搬迁前的两个合同一一对应）---
    var pendingSave by remember { mutableStateOf<((Boolean) -> Unit)?>(null) }
    fun finishSave(saved: Boolean) {
        val callback = pendingSave
        pendingSave = null
        callback?.invoke(saved)
    }
    val csvSaver = rememberFileSaver("text/csv") { finishSave(it) }
    val jsonSaver = rememberFileSaver("application/json") { finishSave(it) }

    // --- 日历同步：先看权限，缺了先申请，授予后再跑 ---
    var pendingSync by remember { mutableStateOf<PendingCalendarSync?>(null) }
    // 权限回调里要跑的那一段：单独存一份，避免在 launcher 回调里直接读可能已过期的组合状态
    var runAfterGrant by remember { mutableStateOf<(() -> Unit)?>(null) }
    val calendarPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val granted = grants.values.all { it }
        val pending = pendingSync
        val runner = runAfterGrant
        pendingSync = null
        runAfterGrant = null
        if (granted) {
            runner?.invoke()
        } else {
            // 拒绝也要有反馈：页面侧已在发起前关掉分享弹层，这条提示才看得见
            pending?.onResult?.invoke(CalendarSyncOutcome.NoPermission)
        }
    }

    fun runCalendarSync(pending: PendingCalendarSync) {
        scope.launch {
            val outcome = when (val r = CalendarSyncer.sync(context, pending.events, pending.reminderMinutes)) {
                is CalendarSyncer.CalendarSyncResult.Success ->
                    CalendarSyncOutcome.Synced(r.count, r.reminderMissing)
                is CalendarSyncer.CalendarSyncResult.Deleted ->
                    CalendarSyncOutcome.Deleted(r.count)
                is CalendarSyncer.CalendarSyncResult.NoCalendarAccount ->
                    CalendarSyncOutcome.NoCalendarAccount
                is CalendarSyncer.CalendarSyncResult.NoPermission ->
                    CalendarSyncOutcome.NoPermission
                is CalendarSyncer.CalendarSyncResult.Error ->
                    CalendarSyncOutcome.Error(r.message)
            }
            pending.onResult(outcome)
        }
    }

    val backgroundStore = remember(context) { Graph.scheduleBackground(context) }

    return remember(context, launchPicker, csvSaver, jsonSaver) {
        WeekScreenPlatformActions(
            pickJsonFile = { onText ->
                pendingPick = onText
                launchPicker()
            },
            saveTextFile = { fileName, mimeType, text, onSaved ->
                pendingSave = onSaved
                if (mimeType == "text/csv") csvSaver(fileName, text) else jsonSaver(fileName, text)
            },
            syncCalendar = { events, reminderMinutes, onResult ->
                val pending = PendingCalendarSync(events, reminderMinutes, onResult)
                val needed = AppPermissions.missing(context, AppPermissions.calendar)
                if (needed.isEmpty()) {
                    runCalendarSync(pending)
                } else {
                    pendingSync = pending
                    runAfterGrant = { runCalendarSync(pending) }
                    calendarPermissionLauncher.launch(needed.toTypedArray())
                }
            },
            backgroundExists = { name -> backgroundStore.exists(name) },
        )
    }
}
