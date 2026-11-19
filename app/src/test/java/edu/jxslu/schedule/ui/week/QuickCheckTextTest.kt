package edu.jxslu.schedule.ui.week

import edu.jxslu.schedule.data.repo.ExamSync
import edu.jxslu.schedule.data.repo.ScheduleSync
import edu.jxslu.schedule.data.repo.ScheduleTermImport
import edu.jxslu.schedule.domain.Course
import edu.jxslu.schedule.domain.CourseKind
import edu.jxslu.schedule.domain.ExamChangeDetector
import edu.jxslu.schedule.domain.ExamMapper
import edu.jxslu.schedule.domain.ScheduleDiff
import edu.jxslu.schedule.ui.common.NoticeTone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 课表页「快速检测」的结果文案（DESIGN §4.17，2026-11-18）。
 *
 * 钉住三件容易被顺手改坏的事：
 * 1. **两条链都要说话**——考试那一路开着自动合并时会当场写课表，报成「无变动」就是静默改课；
 * 2. **失败优先**：任一路失败就换 Error 语气、且不再说「已检查：」（那会读成整轮成功）；
 * 3. **[QuickCheckFeedback.hasChanges] 与文案解耦**：提示条的「查看」动作只认它，
 *    改文案不该把入口改没（或反过来在没差异时冒出一个点进去是空页的按钮）。
 */
class QuickCheckTextTest {

    private fun course(name: String, day: Int = 1, start: Int = 1) = Course(
        id = 0,
        name = name,
        teacher = "教师",
        position = "教室",
        day = day,
        startSection = start,
        endSection = start + 1,
        weeks = setOf(1, 2),
        kind = CourseKind.Theory,
    )

    private fun added(vararg names: String): List<ScheduleDiff.Change> =
        names.map { ScheduleDiff.Change(ScheduleDiff.ChangeKind.ADDED, new = course(it)) }

    private fun scheduleOk(changes: List<ScheduleDiff.Change>) =
        ScheduleSync.CheckResult.Ok(term = "2026-2027-1", changes = changes, repeated = false)

    private fun examUpdated(
        changes: List<ExamChangeDetector.Change> = emptyList(),
        merged: Int = 0,
    ) = ExamSync.Result.Updated(term = "2026-2027-1", count = 3, changes = changes, merged = merged)

    private fun examChange(kind: ExamChangeDetector.Kind) = ExamChangeDetector.Change(
        kind = kind,
        entry = ExamMapper.ExamEntry(
            courseNo = "A001",
            name = "高等数学",
            room = "北A101",
            date = "2026-11-20",
            startTime = "09:00",
            endTime = "11:00",
            seatNo = "12",
        ),
    )

    @Test
    fun `两边都没动 = Info 且不挂查看动作`() {
        val feedback = quickCheckFeedback(scheduleOk(emptyList()), examUpdated())

        assertEquals(NoticeTone.Info, feedback.tone)
        assertFalse(feedback.hasChanges)
        assertEquals("已检查：课表无变动；考试无变动", feedback.text)
    }

    @Test
    fun `有调课差异 = Warning 且按组计数`() {
        // 两条新增各成一组：计数单位是「一次调课」而不是行级 Change
        val feedback = quickCheckFeedback(scheduleOk(added("大学物理", "线性代数")), examUpdated())

        assertEquals(NoticeTone.Warning, feedback.tone)
        assertTrue(feedback.hasChanges)
        assertEquals("已检查：发现 2 处调课差异；考试无变动", feedback.text)
    }

    @Test
    fun `考试变动同样算需要关注`() {
        val feedback = quickCheckFeedback(
            scheduleOk(emptyList()),
            examUpdated(changes = listOf(examChange(ExamChangeDetector.Kind.NEW)), merged = 1),
        )

        assertEquals(NoticeTone.Warning, feedback.tone)
        // 自动合并写的是课表，必须在文案里说出来
        assertEquals("已检查：课表无变动；考试 1 条变动（已并入课表）", feedback.text)
        // 调课没有差异 → 不挂「查看」（那个页面只看课表差异）
        assertFalse(feedback.hasChanges)
    }

    @Test
    fun `课表检测失败 = Error 且去掉已检查前缀`() {
        val feedback = quickCheckFeedback(
            ScheduleSync.CheckResult.Failed("没取到理论课表页，网络不可用（timeout）"),
            examUpdated(),
        )

        assertEquals(NoticeTone.Error, feedback.tone)
        assertFalse(feedback.hasChanges)
        assertEquals(
            "课表检测失败（没取到理论课表页，网络不可用（timeout））；考试无变动",
            feedback.text,
        )
    }

    @Test
    fun `考试检测失败也是 Error`() {
        val feedback = quickCheckFeedback(
            scheduleOk(emptyList()),
            ExamSync.Result.Failed("没取到考试数据，教务会话已失效"),
        )

        assertEquals(NoticeTone.Error, feedback.tone)
        assertEquals("课表无变动；考试检测失败（没取到考试数据，教务会话已失效）", feedback.text)
    }

    @Test
    fun `课表跳过要说清原因`() {
        // force 下只有「没有带学期号的课表」会走到这里；报「间隔未到」会误导用户
        val feedback = quickCheckFeedback(ScheduleSync.CheckResult.Skipped, examUpdated())

        assertEquals("课表未比对（没有带学期号的课表，先从教务导入一次）；考试无变动", feedback.text)
        assertFalse(feedback.hasChanges)
    }

    /** 新学期课表已出：必须单独占一句，并说清已导入——否则用户不知道多了一张表。 */
    @Test
    fun `新学期课表已出要占一句`() {
        val feedback = quickCheckFeedback(
            schedule = scheduleOk(emptyList()),
            exam = examUpdated(),
            probe = ScheduleTermImport.ProbeResult.Found("2025-2026-2", 20, 7L),
            firstTerm = "2023-2024-1",
        )

        assertEquals(
            "已检查：课表无变动；考试无变动；新学期课表已出：大三下（20 门，已导入，可在课表管理里切换）",
            feedback.text,
        )
        // 新表没有差异，提示条不该冒出「查看」入口（那是调课差异专用）
        assertFalse(feedback.hasChanges)
        assertEquals(NoticeTone.Info, feedback.tone)
    }

    /** 没排课是常态：NotYet 不占句，否则这条提示每次都要多读一句废话。 */
    @Test
    fun `新学期还没排课不占句`() {
        val feedback = quickCheckFeedback(
            schedule = scheduleOk(emptyList()),
            exam = examUpdated(),
            probe = ScheduleTermImport.ProbeResult.NotYet("2026-2027-2", "学期已开放但还没有课程"),
        )

        assertEquals("已检查：课表无变动；考试无变动", feedback.text)
    }
}
