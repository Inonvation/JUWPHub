package edu.jxslu.schedule.platform

import edu.jxslu.schedule.data.prefs.DisplayPrefsStore
import edu.jxslu.schedule.data.repo.ScheduleRepository
import edu.jxslu.schedule.domain.AppClock
import edu.jxslu.schedule.domain.ReminderPlan
import edu.jxslu.schedule.domain.TimeSlot
import edu.jxslu.schedule.domain.liveClassLine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

/**
 * 上课提醒的 iOS 实现（N3，2026-11-26）：把未来 [CLASS_REMINDER_DAYS] 天的课每节各挂一条本地通知。
 *
 * 与作业那一份（[IosHomeworkReminder]）同一套路子，只有时刻来源不同：作业是「截止日 → 两个
 * 20:00 提醒点」，上课是「课表 + 作息表 → 上课时刻 − 提前量」，换算整段交给
 * [upcomingClassReminders] / `domain/ReminderPlanner.kt`。
 *
 * | 面 | Android（`ui/reminder/ClassReminder`） | iOS（这里） |
 * |---|---|---|
 * | 排几条 | **一枚**精确闹钟，上课与作业取更早的触发时刻 | 未来 4 天内每节一条，上限 [CLASS_REMINDER_SLOTS] |
 * | 到点 | 拉起 Receiver → 查库 → 发「上课」/「提前量」通知，之后是常驻倒计时卡片 | 系统直接投递，**没有任何代码被执行**（所以没有倒计时卡片） |
 * | 去重 | DataStore 里的「已发键」`日期\|课程id\|起始分钟` | **不需要**：标识符就是去重键，系统队列里同标识符覆盖；已到点的点不排队（只排 `triggerAt > now`） |
 * | 免扰时段 | `QuietHoursGate` 拦下待补发 | **没有**等价物（§12.9.2），拦下就是没有 |
 * | 漏掉的点 | 闹钟落点 / 15 分钟核对补发 | **漏掉就是漏掉**：窗口内不补排（理由见 [upcomingClassReminders] 第 1 条） |
 *
 * ## 重排的时序
 *
 * 每次 [reschedule] 都是「**先按前缀清空整族，再排新的**」：课表被改、提前量被调、提醒被关掉时，
 * 旧的那些标识符不会自己消失，而 iOS 不允许「按当时的内存记录撤」（进程被杀那点记忆就没了）。
 * 清空是异步的（要先枚举待发队列），所以排新的动作挂在清空回调里，不与清空赛跑。
 *
 * 触发点（谁喊 [reschedule]）：`IosServices.warmUpReminders`（冷启动）与提醒设置页的
 * `onReschedule`（改开关 / 改提前量）。课表导入、AI 调课、`date_overrides` 变更之后也该喊一声，
 * 但那些入口都在 `IosNavigation.kt` 里、本轮没接——**所以现在改完课表要等下次冷启动才生效**
 * （降级口径记在 `design/ios-port.md` §12.9.2）。
 *
 * ## 隐私
 *
 * 通知会显示在锁屏上：标题只写「N 分钟后有课 · 课程名」，正文写**上课时刻与教室**，
 * 不含教师（Android 那份 `metaLine` 带教师，这里刻意不带）、不含学号姓名。
 * 点开回到 App，没有带 `userInfo` 的深链。
 */
internal class IosClassReminder(
    private val repo: ScheduleRepository,
    private val prefs: DisplayPrefsStore,
    private val scope: CoroutineScope,
) : ClassReminder {

    override fun reschedule() {
        // 读库是 suspend（Room Flow 的首个值），调用方是组合期 / 启动期的回调，所以落到应用级
        // 作用域：与 Android 的 `ClassReminder.enqueueCheck`、iOS 的作业那份一样是**异步重排**。
        scope.launch {
            val enabled = prefs.reminderEnabled.first()
            // 开关关着连课表都不读（与作业那份同口径）；关掉时只需要「按前缀清空」
            val slots = if (enabled) repo.timeSlots.first() else emptyList()
            val plans: List<ReminderPlan> = if (enabled) {
                upcomingClassReminders(
                    semester = repo.semester.first(),
                    slots = slots,
                    courses = repo.courses.first(),
                    now = AppClock.nowDateTime(),
                    leadMinutes = prefs.reminderLeadMinutes.first(),
                    // 日期覆盖（调休 / 停课，§4.40）：放假那天不排，补课那天按借来的课排——
                    // 与今日页、Android 的排闹钟同一口径
                    overrides = repo.overridesFor(),
                )
            } else {
                // 开关关掉：只清不排（旧版排上的那几节必须撤，否则关了还在响）
                emptyList()
            }
            IosLocalNotifications.removePending(CLASS_ID_PREFIX) {
                if (plans.isEmpty()) return@removePending
                // 权限还没问过时才弹系统框；被拒就静默（没有提醒，课表本身照常能用）
                IosLocalNotifications.ensureAuthorized { granted ->
                    if (granted) plans.forEach { post(it, slots) }
                }
            }
            if (plans.isNotEmpty()) Logger.d(TAG, "重排上课提醒 ${plans.size} 条")
        }
    }

    /** 一条「还没到点的课」→ 一条到点通知；标识符 `class.<日期>.<课程id>.<起始分钟>`（同一条重排是覆盖）。 */
    private fun post(plan: ReminderPlan, slots: List<TimeSlot>) {
        IosLocalNotifications.schedule(
            identifier = CLASS_ID_PREFIX + plan.date + "." + plan.course.id + "." + plan.startMinutes,
            title = "${minutesAhead(plan)} 分钟后有课 · ${plan.course.name}",
            body = liveClassLine(slots, plan.course),
            triggerAtMillis = plan.triggerAt.toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds(),
        )
    }

    private companion object {
        const val TAG = "IosClassReminder"
    }
}

/**
 * 标题里的「N 分钟」= 排的时候用的提前量（`triggerAt = startAt − lead`，这里反算回来）。
 *
 * 不在 [IosClassReminder] 里另存一份提前量：通知一旦排上就活到投递那一刻，中途用户改提前量
 * 会触发整族重排，所以「排的时候那条」和「显示的那条」永远同源；反算比再传一个参数更不容易走样。
 */
private fun minutesAhead(plan: ReminderPlan): Int =
    (AppClock.secondsBetween(plan.triggerAt, plan.startAt) / 60L).toInt()

/**
 * 上课提醒的标识符前缀：`class.<日期>.<课程id>.<起始分钟>`。
 *
 * 前缀是撤销的唯一依据（[IosLocalNotifications.removePending] 按它扫队列），
 * **不能与 `homework.` / `ujing.` / `score.` 混用**——否则撤上课提醒时会把作业提醒一起清掉。
 */
private const val CLASS_ID_PREFIX = "class."
