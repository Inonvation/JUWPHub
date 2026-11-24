package edu.jxslu.schedule.data.repo

import android.content.Context
import edu.jxslu.schedule.data.local.JsonFileStore

/**
 * 选课轮次快照（DESIGN §4.35）：学生选课中心列表落盘（`filesDir` 一份 JSON）。
 *
 * 轮次不像选课结果有 Room 表——它的生命周期以「选课季」为单位（一年出几次、开学前后
 * 各一轮），App 只需要"当前最新一份"；提醒调度也从这份快照取数据（不额外打教务）。
 *
 * 读写都必须在 IO 线程（调用方负责调度）；读写都不抛、坏了等于没有快照（容错在 [JsonFileStore]），
 * 下次同步重建。**只存轮次条目，不存任何凭证**。
 *
 * 与考试基线（`ExamSnapshotStore` 的「宁旧勿空」）不同：这里**允许存空**——
 * `count=0`（非选课期）是教务的权威状态，存空才能让页面如实显示、让过期提醒被撤销。
 *
 * iOS 移植 S4（2026-11-24）：[SelectionSync] 搬进了 `shared/commonMain`，快照的模型与语义
 * 抽成共享层接口 [SelectionRoundsSource]，本类只剩 `JsonFileStore` 这一层落盘。
 */
class SelectionRoundsStore(context: Context) : SelectionRoundsSource {

    /**
     * 落盘交给共享实现（2026-11-06 审计 C2）。**用同步版**：`load`/`save` 是同步 API
     * （`SelectionSync` 在 suspend 里调、提醒调度那边也是同步取），改 suspend 会连带改一串调用点；
     * 同步版不加锁——本类合并前也没有互斥。**文件名不能改**（`selection_rounds.json`）：
     * 改名等于用户丢掉轮次快照，提醒会按「没抓到轮次」全部撤销。
     */
    private val store =
        JsonFileStore(context, SNAPSHOT_FILE, SelectionRoundsSource.Snapshot.serializer())

    override fun load(): SelectionRoundsSource.Snapshot =
        store.readBlocking() ?: SelectionRoundsSource.Snapshot()

    /**
     * 落盘并返回是否成功。调用方据它决定要不要推进「上次检查时间」：写失败却推进了，
     * 下一轮会按旧快照判「轮次没变」，提醒静默失效一整轮。
     */
    override fun save(snapshot: SelectionRoundsSource.Snapshot): Boolean = store.writeBlocking(snapshot)

    companion object {
        private const val SNAPSHOT_FILE = "selection_rounds.json"
    }
}
