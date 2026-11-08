package edu.jxslu.schedule.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 用水状态与文案换算（DESIGN §3.18）。
 *
 * 金额只认服务端返回的厘值，这里锁的是换算本身——真机上「预扣 0.04 元」显示成
 * 「¥0.04」还是「¥4.00」全靠它。
 */
class QzxyWateringTest {

    @Test
    fun `厘换算成元保留两位`() {
        assertEquals("¥0.04", QzxyWateringFormat.money("40"))
        assertEquals("¥0.00", QzxyWateringFormat.money("0"))
        assertEquals("¥2.00", QzxyWateringFormat.money("2000"))
        assertEquals("¥12.36", QzxyWateringFormat.money("12360"))
    }

    @Test
    fun `金额缺失或不可解析时给兜底文案`() {
        assertEquals("金额待服务端结算", QzxyWateringFormat.money(null))
        assertEquals("金额待服务端结算", QzxyWateringFormat.money(""))
        assertEquals("金额待服务端结算", QzxyWateringFormat.money("—"))
    }

    @Test
    fun `金额两端空白不影响换算`() {
        assertEquals("¥0.04", QzxyWateringFormat.money(" 40 "))
    }

    @Test
    fun `可缺金额的换算在拿不到金额时返回空`() {
        assertEquals("¥0.04", QzxyWateringFormat.moneyOrNull("40"))
        assertEquals(null, QzxyWateringFormat.moneyOrNull(null))
        assertEquals(null, QzxyWateringFormat.moneyOrNull("—"))
    }

    @Test
    fun `已结束用水副行带实扣金额与用时`() {
        // 今日页完成态那一行（2026-11-06 用户要求：关水后要能看出消费金额）。
        // 金额排在最前：单行省略号从右往左吃，长设备名不该把金额截掉。
        assertEquals(
            "实扣 ¥0.52 · 热水器-301 · 用时 12:34",
            QzxyWateringFormat.doneSubtitle(
                deviceName = "热水器-301",
                durationText = "12:34",
                amountMilli = "520",
            ),
        )
    }

    @Test
    fun `已结束用水副行缺金额或缺用时时不留占位`() {
        assertEquals(
            "热水器-301 · 用时 12:34",
            QzxyWateringFormat.doneSubtitle("热水器-301", "12:34", null),
        )
        assertEquals(
            "热水器-301 · 用时 12:34",
            QzxyWateringFormat.doneSubtitle("热水器-301", "12:34", ""),
        )
        assertEquals(
            "实扣 ¥0.52 · 热水器-301",
            QzxyWateringFormat.doneSubtitle("热水器-301", null, "520"),
        )
        assertEquals(
            "热水器-301",
            QzxyWateringFormat.doneSubtitle("热水器-301", null, null),
        )
    }

    @Test
    fun `时长不足一小时用分秒`() {
        assertEquals("00:00", QzxyWateringFormat.duration(0L))
        assertEquals("01:15", QzxyWateringFormat.duration(75_000L))
        assertEquals("59:59", QzxyWateringFormat.duration(3_599_000L))
    }

    @Test
    fun `时长超过一小时带上小时位`() {
        assertEquals("1:00:00", QzxyWateringFormat.duration(3_600_000L))
        assertEquals("1:02:05", QzxyWateringFormat.duration(3_725_000L))
    }

    @Test
    fun `负时长按零处理`() {
        assertEquals("00:00", QzxyWateringFormat.duration(-5_000L))
    }

    @Test
    fun `已用时长在起点晚于当前时刻时为零`() {
        val watering = QzxyWatering(
            startedAtMillis = 10_000L,
            deviceAddress = "A4:C1:38:00:00:01",
            deviceName = "测试热水器",
        )
        assertEquals(0L, watering.elapsedMillis(nowMillis = 9_000L))
        assertEquals(5_000L, watering.elapsedMillis(nowMillis = 15_000L))
    }

    @Test
    fun `开阀时刻按二十四小时制显示`() {
        val watering = QzxyWatering(
            startedAtMillis = 1_700_000_000_000L,
            deviceAddress = "A4:C1:38:00:00:01",
            deviceName = "测试热水器",
            preDeductMilli = "2000",
        )
        assertEquals("2000", watering.preDeductMilli)
        val clock = QzxyWateringFormat.clockText(watering.startedAtMillis)
        assertEquals(5, clock.length)
        assertEquals(":", clock.substring(2, 3))
    }

    @Test
    fun `用水记账一小时后过期`() {
        val start = 1_700_000_000_000L
        val watering = QzxyWatering(
            startedAtMillis = start,
            deviceAddress = "A4:C1:38:00:00:01",
            deviceName = "测试热水器",
        )
        assertEquals(false, watering.isExpired(start + 59L * 60 * 1000))
        assertEquals(false, watering.isExpired(start + QzxyWatering.EXPIRE_MILLIS - 1))
        assertEquals(true, watering.isExpired(start + QzxyWatering.EXPIRE_MILLIS))
        assertEquals(true, watering.isExpired(start + 2L * 60 * 60 * 1000))
        assertEquals(3_600_000L, QzxyWatering.EXPIRE_MILLIS)
    }

    @Test
    fun `过期判断可用自定义阈值`() {
        val watering = QzxyWatering(
            startedAtMillis = 1_000L,
            deviceAddress = "A4:C1:38:00:00:01",
            deviceName = "测试热水器",
        )
        assertEquals(false, watering.isExpired(nowMillis = 2_000L, expireMillis = 5_000L))
        assertEquals(true, watering.isExpired(nowMillis = 2_000L, expireMillis = 1_000L))
    }
}
