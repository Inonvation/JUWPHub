package edu.jxslu.schedule.data.qiekj

import edu.jxslu.schedule.domain.DiagnosisResult
import edu.jxslu.schedule.domain.PromotionLine
import edu.jxslu.schedule.domain.UnlockResult
import edu.jxslu.schedule.domain.AppClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class NotLoggedInException(message: String = "请先登录") : Exception(message)

/**
 * 胖乖生活仓库：登录态、余额、历史设备、开水全流程与每日签到（DESIGN §4.10）。
 *
 * 开水调用链的顺序以参考实现 light-life unlockDevice 为准，**不可调整**：
 * latestUsed → skus → sync(预检，可失败) → details(imei / 机型能力) → 积分风控 → 开后付 →
 * 位置风控 → unlock → 轮询 sync(等 workStatus 离开 2) → afterPay/creating → order/detail →
 * 写本地订单快照。前置步骤里风控/后付/位置任一失败都阻断；预检失败仅记录不阻断。
 * 轮询遇到 sync 无 data（设备侧结算关闭会话，2026-10-09 实测）按「会话已结束」收尾，
 * 不当致命错误，见 [noDataPollAction]。
 *
 * debug 包另挂 [qiekjDebugDumpPlugin]，把只读查询与开水链路的响应体原文落到
 * `filesDir/qiekj_debug/`（2026-10-05，为确认设备状态字段而加）。
 *
 * ## iOS 移植 S11（2026-11-24）：搬进 `:shared`
 *
 * 三处跟着换，语义都没变：
 *
 * 1. **传输层**：Retrofit + OkHttp → [QiekjApi]（Ktor，从 `SharedHttp.base` 派生）。
 *    迁移前的 `HttpLoggingInterceptor`（BASIC 级）**没有对应物**——Ktor 侧要打请求行得自己
 *    装 Logging 插件，而那条插件会把 header 一起打出来，与「token/sign 不进 Logcat」的红线冲突。
 *    所以现在**一条请求日志都不打**，比迁移前更严（迁移前只打请求行，本来就够用）；
 *    要补协议样本仍走 [qiekjDebugDumpPlugin] 的落盘。
 * 2. **存储**：`QiekjTokenStore` / `QiekjOrderHistoryStore` / `SessionExpiryStore` 都是
 *    Android 专有介质，仓库改认 [QiekjCredentialStore] / [QiekjOrderStore] /
 *    [QiekjExpiryMarker] 三个窄接口，实现留在 `:app`（理由见 QiekjStores.kt）。
 * 3. **调度器**：`Dispatchers.IO` 在 commonMain 里不可见（它只声明在各平台源集），
 *    [unlockDevice] 的 `withContext` 改成 [Dispatchers.Default]。**与迁移前的唯一行为差异**：
 *    整条开水流程（含 1 秒一次的轮询与网络挂起）跑在 Default 池上，而不是 Android 的 IO 池。
 *    Ktor 的请求本身是挂起的、不占线程，轮询里也没有阻塞调用，Default 池够用。
 */
class QiekjRepository(
    private val tokenStore: QiekjCredentialStore,
    private val orderHistoryStore: QiekjOrderStore,
    /**
     * debug 专用样本目录（`filesDir/qiekj_debug` 的绝对路径，见 [qiekjDebugDumpPlugin]）。
     * release 传 null，一个字节都不落盘。
     */
    private val debugDumpDir: String? = null,
    /** 「登录状态已过期」留痕（见 [QiekjExpiryMarker]）：失效当场写、进页消费。 */
    private val expiry: QiekjExpiryMarker? = null,
) {
    private val api: QiekjApi
    /** 签到专用：Version=1.123.0 + 官方 `&` 拼接；与开水链路的 1.60.3 无分隔签名隔离。 */
    private val pointsApi: QiekjApi

    init {
        // 两条链路各一个 client（与迁移前「两个 OkHttp 派生 client」一一对应）：
        // 共享池派生，只叠自己的头部注入（头部在 QiekjApi 里按请求装配）与可选采样插件
        api = QiekjApi.create(
            tokenProvider = { tokenStore.readToken() },
            debugDumpDir = debugDumpDir,
        )
        pointsApi = QiekjApi.create(
            tokenProvider = { tokenStore.readToken() },
            version = QiekjApiConfig.POINTS_VERSION,
            signPathSeparator = "&",
            debugDumpDir = debugDumpDir,
        )
    }

    // ── 登录态 ──

    /**
     * 登录态（进程级共享）：token 落盘/清除后就地翻转。
     *
     * 今日页开水卡与开水页（SubpageActivity 独立窗口）各持一份 `WaterViewModel`，
     * 两边共用的只有这个仓库单例。没有这条流，一边登录另一边要重启应用才对得上
     * （重建 ViewModel 时重读 token）。
     */
    private val _loggedIn = MutableStateFlow(tokenStore.readToken() != null)
    val loggedIn: StateFlow<Boolean> = _loggedIn.asStateFlow()

    fun readPhone(): String? = tokenStore.readPhone()

    /** 只读口：已登录的 token，供「复制 Token」迁移到另一台设备用。不走网络、不打日志。 */
    fun readToken(): String? = tokenStore.readToken()?.takeIf { it.isNotBlank() }

    /** 落盘 token 并翻转登录态。只在**已确认 token 有效**时调用，见 [validateToken]。 */
    fun saveToken(token: String) {
        tokenStore.saveToken(token)
        expiry?.clearExpired()
        _loggedIn.value = true
    }

    fun savePhone(phone: String) = tokenStore.savePhone(phone)

    /** 退出：token、登录态与快照一并清（对齐参考实现；快照依赖登录态存在意义不大）。 */
    fun logout() {
        tokenStore.clear()
        orderHistoryStore.clearAll()
        expiry?.clearExpired()
        _loggedIn.value = false
    }

    /**
     * 服务端判定 token 失效（401/403 或「请先登录」）：清登录态并留一条「已过期」标记，
     * 供下次进页提示（2026-10-21）。与 [logout] 的区别只在标记——主动退出不该提示过期。
     */
    fun markExpired() {
        tokenStore.clear()
        orderHistoryStore.clearAll()
        expiry?.markExpired()
        _loggedIn.value = false
    }

    private fun requireToken(): String =
        tokenStore.readToken()?.takeIf { it.isNotBlank() } ?: throw NotLoggedInException()

    /**
     * Token 粘贴登录的唯一校验方式：直接查余额，余额能查通即 token 有效。
     *
     * 传 [token] 而不是从存储读，是为了**先验后存**：一落盘 [loggedIn] 就翻转，
     * 今日页开水卡立刻切到已登录形态并开始拉余额/设备，验失败再回滚就是一次可见的闪跳。
     */
    suspend fun validateToken(token: String) {
        api.queryBalance(token).throwIfFailed()
    }

    // ── 登录 / 资产 / 设备 ──

    suspend fun sendCode(phone: String) {
        api.sendCode(phone = phone).throwIfFailed()
    }

    suspend fun login(phone: String, code: String): String {
        val token = api.login(phone = phone, verify = code).requireData().token
            ?: error("登录成功但未返回 token")
        saveToken(token)
        return token
    }

    suspend fun queryBalance(): BalanceData {
        val resp = api.queryBalance(requireToken())
        resp.throwIfFailed()
        return resp.requireData()
    }

    /**
     * 今天是否已签到（`signin/todaySignIn` 的只读判定，2026-11-07）。
     *
     * 签到页进页 / 登录 / 下拉刷新各问一次，用它把签到行摆成「已签到」：自动签到在主窗口
     * `onResume` 就跑完了，页面自己从没点过签到，光看本地状态永远是「签到」。
     * 判定失败照常抛（Token 失效由调用方统一处理），不在这里猜结论。
     */
    suspend fun isSignedInToday(): Boolean {
        val resp = pointsApi.todaySignIn(requireToken())
        resp.throwIfFailed()
        return resp.data.decodeObjectOrNull(TodaySignInData.serializer())?.isSignIn == true
    }

    /**
     * 每日签到（2026-10-23）：只做签到 + 余额，不碰任务/领奖。
     *
     * - `signin/todaySignIn` 先判今天是否已签；
     * - 未签才走 `signin/signInAcList` 取活动 id，再 `signin/doUserSignIn`；
     *   `code=33001` 是服务端幂等结果（今天已签），按成功处理；
     * - 未签时顺手 best-effort 问一次 `signin/todaySignIntegral`（今日可得积分），
     *   失败不阻断签到——它只服务「+X 积分」的提醒展示。
     *
     * 结果带 [QiekjDailySignInResult.freshSignIn]（本次真签到）与两个积分字段；
     * 「今天已签」的两条路 freshSignIn=false，自动签到的成功提醒只看它。
     *
     * 由用户手动触发，或由「打开软件自动签到」在当天首次前台时触发；
     * 不循环、不批量，验证码/风控不绕过。
     */
    suspend fun runDailySignIn(includeBalance: Boolean = true): QiekjDailySignInResult {
        val token = requireToken()

        val today = pointsApi.todaySignIn(token)
        today.throwIfFailed()
        val todayData = today.data.decodeObjectOrNull(TodaySignInData.serializer())

        var gained: String? = null
        var total: String? = null
        val freshSignIn: Boolean
        val signInText = if (todayData?.isSignIn == true) {
            freshSignIn = false
            "签到：今天已签过"
        } else {
            // 只服务展示，失败不挡签到
            gained = runCatching {
                val integral = pointsApi.todaySignIntegral(token)
                integral.throwIfFailed()
                integral.data.decodePrimitiveTextOrNull()
            }.getOrNull()
            val activity = pointsApi.signInActivities(token)
            activity.throwIfFailed()
            val activityData = activity.data.decodeObjectOrNull(SignInActivityData.serializer())
            val activityId = activityData?.id?.takeIf { it.isNotBlank() } ?: "600001"
            val result = pointsApi.doUserSignIn(token = token, activityId = activityId)
            when (result.code) {
                0, 200 -> {
                    freshSignIn = true
                    val resultData = result.data.decodeObjectOrNull(SignInResultData.serializer())
                    total = resultData?.totalIntegral?.takeIf { it.isNotBlank() }
                    "签到成功：activityId=$activityId，累计积分 ${total ?: "-"}"
                }
                33001 -> {
                    freshSignIn = false
                    "签到：服务端确认今天已签"
                }
                else -> {
                    result.throwIfFailed()
                    freshSignIn = false
                    "签到：未知状态"
                }
            }
        }

        val balance = if (!includeBalance) null else try {
            queryBalance()
        } catch (e: CancellationException) {
            throw e
        } catch (e: TokenExpiredException) {
            throw e
        } catch (_: Throwable) {
            null
        }
        return QiekjDailySignInResult(
            signInText = signInText,
            freshSignIn = freshSignIn,
            gainedIntegral = gained,
            totalIntegral = total,
            balance = balance,
        )
    }

    suspend fun latestDevices(): List<DeviceItem> {
        val resp = api.getLatestUsed(token = requireToken())
        resp.throwIfFailed()
        return resp.data ?: emptyList()
    }

    fun orderHistory(): List<OrderHistoryItem> = orderHistoryStore.list()

    // ── 开水全流程 ──

    /**
     * 一键开水。挂在调用方协程上（页面级生命周期），轮询期间协程取消即停止观察，
     * 但服务端会继续按 165s 超时自动关阀结算，不会出现"关不上水"。
     *
     * [stopSignal] 是 UI 的「结束用水」（2026-10-05 起，2026-10-16 改）：
     * - 机型不支持 App 结束（`supportAppUserFinish=false`）：不发注定被拒的
     *   `goods/endWater`，提示「请在设备上点击『结束』」并**继续轮询**，
     *   等设备回报结束照常结算读账单；
     * - 支持（或字段缺失）：best-effort 请求远程关阀，被平台拒绝同样退回
     *   设备端提示继续等——2026-10-16 前这里直接抛错收尾，被拒即丢单，是当时的真 bug；
     * - 只有「平台已受理但 [REMOTE_END_WAIT_MS] 内设备没回报」才抛
     *   [UnlockStoppedException] 交给调用方按「未确认用水」收尾。
     */
    suspend fun unlockDevice(
        device: DeviceItem,
        usePoints: Boolean,
        stopSignal: UnlockStopSignal? = null,
        onStep: suspend (String) -> Unit,
    ): UnlockResult = withContext(Dispatchers.Default) {
        val token = requireToken()
        val goodsId = device.effectiveGoodsId ?: error("设备缺少 goodsId")

        onStep("正在获取 SKU")
        val skuId = api.goodsid2sku(goodsId = goodsId, token = token).requireData()
            .firstOrNull()?.skuId ?: error("未获取到 skuId")

        // 预检失败不阻断流程：设备忙/离线等情况在 unlock 或轮询阶段有更准确的报错
        onStep("正在检测设备状态")
        runCatching { api.syncWater(skuId = skuId, token = token) }

        onStep("正在读取设备信息")
        val details = api.getGoodsDetails(goodsId = goodsId, token = token).requireData()
        val imei = details.imei ?: error("未获取到 imei")
        // 机型能否在 App 内结束用水（2026-10-16）：false 的机型平台必然拒绝远程关阀，
        // 结束分支据此跳过 endWater、改走设备端引导
        val appEndSupported = details.supportAppUserFinish

        onStep("正在检查积分")
        api.checkUserIsRisk(token).throwIfFailed()

        onStep("正在开通后付")
        api.addUserAfterPayChannel(token = token).throwIfFailed()

        onStep("正在检查位置风控")
        api.isCheckLocation(imei = imei, token = token).throwIfFailed()

        onStep("正在启动出水")
        val promotions = if (usePoints) PROMOTIONS_WITH_POINTS else PROMOTIONS_WITHOUT_POINTS
        val unlock = api.unlockWater(skuId = skuId, promotions = promotions, token = token)
            .requireData()
        val orderNo = unlock.orderNo ?: error("未获取到订单号")
        onStep("设备已启动，等待使用结束")

        delay(2_000)

        // 轮询：workStatus==2 表示出水中；必须记录 everWorked，
        // 否则设备从未启动时会被误判为"用完了"（对齐参考实现）。
        //
        // 注意 workStatus 说的是「服务端会话开着」，设备断电时也一直回 2
        // （2026-10-05 实测，15 分钟不变）。会话真结束的标志是 status==5，
        // 见 isSessionFinished / shouldSettleOnManualStop。
        //
        // 无 data 的响应不是错误（2026-10-09 实测）：设备侧点「结算」后平台关闭会话，
        // sync 回 {code:0,msg:"成功",data:null}。处置见 [noDataPollAction]——
        // 见过出水就收尾走结算（用最后已知单号），从未见到才按设备未响应报错。
        var lastStatus: SyncData? = null
        var everWorked = false
        var attempts = 0
        var emptyStreak = 0
        // 手动结束（2026-10-16 改）：机型支持才请求远程关阀（goods/endWater），
        // 不支持或平台拒绝都退回「在设备上点击『结束』」引导并**继续轮询**等设备回报，
        // 照常结算读账单（旧实现被拒即抛错收尾，账单丢单，2026-10-16 用户实测报障）。
        // remoteEndAttempted 保证只发一次；remoteEndOk + remoteEndDeadline 只管
        // 「平台已受理」这条路的技术超时。
        var remoteEndAttempted = false
        var remoteEndOk = false
        var remoteEndDeadline = 0L
        var endWaitStep: String? = null
        val idleWorkingStep = if (shouldRequestRemoteEnd(appEndSupported)) {
            "设备工作中，正在等待完成"
        } else {
            "设备工作中；如需结束，请在设备上点击『结束』"
        }
        while (true) {
            val envelope = diagnosed(
                step = "设备状态轮询",
            ) {
                api.syncWater(skuId = skuId, token = token).also { it.throwIfFailed() }
            }
            attempts++
            val sync = envelope.data
            if (sync == null) {
                emptyStreak++
                when (noDataPollAction(everWorked, emptyStreak)) {
                    NoDataPollAction.Settle -> break
                    NoDataPollAction.AbortOffline ->
                        throw diagnosedOf(Exception("设备未启动或未响应，可能已离线"), "设备状态轮询")
                    NoDataPollAction.KeepWaiting -> Unit
                }
                if (attempts >= MAX_POLL_ATTEMPTS) {
                    throw diagnosedOf(Exception("设备使用超时（${MAX_POLL_ATTEMPTS} 秒）"), "设备状态轮询")
                }
                delay(1_000)
                continue
            }
            emptyStreak = 0
            lastStatus = sync
            if (sync.workStatus == 2) everWorked = true
            if (attempts >= MAX_POLL_ATTEMPTS) {
                throw diagnosedOf(Exception("设备使用超时（${MAX_POLL_ATTEMPTS} 秒）"), "设备状态轮询")
            }
            // 手动结束（2026-10-16）：机型支持才请求远程关阀；不支持 / 被拒都退回设备端引导。
            // 三条路都在下一轮继续轮询，设备回报结束态（shouldSettleOnManualStop）即跳出结算。
            if (stopSignal?.isRequested == true) {
                if (!remoteEndAttempted) {
                    remoteEndAttempted = true
                    if (shouldRequestRemoteEnd(appEndSupported)) {
                        onStep("正在结束用水，等待设备回报")
                        remoteEndOk = try {
                            api.endWater(goodsId = goodsId, token = token).throwIfFailed()
                            true
                        } catch (e: TokenExpiredException) {
                            throw e
                        } catch (e: Throwable) {
                            false
                        }
                        endWaitStep = if (remoteEndOk) {
                            remoteEndDeadline = AppClock.nowMillis() + REMOTE_END_WAIT_MS
                            "正在结束用水，等待设备回报"
                        } else {
                            "结束请求未被平台受理：请在设备上点击『结束』，等待设备回报"
                        }
                    } else {
                        endWaitStep = "该机型不支持 App 结束：请在设备上点击『结束』，等待设备回报"
                    }
                }
                if (shouldSettleOnManualStop(sync)) break
                if (remoteEndOk && AppClock.nowMillis() >= remoteEndDeadline) {
                    throw UnlockStoppedException("已请求结束用水，设备未回报结束，账单以平台为准")
                }
                // 平台未受理 / 机型不支持：不再中断等待（旧实现在这里抛错丢单），
                // 继续按 endWaitStep 提示并轮询，等设备侧结束或 165s 服务端兜底
            } else if (sync.workStatus != 2) {
                break
            }
            onStep(endWaitStep ?: idleWorkingStep)
            delay(1_000)
        }

        if (!everWorked) {
            throw diagnosedOf(Exception("设备未启动或未响应，可能已离线"), "设备状态轮询")
        }

        onStep("正在创建后付订单")
        val finalOrderNo = lastStatus?.identify ?: orderNo
        val orderId = api.createAfterPay(orderNo = finalOrderNo, token = token)
            .requireData().orderId ?: error("未获取到 orderId")

        onStep("正在查询订单详情")
        val detail = api.orderDetail(orderId = orderId, token = token).requireData()
        val result = toUnlockResult(detail = detail, orderNo = finalOrderNo, orderId = orderId)
        orderHistoryStore.add(
            OrderHistoryItem(
                orderNo = result.orderNo,
                orderId = result.orderId,
                goodsName = device.goodsName.ifBlank { "未命名设备" },
                originPrice = result.originPrice,
                ticketCost = result.ticketCost,
                integralCost = result.integralCost,
                otherPromotions = result.otherPromotions,
                completedAt = result.completedAt,
                realPrice = result.realPrice,
                payTypeName = result.payTypeName,
                tokenCoinDiscount = result.tokenCoinDiscount,
            ),
        )
        result
    }

    /**
     * 服务端账单历史（`order/list`，2026-10-14）：账号下的全部订单，分页。
     * 与本地快照（最多 20 笔、只记本机结算）互补；失败照常抛给上层。
     */
    suspend fun serverOrders(page: Int = 1, pageSize: Int = 20): List<ServerOrderItem> {
        val resp = api.orderList(page = page.toString(), pageSize = pageSize.toString(), token = requireToken())
        resp.throwIfFailed()
        return resp.data?.items ?: emptyList()
    }

    /**
     * 拉一条服务端账单的明细（`order/detail`）并转成界面用的 [UnlockResult]。
     * `orderId` 取 [ServerOrderItem.id]（服务端订单标识）；`orderNo` 原样回填展示用。
     */
    suspend fun serverOrderDetail(item: ServerOrderItem): UnlockResult {
        val orderId = item.id ?: error("账单缺少订单标识")
        val detail = api.orderDetail(orderId = orderId, token = requireToken()).requireData()
        return toUnlockResult(detail = detail, orderNo = item.orderNo ?: orderId, orderId = orderId)
    }

    /**
     * `order/detail` → 界面用的 [UnlockResult]（DESIGN §4.10 账单口径）。
     * 现金实付走服务端 `tradeOrderItem.realPrice` → `payPrice` → null（回退公式）；
     * 小票支付（promotionList type=4，回退 tokenCoinDiscount）不在这里合并——
     * 「本次花费 = 小票支付 + 现金实付」由 domain.calculateActualCost 统一计算。
     */
    private fun toUnlockResult(detail: OrderDetailData, orderNo: String, orderId: String): UnlockResult {
        val tradeItem = detail.tradeOrderItem.firstOrNull()
        val ticketCost = detail.promotionList.firstOrNull { it.promotionType == 4 }
            ?.discountAmount ?: "-"
        val integralCost = detail.promotionList.firstOrNull { it.promotionType == 8 }
            ?.discountAmount ?: "-"
        val otherPromotions = detail.promotionList
            .filter { it.promotionType != 4 && it.promotionType != 8 }
            .map { PromotionLine(promotionType = it.promotionType, discountAmount = it.discountAmount) }
        return UnlockResult(
            orderNo = orderNo,
            orderId = orderId,
            originPrice = tradeItem?.originPrice ?: "-",
            ticketCost = ticketCost,
            integralCost = integralCost,
            otherPromotions = otherPromotions,
            completedAt = AppClock.nowMillis(),
            realPrice = tradeItem?.realPrice ?: detail.payPrice,
            payTypeName = detail.payTypeName,
            tokenCoinDiscount = detail.tokenCoinDiscount,
        )
    }

    // ── 错误包装 ──

    private suspend fun <T> diagnosed(step: String, block: suspend () -> T): T =
        try {
            block()
        } catch (e: UnlockException) {
            throw e
        } catch (e: Throwable) {
            throw diagnose(e, step)
        }

    private fun diagnose(error: Throwable, step: String): Nothing {
        val code = if (error.message?.matches(Regex("HTTP \\d+.*")) == true) {
            error.message?.substringAfter("HTTP ")?.substringBefore(":")?.trim()?.toIntOrNull()
        } else null
        val diagnosis = QiekjErrorDiagnosis.diagnose(code, error.message, step)
        throw UnlockException(diagnosis.primaryReason, diagnosis, error)
    }

    private fun diagnosedOf(error: Exception, step: String): UnlockException {
        val diagnosis: DiagnosisResult = QiekjErrorDiagnosis.diagnose(null, error.message, step)
        return UnlockException(diagnosis.primaryReason, diagnosis, error)
    }

    private companion object {
        /** 轮询上限与参考实现一致：1s × 300 次；UI 层另有 165s 自动结算兜底。 */
        const val MAX_POLL_ATTEMPTS = 300

        /**
         * **平台已受理**远程关阀后，最多再等多久让设备回报结束态（`status==5` + 单号）。
         * 等不到就按「未确认用水」收尾——账单仍会由平台生成，只是这次读不到。
         * 机型不支持 / 平台拒绝的路不走这条超时（继续正常轮询等设备回报）。
         */
        const val REMOTE_END_WAIT_MS = 20_000L

        /**
         * promotions 两套常量取自 light-life unlockDevice，字段原样保留：
         * 带 "-6/-7/-8"（用积分）与不带 "-8"（不用积分）两档。
         */
        const val PROMOTIONS_WITH_POINTS =
            """[{"assetId":"0","oldPromotionId":"","orgId":"0","promotionId":"0","promotionType":"-6"},{"assetId":"0","oldPromotionId":"","orgId":"0","promotionId":"0","promotionType":"-7"},{"assetId":"0","oldPromotionId":"0","orgId":"0","promotionId":"0","promotionType":"8"}]"""
        const val PROMOTIONS_WITHOUT_POINTS =
            """[{"assetId":"0","oldPromotionId":"","orgId":"0","promotionId":"0","promotionType":"-6"},{"assetId":"0","oldPromotionId":"","orgId":"0","promotionId":"0","promotionType":"-7"}]"""
    }
}
