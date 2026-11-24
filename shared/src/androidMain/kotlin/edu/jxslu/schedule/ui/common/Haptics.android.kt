package edu.jxslu.schedule.ui.common

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * [rememberAppHaptics] 的 Android 实现（iOS 移植，2026-11-24）。
 *
 * 行为与搬进 `:shared` 之前逐字一致：取 `LocalView`，`remember(view)` 缓存实例，
 * 开关走 [HapticsSettings]（原来是这条 Composable 自己收 DataStore 流，
 * 现在由 `:app` 启动时接到注入点，开关语义不变：默认开、关掉直接短路）。
 */
@Composable
actual fun rememberAppHaptics(): AppHaptics {
    val view = LocalView.current
    // remember 只缓存实例；enabled 读的是 [HapticsSettings] 的当前 lambda，
    // 每次调用都取最新开关值，开关切换不需要重建实例（也因此不触发重组）
    return remember(view) { AndroidHaptics(view) { HapticsSettings.isEnabled() } }
}

private class AndroidHaptics(private val view: View, private val enabled: () -> Boolean) : AppHaptics {

    /** 普通点击（设置入口、图标按钮、课程块）。 */
    override fun tap() {
        if (!enabled()) return
        // 根因：HyperOS/MIUI 把 VIRTUAL_KEY 挂在系统「触摸时振动」设置下，
        // 该设置默认关（Redmi K70 实测 VIRTUAL_KEY 无振动、CONTEXT_CLICK 有），
        // 所以两种语义统一走 CONTEXT_CLICK，不再依赖那个系统开关。
        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
    }

    /** 状态切换（开关、选项选中）。当前与 [tap] 同常量，见接口 KDoc 第 3 条口径。 */
    override fun toggle() {
        if (!enabled()) return
        // CONTEXT_CLICK：语义即「上下文点按」，API 23+，MIUI/HyperOS 上有明显但不重的反馈
        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
    }

    /**
     * 动作成功（开锁成功这类"等了几秒终于成了"的时刻）：API 30+ 用 CONFIRM 语义，
     * 低版本退回 CONTEXT_CLICK（CONFIRM 是 30 才有的常量，minSdk 26 要兜住）。
     * 比 [tap] 明确一档——手在车把上、眼睛看着车锁时，反馈只能靠手。
     */
    override fun success() {
        if (!enabled()) return
        val constant = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.CONFIRM
        } else {
            HapticFeedbackConstants.CONTEXT_CLICK
        }
        view.performHapticFeedback(constant)
    }

    /** 极轻的刻度感（滚轮停稳到新档位）。CLOCK_TICK 语义就是钟表走格，比 tap 弱一档。 */
    override fun tick() {
        if (!enabled()) return
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }
}
