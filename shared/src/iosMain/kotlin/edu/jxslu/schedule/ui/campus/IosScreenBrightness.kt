package edu.jxslu.schedule.ui.campus

import platform.UIKit.UIScreen

/**
 * 付款码页的屏幕亮度（2026-11-26，用户要求「打开付款码要能自动提高亮度」）。
 *
 * 扫码枪要的是**屏幕够亮**：地铁闸机、食堂窗口的扫码器对亮度敏感，用户手动调到最低时
 * 经常扫不出来。Android 侧用 `window.attributes.screenBrightness` 提亮（随窗口消失自动还原），
 * iOS 没有窗口级亮度，只有系统级 `UIScreen.mainScreen.brightness`——**这是全局设置，不会自动还原**，
 * 所以必须自己记住旧值并在离开页面时写回，见 `PayCodeScreen` 的 `DisposableEffect`。
 *
 * ## 边界
 *
 * | 项 | 行为 |
 * |---|---|
 * | 只提不降 | 当前亮度已经 ≥ [PAY_CODE_BRIGHTNESS] 时原样返回，不改用户的设置 |
 * | 只在前台有效 | 亮度是进程对系统的设置，App 进后台系统会按自己的规则处理；回前台再由页面重设 |
 * | 用户中途改过 | 还原时写回的是「进页那一刻」的值，不是页面期间的任何值——期间用户改不了，他在扫码 |
 *
 * **`UIScreen.mainScreen.brightness` 是全系统设置**：如果这里写错、或者离开页面时没还原，
 * 用户会带着一块「永远最亮」的屏幕出 App。所以还原走 `onDispose`，不依赖任何异步回调。
 */
private const val PAY_CODE_BRIGHTNESS = 1.0

/**
 * 把屏幕亮度提到最高，返回**进页前的值**（供 [restoreScreenBrightness] 写回）。
 *
 * 必须在主线程调用——UIKit 属性，页面 `onClick` / `DisposableEffect` 都在主线程。
 */
fun raiseScreenBrightnessForPayCode(): Double {
    val screen = UIScreen.mainScreen
    val previous = screen.brightness
    if (previous < PAY_CODE_BRIGHTNESS) screen.brightness = PAY_CODE_BRIGHTNESS
    return previous
}

/** 把亮度写回 [previous]（[raiseScreenBrightnessForPayCode] 的返回值）。 */
fun restoreScreenBrightness(previous: Double) {
    UIScreen.mainScreen.brightness = previous
}
