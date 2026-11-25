package edu.jxslu.schedule.ui.week

import androidx.compose.ui.graphics.ImageBitmap
import edu.jxslu.schedule.ui.common.GallerySaveOutcome
import edu.jxslu.schedule.ui.common.decodeBitmap
import edu.jxslu.schedule.ui.common.savePixelBitmapToGallery
import edu.jxslu.schedule.ui.common.shareFile
import edu.jxslu.schedule.ui.common.toPngBytes
import edu.jxslu.schedule.ui.common.writeExportedFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 课表导出图片的 MIME（分享时给系统一个提示；iOS 的分享面板实际按扩展名认类型）。 */
private const val SCHEDULE_IMAGE_MIME = "image/png"

/**
 * 课表导出页（`ui/week/ScheduleShareScreen.kt`）在 iOS 侧的两件平台动作（iOS 移植 M3，2026-11-26）。
 *
 * 页面把这两格做成**可空回调**（`design/ios-port.md` §11.2 ④），本文件提供能直接填进去的实现。
 * 宿主接线（`IosNavigation.kt` 的 `SubpageScreen.SCHEDULE_SHARE` 分支）：
 *
 * ```kotlin
 * ScheduleShareScreen(
 *     viewModel = weekViewModel,          // iOS 侧 remember { … }，见该页参数表
 *     onBack = pop,
 *     initialWeek = focusWeek,
 *     loadBackground = null,              // 背景图解码还没做，见该页 KDoc
 *     onSaveToGallery = ::iosSaveScheduleImageToGallery,
 *     onShareImage = ::iosShareScheduleImage,
 * )
 * ```
 *
 * ## 两个前提（缺一个就会出现「点了没反应」）
 *
 * 1. **`IosSharePresenter()` 必须已在根组合里调过**：分享面板的宿主 VC 从那里来
 *    （`ui/common/IosSharePresenter.kt`）。没装时 [iosShareScheduleImage] 会返回
 *    「没有可用的分享目标」，页面照常提示，不会静默。
 * 2. **`Info.plist` 要有 `NSPhotoLibraryAddUsageDescription`**：缺这个键时
 *    `UIImageWriteToSavedPhotosAlbum` 不是返回错误，而是让**系统直接杀掉 App**
 *    （`ui/common/BitmapSave.ios.kt` 的 KDoc 已记这条）。
 *
 * ## 位图怎么来、怎么走
 *
 * 截图（`GraphicsLayer.toImageBitmap()`）在共享层完成，交到这里的是 Compose 的 `ImageBitmap`。
 * iOS 侧没有 `Bitmap.compress`，所以先按 ARGB 读像素再拼 `UIImage`、由 UIKit 编码 PNG
 * （细节与符号出处见 `ui/common/IosBitmapPng.kt`）：
 *
 * | 动作 | 路径 |
 * |---|---|
 * | 分享 | PNG 字节 → `appDataDir()/exports/<displayName>` → `shareFile`（`UIActivityViewController`） |
 * | 存相册 | PNG 字节 → `decodeBitmap` 还原成 `UIImage` → `UIImageWriteToSavedPhotosAlbum` |
 *
 * 两件都先把编码丢进 [Dispatchers.Default]（页面本身已包了一层，这里再包一层是为了「谁调都对」），
 * 到了碰 UIKit 的那一步再回主线程。
 */

/**
 * 保存课表图片到系统相册。
 *
 * 与 Android 那份（`saveBitmapToGallery` → MediaStore）的语义差异照实说：iOS 的相册没有
 * 「App 可写的目录」，文件名与子目录都被系统丢掉，`Saved.location` 恒为 null（见
 * `ui/common/BitmapSave.kt` 的对照表）。返回值「已交给系统相册」**不等于**系统已经写完。
 */
suspend fun iosSaveScheduleImageToGallery(
    image: ImageBitmap,
    displayName: String,
): GallerySaveOutcome {
    val bitmap = withContext(Dispatchers.Default) {
        image.toPngBytes()?.let { bytes -> decodeBitmap(bytes) }
    } ?: return GallerySaveOutcome.Failed("图片编码失败")
    // UIImageWriteToSavedPhotosAlbum 是 UIKit 的东西，回主线程再交出去
    return withContext(Dispatchers.Main) { savePixelBitmapToGallery(bitmap, displayName) }
}

/**
 * 分享课表图片；返回 null = 面板已交给系统，非 null = 给用户看的失败原因（文案与 Android 宿主
 * `ui/week/ScheduleShareHost.kt` 逐字相同，页面只负责显示）。
 *
 * 落点是 `appDataDir()/exports/<displayName>`（`displayName` 自带 `.png`，
 * 形如 `JUWPHub课表_20260109_第3周.png`）。同周重复导出覆盖同一份；不裁旧文件——
 * 理由见 `ui/common/IosExports.kt`。
 */
suspend fun iosShareScheduleImage(image: ImageBitmap, displayName: String): String? {
    val path = withContext(Dispatchers.Default) {
        val bytes = image.toPngBytes() ?: return@withContext null
        writeExportedFile(displayName, bytes)
    } ?: return "分享失败：无法写出图片"
    // present 必须在主线程（UIActivityViewController 的硬要求）
    return withContext(Dispatchers.Main) {
        if (shareFile(path, SCHEDULE_IMAGE_MIME, "分享课表图片")) null else "没有可用的分享目标"
    }
}
