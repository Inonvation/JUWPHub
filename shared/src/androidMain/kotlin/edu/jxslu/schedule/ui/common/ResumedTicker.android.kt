package edu.jxslu.schedule.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
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

/**
 * 「回到前台」计数：与搬迁前 `ui/common/ResumeTick.kt` 的实现逐字一致。
 *
 * `LocalLifecycleOwner` 同样从 `androidx.compose.ui.platform` 换到 `androidx.lifecycle.compose`
 * ——前者是 Android 专有别名，后者是 KMP 库里的正式位置（2.8 起）。
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
