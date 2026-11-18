package edu.jxslu.schedule.domain

/**
 * 「猜你想用」的推荐目标（DESIGN §4.51）。
 *
 * [id] 是落盘用的稳定值——改中文名、改卡片文案都不影响历史习惯数据；
 * 新增服务时只能往后加，不能复用旧 id。
 */
enum class GuessTarget(val id: String) {
    /** 胖乖生活（开水 / 直饮水）。 */
    QIEKJ("qiekj"),

    /** 快趣出行（校园共享单车）。 */
    KVCX("kvcx"),

    /** 趣智校园（蓝牙热水）。 */
    QZXY("qzxy"),

    /** U净（校园洗衣）。 */
    UJING("ujing"),

    /** 问渠（志愿服务 / 劳动教育）。 */
    WENQU("wenqu"),
    ;

    companion object {
        /** 认不出的存储值一律丢弃（老版本写过的未知服务 / 手工改坏的数据）。 */
        fun fromId(id: String?): GuessTarget? = entries.firstOrNull { it.id == id }
    }
}

/** 推荐来源：决定卡片副文案，也决定「这个推荐是命中表还是命中习惯」。 */
enum class GuessSource {
    /** 距上课 ≤30 分钟。 */
    CLASS_START,

    /** 距下课 ≤20 分钟。 */
    CLASS_END,

    /** 周日中午固定问渠。 */
    SUNDAY_NOON,

    /** 平日时段表命中。 */
    SCHEDULE,

    /** 周末先验命中（胖乖 / 快趣 / U净）。 */
    WEEKEND,

    /** 周末无任何习惯证据时的默认项。 */
    WEEKEND_DEFAULT,

    /** 该功能的同槽订单已过证据门槛。 */
    HABIT,
}

/** 上课 / 下课信号（7 天通用，周末有课同样生效）。 */
data class ClassSignal(val kind: GuessSignalKind, val minutes: Int)

/** 上课信号的两种类型。 */
enum class GuessSignalKind {
    /** 还有 N 分钟上课。 */
    BEFORE_CLASS,

    /** 还有 N 分钟下课。 */
    AFTER_CLASS,
}

/**
 * 一次推荐结果。
 *
 * [minutes] 只在 [GuessSource.CLASS_START] / [GuessSource.CLASS_END] 时有值；
 * 它由 ViewModel 每个 tick 用课程状态里的实时分钟数覆盖，缓存里的旧值不直接上屏。
 */
data class GuessPick(
    val target: GuessTarget,
    val source: GuessSource,
    val minutes: Int? = null,
)

/**
 * 一条使用习惯事件：**一次真实订单**（不是打开页面）。
 *
 * 时间点取订单成立那一刻——胖乖确认出水、快趣建单、趣智开阀、U净下单受理、问渠报名成功。
 * [dayOfWeek] 是 1（周一）..7（周日），[slot] 是当天第几个 30 分钟（0..47）。
 */
data class GuessUsage(
    val atMillis: Long,
    val dayOfWeek: Int,
    val slot: Int,
    val target: GuessTarget,
)

/** 平日推荐时段（半开区间 `[startMinute, endMinute)`，分钟数 = 当天 0 点起算）。 */
data class GuessWindow(
    val startMinute: Int,
    val endMinute: Int,
    val target: GuessTarget,
)

/**
 * 「猜你想用」规则表与纯判定逻辑（DESIGN §4.51）。
 *
 * 三层结构，顺序不可换：
 * 1. **上课信号**：一票决定快趣（周末有课也算）；
 * 2. **周日中午**：固定问渠；
 * 3. **时段表 / 周末先验 + 习惯权重**：加权抽样，每 30 分钟槽一次。
 *
 * 习惯只认订单事件（[GuessUsage]），不认页面打开；卡片自身不会训练模型。
 * 所有常量都在本对象里，改口径只动这里，不动 UI / 数据层。
 */
object GuessRules {

    /** 学习与抽样的时间粒度（分钟）。所有推荐时段分界都在它的整数倍上。 */
    const val BUCKET_MINUTES = 30

    /** 一天多少个槽。 */
    const val SLOTS_PER_DAY = 24 * 60 / BUCKET_MINUTES

    /** 习惯事件保留天数；判定时也只统计这个窗口。 */
    const val HISTORY_DAYS = 60

    /** 习惯事件上限：订单型信号一天几条，400 条足够覆盖 60 天。 */
    const val MAX_USAGES = 400

    /** 同一功能两条事件的最小间隔：防「确认核对」等路径把一次订单记成多条。 */
    const val DEDUPE_MILLIS = 3 * 60 * 1000L

    /** 距上课多少分钟内推荐快趣。 */
    const val CLASS_START_WINDOW_MINUTES = 30

    /** 距下课多少分钟内推荐快趣。 */
    const val CLASS_END_WINDOW_MINUTES = 20

    /** 平日时段表命中的基础权重。 */
    const val BASE_WEIGHT = 10

    /** 同星期几、同槽的一次订单权重。 */
    const val CELL_WEIGHT = 4

    /** 同类其他日子、同槽的一次订单权重。 */
    const val BAND_WEIGHT = 1

    /** 进入权重的最低证据数（cell + band）。 */
    const val EVIDENCE_GATE = 3

    /** 单个功能的习惯权重上限：再频繁也留一部分概率给表 / 先验。 */
    const val HABIT_CAP = 40

    /** 周日中午起点（含）：11:00。 */
    const val SUNDAY_NOON_START_MINUTE = 11 * 60

    /** 周日中午终点（不含）：13:00。 */
    const val SUNDAY_NOON_END_MINUTE = 13 * 60

    /**
     * 平日时段表。**半开区间**、连续覆盖 06:00–23:00，空档不在表里
     * （08:30–11:00 / 14:00–16:30 / 23:00–06:00 即「默认不推荐」）。
     */
    val weekdayWindows: List<GuessWindow> = listOf(
        GuessWindow(6 * 60, 7 * 60 + 30, GuessTarget.QIEKJ),
        GuessWindow(7 * 60 + 30, 8 * 60 + 30, GuessTarget.KVCX),
        GuessWindow(11 * 60, 12 * 60 + 30, GuessTarget.KVCX),
        GuessWindow(12 * 60 + 30, 13 * 60, GuessTarget.QIEKJ),
        GuessWindow(13 * 60, 14 * 60, GuessTarget.KVCX),
        GuessWindow(16 * 60 + 30, 18 * 60, GuessTarget.KVCX),
        GuessWindow(18 * 60, 18 * 60 + 30, GuessTarget.QIEKJ),
        GuessWindow(18 * 60 + 30, 19 * 60 + 30, GuessTarget.KVCX),
        GuessWindow(19 * 60 + 30, 23 * 60, GuessTarget.QZXY),
    )

    /** 周末先验：没有习惯证据时按这个顺序兜底（胖乖 > 快趣 > U净）。 */
    val weekendPriors: Map<GuessTarget, Int> = linkedMapOf(
        GuessTarget.QIEKJ to 3,
        GuessTarget.KVCX to 2,
        GuessTarget.UJING to 1,
    )

    /** 当前时刻属于哪个 30 分钟槽。 */
    fun slotOf(minuteOfDay: Int): Int =
        (minuteOfDay.coerceIn(0, 24 * 60 - 1) / BUCKET_MINUTES)

    /** 平日时段表命中项；空档 / 表外返回 null。 */
    fun windowAt(minuteOfDay: Int): GuessWindow? =
        weekdayWindows.firstOrNull { minuteOfDay >= it.startMinute && minuteOfDay < it.endMinute }

    /**
     * 上课 / 下课信号。
     *
     * 先判「快下课」（更近、且骑车是下课后的动作），再判「快上课」。
     * 两者都只在 1..窗口 内成立——0 分钟（刚好到点）不推荐，避免在下课铃响后才弹「提前扫车」。
     */
    fun classSignal(
        hasOngoing: Boolean,
        minutesToOngoingEnd: Int?,
        hasNext: Boolean,
        minutesToNext: Int?,
    ): ClassSignal? {
        if (hasOngoing && minutesToOngoingEnd != null &&
            minutesToOngoingEnd in 1..CLASS_END_WINDOW_MINUTES
        ) {
            return ClassSignal(GuessSignalKind.AFTER_CLASS, minutesToOngoingEnd)
        }
        if (hasNext && minutesToNext != null &&
            minutesToNext in 1..CLASS_START_WINDOW_MINUTES
        ) {
            return ClassSignal(GuessSignalKind.BEFORE_CLASS, minutesToNext)
        }
        return null
    }

    /**
     * 习惯权重：证据不足门槛返回 null（不参与竞争），否则
     * `min(4×cell + band, 40)`。抽出来是为了单测能直接钉住权重表。
     */
    fun habitWeight(cell: Int, band: Int): Int? {
        if (cell + band < EVIDENCE_GATE) return null
        return minOf(CELL_WEIGHT * cell + BAND_WEIGHT * band, HABIT_CAP)
    }

    /**
     * 纯判定：给定时刻、课程信号、习惯事件与一个随机数，返回推荐；无候选返回 null。
     *
     * [roll] 由调用方在**每个槽只取一次**（稳定器负责），取值 `[0, 1)`。
     * 本函数不做缓存——同一输入 + 同一 roll 必须得到同一结果，单测才能钉概率。
     */
    fun decide(
        dayOfWeek: Int,
        minuteOfDay: Int,
        signal: ClassSignal?,
        usages: List<GuessUsage>,
        nowMillis: Long,
        roll: Double,
    ): GuessPick? {
        if (dayOfWeek !in 1..7 || minuteOfDay !in 0..(24 * 60 - 1)) return null

        // 1. 上课信号一票决定（周末有课同样命中，不看 dayOfWeek 是否 1..5）
        if (signal != null) {
            return GuessPick(
                target = GuessTarget.KVCX,
                source = if (signal.kind == GuessSignalKind.BEFORE_CLASS) {
                    GuessSource.CLASS_START
                } else {
                    GuessSource.CLASS_END
                },
                minutes = signal.minutes,
            )
        }

        // 2. 周日中午固定问渠（不参与概率；上课信号已在上面先返回）
        if (dayOfWeek == 7 &&
            minuteOfDay >= SUNDAY_NOON_START_MINUTE &&
            minuteOfDay < SUNDAY_NOON_END_MINUTE
        ) {
            return GuessPick(GuessTarget.WENQU, GuessSource.SUNDAY_NOON)
        }

        // 3. 基础权重：平日命中表给 10；周末无表，给先验
        val base = if (dayOfWeek <= 5) windowAt(minuteOfDay)?.target else null
        val weights = LinkedHashMap<GuessTarget, Int>()
        if (base != null) {
            weights[base] = BASE_WEIGHT
        } else if (dayOfWeek >= 6) {
            weekendPriors.forEach { (target, weight) -> weights[target] = weight }
        }

        // 4. 习惯：只统计近 60 天、同槽；cell + band 过门槛才参与
        val slot = slotOf(minuteOfDay)
        val cutoff = nowMillis - HISTORY_DAYS * 24L * 60L * 60L * 1000L
        val recent = usages.filter {
            it.atMillis >= cutoff && it.slot == slot && it.dayOfWeek in 1..7
        }
        val habitWeights = HashMap<GuessTarget, Int>()
        GuessTarget.entries.forEach { target ->
            val cell = recent.count { it.dayOfWeek == dayOfWeek && it.target == target }
            val band = recent.count {
                it.dayOfWeek != dayOfWeek &&
                    it.target == target &&
                    sameDayKind(it.dayOfWeek, dayOfWeek)
            }
            val weight = habitWeight(cell, band) ?: return@forEach
            habitWeights[target] = weight
            weights[target] = (weights[target] ?: 0) + weight
        }

        if (weights.isEmpty()) return null

        // 5. 周末完全没有习惯证据：固定胖乖（「默认优先胖乖 - 快趣 - U净」的第一名）
        if (dayOfWeek >= 6 && habitWeights.isEmpty()) {
            return GuessPick(GuessTarget.QIEKJ, GuessSource.WEEKEND_DEFAULT)
        }

        val target = weightedPick(weights, roll) ?: return null
        return GuessPick(
            target = target,
            source = when {
                habitWeights.containsKey(target) -> GuessSource.HABIT
                base == target -> GuessSource.SCHEDULE
                else -> GuessSource.WEEKEND
            },
        )
    }

    /** 星期 1–5 为工作日、6–7 为周末；band 只在同一类日子里累计。 */
    private fun sameDayKind(a: Int, b: Int): Boolean = (a <= 5) == (b <= 5)

    /**
     * 加权抽样。按 [GuessTarget] 枚举顺序累减，保证同一权重表 + 同一 roll 结果稳定。
     * 浮点误差兜底返回最后一个正权重项（不会因为累计误差返回 null）。
     */
    private fun weightedPick(weights: Map<GuessTarget, Int>, roll: Double): GuessTarget? {
        val total = weights.values.sum()
        if (total <= 0) return null
        val normalized = roll.coerceIn(0.0, 0.999999999)
        var remaining = normalized * total
        var last: GuessTarget? = null
        GuessTarget.entries.forEach { target ->
            val weight = weights[target] ?: return@forEach
            if (weight <= 0) return@forEach
            last = target
            remaining -= weight
            if (remaining < 0.0) return target
        }
        return last
    }
}

/**
 * 推荐结果的「槽内稳定器」（DESIGN §4.51）。
 *
 * 为什么需要它：页面每 30 秒 tick 一次、订单落库也会让习惯流重新发射；如果每次都重抽，
 * 用户会看到卡片自己换功能。稳定器把「什么时候重抽」收成一个 key：
 * `日期|槽|上课信号类型` 不变就沿用旧结果（null 也缓存，空档不会因一次 tick 突然出卡）。
 *
 * 开关关闭 / 打开时必须 [reset]：重新开启要求「清空习惯、重新开始」。
 */
class GuessPickStabilizer {
    private var key: String? = null
    private var pick: GuessPick? = null

    /** key 变了才调用 [compute]；否则返回缓存（可能为 null）。 */
    fun resolve(key: String, compute: () -> GuessPick?): GuessPick? {
        if (this.key != key) {
            this.key = key
            this.pick = compute()
        }
        return pick
    }

    /** 开关关闭、重新开启或跨天时清空缓存。 */
    fun reset() {
        key = null
        pick = null
    }
}
