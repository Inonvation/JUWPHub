package edu.jxslu.schedule.domain

import java.math.BigDecimal
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class MoneyFormatTest {

    @Test
    fun fenToYuan() {
        assertEquals("¥0.00", MoneyFormat.fromFen(0))
        assertEquals("¥0.01", MoneyFormat.fromFen(1))
        assertEquals("¥12.34", MoneyFormat.fromFen(1234))
        // 负数是退款/冲正，符号留在 ¥ 后（与合并前 `¥%.2f` 一致）
        assertEquals("¥-1.50", MoneyFormat.fromFen(-150))
        // 不加千分位：与合并前所有实现一致，避免改变既有文案长度
        assertEquals("¥1234567.89", MoneyFormat.fromFen(123_456_789))
    }

    @Test
    fun liToYuan() {
        // 趣智 consumeMoney 是厘：1234 厘 = 1.234 元，舍到分
        assertEquals("¥1.23", MoneyFormat.fromLi(1234))
        assertEquals("¥0.00", MoneyFormat.fromLi(0))
        assertEquals("¥1.24", MoneyFormat.fromLi(1235))
        assertEquals("¥-0.10", MoneyFormat.fromLi(-100))
    }

    @Test
    fun yuanDouble() {
        assertEquals("¥12.34", MoneyFormat.fromYuan(12.34))
        assertEquals("¥0.00", MoneyFormat.fromYuan(0.0))
        assertEquals("¥-1.50", MoneyFormat.fromYuan(-1.5))
    }

    @Test
    fun yuanBigDecimal() {
        // 12.345 在 Decimal 下能准确舍入；Double 下可能是 12.34（见 yuanText 的 KDoc）
        assertEquals("¥12.35", MoneyFormat.fromYuan(BigDecimal("12.345")))
        assertEquals("¥12.34", MoneyFormat.fromYuan(BigDecimal("12.344")))
        assertEquals("¥0.00", MoneyFormat.fromYuan(BigDecimal.ZERO))
    }

    @Test
    fun plainTextHasNoCurrencySymbol() {
        assertEquals("12.34", MoneyFormat.fenText(1234))
        assertEquals("1.23", MoneyFormat.liText(1234))
        assertEquals("12.34", MoneyFormat.yuanText(12.34))
    }

    /**
     * 默认 Locale 不能影响金额：合并前 `"%.2f".format()` 走默认 Locale，
     * 阿拉伯语环境会输出阿拉伯数字、德语环境会把小数点换成逗号。
     */
    @Test
    fun localeDoesNotChangeDigitsOrDecimalPoint() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("ar-EG"))
            assertEquals("¥12.34", MoneyFormat.fromFen(1234))
            assertEquals("¥1.23", MoneyFormat.fromLi(1234))
            assertEquals("¥12.34", MoneyFormat.fromYuan(12.34))

            Locale.setDefault(Locale.forLanguageTag("de-DE"))
            assertEquals("¥12.34", MoneyFormat.fromFen(1234))
            assertEquals("¥12.34", MoneyFormat.fromYuan(12.34))
        } finally {
            Locale.setDefault(original)
        }
    }
}
