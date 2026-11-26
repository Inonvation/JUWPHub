package edu.jxslu.schedule.ui.toolbox

import androidx.compose.ui.graphics.ImageBitmap
import edu.jxslu.schedule.domain.StitchScan
import kotlin.math.roundToInt

/**
 * 智能拼接的装配（DESIGN §4.47；iOS 移植 V4，2026-11-26 搬进共享层）：
 * 原图降采样到工作分辨率跑 [StitchScan]，结论按比例折算回原图坐标，交给
 * [ToolboxRenderer.stitchStreaming] 裁着拼。
 *
 * ## 本文件搬过来的部分与本文件不管的部分
 *
 * 改造前 `plan()` 自己拿 `Context` + `Uri` 解码（`decodeUriSampled`），那段是 Android 专有物。
 * 现在**解码由调用方以 lambda 递进来**（[decode]），本文件只剩纯逻辑：算目标分辨率、读像素、
 * 跑 [StitchScan]、把结论折算回原图坐标。所以 iOS 上这套算法是完整可用的
 * ——它一行都没碰到 `android.graphics`。
 *
 * 为什么要降采样：行比对是 O(高度 × 宽度/步长) 的，全分辨率比对九张 1080×2400 会明显卡；
 * 降一档后（长边 ~1080）偏移的识别粒度是原图的 2 像素级，对「滚动截屏」这个场景足够
 * ——滚多少是几百像素量级。**匹配用工作分辨率、出图用原分辨率**，两者只靠一个缩放比相连。
 *
 * 智能模式的硬前提：**所有图同宽**（差 ≤2%）。竖拼与行匹配的坐标系都只依赖宽度，
 * 高度可以不同——系统长截图、裁过的图和普通截图混拼是常态，上一版要求「宽高都相同」
 * 把这些场景全挡在门外了（2026-10-12 用户报「刚刚能拼的，现在提示尺寸不一致」）。
 * 宽度确实不一致时没有同一套坐标系，调用方先判 [StitchScan.sameWidth]，不满足就退回直接拼接。
 */
object StitchSmart {

    /** 匹配用的首选工作宽度（[StitchScan.workWidthFor] 会按最长的那张图再收一次）。 */
    private const val WorkWidth = 480

    /**
     * 规划裁剪区间（原图坐标，半开）。返回 null = 这次智能识别没结论
     * （解码失败 / 裁完有图是空的），调用方退回直接拼接。
     *
     * @param fullSizes 所有图的原始尺寸（宽度一致性已由调用方保证）
     * @param statusBarRowsFull 状态栏高度（原始坐标的行数）
     * @param decode 解码第 index 张，长边不超过 `maxLongSidePx`；读不出回 null
     */
    suspend fun plan(
        fullSizes: List<Pair<Int, Int>>,
        statusBarRowsFull: Int,
        tolerance: StitchScan.Tolerance,
        decode: suspend (index: Int, maxLongSidePx: Int) -> ImageBitmap?,
    ): List<StitchScan.KeepRows>? {
        if (fullSizes.isEmpty()) return null
        val workWidth = StitchScan.workWidthFor(fullSizes, preferred = WorkWidth)
        val rowImages = ArrayList<StitchScan.RowImage>(fullSizes.size)
        val workHeights = ArrayList<Int>(fullSizes.size)
        fullSizes.forEachIndexed { index, size ->
            val (fullWidth, fullHeight) = size
            if (fullWidth <= 0) return null
            // 解码目标：让解出来的宽 ≥ 工作宽（长边 × 宽高比），再精确缩到工作宽。
            // 采样是 2 的幂，长截图（1:7 那种）不这么算就会解出一张 135 宽的图再放大 3 倍。
            val longSide = maxOf(fullWidth, fullHeight).toLong()
            val target = (workWidth.toLong() * longSide / fullWidth)
                .coerceAtLeast(workWidth.toLong())
                .toInt()
            val decoded = decode(index, target) ?: return null
            // 精确缩到同一个工作宽度：平台解码只能按 2 的幂采样，落不到 workWidth 上。
            // 已经等宽时 scaledToWidth 原样返回，不白拷一张。
            val scaled = decoded.scaledToWidth(workWidth)
            val pixels = IntArray(workWidth * scaled.height)
            scaled.readPixels(pixels)
            workHeights += scaled.height
            rowImages += StitchScan.RowImage(pixels, workWidth, scaled.height)
        }
        val bands = StitchScan.scan(
            images = rowImages,
            options = StitchScan.Options(
                statusBarRows = statusBarRowsFull * workWidth / fullSizes.first().first,
                minBandRows = maxOf(3, workHeights.min() / 200),
                tolerance = tolerance,
            ),
        )
        val workKeeps = StitchScan.crops(bands, workHeights) ?: return null
        // 逐图折算：纵向比例取每张图自己的（高度不同、宽高比不同都成立）
        val keeps = workKeeps.mapIndexed { index, keep ->
            val fullHeight = fullSizes[index].second
            val scaleY = fullHeight.toFloat() / workHeights[index]
            val top = (keep.top * scaleY).roundToInt().coerceIn(0, fullHeight)
            val bottom = (keep.bottom * scaleY).roundToInt().coerceIn(0, fullHeight)
            StitchScan.KeepRows(top, bottom)
        }
        return if (keeps.any { it.height <= 0 }) null else keeps
    }
}
