package edu.jxslu.schedule.ui.common

import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import edu.jxslu.schedule.platform.requireAppContext
import java.io.File
import java.io.FileOutputStream

/**
 * [savePixelBitmapToGallery] 的 Android 实现（iOS 移植 P2，2026-11-24）：
 * API 29+ 走 MediaStore（无需权限），26–28 走公共目录 `Pictures/<subDir>` File 直写
 * （manifest 已声明 legacy 外存权限，`maxSdkVersion=28`）。
 *
 * **这段是 `:app/.../ui/common/BitmapSave.kt` 的逐行搬移**，只换了三处：
 * Context 从注入点拿、`Bitmap.CompressFormat` 换成本文件的 [PixelFormat]、
 * 位图从 [PixelBitmap] 里取回原生的 `Bitmap`（不复制像素）。目录口径、文件名、失败文案、
 * 「编码失败要回收已 insert 的空行」那条兜底一字未改——**别顺手统一文案**，用户看过这些字。
 *
 * 为什么 26–28 要走 File 直写而不是 MediaStore：`RELATIVE_PATH` 是 API 29 才有的列，
 * 低版本塞进去会被拒；低版本只能靠 legacy 外存权限直写公共目录再自己发媒体扫描（旧实现
 * 也没发 `MediaScannerConnection`，相册靠系统下次扫描收录，行为保持一致）。
 */
actual fun savePixelBitmapToGallery(
    bitmap: PixelBitmap,
    displayName: String,
    subDir: String,
    format: PixelFormat,
    quality: Int,
): GallerySaveOutcome {
    val context = requireAppContext("savePixelBitmapToGallery()")
    val source = bitmap.nativeBitmap
    val compressFormat = if (format == PixelFormat.PNG) {
        Bitmap.CompressFormat.PNG
    } else {
        Bitmap.CompressFormat.JPEG
    }
    return try {
        if (Build.VERSION.SDK_INT >= 29) {
            val mime = if (format == PixelFormat.PNG) "image/png" else "image/jpeg"
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Images.Media.MIME_TYPE, mime)
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_PICTURES + "/" + subDir,
                )
            }
            val uri = context.contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                values,
            ) ?: return GallerySaveOutcome.Failed("相册创建失败（系统拒绝写入）")
            val encoded = context.contentResolver.openOutputStream(uri)?.use { out ->
                source.compress(compressFormat, quality, out)
            } ?: return GallerySaveOutcome.Failed("相册创建失败（系统拒绝写入）")
            if (!encoded) {
                // 编码失败要回收已 insert 的空行：留着就是相册里一张 0 字节孤儿图
                runCatching { context.contentResolver.delete(uri, null, null) }
                return GallerySaveOutcome.Failed("图片编码失败")
            }
            GallerySaveOutcome.Saved(uri.toString())
        } else {
            @Suppress("DEPRECATION")
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                subDir,
            )
            if (!dir.exists() && !dir.mkdirs()) return GallerySaveOutcome.Failed("无法创建相册目录")
            val file = File(dir, displayName)
            FileOutputStream(file).use { out ->
                if (!source.compress(compressFormat, quality, out)) {
                    return GallerySaveOutcome.Failed("图片编码失败")
                }
            }
            GallerySaveOutcome.Saved(file.absolutePath)
        }
    } catch (e: SecurityException) {
        GallerySaveOutcome.Failed("没有相册写入权限")
    } catch (e: Exception) {
        GallerySaveOutcome.Failed("保存失败：${e.message ?: "未知错误"}")
    }
}
