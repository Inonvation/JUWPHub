package edu.jxslu.schedule.ui.ebike

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import edu.jxslu.schedule.domain.BikeCluster
import edu.jxslu.schedule.domain.BikeStatus
import edu.jxslu.schedule.domain.NearbyBike
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 高德版标记位图（2026-11-17）。
 *
 * 为什么是位图：高德 3D SDK 走 OpenGL，**没有 osmdroid 那种自定义 Canvas overlay 的口子**，
 * 所有标记只能是 `Marker` + 一张图。所以把 [BikeMarkerOverlay] 里那套自绘几何
 * （圆 + 数字 / 小车字形 / 蓝点 + 朝向箭头 / 高亮圈 + 标签）改成**离屏渲染成位图**，
 * 再交给 `BitmapDescriptorFactory.fromBitmap`。
 *
 * 尺寸口径与 [BikeMarkerOverlay] 逐项对齐（12/15dp 聚合圈、24dp 车标、21dp 外晕…），
 * 两边看到的画面应该一样。**改视觉口径要两边一起改**——这是双渲染引擎的固有成本，
 * 换来的是 key 失效时能整层回退栅格。
 *
 * 位图按「颜色 + 状态」缓存：颜色跟着主题走，主题切换时旧图留着不删（几个 20~50dp 的
 * 小图，几百 KB 封顶），比每次重组重画便宜得多。
 */
internal class AmapMarkerBitmaps(private val density: Float) {

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
        setShadowLayer(1.5f * density, 0f, 0f, 0x99000000.toInt())
    }

    private val glyphFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    private val glyphStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val badgeGlyph = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
        setShadowLayer(1.5f * density, 0f, 0f, 0x99000000.toInt())
    }

    private val rect = RectF()
    private val path = Path()

    private val cache = HashMap<String, Bitmap>()

    /** 聚合圈（圆 + 数字）：选中态大一圈、描边加粗、字号加一档。 */
    fun cluster(cluster: BikeCluster, selected: Boolean, colors: BikeMarkerColors): Bitmap {
        val color = clusterColor(cluster, colors)
        val count = cluster.bikes.size
        val key = "cluster|$color|$selected|$count|${colors.label}|${colors.selectedRing}"
        return cached(key) {
            val radiusDp = if (selected) 15f else 12f
            val strokeDp = if (selected) 3f else 2f
            // 位图边长 = 直径，漏了 ×2 会让圆被位图裁成方块（2026-11-17 真机截到过）
            val size = pxInt((radiusDp + strokeDp) * 2f)
            val bmp = newBitmap(size)
            val canvas = Canvas(bmp)
            val center = size / 2f
            val radius = px(radiusDp)
            fill.color = color
            canvas.drawCircle(center, center, radius, fill)
            ring.strokeWidth = px(strokeDp)
            ring.color = if (selected) colors.selectedRing else Color.WHITE
            canvas.drawCircle(center, center, radius, ring)
            label.color = colors.label
            label.textSize = px(if (selected) 13f else 12f)
            canvas.drawText(
                count.toString(),
                center,
                center - (label.ascent() + label.descent()) / 2f,
                label,
            )
            bmp
        }
    }

    /** 「按车辆」模式的单车图标：实心圆 + 白描边 + 白色小车。 */
    fun bike(bike: NearbyBike, colors: BikeMarkerColors): Bitmap {
        val color = bikeColor(bike, colors)
        return cached("bike|$color") {
            val size = pxInt(BIKE_ICON_DP)
            val bmp = newBitmap(size)
            val canvas = Canvas(bmp)
            val center = size / 2f
            val radius = px(BIKE_ICON_DP) / 2f - px(1.5f)
            fill.color = color
            canvas.drawCircle(center, center, radius, fill)
            ring.strokeWidth = px(1.5f)
            ring.color = Color.WHITE
            canvas.drawCircle(center, center, radius, ring)
            drawScooter(canvas, center, center - px(0.6f), Color.WHITE)
            bmp
        }
    }

    /** 选中车标外面那圈加粗描边：单独一张透明位图，叠在车标之上。 */
    fun bikeSelection(colors: BikeMarkerColors): Bitmap =
        cached("bikeSel|${colors.selectedRing}") {
            val radiusDp = BIKE_ICON_DP / 2f + 2f
            val size = pxInt((radiusDp + 2f) * 2f)
            val bmp = newBitmap(size)
            val canvas = Canvas(bmp)
            ring.strokeWidth = px(2.5f)
            ring.color = colors.selectedRing
            canvas.drawCircle(size / 2f, size / 2f, px(radiusDp), ring)
            bmp
        }

    /** 当前用车：外晕 + 实心圆 + 白描边 + 白色小车。 */
    fun ride(colors: BikeMarkerColors): Bitmap =
        cached("ride|${colors.rideMarker}|${colors.rideHalo}|${colors.rideGlyph}") {
            val size = pxInt(42f)
            val bmp = newBitmap(size)
            val canvas = Canvas(bmp)
            val center = size / 2f
            fill.color = colors.rideHalo
            canvas.drawCircle(center, center, px(21f), fill)
            fill.color = colors.rideMarker
            canvas.drawCircle(center, center, px(14f), fill)
            ring.strokeWidth = px(3f)
            ring.color = Color.WHITE
            canvas.drawCircle(center, center, px(14f), ring)
            drawScooter(canvas, center, center, colors.rideGlyph)
            bmp
        }

    /**
     * 用户位置：淡绿光晕 + 朝向箭头 + 绿点。
     *
     * 箭头**固定朝上**画进位图，运行时用 `Marker.rotateAngle` 转——圆点与光晕都是旋转对称的，
     * 转整张图与"只转箭头"肉眼等价，省掉每次朝向变化都重画位图（罗盘 10Hz 更新）。
     */
    fun user(colors: BikeMarkerColors): Bitmap =
        cached("user|${colors.userDot}|${colors.userHalo}") {
            val size = pxInt(24f)
            val bmp = newBitmap(size)
            val canvas = Canvas(bmp)
            val center = size / 2f
            fill.color = colors.userHalo
            canvas.drawCircle(center, center, px(11f), fill)
            path.reset()
            path.moveTo(center, center - px(8f))
            path.lineTo(center - px(3f), center - px(3.5f))
            path.lineTo(center + px(3f), center - px(3.5f))
            path.close()
            fill.color = colors.userDot
            canvas.drawPath(path, fill)
            canvas.drawCircle(center, center, px(5f), fill)
            ring.strokeWidth = px(1f)
            ring.color = Color.WHITE
            canvas.drawCircle(center, center, px(5f), ring)
            bmp
        }

    /**
     * 识别高亮：双层定位圈 + 上方标签 chip（有文案时）。
     *
     * 锚点按 [highlightAnchorY] 给——标签多长都不影响圆点压在地理位置上。
     */
    fun highlight(text: String?, colors: BikeMarkerColors): Bitmap {
        val chipText = text?.takeIf { it.isNotBlank() }
        val key = "hl|$chipText|${colors.available}|${colors.label}"
        return cached(key) {
            val dotArea = px(16f) * 2
            val gap = if (chipText != null) px(4f) else 0f
            val chipHeight = px(19f)
            label.textSize = px(11f)
            val chipWidth = if (chipText != null) {
                max(dotArea, label.measureText(chipText) + px(14f))
            } else {
                0f
            }
            val width = max(dotArea, chipWidth).roundToInt().coerceAtLeast(1)
            val height = (dotArea + gap + (if (chipText != null) chipHeight else 0f))
                .roundToInt().coerceAtLeast(1)
            val bmp = newBitmap(width, height)
            val canvas = Canvas(bmp)
            val cx = width / 2f
            val cy = height - px(16f)
            if (chipText != null) {
                val top = height - dotArea - gap - chipHeight
                rect.set(cx - chipWidth / 2f, top, cx + chipWidth / 2f, top + chipHeight)
                fill.color = colors.available
                canvas.drawRoundRect(rect, px(9f), px(9f), fill)
                ring.strokeWidth = px(1.5f)
                ring.color = Color.WHITE
                canvas.drawRoundRect(rect, px(9f), px(9f), ring)
                label.color = colors.label
                canvas.drawText(
                    chipText,
                    cx,
                    top + chipHeight / 2f - (label.ascent() + label.descent()) / 2f,
                    label,
                )
            }
            ring.strokeWidth = px(2.5f)
            ring.color = colors.available
            canvas.drawCircle(cx, cy, px(16f), ring)
            fill.color = colors.available
            canvas.drawCircle(cx, cy, px(5f), fill)
            ring.strokeWidth = px(2f)
            ring.color = Color.WHITE
            canvas.drawCircle(cx, cy, px(5f), ring)
            bmp
        }
    }

    /** 高亮位图的锚点纵坐标比例（圆点圆心所在行 / 总高）。 */
    fun highlightAnchorY(text: String?): Float {
        val chipText = text?.takeIf { it.isNotBlank() } ?: return 0.5f
        label.textSize = px(11f)
        val dotArea = px(16f) * 2
        val gap = px(4f)
        val chipHeight = px(19f)
        val height = dotArea + gap + chipHeight
        return (height - px(16f)) / height
    }

    /** 还车点图标：圆角方块 + 白「P」。 */
    fun spotBadge(colors: BikeMarkerColors): Bitmap =
        cached("spot|${colors.spotBadge}|${colors.spotGlyph}") {
            val size = pxInt(17f)
            val bmp = newBitmap(size)
            val canvas = Canvas(bmp)
            val center = size / 2f
            val half = px(7.5f)
            rect.set(center - half, center - half, center + half, center + half)
            fill.color = colors.spotBadge
            canvas.drawRoundRect(rect, px(3f), px(3f), fill)
            ring.strokeWidth = px(1.5f)
            ring.color = Color.WHITE
            canvas.drawRoundRect(rect, px(3f), px(3f), ring)
            badgeGlyph.color = colors.spotGlyph
            badgeGlyph.textSize = px(10f)
            canvas.drawText(
                "P",
                center,
                center - (badgeGlyph.ascent() + badgeGlyph.descent()) / 2f,
                badgeGlyph,
            )
            bmp
        }

    /** 簇里只要有一辆能骑就画「可用」，否则退到「电量低」，再退到灰（同 osmdroid 版口径）。 */
    private fun clusterColor(cluster: BikeCluster, colors: BikeMarkerColors): Int = when {
        cluster.bikes.any { it.available } -> colors.available
        cluster.bikes.any { it.status == BikeStatus.LowBattery } -> colors.lowBattery
        else -> colors.unavailable
    }

    private fun bikeColor(bike: NearbyBike, colors: BikeMarkerColors): Int = when (bike.status) {
        BikeStatus.Available -> colors.available
        BikeStatus.LowBattery -> colors.lowBattery
        BikeStatus.Offline, BikeStatus.Disabled -> colors.unavailable
    }

    /** 小车字形（两轮 + 踏板 + 立管 + 把手），与 osmdroid 版同一套几何。 */
    private fun drawScooter(canvas: Canvas, x: Float, y: Float, color: Int) {
        val d = density
        glyphFill.color = color
        glyphStroke.color = color
        glyphStroke.strokeWidth = 1.9f * d
        val wheelY = y + 3.8f * d
        canvas.drawCircle(x - 5.4f * d, wheelY, 2.2f * d, glyphFill)
        canvas.drawCircle(x + 5.4f * d, wheelY, 2.2f * d, glyphFill)
        canvas.drawLine(x - 5.4f * d, wheelY, x + 3.4f * d, wheelY, glyphStroke)
        canvas.drawLine(x + 3.4f * d, wheelY, x + 5.9f * d, y - 3.6f * d, glyphStroke)
        canvas.drawLine(x + 4.2f * d, y - 3.6f * d, x + 7.2f * d, y - 3.6f * d, glyphStroke)
    }

    /**
     * 位图缓存：到上限整表清掉。
     *
     * 键里带车号的（高亮 chip）会随着浏览过的车一直长，主题切换时颜色键也会换新的；
     * 位图只有几十 KB、重建一次也就是几十次 draw，**整表清**比按 LRU 记账划算得多。
     */
    private fun cached(key: String, block: () -> Bitmap): Bitmap {
        cache[key]?.let { return it }
        if (cache.size > CACHE_LIMIT) cache.clear()
        return block().also { cache[key] = it }
    }

    private fun px(value: Float): Float = value * density

    private fun pxInt(value: Float): Int = (value * density).roundToInt().coerceAtLeast(1)

    private fun newBitmap(width: Int, height: Int = width): Bitmap =
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

    private companion object {
        /** 单车图标直径（dp），与 osmdroid 版 [BIKE_ICON_DIAMETER_DP] 对齐。 */
        const val BIKE_ICON_DP = 24f

        /** 位图缓存条数上限（见 [cached]）。 */
        const val CACHE_LIMIT = 256
    }
}
