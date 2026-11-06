package edu.jxslu.schedule.domain

import edu.jxslu.schedule.domain.ExamMapper.ExamEntry
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 考试变动正文口径（DESIGN §4.33 / §4.38）：钉住「改什么就说什么」。
 *
 * 回归背景（2026-11-05 用户实测）：判据比 5 个字段、正文只渲染 3 个，座位号/校区一变，
 * 收件箱里就出现新旧一模一样的「考试安排调整」。
 */
class ExamChangeTextTest {

    private fun entry(
        courseNo: String = "MATH101",
        name: String = "高等数学",
        date: String = "2026-01-13",
        startTime: String = "14:00",
        endTime: String = "16:00",
        room: String = "教一101",
        campus: String = "瑶湖校区",
        seatNo: String = "12",
    ) = ExamEntry(
        courseNo = courseNo,
        name = name,
        teacher = "张三",
        room = room,
        campus = campus,
        date = date,
        startTime = startTime,
        endTime = endTime,
        seatNo = seatNo,
        sessionNo = "第1场",
    )

    /** 走一遍真检测，保证正文与 [ExamChangeDetector] 的口径同源。 */
    private fun describeOf(old: ExamEntry, new: ExamEntry): String {
        val changes = ExamChangeDetector.detect(old = listOf(old), new = listOf(new))
        assertEquals(1, changes.size)
        return ExamChangeText.describe(changes[0])
    }

    @Test
    fun `座位号从无到有，正文写出空到新值`() {
        val text = describeOf(entry(seatNo = ""), entry(seatNo = "52"))
        assertEquals("《高等数学》 座位号 空 → 52", text)
    }

    @Test
    fun `校区变化只列校区`() {
        val text = describeOf(entry(), entry(campus = "青山湖校区"))
        assertEquals("《高等数学》 校区 瑶湖校区 → 青山湖校区", text)
    }

    @Test
    fun `时刻变化列出整段起止`() {
        val text = describeOf(entry(), entry(startTime = "09:00", endTime = "11:00"))
        assertEquals("《高等数学》 时间 14:00–16:00 → 09:00–11:00", text)
    }

    @Test
    fun `只改结束时刻也算时间变化`() {
        val text = describeOf(entry(), entry(endTime = "17:00"))
        assertEquals("《高等数学》 时间 14:00–16:00 → 14:00–17:00", text)
    }

    @Test
    fun `多字段同时变按判据顺序用分号连接`() {
        val text = describeOf(entry(), entry(room = "教二202", seatNo = "07"))
        assertEquals("《高等数学》 考场 教一101 → 教二202；座位号 12 → 07", text)
    }

    @Test
    fun `新增考试给日期时刻与考场`() {
        val changes = ExamChangeDetector.detect(old = emptyList(), new = listOf(entry()))
        assertEquals(1, changes.size)
        assertEquals(
            "《高等数学》 2026-01-13 14:00–16:00 教一101",
            ExamChangeText.describe(changes[0]),
        )
    }

    @Test
    fun `没变的考试不会走到正文`() {
        assertEquals(0, ExamChangeDetector.detect(listOf(entry()), listOf(entry())).size)
    }
}
