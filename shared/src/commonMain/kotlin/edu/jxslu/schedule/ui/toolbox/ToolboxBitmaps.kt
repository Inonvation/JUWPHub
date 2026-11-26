package edu.jxslu.schedule.ui.toolbox

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.roundToInt

/**
 * 共享层离屏渲染的**位图工厂与绘制小工具**（iOS 移植 V4，2026-11-26）。
 *
 * ## 为什么需要一条 expect
 *
 * 水印成品、平铺块、旋转扩边后的块、拼接长图，这四处都要新建空位图再往上画。两端想要的
 * 底层配置**不一样**，而且这个差异是有意义的：
 *
 * | 端 | 实际建出来的 | 为什么 |
 * |---|---|---|
 * | Android | `Bitmap.createBitmap(w, h, ARGB_8888 / RGB_565)` | 与改造前的 `ToolboxRenderer` 逐字一致。拼接超过 800 万像素时换 `RGB_565`（每像素 2 字节）是 `ToolboxRenderer.stitchStreaming` 的内存余量，也是 `.agents/rules/toolbox.md` 里钉住的一条 |
 * | iOS | `ImageBitmap(w, h)`（skia 的 8888） | skiko 没有「建一张 RGB_565 的 `ImageBitmap` 并往上画」的可靠路径，所以 [lowMemory] 在 iOS 上**被忽略** |
 *
 * [lowMemory] 只在拼接那一处传 true，其余位置一律 ARGB_8888——水印要叠半透明，落 565 会把
 * 边缘的 alpha 量化掉。
 *
 * ## 与 Android 旧实现的对应关系
 *
 * 改造前有两处「配置白名单」：解码端钉死 `inPreferredConfig = ARGB_8888`（各解码口），出图端
 * `safeCopyConfig` 只放行 ARGB_8888 / RGB_565。前者现在在 Android 侧的解码 actual 里
 * （`ui/platform/ImagePickers.android.kt`），后者由本函数的两个取值取代——**只有这两种**，
 * 10-bit / HDR 照片解出的 `RGBA_F16` / `RGBA_1010102` 不会再漏进来。
 *
 * ## 编译状态
 *
 * Android 侧随 `:app:assembleDebug` 真编译；iOS 侧本机（Windows）编不了 Kotlin/Native，
 * `ImageBitmap(w, h)` 是 CMP 公共 API，风险低。
 *
 * @param lowMemory true = 允许降成每像素 2 字节（只在拼接超阈值时用）
 */
internal expect fun newToolboxImageBitmap(
    width: Int,
    height: Int,
    lowMemory: Boolean = false,
): ImageBitmap

/**
 * 离屏绘制的密度：恒为 1，让 `sp` 与像素一比一。
 *
 * 不能跟着 `LocalDensity` 走：水印字号是 [edu.jxslu.schedule.domain.ToolboxImage.watermarkTextSizePx]
 * 按**图宽像素**算出来的，跟设备密度/系统字号再乘一次，同一张图在不同手机上会印出不同大小的字。
 */
internal val ToolboxPixelDensity: Density = Density(1f)

/** 离屏绘制没有「界面语言」这回事，恒 LTR（水印文案是用户输入的单行文本）。 */
internal val ToolboxPixelLayoutDirection: LayoutDirection = LayoutDirection.Ltr

/** 在 [target] 上离屏画一块（新建画布 → 执行 [block]）。 */
internal fun drawToolboxBitmap(target: ImageBitmap, block: DrawScope.() -> Unit) {
    CanvasDrawScope().draw(
        density = ToolboxPixelDensity,
        layoutDirection = ToolboxPixelLayoutDirection,
        canvas = Canvas(target),
        size = Size(target.width.toFloat(), target.height.toFloat()),
        block = block,
    )
}

/**
 * 等比缩到指定宽（双线性），返回新位图；[targetWidth] 与当前宽相同就原样返回自己。
 *
 * 用途只有一个：智能拼接要把每张图精确缩到**同一个工作宽度**再逐行比对
 * （`ui/toolbox/StitchSmart.kt`），而平台解码只能按 2 的幂采样，落不到精确宽度。
 *
 * 它与改造前 `Bitmap.createScaledBitmap(src, workWidth, h, filter = true)` 同口径
 * （双线性、双端四舍五入），差异在亚像素采样点上，行比对有容差档兜着。
 */
internal fun ImageBitmap.scaledToWidth(targetWidth: Int): ImageBitmap {
    if (targetWidth <= 0 || width <= 0 || width == targetWidth) return this
    val targetHeight = (height.toFloat() * targetWidth / width).roundToInt().coerceAtLeast(1)
    val out = newToolboxImageBitmap(targetWidth, targetHeight)
    drawToolboxBitmap(out) {
        drawImage(
            image = this@scaledToWidth,
            dstOffset = IntOffset.Zero,
            dstSize = IntSize(targetWidth, targetHeight),
            filterQuality = FilterQuality.Low,
        )
    }
    return out
}

/**
 * 长边缩到不超过 [maxLongSide]（已经是小的就原样返回）。
 *
 * iOS 的图片解码没有 Android `inSampleSize` 那样的采样开关，只能整幅解出来再缩
 * ——`ui/platform/ImagePickers.ios.kt` 用它把预览/缩略图的峰值内存收回来。
 */
internal fun ImageBitmap.scaledToLongSide(maxLongSide: Int): ImageBitmap {
    val longSide = maxOf(width, height)
    if (maxLongSide <= 0 || longSide <= maxLongSide || longSide <= 0) return this
    val ratio = maxLongSide.toFloat() / longSide
    val targetWidth = (width * ratio).roundToInt().coerceAtLeast(1)
    val targetHeight = (height * ratio).roundToInt().coerceAtLeast(1)
    if (targetWidth == width && targetHeight == height) return this
    val out = newToolboxImageBitmap(targetWidth, targetHeight)
    drawToolboxBitmap(out) {
        drawImage(
            image = this@scaledToLongSide,
            dstOffset = IntOffset.Zero,
            dstSize = IntSize(targetWidth, targetHeight),
            filterQuality = FilterQuality.Low,
        )
    }
    return out
}
