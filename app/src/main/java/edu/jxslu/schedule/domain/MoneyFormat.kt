package edu.jxslu.schedule.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

/**
 * 金额文案的唯一格式化入口（2026-11-13，审计收尾）。
 *
 * 合并前同一语义散在 20 余个文件里，写法有 `"¥%.2f".format(…)`、`String.format(Locale.US, …)`、
 * `BigDecimal.movePointLeft`；单位有 **分 / 厘 / 元** 三种，调用方各自除 100 或 1000；
 * Locale 有的默认、有的 US。两个真实后果：
 *
 * 1. 系统语言切成阿拉伯语等 Locale 时，默认 `%.2f` 会输出非 ASCII 数字或逗号小数点，
 *    金额直接看不懂，而同一屏里另一处用 Locale.US 的金额又正常。
 * 2. 厘和分只差一个 0，改文案时抄错一处就是十倍金额（趣智的「厘」口径见 §4.30）。
 *
 * 口径：
 * - 一律 `Locale.ROOT`、两位小数、`HALF_UP`、**不加千分位**（与合并前所有实现一致，
 *   加了会改变既有截图与文案长度，不属于本次收口）。
 * - 入参单位写进函数名：`fen` = 分、`li` = 厘、`yuan` = 元；调用方不再自己除。
 * - 带 `¥` 的版本给用户看；`*Text` 版本给「已有 ¥ 前缀」或小组件这类自己拼的场景。
 */
object MoneyFormat {

    /** 分 → 「¥12.34」。 */
    fun fromFen(fen: Long): String = "¥" + fenText(fen)

    /** 厘 → 「¥1.23」（趣智 `consumeMoney` 口径）。 */
    fun fromLi(milli: Long): String = "¥" + liText(milli)

    /** 元（Double）→ 「¥12.34」。 */
    fun fromYuan(yuan: Double): String = "¥" + yuanText(yuan)

    /** 元（BigDecimal）→ 「¥12.34」；趣智账单合计与开水支付构成用这条。 */
    fun fromYuan(yuan: BigDecimal): String = "¥" + yuanText(yuan)

    /** 分 → 「12.34」（无货币符号）。 */
    fun fenText(fen: Long): String =
        BigDecimal.valueOf(fen).movePointLeft(2).setScale(2, RoundingMode.HALF_UP).toPlainString()

    /** 厘 → 「1.23」（无货币符号）。 */
    fun liText(milli: Long): String =
        BigDecimal.valueOf(milli).movePointLeft(3).setScale(2, RoundingMode.HALF_UP).toPlainString()

    /**
     * 元（Double）→ 「12.34」。
     *
     * 保留 `%.2f` 的二进制浮点语义，不改成 `BigDecimal.valueOf`：后者按 `Double.toString`
     * 的十进制字面量舍入，像 12.345 这种边界值会和合并前的显示差一分钱。金额来源本身是
     * 分/厘整数时优先用 [fenText] / [liText]（精确）。
     */
    fun yuanText(yuan: Double): String = String.format(Locale.ROOT, "%.2f", yuan)

    /** 元（BigDecimal）→ 「12.34」。 */
    fun yuanText(yuan: BigDecimal): String =
        yuan.setScale(2, RoundingMode.HALF_UP).toPlainString()
}
