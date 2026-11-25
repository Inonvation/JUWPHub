package edu.jxslu.schedule.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlin.time.TimeSource

/**
 * 关闭锁的剩余毫秒（0 = 可关）：强制阅读弹窗的倒计时。
 *
 * **用单调时钟而不是墙钟**：用户把系统时间往前调就能把锁瞬间走完，`TimeSource.Monotonic`
 * 不受改时间、时区与 NTP 校正影响。
 *
 * 计时起点是**本组合第一次进入时**，不是宿主算锁的那一刻——弹窗还没上屏就把秒数走掉，
 * 锁就白设了。
 *
 * ## 现在是全仓唯一一份（2026-11-25）
 *
 * 原先 `:app` 的 `ui/common/LegalDialogs.kt` 里有一份同名同语义的实现（用 Android 的
 * `SystemClock.elapsedRealtime()`），本文件这份声明成 `internal` 与它并存。
 * 那个文件随首启引导一起搬进共享层（iOS 也要弹同一份声明），两份合并成这一份，
 * 因此改成 `public`——`:app` 的长文弹窗、U净页与引导页都指向这里。
 */
@Composable
fun rememberCloseLock(totalMs: Long): Long {
    var remainingMs by remember(totalMs) { mutableLongStateOf(totalMs) }
    LaunchedEffect(totalMs) {
        if (totalMs <= 0L) {
            remainingMs = 0L
            return@LaunchedEffect
        }
        val startAt = TimeSource.Monotonic.markNow()
        while (true) {
            val left = totalMs - startAt.elapsedNow().inWholeMilliseconds
            if (left <= 0L) break
            remainingMs = left
            delay(minOf(200L, left))
        }
        remainingMs = 0L
    }
    return remainingMs
}
