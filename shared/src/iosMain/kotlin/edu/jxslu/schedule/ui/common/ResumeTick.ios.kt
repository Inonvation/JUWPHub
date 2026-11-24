package edu.jxslu.schedule.ui.common

import androidx.compose.runtime.Composable

/**
 * [rememberResumeTick] 的 iOS 实现：**恒返回 0**。
 *
 * CMP 1.7.3 的 iOS 侧没有 `LocalLifecycleOwner` 口径（要 Compose 1.9 的
 * `lifecycle-runtime-compose` KMP 变体，本仓锁 1.7.3），所以数不出「回到前台」这一拍。
 * 退化成常量不会让页面停在错误状态：调用方拿它当 `remember` 的 key 或
 * `LaunchedEffect` 的 key，值为 0 就等于「进页读一次」，正是 iOS 上想要的行为
 * （App 切后台不销毁组合，回来时数据流自己会推新值）。
 *
 * 升级条件：CMP 升到 1.9 之后换成 `androidx.lifecycle.compose.LocalLifecycleOwner`
 * 的真实计数，与 Android 侧逐字对齐。
 */
@Composable
actual fun rememberResumeTick(): Int = 0
