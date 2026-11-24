package edu.jxslu.schedule.ui.common

import androidx.compose.runtime.Composable

/**
 * 接管系统返回（iOS 移植 S19，2026-11-25）。
 *
 * ## 为什么非立一条缝不可
 *
 * `androidx.activity.compose.BackHandler` 是 Activity 的东西，`commonMain` 看不见；
 * CMP 1.7.3 也**没有**把它放进公共坐标——实测 `org.jetbrains.compose.ui:ui:1.7.3` 的
 * 元数据 jar 里没有 `androidx/compose/ui/backhandler` 这一族（`ui-backhandler` 是 1.8 起的
 * 新产物，本仓锁 1.7.3 上不去）。所以页面想在共享层「多选模式下先退多选再退页」，
 * 只能自己开一条。
 *
 * ## 两端语义**不同**，这是刻意的
 *
 * | 平台 | 实现 | 行为 |
 * |---|---|---|
 * | Android | 转调 `androidx.activity.compose.BackHandler` | 与搬迁前**一字不差** |
 * | iOS | 空实现 | iOS 没有系统返回键，页面靠顶栏返回键退出 |
 *
 * 空实现不等于「以后补」：iOS 上根本没有这条输入，所以这里不是占位，是最终形态。
 * 以后接上 iOS 的边缘滑动返回手势（CMP 1.8+ 的 `ui-backhandler`）时，改的是本文件的
 * `iosMain` actual，调用方一个字不用动。
 */
@Composable
expect fun AppBackHandler(enabled: Boolean, onBack: () -> Unit)
