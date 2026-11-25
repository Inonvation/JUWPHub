package edu.jxslu.schedule.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import edu.jxslu.schedule.data.prefs.DisplayPrefsStore
import edu.jxslu.schedule.data.repo.HomeworkRepository
import edu.jxslu.schedule.data.repo.ScheduleRepository
import edu.jxslu.schedule.data.weather.WeatherRepository
import edu.jxslu.schedule.domain.AppClock
import edu.jxslu.schedule.domain.Course
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import edu.jxslu.schedule.domain.EbikeUseMode
import edu.jxslu.schedule.domain.GuessPick
import edu.jxslu.schedule.domain.GuessPickStabilizer
import edu.jxslu.schedule.domain.GuessRules
import edu.jxslu.schedule.domain.GuessSource
import edu.jxslu.schedule.domain.GuessTarget
import edu.jxslu.schedule.domain.LocalTimeLike
import edu.jxslu.schedule.domain.PendingHomework
import edu.jxslu.schedule.domain.PendingHomework.Companion.EMPTY
import edu.jxslu.schedule.domain.ScheduleOverride
import edu.jxslu.schedule.domain.ScheduleTips
import edu.jxslu.schedule.domain.SemesterConfig
import edu.jxslu.schedule.domain.TimeSlot
import edu.jxslu.schedule.domain.TodayState
import edu.jxslu.schedule.domain.TodayTip
import edu.jxslu.schedule.domain.WeatherAdvice
import edu.jxslu.schedule.domain.buildTodayState
import edu.jxslu.schedule.domain.capabilities
import edu.jxslu.schedule.domain.pendingHomework
import edu.jxslu.schedule.ui.common.UndoableMessage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.random.Random

/**
 * 今日页 ViewModel。
 *
 * 状态推导本身（焦点课取舍、剩余列表、明日预告）在 `domain/TodayState.kt` 的
 * [buildTodayState]——桌面小组件（`ui/widget`）与今日页共用同一份纯函数，
 * 保证两边「哪节课算正在上 / 什么时候轮到明天上桌」的口径不会分叉。
 * 本类只负责把 Room 数据流与时间 tick 喂进去。
 *
 * ## iOS 移植 V1（2026-11-25）：搬进 `shared/commonMain` 时改的四处
 *
 * 1. `java.time.LocalDate` / `LocalDateTime` / `ZoneId` 换 `kotlinx.datetime` +
 *    [AppClock]（默认参数 `AppClock::today` / `AppClock::nowDateTime`）；
 * 2. `System.currentTimeMillis()` 换 [AppClock.nowMillis]（`refreshTick`）；
 * 3. `LocalDateTime.dayOfWeek.value` 换 `.ordinal + 1`——kotlinx-datetime 的 `DayOfWeek`
 *    没有 `value`，两者取值相同（周一 1 … 周日 7），全仓其余调用点也是这个写法；
 * 4. **删掉 `Factory`**：`design/ios-port.md` §11.2 ① 规定 ViewModel 是页面的必填参数、
 *    由宿主创建，Android 侧改成 `viewModel { TodayViewModel(...) }`，那个 `ViewModelProvider.Factory`
 *    就没有调用方了。留着是给下一批搬页面的人猜「这个还要不要用」。
 */
class TodayViewModel(
    private val repo: ScheduleRepository,
    private val homeworkRepo: HomeworkRepository,
    private val todayProvider: () -> LocalDate = AppClock::today,
    /**
     * 天气仓库（DESIGN §3.29）。可空注入：测试与旧构造点不传时顶栏那行恒为空。
     */
    private val weatherRepo: WeatherRepository? = null,
    /** 天气提示的「现在」来源，独立于 [todayProvider]（它只给日期，提示要判 18:00）。 */
    private val nowProvider: () -> LocalDateTime = AppClock::nowDateTime,
    /**
     * 有在案骑行（快趣单例 `state.ride != null`）。为真时「猜你想用」排除快趣：
     * 服务卡就在推荐卡上方，再推一次是同一件事说两遍（DESIGN §4.51，2026-11-18）。
     * 默认 false：单测与旧构造点不传时行为不变。
     */
    private val kvcxRideActive: Flow<Boolean> = flowOf(false),
    /**
     * 快趣显示偏好（使用方式 / 免费计时起点）。今日页的服务卡内容与计时行都靠它分流。
     * 可空注入：单测与旧构造点不传时按默认档 / 无计时处理，行为与改动前一致。
     */
    private val ebikePrefs: DisplayPrefsStore? = null,
    /**
     * 「猜你想用」要排除的目标。默认空集 = 全部候选都参与（Android 现状）。
     *
     * 用途只有一个：某平台上某个功能整个不存在时，别把它推上推荐卡——推一个点不动的入口，
     * 比不推更糟（iOS 精简版用它摘掉已下线的问渠）。
     */
    private val blockedGuessTargets: Set<GuessTarget> = emptySet(),
) : ViewModel() {

    private val tick = MutableStateFlow(0L)

    /**
     * 快趣偏好的**首帧快照**（2026-11-18）。
     *
     * 为什么必须同步读一次：`ebikeUseMode` / `ebikeRideStartAt` 是 DataStore 冷流。
     * 今日页每次新组合（切 Tab / 二级页返回）第一帧都会拿到默认档与 0——使用方式为
     * 「账号登录」时，服务卡里的骑行行被 `kvcxInAppRide` 挡掉一帧、整卡先收拢再展开；
     * 小程序方式的免费计时行同样晚一帧出现。两种都会把下面的作业 / 猜你想用 / 收件箱
     * 卡片顶得一跳（2026-11-18 用户报的「切 Tab 时提醒卡片重组跳动」）。
     *
     * VM 跨 Tab 存活，这里只读一次；DataStore 在 MainActivity 的启动快照里已经读热，
     * 不碰磁盘。取不到时退回默认值（与收集器首帧的旧行为一致，不新增失败面）。
     */
    private val ebikeFirstFrame: EbikeFirstFrame = ebikePrefs?.let { prefs ->
        runBlocking {
            runCatching {
                EbikeFirstFrame(
                    useMode = prefs.ebikeUseMode.first(),
                    rideStartAt = prefs.ebikeRideStartAt.first(),
                )
            }.getOrElse { EbikeFirstFrame() }
        }
    } ?: EbikeFirstFrame()

    /** 快趣使用方式（账号 / 小程序）。首帧就是真值，且跨 Tab 保留最后一次的值。 */
    val ebikeUseMode: StateFlow<EbikeUseMode> = (ebikePrefs?.ebikeUseMode ?: flowOf(EbikeUseMode.Default))
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ebikeFirstFrame.useMode)

    /** 免费计时起点（落盘）；0 = 没有计时。首帧就是真值，且跨 Tab 保留最后一次的值。 */
    val ebikeRideStartAt: StateFlow<Long> = (ebikePrefs?.ebikeRideStartAt ?: flowOf(0L))
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ebikeFirstFrame.rideStartAt)

    private data class EbikeFirstFrame(
        val useMode: EbikeUseMode = EbikeUseMode.Default,
        val rideStartAt: Long = 0L,
    )

    /**
     * 首帧门闸。
     * 根因：ensureDefaults 首次启动会写库（建默认课表/迁移/配色自愈），而 Room 流是
     * 「先吐当前值」——初始化完成前的空库帧会先发出去，UI 就在
     * 「加载中 → 尚未开学/课表为空 → 真实内容」之间连跳几帧，表现成闪屏。
     * 方案：初始化完成前不订阅数据流，对外第一帧就是写库之后的终态。
     */
    private val ready = MutableStateFlow(false)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<TodayState> = ready.flatMapLatest { ready ->
        if (!ready) {
            flowOf(TodayState())
        } else {
            combine(
                repo.semester,
                repo.timeSlots,
                repo.courses,
                // 日期覆盖（调休 / 停课，§4.40）随课表一起进 combine：改一条覆盖整页重算，
                // 与网格、小组件走同一个 coursesOnDay 口径
                repo.overrides,
                tick,
            ) { semester, slots, courses, overrides, _ ->
                buildTodayState(
                    semester = semester,
                    slots = slots,
                    courses = courses,
                    today = todayProvider(),
                    now = LocalTimeLike.now(),
                    overrides = overrides,
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayState())

    init {
        viewModelScope.launch {
            repo.ensureDefaults()
            ready.value = true
        }
    }

    /**
     * 今日页作业卡（DESIGN §3.3/§3.11）：未完成作业的排序与计数，口径全在
     * `domain/HomeworkCenter`（逾期 → 今天 → 未来 → 无截止）。
     *
     * 单独订阅、不并进 [uiState]：作业与课表是两套数据，混在一起会让改一条作业
     * 重算整页课表状态；随 [tick] 重算是为了跨天时文案（今天/明天/已过期）跟着走。
     */
    val homeworkPending: StateFlow<PendingHomework> =
        combine(homeworkRepo.observePending(), tick) { list, _ ->
            pendingHomework(list, todayProvider())
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EMPTY)

    /**
     * 当前课表学期下「课程名 → 教材名」映射（DESIGN §4.31，2026-10-07）：今日页课程卡
     * 把教材名挂在课名右边（2026-10-08 收口，不再另起一行）。与 [homeworkPending]
     * 同纪律**单独订阅**、不并进 [uiState]——教材是另一张表的数据，混进课表状态会让
     * 导入教材重算整页课表推导；课表没有学期号 / 无教材数据时是空映射，卡片不占位。
     */
    val textbookTitles: StateFlow<Map<String, List<String>>> = repo.textbookTitlesByCourse
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /**
     * [weatherLine] 的输入快照：课表侧四个 Room 流 + 天气开关 + [tick]。
     * 五个源是 combine 的标准重载上限，课表侧四个先套一层（类型都在 5 以内）。
     * **声明在 [weatherLine] 之前**：Kotlin 属性按声明顺序初始化，反过来会读到未初始化值。
     */
    private val scheduleInputs = combine(
        combine(
            repo.semester,
            repo.timeSlots,
            repo.courses,
            repo.overrides,
        ) { semester, slots, courses, overrides ->
            TipInputs(semester = semester, slots = slots, courses = courses, overrides = overrides)
        },
        repo.weatherEnabled,
        tick,
    ) { schedule, enabled, _ ->
        schedule.copy(enabled = enabled)
    }

    /**
     * 今日页顶栏那一行（DESIGN §3.29）：天气句 / 课表句 / 快下课有雨，**轮换显示**。
     *
     * 五条口径：
     * 1. **天气开关关着 = 天气一次网络都不发**（[repo] 的 weatherEnabled 是默认关的全局项），
     *    但**课表句照常出**（用户 2026-10-12：天气关着也显示课表提示）——课表句零网络，
     *    数据就是本页已有的 semester / slots / courses / overrides；
     * 2. 随 [tick] 重算（页面每 30 秒一次）：`now` 跨过 18:00 时提示从「今天」自动切到
     *    「明天」，不用等页面重进；命中仓库的 30 分钟 TTL 缓存时零网络；
     * 3. 「快下课有雨」是**独立链路**（[ScheduleTips.dismissalTipFor]）：永远看今天的课，
     *    命中就整行独占——即便 18:00 后正文讲的是明天，此刻还在上的课要下课了照样提示
     *    （2026-10-12 复查补上，早先跟参考日走会在 18:00 后静默消失）；
     *    它依赖逐小时预报：**天气功能关着时没有这份数据，这条不出**（不猜；课表句照常出）。
     *    它本身零网络，判据是课表 + 已取到的逐小时数据。
     * 4. **天气句与课表句各自独立成句、每 [ROTATE_MILLIS] 轮换**（用户 2026-10-12 定）：
     *    顶栏那一行窄，两句拼一起必被截断；轮换让每条都有整行宽度可读。
     *    只有一条时不轮换、直接常显；
     * 5. 每条都带 [TodayTip.Kind]：天气句点进天气页、课表句点进课表页（2026-10-20）；
     * 6. 取不到任何句子给 null，顶栏不占位、不显示半截内容。
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val weatherLine: StateFlow<TodayTip?> = scheduleInputs
        .map { inputs -> segmentsFor(inputs) }
        // 每 30 秒的 tick 大多产出同一组句子：只有这组句子真的变了才重开轮换，
        // 否则每 30 秒会把轮换重置回第一条（2026-10-12 复查补上）
        .distinctUntilChanged()
        .flatMapLatest { segments -> rotate(segments) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * 算当前该显示的候选句（快下课独占 / 天气句 / 课表句），按显示优先级排列。
     * 天气开关关着时零网络，只出课表句。
     */
    private suspend fun segmentsFor(inputs: TipInputs): List<TodayTip> {
        val now = nowProvider()
        val weather = weatherRepo
        val snapshot = if (inputs.enabled && weather != null) {
            runCatching { weather.snapshot() }.getOrNull()
        } else {
            null
        }
        val appNow = now
        if (snapshot != null) {
            return WeatherAdvice.segments(
                snapshot = snapshot,
                now = appNow,
                semester = inputs.semester,
                slots = inputs.slots,
                courses = inputs.courses,
                overrides = inputs.overrides,
            )
        }
        // 天气开关关着就不发请求（DESIGN §3.29 口径），但课表句照出（零网络）
        val date = appNow.date.plus(DatePeriod(days = WeatherAdvice.referenceDayIndex(appNow)))
        return ScheduleTips.dayTipFor(
            date = date,
            today = appNow.date,
            semester = inputs.semester,
            slots = inputs.slots,
            courses = inputs.courses,
            overrides = inputs.overrides,
        )?.let { listOf(it) } ?: emptyList()
    }

    /** 多条时每 [ROTATE_MILLIS] 轮换一条；单条常显；空表给 null（页面不占位）。 */
    private fun rotate(segments: List<TodayTip>): Flow<TodayTip?> = flow {
        when {
            segments.isEmpty() -> emit(null)
            segments.size == 1 -> emit(segments.first())
            else -> {
                var index = 0
                while (true) {
                    emit(segments[index % segments.size])
                    index++
                    delay(ROTATE_MILLIS)
                }
            }
        }
    }

    private companion object {
        /** 顶栏多条提示的轮换间隔（毫秒）。 */
        const val ROTATE_MILLIS = 5_000L
    }

    /** [weatherLine] 的输入快照：课表侧四个 Room 流 + 天气开关 + tick 的产物。 */
    private data class TipInputs(
        /** 天气开关；课表句不看它（详见 [weatherLine] 的口径 1）。 */
        val enabled: Boolean = true,
        val semester: SemesterConfig?,
        val slots: List<TimeSlot>,
        val courses: List<Course>,
        val overrides: List<ScheduleOverride>,
    )

    /**
     * 「猜你想用」的槽内稳定器（DESIGN §4.51）：key = `日期|槽|上课信号类型`。
     * 页面每 30 秒 tick、订单落库也会让习惯流重新发射；稳定器保证这两类变化都不换卡。
     */
    private val guessStabilizer = GuessPickStabilizer()

    /**
     * 今日页「猜你想用」推荐（DESIGN §4.51）。
     *
     * 输入：今日课表状态（判断 30 分钟内上课 / 20 分钟内下课）、功能开关、近 60 天订单事件、
     * 30 秒 tick（让时段边界与倒计时在页面停留期间也跟上）。
     *
     * 三步：
     * 1. 开关关 / 页面还在加载 → 清缓存、不出卡；
     * 2. 上课信号一票决定快趣（周末有课同样生效；骑行中快趣被排除，信号作废）；
     * 3. 其余走 [GuessRules.decide] 的时段表 / 周末先验 / 习惯权重，**每个 30 分钟槽只抽一次**。
     *
     * 上课信号的分钟数每 tick 用实时值覆盖（缓存里存的是抽样时的旧值，不能直接上屏）。
     *
     * 「在案骑行」= 控制器的 ride **且**使用方式是账号档：小程序方式查不到账号订单，
     * 也不发起查询，冷启动的位置提示（`rideHint`）不能把快趣在当前进程里永久排除。
     */
    val guessPick: StateFlow<GuessPick?> = combine(
        uiState,
        repo.guessYouWantEnabled,
        repo.guessUsage,
        tick,
        combine(kvcxRideActive, ebikeUseMode) { active, mode ->
            active && mode.capabilities().inAppRide
        },
    ) { state, enabled, usages, _, riding ->
        if (state.loading || !enabled) {
            guessStabilizer.reset()
            return@combine null
        }
        val now = nowProvider()
        val minuteOfDay = now.hour * 60 + now.minute
        val signal = GuessRules.classSignal(
            hasOngoing = state.ongoing != null,
            minutesToOngoingEnd = state.minutesToOngoingEnd,
            hasNext = state.next != null,
            minutesToNext = state.minutesToNext,
            minutesSincePrevEnd = state.minutesSinceLastEnd,
        )
        val slot = GuessRules.slotOf(minuteOfDay)
        // 骑行状态进 key：它决定快趣是否被排除，开骑 / 还车时卡片该跟着换
        val key = "${now.date}|$slot|${signal?.kind?.name ?: "none"}|$riding"
        val stable = guessStabilizer.resolve(key) {
            GuessRules.decide(
                dayOfWeek = now.dayOfWeek.ordinal + 1,
                minuteOfDay = minuteOfDay,
                signal = signal,
                usages = usages,
                nowMillis = now.toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds(),
                roll = Random.nextDouble(),
                blocked = (if (riding) setOf(GuessTarget.KVCX) else emptySet()) + blockedGuessTargets,
            )
        }
        // 上课信号的倒计时必须跟实时状态走；其余来源没有分钟数
        stable?.copy(
            minutes = if (stable.source == GuessSource.CLASS_START ||
                stable.source == GuessSource.CLASS_END
            ) {
                signal?.minutes
            } else {
                null
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** 界面每 30 秒调一次：让「还剩 X 分钟」和课的状态跟着时间走。 */
    fun refreshTick() {
        tick.value = AppClock.nowMillis()
    }

    fun upsert(course: Course) {
        viewModelScope.launch { repo.upsertCourse(course) }
    }

    fun deleteCourse(course: Course) {
        viewModelScope.launch {
            repo.deleteCourse(course)
            _undoable.value = UndoableMessage("已删除「${course.name}」") {
                repo.upsertCourse(course)
            }
        }
    }

    // ---- 撤销（DESIGN §3.3）：删除后 5 秒内可撤销 ----

    private val _undoable = MutableStateFlow<UndoableMessage?>(null)

    /** 带撤销动作的反馈；UI 侧消费后调 [consumeUndoable] 置空。 */
    val undoable: StateFlow<UndoableMessage?> = _undoable

    fun consumeUndoable() {
        _undoable.value = null
    }
}
