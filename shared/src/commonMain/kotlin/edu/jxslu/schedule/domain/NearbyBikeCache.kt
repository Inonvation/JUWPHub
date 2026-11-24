package edu.jxslu.schedule.domain

/**
 * 附近车辆结果的**内存短缓存**（2026-11-18）。
 *
 * 为什么加它：拖动地图每次停手都要查一遍附近车辆（一次刷新可能 1 个点、也可能 5~9 个点），
 * 而"拖开看一眼、又拖回来"是找车时最常见的动作——半分钟内回到同一片，服务端大概率还是那批
 * 车，再发一轮请求只是白等一个往返。命中就用上一批结果，**不联网、不转圈**。
 *
 * 缓存的持有者是 `BikeMapViewModel`（页面会话内），不跨页面、不落盘。
 *
 * 与图层缓存（[ZoneCache]）的区别：
 * - 图层落盘、12 小时；车辆**只在内存**、**30 秒**——车随时被骑走，落盘或放久都会误导；
 * - 车辆缓存只在**拖动停手**那条路径上生效；「刷新」按钮、还车后的自动重查、开锁 / 锁车 /
 *   还车这些写动作都会清空它（见 `BikeMapViewModel.cancelQueriesForRideAction`），
 *   用户明确要最新数据时不能拿旧快照顶替。
 *
 * 纯判定都在这里（JVM 可测），编排在 `BikeMapViewModel`。
 */
data class NearbyBikeCacheEntry(
    /** 这次查询的中心（GCJ-02）。 */
    val lat: Double,
    val lng: Double,
    /** 落地时刻（epoch 毫秒）。 */
    val fetchedAt: Long,
    /** 这批结果（可能是空列表：空区域也值得缓存，避免反复查没人住的角落）。 */
    val bikes: List<NearbyBike>,
    /** 是不是缩小视图下的轻量结果（见 [BikeNearby.isZoomedOut]）；两种口径不能混用。 */
    val zoomedOut: Boolean,
)

object NearbyBikeCache {

    /**
     * 新鲜期（毫秒）。
     *
     * 30 秒：够覆盖"拖开又拖回来"这种连续操作，又短到不会让用户拿半分钟前的车位去开锁。
     * 再长就会遇上"刚显示的车已经被骑走"，用户走到跟前才发现。
     */
    const val TTL_MS = 30_000L

    /**
     * 一条缓存覆盖的中心半径（米）。50 米：与位移闸门（30 米）同一量级，只服务
     * "几乎同一片视野"的来回平移；拖出去几百米之后再回来，那一片的车早就不是同一批了。
     */
    const val COVER_RADIUS_METERS = 50.0

    /** 写入时替换同片区旧条目的半径（米）：略大于覆盖半径，避免攒出一串几乎重合的条目。 */
    const val REPLACE_RADIUS_METERS = 100.0

    /** 最多留几条（超了丢最旧的）：用户常去的几片能找回来就够。 */
    const val MAX_ENTRIES = 8

    /** [entry] 是否还在新鲜期内。[now] 为当前时刻（epoch 毫秒）。 */
    fun isFresh(entry: NearbyBikeCacheEntry, now: Long): Boolean =
        now - entry.fetchedAt in 0..TTL_MS

    /**
     * 覆盖 ([lat], [lng]) 的新鲜缓存里最新的一条；没有就返回 null。
     *
     * [zoomedOut] 必须一致：缩小视图只查了中心一页（展示范围也小），拿它当正常视图的结果
     * 会少一大片车；反过来拿正常结果当缩小视图倒不算错，但地图上会突然多出一批圈，
     * 与"缩小视图不再大范围刷新"的口径不一致，所以两种口径各存各的。
     */
    fun bestCovering(
        entries: List<NearbyBikeCacheEntry>,
        lat: Double,
        lng: Double,
        now: Long,
        zoomedOut: Boolean,
    ): NearbyBikeCacheEntry? = entries
        .filter {
            it.zoomedOut == zoomedOut &&
                isFresh(it, now) &&
                BikeNearby.distanceMeters(it.lat, it.lng, lat, lng) <= COVER_RADIUS_METERS
        }
        .maxByOrNull { it.fetchedAt }

    /**
     * 写入一条结果：过期条目与同一片区的旧条目都丢掉，再按时间取最新的 [MAX_ENTRIES] 条。
     */
    fun put(
        entries: List<NearbyBikeCacheEntry>,
        entry: NearbyBikeCacheEntry,
    ): List<NearbyBikeCacheEntry> {
        val kept = entries.filter {
            isFresh(it, entry.fetchedAt) &&
                BikeNearby.distanceMeters(it.lat, it.lng, entry.lat, entry.lng) >
                REPLACE_RADIUS_METERS
        }
        return (kept + entry)
            .sortedByDescending { it.fetchedAt }
            .take(MAX_ENTRIES)
    }
}
