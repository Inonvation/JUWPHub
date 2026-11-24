package edu.jxslu.schedule.ui.common

import androidx.compose.runtime.Composable

/**
 * 「这块内容显示期间别灭屏」的平台缝（问渠蹲点页搬进共享层时立，2026-11-25）。
 *
 * ## 为什么需要它
 *
 * 问渠蹲点页在检测期间要求屏幕常亮：息屏后系统会冻结进程，探测就断了
 * （`.agents/rules/wenqu-alerts.md`「蹲点模式」）。原实现在 `:app` 里拿
 * `LocalView.current.keepScreenOn` 直接改 View 属性，而 `LocalView` 是 Android 侧的实现细节
 * （iOS 的 Compose 宿主没有对应的 View 概念），所以收成一条缝。
 *
 * ## 两端语义
 *
 * | | Android | iOS |
 * |---|---|---|
 * | 实现 | `View.keepScreenOn = enabled` | 空实现（`UIApplication.isIdleTimerDisabled` 待接） |
 * | 离开组合 | 复位成 `false` | 无事发生 |
 * | 用户可见差异 | 无（与搬移前逐字一致） | 长时间盯着蹲点页时可能自动锁屏 |
 *
 * iOS 侧留空不是「以后补」的占位：那条路要拿 `UIApplication.sharedApplication` 并保证
 * 复位（漏复位 = 永久不灭屏，比不实现更糟），而本机没有 macOS 验不了。升级条件 =
 * iOS 壳（`design/ios-port.md` §4 P3）起来之后，在那边的 actual 里置
 * `isIdleTimerDisabled` 并在 `onDispose` 里复位。
 *
 * 调用方只需按「当前要不要常亮」传布尔，**不要**自己记状态：`DisposableEffect` 的
 * 键变化与离开组合都归这条缝处理。
 */
@Composable
expect fun KeepScreenOnEffect(enabled: Boolean)
