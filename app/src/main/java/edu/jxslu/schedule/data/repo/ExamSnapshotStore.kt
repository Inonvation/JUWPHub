package edu.jxslu.schedule.data.repo

import android.content.Context
import edu.jxslu.schedule.data.local.JsonFileStore
import edu.jxslu.schedule.domain.ExamMapper.ExamEntry
import kotlinx.serialization.Serializable

/**
 * 考试基线快照（DESIGN §4.33）：上次成功抓到的考试安排落盘（`filesDir` 下一份 JSON）。
 *
 * 考试不像成绩有 Room 表——它只在手动导入时经 [ExamMapper] 映射成课程行，App 内没有
 * 「教务眼里的考试全量」可比对。变动检测需要旧值，就把上次成功抓取的最小字段存一份。
 *
 * 读写都必须在 IO 线程（调用方负责调度）；读写都不抛、坏了等于没有快照（容错在 [JsonFileStore]），
 * 下次按「首跑」重建基线，不影响主链路。**只存考试条目，不存任何凭证**。
 */
class ExamSnapshotStore(context: Context) {

    @Serializable
    data class Snapshot(
        /** 快照对应的学年学期（如 2026-2027-1）；空 = 未记。 */
        val term: String = "",
        val exams: List<ExamEntry> = emptyList(),
    )

    /**
     * 落盘交给共享实现（2026-11-06 审计 C2）。**用同步版**：本类 `load`/`save` 是同步 API，
     * 调用方要么已在 IO 线程、要么是组合期一次性小文件读（`JwHubScreen` 那条注释就是为此写的），
     * 改成 suspend 会连带改一串调用点。同步版**不加锁**——本类合并前也没有互斥，别顺手把
     * 并发面改掉。**文件名不能改**（`exam_snapshot.json`）：改名等于用户丢掉已有基线，
     * 下轮按首跑重报「教务新增了 N 场考试」。
     */
    private val store = JsonFileStore(context, SNAPSHOT_FILE, Snapshot.serializer())

    fun load(): Snapshot = store.readBlocking() ?: Snapshot()

    /** 空条目不写：一次「取到但为空」不该把上次有数的那份基线洗掉，宁旧勿空。 */
    fun save(snapshot: Snapshot) {
        if (snapshot.exams.isEmpty()) return
        store.writeBlocking(snapshot)
    }

    companion object {
        private const val SNAPSHOT_FILE = "exam_snapshot.json"
    }
}
