package edu.jxslu.schedule.data.power

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 电费下单/支付解析与签名（DESIGN §4.24，fixture 全部取自 2026-09-23 真实响应）。
 */
class PowerPayTest {

    private val orderJson = """
        {"code":200,"success":true,"data":{"paystep":2,"accountno":null,"ccctype":null,"uuid":null,
         "passwordMap":null,"payList":[
           {"id":4,"pay_type_name":"农行支付","pay_type_code":"NEWPAYMENTPLATFORM","icon":"ABCpay","nopassword":0,"partner_key":"1000003","flag":"181"},
           {"id":59,"pay_type_name":"电子账户","pay_type_code":"ACCOUNT","icon":"accountpay","nopassword":0,"partner_key":"1000001","flag":"181"}],
         "payExpDate":"2026-09-23 22:17:12","orderid":"1790171232923431"},"msg":"操作成功"}
    """.trimIndent()

    /** 下单时拿到的真实订单号（paystep=2 响应里不会回传，必须由调用方带过去）。 */
    private val requestedOrderId = "1790249804015241"

    @Test
    fun orderParsing() {
        val order = PowerPayModels.orderFrom(orderJson)
        assertNotNull(order)
        assertEquals("1790171232923431", order!!.orderId)
        assertEquals("2026-09-23 22:17:12", order.payExpDate)
    }

    @Test
    fun channelsParsing() {
        val channels = PowerPayModels.channelsFrom(orderJson)
        assertEquals(2, channels.size)
        assertEquals("59", channels[1].payId)
        assertEquals("ACCOUNT", channels[1].code)
        assertEquals("电子账户", channels[1].name)
        // nopassword=0 = 需要密码
        assertTrue(!channels[1].noPassword)
    }

    @Test
    fun statusParsing() {
        // status 的解析口径由 orderStateFrom 承接（2026-09-29 起扣款与入账一起回）
        val state = PowerPayModels.orderStateFrom("""{"code":200,"order":{"status":0,"flag":"0000000000"}}""")!!
        assertEquals(0, state.status)
        assertEquals(1, PowerPayModels.orderStateFrom("""{"code":200,"order":{"status":1,"flag":"0110000000"}}""")!!.status)
        assertNull(PowerPayModels.orderStateFrom("""{"code":500,"msg":"未知异常"}"""))
    }

    /**
     * 农行收银台链接（2026-09-28 实测响应的形状）。
     *
     * 那段 HTML 由平台原样转发，`action` 的值**首尾带空格**、表单本身没有字段
     * （会话全在 action 的 query 里）——所以链接可以直接 GET 打开。
     */
    @Test
    fun bankCashierUrlParsing() {
        val cashierJson = """
            {"code":200,"success":true,"data":{"paysubmit":"<form name=\"punchout_form\" method=\"post\" target=\"_parent\"  action=\" https://pay.abchina.com/EbusPerbankFront/PaymentModeNewAct?TOKEN=12345678901234567890\">\n</form>\n<script>document.forms[0].submit();</script>","qrFormUrl":"<form name=\"punchout_form\" method=\"post\" target=\"_parent\"  action=\" https://pay.abchina.com/EbusPerbankFront/PaymentModeNewAct?TOKEN=12345678901234567890\">\n</form>"},"msg":"操作成功"}
        """.trimIndent()
        assertEquals(
            "https://pay.abchina.com/EbusPerbankFront/PaymentModeNewAct?TOKEN=12345678901234567890",
            PowerPayModels.bankCashierUrlFrom(cashierJson),
        )
    }

    /** 有的渠道走 `webUrl` 直给链接（官方前端先看它），一样要认。 */
    @Test
    fun bankCashierPrefersWebUrl() {
        val raw = """{"code":200,"data":{"webUrl":"https://pay.abchina.com/EbusPerbankFront/x?TOKEN=1"}}"""
        assertEquals("https://pay.abchina.com/EbusPerbankFront/x?TOKEN=1", PowerPayModels.bankCashierUrlFrom(raw))
    }

    /**
     * 2026-11-06 平台改版后的形状：学校聚合收银台 + `method="GET"` + 三个 hidden 字段。
     *
     * 旧代码只取 `action`，丢了 `redirectUrl`/`ticket`；白名单又只认 `*.abchina.com`，
     * 于是整条链路认不出链接（用户侧「农行收银台链接没拿到（平台可能已改版）」）。
     * 真实地址必须按 GET 表单的拼法带全字段，且值要按表单编码（`&`、`%` 都要转义）。
     */
    @Test
    fun bankCashierUrlParsesAggregatedCashierForm() {
        // 实测响应里 paymentcashierStr 与 qrFormUrl 是同一段 HTML，两个键都得认
        val form = "<form name=\\\"auto_form\\\" method=\\\"GET\\\" action=\\\"https://yktpay.juwp.edu.cn/cashier/cashier\\\">" +
            "\\n<input type=\\\"hidden\\\" name=\\\"redirectUrl\\\" value=\\\"/pay?ticket=abc&payTicket=x%3D\\\">" +
            "\\n<input type=\\\"hidden\\\" name=\\\"ticket\\\" value=\\\"abc\\\">" +
            "\\n<input type=\\\"hidden\\\" name=\\\"embed\\\" value=\\\"1\\\">" +
            "\\n<input type=\\\"submit\\\" value=\\\"立即提交\\\" style=\\\"display:none\\\" >" +
            "\\n</form>\\n<script>document.forms[0].submit();</script>"
        val cashierJson = """
            {"code":200,"success":true,"msg":"操作成功","data":{"paymentcashierStr":"$form","qrFormUrl":"$form"}}
        """.trimIndent()
        assertEquals(
            "https://yktpay.juwp.edu.cn/cashier/cashier" +
                "?redirectUrl=%2Fpay%3Fticket%3Dabc%26payTicket%3Dx%253D&ticket=abc&embed=1",
            PowerPayModels.bankCashierUrlFrom(cashierJson),
        )
    }

    /**
     * 表单带字段但方法不是 GET：没法变成一个 URL（内嵌页只吃 URL）。
     * 认不出就是 null，让上层报「平台可能已改版」，别拼一个半截地址出来。
     */
    @Test
    fun bankCashierUrlRejectsPostFormWithFields() {
        val raw = """
            {"code":200,"data":{"paysubmit":"<form method=\"POST\" action=\"https://pay.abchina.com/x\"><input type=\"hidden\" name=\"a\" value=\"1\"></form>"}}
        """.trimIndent()
        assertNull(PowerPayModels.bankCashierUrlFrom(raw))
    }

    /**
     * **白名单是硬闸**：这段 HTML 来自第三方平台，除 https + 农行域 / 校内域之外一律不认
     * ——不许把任意 URL（钓鱼站、http 降级）交给内嵌页打开。
     */
    @Test
    fun bankCashierUrlRejectsForeignHost() {
        fun form(action: String) = """{"code":200,"data":{"paysubmit":"<form action=\"$action\"></form>"}}"""
        assertNull(PowerPayModels.bankCashierUrlFrom(form("http://pay.abchina.com/x?TOKEN=1")))
        assertNull(PowerPayModels.bankCashierUrlFrom(form("https://pay.abchina.com.evil.cn/x")))
        assertNull(PowerPayModels.bankCashierUrlFrom(form("https://example.com/x?TOKEN=1")))
        assertNull(PowerPayModels.bankCashierUrlFrom(form("javascript:alert(1)")))
        // 校内域与农行域（含子域）放行：2026-11-06 起收银台在 yktpay.juwp.edu.cn
        assertEquals(
            "https://yktpay.juwp.edu.cn/cashier/cashier",
            PowerPayModels.bankCashierUrlFrom(form("https://yktpay.juwp.edu.cn/cashier/cashier")),
        )
        // 平台改版 / 结构不同：认不出就是 null，调用方报「平台可能已改版」
        assertNull(PowerPayModels.bankCashierUrlFrom("""{"code":200,"data":{"paysubmit":"<div>没有表单</div>"}}"""))
        assertNull(PowerPayModels.bankCashierUrlFrom("""{"code":500,"msg":"未知异常，请联系管理员"}"""))
    }

    /**
     * 聚合收银台的 6230 分支把 `jsonStr` 交给原生桥（payload 取自 2026-11-06 真实
     * `gatewayPay` 响应）。拼出来的就是农行手机版收银台；用户要的那一页。
     */
    @Test
    fun abcCashierUrlFromBridgePayload() {
        val payload = """{"param":{"abcTokenID":"17939862043565516333","downloadUrl":"http://mobile.abchina.com/download/clientDownload/zh_CN/MB_Index.aspx","returnUrl":"https://yktpay.juwp.edu.cn/payment/charges/frontNotify/2611060300005001370000501275"},"primaryKey":"synjones.ecampus.pay.abc"}"""
        assertEquals(
            "https://mobile.abchina.com/mpaynew/mpay/index?TOKEN=17939862043565516333",
            PowerPayModels.abcCashierUrlFromBridge(payload),
        )
    }

    /** 别的渠道 / 坏 payload 一律不处理：不猜 URL，也不让桥抛异常打断页面。 */
    @Test
    fun abcCashierUrlFromBridgeRejectsOthers() {
        assertNull(PowerPayModels.abcCashierUrlFromBridge(null))
        assertNull(PowerPayModels.abcCashierUrlFromBridge(""))
        assertNull(PowerPayModels.abcCashierUrlFromBridge("not-json"))
        assertNull(PowerPayModels.abcCashierUrlFromBridge("""{"primaryKey":"synjones.ecampus.pay.wechat","param":{"abcTokenID":"1"}}"""))
        assertNull(PowerPayModels.abcCashierUrlFromBridge("""{"primaryKey":"synjones.ecampus.pay.abc","param":{}}"""))
        assertNull(PowerPayModels.abcCashierUrlFromBridge("""{"primaryKey":"synjones.ecampus.pay.abc"}"""))
    }

    /** 渠道标识是实测值，改错 = 打到别的支付方式的接口上。 */
    @Test
    fun channelConstantsMatchMeasuredPayList() {
        val channels = PowerPayModels.channelsFrom(orderJson)
        val abc = channels.first { it.code == PowerPayChannels.ABC_CODE }
        assertEquals(PowerPayChannels.ABC_ID, abc.payId)
        assertEquals("农行支付", abc.name)
        val account = channels.first { it.code == PowerPayChannels.ACCOUNT_CODE }
        assertEquals(PowerPayChannels.ACCOUNT_ID, account.payId)
    }

    @Test
    fun envelopeCodes() {
        assertEquals(500, PowerPayModels.codeOf("""{"code":500,"msg":"未知异常，请联系管理员"}"""))
        assertEquals("未知异常，请联系管理员", PowerPayModels.messageOf("""{"code":500,"msg":"未知异常，请联系管理员"}"""))
        assertEquals(200, PowerPayModels.codeOf("""{"code":200,"data":{}}"""))
    }

    @Test
    fun delegatesToCampusPaySign() {
        val signed = PowerPaySign.signed(mapOf("feeitemid" to "181"), timestamp = "T", nonce = "N")
        assertEquals(edu.jxslu.schedule.domain.CampusPaySign.APP_ID, signed["APP_ID"])
        assertEquals(64, signed["SIGN"]!!.length)
        assertEquals(signed["SIGN"], PowerPaySign.signOf(signed))
    }

    // ------------------------------------------------------------------
    // 下单表单（2026-09-29 入账事故后新增的钉子）
    //
    // 事故：App 下单只发 feeitemid/tranamt/flag/source/paystep，平台不校验
    // third_party/abstracts 的缺失——订单照建、钱照收，但入账任务找不到电表目标，
    // 三笔共 26 元「支付成功」却永远不入账（order.flag 停在 0000000000）。
    // ------------------------------------------------------------------

    /** 2026-09-29 官网真实订单的 third_party 原文（= getThirdData `map.data` 的 JSON）。 */
    private val thirdPartyJson =
        "{\"campus\":\"江西水利电力大学\",\"tsmAbstract\":\"校区#江西水利电力大学;楼栋#9Z;房间#9Z999\"," +
            "\"campusid\":\"0\",\"yktmercacc\":\"1000001\",\"remark\":\"{\\\"当前剩余电量\\\":\\\"15.67\\\"}\"," +
            "\"sroomid\":9999,\"building\":\"9Z\",\"roomid\":\"99999\",\"room\":\"9Z999\",\"buildingid\":\"0\"}"

    /** 形状取自真实读数响应（房间号已换成合成值）。 */
    private val orderMeter = PowerMeter(
        room = PowerRoom(campus = "江西水利电力大学", building = "9Z", room = "9Z999", roomId = "99999"),
        fields = mapOf("当前剩余电量" to "15.67"),
        remain = 15.67,
        remainField = "当前剩余电量",
        fetchedAtMs = 0L,
        dataJson = thirdPartyJson,
    )

    /** 下单表单 8 个键一个不能少：third_party 是入账目标，abstracts 是账单摘要行。 */
    @Test
    fun orderFormCarriesMeterTargetingAndAbstracts() {
        val form = PowerOrderForm.of("24", orderMeter)
        assertEquals(8, form.size)
        assertEquals("181", form["feeitemid"])
        assertEquals("24", form["tranamt"])
        assertEquals("choose", form["flag"])
        assertEquals("app", form["source"])
        assertEquals("0", form["paystep"])
        assertEquals("h5", form["synAccessSource"])
        // 与官方账单摘要行同格式（分隔符 - 与 ;）
        assertEquals("校区-江西水利电力大学;楼栋-9Z;房间-9Z999", form["abstracts"])
        assertEquals(thirdPartyJson, form["third_party"])
    }

    /** 签名覆盖全部业务字段（签名漏字段 = 平台按缺参处理，就是那次事故的形态）。 */
    @Test
    fun signedOrderFormKeepsEveryBusinessField() {
        val signed = PowerPaySign.signed(
            PowerOrderForm.of("24", orderMeter),
            timestamp = "20260929115433000",
            nonce = "abc123xyz00",
        )
        for (key in listOf(
            "feeitemid", "tranamt", "flag", "source", "paystep", "synAccessSource", "abstracts", "third_party",
            "APP_ID", "TIMESTAMP", "NONCE", "SIGN_TYPE", "SIGN",
        )) {
            assertNotNull("签名表单缺 $key", signed[key])
        }
        assertEquals(signed["SIGN"], PowerPaySign.signOf(signed))
    }

    /** 没有真读表的 data 原文就不许组表单——宁可拒单，不发缺字段的单出去。 */
    @Test
    fun orderFormRefusesMissingMeterData() {
        assertThrows(IllegalStateException::class.java) { PowerOrderForm.of("1", orderMeter.copy(dataJson = null)) }
        assertThrows(IllegalStateException::class.java) { PowerOrderForm.of("1", orderMeter.copy(dataJson = " ")) }
    }

    /** 摘要格式对显示名缺项保持三段结构（平台照原样入账单，宁空勿改格式）。 */
    @Test
    fun abstractsKeepsThreePartFormatWithPartialNames() {
        assertEquals(
            "校区-;楼栋-9Z;房间-9Z999",
            PowerOrderForm.abstractsOf(PowerRoom(campus = null, building = "9Z", room = "9Z999", roomId = null)),
        )
    }

    /**
     * 入账位 = `order.flag` 第 2 位（官方账单详情页同一判据）：
     * `1` 入账成功 / `2` 入账失败 / 其余未入账。App 事故三笔的 flag 全是 `0000000000`。
     */
    @Test
    fun entryStateReadsFlagSecondChar() {
        // 官网已入账订单的实测 flag（2026-09-29 及历史各笔）
        assertEquals(PowerEntryState.ENTERED, PowerPayModels.entryStateOf("0110000000"))
        assertEquals(PowerEntryState.FAILED, PowerPayModels.entryStateOf("0210000000"))
        assertEquals(PowerEntryState.PENDING, PowerPayModels.entryStateOf("0000000000"))
        assertEquals(PowerEntryState.ENTERED, PowerPayModels.entryStateOf("01"))
        // 判不出按「不知道」：调用方不推断
        assertNull(PowerPayModels.entryStateOf(null))
        assertNull(PowerPayModels.entryStateOf(""))
        assertNull(PowerPayModels.entryStateOf("0"))
    }

    /** getpayinfo 的 order 对象里 status（扣款）与 flag（入账）一起解析。 */
    @Test
    fun orderStateFromReadsStatusAndEntry() {
        val raw = """{"code":200,"order":{"orderid":"1790567663881293","status":1,"flag":"0000000000"}}"""
        val state = PowerPayModels.orderStateFrom(raw)!!
        assertEquals(1, state.status)
        assertEquals(PowerEntryState.PENDING, state.entry)
        assertNull(PowerPayModels.orderStateFrom("""{"code":500,"msg":"未知异常"}"""))
        assertNull(PowerPayModels.orderStateFrom("""{"code":200}"""))
    }
}
