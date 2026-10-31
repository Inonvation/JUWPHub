package edu.jxslu.schedule.domain

import java.time.LocalDate

/**
 * 今天下一个「上课 / 下课 / 小节切换」时刻（自午夜起的分钟数）；今天没有剩余边界时返回 null。
 *
 * 用途：桌面小组件的边界闹钟——桌面内容真正需要变样子的只有这些瞬间，
 * 按边界排闹钟比每 15 分钟无脑刷更准也更省电。
 *
 * **小节起止也算边界**（2026-10-29）：大节内两小节之间有 5 分钟课间，桌面文案要从
 * 「第3节 · 还有 …」切成「课间 · 还有 … 上课」。此前只排整门课起止，10:55 的课间
 * 要等到 11:40 下课才刷新，卡片会在课间里一直显示上一节的倒计时。
 * 自定义时间课没有可用的小节表，仍只取课程起止。
 *
 * 放在 domain 而不是 widget 包里：它是纯查询（给定学期/作息/课程/时刻算出边界），
 * 与 Android 无关，需要能被 JVM 单测直接钉住——边界算错的表现是
 * 「桌面下课后还显示正在上课」，只在真机上很难发现。
 */
fun nextTodayBoundaryMinutes(
    semester: SemesterConfig?,
    slots: List<TimeSlot>,
    courses: List<Course>,
    today: LocalDate,
    now: LocalTimeLike,
    overrides: List<ScheduleOverride> = emptyList(),
): Int? {
    if (semester == null) return null
    val week = ScheduleCalculator.weekNumberOf(semester, today)
    if (!ScheduleCalculator.isWeekInTerm(semester, week)) return null
    val day = today.dayOfWeek.value
    val nowMinutes = now.toMinutes()
    return ScheduleCalculator.coursesOnDay(
        courses,
        week,
        day,
        ScheduleOverrides.plan(overrides, semester),
    )
        .flatMap { course ->
            val sectionBounds = if (course.isCustomTime) {
                emptyList()
            } else {
                slots.filter { it.number in course.startSection..course.endSection }
                    .flatMap { slot ->
                        listOf(
                            ScheduleCalculator.toMinutes(slot.startTime),
                            ScheduleCalculator.toMinutes(slot.endTime),
                        )
                    }
            }
            listOfNotNull(
                ScheduleCalculator.courseStartMinutes(slots, course),
                ScheduleCalculator.courseEndMinutes(slots, course),
            ) + sectionBounds
        }
        .filter { it > nowMinutes }
        .minOrNull()
}
