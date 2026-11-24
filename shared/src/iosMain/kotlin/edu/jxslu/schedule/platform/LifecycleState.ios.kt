package edu.jxslu.schedule.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * iOS 侧退化到 `collectAsState`（理由见 `LifecycleState.kt` 头部的两端对照表）。
 *
 * 代价写清楚：**组合在，收集就在**。退到后台后上游流仍活跃，`stateIn(WhileSubscribed)`
 * 的 5 秒自动断流在这条路径上不会触发。对本批搬过去的两页（课表 / 今日）的实际影响是
 * 「后台时 Room 的 Flow 还在被读」，没有写放大，也没有网络请求（检测链是手动触发的）。
 *
 * shortcut: 等 CMP 升到带 iOS LifecycleOwner 的版本，把这个文件改成真正的生命周期版，
 * 调用点不用动。升级条件 = CMP ≥ 1.8 与本仓 Glance 1.2.0 的冲突被解开。
 */
@Composable
actual fun <T> StateFlow<T>.collectAsStateWithLifecycle(initialValue: T): State<T> =
    collectAsState(initialValue)

@Composable
actual fun <T> Flow<T>.collectAsStateWithLifecycle(initialValue: T): State<T> =
    collectAsState(initialValue)
