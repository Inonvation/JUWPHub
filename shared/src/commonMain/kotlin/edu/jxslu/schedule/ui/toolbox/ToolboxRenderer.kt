package edu.jxslu.schedule.ui.toolbox

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import edu.jxslu.schedule.domain.StitchScan
import edu.jxslu.schedule.domain.ToolboxImage
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin

/**
 * 百宝箱图片工具的渲染核（DESIGN §4.47；iOS 移植 V4，2026-11-26 搬进共享层）。
 *
 * ## 为什么整块换成了 Compose 的离屏绘制
 *
 * 改造前这里是 `android.graphics.Bitmap` + `Canvas` + `Paint` + `StaticLayout` +
 * `BitmapShader`，`commonMain` 里一个都没有。现在一律走
 * `ImageBitmap` + [CanvasDrawScope] + [TextMeasurer]，两端**同一份代码**：
 *
 * | 改造前（Android 专有） | 现在 |
 * |---|---|
 * | `Bitmap.createBitmap(w, h, config)` | [newToolboxImageBitmap]（Android 仍是同一个调用，iOS 是 skia 的 8888） |
 * | `Canvas(bitmap)` + `drawBitmap` | `Canvas(imageBitmap)` + `DrawScope.drawImage` |
 * | `TextPaint` + `StaticLayout` + `layout.draw(canvas)` | [TextMeasurer.measure] + `DrawScope.drawText` |
 * | `BitmapShader(TileMode.REPEAT)` + `drawRect` | `clipRect` 里按块宽/块高手工平铺（[watermark]） |
 * | `canvas.rotate(deg, cx, cy)` | `DrawScope.rotate(degrees, pivot)` |
 *
 * **像素级的差异是允许的、版式级的不是**：字体度量（行高、字宽四舍五入）与抗锯齿在两端、
 * 乃至 Android 新旧实现之间都会有半像素级差别；但「字号只按图宽取比例、平铺块尾部补空行调
 * 密度、旋转先按包围盒扩边、拼接目标宽取最大源宽」这四条版式口径一字未改，
 * 参数也仍全部来自 [ToolboxImage] 的纯函数（`ToolboxImageTest` 钉着）。
 *
 * ## 三处需要知道的行为差异（都是「平台没有等价物」，不是漏做）
 *
 * 1. **拼接不再降成 RGB_565**：改成由 [newToolboxImageBitmap] 的平台实现决定——Android 照旧
 *    按 [Rgb565ThresholdPixels] 降档，iOS 只能 8888。见 `ToolboxBitmaps.kt`。
 * 2. **水印成品的配置不再跟随源图**：旧实现用 `safeCopyConfig(src)`，源是 `RGB_565` 时成品也
 *    是 565。现在恒为 ARGB_8888。实际入参不会走到 565——解码口钉死 ARGB_8888，
 *    见 `ui/platform/ImagePickers.android.kt`。
 * 3. **平铺是「画 N 次」而不是 shader**：`BitmapShader` 没有跨平台等价物，改成在
 *    `clipRect` 里按块宽/块高手工平铺。观感一致（都从 (0,0) 起、边缘被裁），
 *    代价是块多时绘制指令多几倍——1200px 预览实测仍是几十毫秒级。
 *
 * ## 线程：水印为什么拆成两步
 *
 * [watermarkTile] 要调 [TextMeasurer.measure]，**必须在组合线程上调**（文本布局的字体解析器
 * 与布局缓存都按主线程用法设计）；而铺满整幅（[watermark]）与拼接（[stitchStreaming]）是纯图形，
 * 应该在 `Dispatchers.Default` 上跑。所以水印不是一个函数而是两步：
 *
 * ```kotlin
 * val tile = ToolboxRenderer.watermarkTile(measurer, text, color, src.width, scale, density, rotation)
 * val rendered = withContext(Dispatchers.Default) { ToolboxRenderer.watermark(src, tile, alphaPercent) }
 * ```
 *
 * 调用方拿到的 `measurer` 必须用 `Density(1f)` 构造（见 `rememberToolboxTextMeasurer`），
 * 否则 `sp` 会跟着设备密度 / 系统字号缩放，字号就不再是按
 * [ToolboxImage.watermarkTextSizePx] 算出来的那个像素值了。
 */
object ToolboxRenderer {

    /** 拼接成品的总像素上限（约等于 1080 × 20000 的长图；超出即等比缩宽）。 */
    private const val MaxStitchPixels = 24_000_000L

    /** 超过这个像素数换 RGB_565：每像素 2 字节，给长图留出内存余量（iOS 侧忽略，见类 KDoc）。 */
    private const val Rgb565ThresholdPixels = 8_000_000L

    /**
     * 满图平铺文字水印。
     *
     * 平铺块 = 多行文字（[ToolboxImage.watermarkPattern]，密度档位 = 尾部空行）→ 旋转 →
     * 按旋转后包围盒扩边。**必须先在组合线程调**（要摸 [TextMeasurer]），得到的块再交给
     * [watermark] 去铺满整幅。
     *
     * 块宽只能来自**文字实际布局宽度**（`TextLayoutResult.size`），不能用「测量时的约束宽度」：
     * 改造前为「不换行」传的是 `Int.MAX_VALUE`，把它当位图宽会让原生位图分配失败并
     * **abort 整个进程**（2026-10-12 真机，见 `.agents/rules/toolbox.md`）。这里同样只取
     * 布局自己的尺寸，`coerceAtLeast(1)` 防空白文字算出 0 宽。
     *
     * @param textMeasurer 必须用 `Density(1f)` 构造（见类 KDoc「线程」）
     * @param color ARGB 整数（与改造前 `TextPaint.color` 同口径）
     * @param imageWidthPx 水位图宽度：字号按它取比例（预览与成品各自传自己的宽，预览即所得）
     * @param scale 字号档位（1f = 100%）
     * @param rotationDeg 旋转角（-90..90），0 时不扩边，直接原块铺
     */
    fun watermarkTile(
        textMeasurer: TextMeasurer,
        text: String,
        color: Int,
        imageWidthPx: Int,
        scale: Float,
        density: ToolboxImage.Density,
        rotationDeg: Float,
    ): ImageBitmap {
        val textSize = ToolboxImage.watermarkTextSizePx(imageWidthPx, scale)
        val pattern = ToolboxImage.watermarkPattern(text, density)
        // 不传 constraints：默认是不限制宽度，等价于改造前 `StaticLayout` 的「不换行」。
        // 行距也不设 lineHeight：默认行高 = 字体的自然行高，等价于 `setLineSpacing(0f, 1f)`。
        val laid = textMeasurer.measure(
            text = pattern,
            style = TextStyle(
                color = Color(color),
                fontSize = TextUnit(textSize, TextUnitType.Sp),
            ),
        )
        val tileWidth = laid.size.width.coerceAtLeast(1)
        val tileHeight = laid.size.height.coerceAtLeast(1)
        val tile = newToolboxImageBitmap(tileWidth, tileHeight)
        drawToolboxBitmap(tile) { drawText(laid, topLeft = Offset.Zero) }
        return rotateWithBounds(tile, rotationDeg)
    }

    /**
     * 把 [watermarkTile] 的平铺块铺满整幅：原图铺底 + 块按 (0,0) 起点重复。
     *
     * 纯图形，可在 `Dispatchers.Default` 上跑。块不透明区由 `clipRect` 裁掉，
     * 观感与改造前的 `BitmapShader(TileMode.REPEAT)` 一致。
     *
     * @param alphaPercent 0–100，经 [ToolboxImage.alphaOf] 换成 0–255
     */
    fun watermark(
        src: ImageBitmap,
        tile: ImageBitmap,
        alphaPercent: Float,
    ): ImageBitmap {
        val alpha = ToolboxImage.alphaOf(alphaPercent) / 255f
        val result = newToolboxImageBitmap(src.width, src.height)
        drawToolboxBitmap(result) {
            drawImage(src, dstOffset = IntOffset.Zero)
            if (alpha > 0f) {
                clipRect(0f, 0f, size.width, size.height) {
                    var y = 0
                    while (y < size.height) {
                        var x = 0
                        while (x < size.width) {
                            // dstSize 默认取整块（srcSize），块的右/下越界部分由 clipRect 裁掉
                            drawImage(tile, dstOffset = IntOffset(x, y), alpha = alpha)
                            x += tile.width
                        }
                        y += tile.height
                    }
                }
            }
        }
        return result
    }

    /**
     * 竖拼（**流式**）：目标宽取最大源宽（等宽归一），逐张「要一张、画一张、丢一张」。
     *
     * 长图拼接是内存最重的一个工具——九张截屏同时解码就是上百 MB，所以这里只收尺寸表
     * （调用方先用 `PickedImage.bounds()` 读边界，零像素内存），源图由 [sourceAt] 现取、
     * 画完即弃，任意时刻只有一张源图 + 一张成品在内存里。
     *
     * [keeps] 由智能拼接的规划给出（`ui/toolbox/StitchSmart.kt`）——每张只画保留的行区间，
     * 重复的头尾与滚动内容在那里被裁掉；null = 直接拼接（逐张全画）。
     *
     * [sourceAt] 返回 null = 这一张读不出来：对应高度留白（底色白）继续拼，
     * 不让单张失败把整张长图作废。
     */
    suspend fun stitchStreaming(
        sourceSizes: List<Pair<Int, Int>>,
        spacingPx: Int,
        /** 每张图保留的行区间（原图坐标，半开）；null = 逐张全画（直接拼接）。 */
        keeps: List<StitchScan.KeepRows>? = null,
        sourceAt: suspend (Int) -> ImageBitmap?,
    ): ImageBitmap {
        require(sourceSizes.isNotEmpty()) { "stitch 至少要一张源图" }
        val targetWidth = ToolboxImage.stitchFitTargetWidth(
            sourceSizes = sourceSizes,
            maxPixels = MaxStitchPixels,
        )
        val scaledHeights = sourceSizes.mapIndexed { index, (srcWidth, srcHeight) ->
            val keptHeight = keeps?.getOrNull(index)?.height ?: srcHeight
            ToolboxImage.stitchScaledHeight(srcWidth, keptHeight, targetWidth)
        }
        val spacing = spacingPx.coerceAtLeast(0)
        val totalHeight = ToolboxImage.stitchTotalHeight(scaledHeights, spacing)
        val lowMemory = targetWidth.toLong() * totalHeight > Rgb565ThresholdPixels
        val result = newToolboxImageBitmap(targetWidth, totalHeight, lowMemory)
        val canvas = Canvas(result)
        val scope = CanvasDrawScope()
        val total = Size(targetWidth.toFloat(), totalHeight.toFloat())
        // 底色铺白：带透明通道的 PNG 截图拼出来不该是黑底（改造前 `canvas.drawColor(WHITE)`）
        scope.draw(ToolboxPixelDensity, ToolboxPixelLayoutDirection, canvas, total) {
            drawRect(Color.White, size = Size(targetWidth.toFloat(), totalHeight.toFloat()))
        }
        // y 只累加整数：spacing 与每段高度都是 Int，不存在累加误差，
        // 因此这里不需要浮点矩形（Compose 的 drawImage 只吃整数 dst 矩形）。
        var y = 0
        sourceSizes.indices.forEach { index ->
            val height = scaledHeights[index]
            sourceAt(index)?.let { bitmap ->
                val keep = keeps?.getOrNull(index)
                val srcTop = (keep?.top ?: 0).coerceIn(0, bitmap.height)
                val srcBottom = (keep?.bottom ?: bitmap.height).coerceIn(srcTop, bitmap.height)
                // 保留区间落在源图之外（智能规划的坐标与解码尺寸对不上时）：跳过这一张，
                // 不要拿 0 高的源矩形去缩放
                if (srcBottom > srcTop) scope.draw(ToolboxPixelDensity, ToolboxPixelLayoutDirection, canvas, total) {
                    drawImage(
                        image = bitmap,
                        srcOffset = IntOffset(0, srcTop),
                        srcSize = IntSize(bitmap.width, srcBottom - srcTop),
                        dstOffset = IntOffset(0, y),
                        dstSize = IntSize(targetWidth, height),
                        // 与改造前 `Paint(FILTER_BITMAP_FLAG)` 同档：双线性
                        filterQuality = FilterQuality.Low,
                    )
                }
            }
            y += height + spacing
        }
        return result
    }

    /**
     * 按旋转角扩边后绘制平铺块：先算旋转后包围盒，把原块旋转画进新画布中心。
     * 角度为 0 时原样返回，不白拷一张。
     *
     * **必须扩边**：直接旋原尺寸块会在 45° 附近把字啃掉边（「一个木函」原版就是这个毛病）。
     */
    private fun rotateWithBounds(tile: ImageBitmap, rotationDeg: Float): ImageBitmap {
        if (rotationDeg % 360f == 0f) return tile
        val radians = rotationDeg * PI / 180.0
        val cosA = abs(cos(radians)).toFloat()
        val sinA = abs(sin(radians)).toFloat()
        val width = tile.width.toFloat()
        val height = tile.height.toFloat()
        val expandedWidth = ceil(width * cosA + height * sinA).toInt().coerceAtLeast(1)
        val expandedHeight = ceil(width * sinA + height * cosA).toInt().coerceAtLeast(1)
        val out = newToolboxImageBitmap(expandedWidth, expandedHeight)
        drawToolboxBitmap(out) {
            rotate(degrees = rotationDeg, pivot = Offset(expandedWidth / 2f, expandedHeight / 2f)) {
                drawImage(
                    image = tile,
                    topLeft = Offset((expandedWidth - width) / 2f, (expandedHeight - height) / 2f),
                )
            }
        }
        return out
    }
}

/**
 * 水印渲染要的 [TextMeasurer]：**固定 `Density(1f)`**（iOS 移植 V4，2026-11-26）。
 *
 * 为什么不能直接用 `rememberTextMeasurer()`：那个用 `LocalDensity`（设备密度 + 系统字号），
 * `sp` 会被再放大一次，而水印字号已经是
 * [ToolboxImage.watermarkTextSizePx] 按**图宽像素**算好的绝对像素值。用设备密度渲染，
 * 同一张图在不同手机上会印出不同大小的字，预览与成品也会对不上。
 *
 * 字族与排版方向仍取自组合（[LocalFontFamilyResolver] / [LocalLayoutDirection]），
 * 只把密度钉成 1。
 */
@Composable
internal fun rememberToolboxTextMeasurer(): TextMeasurer {
    val resolver = LocalFontFamilyResolver.current
    val direction = LocalLayoutDirection.current
    return remember(resolver, direction) {
        TextMeasurer(
            defaultFontFamilyResolver = resolver,
            defaultDensity = ToolboxPixelDensity,
            defaultLayoutDirection = direction,
        )
    }
}