package edu.jxslu.schedule.data.qzxy

import edu.jxslu.schedule.data.net.SharedHttp
import edu.jxslu.schedule.data.qiekj.QiekjJson
import edu.jxslu.schedule.domain.QzxyCredential
import edu.jxslu.schedule.domain.QzxySign
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.parameter
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.Parameters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonElement

/**
 * 蓝牙下单所需的一组现场参数（DESIGN §4.30）。
 *
 * 全部来自蓝牙侧的设备查询响应，少一个服务端就不认。字段命名沿用官方两套口径：
 * [useLegacyFieldNames] = true 对应 `macAddress / bigTypeId`，签名里放 `telephone`；
 * false 对应 `deviceMac / mainTypeId`，签名里放 `telPhone`。App 用哪套由它内部的
 * `MyApp.notBLE` 开关决定，我们这边做成可切换，真机试哪套通就固定哪套。
 */
data class QzxyRateOrderRequest(
    val macAddress: String,
    val macTypeHex: String,
    val protocolType: String,
    val deviceId: String,
    val bigTypeId: String,
    val smallTypeId: String,
    val randomNumber: String,
    val useLegacyFieldNames: Boolean = true,
)

/**
 * 趣智校园仓库（DESIGN §4.30）：登录态、账号信息、余额、账单，
 * 以及蓝牙型设备的两段式下单。
 *
 * 蓝牙开阀的实际链路是「先蓝牙读设备、再 HTTP 下单、再把 downData 写回设备」，
 * 蓝牙那一半在 `:app` 的 `QzxyGattLink` 与共享层 [QzxyWaterFlow]（经 [QzxyBluetoothLink] 缝），
 * 本类只负责 HTTP 这一段，参数由流程层从设备响应里取好后传进来。这样职责边界清楚：
 * 签名或字段错了改这里，蓝牙连接错了改那边。
 *
 * ## iOS 移植 S12（2026-11-24）：Retrofit → Ktor
 *
 * 跟着换掉的四处：
 * - `QzxyApi`（Retrofit 接口）整份删除，九个端点变成这里的 [get] / [postForm] 两次调用；
 *   端点路径、参数名、默认值逐字照搬。
 * - `withContext(Dispatchers.IO)` 删掉——Ktor 的请求本身挂起，`Dispatchers.IO` 是 JVM 专有。
 * - `HttpLoggingInterceptor(BASIC)` 删掉：共享 `SharedHttp` 不带日志插件，而 Ktor 的
 *   Logging 插件要单加依赖（`shared/build.gradle.kts` 本轮不动）。这也更贴合红线——
 *   `loginCode` 与 `signature` 不进日志；排查靠诊断页与失败文案。
 * - 会话存储与「登录状态已过期」留痕改成注入：凭证落 [QzxySessionStore]（`:app` 是
 *   加密 prefs），过期标记走 [onSessionExpired] / [onSessionCleared] 两个回调
 *   （`SessionExpiryStore` 在 `:app` 的 `data/session`，本轮不动）。
 *
 * 失败分类保持原样：服务端信封失败抛 [QzxyApiException] 或 [QzxySessionExpiredException]；
 * 传输层失败（超时 / 断网 / DNS）统一包成 [QzxyApiException]（原来抛 `IOException`，
 * 那是 JVM 类型）。
 */
class QzxyRepository(
    private val sessionStore: QzxySessionStore,
    /** 「登录状态已过期」留痕：失效当场写、进页消费（`:app` 接 `SessionExpiryStore.mark`）。 */
    private val onSessionExpired: () -> Unit = {},
    /** 登录成功 / 主动退出：清掉上面那条留痕。 */
    private val onSessionCleared: () -> Unit = {},
) {

    /** 与 `AppHttp` 的口径同量级：请求都很小，不需要长页面那种 120 秒总上限。 */
    private val http: HttpClient = SharedHttp.create(
        requestTimeoutSeconds = 30,
        connectTimeoutSeconds = 15,
        socketTimeoutSeconds = 20,
    )

    /**
     * 登录态流（对齐胖乖仓库的 [edu.jxslu.schedule.data.qiekj.QiekjRepository.loggedIn]）。
     * 页面直接订阅它，登录/退出在任何窗口发生都能即时反映；
     * 用 `ON_RESUME` 重读一次的老写法只覆盖「从子页返回」那一条路径。
     */
    private val _loggedIn = MutableStateFlow(sessionStore.read() != null)
    val loggedIn: StateFlow<Boolean> = _loggedIn.asStateFlow()

    // ── 登录态 ──

    fun localSession(): QzxySession? = sessionStore.read()

    fun logout() {
        sessionStore.clear()
        onSessionCleared()
        _loggedIn.value = false
    }

    /**
     * 服务端判定会话失效：清会话并留一条「已过期」标记，供下次进页提示（2026-10-21）。
     * 与 [logout] 的区别只在标记——主动退出不该提示过期。
     */
    fun markExpired() {
        sessionStore.clear()
        onSessionExpired()
        _loggedIn.value = false
    }

    private fun requireSession(): QzxySession =
        sessionStore.read()?.takeIf { it.isComplete }
            ?: throw QzxySessionExpiredException("登录信息不完整，请重新登录")

    // ── 登录 ──

    suspend fun sendCode(phone: String) {
        get(
            "user/verification/code/get",
            mapOf(
                "telephone" to phone,
                "secret" to QzxyCredential.smsSecret(phone),
                "typeId" to "3",
                "platform" to "1",
                "phoneSystem" to QzxyApiConfig.PHONE_SYSTEM,
                "version" to QzxyApiConfig.VERSION,
            ),
        ).requireSuccess()
    }

    suspend fun loginByPassword(phone: String, password: String): QzxySession {
        val envelope = postForm(
            "user/login",
            mapOf(
                "telephone" to phone,
                "password" to QzxyCredential.passwordValue(password),
                "type" to "0",
                "identifier" to "",
                "phoneSystem" to QzxyApiConfig.PHONE_SYSTEM,
                "version" to QzxyApiConfig.VERSION,
            ),
        )
        envelope.requireSuccess()
        return persistLogin(envelope.data, phone)
    }

    suspend fun loginBySms(phone: String, smsCode: String): QzxySession {
        val envelope = postForm(
            "user/registerAndLogin",
            mapOf(
                "telephone" to phone,
                "smsCode" to smsCode,
                "type" to "5",
                "phoneSystem" to QzxyApiConfig.PHONE_SYSTEM,
                "version" to QzxyApiConfig.VERSION,
            ),
        )
        envelope.requireSuccess()
        return persistLogin(envelope.data, phone)
    }

    private fun persistLogin(data: JsonElement?, phone: String): QzxySession {
        val loginData = decode(data, QzxyLoginData.serializer())
            ?: throw QzxyApiException("登录响应缺少用户信息")
        val session = QzxySession.from(loginData, phone)
        if (!session.isComplete) {
            throw QzxyApiException("登录响应缺少项目或账号标识，可能是新学校未接入")
        }
        sessionStore.save(session)
        onSessionCleared()
        _loggedIn.value = true
        return session
    }

    // ── 账号 / 项目 ──

    /** 学校名。用它确认登录确实落在本校项目上，而不是别的学校。 */
    suspend fun projectInfo(): QzxyProjectInfo {
        val envelope = get("project/info/triple", requireSession().authFields())
        envelope.requireSuccess()
        return decode(envelope.data, QzxyProjectInfo.serializer()) ?: QzxyProjectInfo()
    }

    /**
     * 验证一份外部粘进来的会话：拿它调一次只读接口（学校名）。
     *
     * **先验后存**：一落盘登录态就翻，无效的会话必须挡在存之前，否则用户会看到
     * 「已登录但什么都查不到」。与胖乖的 token 粘贴登录同构（那边验的是余额）。
     */
    suspend fun validateSession(candidate: QzxySession): QzxyProjectInfo {
        val envelope = get("project/info/triple", candidate.authFields())
        envelope.requireSuccess()
        return decode(envelope.data, QzxyProjectInfo.serializer()) ?: QzxyProjectInfo()
    }

    /** 把一份已经确认有效的会话落盘并翻转登录态。 */
    fun adoptSession(session: QzxySession) {
        sessionStore.save(session)
        onSessionCleared()
        _loggedIn.value = true
    }

    suspend fun balance(): QzxyBalance {
        val envelope = get("account/wallet", requireSession().authFields())
        envelope.requireSuccess()
        return decode(envelope.data, QzxyBalance.serializer()) ?: QzxyBalance()
    }

    // ── 设备 / 账单 ──

    /**
     * 按 MAC 查设备详情。返回里的 `communicationTypeId`、`onlineStatusId`、`isMigrated`
     * 是判断「这台设备走网络还是走蓝牙」的现场证据，诊断页要把原样值展示出来。
     *
     * 会依次试 [macLookupValues] 给出的几个候选值：服务端对同一台设备可能只认其中一种写法，
     * 查不到时换一个再试一次，比让用户对着「设备不存在」猜要省事。
     */
    suspend fun deviceInfo(macAddress: String): QzxyDeviceInfo? {
        val session = requireSession()
        for (candidate in macLookupValues(macAddress)) {
            val params = session.authFields() + mapOf(
                "macAddress" to candidate,
                "isNew" to "1",
            )
            val envelope = get("device/info/mac", params)
            // 会话问题直接上抛；「这台设备查不到」只是这个候选不对，换下一个
            if (QzxySessionExpiredException.matches(envelope.errorCode, envelope.text)) {
                throw QzxySessionExpiredException(envelope.text ?: "登录已失效，请重新登录")
            }
            if (!envelope.success) continue
            decode(envelope.data, QzxyDeviceInfo.serializer())?.let { return it }
        }
        return null
    }

    /**
     * 设备查询的 MAC 候选值，顺序即尝试顺序。
     *
     * **C0 开头要额外试一次前两位置换成 00**：这条来自 quzhi-lite 的实现
     * （它在查设备前对 `C0` 前缀做了同样替换，两个参考项目只有它这么做），
     * 推测原因是 Android 上报的蓝牙地址与设备实际登记的 MAC 头一字节不一致。
     * 实测本校设备的广播地址都以 `C0` 开头（服务端登记的是 `00` 开头），所以这不是理论问题。
     *
     * 大小写折叠用 `uppercase()`（不带 Locale）：Kotlin 的默认实现按 Unicode 大小写映射，
     * 对十六进制字符与 `Locale.ROOT` 逐字一致，也就不用 `java.util.Locale`。
     */
    private fun macLookupValues(raw: String): List<String> {
        val hex = raw.replace(":", "").replace("-", "").uppercase()
        val variants = if (hex.length == 12 && hex.startsWith("C0")) {
            listOf("00" + hex.drop(2), hex)
        } else {
            listOf(hex)
        }
        return variants.distinct()
    }

    suspend fun billList(month: String): List<QzxyBill> {
        val params = requireSession().authFields() + mapOf(
            "month" to month,
            "billRequestType" to "2",
        )
        val envelope = get("order/query/account/bill/list", params)
        envelope.requireSuccess()
        val element = envelope.data ?: return emptyList()
        return runCatching {
            QiekjJson.json.decodeFromJsonElement(
                ListSerializer(QzxyBillList.serializer()),
                element,
            )
        }.getOrDefault(emptyList()).mapNotNull { it.consumeBillDTO }
    }

    // ── 蓝牙开阀（要签名）──

    /**
     * 蓝牙下单：把现场参数换成服务端签发的 `downData`。
     *
     * **签名字段集合与请求字段集合不是一回事**：签名只放 `telephone`（或 `telPhone`）、
     * `deviceId`、`xfModel`、`randomNumber` 四项，见 [QzxySign] 的说明。
     */
    suspend fun rateOrder(
        request: QzxyRateOrderRequest,
        /** 试签名时由调用方指定；正常流程留空，走 [rateOrderSignFields] 与 [QzxySign] 的默认实现。 */
        signatureOverride: String? = null,
    ): QzxyRateOrderData {
        val session = requireSession()
        val signature = signatureOverride ?: QzxySign.sign(
            fields = rateOrderSignFields(session, request),
            // 第一次 &key= 后挂会话值；固定后缀由 QzxySign 自己追加（见 SIGN_SUFFIX）
            key = session.loginCode,
        )
        val form = buildMap {
            putAll(session.authFields())
            put("xfModel", "0")
            put("deviceId", request.deviceId)
            put("macType", request.macTypeHex)
            put("protocolType", request.protocolType)
            put("randomNumber", request.randomNumber)
            put("signature", signature)
            put("smallTypeId", request.smallTypeId)
            if (request.useLegacyFieldNames) {
                put("macAddress", request.macAddress)
                put("bigTypeId", request.bigTypeId)
            } else {
                put("deviceMac", request.macAddress)
                put("mainTypeId", request.bigTypeId)
            }
        }
        val envelope = postForm("order/downRate/bluetooth/rateOrder", form)
        envelope.requireSuccess()
        return decode(envelope.data, QzxyRateOrderData.serializer())
            ?: throw QzxyApiException("下单成功但未返回 downData，无法下发到设备")
    }

    /**
     * 下单接口参与签名的字段集合。公开资料与协议分析一致：只有这四项，
     * 不是请求里的全部参数。试签名时也用这一份，保证试的就是真在用的输入。
     */
    fun rateOrderSignFields(
        session: QzxySession,
        request: QzxyRateOrderRequest,
    ): Map<String, String> = buildMap {
        put(if (request.useLegacyFieldNames) "telephone" else "telPhone", session.telephone)
        put("deviceId", request.deviceId)
        put("xfModel", "0")
        put("randomNumber", request.randomNumber)
    }

    /**
     * 下单签名的全部候选（见 [QzxySign.candidates]）。登录态不完整时返回空表。
     *
     * 两组都生成：官方 App 内部按 `MyApp.notBLE` 开关决定签名里放 `telephone` 还是
     * `telPhone`，我们不知道本校设备落在哪一支，标签前缀标出来，试通哪组就固定哪组。
     */
    fun rateOrderSignCandidates(request: QzxyRateOrderRequest): List<QzxySign.Candidate> {
        val session = sessionStore.read() ?: return emptyList()
        val telephone = QzxySign.candidates(
            rateOrderSignFields(session, request.copy(useLegacyFieldNames = true)),
            session.loginCode,
        ).map { it.copy(label = "[telephone] ${it.label}") }
        val telPhone = QzxySign.candidates(
            rateOrderSignFields(session, request.copy(useLegacyFieldNames = false)),
            session.loginCode,
        ).map { it.copy(label = "[telPhone] ${it.label}") }
        return (telephone + telPhone).distinctBy { it.signature }
    }

    /**
     * 上报消费数据结算。`xfData` 是从设备读回的消费记录（十六进制文本）。
     * 返回的 `consumeMoney` 单位是**厘**，展示前除 1000。
     */
    suspend fun uploadConsume(
        xfData: String,
        randomNumber: String,
        protocolType: String,
    ): QzxyConsumeResult {
        val session = requireSession()
        val signature = QzxySign.sign(
            fields = mapOf(
                "loginCode" to session.loginCode,
                "telephone" to session.telephone,
                "xfData" to xfData,
            ),
            key = session.loginCode,
        )
        val form = buildMap {
            putAll(session.authFields())
            put("xfData", xfData)
            put("randomNumber", randomNumber)
            put("protocolType", protocolType)
            put("signature", signature)
        }
        val envelope = postForm("order/upload/bluetooth/data", form)
        envelope.requireSuccess()
        return decode(envelope.data, QzxyConsumeResult.serializer()) ?: QzxyConsumeResult()
    }

    // ── HTTP ──

    /** GET：认证参数走 query（Retrofit 的 `@QueryMap` 同口径）。 */
    private suspend fun get(path: String, params: Map<String, String>): QzxyEnvelope = send {
        method = HttpMethod.Get
        url(QzxyApiConfig.BASE_URL + path)
        params.forEach { (k, v) -> parameter(k, v) }
    }

    /** POST 表单：认证参数与业务字段一起走 `x-www-form-urlencoded`。 */
    private suspend fun postForm(path: String, fields: Map<String, String>): QzxyEnvelope = send {
        url(QzxyApiConfig.BASE_URL + path)
        setBody(FormDataContent(Parameters.build { fields.forEach { (k, v) -> append(k, v) } }))
    }

    /**
     * 发一次请求并解出信封。
     *
     * 与 Retrofit 版的差别只有异常类型：传输失败不再抛 `IOException`（JVM 专有），
     * 统一包成 [QzxyApiException]——调用方原本就只读 `message` 或判
     * [QzxySessionExpiredException]，行为不变。
     */
    private suspend fun send(block: HttpRequestBuilder.() -> Unit): QzxyEnvelope {
        val response: HttpResponse = try {
            http.request { block() }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            throw QzxyApiException("网络请求失败：${error.message ?: error::class.simpleName}")
        }
        val text = response.bodyAsText().removePrefix("\uFEFF")
        if (response.status.value == 401 || response.status.value == 403) {
            throw QzxySessionExpiredException("登录已失效，请重新登录")
        }
        return decodeEnvelope(text)
    }

    private fun decodeEnvelope(text: String): QzxyEnvelope = runCatching {
        QiekjJson.json.decodeFromString(QzxyEnvelope.serializer(), text)
    }.getOrElse { throw QzxyApiException("接口响应格式无法解析：${it.message}") }

    private fun <T> decode(element: JsonElement?, serializer: KSerializer<T>): T? {
        if (element == null) return null
        return runCatching { QiekjJson.json.decodeFromJsonElement(serializer, element) }
            .getOrElse { throw QzxyApiException("接口响应格式无法解析：${it.message}") }
    }
}
