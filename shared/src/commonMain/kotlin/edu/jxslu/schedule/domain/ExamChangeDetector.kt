package edu.jxslu.schedule.domain

import edu.jxslu.schedule.domain.ExamMapper.ExamEntry

/**
 * 考试安排变更检测（DESIGN §4.33）：考试变动提醒的纯逻辑。
 *
 * 教务考试是「一次发布 + 零星调整」：新发布一门考试、某门考试改了时间或考场。
 * 身份键 = 课程号（空则课程名）+ 日期——同名课可能不同日期考两场，日期参与身份。
 * 「旧有新无」（撤考）忽略：极罕见，宁可少打扰。
 */
object ExamChangeDetector {

    /** 新发布 / 时间地点调整。 */
    enum class Kind { NEW, UPDATED }

    data class Change(
        val kind: Kind,
        val entry: ExamEntry,
        /** [Kind.UPDATED] 时是调整前的那条。 */
        val before: ExamEntry? = null,
    )

    fun detect(old: List<ExamEntry>, new: List<ExamEntry>): List<Change> {
        val oldByKey = old.associateBy(::keyOf)
        val changes = mutableListOf<Change>()
        for (entry in new) {
            // 日期解析失败的行没法跟旧数据对账（键会漂移成「每次都是新的」），跳过
            if (entry.date.isBlank()) continue
            val before = oldByKey[keyOf(entry)]
            val change = when {
                before == null -> Change(Kind.NEW, entry)
                before.isSameSlot(entry) -> null
                else -> Change(Kind.UPDATED, entry, before)
            }
            change?.let(changes::add)
        }
        return changes
    }

    /** 调整判据：起止时刻、考场、校区、座位号任一变了才算，教师/场次名不动。 */
    private fun ExamEntry.isSameSlot(other: ExamEntry): Boolean = diff(this, other).isEmpty()

    /** 判据字段，顺序即正文展示顺序。 */
    enum class Field(val label: String) {
        TIME("时间"),
        ROOM("考场"),
        CAMPUS("校区"),
        SEAT("座位号"),
    }

    /** 单个字段的新旧值；[text] 是正文用的「标签 旧 → 新」。 */
    data class FieldChange(val field: Field, val before: String, val after: String) {
        /** 空值写成「空」：座位号/校区从无到有时，不写「空」就看不出变了。 */
        val text: String = "${field.label} ${before.ifBlank { "空" }} → ${after.ifBlank { "空" }}"
    }

    /**
     * 逐字段差异（空列表 = 没变）。**判据与正文共用这一份**：检测比哪些字段、消息就该说
     * 哪些字段——2026-11-05 之前正文只渲染日期/开始时刻/考场，座位号或校区变化就渲染成
     * 新旧一模一样的「考试安排调整」（用户实测 8 场考试一次刷 8 条）。
     */
    fun diff(before: ExamEntry, after: ExamEntry): List<FieldChange> = buildList {
        if (before.startTime != after.startTime || before.endTime != after.endTime) {
            add(FieldChange(Field.TIME, rangeOf(before), rangeOf(after)))
        }
        if (before.room != after.room) add(FieldChange(Field.ROOM, before.room, after.room))
        if (before.campus != after.campus) add(FieldChange(Field.CAMPUS, before.campus, after.campus))
        if (before.seatNo != after.seatNo) add(FieldChange(Field.SEAT, before.seatNo, after.seatNo))
    }

    /** 起–止（缺一半只写有的那半，别渲染出「–17:10」）。 */
    private fun rangeOf(entry: ExamEntry): String = when {
        entry.startTime.isBlank() -> entry.endTime
        entry.endTime.isBlank() -> entry.startTime
        else -> "${entry.startTime}–${entry.endTime}"
    }

    /**
     * 身份键 = 课程号（空则课程名）+ 日期。公开自 2026-09-30：考试页把「本次更新探测到的
     * 新增/调整」标到列表卡上，用的必须是检测器这一份键，两份各写一个迟早会漂。
     */
    fun keyOf(entry: ExamEntry): String =
        entry.courseNo.ifBlank { entry.name } + "|" + entry.date
}
