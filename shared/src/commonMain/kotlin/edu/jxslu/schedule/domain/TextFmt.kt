package edu.jxslu.schedule.domain

import kotlin.math.abs

/**
 * 定宽文本格式化（iOS 移植 P-1 第 3 批，2026-11-24；P0 起在 `:shared` 的 commonMain）。
 *
 * `String.format` 是 JVM 专有 API，Kotlin/Native 上没有对应物；这类调用原先散在十来个文件里。
 * 纯整数的三种（补零、`HH:mm`、两位十六进制）在这里改成纯 Kotlin 实现，跨平台无差异。
 *
 * [decimals] 走 [formatFixed]：**不要**在这里另写浮点舍入，也不要把 [formatFixed] 改回
 * `expect/actual`。按平台各调各的 printf 试过了，iOS 的 `NSString(format:)` 是十进制 HALF_EVEN，
 * 与 Java 的 `%f`（最短表示 HALF_UP）在「末位恰为 5」时分叉，装真机就露。现在是一份纯 Kotlin
 * 实现 + 对着 `String.format` 的全量单测，详见 [formatFixed] 的 KDoc。
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
    fun decimals(value: Double, places: Int): String = formatFixed(value, places)

    /**
     * 去掉无意义的尾部 0：`3.0 → 3`、`84.50 → 84.5`、`84.0 → 84`。
     *
     * 学分 / 学时这类数在教务里有整数也有两位小数，页面上「79 学分」比「79.00 学分」好读。
     * 舍入口径走 [decimals]（HALF_UP，与 Java 的 `%f` 一致），不在这里另写一套。
     *
     * 收在这里而不是各页各写一份：学业完成情况 / 培养方案 / 成绩三页原本各有一份
     * 逐字相同的 `trimNum`（2026-11-25 合并）。
     */
    fun trim(value: Double, places: Int = 2): String =
        decimals(value, places).trimEnd('0').trimEnd('.').ifEmpty { "0" }
}
