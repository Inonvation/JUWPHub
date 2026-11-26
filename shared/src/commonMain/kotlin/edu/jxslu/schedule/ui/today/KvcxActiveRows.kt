package edu.jxslu.schedule.ui.today

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import edu.jxslu.schedule.domain.EbikeFreeRide
import edu.jxslu.schedule.domain.KqcxAuth
import edu.jxslu.schedule.domain.MoneyFormat
import edu.jxslu.schedule.ui.common.rememberAppHaptics
import edu.jxslu.schedule.ui.common.rememberResumedNow
import edu.jxslu.schedule.ui.ebike.KvcxAction
import edu.jxslu.schedule.ui.ebike.KvcxReturnSummary
import edu.jxslu.schedule.ui.ebike.KvcxRideState
import edu.jxslu.schedule.ui.ebike.rideElapsedSeconds
import edu.jxslu.schedule.ui.ebike.unlockCooldownSecondsLeft
import edu.jxslu.schedule.ui.theme.semanticColors

/**
 * 今日页服务卡里**快趣骑行**的两行：进行中（[KvcxActiveRow]）与已还车完成态（[KvcxDoneRow]）。
 *
 * ## 为什么现在能上移（2026-11-26）
 *
 * 这两行原在 `:app` 的 `ui/today/ActiveServiceRows.kt`，搬不动的原因只有一个：
 * 它吃的 `KvcxRideState` / `KvcxAction` / `KvcxReturnSummary` / `unlockCooldownSecondsLeft`
 * 都在 `:app` 的 `ui/ebike`（高德 SDK 那一包，iOS 结构性做不到）。
 * 快趣页面与状态类型整体搬进 `shared/commonMain` 之后，跨模块的那层阻碍消失，
 * 只剩品牌位图 `R.drawable.ic_brand_kqcx` 一处平台耦合——而它可以用参数走掉，见下。
 *
 * ## 品牌位图为什么走参数
 *
 * `R` 只存在于 Android 的 `:app`（`app/src/main/res`），commonMain 看不见它。
 * 所以两个函数各带 `brandId: Int? = null`，原样交给 [ActiveRow] 的 `brand`。
 * `null` = 本平台没有这张品牌位图（iOS）：`BrandMark` 的 actual 在 iOS 上本来就渲染为空，
 * 行照旧渲染、只是不画图标位，与「平台动作可空回调」同一语义，不画一个假图标占位。
 * 默认 `null` 让 iOS 侧连这个参数都不用写；Android 在 `TodayHost` 传 `R.drawable.ic_brand_kqcx`。
 *
 * ## 与 ActiveServiceCard.kt 的关系
 *
 * 行骨架 [ActiveRow]、动作钮 [ActionChip]、完成态的勾 [DoneCheck] 都在同包同模块的
 * `ActiveServiceCard.kt` 里，直接调用即可（原先为跨模块而放开的 `public` 现在连跨模块都省了）。
 * 行高 / 按下手感 / 进度条口径因此与开水、趣智、U净 三行仍**同一份实现**，本文件只保留
 * 快趣特有的判据与文案。卡片把这一行收成 [ActiveServiceSlot]（`null` = 本平台没有快趣骑行），
 * 内容由这里给。
 */

/**
 * 快趣骑行行：副行 = 车号 · 已骑时长（本地走时）· 电量；右侧「开锁⇄锁车」+「还车」。
 * 行本体点击进骑行页（[onOpen]）——完整骑行卡 / 地图在那里；行内按钮管动作，行本体管「去看」。
 *
 * 锁态口径（与骑行页同源）：`locked == false` 显「锁车」（免确认）；`locked == true`
 * 显「开锁」；悬置态（开锁未确认 / 锁车确认中）显「确认中…」置灰。开锁后 3 秒冷却内
 * **「开锁 / 锁车 / 还车」一起置灰并显示剩余秒**（2026-10-29 用户口径「都置灰」，
 * 与骑行页同口径）。还车与骑行页共用同一确认闸（调用方接 `kvcxConfirmDialog`）。
 *
 * @param brandId 品牌位图资源 id；`null` = 本平台没有这张位图（iOS），不画图标位。
 */
@Composable
public fun KvcxActiveRow(
    ride: KqcxAuth.Ride,
    state: KvcxRideState,
    /** 免费窗口起点（`ebikeRideStartAt`）；null / ≤0 = 没有计时，不画进度条。 */
    freeStartAt: Long?,
    onUnlock: () -> Unit,
    onLock: () -> Unit,
    onReturn: () -> Unit,
    onOpen: () -> Unit,
    brandId: Int? = null,
) {
    val haptics = rememberAppHaptics()
    // 骑行行没有「到点停表」的语义：只要这行在组合里就要逐秒刷新（免费窗口倒计时 / 超时分钟数 /
    // 动作已等秒数 / 开锁冷却），故直接走公共件 `rememberResumedNow`（RESUMED 才走）。
    val now by rememberResumedNow(1_000L, key = ride.carNum to state.fetchedAt)
    val elapsed = rideElapsedSeconds(ride, state.fetchedAt, now)
    val locked = ride.locked == true
    val pending = state.unlockPending || state.lockPending
    val busy = state.busy
    // 动作跑起来之后按钮要说话（2026-10-29）：旧版还车只把两枚钮一起置灰，用户看不出
    // 点上了没有。还车链路最坏可挂一两分钟（定位 + 多次查询各自超时），带上已等秒数。
    val busySeconds = if (busy != null && state.busyAt > 0L) {
        ((now - state.busyAt) / 1_000L).toInt().coerceAtLeast(0)
    } else {
        0
    }
    val cooldownLeft = unlockCooldownSecondsLeft(state.unlockCooldownUntil, now)

    // 免费窗口进度（2026-10-22）：15 分钟内倒计时条（与骑行页同一条口径），
    // 过点后切成 error 色的超时增长条；状态词报「超时 X 分钟」，进度条按 15 分钟一格
    // 增长（见 EbikeFreeRide.overtimeProgressFraction）。锁车后照挂：计费仍在继续。
    val freeStart = freeStartAt?.takeIf { it > 0L }
    val overtime = freeStart != null && !EbikeFreeRide.isActive(freeStart, now)
    val overtimeMinutes = if (overtime) {
        EbikeFreeRide.overtimeMinutes(freeStart, now).coerceAtLeast(1)
    } else {
        0
    }
    val freeProgress = when {
        freeStart == null -> null
        overtime -> EbikeFreeRide.overtimeProgressFraction(freeStart, now)
        else -> EbikeFreeRide.progressFraction(freeStart, now)
    }
    val status = when {
        overtime -> "超时 $overtimeMinutes 分钟"
        ride.locked == false && freeStart != null ->
            "骑行中 · " + EbikeFreeRide.formatRemaining(
                EbikeFreeRide.remainingSeconds(freeStart, now),
            )
        ride.locked == false -> "骑行中"
        else -> null
    }
    val statusColor = if (overtime) MaterialTheme.colorScheme.error else null

    ActiveRow(
        brand = brandId,
        title = "快趣骑行",
        status = status,
        statusColor = statusColor,
        progress = freeProgress,
        progressError = overtime,
        onClick = onOpen,
        onClickLabel = "查看骑行详情",
        subtitle = buildString {
            append(ride.carNum)
            elapsed?.let { append(" · 已骑 ").append(KqcxAuth.formatRideDuration(it)) }
            ride.batteryPercent?.let { append(" · 电量 ").append(it).append("%") }
            // 临时锁车 ≠ 订单结束：不写清楚，用户会以为已经还了车（与骑行页状态胶囊同口径）
            if (ride.locked == true) append(" · 已锁车（计费继续）")
        },
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                when {
                    // 动作在跑：只留一枚置灰的「还车中… 12s」+ 转圈。旧版是「确认中…」
                    // 旁边挂一枚禁用的「还车」，看不出是哪一个动作在跑（2026-10-29）
                    busy != null -> ActionChip(
                        label = buildString {
                            append(busy.label())
                            if (busySeconds > 0) append(" ").append(busySeconds).append("s")
                        },
                        enabled = false,
                        busy = true,
                        onClick = {},
                    )
                    // 悬置（指令已发、车侧还没回报）：只置灰，不加转圈——这一段可能挂到
                    // 下次刷新才消解，转圈转久了反而像卡死
                    pending -> ActionChip(label = "确认中…", enabled = false, onClick = {})
                    else -> {
                        // 开锁 ⇄ 锁车（免确认的是锁车；开锁走重发解锁那条现成路）
                        // 冷却期两枚都置灰（2026-10-29）：已锁态这枚也是物理写，
                        // 「重新使用该车」建单后服务端还在落账，早点点下去没有结果
                        ActionChip(
                            label = when {
                                locked && cooldownLeft > 0 -> "开锁 ${cooldownLeft}s"
                                locked -> "开锁"
                                cooldownLeft > 0 -> "锁车 ${cooldownLeft}s"
                                else -> "锁车"
                            },
                            enabled = cooldownLeft <= 0,
                            onClick = {
                                haptics.tap()
                                if (locked) onUnlock() else onLock()
                            },
                        )
                        Spacer(Modifier.width(6.dp))
                        ActionChip(
                            label = if (cooldownLeft > 0) "还车 ${cooldownLeft}s" else "还车",
                            enabled = cooldownLeft <= 0,
                            filled = false,
                            onClick = {
                                haptics.tap()
                                onReturn()
                            },
                        )
                    }
                }
            }
        },
    )
}

/** 忙碌动作的按钮文案（与骑行页底栏同族：开锁中… / 锁车中… / 还车中…）。 */
private fun KvcxAction.label(): String = when (this) {
    KvcxAction.UNLOCK, KvcxAction.DIRECT_UNLOCK, KvcxAction.RETRY_UNLOCK, KvcxAction.RESUME -> "开锁中…"
    KvcxAction.LOCK -> "锁车中…"
    KvcxAction.RETURN -> "还车中…"
    KvcxAction.RING -> "响铃中…"
    KvcxAction.QUERY_LOCK -> "查询中…"
}

/**
 * 快趣「已还车」完成态（2026-10-29，停留 8 秒 / 可点 2026-10-31）：订单结束、行数据消失后停留。
 * 副行 = 车号 · 本次时长 · 费用（`returnSummary` 就是还车那一刻算出来的那一份）。
 * 点整行进骑行页——完成态也要能看结果明细（用户要求）。
 *
 * 品牌位图与勾都走共享层（`BrandMark` / `DoneCheck`）：完成态与进行中行的行高、字号、
 * 左右留白必须是同一份实现，各画一份必然差几个 dp。
 *
 * @param brandId 品牌位图资源 id；`null` = 本平台没有这张位图（iOS），不画图标位。
 */
@Composable
public fun KvcxDoneRow(
    summary: KvcxReturnSummary?,
    onOpen: () -> Unit,
    brandId: Int? = null,
) {
    ActiveRow(
        brand = brandId,
        title = "快趣骑行",
        status = "已还车",
        pulsing = false,
        // 完成态状态词与行尾勾同色（success）：别让「已还车」还是进行中的主色
        statusColor = MaterialTheme.semanticColors.success,
        onClick = onOpen,
        onClickLabel = "查看骑行详情",
        subtitle = buildString {
            summary?.carNum?.let { append("车 ").append(it) }
            summary?.durationSeconds?.let { seconds ->
                if (isNotEmpty()) append(" · ")
                append("已骑 ").append(KqcxAuth.formatRideDuration(seconds))
            }
            summary?.feeCents?.let { cents ->
                if (isNotEmpty()) append(" · ")
                append(MoneyFormat.fromFen(cents))
            }
        }.ifBlank { "订单已结束" },
        trailing = { DoneCheck() },
    )
}
