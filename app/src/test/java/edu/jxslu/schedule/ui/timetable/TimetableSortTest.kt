package edu.jxslu.schedule.ui.timetable

import edu.jxslu.schedule.domain.Timetable
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 课表管理页的显示顺序（2026-11-19 用户要求「按学期排序」）：钉住三条——
 * 学期从新到旧、没有学期号的手建表排最后、不动 `sortOrder`。
 */
class TimetableSortTest {

    private fun table(id: Long, name: String, term: String?, sortOrder: Int = 0) = Timetable(
        id = id,
        name = name,
        createdAt = id,
        sortOrder = sortOrder,
        slotsCustomized = false,
        term = term,
    )

    @Test
    fun sortsByTermNewestFirst() {
        val list = listOf(
            table(1, "大一上", "2024-2025-1", sortOrder = 1),
            table(2, "大三上", "2026-2027-1", sortOrder = 0),
            table(3, "大二下", "2025-2026-2", sortOrder = 2),
        )
        assertEquals(listOf("大三上", "大二下", "大一上"), sortTimetablesForDisplay(list).map { it.name })
    }

    /** 手建表（term 为空）不属于任何学期：排最后，别插在时间线中间。 */
    @Test
    fun tablesWithoutTermGoLast() {
        val list = listOf(
            table(1, "我的课表", null, sortOrder = 0),
            table(2, "大二下", "2025-2026-2", sortOrder = 1),
        )
        assertEquals(listOf("大二下", "我的课表"), sortTimetablesForDisplay(list).map { it.name })
    }

    /** 同 term 的复制表按 sortOrder 稳定排序，不依赖列表输入顺序。 */
    @Test
    fun sameTermFallsBackToSortOrder() {
        val list = listOf(
            table(5, "大二下 副本", "2025-2026-2", sortOrder = 9),
            table(4, "大二下", "2025-2026-2", sortOrder = 2),
        )
        assertEquals(listOf("大二下", "大二下 副本"), sortTimetablesForDisplay(list).map { it.name })
    }

    /** 非法学期号（脏 term）当「没有学期」处理，不能让它排到最前面。 */
    @Test
    fun invalidTermTreatedAsNoTerm() {
        val list = listOf(
            table(1, "脏数据", "2026-2027-9"),
            table(2, "大三上", "2026-2027-1"),
        )
        assertEquals(listOf("大三上", "脏数据"), sortTimetablesForDisplay(list).map { it.name })
    }
}
