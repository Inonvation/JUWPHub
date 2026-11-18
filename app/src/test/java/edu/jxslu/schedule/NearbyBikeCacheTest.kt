package edu.jxslu.schedule

import edu.jxslu.schedule.domain.BikeStatus
import edu.jxslu.schedule.domain.NearbyBike
import edu.jxslu.schedule.domain.NearbyBikeCache
import edu.jxslu.schedule.domain.NearbyBikeCacheEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 附近车辆的进程内短缓存（2026-11-18）：覆盖半径、新鲜期、口径（缩小视图）与替换规则。
 *
 * 这些判定决定"拖开又拖回来时要不要联网"，边界写错就会拿旧车冒充新车、或者缓存永远不命中。
 */
class NearbyBikeCacheTest {

    private val centerLat = 28.688320
    private val centerLng = 116.028466
    private val now = 1_800_000_000_000L

    private fun north(meters: Double): Double = centerLat + meters / 111_320.0

    private fun bike(carNum: String = "100000652"): NearbyBike = NearbyBike(
        carNum = carNum,
        lat = centerLat,
        lng = centerLng,
        batteryPercent = 90.0,
        status = BikeStatus.Available,
        model = "雅迪A8",
        siteName = "教学北大楼左侧",
        campusName = "南昌工程学院",
        distanceMeters = 0,
    )

    private fun entry(
        lat: Double = centerLat,
        lng: Double = centerLng,
        at: Long = now,
        bikes: List<NearbyBike> = listOf(bike()),
        zoomedOut: Boolean = false,
    ): NearbyBikeCacheEntry = NearbyBikeCacheEntry(
        lat = lat,
        lng = lng,
        fetchedAt = at,
        bikes = bikes,
        zoomedOut = zoomedOut,
    )

    @Test
    fun `覆盖半径内命中、超出一律不命中`() {
        val entries = listOf(entry(at = now - 1_000))
        // 40 米内（覆盖半径 50 米）
        assertEquals(
            entries.first(),
            NearbyBikeCache.bestCovering(
                entries,
                north(40.0),
                centerLng,
                now,
                zoomedOut = false,
            ),
        )
        // 70 米外：那是另一片视野，重新联网
        assertNull(
            NearbyBikeCache.bestCovering(
                entries,
                north(70.0),
                centerLng,
                now,
                zoomedOut = false,
            ),
        )
    }

    @Test
    fun `过了新鲜期不命中`() {
        val stale = entry(at = now - NearbyBikeCache.TTL_MS - 1)
        assertFalse(NearbyBikeCache.isFresh(stale, now))
        assertNull(
            NearbyBikeCache.bestCovering(listOf(stale), centerLat, centerLng, now, false),
        )
        // 刚好在新鲜期边界上仍算新鲜
        val edge = entry(at = now - NearbyBikeCache.TTL_MS)
        assertTrue(NearbyBikeCache.isFresh(edge, now))
    }

    @Test
    fun `缩小视图与正常视图的结果不混用`() {
        val light = entry(zoomedOut = true)
        // 同一片、同一时刻：口径不同就不能拿来顶替
        assertNull(
            NearbyBikeCache.bestCovering(listOf(light), centerLat, centerLng, now, zoomedOut = false),
        )
        assertEquals(
            light,
            NearbyBikeCache.bestCovering(listOf(light), centerLat, centerLng, now, zoomedOut = true),
        )
    }

    @Test
    fun `多条覆盖时取最新的一条`() {
        val older = entry(at = now - 10_000, bikes = listOf(bike("100000111")))
        val newer = entry(at = now - 1_000, bikes = listOf(bike("100000222")))
        val best = NearbyBikeCache.bestCovering(
            listOf(older, newer),
            centerLat,
            centerLng,
            now,
            zoomedOut = false,
        )
        assertSame(newer, best)
    }

    @Test
    fun `写入替换同片区旧条目并丢弃过期条目`() {
        val replaced = entry(lat = north(80.0), at = now - 5_000, bikes = listOf(bike("100000111")))
        val far = entry(lat = north(500.0), at = now - 5_000, bikes = listOf(bike("100000333")))
        val stale = entry(lat = north(-500.0), at = now - NearbyBikeCache.TTL_MS - 5_000)
        val fresh = entry(at = now, bikes = listOf(bike("100000222")))
        val result = NearbyBikeCache.put(listOf(replaced, far, stale), fresh)
        // 同片区（≤100 米）被替换；远处那片保留；过期条目丢掉
        assertEquals(listOf(fresh, far), result)
    }

    @Test
    fun `最多保留规定条数且新的在前`() {
        val many = (0 until NearbyBikeCache.MAX_ENTRIES).map { index ->
            entry(lat = north(index * 200.0), at = now - index * 1_000L)
        }
        val newest = entry(lat = north(5_000.0), at = now + 1_000)
        val result = NearbyBikeCache.put(many, newest)
        assertEquals(NearbyBikeCache.MAX_ENTRIES, result.size)
        assertEquals(newest, result.first())
        // 最旧的一条被挤掉
        assertFalse(result.any { it.fetchedAt == now - (NearbyBikeCache.MAX_ENTRIES - 1) * 1_000L })
    }
}
