package edu.jxslu.schedule

import edu.jxslu.schedule.data.ykt.YktPaymentAttempt
import edu.jxslu.schedule.data.ykt.YktRechargeOrder
import edu.jxslu.schedule.data.ykt.firstOpened
import edu.jxslu.schedule.data.ykt.paymentAttempts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 一卡通充值「按什么顺序把支付页递出去」的契约（`design/ios-port.md` §10.4）。
 *
 * 背景：`POST /charge/order/thirdOrder` 之后平台会同时给出两条路——直拉微信
 * （`weixin://wap/pay?prepayid=…`）与官方收银台（`/payment/?synjones-auth=…`）。
 * Android 只用前者（`ACTION_VIEW` 一句话拉起微信，打不开就报「未找到微信」）。
 *
 * iOS 在 2026-11-28 之前直接改走收银台页，依据是「从原生 App 直拉 `weixin://` 会被微信拒
 * （商户域名与 referer 对不上）」——那是推测，Scriptable 版实测能拉起微信付款页
 * （`docs/ios-paycode-shortcut/说明.md`）。现在改成**先试直拉、系统不接受再退收银台页**，
 * 两种可能都兜住。顺序只有一处实现（`YktRechargeOrder.paymentAttempts`）与一处执行
 * （`List<YktPaymentAttempt>.firstOpened`），本测试钉住这两处。
 */
class YktRechargePaymentUrlTest {

    private val wechatUrl = "weixin://wap/pay?prepayid%3Dwx123"
    private val cashierUrl = "https://yktwx.juwp.edu.cn/payment/?synjones-auth=t&orderid=A1"

    private val wechatPay = YktRechargeOrder.WechatPay(
        orderId = "A1",
        wechatUrl = wechatUrl,
        cashierUrl = cashierUrl,
    )

    private val cashier = YktRechargeOrder.Cashier(
        orderId = "A2",
        cashierUrl = cashierUrl,
    )

    @Test
    fun android_triesOnlyTheDirectWechatDeepLink() {
        // Android 保持原行为：直拉微信最快，不经过中间页，且**不退**收银台页
        val attempts = wechatPay.paymentAttempts(preferCashierPage = false)
        assertEquals(listOf(YktPaymentAttempt.WechatDeepLink(wechatUrl)), attempts)
        assertTrue(attempts.none { it is YktPaymentAttempt.CashierPage })
    }

    @Test
    fun ios_triesDirectWechatFirst_thenFallsBackToCashierPage() {
        // iOS 两条都试，顺序不能反：先直拉（少两跳），打不开才退税银台页
        assertEquals(
            listOf(
                YktPaymentAttempt.WechatDeepLink(wechatUrl),
                YktPaymentAttempt.CashierPage(cashierUrl),
            ),
            wechatPay.paymentAttempts(preferCashierPage = true),
        )
    }

    @Test
    fun cashierOnlyOrder_isTheSameOnBothPlatforms() {
        // 平台没给直拉链接时（兜底）两边都只有收银台地址，取值不该因平台变
        val expected = listOf(YktPaymentAttempt.CashierPage(cashierUrl))
        assertEquals(expected, cashier.paymentAttempts(preferCashierPage = false))
        assertEquals(expected, cashier.paymentAttempts(preferCashierPage = true))
    }

    @Test
    fun android_doesNotFallBackToCashierPage() {
        // 直拉失败就该报错（「未找到微信」），不能偷偷改成打开收银台页——
        // 那是 Android 侧从未有过的行为
        val opened = wechatPay.paymentAttempts(preferCashierPage = false)
            .firstOpened { false }
        assertNull(opened)
    }

    @Test
    fun ios_fallsBackToCashierPageWhenTheDeepLinkCannotOpen() {
        val tried = mutableListOf<YktPaymentAttempt>()
        val opened = wechatPay.paymentAttempts(preferCashierPage = true)
            .firstOpened { attempt ->
                tried += attempt
                attempt is YktPaymentAttempt.CashierPage
            }
        // 兜底命中收银台页，且直拉那条确实先试过（顺序与「没接住就往下走」都钉住）
        assertEquals(YktPaymentAttempt.CashierPage(cashierUrl), opened)
        assertEquals(
            listOf(
                YktPaymentAttempt.WechatDeepLink(wechatUrl),
                YktPaymentAttempt.CashierPage(cashierUrl),
            ),
            tried,
        )
    }

    @Test
    fun firstOpened_stopsAtTheFirstAcceptedAttempt() {
        // 直拉被系统接受就收手：不再多开一个收银台页（否则用户会看到两个支付入口）
        val tried = mutableListOf<YktPaymentAttempt>()
        val opened = wechatPay.paymentAttempts(preferCashierPage = true)
            .firstOpened { attempt ->
                tried += attempt
                true
            }
        assertEquals(YktPaymentAttempt.WechatDeepLink(wechatUrl), opened)
        assertEquals(listOf(YktPaymentAttempt.WechatDeepLink(wechatUrl)), tried)
    }

    @Test
    fun firstOpened_returnsNullWhenNothingOpens() {
        // 两条都接不住 = 调用方报「无法打开支付页」（iOS 目前到不了：收银台页恒被接住）
        assertNull(
            wechatPay.paymentAttempts(preferCashierPage = true).firstOpened { false },
        )
    }
}
