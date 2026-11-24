package edu.jxslu.schedule.domain

// commonMain 不默认导入 `kotlin.jvm.*`（JVM 模块才会），所以 `@JvmInline` 必须显式 import。
// 别把它当成「多余 import」删掉——删掉后 JVM 目标会报「Value classes without '@JvmInline'」。
import kotlin.jvm.JvmInline

/**
 * 金额，内部是整数「分」（iOS 移植 P-1 第 4 批，2026-11-24）。
 *
 * 换掉 `java.math.BigDecimal`——它在 Kotlin/Native 上没有实现。金额只到两位小数，
 * 用「分」做整数运算既精确又跨平台，不必自建十进制库。
 *
 * 口径与 [MoneyFormat] 一致：
 * - 解析按 `HALF_UP` 折到分（与原 `BigDecimal.movePointRight(2).setScale(0, HALF_UP)` 相同）；
 * - 负数表示退款/冲正，符号跟着数字走，`¥` 由 [MoneyFormat] 拼在最前；
 * - 不保留千分位、不做本地化。
 *
 * 不做溢出防护：金额量级离 `Long` 上限极远，[parse] 按位数拒绝脏输入。
 */
@JvmInline
value class Money(val fen: Long) : Comparable<Money> {

    operator fun plus(other: Money): Money = Money(fen + other.fen)

    operator fun minus(other: Money): Money = Money(fen - other.fen)

    override fun compareTo(other: Money): Int = fen.compareTo(other.fen)

    fun coerceAtLeast(minimum: Money): Money = if (fen < minimum.fen) minimum else this

    /** 「12.34」：两位小数、无货币符号、无千分位；负数形如 `-1.50`。 */
    fun text(): String {
        val negative = fen < 0
        val absFen = if (negative) -fen else fen
        return buildString {
            if (negative) append('-')
            append(absFen / 100)
            append('.')
            append(TextFmt.pad((absFen % 100).toInt()))
        }
    }

    companion object {

        val ZERO: Money = Money(0)

        fun ofFen(fen: Long): Money = Money(fen)

        /** 厘 → 分（`HALF_UP`，趣智 `consumeMoney` 口径）：1234 厘 → 1.23 元。 */
        fun ofLi(li: Long): Money {
            val abs = if (li < 0) -li else li
            val fen = (abs + 5) / 10
            return Money(if (li < 0) -fen else fen)
        }

        /** `"12.34"` → 1234；空串、脏值、超长输入返回 null。 */
        fun parse(raw: String?): Money? {
            val text = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            val negative = text.startsWith('-')
            val body = text.removePrefix("-").removePrefix("+")
            val dot = body.indexOf('.')
            val integerPart = if (dot < 0) body else body.substring(0, dot)
            val fractionPart = if (dot < 0) "" else body.substring(dot + 1)
            if (integerPart.length > 12 || fractionPart.length > 6) return null
            if (integerPart.any { it !in '0'..'9' }) return null
            if (fractionPart.any { it !in '0'..'9' }) return null
            if (integerPart.isEmpty() && fractionPart.isEmpty()) return null
            val yuan = if (integerPart.isEmpty()) 0L else integerPart.toLongOrNull() ?: return null
            val cents = if (fractionPart.isEmpty()) {
                0L
            } else {
                fractionPart.take(2).padEnd(2, '0').toLong()
            }
            val thirdDigit = fractionPart.getOrNull(2)?.let { it - '0' } ?: 0
            val fen = yuan * 100 + cents + if (thirdDigit >= 5) 1 else 0
            return Money(if (negative) -fen else fen)
        }
    }
}
