package edu.jxslu.schedule.data.repo

import edu.jxslu.schedule.domain.Course
import edu.jxslu.schedule.domain.ScheduleCourseRow
import kotlinx.serialization.Serializable

/**
 * 调课检测基线的**读写口**（iOS 移植 S4，2026-11-24）。
 *
 * [ScheduleRepository] 搬进 `shared/commonMain` 之后还要读 / 推进基线，而真身
 * `ScheduleBaselineStore` 靠 `Context` + `java.io.File` 落盘（`JsonFileStore`），
 * 留在 `:app`。这里只抽它被仓库层用到的那三个东西：`load` / `save` / [Snapshot]。
 *
 * 不是第二套实现：全仓只有 `:app` 的 `ScheduleBaselineStore` 一个实现，
 * 语义（允许空列表、文件坏了按「没有基线」、文件名不变）仍写在那个实现类上。
 */
interface ScheduleBaseline {

    fun load(): Snapshot

    fun save(snapshot: Snapshot): Boolean

    /**
     * 一份基线快照。字段口径与落盘格式绑定，**不能改名**（改名等于丢掉老用户的基线，
     * 下轮检查按首跑重建，静默吞掉一轮「教务改了什么」）。
     */
    @Serializable
    data class Snapshot(
        /**
         * 基线归属的课表。变动检测只对**term 最大的那张课表**做（2026-11-19 起，
         * 此前是「当前活跃课表」），id 不匹配 = 首跑。
         */
        val timetableId: Long = 0,
        /** 基线的学年学期（如 2026-2027-1）；与抓取学期不一致 = 换学期，按首跑处理。 */
        val term: String = "",
        val courses: List<ScheduleCourseRow> = emptyList(),
        val savedAt: Long = 0,
    )

    companion object {

        /** 由课表条目造快照（过滤考试等口径由调用方在传参前处理）。 */
        fun snapshotOf(
            timetableId: Long,
            term: String,
            courses: List<Course>,
            savedAt: Long,
        ): Snapshot = Snapshot(
            timetableId = timetableId,
            term = term,
            courses = courses.map(ScheduleCourseRow::of),
            savedAt = savedAt,
        )
    }
}
