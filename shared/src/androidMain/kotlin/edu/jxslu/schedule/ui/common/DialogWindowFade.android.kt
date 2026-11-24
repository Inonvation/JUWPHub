package edu.jxslu.schedule.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import edu.jxslu.schedule.shared.R

/**
 * [DialogWindowFadeEffect] 的 Android 实现：把 `DialogWindowFade` 窗口动画样式挂到
 * 当前弹窗的 window 上。
 *
 * 根因与生效时机（为什么 `setWindowAnimations` 要在 `SideEffect` 里、以及为什么进、出两段
 * 都能播）见 commonMain 那份 KDoc，这里只留实现。
 *
 * 注意 `R` 是**本模块**的（`edu.jxslu.schedule.shared.R`）：样式与两个 `@anim` 已经搬到
 * `shared/src/androidMain/res/`，`:app` 侧同名资源不存在了，别改成 `edu.jxslu.schedule.R`。
 */
@Composable
actual fun DialogWindowFadeEffect() {
    val view = LocalView.current
    SideEffect {
        // 弹窗内容里的 LocalView 应当就是 DialogLayout；parent 一层是兼容写法，
        // 万一某版本把内容装进子 AndroidComposeView（Popup 那套结构）也不落空
        val window = (view as? DialogWindowProvider)?.window
            ?: (view.parent as? DialogWindowProvider)?.window
        window?.setWindowAnimations(R.style.DialogWindowFade)
    }
}
