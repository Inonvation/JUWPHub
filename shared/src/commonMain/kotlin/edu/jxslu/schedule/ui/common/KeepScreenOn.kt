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
 * | 实现 | `View.keepScreenOn = enabled` | `UIApplication.sharedApplication.idleTimerDisabled`（2026-11-26 接上） |
 * | 离开组合 | 复位成 `false` | 复位：持有者计数归零时把 `idleTimerDisabled` 放回 `false` |
 * | 用户可见差异 | 无（与搬移前逐字一致） | 无 |
 *
 * iOS 那条**必须成对复位**：`idleTimerDisabled` 是进程级全局开关，漏复位 = 退出蹲点页后
 * 设备再也不自动锁屏。实现里按「持有者个数」增减，归零才放开（`KeepScreenOn.ios.kt`）。
 *
 * 调用方只需按「当前要不要常亮」传布尔，**不要**自己记状态：`DisposableEffect` 的
 * 键变化与离开组合都归这条缝处理。
 */
@Composable
expect fun KeepScreenOnEffect(enabled: Boolean)
