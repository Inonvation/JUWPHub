package edu.jxslu.schedule.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/**
 * [rememberResumeTick] 的 Android 实现：`LocalLifecycleOwner` + `ON_RESUME` 计数。
 *
 * 语义与搬迁前 `:app/ui/common/ResumeTick.kt` **逐字一致**——`SettingsScreen` 搬进
 * 共享层时把这条缝一起带过来（iOS 移植 S17，2026-11-25），其余 6 个 `:app` 调用点
 * 只改 import 所在模块、包名没变，所以一个字都不用动。
 */
@Composable
actual fun rememberResumeTick(): Int {
    var tick by remember { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        var entering = true
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (entering) entering = false else tick++
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return tick
}
