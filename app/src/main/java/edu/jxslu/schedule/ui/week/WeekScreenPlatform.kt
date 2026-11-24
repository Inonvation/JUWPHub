package edu.jxslu.schedule.ui.week

import android.net.Uri
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
import edu.jxslu.schedule.ui.common.readTextFromUri
import edu.jxslu.schedule.ui.platform.CalendarSyncOutcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 课表页要宿主代劳的那几件平台事（iOS 移植 M1，2026-11-25）。
 *
 * ## 为什么从 WeekScreen 里搬出来
 *
 * `WeekScreen` 已在 `shared/commonMain`，那里没有 `Context` / `Uri` / `ActivityResultLauncher`。
 * 原来内联在页面里的四段平台代码——SAF 选文件、SAF 另存、申请日历权限再写日历、
 * 查背景图文件在不在——原样搬到这个文件，**行为一字不改**（连"先关分享弹层再申请权限"
 * 那种顺序细节都还是原来的位置，只是现在由页面侧在调用前关）。
 *
 * ## 为什么用「一组回调」而不是 expect/actual
 *
 * 这些动作全都需要 `rememberLauncherForActivityResult`，也就是**必须在组合里创建**；
 * 而共享层能给的东西只有显式参数。把包装成 `expect @Composable` 反而要在
 * `shared/androidMain` 里重新声明一遍 `Context` / `androidx.core` 依赖，
 * 还得多搭一层「共享层注册、`:app` 填实现」的钩子——比直接把回调传下去复杂得多。
 *
 * 换来的代价是：iOS 侧没有这一组回调，于是课表页的 JSON 导入、CSV/JSON 导出、
 * 日历同步这三个入口**整行不出现**（见 `WeekScreen` 头部的参数表）。
 */
class WeekScreenPlatformActions(
    /** 选一个文本文件（SAF `OpenDocument`）；读不出来给 null。 */
    val pickJsonFile: (onText: (String?) -> Unit) -> Unit,
    /** 另存一段文本（SAF `CreateDocument`），按 mime 分派到两个合同上。 */
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

/** 一次性待写入内容：`CreateDocument` 的回调里拿不到调用时刻的文本，只能先存下来。 */
private class PendingSave(val text: String, val onSaved: (Boolean) -> Unit)

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

    // --- 选 JSON 文件 ---
    var pendingPick by remember { mutableStateOf<((String?) -> Unit)?>(null) }
    val jsonPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        val callback = pendingPick
        pendingPick = null
        if (uri == null) {
            callback?.invoke(null)
        } else {
            // 读文件必须离开主线程：大备份 JSON 有几 MB，读在主线程会掉帧（搬迁前同口径）
            scope.launch { callback?.invoke(readTextFromUri(context, uri)) }
        }
    }

    // --- 另存文本（CSV / JSON 各一个合同，mime 在建 launcher 时就固定）---
    var pendingSave by remember { mutableStateOf<PendingSave?>(null) }
    fun writeTo(uri: Uri?) {
        val pending = pendingSave
        pendingSave = null
        if (pending == null) return
        if (uri == null) {
            pending.onSaved(false)
            return
        }
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                        out.write(pending.text.toByteArray(Charsets.UTF_8))
                    } != null
                }.getOrDefault(false)
            }
            pending.onSaved(ok)
        }
    }
    val csvSaver = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri -> writeTo(uri) }
    val jsonSaver = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> writeTo(uri) }

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

    return remember(context) {
        WeekScreenPlatformActions(
            pickJsonFile = { onText ->
                pendingPick = onText
                jsonPicker.launch(
                    arrayOf("application/json", "text/plain", "application/octet-stream", "*/*"),
                )
            },
            saveTextFile = { fileName, mimeType, text, onSaved ->
                pendingSave = PendingSave(text, onSaved)
                if (mimeType == "text/csv") csvSaver.launch(fileName) else jsonSaver.launch(fileName)
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
