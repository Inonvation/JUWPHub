package edu.jxslu.schedule.data.power

import edu.jxslu.schedule.domain.CampusPaySign
import java.net.URLEncoder
import kotlin.random.Random
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/**
 * 电费下单/支付的签名与解析（DESIGN §4.24「电费充值」）。
 *
 * 与 [edu.jxslu.schedule.domain.YktRechargeSign] 同一算法（前端公开常量，两处一致），
 * 单独落一份的原因：电费的 [NONCE] 用 `Math.random().toString(36)` 口径（同款），
 * 但下单字段与响应结构完全不同，放同一对象会把两套协议搅在一起。
 */
object PowerPaySign {

    /** 前端硬编码的应用标识与签名密钥（公开 JS 常量，非逆向所得）。 */
    const val APP_ID = CampusPaySign.APP_ID
    const val SECRET_KEY = CampusPaySign.SECRET_KEY

    /** 给业务参数补元参数与 SIGN（新 map）。[timestamp]/[nonce] 可注入（单测定死）。 */
    fun signed(
        params: Map<String, String>,
        timestamp: String = buildTimestamp(),
        nonce: String = buildNonce(),
    ): Map<String, String> = CampusPaySign.signed(params, timestamp, nonce)

    /** key 字典序、跳过空值与 SIGN/SECRET_KEY，拼 `k=v&` + `SECRET_KEY=…`，SHA256 大写。 */
    fun signOf(params: Map<String, String>): String = CampusPaySign.signOf(params)

    /** `yyyyMMddHHmmssSSS`（本地时区；前端 `new Date()` 口径）。 */
    fun buildTimestamp(now: Long = System.currentTimeMillis()): String =
        CampusPaySign.buildTimestamp(now)

    /** 11 位小写字母数字（前端 `Math.random().toString(36).substring(2)` 口径）。 */
    fun buildNonce(random: Random = Random): String = CampusPaySign.buildNonce(random)
}

/**
 * 支付渠道标识（下单响应 `payList` 里的 `pay_type_code` + `id`，2026-09-23 / 2026-09-28 实测）。
 *
 * 平台照渠道表发 id，两份都要带上（`paystep=2` 同时收 `paytype` 与 `paytypeid`）。
 */
object PowerPayChannels {

    /** 电子账户（扣电子账户余额）。`payList` 名称「电子账户」。2026-11-06 起这个渠道在聚合收银台网页里选，App 不再走 `paystep=2 + ACCOUNT` 密码链路。 */
    const val ACCOUNT_CODE = "ACCOUNT"
    const val ACCOUNT_ID = "59"

    /**
     * 农行支付（K 码支付：卡号 + 手机短信验证码在**农行页面**完成）。
     * `payList` 里 `id=4`、`pay_type_name=农行支付`、`icon=ABCpay`、`nopassword=0`。
     */
    const val ABC_CODE = "NEWPAYMENTPLATFORM"
    const val ABC_ID = "4"
}

/** 电量入账状态（`order.flag` 第 2 位，见 [PowerPayModels.entryStateFrom]）。 */
enum class PowerEntryState {
    /** 电量已写入电表。 */
    ENTERED,

    /** 平台判了入账失败（官方详情页同款判据）。 */
    FAILED,

    /** 还没入账——扣款可能已成功，等平台的入账任务。 */
    PENDING,
}

/** 一次查单的结果：扣款状态与入账状态分开（2026-09-29 事故的教训，别再合并成一个数）。 */
data class PowerOrderState(val status: Int?, val entry: PowerEntryState?)

/**
 * 电费下单（`paystep=0`）的业务表单——**字段与官方 H5 的 choose 模板逐一对齐**
 * （`_static_js_app.js` 的 `placeOrder`，2026-09-29 逐字段核对）：
 *
 * ```js
 * var t = {feeitemid:…, tranamt:…, flag:this.template, source:"app", paystep:0,
 *          abstracts:this.chooseRowsVal};          // ← 选房描述串
 * this.$set(t, "third_party", this.third_party);   // ← 电表分支：读表 data 原文 JSON
 * ```
 *
 * 2026-09-29 入账事故：App 此前只发前五个字段。平台对 `third_party` / `abstracts`
 * **不做任何校验**——订单照建、钱照收，但入账任务从订单里找不到电表目标，永远不执行
 * （`order.flag` 停在 `0000000000`），三笔共 26 元卡在平台。红线：**缺读数原文就拒单，
 * 绝不发缺字段的表单**。
 */
object PowerOrderForm {

    /** 项目类型固定 choose（房间电费在平台就是该模板；官方页同款）。 */
    private const val FLAG_CHOOSE = "choose"

    /**
     * 组下单表单。8 个键一个不能少；[meter.dataJson] 缺失（没有真读过电表）直接抛
     * [IllegalStateException]—— repository 层会先给用户能看懂的 [PowerException.Protocol]，
     * 这里是最后一道闸，防「静默发出缺字段表单」复发。
     */
    fun of(yuan: String, meter: PowerMeter): Map<String, String> {
        val dataJson = meter.dataJson?.takeIf { it.isNotBlank() }
            ?: error("没有电表读数原文（dataJson），禁止下单——third_party 缺失会让订单永不入账")
        return linkedMapOf(
            "feeitemid" to PowerModels.RECHARGE_FEE_ITEM_ID.toString(),
            "tranamt" to yuan,
            "flag" to FLAG_CHOOSE,
            "source" to "app",
            "paystep" to "0",
            "synAccessSource" to "h5",
            "abstracts" to abstractsOf(meter.room),
            "third_party" to dataJson,
        )
    }

    /**
     * 账单摘要（`abstracts`）：官方账单上「校区-…;楼栋-…;房间-…」那一行的来源，
     * 取读数 `data` 的三个显示名拼装（与官方 `chooseRowsVal` 同格式，分隔符是 `-`/`;`）。
     */
    fun abstractsOf(room: PowerRoom): String =
        "校区-${room.campus.orEmpty()};楼栋-${room.building.orEmpty()};房间-${room.room.orEmpty()}"
}

/** 一次下单的返回（`POST /blade-pay/pay` `paystep=0`）。 */
data class PowerOrder(
    val orderId: String,
    /** 支付有效期（服务端 `payExpDate`，原文）。 */
    val payExpDate: String?,
)

/** 支付渠道（下单响应的 `payList` 元素）。 */
data class PowerPayChannel(val payId: String, val name: String, val code: String, val noPassword: Boolean)

object PowerPayModels {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun codeOf(raw: String): Int? =
        runCatching {
            val el = json.parseToJsonElement(raw).jsonObject["code"]
            (el as? JsonPrimitive)?.contentOrNull?.toIntOrNull()
                ?: (el as? JsonPrimitive)?.content?.toIntOrNull()
        }.getOrNull()

    fun messageOf(raw: String): String? = runCatching {
        val obj = json.parseToJsonElement(raw).jsonObject
        (obj["msg"] as? JsonPrimitive)?.contentOrNull
            ?: (obj["message"] as? JsonPrimitive)?.contentOrNull
    }.getOrNull()

    fun orderFrom(raw: String): PowerOrder? = runCatching {
        val data = dataOf(raw) ?: return@runCatching null
        val orderId = (data["orderid"] as? JsonPrimitive)?.contentOrNull
            ?: (data["orderId"] as? JsonPrimitive)?.contentOrNull
            ?: return@runCatching null
        PowerOrder(
            orderId = orderId,
            payExpDate = (data["payExpDate"] as? JsonPrimitive)?.contentOrNull,
        )
    }.getOrNull()

    fun channelsFrom(raw: String): List<PowerPayChannel> = runCatching {
        val data = dataOf(raw) ?: return@runCatching emptyList()
        val arr = data["payList"] as? kotlinx.serialization.json.JsonArray ?: return@runCatching emptyList()
        arr.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            PowerPayChannel(
                payId = (o["id"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null,
                name = (o["pay_type_name"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
                code = (o["pay_type_code"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
                noPassword = ((o["nopassword"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: 0) == 1,
            )
        }
    }.getOrDefault(emptyList())

/**
 * 入账状态（`getpayinfo` / `appAccountDetail` 的 `order.flag`，第 2 位字符——
 * 与官方账单详情页同一判据：`"1"` = 入账成功、`"2"` = 入账失败、其余 = 还没入账）。
 *
 * **`status=1` 只代表扣款成功，不代表电量进了电表**（2026-09-29 事故：App 下单缺
 * `third_party`，三笔「支付成功」的订单永远停在 `flag="0000000000"`）。判不出
 * （flag 缺失 / 太短）返回 null，调用方按「不知道」处理，不推断。
 */
fun entryStateFrom(raw: String): PowerEntryState? = runCatching {
    val obj = json.parseToJsonElement(raw).jsonObject
    val order = obj["order"] as? JsonObject ?: return@runCatching null
    val flag = (order["flag"] as? JsonPrimitive)?.contentOrNull ?: return@runCatching null
    entryStateOf(flag)
}.getOrNull()

/** [entryStateFrom] 的纯字符串口径（单测直接钉它）。 */
fun entryStateOf(flag: String?): PowerEntryState? {
    val second = flag?.getOrNull(1) ?: return null
    return when (second) {
        '1' -> PowerEntryState.ENTERED
        '2' -> PowerEntryState.FAILED
        else -> PowerEntryState.PENDING
    }
}

/** 查单一次拿全：`status`（扣款）与 `entry`（入账）是两回事，见 [entryStateFrom]。 */
fun orderStateFrom(raw: String): PowerOrderState? = runCatching {
    val obj = json.parseToJsonElement(raw).jsonObject
    val order = obj["order"] as? JsonObject ?: return@runCatching null
    PowerOrderState(
        status = (order["status"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull(),
        entry = entryStateOf((order["flag"] as? JsonPrimitive)?.contentOrNull),
    )
}.getOrNull()

    /**
     * 支付收银台链接（`paystep=2` + [PowerPayChannels.ABC_CODE] 的响应）。
     *
     * 平台有两种形状，**都要认**：
     *
     * 1. **2026-09-28 至 2026-11-06 的农行直连**（`paysubmit` / `qrFormUrl`）：表单**没有字段**
     *    （会话全在 action 的 query 里），链接直接 GET 就能打开。
     *    ```
     *    <form name="punchout_form" method="post" target="_parent"
     *       action=" https://pay.abchina.com/EbusPerbankFront/PaymentModeNewAct?TOKEN=…">
     *    ```
     * 2. **2026-11-06 起的聚合收银台**（`paymentcashierStr` / `qrFormUrl`，`data` 里已经没有
     *    `paysubmit`）：学校自己的收银台 `https://yktpay.juwp.edu.cn/cashier/cashier`，
     *    **`method="GET"` + 三个 hidden input**（`redirectUrl` / `ticket` / `embed`），
     *    真实地址 = action + `?` + 这些字段。只取 action 会丢会话，页面打不开。
     *    这就是用户报「农行收银台链接没拿到（平台可能已改版）」的那次改版。
     *    ```
     *    <form name="auto_form" method="GET" action="https://yktpay.juwp.edu.cn/cashier/cashier">
     *    <input type="hidden" name="redirectUrl" value="/pay?ticket=…&payTicket=…">
     *    <input type="hidden" name="ticket" value="…"><input type="hidden" name="embed" value="1">
     *    ```
     *
     * **白名单是硬闸**（这段 HTML 由第三方平台转发）：只认 https + `*.abchina.com`（农行）
     * 与 `*.juwp.edu.cn`（学校自己的聚合收银台）。平台再换域时这里是第一个要改的地方，
     * 认不出就返回 null，由调用方报「平台可能已改版」，绝不把任意 URL 交给内嵌页。
     */
    fun bankCashierUrlFrom(raw: String): String? = runCatching {
        val data = dataOf(raw) ?: return@runCatching null
        val candidates = buildList {
            (data["webUrl"] as? JsonPrimitive)?.contentOrNull?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let { add(it) }
            for (key in CASHIER_HTML_KEYS) {
                (data[key] as? JsonPrimitive)?.contentOrNull?.let { html ->
                    cashierUrlOfForm(html)?.let { add(it) }
                }
            }
        }
        candidates.firstOrNull { isCashierUrl(it) }
    }.getOrNull()

    /**
     * 表单 → 可直接 GET 的收银台地址：没有字段就交出 action（老形状）；
     * 有字段则按 GET 表单的拼法把字段编码进 query（新形状）。POST 带字段的表单
     * 没法变成一个 URL——认不出就 null，不猜。
     */
    private fun cashierUrlOfForm(html: String): String? {
        val formTag = FORM_TAG.find(html)?.value ?: return null
        val action = attrOf(formTag, "action")?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val fields = FIELD_INPUT.findAll(html).mapNotNull { input ->
            val name = attrOf(input.value, "name") ?: return@mapNotNull null
            name to attrOf(input.value, "value").orEmpty()
        }.toList()
        if (fields.isEmpty()) return action
        if (!attrOf(formTag, "method").orEmpty().equals("GET", ignoreCase = true)) return null
        val query = fields.joinToString("&") { (name, value) ->
            "${URLEncoder.encode(name, "UTF-8")}=${URLEncoder.encode(value, "UTF-8")}"
        }
        return action + (if ("?" in action) "&" else "?") + query
    }

    /** 取标签里的属性值；实测引号是双引号，单引号一并认（第三方 HTML，别赌格式）。 */
    private fun attrOf(tag: String, name: String): String? =
        Regex("""(?<![\w-])$name\s*=\s*["']([^"']*)["']""", RegexOption.IGNORE_CASE)
            .find(tag)?.groupValues?.get(1)

    /** 收银台链接白名单：https + 农行域或校内域（含子域）。 */
    private fun isCashierUrl(url: String): Boolean = runCatching {
        val uri = java.net.URI(url)
        val host = uri.host.orEmpty().lowercase()
        uri.scheme.equals("https", ignoreCase = true) && ALLOWED_CASHIER_HOSTS.any { allowed ->
            host == allowed || host.endsWith(".$allowed")
        }
    }.getOrDefault(false)

    /**
     * 收银台页 **6230 分支**交给原生桥的 `jsonStr` → 农行手机版收银台 URL（2026-11-06 实测）。
     *
     * 聚合收银台选「农行缴电费专用」后，页面并不自己跳转：`pay()` 收到
     * `respCode=6230` 就把 `obj.extendParams.jsonStr` 交给**宿主 App 的原生桥**
     * （`window.AndroidFunc.SynObject(jsonStr)`），由 App 去拉起农行的支付。浏览器里没有这个桥，
     * 页面会一直停在「加载中」——这就是用户报的「选农行一直卡加载」。
     *
     * 实测 payload（`POST /payment/orders/{tx}/gatewayPay` 的真实响应）：
     * ```
     * {"param":{"abcTokenID":"17939862043565516333",
     *           "downloadUrl":"http://mobile.abchina.com/download/…",
     *           "returnUrl":"https://yktpay.juwp.edu.cn/payment/charges/frontNotify/…"},
     *  "primaryKey":"synjones.ecampus.pay.abc"}
     * ```
     * `param.abcTokenID` 就是农行 H5 收银台的 `TOKEN`（实测打开 `mobile.abchina.com/mpaynew/
     * mpay/index?TOKEN=…` 直接就是「身份验证 / 商户名称 江西水利电力大学 / 订单编号 / 订单金额 /
     * 手机号 → 验证码」那一页）。所以桥里把 URL 拼出来、交给同一个 WebView 打开即可。
     *
     * 只认 `primaryKey` 含 `abc` 的 payload，且必须带 `abcTokenID`；别的银行/渠道一律返回 null
     * （宁可不处理，也不把猜出来的 URL 交给内嵌页）。
     */
    fun abcCashierUrlFromBridge(payload: String?): String? = runCatching {
        val text = payload?.trim().orEmpty()
        if (text.isEmpty()) return@runCatching null
        val obj = json.parseToJsonElement(text).jsonObject
        val primaryKey = (obj["primaryKey"] as? JsonPrimitive)?.contentOrNull.orEmpty()
        if (!primaryKey.contains("abc", ignoreCase = true)) return@runCatching null
        val token = ((obj["param"] as? JsonObject)?.get("abcTokenID") as? JsonPrimitive)
            ?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
            ?: return@runCatching null
        ABC_MOBILE_CASHIER + URLEncoder.encode(token, "UTF-8")
    }.getOrNull()

    /** 农行手机版收银台入口（K 码支付：手机号 → 短信验证码），`TOKEN` 由平台现发。 */
    private const val ABC_MOBILE_CASHIER = "https://mobile.abchina.com/mpaynew/mpay/index?TOKEN="

    /** 收银台可能给的两个 HTML 键（老形状 `paysubmit`，新形状 `paymentcashierStr`）。 */
    private val CASHIER_HTML_KEYS = listOf("paysubmit", "qrFormUrl", "paymentcashierStr")

    private val ALLOWED_CASHIER_HOSTS = listOf("abchina.com", "juwp.edu.cn")

    private val FORM_TAG = Regex("""<form\b[^>]*>""", RegexOption.IGNORE_CASE)

    /** 带 `name` 的 input：hidden 字段与提交按钮（无 name）靠这个区分。 */
    private val FIELD_INPUT = Regex("""<input\b[^>]*>""", RegexOption.IGNORE_CASE)

    private fun dataOf(raw: String): JsonObject? = runCatching {
        (json.parseToJsonElement(raw).jsonObject["data"] as? JsonObject)
    }.getOrNull()
}
