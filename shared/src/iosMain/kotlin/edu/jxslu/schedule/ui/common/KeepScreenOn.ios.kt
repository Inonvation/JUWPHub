package edu.jxslu.schedule.ui.common

import androidx.compose.runtime.Composable

/**
 * [KeepScreenOnEffect] 的 iOS 实现：**空实现**（2026-11-25）。
 *
 * 为什么现在不接：iOS 那边对应的是 `UIApplication.sharedApplication.idleTimerDisabled`，
 * 需要 (1) 主线程访问，(2) 成对复位——漏复位会让设备永久不自动锁屏，这比「蹲点时可能自动
 * 锁屏」严重得多。本机没有 macOS，Kotlin/Native 那一侧编译不了，写一段验不了的全局状态
 * 开关不划算。
 *
 * 用户可见差异只有一个：iOS 上长时间停在问渠蹲点页时，系统可能按设置自动锁屏；锁屏后
 * 探测协程随进程一起被挂起（本页设计前提就是「开着页面才蹲」，见 `WenquGrabScreen` 的 KDoc）。
 *
 * 升级条件：iOS 壳（`design/ios-port.md` §4 P3）落地后，在这里用
 * `UIApplication.sharedApplication.idleTimerDisabled = enabled` 实现，并在 `onDispose`
 * 里复位成 `false`。
 */
@Composable
actual fun KeepScreenOnEffect(enabled: Boolean) {
    // 有意为空，理由见文件头。
}
