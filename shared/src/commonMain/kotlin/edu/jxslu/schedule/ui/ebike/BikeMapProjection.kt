package edu.jxslu.schedule.ui.ebike

import edu.jxslu.schedule.domain.BikeNearby
import edu.jxslu.schedule.domain.GcjPoint
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sinh
import kotlin.math.sqrt

/**
 * 经纬度 → 画布像素的**唯一一份**投影（2026-11-26，iOS 纯 Compose 地图）。
 *
 * ## 为什么需要它
 *
 * 本仓 Kotlin 2.1.21 的 MapKit 绑定放不了标注（`MKAnnotationProtocol.coordinate` 是 final、
 * `addOverlay(s)` 全 Unresolved，见 `BikeMapView.ios.kt` 的对照表），所以 iOS 上的车标改由
 * Compose 画布自己画（[BikeMapCanvas]）。画布要画的第一件事就是「这个经纬度落在画布哪一点」，
 * 那一件事在这里，**只有这一份**：页面的手势、镜移动、命中测试、绘制全部走同一套换算，
 * 各写一份迟早出现「点得到的圈和画出来的圈不是一个位置」。
 *
 * Android 不需要它：高德 / osmdroid 的 `Projection` 由 SDK 给（`AmapMapView` 里的
 * `projection.toScreenLocation`）。所以这里不是「第二份实现」，是「iOS 侧那一份缺失的实现」。
 *
 * ## 基准：Web 墨卡托（EPSG:3857），瓦片 256 px
 *
 * 与 osmdroid（Android 回退底图）同一个口径，缩放级别可以互相对照；坐标本身仍是 GCJ-02
 * （见 `BikeMapContract.kt` 第三节：**不要**在这里再转一次坐标系，投影与坐标系是两件事）。
 *
 * 世界宽度 = `256 * 2^zoom` 像素，`x` 自西向东、`y` 自北向南（所以在屏幕上 y 越大越靠下，
 * 与画布坐标同向，不用翻）。经度跨越 180° 时按最短方向取差（[wrapWorldX]），
 * 校园尺度用不到，但带着它才不会在赤道另一侧画出横穿整屏的线。
 */
object BikeMapProjection {

    /** 瓦片边长（px）：Web 墨卡托的标准值，缩放级别 0 时整个世界就是一块 256 px。 */
    const val TILE_SIZE = 256.0

    /**
     * 缩放级别上下限。下限 3 = 整个中国一屏；上限 20 ≈ 每像素 0.1 米，
     * 再放大也没有更细的几何可看（车辆坐标只有 6 位小数）。
     */
    const val MIN_ZOOM = 3.0
    const val MAX_ZOOM = 20.0

    /** 墨卡托在 ±85° 之外发散，温度计式的钳制（与所有 Web 地图同口径）。 */
    const val MAX_MERCATOR_LAT = 85.0

    /** 赤道周长（米），[metersPerPixel] 用。 */
    private const val EARTH_CIRCUMFERENCE_METERS = 40_075_016.686

    /** 一个纬度 = 多少米（球面近似；[gridStepMeters] 的网格线用，误差在校园尺度内可忽略）。 */
    private const val METERS_PER_DEGREE_LAT = 111_320.0

    /** 网格线之间至少留多少像素：再密就糊成一片，看不出「这是一张图」。 */
    private const val GRID_MIN_SPACING_PX = 64.0

    /**
     * 网格间距的候选台阶（米）：取第一个让屏幕间距 ≥ [GRID_MIN_SPACING_PX] 的档。
     * 用固定台阶而不是连续值，缩放时网格是「跳一档」而不是满屏线条蠕动。
     */
    private val GRID_STEPS_METERS = doubleArrayOf(
        10.0, 25.0, 50.0, 100.0, 200.0, 500.0, 1_000.0, 2_000.0, 5_000.0, 10_000.0,
    )

    /**
     * 缩放级别钳到 [MIN_ZOOM]..[MAX_ZOOM]；`NaN` / 无穷回落到默认级别（脏偏好不该画出空白）。
     *
     * 只做钳制，不替调用方判「0 算不算合法」：从偏好里读出的级别由调用方先过滤
     * （`BikeMapCanvas` 建图时把 ≤ 0 当缺失处理），捏合算出来的级别直接钳到端点即可。
     */
    fun clampZoom(zoom: Double): Double =
        if (zoom.isFinite()) zoom.coerceIn(MIN_ZOOM, MAX_ZOOM) else BikeNearby.DEFAULT_ZOOM

    /** 经度规范到 [-180, 180)：偏好的世界坐标差要按最短方向算，不能出现跨半球的假位移。 */
    fun normalizeLng(lng: Double): Double {
        var value = (lng + 180.0) % 360.0
        if (value < 0) value += 360.0
        return value - 180.0
    }

    /** 经度 → 世界 x（0..1，自西向东）。 */
    fun worldX(lng: Double): Double = (normalizeLng(lng) + 180.0) / 360.0

    /** 纬度 → 世界 y（0..1，自北向南）。 */
    fun worldY(lat: Double): Double {
        val clamped = lat.coerceIn(-MAX_MERCATOR_LAT, MAX_MERCATOR_LAT)
        val s = sin(clamped * PI / 180.0)
        return 0.5 - ln((1 + s) / (1 - s)) / (4 * PI)
    }

    /** 世界 y（0..1）→ 纬度，[worldY] 的反函数（拖动地图时用）。 */
    fun latFromWorldY(y: Double): Double =
        atan(sinh(PI * (1 - 2 * y))) * 180.0 / PI

    /** 某一缩放级别下整个世界的像素宽度。 */
    fun worldSizePx(zoom: Double): Double = TILE_SIZE * 2.0.pow(clampZoom(zoom))

    /** 世界坐标差按最短方向收进 [-0.5, 0.5)。 */
    fun wrapWorldX(delta: Double): Double {
        var value = delta
        while (value >= 0.5) value -= 1.0
        while (value < -0.5) value += 1.0
        return value
    }

    /**
     * 经纬度 → 画布像素（原点左上角）。[widthPx] / [heightPx] 是画布尺寸，
     * 视野中心 [centerLat] / [centerLng] 落在画布正中。
     */
    fun toScreen(
        centerLat: Double,
        centerLng: Double,
        zoom: Double,
        lat: Double,
        lng: Double,
        widthPx: Float,
        heightPx: Float,
    ): BikeMapPoint {
        val size = worldSizePx(zoom)
        val dx = wrapWorldX(worldX(lng) - worldX(centerLng))
        return BikeMapPoint(
            x = (dx * size + widthPx / 2.0).toFloat(),
            y = ((worldY(lat) - worldY(centerLat)) * size + heightPx / 2.0).toFloat(),
        )
    }

    /**
     * 中心点处每像素多少米。
     *
     * 与正式公式同源：地球周长 ÷ 世界像素宽度，再乘 `cos(纬度)`——墨卡托在纬度 φ 处
     * 横向被放大了 `1/cos φ` 倍，所以一像素对应的实地距离随纬度缩小。
     */
    fun metersPerPixel(lat: Double, zoom: Double): Double {
        val clamped = lat.coerceIn(-MAX_MERCATOR_LAT, MAX_MERCATOR_LAT)
        return EARTH_CIRCUMFERENCE_METERS * cos(clamped * PI / 180.0) / worldSizePx(zoom)
    }

    /**
     * 视口半对角线（米）——`onViewportMeasured` 要的那个数。
     *
     * 采查询半径与跟随回中距离都按它缩放（`BikeNearby.sampleRadiusMeters`），
     * 所以口径必须与 Android 一致：**对角线的半径**，不是宽或高的一半。
     */
    fun halfDiagonalMeters(lat: Double, zoom: Double, widthPx: Float, heightPx: Float): Double {
        if (widthPx <= 0f || heightPx <= 0f) return 0.0
        val w = widthPx.toDouble()
        val h = heightPx.toDouble()
        return sqrt(w * w + h * h) / 2.0 * metersPerPixel(lat, zoom)
    }

    /**
     * 拖动 [dxPx] / [dyPx] 之后的新中心（**手指往哪拖，地图往哪走**：屏幕内容跟着手指，
     * 所以中心点朝反方向移动）。
     */
    fun shiftCenter(
        centerLat: Double,
        centerLng: Double,
        dxPx: Double,
        dyPx: Double,
        zoom: Double,
    ): GcjPoint {
        val size = worldSizePx(zoom)
        val lng = normalizeLng(centerLng - dxPx / size * 360.0)
        val y = (worldY(centerLat) - dyPx / size).coerceIn(0.0, 1.0)
        return GcjPoint(lat = latFromWorldY(y), lng = lng)
    }

    /** 捏合因子 → 新的缩放级别（`factor` > 1 = 放大）。 */
    fun zoomAfterPinch(zoom: Double, factor: Float): Double {
        if (!factor.isFinite() || factor <= 0f || factor == 1f) return clampZoom(zoom)
        return clampZoom(zoom + ln(factor.toDouble()) / ln(2.0))
    }

    /**
     * 网格间距（米）：从 [GRID_STEPS_METERS] 里挑第一个屏幕间距 ≥ 64 px 的档。
     *
     * 网格是「没有底图的画布」上唯一的尺度参照（iOS 那侧没有瓦片可画），
     * 所以它得跟着缩放变档：一条线 = 一个能用嘴说清的整数距离。
     */
    fun gridStepMeters(metersPerPixel: Double): Double {
        if (!metersPerPixel.isFinite() || metersPerPixel <= 0.0) return GRID_STEPS_METERS.last()
        val minStep = metersPerPixel * GRID_MIN_SPACING_PX
        return GRID_STEPS_METERS.firstOrNull { it >= minStep } ?: GRID_STEPS_METERS.last()
    }

    /**
     * 画布上要画几条网格线（每个方向）：`ceil(边长 / 间距) + 1`，上下各留一条。
     * 与 [gridStepMeters] 一样是纯算术，单测直接钉住（避免死循环式的 while 计数）。
     */
    fun gridLineCount(spanPx: Float, stepMeters: Double, metersPerPixel: Double): Int {
        if (spanPx <= 0f || stepMeters <= 0.0 || metersPerPixel <= 0.0) return 0
        val stepPx = stepMeters / metersPerPixel
        if (!stepPx.isFinite() || stepPx <= 0.0) return 0
        val count = ceil(spanPx.toDouble() / stepPx).toInt() + 2
        // 防御脏偏好算出的极端缩放：一屏画上千条线只会卡死帧率，也没有信息量
        return count.coerceIn(0, 200)
    }

    /**
     * 某个纬度上、每 [stepMeters] 米对应的经度差。
     *
     * 经线间距随纬度收窄（`cos φ`），与 [metersPerPixel] 同一个因子；纬度取绝对值再钳，
     * 免得在极点附近除以 0。
     */
    fun lngStepForMeters(stepMeters: Double, lat: Double): Double {
        val clamped = lat.coerceIn(-MAX_MERCATOR_LAT, MAX_MERCATOR_LAT)
        val metersPerDegreeLng = METERS_PER_DEGREE_LAT * cos(clamped * PI / 180.0)
        if (metersPerDegreeLng <= 0.0) return 0.0
        return stepMeters / metersPerDegreeLng
    }

    /** 每 [stepMeters] 米对应的纬度差（球面近似，见 [METERS_PER_DEGREE_LAT]）。 */
    fun latStepForMeters(stepMeters: Double): Double = stepMeters / METERS_PER_DEGREE_LAT
}

/** 画布坐标（像素，原点左上角）。用数据类而不是 Compose 的 `Offset`：本文件要能被纯 JVM 单测。 */
data class BikeMapPoint(val x: Float, val y: Float) {

    /** 到另一点的距离（像素）。命中测试用。 */
    fun distanceTo(other: BikeMapPoint): Float {
        val dx = x - other.x
        val dy = y - other.y
        return sqrt(dx * dx + dy * dy)
    }
}
