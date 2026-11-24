package edu.jxslu.schedule.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State

/**
 * 页面级定时器的生命周期口径（2026-10-02 收口；iOS 移植 M1 拆成两端）。
 *
 * 此前散在各页的 `LaunchedEffect(Unit) { while(true) { tick; delay } }` 只随「组合离开」
 * 停——App 退到后台 / 灭屏时组合还在，循环照跑：每 30 秒唤醒进程 + 重组整页的时钟
 * （今日页 / 课表页）、每秒走秒（骑行卡）、每 15 秒重算「更新于」（车辆面板）。
 * 全部收口成 `repeatOnLifecycle(RESUMED)`：**灭屏或退后台立刻停，回前台立刻续**。
 * delay 的最后一次到点若恰好落在后台，回前台后第一拍立即补上，不丢拍。
 *
 * ## 为什么现在是 expect/actual
 *
 * `LocalLifecycleOwner` 与 `repeatOnLifecycle` 都活在 `androidx.lifecycle` 的 Android 变体里，
 * `commonMain` 看不见（KMP 版要 Compose 1.9，本仓锁 1.7.3）。两端因此各持一份**语义等价**
 * 的实现，差别只在「暂停条件」：
 *
 * | 平台 | 暂停条件 |
 * |---|---|
 * | Android | 生命周期离开 RESUMED（与搬迁前一致） |
 * | iOS | 无暂停——组合在，心跳就在。CMP 1.7.3 的 iOS 侧没有 LifecycleOwner 口径 |
 *
 * 调用点两个平台一个字不用改。
 */

/** 周期回调型：每 [intervalMillis] 跑一次 [onTick]，仅 RESUMED 期间（刷新整页状态的那类）。 */
@Composable
expect fun ResumedTickerEffect(intervalMillis: Long, onTick: () -> Unit)

/**
 * 读值型：RESUMED 期间每 [intervalMillis] 更新一次「现在」的 [State]，
 * 非 RESUMED 时循环挂起、值定格（读值消费方也停重组，两不耽误）。
 */
@Composable
expect fun rememberResumedNow(intervalMillis: Long, key: Any? = null): State<Long>
