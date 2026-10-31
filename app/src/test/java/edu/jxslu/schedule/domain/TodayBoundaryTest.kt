package edu.jxslu.schedule.domain

import edu.jxslu.schedule.data.DefaultData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/**
 * 小组件边界闹钟的时刻计算。
 *
 * 边界算错的表现是「桌面在下课后还显示正在上课 / 该跨天时没换日期」，
 * 这种问题只在真机上肉眼可见，所以把口径在 JVM 单测里钉死。
 */
class TodayBoundaryTest {

    /** 第 1 周 = 8/31–9/6，9/17（周四）是第 3 周 */
    private val semester = SemesterConfig(startDate = "2026-08-31", totalWeeks = 20)
    private val slots = DefaultData.defaultTimeSlots
    private val thursday = LocalDate.parse("2026-09-17")

    private fun course(id: Long, start: Int, end: Int) = Course(
        id = id,
        name = "课$id",
        teacher = "",
        position = "",
        day = 4,
        startSection = start,
        endSection = end,
        weeks = (1..10).toSet(),
    )

    /** 周四三节连排：3-4（10:15–11:40）、5-6（14:00–15:25）、7-8（15:45–17:10） */
    private val courses = listOf(
        course(1, 3, 4),
        course(2, 5, 6),
        course(3, 7, 8),
    )

    @Test
    fun beforeFirstClass_nextBoundaryIsFirstStart() {
        assertEquals(10 * 60 + 15, nextTodayBoundaryMinutes(semester, slots, courses, thursday, LocalTimeLike(7, 0)))
    }

    @Test
    fun betweenClasses_nextBoundaryIsNextStart() {
        // 午休（11:40 下课后）：下一个边界是 14:00 上课
        assertEquals(14 * 60, nextTodayBoundaryMinutes(semester, slots, courses, thursday, LocalTimeLike(12, 0)))
    }

    @Test
    fun duringClass_nextBoundaryIsThisSectionEnd() {
        // 5-6 节进行中（5 节 14:00–14:40、6 节 14:45–15:25）：下一个边界是本节结束，
        // 不是整门课结束——课间那 5 分钟也要让桌面从「第5节」切到「课间」
        assertEquals(14 * 60 + 40, nextTodayBoundaryMinutes(semester, slots, courses, thursday, LocalTimeLike(14, 30)))
    }

    @Test
    fun duringBreak_nextBoundaryIsNextSectionStart() {
        // 14:42 在 5、6 节之间的 5 分钟课间：下一个边界是 14:45 上第 6 节
        assertEquals(14 * 60 + 45, nextTodayBoundaryMinutes(semester, slots, courses, thursday, LocalTimeLike(14, 42)))
    }

    @Test
    fun afterLastClass_noBoundaryToday() {
        assertNull(nextTodayBoundaryMinutes(semester, slots, courses, thursday, LocalTimeLike(18, 0)))
    }

    @Test
    fun outOfTerm_returnsNull() {
        val afterTerm = LocalDate.parse("2027-02-01") // 20 周早已结束
        assertNull(nextTodayBoundaryMinutes(semester, slots, courses, afterTerm, LocalTimeLike(7, 0)))
    }

    @Test
    fun noSemester_returnsNull() {
        assertNull(nextTodayBoundaryMinutes(null, slots, courses, thursday, LocalTimeLike(7, 0)))
    }

    @Test
    fun boundaryIsStrictlyAfterNow() {
        // 10:30（3-4 节进行中）：10:15 已过，取本节 10:55 结束
        assertEquals(10 * 60 + 55, nextTodayBoundaryMinutes(semester, slots, courses, thursday, LocalTimeLike(10, 30)))
    }
}
