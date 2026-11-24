package edu.jxslu.schedule.domain

import platform.Foundation.NSLocale
import platform.Foundation.NSString
import platform.Foundation.create

/**
 * [formatFixed] 的 iOS 实现：走 Foundation 的 `NSString(format:)`，底层同样是 C 的
 * `printf` 家族，舍入口径与 Android 的 `String.format` 一致。
 *
 * 两处不是随手写的，都是编译期撞出来的：
 * - **`locale` 必须显式给**。`NSString.create` 有两个候选（带 / 不带 `locale`），
 *   只传 `format` + 值会同时命中两个、报重载歧义。
 * - **locale 用 `en_US_POSIX` 而不是 null**。传 null 会用系统区域设置，德语/法语等区域
 *   会把小数点写成逗号（「12,34」），而 Android 那边写死 `Locale.ROOT`——金额显示会分叉。
 *   `en_US_POSIX` 的小数点是 `.` 且不做分组，与 `Locale.ROOT` 同口径。
 *
 * 本分支现在有 CI 证据（`.github/workflows/ios-compile.yml`），不再是「照文档推的」。
 */
internal actual fun formatFixed(value: Double, places: Int): String =
    NSString.create(
        format = "%.${places}f",
        locale = NSLocale(localeIdentifier = "en_US_POSIX"),
        value,
    ).toString()
