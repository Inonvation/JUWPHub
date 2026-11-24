package edu.jxslu.schedule.ui.selection

import edu.jxslu.schedule.domain.TextFmt

/**
 * 选课模块的展示格式化（选课中心卡片、确认弹窗与已选课程页共用，别各写一份）。
 *
 * 原实现是 `Math.round(value * 100) / 100.0` + `Double.toString()`：`Math` 是 JVM 专有类，
 * `Double.toString()` 的尾数在不同平台不保证一致（`commonMain` 要按 Kotlin 公共标准库编译）。
 * 现在走 [TextFmt.decimals]——它对着一份 `String.format` 的全量单测，舍入口径与 `%.2f` 一致，
 * 尾零裁剪保持原样（2.0 → 2、2.50 → 2.5）。
 *
 * 2026-11-25 搬迁时把 `SelectionRecordsScreen` 里逐字重复的 `trimCredit` 一并删了，两页改调这里。
 */
fun creditText(value: Double): String {
    val s = TextFmt.decimals(value, 2).trimEnd('0').trimEnd('.')
    return s.ifEmpty { "0" }
}
