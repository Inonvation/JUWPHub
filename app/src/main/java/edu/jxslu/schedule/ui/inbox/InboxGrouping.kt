package edu.jxslu.schedule.ui.inbox

import edu.jxslu.schedule.data.local.InboxMessageEntity
import edu.jxslu.schedule.domain.InboxRules
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 收件箱列表的一项：**分组头或消息行**（时间分段「今天 / 昨天 / 本周 / 更早」）。
 *
 * 为什么要把分组头做成独立的列表项，而不是在 `items` 的内容 lambda 里顺手插一个：
 * `LazyColumn` 的内容 lambda **只为可见项、按滚动/复用的时机执行**，不是按列表顺序。
 * 旧实现用 `var lastGroup` 在这个 lambda 里推进——滚出去再滚回来、点进多选导致单行重组、
 * 跨分组快速滑动时，这个变量的值来自「上一次被组合的那一行」，于是分组头会重复出现，
 * 或该出现的没出现（2026-11-06 审计发现的真 bug）。分组头一旦是**独立 item + 稳定 key**，
 * 它就跟消息行一样由列表数据决定，与组合顺序无关。
 *
 * 纯函数（不碰 Compose / Android）：`InboxGroupingTest` 直接钉住分段结果。
 */
internal sealed interface InboxListItem {
    /** LazyColumn 的稳定 key。带前缀防止消息 id 与分组头撞车。 */
    val key: String

    /**
     * 分组头。[occurrence] 是这个桶在列表里第几次出现（从 1 起）。
     *
     * 必须带它：数据不单调时同一个桶会插两个头（见 [inboxListItems] 的 KDoc），
     * 只用桶名做 key 会让两条 `hdr-TODAY` 撞车，`LazyColumn` 直接抛
     * `Key "hdr-TODAY" was already used`（2026-11-06 交叉复核；当前 DAO 排序使它不可达，
     * 但函数契约与用例都声明支持非单调输入，排序一改就是渲染期闪退）。
     */
    data class Header(val group: InboxRules.TimeGroup, val occurrence: Int = 1) : InboxListItem {
        override val key: String get() = "hdr-${group.name}-$occurrence"
    }

    data class Message(val msg: InboxMessageEntity) : InboxListItem {
        override val key: String get() = "msg-${msg.id}"
    }
}

/**
 * 把消息列表（已按 `createdAt` 倒序）展开成「分组头 + 消息行」的平铺列表。
 *
 * 分组判定仍走 [InboxRules.timeGroupOf]（唯一口径，含 ISO 周边界）；本函数只负责
 * 「桶变了就插一个头」。同一桶连续出现只插一次头，桶可以重复出现（数据不单调时）——
 * 这是有意的：宁可多一个头，也不要把不同日期的消息并到同一个头下面。
 * 重复出现的头靠 [InboxListItem.Header.occurrence] 区分 key，不会撞车。
 */
internal fun inboxListItems(
    messages: List<InboxMessageEntity>,
    zone: ZoneId,
    today: LocalDate,
): List<InboxListItem> = buildList {
    var last: InboxRules.TimeGroup? = null
    val occurrences = mutableMapOf<InboxRules.TimeGroup, Int>()
    for (msg in messages) {
        val group = InboxRules.timeGroupOf(Instant.ofEpochMilli(msg.createdAt), zone, today)
        if (group != last) {
            val n = (occurrences[group] ?: 0) + 1
            occurrences[group] = n
            add(InboxListItem.Header(group, n))
            last = group
        }
        add(InboxListItem.Message(msg))
    }
}
