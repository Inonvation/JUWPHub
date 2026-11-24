package edu.jxslu.schedule.ui.me

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import edu.jxslu.schedule.data.calendar.CalendarSyncer
import edu.jxslu.schedule.domain.ShortcutItem
import edu.jxslu.schedule.ui.common.AppPermissions
import edu.jxslu.schedule.ui.common.NoticeFeedback
import edu.jxslu.schedule.ui.common.NoticeTone
import edu.jxslu.schedule.ui.common.ShortcutLauncher
import edu.jxslu.schedule.ui.platform.CalendarSyncOutcome
import edu.jxslu.schedule.ui.reminder.ClassReminder
import edu.jxslu.schedule.ui.score.maybeRequestNotifPermission

/**
 * 「我的」三个设置子页搬进共享层之后留在 `:app` 的平台动作（iOS 移植 W1，2026-11-25）。
 *
 * 口径同 `ui/score/ScoreScreenPlatform.kt` 与 `ui/week/WeekScreenPlatform.kt`：
 * 共享页只认可空回调 / 槽位，`null` 的语义是「本平台没有这项能力」，入口整行不渲染；
 * 这里的每个字段都必须是**真的能执行**的，不要为了凑参数塞空 lambda。
 *
 * | 工厂 | 喂给谁 | 平台物 |
 * |---|---|---|
 * | [rememberCalendarSyncActions] | `CalendarSettingsScreen`（VM 构造 + 权限行） | `CalendarSyncer`（`CalendarContract`）、`AppPermissions.calendar` |
 * | [rememberReminderScreenActions] | `ReminderSettingsScreen` | `ClassReminder`（`AlarmManager` + 通知渠道）、`POST_NOTIFICATIONS` |
 * | [rememberShortcutScreenActions] | `ShortcutSettingsScreen` | `ShortcutLauncher`（三种 `Intent` 拉起）、`PackageManager` 查应用 + `Drawable → Bitmap` |
 */

/**
 * 系统日历读写：权限 + 写入 + 删除。
 *
 * `sync` / `deleteSynced` 是**同步阻塞**调用（写整学期事件要好几秒），与搬迁前的行为一致：
 * 原来就写在 `viewModelScope.launch { CalendarSyncer.sync(...) }` 里，而 `viewModelScope`
 * 是 `Dispatchers.Main.immediate`——这里不再额外加线程切换，免得同一件事两套调度。
 */
@Composable
fun rememberCalendarSyncActions(): CalendarSyncActions {
    val context = LocalContext.current
    // launcher 只在组合期建一次，而「这一次申请的回调」每次点击才换：照 WeekScreenPlatform 存一个槽
    val pendingResult = remember { mutableStateOf<((Boolean) -> Unit)?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val granted = grants.values.all { it }
        val callback = pendingResult.value
        pendingResult.value = null
        callback?.invoke(granted)
    }

    return remember(context) {
        CalendarSyncActions(
            isGranted = { AppPermissions.missing(context, AppPermissions.calendar).isEmpty() },
            request = { onResult ->
                val needed = AppPermissions.missing(context, AppPermissions.calendar)
                if (needed.isEmpty()) {
                    // 已经给过：直接回「授予」，别再弹一次（系统会当拒绝处理）
                    onResult(true)
                } else {
                    pendingResult.value = onResult
                    permissionLauncher.launch(needed.toTypedArray())
                }
            },
            sync = { events, reminderMinutes ->
                runCatching { CalendarSyncer.sync(context, events, reminderMinutes) }
                    .fold({ it.toOutcome() }, { CalendarSyncOutcome.Error(it.message ?: "未知错误") })
            },
            deleteSynced = {
                runCatching { CalendarSyncer.deleteSynced(context) }
                    .fold({ it.toOutcome() }, { CalendarSyncOutcome.Error(it.message ?: "未知错误") })
            },
        )
    }
}

/** `CalendarSyncer.CalendarSyncResult` → 共享层的 [CalendarSyncOutcome]（`ReminderMissing` 是条数）。 */
private fun CalendarSyncer.CalendarSyncResult.toOutcome(): CalendarSyncOutcome = when (this) {
    is CalendarSyncer.CalendarSyncResult.Success ->
        CalendarSyncOutcome.Synced(count, reminderMissing)
    is CalendarSyncer.CalendarSyncResult.Deleted -> CalendarSyncOutcome.Deleted(count)
    is CalendarSyncer.CalendarSyncResult.NoCalendarAccount -> CalendarSyncOutcome.NoCalendarAccount
    is CalendarSyncer.CalendarSyncResult.NoPermission -> CalendarSyncOutcome.NoPermission
    is CalendarSyncer.CalendarSyncResult.Error -> CalendarSyncOutcome.Error(message)
}

/** 提醒页要宿主代劳的三件：重排闹钟 / 核对作业提醒 / 申请通知权限。 */
class ReminderScreenActions(
    /** 开关或提前量变化后重排上课闹钟（关闭时撤销已排的）。 */
    val reschedule: suspend () -> Unit,
    /** 作业提醒开关变化后核对一次待提醒作业。 */
    val recheckHomework: suspend () -> Unit,
    /** 申请 `POST_NOTIFICATIONS`；拒绝时回调 `onDenied`。 */
    val requestNotifPermission: (onDenied: () -> Unit) -> Unit,
)

@Composable
fun rememberReminderScreenActions(): ReminderScreenActions {
    val context = LocalContext.current
    // 权限回调在组合之外，读不到调用时刻的 lambda，只能先存一份（同 ScoreScreenActions）
    val pendingDenied = remember { mutableStateOf<(() -> Unit)?>(null) }
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (!granted) pendingDenied.value?.invoke()
    }

    return remember(context) {
        ReminderScreenActions(
            reschedule = { ClassReminder.scheduleNext(context) },
            recheckHomework = { ClassReminder.enqueueCheck(context) },
            requestNotifPermission = { onDenied ->
                pendingDenied.value = onDenied
                maybeRequestNotifPermission(context, notifPermissionLauncher)
            },
        )
    }
}

/** 快捷方式页要宿主代劳的两件：测试拉起 / 读已安装应用。 */
class ShortcutScreenActions(
    /** 用当前表单值即时拉起；返回值作为行内反馈回给编辑弹层。 */
    val onTest: (ShortcutItem) -> NoticeFeedback,
    /** 读有桌面入口的应用；图标已转成 `ImageBitmap`。 */
    val onQueryInstalledApps: suspend () -> List<InstalledAppOption>,
)

@Composable
fun rememberShortcutScreenActions(): ShortcutScreenActions {
    val context = LocalContext.current
    return remember(context) {
        ShortcutScreenActions(
            onTest = { item ->
                // 测试不落盘：用当前表单值直接拉起，结果与今日页同一执行层
                ShortcutLauncher.launch(context, item)?.let { NoticeFeedback(it, NoticeTone.Error) }
                    ?: NoticeFeedback("已启动「${item.name}」", NoticeTone.Success)
            },
            onQueryInstalledApps = { installedAppOptions(context) },
        )
    }
}

/**
 * 有桌面入口的应用；排除自身，按应用名排序。
 * Manifest 的 MAIN/LAUNCHER queries 是可见性前提（`<queries>` 里声明了那条 intent）。
 */
private fun installedAppOptions(context: Context): List<InstalledAppOption> {
    val pm = context.packageManager
    val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(main, 0).asSequence()
        .mapNotNull { ri ->
            val pkg = ri.activityInfo?.packageName ?: return@mapNotNull null
            if (pkg == context.packageName) return@mapNotNull null
            val label = try {
                ri.loadLabel(pm)?.toString().orEmpty()
            } catch (_: Exception) {
                ""
            }
            if (label.isBlank()) return@mapNotNull null
            InstalledAppOption(label, pkg, drawableToBitmap(ri.loadIcon(pm)).asImageBitmap())
        }
        .distinctBy { it.pkg }
        .sortedBy { it.label.lowercase() }
        .toList()
}

/** 应用图标 Drawable → Bitmap（accompanist 的 DrawablePainter 不在依赖里，不值得为它加）。 */
private fun drawableToBitmap(drawable: Drawable): Bitmap {
    if (drawable is android.graphics.drawable.BitmapDrawable && drawable.bitmap != null) {
        return drawable.bitmap
    }
    val width = maxOf(drawable.intrinsicWidth, 1)
    val height = maxOf(drawable.intrinsicHeight, 1)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return bitmap
}
