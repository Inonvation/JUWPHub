package edu.jxslu.schedule.ui.ebike

import edu.jxslu.schedule.domain.BarcodeMatrix
import edu.jxslu.schedule.ui.common.GallerySaveOutcome
import edu.jxslu.schedule.ui.common.decodeBitmap
import edu.jxslu.schedule.ui.common.savePixelBitmapToGallery
import edu.jxslu.schedule.ui.common.toPngBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 「存相册」的 iOS 实现（2026-11-26，补上 `EbikeQrGallery` 缺的那一半）。
 *
 * ## 一条链，三处既有出口
 *
 * | 步骤 | 用什么 | 出处 |
 * |---|---|---|
 * | 位阵 → 位图 | [qrImageBitmap]（Compose `ImageBitmap`，共享层） | 本仓 `ui/ebike/EbikeQrImage.kt` |
 * | 位图 → PNG 字节 | `ImageBitmap.toPngBytes()`（CoreGraphics 拼 `UIImage` + `UIImagePNGRepresentation`） | `ui/common/IosBitmapPng.kt`（CI 编过） |
 * | 字节 → `UIImage` → 相册 | `decodeBitmap(bytes)` → `savePixelBitmapToGallery`（`UIImageWriteToSavedPhotosAlbum`） | `ui/common/PixelBitmap.ios.kt` / `BitmapSave.ios.kt` |
 *
 * 为什么绕一圈 PNG 而不是直接拼 `UIImage`：`PixelBitmap` 的唯一构造入口就是 `decodeBitmap(bytes)`
 * （见 `ui/common/PixelBitmap.kt`「不暴露构造器」那段），而 `savePixelBitmapToGallery` 只吃
 * `PixelBitmap`。这条链与「课表导出图片存相册」（`ui/week/IosScheduleShareActions.kt`）逐段相同，
 * 所以**没有一个新符号**——本文件一行 CoreGraphics / UIKit 都不写。
 *
 * `Info.plist` 的 `NSPhotoLibraryAddUsageDescription` 已经就位（2026-11-26 补，缺了会被系统直接杀进程）。
 *
 * ## 扫完即焚在 iOS 上做不到
 *
 * `UIImageWriteToSavedPhotosAlbum` 不回头给任何句柄（`BitmapSave.ios.kt` 的 KDoc 第 3 条），
 * 所以「按 key 删除刚才那张」在 iOS 上无从实现。要做得换 PhotoKit
 * （`PHPhotoLibrary.performChanges` 拿 `PHObjectPlaceholder.localIdentifier`，删除时
 * `PHAssetChangeRequest.deleteAssets`），仓里一处先例都没有，而且 iOS 每次删除都会弹一次
 * 系统确认框——用户点「骑完车自动删除」之后还得逐张确认，那不是「自动」。
 *
 * 因此 [canDelete] = false：共享层据此把「骑完车自动删除」开关藏起来、**并且不记录待焚毁 key**
 * （`EbikeViewModel.saveCurrentInternal`）。存相册本身照常可用。
 *
 * ## 返回值语义（与 Android 的差异照实说）
 *
 * [EbikeQrSaveResult.Saved] 在这里只表示「已交给系统相册」。iOS 不告诉 App 存没存成、
 * 也不给条目句柄，所以 `pendingKey` 是一个**不可删除的记号**（[ALBUM_PENDING_PREFIX] 前缀，
 * 不满足 `EbikeQr.parsePendingKey`，任何删除通道都会把它当成脏数据丢掉）。
 *
 * ## 编译状态
 *
 * 本机是 Windows，**没有编译验证过**（Kotlin/Native 要 macOS）。本文件没有引入新 iOS 符号，
 * 全部调用点都在上面那张表里指了出来。
 */
class IosEbikeQrGallery : EbikeQrGallery {

    /** 相册资产没有可回传的句柄，删不掉（见类 KDoc「扫完即焚」一节）。 */
    override val canDelete: Boolean get() = false

    override suspend fun save(
        matrix: BarcodeMatrix,
        dark: Boolean,
        displayName: String,
    ): EbikeQrSaveResult {
        // 编码与解码都按 ARGB 逐像素跑一遍 720×720，必须在后台线程（同 `IosScheduleShareActions`）
        val bitmap = withContext(Dispatchers.Default) {
            qrImageBitmap(matrix, dark).toPngBytes()?.let { bytes -> decodeBitmap(bytes) }
        } ?: return EbikeQrSaveResult.Failed("图片编码失败")
        // 碰 UIKit 的那一步回主线程：`UIImageWriteToSavedPhotosAlbum` 是 UIKit 的东西
        return withContext(Dispatchers.Main) {
            when (val outcome = savePixelBitmapToGallery(bitmap, displayName)) {
                is GallerySaveOutcome.Saved ->
                    EbikeQrSaveResult.Saved(pendingKey = ALBUM_PENDING_PREFIX + displayName)

                is GallerySaveOutcome.Failed -> EbikeQrSaveResult.Failed(outcome.message)
            }
        }
    }

    /**
     * 永远 false：iOS 这一版没有删除通道。
     *
     * 不会造成空转——共享层在 [canDelete] 为 false 时根本不记录待焚毁 key
     * （`EbikeViewModel.saveCurrentInternal` / `burnPending` 都先看这个标志）。
     */
    override suspend fun deletePending(key: String): Boolean = false
}

/**
 * 「已存进相册」的记号前缀：`album:<文件名>`。
 *
 * 故意不是 `EbikeQr.PENDING_MEDIA_PREFIX` / `PENDING_FILE_PREFIX`：那两个会被
 * `EbikeQr.parsePendingKey` 认成「可删除目标」，而这里根本没有能删的东西。
 * 未知前缀在那边是「脏数据，丢掉」，正是想要的行为。
 */
private const val ALBUM_PENDING_PREFIX = "album:"
