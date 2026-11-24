package edu.jxslu.schedule.ui.campus

import edu.jxslu.schedule.SubpageScreen
import edu.jxslu.schedule.domain.InboxKind
import edu.jxslu.schedule.domain.InboxKeys
import edu.jxslu.schedule.domain.MoneyFormat
import edu.jxslu.schedule.domain.YktPayment
import edu.jxslu.schedule.domain.YktTurnoverRow
import edu.jxslu.schedule.platform.Services

/**
 * 一卡通消费入收件箱的唯一实现（2026-10-23）：扫码命中（`PayCodeViewModel`，吃
 * [YktPayment]）与流水同步新见（`YktTurnoverSyncer.postInbox`，吃 [YktTurnoverRow]，
 * 覆盖食堂 POS 直刷等非付款码场景）两条路径共用，文案与键口径只有这一份。
 *
 * 键 = `ykt|pay|` + 服务端订单号，同一次扣款的重复到达同键落更新；缺的字段各段缺省，
 * 不编数据。落点 = 消费流水页；silent = 静默留痕（插入即已读、重投保持已读，
 * 不点红点）。`post` 内部吞错（旁路纪律），留痕失败不影响调用方主链。
 *
 * ## 为什么搬进共享层（iOS 移植 V2，2026-11-25）
 *
 * 它只有「一个收件箱仓库 + 一段文案」，没有一处需要 `Context`——原来那个参数只是
 * `Graph.inboxRepository(context)` 的入口。搬过来之后改为 [Services.current] 取同一个
 * 仓库单例（Android 侧就是 `Graph.inboxRepository` 缓存的那一份），
 * 于是付款码 / 流水两条链在 iOS 上也能留痕。
 *
 * 两个重载从 `internal` 放开成 `public`：跨模块 `internal` 不可见，而 `:app` 的
 * `SubpageActivity` / `PayCodeViewModel` / `CampusCardViewModel` 还在原地调它们
 * （包名不变，调用点只少一个 `context` 实参）。
 */
suspend fun postYktPaymentInbox(row: YktTurnoverRow) =
    postYktPaymentInbox(
        orderId = row.orderId,
        merchant = row.locationName,
        amountFen = row.amountFen,
        balanceAfterFen = row.balanceAfterFen,
    )

suspend fun postYktPaymentInbox(payment: YktPayment) =
    postYktPaymentInbox(
        orderId = payment.orderId,
        merchant = payment.merchant,
        amountFen = payment.amountFen,
        balanceAfterFen = payment.balanceAfterFen,
    )

private suspend fun postYktPaymentInbox(
    orderId: String,
    merchant: String?,
    amountFen: Long,
    balanceAfterFen: Long?,
) {
    Services.current.inboxRepository().post(
        kind = InboxKind.YKT_PAYMENT,
        dedupeKey = InboxKeys.YKT_PAYMENT_PREFIX + orderId,
        title = "一卡通消费",
        body = buildString {
            merchant?.takeIf { it.isNotBlank() }?.let { append(it).append(" · ") }
            append("扣款 ${MoneyFormat.fromFen(amountFen)}")
            balanceAfterFen?.let { append(" · 余额 ${MoneyFormat.fromFen(it)}") }
        },
        route = SubpageScreen.CAMPUS_STATEMENT.name,
        silent = true,
    )
}
