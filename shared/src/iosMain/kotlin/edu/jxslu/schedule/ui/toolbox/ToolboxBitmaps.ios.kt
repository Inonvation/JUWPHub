package edu.jxslu.schedule.ui.toolbox

import androidx.compose.ui.graphics.ImageBitmap

/**
 * [newToolboxImageBitmap] 的 iOS 实现（iOS 移植 V4，2026-11-26）：直接建一张 Compose 的
 * `ImageBitmap`，由 skiko 分配一块 ARGB_8888 像素缓冲。
 *
 * [lowMemory] **有意忽略**：skiko 没有「建一张 RGB_565 的 `ImageBitmap` 并作为绘制目标」
 * 的可靠路径，硬做只能自己分配 `UInt16Array` 再桥接，换不来正确性上的收益。代价是超长拼接
 * （超过 [ToolboxRenderer] 的 800 万像素阈值）在 iOS 上内存峰值比 Android 高一倍——
 * 24 亿像素的硬上限照旧生效，见 `ui/toolbox/ToolboxRenderer.kt` 的 `MaxStitchPixels`。
 *
 * 编译状态：本机是 Windows，Kotlin/Native 编不了，**这段没有编译验证过**；
 * `ImageBitmap(width, height)` 是 CMP 公共工厂（commonMain 元数据编译已核实可见）。
 */
internal actual fun newToolboxImageBitmap(
    width: Int,
    height: Int,
    lowMemory: Boolean,
): ImageBitmap = ImageBitmap(width, height)
