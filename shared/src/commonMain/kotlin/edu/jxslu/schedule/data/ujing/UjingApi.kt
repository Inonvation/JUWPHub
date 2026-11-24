package edu.jxslu.schedule.data.ujing

import edu.jxslu.schedule.data.net.SharedHttp
import edu.jxslu.schedule.data.qiekj.QiekjJson
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * U净 HTTP 客户端（DESIGN §4.37）。
 *
 * 形态与 [edu.jxslu.schedule.data.kqcx.KqcxAuthClient] 同款：私有构造 + `create()`、
 * 手写指纹头、解析交宽容序列化。**不用 Retrofit**：请求头分三组（鉴权前 ZI / 门店查询
 * ZA / 业务 BI），按调用点显式切换比在拦截器里猜 URL 直白。
 *
 * 红线：
 * - **无日志拦截器**——token 绝不进 Logcat（对齐一卡通 / 电费 / 快趣）；
 * - 写操作零自动重试由仓库层把关，本类只负责发一次、如实抛错；
 * - token 不驻留本类（持久化归 [UjingSessionStore]，本类只见调用方传入的串）。
 *
 * iOS 移植 P1（2026-11-24）：搬进 `:shared`，OkHttp → Ktor（客户端由 [SharedHttp.create]
 * 出，超时口径不变：connect 15s / socket 20s / 总上限 30s）。两处**可观测差异**：
 * - 查询串改由 Ktor 的 `URLBuilder.parameters` 按 RFC 3986 编码。原来手写
 *   `java.net.URLEncoder.encode`（JVM 专有）出的是 form-urlencoded：空格编成 `+`，
 *   现在编成 `%20`。受影响的是 `stores/near` 的 `keyword`（店名可能带空格）；
 *   中文两边都编成 `%E6%B4%97…`，一致。`%20` 在 query string 里无歧义，保留 `+`
 *   反而会让「按 `+` 解空格」与「按字面加号」两种服务端实现分歧。
 * - 请求体 `Content-Type` 不再带 `charset=utf-8` 参数（现为 `application/json`，
 *   字节仍是 UTF-8）：`Charsets` 是 JVM 专有的，commonMain 里取不到，
 *   而 JSON 按 RFC 8259 本就只允许 UTF-8。
 *
 * 网络异常（连不上 / 超时）**不换算、原样上抛**——与迁移前的 OkHttp 行为一致；
 * 这里只有一处 catch，收窄在 [SerializationException] 上。
 */
class UjingApi private constructor(private val http: HttpClient) {

    companion object {
        fun create(): UjingApi = UjingApi(
            // 共享池派生；下单/支付参数给总上限 30s（洗衣机的 2 分钟窗口等不起挂死）。
            SharedHttp.create(requestTimeoutSeconds = 30),
        )
    }

    /** 短信验证码（60 秒冷却由调用方控制）。 */
    suspend fun requestCaptcha(mobile: String): UjingEnvelope = request(
        method = "GET",
        path = "captcha",
        identity = Identity.Account,
        query = mapOf(
            "mobile" to mobile,
            "type" to "1",
            "sessionId" to "AFS_SWITCH_OFF",
            "token" to "AFS_SWITCH_OFF",
            "sig" to "AFS_SWITCH_OFF",
        ),
    )

    /** 短信验证码登录 → `data.token`（JWT）。 */
    suspend fun login(mobile: String, captcha: String): UjingEnvelope = request(
        method = "POST",
        path = "login",
        identity = Identity.Account,
        body = buildJsonObject {
            put("mobile", mobile)
            put("captcha", captcha)
        },
    )

    /** 扫码识别设备。`qrCode` 必须传**二维码原始内容**（服务端自行解析，勿预提取）。 */
    suspend fun scanWasher(token: String, qrCode: String): UjingEnvelope = request(
        method = "POST",
        path = "devices/scanWasherCode",
        identity = Identity.Business,
        token = token,
        body = buildJsonObject { put("qrCode", qrCode) },
    )

    /** 设备套餐（洗涤模式 + 价格 + 时长 + storeId）。 */
    suspend fun programInfo(token: String, deviceId: String): UjingEnvelope = request(
        method = "GET",
        path = "app/washer/devices/program/info",
        identity = Identity.Business,
        token = token,
        query = mapOf("deviceId" to deviceId),
    )

    /** 附近洗衣房（注意平台把经度拼成 `lont`，照抄协议事实）。 */
    suspend fun storesNear(
        token: String,
        lat: Double,
        lng: Double,
        keyword: String? = null,
    ): UjingEnvelope = request(
        method = "GET",
        path = "stores/near",
        identity = Identity.Query,
        token = token,
        query = buildMap {
            put("lat", lat.toString())
            put("lont", lng.toString())
            put("scope", "2000")
            put("page", "1")
            put("size", "100")
            put("mode", "BA")
            if (!keyword.isNullOrBlank()) put("keyword", keyword)
        },
    )

    // ── 订单链（P2） ──

    /**
     * 创建订单。body 字段全部来自扫码与套餐结果；机器必须用户亲手扫，不做
     * "不传 deviceId 由服务端自动匹配"的路径。
     *
     * 可选字段按「服务端声明需要才发」落（实机验证：五基础字段即可过）：
     * - `washTemperatureId`：仅洗衣机业务（type != 2）且机型开放水温时带；
     * - `wp_detergentGearId` / `wp_disinfectantGearId`：强制投放机型带标准档
     *   （档位枚举 1 = 洗涤剂标准、4 = 消毒液标准，社区实测口径）；
     * - `dryTime`：仅烘干机，协议事实 = 分钟 / 10。**由调用方判定烘干机后传入**
     *   （官方 `submitOrder` 按 `isDryer` 决定是否带，不按 `type` 卡）；非烘干机不传。
     */
    suspend fun createOrder(
        token: String,
        deviceId: String,
        deviceTypeId: Int,
        storeId: String,
        washModelId: Int,
        type: Int,
        washTemperatureId: Int? = null,
        detergentGearId: Int? = null,
        disinfectantGearId: Int? = null,
        dryTime: Int? = null,
    ): UjingEnvelope = request(
        method = "POST",
        path = "orders/create",
        identity = Identity.Business,
        token = token,
        body = buildJsonObject {
            put("type", type)
            put("deviceTypeId", deviceTypeId)
            put("deviceId", deviceId)
            put("deviceWashModelId", washModelId)
            put("storeId", storeId)
            // 温度仅洗衣机业务有；烘干机不传
            if (type != 2 && washTemperatureId != null) {
                put("washTemperatureId", washTemperatureId)
            }
            if (type != 2 && detergentGearId != null) {
                put("wp_detergentGearId", detergentGearId)
            }
            if (type != 2 && disinfectantGearId != null) {
                put("wp_disinfectantGearId", disinfectantGearId)
            }
            if (dryTime != null) {
                put("dryTime", dryTime)
            }
        },
    )

    /** 支付参数（channel=alipay；查询串字段照抄社区实测的完整形态）。 */
    suspend fun paymentArguments(token: String, orderId: String): UjingEnvelope = request(
        method = "GET",
        path = "payment/arguments",
        identity = Identity.Business,
        token = token,
        query = mapOf(
            "channel" to "alipay",
            "orderId" to orderId,
            "couponId" to "",
            "isUseRedPacket" to "false",
            "redPacketId" to "0",
            "alipayF2FNoAds" to "false",
            "branchType" to "0",
            "jumpToAliMini" to "false",
            "payVersion" to "1",
        ),
    )

    /**
     * 订单详情。`additional=price` 照抄实测形态（社区实测用它取 `payPrice`）。
     */
    suspend fun orderDetail(token: String, orderId: String): UjingEnvelope = request(
        method = "GET",
        path = "orders/$orderId/detail",
        identity = Identity.Business,
        token = token,
        query = mapOf("additional" to "price"),
    )

    /** 取消订单（释放 2 分钟独占）。 */
    suspend fun cancelOrder(token: String, orderId: String): UjingEnvelope = request(
        method = "POST",
        path = "orders/$orderId/cancel",
        identity = Identity.Business,
        token = token,
        body = buildJsonObject { put("orderId", orderId) },
    )

    /** 最近一次支付状态（支付回跳后确认）。 */
    suspend fun lastPayStatus(token: String, orderId: String): UjingEnvelope = request(
        method = "GET",
        path = "app/payment/$orderId/lastPayStatus",
        identity = Identity.Business,
        token = token,
    )

    /** 云端控制：`orders/{id}/control/{action}`，action = start / stop / continue / restart。 */
    suspend fun control(token: String, orderId: String, action: String): UjingEnvelope = request(
        method = "GET",
        path = "orders/$orderId/control/$action",
        identity = Identity.Business,
        token = token,
    )

    private enum class Identity { Account, Query, Business }

    /**
     * 发一次请求并解析。
     *
     * 查询串走 `url.parameters.append`（RFC 3986 编码），不再手拼 URL——原实现的
     * `URLEncoder` 是 JVM 专有 API，iOS 侧编不过。
     *
     * 方法名参数故意先在 lambda 外算成 `verb`：`HttpRequestBuilder.method` 与参数同名，
     * `method = if (method == "POST") …` 会被解析成读 builder 自己的 method（恒为 GET），
     * 所以先算再赋 `this.method`。
     */
    private suspend fun request(
        method: String,
        path: String,
        identity: Identity,
        token: String? = null,
        query: Map<String, String> = emptyMap(),
        body: JsonObject? = null,
    ): UjingEnvelope {
        val verb = if (method == "POST") HttpMethod.Post else HttpMethod.Get
        val response = http.request {
            url("${UjingApiConfig.BASE_URL}/$path")
            query.forEach { (key, value) -> url.parameters.append(key, value) }
            this.method = verb
            header("Accept", "*/*")
            header("Accept-Language", "zh-Hans-CN;q=1")
            header("x-user-geo", UjingApiConfig.USER_GEO_UNKNOWN)
            header("x-mobile-brand", UjingApiConfig.MOBILE_BRAND)
            header("x-mobile-model", UjingApiConfig.MOBILE_MODEL)
            when (identity) {
                Identity.Account -> {
                    header("x-app-code", UjingApiConfig.APP_CODE_ACCOUNT)
                    header("x-app-version", UjingApiConfig.APP_VERSION_ACCOUNT)
                    header("User-Agent", UjingApiConfig.UA_ACCOUNT)
                }

                // 门店查询组：只带 code/version（对齐社区实测的可用形态），
                // 不混入业务组的 weex / UA —— 混着发就是本轮回 401 的那版。
                Identity.Query -> {
                    header("x-app-code", UjingApiConfig.APP_CODE_QUERY)
                    header("x-app-version", UjingApiConfig.APP_VERSION_QUERY)
                }

                Identity.Business -> {
                    header("x-app-code", UjingApiConfig.APP_CODE_BUSINESS)
                    header("x-app-version", UjingApiConfig.APP_VERSION_BUSINESS)
                    header("weex-version", UjingApiConfig.WEEX_VERSION)
                    header("User-Agent", UjingApiConfig.UA_BUSINESS)
                }
            }
            if (!token.isNullOrBlank()) header("Authorization", "Bearer $token")
            if (verb == HttpMethod.Post) {
                contentType(ContentType.Application.Json)
                // 无 body 的 POST 原来也发 `{}`（`body?.toString() ?: "{}"`），照旧。
                setBody(body?.toString() ?: "{}")
            }
        }
        val text = response.bodyAsText()
        val httpCode = response.status.value
        return try {
            QiekjJson.json.decodeFromString(UjingEnvelope.serializer(), text)
        } catch (e: SerializationException) {
            // 业务码与 HTTP 码分开：这里的 -1 是「解析不出壳」的固定标记，不是 HTTP 状态。
            throw UjingApiException("响应格式异常（HTTP $httpCode）", -1, e)
        }
    }
}
