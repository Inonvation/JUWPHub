package edu.jxslu.schedule.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import edu.jxslu.schedule.domain.AppClock
import kotlinx.coroutines.delay

/**
 * iOS 侧：**没有暂停条件**——组合在，心跳就在（理由见 `ResumedTicker.kt` 的两端对照表）。
 *
 * 为什么接受这个差别：CMP 1.7.3 的 iOS 侧既不提供 `LocalLifecycleOwner`，也不提供
 * `repeatOnLifecycle`；而这两页真正需要「停」的场景只有一种——退到后台后不该继续跑网络、
 * 不该继续重组。iOS 上退到后台时系统会挂起整个进程，`delay` 随之冰冻，回前台续上，
 * 所以这条路径的实际代价只有「回前台后第一拍可能立刻补一次」，比 Android 还轻。
 *
 * shortcut: 等 CMP 升到带 iOS LifecycleOwner 的版本，把这个文件换成真正的生命周期版；
 * 升级条件 = CMP ≥ 1.8 与 Glance 1.2.0 的冲突被解开。
 */
@Composable
actual fun ResumedTickerEffect(intervalMillis: Long, onTick: () -> Unit) {
    LaunchedEffect(intervalMillis) {
        while (true) {
            delay(intervalMillis)
            onTick()
        }
    }
}

@Composable
actual fun rememberResumedNow(intervalMillis: Long, key: Any?): State<Long> =
    produceState(initialValue = AppClock.nowMillis(), key) {
        while (true) {
            value = AppClock.nowMillis()
            delay(intervalMillis)
        }
    }
