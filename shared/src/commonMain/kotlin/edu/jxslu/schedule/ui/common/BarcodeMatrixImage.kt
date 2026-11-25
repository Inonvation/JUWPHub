package edu.jxslu.schedule.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import edu.jxslu.schedule.domain.BarcodeMatrix
import kotlin.math.roundToInt

/**
 * 把码位阵画出来（iOS 移植 Y1，2026-11-25）。
 *
 * ## 为什么不用位图
 *
 * 搬迁前付款码页是「`CodeBitmaps.render(matrix)` → `android.graphics.Bitmap` → `Image()`」。
 * `Bitmap` 是 Android 专有类型，`commonMain` 里既没有它也进不了 Kotlin/Native；
 * 而位阵本身（[BarcodeMatrix]）两端本来就是同一份（`domain/YktPayCode.matrices` 出码，
 * Android 走 zxing、iOS 走 CoreImage）。缺的只是「位阵 → 一张能画的东西」这一步。
 *
 * 这一步用 Compose 画布直接完成：位阵里 `true` 就是一块黑方块，画方块不需要位图。
 * 换来的两个好处：没有 720×720 的位图分配与 `recycle()`（搬迁前三处要手动回收，
 * 漏一处就是内存泄漏）；两端逐像素同一份输出，iOS 不需要另写一份渲染。
 *
 * ## 列合并与像素对齐（改这里之前先读）
 *
 * 朴素画法（每个像素一个 `drawRect`）在 720×720 上要 51.8 万次绘制。所以先按**列**合并：
 * 与当前列逐位相同的连续列算一段，只在段首那一列上求纵向连续黑段，一次画整段宽。
 * 二维码 ≈ 模块列数（二十几段）× 每列十几个黑段 ≈ 几百次；Code128 一维码整列同色，
 * 只有二三百次。
 *
 * 边界一律**四舍五入到整数像素**再绘制：相邻两块黑区落在同一个像素网格上，
 * 既不会因为小数坐标出现半透明接缝（`AntiAlias` 会把接缝灰掉半格，扫码枪按 50% 阈值
 * 判定，灰边会让窄条变细），也不会互相吃掉一格。
 *
 * ## 黑白取值只跟深浅主题走
 *
 * 深色主题出「深底白码」（`dark = true`）：微信/相机扫一扫对反色码兼容良好，
 * 且与页面观感一致——与搬迁前 `CodeBitmaps.render(matrix, dark)` 完全同一口径。
 */
@Composable
fun BarcodeMatrixImage(
    matrix: BarcodeMatrix,
    /** 深色主题：`true` = 黑底白码。 */
    dark: Boolean,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    val fg = if (dark) Color.White else Color.Black
    val bg = if (dark) Color.Black else Color.White
    val semanticsModifier = if (contentDescription == null) {
        modifier
    } else {
        modifier.semantics { this.contentDescription = contentDescription }
    }
    Canvas(semanticsModifier) {
        // 底先铺满：段与段之间不留白，深色主题下也不会露出页面底色
        drawRect(color = bg)
        if (matrix.width <= 0 || matrix.height <= 0) return@Canvas
        val unitX = size.width / matrix.width
        val unitY = size.height / matrix.height
        // 位阵坐标 → 设备像素（取整，见 KDoc「像素对齐」）
        fun pxX(value: Int): Float = (value * unitX).roundToInt().toFloat()
        fun pxY(value: Int): Float = (value * unitY).roundToInt().toFloat()

        var x = 0
        while (x < matrix.width) {
            var end = x + 1
            while (end < matrix.width && sameColumn(matrix, x, end)) end++
            var y = 0
            while (y < matrix.height) {
                if (!matrix[x, y]) {
                    y++
                    continue
                }
                var runEnd = y + 1
                while (runEnd < matrix.height && matrix[x, runEnd]) runEnd++
                val left = pxX(x)
                val top = pxY(y)
                val width = pxX(end) - left
                val height = pxY(runEnd) - top
                // 段宽 / 段高取整后可能为 0（位阵比可用像素还密，实际不会发生）：
                // 画 0 宽只是什么也不显示，比画一条错位的线安全
                if (width > 0f && height > 0f) {
                    drawRect(
                        color = fg,
                        topLeft = Offset(left, top),
                        size = Size(width, height),
                    )
                }
                y = runEnd
            }
            x = end
        }
    }
}

/** 两列逐位相同？（`a` / `b` 都是列下标，行数取矩阵高度） */
private fun sameColumn(matrix: BarcodeMatrix, a: Int, b: Int): Boolean {
    for (y in 0 until matrix.height) {
        if (matrix[a, y] != matrix[b, y]) return false
    }
    return true
}
