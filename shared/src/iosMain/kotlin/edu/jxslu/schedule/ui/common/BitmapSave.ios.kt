@file:OptIn(ExperimentalForeignApi::class)

package edu.jxslu.schedule.ui.common

import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UIImageWriteToSavedPhotosAlbum

/**
 * [savePixelBitmapToGallery] 的 iOS 实现（iOS 移植 P2，2026-11-24）：
 * `UIImageWriteToSavedPhotosAlbum`。
 *
 * ## 四处有意的降级（都写在这里，别当 bug 报）
 *
 * 1. **`subDir` 与 `displayName` 丢弃**。iOS 的相册（照片 App）没有「App 可写的目录」这种
 *    东西：每条资产只属于相册自身（`Recents`）或用户自己建的相簿，App 不能指定存放路径，
 *    也不能指定文件名。Android 那边 `Pictures/JUWPHub` 这个口径在 iOS 上无从对应。
 * 2. **[format] / [quality] 丢弃**。这个 API 写的是 `UIImage` 本身：`format` 在**解码**那一步
 *    就已经决定了（`decodeBitmap` 解出来就是这么一张图），在这里没有第二次编码的机会；
 *    想让 JPEG 掉一次画质，只能自己重编码一遍再写，那是负收益。
 * 3. **返回值是「已提交」而不是「已写入」**。`UIImageWriteToSavedPhotosAlbum` 的完成回调是
 *    Objective-C selector 形态（要一个 target 对象 + `@objc` 方法），我们这边没有这样一个对象，
 *    所以传 `null` 不接回调。返回 [GallerySaveOutcome.Saved] 表示「已经交给系统相册」，
 *    `location` 为 null（系统不回头给句柄）。
 * 4. **权限失败这里看不出来**。写相册要 Info.plist 的 `NSPhotoLibraryAddUsageDescription`（P3 补）：
 *    缺这个键时 App 会被系统**直接杀掉**，不是返回错误；用户拒绝授权时系统只是静默不存。
 *    真要给用户一句准话，得换 `PHPhotoLibrary.performChanges`（异步回调）或者先同步查
 *    `authorizationStatus`——那两件事都要等 P3 的权限引导一起做。
 *
 * ## 为什么不用 PHPhotoLibrary
 *
 * 它唯一的好处是能拿到成功/失败（异步）。现在没有 `suspend` 的缝形状，接了回调也只能扔，
 * 白白多一套符号与一个 `PHAssetChangeRequest` 的依赖；等 P3 把这条缝改成挂起函数时再换。
 *
 * shortcut: 上面第 3、4 条一起解决——P3 拿到相册权限流后，把 `savePixelBitmapToGallery` 改成
 * `suspend`（Android 侧仍是同步实现），iOS 侧换 `PHPhotoLibrary.performChanges`，
 * 那时 `Saved.location` 也才有机会回传 `PHAsset` 的 localIdentifier。
 *
 * 编译状态：本机是 Windows，**这段没有编译验证过**（Kotlin/Native 要 macOS）。
 * 用到的符号只有一个 C 函数 `UIImageWriteToSavedPhotosAlbum`，参数全给 null，风险见返回报告。
 */
actual fun savePixelBitmapToGallery(
    bitmap: PixelBitmap,
    displayName: String,
    subDir: String,
    format: PixelFormat,
    quality: Int,
): GallerySaveOutcome {
    // 三个 null 分别是 completionTarget / completionSelector / contextInfo：见类 KDoc 第 3 条
    UIImageWriteToSavedPhotosAlbum(bitmap.nativeImage, null, null, null)
    return GallerySaveOutcome.Saved(location = null)
}
