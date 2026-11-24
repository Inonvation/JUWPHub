@file:OptIn(ExperimentalForeignApi::class)

package edu.jxslu.schedule.domain

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.set
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGBitmapContextCreate
import platform.CoreGraphics.CGBitmapContextCreateImage
import platform.CoreGraphics.CGColorSpaceCreateDeviceGray
import platform.CoreGraphics.CGColorSpaceCreateDeviceRGB
import platform.CoreGraphics.CGColorSpaceRelease
import platform.CoreGraphics.CGContextDrawImage
import platform.CoreGraphics.CGContextFillRect
import platform.CoreGraphics.CGContextSetGrayFillColor
import platform.CoreGraphics.CGContextSetInterpolationQuality
import platform.CoreGraphics.CGImageAlphaInfo
import platform.CoreGraphics.CGRectGetMinX
import platform.CoreGraphics.CGRectGetMinY
import platform.CoreGraphics.CGInterpolationQuality
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGAffineTransformMakeTranslation
import platform.CoreGraphics.kCGInterpolationNone
import platform.CoreImage.CIContext
import platform.CoreImage.CIDetector
import platform.CoreImage.CIDetectorAccuracy
import platform.CoreImage.CIDetectorAccuracyHigh
import platform.CoreImage.CIDetectorTypeQRCode
import platform.CoreImage.CIFilter
import platform.CoreImage.CIImage
import platform.CoreImage.CIQRCodeFeature
import platform.CoreImage.createCGImage
import platform.CoreImage.filterWithName
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.dataUsingEncoding
import platform.Foundation.setValue
/**
 * [encodeQrMatrix] / [encodeCode128Matrix] / [decodeQrMatrix] 的 iOS 实现：
 * CoreImage 出码、`CIDetector` 读码。
 *
 * ## 这个文件在本机无法编译验证
 *
 * Kotlin/Native 的 iOS target 只在 macOS + Xcode 上编译，开发机是 Windows，本地
 * `:shared:meta...` 只能验 commonMain 的纯净度，验不到这里。下面这些 ObjC 接口签名
 * 是按 Kotlin/Native 的映射规则推的，第一次在 mac 上跑 `:shared:compileKotlinIosArm64`
 * 时大概率要修 import 与参数名（重点怀疑 `CGImageAlphaInfo` / `CGInterpolationQuality`
 * 这类 CF_ENUM 的写法，以及 `CIFilter.setValue(forKey:)` 的 KVC 映射）。**口径不用改**：
 * 容错 M、静区按调用方给的量、只读二维码，这三条与 Android 侧一致。
 *
 * ## 为什么自己补静区
 *
 * CoreImage 的生成器自带多少静区 Apple 没有承诺（随版本变过），所以这里不去判断它：
 * 把生成器输出当"纯符号区"，自己按调用方给的 margin 在四周填白。若生成器自带静区，
 * 总静区会偏大——偏大不影响扫码，偏小才扫不出，这是唯一安全的做法。
 */
// shortcut: iOS 侧条码实现未经编译验证，首次在 mac 上跑 :shared:compileKotlinIosArm64 时修正, 到 P3 接真机验证时删掉这行

internal actual fun encodeQrMatrix(
    content: String,
    sizePx: Int,
    marginModules: Int,
): BarcodeMatrix {
    val symbol = generatorImage("CIQRCodeGenerator", content)
    // CIQRCodeGenerator 的输出是 1 点 = 1 模块，所以输出宽度就是模块数：
    // 用它把"模块数"换算成像素，静区才能按 zxing 的模块口径留。
    // shortcut: 这条"1 点 = 1 模块"是经验值，Apple 没写进文档；若 mac 上量出不是 1:1，
    // 把 modules 换成实际模块数即可（静区偏大仍可扫，偏小才出问题）。
    val modules = symbol.extent.useContents { size.width }
    val modulePx = sizePx.toDouble() / (modules + 2.0 * marginModules)
    return rasterizeGray(symbol, sizePx, sizePx, modulePx * marginModules, modulePx * marginModules)
}

internal actual fun encodeCode128Matrix(
    content: String,
    widthPx: Int,
    heightPx: Int,
    marginModules: Int,
): BarcodeMatrix {
    val symbol = generatorImage("CICode128BarcodeGenerator", content)
    // 一维码的 margin 是像素口径（zxing 对 CODE_128 就是这么算的），照搬原值；
    // 垂直方向 zxing 不留白，这里也不留——条码画满整高，扫码枪更好对。
    return rasterizeGray(symbol, widthPx, heightPx, marginModules.toDouble(), 0.0)
}

internal actual fun decodeQrMatrix(
    pixels: IntArray,
    width: Int,
    height: Int,
    tryHarder: Boolean,
): String? {
    // 尺寸与像素长度对不上就先挡掉：下面要按 width*height 写满一块 RGBA 缓冲
    if (width <= 0 || height <= 0 || pixels.size < width * height) return null
    // tryHarder 在 iOS 侧没有对应开关：CIDetector 固定一套高精度参数，忽略即可。
    return memScoped {
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
            CGColorSpaceRelease(colorSpace)
            return@memScoped null
        }
        val cgImage = CGBitmapContextCreateImage(bitmap)
        // 上下文与图像都已持有色彩空间，这里可以还回去了（GC 管不了 C 的 Create 规则）
        CGColorSpaceRelease(colorSpace)
        if (cgImage == null) return@memScoped null
        // CGImage 的像素序是自上而下的，CIImage 由它构造时原点自动落到左下，
        // 角度没有偏差，不需要再翻一次。
        val image = CIImage.imageWithCGImage(cgImage)
        val detector = CIDetector.detectorOfType(
            CIDetectorTypeQRCode,
            context = null,
            options = mapOf(CIDetectorAccuracy to CIDetectorAccuracyHigh),
        ) ?: return@memScoped null
        detector.featuresInImage(image)
            .firstNotNullOfOrNull { (it as? CIQRCodeFeature)?.messageString }
    }
}

/**
 * CoreImage 生成器的公共前半段：建 filter、喂内容、固定参数、平移回原点。
 *
 * 平移是防生成器的 extent 不从 (0,0) 起——后面摆放只看尺寸，留个非零原点只会让
 * 图像偏出去。
 */
private fun generatorImage(filterName: String, content: String): CIImage {
    // `CIFilter(name:)` 在 K/N 里没有对应构造器（只有 `CIFilter()` 与 `CIFilter(coder:)`），
    // 要用 ObjC 的类工厂方法 `+filterWithName:`，它映射成同名的顶层函数，需单独 import。
    val filter = CIFilter.filterWithName(filterName) ?: error("CoreImage 没有 $filterName")
    filter.setValue(content.toNSData(), forKey = "inputMessage")
    if (filterName == "CIQRCodeGenerator") {
        // 容错 M（15%）：与 Android 侧 zxing 的 ErrorCorrectionLevel.M 同一档
        filter.setValue("M", forKey = "inputCorrectionLevel")
    }
    // `outputImage` 是 CIFilter 的成员属性（直接取即可——之前报「未解析」是因为
    // `CIFilter(name:)` 那一步失败、filter 成了错误类型，属于级联错）
    val output = filter.outputImage ?: error("$filterName 编不出这段内容")
    // `CGRect` 是 C 结构体，`origin` 是嵌在里面的另一个结构体，取值要走 `CGRectGetMinX/Y`
    // （拿 CValue<CGRect> 直接 `.useContents { origin.x }` 在 Kotlin/Native 上解析不了）。
    val dx = CGRectGetMinX(output.extent)
    val dy = CGRectGetMinY(output.extent)
    return output.imageByApplyingTransform(CGAffineTransformMakeTranslation(-dx, -dy))
}

/**
 * CIImage → 灰度 [BarcodeMatrix]：先铺一块白底灰度位图，再把符号按 margin 缩放着上去，
 * 最后逐像素阈值化成黑白。
 *
 * 缩放走 `CGContextDrawImage` 而不是 `CIImage.imageByApplyingTransform`：后者在非整数
 * 倍率下会做双线性插值，模块边界糊成灰；位图上下文里把插值质量关掉，模块才是硬边。
 */
private fun rasterizeGray(
    symbol: CIImage,
    widthPx: Int,
    heightPx: Int,
    marginX: Double,
    marginY: Double,
): BarcodeMatrix = memScoped {
    val ciContext = CIContext()
    val image = ciContext.createCGImage(symbol, fromRect = symbol.extent)
        ?: error("CoreImage 没能把条码光栅化")
    val colorSpace = CGColorSpaceCreateDeviceGray()
    val buffer = allocArray<UByteVar>(widthPx * heightPx)
    val bitmap = CGBitmapContextCreate(
        data = buffer,
        width = widthPx.toULong(),
        height = heightPx.toULong(),
        bitsPerComponent = 8uL,
        bytesPerRow = widthPx.toULong(),
        space = colorSpace,
        bitmapInfo = CGImageAlphaInfo.kCGImageAlphaNone.value,
    ) ?: error("灰度位图上下文建不出来")
    // 底色先填白：生成器只画符号区，静区靠这一层白
    CGContextSetGrayFillColor(bitmap, 1.0, 1.0)
    CGContextFillRect(bitmap, CGRectMake(0.0, 0.0, widthPx.toDouble(), heightPx.toDouble()))
    // 常量在 K/N 里是 `platform.CoreGraphics` 的顶层 val，不是 enum 的成员
    CGContextSetInterpolationQuality(bitmap, kCGInterpolationNone)
    CGContextDrawImage(
        bitmap,
        CGRectMake(
            marginX,
            marginY,
            widthPx.toDouble() - 2 * marginX,
            heightPx.toDouble() - 2 * marginY,
        ),
        image,
    )
    CGColorSpaceRelease(colorSpace)
    // 位图上下文里 Quartz 的 y 轴朝上（y=0 落在内存最后一行），所以内存第 0 行就是
    // 图像最上面一行：按行顺序直接读即可。这里再拿 height-1-y 翻一次会让整张码上下颠倒。
    BarcodeMatrix(widthPx, heightPx) { x, y -> buffer[y * widthPx + x] < 128u }
}

/** Kotlin 字符串 → 生成器要的 `NSData`（UTF-8，与 Android 侧 zxing 喂 String 等价）。 */
private fun String.toNSData(): NSData? = (this as NSString).dataUsingEncoding(NSUTF8StringEncoding)
