package edu.jxslu.schedule.platform

import edu.jxslu.schedule.domain.Course
import edu.jxslu.schedule.domain.ReminderPlan
import edu.jxslu.schedule.domain.ScheduleOverride
import edu.jxslu.schedule.domain.SemesterConfig
import edu.jxslu.schedule.domain.TimeSlot
import edu.jxslu.schedule.domain.nextReminderPlan
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.plus

/**
 * 上课提醒（DESIGN §3.7）：按课表排「提前量那一刻」的提醒，两端各自实现。
 *
 * | 端 | 实现 | 排程机制 |
 * |---|---|---|
 * | Android | `GraphServices` 转接 `ui/reminder/ClassReminder` | `AlarmManager.setAlarmClock` + 15 分钟核对 + 倒计时卡片 |
 * | iOS | `platform/IosClassReminder` | `UNUserNotificationCenter` 上挂 N 条一次性本地通知，标识符 `class.<日期>.<课程id>.<起始分钟>` |
 *
 * 两端**时刻算法同一份**（`domain/ReminderPlanner.kt`：`ScheduleCalculator` 把「第 N 周第 K 天第
 * S 节」+ 作息表换算成绝对时刻，含单双周、自定义时间课与 `date_overrides` 停调课），
 * 差别只在「到点之后怎么办」——iOS 到点由系统直接投递，**没有任何代码被执行**，所以必须提前
 * 把未来若干个时刻排上，且没有免扰队列、没有补发（降级口径见 `design/ios-port.md` §12.9）。
 *
 * 与 [HomeworkReminder] 的分工：那一族管作业截止，这一族管上课；标识符前缀 `homework.` / `class.`
 * 互不牵连，各自 `removePending` 只清自己那批。
 */
fun interface ClassReminder {

    /** 重排一次：读当前课表、提醒开关与提前量，把该排的排上、把旧的整族撤掉（幂等，可随时重复调）。 */
    fun reschedule()
}

/**
 * 上课提醒最多占用的**待发槽位**数（iOS）。
 *
 * iOS 每个 App 最多挂 64 条待发本地通知，装不下时系统丢哪条不可依赖。分配：
 *
 * | 用途 | 槽位 |
 * |---|---|
 * | 上课提醒（未来 [CLASS_REMINDER_DAYS] 天） | **40** |
 * | 作业截止 | 15（`HOMEWORK_REMINDER_SLOTS`） |
 * | 洗衣完成 | 1 |
 * | 成绩 | 1 |
 * | 合计 | 57（离 64 还留 7 条余量） |
 *
 * 40 而不是「4 天 × 11 小节 = 44」：11 小节是满课理论值，实际一周难得有一天排满 11 节；
 * 而 44 会把总量顶到 61，只剩 3 条余量——成绩与洗衣那两条是**同标识符覆盖**（不涨），
 * 但将来任何一处新增一族通知都会直接撞上限。40 是「四天里排得下全周满课」与「留一成余量」
 * 的折中：真排满 44 节时第 41 节起当天不提醒，代价是一天里最后几节安静，而不是整族被系统丢弃。
 *
 * **改这个数要先改 `design/ios-port.md` §12.9.3 的分配表**，否则表与代码各说各话。
 */
const val CLASS_REMINDER_SLOTS: Int = 40

/**
 * 上课提醒在 iOS 上向后铺的天数：**4 天**（含今天）。
 *
 * 为什么不是 7 天（§12.9.3 策略 A 的取舍）：一周满课 77 节，排满一周必然超过 64 条上限；
 * 而就算截断到 40 条，多出来的名额也会落在「第 5 天以后」——那段时间 App 几乎一定被开过一次
 * （查课表、看今日页都会进前台），每次进前台都会按前缀清空重排，**所以远端的槽位是被重复占用的浪费**。
 * 4 天是「覆盖到下一个进前台周期」的最短窗口：一个学生一周内必开 App，改成 7 天只会在中途被
 * 重排覆盖掉，凭空多占 4 天 × 11 = 44 个槽位去换取零收益。
 *
 * 代价（如实写在 §12.9）：**连续 4 天以上不开 App 就断**，第 5 天起没有上课提醒。进程被杀后
 * 那 64 条待发不会自己补，排了也白占额度——这就是不做 7 天的全部理由。
 */
const val CLASS_REMINDER_DAYS: Int = 4

/**
 * 从 [now] 起、[daysAhead] 天内（含今天）还没到点的上课提醒，按上课时刻升序，最多 [limit] 条。
 *
 * 给 iOS 用：本地通知到点不执行代码，未来若干个提醒点必须**提前**排上（Android 只需要最近的
 * 一个时刻去排那一枚闹钟，到点后整组重新求值，见 `ClassReminder.scheduleNext`）。
 *
 * 三条口径都是刻意的：
 *
 * 1. **只取 `triggerAt > now` 的点**：已经过了提前量时刻但还没上课的课不补排——iOS 每次重排
 *    （进 App、改设置、换课表）都会再走一遍这个函数，把过去的点排成「1 秒后」等于用户在
 *    提前量窗口里开几次 App 就响几次。代价是**漏掉的就是漏掉**，与作业那一族同一条降级口径。
 * 2. **截断口径 = 上课时刻从近到远**：[limit] 不够时先保最近的课，远期的那几节当天不提醒。
 * 3. **换算不在这里重写**：逐个调用 [nextReminderPlan]（公开的单条版本），把它的游标从
 *    `now` 推到上一条的 `triggerAt`。这样「第 N 周第 K 天第 S 节 + 作息表 + 自定义时间 +
 *    `date_overrides` → 绝对时刻」始终只有 `domain/ReminderPlanner.kt` 一处实现，
 *    不会因为这里少写一个 `isWeekInTerm` 或漏传 `overrides` 而分叉。
 *
 * 游标用上一条的 `triggerAt` 而不是 `startAt`：两门课挨得很近（自定义时间课）时，
 * 后一门的上课时刻可能早于前一门的触发时刻已被跳过，它的提醒点其实还没到——按 `triggerAt`
 * 推进才不会漏；停在同一分钟上的两门（重复导入）只排一条，正好是想要的去重。
 *
 * 学期未配置 / 课表空 / [limit] 或 [daysAhead] 非正 → 空表（调用方据此只清不排）。
 */
fun upcomingClassReminders(
    semester: SemesterConfig?,
    slots: List<TimeSlot>,
    courses: List<Course>,
    now: LocalDateTime,
    leadMinutes: Int,
    limit: Int = CLASS_REMINDER_SLOTS,
    daysAhead: Int = CLASS_REMINDER_DAYS,
    overrides: List<ScheduleOverride> = emptyList(),
): List<ReminderPlan> {
    if (semester == null || limit <= 0 || daysAhead <= 0) return emptyList()
    val lastDate = now.date.plus(DatePeriod(days = daysAhead - 1))
    val plans = mutableListOf<ReminderPlan>()
    var cursor = now
    while (plans.size < limit) {
        val next = nextReminderPlan(
            semester = semester,
            slots = slots,
            courses = courses,
            now = cursor,
            leadMinutes = leadMinutes,
            // 每次从游标再往后看满整个窗口，越界的由下面的 lastDate 兜住：
            // 游标推进后窗口跟着右移，不夹住就会排到第 5 天以后
            maxDaysAhead = daysAhead,
            overrides = overrides,
        ) ?: break
        if (next.date > lastDate) break
        plans += next
        cursor = next.triggerAt
    }
    return plans
}
