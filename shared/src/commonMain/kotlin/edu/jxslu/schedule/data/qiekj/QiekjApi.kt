package edu.jxslu.schedule.data.qiekj

import edu.jxslu.schedule.data.net.SharedHttp
import edu.jxslu.schedule.domain.AppClock
import edu.jxslu.schedule.domain.QiekjSign
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.Parameters
import io.ktor.http.Url
import io.ktor.http.encodedPath
import io.ktor.http.isSuccess
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonElement
import okio.Path.Companion.toPath

/**
 * 胖乖生活客户端（DESIGN §4.10）：开水链路与参考实现 light-life DeviceApi.kt 一致；
 * 签到端点取自 1.123.0 反编译（2026-10-23）。全部 Form POST；
 * 调用顺序约束见 [QiekjRepository.unlockDevice] / [QiekjRepository.runDailySignIn] 注释，禁止调整。
 *
 * ## iOS 移植 S11（2026-11-24）：Retrofit + OkHttp → Ktor
 *
 * Retrofit 的注解接口在 commonMain 里不存在（Retrofit 是 JVM 库），所以这里从「接口 + 注解」
 * 改成「一个类 + 手写请求」，端点、字段名、字段顺序、默认值一个都没动。
 * 四处必须知道的差别：
 *
 * 1. **客户端从共享池派生**：[SharedHttp.base] 就是 OkHttp 那个 base 的对应物
 *    （connect 15s / read 20s / call 120s / IPv4 优先 DNS 全在里面，见 [SharedHttp] 的对照表）。
 *    需要挂采样插件时用 `HttpClient.config {}` 派生一个新实例——它**共用同一个引擎**，
 *    与迁移前 `AppHttp.withInterceptors(...)` 的 `newBuilder()` 同义。派生实例不要 `close()`，
 *    那会把共享引擎一起关掉。
 * 2. **非 2xx 的报错口径**：[QiekjHttpException] 的 message 逐字沿用 Retrofit 的
 *    `"HTTP <码> <原因短语>"`，因为 [QiekjRepository] 的诊断逻辑按这个格式抠错误码。
 * 3. **请求头集中在 [applyQiekjHeaders]**：迁移前是 OkHttp `Interceptor`，现在没有拦截器，
 *    头部装配跟着请求走。规则一条没改，见该函数的注释。
 * 4. **不再有 `Content-Type` 之外的自动行为**：Retrofit 会按 `@FormUrlEncoded` 编码表单，
 *    这里显式用 [FormDataContent]，转义交 Ktor（原先交 OkHttp）。
 */
class QiekjApi private constructor(
    private val http: HttpClient,
    /** 请求头里的 token / Authorization / sign 都从它取；表单里的 token 由调用方另传。 */
    private val tokenProvider: () -> String?,
    /** 签到接口用 1.123.0；开水链路保持 1.60.3。 */
    private val version: String,
    /** 官方积分口径在 version 与 path 之间多一个 `&`；开水链路保持无分隔。 */
    private val signPathSeparator: String,
) {

    companion object {
        /**
         * 造一个客户端。
         *
         * [debugDumpDir] 非空时挂 [qiekjDebugDumpPlugin]（debug 包传 `filesDir/qiekj_debug`，
         * release 传 null，一个字节都不落盘）。签到链路与开水链路各建一个实例，
         * 与迁移前「两个 OkHttp 派生 client」一一对应。
         */
        fun create(
            tokenProvider: () -> String?,
            version: String = QiekjApiConfig.VERSION,
            signPathSeparator: String = "",
            debugDumpDir: String? = null,
        ): QiekjApi {
            val http = if (debugDumpDir == null) {
                SharedHttp.base
            } else {
                SharedHttp.base.config { install(qiekjDebugDumpPlugin(debugDumpDir.toPath())) }
            }
            return QiekjApi(http, tokenProvider, version, signPathSeparator)
        }
    }

    // ── 登录 / 资产 ──

    suspend fun sendCode(
        phone: String,
        template: String = "reg",
    ): ApiEnvelope<EmptyData> = post(
        path = "common/sms/sendCode",
        dataSerializer = EmptyDataSerializer,
        fields = listOf("phone" to phone, "template" to template),
    )

    suspend fun login(
        phone: String,
        verify: String,
        channel: String = QiekjApiConfig.LOGIN_CHANNEL,
    ): ApiEnvelope<LoginData> = post(
        path = "user/reg",
        dataSerializer = LoginData.serializer(),
        fields = listOf("channel" to channel, "phone" to phone, "verify" to verify),
    )

    suspend fun queryBalance(token: String): ApiEnvelope<BalanceData> = post(
        path = "user/balance",
        dataSerializer = BalanceData.serializer(),
        fields = listOf("token" to token),
    )

    // ── 每日签到（2026-10-23）──
    // 这些端点走 pointsApi（Version=1.123.0 + 官方 & 拼接），不开水链路共用。

    /**
     * 今日签到可得积分（官方注解是 GET，但 2026-10-11 只读探针用 POST + 表单 token
     * 实测通过——与 `user/balance` 同挂 1.60.3 的客户端，不走去签到链路的 pointsApi）。
     * 响应 data 是裸字符串（如 "2"），调用方从 JsonElement 自行取值。
     */
    suspend fun todaySignIntegral(token: String): ApiEnvelope<JsonElement> = post(
        path = "signin/todaySignIntegral",
        dataSerializer = JsonElement.serializer(),
        fields = listOf("token" to token),
    )

    suspend fun todaySignIn(token: String): ApiEnvelope<JsonElement> = post(
        path = "signin/todaySignIn",
        dataSerializer = JsonElement.serializer(),
        fields = listOf("token" to token),
    )

    suspend fun signInActivities(token: String): ApiEnvelope<JsonElement> = post(
        path = "signin/signInAcList",
        dataSerializer = JsonElement.serializer(),
        fields = listOf("token" to token),
    )

    suspend fun doUserSignIn(token: String, activityId: String): ApiEnvelope<JsonElement> = post(
        path = "signin/doUserSignIn",
        dataSerializer = JsonElement.serializer(),
        fields = listOf("token" to token, "activityId" to activityId),
    )

    // ── 设备 ──

    suspend fun getLatestUsed(
        token: String,
        categoryCode: String = "5",
    ): ApiEnvelope<List<DeviceItem>> = post(
        path = "goods/latestUsed",
        dataSerializer = ListSerializer(DeviceItem.serializer()),
        fields = listOf("categoryCode" to categoryCode, "token" to token),
    )

    suspend fun goodsid2sku(goodsId: String, token: String): ApiEnvelope<List<SkuData>> = post(
        path = "goods/normal/skus",
        dataSerializer = ListSerializer(SkuData.serializer()),
        fields = listOf("goodsId" to goodsId, "token" to token),
    )

    /**
     * 设备信息：拿 imei（位置风控要传）与机型能力 `supportAppUserFinish`
     * （能否在 App 内结束用水，2026-10-16 起读）。
     */
    suspend fun getGoodsDetails(goodsId: String, token: String): ApiEnvelope<GoodsDetailsData> = post(
        path = "goods/normal/details",
        dataSerializer = GoodsDetailsData.serializer(),
        fields = listOf("goodsId" to goodsId, "token" to token),
    )

    // ── 开水流程前置 ──

    suspend fun checkUserIsRisk(token: String): ApiEnvelope<EmptyData> = post(
        path = "userIntegral/checkUserIsRisk",
        dataSerializer = EmptyDataSerializer,
        fields = listOf("token" to token),
    )

    suspend fun addUserAfterPayChannel(
        token: String,
        method: String = "15",
    ): ApiEnvelope<EmptyData> = post(
        path = "payChannelRoute/addUserAfterPayChannel",
        dataSerializer = EmptyDataSerializer,
        fields = listOf("method" to method, "token" to token),
    )

    suspend fun isCheckLocation(
        imei: String,
        token: String,
        categoryCode: String = "04",
    ): ApiEnvelope<EmptyData> = post(
        path = "orderRisk/isCheckLocation",
        dataSerializer = EmptyDataSerializer,
        fields = listOf("categoryCode" to categoryCode, "imei" to imei, "token" to token),
    )

    // ── 开水 ──

    suspend fun unlockWater(
        skuId: String,
        promotions: String,
        token: String,
    ): ApiEnvelope<UnlockData> = post(
        path = "goods/water/unlock",
        dataSerializer = UnlockData.serializer(),
        fields = listOf("skuId" to skuId, "promotions" to promotions, "token" to token),
    )

    suspend fun syncWater(skuId: String, token: String): ApiEnvelope<SyncData> = post(
        path = "goods/water/sync",
        dataSerializer = SyncData.serializer(),
        fields = listOf("skuId" to skuId, "token" to token),
    )

    /**
     * 远程结束用水（2026-10-14 从胖乖 1.123.0 反编译补出）：`{ goodsId }` → 布尔。
     * **官方 App 只对 `goods/normal/details` 里 `supportAppUserFinish=true` 的机型调用它**；
     * 实测的直饮水机该字段为 false，服务端回 4072「结束失败，请在设备端操作结束」
     * （2026-10-16 真机样本）。调用方按 [shouldRequestRemoteEnd] 门控，被拒时退回设备端引导。
     */
    suspend fun endWater(goodsId: String, token: String): ApiEnvelope<EmptyData> = post(
        path = "goods/endWater",
        dataSerializer = EmptyDataSerializer,
        fields = listOf("goodsId" to goodsId, "token" to token),
    )

    // ── 结算 / 账单 ──

    suspend fun createAfterPay(orderNo: String, token: String): ApiEnvelope<AfterPayCreatingData> = post(
        path = "order/afterPay/creating",
        dataSerializer = AfterPayCreatingData.serializer(),
        fields = listOf("orderNo" to orderNo, "token" to token),
    )

    suspend fun orderDetail(orderId: String, token: String): ApiEnvelope<OrderDetailData> = post(
        path = "order/detail",
        dataSerializer = OrderDetailData.serializer(),
        fields = listOf("orderId" to orderId, "token" to token),
    )

    /**
     * 服务端账单历史（2026-10-14 反编译补出）：`{ page, pageSize }` → `ItemsList<OrderItemBean>`。
     * 与本地快照（最多 20 笔、只记本机结算）互补，能看到账号下的全部订单。
     */
    suspend fun orderList(
        page: String,
        pageSize: String,
        token: String,
    ): ApiEnvelope<ItemsList<ServerOrderItem>> = post(
        path = "order/list",
        dataSerializer = ItemsList.serializer(ServerOrderItem.serializer()),
        fields = listOf("page" to page, "pageSize" to pageSize, "token" to token),
    )

    // ── 传输 ──

    /**
     * 一次 Form POST：拼头 → 发 → 判状态码 → 解信封。
     *
     * 顺序与 Retrofit 一致：**先判 HTTP 状态、再解析**。所以非 2xx 时抛 [QiekjHttpException]
     * 而不是序列化异常——诊断层按 message 里的 `HTTP <码>` 抠错误码，别改格式。
     */
    private suspend fun <T> post(
        path: String,
        dataSerializer: KSerializer<T>,
        fields: List<Pair<String, String>>,
    ): ApiEnvelope<T> {
        val url = QiekjApiConfig.BASE_URL + path
        val response = http.request(url) {
            method = HttpMethod.Post
            applyQiekjHeaders(encodedPath = Url(url).encodedPath)
            setBody(FormDataContent(Parameters.build { fields.forEach { (k, v) -> append(k, v) } }))
        }
        if (!response.status.isSuccess()) {
            throw QiekjHttpException(response.status.value, response.status.description)
        }
        // 网关偶尔回带 BOM 的正文，不剥掉会让 JSON 解析当场失败
        val text = response.bodyAsText().removePrefix("\uFEFF")
        return QiekjJson.json.decodeFromString(ApiEnvelope.serializer(dataSerializer), text)
    }

    /**
     * 胖乖请求统一头装配（对齐 light-life HeaderInterceptor；迁移前是 OkHttp 拦截器，
     * 现在是请求构造的一部分，规则一条没改）。
     *
     * 关键规则：
     * - 登录类接口（`/common/` 与 `/user/reg` 路径）用 [QiekjApiConfig.LOGIN_CHANNEL]，
     *   且**不带** sign 与 token 头；
     * - 其余业务接口带 token + Authorization + sign（SHA-256，见 [QiekjSign]）。
     * 计算已抽到 domain 纯函数，这里只做装配；token 不进任何日志。
     *
     * [encodedPath] 是**带前导斜杠**的完整路径（`/goods/water/unlock`）：登录判据与
     * [QiekjSign.sign] 的拼接都用这个形态，别传相对路径。
     *
     * `Host` / `Connection` / `Accept-Encoding` 三个逐跳头照原样发：它们的值本来就是
     * HTTP/1.1 的默认值（host 与 URL 一致、keep-alive、gzip），部分引擎会静默丢弃，不影响语义。
     */
    private fun HttpRequestBuilder.applyQiekjHeaders(encodedPath: String) {
        val timestamp = AppClock.nowMillis().toString()
        val isLoginApi = encodedPath.startsWith("/common/") || encodedPath.startsWith("/user/reg")
        val channel = if (isLoginApi) QiekjApiConfig.LOGIN_CHANNEL else QiekjApiConfig.API_CHANNEL

        header("Version", version)
        header("channel", channel)
        header("phoneBrand", QiekjApiConfig.PHONE_BRAND)
        header("User-Agent", QiekjApiConfig.USER_AGENT)
        header("Content-Type", QiekjApiConfig.CONTENT_TYPE)
        header("timestamp", timestamp)
        header("Host", "userapi.qiekj.com")
        header("Connection", "Keep-Alive")
        header("Accept-Encoding", "gzip")

        val token = tokenProvider()?.takeIf { it.isNotBlank() } ?: return
        header("token", token)
        header("Authorization", token)
        if (!isLoginApi) {
            header(
                "sign",
                QiekjSign.sign(
                    appSecret = QiekjApiConfig.ANDROID_SECRET,
                    channel = channel,
                    timestamp = timestamp,
                    token = token,
                    version = version,
                    path = encodedPath,
                    pathSeparator = signPathSeparator,
                ),
            )
        }
    }
}

/**
 * 非 2xx 的响应。等价迁移前 Retrofit 抛的 `HttpException`：
 * message 逐字是 `HTTP <码> <原因短语>`，[QiekjRepository] 的诊断按这个格式抠码，别改。
 */
class QiekjHttpException(val code: Int, description: String) : Exception("HTTP $code $description")
