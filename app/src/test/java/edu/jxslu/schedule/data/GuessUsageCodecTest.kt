package edu.jxslu.schedule.data

import edu.jxslu.schedule.data.prefs.decodeGuessUsageLog
import edu.jxslu.schedule.data.prefs.encodeGuessUsageLog
import edu.jxslu.schedule.domain.GuessTarget
import edu.jxslu.schedule.domain.GuessUsage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「猜你想用」习惯事件编解码（DESIGN §4.51）。
 *
 * 数据是自己写自己的，但跨版本 / 手工改坏 / 未来新增服务都可能出现坏条目；
 * 纪律是**只丢坏的那一条**，不让整份习惯表失效。
 */
class GuessUsageCodecTest {

    @Test
    fun `往返保真`() {
        val events = listOf(
            GuessUsage(1_000L, 2, 15, GuessTarget.KVCX),
            GuessUsage(2_000L, 6, 30, GuessTarget.UJING),
        )
        assertEquals(events, decodeGuessUsageLog(encodeGuessUsageLog(events)))
    }

    @Test
    fun `空文本与坏 JSON 退空表`() {
        assertTrue(decodeGuessUsageLog(null).isEmpty())
        assertTrue(decodeGuessUsageLog("").isEmpty())
        assertTrue(decodeGuessUsageLog("{").isEmpty())
        assertTrue(decodeGuessUsageLog("""{"v":1}""").isEmpty())
    }

    @Test
    fun `未知功能与非法字段只丢坏条目`() {
        val text = """
            {"v":1,"events":[
              {"at":1000,"dow":2,"slot":15,"target":"nope"},
              {"at":0,"dow":2,"slot":15,"target":"kvcx"},
              {"at":1000,"dow":0,"slot":15,"target":"kvcx"},
              {"at":1000,"dow":2,"slot":48,"target":"kvcx"},
              {"at":2000,"dow":2,"slot":15,"target":"kvcx"}
            ]}
        """.trimIndent()
        val decoded = decodeGuessUsageLog(text)
        assertEquals(1, decoded.size)
        assertEquals(GuessTarget.KVCX, decoded.single().target)
        assertEquals(2_000L, decoded.single().atMillis)
    }

    @Test
    fun `超过上限只留最近四百条`() {
        val events = (1..450).map { GuessUsage(it.toLong(), 2, 15, GuessTarget.KVCX) }
        val decoded = decodeGuessUsageLog(encodeGuessUsageLog(events))
        assertEquals(400, decoded.size)
        assertEquals(51L, decoded.first().atMillis)
        assertEquals(450L, decoded.last().atMillis)
    }

    @Test
    fun `解码后按时间排序`() {
        val events = listOf(
            GuessUsage(3_000L, 2, 15, GuessTarget.KVCX),
            GuessUsage(1_000L, 2, 15, GuessTarget.KVCX),
            GuessUsage(2_000L, 2, 15, GuessTarget.KVCX),
        )
        assertEquals(
            listOf(1_000L, 2_000L, 3_000L),
            decodeGuessUsageLog(encodeGuessUsageLog(events)).map { it.atMillis },
        )
    }
}
