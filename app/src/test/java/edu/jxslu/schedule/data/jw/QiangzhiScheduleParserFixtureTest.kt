package edu.jxslu.schedule.data.jw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 理论课表解析器对**真实教务页面**的回归（2026-11-13）。
 *
 * 输入不是手写样本，而是 `scripts/out/xskb_vt0.html`（2026-10-07 抓的 2026-2027-1 页面）
 * 按同一个注入脚本的口径抽出来的 JSON：34 个课块、rowspan/colspan 合并单元格、单双周标注、
 * 同门课按周拆块都在里面。老师姓名已替换为「某老师」，其余字段保持原样。
 *
 * 存在的意义：手写样本只能覆盖写测试时想到的形态。学校改版或解析器改动把大半课程丢掉时，
 * 这条用例会先红，不用等用户导入后发现课表少了。
 *
 * 重新生成 fixture：见 `.agents/rules/tests.md` 的「教务页面 fixture」一节。
 */
class QiangzhiScheduleParserFixtureTest {

    private fun fixture(): String =
        checkNotNull(javaClass.classLoader?.getResource("jw/xskb_vt0_extract.json")) {
            "缺少测试资源 jw/xskb_vt0_extract.json"
        }.readText()

    @Test
    fun parsesRealPageExtract() {
        val result = QiangzhiScheduleParser.parseExtractJson(fixture())

        assertTrue("应解析成功：$result", result is ImportParseResult.Success)
        val success = result as ImportParseResult.Success
        assertEquals("2026-2027-1", success.term)

        // 页面共 34 个课块，其中 1 条完全重复（教务同一格渲染两遍），导入层 distinct 后 33 条
        assertEquals(33, success.courses.size)
        assertEquals(null, success.note)

        // 抽查一条按周拆块的课：同门课第 11、15 周各一块，周次、节次、星期、老师口径都要对
        val hydraulicBlocks = success.courses.filter { it.name == "液压与气压传动A" }
        assertTrue("第 11 周应在某一块里：$hydraulicBlocks", hydraulicBlocks.any { 11 in it.weeks })
        assertTrue("第 15 周应在某一块里：$hydraulicBlocks", hydraulicBlocks.any { 15 in it.weeks })
        val hydraulic = hydraulicBlocks.first { 11 in it.weeks }
        assertEquals(1, hydraulic.day)
        assertEquals(1, hydraulic.startSection)
        assertEquals(2, hydraulic.endSection)
        assertEquals("某老师", hydraulic.teacher)
        assertTrue(hydraulic.position.contains("北B102"))
    }

    @Test
    fun everyCourseCarriesDayAndSections() {
        val success = QiangzhiScheduleParser.parseExtractJson(fixture()) as ImportParseResult.Success
        success.courses.forEach { course ->
            assertTrue("星期越界：$course", course.day in 1..7)
            assertTrue("节次越界：$course", course.startSection in 1..11)
            assertTrue("节次越界：$course", course.endSection in course.startSection..11)
            assertTrue("周次为空：$course", course.weeks.isNotEmpty())
        }
    }

    @Test
    fun sameCourseSplitAcrossWeeksIsAggregatedOrKeptDistinct() {
        // 真实页面里同一门课会按周拆成多个 li（液压 A 第 11、15 周各一块）。
        // 这里只钉住「每块都保留自己的周次集合」，聚合与否由导入层决定。
        val success = QiangzhiScheduleParser.parseExtractJson(fixture()) as ImportParseResult.Success
        val blocks = success.courses.filter { it.name == "液压与气压传动A" }
        assertTrue("至少有两块：${blocks.size}", blocks.size >= 2)
        blocks.forEach { assertTrue("周次为空：$it", it.weeks.isNotEmpty()) }
    }
}
