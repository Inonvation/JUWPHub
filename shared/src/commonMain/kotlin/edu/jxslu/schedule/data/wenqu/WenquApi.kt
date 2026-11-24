package edu.jxslu.schedule.data.wenqu

import edu.jxslu.schedule.data.net.SharedHttp
import edu.jxslu.schedule.data.qiekj.QiekjJson
import edu.jxslu.schedule.domain.AppClock
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 问渠实践教育平台客户端（DESIGN §4.45）。
 *
 * 形态对齐 [edu.jxslu.schedule.data.ujing.UjingApi]：私有构造 + `create()`、手写请求头、
 * 解析交宽容序列化。**不用 Retrofit**——端点都是 POST 一条 JSON（下载那条 GET）、无 query 参数，
 * 直接发请求更直白。
 *
 * 红线：
 * - **无日志拦截器**——手机号 / 密码 / token 绝不进 Logcat（对齐一卡通 / 电费 / 快趣 / U净）；
 * - token 不驻留本类（持久化归 [WenquCredentialStore]），本类只见调用方传入的串；
 * - 只发读接口 + 用户显式触发的写（登录 / 设密码 / **报名** / **取消报名**）；
 *   **签到、提交材料类写接口仍不碰**（`volunteer/checkin`、`*_submit` 一个都不要调）。
 *
 * iOS 移植 P1（2026-11-24）：搬进 `:shared`，OkHttp → Ktor。`withServerClock` 原本靠
 * 挂一层 OkHttp 拦截器实现，现在改为在 [send] 里就地记账（Ktor 没有 Interceptor），
 * 口径不变：发前取钟 → 收后取钟 → 读 `Date` 头算偏移。
 */
class WenquApi private constructor(
    private val http: HttpClient,
    private val withServerClock: Boolean,
) {

    companion object {
        /** 平台根（与小程序 `HTTP_REQUEST_URL` 逐字一致）。 */
        const val BASE = "https://wenqu.juwp.edu.cn/api/"

        /**
         * 取消报名时「我没报过这条活动」的平台原话（2026-10-11 实测）。
         *
         * 用来给 [WenquRepository.cancelApply] 的 id 语义兜底：`volunteer/apply/cancel/{id}`
         * 里的 `{id}` 到底是**活动 id** 还是**报名 id** 未钉死，撞上这句就换另一个 id 再试一次。
         */
        const val MSG_NOT_APPLIED = "您未报名该活动"

        /**
         * `Accept` 头原值。抽成常量是为了让 KDoc 能引用它——把星号与斜杠连写进 KDoc
         * 会被当成块注释的结束符，编译器只会报一串「Expecting member declaration」，
         * 真正的病因（注释提前闭合）得自己往回找。
         */
        private const val ACCEPT_JSON = "application/json, text/plain, */*"

        /**
         * 共享池派生；[callTimeoutSeconds] 默认 30s（登录 / 详情 / 写操作都够用）。
         *
         * **蹲点探测单独派生短超时的实例**（`WenquRepository.probeApi` / `burstApi`，
         * 6s / 3s 见 `WenquRules.GRAB_REQUEST_TIMEOUT_S` / `GRAB_BURST_TIMEOUT_S`）：
         * 高频轮询挂 30 秒等于一轮白等——别直接把这个默认值改小，会误伤写操作。
         *
         * [withServerClock] = true 时每次响应顺手记一笔服务器时钟偏移（预报名专用：
         * 它要按服务器的钟算「提前 15 秒」，见 `WenquRules.preregTryStartAt`）。
         */
        fun create(callTimeoutSeconds: Long = 30L, withServerClock: Boolean = false): WenquApi =
            WenquApi(
                http = SharedHttp.create(requestTimeoutSeconds = callTimeoutSeconds),
                withServerClock = withServerClock,
            )
    }

    /**
     * 发「重置密码」验证码。**成功与失败都是 `status:200`**，结果看 [WenquEnvelope.sendResult]。
     * 平台短信限流实测约 5 分钟一条。
     */
    suspend fun sendResetCode(phone: String): WenquEnvelope = post(
        path = "register/verify",
        body = buildJsonObject {
            put("phone", phone)
            put("type", "reset")
        },
    )

    /**
     * 设置 / 重置密码。账号是**手机号**（学号不行，实测 `账号或密码错误`）。
     * 对「从没设过密码」的账号实测有效（平台语义是重置，无密码即创建）。
     */
    suspend fun resetPassword(account: String, captcha: String, password: String): WenquEnvelope = post(
        path = "register/reset",
        body = buildJsonObject {
            put("account", account)
            put("captcha", captcha)
            put("password", password)
        },
    )

    /** 日常登录（手机号 + 密码）→ `data.token`（JWT，3 天）。 */
    suspend fun login(account: String, password: String): WenquEnvelope = post(
        path = "login",
        body = buildJsonObject {
            put("account", account)
            put("password", password)
        },
    )

    /** 我的志愿 / 劳动教育记录 + 总工时。 */
    suspend fun volunteerMine(token: String): WenquEnvelope = post(
        path = "volunteer/my",
        token = token,
        body = buildJsonObject {
            put("status", "")
            put("start_time", "")
            put("end_time", "")
        },
    )

    /** 星级证书（总工时 + 星级）。 */
    suspend fun starCert(token: String): WenquEnvelope = post(
        path = "volunteer/star_cert",
        token = token,
        body = buildJsonObject {},
    )

    /**
     * **星级证书 PDF**（2026-10-15）：`GET volunteer/star_cert/download`。
     *
     * 平台回的是 **JSON**（`data.pdf_base64` + `filename` + `size`），不是文件流——
     * 见 [WenquCertData]。只读。
     */
    suspend fun starCertDownload(token: String): WenquEnvelope = get(
        path = "volunteer/star_cert/download",
        token = token,
    )

    /**
     * 平台上的志愿服务活动列表（小程序「水利文化 → 劳动教育」那张表）。
     *
     * 三个参数都必填：`keyword` 空串 = 不过滤（**与教务选课中心不同，这里空串是合法的**，
     * 实测空关键词回全量前 `limit` 条）。`limit` 服务端不限（实测 200 照收），
     * 但列表是「按 id 倒序的时间流」且总量上千，仍按页取。
     */
    suspend fun volunteerList(
        token: String,
        page: Int,
        limit: Int,
        keyword: String,
    ): WenquEnvelope = post(
        path = "volunteer/v2",
        token = token,
        body = buildJsonObject {
            put("page", page)
            put("limit", limit)
            put("keyword", keyword)
        },
    )

    /** 单个活动详情。**id 走路径**（`volunteer/detail/{id}`），请求体是空对象。 */
    suspend fun volunteerDetail(token: String, id: Long): WenquEnvelope = post(
        path = "volunteer/detail/$id",
        token = token,
        body = buildJsonObject {},
    )

    /**
     * **报名**（2026-10-11 实测钉死）：`POST volunteer/apply`，请求体 `{"id": <活动id>}`。
     *
     * 参数校验实测：`{}` / `id=0` → `参数错误`（`id` 必填）；`id` 不存在 →
     * `服务项目不存在或已下架`（说明它查的是**活动**）。所以 `id` 就是活动 id，不是报名 id。
     *
     * 写操作：**只发一次**，失败由调用方决定是否提示（见 [WenquRepository.applyActivity]）。
     */
    suspend fun volunteerApply(token: String, activityId: Long): WenquEnvelope = post(
        path = "volunteer/apply",
        token = token,
        body = buildJsonObject { put("id", activityId) },
    )

    /**
     * **取消报名**（2026-10-11 实测存在）：`POST volunteer/apply/cancel/{id}`，请求体空。
     *
     * `{id}` 是**活动 id** 还是**报名 id** 未能钉死（不存在的 id 两种情况都回
     * [MSG_NOT_APPLIED]）；[WenquRepository.cancelApply] 据此做一次兜底重试。
     */
    suspend fun volunteerCancel(token: String, id: Long): WenquEnvelope = post(
        path = "volunteer/apply/cancel/$id",
        token = token,
        body = buildJsonObject {},
    )

    private suspend fun post(
        path: String,
        body: JsonObject,
        token: String? = null,
    ): WenquEnvelope = send(path, body.toString(), token)

    /** `GET` 版（星级证书下载用）。 */
    private suspend fun get(path: String, token: String? = null): WenquEnvelope =
        send(path, jsonBody = null, token = token)

    /**
     * 发一次请求并解析。
     *
     * 请求头两个都要（值见 [ACCEPT_JSON]）：
     * - `Accept`：JSON 优先、其余走通配
     * - **`Authori-zation`**——CRMEB 特有拼写，写成 `Authorization` 会被判未登录。
     */
    private suspend fun send(path: String, jsonBody: String?, token: String?): WenquEnvelope {
        val sentAt = AppClock.nowMillis()
        val response = http.request(BASE + path) {
            header("Accept", ACCEPT_JSON)
            if (!token.isNullOrBlank()) header("Authori-zation", "Bearer $token")
            if (jsonBody != null) {
                method = HttpMethod.Post
                contentType(ContentType.Application.Json)
                setBody(jsonBody)
            }
        }
        val receivedAt = AppClock.nowMillis()
        if (withServerClock) {
            WenquServerClock.recordFrom(response.headers["Date"], sentAt, receivedAt)
        }
        val text = response.bodyAsText()
        val httpCode = response.status.value
        return try {
            QiekjJson.json.decodeFromString(WenquEnvelope.serializer(), text)
        } catch (e: SerializationException) {
            throw WenquApiException("响应格式异常（HTTP $httpCode）", httpCode, e)
        }
    }
}
