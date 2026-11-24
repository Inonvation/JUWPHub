package edu.jxslu.schedule.ui.common

import androidx.compose.runtime.Composable
import edu.jxslu.schedule.domain.YearMonthKey
import java.time.YearMonth

/**
 * `java.time.YearMonth` 版 [MonthPickerDialog] 重载（iOS 移植 S20，2026-11-25）。
 *
 * 共享层的弹窗只吃 [YearMonthKey]（`java.time` 在 Kotlin/Native 上不存在）。
 * 但 `ui/ebike` 的订单页（`KvcxOrdersScreen` / `KvcxOrdersViewModel`）把
 * `YearMonth` 直接当状态字段用，而那一批页面本轮不在搬移范围、也不许改。
 *
 * 所以适配放在**边界**：这里只做双向换算，弹窗实现仍然只有共享层那一份。
 * 等 `ui/ebike` 哪天改用 [YearMonthKey]（它要等 `com.amap` 高德 SDK 那条缝），
 * 本文件连同一个重载一起删掉——`grep "YearMonth"` 会立刻指出它。
 */
@Composable
fun MonthPickerDialog(
    months: List<YearMonth>,
    selected: YearMonth,
    onDismiss: () -> Unit,
    onSelect: (YearMonth) -> Unit,
    title: String = "选择月份",
) {
    MonthPickerDialog(
        months = months.map { YearMonthKey(it.year, it.monthValue) },
        selected = YearMonthKey(selected.year, selected.monthValue),
        onDismiss = onDismiss,
        onSelect = { onSelect(YearMonth.of(it.year, it.month)) },
        title = title,
    )
}
