@file:OptIn(ExperimentalForeignApi::class)

package edu.jxslu.schedule.ui.common

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.set
import platform.CoreGraphics.CGBitmapContextCreate
import platform.CoreGraphics.CGBitmapContextCreateImage
import platform.CoreGraphics.CGColorSpaceCreateDeviceRGB
import platform.CoreGraphics.CGColorSpaceRelease
import platform.CoreGraphics.CGImageAlphaInfo
import platform.Foundation.NSData
import platform.UIKit.UIImage
import platform.UIKit.UIImagePNGRepresentation

/**
 * `ImageBitmap` → PNG 字节的 iOS 实现（iOS 移植 M3，2026-11-26）。
 *
 * ## 为什么不能照抄 Android 那两行
 *
 * Android 宿主是 `image.asAndroidBitmap().compress(PNG, 100, out)`。iOS 的 `ImageBitmap` 是
 * Compose 的 skia 位图，没有 `compress`，`PixelBitmap` 的构造器又是 internal 且只从
 * `decodeBitmap` 出来，所以「先按 ARGB 读像素 → 自己拼一张 `UIImage` → 让 UIKit 编码 PNG」
 * 是唯一一条只用得上**已有出处符号**的路：
 *
 * | 步骤 | 符号 | 出处 |
 * |---|---|---|
 * | 读像素 | `ImageBitmap.readPixels(IntArray)` | Compose 公共 API（`androidx.compose.ui.graphics`），已用 `:shared:metadataCommonMainClasses` 在本机核实 |
 * | ARGB → RGBA 缓冲 | `allocArray<UByteVar>` / `memScoped` / `readBytes` | `domain/Barcode.ios.kt` 的 `decodeQrMatrix`、`platform/IosKeychain.kt` 逐字同款 |
 * | 建位图上下文 | `CGBitmapContextCreate` / `CGColorSpaceCreateDeviceRGB` / `CGImageAlphaInfo.kCGImageAlphaPremultipliedLast` | `domain/Barcode.ios.kt`（CI 已编译过） |
 * | 出 `CGImage` | `CGBitmapContextCreateImage` | 同上 |
 * | `CGImage` → `UIImage` | `UIImage.imageWithCGImage` | **新符号**。形状与 `Barcode.ios.kt` 的 `CIImage.imageWithCGImage` 相同（同为 ObjC 类工厂，不需要单独 import） |
 * | `UIImage` → PNG | `UIImagePNGRepresentation` | **新符号**。与 `BitmapSave.ios.kt` 的 `UIImageWriteToSavedPhotosAlbum` 同为 UIKit 的 C 函数 |
 * | `NSData` → `ByteArray` | `NSData.bytes` + `reinterpret<ByteVar>().readBytes` | **新属性**，取值写法与 `IosKeychain.kt` 的 `CFDataGetBytePtr` 那段一致 |
 *
 * 刻意**不**走 skia（`asSkiaBitmap()` + `Image.encodeToData`）：那条路少写 30 行，但要在
 * `iosMain` 里新引 `org.jetbrains.skia.*` 四个符号，而仓里一处都没有——按「无出处 = 高风险」
 * 的规矩，宁可多写这段 CoreGraphics。
 *
 * ## 两个必须记住的前提
 *
 * 1. **不透明**：像素按 `kCGImageAlphaPremultipliedLast`（RGBA、alpha 已预乘）写入。
 *    课表截图是不透明的，alpha 恒 255，预乘等于直通；将来若有半透明图，颜色会偏暗。
 * 2. **慢**：`readPixels` 要整张拷一遍，再逐像素拼一次字节。一张 1080×2400 的图是
 *    2.6M 次循环，必须在后台线程调（调用方 `ui/week/IosScheduleShareActions.kt` 已经
 *    `withContext(Dispatchers.Default)` 包住）。
 *
 * 编译状态：本机是 Windows，编不了 Kotlin/Native。上面标「新符号」的三处是**纸面推导**，
 * 见返回报告的「未验」清单。像素序（ARGB → RGBA）与上下方向只能在真机上看图确认。
 */

/** PNG 字节；读不到像素 / 建不了图 / 编码失败一律回 null，**不抛**（调用方给一句文案）。 */
internal fun ImageBitmap.toPngBytes(): ByteArray? {
    if (width <= 0 || height <= 0) return null
    val pixels = IntArray(width * height)
    // Compose 的 readPixels 交出来的是 ARGB（0xAARRGGBB），与 Android 的 Bitmap.getPixels 同口径
    readPixels(pixels)
    val image = argbToUIImage(pixels, width, height) ?: return null
    val data = UIImagePNGRepresentation(image) ?: return null
    return data.toByteArray()
}

/**
 * ARGB 像素 → `UIImage`。
 *
 * 中间那块 RGBA 缓冲生在 `memScoped` 里：`CGBitmapContextCreate` 不复制数据，`CGImage` 也还
 * 指着它，所以出图这一步必须在作用域内完成（`CGBitmapContextCreateImage` 已经拷了一份）。
 */
private fun argbToUIImage(pixels: IntArray, width: Int, height: Int): UIImage? = memScoped {
    val rgba = allocArray<UByteVar>(width * height * 4)
    for (i in 0 until width * height) {
        val argb = pixels[i]
        val at = i * 4
        rgba[at] = ((argb shr 16) and 0xFF).toUByte()
        rgba[at + 1] = ((argb shr 8) and 0xFF).toUByte()
        rgba[at + 2] = (argb and 0xFF).toUByte()
        rgba[at + 3] = ((argb ushr 24) and 0xFF).toUByte()
    }
    val colorSpace = CGColorSpaceCreateDeviceRGB()
    val bitmap = CGBitmapContextCreate(
        data = rgba,
        width = width.toULong(),
        height = height.toULong(),
        bitsPerComponent = 8uL,
        bytesPerRow = (width * 4).toULong(),
        space = colorSpace,
        bitmapInfo = CGImageAlphaInfo.kCGImageAlphaPremultipliedLast.value,
    )
    if (bitmap == null) {
        // 上下文没建起来也要还色彩空间：Create 规则是调用方持有
        CGColorSpaceRelease(colorSpace)
        return@memScoped null
    }
    val cgImage = CGBitmapContextCreateImage(bitmap)
    // 上下文与图像都已各自持有色彩空间，这里还回去
    CGColorSpaceRelease(colorSpace)
    if (cgImage == null) return@memScoped null
    // 位图上下文里 Quartz 的 y 轴朝上，内存第 0 行就是图像最上面一行；CGImage 也是自上而下，
    // 所以这里不需要再翻一次（翻法见 Barcode.ios.kt 的 rasterizeGray 同款注释）
    UIImage.imageWithCGImage(cgImage)
}

/**
 * `NSData` → 字节数组；取长度与指针的写法照 `IosKeychain.kt` 里 `CFDataGetBytePtr` 那段。
 *
 * `internal`（2026-11-26）：课表背景图的导入（`ui/week/IosScheduleBackgroundImport.kt`）读
 * `NSData` 之后也要同一份转换，别再写第二遍。
 */
internal fun NSData.toByteArray(): ByteArray? {
    val size = length.toInt()
    if (size <= 0) return null
    val pointer = bytes ?: return null
    return pointer.reinterpret<ByteVar>().readBytes(size)
}
