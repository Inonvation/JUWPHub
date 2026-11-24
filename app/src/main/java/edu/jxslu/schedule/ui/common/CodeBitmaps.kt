package edu.jxslu.schedule.ui.common

import android.graphics.Bitmap
import edu.jxslu.schedule.domain.BarcodeMatrix

/**
 * 位阵 → Bitmap 的共用渲染（DESIGN §4.19：校园卡与共享单车出码共用，不复制）。
 *
 * 黑白两色取当前主题的前景/背景（深色主题出「深底白码」：微信/相机扫一扫对反色码
 * 兼容良好，且与页面观感一致）。矩阵生成在 `:shared` 的 domain 层（[BarcodeMatrix]，
 * 两平台共用），本函数只做着色——Bitmap 是 Android 专有类型，出码逻辑不进 commonMain。
 */
object CodeBitmaps {

    fun render(matrix: BarcodeMatrix, dark: Boolean): Bitmap {
        val fg = if (dark) android.graphics.Color.WHITE else android.graphics.Color.BLACK
        val bg = if (dark) android.graphics.Color.BLACK else android.graphics.Color.WHITE
        val bitmap = Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
        for (y in 0 until matrix.height) {
            for (x in 0 until matrix.width) {
                bitmap.setPixel(x, y, if (matrix[x, y]) fg else bg)
            }
        }
        return bitmap
    }
}
