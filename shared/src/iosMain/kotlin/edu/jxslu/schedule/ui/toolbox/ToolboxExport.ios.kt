@file:OptIn(ExperimentalForeignApi::class)

package edu.jxslu.schedule.ui.toolbox

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import edu.jxslu.schedule.ui.common.GallerySaveOutcome
import edu.jxslu.schedule.ui.common.PixelFormat
import edu.jxslu.schedule.ui.common.decodeBitmap
import edu.jxslu.schedule.ui.common.savePixelBitmapToGallery
import edu.jxslu.schedule.ui.common.shareFile
import edu.jxslu.schedule.ui.common.writeExportedFile
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image

/**
 * [saveToolboxImage] / [shareToolboxImage] 的 iOS 实现（iOS 移植 V4，2026-11-26）：
 * skia 编码 JPEG q92（`Image.encodeToData`），再交给两条现成的缝。
 *
 * ## 为什么编码走 skia
 *
 * iOS 的 `ImageBitmap` 背后是 skiko 的位图，UIKit 拿不到「一个文件里的一串像素」——
 * 要么重绘成 `CGImage`（`ui/common/IosBitmapPng.kt` 那条路，写了几十行 CoreGraphics），
 * 要么让 skia 自己编。这里选后者：`asSkiaBitmap()` 零拷贝拿到源位图，
 * `Image.makeFromBitmap(...).encodeToData(JPEG, 92)` 一行出字节，JPEG 质量与 Android 同档。
 *
 * | 出口 | 路径 |
 * |---|---|
 * | 保存到相册 | JPEG 字节 → `decodeBitmap`（`UIImage`）→ `savePixelBitmapToGallery`（`UIImageWriteToSavedPhotosAlbum`） |
 * | 分享 | JPEG 字节 → `writeExportedFile`（`appDataDir()/exports/<文件名>`）→ `shareFile`（`UIActivityViewController`） |
 *
 * 两处「先编码再解码」看着绕，但都是**沿用已有缝**的必然结果：相册缝吃 `PixelBitmap`
 * （包着一个 `UIImage`），分享缝吃文件路径。真正的像素只存在一次，编码这一步只是换容器。
 *
 * ## 三个前提（缺一个就是「点了没反应」）
 *
 * 1. **`IosSharePresenter()` 必须已在根组合里调过**：分享面板的宿主 VC 从那里来
 *    （`ui/common/IosSharePresenter.kt`，与课表导出图片同一条链）。
 * 2. **`Info.plist` 要有 `NSPhotoLibraryAddUsageDescription`**：缺这个键时
 *    `UIImageWriteToSavedPhotosAlbum` 不是返回错误，而是让系统**直接杀掉 App**。
 * 3. **`UIActivityViewController` 必须在主线程 present**：所以 `shareFile` 那句包在
 *    `withContext(Dispatchers.Main)` 里；同理相册写入也回主线程。
 *
 * ## 编译状态（本机是 Windows，编不了 Kotlin/Native）
 *
 * | 符号 | 出处 / 风险 |
 * |---|---|
 * | `ImageBitmap.asSkiaBitmap()` | **新符号**。CMP 的 skiko 侧扩展；`Image.makeFromBitmap` 零拷贝取像素 |
 * | `Image.encodeToData(EncodedImageFormat.JPEG, quality)` | **新符号**。返回 `Data?`，`.bytes` 取字节 |
 * | `decodeBitmap` / `savePixelBitmapToGallery` / `writeExportedFile` / `shareFile` | 仓内既有实现（`PixelBitmap.ios.kt` / `BitmapSave.ios.kt` / `IosExports.kt` / `ShareIntents.ios.kt`） |
 */
internal actual suspend fun saveToolboxImage(
    image: ImageBitmap,
    fileName: String,
): GallerySaveOutcome {
    // 编码在后台：2600 长边的一张图要几十毫秒
    val bitmap = withContext(Dispatchers.Default) {
        encodeSkia(image, EncodedImageFormat.JPEG, JPEG_QUALITY)?.let(::decodeBitmap)
    } ?: return GallerySaveOutcome.Failed("图片编码失败")
    // UIImageWriteToSavedPhotosAlbum 是 UIKit 的东西，回主线程再交出去
    return withContext(Dispatchers.Main) {
        savePixelBitmapToGallery(
            bitmap = bitmap,
            displayName = fileName,
            format = PixelFormat.JPEG,
            quality = JPEG_QUALITY,
        )
    }
}

internal actual suspend fun shareToolboxImage(
    image: ImageBitmap,
    fileName: String,
): String? {
    val path = withContext(Dispatchers.Default) {
        val bytes = encodeSkia(image, EncodedImageFormat.JPEG, JPEG_QUALITY) ?: return@withContext null
        writeExportedFile(fileName, bytes)
    } ?: return "分享失败：无法写出图片"
    // present 必须在主线程（UIActivityViewController 的硬要求）
    return withContext(Dispatchers.Main) {
        if (shareFile(path, "image/jpeg", "分享图片")) null else "没有可用的分享目标"
    }
}

/** 与 Android 同档的 JPEG 质量（`Bitmap.compress(JPEG, 92)`）。 */
private const val JPEG_QUALITY = 92


/**
 * `ImageBitmap` → 编码字节；任何一步失败（位图不是 skia 支持的格式、编码器返回 null）回 null，不抛。
 */
private fun encodeSkia(
    image: ImageBitmap,
    format: EncodedImageFormat,
    quality: Int,
): ByteArray? = runCatching {
    Image.makeFromBitmap(image.asSkiaBitmap()).encodeToData(format, quality)?.bytes
}.getOrNull()
