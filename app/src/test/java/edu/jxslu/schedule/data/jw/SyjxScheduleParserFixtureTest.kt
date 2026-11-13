package edu.jxslu.schedule.data.jw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 实验课表解析器对**真实教务页面**的回归（2026-11-13）。
 *
 * 输入是 `scripts/out/syxkb.html`（2026-10-07 抓的 2026-2027-1 页面）按同一个注入脚本
 * 的口径抽出来的 JSON：16 个原始课块、同门课按周拆块、同门课分布在不同实训室。
 * 实验课表页不提供教师字段，fixture 里也没有教师姓名。
 *
 * 重新生成 fixture：见 `.agents/rules/tests.md` 的「教务页面 fixture」一节。
 */
class SyjxScheduleParserFixtureTest {

    private fun fixture(): String =
        checkNotNull(javaClass.classLoader?.getResource("jw/syxkb_extract.json")) {
            "缺少测试资源 jw/syxkb_extract.json"
        }.readText()

    @Test
    fun parsesRealPageExtractAndMergesByWeek() {
        val result = SyjxScheduleParser.parseExtractJson(fixture())

        assertTrue("应解析成功：$result", result is ImportParseResult.Success)
        val success = result as ImportParseResult.Success
        assertEquals("2026-2027-1", success.term)

        // 16 个课块按「名称 + 星期 + 节次 + 地点」聚合成 12 条
        assertEquals(12, success.courses.size)

        val motor = success.courses.first { it.name == "机电传动控制B" }
        assertEquals(6, motor.day)
        assertEquals(3, motor.startSection)
        assertEquals(4, motor.endSection)
        assertEquals(setOf(1, 3), motor.weeks)
        assertEquals("工程训练中心207", motor.position)
        // 实验课表页根本没有教师字段，解析器必须保持空串而不是瞎猜
        assertEquals("", motor.teacher)
    }

    @Test
    fun sameCourseAtDifferentRoomsStaysSeparate() {
        // 工程训练按批次分周：同名课在不同实训室上课，只按课程名合并会把批次并丢地点
        val success = SyjxScheduleParser.parseExtractJson(fixture()) as ImportParseResult.Success
        val rooms = success.courses.filter { it.name == "机械制造基础A" }.map { it.position }.toSet()
        assertTrue("不同实训室应保留成不同条目：$rooms", rooms.size >= 2)
    }

    @Test
    fun everyCourseCarriesDayWeekAndSections() {
        val success = SyjxScheduleParser.parseExtractJson(fixture()) as ImportParseResult.Success
        success.courses.forEach { course ->
            assertTrue("星期越界：$course", course.day in 1..7)
            assertTrue("节次越界：$course", course.startSection in 1..11)
            assertTrue("节次越界：$course", course.endSection in course.startSection..11)
            assertTrue("周次为空：$course", course.weeks.isNotEmpty())
        }
    }
}
