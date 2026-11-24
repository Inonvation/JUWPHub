package edu.jxslu.schedule.domain

/**
 * 条码 / 二维码位阵（`true` = 黑模块），行主序、构造后不再改动（iOS 移植，2026-11-24）。
 *
 * ## 为什么不用 zxing 的 `BitMatrix`
 *
 * `com.google.zxing.common.BitMatrix` 是 JVM 库类型：Kotlin/Native 上没有这个类，
 * 而 `EbikeQr` / `YktPayCode` 要搬进 `:shared` 的 commonMain（iOS 端也要出同一张码）。
 * commonMain 的门禁是元数据编译，看不见 JDK，classpath 上出现 JVM 库类型就当场失败。
 * 所以这里立一个只依赖 Kotlin 标准库的中性位阵充当两条平台实现与上层之间的唯一契约，
 * zxing 退到 androidMain 的 `actual` 里（见 [encodeQrMatrix] / [decodeQrMatrix]）。
 *
 * 坐标口径与原 `BitMatrix` 一致：`get(x, y)` 的 y 自上而下，第 0 行是码的最上面一行，
 * 与 `Bitmap.setPixel` / 测试里 `matrixPixels` 的像素序对得上。
 */
class BarcodeMatrix(val width: Int, val height: Int, private val bits: BooleanArray) {

    /** 由逐点函数构造（`bit(x, y)` 为 true 即黑）：按行主序拍平进 [bits]。 */
    constructor(width: Int, height: Int, bit: (x: Int, y: Int) -> Boolean) : this(
        width = width,
        height = height,
        bits = BooleanArray(width * height) { index -> bit(index % width, index / width) },
    )

    /**
     * 取 `(x, y)` 是否为黑。
     *
     * 直接按 `y * width + x` 下标取，不做边界判断：渲染方（`CodeBitmaps`）与测试都是
     * 按 `0 until width` / `0 until height` 自己算的合法坐标，越界属于编程错误，
     * 让它当场抛比悄悄返回白点好定位。
     */
    operator fun get(x: Int, y: Int): Boolean = bits[y * width + x]
}
