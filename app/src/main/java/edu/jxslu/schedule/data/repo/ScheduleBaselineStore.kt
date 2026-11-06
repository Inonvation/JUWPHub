package edu.jxslu.schedule.data.repo

import android.content.Context
import edu.jxslu.schedule.data.local.JsonFileStore
import edu.jxslu.schedule.domain.ScheduleCourseRow
import kotlinx.serialization.Serializable

/**
 * 课表基线快照（DESIGN §4.17，2026-10-04 重立）：「上次与教务达成一致时的课表状态」，
 * `filesDir` 下一份 JSON。
 *
 * 调课检测拿它与教务最新抓取做对比——**不是**拿当前课表比：用户手动改课、手动加课
 * 都只动 Room，基线保持不动，差异才纯粹是「教务那边变了什么」。基线在三个时点推进：
 * 导入写库成功（`importParsedCourses` / `importJson`）、合并差异成功（`applyScheduleChanges`）、
 * 检查首跑（老用户升级上来没有基线，用第一次抓到的教务状态建立，不通知）。
 *
 * **允许存空列表**（一门课没有的学期是教务的合法状态，空基线才能在下学期正确报出
 * 「教务新增了 N 门课」）——但**只由「可信的成功路径」写入**：写库成功、抓取解析成功。
 * 文件坏了按「没有基线」处理，下轮检查重建，不影响主链路。只存课表条目，不存任何凭证。
 */
class ScheduleBaselineStore(context: Context) {

    @Serializable
    data class Snapshot(
        /** 基线归属的课表。调课检查只对「当前活跃课表」做，id 不匹配 = 首跑。 */
        val timetableId: Long = 0,
        /** 基线的学年学期（如 2026-2027-1）；与抓取学期不一致 = 换学期，按首跑处理。 */
        val term: String = "",
        val courses: List<ScheduleCourseRow> = emptyList(),
        val savedAt: Long = 0,
    )

    /**
     * 落盘交给共享实现（2026-11-06 审计 C2）。**用同步版**：`load`/`save` 是同步 API，
     * 调用点分布在 `ScheduleRepository` 的多个 suspend 函数与导入链路里，改成 suspend 会连带
     * 改一串调用点；同步版不加锁——本类合并前也没有互斥（写入点各自在事务提交之后）。
     * **文件名不能改**（`schedule_baseline.json`）：改名等于用户丢掉基线，下次检查按首跑重建、
     * 静默吞掉一轮「教务改了什么」。
     */
    private val store = JsonFileStore(context, SNAPSHOT_FILE, Snapshot.serializer())

    fun load(): Snapshot = store.readBlocking() ?: Snapshot()

    fun save(snapshot: Snapshot) {
        store.writeBlocking(snapshot)
    }

    companion object {
        private const val SNAPSHOT_FILE = "schedule_baseline.json"

        fun snapshotOf(timetableId: Long, term: String, courses: List<edu.jxslu.schedule.domain.Course>, savedAt: Long) =
            Snapshot(
                timetableId = timetableId,
                term = term,
                courses = courses.map(ScheduleCourseRow::of),
                savedAt = savedAt,
            )
    }
}
