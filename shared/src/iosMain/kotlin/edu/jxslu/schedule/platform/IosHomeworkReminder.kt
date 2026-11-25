package edu.jxslu.schedule.platform

import edu.jxslu.schedule.data.prefs.DisplayPrefsStore
import edu.jxslu.schedule.data.repo.HomeworkRepository
import edu.jxslu.schedule.domain.AppClock
import edu.jxslu.schedule.domain.HomeworkReminderPlan
import edu.jxslu.schedule.domain.upcomingHomeworkReminders
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

/**
 * 作业截止提醒的 iOS 实现（N2，2026-11-26）。
 *
 * 与 Android 那份（`ui/reminder/ClassReminder`）的算法同一个来源——`domain/ReminderPlanner.kt`
 * 的 `homeworkReminderPoints`：截止日前一天 20:00（「明天截止」）与截止日 20:00（「今天截止」），
 * 只算未完成、未逾期、30 天内的。差别全在「到点之后」：
 *
 * | 面 | Android | iOS（这里） |
 * |---|---|---|
 * | 排几条 | **一条**就近的闹钟，与上课提醒取更早的那个时刻 | 未来若干个提醒点**各挂一条**本地通知（上限 [HOMEWORK_REMINDER_SLOTS]） |
 * | 到点 | 拉起 Receiver → 查库 → 求「当前有效」的整组 → 单条或汇总 | 系统直接投递，**没有任何代码被执行** |
 * | 去重 | DataStore 里的 `homework.<id>.<提醒点日期>` 已发键集合 | **不需要**：标识符就是去重键，系统队列里同标识符覆盖；已投递的不再排（只排 `triggerAt > now`） |
 * | 免扰时段 | `QuietHoursGate` 拦下待补发 | **没有**等价物（§12.9.2） |
 * | 漏掉的点 | 闹钟落点 / 15 分钟核对补发 | **漏掉就是漏掉**：窗口内不补排（理由见 `upcomingHomeworkReminders` 的 KDoc 第 1 条） |
 *
 * ## 重排的时序
 *
 * 每次 [reschedule] 都是「**先按前缀清空整族，再排新的**」：作业被勾完成、被删、截止日被改，
 * 旧的那些标识符不会自己消失，而 iOS 不允许「按当时的内存记录撤」（进程被杀那点记忆就没了）。
 * 清空是异步的（要先枚举待发队列），所以排新的动作挂在清空回调里，不会与清空赛跑。
 *
 * ## 隐私
 *
 * 通知会显示在锁屏上：标题只写「今天/明天 23:59 截止 · 课程名」，**正文不带作业正文**
 * （那是用户自己写的内容，可能带姓名/寝室/教师）。点开回到 App，没有带 `userInfo` 的深链。
 */
class IosHomeworkReminder(
    private val repo: HomeworkRepository,
    private val prefs: DisplayPrefsStore,
    private val scope: CoroutineScope,
) : HomeworkReminder {

    override fun reschedule() {
        // 读库是 suspend（Room Flow 的首个值），调用方是组合期的回调，所以落到应用级作用域；
        // 与 Android 的 `ClassReminder.enqueueCheck` 一样是**异步重排**，返回值没有意义。
        scope.launch {
            val plans = if (prefs.homeworkReminderEnabled.first()) {
                upcomingHomeworkReminders(
                    items = repo.observePending().first(),
                    now = AppClock.nowDateTime(),
                    limit = HOMEWORK_REMINDER_SLOTS,
                )
            } else {
                // 开关关掉：只清不排（旧版排上的那几条必须撤，否则关了还在响）
                emptyList()
            }
            IosLocalNotifications.removePending(HOMEWORK_ID_PREFIX) {
                if (plans.isEmpty()) return@removePending
                // 权限还没问过时才弹系统框；被拒就静默（没有提醒，作业本身照常能用）
                IosLocalNotifications.ensureAuthorized { granted ->
                    if (granted) plans.forEach { post(it) }
                }
            }
            if (plans.isNotEmpty()) Logger.d(TAG, "重排作业截止提醒 ${plans.size} 条")
        }
    }

    /** 一条提醒点 → 一条到点通知；标识符 `homework.<作业id>.<提醒点日期>`（同一条重排是覆盖）。 */
    private fun post(plan: HomeworkReminderPlan) {
        val homework = plan.homework
        // 文案口径（§12.9）：只写「哪门课 + 什么时候截止」，不带作业正文与任何身份信息。
        // 截止日期只到日粒度，所以统一写 23:59——与今日页/作业页的「今天截止」同口径。
        val deadline = when (plan.kind) {
            HomeworkReminderPlan.Kind.Today -> "今天"
            HomeworkReminderPlan.Kind.Tomorrow -> "明天"
        }
        IosLocalNotifications.schedule(
            identifier = HOMEWORK_ID_PREFIX + homework.id + "." + plan.noticeDate,
            title = "$deadline 23:59 截止 · ${homework.courseName}",
            body = BODY,
            triggerAtMillis = plan.triggerAt.toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds(),
        )
    }

    private companion object {
        const val TAG = "IosHomeworkReminder"

        /**
         * 正文不带作业内容：锁屏上看得见，而作业正文是用户自己写的（可能含姓名、寝室、教师）。
         * 课程名留在标题里是因为「哪门课的作业」是这条提醒唯一有用的信息（§12.9 口径）。
         */
        const val BODY = "作业还没完成，记得提交"
    }
}

/**
 * 作业截止提醒的标识符前缀：`homework.<作业id>.<提醒点日期>`。
 *
 * 前缀是撤销的唯一依据（[IosLocalNotifications.removePending] 按它扫队列），
 * **不能与 `ujing.` / `score.` 混用**——否则撤洗衣提醒时会把作业提醒一起清掉。
 */
private const val HOMEWORK_ID_PREFIX = "homework."
