package edu.jxslu.schedule.ui.jwvw

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 课表导入成功后那条气泡的文案（DESIGN §3.3，2026-11-21 加口径说明）。
 *
 * 钉住三件容易被顺手改坏的事：
 * 1. **口径说明必须跟着成功结果一起出现**——用户拿这条课表去教室，调课 / 停课这类导入之后
 *    才由教务录入的变动脚本抓不到。「导入成功」四个字单独出现等于暗示课表与教务一致。
 * 2. **合并与覆盖说的是两件事**：合并报「新课」且说明重复已跳过，覆盖报全部条数且说明清空，
 *    两者不能互相顶用（覆盖说成「新课」会让人以为只是追加）。
 * 3. **课表名读不到时不留下空的「到「」」**（旧数据或课表刚被删）。
 */
class ScheduleOutcomeTextTest {

    @Test
    fun mergeSaysNewCoursesAndKeepsCaveat() {
        val text = scheduleOutcomeText(count = 3, merge = true, timetableName = "大三上")
        assertTrue(text.contains("已合并导入 3 门新课到「大三上」"))
        assertTrue(text.contains("重复课程已跳过"))
        assertTrue(text.contains(SCHEDULE_IMPORT_CAVEAT))
    }

    @Test
    fun coverReportsAllRowsAndKeepsCaveat() {
        val text = scheduleOutcomeText(count = 60, merge = false, timetableName = "大三上")
        assertTrue(text.contains("已覆盖写入 60 条课次到「大三上」"))
        assertTrue(text.contains("原有课程已清空"))
        assertFalse(text.contains("新课"))
        assertTrue(text.contains(SCHEDULE_IMPORT_CAVEAT))
    }

    @Test
    fun blankTimetableNameDropsTargetPhrase() {
        val text = scheduleOutcomeText(count = 12, merge = true, timetableName = "  ")
        assertFalse(text.contains("到「"))
        assertTrue(text.contains("已合并导入 12 门新课"))
    }

    @Test
    fun caveatPointsAtJwAsTheAuthority() {
        assertTrue(SCHEDULE_IMPORT_CAVEAT.contains("以教务课表为准"))
        // 不带句末标点：调用点自己拼「。」，否则气泡里会出现「。。」
        assertFalse(SCHEDULE_IMPORT_CAVEAT.endsWith("。"))
    }
}
