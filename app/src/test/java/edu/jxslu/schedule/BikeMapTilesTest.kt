package edu.jxslu.schedule

import edu.jxslu.schedule.domain.BikeNearby
import edu.jxslu.schedule.ui.ebike.BikeMapProjection
import edu.jxslu.schedule.ui.ebike.BikeMapTiles
import edu.jxslu.schedule.ui.ebike.BikeTileKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.pow

/**
 * 快趣地图的**底图瓦片算术**（iOS 画布地图的底图，2026-11-26）。
 *
 * 这些数字错了，画面上的表现是「瓦片贴到隔壁那条街」或「一屏请求上百块」——本机（Windows）
 * 编不了 iOS，所以这一层只能力争在纯 JVM 单测里钉死。钉四件事：
 *
 * 1. 瓦片级别 = 相机级别的整数档低一级，钳在 1..18（高德 z19 起回空瓦片）；
 * 2. 一块瓦片在画布上的边长 = 256 × 2^(相机 - 瓦片)，与投影同一单位（设备像素）；
 * 3. 视野中心一定落在算出来的某一瓦片里（否则拖动时会有一块永远补不上）；
 * 4. 脏尺寸 / 超密视野回空列表（宁可这一帧不贴底图，也不发几十个请求）。
 */
class BikeMapTilesTest {

    private val centerLat = BikeNearby.DEFAULT_CENTER_LAT
    private val centerLng = BikeNearby.DEFAULT_CENTER_LNG

    /** 手机竖屏的设备像素尺寸：iPhone 14 Pro ≈ 1179×2556（这里取整便于断言）。 */
    private val widthPx = 1170f
    private val heightPx = 2530f

    private fun keys(zoom: Double = 17.0): List<BikeTileKey> =
        BikeMapTiles.visibleKeys(centerLat, centerLng, zoom, widthPx, heightPx)

    private fun assertClose(
        expected: Double,
        actual: Double,
        tolerance: Double = 1e-6,
        hint: String = "",
    ) {
        assertTrue("$hint 期望 $expected，实际 $actual", abs(expected - actual) <= tolerance)
    }

    @Test
    fun `瓦片级别比相机低一级并钳在 1 到 18`() {
        // 降一级是为了让 256px 栅格的注记在 3x 屏上还能读（见 BikeMapTiles.tileZoom 的说明）
        assertEquals(16, BikeMapTiles.tileZoom(17.0))
        assertEquals(16, BikeMapTiles.tileZoom(17.99))
        assertEquals(17, BikeMapTiles.tileZoom(18.0))
        // 相机上限是 20，但高德栅格 z19 起是 179 字节的空瓦片
        assertEquals(18, BikeMapTiles.tileZoom(20.0))
        // 下限：相机最低 3，瓦片级别跟着钳
        assertEquals(2, BikeMapTiles.tileZoom(3.0))
        assertEquals(2, BikeMapTiles.tileZoom(0.5))
        // 脏偏好（NaN / 无穷）由 clampZoom 收口到默认级别（17 → z16）
        assertEquals(BikeNearby.DEFAULT_ZOOM.toInt() - 1, BikeMapTiles.tileZoom(Double.NaN))
        assertEquals(
            BikeNearby.DEFAULT_ZOOM.toInt() - 1,
            BikeMapTiles.tileZoom(Double.POSITIVE_INFINITY),
        )
    }

    @Test
    fun `一块瓦片按相机与瓦片级别之差放大`() {
        // 相机 17 取 z16：512 px（2 倍放大）；再低一级是 1024 px
        assertClose(512.0, BikeMapTiles.tileSpanPx(17.0, 16))
        assertClose(1024.0, BikeMapTiles.tileSpanPx(17.0, 15))
        assertClose(512.0, BikeMapTiles.tileSpanPx(18.0, 17))
        // 相机比瓦片上限高时继续放大（z20 用 z18 的瓦片，铺 1024 px）
        assertClose(1024.0, BikeMapTiles.tileSpanPx(20.0, 18))
        // 小数级别的半档
        assertClose(256.0 * 2.0.pow(1.5), BikeMapTiles.tileSpanPx(17.5, 16), 1e-9)
    }

    @Test
    fun `校园尺度的视野算出若干块瓦片且全部落在合法行列`() {
        val result = keys()
        assertTrue("视野不该为空", result.isNotEmpty())
        assertTrue("一屏瓦片数应远小于上限，实际 ${result.size}", result.size <= BikeMapTiles.MAX_KEYS)
        val count = BikeMapTiles.tileCount(16)
        result.forEach { key ->
            assertEquals(16, key.z)
            assertTrue("x=${key.x} 越界", key.x in 0 until count)
            assertTrue("y=${key.y} 越界", key.y in 0 until count)
        }
        // 同一块瓦片不该被列两次（重复请求会被在途去重挡住，但列表本身也应干净）
        assertEquals(result.size, result.toSet().size)
    }

    @Test
    fun `视野中心落在算出来的某一块瓦片里`() {
        val result = keys()
        val centerX = widthPx / 2.0
        val centerY = heightPx / 2.0
        val span = BikeMapTiles.tileSpanPx(17.0, 16)
        val covered = result.any { key ->
            val topLeft = BikeMapTiles.tileTopLeft(key, centerLat, centerLng, 17.0, widthPx, heightPx)
            centerX >= topLeft.x && centerX <= topLeft.x + span &&
                centerY >= topLeft.y && centerY <= topLeft.y + span
        }
        assertTrue("视野中心 $centerX,$centerY 没有被任何瓦片覆盖", covered)
    }

    @Test
    fun `瓦片左上角与同一点的投影一致`() {
        // 取视野中心那一带的一块 z16 瓦片，瓦片中心（x+0.5, y+0.5）应落在「左上角 + 半块」上——
        // 这是瓦片与车标共用一个投影的关键：差半块就是整条街的偏移
        val key = BikeTileKey(z = 16, x = 53_890, y = 27_312)
        val topLeft = BikeMapTiles.tileTopLeft(key, centerLat, centerLng, 17.0, widthPx, heightPx)
        val count = BikeMapTiles.tileCount(key.z).toDouble()
        val midLat = BikeMapProjection.latFromWorldY((key.y + 0.5) / count)
        val midLng = (key.x + 0.5) / count * 360.0 - 180.0
        val mid = BikeMapProjection.toScreen(
            centerLat,
            centerLng,
            17.0,
            midLat,
            midLng,
            widthPx,
            heightPx,
        )
        val half = BikeMapTiles.tileSpanPx(17.0, key.z) / 2.0
        assertClose(topLeft.x + half, mid.x.toDouble(), 1e-3, "x")
        assertClose(topLeft.y + half, mid.y.toDouble(), 1e-3, "y")
    }

    @Test
    fun `脏尺寸与超密视野直接回空`() {
        assertTrue(BikeMapTiles.visibleKeys(centerLat, centerLng, 17.0, 0f, 2530f).isEmpty())
        assertTrue(BikeMapTiles.visibleKeys(centerLat, centerLng, 17.0, 1170f, -1f).isEmpty())
        // 1e6 px 的视野在 z18 下要几百块瓦片：宁可这一帧不贴底图，也不发几百个请求
        assertTrue(
            BikeMapTiles.visibleKeys(centerLat, centerLng, 3.0, 1_000_000f, 1_000_000f).isEmpty(),
        )
    }

    @Test
    fun `跨 180 度经线时 x 绕回合法区间`() {
        val result = BikeMapTiles.visibleKeys(
            centerLat = centerLat,
            centerLng = 179.999,
            zoom = 3.0,
            widthPx = 1170f,
            heightPx = 2530f,
        )
        assertTrue(result.isNotEmpty())
        val count = BikeMapTiles.tileCount(result.first().z)
        result.forEach { key ->
            assertTrue("x=${key.x} 应绕回 0..${count - 1}", key.x in 0 until count)
        }
    }
}
