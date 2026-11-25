@file:OptIn(ExperimentalForeignApi::class)

package edu.jxslu.schedule.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.interop.LocalUIViewController
import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UIViewController

/**
 * 「从组合里取宿主 `UIViewController`」的**唯一实现**（iOS 移植 M3，2026-11-26）。
 *
 * ## 为什么它必须只有一份
 *
 * 凡是 present 系统面板的动作（分享、相册、文件面板）都要一个**真实的、当前在屏上的**
 * VC；猜 `UIApplication.keyWindow.rootViewController` 在 iOS 13 多场景之后语义不成立
 * （理由见 `ShareIntents.ios.kt` 的文件头）。iOS 侧能拿到它的唯一来源是 Compose 的
 * `LocalUIViewController`（CMP 1.7.3 在 `androidx.compose.ui.interop`），而这条取法原先在
 * `ui/me/hub/IosPermissionsStorage.kt` 里单独写过一遍。本文件把它收成一处，两个入口：
 *
 * | 场景 | 入口 | 生命周期 |
 * |---|---|---|
 * | 在**组合里**当场要一个 `ShareTarget`（权限页「导出诊断日志」那种） | [rememberIosShareTarget] | 与这一屏同寿 |
 * | 让**非组合**的 `shareFile` / `sharePlainText` 也能弹面板 | [IosSharePresenter] + [IosShareHost] | 与根组合同寿 |
 *
 * UIKit 那一半（活动项、iPad 锚点、`present` 调用）仍然只有 `IosShareTarget`（在
 * `ShareIntents.ios.kt` 里）。本文件只负责「VC 从哪来」，不重复实现 presenter。
 *
 * ## 宿主必须做的一件事
 *
 * 在**根组合**里调一次 `IosSharePresenter()`（`MainViewController` 的 `ComposeUIViewController {}`
 * 里，与主题 / 导航同级）。不调它 = `shareFile` 没有可用的 VC，照旧返回 `false`。
 * Swift 壳那条 `ShareSettings.shared.target = …` 仍然有效且**优先**，两边不打架。
 *
 * 线程：`present` 只能在主线程（见 `IosShareTarget` 的类 KDoc）。这里不切线程——切过去
 * 反而会因为跨线程碰 UIKit 崩。
 */
@Composable
fun rememberIosShareTarget(): ShareTarget {
    val host = LocalUIViewController.current
    // 同一个 VC 上没必要重建 target：remember 的 key 就是 VC 本身
    return remember(host) { IosShareTarget { host } }
}

/**
 * 把当前宿主 VC 装进 [IosShareHost]，让**非组合**代码（`shareFile` / `sharePlainText`）也能弹面板。
 *
 * 只在根组合里调一次；进出组合各对应一次装卸，多窗口重建 VC 时自动换成新的那一份。
 */
@Composable
fun IosSharePresenter() {
    val host = LocalUIViewController.current
    DisposableEffect(host) {
        IosShareHost.attach(host)
        onDispose { IosShareHost.detach(host) }
    }
}

/**
 * 进程级的「当前宿主 VC」持有者，读方是 `ShareIntents.ios.kt` 的两个 actual。
 *
 * 写方只有 [IosSharePresenter]（`attach` / `detach` 是 internal）。**不要**在别处直接 attach：
 * 那样又会多出第二份「VC 从哪儿拿」的答案，也就是又要再猜一次。
 */
internal object IosShareHost {
    private var controller: UIViewController? = null

    fun attach(host: UIViewController) {
        controller = host
    }

    /** 只有「卸的正是当前这一份」时才清空：多窗口场景下别的窗口可能已经装上了自己那份。 */
    fun detach(host: UIViewController) {
        if (controller === host) controller = null
    }

    /** 宿主没调 [IosSharePresenter] 时给 null，调用方按「分享不可用」处理（返回 false）。 */
    fun target(): ShareTarget? = controller?.let { host -> IosShareTarget { host } }
}
