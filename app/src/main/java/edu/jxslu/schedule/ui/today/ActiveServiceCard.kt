package edu.jxslu.schedule.ui.today

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import edu.jxslu.schedule.data.qiekj.QiekjWateringSession
import edu.jxslu.schedule.data.ujing.UjingOrderSnapshot
import edu.jxslu.schedule.domain.EbikeFreeRide
import edu.jxslu.schedule.domain.KqcxAuth
import edu.jxslu.schedule.domain.QzxyWatering
import edu.jxslu.schedule.domain.QzxyWateringFormat
import edu.jxslu.schedule.domain.UjingState
import edu.jxslu.schedule.ui.common.AppCard
import edu.jxslu.schedule.ui.common.AppCardDivider
import edu.jxslu.schedule.ui.common.BreathingDot
import edu.jxslu.schedule.ui.common.rememberAppHaptics
import edu.jxslu.schedule.ui.common.rememberResumedNow
import edu.jxslu.schedule.ui.ebike.KvcxAction
import edu.jxslu.schedule.ui.ebike.KvcxReturnSummary
import edu.jxslu.schedule.ui.ebike.KvcxRideState
import edu.jxslu.schedule.ui.ebike.unlockCooldownSecondsLeft
import edu.jxslu.schedule.ui.qzxy.isQzxyWateringExpired
import edu.jxslu.schedule.ui.qzxy.rememberQzxyWateringClock
import edu.jxslu.schedule.ui.theme.Motion
import edu.jxslu.schedule.ui.theme.semanticColors
import kotlinx.coroutines.delay
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.CheckmarkCircle02
import me.rerere.hugeicons.stroke.ChevronRight

/**
 * 今日页「进行中服务」状态卡（2026-10-21）：快趣骑行 / 胖乖开水 / 趣智洗澡 / U净 洗烘
 * 四项服务谁在进行中谁占一行，一张卡平铺（`AppCardDivider` 分隔），全无则整卡不渲染。
 *
 * 数据源（各自已有的真相源，本卡零新增网络路径）：
 * - 快趣：`KvcxRideController.state`（Graph 单例，进页/下拉各静默查一次，不轮询）；
 * - 胖乖：`WaterViewModel.uiState.flow is Working`（进程内存态，进程重启即失——服务端
 *   会话状态会黏住不可信，不做伪恢复）；
 * - 趣智：`QzxyWateringStore.watering`（落盘），对账沿用今日页已有的 `WateringReconcileEffect`；
 * - U净：`UjingRepository.orderState`（落盘快照），对表沿用 10s TTL 节流。
 *
 * 动效：行的出现/消失走 `AnimatedVisibility`（进 220ms / 退 160ms，ui-common 口径），
 * 卡体高度跟着行的展开/收拢自然收平（**不要再套一层 `animateContentSize`**：两层高度动画
 * 叠加会互相追着跑，行收完了卡还在慢慢缩）；计时文案 `tnum` 等宽 + 每秒走（不做 crossfade，
 * 高频数值口径）；进行中行首一枚 900ms 呼吸圆点（同 `WateringBadge` 手法）。
 *
 * 结束动作的两段反馈（2026-10-29，用户报「点了还车/结束用水没有任何反馈，成功后卡片直接消失」）：
 * 1. **动作中**：行尾动作钮换成置灰的「还车中… 12s」/「结束中…」+ 一枚 12dp 转圈，快趣带已等秒数，
 *    趣智副行直接展示流程当前步骤（`QzxyFlowState.Working.step`）——等待有解释，不是卡住；
 * 2. **成功后**：行数据一没就没，旧版那一帧直接退场；现在先亮 8 秒「已还车 / 已结束用水」完成态
 *    （静态点 + 成功色勾 + 结果摘要），再走常规退场。完成态与进行中行共用同一个
 *    `AnimatedVisibility`（内容就地切换）——分成两条会同时占两行高，卡体当场弹一下。
 *    **完成态本身可点**（2026-10-31 用户要求）：整行进对应的服务页，结果摘要那里还能细看。
 *
 * 快趣骑行中（明确未锁车）把**整张卡片的边缘**点亮（`AppCard.highlighted` +
 * `highlightedFill = false`：主色 1.5dp 描边、不抬底色，180ms 过渡）：卡内行保持平铺，
 * 别在行内再套描边 / 竖条——那是气泡套气泡（2026-10-22 用户口径）。临时锁车 / 锁状态未知不点亮。
 */
@Composable
internal fun ActiveServiceCard(
    /**
     * 快趣骑行状态（能力闸 + 登录双闸已在调用侧滤过）：null 或无在案订单 = 行不出现。
     */
    kvcxState: KvcxRideState?,
    /**
     * 账号方式骑行的免费窗口起点（`ebikeRideStartAt`）：给快趣行的倒计时 / 超时进度条用。
     * 小程序方式的同一起点走 [freeRideStartAt]（那一行优先）；两档由调用侧按能力档传。
     */
    kvcxFreeStartAt: Long? = null,
    /**
     * 微信小程序方式的免费时长计时起点（`ebikeRideStartAt`，DataStore）：
     * `isActive` 为 true = 小程序方式正在骑行（扫码起计时），展示倒计时行——
     * 小程序方式查不了在案订单（无账号 token），但计时是本机动作，状态照样能摆。
     */
    freeRideStartAt: Long?,
    /**
     * 胖乖「出水中」的落盘会话（`QiekjWateringStore`，2026-10-21）：跨窗口同步的真相源。
     * null = 没有进行中的开水；过期残留在 store 读取时已清。
     */
    waterSession: QiekjWateringSession?,
    /** 本窗口的开水流程是否正在「结束中」（置灰按钮 + 文案切换）。 */
    waterStopping: Boolean,
    /** 本窗口能否直接结束（流程在本 Activity 的 ViewModel 里跑；否则只能点行进页）。 */
    waterCanStop: Boolean,
    qzxyWatering: QzxyWatering?,
    /** 趣智对账已确认设备那边结束（转「待核对」，不点亮）。 */
    qzxyDeviceEnded: Boolean,
    /**
     * 趣智「结束用水」流程正在跑（本窗口 ViewModel 的 `flow` 是 `Working`，2026-10-29）：
     * 行切「正在结束…」、动作钮换成禁用的「结束中…」，副行展示当前步骤。
     */
    qzxyStopping: Boolean = false,
    /** 趣智结束中的当前步骤文案（`QzxyFlowState.Working.step`）；null = 还没进到第一步。 */
    qzxyStopStep: String? = null,
    /**
     * 快趣还车的完成态是否在亮（2026-10-29）。由列表层算好传进来而不是卡内自算：
     * 有在案骑行时这张卡被提到课程区之前，还车成功那一刻 `ride` 就没了——卡内自算的话
     * 卡片会带着「已还车」当场挪回列表中间（`animateItem` 滑走），完成态根本看不到。
     */
    kvcxDone: Boolean = false,
    ujingOrder: UjingOrderSnapshot?,
    /** 快趣动作回调（null = 行不渲染）。 */
    kvcxOnUnlock: (() -> Unit)?,
    kvcxOnLock: (() -> Unit)?,
    kvcxOnReturn: (() -> Unit)?,
    waterOnEnd: () -> Unit,
    qzxyOnStop: () -> Unit,
    /** 快趣行整行落点（骑行页：地图 / 完整骑行卡；行内按钮管动作，行本体管「去看详情」）。 */
    onOpenEbike: () -> Unit = {},
    /** 胖乖行整行落点（开水页：完整流程态 / 订单结果卡）。 */
    onOpenWater: () -> Unit = {},
    /** 趣智行整行落点（趣智页：用水状态卡 / 消费记录）。 */
    onOpenQzxy: () -> Unit = {},
    /** U净 行整行落点（U净 页：订单英雄卡 / 扫码）。 */
    onOpenUjing: () -> Unit,
    /**
     * U净 取衣提醒行尾「我已取走」：清掉已完成订单快照（并撤完成闹钟，调用侧做）。
     * 只有这个动作（或 U净 页的「知道了」）能收掉提醒，不设时间上限。
     */
    onUjingDismiss: () -> Unit,
    /** 小程序免费计时行的「结束计时」意图（确认弹窗在调用侧）。 */
    onEndFreeRide: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val ride = kvcxState?.ride
    val kvcxVisible = ride != null && kvcxOnUnlock != null
    // 骑行中 = 明确未锁车（2026-10-22）：高亮**外层卡片边缘**（`AppCard.highlighted`），
    // 行内不再套描边 / 竖条——卡内画高亮只会变成气泡套气泡。临时锁车 / 锁状态未知不点亮。
    val kvcxRiding = kvcxVisible && kvcxOnLock != null && kvcxOnReturn != null && ride?.locked == false
    val qzxyVisible = qzxyWatering != null
    val ujingLive = ujingOrder?.takeIf { !it.isTerminal }
    // 已完成（50）的订单不随行退场（2026-10-29 用户要求：洗完忘了拿）：快照落盘，
    // 一直留到用户点行尾「我已取走」（或 U净 页「知道了」）。取消 / 超时 / 启动失败不留。
    val ujingDone = ujingOrder?.takeIf { UjingState.isWashDone(it.status) }
    val ujingRow = ujingLive != null || ujingDone != null
    // 秒级走时按行隔离（2026-10-22）：此前 `now` 读在卡顶层，免费计时 / 开水期间整卡
    // （含快趣行与按钮）每秒重组。现在时钟收进各自的 Slot，只有那一行每秒重组；
    // 到点由 Slot 回调父级收行（freeRideExpired / waterExpired），父级不再自己走秒。
    val freeRideExpired = remember(freeRideStartAt) { mutableStateOf(false) }
    val waterExpired = remember(waterSession?.startedAtMillis) { mutableStateOf(false) }
    val freeRideVisible = freeRideStartAt != null && !freeRideExpired.value &&
        EbikeFreeRide.isActive(freeRideStartAt, System.currentTimeMillis())
    // 胖乖：落盘会话未过期即「出水中」（store 已在读取时清掉过期残留；页面开着跨过
    // 5 分钟线时由 WaterSlot 的时钟回调把它收掉）
    val waterVisible = waterSession != null && !waterExpired.value &&
        !waterSession.isStale(System.currentTimeMillis())
    // 完成态要交代「刚结束的是哪台设备、用了多久」：数据消失后原对象就没了，留住最后一份
    val lastQzxyWatering = rememberLastValue(qzxyWatering)
    val lastWaterSession = rememberLastValue(waterSession)
    // 快趣的还车摘要还有第二个消失来源：骑行页 8 秒自动「继续找车」会清掉
    // `returnSummary`（2026-10-31）。完成行优先读实时值（同帧落卡不闪），
    // 实时值没了再用最后一份，别让「已还车」的副行突然变成「订单已结束」。
    val lastKvcxSummary = rememberLastValue(kvcxState?.returnSummary)
    // 进行中 ⇄ 完成态交叉淡入时，退场那份读到的 ride / 订单已经是 null——留住最后一份
    // 非空值，旧内容才不会在淡出途中变成空白（与结算卡同一手法）。
    val lastRide = rememberLastValue(ride)
    val lastUjingLive = rememberLastValue(ujingLive)
    val lastUjingDone = rememberLastValue(ujingDone)
    // 结束成功的「已完成」停留（2026-10-29）：动作一成功行数据就没了，旧版那一帧直接退场，
    // 用户既看不到「结束中」也看不到结果。现在先亮 8 秒完成态，再走常规退场。
    // 快趣那份在列表层算（见 [kvcxDone]：它还决定卡片要不要留在原位）。
    val qzxyDone = rememberDoneLinger(qzxyVisible, qzxyStopping)
    val waterDone = rememberDoneLinger(waterVisible, waterStopping)
    // 完成态与进行中行共用一条可见性：内容就地切换，卡体不会同时装两行
    val kvcxRow = kvcxVisible || kvcxDone
    val qzxyRow = qzxyVisible || qzxyDone
    val waterRow = waterVisible || waterDone
    val anyActive = kvcxRow || freeRideVisible || waterRow || qzxyRow || ujingRow
    // 退场沿用最后显示的形态（见 rememberLastRowMode）：自然到期 / 后台还车不在收拢动画里
    // 闪成完成态，完成态停留结束也不闪回进行中
    val kvcxLastMode = rememberLastRowMode(active = kvcxVisible, done = kvcxDone)
    val qzxyLastMode = rememberLastRowMode(active = qzxyVisible, done = qzxyDone)
    val ujingLastMode = rememberLastRowMode(active = ujingLive != null, done = ujingDone != null)

    // 整卡壳的显隐也走 AnimatedVisibility：**不能 early return**——组合一卸载，
    // 收拢动画就没有载体了（用户报的「状态卡片消失是瞬间消失的」正是它）。
    // 全部行退完，卡片再收平 + 淡出；第一行出现时反向展开。
    AnimatedVisibility(
        visible = anyActive,
        enter = EnterRow,
        exit = ExitRow,
        modifier = modifier,
    ) {
        AppCard(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            highlighted = kvcxRiding,
            // 只亮卡边、不抬底色：服务卡本身就在页面底色上，抬高底色会再叠出一层卡片
            highlightedFill = false,
        ) {
            AnimatedVisibility(
                visible = kvcxRow,
                enter = EnterRow,
                exit = ExitRow,
            ) {
                // 进行中 ⇄ 完成态：内容交叉淡入 + 容器高度动画（完成态没有行底进度条，
                // 直接换内容会让卡片矮一截硬跳；见 [rowSwapSpec]）
                AnimatedContent(
                    targetState = kvcxVisible,
                    transitionSpec = { rowSwapSpec() },
                    // 旧内容按顶对齐淡出：文字左缘与首行基线都不动，只有卡片下沿收上去
                    contentAlignment = Alignment.TopStart,
                    label = "kvcxActiveToDone",
                ) { active ->
                    // 退场（active / done 都假）沿用最后显示的形态：自然到期 / 后台自动还车
                    // 不在收拢动画里闪成「已还车」，完成态停留结束也不闪回「骑行中」
                    val showDone = !active && (kvcxDone || kvcxLastMode.active == false)
                    val shownRide = ride ?: lastRide
                    if (!showDone && shownRide != null && kvcxOnUnlock != null &&
                        kvcxOnLock != null && kvcxOnReturn != null
                    ) {
                        KvcxActiveRow(
                            ride = shownRide,
                            state = kvcxState ?: KvcxRideState(),
                            freeStartAt = kvcxFreeStartAt,
                            onUnlock = kvcxOnUnlock,
                            onLock = kvcxOnLock,
                            onReturn = kvcxOnReturn,
                            onOpen = onOpenEbike,
                        )
                    } else if (showDone) {
                        KvcxDoneRow(
                            summary = kvcxState?.returnSummary ?: lastKvcxSummary,
                            onOpen = onOpenEbike,
                        )
                    }
                }
            }
            // 免费时长行（小程序方式）：骑行行不出现时才轮到它（账号方式的骑行信息更全，
            // 两者不会同时成立——能力闸互斥；防御性写法不叠分隔线即可）
            val afterKvcx = kvcxRow || freeRideVisible
            if (freeRideStartAt != null) {
                FreeRideSlot(
                    startAtMillis = freeRideStartAt,
                    onOpen = onOpenEbike,
                    onEndTimer = onEndFreeRide,
                    onExpired = { freeRideExpired.value = true },
                )
            }
            AnimatedDivider(visible = afterKvcx && (waterRow || qzxyRow || ujingRow))
            // Slot 常驻（不是 `waterSession?.let`）：会话消失后还要承载完成态，退场动画也
            // 得有载体——跟着会话一起卸载，那一帧就是硬消失（2026-10-29）
            WaterSlot(
                session = waterSession,
                done = waterDone,
                lastSession = lastWaterSession,
                stopping = waterStopping,
                canStop = waterCanStop,
                onEnd = waterOnEnd,
                onOpen = onOpenWater,
                onExpired = { waterExpired.value = true },
            )
            AnimatedDivider(visible = waterRow && (qzxyRow || ujingRow))
            AnimatedVisibility(
                visible = qzxyRow,
                enter = EnterRow,
                exit = ExitRow,
            ) {
                AnimatedContent(
                    targetState = qzxyVisible,
                    transitionSpec = { rowSwapSpec() },
                    contentAlignment = Alignment.TopStart,
                    label = "qzxyActiveToDone",
                ) { active ->
                    val showDone = !active && (qzxyDone || qzxyLastMode.active == false)
                    val shownWatering = qzxyWatering ?: lastQzxyWatering
                    if (!showDone && shownWatering != null) {
                        QzxyActiveRow(
                            watering = shownWatering,
                            deviceEnded = qzxyDeviceEnded,
                            stopping = qzxyStopping,
                            step = qzxyStopStep,
                            onStop = qzxyOnStop,
                            onOpen = onOpenQzxy,
                        )
                    } else if (showDone) {
                        QzxyDoneRow(watering = lastQzxyWatering, onOpen = onOpenQzxy)
                    }
                }
            }
            AnimatedDivider(visible = qzxyRow && ujingRow)
            AnimatedVisibility(
                visible = ujingRow,
                enter = EnterRow,
                exit = ExitRow,
            ) {
                AnimatedContent(
                    targetState = ujingLive != null,
                    transitionSpec = { rowSwapSpec() },
                    contentAlignment = Alignment.TopStart,
                    label = "ujingActiveToDone",
                ) { live ->
                    val showDone = !live &&
                        (ujingDone != null || ujingLastMode.active == false)
                    val shownLive = ujingLive ?: lastUjingLive
                    if (!showDone && shownLive != null) {
                        UjingActiveRow(order = shownLive, onOpen = onOpenUjing)
                    } else if (showDone) {
                        (ujingDone ?: lastUjingDone)?.let {
                            UjingDoneRow(
                                order = it,
                                onOpen = onOpenUjing,
                                onDismiss = onUjingDismiss,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 免费计时行：**行内自己走秒**（每拍只重组这一行），到点回调 [onExpired] 让父级收行。
 * 父级因此不再持有秒级状态——开水 / 免费计时期间快趣行与按钮不跟着每秒重组。
 */
@Composable
private fun FreeRideSlot(
    startAtMillis: Long,
    onOpen: () -> Unit,
    onEndTimer: () -> Unit,
    onExpired: () -> Unit,
) {
    val now by rememberResumedSecondWhile(startAtMillis) { EbikeFreeRide.isActive(startAtMillis, it) }
    val active = EbikeFreeRide.isActive(startAtMillis, now)
    LaunchedEffect(active) { if (!active) onExpired() }
    AnimatedVisibility(visible = active, enter = EnterRow, exit = ExitRow) {
        FreeRideRow(
            startAtMillis = startAtMillis,
            now = now,
            onOpen = onOpen,
            onEndTimer = onEndTimer,
        )
    }
}

/**
 * 开水行：同上，秒级走时留在本行；超过 5 分钟（过期）回调 [onExpired] 撤行。
 *
 * [session] 可为 null：会话成功后落盘 store 已清空，但完成态（[done]）还要在这条 Slot 里
 * 再亮 8 秒——Slot 若跟着会话一起卸载，退场就没有载体了（2026-10-29）。
 */
@Composable
private fun WaterSlot(
    session: QiekjWateringSession?,
    done: Boolean,
    lastSession: QiekjWateringSession?,
    stopping: Boolean,
    canStop: Boolean,
    onEnd: () -> Unit,
    onOpen: () -> Unit,
    onExpired: () -> Unit,
) {
    val now by rememberResumedSecondWhile(session?.startedAtMillis) { at ->
        session != null && !session.isStale(at)
    }
    val active = session != null && !session.isStale(now)
    LaunchedEffect(active) { if (!active) onExpired() }
    val lastMode = rememberLastRowMode(active = active, done = done)
    AnimatedVisibility(visible = active || done, enter = EnterRow, exit = ExitRow) {
        AnimatedContent(
            targetState = active,
            transitionSpec = { rowSwapSpec() },
            contentAlignment = Alignment.TopStart,
            label = "waterActiveToDone",
        ) { isActive ->
            val showDone = !isActive && (done || lastMode.active == false)
            val shownSession = session ?: lastSession
            if (!showDone && shownSession != null) {
                WaterActiveRow(
                    session = shownSession,
                    now = now,
                    stopping = stopping,
                    canStop = canStop,
                    onEnd = onEnd,
                    onOpen = onOpen,
                )
            } else if (showDone) {
                WaterDoneRow(session = lastSession, onOpen = onOpen)
            }
        }
    }
}

/**
 * 每秒一拍，**只在 RESUMED 且 [stillActive] 为 true 时走**。
 *
 * 两道闸门都不能少：
 * - **RESUMED**（[repeatOnLifecycle]）：`delay` 走的是 Dispatcher 定时器，**不随窗口 STOP 暂停**。
 *   此前这里只有「组合离开才停」，而服务卡在退后台时组合还活着——一次 1 小时的趣智记账
 *   就是 3600 次进程唤醒 + 3600 次快照写，U净 洗烘同理。灭屏/退后台立刻停、回前台立刻续。
 * - **[stillActive]**：到点落一次最终值后停表，别让一行静态文案继续每秒重组。
 * key 变化时先落当前值（`produceState` 的 initialValue 只在首次组合生效，重启必须显式赋值）。
 *
 * **为什么不直接用 `ui/common` 的 [rememberResumedNow]**：那个 API 只有 RESUMED 闸门、
 * 没有「条件到点停表」的入参，而这四行各有自己的终止条件（免费窗口到期 / 会话过期 /
 * 转「待核对」/ 订单不再洗涤中）。给它加入参就得改公共件（本轮不动公共件），
 * 所以这里保留一个**带闸门**的私有版本；公共件那份仍是「无条件走表」场景的唯一实现。
 *
 * **key 必须带上状态位**（如 `startedAtMillis to pending`）：状态翻面时 produceState 重启，
 * 否则闭包里捕获的是旧 [stillActive]，行会一直空转。
 */
@Composable
private fun rememberResumedSecondWhile(
    key: Any?,
    stillActive: (Long) -> Boolean,
): State<Long> {
    val owner = LocalLifecycleOwner.current
    return produceState(initialValue = System.currentTimeMillis(), key, owner) {
        value = System.currentTimeMillis()
        if (!stillActive(value)) return@produceState
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            // 回前台先补一拍：定格在退后台那一刻的值会让「已用时」少算一段
            value = System.currentTimeMillis()
            while (stillActive(value)) {
                delay(1_000)
                value = System.currentTimeMillis()
            }
        }
    }
}

/**
 * 完成态停留时长（2026-10-31 用户口径 8 秒）：1.4 秒只够看见，来不及看清结果，
 * 也来不及点进详情。
 */
private const val DoneLingerMillis = 8_000L

/**
 * 动作收尾后多久内数据消失还算「这次动作的结果」。
 *
 * 成功时数据与动作**同帧**收尾（`ride = null` / `wateringStore.clear()` 就在动作块里），
 * 但失败时动作先结束、数据还在——过一阵的到期 / 别处清理不能算成「刚结束」，
 * 否则会把 165 秒自动关阀、后台自动还车也报成用户刚点的完成态。
 */
private const val EndActionGraceMillis = 6_000L

/**
 * 外层卡片退场（[ExitRow]，160ms）之后多留的余量。
 *
 * 列表层（`TodayContent`）用它决定条目什么时候允许换位：退场动画还在画卡片时挪条目，
 * `animateItem` 会演成「一边淡出一边往下掉」。等退场画完再换位，换的就是一张不可见的卡。
 */
internal const val ServiceCardExitGraceMillis = 200L

/**
 * 「结束成功 → 完成态停留」的帧级状态机（原 `rememberDoneLinger` 的判据抽出，2026-10-31）。
 *
 * 输入每帧的 (visible, ending)，返回该帧是否显示完成态。
 *
 * **必须在组合期同步推进**，不能放进 `LaunchedEffect`：行数据消失与完成态亮起必须同一帧。
 * 异步会晚一帧——那一帧「有骑行 → 卡片置顶」的判据先掉回 false，`LazyColumn` 把条目从
 * 顶部挪到列表下方（`animateItem` 开始位移动画），下一帧完成态亮起再把条目挪回顶部：
 * 用户看到的就是一张「已还车」卡从下面窜上来（2026-10-31 报障）。
 *
 * [visible] 翻真时清掉停留；翻假时只有「跑过结束动作」（[ending] 曾为真）才亮完成态——
 * 到期自然收尾 / 后台自动还车不算。
 */
internal class DoneLingerGate(visible: Boolean = false) {
    private var lastVisible = visible
    private var sawEnding = false
    private var lingering = false

    /** 推进一帧，返回该帧是否显示完成态。 */
    fun frame(visible: Boolean, ending: Boolean): Boolean {
        // 动作与数据同帧收尾时，顺序不能反：先记「刚结束」，再判「数据没了」
        if (ending) sawEnding = true
        if (visible != lastVisible) {
            lastVisible = visible
            if (visible) {
                lingering = false
            } else if (sawEnding) {
                sawEnding = false
                lingering = true
            }
        }
        return lingering
    }

    /** 宽限期到：结束动作没等到数据消失（失败 / 被挡），之后的数据退场不算「刚结束」。 */
    fun expireEnding() {
        sawEnding = false
    }

    /** 停留时间到。 */
    fun expireLinger() {
        lingering = false
    }
}

/**
 * 「结束成功 → 完成态停留」闸（2026-10-29，判据 2026-10-31 改为帧级同步）。
 *
 * 组合期同步推进 [DoneLingerGate]：状态写入只发生在跳变那一帧，其余时候只读。
 * 8 秒停留与 6 秒宽限是异步的，放副作用里。
 *
 * `internal` 而不是私有：快趣那份要在列表层（`TodayContent`）算——它还决定卡片要不要
 * 留在课程区之前（见 `ActiveServiceCard` 的 `kvcxDone`）。
 */
@Composable
internal fun rememberDoneLinger(visible: Boolean, ending: Boolean): Boolean {
    val gate = remember { DoneLingerGate(visible) }
    val linger = remember { mutableStateOf(false) }
    val shown = gate.frame(visible = visible, ending = ending)
    if (linger.value != shown) linger.value = shown
    LaunchedEffect(linger.value) {
        if (linger.value) {
            delay(DoneLingerMillis)
            gate.expireLinger()
            linger.value = false
        }
    }
    LaunchedEffect(ending) {
        if (!ending) {
            delay(EndActionGraceMillis)
            gate.expireEnding()
        }
    }
    return linger.value
}

/** 记住最后一个非空值：行数据消失后，完成态还要用它交代「刚结束的是哪台设备」。 */
@Composable
private fun <T : Any> rememberLastValue(value: T?): T? {
    val last = remember { mutableStateOf(value) }
    LaunchedEffect(value) { if (value != null) last.value = value }
    return last.value
}

/**
 * 行退场时沿用最后一次显示的形态（2026-10-31）。
 *
 * `active` / `done` 同时翻假的那一帧，外层 `AnimatedVisibility` 还要用 160ms 收拢；
 * 内容若按当前值二选一，会在收拢动画里闪成另一态——自然到期（开水 5 分钟 / 趣智 1 小时）
 * 闪「已结束」、后台自动还车闪「已还车」。这里记住最后一次真正显示过的形态，退场沿用它。
 *
 * 普通持有者，不驱动重组：形态变化本身一定伴随一次组合，读到的就是当帧的最新值。
 */
private class LastRowMode {
    /** true = 最后显示进行中；false = 最后显示完成态；null = 还没显示过。 */
    var active: Boolean? = null
}

@Composable
private fun rememberLastRowMode(active: Boolean, done: Boolean): LastRowMode {
    val holder = remember { LastRowMode() }
    if (active) {
        holder.active = true
    } else if (done) {
        holder.active = false
    }
    return holder
}

/** 完成态行尾的成功色勾（静态；呼吸点已停，勾负责把「结束了」说清）。 */
@Composable
private fun DoneCheck() {
    androidx.compose.material3.Icon(
        HugeIcons.CheckmarkCircle02,
        contentDescription = null,
        tint = MaterialTheme.semanticColors.success,
        modifier = Modifier.size(18.dp),
    )
}

/** 卡内分隔线的显隐也走收拢/淡出：行在退场时分隔线同步收掉，不闪一截硬边。 */
@Composable
private fun AnimatedDivider(visible: Boolean) {
    AnimatedVisibility(visible = visible, enter = EnterRow, exit = ExitRow) {
        AppCardDivider()
    }
}

/**
 * 行的进出场规格（[Motion] 口径：进 reveal 220ms / 退 state 180ms，顶部展开收拢）。
 *
 * 曲线也跟着档位走了（[Motion.reveal] 是 decelerate、[Motion.state] 是 standard）：
 * 原来这里写的是 `FastOutSlowInEasing`，与全 App 其余 200ms 级动画不同曲线——这正是
 * 2026-11-06 归并要清掉的东西。**别为了「和以前一模一样」在这里另写 easing**。
 */
private val EnterRow = fadeIn(Motion.reveal()) +
    expandVertically(Motion.reveal(), expandFrom = Alignment.Top)
private val ExitRow = fadeOut(Motion.state()) +
    shrinkVertically(Motion.state(), shrinkTowards = Alignment.Top)

/**
 * 进行中 ⇄ 完成态的就地切换（2026-10-31）：内容交叉淡入 + 容器高度 200ms 收平。
 *
 * 完成态没有行底进度条，比进行中矮 11dp；直接换内容会让卡片高度硬跳一截（用户报
 * 「卡片高度瞬间少 11dp」）。`SizeTransform` 把这段高度做成动画，旧内容按 TopStart
 * 对齐淡出——文字左缘与首行基线不动，只有卡片下沿收上去。**别把 `contentAlignment`
 * 改成 Center**：两态高度不同，居中会让文字在过渡中上下滑一下。
 *
 * `using` 是 [AnimatedContentTransitionScope] 的成员扩展，只能在 `transitionSpec`
 * 的接收者作用域里调用，所以做成扩展函数而不是顶层 `ContentTransform` 常量。
 */
private fun AnimatedContentTransitionScope<*>.rowSwapSpec(): ContentTransform =
    (fadeIn(Motion.state()) togetherWith fadeOut(Motion.tap())).using(
        SizeTransform(clip = true) { _, _ ->
            // 高度补间与淡入淡出同档（state 180）：两态差 11dp，比内容淡出更慢会看到「文字
            // 已经换完、卡片下沿还在缩」
            Motion.state<IntSize>()
        },
    )

/** 品牌图标：与侧栏同款位图，24dp。**必须用 `Image`**——`Icon(painter)` 会把位图整体
 *  染成 LocalContentColor（真机上呈黑色块），侧栏同款位图就是用 `Image` 渲染的。 */
@Composable
private fun BrandIcon(resId: Int) {
    androidx.compose.foundation.Image(
        painter = androidx.compose.ui.res.painterResource(resId),
        contentDescription = null,
        modifier = Modifier.size(24.dp),
    )
}

/**
 * 单行骨架：呼吸点 + 品牌图标 + 两行文本（标题 + 副行）+ 右侧动作区。
 * 计时走秒由各自的副行文案负责；文本统一 `tnum` 等宽防抖。
 *
 * [pulsing] = 圆点是否呼吸（默认是）：「待核对」这类**已结束待处理**的行不呼吸——
 * 呼吸的含义是「正在进行中」，结束时呼吸反而是误导。
 *
 * 点击反馈（2026-10-22 用户报「矩形的丑陋灰色背景边框」）：不再用默认灰色涟漪——
 * 圆角裁切 + 按下时浅主色底、0.985 轻微回缩（与侧栏行 / 扫一扫同族），松手回弹。
 * 行的「骑行中」高亮**不在这里**：它归外层卡片边缘（`ActiveServiceCard` 的
 * `AppCard.highlighted`），行内保持平铺，与其它服务行同一轮廓。
 *
 * [status] = 标题后的状态词（如「骑行中」，主色 `labelSmall`）：骑行态不只靠卡边描边
 * 表达，也进无障碍朗读。null = 不显示。
 *
 * [progress] = 行底的时长进度条（0..1，可为 null = 不画）：快趣免费窗口倒计时 / 超时增长，
 * 开水、趣智、U净 的进行中时长。`progressError` 时走 error 色（超时）。
 *
 * [dotColor] = 不呼吸时那枚静态点的颜色（默认 `outlineVariant`，语气降下来）。
 * U净 取衣提醒传 `semanticColors.warning`：「已结束但还要用户处理」需要一个注意力信号，
 * 而呼吸点只属于进行中，不能借用。
 */
@Composable
private fun ActiveRow(
    brandRes: Int,
    title: String,
    subtitle: String,
    trailing: @Composable RowScope.() -> Unit,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    pulsing: Boolean = true,
    dotColor: Color? = null,
    status: String? = null,
    statusColor: Color? = null,
    progress: Float? = null,
    progressError: Boolean = false,
) {
    val haptics = rememberAppHaptics()
    val scheme = MaterialTheme.colorScheme
    val rowShape = RoundedCornerShape(12.dp)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.985f else 1f,
        label = "serviceRowPress",
    )
    val containerColor by animateColorAsState(
        targetValue = if (pressed) scheme.primary.copy(alpha = 0.05f) else Color.Transparent,
        animationSpec = Motion.state(),
        label = "serviceRowContainer",
    )
    // 进度条补间（[Motion.page] 口径）：计时每秒更新一次，条形平滑推进，不做数字 crossfade；
    // 行切换时 progress 变 null，进度条随容器高度一起收掉。
    val animatedProgress by animateFloatAsState(
        targetValue = (progress ?: 0f).coerceIn(0f, 1f),
        animationSpec = Motion.page(),
        label = "serviceRowProgress",
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clip(rowShape)
            .background(containerColor)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interaction,
                        // 默认涟漪是灰色矩形（用户报的就是它）；按下感由上面的底色 + 回缩表达
                        indication = null,
                        onClickLabel = onClickLabel ?: title,
                        onClick = {
                            haptics.tap()
                            onClick()
                        },
                    )
                } else {
                    Modifier
                },
            )
            .heightIn(min = 52.dp)
            .padding(vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (pulsing) {
                BreathingDot()
                Spacer(Modifier.width(8.dp))
            } else {
                // 占位同尺寸的静态点（默认弱色）：布局不跳，语气降下来；
                // 取衣提醒这类「待处理」传 dotColor 换个语气色
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(dotColor ?: MaterialTheme.colorScheme.outlineVariant, CircleShape),
                )
                Spacer(Modifier.width(8.dp))
            }
            BrandIcon(brandRes)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                // 标题与状态词按**基线**对齐（2026-10-31）：两者字号不同（bodyMedium /
                // labelSmall），CenterVertically 会让小一号的状态词比标题低 2sp 左右，
                // 一行里看着「没坐齐」。alignByBaseline 把首行基线锁在一起。
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        // 标题先让位：状态词是固定宽度的短标签，不能被长标题挤成省略号
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .alignByBaseline(),
                    )
                    if (status != null) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = status,
                            style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                            color = statusColor ?: MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            modifier = Modifier.alignByBaseline(),
                        )
                    }
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            trailing()
        }
        if (progress != null) {
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 7.dp)
                    .height(4.dp),
                color = if (progressError) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
            )
        }
    }
}

/**
 * 快趣骑行行：副行 = 车号 · 已骑时长（本地走时）· 电量；右侧「开锁⇄锁车」+「还车」。
 * 行本体点击进骑行页（[onOpen]）——完整骑行卡 / 地图在那里；行内按钮管动作，行本体管「去看」。
 *
 * 锁态口径（与骑行页同源）：`locked == false` 显「锁车」（免确认）；`locked == true`
 * 显「开锁」；悬置态（开锁未确认 / 锁车确认中）显「确认中…」置灰。开锁后 3 秒冷却内
 * **「开锁 / 锁车 / 还车」一起置灰并显示剩余秒**（2026-10-29 用户口径「都置灰」，
 * 与骑行页同口径）。还车与骑行页共用同一确认闸（调用方接 `kvcxConfirmDialog`）。
 */
@Composable
private fun KvcxActiveRow(
    ride: KqcxAuth.Ride,
    state: KvcxRideState,
    /** 免费窗口起点（`ebikeRideStartAt`）；null / ≤0 = 没有计时，不画进度条。 */
    freeStartAt: Long?,
    onUnlock: () -> Unit,
    onLock: () -> Unit,
    onReturn: () -> Unit,
    onOpen: () -> Unit,
) {
    val haptics = rememberAppHaptics()
    // 骑行行没有「到点停表」的语义：只要这行在组合里就要逐秒刷新（免费窗口倒计时 / 超时分钟数 /
    // 动作已等秒数 / 开锁冷却），故直接走公共件 `rememberResumedNow`（RESUMED 才走）。
    val now by rememberResumedNow(1_000L, key = ride.carNum to state.fetchedAt)
    val elapsed = rideElapsedSecondsShared(ride, state.fetchedAt, now)
    val locked = ride.locked == true
    val pending = state.unlockPending || state.lockPending
    val busy = state.busy
    // 动作跑起来之后按钮要说话（2026-10-29）：旧版还车只把两枚钮一起置灰，用户看不出
    // 点上了没有。还车链路最坏可挂一两分钟（定位 + 多次查询各自超时），带上已等秒数。
    val busySeconds = if (busy != null && state.busyAt > 0L) {
        ((now - state.busyAt) / 1_000L).toInt().coerceAtLeast(0)
    } else {
        0
    }
    val cooldownLeft = unlockCooldownSecondsLeft(state.unlockCooldownUntil, now)

    // 免费窗口进度（2026-10-22）：15 分钟内倒计时条（与骑行页同一条口径），
    // 过点后切成 error 色的超时增长条；状态词报「超时 X 分钟」，进度条按 15 分钟一格
    // 增长（见 EbikeFreeRide.overtimeProgressFraction）。锁车后照挂：计费仍在继续。
    val freeStart = freeStartAt?.takeIf { it > 0L }
    val overtime = freeStart != null && !EbikeFreeRide.isActive(freeStart, now)
    val overtimeMinutes = if (overtime) {
        EbikeFreeRide.overtimeMinutes(freeStart, now).coerceAtLeast(1)
    } else {
        0
    }
    val freeProgress = when {
        freeStart == null -> null
        overtime -> EbikeFreeRide.overtimeProgressFraction(freeStart, now)
        else -> EbikeFreeRide.progressFraction(freeStart, now)
    }
    val status = when {
        overtime -> "超时 $overtimeMinutes 分钟"
        ride.locked == false && freeStart != null ->
            "骑行中 · " + EbikeFreeRide.formatRemaining(
                EbikeFreeRide.remainingSeconds(freeStart, now),
            )
        ride.locked == false -> "骑行中"
        else -> null
    }
    val statusColor = if (overtime) MaterialTheme.colorScheme.error else null

    ActiveRow(
        brandRes = edu.jxslu.schedule.R.drawable.ic_brand_kqcx,
        title = "快趣骑行",
        status = status,
        statusColor = statusColor,
        progress = freeProgress,
        progressError = overtime,
        onClick = onOpen,
        onClickLabel = "查看骑行详情",
        subtitle = buildString {
            append(ride.carNum)
            elapsed?.let { append(" · 已骑 ").append(KqcxAuth.formatRideDuration(it)) }
            ride.batteryPercent?.let { append(" · 电量 ").append(it).append("%") }
            // 临时锁车 ≠ 订单结束：不写清楚，用户会以为已经还了车（与骑行页状态胶囊同口径）
            if (ride.locked == true) append(" · 已锁车（计费继续）")
        },
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                when {
                    // 动作在跑：只留一枚置灰的「还车中… 12s」+ 转圈。旧版是「确认中…」
                    // 旁边挂一枚禁用的「还车」，看不出是哪一个动作在跑（2026-10-29）
                    busy != null -> ActionChip(
                        label = buildString {
                            append(busy.label())
                            if (busySeconds > 0) append(" ").append(busySeconds).append("s")
                        },
                        enabled = false,
                        busy = true,
                        onClick = {},
                    )
                    // 悬置（指令已发、车侧还没回报）：只置灰，不加转圈——这一段可能挂到
                    // 下次刷新才消解，转圈转久了反而像卡死
                    pending -> ActionChip(label = "确认中…", enabled = false, onClick = {})
                    else -> {
                        // 开锁 ⇄ 锁车（免确认的是锁车；开锁走重发解锁那条现成路）
                        // 冷却期两枚都置灰（2026-10-29）：已锁态这枚也是物理写，
                        // 「重新使用该车」建单后服务端还在落账，早点点下去没有结果
                        ActionChip(
                            label = when {
                                locked && cooldownLeft > 0 -> "开锁 ${cooldownLeft}s"
                                locked -> "开锁"
                                cooldownLeft > 0 -> "锁车 ${cooldownLeft}s"
                                else -> "锁车"
                            },
                            enabled = cooldownLeft <= 0,
                            onClick = {
                                haptics.tap()
                                if (locked) onUnlock() else onLock()
                            },
                        )
                        Spacer(Modifier.width(6.dp))
                        ActionChip(
                            label = if (cooldownLeft > 0) "还车 ${cooldownLeft}s" else "还车",
                            enabled = cooldownLeft <= 0,
                            filled = false,
                            onClick = {
                                haptics.tap()
                                onReturn()
                            },
                        )
                    }
                }
            }
        },
    )
}

/** 忙碌动作的按钮文案（与骑行页底栏同族：开锁中… / 锁车中… / 还车中…）。 */
private fun KvcxAction.label(): String = when (this) {
    KvcxAction.UNLOCK, KvcxAction.DIRECT_UNLOCK, KvcxAction.RETRY_UNLOCK, KvcxAction.RESUME -> "开锁中…"
    KvcxAction.LOCK -> "锁车中…"
    KvcxAction.RETURN -> "还车中…"
    KvcxAction.RING -> "响铃中…"
    KvcxAction.QUERY_LOCK -> "查询中…"
}

/**
 * 快趣「已还车」完成态（2026-10-29，停留 8 秒 / 可点 2026-10-31）：订单结束、行数据消失后停留。
 * 副行 = 车号 · 本次时长 · 费用（`returnSummary` 就是还车那一刻算出来的那一份）。
 * 点整行进骑行页——完成态也要能看结果明细（用户要求）。
 */
@Composable
private fun KvcxDoneRow(summary: KvcxReturnSummary?, onOpen: () -> Unit) {
    ActiveRow(
        brandRes = edu.jxslu.schedule.R.drawable.ic_brand_kqcx,
        title = "快趣骑行",
        status = "已还车",
        pulsing = false,
        // 完成态状态词与行尾勾同色（success）：别让「已还车」还是进行中的主色
        statusColor = MaterialTheme.semanticColors.success,
        onClick = onOpen,
        onClickLabel = "查看骑行详情",
        subtitle = buildString {
            summary?.carNum?.let { append("车 ").append(it) }
            summary?.durationSeconds?.let { seconds ->
                if (isNotEmpty()) append(" · ")
                append("已骑 ").append(KqcxAuth.formatRideDuration(seconds))
            }
            summary?.feeCents?.let { cents ->
                if (isNotEmpty()) append(" · ")
                append("¥%.2f".format(cents / 100.0))
            }
        }.ifBlank { "订单已结束" },
        trailing = { DoneCheck() },
    )
}

/**
 * 快趣免费时长行（微信小程序方式，2026-10-21 补）：小程序方式查不了在案订单
 * （无账号 token），但「点扫一扫起计时」是本机动作——`ebikeRideStartAt` 落了盘，
 * 免费窗口内照样有状态可摆。副行 = 免费剩余倒计时（`remainingSeconds` 钳满格，
 * 起点缓冲期显 15:00 不动），点整行进骑行页（查车 / 图层仍可用）。
 *
 * 右侧「结束计时」（2026-10-21）：小程序里提前还车后本地倒计时会挂到 15 分钟
 * （幽灵倒计时），给一个显式收尾出口；调用方负责确认弹窗，这里只发意图。
 * 计时到点（`isActive` 为 false）行自动消失。
 */
@Composable
private fun FreeRideRow(
    startAtMillis: Long,
    now: Long,
    onOpen: () -> Unit,
    onEndTimer: () -> Unit,
) {
    val haptics = rememberAppHaptics()
    ActiveRow(
        brandRes = edu.jxslu.schedule.R.drawable.ic_brand_kqcx,
        title = "快趣骑行 · 免费时长",
        subtitle = "剩余 " + EbikeFreeRide.formatRemaining(
            EbikeFreeRide.remainingSeconds(startAtMillis, now),
        ) + "（超时开始计费）",
        // 免费窗口倒计时条（与骑行页同口径：满格开始、随免费时间流干）
        progress = EbikeFreeRide.progressFraction(startAtMillis, now),
        onClick = onOpen,
        onClickLabel = "查看骑行页",
        trailing = {
            ActionChip(
                label = "结束计时",
                filled = false,
                onClick = {
                    haptics.tap()
                    onEndTimer()
                },
            )
        },
    )
}

/**
 * 胖乖开水行（2026-10-21 改为落盘会话驱动）：副行 = 设备 · 出水计时；右侧「结束开水」。
 * 行本体进开水页。耗时按会话起点本地走（同一轮出水只登记一次，不随步骤推进重置）。
 *
 * [canStop] = 开水流程在**本窗口**的 ViewModel 里跑：从侧栏面板/今日页发起时能在这儿结束；
 * 若流程在开水页（另一个 Activity 的 ViewModel）里跑，这里只展示与跳转——跨窗口结束
 * 需要那边的 ViewModel，硬做只会发一条注定落空的结束请求。
 */
@Composable
private fun WaterActiveRow(
    session: QiekjWateringSession,
    now: Long,
    stopping: Boolean,
    canStop: Boolean,
    onEnd: () -> Unit,
    onOpen: () -> Unit,
) {
    val haptics = rememberAppHaptics()
    ActiveRow(
        brandRes = edu.jxslu.schedule.R.drawable.ic_brand_pangguai,
        title = when {
            stopping -> "胖乖生活 · 正在结束…"
            canStop -> "胖乖生活 · 出水中"
            else -> "胖乖生活 · 出水中（点按查看）"
        },
        onClick = onOpen,
        onClickLabel = "查看开水详情",
        subtitle = buildString {
            append(session.deviceName.ifBlank { "开水设备" })
            append(" · ")
            append(QzxyWateringFormat.duration((now - session.startedAtMillis).coerceAtLeast(0L)))
        },
        // 出水进度（与开水页同口径：165 秒自动关阀兜底）
        progress = session.progressFraction(now),
        trailing = {
            if (canStop) {
                ActionChip(
                    label = if (stopping) "结束中…" else "结束开水",
                    enabled = !stopping,
                    busy = stopping,
                    onClick = {
                        haptics.tap()
                        onEnd()
                    },
                )
            } else {
                androidx.compose.material3.Icon(
                    HugeIcons.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                    modifier = Modifier.size(16.dp),
                )
            }
        },
    )
}

/**
 * 胖乖「已结束开水」完成态（2026-10-29，停留 8 秒 / 可点 2026-10-31）：流程成功、落盘会话
 * 清掉后停留。副行 = 设备 · 本次出水时长（会话起点在 [session] 里，取的是消失前最后一份）。
 * 点整行进开水页。
 */
@Composable
private fun WaterDoneRow(session: QiekjWateringSession?, onOpen: () -> Unit) {
    // 时长在完成态首次组合时定住：8 秒停留里父级偶发重组不该让「用时」继续涨
    val duration = remember(session) {
        session?.let {
            QzxyWateringFormat.duration(
                (System.currentTimeMillis() - it.startedAtMillis).coerceAtLeast(0L),
            )
        }
    }
    ActiveRow(
        brandRes = edu.jxslu.schedule.R.drawable.ic_brand_pangguai,
        title = "胖乖生活",
        status = "已结束开水",
        pulsing = false,
        statusColor = MaterialTheme.semanticColors.success,
        onClick = onOpen,
        onClickLabel = "查看开水详情",
        subtitle = session?.let { s ->
            buildString {
                append(s.deviceName.ifBlank { "开水设备" })
                append(" · 用时 ")
                append(duration)
            }
        } ?: "本次出水已结束",
        trailing = { DoneCheck() },
    )
}

/**
 * 趣智用水行：用水中（计时 + 结束用水）/ 结束中（步骤文案 + 禁用的结束钮）/ 待核对
 * （超 1h 或对账确认设备结束）三形态，判据与 `WateringStatusBlock` 同源。
 *
 * 结束中的反馈（2026-10-29）：结束走蓝牙（停阀 → 轮询设备结算 → 上报），十几秒起步，
 * 旧版点了之后这一行纹丝不动、按钮还能连点。现在标题切「正在结束…」，副行直接展示
 * 流程步骤（[step]，来自 `QzxyFlowState.Working.step`），动作钮换成禁用的「结束中…」。
 * 「待核对」仍给「结算核对」按钮（要去设备旁）。
 */
@Composable
private fun QzxyActiveRow(
    watering: QzxyWatering,
    deviceEnded: Boolean,
    stopping: Boolean,
    step: String?,
    onStop: () -> Unit,
    onOpen: () -> Unit,
) {
    val haptics = rememberAppHaptics()
    val pending = deviceEnded || isQzxyWateringExpired(watering.startedAtMillis)
    // 只在「用水中」走秒：转「待核对」后停表，别让一行静态文案继续每秒重组。
    // key 带上 [pending]：状态翻面时重启走表协程，旧闭包里的 `!pending` 不会继续跑。
    val now by rememberResumedSecondWhile(watering.startedAtMillis to pending) { !pending }
    ActiveRow(
        brandRes = edu.jxslu.schedule.R.drawable.ic_brand_qzxy,
        title = when {
            stopping -> "趣智校园 · 正在结束…"
            pending -> "趣智校园 · 待核对"
            else -> "趣智校园 · 用水中"
        },
        // 待核对 = 用水已结束：圆点不呼吸（呼吸只属于「正在进行中」）；结束中照旧呼吸
        pulsing = !pending,
        onClick = onOpen,
        onClickLabel = "查看用水详情",
        subtitle = when {
            stopping -> step?.takeIf { it.isNotBlank() } ?: "正在结束用水…"
            pending -> "开阀于 ${QzxyWateringFormat.clockText(watering.startedAtMillis)} · 预扣 " +
                QzxyWateringFormat.money(watering.preDeductMilli)
            else -> "${watering.deviceName.ifBlank { "热水器" }} · " +
                rememberQzxyWateringClock(watering.startedAtMillis)
        },
        // 用水时长按 1 小时记账窗口推（超过即转「待核对」，不再画进行中的条）
        progress = if (pending) {
            null
        } else {
            (watering.elapsedMillis(now).toFloat() / QzxyWatering.EXPIRE_MILLIS)
                .coerceIn(0f, 1f)
        },
        trailing = {
            if (stopping) {
                ActionChip(label = "结束中…", enabled = false, busy = true, onClick = {})
            } else {
                ActionChip(label = if (pending) "结算核对" else "结束用水", onClick = {
                    haptics.tap()
                    onStop()
                })
            }
        },
    )
}

/**
 * 趣智「已结束用水」完成态（2026-10-29，停留 8 秒 / 可点 2026-10-31）：结算成功、本地记账
 * 清掉后停留。副行 = 设备 · 本次用时；金额与账单在结算提示（Snackbar）和趣智页消费记录里。
 * 点整行进趣智页。
 */
@Composable
private fun QzxyDoneRow(watering: QzxyWatering?, onOpen: () -> Unit) {
    // 同上：完成态停留期间把用时定住，别让重组把「用时」继续往上加
    val duration = remember(watering) {
        watering?.let {
            QzxyWateringFormat.duration(
                (System.currentTimeMillis() - it.startedAtMillis).coerceAtLeast(0L),
            )
        }
    }
    ActiveRow(
        brandRes = edu.jxslu.schedule.R.drawable.ic_brand_qzxy,
        title = "趣智校园",
        status = "已结束用水",
        pulsing = false,
        statusColor = MaterialTheme.semanticColors.success,
        onClick = onOpen,
        onClickLabel = "查看用水详情",
        subtitle = watering?.let { w ->
            buildString {
                append(w.deviceName.ifBlank { "热水器" })
                append(" · 用时 ")
                append(duration)
            }
        } ?: "本次用水已结束",
        trailing = { DoneCheck() },
    )
}

/** U净 洗烘行：纯展示（写操作只在 U净 主页），点整行进 U净 页。 */
@Composable
private fun UjingActiveRow(order: UjingOrderSnapshot, onOpen: () -> Unit) {
    val running = order.status == "40"
    // 只在洗涤中走秒；key 带 [running]，订单转完成态时重启协程直接停表
    val now by rememberResumedSecondWhile(order.snapshotAt to running) { running }
    val liveRemain = if (running) {
        UjingState.liveRemainSeconds(order.status, order.remainSeconds, order.snapshotAt, now)
    } else {
        0
    }
    ActiveRow(
        brandRes = edu.jxslu.schedule.R.drawable.ic_brand_ujing,
        title = "U净 · " + UjingState.statusLabel(order.status, order.statusRemark),
        subtitle = buildString {
            append(order.deviceName.ifBlank { UjingState.deviceFallback(order.dryer) })
            if (running && order.remainSeconds > 0) {
                append(" · 剩 ").append(UjingState.remainText(liveRemain))
            }
        },
        // 洗涤进度（与 U净 页英雄卡同口径：1 - 剩余/总时长；旧快照无时长则不画）
        progress = if (running && order.durationSeconds > 0) {
            (1f - liveRemain.toFloat() / order.durationSeconds).coerceIn(0f, 1f)
        } else {
            null
        },
        onClick = onOpen,
        onClickLabel = "查看洗衣订单",
        trailing = {
            androidx.compose.material3.Icon(
                HugeIcons.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                modifier = Modifier.size(16.dp),
            )
        },
    )
}

/**
 * U净 取衣提醒行（2026-10-29 用户要求；2026-11-05 用户反馈「整行置灰像已经取走了」重做）：
 * 订单已完成（50）后不再随行退场，一直留到用户点行尾「我已取走」（或去 U净 页点「知道了」），
 * 避免洗完忘了拿。
 *
 * **不置灰**：置灰的完成态与「已取走」并列，读起来像一件已经收摊的事——而这行恰恰是
 * 还没取走的待办。现在给正常文字权重 + `warning` 色静态点与「待取衣」状态词（已结束、
 * 等用户处理），行尾用实心主色「我已取走」把动作说清楚；圆点仍不呼吸——呼吸只属于进行中。
 * 文案区分烘干机。行本体仍进 U净 页，收提醒只走行尾动作。
 */
@Composable
private fun UjingDoneRow(
    order: UjingOrderSnapshot,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
) {
    val haptics = rememberAppHaptics()
    ActiveRow(
        brandRes = edu.jxslu.schedule.R.drawable.ic_brand_ujing,
        title = "U净 · " + UjingState.washDoneTitle(order.dryer),
        status = "待取衣",
        statusColor = MaterialTheme.semanticColors.warning,
        pulsing = false,
        dotColor = MaterialTheme.semanticColors.warning,
        // 提醒语放最前：设备名较长时单行省略会先吃掉尾巴，别把「记得去拿」截掉
        subtitle = "记得去拿衣物 · 「" +
            order.deviceName.ifBlank { UjingState.deviceFallback(order.dryer) } + "」",
        onClick = onOpen,
        onClickLabel = "查看洗衣订单",
        trailing = {
            ActionChip(
                label = "我已取走",
                onClick = {
                    haptics.tap()
                    onDismiss()
                },
            )
        },
    )
}

/**
 * 行尾的紧凑动作钮（32dp 高，与 `PowerRoomPickerSheet` 的行内小钮同规格）。
 *
 * [busy] = 动作正在跑：文字前加一枚 12dp 转圈（2026-10-29）。按钮同时置灰，
 * 「点上了、在等结果」和「按钮点不动」才分得开；转圈取 `LocalContentColor`，
 * 跟着禁用态一起变淡，不另调一套颜色。
 */
@Composable
private fun ActionChip(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    filled: Boolean = true,
    busy: Boolean = false,
) {
    val shape = CircleShape
    val content: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    strokeWidth = 1.5.dp,
                    color = LocalContentColor.current,
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
    if (filled) {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.height(32.dp),
            shape = shape,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 0.dp),
            content = { content() },
        )
    } else {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.height(32.dp),
            shape = shape,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 0.dp),
            content = { content() },
        )
    }
}

// ── 秒级走时：公共件 `rememberResumedNow` 管「无条件走表」，本文件的
//    `rememberResumedSecondWhile` 管「带终止条件的走表」，两者都受 RESUMED 闸门约束 ──

/** 骑行时长本地走时：与 `KvcxRideController.rideElapsedSeconds` 同一口径（不跨包引 internal）。 */
private fun rideElapsedSecondsShared(ride: KqcxAuth.Ride, fetchedAt: Long, now: Long): Long? {
    val base = ride.totalDateSeconds ?: return null
    if (fetchedAt <= 0L) return base
    return base + (now - fetchedAt).coerceAtLeast(0L) / 1_000L
}
