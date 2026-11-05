package edu.jxslu.schedule.ui.common

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * 开水确认点击策略。
 *
 * 2026-11-05 起固定单击（用户要求，与今日页余额面板同口径）：原先默认双击、
 * 设置里可切换，但整页只有这一个按钮读它，切换开关本身比它管的动作还重。
 * 双击分支保留给 `requireDoubleClick = true` 的调用方（目前没有），
 * `WaterClickPolicy` 的窗口值仍由该分支使用。
 */
object WaterClickPolicy {
    /** 两次点击间隔超过该值则重新计第一次。 */
    const val DOUBLE_CLICK_WINDOW_MS = 2500L
}

/**
 * 开水按钮：默认单击出水。
 *
 * 传 `requireDoubleClick = true` 时第一次点击只改文案提示确认，不触发 unlock。
 */
@Composable
fun WaterUnlockButton(
    enabled: Boolean,
    onUnlock: () -> Unit,
    modifier: Modifier = Modifier,
    requireDoubleClick: Boolean = false,
    text: String = "开水",
    height: Dp = 36.dp,
    fillMaxWidth: Boolean = false,
) {
    var armedAt by remember { mutableLongStateOf(0L) }
    var awaitingConfirm by remember { mutableStateOf(false) }

    // 超时自动收回「待确认」，避免用户以为已点上却没反应
    LaunchedEffect(awaitingConfirm) {
        if (awaitingConfirm) {
            delay(WaterClickPolicy.DOUBLE_CLICK_WINDOW_MS)
            awaitingConfirm = false
            armedAt = 0L
        }
    }

    val label = if (awaitingConfirm) "再点确认" else text

    Button(
        onClick = {
            if (!enabled) return@Button
            if (!requireDoubleClick) {
                awaitingConfirm = false
                armedAt = 0L
                onUnlock()
                return@Button
            }
            val now = System.currentTimeMillis()
            if (awaitingConfirm && now - armedAt <= WaterClickPolicy.DOUBLE_CLICK_WINDOW_MS) {
                awaitingConfirm = false
                armedAt = 0L
                onUnlock()
            } else {
                armedAt = now
                awaitingConfirm = true
            }
        },
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 6.dp),
        modifier = modifier
            .then(if (fillMaxWidth) Modifier.fillMaxWidth() else Modifier)
            .height(height),
    ) {
        Text(label)
    }
}
