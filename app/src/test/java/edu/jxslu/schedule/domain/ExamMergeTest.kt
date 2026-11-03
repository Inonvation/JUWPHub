package edu.jxslu.schedule.domain

import edu.jxslu.schedule.data.DefaultData
import edu.jxslu.schedule.domain.ExamMapper.ExamEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 考试变动自动合并（DESIGN §4.33，2026-11-03）：钉住「补缺 / 原地改 / 改期复用旧行 / 不删行」。
 * 作息表与学期配置沿用 §3.5 默认口径（08:30 起 11 小节；2026-03-02 开学，2026-05-18 = 第 12 周周一）。
 */
class ExamMergeTest {

    private val slots = DefaultData.defaultTimeSlots
    private val config = SemesterConfig(startDate = "2026-03-02", totalWeeks = 20, firstDayOfWeek = 1)
    private val term = "2025-2026-2"

    private fun entry(
        name: String = "高等数学",
        date: String = "2026-05-18",
        startTime: String = "08:30",
        endTime: String = "09:55",
        room: String = "教一101",
        campus: String = "瑶湖校区",
    ) = ExamEntry(
        courseNo = "MATH101",
        name = name,
        teacher = "张三",
        room = room,
        campus = campus,
        date = date,
        startTime = startTime,
        endTime = endTime,
        seatNo = "12",
        sessionNo = "第1场",
    )

    /** 课表里由上次导入落下的考试行（字段与 `ExamMapper.toExamCourse` 的产物一致）。 */
    private fun row(
        id: Long = 1L,
        name: String = "高等数学",
        day: Int = 1,
        week: Int = 12,
        startSection: Int = 1,
        endSection: Int = 2,
        startTime: String = "08:30",
        endTime: String = "09:55",
        position: String = "教一101 瑶湖校区",
        colorIndex: Int = 5,
        remark: String = "带计算器",
        kind: CourseKind = CourseKind.Exam,
    ) = Course(
        id = id,
        name = name,
        teacher = "张三",
        position = position,
        day = day,
        startSection = startSection,
        endSection = endSection,
        weeks = setOf(week),
        isCustomTime = true,
        customStartTime = startTime,
        customEndTime = endTime,
        colorIndex = colorIndex,
        kind = kind,
        remark = remark,
    )

    private fun plan(existing: List<Course>, entries: List<ExamEntry>, config: SemesterConfig? = this.config) =
        ExamMerge.plan(existing, entries, slots, config, term)

    @Test
    fun `课表没有考试时把整份列表补上`() {
        val result = plan(existing = emptyList(), entries = listOf(entry(), entry(name = "大学英语", date = "2026-05-19")))
        assertEquals(2, result.adds.size)
        assertTrue(result.updates.isEmpty())
        assertEquals(CourseKind.Exam, result.adds[0].kind)
        assertEquals(setOf(12), result.adds[0].weeks)
    }

    @Test
    fun `同一场考试内容一致时不写库`() {
        val result = plan(existing = listOf(row()), entries = listOf(entry()))
        assertTrue(result.isEmpty)
    }

    @Test
    fun `时间调整原地改并保留本地字段`() {
        val result = plan(existing = listOf(row()), entries = listOf(entry(startTime = "14:00", endTime = "15:25")))
        assertTrue(result.adds.isEmpty())
        val (old, new) = result.updates.single()
        assertEquals(1L, old.id)
        // 本地字段原样保留，内容按教务改
        assertEquals(1L, new.id)
        assertEquals(5, new.colorIndex)
        assertEquals("带计算器", new.remark)
        assertEquals(5..6, new.startSection..new.endSection)
        assertEquals("14:00", new.customStartTime)
    }

    @Test
    fun `改期复用旧行不新增`() {
        // 2026-05-25 = 第 13 周周一：旧行第 12 周不在最新列表里 = 改期，复用那一行
        val result = plan(existing = listOf(row()), entries = listOf(entry(date = "2026-05-25")))
        assertTrue(result.adds.isEmpty())
        val (_, new) = result.updates.single()
        assertEquals(1L, new.id)
        assertEquals(setOf(13), new.weeks)
    }

    @Test
    fun `同名另一场考试照常补上`() {
        val result = plan(
            existing = listOf(row()),
            entries = listOf(entry(), entry(date = "2026-05-25")),
        )
        assertTrue(result.updates.isEmpty())
        assertEquals(1, result.adds.size)
        assertEquals(setOf(13), result.adds.single().weeks)
    }

    @Test
    fun `非考试行不参与对账`() {
        val theory = row(kind = CourseKind.Theory)
        val result = plan(existing = listOf(theory), entries = listOf(entry()))
        assertEquals(1, result.adds.size)
        assertTrue(result.updates.isEmpty())
    }

    @Test
    fun `撤考不动已有行`() {
        // 最新列表里没有「高等数学」：旧行留着（删考试由用户自己动手）
        val result = plan(existing = listOf(row()), entries = listOf(entry(name = "大学英语", date = "2026-05-19")))
        assertEquals(1, result.adds.size)
        assertTrue(result.updates.isEmpty())
    }

    @Test
    fun `学期配置缺失且学期号不可识别时不落错周`() {
        // 没有学期配置、学期号也识别不了 → 定位不出周次，宁可不写
        val result = ExamMerge.plan(emptyList(), listOf(entry()), slots, null, "")
        assertTrue(result.isEmpty)
        // 日期非法同理
        val invalidDate = plan(existing = emptyList(), entries = listOf(entry(date = "2026/05/18")))
        assertTrue(invalidDate.isEmpty)
    }
}
