package edu.jxslu.schedule.ui.toolbox

import androidx.compose.ui.graphics.ImageBitmap
import edu.jxslu.schedule.domain.AppClock
import edu.jxslu.schedule.domain.TextFmt
import edu.jxslu.schedule.ui.common.GallerySaveOutcome
import kotlinx.datetime.LocalDateTime

/**
 * 百宝箱图片的**出口**（DESIGN §4.47；iOS 移植 V4，2026-11-26）。
 *
 * ## 出口只有两条，都在这里（形状沿用 `:app` 的 `ToolboxExport`）
 *
 * | 出口 | 落点 |
 * |---|---|
 * | 保存到相册 | `ui/common/BitmapSave` 的 [edu.jxslu.schedule.ui.common.savePixelBitmapToGallery]（Android `MediaStore` 的 `Pictures/JUWPHub`；iOS 交系统相册） |
 * | 分享 | `ui/common/ShareIntents` 的 [edu.jxslu.schedule.ui.common.shareFile]（Android `ACTION_SEND` + 临时授权 Uri；iOS `UIActivityViewController`） |
 *
 * 两个 expect 只是把「渲染出来的 `ImageBitmap` → 像素 → 上面那两条现成缝」这一段按平台接上：
 *
 * | 端 | 保存 | 分享 |
 * |---|---|---|
 * | Android | `asAndroidBitmap()` → `PixelBitmap` → `savePixelBitmapToGallery(JPEG, 92)` | `compress(JPEG, 92)` → `ScheduleShareStore(cacheDir/toolbox_share)` → `FileProvider` → `shareFile` |
 * | iOS | skia 编码 JPEG → `decodeBitmap`（`UIImage`）→ `savePixelBitmapToGallery` | skia 编码 JPEG → `appDataDir()/exports/` → `shareFile`（路径） |
 *
 * **不新开存储 / 分享机制**：Android 的目录与 FileProvider 根仍是 `file_paths.xml` 里原有的
 * `cache-path toolbox_share`（改造前由 `Graph.toolboxShareStore` 拼同一个路径），
 * iOS 仍是 `ui/common/IosExports.kt` 的 `exports/`（与课表导出图片同一处）。
 *
 * ## 格式为什么还是 JPEG q92
 *
 * 产物是截图 / 照片，PNG 体积放大一个量级；q92 下叠加的小字仍然锐利（课表导出是纯色块图，
 * 才用 PNG）。改格式要一并改文件名后缀，见 `.agents/rules/toolbox.md`「出口只有两条」。
 *
 * ## 线程
 *
 * 两个 expect 内部自己切线程：编码/落盘在后台，碰 UIKit（`UIImageWriteToSavedPhotosAlbum`、
 * `UIActivityViewController`）与 `MediaStore` 的写回按平台要求回到主线程 / IO。
 * 调用方在 `rememberCoroutineScope().launch` 里直接 `await` 即可。
 */
object ToolboxExport {

    /**
     * 落盘 / 分享共用的文件名：`JUWPHub-百宝箱-<工具>-<时间戳>.jpg`。
     *
     * 时间戳格式与改造前的 `DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")` 逐字相同；
     * 现在走 [AppClock] + [TextFmt.pad]，不依赖 `java.time`。
     */
    fun fileName(tool: String, now: LocalDateTime = AppClock.nowDateTime()): String = buildString {
        append("JUWPHub-百宝箱-")
        append(tool)
        append('-')
        append(now.year)
        append(TextFmt.pad(now.monthNumber))
        append(TextFmt.pad(now.dayOfMonth))
        append('-')
        append(TextFmt.pad(now.hour))
        append(TextFmt.pad(now.minute))
        append(TextFmt.pad(now.second))
        append(".jpg")
    }
}

/**
 * 保存一张成品到系统相册：成功 [GallerySaveOutcome.Saved]，失败带原因（调用方负责提示）。
 *
 * iOS 侧的语义差异（文件名/目录被丢弃、`Saved` 只代表「已交给系统相册」）见
 * [edu.jxslu.schedule.ui.common.savePixelBitmapToGallery] 的 KDoc，这里不重复。
 */
internal expect suspend fun saveToolboxImage(
    image: ImageBitmap,
    fileName: String,
): GallerySaveOutcome

/**
 * 分享一张成品；返回 null = 面板已交给系统，非 null = 给用户看的失败原因（与改造前文案相同）。
 */
internal expect suspend fun shareToolboxImage(
    image: ImageBitmap,
    fileName: String,
): String?

/**
 * 看起来是不是内存不足（`OutOfMemoryError`）。
 *
 * 为什么不用类型判断：`OutOfMemoryError` 不在 Kotlin 公共标准库里（只有 JVM 有），
 * `commonMain` 写不出 `catch (e: OutOfMemoryError)`。两个工具页都保留改造前那句
 * 「图片太大，内存不足……」的提示，判据退回类名——JVM 与 Kotlin/Native 的类名都是这一个。
 */
internal fun Throwable.looksLikeOutOfMemory(): Boolean =
    this::class.simpleName == "OutOfMemoryError"
