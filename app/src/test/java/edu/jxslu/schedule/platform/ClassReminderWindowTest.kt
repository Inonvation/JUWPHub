package edu.jxslu.schedule.platform

import edu.jxslu.schedule.data.DefaultData
import edu.jxslu.schedule.domain.Course
import edu.jxslu.schedule.domain.OverrideKind
import edu.jxslu.schedule.domain.ScheduleOverride
import edu.jxslu.schedule.domain.SemesterConfig
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * iOS 上课提醒的「未来窗口 + 槽位上限」取值（N3，2026-11-26，DESIGN §3.7 / `design/ios-port.md` §12.9.3）。
 *
 * 这部分纯计算在 `commonMain`（`platform/ClassReminderPlatform.kt`），所以能在 JVM 单测里钉住；
 * iOS 那一层（`IosClassReminder` / `UNUserNotificationCenter`）没有测试源集，只能真机验。
 *
 * 算错的表现和 Android 那份一样：少排一节 → 那天不提醒；多排一节 → 挤掉别的族的槽位；
 * 排到第 5 天 → 直接越过 64 条上限（系统丢哪条不可依赖）。都在这里用断言写死。
 */
class ClassReminderWindowTest {

    /** 第 1 周 = 8/31–9/6，9/17（周四）是第 3 周（与 [edu.jxslu.schedule.domain.ReminderPlannerTest] 同一份布置）。 */
    private val semester = SemesterConfig(startDate = "2026-08-31", totalWeeks = 20)
    private val slots = DefaultData.defaultTimeSlots
    private val thursday = LocalDate.parse("2026-09-17")

    private fun at(hour: Int, minute: Int): LocalDateTime =
        LocalDateTime(thursday, LocalTime(hour, minute))

    private fun course(id: Long, day: Int, start: Int, end: Int = start) = Course(
        id = id,
        name = "课$id",
        teacher = "",
        position = "",
        day = day,
        startSection = start,
        endSection = end,
        weeks = (1..20).toSet(),
    )

    @Test
    fun everyCourseInWindow_getsOnePlan() {
        // 周四 07:00：周四两节 + 周五一节，各一条，按上课时刻升序
        val courses = listOf(
            course(1, day = 4, start = 3, end = 4),
            course(2, day = 4, start = 5, end = 6),
            course(3, day = 5, start = 1),
        )
        val plans = upcomingClassReminders(semester, slots, courses, at(7, 0), leadMinutes = 10)
        assertEquals(listOf(1L, 2L, 3L), plans.map { it.course.id })
        assertEquals(
            listOf(thursday, thursday, LocalDate.parse("2026-09-18")),
            plans.map { it.date },
        )
    }

    @Test
    fun trigger_isStartMinusLead() {
        // 3-4 节 10:15 上课，提前量 10 → 10:05
        val plans = upcomingClassReminders(
            semester, slots, listOf(course(1, 4, 3, 4)), at(7, 0), leadMinutes = 10,
        )
        assertEquals(LocalDateTime(thursday, LocalTime(10, 5)), plans.single().triggerAt)
        // 提前量改成 30 → 9:45（同一节课只差触发时刻）
        val early = upcomingClassReminders(
            semester, slots, listOf(course(1, 4, 3, 4)), at(7, 0), leadMinutes = 30,
        )
        assertEquals(LocalDateTime(thursday, LocalTime(9, 45)), early.single().triggerAt)
    }

    @Test
    fun window_coversFourDaysAndStopsBeforeFifth() {
        // 周四 07:00：窗口 = 周四 / 周五 / 周六 / 周日共 4 天，下周一（9/21）不在内
        val courses = listOf(
            course(1, day = 4, start = 3),
            course(2, day = 5, start = 3),
            course(3, day = 6, start = 3),
            course(4, day = 7, start = 3),
            course(5, day = 1, start = 3),
        )
        val plans = upcomingClassReminders(semester, slots, courses, at(7, 0), leadMinutes = 10)
        assertEquals(listOf(1L, 2L, 3L, 4L), plans.map { it.course.id })
        assertEquals(LocalDate.parse("2026-09-20"), plans.last().date)
    }

    @Test
    fun limit_keepsEarliestCourses() {
        // 上限 2：先保最早的 5-6 节（14:00），再保 3-4 节（10:15）——按上课时刻，不按入参顺序
        val courses = listOf(
            course(1, day = 4, start = 5, end = 6),
            course(2, day = 4, start = 3, end = 4),
            course(3, day = 4, start = 7, end = 8),
        )
        val plans = upcomingClassReminders(
            semester, slots, courses, at(7, 0), leadMinutes = 10, limit = 2,
        )
        assertEquals(listOf(2L, 1L), plans.map { it.course.id })
    }

    @Test
    fun budget_capsAtFortyOfFortyFourInFourDays() {
        // 4 天 × 11 小节 = 44 节满课：只排前 40 条（槽位预算），第 41 节起当天不提醒
        val courses = (4..7).flatMap { day ->
            (1..11).map { section -> course(((day - 4) * 11 + section).toLong(), day, section) }
        }
        val plans = upcomingClassReminders(semester, slots, courses, at(7, 0), leadMinutes = 10)
        assertEquals(40, plans.size)
        assertEquals((1L..40L).toList(), plans.map { it.course.id })
    }

    @Test
    fun passedTrigger_isNotScheduled() {
        // 10:10：3-4 节的触发点 10:05 已过（课还没上）——不补排，否则在窗口里开几次 App 就响几次
        val courses = listOf(course(1, day = 4, start = 3, end = 4))
        val plans = upcomingClassReminders(semester, slots, courses, at(10, 10), leadMinutes = 10)
        assertTrue(plans.isEmpty())
    }

    @Test
    fun suspendedDay_isSkipped() {
        // 9/17 停课：那天一条都不排，周五的照排
        val overrides = listOf(
            ScheduleOverride(fromDate = "2026-09-17", toDate = "2026-09-17", kind = OverrideKind.SUSPENDED),
        )
        val courses = listOf(course(1, day = 4, start = 3), course(2, day = 5, start = 3))
        val plans = upcomingClassReminders(
            semester, slots, courses, at(7, 0), leadMinutes = 10, overrides = overrides,
        )
        assertEquals(listOf(2L), plans.map { it.course.id })
    }

    @Test
    fun duplicateCourseSameStart_onlyOnePlan() {
        // 重复导入同一天同一节（两行、不同 id）：只排一条——同一分钟两条提醒是噪音
        val courses = listOf(course(1, day = 4, start = 3), course(2, day = 4, start = 3))
        val plans = upcomingClassReminders(semester, slots, courses, at(7, 0), leadMinutes = 10)
        assertEquals(1, plans.size)
    }

    @Test
    fun emptyInputs_produceNothing() {
        val courses = listOf(course(1, day = 4, start = 3))
        assertTrue(upcomingClassReminders(null, slots, courses, at(7, 0), 10).isEmpty())
        assertTrue(upcomingClassReminders(semester, slots, emptyList(), at(7, 0), 10).isEmpty())
        assertTrue(upcomingClassReminders(semester, slots, courses, at(7, 0), 10, limit = 0).isEmpty())
    }
}
