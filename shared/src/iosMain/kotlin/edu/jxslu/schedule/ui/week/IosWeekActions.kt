package edu.jxslu.schedule.ui.week

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import edu.jxslu.schedule.domain.ScheduleExporter
import edu.jxslu.schedule.ui.platform.CalendarSyncOutcome
import edu.jxslu.schedule.ui.platform.rememberFileSaver
import edu.jxslu.schedule.ui.platform.rememberTextFilePicker

/**
 * iOS 课表页要宿主代劳的三件平台事（2026-11-26 晚接线）。
 *
 * Android 的对应物是 `app/.../ui/week/WeekScreenPlatform.kt` 的 `WeekScreenPlatformActions`。
 * 两端字段逐个同形，因为消费方是同一个 [WeekScreen]：
 *
 * | 字段 | iOS 实现 |
 * |---|---|
 * | [pickJsonFile] | 转发 [rememberTextFilePicker]（`UIDocumentPickerViewController`） |
 * | [saveTextFile] | 转发 [rememberFileSaver]（写沙箱 `Documents/exports/`，CSV / JSON 两个实例） |
 * | [syncCalendar] | [rememberIosCalendarSync]（EventKit） |
 *
 * ## 为什么要多存两个「待办」变量
 *
 * 两个平台缝的回调都必须在**组合期**交给组件（`rememberTextFilePicker` / `rememberFileSaver`
 * 只在建实例时收一次回调），而页面这边是**每次点击**一个新闭包（谁能拿到结果只有点击方知道）。
 * 所以这里只留一个「最新一次点击的回调」槽，缝那边回调进来时读槽、清槽——与 Android 那份
 * `WeekScreenPlatformActions` 逐行同构，别在这里另发明一种。
 *
 * ## 与 Android 的一处差别
 *
 * Android 的 `syncCalendar` 先查运行时权限、缺了先申请；iOS 的授权是系统弹窗、由 EventKit
 * 自己发起（`rememberIosCalendarSync` 内部处理 `NotDetermined` / 拒绝两档），所以这里不需要
 * 权限 launcher，也没有 `PendingCalendarSync` 那套状态。
 */
class IosWeekActions(
    /** 选一个文本文件；回调给 null = 用户取消或读不出来。 */
    val pickJsonFile: (onText: (String?) -> Unit) -> Unit,
    /** 另存一段文本，按 mime 分派到 CSV / JSON 两个 saver 实例。 */
    val saveTextFile:
        (fileName: String, mimeType: String, text: String, onSaved: (Boolean) -> Unit) -> Unit,
    /** 申请日历访问并写入系统日历；结果通过 `onResult` 回传。 */
    val syncCalendar: (
        events: List<ScheduleExporter.CourseEvent>,
        reminderMinutes: Int,
        onResult: (CalendarSyncOutcome) -> Unit,
    ) -> Unit,
)

/** 建一份 [IosWeekActions]；与课表页同寿（离开页面时 saver / picker 一起回收）。 */
@Composable
fun rememberIosWeekActions(): IosWeekActions {
    // --- 选 JSON 文件：槽里放「这一次点击」的回调 ---
    var pendingPick by remember { mutableStateOf<((String?) -> Unit)?>(null) }
    val launchPicker = rememberTextFilePicker(
        onPicked = { text ->
            val callback = pendingPick
            pendingPick = null
            callback?.invoke(text)
        },
    )

    // --- 另存文本：mime 在组合期固化，所以仍是两个实例（与 Android 的两个合同一一对应）---
    var pendingSave by remember { mutableStateOf<((Boolean) -> Unit)?>(null) }
    fun finishSave(saved: Boolean) {
        val callback = pendingSave
        pendingSave = null
        callback?.invoke(saved)
    }
    val csvSaver = rememberFileSaver("text/csv") { finishSave(it) }
    val jsonSaver = rememberFileSaver("application/json") { finishSave(it) }

    val syncCalendar = rememberIosCalendarSync()

    return remember(launchPicker, csvSaver, jsonSaver, syncCalendar) {
        IosWeekActions(
            pickJsonFile = { onText ->
                pendingPick = onText
                launchPicker()
            },
            saveTextFile = { fileName, mimeType, text, onSaved ->
                pendingSave = onSaved
                if (mimeType == "text/csv") csvSaver(fileName, text) else jsonSaver(fileName, text)
            },
            syncCalendar = syncCalendar,
        )
    }
}
