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
 * 不受改时间、时区与 NTP 校正影响（`:app` 那份用的是 `SystemClock.elapsedRealtime()`，
 * 语义相同）。
 *
 * 计时起点是**本组合第一次进入时**，不是宿主算锁的那一刻——弹窗还没上屏就把秒数走掉，
 * 锁就白设了。
 *
 * ## 为什么与 `:app` 的 `rememberCloseLock` 并存
 *
 * `:app` 的 `ui/common/LegalDialogs.kt` 里有一份同名同语义的实现，仍被长文声明弹窗与
 * U净页用着；那个文件在本次改造的禁改清单里，删不掉。这里声明成 `internal`，
 * 所以 `:app` 看不见它（不会出现同名重载歧义），等 `LegalDialogs.kt` 解冻后删掉那边那份、
 * 把这里改成 `public` 即可——**届时全仓只剩一份**。
 */
@Composable
internal fun rememberCloseLock(totalMs: Long): Long {
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
