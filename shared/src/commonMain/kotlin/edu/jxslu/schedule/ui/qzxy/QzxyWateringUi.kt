package edu.jxslu.schedule.ui.qzxy

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import edu.jxslu.schedule.domain.AppClock
import edu.jxslu.schedule.domain.QzxyWatering
import edu.jxslu.schedule.domain.QzxyWateringFormat
import edu.jxslu.schedule.ui.common.ResumedTickerEffect
import edu.jxslu.schedule.ui.common.rememberResumeTick
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 用水计时的每秒刷新（DESIGN §3.18）。
 *
 * 趣智校园页与今日页卡片都要显示「用水中 12:35」，两处各写一份 tick 迟早会不一致，
 * 收在这里。切走页面时 Composable 离开组合，循环跟着停，不会在后台空转；
 * 用时超过过期线（[QzxyWatering.EXPIRE_MILLIS]，1 小时）也停——过期文案由
 * [isQzxyWateringExpired] 接管，计时再跳没有意义，不能进页就永久每秒唤醒。
 */
@Composable
fun rememberQzxyWateringClock(startedAtMillis: Long): String {
    val text by produceState(
        initialValue = QzxyWateringFormat.duration(AppClock.nowMillis() - startedAtMillis),
        key1 = startedAtMillis,
    ) {
        while (AppClock.nowMillis() - startedAtMillis < QzxyWatering.EXPIRE_MILLIS) {
            value = QzxyWateringFormat.duration(AppClock.nowMillis() - startedAtMillis)
            delay(1_000L)
        }
        // 退出即定格真实经过时间（可能已超 1 小时）：硬截到 EXPIRE_MILLIS 会把
        // 「离开几分钟后回来」的真实时长抹成整 1:00:00
        value = QzxyWateringFormat.duration(AppClock.nowMillis() - startedAtMillis)
    }
    return text
}

/**
 * 用水状态是否已过期（超过 [QzxyWatering.EXPIRE_MILLIS]，1 小时）。
 *
 * 过期只是**展示口径**：状态卡与卡片把它当「待核对」渲染（没有进行中的计时），
 * 记账本身由结算成功、清除设备记录或用户显式「已到账」清理——2026-10-09 起
 * 不再静默清掉，清了会让预扣的去向失去界面痕迹。
 */
fun isQzxyWateringExpired(startedAtMillis: Long): Boolean =
    QzxyWatering(startedAtMillis, "", "").isExpired(AppClock.nowMillis())

/** 对账两次读取之间的间隔。见 [WateringReconcileEffect]。 */
private const val WATERING_RECONCILE_INTERVAL_MILLIS = 30_000L

/**
 * 用水对账的驱动（DESIGN §3.18）：页面/面板可见（RESUMED）期间周期调
 * [QzxyViewModel.reconcileWateringOnce]，把本地记账与设备真实状态核对——
 * 设备自动关停（停水过一阵，或一直用到预扣耗尽）没有可预测的时刻，只能这样反应式发现：
 * 读到「消费完成，待采集」就自动结算，读到空闲就把记账切「待核对」口径。
 *
 * [enabled] 为 false（本地没有记账）时不跑；[QzxyViewModel.reconcileWateringOnce] 返回
 * false 即停到本次前台周期结束（没有记账 / 到终端态 / 连续读不到设备）；回到前台时
 * 复位退避并立即再来一拍。与 `ResumedTickerEffect` 同一条生命周期口径：灭屏 /
 * 退后台立刻停，回前台立刻续。
 *
 * ## iOS 移植 V3（2026-11-25）：`repeatOnLifecycle` 换成共享层的两条缝
 *
 * 原来是 `LocalLifecycleOwner` + `repeatOnLifecycle(RESUMED)`，两者都在 `androidx.lifecycle`
 * 的 Android 变体里，`commonMain` 看不见。现在拆成两半、语义对齐搬迁前：
 *
 * | 原来的一步 | 现在 |
 * |---|---|
 * | 进 RESUMED 时 `onWateringWatchResumed()` 复位退避 | `rememberResumeTick()` 每次回前台 +1，`LaunchedEffect(tick)` 里复位 |
 * | RESUMED 期间每 30 秒对一次账 | `ResumedTickerEffect(30s)`（Android 仍是 `repeatOnLifecycle`，退后台即停） |
 *
 * 差别只有一处：原来「对账返回 false 就跳出循环、本次前台周期内不再尝试」，现在
 * 每 30 秒仍会再调一次 `reconcileWateringOnce()`。它自己会按
 * `WATERING_POLL_MAX_FAILURES` 与记账状态短路（读不到设备 / 已到终端态都直接返回
 * false，不发帧），所以多出来的调用不产生蓝牙流量，只是每 30 秒读一次内存状态。
 */
@Composable
fun WateringReconcileEffect(viewModel: QzxyViewModel, enabled: Boolean) {
    val resumeTick = rememberResumeTick()
    LaunchedEffect(resumeTick, enabled) {
        if (enabled) viewModel.onWateringWatchResumed()
    }
    // `reconcileWateringOnce` 是挂起函数（要发蓝牙帧），而 tick 回调不是：
    // 每拍开一个页面作用域的协程。页面离开组合时它随作用域取消，不会泄漏。
    val scope = rememberCoroutineScope()
    ResumedTickerEffect(WATERING_RECONCILE_INTERVAL_MILLIS) {
        if (enabled) scope.launch { viewModel.reconcileWateringOnce() }
    }
}
