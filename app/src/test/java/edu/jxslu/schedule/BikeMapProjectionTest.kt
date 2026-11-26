package edu.jxslu.schedule

import edu.jxslu.schedule.domain.BikeNearby
import edu.jxslu.schedule.domain.GcjPoint
import edu.jxslu.schedule.ui.ebike.BikeMapPoint
import edu.jxslu.schedule.ui.ebike.BikeMapProjection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * 快趣地图的经纬度 → 画布投影（iOS 纯 Compose 地图的唯一换算，2026-11-26）。
 *
 * 钉住四件事，都是「写错了画面上就是错的位置」：
 * 1. 视野中心落在画布正中，北在上、东在右（方向反了车会跑到底图对面）；
 * 2. `metersPerPixel` / 半对角线（`onViewportMeasured` 的口径）与缩放级别的比例关系；
 * 3. 拖动（`shiftCenter`）与缩放（`zoomAfterPinch`）可逆、钳在合法区间；
 * 4. 尺度网格的档位与条数（没有底图时它是唯一的尺度参照）。
 */
class BikeMapProjectionTest {

    private val centerLat = BikeNearby.DEFAULT_CENTER_LAT
    private val centerLng = BikeNearby.DEFAULT_CENTER_LNG
    private val widthPx = 1080f
    private val heightPx = 2000f

    private fun project(lat: Double, lng: Double, zoom: Double = 17.0): BikeMapPoint =
        BikeMapProjection.toScreen(centerLat, centerLng, zoom, lat, lng, widthPx, heightPx)

    private fun assertClose(expected: Double, actual: Double, tolerance: Double = 1e-9, hint: String = "") {
        assertTrue("$hint 期望 $expected，实际 $actual", abs(expected - actual) <= tolerance)
    }

    @Test
    fun `视野中心落在画布正中`() {
        val point = project(centerLat, centerLng)
        assertClose(widthPx / 2.0, point.x.toDouble(), 1e-3, "x")
        assertClose(heightPx / 2.0, point.y.toDouble(), 1e-3, "y")
    }

    @Test
    fun `北在画布上方东在画布右方`() {
        // 纬度更大的点更靠北 → 屏幕 y 更小；经度更大的点更靠东 → 屏幕 x 更大
        val north = project(centerLat + 0.002, centerLng)
        val south = project(centerLat - 0.002, centerLng)
        val east = project(centerLat, centerLng + 0.002)
        val west = project(centerLat, centerLng - 0.002)
        assertTrue("北点应在中心之上", north.y < heightPx / 2f)
        assertTrue("南点应在中心之下", south.y > heightPx / 2f)
        assertTrue("东点应在中心之右", east.x > widthPx / 2f)
        assertTrue("西点应在中心之左", west.x < widthPx / 2f)
        // 同纬度同距离：南北对称（墨卡托在非赤道处只有二阶不对称，留 0.05 px 余量）
        assertClose(
            (heightPx / 2f - north.y).toDouble(),
            (south.y - heightPx / 2f).toDouble(),
            0.05,
        )
    }

    @Test
    fun `世界坐标与纬度互为反函数`() {
        listOf(-60.0, -10.0, 0.0, 28.68832, 45.5, 80.0).forEach { lat ->
            assertClose(lat, BikeMapProjection.latFromWorldY(BikeMapProjection.worldY(lat)), 1e-9, "lat=$lat")
        }
        // 赤道在世界正中
        assertClose(0.5, BikeMapProjection.worldY(0.0))
        // 经度 ±180 落在世界两端
        assertClose(0.0, BikeMapProjection.worldX(-180.0))
        assertClose(0.5, BikeMapProjection.worldX(0.0))
    }

    @Test
    fun `经度跨半球时取最短方向`() {
        // +179 与 -179 只差 2 度，不是 358 度（否则画面上会出现一条横穿整屏的假位移）
        assertClose(-0.25, BikeMapProjection.wrapWorldX(0.75))
        assertClose(0.25, BikeMapProjection.wrapWorldX(-0.75))
        assertClose(0.1, BikeMapProjection.wrapWorldX(0.1))
        assertClose(-180.0, BikeMapProjection.normalizeLng(180.0))
        assertClose(179.0, BikeMapProjection.normalizeLng(-181.0))
    }

    @Test
    fun `每像素米数与缩放级别的比例关系`() {
        // 赤道最小缩放（z3）：地球周长 ÷ 世界像素宽度（256 × 2³ = 2048）
        assertClose(156_543.03 / 8.0, BikeMapProjection.metersPerPixel(0.0, BikeMapProjection.MIN_ZOOM), 0.01)
        // 每放大一级，一像素代表的距离减半
        assertClose(
            BikeMapProjection.metersPerPixel(centerLat, 17.0) / 2.0,
            BikeMapProjection.metersPerPixel(centerLat, 18.0),
            1e-6,
        )
        // 纬度 60° 处横向距离是赤道的一半（墨卡托的 1/cos φ 放大）
        assertClose(
            BikeMapProjection.metersPerPixel(0.0, 17.0) / 2.0,
            BikeMapProjection.metersPerPixel(60.0, 17.0),
            1e-6,
        )
    }

    @Test
    fun `视口半对角线按对角线算而不是边长`() {
        // 600 × 800 的视口：对角线 1000，半对角线 500 px
        val mpp = BikeMapProjection.metersPerPixel(centerLat, 17.0)
        assertClose(500.0 * mpp, BikeMapProjection.halfDiagonalMeters(centerLat, 17.0, 600f, 800f), 1e-6)
        // 没有尺寸时给 0（首帧还没测到布局，别报一个假数字上去）
        assertClose(0.0, BikeMapProjection.halfDiagonalMeters(centerLat, 17.0, 0f, 800f))
        // 手机尺度（1080 × 2000）在默认缩放 z17 下约一公里——与 `BikeNearby` 的注释同量级
        val half = BikeMapProjection.halfDiagonalMeters(centerLat, BikeNearby.DEFAULT_ZOOM, 1080f, 2000f)
        assertTrue("z17 半对角线应约 1 公里，实际 $half", half in 1_000.0..1_400.0)
    }

    @Test
    fun `拖动中心点可逆`() {
        val zoom = 17.0
        val shifted = BikeMapProjection.shiftCenter(centerLat, centerLng, dxPx = 120.0, dyPx = -80.0, zoom = zoom)
        // 手指往右拖（+x）→ 视野中心往西走
        assertTrue("向右拖，中心应西移", shifted.lng < centerLng)
        // 往右下拖之后，原来的中心点会出现在画布左上（屏幕内容跟着手指走）
        val point = BikeMapProjection.toScreen(
            centerLat = shifted.lat,
            centerLng = shifted.lng,
            zoom = zoom,
            lat = centerLat,
            lng = centerLng,
            widthPx = widthPx,
            heightPx = heightPx,
        )
        assertClose(widthPx / 2.0 + 120.0, point.x.toDouble(), 1e-3)
        assertClose(heightPx / 2.0 - 80.0, point.y.toDouble(), 1e-3)
        // 反着拖回来，回到原点
        val back = BikeMapProjection.shiftCenter(shifted.lat, shifted.lng, -120.0, 80.0, zoom)
        assertClose(centerLat, back.lat, 1e-9)
        assertClose(centerLng, back.lng, 1e-9)
    }

    @Test
    fun `捏合换算成缩放级别并钳在上下限`() {
        assertClose(18.0, BikeMapProjection.zoomAfterPinch(17.0, 2f), 1e-6)
        assertClose(16.0, BikeMapProjection.zoomAfterPinch(17.0, 0.5f), 1e-6)
        // 手指没动 / 非法因子：级别不变
        assertClose(17.0, BikeMapProjection.zoomAfterPinch(17.0, 1f), 1e-6)
        assertClose(17.0, BikeMapProjection.zoomAfterPinch(17.0, 0f), 1e-6)
        assertClose(17.0, BikeMapProjection.zoomAfterPinch(17.0, Float.NaN), 1e-6)
        // 上下限
        assertClose(BikeMapProjection.MAX_ZOOM, BikeMapProjection.zoomAfterPinch(20.0, 4f), 1e-6)
        assertClose(BikeMapProjection.MIN_ZOOM, BikeMapProjection.zoomAfterPinch(3.0, 0.1f), 1e-6)
        // 非有限值回落到默认级别（脏偏好不该画出空白）；有限值只钳到端点
        assertClose(BikeNearby.DEFAULT_ZOOM, BikeMapProjection.clampZoom(Double.NaN))
        assertClose(BikeNearby.DEFAULT_ZOOM, BikeMapProjection.clampZoom(Double.POSITIVE_INFINITY))
        assertClose(BikeMapProjection.MIN_ZOOM, BikeMapProjection.clampZoom(0.0))
        assertClose(BikeMapProjection.MIN_ZOOM, BikeMapProjection.clampZoom(-5.0))
        assertClose(BikeMapProjection.MIN_ZOOM, BikeMapProjection.clampZoom(1.0))
        assertClose(BikeMapProjection.MAX_ZOOM, BikeMapProjection.clampZoom(25.0))
        assertClose(17.5, BikeMapProjection.clampZoom(17.5))
    }

    @Test
    fun `尺度网格按台阶跳档且间距不小于下限`() {
        // 一像素 0.5 米：64 px 至少 32 米 → 落到 50 米那一档
        assertClose(50.0, BikeMapProjection.gridStepMeters(0.5))
        assertClose(500.0, BikeMapProjection.gridStepMeters(5.0))
        // 极端值不返回 0（否则画不出来也除不尽）
        assertTrue(BikeMapProjection.gridStepMeters(0.0) > 0.0)
        assertTrue(BikeMapProjection.gridStepMeters(Double.NaN) > 0.0)
        // 真实缩放级别下，屏幕上两条线之间至少 64 px
        listOf(14.0, 17.0, 19.0).forEach { zoom ->
            val mpp = BikeMapProjection.metersPerPixel(centerLat, zoom)
            val step = BikeMapProjection.gridStepMeters(mpp)
            assertTrue("z$zoom 网格间距 ${step / mpp} px 应不小于 64", step / mpp >= 64.0 - 1e-6)
        }
    }

    @Test
    fun `网格条数按跨度和间距算并封顶`() {
        // 1000 px、每格 100 px → 10 格 → 12 条（上下各留一条）
        assertEquals(12, BikeMapProjection.gridLineCount(1000f, 100.0, 1.0))
        // 尺寸 / 参数非法：0 条
        assertEquals(0, BikeMapProjection.gridLineCount(0f, 100.0, 1.0))
        assertEquals(0, BikeMapProjection.gridLineCount(1000f, 0.0, 1.0))
        assertEquals(0, BikeMapProjection.gridLineCount(1000f, 100.0, 0.0))
        // 脏缩放算出的极端条数被封顶，不会一屏画上千条线
        assertTrue(BikeMapProjection.gridLineCount(100_000f, 1.0, 1e-6) <= 200)
    }

    @Test
    fun `经度步长随纬度收窄`() {
        val atEquator = BikeMapProjection.lngStepForMeters(100.0, 0.0)
        val atCampus = BikeMapProjection.lngStepForMeters(100.0, centerLat)
        val at60 = BikeMapProjection.lngStepForMeters(100.0, 60.0)
        assertTrue("高纬度的 100 米应换成更大的经度差", at60 > atCampus && atCampus > atEquator)
        assertClose(100.0 / 111_320.0, atEquator, 1e-12)
        // 纬度步长与经度无关
        assertClose(100.0 / 111_320.0, BikeMapProjection.latStepForMeters(100.0), 1e-12)
        // 极点附近不会除出 0 / 无穷
        assertTrue(BikeMapProjection.lngStepForMeters(100.0, 89.9) > 0.0)
    }

    @Test
    fun `画布点距离可用于命中测试`() {
        val a = BikeMapPoint(0f, 0f)
        val b = BikeMapPoint(3f, 4f)
        assertClose(5.0, a.distanceTo(b).toDouble(), 1e-6)
        assertClose(5.0, b.distanceTo(a).toDouble(), 1e-6)
        assertClose(0.0, a.distanceTo(a).toDouble())
        // 与投影串起来：两点的像素距离 = 米数 / 每像素米数（水平方向）
        val mpp = BikeMapProjection.metersPerPixel(centerLat, 17.0)
        val meters = 200.0
        val latStep = meters / 111_320.0
        val p1 = project(centerLat, centerLng)
        val p2 = project(centerLat + latStep, centerLng)
        assertClose(meters / mpp, p1.distanceTo(p2).toDouble(), 0.5)
        // 经度方向同理：0.001° 对应世界宽度的 0.001/360，像素距离按当前缩放换算
        val eastPx = project(centerLat, centerLng + 0.001).x - p1.x
        assertClose(BikeMapProjection.worldSizePx(17.0) * 0.001 / 360.0, eastPx.toDouble(), 1e-3)
    }

    @Test
    fun `投影不改变坐标系只换表达方式`() {
        // 同一份 GCJ-02 点集：投影只做平面换算，不涉及任何坐标系转换（契约第三节）
        val bikes = listOf(
            GcjPoint(28.6887, 116.0291),
            GcjPoint(28.6881, 116.0288),
        )
        val points = bikes.map { project(it.lat, it.lng) }
        assertEquals(2, points.size)
        // 纬度更大的那辆在上方
        assertTrue(points[0].y < points[1].y)
    }
}
