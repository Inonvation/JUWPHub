package edu.jxslu.schedule.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import edu.jxslu.schedule.domain.AppClock
import kotlinx.coroutines.delay

/**
 * Android 侧：与搬迁前逐字一致（`repeatOnLifecycle(RESUMED)`）。
 *
 * `LocalLifecycleOwner` 从 `androidx.compose.ui.platform` 换成 `androidx.lifecycle.compose`
 * ——前者是 Android 专有别名，后者是 KMP 库里的正式位置（2.8 起）。
 * `System.currentTimeMillis()` 换成 [AppClock.nowMillis]，Behavior 相同（都是墙上时钟毫秒）。
 */
@Composable
actual fun ResumedTickerEffect(intervalMillis: Long, onTick: () -> Unit) {
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner, intervalMillis) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                delay(intervalMillis)
                onTick()
            }
        }
    }
}

@Composable
actual fun rememberResumedNow(intervalMillis: Long, key: Any?): State<Long> {
    val owner = LocalLifecycleOwner.current
    return produceState(initialValue = AppClock.nowMillis(), key, owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                value = AppClock.nowMillis()
                delay(intervalMillis)
            }
        }
    }
}
