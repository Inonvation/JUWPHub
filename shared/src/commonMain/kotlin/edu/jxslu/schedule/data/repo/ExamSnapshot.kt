package edu.jxslu.schedule.data.repo

import edu.jxslu.schedule.domain.ExamMapper.ExamEntry
import kotlinx.serialization.Serializable

/**
 * 考试基线快照的**读写口**（iOS 移植 S4，2026-11-24）。
 *
 * 与 [ScheduleBaseline] / [SelectionRoundsSource] 同一手法：[ExamSync] 搬进
 * `shared/commonMain` 之后仍要落一份「上次抓到的考试全量」。[ExamSync] 搬过来时真身
 * `ExamSnapshotStore` 还在 `:app`（要 `Context` + `java.io.File`），所以先把「模型 + 读写
 * 两个方法」抽在这里；**S7（2026-11-24）起实现也搬进来了**，接口与实现现在同在
 * `data/repo`。实现仍只有那一个，「空条目不写」「文件名不变」的口径写在实现类的 KDoc 上。
 */
interface ExamSnapshot {

    fun load(): Snapshot

    /** 落盘并返回是否真的写了（空条目的「有意的没动」算成功）。 */
    fun save(snapshot: Snapshot): Boolean

    /**
     * 一份考试快照。字段口径与落盘格式绑定，**不能改名**（改名等于丢掉已有基线，
     * 下轮按首跑重报「教务新增了 N 场考试」）。
     */
    @Serializable
    data class Snapshot(
        /** 快照对应的学年学期（如 2026-2027-1）；空 = 未记。 */
        val term: String = "",
        val exams: List<ExamEntry> = emptyList(),
    )
}
