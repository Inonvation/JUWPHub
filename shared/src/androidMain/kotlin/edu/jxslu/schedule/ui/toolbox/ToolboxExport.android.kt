package edu.jxslu.schedule.ui.toolbox

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.core.content.FileProvider
import edu.jxslu.schedule.data.repo.ScheduleShareStore
import edu.jxslu.schedule.platform.requireAppContext
import edu.jxslu.schedule.ui.common.GallerySaveOutcome
import edu.jxslu.schedule.ui.common.PixelBitmap
import edu.jxslu.schedule.ui.common.PixelFormat
import edu.jxslu.schedule.ui.common.savePixelBitmapToGallery
import edu.jxslu.schedule.ui.common.shareFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * [saveToolboxImage] / [shareToolboxImage] 的 Android 实现（iOS 移植 V4，2026-11-26）。
 *
 * 两段都是 `:app` 那份 `ToolboxExport` 的**逐行搬移**，只换了三处：
 * `Context` 从注入点拿、位图从 Compose 的 `ImageBitmap` 取回原生 `Bitmap`（`asAndroidBitmap()`
 * 零拷贝）、分享目录仍由 `ScheduleShareStore` 拼（`cacheDir/toolbox_share`，与
 * `res/xml/file_paths.xml` 的 `cache-path toolbox_share` 对应）。
 *
 * 文件名、JPEG q92、失败文案、「先写 `.part` 再改名、只留最近 5 份」全部一字未改。
 */

/** 保存到相册：`MediaStore` 写 `Pictures/JUWPHub`，JPEG q92（见 `ToolboxExport` 的 KDoc）。 */
internal actual suspend fun saveToolboxImage(
    image: ImageBitmap,
    fileName: String,
): GallerySaveOutcome = withContext(Dispatchers.IO) {
    savePixelBitmapToGallery(
        bitmap = PixelBitmap(image.asAndroidBitmap()),
        displayName = fileName,
        format = PixelFormat.JPEG,
        quality = 92,
    )
}

/**
 * 分享：编码 JPEG → 落 `cacheDir/toolbox_share`（`ScheduleShareStore`，只留最近 5 份）→
 * `FileProvider` 换成临时授权 Uri → 系统分享面板。
 *
 * authority 是 `${applicationId}.fileprovider`，debug / release 两个包名不同——所以只能在
 * 这里用运行时的 `packageName` 拼，**不能写死**（改造前 `shareUriOf` 同一条理由）。
 */
internal actual suspend fun shareToolboxImage(
    image: ImageBitmap,
    fileName: String,
): String? {
    val context = requireAppContext("shareToolboxImage()")
    val uri = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = ByteArrayOutputStream().use { out ->
                if (!image.asAndroidBitmap().compress(Bitmap.CompressFormat.JPEG, 92, out)) {
                    return@runCatching null
                }
                out.toByteArray()
            }
            val store = ScheduleShareStore(
                File(context.cacheDir, ScheduleShareStore.DIR_TOOLBOX).absolutePath,
            )
            val path = store.save(bytes, fileName)
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(path))
                .toString()
        }.getOrNull()
    } ?: return "分享失败：无法写出图片"
    val ok = shareFile(uri, "image/jpeg", "分享图片")
    return if (ok) null else "没有可用的分享目标"
}
