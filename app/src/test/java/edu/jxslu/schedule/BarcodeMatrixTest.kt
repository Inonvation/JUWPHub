package edu.jxslu.schedule

import edu.jxslu.schedule.domain.BarcodeMatrix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 中性位阵 [BarcodeMatrix] 的坐标契约（iOS 移植，2026-11-24）。
 *
 * 只有一条不变量值得钉住：**行主序、第 0 行是码的最上面一行**。出码两端（Android 的
 * zxing 转换、iOS 的灰度缓冲按行读）各自实现，方向错了不会编译失败，只会让整张码
 * 上下颠倒、扫码器认不出——真机上发现问题时的排查成本远高于这里三行断言。
 */
class BarcodeMatrixTest {

    @Test
    fun `逐点构造按行主序落位`() {
        // 只有 (1, 0) 与 (0, 1) 为黑：前者在第 0 行，后者在第 1 行
        val matrix = BarcodeMatrix(width = 2, height = 2) { x, y -> x == 1 && y == 0 }
        assertTrue(matrix.get(1, 0))
        assertFalse(matrix.get(0, 0))
        assertFalse(matrix.get(0, 1))
        assertFalse(matrix.get(1, 1))
    }

    @Test
    fun `原始数组按行主序解释`() {
        // 数组顺序 = 第 0 行从左到右、第 1 行……；换成列主序会在这里翻车
        val matrix = BarcodeMatrix(3, 2, booleanArrayOf(true, false, false, false, true, false))
        assertEquals(3, matrix.width)
        assertEquals(2, matrix.height)
        assertTrue(matrix[0, 0])
        assertTrue(matrix[1, 1])
        assertFalse(matrix[1, 0])
        assertFalse(matrix[2, 1])
    }
}
