package edu.jxslu.schedule.domain

import platform.Foundation.NSString
import platform.Foundation.create

/**
 * [formatFixed] 的 iOS 实现：走 Foundation 的 `NSString(format:)`，底层同样是 C 的
 * `printf` 家族，舍入口径与 Android 的 `String.format` 一致。
 *
 * 未在本机验证：Kotlin/Native 的 iOS 编译需要 macOS + Xcode，本项目开发机是 Windows，
 * 这个分支只在 Windows 上被**声明**、不会被编译。首次在 mac 上跑
 * `:shared:compileKotlinIosArm64` 时若签名对不上，改这一个文件即可。
 */
internal actual fun formatFixed(value: Double, places: Int): String =
    NSString.create(format = "%.${places}f", value).toString()
