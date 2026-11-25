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
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import edu.jxslu.schedule.data.qiekj.QiekjWateringSession
import edu.jxslu.schedule.data.ujing.UjingOrderSnapshot
import edu.jxslu.schedule.domain.AppClock
import edu.jxslu.schedule.domain.EbikeFreeRide
import edu.jxslu.schedule.domain.QzxyWatering
import edu.jxslu.schedule.domain.QzxyWateringFormat
import edu.jxslu.schedule.domain.UjingState
import edu.jxslu.schedule.ui.common.AppCard
import edu.jxslu.schedule.ui.common.AppCardDivider
import edu.jxslu.schedule.ui.common.BrandMark
import edu.jxslu.schedule.ui.common.BreathingDot
import edu.jxslu.schedule.ui.common.rememberAppHaptics
import edu.jxslu.schedule.ui.common.rememberResumedNowWhile
import edu.jxslu.schedule.ui.icon.AppIcons
import edu.jxslu.schedule.ui.qzxy.QzxySettlement
import edu.jxslu.schedule.ui.qzxy.isQzxyWateringExpired
import edu.jxslu.schedule.ui.qzxy.rememberQzxyWateringClock
import edu.jxslu.schedule.ui.theme.Motion
import edu.jxslu.schedule.ui.theme.semanticColors
import kotlinx.coroutines.delay

/**
 * 今日页「进行中服务」状态卡（2026-10-21）：快趣骑行 / 胖乖开水 / 趣智洗澡 / U净 洗烘
 * 四项服务谁在进行中谁占一行，一张卡平铺（`AppCardDivider` 分隔），全无则整卡不渲染。
 *
 * ## iOS 移植 V2（2026-11-25）：从 `:app` 搬进 `shared/commonMain`
 *
 * 卡片外壳、四项服务的排布、分隔线、完成态停留、卡边高亮这套状态机与平台无关，全部留在
 * 这里；**依赖平台能力的三样做成可空槽位**（`design/ios-port.md` §11.2 ④：`null` = 本平台
 * 确实没有这块能力，对应内容整块不渲染）：
 *
 * | 槽位 | 形状 | null 的含义 | 谁填 |
 * |---|---|---|---|
 * | 快趣骑行 | [ActiveServiceSlot] | 没有快趣骑行（iOS 无高德 SDK） | Android `ui/today/TodayHost.kt` |
 * | 趣智开水 | [QzxyWaterSlot] | 没有趣智开水（iOS 无蓝牙传输层） | Android `ui/today/TodayHost.kt` |
 * | 品牌位图 | [ServiceBrands] | 没有 `ic_brand_*` 素材（iOS） | 两端宿主 |
 *
 * 为什么快趣是「内容槽位」而趣智是「数据槽位」：快趣行的数据（`KvcxRideState` /
 * `KvcxAction` / 还车摘要）在 `:app` 的 `ui/ebike`，共享层看不见那些类型，只能把**整行内容**
 * 交给宿主渲染；趣智行的数据（`QzxyWatering` / `QzxySettlement`）早已在共享层，缺的只是
 * 蓝牙那条能力，所以只把「有没有这项能力 + 当前状态」收成一个可空对象，行本体仍在这里。
 *
 * 开水（胖乖）与 U净 走的是 HTTP，两端都有，因此不是槽位——iOS 传 [QiekjWateringSession]
 * 与 [UjingOrderSnapshot] 即可显示同一张卡。
 *
 * ## 数据源（各自已有的真相源，本卡零新增网络路径）
 *
 * - 快趣：`KvcxRideController.state`（Graph 单例，进页/下拉各静默查一次，不轮询）；
 * - 胖乖：`QiekjWateringSessions.watering`（落盘会话，跨窗口唯一真相源）；
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
fun ActiveServiceCard(
    /**
     * 快趣骑行槽位：`null` = 本平台没有快趣骑行能力 → 该行（含完成态）整行不渲染。
     * 小程序方式的免费计时行不在此槽（它由 [freeRideStartAt] 驱动，数据在共享层）。
     */
    kvcx: ActiveServiceSlot? = null,
    /**
     * 微信小程序方式的免费时长计时起点（`ebikeRideStartAt`，DataStore）：
     * `isActive` 为 true = 小程序方式正在骑行（扫码起计时），展示倒计时行——
     * 小程序方式查不了在案订单（无账号 token），但计时是本机动作，状态照样能摆。
     */
    freeRideStartAt: Long? = null,
    /** 品牌位图槽位（见 [ServiceBrands]）。默认全 null = 本平台没有这套素材。 */
    brands: ServiceBrands = ServiceBrands(),
    /**
     * 胖乖「出水中」的落盘会话（`QiekjWateringSessions.watering`，2026-10-21）：跨窗口同步的真相源。
     * null = 没有进行中的开水；过期残留在 store 读取时已清。
     */
    waterSession: QiekjWateringSession? = null,
    /** 本窗口的开水流程是否正在「结束中」（置灰按钮 + 文案切换）。 */
    waterStopping: Boolean = false,
    /** 本窗口能否直接结束（流程在本窗口的 ViewModel 里跑；否则只能点行进页）。 */
    waterCanStop: Boolean = false,
    /**
     * 趣智开水槽位：`null` = 本平台没有趣智开水能力 → 该行（含完成态）整行不渲染。
     */
    qzxy: QzxyWaterSlot? = null,
    ujingOrder: UjingOrderSnapshot? = null,
    waterOnEnd: () -> Unit = {},
    /** 快趣行整行落点（骑行页：地图 / 完整骑行卡；行内按钮管动作，行本体管「去看详情」）。 */
    onOpenEbike: () -> Unit = {},
    /** 胖乖行整行落点（开水页：完整流程态 / 订单结果卡）。 */
    onOpenWater: () -> Unit = {},
    /** 趣智行整行落点（趣智页：用水状态卡 / 消费记录）。 */
    onOpenQzxy: () -> Unit = {},
    /** U净 行整行落点（U净 页：订单英雄卡 / 扫码）。 */
    onOpenUjing: () -> Unit = {},
    /**
     * U净 取衣提醒行尾「我已取走」：清掉已完成订单快照（并撤完成闹钟，调用侧做）。
     * 只有这个动作（或 U净 页的「知道了」）能收掉提醒，不设时间上限。
     */
    onUjingDismiss: () -> Unit = {},
    /** 小程序免费计时行的「结束计时」意图（确认弹窗在调用侧）。 */
    onEndFreeRide: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val kvcxActive = kvcx?.active == true
    val kvcxDoneVisible = kvcx?.done == true
    // 骑行中 = 明确未锁车（2026-10-22）：高亮**外层卡片边缘**（`AppCard.highlighted`），
    // 行内不再套描边 / 竖条——卡内画高亮只会变成气泡套气泡。临时锁车 / 锁状态未知不点亮。
    // 锁态判据由宿主给（`locked == false` 在 `:app` 的骑行状态里）。
    val kvcxRiding = kvcxActive && kvcx?.highlighted == true
    val qzxyVisible = qzxy?.watering != null
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
        EbikeFreeRide.isActive(freeRideStartAt, AppClock.nowMillis())
    // 胖乖：超过服务端自动关阀时刻（165 秒）即不再显示「出水中」（2026-11-06）。
    // 流程正常时 Success / 超时收尾会先清 store；残留会话（流程不在本窗口 / 页面被关 /
    // 协程被取消）只有 5 分钟过期兜底，水停了卡片还挂着——展示口径必须跟着关阀时刻走。
    val waterVisible = waterSession != null && !waterExpired.value &&
        !waterSession.isPastAutoClose(AppClock.nowMillis())
    // 完成态要交代「刚结束的是哪台设备、用了多久」：数据消失后原对象就没了，留住最后一份
    val lastQzxyWatering = rememberLastValue(qzxy?.watering)
    val lastWaterSession = rememberLastValue(waterSession)
    // 进行中 ⇄ 完成态交叉淡入时，退场那份读到的订单已经是 null——留住最后一份
    // 非空值，旧内容才不会在淡出途中变成空白（与结算卡同一手法）。
    val lastUjingLive = rememberLastValue(ujingLive)
    val lastUjingDone = rememberLastValue(ujingDone)
    // 结束成功的「已完成」停留（2026-10-29）：动作一成功行数据就没了，旧版那一帧直接退场，
    // 用户既看不到「结束中」也看不到结果。现在先亮 8 秒完成态，再走常规退场。
    // 快趣那份在宿主算（它还决定卡片要不要留在原位，见 `TodayServiceSlot.pinned`）。
    val qzxyDone = rememberDoneLinger(qzxyVisible, qzxy?.stopping == true)
    val waterDone = rememberDoneLinger(waterVisible, waterStopping)
    // 完成态与进行中行共用一条可见性：内容就地切换，卡体不会同时装两行
    val kvcxRow = kvcxActive || kvcxDoneVisible
    val qzxyRow = qzxyVisible || qzxyDone
    val waterRow = waterVisible || waterDone
    val anyActive = kvcxRow || freeRideVisible || waterRow || qzxyRow || ujingRow
    // 退场沿用最后显示的形态（见 rememberLastRowMode）：自然到期 / 后台还车不在收拢动画里
    // 闪成完成态，完成态停留结束也不闪回进行中
    val kvcxLastMode = rememberLastRowMode(active = kvcxActive, done = kvcxDoneVisible)
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
                    targetState = kvcxActive,
                    transitionSpec = { rowSwapSpec() },
                    // 旧内容按顶对齐淡出：文字左缘与首行基线都不动，只有卡片下沿收上去
                    contentAlignment = Alignment.TopStart,
                    label = "kvcxActiveToDone",
                ) { active ->
                    // 退场（active / done 都假）沿用最后显示的形态：自然到期 / 后台自动还车
                    // 不在收拢动画里闪成「已还车」，完成态停留结束也不闪回「骑行中」
                    val showDone = !active && (kvcxDoneVisible || kvcxLastMode.active == false)
                    val slot = kvcx
                    if (slot != null) {
                        // 退场那一份要读的是「最后一份非空数据」，由宿主自己留住
                        // （`rememberLastValue` 在共享层，宿主两行就能用上）
                        if (showDone) slot.doneContent() else slot.activeContent()
                    }
                }
            }
            // 免费时长行（小程序方式）：骑行行不出现时才轮到它（账号方式的骑行信息更全，
            // 两者不会同时成立——能力闸互斥；防御性写法不叠分隔线即可）
            val afterKvcx = kvcxRow || freeRideVisible
            if (freeRideStartAt != null) {
                FreeRideSlot(
                    brand = brands.kqcx,
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
                brand = brands.pangguai,
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
                    val shownWatering = qzxy?.watering ?: lastQzxyWatering
                    if (!showDone && shownWatering != null) {
                        QzxyActiveRow(
                            brand = brands.qzxy,
                            watering = shownWatering,
                            deviceEnded = qzxy?.deviceEnded == true,
                            stopping = qzxy?.stopping == true,
                            step = qzxy?.stopStep,
                            onStop = { qzxy?.onStop?.invoke() },
                            onOpen = onOpenQzxy,
                        )
                    } else if (showDone) {
                        QzxyDoneRow(
                            brand = brands.qzxy,
                            watering = lastQzxyWatering,
                            settlement = qzxy?.settlement,
                            onOpen = onOpenQzxy,
                        )
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
                        UjingActiveRow(
                            brand = brands.ujing,
                            order = shownLive,
                            onOpen = onOpenUjing,
                        )
                    } else if (showDone) {
                        (ujingDone ?: lastUjingDone)?.let {
                            UjingDoneRow(
                                brand = brands.ujing,
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
 * 品牌位图槽位（`ic_brand_*`，`ui/common/BrandMark` 那条缝）。
 *
 * 四个字段都是**资源 id**（Android 的 `R.drawable.ic_brand_*`），`null` = 本平台没有那张素材：
 * iOS 侧没有这套位图（它们住在 `app/src/main/res`），传 `null` 之后行照旧渲染，只是不画图标位
 * ——这与「平台动作可空回调」同一语义，不画一个假图标占位。
 *
 * 为什么不用 `Painter?`：`BrandMark` 的现有实现就是「按 id 取位图」，它的四个调用页还在
 * `:app`，换成 `Painter?` 要连带改那边的签名；等 iOS 补一套品牌素材（或换成矢量）时，
 * 这条缝整体作废，届时连 `BrandMark` 一起换。
 */
class ServiceBrands(
    /** 快趣（小程序免费计时行用；账号方式的骑行行在宿主侧，自带品牌位图）。 */
    val kqcx: Int? = null,
    /** 胖乖生活。 */
    val pangguai: Int? = null,
    /** 趣智校园。 */
    val qzxy: Int? = null,
    /** U净 洗衣。 */
    val ujing: Int? = null,
)

/**
 * 平台自带的进行中服务行（快趣骑行）：`null` = 本平台没有这项能力 → 该行整行不渲染。
 *
 * 为什么把**内容**交出来而不是数据：快趣行的数据（`KvcxRideState` / `KvcxAction` /
 * `KvcxReturnSummary`）在 `:app` 的 `ui/ebike`，共享层看不见那些类型。行内按钮文案
 * （「开锁中…」）与冷却秒数都由 `KvcxAction` 决定，硬搬到共享层等于再造一份平行模型。
 *
 * 宿主必须自己留住「最后一份非空数据」（`rememberLastValue`，共享层已提供）：
 * 退场那 160ms 里 `active` 已经是 false，内容仍会被画一次，读到 null 就是淡出途中变空白。
 *
 * @property active 有在案服务（快趣 = `state.ride != null`）。
 * @property done 完成态是否在亮（宿主按 `rememberDoneLinger` 算，它同时决定卡片位置）。
 * @property highlighted 是否点亮**外层卡片边缘**（快趣 = 明确未锁车）。
 * @property activeContent 进行中形态（退场期间也走它，读宿主留住的那份数据）。
 * @property doneContent 完成态形态（「已还车」+ 结果摘要）。
 */
class ActiveServiceSlot(
    val active: Boolean,
    val done: Boolean = false,
    val highlighted: Boolean = false,
    val activeContent: @Composable () -> Unit,
    val doneContent: @Composable () -> Unit = {},
)

/**
 * 趣智开水槽位：`null` = 本平台没有趣智开水能力（蓝牙开阀）→ 该行整行不渲染。
 *
 * 与 [ActiveServiceSlot] 的差别：趣智行的数据（`QzxyWatering` / `QzxySettlement`）**早已在
 * 共享层**，缺的只是蓝牙那条能力，所以行本体留在本文件，这里只收「有没有 + 当前状态」。
 * 拆成内容槽位反而要把行本体抄到 `:app` 一份，两条流的判据必然漂移。
 *
 * 槽位在**能力存在时就要给**（`watering` 可为 null）：完成态那一行（「已结束用水」+ 实扣金额）
 * 要在 [watering] 消失之后再亮 8 秒，槽位跟着会话一起变 null 就没有载体了。
 *
 * @property watering 进行中的用水记录（`QzxyUiState.watering`）；null = 当前没在用水。
 * @property deviceEnded 对账已确认设备那边结束（转「待核对」，不点亮）。
 * @property stopping 「结束用水」流程正在跑（本窗口 ViewModel 的 `flow is Working`）。
 * @property stopStep 结束中的当前步骤文案（`QzxyFlowState.Working.step`）；null = 还没进到第一步。
 * @property settlement 最近一次结算（完成态报「实扣多少」）；记账起点对不上时不用它。
 * @property onStop 「结束用水 / 结算核对」意图；调用侧负责确认弹窗与蓝牙流程。
 */
class QzxyWaterSlot(
    val watering: QzxyWatering? = null,
    val deviceEnded: Boolean = false,
    val stopping: Boolean = false,
    val stopStep: String? = null,
    val settlement: QzxySettlement? = null,
    val onStop: () -> Unit = {},
)

/**
 * 免费计时行：**行内自己走秒**（每拍只重组这一行），到点回调 [onExpired] 让父级收行。
 * 父级因此不再持有秒级状态——开水 / 免费计时期间快趣行与按钮不跟着每秒重组。
 */
@Composable
private fun FreeRideSlot(
    brand: Int?,
    startAtMillis: Long,
    onOpen: () -> Unit,
    onEndTimer: () -> Unit,
    onExpired: () -> Unit,
) {
    val now by rememberResumedNowWhile(1_000L, startAtMillis) {
        EbikeFreeRide.isActive(startAtMillis, it)
    }
    val active = EbikeFreeRide.isActive(startAtMillis, now)
    LaunchedEffect(active) { if (!active) onExpired() }
    AnimatedVisibility(visible = active, enter = EnterRow, exit = ExitRow) {
        FreeRideRow(
            brand = brand,
            startAtMillis = startAtMillis,
            now = now,
            onOpen = onOpen,
            onEndTimer = onEndTimer,
        )
    }
}

/**
 * 开水行：同上，秒级走时留在本行；超过服务端自动关阀时刻（165 秒）回调 [onExpired] 撤行
 * （2026-11-06：此前按 5 分钟过期撤，水停了卡片还挂着「出水中」）。
 *
 * [session] 可为 null：会话成功后落盘 store 已清空，但完成态（[done]）还要在这条 Slot 里
 * 再亮 8 秒——Slot 若跟着会话一起卸载，退场就没有载体了（2026-10-29）。
 */
@Composable
private fun WaterSlot(
    brand: Int?,
    session: QiekjWateringSession?,
    done: Boolean,
    lastSession: QiekjWateringSession?,
    stopping: Boolean,
    canStop: Boolean,
    onEnd: () -> Unit,
    onOpen: () -> Unit,
    onExpired: () -> Unit,
) {
    val now by rememberResumedNowWhile(1_000L, session?.startedAtMillis) { at ->
        session != null && !session.isPastAutoClose(at)
    }
    val active = session != null && !session.isPastAutoClose(now)
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
                    brand = brand,
                    session = shownSession,
                    now = now,
                    stopping = stopping,
                    canStop = canStop,
                    onEnd = onEnd,
                    onOpen = onOpen,
                )
            } else if (showDone) {
                WaterDoneRow(brand = brand, session = lastSession, onOpen = onOpen)
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
 *
 * `public`：宿主（Android `ui/today/TodayHost.kt`）算卡片位置时要用同一个余量。
 */
const val ServiceCardExitGraceMillis = 200L

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
 *
 * `public`：快趣那份要在宿主（列表层）算——它还决定卡片要不要留在课程区之前。
 */
class DoneLingerGate(visible: Boolean = false) {
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
 * `public` 而不是私有：快趣那份要在宿主（列表层）算——它还决定卡片要不要
 * 留在课程区之前（见 [ActiveServiceSlot.done] 与 `TodayServiceSlot.pinned`）。
 */
@Composable
fun rememberDoneLinger(visible: Boolean, ending: Boolean): Boolean {
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

/**
 * 记住最后一个非空值：行数据消失后，完成态还要用它交代「刚结束的是哪台设备」。
 *
 * `public`：宿主渲染快趣行时同样要留住最后一份 `ride` / 还车摘要（退场那 160ms 仍在画）。
 */
@Composable
fun <T : Any> rememberLastValue(value: T?): T? {
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

/**
 * 完成态行尾的成功色勾（静态；呼吸点已停，勾负责把「结束了」说清）。
 *
 * `public`：宿主的快趣「已还车」行要用同一枚勾。
 */
@Composable
fun DoneCheck() {
    Icon(
        AppIcons.CheckmarkCircle02,
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
    (fadeIn(Motion.state()) togetherWith fadeOut(Motion.exit())).using(
        SizeTransform(clip = true) { _, _ ->
            // 高度补间与淡入淡出同档（state 180）：两态差 11dp，比内容淡出更慢会看到「文字
            // 已经换完、卡片下沿还在缩」
            Motion.state<IntSize>()
        },
    )

/**
 * 单行骨架：呼吸点 + 品牌图标 + 两行文本（标题 + 副行）+ 右侧动作区。
 * 计时走秒由各自的副行文案负责；文本统一 `tnum` 等宽防抖。
 *
 * [brand] = 品牌位图资源 id（见 [ServiceBrands]）；`null` = 本平台没有那张素材，
 * 图标位与它后面那 10dp 间距一起不渲染（行文字照旧，不画假图标占位）。
 *
 * [pulsing] = 圆点是否呼吸（默认是）：「待核对」这类**已结束待处理**的行不呼吸——
 * 呼吸的含义是「正在进行中」，结束时呼吸反而是误导。
 *
 * 点击反馈（2026-10-22 用户报「矩形的丑陋灰色背景边框」）：不再用默认灰色涟漪——
 * 圆角裁切 + 按下时浅主色底、0.985 轻微回缩（与侧栏行 / 扫一扫同族），松手回弹。
 * 行的「骑行中」高亮**不在这里**：它归外层卡片边缘（[ActiveServiceCard] 的
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
 *
 * `public`：宿主（Android）渲染快趣行时用的是同一副骨架——行高、按下手感、进度条口径
 * 必须与其它三行一模一样，各写一份必然漂移。
 */
@Composable
fun ActiveRow(
    brand: Int?,
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
            if (brand != null) {
                BrandMark(brand, 24.dp)
                Spacer(Modifier.width(10.dp))
            }
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
    brand: Int?,
    startAtMillis: Long,
    now: Long,
    onOpen: () -> Unit,
    onEndTimer: () -> Unit,
) {
    val haptics = rememberAppHaptics()
    ActiveRow(
        brand = brand,
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
 * 若流程在开水页（另一个窗口的 ViewModel）里跑，这里只展示与跳转——跨窗口结束
 * 需要那边的 ViewModel，硬做只会发一条注定落空的结束请求。
 */
@Composable
private fun WaterActiveRow(
    brand: Int?,
    session: QiekjWateringSession,
    now: Long,
    stopping: Boolean,
    canStop: Boolean,
    onEnd: () -> Unit,
    onOpen: () -> Unit,
) {
    val haptics = rememberAppHaptics()
    ActiveRow(
        brand = brand,
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
                Icon(
                    AppIcons.ChevronRight,
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
private fun WaterDoneRow(brand: Int?, session: QiekjWateringSession?, onOpen: () -> Unit) {
    // 时长在完成态首次组合时定住：8 秒停留里父级偶发重组不该让「用时」继续涨
    val duration = remember(session) {
        session?.let {
            QzxyWateringFormat.duration(
                (AppClock.nowMillis() - it.startedAtMillis).coerceAtLeast(0L),
            )
        }
    }
    ActiveRow(
        brand = brand,
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
    brand: Int?,
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
    val now by rememberResumedNowWhile(1_000L, watering.startedAtMillis to pending) { !pending }
    ActiveRow(
        brand = brand,
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
 * 清掉后停留。副行 = 实扣金额 · 设备 · 本次用时（2026-11-06 加金额：用户关水后要能当场
 * 看出花了多少，别让人再翻消费记录）。金额排最前——单行省略号从右往左吃，服务端设备名
 * 可以长到「热水器-学生公寓-1号楼-3层-301」，金额放末尾必然被截。金额缺失就整段不写
 * （`QzxyWateringFormat.doneSubtitle`）。点整行进趣智页。
 */
@Composable
private fun QzxyDoneRow(
    brand: Int?,
    watering: QzxyWatering?,
    settlement: QzxySettlement?,
    onOpen: () -> Unit,
) {
    // 同上：完成态停留期间把用时定住，别让重组把「用时」继续往上加
    val duration = remember(watering) {
        watering?.let {
            QzxyWateringFormat.duration(
                (AppClock.nowMillis() - it.startedAtMillis).coerceAtLeast(0L),
            )
        }
    }
    // 金额只认「这一笔」的结算：`lastSettlement` 不自动清，上一次用水的金额会留在状态里
    // （比如这次是从趣智页结束的——今日页这份 ViewModel 没跑结算）。按记账起点对齐，
    // 对不上的宁缺勿错，别把别人的金额贴到这一行。
    val amountMilli = settlement
        ?.takeIf { watering != null && it.startedAtMillis == watering.startedAtMillis }
        ?.consumeMoneyMilli
    ActiveRow(
        brand = brand,
        title = "趣智校园",
        status = "已结束用水",
        pulsing = false,
        statusColor = MaterialTheme.semanticColors.success,
        onClick = onOpen,
        onClickLabel = "查看用水详情",
        subtitle = watering?.let { w ->
            QzxyWateringFormat.doneSubtitle(
                deviceName = w.deviceName.ifBlank { "热水器" },
                durationText = duration,
                amountMilli = amountMilli,
            )
        } ?: "本次用水已结束",
        trailing = { DoneCheck() },
    )
}

/** U净 洗烘行：纯展示（写操作只在 U净 主页），点整行进 U净 页。 */
@Composable
private fun UjingActiveRow(brand: Int?, order: UjingOrderSnapshot, onOpen: () -> Unit) {
    val running = order.status == "40"
    // 只在洗涤中走秒；key 带 [running]，订单转完成态时重启协程直接停表
    val now by rememberResumedNowWhile(1_000L, order.snapshotAt to running) { running }
    val liveRemain = if (running) {
        UjingState.liveRemainSeconds(order.status, order.remainSeconds, order.snapshotAt, now)
    } else {
        0
    }
    ActiveRow(
        brand = brand,
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
            Icon(
                AppIcons.ChevronRight,
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
    brand: Int?,
    order: UjingOrderSnapshot,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
) {
    val haptics = rememberAppHaptics()
    ActiveRow(
        brand = brand,
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
 *
 * `public`：宿主的快趣行（`开锁 / 锁车 / 还车`）用的是同一枚钮。
 */
@Composable
fun ActionChip(
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
