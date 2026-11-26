package edu.jxslu.schedule.ui.toolbox

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/**
 * [newToolboxImageBitmap] 的 Android 实现（iOS 移植 V4，2026-11-26）：
 * `Bitmap.createBitmap` + `asImageBitmap()`（零拷贝包装，不复制像素）。
 *
 * 配置只有两档，与改造前的 `ToolboxRenderer` 完全一致：默认 ARGB_8888，
 * [lowMemory] 时 RGB_565。**不要**改成跟随源图的 config：10-bit / HDR 照片可能解出
 * `RGBA_F16` / `RGBA_1010102`，`Bitmap.createBitmap` 拿这类非常规 config 会失败
 * （`.agents/rules/toolbox.md`「位图 config 仍走白名单」）。
 *
 * 2026-10-12 那次真机 abort 的病因不在配置，而在水印平铺块宽度取了 `Int.MAX_VALUE`
 * ——那条已由 `ToolboxRenderer.watermark` 用文字实际布局宽度取代。
 */
internal actual fun newToolboxImageBitmap(
    width: Int,
    height: Int,
    lowMemory: Boolean,
): ImageBitmap {
    val config = if (lowMemory) Bitmap.Config.RGB_565 else Bitmap.Config.ARGB_8888
    return Bitmap.createBitmap(width, height, config).asImageBitmap()
}
