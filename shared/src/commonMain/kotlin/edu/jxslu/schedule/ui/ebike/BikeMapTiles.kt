package edu.jxslu.schedule.ui.ebike

import androidx.compose.runtime.State
import androidx.compose.ui.graphics.ImageBitmap
import kotlin.math.floor
import kotlin.math.pow

/**
 * 底图瓦片（2026-11-26，iOS 快趣地图「真的能看到路」）。
 *
 * ## 为什么需要这一层
 *
 * [BikeMapCanvas] 在没有底图时只画尺度网格——车标、围栏、还车点全都在，但看不出
 * 「那是哪栋楼、路在哪」（用户装机第一眼的原话是「地图不显示」）。Android 的底图由高德
 * / osmdroid 提供，iOS 只能自己把瓦片贴上去。
 *
 * 瓦片来源与 Android 的回退底图**同一个接口**（`app/.../ui/ebike/OsmMapView.kt` 的
 * `AMAP_TILE_SOURCE`，高德非公开栅格、`style=9`、坐标基准 GCJ-02）：两端看到的路网与
 * 楼栋名因此是同一份数据，坐标也不用再转一次。取图与解码在 `shared/src/iosMain` 的
 * `IosBikeMapTiles`，这里只放**纯算术**（能在本机 JVM 单测里钉住的部分）。
 *
 * ## 三个口径，改之前先看
 *
 * 1. **瓦片级别比相机级别低一级**（[tileZoom]）。相机 17 取 z16 的瓦片、铺 512 px：一块
 *    256 px 的栅格在 3x 屏上按 1:1 画，注记只有 11 个设备像素（≈3.7pt），根本读不出来；
 *    降一级放大 2 倍后注记约 7pt，路名与楼名才看得见。再降一级（4 倍）字更大，但高德的
 *    低一级底图只剩主路、没有楼名——两级之间的取舍以「能认出这是哪个楼」为准。
 *    相机级别 20 时仍取 18（高德栅格 z19 起回空瓦片，实测 179 字节），由绘制侧放大。
 * 2. **经度方向环绕**：x 超出 `0..2^z-1` 时绕回去，跨 180° 经线才不会整列空掉。
 * 3. **数量上限** [MAX_KEYS]：视野算出几十块就说明相机级别或画布尺寸脏了，
 *    与其发几十个请求，不如这一帧不贴瓦片（网格照旧，页面不白）。
 */
object BikeMapTiles {

    /**
     * 瓦片最高级别：高德栅格在 z19 起返回空瓦片（179 字节的占位图），所以真实瓦片只到 18。
     * 相机可以更高（[BikeMapProjection.MAX_ZOOM] = 20），多出来的倍数由绘制侧放大。
     */
    const val MAX_TILE_ZOOM = 18

    /** 瓦片最低级别：相机下限是 3（[BikeMapProjection.MIN_ZOOM]），低于 0 没有瓦片。 */
    const val MIN_TILE_ZOOM = 1

    /** 一屏最多请求多少块瓦片。正常手机横竖屏都在 30 块以内，超过就是脏输入。 */
    const val MAX_KEYS = 48

    /** 瓦片级别比相机级别低几级（见 [tileZoom] 的说明）。 */
    private const val TILE_ZOOM_OFFSET = 1

    /**
     * 相机级别 → 瓦片级别：`floor(相机)` **减一**，再钳到 [MIN_TILE_ZOOM]..[MAX_TILE_ZOOM]。
     *
     * 减一是为了注记大小：画布用设备像素做投影（与 Android 同尺度），而瓦片是给 256 CSS px
     * 设计的栅格——1:1 贴上去，3x 屏上 11px 的楼名只有 3.7pt。降一级放大 2 倍后约 7pt，
     * 是「看得清字」与「还认得出楼」之间的那一档。
     *
     * `NaN` / 无穷由 [BikeMapProjection.clampZoom] 收口到默认级别（17 → z16）。
     */
    fun tileZoom(zoom: Double): Int {
        val clamped = BikeMapProjection.clampZoom(zoom)
        return (floor(clamped).toInt() - TILE_ZOOM_OFFSET)
            .coerceIn(MIN_TILE_ZOOM, MAX_TILE_ZOOM)
    }

    /** 某一级别的瓦片行列数（`2^z`）。 */
    fun tileCount(z: Int): Int = 1 shl z.coerceIn(0, 20)

    /**
     * 相机级别 [zoom] 下，一块 [z] 级瓦片在画布上占多少像素。
     *
     * `256 * 2^(zoom - z)`：墨卡托的缩放是 2 的幂，所以每差一级就是两倍。
     * [z] 由 [tileZoom] 给（比相机低一级），相机 17 时是 512 px。
     */
    fun tileSpanPx(zoom: Double, z: Int): Double =
        BikeMapProjection.TILE_SIZE * 2.0.pow(BikeMapProjection.clampZoom(zoom) - z)

    /**
     * 视野内需要的瓦片（按行优先排列，不含视野外的一圈）。
     *
     * [widthPx] / [heightPx] 是画布的设备像素尺寸（与 [BikeMapProjection.toScreen] 同一单位）。
     */
    fun visibleKeys(
        centerLat: Double,
        centerLng: Double,
        zoom: Double,
        widthPx: Float,
        heightPx: Float,
    ): List<BikeTileKey> {
        if (widthPx <= 0f || heightPx <= 0f) return emptyList()
        val clamped = BikeMapProjection.clampZoom(zoom)
        val z = tileZoom(clamped)
        val count = tileCount(z)
        val tileSize = BikeMapProjection.TILE_SIZE
        // 画布 1 px 等于多少个「z 级瓦片像素」：相机比瓦片级别大时是放大，所以要缩回去
        val scale = 2.0.pow(z - clamped)
        val centerX = BikeMapProjection.worldX(centerLng) * count * tileSize
        val centerY = BikeMapProjection.worldY(centerLat) * count * tileSize
        val halfWidth = widthPx / 2.0 * scale
        val halfHeight = heightPx / 2.0 * scale
        val firstX = floor((centerX - halfWidth) / tileSize).toInt()
        val lastX = floor((centerX + halfWidth) / tileSize).toInt()
        val firstY = floor((centerY - halfHeight) / tileSize).toInt()
        val lastY = floor((centerY + halfHeight) / tileSize).toInt()
        if (lastX < firstX || lastY < firstY) return emptyList()
        if ((lastX - firstX + 1) * (lastY - firstY + 1) > MAX_KEYS) return emptyList()

        val keys = ArrayList<BikeTileKey>((lastX - firstX + 1) * (lastY - firstY + 1))
        for (y in firstY..lastY) {
            // 纬度方向不环绕：越界就是视野已经在世界之外（脏相机），直接跳过
            if (y < 0 || y >= count) continue
            for (x in firstX..lastX) {
                keys += BikeTileKey(z = z, x = wrap(x, count), y = y)
            }
        }
        return keys
    }

    /**
     * 一块瓦片的左上角落在画布哪里（设备像素）。用瓦片**西北角**的经纬度反投到画布，
     * 与车标 / 围栏走同一个 [BikeMapProjection.toScreen]，所以瓦片与车标不会错位。
     */
    fun tileTopLeft(
        key: BikeTileKey,
        centerLat: Double,
        centerLng: Double,
        zoom: Double,
        widthPx: Float,
        heightPx: Float,
    ): BikeMapPoint {
        val count = tileCount(key.z).toDouble()
        val lng = key.x / count * 360.0 - 180.0
        val lat = BikeMapProjection.latFromWorldY(key.y / count)
        return BikeMapProjection.toScreen(
            centerLat = centerLat,
            centerLng = centerLng,
            zoom = zoom,
            lat = lat,
            lng = lng,
            widthPx = widthPx,
            heightPx = heightPx,
        )
    }

    private fun wrap(index: Int, count: Int): Int = ((index % count) + count) % count
}

/** 一块瓦片的坐标：级别 + 行列（Web 墨卡托的标准编号，与高德的 `appmaptile` 参数同义）。 */
data class BikeTileKey(val z: Int, val x: Int, val y: Int)

/**
 * 底图瓦片来源（页面槽位）。
 *
 * 只有 iOS 会实现：Android 的底图在高德 / osmdroid 视图里，共享层画布那边
 * `BikeMapCanvas` 拿到的是 `null`，行为与 2026-11-26 之前完全一样（画网格 + 车标）。
 *
 * ## 为什么 `cached` 和 `load` 分开
 *
 * 绘制是同步的（`DrawScope` 里不能挂起），取图是异步的。所以画布先问 [cached] 拿现成的，
 * 缺的那些交给 [load] 去取；取到一块就把 [revision] +1，画布的绘制 lambda 读到它就重画。
 * 这样**不需要**为了「瓦片到货」而重组整张地图。
 */
interface BikeMapTileSource {

    /**
     * 已到货的瓦片计数。绘制期读它（`DrawScope` 里的 snapshot 读取只失效绘制，不触发重组），
     * 实现方每解出一块瓦片 +1。
     */
    val revision: State<Int>

    /** 同步取一块已到货的瓦片；没有就回 `null`（这一帧先不画它）。 */
    fun cached(key: BikeTileKey): ImageBitmap?

    /**
     * 取一批瓦片。已经在缓存 / 在途的会被实现方自己跳过；调用方（画布）会随视野变化
     * 重新调用，实现方不必自己监听视野。
     */
    suspend fun load(keys: List<BikeTileKey>)
}
