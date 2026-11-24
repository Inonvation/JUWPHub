package edu.jxslu.schedule.domain

import kotlin.math.abs

/**
 * 定点小数格式化（纯 Kotlin，两端同一份实现）。
 *
 * ## 为什么不再是 expect/actual
 *
 * 2026-11-24 的 iOS 移植里这条缝原本是 `expect`：Android 用 `String.format(Locale.ROOT, "%.2f", v)`、
 * iOS 用 `NSString(format:)`，当时的假设是「两个平台都走 C 的 printf 家族，舍入口径一致」。
 * 装到 iPhone 上一测就破了 —— 同一批数值两端给出不同结果：
 *
 * | 值 | JDK 17 `%.2f` | `NSString(format:)` |
 * |---|---|---|
 * | 12.345 | 12.35 | 12.34 |
 * | 1.045 | 1.05 | 1.04 |
 * | 2.675 | 2.68 | 2.68 |
 * | 0.05（`%.1f`） | 0.1 | 0.0 |
 * | 8.835 | 8.84 | 8.84 |
 *
 * 差异的根因：Java 的 `Formatter` 先取 double 的**最短往返十进制表示**（`12.345`），
 * 再对它做 HALF_UP；Foundation 的 `NSString(format:)` 走的是**十进制 HALF_EVEN**
 * （`0.05 → 0.0`、`2.675 → 2.68` 正是 half-even 的特征）。两者在「末位恰为 5」时分叉。
 *
 * 结论：金额显示不能有两个平台口径，也不能靠「CI 编译通过」证明一致。
 * 改成公共代码里的一份实现，用 [TextFmtTest] 对着 `String.format` 全量比对 ——
 * 那个测试在 JVM 上能跑，等于把 iOS 侧的舍入行为也钉死了。
 *
 * ## 口径（与 Java `%f` 逐位一致）
 *
 * 1. 取 `Double.toString()` 的最短往返表示，拆成「数字串 + 十进制指数」；
 * 2. 按十进制做 HALF_UP 进位（末位 ≥5 进一），全程整数串运算，**不碰浮点**；
 * 3. 固定输出 [places] 位小数（不足补 0），`places = 0` 时不出小数点。
 *
 * 这条口径能覆盖金额 / 百分比 / 距离 / 体积所有调用点：值域 0–10⁶、位数 ≤ 4。
 * 若将来要格式化 `1e300` 这类值，先跑一遍 [TextFmtTest] 再改。
 */
internal fun formatFixed(value: Double, places: Int): String {
    require(places >= 0) { "小数位数不能为负：$places" }
    if (value.isNaN()) return "NaN"
    if (value.isInfinite()) return if (value > 0) "Infinity" else "-Infinity"

    // -0.0 要保留负号：Java 的 %.2f 对 -0.0 给 "-0.00"
    val negative = value < 0.0 || (value == 0.0 && value.toRawBits() < 0L)
    val repr = abs(value).toString()

    // 拆 "12.345" / "1.0E7" / "1.0E-5"：value = digits × 10^scale
    val eIndex = repr.indexOf('E')
    val exponent = if (eIndex >= 0) repr.substring(eIndex + 1).toInt() else 0
    val mantissa = if (eIndex >= 0) repr.substring(0, eIndex) else repr
    val dotIndex = mantissa.indexOf('.')
    val intPart = if (dotIndex >= 0) mantissa.substring(0, dotIndex) else mantissa
    val fracPart = if (dotIndex >= 0) mantissa.substring(dotIndex + 1) else ""
    val digits = (intPart + fracPart).trimStart('0')
    val scale = exponent - fracPart.length

    val scaled = when {
        // 0.0：最短表示是 "0.0"，去掉前导零后为空
        digits.isEmpty() -> "0"
        // value × 10^places 已经是整数：只需补零，不可能进位
        scale + places >= 0 -> digits + "0".repeat(scale + places)
        else -> {
            val drop = -(scale + places)
            when {
                // 被丢掉的位数比有效数字还多：整数部分恒为 0，最高丢弃位也是 0，不进位
                drop > digits.length -> "0"
                // 恰好全部丢掉：只看首位是否满 5
                drop == digits.length -> if (digits[0] >= '5') "1" else "0"
                else -> {
                    val kept = digits.substring(0, digits.length - drop)
                    val firstDropped = digits[digits.length - drop]
                    if (firstDropped >= '5') incrementDecimal(kept) else kept
                }
            }
        }
    }
    return if (negative) "-${buildFixed(scaled, places)}" else buildFixed(scaled, places)
}

/** 把整数数字串按 [places] 位小数插小数点；不足左侧补 0。 */
private fun buildFixed(intDigits: String, places: Int): String {
    if (places == 0) return intDigits
    val padded = intDigits.padStart(places + 1, '0')
    val cut = padded.length - places
    return padded.substring(0, cut) + "." + padded.substring(cut)
}

/** 十进制数字串 +1，处理连续 9 的进位（纯字符串，避免任何精度问题）。 */
private fun incrementDecimal(digits: String): String {
    val chars = digits.toCharArray()
    var i = chars.size - 1
    while (i >= 0) {
        if (chars[i] == '9') {
            chars[i] = '0'
            i--
        } else {
            chars[i] = chars[i] + 1
            return chars.concatToString()
        }
    }
    return "1" + chars.concatToString()
}
