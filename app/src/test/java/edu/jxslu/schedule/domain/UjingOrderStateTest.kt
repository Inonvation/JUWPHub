package edu.jxslu.schedule.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** U净 订单纯逻辑（DESIGN §4.37 P2）：状态映射 / 启停取消窗口 / 受理判定 / 倒计时文案。 */
class UjingOrderStateTest {

    // ---- 状态映射 ----

    @Test
    fun statusTextCoversKnownStates() {
        assertEquals("待支付", UjingState.statusText("10"))
        assertEquals("已支付 · 待启动", UjingState.statusText("20"))
        assertEquals("自洁启动中", UjingState.statusText("22"))
        assertEquals("自洁完成", UjingState.statusText("35"))
        assertEquals("洗涤中", UjingState.statusText("40"))
        assertEquals("已完成", UjingState.statusText("50"))
        assertEquals("已取消", UjingState.statusText("53"))
        assertEquals("已取消", UjingState.statusText("60"))
        // 未知状态不编文案
        assertEquals("状态 99", UjingState.statusText("99"))
    }

    @Test
    fun statusLabelPrefersServerRemark() {
        // 服务端 statusRemark 非空优先
        assertEquals("运行中", UjingState.statusLabel("40", "运行中"))
        // 缺失 / 空串 / 纯空白都退回本地表
        assertEquals("洗涤中", UjingState.statusLabel("40", null))
        assertEquals("洗涤中", UjingState.statusLabel("40", ""))
        assertEquals("洗涤中", UjingState.statusLabel("40", "   "))
        // 未知状态 + 无文案 → 本地兜底格式
        assertEquals("状态 99", UjingState.statusLabel("99", null))
    }

    // ---- 窗口 ----

    @Test
    fun startWindowMatchesCommunitySpec() {
        // 启动窗口只有 20 / 22 / 35（社区实测）
        assertTrue(UjingState.canStart("20"))
        assertTrue(UjingState.canStart("22"))
        assertTrue(UjingState.canStart("35"))
        assertFalse(UjingState.canStart("10"))
        assertFalse(UjingState.canStart("40"))
        assertFalse(UjingState.canStart("50"))
    }

    @Test
    fun cancelWindowOnlyBeforePaid() {
        assertTrue(UjingState.canCancel("0"))
        assertTrue(UjingState.canCancel("10"))
        assertTrue(UjingState.canCancel("17"))
        // 20 起机器已锁定
        assertFalse(UjingState.canCancel("20"))
        assertFalse(UjingState.canCancel("40"))
    }

    @Test
    fun terminalStates() {
        assertTrue(UjingState.isTerminal("50"))
        assertTrue(UjingState.isTerminal("51"))
        assertTrue(UjingState.isTerminal("52"))
        assertTrue(UjingState.isTerminal("53"))
        assertTrue(UjingState.isTerminal("60"))
        assertFalse(UjingState.isTerminal("40"))
        assertFalse(UjingState.isTerminal("20"))
    }

    // ---- 云端控制受理 ----

    @Test
    fun commandAcceptedShapes() {
        // 普通 0 受理
        assertTrue(UjingState.commandAccepted(0, null))
        assertTrue(UjingState.commandAccepted(0, -1))
        // 1703 是设备控制的受理码，内层 errorCode == 0 才算成功
        assertTrue(UjingState.commandAccepted(1703, 0))
        assertFalse(UjingState.commandAccepted(1703, 1))
        assertFalse(UjingState.commandAccepted(1703, null))
        assertFalse(UjingState.commandAccepted(-1, 0))
    }

    // ---- 倒计时文案 ----

    @Test
    fun remainTextFormats() {
        assertEquals("35 分 0 秒", UjingState.remainText(2100))
        // 不足一分钟不带「0 分」前缀
        assertEquals("42 秒", UjingState.remainText(42))
        // 负数（本地时钟追上服务端）夹到 0
        assertEquals("0 秒", UjingState.remainText(-5))
    }

    @Test
    fun liveRemainCountsDownFromSnapshot() {
        // 洗涤中：按快照时刻本地换算（不每秒请求）
        assertEquals(1800, UjingState.liveRemainSeconds("40", 2100, 1_000L, 301_000L))
        // 非洗涤中：原样返回服务端值
        assertEquals(2100, UjingState.liveRemainSeconds("20", 2100, 1_000L, 301_000L))
        // 旧快照无剩余（0）：不动
        assertEquals(0, UjingState.liveRemainSeconds("40", 0, 1_000L, 301_000L))
    }

    // ---- 洗衣完成状态过渡（2026-10-21，收件箱留痕触发口径） ----

    @Test
    fun washDoneTransitOnlyOnNonDoneToDone() {
        // 没有上一份 → 50：首刷就完成（如冷启动后查已结束的订单），算完成事实
        assertTrue(UjingState.transitToWashDone(null, "50"))
        // 洗涤中 → 50：常规完成路径
        assertTrue(UjingState.transitToWashDone("40", "50"))
        // 已支付待启动 → 50：跳过洗涤态直接完成也认
        assertTrue(UjingState.transitToWashDone("20", "50"))
    }

    @Test
    fun washDoneTransitNotFiredOnRepeatOrCancel() {
        // 已是 50 的重刷（页面轮询 / 完成闹钟 / 手动刷新多路到达）：不再算过渡
        assertFalse(UjingState.transitToWashDone("50", "50"))
        // 取消 / 超时 / 启动失败不是完成
        assertFalse(UjingState.transitToWashDone("40", "60"))
        assertFalse(UjingState.transitToWashDone("10", "51"))
        assertFalse(UjingState.transitToWashDone("20", "52"))
        // 完成以外的一切当前态都不触发
        assertFalse(UjingState.transitToWashDone("40", "40"))
        assertFalse(UjingState.transitToWashDone(null, "40"))
    }

    // ---- 今日页取衣提醒（2026-10-29） ----

    @Test
    fun pickupReminderOnlyForCompletedStatus() {
        // 只有完成（50）留提醒；取消 / 超时 / 启动失败没有「衣服还在机器里」
        assertTrue(UjingState.isWashDone("50"))
        assertFalse(UjingState.isWashDone("40"))
        assertFalse(UjingState.isWashDone("51"))
        assertFalse(UjingState.isWashDone("52"))
        assertFalse(UjingState.isWashDone("53"))
        assertFalse(UjingState.isWashDone("60"))
        assertFalse(UjingState.isWashDone(""))
    }

    @Test
    fun pickupReminderWordingDistinguishesDryer() {
        assertEquals("衣物已洗完", UjingState.washDoneTitle(dryer = false))
        assertEquals("衣物已烘干", UjingState.washDoneTitle(dryer = true))
        // 设备名缺失时的兜底同样区分机型
        assertEquals("洗衣机", UjingState.deviceFallback(dryer = false))
        assertEquals("烘干机", UjingState.deviceFallback(dryer = true))
    }

    // ---- 完成通知发送判定（2026-11-06，修「衣服洗好了」反复弹） ----

    @Test
    fun doneNotificationOnlyOnFreshTransitWhileOnFile() {
        // 常规完成：闹钟核对带着洗涤中快照查到 50，订单仍在案 → 发
        assertTrue(UjingState.shouldNotifyWashDone("40", "50", stillOnFile = true))
        // 重启丢闹钟的兜底：快照还停在 40（完成发生在关机期间）→ 冷启动核对发
        assertTrue(UjingState.shouldNotifyWashDone("20", "50", stillOnFile = true))
        assertTrue(UjingState.shouldNotifyWashDone(null, "50", stillOnFile = true))
        // 快照已在案为 50：完成通知早已发过（或用户已在页面看到），
        // 冷启动核对不重发——否则用户每开一次 App 就重弹一条
        assertFalse(UjingState.shouldNotifyWashDone("50", "50", stillOnFile = true))
        // 刷新期间用户已点「我已取走 / 知道了」（快照已清）或已下新单：不发，
        // 回写守卫只挡快照复活，挡不住通知发送，这道闸必须在发送前
        assertFalse(UjingState.shouldNotifyWashDone("40", "50", stillOnFile = false))
        // 非完成态永远不发
        assertFalse(UjingState.shouldNotifyWashDone("40", "40", stillOnFile = true))
        assertFalse(UjingState.shouldNotifyWashDone("40", "60", stillOnFile = true))
    }
}
