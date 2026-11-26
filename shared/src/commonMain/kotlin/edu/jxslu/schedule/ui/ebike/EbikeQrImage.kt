package edu.jxslu.schedule.ui.ebike

import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import edu.jxslu.schedule.domain.BarcodeMatrix
import kotlin.math.max

/**
 * 位阵 → 一张真位图（2026-11-26，iOS「存相册」用）。
 *
 * ## 为什么需要它，以及为什么不和屏幕共用 `BarcodeMatrixImage`
 *
 * 屏幕上那张码由 `ui/common/BarcodeMatrixImage` 用 Compose **画布**直接画（不经过位图），
 * 那是屏幕的正确形态。落相册不一样：iOS 的 `UIImageWriteToSavedPhotosAlbum` 只吃 `UIImage`，
 * 而 `PixelBitmap` 的唯一构造入口是 `decodeBitmap(bytes)`（图片字节），所以 iOS 侧必须
 * 「先出一张图、编码成 PNG、再解回来」（整条链见 `ui/week/IosScheduleShareActions.kt`，
 * 那里存课表图走的是同一条）。
 *
 * 于是这里补上缺的那一环：把 [BarcodeMatrix] 画进 [ImageBitmap]。**不重写绘制算法**——
 * 逐行合并连续黑段、一次性画整段（二维码一行约三十段），口径与 `BarcodeMatrixImage` 一致：
 * 黑白取值只跟 `dark` 走（深色主题出「深底白码」，与屏幕上那张同款）。
 *
 * 本文件里没有平台类型：Compose 的 `ImageBitmap` / `Canvas` 在两端都是公共 API，
 * 所以它留在 commonMain（也就能被 `:shared:metadataCommonMainClasses` 在本机验证），
 * 只有「编码成 PNG」那一步是 iOS 私事。
 *
 * ## 边界
 *
 * 位阵尺寸非法（≤ 0）时给一张 1×1 的图——调用方紧接着要编码它，抛异常不如给一张扫不开的图
 * 让编码失败照常落到「图片编码失败」的提示上（与 `toPngBytes` 返回 null 同一条出口）。
 */
fun qrImageBitmap(matrix: BarcodeMatrix, dark: Boolean): ImageBitmap {
    val width = max(1, matrix.width)
    val height = max(1, matrix.height)
    val image = ImageBitmap(width = width, height = height)
    val canvas = Canvas(image)
    val paint = Paint()
    val foreground = if (dark) Color.White else Color.Black
    val background = if (dark) Color.Black else Color.White

    // 底先铺满：模块之间不能露透明（PNG 带 alpha，相册里的白底变黑底就没法扫了）
    paint.color = background
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

    if (matrix.width <= 0 || matrix.height <= 0) return image

    paint.color = foreground
    for (y in 0 until matrix.height) {
        var x = 0
        while (x < matrix.width) {
            if (!matrix[x, y]) {
                x++
                continue
            }
            var end = x + 1
            while (end < matrix.width && matrix[end, y]) end++
            canvas.drawRect(
                left = x.toFloat(),
                top = y.toFloat(),
                right = end.toFloat(),
                bottom = (y + 1).toFloat(),
                paint = paint,
            )
            x = end
        }
    }
    return image
}
