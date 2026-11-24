package edu.jxslu.schedule.domain

import edu.jxslu.schedule.domain.ExamChangeDetector.Change
import edu.jxslu.schedule.domain.ExamChangeDetector.Kind
import edu.jxslu.schedule.domain.ExamMapper.ExamEntry

/**
 * 考试变动的正文口径（收件箱 §4.38 与系统通知 §4.33 共用一份，2026-11-05 收口）。
 *
 * 起因：检测判据比 5 个字段（起止时刻 / 考场 / 校区 / 座位号），正文却只渲染
 * 日期 + 开始时刻 + 考场——座位号或校区一变，消息就渲染成新旧一模一样的文本
 * （用户实测：8 场考试一次刷 8 条看起来没变的「考试安排调整」）。现在正文按
 * [ExamChangeDetector.diff] 逐字段列「旧 → 新」，改什么就说什么。
 *
 * 口径：
 * - 新增 → 「《课名》 日期 起–止 考场」；
 * - 调整 → 「《课名》 字段 旧 → 新」，多字段用「；」分隔。**不写日期**：日期是身份键
 *   （[ExamChangeDetector.keyOf]），调整时必然没变，写出来只是噪音；收件箱正文只有 2 行
 *   （`InboxScreen` / `InboxTodayCard`），行宽留给「到底改了什么」。
 */
object ExamChangeText {

    /** 一条变动的正文（不含「新增/调整」前缀，通知层自己拼）。 */
    fun describe(change: Change): String {
        val entry = change.entry
        val head = "《${entry.name}》"
        val before = change.before
        if (change.kind == Kind.NEW || before == null) {
            val slot = slotOf(entry)
            return if (slot.isBlank()) head else "$head $slot"
        }
        val parts = ExamChangeDetector.diff(before, entry).map { it.text }
        return if (parts.isEmpty()) head else "$head ${parts.joinToString("；")}"
    }

    /** 新增考试的时刻与考场：日期 + 起–止 + 考场。 */
    private fun slotOf(entry: ExamEntry): String = buildString {
        if (entry.date.isNotBlank()) append(entry.date)
        if (entry.startTime.isNotBlank()) {
            if (isNotEmpty()) append(" ")
            append(entry.startTime)
            if (entry.endTime.isNotBlank()) append("–${entry.endTime}")
        }
        if (entry.room.isNotBlank()) {
            if (isNotEmpty()) append(" ")
            append(entry.room)
        }
    }
}
