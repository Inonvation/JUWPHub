package edu.jxslu.schedule.ui.common

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

/**
 * 文件图片的降采样解码（两遍：先读边界算 `inSampleSize`，再真解码）。
 *
 * 校历镜像这类**外部来源**的图尺寸不可控，一张 3000×4000 的 ARGB_8888 整幅解码就是
 * 48MB，低端机有 OOM 风险。工程内 [edu.jxslu.schedule.data.repo.ImageImport] /
 * ScheduleBackground / AttachmentImage 的解码都走两遍降采样，这里把「文件 → 目标长边」
 * 的公共形态收成一份，别处再写整幅 `decodeFile` 先想想是不是该用它。
 */
fun decodeFileSampled(file: File, targetLongSidePx: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    val options = BitmapFactory.Options().apply {
        inSampleSize = sampleSizeFor(maxOf(bounds.outWidth, bounds.outHeight), targetLongSidePx)
        // 钉死 8888：不再往下游漏非常规 color config（10-bit / HDR 照片在 API 34+ 会被解成
        // RGBA_F16 / RGBA_1010102，这类 config 不该传给 createBitmap）。
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    return BitmapFactory.decodeFile(file.absolutePath, options)
}


/** 两遍解码共用的「目标长边 → 采样倍率」判据（2 的幂，直到再降一半就低于目标）。 */
fun sampleSizeFor(longSidePx: Int, targetLongSidePx: Int): Int {
    var sample = 1
    while (longSidePx / (sample * 2) >= targetLongSidePx) sample *= 2
    return sample
}
