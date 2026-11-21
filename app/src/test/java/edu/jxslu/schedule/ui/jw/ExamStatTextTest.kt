package edu.jxslu.schedule.ui.jw

import edu.jxslu.schedule.domain.ExamMapper.ExamEntry
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 教务页「考试」两处文案的口径（2026-11-19）。
 *
 * 钉住的是用户实报的那条：教务已 9 场，卡片却写 8 场——快照是新的，旧实现数的是
 * **待考**场次，11-17 那场考完后数字自己掉了 1，看起来像「没更新」。场次必须是
 * **本学期总场次**，与考试页表头「共 N 场」同一份数；「最近」只取待考里最早的那场。
 */
class ExamStatTextTest {

    private fun exam(date: String) = ExamEntry(
        courseNo = "080320080",
        name = "现代机械设计方法",
        date = date,
        startTime = "14:00",
        endTime = "15:25",
    )

    @Test
    fun `已考过的场次仍计入总场次`() {
        val exams = listOf(
            exam("2026-11-17"), exam("2026-11-30"), exam("2026-12-10"),
            exam("2026-12-15"), exam("2026-12-15"), exam("2026-12-22"),
            exam("2026-12-28"), exam("2027-01-04"), exam("2027-01-05"),
        )
        val text = examStatText(exams, LocalDate.of(2026, 11, 19), loaded = true)
        assertEquals("共 9 场 · 最近 11-30", text.summary)
        assertEquals("9 场", text.short)
    }

    @Test
    fun `全部考完说总场次与均已结束`() {
        val text = examStatText(
            listOf(exam("2026-11-17"), exam("2026-11-30")),
            LocalDate.of(2026, 12, 1),
            loaded = true,
        )
        assertEquals("共 2 场 · 均已结束", text.summary)
        assertEquals("2 场", text.short)
    }

    @Test
    fun `当天那场仍算待考`() {
        val text = examStatText(listOf(exam("2026-11-19")), LocalDate.of(2026, 11, 19), loaded = true)
        assertEquals("共 1 场 · 最近 11-19", text.summary)
    }

    @Test
    fun `快照未读回给占位符，不下没有考试的结论`() {
        val text = examStatText(listOf(exam("2026-11-30")), LocalDate.of(2026, 11, 19), loaded = false)
        assertEquals("…", text.summary)
        assertEquals("…", text.short)
    }

    @Test
    fun `空快照与日期缺失的行`() {
        val empty = examStatText(emptyList(), LocalDate.of(2026, 11, 19), loaded = true)
        assertEquals("暂无考试", empty.summary)
        assertEquals("暂无", empty.short)

        // 日期缺失的行无法定位，既不计入总场次也不参与「最近」
        val blankDate = examStatText(listOf(exam("")), LocalDate.of(2026, 11, 19), loaded = true)
        assertEquals("暂无考试", blankDate.summary)
    }
}
