package edu.jxslu.schedule.data.kqcx

import android.content.Context
import edu.jxslu.schedule.data.local.JsonFileStore
import edu.jxslu.schedule.domain.GcjPoint
import edu.jxslu.schedule.domain.KvcxParkSpot
import edu.jxslu.schedule.domain.KvcxZones
import edu.jxslu.schedule.domain.ZoneCache
import edu.jxslu.schedule.domain.ZoneCacheEntry
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.io.File

/**
 * 还车点 / 禁停区图层的落盘缓存（DESIGN §3.9，2026-09-28；策略在 [ZoneCache]）。
 *
 * `filesDir` 下一份 JSON，存「以某点为中心查到的那批图层」（最多 [ZoneCache.MAX_ENTRIES] 条）。
 * 冷启动进地图、或拖回缓存覆盖的片区时先用它把图层顶起来，命中新鲜缓存连请求都不发
 * （判定全在 domain 层，本类只负责读写）。
 *
 * 读写都在 IO 线程（[JsonFileStore] 自己调度）；读写都不抛——缓存坏了等于没有缓存，不影响主链路。
 * **只存公开的图层数据**：不含 token、不含账号信息（与本地快照类缓存同一纪律）。
 *
 * 文件可注入（主构造收 [File]）：缓存文件格式是隐式契约，用临时文件往返一遍钉在单测里，
 * 改字段名这类"读旧文件读空"的静默回归当场就红。
 */
class ZoneCacheStore(private val file: File) {

    constructor(context: Context) : this(File(context.filesDir, CACHE_FILE))

    /**
     * 落盘交给共享实现（2026-11-06 审计 C2）：读不到/坏了回空、写失败不抛、临时文件原子替换，
     * 与另外四份同构快照共用一处；这里只剩「域对象 ↔ 可序列化影子」的映射。
     *
     * 串行化由它内部的 Mutex 提供（合并前是本类自己那把，语义不变）：地图页只有一个 VM 会写，
     * `EbikeMapCache` 的删除是单向的。**文件名不能改**——改名等于用户丢掉已有的图层缓存。
     */
    private val store = JsonFileStore(file, ListSerializer(EntryRow.serializer()))

    /** 全部缓存条目（读不到/坏了回空表，调用方当"没有缓存"处理）。 */
    suspend fun load(): List<ZoneCacheEntry> = store.read().orEmpty().map(::toDomain)

    /** 覆盖写整份缓存（条数由 [ZoneCache.put] 控制在小表，整写比增量稳）。 */
    suspend fun save(entries: List<ZoneCacheEntry>) {
        store.write(entries.map(::toRow))
    }

    /** 当前占用字节。**调用方负责 IO 线程**（`EbikeMapCache` 统计时用）。 */
    fun bytes(): Long = store.bytes()

    /** 删除缓存文件（「清除地图缓存」用）。缓存删掉即失效，不需要额外状态。 */
    fun delete() = store.delete()

    // ---------- 序列化影子（本体不是 @Serializable，字段一一对应；与本地快照类缓存同手法） ----------

    private fun toDomain(row: EntryRow): ZoneCacheEntry = ZoneCacheEntry(
        lat = row.lat,
        lng = row.lng,
        fetchedAt = row.fetchedAt,
        zones = KvcxZones(
            parkSpots = row.spots.map { spot ->
                KvcxParkSpot(
                    lat = spot.lat,
                    lng = spot.lng,
                    outline = spot.outline.map { GcjPoint(it.lat, it.lng) },
                    name = spot.name,
                )
            },
            nogoZones = row.nogo.map { outline -> outline.map { GcjPoint(it.lat, it.lng) } },
        ),
    )

    private fun toRow(entry: ZoneCacheEntry): EntryRow = EntryRow(
        lat = entry.lat,
        lng = entry.lng,
        fetchedAt = entry.fetchedAt,
        spots = entry.zones.parkSpots.map { spot ->
            SpotRow(
                lat = spot.lat,
                lng = spot.lng,
                outline = spot.outline.map { PointRow(it.lat, it.lng) },
                name = spot.name,
            )
        },
        nogo = entry.zones.nogoZones.map { outline ->
            outline.map { PointRow(it.lat, it.lng) }
        },
    )

    @Serializable
    private data class EntryRow(
        val lat: Double,
        val lng: Double,
        val fetchedAt: Long,
        val spots: List<SpotRow> = emptyList(),
        val nogo: List<List<PointRow>> = emptyList(),
    )

    @Serializable
    private data class SpotRow(
        val lat: Double,
        val lng: Double,
        val outline: List<PointRow> = emptyList(),
        /**
         * 还车点名（2026-09-30 加）。**必须有默认值**：老版本写下的缓存文件没有这一列，
         * 缺默认值会让整份文件解码失败——那等于用户升级后白丢一次图层缓存
         * （静默回归，页面只是少一层「P」，没人会报错）。
         */
        val name: String = "",
    )

    @Serializable
    private data class PointRow(val lat: Double, val lng: Double)

    companion object {
        /** 缓存文件名（`filesDir` 下；「清除地图缓存」就删它）。 */
        const val CACHE_FILE = "bike_zones_cache.json"
    }
}
