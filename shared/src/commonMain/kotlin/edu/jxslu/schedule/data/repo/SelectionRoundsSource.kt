package edu.jxslu.schedule.data.repo

import edu.jxslu.schedule.domain.SelectionRound
import kotlinx.serialization.Serializable

/**
 * 选课轮次快照的**读写口**（iOS 移植 S4，2026-11-24）。
 *
 * 与 [ScheduleBaseline] 同一手法：[SelectionSync] 搬进 `shared/commonMain` 之后仍要把轮次
 * 落盘，而真身 `SelectionRoundsStore` 靠 `Context` + `java.io.File`（`JsonFileStore`）留在
 * `:app`。这里只抽「快照模型 + 读写两个方法」，实现仍只有那一个。
 *
 * 语义（允许存空、文件坏了按「没有快照」、文件名不变）写在实现类的 KDoc 上。
 */
interface SelectionRoundsSource {

    fun load(): Snapshot

    fun save(snapshot: Snapshot): Boolean

    /**
     * 一份轮次快照。字段口径与落盘格式绑定，**不能改名**（改名等于丢掉用户已有的轮次快照，
     * 提醒会按「没抓到轮次」全部撤销）。允许存空：`count=0`（非选课期）是教务的权威状态。
     */
    @Serializable
    data class Snapshot(
        /** 抓取成功时刻（epoch millis）；0 = 未知。 */
        val fetchedAt: Long = 0L,
        val rounds: List<Row> = emptyList(),
    )

    /** [SelectionRound] 的可序列化影子（本体不是 @Serializable，字段一一对应）。 */
    @Serializable
    data class Row(
        val id: String = "",
        val term: String = "",
        val name: String = "",
        val timeText: String = "",
        val startAt: Long? = null,
        val endAt: Long? = null,
        val canPreview: Boolean = false,
    )

    companion object {

        fun rowOf(round: SelectionRound) = Row(
            id = round.id,
            term = round.term,
            name = round.name,
            timeText = round.timeText,
            startAt = round.startAt,
            endAt = round.endAt,
            canPreview = round.canPreview,
        )

        fun rowToRound(row: Row) = SelectionRound(
            id = row.id,
            term = row.term,
            name = row.name,
            timeText = row.timeText,
            startAt = row.startAt,
            endAt = row.endAt,
            canPreview = row.canPreview,
        )
    }
}
