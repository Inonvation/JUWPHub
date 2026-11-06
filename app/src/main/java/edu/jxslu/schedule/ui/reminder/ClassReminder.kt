package edu.jxslu.schedule.ui.reminder

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import edu.jxslu.schedule.Graph
import edu.jxslu.schedule.MainActivity
import edu.jxslu.schedule.R
import edu.jxslu.schedule.SubpageRequest
import edu.jxslu.schedule.SubpageScreen
import edu.jxslu.schedule.subpageLaunchIntent
import edu.jxslu.schedule.data.repo.ScheduleRepository
import edu.jxslu.schedule.data.prefs.ReminderKeyKind
import edu.jxslu.schedule.data.repo.PendingLaunch
import edu.jxslu.schedule.data.repo.PendingNotification
import edu.jxslu.schedule.domain.ReminderPlan
import edu.jxslu.schedule.domain.Course
import edu.jxslu.schedule.domain.TimeSlot
import edu.jxslu.schedule.domain.dueReminderPlan
import edu.jxslu.schedule.domain.dueClassStartPlan
import edu.jxslu.schedule.domain.metaLine
import edu.jxslu.schedule.domain.nextHomeworkReminder
import edu.jxslu.schedule.domain.HomeworkReminderPlan
import edu.jxslu.schedule.domain.homeworkReminderGroup
import edu.jxslu.schedule.domain.homeworkDisplayTitle
import edu.jxslu.schedule.domain.nextClassStartPlan
import edu.jxslu.schedule.domain.nextReminderPlan
import edu.jxslu.schedule.domain.remarkPreview
import edu.jxslu.schedule.domain.liveChipText
import edu.jxslu.schedule.domain.liveClassLine
import edu.jxslu.schedule.domain.minutesLeft
import edu.jxslu.schedule.domain.progressPercent
import edu.jxslu.schedule.ui.wenqu.WenquPreregReminder
import edu.jxslu.schedule.ui.widget.TodayWidgetRefresh
import kotlinx.coroutines.flow.first
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * 提醒调度（DESIGN §3.7 上课提醒 + §3.11 作业截止提醒，两者共用一个闹钟）。
 *
 * 与桌面小组件（`ui/widget/TodayWidgetRefresh`）同构的三层兜底：
 * 边界闹钟（上课「提前量点 / 上课时刻」取更早的 / 作业「提醒点 20:00」，三者再取更早）为主，
 * WorkManager 15 分钟周期核对补发/补排，冷启动 / 数据变化 / 开机广播立即重排。
 * 闹钟用 `setAlarmClock`（2026-09-23 自 `setAndAllowWhileIdle` 改）：
 * 真机上提醒迟到几分钟是反复出现的问题——非精确闹钟可被 ROM 省电策略推迟。
 * `setAlarmClock` 是系统级精确闹钟（系统时钟应用同款），到点即触发、不受推迟，
 * 且不需要 SCHEDULE_EXACT_ALARM 等任何特殊权限；代价仅是触发时状态栏短暂
 * 显示闹钟图标，对「上课提醒」语义贴切。小组件刷新保持非精确闹钟不动（见 §3.6）。
 */
object ClassReminder {

    private const val TAG = "ClassReminder"
    private const val PERIODIC_WORK = "class_reminder_periodic"
    private const val ONE_SHOT_WORK = "class_reminder_check"
    private const val ALARM_REQUEST_CODE = 4002

    /**
     * 倒计时卡片「分钟刻度」的闹钟 requestCode（2026-11-06 从 [ALARM_REQUEST_CODE] 拆出来）。
     *
     * 与上课（4002）、小组件日界（4001）、骑行（4003）、选课（4004）、U净（4005）、
     * 免扰补发（4006）、问渠周提醒（4007）、问渠预报名（4008）都错开：
     * PendingIntent 的身份是「requestCode + Intent.filterEquals」，撞了会互相顶掉。
     * **别把它合回 4002**——两者要用不同的 AlarmManager API（见 [scheduleLiveTick]）。
     * 取 4009 而不是 4007：问渠周提醒（`WenquWeeklyAlertReminder`）已经占了 4007，
     * 号段登记表只能有一份真相（2026-11-06 交叉复核）。
     */
    private const val LIVE_TICK_REQUEST_CODE = 4009

    /**
     * 重排下一个提醒：上课（提前量 / 上课时刻）与作业两个来源取**更早**的触发时刻，
     * 排同一个精确闹钟；倒计时卡片的「分钟刻度」另排一枚非精确闹钟（见 [scheduleLiveTick]）。
     * 某一类开关关掉就忽略那一类；两者都没有（提醒关、课表空、不在学期内、没有待提醒作业）
     * 时撤销已排闹钟——「关掉开关后通知还在响」比不响严重得多。顺带：不在倒计时窗口里
     * 就撤掉岛上那张卡。
     */
    suspend fun scheduleNext(context: Context) {
        runCatching {
            // 渠道迁移（删旧建 v2）顺手挂在这里：冷启动 / 设置变更 / 核对后都会走到，
            // 升级后不用等下一次提醒真的触发，系统设置里就能看到带振动的 v2 渠道
            ReminderNotifications.ensureChannels(context)
            val repo = Graph.repository(context)
            val manager = context.getSystemService(AlarmManager::class.java)
                ?: return
            val pending = alarmPendingIntent(context)
            val tickPending = liveTickPendingIntent(context)
            val now = LocalDateTime.now()
            val reminderOn = repo.reminderEnabled.first()
            var livePlan: ReminderPlan? = null
            var liveTickAt: LocalDateTime? = null
            val classTriggerAt = if (reminderOn) {
                val semester = repo.semester.first()
                val slots = repo.timeSlots.first()
                val courses = repo.courses.first()
                val lead = repo.reminderLeadMinutes.first()
                // 日期覆盖（调休 / 停课，§4.40）：放假那天不排上课提醒，
                // 补课那天按借来的课排——与今日页同一口径
                val overrides = repo.overridesFor()
                // 灵动岛倒计时卡片（2026-10-29）：提前量窗口内每分钟边界醒一次，
                // 刷新芯片上的剩余分钟数与进度条。**单独一枚非精确闹钟**（2026-11-06），
                // 不再挤进下面这枚精确闹钟——理由见 [scheduleLiveTick]。
                livePlan = dueReminderPlan(semester, slots, courses, now, lead, overrides)
                liveTickAt = livePlan
                    ?.takeIf { ReminderNotifications.liveTickNeeded(context, repo, it) }
                    ?.let { nextMinuteBoundary(now) }
                // 上课提醒两个**精确**触发点（DESIGN §3.7）：提前量、上课时刻，取更早的排闹钟；
                // 核对 Worker 会先补发窗口内的、再重排
                listOfNotNull(
                    nextReminderPlan(
                        semester = semester,
                        slots = slots,
                        courses = courses,
                        now = now,
                        leadMinutes = lead,
                        overrides = overrides,
                    )?.triggerAt,
                    nextClassStartPlan(
                        semester = semester,
                        slots = slots,
                        courses = courses,
                        now = now,
                        overrides = overrides,
                    )?.startAt,
                ).minOrNull()
            } else null
            // 不在倒计时窗口里（提醒关 / 已上课 / 调休停课 / 换了课表）就别让岛上那张卡留着：
            // 设置页与数据变化只调这里、不调核对 Worker，撤卡不能只指望 Worker
            if (livePlan == null) ReminderNotifications.cancelLiveUpdate(context)
            // 分钟刻度：窗口内才排，出窗口（含提醒关掉）就撤。空跑是一次 cancel。
            scheduleLiveTick(manager, tickPending, liveTickAt)
            // 作业开关关 → 连库都不读（未完成列表要查 Room）；开着才求下一个作业提醒点
            val homeworkTriggerAt = if (repo.homeworkReminderEnabled.first()) {
                nextHomeworkReminder(
                    items = Graph.homeworkRepository(context).observePending().first(),
                    now = now,
                )?.triggerAt
            } else null
            val triggerAt = listOfNotNull(classTriggerAt, homeworkTriggerAt).minOrNull()
            if (triggerAt == null) {
                manager.cancel(pending)
                return
            }
            val triggerAtMillis = triggerAt
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            // 系统级精确闹钟：到点即触发，不受 Doze/省电推迟，无需特殊权限。
            // showIntent 交给系统时钟的闹钟页：用户点状态栏那枚闹钟图标时落到「下一个闹钟列表」，
            // 而不是本 App 的某个页面——那枚图标只表示"有个闹钟排着"，不是提醒本身
            val clockPending = PendingIntent.getActivity(
                context,
                0,
                Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            manager.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAtMillis, clockPending), pending)
        }.onFailure { Log.w(TAG, "schedule reminder failed", it) }
    }

    /** 一次性核对（闹钟触发 / 开机 / 设置变更落点）：BroadcastReceiver 里不能做长任务。 */
    fun enqueueCheck(context: Context) {
        val request = OneTimeWorkRequestBuilder<ReminderCheckWorker>()
            .setConstraints(Constraints.NONE)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(ONE_SHOT_WORK, ExistingWorkPolicy.REPLACE, request)
    }

    /** 15 分钟周期核对兜底：闹钟被 ROM 推迟/丢失时还能在窗口内补发。幂等（KEEP）。 */
    fun ensurePeriodicWork(context: Context) {
        val request = PeriodicWorkRequestBuilder<ReminderCheckWorker>(15, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(PERIODIC_WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    private fun alarmPendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        ALARM_REQUEST_CODE,
        Intent(context, ReminderAlarmReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** 分钟刻度的落点（与提醒闹钟分开的 PendingIntent，理由见 [scheduleLiveTick]）。 */
    private fun liveTickPendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        LIVE_TICK_REQUEST_CODE,
        Intent(context, ReminderAlarmReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /**
     * 倒计时卡片的「分钟刻度」闹钟：**非精确**（`setAndAllowWhileIdle`），[at] 为 null 时撤销。
     *
     * 为什么与提醒闹钟分开（2026-11-06）：刻度只为刷新灵动岛芯片上的「剩 N 分」与进度条，
     * 迟几十秒对用户没有任何意义；而旧实现把刻度塞进 `setAlarmClock`，代价是提前量
     * 10~30 分钟里每分钟排一枚**系统级**闹钟，状态栏那枚闹钟图标**常驻整个窗口**。
     * 真正的提醒点（提前量 / 上课时刻）仍用 `setAlarmClock`，那两处才「不能迟」。
     *
     * **代价没有全消**（2026-11-06 复核纠正）：刻度仍落 [ReminderAlarmReceiver]，
     * 每次依旧走「Receiver → 一次性 Worker → 读库 → 重排」整条核对链，只是不再占精确闹钟
     * 与状态栏图标。真要省这笔得给刻度单独一个只刷芯片 + 重排的轻量 Receiver，
     * 属另一件事（收益是每次省一次数据库读，风险是两条重排路径要各自正确）。
     *
     * **别把两者合回一枚闹钟**：`AlarmClockInfo` 只能表达一个触发时刻，合回去就得二选一
     * ——要么刻度拖着精确闹钟一起变非精确（提醒迟到，正是 2026-09-23 改 setAlarmClock 的原因），
     * 要么精确闹钟被刻度占满（状态栏图标常驻）。
     *
     * 已知代价：Doze 下 `setAndAllowWhileIdle` 被限制为每 9 分钟一次。那时屏幕是灭的，
     * 芯片本来就看不见，刻度丢几次不影响任何可感知行为；出 Doze / 亮屏后下一轮核对
     * （15 分钟 Worker 或任一数据变化）会立刻重排回逐分。
     */
    private fun scheduleLiveTick(
        manager: AlarmManager,
        pending: PendingIntent,
        at: LocalDateTime?,
    ) {
        if (at == null) {
            manager.cancel(pending)
            return
        }
        val millis = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        runCatching {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pending)
        }.onFailure { Log.w(TAG, "schedule live tick failed", it) }
    }

    /**
     * 下一个整分边界（严格晚于 [now]）。倒计时卡片的分钟刻度：芯片「剩 N 分」与
     * 进度条按整分翻转，闹钟落在 :00 秒上，芯片就不会「剩 8 分」停满 59 秒才变。
     *
     * 先截断到分再加一分钟，保证结果**严格大于** now（截断最多抹掉 59.999 秒，
     * 再加 60 秒），不会排出一个已经过去的时刻导致闹钟立即回环。
     */
    private fun nextMinuteBoundary(now: LocalDateTime): LocalDateTime =
        now.withSecond(0).withNano(0).plusMinutes(1)
}

/**
 * 通知的构建与去重落盘。同一节课只发一次：DataStore 记「已发键」，
 * 闹钟与周期核对共用；窗口已过（迟到型）不发，避免「已经上课了还提醒 10 分钟后上课」。
 *
 * 2026-09-21 起（DESIGN §3.11）这里也发作业截止提醒：通道/通知 id/tag/去重键
 * 都与上课提醒各自独立（作业是多条、两个提醒点，共用一套键会互相顶掉），
 * 但落点相同——都是核对 Worker 里先发后重排。
 */
internal object ReminderNotifications {

    // 渠道 id 用 _v2：旧 id 的渠道在用户设备上已按「无振动」建成，而渠道建出来后
    // vibration 就改不动（与免费时长提醒 _v2 同坑，见 EbikeFreeRide.NotificationIds 的 KDoc）。
    // 旧 id 只剩清理职责，见 ensureChannels。
    private const val CHANNEL_ID = "class_reminder_v2"
    private const val LEGACY_CHANNEL_ID = "class_reminder"
    private const val NOTIFICATION_TAG = "class_reminder"
    /** 固定 id：新提醒覆盖上一条，不堆叠。 */
    private const val NOTIFICATION_ID = 1001

    /** 「上课」通知（上课时刻触发，2026-09-22 新增）：固定 id 1004，与提前量（1001）、
     *  作业（1002/1003）互不覆盖。 */
    private const val CLASS_START_NOTIFICATION_ID = 1004

    /**
     * 倒计时卡片的上课时刻余量（2026-10-29）：上课后多久由系统兜底撤掉。
     * 正常路径是核对 Worker 在上课时刻那轮 [cancelLiveUpdate]；这个 timeout 只在
     * 「闹钟全丢 + App 被冻结」时兜底，别把它当主路径。
     */
    private const val LIVE_GRACE_MILLIS = 2 * 60 * 1000L

    /** 作业截止提醒通道（DESIGN §3.11）。与上课提醒同档 importance，用户可在系统设置降级。 */
    private const val HOMEWORK_CHANNEL_ID = "homework_deadline_v2"

    /** 作业渠道的旧 id（无振动版，见 [CHANNEL_ID] 的注释），只剩清理职责。 */
    private const val LEGACY_HOMEWORK_CHANNEL_ID = "homework_deadline"

    /** 单条作业提醒（只提醒到一条作业时）：点击直达该作业（DESIGN §3.11）。 */
    private const val HOMEWORK_NOTIFICATION_TAG = "homework_reminder"

    /** 固定 id 1002：与上课提醒（1001）不同，两者不得互相覆盖。 */
    private const val HOMEWORK_NOTIFICATION_ID = 1002

    /** 多条同时到点时的汇总提醒（2026-09-21，优化：不再一条一条发）点击进作业中心。 */
    private const val HOMEWORK_SUMMARY_TAG = "homework_reminder_summary"
    private const val HOMEWORK_SUMMARY_ID = 1003

    /**
     * 作业通知点击落点的 requestCode。**不能沿用 0**：PendingIntent 的身份是
     * 「requestCode + Intent.filterEquals」（不含 extras），而 `JwDetectNotifier`
     * 也用 SubpageActivity + requestCode 0 建 PendingIntent——两者会被系统视为同一个，
     * FLAG_UPDATE_CURRENT 会把彼此的 EXTRA_SCREEN 互相改掉（点了调课通知跳作业中心）。
     * 取与通知 id 同值便于排查。
     */
    private const val HOMEWORK_CONTENT_REQUEST_CODE = HOMEWORK_NOTIFICATION_ID

    /** 汇总通知的 requestCode：与单条（1002）不同，否则两条通知的落点会互相改写。 */
    private const val HOMEWORK_SUMMARY_REQUEST_CODE = HOMEWORK_SUMMARY_ID

    /** 汇总通知正文最多列几条标题（超出用「等 N 项」收口，别把通知撑成一屏）。 */
    private const val SUMMARY_TITLE_LIMIT = 3

    fun ensureChannel(context: Context) {
        ensureChannels(context, homework = false)
    }

    /** 两个渠道一起确保。冷启动重排（[ClassReminder.scheduleNext]）也调它：升级后旧渠道
     *  （无振动版）能尽快被 v2 取代，不用等下一次提醒真的触发。 */
    fun ensureChannels(context: Context) {
        ensureChannels(context, homework = true)
    }

    private fun ensureChannels(context: Context, homework: Boolean) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        // 旧渠道（2026-09-19 建的无振动版）清掉免得设置页留死通道；
        // delete 与 create 用的是不同 id，不会踩「delete 异步、紧接 create 被当成更新」的坑
        manager.deleteNotificationChannel(LEGACY_CHANNEL_ID)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "上课提醒",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "上课前按设定的提前量发出通知"
                enableVibration(true)
                vibrationPattern = ReminderVibration.PATTERN
            }
            manager.createNotificationChannel(channel)
        }
        if (!homework) return
        manager.deleteNotificationChannel(LEGACY_HOMEWORK_CHANNEL_ID)
        if (manager.getNotificationChannel(HOMEWORK_CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                HOMEWORK_CHANNEL_ID,
                "作业截止提醒",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "截止前一天 20:00 与截止当天 20:00 提醒未完成的作业"
                enableVibration(true)
                vibrationPattern = ReminderVibration.PATTERN
            }
            manager.createNotificationChannel(channel)
        }
    }

    /** 核对入口：上课先、作业后，两者互不阻塞（各自开关关掉时是空跑）。 */
    suspend fun postDueReminderIfAny(context: Context) {
        val repo = Graph.repository(context)
        postClassReminderIfAny(context, repo)
        postHomeworkReminderIfAny(context, repo)
    }

    private suspend fun postClassReminderIfAny(context: Context, repo: ScheduleRepository) {
        if (!repo.reminderEnabled.first()) {
            // 关开关后残留的倒计时卡片必须撤：关掉后通知还在，比不响严重得多
            cancelLiveUpdate(context)
            return
        }
        // 通知被系统/用户整体关闭时静默跳过：写不出去也不该在核对里报错
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        val slots = repo.timeSlots.first()
        val now = LocalDateTime.now()
        val semester = repo.semester.first()
        val courses = repo.courses.first()
        val lead = repo.reminderLeadMinutes.first()
        // 日期覆盖（调休 / 停课，§4.40）与排闹钟同口径（scheduleNext 已传）：
        // 这里是「要不要发」的最后一道闸，漏了它，AI 调课/校历调休把今天的课
        // 停掉后，周期核对仍按裸课表在原上课时间点发通知（2026-10-07 实报）。
        val overrides = repo.overridesFor()

        // 两个通知点各自核对、各自去重（DESIGN §3.7）：同一轮可能只命中一个
        // （两触发点相差一个提前量，通常分处两轮），也可能同时命中（闹钟被推迟跨过两点）。
        // 先算「上课开始」（更晚的点）：它的窗口不依赖提前量，先判可避免 lead=0 时
        // 两个窗口重叠导致的重复计算。
        val startDue = dueClassStartPlan(semester, slots, courses, now, overrides)
        val startKey = startDue?.dedupKey
        // 通知强提醒（2026-10-06，通用设置）：默认关，开了才在发出后直接震动
        val strongVibration = repo.strongReminderVibration.first()
        // 免扰拦截描述要存 epoch 毫秒（eventAt），LocalDateTime → Instant 需要时区
        val zone = ZoneId.systemDefault()
        if (startKey != null && repo.reminderLastKey(ReminderKeyKind.Start) != startKey) {
            // 免扰时段（DESIGN §3.7）：拦下入队待补发，拦截 = 已处理（照常落去重键），
            // 否则 15 分钟周期核对会在免扰期内反复入队
            val held = QuietHoursGate.intercept(
                context,
                PendingNotification(
                    tag = NOTIFICATION_TAG,
                    notificationId = CLASS_START_NOTIFICATION_ID,
                    channelId = CHANNEL_ID,
                    category = NotificationCompat.CATEGORY_EVENT,
                    title = "上课 · ${startDue.course.name}",
                    text = notificationLine(slots, startDue.course),
                    eventAt = startDue.startAt.atZone(zone).toInstant().toEpochMilli(),
                    enqueuedAt = System.currentTimeMillis(),
                    vibrate = strongVibration,
                    launch = PendingLaunch(kind = PendingLaunch.Kind.MAIN_PLAIN, requestCode = 0),
                ),
            )
            if (held) {
                repo.setReminderLastKey(ReminderKeyKind.Start, startKey)
                // 不 return：上课提醒被拦不等于倒计时卡片不用撤——上课时刻到了，
                // 岛上那张卡（若有）必须在同一轮里撤掉，否则要等 timeoutAfter 兜底
            } else {
                val posted = postClassNotification(
                    context,
                    id = CLASS_START_NOTIFICATION_ID,
                    title = "上课 · ${startDue.course.name}",
                    text = notificationLine(slots, startDue.course),
                    strongVibration = strongVibration,
                )
                if (posted) repo.setReminderLastKey(ReminderKeyKind.Start, startKey)
            }
        }

        syncLiveUpdate(
            context = context,
            repo = repo,
            plan = dueReminderPlan(semester, slots, courses, now, leadMinutes = lead, overrides = overrides),
            slots = slots,
            strongVibration = strongVibration,
        )
    }

    /**
     * 灵动岛倒计时卡片（DESIGN §3.7，2026-10-29）：提前量提醒不再是「发一条就走」的
     * 一次性通知，而是**常驻到上课时刻**的 Live Update——状态栏一枚芯片（`南B208·8分`）、
     * 下拉一张卡（课名 + 地点/上课时刻 + 系统倒计时 + 进度条）。
     *
     * 与提前量提醒共用同一条去重键（`Before`）：键已落 = 这节课的提醒处理过
     * （发过，或被免扰拦下）。**只有第一次发要响**（HIGH 渠道 + 直接震动），
     * 之后每分钟的刷新静默（`setOnlyAlertOnce` + 同一 id）。
     *
     * 三种「不上岛 / 撤岛」：
     * - 免扰时段拦下第一次发送 → 不补发（拦截 = 已处理，与旧提醒同口径）；
     * - 用户把岛划掉 → [isLivePosted] 为 false，**不再顶回去**（Live Updates 的硬要求：
     *   别把用户划掉的再弹回来）；
     * - [plan] 为 null（还没进提前量窗口 / 已上课 / 提醒关了 / 调休停课）→ 撤掉残留。
     *
     * [plan] 由 [dueReminderPlan] 给：它只看「还没开始、且在上课时刻 − 提前量之内」的课，
     * 上课那一刻自然变 null——这正是「倒计时归零即撤岛」的语义，不另写判定。
     */
    private suspend fun syncLiveUpdate(
        context: Context,
        repo: ScheduleRepository,
        plan: ReminderPlan?,
        slots: List<TimeSlot>,
        strongVibration: Boolean,
    ) {
        if (plan == null) {
            cancelLiveUpdate(context)
            return
        }
        val key = plan.dedupKey
        val now = LocalDateTime.now()
        if (repo.reminderLastKey(ReminderKeyKind.Before) == key) {
            // 已处理过：只有还挂在通知栏上才刷新。划掉的、被免扰拦下的都不再发
            if (isLivePosted(context)) postLiveUpdate(context, plan, slots, now)
            return
        }
        val held = QuietHoursGate.intercept(
            context,
            PendingNotification(
                tag = NOTIFICATION_TAG,
                notificationId = NOTIFICATION_ID,
                channelId = CHANNEL_ID,
                category = NotificationCompat.CATEGORY_EVENT,
                // 标题/正文与岛上那张卡同一口径：补发出来的是普通通知，
                // 不带相对时间（「20 分钟后上课」在补发时刻可能是假信息）
                title = plan.course.name,
                text = liveClassLine(slots, plan.course),
                eventAt = plan.startAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                enqueuedAt = System.currentTimeMillis(),
                vibrate = strongVibration,
                launch = PendingLaunch(kind = PendingLaunch.Kind.MAIN_PLAIN, requestCode = 0),
            ),
        )
        if (held) {
            repo.setReminderLastKey(ReminderKeyKind.Before, key)
            return
        }
        val posted = postLiveUpdate(context, plan, slots, now)
        if (posted) {
            repo.setReminderLastKey(ReminderKeyKind.Before, key)
            // 开了「通知强提醒」才直接震（口径同上课时刻通知，见 postClassNotification）
            if (strongVibration) ReminderVibration.vibrate(context)
        }
    }

    /**
     * 撤掉倒计时卡片（与提前量提醒同一个 tag/id）。没有这条通知时是空跑。
     * internal：`ClassReminder.scheduleNext` 在提醒被关掉时也要撤（它不走核对 Worker）。
     *
     * **只撤 `ongoing` 的那条**：免扰时段补发出来的提前量提醒复用同一个 tag/id，
     * 但它是普通通知（非 ongoing）——顺手撤掉会把刚补发的提醒吃掉。
     */
    internal fun cancelLiveUpdate(context: Context) {
        if (!isLivePosted(context)) return
        runCatching {
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_TAG, NOTIFICATION_ID)
        }.onFailure { Log.w("ClassReminder", "cancel live update failed", it) }
    }

    /** 本应用挂在通知栏上的那条上课类通知（固定 tag/id），没有则 null。 */
    private fun activeClassNotification(context: Context): StatusBarNotification? = runCatching {
        context.getSystemService(NotificationManager::class.java)
            ?.activeNotifications
            ?.firstOrNull { it.tag == NOTIFICATION_TAG && it.id == NOTIFICATION_ID }
    }.getOrNull()

    /**
     * 倒计时卡片现在是否挂在通知栏上（`ongoing` 才是它）。用来实现「用户划掉的
     * 不再顶回去」：每轮核对先问系统，没有就不发——不需要额外的已划掉标记，
     * 也不会因为进程重启而丢失（通知本身由系统持有）。
     */
    private fun isLivePosted(context: Context): Boolean =
        activeClassNotification(context)?.isOngoing == true

    /**
     * 分钟刻度要不要排（DESIGN §3.7）：只有「卡片看得见」或「这节课的提醒还没处理过」
     * 才值得每分钟唤醒一次核对。
     *
     * - 卡片在栏上 → 要刷新分钟数 / 进度条；
     * - 提醒还没处理（用户刚在提前量窗口里打开开关、闹钟漏了但 App 刚被打开）→
     *   先让下一轮核对把卡发出来；
     * - 用户划掉了 / 免扰时段拦下了 / 渠道被系统关掉 → **不再每分钟醒**：
     *   刻度只为看得见的卡片服务，否则提前量 30 分钟就是 30 次白唤醒。
     */
    internal suspend fun liveTickNeeded(
        context: Context,
        repo: ScheduleRepository,
        plan: ReminderPlan,
    ): Boolean = isLivePosted(context) || repo.reminderLastKey(ReminderKeyKind.Before) != plan.dedupKey

    private fun postLiveUpdate(
        context: Context,
        plan: ReminderPlan,
        slots: List<TimeSlot>,
        now: LocalDateTime,
    ): Boolean {
        ensureChannel(context)
        val notification = liveClassNotification(context, plan, slots, now)
        // 无 POST_NOTIFICATIONS 权限时系统静默丢弃；SecurityException 等异常也不外漏
        return runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_TAG, NOTIFICATION_ID, notification)
        }.isSuccess
    }

    /**
     * 倒计时卡片的构建（Android 16 上由系统提升为 Live Update / 灵动岛）。
     *
     * 上岛只要两件东西：`setRequestPromotedOngoing(true)` + manifest 里的
     * `POST_PROMOTED_NOTIFICATIONS`（普通权限，已在）。低版本设备这两项被系统忽略，
     * 同一份通知退化成「常驻 + 倒计时」的普通通知，不是分档实现。
     * 官方资格清单全部满足：`ongoing` ✓、有 contentTitle ✓、无自定义 RemoteViews ✓、
     * 非 group summary ✓、不 colorized ✓、渠道不是 IMPORTANCE_MIN ✓、样式是
     * `ProgressStyle`（清单里允许）✓。**别加 `setCustomContentView`、别把渠道降成
     * IMPORTANCE_MIN**，那会当场取消资格。
     *
     * 三层时间表达各有分工，别互相替代：
     * - 卡片头部：系统 chronometer 倒数到上课时刻（秒级，不靠我们重发）；
     * - 芯片：`南B208·8分`（地点缩到 5 字以内再拼分钟数，`chipPlace` / `liveChipText`；
     *   HyperOS 3 的芯片不显示 chronometer、也没有第二个槽放地点，只能压缩进这一行，
     *   见 `EbikeFreeRideNotifier.countdownNotification` 的实测记录）；
     * - 进度条：提前量窗口走完的比例，与芯片分钟数同一口径。
     */
    private fun liveClassNotification(
        context: Context,
        plan: ReminderPlan,
        slots: List<TimeSlot>,
        now: LocalDateTime,
    ): Notification {
        val zone = ZoneId.systemDefault()
        val startAtMillis = plan.startAt.atZone(zone).toInstant().toEpochMilli()
        val nowMillis = now.atZone(zone).toInstant().toEpochMilli()
        val text = liveClassLine(slots, plan.course)
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_class)
            .setContentTitle(plan.course.name)
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            // 常驻到上课时刻：不自动消失、不随点击消失（上课时刻那条是另一条通知）
            .setOngoing(true)
            .setAutoCancel(false)
            // 第一次发要响（渠道 HIGH + 振动），之后每分钟的刷新必须静默
            .setOnlyAlertOnce(true)
            .setWhen(startAtMillis)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setStyle(NotificationCompat.ProgressStyle().setProgress(plan.progressPercent(now)))
            // 兜底：核对链路全挂（闹钟丢失 + App 被冻结）时也别让岛永远挂着
            .setTimeoutAfter((startAtMillis - nowMillis).coerceAtLeast(0L) + LIVE_GRACE_MILLIS)
            .setContentIntent(mainContentIntent(context))
            .setRequestPromotedOngoing(true)
            .setShortCriticalText(liveChipText(plan.course.position, plan.minutesLeft(now)))
            .build()
    }

    /**
     * 上课类通知正文（2026-10-08）：`metaLine`（时刻 · @地点 · 教师）+ 备注
     * （有备注才换行追加，无备注维持原样）；备注过 [remarkPreview] 截断，
     * 通知 BigText 能放多行但用户手写的备注长短不受控，与卡片共用一个上限。
     * internal：RemarkPreviewTest 直调钉住「通知带备注」契约（private 测不到，
     * 同文件 ReminderNotifications 已有 internal 先例）。
     */
    internal fun notificationLine(slots: List<TimeSlot>, course: Course): String {
        val meta = metaLine(slots, course)
        val remark = remarkPreview(course.remark) ?: return meta
        return "$meta\n$remark"
    }

    /**
     * 上课类通知的点击落点：主界面（今日页）。两条通知共用同一个 PendingIntent
     * ——requestCode + filterEquals 完全相同，系统本来就只认一份。
     */
    private fun mainContentIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** 上课类通知的共用构建（上课时刻通知用；提前量那条已改成倒计时卡片）。 */
    private fun postClassNotification(
        context: Context,
        id: Int,
        title: String,
        text: String,
        strongVibration: Boolean,
    ): Boolean {
        ensureChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_class)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setAutoCancel(true)
            .setContentIntent(mainContentIntent(context))
            .build()
        // 无 POST_NOTIFICATIONS 权限时系统静默丢弃；SecurityException 等异常也不外漏
        val posted = runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_TAG, id, notification)
        }.isSuccess
        // 开了「通知强提醒」才在发出后直接震动（2026-10-06 收成通用设置开关，默认关）：
        // HyperOS 的通知策略默认把渠道振动关掉（见 ReminderVibration），直接震动不看那个开关，
        // 但它也穿免打扰——所以只能由用户显式打开，关 = 回到纯渠道震动
        if (posted && strongVibration) ReminderVibration.vibrate(context)
        return posted
    }

    /**
     * 作业截止提醒核对（DESIGN §3.11）：对每条未完成作业求「当前有效」的提醒点，
     * 取触发时刻最早的一条（同一时刻的多条作业只发一条，不堆叠），
     * 去重键没发过才发；发出后才落键（发失败不落，下一轮还有机会）。
     */
    private suspend fun postHomeworkReminderIfAny(context: Context, repo: ScheduleRepository) {
        if (!repo.homeworkReminderEnabled.first()) return
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        val now = LocalDateTime.now()
        // 汇总口径（DESIGN §3.11）：本次处于有效期内的提醒点；有「今天截止」优先发那一组
        val group = homeworkReminderGroup(Graph.homeworkRepository(context).observePending().first(), now)
        if (group.isEmpty()) return
        val alreadySent = repo.homeworkRemindedKeys()
        val fresh = group.filter { it.dedupKey !in alreadySent }
        if (fresh.isEmpty()) return

        ensureChannels(context, homework = true)
        // 口径同上课提醒：开了「通知强提醒」才直接震动（默认关）
        val strongVibration = repo.strongReminderVibration.first()
        // 免扰时段（DESIGN §3.7）：整组拦下入队待补发。汇总与单条的描述在此时就定稿
        // （标题/正文/落点/去重键），补发时按描述重建；拦截 = 已处理，照常落全组去重键
        val zone = ZoneId.systemDefault()
        val held = if (fresh.size == 1) {
            val plan = fresh.single()
            QuietHoursGate.intercept(
                context,
                PendingNotification(
                    tag = HOMEWORK_NOTIFICATION_TAG,
                    notificationId = HOMEWORK_NOTIFICATION_ID,
                    channelId = HOMEWORK_CHANNEL_ID,
                    category = NotificationCompat.CATEGORY_REMINDER,
                    title = "${plan.kind.label} · ${plan.homework.courseName}",
                    text = homeworkDisplayTitle(plan.homework.detail),
                    eventAt = plan.triggerAt.atZone(zone).toInstant().toEpochMilli(),
                    enqueuedAt = System.currentTimeMillis(),
                    vibrate = strongVibration,
                    launch = PendingLaunch(
                        kind = PendingLaunch.Kind.SUBPAGE,
                        requestCode = HOMEWORK_CONTENT_REQUEST_CODE,
                        screen = SubpageScreen.HOMEWORK_DETAIL.name,
                        courseName = plan.homework.courseName,
                        itemId = plan.homework.id.toString(),
                    ),
                ),
            )
        } else {
            val kindLabel = fresh.first().kind.label
            val titles = fresh.map { homeworkDisplayTitle(it.homework.detail) }
            val body = buildString {
                append(titles.take(SUMMARY_TITLE_LIMIT).joinToString(" · "))
                if (titles.size > SUMMARY_TITLE_LIMIT) append(" 等 ${titles.size} 项")
            }
            QuietHoursGate.intercept(
                context,
                PendingNotification(
                    tag = HOMEWORK_SUMMARY_TAG,
                    notificationId = HOMEWORK_SUMMARY_ID,
                    channelId = HOMEWORK_CHANNEL_ID,
                    category = NotificationCompat.CATEGORY_REMINDER,
                    title = "${fresh.size} 项作业$kindLabel",
                    text = body,
                    eventAt = fresh.minOf { it.triggerAt }.atZone(zone).toInstant().toEpochMilli(),
                    enqueuedAt = System.currentTimeMillis(),
                    vibrate = strongVibration,
                    launch = PendingLaunch(
                        kind = PendingLaunch.Kind.SUBPAGE,
                        requestCode = HOMEWORK_SUMMARY_REQUEST_CODE,
                        screen = SubpageScreen.HOMEWORK_TODO.name,
                    ),
                ),
            )
        }
        if (held) {
            fresh.forEach { repo.addHomeworkRemindedKey(it.dedupKey) }
            return
        }
        val posted = if (fresh.size == 1) {
            postSingleHomework(context, fresh.single())
        } else {
            postHomeworkSummary(context, fresh)
        }
        if (posted && strongVibration) ReminderVibration.vibrate(context)
        // 发出后才落键（发失败不落，下一轮还有机会）；整组一起记，免得下轮把同组再发一遍
        if (posted) fresh.forEach { repo.addHomeworkRemindedKey(it.dedupKey) }
    }

    /** 单条作业提醒：标题「明天截止 · 课名」，正文作业标题，点击直达该作业。 */
    private fun postSingleHomework(context: Context, plan: HomeworkReminderPlan): Boolean {
        val homework = plan.homework
        val text = homeworkDisplayTitle(homework.detail)
        val notification = NotificationCompat.Builder(context, HOMEWORK_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_bell)
            .setContentTitle("${plan.kind.label} · ${homework.courseName}")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    HOMEWORK_CONTENT_REQUEST_CODE,
                    // 带 courseName/itemId：点通知直达这条作业（DESIGN §3.11）
                    // 跳板是 MainActivity 而非 SubpageActivity：进程没起时若让二级页当 task 根，
                    // 返回会直接退出 App，见 subpageLaunchIntent 的 KDoc
                    subpageLaunchIntent(
                        context,
                        SubpageRequest(
                            screen = SubpageScreen.HOMEWORK_DETAIL,
                            courseName = homework.courseName,
                            itemId = homework.id,
                        ),
                    ),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .build()
        return runCatching {
            NotificationManagerCompat.from(context)
                .notify(HOMEWORK_NOTIFICATION_TAG, HOMEWORK_NOTIFICATION_ID, notification)
        }.isSuccess
    }

    /**
     * 多条同时到点：发一条汇总（「3 项作业明天截止」），正文列出前几条标题，
     * 点击进作业中心逐条处理——比连发 N 条通知更少打扰、信息量也更大。
     */
    private fun postHomeworkSummary(context: Context, plans: List<HomeworkReminderPlan>): Boolean {
        val kindLabel = plans.first().kind.label
        val titles = plans.map { homeworkDisplayTitle(it.homework.detail) }
        val body = buildString {
            append(titles.take(SUMMARY_TITLE_LIMIT).joinToString(" · "))
            if (titles.size > SUMMARY_TITLE_LIMIT) append(" 等 ${titles.size} 项")
        }
        val notification = NotificationCompat.Builder(context, HOMEWORK_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_bell)
            .setContentTitle("${plans.size} 项作业$kindLabel")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    HOMEWORK_SUMMARY_REQUEST_CODE,
                    subpageLaunchIntent(context, SubpageRequest(SubpageScreen.HOMEWORK_TODO)),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .build()
        return runCatching {
            NotificationManagerCompat.from(context)
                .notify(HOMEWORK_SUMMARY_TAG, HOMEWORK_SUMMARY_ID, notification)
        }.isSuccess
    }
}

/** 边界闹钟落点：只把核对排进 WorkManager，立即返回。 */
class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ClassReminder.enqueueCheck(context)
    }
}

/**
 * 重启、**应用更新**、以及**时区 / 系统时间变化**后各补排一次
 * （上课/作业、选课、问渠、小组件边界闹钟各自重排）。
 *
 * `MY_PACKAGE_REPLACED`（2026-10-02，体检第三批）：覆盖安装不走 BOOT_COMPLETED，
 * 而精确闹钟不跨安装存活——不加这条，用户每次升级后所有提醒静默失效到下一次冷启动。
 *
 * `TIMEZONE_CHANGED` / `TIME_SET`（2026-11-06）：所有提醒都是「墙上时钟 → 绝对时刻」
 * 换算后钉在 AlarmManager 上（`atZone(systemDefault()).toInstant()`），时区一换
 * （放假回家 / 出差）或用户手改系统时间，已排的闹钟就落在错误的墙上时刻，而系统
 * 不会替我们重算。这两条广播只在**时间基准**变了时发，正常一天里一次都不会来。
 * 补排本身是幂等的（全量重算 + `REPLACE`/`KEEP`），重复触发只是白跑一轮。
 */
class ReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            -> {
                ClassReminder.enqueueCheck(context)
                SelectionAlertReminder.enqueueCheck(context)
                // 问渠每周日抢活动提醒（2026-10-11）：同一条精确闹钟口径
                WenquWeeklyAlertReminder.enqueueCheck(context)
                // 问渠预报名（2026-10-16）：精确闹钟不跨重启 / 安装存活，补排——
                // 没排上时是撤闹钟的空跑；错过窗口的在补排里作废并通知
                WenquPreregReminder.enqueueReschedule(context)
                // 小组件边界闹钟同一条口径（2026-11-06）：它也是「墙上时刻 → 绝对时刻」，
                // 时区一变就落在错误的时刻上。无实例时是廉价空跑（内部先查 getGlanceIds）。
                TodayWidgetRefresh.enqueueRefresh(context)
            }
        }
    }
}

/** 真正的核对：先看窗口内有没有该发的（去重后发），再重排下一个闹钟。 */
class ReminderCheckWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        runCatching {
            ReminderNotifications.postDueReminderIfAny(applicationContext)
            ClassReminder.scheduleNext(applicationContext)
        }
        return Result.success()
    }
}
