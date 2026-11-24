package edu.jxslu.schedule.ui.common

import androidx.compose.runtime.Composable

/**
 * 「回到前台」计数：每次宿主窗口从后台（或被另一个 Activity 盖住）回到前台 +1。
 *
 * 用作 `remember` 的 key，强制重读一次只读快照（凭证在不在、CookieManager 里有没有会话）。
 * 为什么需要它：Compose 的组合在「被另一个 Activity 盖住又回来」时**不重建**，
 * `remember` 里的值原样留着；而登录态这类快照是组合期读一次的，从引导页 / 导入页 /
 * 校园卡设置页返回后不重读，卡片就停在旧状态（2026-09-27 用户报：在导入课表里登录过
 * 教务，「我的」页仍写着未登录）。
 *
 * **注册时补发的那次 ON_RESUME 不算**——它就是「进页」本身，首帧已经读过一次了；
 * 只有真正的「回来」才 +1，避免进页时白读两遍。
 *
 * ## 为什么是 expect/actual（iOS 移植 S17，2026-11-25）
 *
 * `LocalLifecycleOwner` 与 `LifecycleEventObserver` 都活在 `androidx.lifecycle` 的 Android
 * 变体里，`commonMain` 看不见（与 `ResumedTicker.kt` 同一条约束）。`SettingsScreen` 搬进
 * 共享层时把这条缝一起带过来，其余 6 个 `:app` 调用点包名没变、只换了所在模块。
 *
 * | 平台 | 行为 |
 * |---|---|
 * | Android | 真实计数：离开 RESUMED 再回来才 +1 |
 * | iOS | **恒返回 0**：CMP 1.7.3 的 iOS 侧没有 LifecycleOwner 口径，退后台不销毁组合，也就没有「回来」这一拍 |
 *
 * iOS 侧退化成 0 不影响正确性：这一拍只是「再查一次」的触发器，不是数据的唯一来源。
 */
@Composable
expect fun rememberResumeTick(): Int
