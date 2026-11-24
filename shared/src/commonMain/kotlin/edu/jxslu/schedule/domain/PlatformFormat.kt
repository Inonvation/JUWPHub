package edu.jxslu.schedule.domain

/**
 * 定点小数的平台实现缝（iOS 移植 P0，2026-11-24）。
 *
 * 这是全仓库唯一一处「必须调平台库」的字符串格式化：`String.format(Locale.ROOT, "%.2f", v)`
 * 的舍入口径是**对 double 的精确十进制展开做 HALF_UP**，手写「乘 10^n 再 round」会在
 * `1.045` 这类值上偏一分（`1.045 * 100` 在 double 里正好落在 `104.5`，四舍五入成 `105`，
 * 而 Java 给出 `104`）。金额显示依赖它，所以不能自己实现，只能交给各平台自己的 printf。
 *
 * Android 侧用 `String.format`，iOS 侧用 `NSString(format:)`。两个平台都走 C 的
 * `printf` 家族口径，结果一致。
 */
internal expect fun formatFixed(value: Double, places: Int): String
