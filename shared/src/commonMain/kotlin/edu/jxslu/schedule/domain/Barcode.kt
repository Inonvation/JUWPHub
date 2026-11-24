package edu.jxslu.schedule.domain

/**
 * 条码 / 二维码编解码的平台缝（iOS 移植，2026-11-24）。
 *
 * 全仓库只有这三个动作离不开「会画码 / 会读码」的第三方库：zxing 在 JVM 侧够用，
 * Kotlin/Native 上没有它，iOS 侧走 CoreImage / `CIDetector`。commonMain 只认
 * [BarcodeMatrix] 与字符串，平台库留在 androidMain / iosMain 的 `actual` 里。
 *
 * 参数口径照搬改造前的 zxing 调用，**不要"顺手统一"**：
 * - 二维码容错级别固定 M（15%），Android 侧写在 `actual` 里；
 * - `marginModules` 就是 zxing 的 `EncodeHintType.MARGIN` 原值——二维码按**模块数**算，
 *   一维码按**像素**算，这是 zxing 自己的两套口径。
 *
 * 三个函数都是 `internal`：对外暴露的是 [EbikeQr.qrMatrix] / [EbikeQr.decodeFromPixels] /
 * [YktPayCode.matrices]，平台缝不该成为新 API。
 */

/**
 * 生成二维码位阵：[sizePx] 正方形，四周留 [marginModules] 个模块的静区，容错级别 M。
 *
 * 编不出（内容超出容量等）就让平台实现抛——改造前 zxing 也是抛 `WriterException`，
 * 上层没有兜底分支，静默返回一张白图反而会让用户拿着扫不开的码去试。
 */
internal expect fun encodeQrMatrix(content: String, sizePx: Int, marginModules: Int): BarcodeMatrix

/** 生成 Code128 一维码位阵：[widthPx] × [heightPx]，[marginModules] 按像素口径（见文件头）。 */
internal expect fun encodeCode128Matrix(
    content: String,
    widthPx: Int,
    heightPx: Int,
    marginModules: Int,
): BarcodeMatrix

/**
 * ARGB 像素 → 二维码原文；认不出返回 null，**不许抛**。
 *
 * [pixels] 是 `0xFF000000`（黑）/ `0xFFFFFFFF`（白）的 ARGB，长度不足 `width * height`
 * 或尺寸非正（相册里挑到脏数据）也返回 null——像素数组的越界读不能带进平台解码器。
 * [tryHarder] 对应 zxing 的 `DecodeHintType.TRY_HARDER`：相册只解一次，值得多花时间。
 */
internal expect fun decodeQrMatrix(
    pixels: IntArray,
    width: Int,
    height: Int,
    tryHarder: Boolean,
): String?
