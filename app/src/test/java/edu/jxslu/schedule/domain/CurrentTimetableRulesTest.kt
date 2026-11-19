package edu.jxslu.schedule.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 当前课表的按日期自动选择（DESIGN §4.17）：钉住「命中取最新学期 / 假期退回教务当前学期 /
 * 手建表不参与」三条。
 */
class CurrentTimetableRulesTest {

    private fun table(
        id: Long,
        term: String?,
        start: String?,
        weeks: Int = 20,
    ) = CurrentTimetableRules.Candidate(
        timetableId = id,
        term = term,
        startDate = start?.let(LocalDate::parse),
        totalWeeks = weeks,
    )

    private val autumn = table(1, "2025-2026-1", "2025-09-01")
    private val spring = table(2, "2025-2026-2", "2026-03-02")

    @Test
    fun picksTableWhoseSpanCoversToday() {
        assertEquals(1L, CurrentTimetableRules.pick(listOf(autumn, spring), LocalDate.of(2025, 10, 1), null))
        assertEquals(2L, CurrentTimetableRules.pick(listOf(autumn, spring), LocalDate.of(2026, 4, 1), null))
    }

    @Test
    fun spanIsHalfOpen() {
        assertTrue(CurrentTimetableRules.covers(autumn, LocalDate.of(2025, 9, 1)))
        // 20 周后的那天已是区间外
        assertFalse(CurrentTimetableRules.covers(autumn, LocalDate.of(2025, 9, 1).plusWeeks(20)))
    }

    @Test
    fun overlappingSpansPreferNewerTerm() {
        // 下学期提前开课，与上学期重叠在 2025-12-15：新学期（term 更大）优先
        val early = table(3, "2025-2026-2", "2025-12-01")
        assertEquals(3L, CurrentTimetableRules.pick(listOf(autumn, early), LocalDate.of(2025, 12, 15), null))
    }

    @Test
    fun holidayFallsBackToPreferredTerm() {
        // 2026-08-01 在两张表之外：退回教务当前学期那张
        assertEquals(1L, CurrentTimetableRules.pick(listOf(autumn, spring), LocalDate.of(2026, 8, 1), "2025-2026-1"))
        assertNull(CurrentTimetableRules.pick(listOf(autumn, spring), LocalDate.of(2026, 8, 1), "2026-2027-1"))
        assertNull(CurrentTimetableRules.pick(listOf(autumn, spring), LocalDate.of(2026, 8, 1), null))
    }

    /** 手建表（term 为空）即使日期落在它的区间内也不参与——否则会把用户自建的表顶成当前表。 */
    @Test
    fun handMadeTableWithoutTermNeverWins() {
        val handMade = table(9, null, "2025-09-01")
        assertEquals(1L, CurrentTimetableRules.pick(listOf(handMade, autumn), LocalDate.of(2025, 10, 1), null))
        assertNull(CurrentTimetableRules.pick(listOf(handMade), LocalDate.of(2025, 10, 1), null))
    }

    /** 学科配置缺失（没配过开学日）时只走 preferred 分支，不按默认开学日瞎命中。 */
    @Test
    fun missingStartDateSkipsDateMatch() {
        val noStart = table(5, "2026-2027-1", null)
        assertEquals(5L, CurrentTimetableRules.pick(listOf(noStart), LocalDate.of(2026, 9, 10), "2026-2027-1"))
        assertNull(CurrentTimetableRules.pick(listOf(noStart), LocalDate.of(2026, 9, 10), null))
    }
}
