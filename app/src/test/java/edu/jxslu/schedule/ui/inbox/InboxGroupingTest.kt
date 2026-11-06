package edu.jxslu.schedule.ui.inbox

import edu.jxslu.schedule.data.local.InboxMessageEntity
import edu.jxslu.schedule.domain.InboxRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * 收件箱列表的分段展开（[inboxListItems]）。
 *
 * 钉住的契约：**分组头是数据的一部分，不是组合期的副作用**。
 * 旧实现把「上一条的桶」放在 `items` 的内容 lambda 里推进，而那个 lambda 只为可见项、
 * 按滚动/复用顺序执行——分组头会重复或缺失（2026-11-06 审计发现的真 bug）。
 * 这里用固定时区与固定「今天」，把分段结果钉成纯数据。
 */
class InboxGroupingTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")

    /** 固定「今天」：2026-11-06 是周五，所在 ISO 周 = 11-02(周一) … 11-08(周日)。 */
    private val today: LocalDate = LocalDate.of(2026, 11, 6)

    private fun msg(id: Long, date: LocalDate, hour: Int = 12): InboxMessageEntity =
        InboxMessageEntity(
            id = id,
            kind = "EXAM",
            dedupeKey = "k$id",
            title = "t$id",
            body = "b$id",
            createdAt = date.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli(),
            readAt = null,
            deletedAt = null,
            route = null,
            focusItemId = null,
        )

    private fun headersOf(items: List<InboxListItem>): List<InboxRules.TimeGroup> =
        items.filterIsInstance<InboxListItem.Header>().map { it.group }

    private fun idsOf(items: List<InboxListItem>): List<Long> =
        items.filterIsInstance<InboxListItem.Message>().map { it.msg.id }

    @Test
    fun `空列表不给任何分组头`() {
        assertEquals(emptyList<InboxListItem>(), inboxListItems(emptyList(), zone, today))
    }

    @Test
    fun `同一桶的多条消息只插一个头`() {
        val rows = inboxListItems(
            listOf(
                msg(3, today, hour = 15),
                msg(2, today, hour = 14),
                msg(1, today, hour = 13),
            ),
            zone,
            today,
        )
        assertEquals(listOf(InboxRules.TimeGroup.TODAY), headersOf(rows))
        assertEquals(listOf(3L, 2L, 1L), idsOf(rows))
        // 头在前、消息顺序不变：头 + 3 行 = 4 项
        assertEquals(4, rows.size)
        assertTrue(rows.first() is InboxListItem.Header)
    }

    @Test
    fun `跨三个桶各插一个头且顺序跟随输入`() {
        val rows = inboxListItems(
            listOf(
                msg(4, today),
                msg(3, today.minusDays(1)),
                msg(2, LocalDate.of(2026, 10, 20)),
                msg(1, LocalDate.of(2026, 9, 1)),
            ),
            zone,
            today,
        )
        assertEquals(
            listOf(
                InboxRules.TimeGroup.TODAY,
                InboxRules.TimeGroup.YESTERDAY,
                InboxRules.TimeGroup.OLDER,
            ),
            headersOf(rows),
        )
        assertEquals(listOf(4L, 3L, 2L, 1L), idsOf(rows))
    }

    @Test
    fun `桶重复出现时允许重复插头而不是并桶`() {
        // 数据不单调（例如某条消息被重投刷新了 createdAt）：宁可多一个头，
        // 也不要把 9 月的消息并进「今天」那个头下面。
        val rows = inboxListItems(
            listOf(
                msg(3, today),
                msg(2, LocalDate.of(2026, 9, 1)),
                msg(1, today),
            ),
            zone,
            today,
        )
        assertEquals(
            listOf(
                InboxRules.TimeGroup.TODAY,
                InboxRules.TimeGroup.OLDER,
                InboxRules.TimeGroup.TODAY,
            ),
            headersOf(rows),
        )
        // 两个 TODAY 头的 key 必须不同，否则 LazyColumn 会抛「Key … was already used」
        val keys = rows.map { it.key }
        assertEquals(keys.size, keys.distinct().size)
    }

    @Test
    fun `本周桶按ISO周边界`() {
        // 周一 11-02 起算：本周一 → THIS_WEEK；上周日 11-01 → OLDER（距今 5 天也不并进本周）
        val rows = inboxListItems(
            listOf(
                msg(2, LocalDate.of(2026, 11, 2)),
                msg(1, LocalDate.of(2026, 11, 1)),
            ),
            zone,
            today,
        )
        assertEquals(
            listOf(InboxRules.TimeGroup.THIS_WEEK, InboxRules.TimeGroup.OLDER),
            headersOf(rows),
        )
    }

    @Test
    fun `key稳定且分组头与消息不撞车`() {
        val rows = inboxListItems(
            listOf(msg(7, today), msg(8, today.minusDays(1))),
            zone,
            today,
        )
        val keys = rows.map { it.key }
        assertEquals(keys.size, keys.distinct().size)
        assertEquals(listOf("hdr-TODAY-1", "msg-7", "hdr-YESTERDAY-1", "msg-8"), keys)
    }
}
