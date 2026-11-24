package edu.jxslu.schedule.ui.ujing

import edu.jxslu.schedule.domain.AppClock
import edu.jxslu.schedule.domain.TextFmt

/**
 * U净 账单的时间标签：`M月d日 HH:mm`（主页记录卡与历史账单页共用一份）。
 *
 * 原来两处各写一遍 `SimpleDateFormat("M月d日 HH:mm", Locale.CHINESE).format(Date(at))`：
 * `java.text` / `java.util.Date` 在 `commonMain` 不可用（iOS 移植 S21），换成
 * [AppClock] 的墙上时刻 + [TextFmt.pad] 手拼。
 *
 * **不加「今天 / 昨天」这类相对说法**：账单是留档，绝对日期比「昨天」更好对账
 * （与收件箱那张卡片的口径不同，别顺手统一）。
 */
internal fun ujingTimeLabel(epochMillis: Long): String {
    val at = AppClock.dateTimeOf(epochMillis)
    return "${at.monthNumber}月${at.dayOfMonth}日 ${TextFmt.pad(at.hour)}:${TextFmt.pad(at.minute)}"
}
