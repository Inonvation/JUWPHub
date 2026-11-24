package edu.jxslu.schedule.domain

/**
 * 校园卡付款码的码图矩阵（DESIGN §3.10 / §4.19）。不含平台 API，可测。
 *
 * 网页端（`chunk-fa993c88`）的付款码 = 一条 Code128 一维码 + 一个二维码，
 * 两者内容**都是 20 位数字原样编码**——无前缀、无校验位包装。因此 App 侧只要把
 * 同一串数字分别喂给 [encodeQrMatrix] 与 [encodeCode128Matrix]（Android 是 zxing，
 * iOS 是 CoreImage），扫出来的结果与网页端完全一致。
 */
object YktPayCode {

    /** QR 输出像素边长（同共享单车出码口径，扫码距离近，大了不亏）。 */
    const val QR_SIZE_PX = 720

    /** Code128 输出像素宽度；高度 120px 够扫码枪/相机扫一维码。 */
    const val BARCODE_WIDTH_PX = 720
    const val BARCODE_HEIGHT_PX = 120

    data class Matrices(val qr: BarcodeMatrix, val barcode: BarcodeMatrix)

    /**
     * 生成付款码双矩阵。内容必须是纯数字（服务端口径 20 位）；其他内容交由
     * 上游校验，这里不做格式假设——能与不能编码由平台实现决定。
     */
    fun matrices(code: String): Matrices {
        // 白边口径照搬改造前：QR margin=1（模块数），Code128 margin=2（像素数）——
        // zxing 对一维码的 MARGIN 是按像素算的，别把两者统一成一套。
        val qr = encodeQrMatrix(code, QR_SIZE_PX, marginModules = 1)
        val barcode = encodeCode128Matrix(code, BARCODE_WIDTH_PX, BARCODE_HEIGHT_PX, marginModules = 2)
        return Matrices(qr, barcode)
    }
}
