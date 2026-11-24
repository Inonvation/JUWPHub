package edu.jxslu.schedule.domain

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/**
 * 「当前课表」按日期自动选定（DESIGN §4.17）。
 *
 * 根因：归档会给每个学期各建一张课表，若「当前课表」只在导入那一刻定一次，开学后
 * 用户看到的还是上学期的表。这里把选择规则抽成纯函数，冷启动 / 导入完成 / 周期检查
 * 三处共用同一套判定。
 *
 * 三条口径：
 * 1. **只认带学期号的课表**：手建表也有默认开学日，参与判定会误命中；
 * 2. 今天落在某张表的学期区间内就选它，多张命中取 term 最大者（学期延长 / 重叠时
 *    以新学期为准）；
 * 3. 假期（没有任何表命中）退回「教务最近一次给出的当前学期」那张，再没有就返回 null
 *    ——保持现状，不回退到历史表。
 *
 * 判定不读写 DataStore，调用方拿结果去 `setCurrentTimetable`。
 */
object CurrentTimetableRules {

    /** 一张课表参与判定所需的全部信息。 */
    data class Candidate(
        val timetableId: Long,
        val term: String?,
        /** yyyy-MM-dd；null = 没配过学期，不参与日期判定。 */
        val startDate: LocalDate?,
        val totalWeeks: Int,
    )

    fun pick(candidates: List<Candidate>, today: LocalDate, preferredTerm: String?): Long? {
        val covering = candidates.filter { covers(it, today) }
        if (covering.isNotEmpty()) {
            return covering.maxByOrNull { TermNaming.sortKey(it.term) ?: Int.MIN_VALUE }?.timetableId
        }
        val preferred = preferredTerm?.takeIf { it.isNotBlank() } ?: return null
        return candidates.firstOrNull { it.term == preferred }?.timetableId
    }

    /** 今天是否落在该表的学期区间 `[开学日, 开学日 + 总周数×7)` 内。 */
    fun covers(candidate: Candidate, today: LocalDate): Boolean {
        if (candidate.term.isNullOrBlank()) return false
        val start = candidate.startDate ?: return false
        val weeks = candidate.totalWeeks.coerceIn(1, 30)
        return today >= start && today < start.plus(DatePeriod(days = weeks * 7))
    }
}
