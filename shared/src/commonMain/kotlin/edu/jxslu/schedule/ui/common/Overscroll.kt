package edu.jxslu.schedule.ui.common

import androidx.compose.runtime.Composable

/**
 * 关掉触底回弹（stretch overscroll）的容器（iOS 移植 S19，2026-11-25）。
 *
 * ## 为什么是缝
 *
 * 天气页有一条 2026-10-11 的用户报障修复：这一页是 `Column + verticalScroll` 的四屏长内容
 * （30 多个 emoji 字形），拖到底后系统把**整个滚动内容**做拉伸，那一帧要整块重栅格化，
 * 表现为「滑到底再滑卡一下」。当时的修法是 `LocalOverscrollConfiguration provides null`——
 * 而 `androidx.compose.foundation.LocalOverscrollConfiguration` 在 CMP 1.7.3 的
 * `commonMain` 里**不存在**（元数据门禁当场报 Unresolved reference），1.8 才换成
 * `LocalOverscrollFactory`。所以收成一条缝：Android 保持同一个开关，iOS 直接跑内容。
 *
 * ## 两端语义
 *
 * | 平台 | 实现 | 行为 |
 * |---|---|---|
 * | Android | `LocalOverscrollConfiguration provides null` | 与搬迁前**一字不差** |
 * | iOS | 直接跑 [content] | CMP 1.7.3 的 iOS 滚动没有这套 stretch 效果，无开关可关 |
 *
 * iOS 侧不是「还没实现」：那边的滚动容器本来就不做 Android 的拉伸回弹，
 * 这段卡顿的成因在 iOS 上不存在。
 */
@Composable
expect fun DisableStretchOverscroll(content: @Composable () -> Unit)
