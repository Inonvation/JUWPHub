package edu.jxslu.schedule.ui.life

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import edu.jxslu.schedule.Graph
import edu.jxslu.schedule.SubpageScreen
import edu.jxslu.schedule.data.power.PowerException
import edu.jxslu.schedule.data.power.PowerEntryState
import edu.jxslu.schedule.data.power.PowerOrderState
import edu.jxslu.schedule.data.power.PowerModels
import edu.jxslu.schedule.data.power.PowerRepository
import edu.jxslu.schedule.data.power.PowerReadingStore
import edu.jxslu.schedule.data.power.PowerSnapshot
import edu.jxslu.schedule.data.jw.JwVpnDetector
import edu.jxslu.schedule.data.session.NetworkHint
import edu.jxslu.schedule.data.ykt.YktCredentialStore
import edu.jxslu.schedule.domain.InboxKind
import edu.jxslu.schedule.domain.InboxKeys
import edu.jxslu.schedule.ui.common.NoticeTone
import edu.jxslu.schedule.ui.widget.LifeWidgetSync
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 侧栏电费卡状态（DESIGN §3.13）。
 *
 * 电费卡片的数据全在这里；一卡通余额与付款码复用 [edu.jxslu.schedule.ui.campus] 的
 * ViewModel（余额快照、取码、充值、到账判定都在那边，本页只做入口与展示）。
 */
data class PowerCardState(
    val loading: Boolean = false,
    /**
     * 上次成功读数。刷新失败时**保留**它，卡上照旧显示上次的电量与时刻，
     * 另起一行报错——不给一个看不出新旧的数字（DESIGN §3.13）。
     *
     * 冷启动时先进来的可能是本机读数种子（[LifeViewModel.seedPowerFromReadings]），
     * 口径同上：卡上照实显示读数时刻，平台那条回来原地替换。
     */
    val snapshot: PowerSnapshot? = null,
    val error: String? = null,
    /** 凭证没配置（或已随一卡通关闭被清除）：指引去「我的 → 校园卡」开启。 */
    val noCredentials: Boolean = false,
    /**
     * 已在缴费平台取到项目详情，但**没有确认寝室**（`sceneinfo` 为空，2026-10-08）。
     *
     * 这不是读取失败：官方渠道的判据同款，卡面要换「未设置寝室，点此设置」的出口，
     * 由 [PowerRepository.sceneRoot] / `bindScene` 在 App 内完成设置（DESIGN §4.24）。
     * 只在服务端明确判出时置位；网络类失败不清它（拿不准就维持原状）。
     */
    val roomNotBound: Boolean = false,
)

/** 生活卡 UI 状态（生活页已删，只剩侧栏电费卡）。 */
data class LifeUiState(
    val power: PowerCardState = PowerCardState(),
)

/** 一次性事件。 */
sealed interface LifeEvent {
    data class Notice(val text: String, val tone: NoticeTone) : LifeEvent

    /**
     * 打开平台支付页（聚合收银台，DESIGN §4.24）：链接现取现用，交给
     * `PowerBankPayActivity` 的**内嵌 WebView**（用户不用离开 App）。
     *
     * 做成一次性事件而不是状态字段：同一个链接不该因为重组、转屏被重复打开；
     * 页面里另有「用浏览器打开」的兜底出口。
     */
    data class OpenBankCashier(val url: String) : LifeEvent
}

class LifeViewModel(
    private val powerRepo: PowerRepository,
    private val credentialStore: YktCredentialStore,
    /** 本机读数（Room）：侧栏电费卡首屏种子的来源，见 [seedPowerFromReadings]。 */
    private val readingStore: PowerReadingStore,
    /**
     * VPN / 代理探测（默认不探测，测试与不关心网络的调用点不必给）。
     *
     * 只在**网络类**失败时用：取数失败要说清下一步做什么，开着代理就关掉它，
     * 没开就换一条网络（DESIGN §7.6 实测的诱因）。密码不对、平台结构变了这些
     * 与出口无关，照旧报原文。
     */
    private val isVpnActive: () -> Boolean = { false },
) : ViewModel() {

    private val _power = MutableStateFlow(PowerCardState())

    private val _events = Channel<LifeEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) { seedPowerFromReadings() }
    }

    /**
     * 电费卡首屏种子：拿本机最新读数把卡面顶起来（零网络）。
     *
     * 进页链路（登录 → 项目详情 → 读表）冷启动要好几秒，此前这段时间卡面是「—／读取中…」
     * （2026-09-27 用户报「首次进入生活页电费余额还是很慢，手动下拉反而秒出」——下拉时
     * token 已经热了）。读数是同一份数据、来自同一处写入（[PowerRepository.snapshot]），
     * 顶上后平台那条回来原地替换，与「刷新失败保留上次读数」是同一种展示口径。
     *
     * 只在**还没有数**时填：真读数（或刷新失败留下的上次读数）已经在卡上就不动它，
     * 免得旧读数把新的盖回去。凭证没开就没有电费可看，直接跳过。
     */
    private suspend fun seedPowerFromReadings() {
        if (credentialStore.read() == null) return
        val latest = runCatching { readingStore.latest() }.getOrNull() ?: return
        _power.update { state ->
            if (state.snapshot != null) state else state.copy(snapshot = PowerModels.snapshotSeedOf(latest))
        }
    }

    val uiState: StateFlow<LifeUiState> =
        _power.map { LifeUiState(power = it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LifeUiState())

    /**
     * 电费读数（点卡片、右上刷新、充值成功后都走它）。
     *
     * [force] = true 时绕过仓库内存缓存并重取；进页那条路传 false。
     */
    fun refreshPower(force: Boolean = true) {
        val credentials = credentialStore.read()
        if (credentials == null) {
            _power.value = PowerCardState(noCredentials = true)
            return
        }
        _power.update { it.copy(loading = true, error = null, noCredentials = false) }
        viewModelScope.launch {
            try {
                val snapshot = powerRepo.snapshot(credentials.username, credentials.password, force = force)
                _power.update { it.copy(loading = false, snapshot = snapshot, error = null, roomNotBound = false) }
                // 桌面生活小组件的电费副行（DESIGN §3.6 二条目改版）：读数已落 Room，
                // 顺手镜像一次（零网络）
                runCatching { LifeWidgetSync.refreshLifeWidgets(Graph.appContext) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: PowerException.RoomNotBound) {
                // 未确认寝室（DESIGN §4.24）：不是错误，卡面转「未设置寝室，点此设置」的
                // 出口文案，由用户在 App 内选好房间确认后自然恢复
                _power.update { it.copy(loading = false, error = null, roomNotBound = true) }
            } catch (e: PowerException.Credential) {
                _power.update {
                    it.copy(loading = false, error = "${e.message}。若密码已改，请在「我的 → 校园卡」重新验证")
                }
            } catch (e: PowerException.Network) {
                // 卡副行只有一行（约十来个字宽），给短文案；关代理 / 换网络二选一
                _power.update { it.copy(loading = false, error = NetworkHint.briefOf(isVpnActive())) }
            } catch (e: PowerException) {
                _power.update { it.copy(loading = false, error = e.message ?: "电费读取失败") }
            } catch (e: Exception) {
                _power.update { it.copy(loading = false, error = "电费读取失败：${e.message ?: "未知错误"}") }
            }
        }
    }

    // ------------------------------------------------------------------
    // 电费充值（DESIGN §4.24：App 内填金额下单 + 平台聚合收银台网页里付款，2026-11-06 改版）
    // ------------------------------------------------------------------

    /** 一次充值流程的状态。 */
    data class PowerRechargeUi(
        val step: Step = Step.Amount,
        /** 下单成功的订单。 */
        val orderId: String? = null,
        /** 本次充值金额（元，原样字符串）：等待步展示「本次充值 ¥X」用。 */
        val amountYuan: String? = null,
        /** 聚合收银台（内嵌页）的支付链接（[Step.BankPay] 步持有；「重新打开支付页」会重取一条新的）。 */
        val cashierUrl: String? = null,
        /** 已受理之后的查单结果：1 = 平台确认已记账；0 = 查到了但仍是待支付；null = 还没查到 / 查不到。 */
        val paidStatus: Int? = null,
        /**
         * 电量入账状态（2026-09-29 加，与 [paidStatus] 分开）：
         * `paidStatus=1` 只代表**扣款**成功；电量进没进电表看这一位
         * （`order.flag` 第 2 位，[PowerEntryState]）。null = 还没查到入账位。
         */
        val entryStatus: PowerEntryState? = null,
        /**
         * 支付页那步的中性提示（灰字一行）：查单还没到账、重开链接等，都不是错误。
         *
         * 与 [error] 分开放：这里的话不该长成红色「出错了」的样子。
         */
        val bankNote: String? = null,
        val busy: Boolean = false,
        val error: String? = null,
        /**
         * 打开弹层时清掉的未支付订单数（>0 才展示）。
         *
         * 平台不自动清过期单、堆积会让新下单 500，所以每次点开都清一次；清了几笔
         * 直接写在弹层里（这条提示落在弹层这个独立窗口内，Snackbar 会被弹层盖住）。
         */
        val cleanedOrders: Int = 0,
    ) {
        /**
         * 流程步骤：金额 → 支付页（内嵌聚合收银台，等用户在里面付）→ 受理结果。
         *
         * 2026-11-06 前的 [Password]（电子账户 6 位消费密码）已删：支付方式改在网页里选，
         * 校园卡 / 电子帐户都由平台收银台自己扣，App 不再经手任何支付密码。
         */
        enum class Step { Amount, BankPay, Accepted }
    }

    private val _powerRecharge = MutableStateFlow(PowerRechargeUi())
    val powerRecharge: StateFlow<PowerRechargeUi> = _powerRecharge.asStateFlow()

    /**
     * 「点开充值弹层」时挂的清理任务（见 [preparePowerRecharge]）。
     * [placePowerOrder] 下单前会 `join()` 它——清理与下单抢跑就白清了：平台侧未支付单
     * 还没删掉，新下单照样回 500「未知异常」。
     */
    private var pendingOrderCleanup: Job? = null

    /**
     * 流程会话号：每次打开弹层（[preparePowerRecharge]）自增。
     *
     * 异步结果回来时若会话已变（用户关掉弹层又重开，或点了两次「电费充值」），整批丢弃——
     * 否则上一轮的结果会写进新一轮界面：最典型的是「支付成功后马上重开弹层，新弹层直接
     * 显示支付成功」。钱相关的流程，状态串台比多写几行判断更糟。
     */
    private var flowSession = 0

    /** 结果是否仍属于当前这一轮流程。 */
    private fun isCurrentSession(session: Int) = session == flowSession

    /**
     * 打开电费充值弹层时调用（DESIGN §4.24）：重置流程状态 + 清一遍未支付订单。
     *
     * 为什么在这里清：未支付单只可能由本流程产生（下单后没付完就退出），而堆积会让
     * 新下单 500。放在这个点，既是用户主动动作，又正好在真正需要之前——侧栏刷新
     * 不必再为它多打 1 + N 条请求。失败静默：清不掉不该挡用户看弹层，下单失败时
     * 服务端原话会照实显示。
     */
    fun preparePowerRecharge() {
        flowSession++
        _powerRecharge.value = PowerRechargeUi()
        val credentials = credentialStore.read() ?: return
        pendingOrderCleanup?.cancel()
        pendingOrderCleanup = viewModelScope.launch {
            val cleaned = try {
                powerRepo.cancelAllPendingOrders(credentials.username, credentials.password)
            } catch (e: CancellationException) {
                // 连点两次「电费充值」会取消上一条，取消不算失败——照项目口径重新抛出
                throw e
            } catch (e: Exception) {
                0
            }
            if (cleaned > 0) _powerRecharge.update { it.copy(cleanedOrders = cleaned) }
        }
    }

    /**
     * 下单（金额已由弹层校验过）。
     *
     * **支付一律在平台的聚合收银台网页里完成**（2026-11-06 用户拍板改版）：
     * 下单成功就把收银台链接取回来、交给内嵌页，弹层直接落「等待支付结果」步。
     * 支付方式（校园卡 / 电子帐户 / 农行缴电费专用…）由页面里的「支付方式」选，
     * App 不再选渠道、也不碰任何支付密码。
     */
    fun placePowerOrder(yuan: String) {
        val credentials = credentialStore.read() ?: run {
            _events.trySend(LifeEvent.Notice("请先在「我的 → 校园卡」开启一卡通", NoticeTone.Warning))
            return
        }
        // 保留 cleanedOrders（那条提示在弹层里，下单时不该被抹掉），其余字段回到干净起点
        val session = flowSession
        _powerRecharge.update {
            it.copy(
                step = PowerRechargeUi.Step.Amount,
                orderId = null,
                amountYuan = yuan,
                cashierUrl = null,
                paidStatus = null,
                entryStatus = null,
                bankNote = null,
                busy = true,
                error = null,
            )
        }
        viewModelScope.launch {
            // 等弹层打开时那次清理跑完再下单（见 pendingOrderCleanup）
            pendingOrderCleanup?.join()
            if (!isCurrentSession(session)) return@launch
            try {
                val order = powerRepo.createOrder(credentials.username, credentials.password, yuan)
                if (!isCurrentSession(session)) return@launch
                openBankCashier(credentials.username, credentials.password, order.orderId, session)
            } catch (e: CancellationException) {
                throw e
            } catch (e: PowerException.RoomNotBound) {
                // 弹层开到一半发现寝室没设置（打开前的判据还没回来等竞态）：报在弹层里，
                // 同时把卡面切到「去设置」——关掉弹层就能看到出口（DESIGN §4.24）
                if (!isCurrentSession(session)) return@launch
                _powerRecharge.value = _powerRecharge.value.copy(busy = false, error = e.message)
                _power.update { it.copy(roomNotBound = true) }
            } catch (e: PowerException) {
                if (!isCurrentSession(session)) return@launch
                _powerRecharge.value = _powerRecharge.value.copy(busy = false, error = e.message)
            } catch (e: Exception) {
                if (!isCurrentSession(session)) return@launch
                _powerRecharge.value = _powerRecharge.value.copy(
                    busy = false,
                    error = "下单失败：${e.message ?: "未知错误"}",
                )
            }
        }
    }

    /**
     * 取聚合收银台链接 → 交给 App 内嵌页打开 → 弹层落到 [PowerRechargeUi.Step.BankPay]。
     *
     * 「重新打开支付页」也走这里（[reopenBankCashier]）：链接里的 ticket 是平台侧一次性会话，
     * 每次现取现用，**不复用旧链接**。
     */
    private suspend fun openBankCashier(
        username: String,
        password: String,
        orderId: String,
        session: Int,
    ) {
        val url = try {
            powerRepo.bankCashier(username, password, orderId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (isCurrentSession(session)) {
                // **订单已经建好了**（createOrder 成功才会走到这里）：把订单号留在状态里，
                // 重试走「重新打开支付页」而不是再下一单——平台在慢网下这一步会超时
                // （2026-11-06 用户连续踩到，见 PowerClient.create 的超时说明）。
                _powerRecharge.value = _powerRecharge.value.copy(
                    busy = false,
                    orderId = orderId,
                    error = "支付页打不开：${e.message ?: "未知错误"}。订单已创建，可点「重新打开支付页」重试",
                )
            }
            return
        }
        if (!isCurrentSession(session)) return
        _powerRecharge.value = _powerRecharge.value.copy(
            step = PowerRechargeUi.Step.BankPay,
            orderId = orderId,
            cashierUrl = url,
            busy = false,
            error = null,
            bankNote = null,
        )
        _events.send(LifeEvent.OpenBankCashier(url))
    }

    /**
     * 「重新打开支付页」（支付步）：重新取一条收银台链接再交给内嵌页。
     *
     * 两个入口都允许：正常等待步（[PowerRechargeUi.Step.BankPay]），以及**下单成功但
     * 收银台没打开**的金额步（`orderId` 已在、`error` 在）——后者是 2026-11-06 补的：
     * 平台慢响应超时后不该再下一单，同一单可以反复取新的收银台会话。
     */
    fun reopenBankCashier() {
        val credentials = credentialStore.read() ?: return
        val state = _powerRecharge.value
        val orderId = state.orderId ?: return
        if (state.step != PowerRechargeUi.Step.BankPay &&
            state.step != PowerRechargeUi.Step.Amount
        ) {
            return
        }
        val session = flowSession
        _powerRecharge.update { it.copy(busy = true, error = null, bankNote = null) }
        viewModelScope.launch {
            openBankCashier(credentials.username, credentials.password, orderId, session)
        }
    }

    /**
     * 查一次这笔充值到没到账（`order.status = 1`）。
     *
     * [silent] = true 是「从浏览器回到 App」的自动路径：没查到**不报错**，只留一句中性提示
     * （银行侧回调常有几十秒延迟，刚回来就报「失败」是假警报）；手动点「检查到账」时
     * 传 false，同样没查到也给同一句实话。
     */
    fun checkBankPaid(silent: Boolean = false) {
        val credentials = credentialStore.read() ?: return
        val state = _powerRecharge.value
        val orderId = state.orderId ?: return
        if (state.step != PowerRechargeUi.Step.BankPay) return
        val session = flowSession
        if (!silent) _powerRecharge.update { it.copy(busy = true, error = null, bankNote = null) }
        viewModelScope.launch {
            val state = powerOrderState(credentials.username, credentials.password, orderId)
            // 扣款事实先留痕（旁路），再判会话——弹层被关掉也不吞「钱已扣」
            if (state?.status == 1) {
                postPowerRechargeInbox(orderId, _powerRecharge.value.amountYuan)
            }
            if (!isCurrentSession(session)) return@launch
            if (state?.status == 1) {
                _powerRecharge.update {
                    it.copy(
                        step = PowerRechargeUi.Step.Accepted,
                        paidStatus = 1,
                        entryStatus = state.entry,
                        busy = false,
                        error = null,
                        bankNote = null,
                    )
                }
                refreshPower()
                // 扣款成功但电量还没入账：再跟几轮查单，别把「处理中」说成最终结果
                if (state.entry != PowerEntryState.ENTERED && state.entry != PowerEntryState.FAILED) {
                    confirmPowerPaid(credentials.username, credentials.password, orderId, session)
                }
            } else {
                _powerRecharge.update {
                    it.copy(
                        busy = false,
                        paidStatus = state?.status,
                        bankNote = when {
                            state?.status == 0 -> "还没查到这笔到账——银行侧回调可能有延迟，稍后再点一次检查。"
                            else -> "暂时查不到这笔订单（网络或平台原因），稍后再试。"
                        },
                    )
                }
            }
        }
    }


    /**
     * 受理后的确认（**不无限轮询**，最多 [ENTRY_CONFIRM_ATTEMPTS] 次查单）：
     * 先看 `status=1`（平台把这笔记账了 → 「已扣款」），再看入账位
     * [PowerEntryState]——2026-09-29 事故（三笔「支付成功」的订单永远没入账）之后，
     * **扣款与入账必须分开报告**：入账没到位就不把话说满。
     *
     * 查不到（网络/平台改版）就保持现状，不给用户任何异常——这一步是锦上添花。
     */
    private fun confirmPowerPaid(username: String, password: String, orderId: String?, session: Int) {
        if (orderId.isNullOrBlank()) return
        viewModelScope.launch {
            for (attempt in 0 until ENTRY_CONFIRM_ATTEMPTS) {
                delay(if (attempt == 0) PAID_CONFIRM_DELAY_MS else ENTRY_CONFIRM_DELAY_MS)
                val state = powerOrderState(username, password, orderId) ?: return@launch
                // 扣款事实先留痕（旁路），再判会话——弹层被关掉也不吞「钱已扣」
                if (state.status == 1) {
                    postPowerRechargeInbox(orderId, _powerRecharge.value.amountYuan)
                }
                if (!isCurrentSession(session)) return@launch
                if (state.status == 1) {
                    _powerRecharge.update { it.copy(paidStatus = 1, entryStatus = state.entry) }
                    // 入账有终态（成功/失败）就收手；还在「未入账」就再等一轮
                    if (state.entry == PowerEntryState.ENTERED || state.entry == PowerEntryState.FAILED) {
                        return@launch
                    }
                } else {
                    // 还在待支付 / 状态查不到：不再空转，界面维持「已受理」原话
                    return@launch
                }
            }
        }
    }

    /** 查单（扣款 + 入账一起拿）。失败按「不知道」返回 null（调用方不做任何推断）。 */
    private suspend fun powerOrderState(username: String, password: String, orderId: String): PowerOrderState? =
        try {
            powerRepo.orderState(username, password, orderId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }

    /**
     * 电费充值扣款留痕收件箱（2026-10-22）：调用点在查单 `status==1`（平台确认已记账）
     * 处——金额在 [amountYuan]（弹层状态里的本次充值金额）。**在 `isCurrentSession` 判定
     * 之前调用**：用户中途关掉弹层、会话号已变时，钱扣了的事实仍然成立，不能被吞；
     * 同一订单两路确认（支付后有限次查单 `confirmPowerPaid` / 手动「检查到账」`checkBankPaid`）
     * 先后到达时按订单号同键落更新。
     * 金额取不到（弹层已重置）就不写金额，宁缺勿错。落点 = 缴费账单页。
     */
    private fun postPowerRechargeInbox(orderId: String, amountYuan: String?) {
        viewModelScope.launch {
            runCatching {
                Graph.inboxRepository(Graph.appContext).post(
                    kind = InboxKind.POWER_RECHARGE,
                    dedupeKey = InboxKeys.POWER_RECHARGE_PREFIX + orderId,
                    title = "电费充值成功",
                    body = buildString {
                        amountYuan?.takeIf { it.isNotBlank() }?.let { append("充值 ¥").append(it).append(" · ") }
                        append("平台已确认扣款")
                    },
                    route = SubpageScreen.POWER_BILL.name,
                    silent = true,
                )
            }
        }
    }

    /** 关闭/取消充值弹层（未支付订单 30 分钟自动失效，DESIGN §4.24）。 */
    fun dismissPowerRecharge() {
        _powerRecharge.value = PowerRechargeUi()
    }

    /** 打开平台页面：先拿深链（顺带保证有 token），失败给一次性提示。 */
    private fun openPlatformPage(
        what: String,
        url: suspend (String, String) -> String,
        onReady: (String) -> Unit,
    ) {
        val credentials = credentialStore.read()
        if (credentials == null) {
            _events.trySend(LifeEvent.Notice("请先在「我的 → 校园卡」开启一卡通，再用${what}", NoticeTone.Warning))
            return
        }
        viewModelScope.launch {
            try {
                onReady(url(credentials.username, credentials.password))
            } catch (e: CancellationException) {
                throw e
            } catch (e: PowerException) {
                _events.send(LifeEvent.Notice(e.message ?: "${what}打不开", NoticeTone.Error))
            } catch (e: Exception) {
                _events.send(LifeEvent.Notice("${what}打不开：${e.message ?: "未知错误"}", NoticeTone.Error))
            }
        }
    }

    companion object {
        /**
         * 受理后等多久查一次单。记账是平台侧同步完成的，留一点余量避免查得太早拿到
         * 还没落库的 `status=0`（查到 0 也不报错，只是文案停在「已受理」）。
         */
        private const val PAID_CONFIRM_DELAY_MS = 1_200L

        /** 入账位的复查间隔（官方渠道实测入账是秒级，几秒内基本到位）。 */
        private const val ENTRY_CONFIRM_DELAY_MS = 3_000L

        /** 入账确认的总查单次数（含第一次）：有界，不无限轮询第三方平台。 */
        private const val ENTRY_CONFIRM_ATTEMPTS = 3

        fun Factory(context: Context) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = LifeViewModel(
                Graph.powerRepository(context.applicationContext),
                Graph.yktCredentialStore(context.applicationContext),
                Graph.powerReadingStore(context.applicationContext),
                isVpnActive = { JwVpnDetector.isVpnActive(context.applicationContext) },
            ) as T
        }
    }
}
