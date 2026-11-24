package edu.jxslu.schedule.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle
import platform.UIKit.UINotificationFeedbackGenerator
import platform.UIKit.UINotificationFeedbackType
import platform.UIKit.UISelectionFeedbackGenerator

/**
 * [rememberAppHaptics] 的 iOS 实现（iOS 移植，2026-11-24）：用 UIKit 的三类反馈发生器。
 *
 * ## 四档压成三档是**有意的降级**，不是漏了
 *
 * iOS 的 `UIFeedbackGenerator` 家族只有三种触感语义：冲击（impact，四档力度）、
 * 选择（selection，一格一格地挪）和通知（notification，成功/警告/失败）。没有 Android
 * `CLOCK_TICK` 那种"钟表走格"的独立常量——最接近的就是选择反馈，所以 [tick] 与 [toggle]
 * 落到同一个发生器：滚轮每挪一格震一次，与"刻度感"的意图一致，只是少了 Android 侧那点
 * 更轻弱的层次。真要在 iOS 上再分化，改这一个文件即可，60+ 个调用点不受影响。
 *
 * 映射：[tap] → LIGHT 冲击、[toggle] → 选择、[success] → SUCCESS 通知、[tick] → 选择。
 *
 * ## 为什么发生器只建一次
 *
 * `prepare()` 的作用是让 Taptic Engine 提前上电，把延迟从几十毫秒压到几乎为零；它省的是
 * **同一个实例**的响应延迟，所以实例必须 `remember` 住复用，不能每次点击 new 一个
 * （那样 prepare 等于白做，还会多一次对象分配）。
 *
 * 线程：Compose 在 iOS 上跑在主线程，`remember` 与这四个调用点都在主线程，符合 UIKit 要求。
 *
 * 编译状态：本机是 Windows，**这段代码没有编译验证过**（Kotlin/Native 需要 macOS）。
 * 用的都是最常用的 UIKit 反馈 API，符号清单见返回报告；若 iOS 编译报符号错，改这一处即可。
 */
@Composable
actual fun rememberAppHaptics(): AppHaptics = remember { IosHaptics() }

private class IosHaptics : AppHaptics {

    private val impact = UIImpactFeedbackGenerator(UIImpactFeedbackStyle.UIImpactFeedbackStyleLight)
    private val selection = UISelectionFeedbackGenerator()
    private val notification = UINotificationFeedbackGenerator()

    /** 普通点击：LIGHT 冲击，与 Android 侧 `CONTEXT_CLICK` 的"轻一点击"对齐。 */
    override fun tap() {
        if (!HapticsSettings.isEnabled()) return
        impact.prepare()
        impact.impactOccurred()
    }

    /** 状态切换：选择反馈（iOS 上开关/选项切换的标准手感）。 */
    override fun toggle() {
        if (!HapticsSettings.isEnabled()) return
        selection.prepare()
        selection.selectionChanged()
    }

    /** 动作成功：SUCCESS 通知反馈，比 [tap] 明确一档（与 Android 的 CONFIRM 同一语义位）。 */
    override fun success() {
        if (!HapticsSettings.isEnabled()) return
        notification.prepare()
        notification.notificationOccurred(UINotificationFeedbackType.UINotificationFeedbackTypeSuccess)
    }

    /** 刻度感：iOS 没有 CLOCK_TICK 的等价常量，用选择反馈（四档压三档的降级，见类 KDoc）。 */
    override fun tick() {
        if (!HapticsSettings.isEnabled()) return
        selection.prepare()
        selection.selectionChanged()
    }
}
