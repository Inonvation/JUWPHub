package edu.jxslu.schedule.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 学期推算与年级命名（DESIGN §4.17 归档链）：钉住跨学年、休学（学期号连续而序号不连续）
 * 与非法输入三条口径。
 */
class TermNamingTest {

    @Test
    fun nextCrossesAcademicYear() {
        assertEquals("2026-2027-2", TermNaming.next("2026-2027-1"))
        assertEquals("2027-2028-1", TermNaming.next("2026-2027-2"))
        assertNull(TermNaming.next("2026-2027-3"))
        assertNull(TermNaming.next(null))
    }

    @Test
    fun previousMirrorsNext() {
        assertEquals("2026-2027-1", TermNaming.previous("2026-2027-2"))
        assertEquals("2025-2026-2", TermNaming.previous("2026-2027-1"))
    }

    @Test
    fun sortKeyOrdersTermsChronologically() {
        val sorted = TermNaming.sortedAscending(
            listOf("2025-2026-2", "2024-2025-1", "2026-2027-1", "2024-2025-2"),
        )
        assertEquals(
            listOf("2024-2025-1", "2024-2025-2", "2025-2026-2", "2026-2027-1"),
            sorted,
        )
        // 非法项被丢掉，不参与排序
        assertEquals(listOf("2026-2027-1"), TermNaming.sortedAscending(listOf("bad", "2026-2027-1")))
    }

    @Test
    fun labelsFollowAcademicYear() {
        val first = "2023-2024-1"
        assertEquals("大一上", TermNaming.gradeLabel("2023-2024-1", first))
        assertEquals("大一下", TermNaming.gradeLabel("2023-2024-2", first))
        assertEquals("大二上", TermNaming.gradeLabel("2024-2025-1", first))
        assertEquals("大三下", TermNaming.gradeLabel("2025-2026-2", first))
        assertEquals("大四下", TermNaming.gradeLabel("2026-2027-2", first))
    }

    /** 基准落在下半年时从「大一下」起算，之后的学期不会整体错位一位。 */
    @Test
    fun firstTermInSecondHalfStartsAtSecondSemester() {
        val first = "2023-2024-2"
        assertEquals("大一下", TermNaming.gradeLabel("2023-2024-2", first))
        assertEquals("大二上", TermNaming.gradeLabel("2024-2025-1", first))
    }

    /** 休学一学期：学期号仍然连续，标签按学年推算，不按导入序号数。 */
    @Test
    fun gapSemesterKeepsLabelsCorrect() {
        val first = "2023-2024-1"
        // 跳过 2023-2024-2（休学），直接到 2024-2025-1
        assertEquals("大二上", TermNaming.gradeLabel("2024-2025-1", first))
    }

    @Test
    fun outOfRangeOrEarlierThanFirstIsNull() {
        val first = "2023-2024-1"
        assertNull(TermNaming.gradeLabel("2022-2023-2", first))
        // 超过本科 4 年不猜
        assertNull(TermNaming.gradeLabel("2027-2028-1", first))
        // 基准缺失 / 非法学期
        assertNull(TermNaming.gradeLabel("2024-2025-1", null))
        assertNull(TermNaming.gradeLabel("bad", first))
    }

    @Test
    fun timetableNameFallsBackToTerm() {
        assertEquals("大二上", TermNaming.timetableName("2024-2025-1", "2023-2024-1"))
        assertEquals("2024-2025-1", TermNaming.timetableName("2024-2025-1", null))
    }
}
