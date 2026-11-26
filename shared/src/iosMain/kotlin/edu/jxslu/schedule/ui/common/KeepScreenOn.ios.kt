package edu.jxslu.schedule.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UIApplication
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/**
 * [KeepScreenOnEffect] 的 iOS 实现：`UIApplication.sharedApplication.idleTimerDisabled`（2026-11-26）。
 *
 * ## 为什么必须成对复位
 *
 * 这个开关是**进程级、全局**的：置 true 之后系统不再自动锁屏，直到有人置回 false 或进程退出。
 * 漏复位 = 用户退出蹲点页后设备**永远不灭屏**（耗电、烧屏），比「蹲点时可能自动锁屏」严重得多。
 * 所以复位写在 [DisposableEffect] 的 `onDispose` 里，与置位同一次组合、同一个键：
 * 页面离开组合、或者 `enabled` 从 true 翻成 false，都会走一遍。
 *
 * ## 为什么是计数而不是布尔
 *
 * 两个页面同时要常亮时（今天只有问渠蹲点页一个调用方，但这不是可依赖的前提），
 * 布尔实现会让先离开的那一页把另一个页面的常亮关掉。这里按「持有者个数」加减，
 * 归零才真的放开——每一次 [IosIdleTimer.acquire] 都由同一个 `DisposableEffect` 的
 * `onDispose` 配一次 [IosIdleTimer.release]，配平由结构保证。
 *
 * ## 线程
 *
 * `UIApplication` 的属性只能在主线程改，所以两个动作都丢到主队列（与
 * `platform/IosLocalNotifications` 同一个纪律）。它们都是 `dispatch_async`，队列是串行 FIFO，
 * acquire / release 的先后不会乱序。
 *
 * ## 编译状态
 *
 * 本机是 Windows，**没有编译验证过**（Kotlin/Native 要 macOS）。用到的两个符号都是仓里已有的：
 * `UIApplication.sharedApplication`（`ui/ebike/IosWechatLaunch.kt` 里点 `openURL` 用的同一个）
 * 与 `dispatch_async` + `dispatch_get_main_queue`（`platform/IosLocalNotifications` 已用）。
 * `idleTimerDisabled` 本身是 UIKit 的标准 `BOOL` 属性，若 K/N 绑定给出的是 `setIdleTimerDisabled()`
 * 方法而不是属性赋值，只需把 [IosIdleTimer.apply] 里那一行改成调用形式即可——**别改成
 * `UIApplication.sharedApplication.setValue(...)` 之类绕路**，那是 KVC 字符串拼写，写错了编译能过、
 * 运行时抛异常。
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun KeepScreenOnEffect(enabled: Boolean) {
    // 键是 enabled：检测中 ↔ 停止时要跟着切。`enabled = false` 时不留持有者，
    // 也不需要 onDispose（没置过就没有要复位的）
    DisposableEffect(enabled) {
        if (!enabled) return@DisposableEffect onDispose { }
        IosIdleTimer.acquire()
        onDispose { IosIdleTimer.release() }
    }
}

/**
 * `idleTimerDisabled` 的持有者计数（见 [KeepScreenOnEffect] 的 KDoc）。
 *
 * 只在主队列上增减与写入，所以不需要锁：`dispatch_async` 投到主队列的块是串行执行的。
 */
private object IosIdleTimer {

    /** 当前有几个页面要求常亮。归零才把系统开关放开。 */
    private var holders = 0

    fun acquire() {
        onMain {
            holders++
            apply()
        }
    }

    fun release() {
        onMain {
            // 防御性下溢保护：配平由 DisposableEffect 结构保证，走到这里为负说明别处漏了 release
            if (holders > 0) holders--
            apply()
        }
    }

    private fun apply() {
        UIApplication.sharedApplication.idleTimerDisabled = holders > 0
    }

    private fun onMain(block: () -> Unit) {
        dispatch_async(dispatch_get_main_queue()) { block() }
    }
}
