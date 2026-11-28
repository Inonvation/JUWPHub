package edu.jxslu.schedule.data.local

import edu.jxslu.schedule.ui.common.decodeBitmap
import edu.jxslu.schedule.ui.common.toByteArray
import edu.jxslu.schedule.ui.platform.PickedImage
import edu.jxslu.schedule.ui.week.downscaledToLongSide
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSData
import platform.Foundation.dataWithContentsOfURL
import platform.UIKit.UIImageJPEGRepresentation

/**
 * 选中图 → JPEG 字节（iOS，2026-11-29）：笔记附件与 AI 会话图片共用同一条压缩链。
 *
 * ## 口径
 *
 * 读文件 → `UIImage` 解码 → 长边钳制 → JPEG 编码；超字节上限就**降质量**重编，
 * 不降尺寸（图已经按长边缩过，再缩会动到像素尺度）。与 Android `ImageImport` 同一取舍。
 *
 * ## 为什么单独一个文件
 *
 * 两处调用（`IosAttachmentStore` 的长边 1600 / 质量 0.86、`IosAiImageStore` 的 1600 / 0.80 / ≤400KB）
 * 只有参数不同，解码与重编码这段一行不该抄两份——`scripts/find_duplicate_kt.ps1` 会盯同名私有函数。
 */
internal object IosImageCompress {

    /**
     * @param maxSide 长边上限（像素）
     * @param quality 起始 JPEG 质量（0..1）
     * @param maxBytes 字节上限；超过就按 [step] 降质量，降到 [minQuality] 仍超限就按当前结果返回
     */
    suspend fun jpegBytes(
        picked: PickedImage,
        maxSide: Int,
        quality: Double = 0.86,
        maxBytes: Long = Long.MAX_VALUE,
        minQuality: Double = 0.45,
        step: Double = 0.15,
    ): ByteArray? = withContext(Dispatchers.Default) {
        runCatching {
            val data = NSData.dataWithContentsOfURL(picked.url) ?: return@runCatching null
            val bytes = data.toByteArray() ?: return@runCatching null
            val image = decodeBitmap(bytes)?.nativeImage ?: return@runCatching null
            val scaled = image.downscaledToLongSide(maxSide)
            var current = quality
            var encoded = UIImageJPEGRepresentation(scaled, current)?.toByteArray()
                ?: return@runCatching null
            while (encoded.size > maxBytes && current > minQuality) {
                current -= step
                encoded = UIImageJPEGRepresentation(scaled, current)?.toByteArray()
                    ?: return@runCatching null
            }
            encoded
        }.getOrNull()
    }
}
