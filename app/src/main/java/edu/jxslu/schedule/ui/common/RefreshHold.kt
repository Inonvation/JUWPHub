package edu.jxslu.schedule.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

/**
 * 下拉刷新指示器的最短驻留（2026-11-05）。与天气页原来的 `MIN_REFRESH_MILLIS` 同值，
 * 现在只有这一处定义。
 */
const val RefreshMinDwellMillis: Long = 650L

/**
 * 下拉刷新的「驻留闸」判据（纯 JVM，`RefreshHoldGateTest` 钉住）。
 *
 * **为什么需要它**：M3 1.3.0 的 `PullToRefreshModifierNode` 把 `isRefreshing` 当**跳变信号**
 * 用——每次变化都起一次动画（true → `animateToThreshold()`，false → `animateToHidden()`），
 * 中间没有任何缓冲；`Animatable` 的互斥会让后一次动画把前一次在空中掉头。所以只要刷新期间
 * `isRefreshing` 掉过一次电平，屏幕上就是「指示器滑出 → 缩回 → 再滑出」的抽搐。
 *
 * 本仓原来把它直接接到各 ViewModel 的 loading 标志上，于是有三类必然掉电平：
 * 1. 刷新太快（缓存命中 / 未登录 / 秒失败）：指示器还没落位就被收回；
 * 2. 一次下拉发多个请求，请求之间标志归零；
 * 3. 进页 / 回层的后台加载把 `isRefreshing` 提前置真，M3 的 `onRelease()` 开头
 *    `if (isRefreshing) return 0f` 会把这次下拉整个吞掉，加载结束时指示器被硬拽回去。
 *
 * 三条口径：
 * - **只有 [begin]（用户真下拉）能点亮**——后台加载不再吞下拉；
 * - **点亮后只在「不 busy 且距 begin 已满 [minDwellMillis]」才收**——快速刷新与逐请求抖动
 *   都不再透传；
 * - **busy 变化只续命**，计时基准固定在 begin，不会被重启成无限驻留。
 */
internal class RefreshHoldGate(private val minDwellMillis: Long = RefreshMinDwellMillis) {

    private var beganAtMillis = 0L
    private var holding = false

    /** 用户下拉（`onRefresh` 回调里同步调用）：点亮并重新计时。 */
    fun begin(nowMillis: Long) {
        holding = true
        beganAtMillis = nowMillis
    }

    /** 推进一帧，返回该帧是否还该转指示器。 */
    fun settle(busy: Boolean, nowMillis: Long): Boolean {
        if (holding && !busy && nowMillis - beganAtMillis >= minDwellMillis) holding = false
        return holding
    }

    /** 距离驻留期满还有多少毫秒（已满 / 没在下拉时为 0），给副作用决定要等多久。 */
    fun remainingMillis(nowMillis: Long): Long {
        if (!holding) return 0L
        val left = minDwellMillis - (nowMillis - beganAtMillis)
        return if (left > 0L) left else 0L
    }
}

/**
 * [rememberRefreshHold] 的句柄：`isRefreshing` 直接喂给 `PullToRefreshBox` /
 * `Modifier.pullToRefresh` / `PullToRefreshDefaults.Indicator`，`begin()` 在 `onRefresh` 里调。
 */
@Stable
class RefreshHoldState internal constructor(internal val gate: RefreshHoldGate) {

    internal var refreshing by mutableStateOf(false)
    internal var pullTick by mutableIntStateOf(0)

    /** 该不该转指示器。 */
    val isRefreshing: Boolean get() = refreshing

    /**
     * 用户下拉成功（M3 只在拉过阈值松手时回调 `onRefresh`）：**必须同步调用**——
     * ViewModel 的刷新协程要下一帧才把 loading 置真，这一帧的窗口就靠它兜住。
     */
    fun begin() {
        gate.begin(System.currentTimeMillis())
        refreshing = true
        pullTick++
    }
}

/**
 * 把一次下拉刷新收敛成「点亮 → 至少 [minDwellMillis] → 熄灭」，中间任何 loading 抖动都不透传。
 *
 * 用法：
 * ```
 * val hold = rememberRefreshHold(busy = state.refreshing)
 * PullToRefreshBox(
 *     isRefreshing = hold.isRefreshing,
 *     onRefresh = { hold.begin(); vm.refresh() },
 * ) { … }
 * ```
 *
 * [busy] 传该页「刷新是否还在跑」的原表达式（各页口径不同，别统一改成 `loading`）：
 * 它只负责续命，不负责点亮——后台加载不算下拉。
 */
@Composable
fun rememberRefreshHold(
    busy: Boolean,
    minDwellMillis: Long = RefreshMinDwellMillis,
): RefreshHoldState {
    val hold = remember(minDwellMillis) { RefreshHoldState(RefreshHoldGate(minDwellMillis)) }
    LaunchedEffect(busy, hold.pullTick) {
        hold.refreshing = hold.gate.settle(busy, System.currentTimeMillis())
        if (!hold.refreshing || busy) return@LaunchedEffect
        delay(hold.gate.remainingMillis(System.currentTimeMillis()))
        hold.refreshing = hold.gate.settle(false, System.currentTimeMillis())
    }
    return hold
}
