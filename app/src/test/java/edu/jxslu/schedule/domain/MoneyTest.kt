package edu.jxslu.schedule.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 金额值对象（iOS 移植 P-1 第 4 批）：替代 `BigDecimal` 之后，解析与进位口径必须与
 * 原来的 `movePointRight(2).setScale(0, HALF_UP)` 完全一致，否则账单会差一分钱。
 */
class MoneyTest {

    @Test
    fun `解析整数与小数`() {
        assertEquals(1234L, Money.parse("12.34")?.fen)
        assertEquals(1200L, Money.parse("12")?.fen)
        assertEquals(1230L, Money.parse("12.3")?.fen)
        assertEquals(0L, Money.parse("0.00")?.fen)
        assertEquals(1200L, Money.parse("12.")?.fen)
        assertEquals(1234L, Money.parse(" 12.34 ")?.fen)
    }

    @Test
    fun `第三位起按 HALF_UP 进退`() {
        assertEquals(1235L, Money.parse("12.345")?.fen)
        assertEquals(1234L, Money.parse("12.344")?.fen)
        assertEquals(1L, Money.parse("0.005")?.fen)
        assertEquals(-1L, Money.parse("-0.005")?.fen)
    }

    @Test
    fun `脏值返回 null`() {
        assertNull(Money.parse(null))
        assertNull(Money.parse(""))
        assertNull(Money.parse("   "))
        assertNull(Money.parse("abc"))
        assertNull(Money.parse("."))
        assertNull(Money.parse("1.2.3"))
        assertNull(Money.parse("1e5"))
        assertNull(Money.parse("1234567890123"))
    }

    @Test
    fun `文案两位小数且负号跟数字`() {
        assertEquals("0.00", Money.ZERO.text())
        assertEquals("0.01", Money.ofFen(1).text())
        assertEquals("12.34", Money.ofFen(1234).text())
        assertEquals("-1.50", Money.ofFen(-150).text())
        assertEquals("1234567.89", Money.ofFen(123_456_789).text())
    }

    @Test
    fun `厘折算到分`() {
        assertEquals(123L, Money.ofLi(1234).fen)
        assertEquals(124L, Money.ofLi(1235).fen)
        assertEquals(0L, Money.ofLi(0).fen)
        assertEquals(-10L, Money.ofLi(-100).fen)
    }

    @Test
    fun `加减与比较`() {
        val a = Money.parse("0.12")!!
        val b = Money.parse("0.11")!!
        // KDoc 里举的就是这一对：0.12 - 0.11 用 Double 得 0.0099…，整数分是干净的 1 分
        assertEquals(1L, (a - b).fen)
        assertEquals(23L, (a + b).fen)
        assertTrue(a > b)
        assertEquals(Money.ZERO, (b - a).coerceAtLeast(Money.ZERO))
    }
}
