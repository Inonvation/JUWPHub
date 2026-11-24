package edu.jxslu.schedule.ui.common

import edu.jxslu.schedule.domain.GalleryPaths

/**
 * 位图落盘到系统相册的平台缝（iOS 移植 P2，2026-11-24）。
 *
 * ## 为什么需要它
 *
 * 有 5 处在存图：共享单车出码（§4.18）、课表导出图片（§4.12）、百宝箱水印与拼接（§4.47）、
 * 支持开发者页的收款码。Android 那份实现在 `:app/.../ui/common/BitmapSave.kt`（86 行），
 * 里面是 `MediaStore` / 公共目录直写；iOS 是 `UIImageWriteToSavedPhotosAlbum`。
 * 这一批只立缝：**调用点仍然在 `:app`，那份旧实现原样留着**，等 §4.47 那批页面搬进
 * `commonMain` 时删掉它、调用点改用本文件。所以现在仓里暂时有两份相册保存代码，
 * 是有意的过渡态（旧的那份吃 `android.graphics.Bitmap`，新的吃 [PixelBitmap]）。
 *
 * ## 两端的语义差异（照实写，别当 bug 报）
 *
 * | 项 | Android | iOS |
 * |---|---|---|
 * | 目录 | API 29+ 落 `Pictures/<subDir>`（MediaStore），26–28 直写公共目录 | `subDir`、`displayName` **都丢弃**：照片 App 只按时间线组织，没有「相册目录」这种可写的位置 |
 * | 编码 | 按 [PixelFormat] 自己压：PNG / JPEG + 质量 | 交给系统：写的是 `UIImage` 本身，[format] / [quality] 只在**解码**那一步影响画质，这里有意的忽略（见 `BitmapSave.ios.kt`） |
 * | 返回值 | 同步：真落盘了才给 `Saved`，带 uri 或绝对路径 | 异步：`Saved` 表示「已交给系统相册」，**不代表已经写完**；`location` 恒为 null（系统不回头给我们句柄） |
 * | 权限 | 29+ 无需权限，26–28 靠 manifest 的 legacy 外存权限 | 需要 Info.plist 的 `NSPhotoLibraryAddUsageDescription`（P3 补），缺失时系统直接杀掉 App |
 *
 * ## 为什么类型叫 [GallerySaveOutcome]、函数叫 [savePixelBitmapToGallery]
 *
 * `:app` 里那份旧实现有同包同名的 `GallerySaveResult` 与 `saveBitmapToGallery`。
 * 两个模块的同包同名类在 dex 合并时会撞成 `Duplicate program class`，`assembleDebug` 直接失败，
 * 所以共享层这两个名字都带前缀避开。等旧实现删掉那天可以收掉前缀，
 * **别为了名字好看现在去删旧文件**——旧文件的调用点在业务页面里，属于它们各自的搬迁批次。
 *
 * ## shortcut
 *
 * 只做「一张图、一个文件名」这一件事，失败只给一句话文案。等 P3 拿到真的 iOS 相册权限流，
 * 再考虑把异步完成做成挂起函数（届时 iOS 用 `PHPhotoLibrary.performChanges` 的回调，
 * Android 侧仍是同步实现，签名会变成 `suspend`）。现在加 `suspend` 只会让 Android 那半白等。
 */
sealed interface GallerySaveOutcome {
    /**
     * 落盘位置；[location] 为 null 表示「已交给系统，但没有可回传的句柄」。
     *
     * Android：API 29+ 是 MediaStore 的 `content://` 字符串，26–28 是落盘文件的绝对路径
     * （可以直接拿去分享，或者当作「待焚毁 key」）。iOS：恒为 null。
     */
    data class Saved(val location: String?) : GallerySaveOutcome

    /** 失败原因，直接展示给用户（调用方负责提示，不静默丢）。 */
    data class Failed(val message: String) : GallerySaveOutcome
}

/** 保存时用的编码格式。只需要这两档：旧实现里除 PNG 之外一律按 JPEG 处理。 */
enum class PixelFormat {
    PNG,
    JPEG,
}

/**
 * 保存一张位图到系统相册。成功返回 [GallerySaveOutcome.Saved]，失败返回带原因的
 * [GallerySaveOutcome.Failed]（调用方负责给用户提示，不静默丢）。
 *
 * @param bitmap 解码得到的位图（见 [decodeBitmap]）；不做尺寸检查，原样存
 * @param displayName 文件名（含扩展名）。Android 用它命名相册条目；iOS 忽略
 * @param subDir 相册子目录。Android 落 `Pictures/<subDir>`；iOS 忽略。
 *   默认 `GalleryPaths.CURRENT_SUBDIR`，旧图留在 `GalleryPaths.LEGACY_SUBDIR`，不迁移
 * @param format 编码格式，默认 PNG；Android 用它决定 mime 与压缩方式
 * @param quality JPEG 质量（0–100），PNG 忽略
 */
expect fun savePixelBitmapToGallery(
    bitmap: PixelBitmap,
    displayName: String,
    subDir: String = GalleryPaths.CURRENT_SUBDIR,
    format: PixelFormat = PixelFormat.PNG,
    quality: Int = 100,
): GallerySaveOutcome
