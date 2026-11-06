package edu.jxslu.schedule.domain

/**
 * 「余额变了 → 补拉一次消费流水」的判定（纯逻辑，DESIGN §4.19 / §4.38）。
 *
 * 背景（2026-11-06 用户反馈）：一卡通消费入收件箱的唯一挂点在流水同步收尾
 * （`YktTurnoverSyncer.paymentInboxCandidates`），而流水同步原先只由「进消费流水页 /
 * 付款码页盯单 / 充值到账核对」触发——食堂 POS 直刷这类没有付款码页的消费，
 * 收件箱里那条「一卡通消费」要等用户自己打开账单页才出现。
 *
 * 余额是「刚发生过交易」的现成信号：两次观察之间余额不同，说明中间有扣款或入账，
 * 补拉一页最新流水就能把新消费补进收件箱。**方向不区分**：充值到账（余额上涨）同样
 * 值得拉一次——流水页的充值行也随之立刻可见；入箱那侧只投支出，收入有专门键。
 *
 * 首次观察（[previousFen] 为 null）**只建基线不补拉**：没有可比的上一次，补拉是白打
 * 一次接口；刚装机/刚开启的流水本来也会在首次进页时全量同步。
 */
object YktBalanceChange {

    /**
     * [previousFen] = 上次已知的正式卡余额（分，null = 从未记录过）；
     * [currentFen] = 这次刚取到的正式卡余额（分）。要不要补拉一页流水。
     */
    fun shouldPullStatement(previousFen: Long?, currentFen: Long): Boolean =
        previousFen != null && previousFen != currentFen
}
