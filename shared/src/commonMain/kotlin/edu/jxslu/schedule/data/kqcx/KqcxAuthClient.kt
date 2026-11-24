package edu.jxslu.schedule.data.kqcx

import edu.jxslu.schedule.data.net.SharedHttp
import edu.jxslu.schedule.platform.Logger
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Parameters
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException

/**
 * 快趣出行「账号 + 用车」接口客户端（DESIGN §4.32，2026-09-28）。
 *
 * 形态与 [KqcxBikeClient] 同款（私有构造 + `create()`、只做请求与失败分类，解析交
 * `domain/KqcxAuth`），同域名 `api.kvcoogo.com`。协议口径来自对快趣小程序行为的分析：
 * form POST、**无签名**、鉴权只有 `token` 头。
 *
 * 红线：
 * - **无日志拦截器**——token 与密码凭据绝不进日志（对齐一卡通/电费），失败时只打
 *   异常类名与动作名（[execute]）；
 * - token 不在本类持久化（调用方 [KqcxSessionCore] 持内存）；
 * - 写操作（开锁/锁车/还车）**不做任何自动重试**——重试与否由仓库层按「先查状态」
 *   判定，本类只负责发一次、如实抛错。
 *
 * iOS 移植 S12（2026-11-24）：搬进 `:shared`，OkHttp → Ktor。跟着换掉的四处：
 * - 超时搬进 [SharedHttp.create]：原 `connect 10s / read 15s / call 15s`，Ktor 只有
 *   request / connect / socket 三个维度，写与读同受 socket 管（写体最大几十字节）；
 * - `okhttp3.FormBody` → [FormDataContent]，转义交给 Ktor；
 * - `withContext(Dispatchers.IO)` 全部删掉——Ktor 的请求本身挂起、不占线程，
 *   `Dispatchers.IO` 还是 JVM 专有的；
 * - `android.util.Log` → [Logger]（平台缝），失败日志的级别与文案照抄原实现。
 *
 * 原 `ServiceHttpException : java.io.IOException` 不能再继承 JVM 类型，改为
 * [Exception]；调用方原先靠 `catch (IOException)` 区分「网络/协议失败」的地方，
 * 现在改成「先放行 [CancellationException] 与 [edu.jxslu.schedule.domain.KvcBusinessError]、
 * 其余一律按网络失败」（见 [KqcxSessionCore]）。
 */
class KqcxAuthClient private constructor(private val http: HttpClient) {

    companion object {
        private const val BASE = "https://api.kvcoogo.com/ManagerApi/api"

        /** 账号密码登录（小程序 pages/login/index 同款接口）。 */
        private const val ENDPOINT_LOGIN = "$BASE/v1.0.0/userLoginByPassword"

        /** 骑行中订单查询（只读）。 */
        private const val ENDPOINT_UNDERWAY = "$BASE/v1.0.0/queryUnderwayOrder"

        /** 创建订单（开锁第一步，README：创建即绑定车辆并开始计费流程）。 */
        private const val ENDPOINT_CREATE_ORDER = "$BASE/v4.0.0/createOrder"

        /** GPRS 开锁。 */
        private const val ENDPOINT_UNLOCK = "$BASE/v1.0.0/greenCarUnlock"

        /** 头盔锁（官方在 helmet==1 && helmetConfig==1 时替代车锁开锁）。 */
        private const val ENDPOINT_HELMET_UNLOCK = "$BASE/v1.0.0/helmetUnLock"

        /** 锁车（官方骑行面板「锁车」按钮 = 临时锁车，订单与计费继续）。 */
        private const val ENDPOINT_LOCK = "$BASE/v1.0.0/greenCarLock"

        /** 静默锁车（官方还车流程第一步用的无声锁）。 */
        private const val ENDPOINT_MUTE_LOCK = "$BASE/v1.0.0/muteCarLock"

        /** 还车（结束订单）。 */
        private const val ENDPOINT_END_ORDER = "$BASE/v3.0.0/endTheOrder"

        /** 未支付订单查询（还车后的扣款确认）。 */
        private const val ENDPOINT_UNPAY = "$BASE/v1.0.0/queryUnPayOrder"

        /**
         * 服务端订单历史（`queryOrderList`，2026-11-05 从解包产物补出）：官方「历史订单」页
         * `pages/order` 用它按「年 + 月 + 车型」翻订单。只读接口。
         */
        private const val ENDPOINT_ORDER_LIST = "$BASE/v2.0.0/queryOrderList"

        /**
         * 服务区 / 还车点 / 禁停区图层（`queryZoneList`，2026-09-28 从解包产物里找到的）：
         * 官方还车点页 `pages/epark/index` 用它画多边形与还车点标记。只读接口。
         */
        private const val ENDPOINT_ZONES = "$BASE/v1.0.0/queryZoneList"

        /**
         * 响铃寻车（`greenCarFind`，2026-09-30）：让**在案订单**的车鸣笛，官方骑行页
         * 「响铃寻车」同款（无参数、作用于当前订单）。
         */
        private const val ENDPOINT_FIND_CAR = "$BASE/v1.0.0/greenCarFind"

        /**
         * 按车号查单车（`/v2.0.0/queryOneCar`，2026-09-30）：官方扫码后 `loadOneCar`
         * 用它拿单辆车的真实坐标与电量。只读接口。
         */
        private const val ENDPOINT_ONE_CAR = "$BASE/v2.0.0/queryOneCar"

        /** 用户信息（`getUserInfo`）：充值 / 赠送余额都在 `result` 里。只读。 */
        private const val ENDPOINT_USER_INFO = "$BASE/v2.0.0/getUserInfo"

        /** 用户持有卡券（`queryUserCoupon`）：`list[]`，剩余次数与免费时长。只读。 */
        private const val ENDPOINT_USER_COUPON = "$BASE/v2.0.0/queryUserCoupon"

        /** 小程序请求头的 `client-type: 1`；服务端按 token 认人，此头照抄即可。 */
        private const val CLIENT_TYPE = "1"

        /** 客户端版本（官方 config.Version 的现值）；createOrder 要带。 */
        private const val CLIENT_VERSION = "V6.0.0"

        /** UA 写自己包名，不冒充快趣客户端（与 [KqcxBikeClient] 同口径）。 */
        private const val UA = "edu.jxslu.schedule"

        private const val TAG = "KqcxAuthClient"

        fun create(): KqcxAuthClient = KqcxAuthClient(
            // 原 OkHttp 侧 connect 10s / read 15s / callTimeout 15s（官方 wx.request 的
            // 总超时就是 15 秒；base 的 120 秒只适合长页面下载，放在开锁/还车链路上会让
            // 一个挂死的请求把按钮拖两分钟）。Ktor 三个维度按同口径给。
            SharedHttp.create(
                requestTimeoutSeconds = 15,
                connectTimeoutSeconds = 10,
                socketTimeoutSeconds = 15,
            ),
        )
    }

    /**
     * 账号密码登录，返回响应原文（信封 + token 字段）。
     * [passwordMd5] 由调用方经 [edu.jxslu.schedule.domain.KqcxAuth.passwordCipher] 现算，
     * 本类不存密码。
     */
    suspend fun loginJson(mobile: String, passwordMd5: String): String =
        post(ENDPOINT_LOGIN, "登录", fields = mapOf("mobile" to mobile, "password" to passwordMd5))

    /** 骑行中订单查询（只读），返回响应原文。 */
    suspend fun underwayJson(token: String): String =
        post(ENDPOINT_UNDERWAY, "骑行查询", token)

    /** 创建订单（开锁第一步）。[locationSource] 官方口径 `"realtime"`。 */
    suspend fun createOrderJson(
        token: String,
        carNum: String,
        lat: Double,
        lng: Double,
        locationSource: String,
    ): String = post(
        ENDPOINT_CREATE_ORDER,
        "创建订单",
        token,
        fields = mapOf(
            "carNum" to carNum,
            "lat" to lat.toString(),
            "lng" to lng.toString(),
            "clientType" to "1",
            "clientVersion" to CLIENT_VERSION,
            "locationSource" to locationSource,
        ),
    )

    /** GPRS 开锁。 */
    suspend fun unlockJson(
        token: String,
        lat: Double,
        lng: Double,
        locationSource: String,
    ): String = post(ENDPOINT_UNLOCK, "开锁", token, fields = lockFields(lat, lng, locationSource))

    /** 头盔锁开锁（官方头盔流程）。 */
    suspend fun helmetUnlockJson(token: String): String =
        post(ENDPOINT_HELMET_UNLOCK, "头盔锁", token)

    /** 临时锁车（有声）。 */
    suspend fun lockJson(
        token: String,
        lat: Double,
        lng: Double,
        locationSource: String,
    ): String = post(ENDPOINT_LOCK, "锁车", token, fields = lockFields(lat, lng, locationSource))

    /** 静默锁车（还车流程第一步）。 */
    suspend fun muteLockJson(
        token: String,
        lat: Double,
        lng: Double,
        locationSource: String,
    ): String = post(ENDPOINT_MUTE_LOCK, "锁车", token, fields = lockFields(lat, lng, locationSource))

    /**
     * 还车（结束订单）。载荷逐字对齐官方 `doHandleOnReturnBike`：`locationValid` 按实际坐标
     * 是否有效传 1/0，`bdStatus/bdPileStatus:0`、`bluetoothName:""`、`dispatchFlag:2`
     * （普通还车；官方在用户已确认调度费时改发 1——App 不代确认）。
     */
    suspend fun endOrderJson(
        token: String,
        lat: Double,
        lng: Double,
        locationSource: String,
        locationValid: Boolean,
        locationTimeSeconds: Long,
    ): String = post(
        ENDPOINT_END_ORDER,
        "还车",
        token,
        fields = mapOf(
            "lat" to lat.toString(),
            "lng" to lng.toString(),
            "locationValid" to if (locationValid) "1" else "0",
            "locationSource" to locationSource,
            "locationTime" to locationTimeSeconds.toString(),
            "bdStatus" to "0",
            "bdPileStatus" to "0",
            "bluetoothName" to "",
            "dispatchFlag" to "2",
        ),
    )

    /** 未支付订单查询（只读）。 */
    suspend fun unpayJson(token: String): String = post(ENDPOINT_UNPAY, "欠费查询", token)

    /**
     * 服务端订单历史（只读）。参数逐字对齐官方「历史订单」页：
     * `{page, rows, orderYear, orderMonth, deviceType}`——**按年 + 月 + 车型查**，
     * 不是一次给全部；`deviceType` 1 = 电单车 / 2 = 单车，月份是不补零的数字。
     */
    suspend fun orderListJson(
        token: String,
        page: Int,
        rows: Int,
        year: Int,
        month: Int,
        deviceType: Int,
    ): String = post(
        ENDPOINT_ORDER_LIST,
        "订单查询",
        token,
        fields = mapOf(
            "page" to page.toString(),
            "rows" to rows.toString(),
            "orderYear" to year.toString(),
            "orderMonth" to month.toString(),
            "deviceType" to deviceType.toString(),
        ),
    )

    /**
     * 还车点 / 禁停区图层。参数逐字对齐官方：`{lat, lng, carNum, page:1, rows:15}`
     * （`carNum` 是"以哪辆车为上下文"，官方在还车点页传选中车、在地图页传最近一辆车）。
     *
     * `token` 允许空串（2026-10-01 实测：这个接口**不需要凭证**，不带 `token` 头也回
     * `resultCode=1`）——小程序方式没有会话，就空着发，不要为了它按需登一次账号
     * （见 [KqcxSessionCore.queryZones] 的注释）。
     */
    suspend fun zonesJson(token: String, lat: Double, lng: Double, carNum: String): String = post(
        ENDPOINT_ZONES,
        "还车点查询",
        token.takeIf { it.isNotBlank() },
        fields = mapOf(
            "lat" to lat.toString(),
            "lng" to lng.toString(),
            "carNum" to carNum,
            "page" to "1",
            "rows" to "15",
        ),
    )

    /** 响铃寻车：无参数（作用于在案订单），成功即车已响。 */
    suspend fun findCarJson(token: String): String = post(ENDPOINT_FIND_CAR, "响铃寻车", token)

    /**
     * 按车号查单车。参数逐字对齐官方 `loadOneCar`：`{carNum, lat, lng}`（坐标是查询上下文，
     * 官方传扫当时的定位）。token 允许空串——与 [zonesJson] 同口径的只读接口，
     * 小程序方式（无会话）也要能用「按车号定位」。
     */
    suspend fun oneCarJson(token: String, carNum: String, lat: Double, lng: Double): String = post(
        ENDPOINT_ONE_CAR,
        "单车查询",
        token.takeIf { it.isNotBlank() },
        fields = mapOf(
            "carNum" to carNum,
            "lat" to lat.toString(),
            "lng" to lng.toString(),
        ),
    )

    /** 账户资产（只读）：余额 / 卡券，参数都是空表单。 */
    suspend fun userInfoJson(token: String): String = post(ENDPOINT_USER_INFO, "资产查询", token)

    suspend fun userCouponJson(token: String): String = post(ENDPOINT_USER_COUPON, "卡券查询", token)

    /** 锁类接口的公共载荷：`{lng, lat, locationSource}`（官方三处同构）。 */
    private fun lockFields(lat: Double, lng: Double, locationSource: String): Map<String, String> =
        mapOf(
            "lng" to lng.toString(),
            "lat" to lat.toString(),
            "locationSource" to locationSource,
        )

    /**
     * 一次 form POST：公共头（`client-type` + UA）+ 可选 `token` 头 + 表单体。
     *
     * [token] 为 null 表示**不带 token 头**（不是带空串）：图层与单车查询在无会话时
     * 就是这条路径，服务端两种写法都认，但空串头会让部分网关把它当无效凭证。
     */
    private suspend fun post(
        url: String,
        what: String,
        token: String? = null,
        fields: Map<String, String> = emptyMap(),
    ): String = execute(what) {
        http.post(url) {
            header("client-type", CLIENT_TYPE)
            header("User-Agent", UA)
            token?.let { header("token", it) }
            setBody(FormDataContent(Parameters.build { fields.forEach { (k, v) -> append(k, v) } }))
        }
    }

    /**
     * 发一次请求并归一成响应文本。
     *
     * 失败只打**异常类名 + 动作名**（token 与凭据不许进日志）；[CancellationException]
     * 原样抛出去，不当失败处理——协程取消不是网络故障。
     */
    private suspend fun execute(what: String, block: suspend () -> HttpResponse): String = try {
        val response = block()
        if (!response.status.isSuccess()) throw ServiceHttpException(response.status.value, what)
        response.bodyAsText()
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        Logger.w(TAG, "$what failed: ${error::class.simpleName}")
        throw error
    }

    /** 服务端返回非 2xx。调用方只用一层 try/catch 区分业务失败与传输失败。 */
    class ServiceHttpException(val httpCode: Int, what: String) :
        Exception("快趣$what 接口返回 $httpCode")
}
