package edu.jxslu.schedule.ui.me

import edu.jxslu.schedule.domain.ScheduleExporter
import edu.jxslu.schedule.ui.platform.CalendarSyncOutcome

/**
 * 「我的 → 日历同步」要宿主代劳的系统日历能力（iOS 移植 W1，2026-11-25）。
 *
 * ## 为什么收成一组回调
 *
 * 页面与 ViewModel 已搬进 `shared/commonMain`，那里没有 `Context` / `ContentResolver` /
 * `ActivityResultLauncher`：写入走 Android 的 `CalendarContract`（`:app` 的 `CalendarSyncer`），
 * 权限是 Android 运行时权限。按 `design/ios-port.md` §11.2 ④ 收成可空槽位，
 * **null = 本平台没有系统日历**（iOS 目前没有 EventKit 实现）——此时页面上
 * 「同步 / 一键删除 / 日历读写权限」三行整行不渲染，只留「提醒时长」。
 *
 * ## 为什么只有 [request] 是回调
 *
 * 权限申请必须在组合期交给 `rememberLauncherForActivityResult`，结果只能从 launcher 回调里回来，
 * 所以它是回调形状（并且**必须恰好回调一次**，否则页面停在「未授予」）。
 * [sync] / [deleteSynced] 则本来就是挂起调用（`CalendarSyncer` 内部已切 `Dispatchers.IO`），
 * 保持挂起形状即可，结果类型用两端都能表达的 [CalendarSyncOutcome]
 * （`:app` 的 `CalendarSyncer.CalendarSyncResult` 由宿主翻译，共享层看不见它）。
 */
class CalendarSyncActions(
    /** 日历读写权限当前是否已授予（`AppPermissions.missing(context, calendar).isEmpty()`）。 */
    val isGranted: () -> Boolean,
    /** 申请日历读写权限；结果从 launcher 回来时回调一次。 */
    val request: (onResult: (Boolean) -> Unit) -> Unit,
    /** 把 [events] 写进系统日历，提前 [reminderMinutes] 分钟提醒。 */
    val sync: suspend (
        events: List<ScheduleExporter.CourseEvent>,
        reminderMinutes: Int,
    ) -> CalendarSyncOutcome,
    /** 删掉本应用写入的全部日历事件。 */
    val deleteSynced: suspend () -> CalendarSyncOutcome,
)
