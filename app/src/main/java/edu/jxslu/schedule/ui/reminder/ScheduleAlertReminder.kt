package edu.jxslu.schedule.ui.reminder

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import edu.jxslu.schedule.Graph
import edu.jxslu.schedule.R
import edu.jxslu.schedule.SubpageRequest
import edu.jxslu.schedule.SubpageScreen
import edu.jxslu.schedule.data.repo.PendingLaunch
import edu.jxslu.schedule.data.repo.PendingNotification
import edu.jxslu.schedule.data.repo.ScheduleSync
import edu.jxslu.schedule.data.repo.ScheduleTermImport
import edu.jxslu.schedule.domain.AiSchedulePrompt
import edu.jxslu.schedule.domain.Course
import edu.jxslu.schedule.domain.ScheduleDiff
import edu.jxslu.schedule.domain.TermNaming
import edu.jxslu.schedule.subpageLaunchIntent

/**
 * 调课提醒（DESIGN §4.17，2026-10-04 重立）：教务课表与基线比对出差异时发一条通知。
 *
 * 编排搭 [ScoreAlertReminder] 的周期任务（第四路，间隔共用 `alert_interval_hours`，
 * 不为新链单开 WorkManager 任务——2026-09-24 下线的旧版带着后台自动登录的成本，
 * 这一版只在既有核对醒来时顺带查一次）。闸门在 [ScheduleSync.check]（开关 + 间隔 +
 * 成功才落时刻），本类只管「拿到差异后发通知、点通知去哪」。
 *
 * 三条口径（改之前先读）：
 * 1. **首跑 / 换学期 / 重复差异不通知**：`ScheduleSync.check` 返回的 changes 已经
 *    滤掉这些情形（空列表），这里只按非空差异发；
 * 2. **绝不写库**：点通知落处置页（`SubpageScreen.SCHEDULE_CHANGES`），差异列表、
 *    勾选合并、忽略都在那里由用户逐条决定——后台静默改课表是红线；
 * 3. **通知与 pending 缓存同生**：发通知的前提就是 `check` 写了新的待处置缓存，
 *    合并/忽略后缓存清空，同一次差异不会再发第二条。
 */
object ScheduleAlertReminder {

    private const val TAG = "ScheduleAlert"

    private const val CHANNEL_ID = "schedule_alert"

    /**
     * 通知 id/tag/requestCode 与成绩(1008/3008)、考试(1009/3009)、选课(1010/3010)错开。
     * requestCode **不能撞**：两条落点 intent 都无 action、同一个 SubpageActivity，
     * filterEquals 只看 action/data/component 不看 extra——requestCode 撞了会互相
     * 改写落点（1005/1006、3005/3006 两轮历史坑）。
     */
    private const val NOTIFICATION_ID = 1011
    private const val NOTIFICATION_TAG = "schedule_alert"
    private const val REQUEST_CODE = 3011

    /**
     * 学期归档完成（2026-11-19）：id/tag/requestCode 与差异、成绩(1008/3008)、考试(1009/3009)、
     * 选课(1010/3010)、U净(1011/3012)、问渠(1013/3013、1015/3014、1016/3015、1017/3016)
     * 全部错开——requestCode 撞了会互相改写通知落点。
     */
    private const val ARCHIVE_NOTIFICATION_ID = 1014
    private const val ARCHIVE_NOTIFICATION_TAG = "schedule_archive"
    private const val ARCHIVE_REQUEST_CODE = 3019

    /** 新学期课表已出（2026-11-19）：同上，另起一套 id/requestCode。 */
    private const val NEW_TERM_NOTIFICATION_ID = 1019
    private const val NEW_TERM_NOTIFICATION_TAG = "schedule_new_term"
    private const val NEW_TERM_REQUEST_CODE = 3020

    /** 通知正文最多列出的差异条数，多出的折叠。 */
    private const val MAX_LINES = 4

    /**
     * 周期核对的一路（由 `ScoreAlertReminder.check` 叫醒）。开关关着直接返回，
     * 一行网络请求都不发。
     */
    suspend fun check(context: Context) {
        when (val result = Graph.scheduleSync(context).check()) {
            is ScheduleSync.CheckResult.Ok -> {
                if (result.repeated || result.changes.isEmpty()) return
                // 展示单位是「一次调课」：单周调课在教务课表里是两行，按组计数
                // 才是用户看到的条数（与处置页、收件箱同一口径）
                val groups = ScheduleDiff.group(result.changes)
                // 免扰时段（DESIGN §3.7）：调课差异是事实型通知（eventAt=null），
                // 拦下入队待时段结束后补发。入箱在 ScheduleSync 内已完成
                val held = QuietHoursGate.intercept(
                    context,
                    PendingNotification(
                        tag = NOTIFICATION_TAG,
                        notificationId = NOTIFICATION_ID,
                        channelId = CHANNEL_ID,
                        category = NotificationCompat.CATEGORY_REMINDER,
                        title = if (groups.size == 1) "课程安排有变动"
                        else "有 ${groups.size} 处课程变动",
                        text = changesText(groups),
                        enqueuedAt = System.currentTimeMillis(),
                        launch = PendingLaunch(
                            kind = PendingLaunch.Kind.SUBPAGE,
                            requestCode = REQUEST_CODE,
                            screen = SubpageScreen.SCHEDULE_CHANGES.name,
                            clearTask = true,
                        ),
                    ),
                )
                if (!held) post(context, groups)
            }
            else -> Unit // Skipped / Failed 一律静默，失败闸门不落时刻
        }
    }

    /** 通知正文（拦截入队与即时发送共用；超出 [MAX_LINES] 折叠）。 */
    private fun changesText(groups: List<ScheduleDiff.ChangeGroup>): String {
        val lines = groups.take(MAX_LINES).map { group ->
            val move = group.move
            if (move != null) {
                // 与处置页「调课」卡同口径：只讲让出周次 → 目标块
                buildString {
                    append("调课：").append(move.to.name).append("（")
                    append(AiSchedulePrompt.weeksLabel(move.from.weeks)).append(' ')
                    append(slotText(move.from))
                    append(" → ")
                    append(AiSchedulePrompt.weeksLabel(move.to.weeks)).append(' ')
                    append(slotText(move.to))
                    append('）')
                }
            } else {
                val change = group.anchor
                val course = change.new ?: change.old
                val prefix = when (change.kind) {
                    ScheduleDiff.ChangeKind.MODIFIED -> "调整"
                    ScheduleDiff.ChangeKind.ADDED -> "新增"
                    ScheduleDiff.ChangeKind.REMOVED -> "删除"
                }
                buildString {
                    append(prefix).append('：').append(course?.name.orEmpty())
                    if (change.kind == ScheduleDiff.ChangeKind.MODIFIED) {
                        val o = change.old ?: return@buildString
                        append("（原 ").append(slotText(o)).append('）')
                    } else if (course != null) {
                        append("（").append(slotText(course)).append('）')
                    }
                }
            }
        }
        val rest = groups.size - lines.size
        return buildString {
            append(lines.joinToString("\n"))
            if (rest > 0) append("\n等 $rest 处")
        }
    }

    private fun slotText(course: Course): String =
        AiSchedulePrompt.weekdayLabel(course.day) + AiSchedulePrompt.sectionLabel(course)

    /** 通知：正文列「调课/调整/新增/删除：课程 摘要」，超出 [MAX_LINES] 折叠。点击落处置页。 */
    private fun post(context: Context, groups: List<ScheduleDiff.ChangeGroup>) {
        val text = changesText(groups)
        ensureChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_bell)
            .setContentTitle(if (groups.size == 1) "课程安排有变动" else "有 ${groups.size} 处课程变动")
            .setContentText(text.lines().firstOrNull().orEmpty())
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    REQUEST_CODE,
                    subpageLaunchIntent(context, SubpageRequest(SubpageScreen.SCHEDULE_CHANGES))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_TAG, NOTIFICATION_ID, notification)
        }.onFailure { Log.w(TAG, "notify schedule changes failed", it) }
    }

    /**
     * 学期归档完成的通知（DESIGN §4.17，2026-11-19）。
     *
     * 只给「老用户升级后首次冷启动自动补跑」这一条路用：新装用户在引导结果弹窗里已经
     * 看到，手动入口有页面气泡。入箱由 `ScheduleTermImport` 自己落（不依赖通知权限）。
     */
    suspend fun notifyArchive(context: Context, archived: ScheduleTermImport.Result.Archived) {
        if (archived.createdTerms.isEmpty()) return
        val earliest = archived.createdTerms.first()
        val latest = archived.createdTerms.last()
        val eLabel = TermNaming.gradeLabel(earliest, archived.firstTerm) ?: earliest
        val lLabel = TermNaming.gradeLabel(latest, archived.firstTerm) ?: latest
        val title = "已归档 ${archived.createdTerms.size} 个学期课表"
        val text = if (earliest == latest) {
            "$eLabel 课表已导入，可在课表管理里切换查看"
        } else {
            "$eLabel 至 $lLabel 课表已导入，可在课表管理里切换查看"
        }
        // 免扰时段（DESIGN §3.7）：事实型通知，拦下入队待时段结束后补发
        val held = QuietHoursGate.intercept(
            context,
            PendingNotification(
                tag = ARCHIVE_NOTIFICATION_TAG,
                notificationId = ARCHIVE_NOTIFICATION_ID,
                channelId = CHANNEL_ID,
                category = NotificationCompat.CATEGORY_REMINDER,
                title = title,
                text = text,
                enqueuedAt = System.currentTimeMillis(),
                launch = PendingLaunch(
                    kind = PendingLaunch.Kind.SUBPAGE,
                    requestCode = ARCHIVE_REQUEST_CODE,
                    screen = SubpageScreen.TIMETABLE_MANAGE.name,
                    clearTask = true,
                ),
            ),
        )
        if (held) return
        ensureChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_bell)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    ARCHIVE_REQUEST_CODE,
                    subpageLaunchIntent(context, SubpageRequest(SubpageScreen.TIMETABLE_MANAGE))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(ARCHIVE_NOTIFICATION_TAG, ARCHIVE_NOTIFICATION_ID, notification)
        }.onFailure { Log.w(TAG, "notify archive failed", it) }
    }

    /**
     * 新学期课表已出的通知（DESIGN §4.17，2026-11-19）：不带开关，只要系统通知权限。
     * 入箱由 `ScheduleTermImport.probeNextTerm` 自己落。
     */
    suspend fun notifyNewTerm(context: Context, term: String, label: String?, courseCount: Int) {
        val shown = label ?: term
        val title = "${shown}课表已出"
        val text = "教务已发布 $term 课表（共 $courseCount 门），已自动导入，可在课表管理里查看"
        val held = QuietHoursGate.intercept(
            context,
            PendingNotification(
                tag = NEW_TERM_NOTIFICATION_TAG,
                notificationId = NEW_TERM_NOTIFICATION_ID,
                channelId = CHANNEL_ID,
                category = NotificationCompat.CATEGORY_REMINDER,
                title = title,
                text = text,
                enqueuedAt = System.currentTimeMillis(),
                launch = PendingLaunch(
                    kind = PendingLaunch.Kind.SUBPAGE,
                    requestCode = NEW_TERM_REQUEST_CODE,
                    screen = SubpageScreen.TIMETABLE_MANAGE.name,
                    clearTask = true,
                ),
            ),
        )
        if (held) return
        ensureChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_bell)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    NEW_TERM_REQUEST_CODE,
                    subpageLaunchIntent(context, SubpageRequest(SubpageScreen.TIMETABLE_MANAGE))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(NEW_TERM_NOTIFICATION_TAG, NEW_TERM_NOTIFICATION_ID, notification)
        }.onFailure { Log.w(TAG, "notify new term failed", it) }
    }

    private fun ensureChannel(context: Context) {
        // IMPORTANCE_DEFAULT：课表变动不是「马上要做的事」，不值得横幅打断
        ensureReminderChannel(
            context,
            CHANNEL_ID,
            "课表变动提醒",
            NotificationManager.IMPORTANCE_DEFAULT,
            "教务课表与你的课表出现差异、或下学期课表已出时提醒",
        )
    }
}
