package edu.jxslu.schedule

import edu.jxslu.schedule.data.ykt.YktRechargeOrder
import edu.jxslu.schedule.data.ykt.paymentUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * 一卡通充值「交给平台打开哪个 URL」的契约（`design/ios-port.md` §10.4）。
 *
 * 背景：`POST /charge/order/thirdOrder` 之后平台会同时给出两条路——直拉微信
 * （`weixin://wap/pay?prepayid=…`）与官方收银台（`/payment/?synjones-auth=…`）。Android 用
 * 前者（`ACTION_VIEW` 一句话拉起微信），**iOS 不能用它**：`weixin://` 这条深链由微信硬校验
 * 商户域名与 referer，从原生 App 直接 open 会被拒（「商家参数格式有误」），只有在收银台页面
 * 里由页面自己跳才对。所以 iOS 必须拿收银台地址进 `WKWebView`。
 *
 * 这条判断只有一处实现（`YktRechargeOrder.paymentUrl`），本测试钉住两个平台各走哪条，
 * 以及「iOS 那条上不许出现 weixin://」。
 */
class YktRechargePaymentUrlTest {

    private val wechatPay = YktRechargeOrder.WechatPay(
        orderId = "A1",
        wechatUrl = "weixin://wap/pay?prepayid%3Dwx123",
        cashierUrl = "https://yktwx.juwp.edu.cn/payment/?synjones-auth=t&orderid=A1",
    )

    private val cashier = YktRechargeOrder.Cashier(
        orderId = "A2",
        cashierUrl = "https://yktwx.juwp.edu.cn/payment/?synjones-auth=t&orderid=A2",
    )

    @Test
    fun android_usesDirectWechatDeepLink() {
        // Android 保持原行为：直拉微信最快，不经过中间页
        assertEquals("weixin://wap/pay?prepayid%3Dwx123", wechatPay.paymentUrl(preferCashierPage = false))
    }

    @Test
    fun ios_usesCashierPageInsteadOfWechatDeepLink() {
        val url = wechatPay.paymentUrl(preferCashierPage = true)
        assertEquals("https://yktwx.juwp.edu.cn/payment/?synjones-auth=t&orderid=A1", url)
        // 硬约束：iOS 这条链上不许出现 weixin://（否则就是被微信拒的那一跳）
        assertFalse(url.startsWith("weixin://"))
    }

    @Test
    fun cashierFallback_isTheSameOnBothPlatforms() {
        // 平台没给直拉链接时（兜底）两边都只有收银台地址，取值不该因平台变
        assertEquals(cashier.cashierUrl, cashier.paymentUrl(preferCashierPage = false))
        assertEquals(cashier.cashierUrl, cashier.paymentUrl(preferCashierPage = true))
    }
}
