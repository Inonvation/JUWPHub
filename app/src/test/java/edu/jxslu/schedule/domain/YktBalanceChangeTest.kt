package edu.jxslu.schedule.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 余额变化 → 补拉消费流水的判定（DESIGN §4.19 / §4.38）。
 *
 * 回归点 = 2026-11-06 用户反馈「余额有变化必须要到账单看一下，收件箱才会有更新」：
 * 余额是「刚发生过交易」的现成信号，变了就补拉一页流水，新的消费随之入箱。
 * 两条边界钉在这里：首次观察只建基线（没得比，白打接口）、余额没变不补拉（每轮核对
 * 都发一条请求就是纯浪费）。
 */
class YktBalanceChangeTest {

    @Test
    fun `首次观察只建基线不补拉`() {
        assertFalse(YktBalanceChange.shouldPullStatement(previousFen = null, currentFen = 9_950L))
    }

    @Test
    fun `余额没变不补拉`() {
        assertFalse(YktBalanceChange.shouldPullStatement(previousFen = 9_950L, currentFen = 9_950L))
    }

    @Test
    fun `余额减少（刷卡消费）要补拉`() {
        assertTrue(YktBalanceChange.shouldPullStatement(previousFen = 9_950L, currentFen = 8_150L))
    }

    @Test
    fun `余额增加（充值到账）也要补拉`() {
        // 入箱那侧只投支出（paymentInboxCandidates），这里补拉是为了流水页的充值行立刻可见
        assertTrue(YktBalanceChange.shouldPullStatement(previousFen = 1_500L, currentFen = 20_000L))
    }

    @Test
    fun `余额归零也算变化`() {
        assertTrue(YktBalanceChange.shouldPullStatement(previousFen = 800L, currentFen = 0L))
    }
}
