package edu.jxslu.schedule.domain

import java.util.Locale
import kotlin.math.abs

/**
 * 定宽文本格式化（iOS 移植 P-1 第 3 批，2026-11-24）。
 *
 * `String.format` 是 JVM 专有 API，Kotlin/Native 上没有对应物；这类调用原先散在十来个文件里。
 * 纯整数的三种（补零、`HH:mm`、两位十六进制）在这里改成纯 Kotlin 实现，跨平台无差异。
 *
 * [decimals] 仍走 `String.format`：**不要**在这里手写浮点舍入——`%.1f` / `%.2f` 的边界值
 * （12.345 这种）手写实现会和原显示差一分钱，详见 [MoneyFormat.yuanText] 的说明。
 * 它保留在原地的目的是把十来个调用点收成一个；P1 拆模块时这一处改成 `expect/actual`
 * （Android 用 `String.format`，iOS 用 `NSString(format:)`），调用方不用动。
 */
object TextFmt {

    /** 十进制补零到 [width] 位；负数把负号放最前（负数的补零口径与 `%0Nd` 不同，调用方都是非负）。 */
    fun pad(value: Long, width: Int = 2): String {
        val digits = abs(value).toString().padStart(width, '0')
        return if (value < 0) "-$digits" else digits
    }

    fun pad(value: Int, width: Int = 2): String = pad(value.toLong(), width)

    /** 当天第几分钟 → `HH:mm`（调用方负责先归一化到 0..1439）。 */
    fun clock(minuteOfDay: Int): String = "${pad(minuteOfDay / 60)}:${pad(minuteOfDay % 60)}"

    /** 两位小写十六进制（协议功能码等），等价 `%02x`。 */
    fun hex2(value: Int): String = value.toString(16).padStart(2, '0')

    /** 定点小数，等价 `String.format(Locale.ROOT, "%.<places>f", value)`。 */
    fun decimals(value: Double, places: Int): String =
        String.format(Locale.ROOT, "%.${places}f", value)
}
