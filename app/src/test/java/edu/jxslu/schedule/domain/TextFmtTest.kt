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

    /**
     * 定点小数：对着 `String.format` 全量比对。
     *
     * 这条测试是 iOS 侧舍入行为的**唯一证据**（`:shared` 的 commonMain 在 Kotlin/Native 上
     * 无法在 Windows 编译，更跑不了）。2026-11-24 就是因为缺它，装了真机才发现 iOS 走的是
     * 十进制 HALF_EVEN、Android 走的是最短表示 HALF_UP 的差异（见 [formatFixed] 的 KDoc）。
     * 现在实现只有一份，本地跑通即等于两端一致。
     */
    @Test
    fun `定点小数与 printf 全量一致`() {
        val samples = buildList {
            addAll(
                listOf(
                    0.0, -0.0, 0.5, -0.5, 1.0, -1.0, 12.34, 12.345, 1.045, 2.675, 0.05, 8.835,
                    1.005, 999.999, 1.5, 1023.0 / 1024, 0.1, 100.0, 123456.789,
                    1e7, 1.0e-5, 1.0 / 3.0, 2.0 / 3.0, 1e-7,
                ),
            )
            // 伪随机（固定种子，可复现）：覆盖 [0,1) 与 [0,1e6) 两档，模拟金额 / 距离 / 体积
            var seed = 20261124L
            repeat(400) {
                seed = seed * 6364136223846793005L + 1442695040888963407L
                val unit = (seed ushr 11).toDouble() / (1L shl 53).toDouble()
                add(unit * 1_000_000.0)
                add(unit * 10.0)
            }
        }
        for (v in samples) {
            for (p in 0..4) {
                assertEquals("%.${p}f".format(Locale.ROOT, v), TextFmt.decimals(v, p))
            }
        }
    }

    @Test
    fun `体积文案各档位`() {
        assertEquals("512 B", SizeText.format(512))
        assertEquals("2 KB", SizeText.format(2048))
        assertEquals("1.5 MB", SizeText.format(1572864))
    }
}
