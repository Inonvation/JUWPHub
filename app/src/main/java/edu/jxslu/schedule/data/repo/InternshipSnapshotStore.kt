package edu.jxslu.schedule.data.repo

import android.content.Context
import edu.jxslu.schedule.data.local.JsonFileStore
import edu.jxslu.schedule.domain.InternshipBase
import kotlinx.serialization.Serializable

/**
 * 实习基地快照（DESIGN §4.43）：上次成功抓取的清单落盘（`filesDir` 一份 JSON），
 * 让「短时间内几乎不变」的参考数据不必每次进页都打教务——12 小时新鲜期内直接用快照
 * （[edu.jxslu.schedule.domain.InternshipCacheRules]），顶栏手动刷新才强刷。
 *
 * 读写都必须在 IO 线程（调用方负责调度）；读写都不抛、坏了等于没有快照（容错在 [JsonFileStore]），
 * 下次按首次抓取重建。**不进备份**（本机缓存，重取即有）。只存基地字段，不存任何凭证。
 */
class InternshipSnapshotStore(context: Context) {

    @Serializable
    data class Snapshot(
        /** 抓取成功时刻（毫秒）；0 = 未记。 */
        val fetchedAt: Long = 0L,
        /** 教务声明的总数（-1 = 未声明）。 */
        val declaredCount: Int = -1,
        val bases: List<Row> = emptyList(),
    )

    /** [InternshipBase] 的可序列化影子（本体不是 @Serializable，字段一一对应）。 */
    @Serializable
    data class Row(
        val id: String = "",
        val no: String = "",
        val name: String = "",
        val category: String = "",
        val unit: String = "",
        val leader: String = "",
        val address: String = "",
        val phone: String = "",
        val builtDate: String = "",
        val listedYears: String = "",
        val capacity: String = "",
        val campusContact: String = "",
        val campusPhone: String = "",
        val college: String = "",
        val intro: String = "",
    )

    /**
     * 落盘交给共享实现（2026-11-06 审计 C2）。**用同步版**：`load`/`save` 是同步 API，
     * 调用方（`InternshipBasesLoader`）本来就在 IO 线程上，改 suspend 会连带改一串调用点；
     * 同步版不加锁——本类合并前也没有互斥。**文件名不能改**（`internship_bases.json`）：
     * `AppCacheCleaner` 按这个旧名字统计/清理，改名等于用户那边多出一份谁也管不到的残留。
     */
    private val store = JsonFileStore(context, SNAPSHOT_FILE, Snapshot.serializer())

    fun load(): Snapshot = store.readBlocking() ?: Snapshot()

    /**
     * 原样落盘（写失败吞错）。**空结果也写**：「count≤0 的空」是教务的权威状态，
     * 存下来页面才会如实显示（先例：SelectionRoundsStore 的「轮次快照允许存空」）；
     * 「宁旧勿空」的取舍在装载器（异常空不写、更新失败回退旧快照），不在这一层。
     */
    fun save(snapshot: Snapshot) {
        store.writeBlocking(snapshot)
    }

    companion object {
        private const val SNAPSHOT_FILE = "internship_bases.json"

        fun rowOf(base: InternshipBase) = Row(
            id = base.id,
            no = base.no,
            name = base.name,
            category = base.category,
            unit = base.unit,
            leader = base.leader,
            address = base.address,
            phone = base.phone,
            builtDate = base.builtDate,
            listedYears = base.listedYears,
            capacity = base.capacity,
            campusContact = base.campusContact,
            campusPhone = base.campusPhone,
            college = base.college,
            intro = base.intro,
        )

        fun rowToBase(row: Row) = InternshipBase(
            id = row.id,
            no = row.no,
            name = row.name,
            category = row.category,
            unit = row.unit,
            leader = row.leader,
            address = row.address,
            phone = row.phone,
            builtDate = row.builtDate,
            listedYears = row.listedYears,
            capacity = row.capacity,
            campusContact = row.campusContact,
            campusPhone = row.campusPhone,
            college = row.college,
            intro = row.intro,
        )
    }
}
