package edu.jxslu.schedule.domain

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.ReaderException
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.BitMatrix
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.oned.Code128Writer
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * [encodeQrMatrix] / [encodeCode128Matrix] / [decodeQrMatrix] 的 Android 实现：
 * 直接落到 zxing（`com.google.zxing:core`）。
 *
 * 这三段就是 `EbikeQr.qrMatrix` / `YktPayCode.matrices` / `EbikeQr.decodeFromPixels`
 * 改造前的原始调用，参数一行都不许变（容错 M、margin 原值、`TRY_HARDER`、
 * 候选格式只留 `QR_CODE`）：出码参数错了表现为"扫不出来"，解码参数错了表现为
 * "相册解不出"，两者都只在真机扫码时才暴露，单测拦不住。
 */
internal actual fun encodeQrMatrix(content: String, sizePx: Int, marginModules: Int): BarcodeMatrix =
    QRCodeWriter().encode(
        content,
        BarcodeFormat.QR_CODE,
        sizePx,
        sizePx,
        mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to marginModules,
        ),
    ).toBarcodeMatrix()

internal actual fun encodeCode128Matrix(
    content: String,
    widthPx: Int,
    heightPx: Int,
    marginModules: Int,
): BarcodeMatrix =
    Code128Writer().encode(
        content,
        BarcodeFormat.CODE_128,
        widthPx,
        heightPx,
        mapOf(EncodeHintType.MARGIN to marginModules),
    ).toBarcodeMatrix()

internal actual fun decodeQrMatrix(
    pixels: IntArray,
    width: Int,
    height: Int,
    tryHarder: Boolean,
): String? {
    // 尺寸与像素长度对不上就先挡掉：RGBLuminanceSource 会按 width*height 读数组
    if (width <= 0 || height <= 0 || pixels.size < width * height) return null
    val binary = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(width, height, pixels)))
    val reader = MultiFormatReader().apply {
        setHints(
            mapOf(
                DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                DecodeHintType.TRY_HARDER to tryHarder,
            ),
        )
    }
    return try {
        reader.decodeWithState(binary).text
    } catch (_: ReaderException) {
        null
    }
}

/** zxing 位阵 → 中性位阵（`BitMatrix.get(x, y)` 的坐标序与 [BarcodeMatrix] 一致）。 */
private fun BitMatrix.toBarcodeMatrix(): BarcodeMatrix {
    val source = this
    return BarcodeMatrix(width, height) { x, y -> source[x, y] }
}
