@file:OptIn(ExperimentalForeignApi::class)

package edu.jxslu.schedule.ui.common

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.UIKit.UIImage

/**
 * [PixelBitmap] 的 iOS 实现（iOS 移植 P2，2026-11-24）：包一层 `UIImage`。
 *
 * 不转 `CGImage`、不重绘画布：解码后原样持有，保存到相册时直接交给
 * `UIImageWriteToSavedPhotosAlbum`。少一层重绘就少一次色彩空间转换（JPEG 重编码掉画质的坑）。
 *
 * ## 宽高口径与 Android 不同（这是唯一需要注意的差异）
 *
 * `UIImage.size` 是**点**，不是像素：`size × scale` 才是像素。用数据解码出来的 `UIImage`
 * 通常 `scale == 1.0`（两者相等），但从相册/资源目录来、带 `@2x` 后缀的那种 `scale == 2.0`。
 * 这里一律换成像素，与 Android 侧 `Bitmap.width` 的口径对齐——调用方算比例时才不会差一倍。
 *
 * ## shortcut
 *
 * 现在没有「取 ARGB 像素」的入口。等百宝箱的水印/拼接搬过来，如果那条链路需要逐像素读写，
 * 在这里补 `CGBitmapContextCreate` 那条路（`domain/Barcode.ios.kt` 里有一段现成的用法可以先抄）。
 * 现在不加，是因为没有任何调用方能证明它需要。
 *
 * 编译状态：本机是 Windows，**这段没有编译验证过**（Kotlin/Native 要 macOS）。
 * `NSData.create(bytes:length:)` 与 `UIImage(data:)` 是本文件唯一两处不确定符号。
 */
actual class PixelBitmap internal constructor(internal val nativeImage: UIImage) {
    actual val width: Int get() = nativeImage.size.useContents { (width * nativeImage.scale).toInt() }
    actual val height: Int get() = nativeImage.size.useContents { (height * nativeImage.scale).toInt() }
}

/**
 * 解码图片字节：`UIImage(data:)`，解不出（不是图片 / 数据损坏）给 null。
 *
 * 与 Android 侧同一条失败口径：返回 null，不抛。`UIImage` 对无法识别的数据是返回 null 而不是
 * 抛异常，所以这里不需要 `try`。
 */
actual fun decodeBitmap(bytes: ByteArray): PixelBitmap? {
    if (bytes.isEmpty()) return null
    val image = UIImage(data = bytes.toNSData()) ?: return null
    return PixelBitmap(image)
}

/**
 * Kotlin `ByteArray` → `NSData`（复制一份，不共享内存）。
 *
 * 用 `usePinned` + `addressOf(0)`：`NSData.create` 会立刻把这段内存读走，pinning 在调用期间
 * 保证 GC 不挪动底层数组。**不要**换成 `bytesNoCopy`——那要求调用方保证内存在 NSData 的整个
 * 生命周期内有效，Kotlin 侧给不出这个承诺。
 */
private fun ByteArray.toNSData(): NSData = usePinned { pinned ->
    NSData.create(bytes = pinned.addressOf(0), length = size.toULong())
}
