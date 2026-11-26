package edu.jxslu.schedule.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 抽屉主卡进度的合法性收敛（`DrawerCardTransform.kt` 的 `drawerCardProgress`）。
 *
 * 钉住 2026-11-26 iOS 侧载包「启动瞬间回桌面、屏幕上没有任何文字」的根因：
 * Material3 的 `DrawerState.currentOffset` 在**首帧测量之前是 `NaN`**，iOS 的调用点把它
 * 归一化成 `-NaN / 侧栏宽` 喂进绘制 lambda，一路落到
 * `(eased * DrawerCardCornerSteps).roundToInt()`——而 `Float.roundToInt()` 碰到 NaN 直接抛
 * `IllegalArgumentException: Cannot round NaN value`，进程 abort。
 *
 * 抛点在绘制期（`MetalView.layoutSubviews → MetalRedrawer.draw`），组合期的兜底接不住，
 * 所以这一条只能靠「进度进来先收敛」保证。Android 侧不暴露同一个坑：那里的
 * `StackedDrawerState.progress` 由自己的拖动状态给出，恒是 0…1 的有限值。
 */
class DrawerCardProgressTest {

    @Test
    fun `NaN 当成抽屉合上的静止态`() {
        // 首帧测量前就是这一种。归 0 = 缩放 1 / 圆角 0 / 位移 0 / 阴影 0
        assertEquals(0f, drawerCardProgress(Float.NaN), 0f)
    }

    @Test
    fun `正负无穷也收成 0`() {
        // 侧栏宽为 0（密度还没就绪）时 -offset / 0 会算出 ±Inf
        assertEquals(0f, drawerCardProgress(Float.POSITIVE_INFINITY), 0f)
        assertEquals(0f, drawerCardProgress(Float.NEGATIVE_INFINITY), 0f)
    }

    @Test
    fun `有限值原样透传`() {
        // 包括拖动时的越界值（阻尼、回弹）：收敛只处理非有限值，不夹区间
        assertEquals(0f, drawerCardProgress(0f), 0f)
        assertEquals(0.5f, drawerCardProgress(0.5f), 0f)
        assertEquals(1f, drawerCardProgress(1f), 0f)
        assertEquals(-0.25f, drawerCardProgress(-0.25f), 0f)
        assertEquals(1.4f, drawerCardProgress(1.4f), 0f)
    }
}
