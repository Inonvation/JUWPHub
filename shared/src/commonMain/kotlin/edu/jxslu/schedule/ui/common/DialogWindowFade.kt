package edu.jxslu.schedule.ui.common

import androidx.compose.runtime.Composable

/**
 * 给弹窗窗口补上进出场动画（淡入 + 轻微放大 / 淡出）。
 *
 * 为什么要这一段：material3 1.3.0 的 `AlertDialog` 内部没有任何 compose 侧动画，
 * compose 的 Dialog 窗口主题（`DialogWindowTheme`）也没配 `windowAnimationStyle`
 * ——弹窗开与关都是瞬间出现 / 瞬间抽掉（2026-10-03 用户报「开车还车的弹窗有突变跳动」
 * 时的定位结论）。Android 侧修法是把窗口层的动画样式补上（样式与两个 `@anim` 分别在
 * `shared/src/androidMain/res/` 里）。
 *
 * 用法：放在 `AlertDialog { }` 的 `text`（内容槽）**第一行**调用一次。放槽外面是死代码：
 * 那时 `LocalView` 还是页面那个，拿不到 dialog window。
 *
 * ## 为什么是 `expect`
 *
 * 「窗口动画」是 Android WindowManager 的概念，iOS 没有对应物——Compose 在 iOS 上画的
 * 弹窗就是组合里的一个层，进出场由 compose 动画负责，没有 window 可以挂样式。所以这里是
 * 一条真·平台缝：Android 挂样式，iOS 空实现。
 *
 * 迁移痕迹：本函数原先在 `:app` 的 `ui/common/DialogWindowFade.kt`，iOS 移植 P2 随
 * `WheelPicker` 一族搬进 `:shared`。当时为了绕过「commonMain 看不见 `R.style`」直接删掉了
 * 三个滚轮弹窗的调用，Android 侧因此丢了淡入淡出——现在把缝补回来，20+ 个调用点一个都不用改。
 *
 * 退场条件（`// shortcut` 的同款口径）：material3 1.4.0 起弹窗自带进出场动画，届时
 * 这个 seam、两份 actual 和三个资源文件一起删掉。
 */
@Composable
expect fun DialogWindowFadeEffect()
