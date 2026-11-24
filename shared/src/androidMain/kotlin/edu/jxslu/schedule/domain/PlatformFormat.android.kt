package edu.jxslu.schedule.domain

import java.util.Locale

/**
 * [formatFixed] 的 Android 实现：直接落到 `String.format`。
 *
 * `Locale.ROOT` 是有意的——欧洲语言下系统默认 locale 会把小数点写成逗号
 * （「1,9 公里」），而协议与界面文案统一用 `.`。
 */
internal actual fun formatFixed(value: Double, places: Int): String =
    String.format(Locale.ROOT, "%.${places}f", value)
