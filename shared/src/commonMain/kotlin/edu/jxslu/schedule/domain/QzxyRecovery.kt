package edu.jxslu.schedule.domain

/**
 * 开阀失败后的自动修复阶梯（DESIGN §3.18 / §4.30，2026-11-06）。
 *
 * 为什么要有它：共享水表最常见的失败只有两类——「上一位用户没结算，设备停在
 * 状态 3」与「链路已经死了 / 被另一台手机占着」。两类都能靠有限几步自愈，但以前
 * 要用户自己找出口（重新绑定、点清除、再点开阀），顺序错了就以为「连不上」。
 *
 * 这里只放**决策与文案**（纯 JVM，单测钉住）；建链、清除、结算的执行在
 * `QzxyViewModel.retryOpenAfterFailure`，蓝牙时序仍归 `QzxyWaterFlow`。
 */
object QzxyRecovery {

    /** 自动重试最多跑几轮；跑完仍失败就把主按钮交给「手动清除设备记录」。 */
    const val MAX_ROUNDS = 2

    /** 整段自动重试的时间预算（毫秒）。到点收手，别让用户对着弹窗干等。 */
    const val BUDGET_MILLIS = 40_000L

    /** close() 后等系统释放连接再建链；立刻重连大多还是 133。 */
    const val RECONNECT_SETTLE_MILLIS = 800L

    /** 第二轮开头短扫确认设备还在附近。 */
    const val RESCAN_MILLIS = 3_000L

    /** 状态 6「结算中」与出水态轮询的间隔；一轮最多等 [PROBES_PER_ROUND] 拍。 */
    const val SETTLING_POLL_MILLIS = 1_500L

    /** 一轮里「读状态 → 处理」最多循环几次（等结算、清完再读都用得上）。 */
    const val PROBES_PER_ROUND = 4

    /** 一次开阀失败属于哪一类。决定文案、是否值得自动重试、要不要跑蓝牙阶梯。 */
    enum class Failure {
        /** 蓝牙命令没有回包 / 建链失败：链路死了，或设备被别的手机占着。 */
        LINK,

        /** 设备回包了，但状态不是空闲：记录压着、结算中、正在出水。 */
        DEVICE_BUSY,

        /** 下单 / 设备登记信息失败：HTTP 侧，重连一百次也没用。 */
        ORDER,

        /** 服务端已下单，`downData` 写不进设备。 */
        WRITE,

        /** 本地没有可重发的订单（只在「重发」入口出现，不进自动阶梯）。 */
        NO_PENDING,
    }

    /** 读到设备状态之后该做什么。 */
    enum class Action {
        /** 设备空闲：重发缓存的开阀数据（没有缓存才新下单）。 */
        OPEN,

        /** 是本地记账那笔记录：先结算，别把还能救的钱清掉。 */
        SETTLE_OURS,

        /** 不是自己的记录：清除后重新开阀（用户已在弹窗里确认过）。 */
        CLEAR_RECORD,

        /** 结算中 / 正在出水：等它自己走完，这时发清除会被拒。 */
        WAIT,

        /** 读不到状态：交给下一轮重连。 */
        LINK_RETRY,

        /** 当前状态不适合自动处理（远程控制模式、清除后仍是状态 3 等）。 */
        STOP,
    }

    /** 弹窗里「自动重试会依次做」的一步。 */
    enum class Step { RESCAN, RECONNECT, READ_STATE, WAIT, SETTLE_OURS, CLEAR_RECORD, OPEN }

    /**
     * 弹窗里「可能是这些原因」的一条。
     *
     * [warning] = 涉及别人订单/资金的那条，界面用 error 色强调（不是普通说明）。
     */
    data class Cause(val title: String, val hint: String, val warning: Boolean = false)

    /**
     * 读到设备状态后的动作。
     *
     * [state] 是设备状态码，null = 没读到；[recordIsOurs] = 本地用水记账指向这台设备
     * 且还没过期（过期记账不算自己的：设备上那条多半是上一位用户的）；
     * [clearedThisRound] / [settledThisRound] = 这一轮已经试过清除 / 结算。
     */
    fun nextAction(
        state: Int?,
        recordIsOurs: Boolean,
        clearedThisRound: Boolean,
        settledThisRound: Boolean,
    ): Action = when {
        state == null -> Action.LINK_RETRY
        state == QzxyProtocol.STATE_IDLE -> Action.OPEN
        state == QzxyProtocol.STATE_FINISHED_UNCOLLECTED -> when {
            // 清过一遍还是状态 3：设备收下了命令却没清干净，再清也是白试
            clearedThisRound -> Action.STOP
            recordIsOurs && !settledThisRound -> Action.SETTLE_OURS
            else -> Action.CLEAR_RECORD
        }
        // 结算中（6）与出水/刷卡的临时态（1/2）：等它自己走完
        state == QzxyProtocol.STATE_SETTLING ||
            state == QzxyProtocol.STATE_IN_ORDER ||
            state == QzxyProtocol.STATE_CARD_CONSUMING -> Action.WAIT
        else -> Action.STOP
    }

    /** 这类失败还值不值得自动重试。没有可重发的订单只能回卡片重新点开阀。 */
    fun retryable(failure: Failure): Boolean = failure != Failure.NO_PENDING

    /**
     * 这一次失败可能的原因（按可能性排，2–3 条）。
     *
     * 「上游平台接口变更」对每一类都列：客户端字段/签名/帧格式与服务端对不上时，
     * 现象与蓝牙故障一样，用户没法从界面上分辨，只能靠复制诊断去核。
     */
    fun causes(failure: Failure): List<Cause> = when (failure) {
        Failure.LINK -> listOf(
            Cause(
                title = "蓝牙没连上：设备可能被另一台手机占着",
                hint = "水表同一时间只认一个连接，被连着的时候也会从扫描结果里消失。" +
                    "让另一台手机先断开蓝牙、关掉官方趣智校园，再点重试。",
            ),
            Cause(
                title = "上一次的连接已经死了，系统还没回调",
                hint = "重试会先断开、等系统释放，再重新建链；这一步通常一次就好。",
            ),
            Cause(
                title = "可能是上游平台接口变更",
                hint = "接口或协议口径变了的话，本机也会表现为连不上、开不了。" +
                    "两轮都失败就去诊断页复制文本发出来。",
            ),
        )

        Failure.DEVICE_BUSY -> listOf(
            Cause(
                title = "设备上压着一条没结算的用水记录",
                hint = "多半是上一位用户没点「结束用水」。请先确认室友这次的用水已经结束，" +
                    "否则重试里的清除可能强制终止对方的订单。",
                warning = true,
            ),
            Cause(
                title = "设备刚停阀，还在结算中",
                hint = "停阀后设备要几秒才写完记录。重试会先等它走完，不会在这时清记录。",
            ),
            Cause(
                title = "可能是上游平台接口变更",
                hint = "清除之后仍然被挡、或状态字段对不上，都可能是接口口径变了；把诊断发出来。",
            ),
        )

        Failure.ORDER -> listOf(
            Cause(
                title = "可能是上游平台接口变更",
                hint = "下单接口的字段、签名或版本口径变了，本机会表现为「下单失败」。",
            ),
            Cause(
                title = "网络或登录态临时不通",
                hint = "重试会重新下单一次；仍失败就去趣智校园页看登录态与余额。",
            ),
        )

        Failure.WRITE -> listOf(
            Cause(
                title = "服务端已经下单，开阀数据没写进设备",
                hint = "重试只重发这一单，不会重复下单，也不会多一笔预扣。",
            ),
            Cause(
                title = "写到一半蓝牙断了",
                hint = "重试会重新建链再写一次。",
            ),
            Cause(
                title = "可能是上游平台接口变更",
                hint = "服务端换了开阀数据格式，设备就会拒收；把诊断文本发出来核对字节。",
            ),
        )

        Failure.NO_PENDING -> listOf(
            Cause(
                title = "这一单已经过期",
                hint = "重新点「开阀」会新建一单。",
            ),
        )
    }
}
