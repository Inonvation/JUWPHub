package edu.jxslu.schedule.platform

/**
 * 作业截止提醒（DESIGN §3.11）：排 / 重排「今天截止」「明天截止」两类提示。
 *
 * 只有一个动作 [reschedule]，因为两端**都不需要调用方交出数据**：
 * Android 那份读库自己算下一个闹钟（`ClassReminder.schedule` → `nextHomeworkReminder`），
 * iOS 那份读库自己算未来若干个时刻（`nextHomeworkReminder` 的列表版
 * `upcomingHomeworkReminders`）。调用方要做的只有一件事：**作业数据或提醒开关变了就喊一声**。
 *
 * | 端 | 实现 | 排程机制 |
 * |---|---|---|
 * | Android | `GraphServices` 转接 `ui/reminder/ClassReminder.enqueueCheck` | 一次性 `AlarmManager` 闹钟（与上课提醒共用最近的那一枚）+ 15 分钟兜底核对 |
 * | iOS | `platform/IosHomeworkReminder` | `UNUserNotificationCenter` 上挂 N 条一次性本地通知，标识符 `homework.<作业id>.<提醒点日期>` |
 *
 * 两端的**时刻算法是同一份**（`domain/ReminderPlanner.kt` 的 `homeworkReminderPoints`：
 * 截止日前一天 20:00 与截止日 20:00），差别只在「到点之后怎么办」：
 *
 * - Android 到点先查一次库，处在有效期窗口内的整组发出去（`homeworkReminderGroup`），
 *   多条合成一条汇总；跨免扰时段拦下待补发。
 * - iOS 到点由系统直接投递，**没有任何代码被执行**：所以必须提前把时刻排上，且没有
 *   免扰队列、没有补发。这些降级口径写在 `design/ios-port.md` §12.9。
 *
 * 触发点（谁喊 [reschedule]）：
 *
 * - iOS：`IosServices.warmUp`（冷启动）与作业四页的 `onHomeworkChanged`、提醒设置页的
 *   `onRecheckHomework`。
 * - Android：宿主仍直接调 `ClassReminder.enqueueCheck`（`SubpageActivity` 四处、
 *   `ReminderSettingsScreen` 的 `onRecheckHomework`、课表变化收集器）；`GraphServices` 里那份
 *   实现指向同一个函数，两端行为因此只有一处定义。
 */
fun interface HomeworkReminder {

    /** 重排一次：读当前未完成作业与开关，把该排的排上、该撤的撤掉（幂等，可随时重复调）。 */
    fun reschedule()
}

/**
 * 作业截止提醒最多占用的**待发槽位**数（iOS）。
 *
 * iOS 每个 App 最多挂 64 条待发本地通知，装不下时系统丢哪条不可依赖。分配（`design/ios-port.md`
 * §12.9.3 策略 A）：
 *
 * | 用途 | 槽位 |
 * |---|---|
 * | 上课提醒（未来 4 天 × 11 小节） | 44 |
 * | 洗衣完成 | 1 |
 * | 作业截止 | 15 |
 * | 安全余量（成绩 / 上课提醒的边界） | 4 |
 * | 合计 | 64 |
 *
 * 15 = 60（逼近上限前就截断）− 44 − 1。作业每条两个提醒点，所以 15 条约等于 7 门作业，
 * 截断口径「按截止日从近到远」写在 [edu.jxslu.schedule.domain.upcomingHomeworkReminders] 的 KDoc。
 * **改这个数要先改 §12.9.3 的分配表**，否则上课提醒那 44 条就超了。
 */
const val HOMEWORK_REMINDER_SLOTS: Int = 15
