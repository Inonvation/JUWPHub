package edu.jxslu.schedule.domain

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 定宽格式化的回归网（iOS 移植 P-1 第 3 批，2026-11-24）。
 *
 * 这批改动的唯一验收标准是「换成纯 Kotlin 实现之后输出一字不差」，所以对照断言直接
 * 拿**旧的 `String.format` 写法**当参照系，而不是写死字面量——写死字面量只能证明结果没变，
 * 证明不了口径没变。
 */
class TextFmtTest {

    @Test
    fun `补零与 %0Nd 一致`() {
        for (v in listOf(0, 1, 9, 10, 99, 615, 2026)) {
            assertEquals("%02d".format(Locale.ROOT, v), TextFmt.pad(v))
            assertEquals("%04d".format(Locale.ROOT, v), TextFmt.pad(v, 4))
        }
    }

    @Test
    fun `HH-mm 与旧写法一致`() {
        for (m in listOf(0, 1, 59, 60, 61, 615, 1439)) {
            assertEquals("%02d:%02d".format(Locale.ROOT, m / 60, m % 60), TextFmt.clock(m))
        }
    }

    @Test
    fun `两位十六进制与 %02x 一致`() {
        for (v in listOf(0, 1, 10, 15, 16, 127, 128, 255)) {
            assertEquals("%02x".format(Locale.ROOT, v), TextFmt.hex2(v))
        }
    }

    @Test
    fun `定点小数与 printf 一致`() {
        val samples = listOf(0.0, 0.5, 1.0, 12.34, 12.345, 1.005, 999.999, 1.5, 1023.0 / 1024)
        for (v in samples) {
            assertEquals("%.2f".format(Locale.ROOT, v), TextFmt.decimals(v, 2))
            assertEquals("%.1f".format(Locale.ROOT, v), TextFmt.decimals(v, 1))
            assertEquals("%.3f".format(Locale.ROOT, v), TextFmt.decimals(v, 3))
        }
    }

    @Test
    fun `体积文案各档位`() {
        assertEquals("512 B", SizeText.format(512))
        assertEquals("2 KB", SizeText.format(2048))
        assertEquals("1.5 MB", SizeText.format(1572864))
    }
}
