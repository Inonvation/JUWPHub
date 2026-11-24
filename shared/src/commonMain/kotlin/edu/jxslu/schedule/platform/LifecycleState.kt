package edu.jxslu.schedule.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * `collectAsStateWithLifecycle` 的跨平台入口（iOS 移植 M1，2026-11-25）。
 *
 * ## 为什么自己写一个
 *
 * 官方实现住在 `androidx.lifecycle:lifecycle-runtime-compose`，而它的 **KMP 变体要
 * Compose 1.9**；本仓锁在 CMP 1.7.3（升 1.8+ 会把 Android 侧的 androidx.compose 整条
 * 抬一个大版本、Glance 1.2.0 全要重验，见 `AGENTS.md`「工程实况」）。所以在
 * 「搬 UI」和「升 Compose」之间只能先选前者。
 *
 * ## 两端语义**不同**，这是刻意的
 *
 * | 平台 | 实现 | 行为 |
 * |---|---|---|
 * | Android | 转调官方的 `androidx.lifecycle.compose.collectAsStateWithLifecycle` | 与搬迁前**逐字一致**：STOPPED 时停止收集 |
 * | iOS | 退化到 `collectAsState` | 组合存活期间一直收集；CMP 1.7.3 的 iOS 侧没有 LifecycleOwner 口径 |
 *
 * 也就是说**Android 侧没有任何行为变化**（这是搬 UI 的硬要求），iOS 侧接受「后台仍在收集」
 * ——那边的流大多是 `stateIn(WhileSubscribed)`，代价是退到后台后上游不断流，等 M2 之后
 * 用 CMP 的 iOS 生命周期补上。
 *
 * ## 用法
 *
 * 搬进 `commonMain` 的页面**改 import 到本包**即可，调用点一个字不用动：
 * 原来 `androidx.lifecycle.compose.collectAsStateWithLifecycle`，现在
 * `edu.jxslu.schedule.platform.collectAsStateWithLifecycle`。
 */
@Composable
expect fun <T> StateFlow<T>.collectAsStateWithLifecycle(initialValue: T): State<T>

/** [Flow] 版；语义同前，供「一次性查询流」这类调用点用（如按课程名查教材）。 */
@Composable
expect fun <T> Flow<T>.collectAsStateWithLifecycle(initialValue: T): State<T>

/** 不带初值：直接取 `StateFlow.value` 当首帧，避免首帧闪一个默认值。 */
@Composable
fun <T> StateFlow<T>.collectAsStateWithLifecycle(): State<T> =
    collectAsStateWithLifecycle(value)
