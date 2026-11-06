package edu.jxslu.schedule.ui.week

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import edu.jxslu.schedule.data.calendar.CalendarSyncer
import edu.jxslu.schedule.data.prefs.DisplayPrefs
import edu.jxslu.schedule.data.repo.ScheduleRepository
import edu.jxslu.schedule.domain.CalendarSyncDefaults
import edu.jxslu.schedule.domain.BgScale
import edu.jxslu.schedule.domain.Course
import edu.jxslu.schedule.domain.CourseFilter
import edu.jxslu.schedule.domain.LocalTimeLike
import edu.jxslu.schedule.domain.ScheduleCalculator
import edu.jxslu.schedule.domain.ScheduleExporter
import edu.jxslu.schedule.domain.ScheduleOverride
import edu.jxslu.schedule.domain.SemesterConfig
import edu.jxslu.schedule.domain.TimeSlot
import edu.jxslu.schedule.domain.Timetable
import edu.jxslu.schedule.domain.TimetablePrefs
import edu.jxslu.schedule.ui.common.NoticeFeedback
import edu.jxslu.schedule.ui.common.NoticeTone
import edu.jxslu.schedule.ui.common.UndoableMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

data class WeekUiState(
    val loading: Boolean = true,
    val week: Int = 1,
    /**
     * 这张课表的「本周」周次；**null = 今天不在它的学期内**（学期未配置 / 未开学 / 已结束）。
     * 界面所有「回到本周」类动作都以它为闸：学期外没有可回的周次，
     * 此前拿裸周次（可能 > totalWeeks）比较，按钮会一直挂着点不掉（2026-11-05）。
     */
    val todayWeek: Int? = null,
    val semester: SemesterConfig? = null,
    val slots: List<TimeSlot> = emptyList(),
    /** 当前周课程 */
    val courses: List<Course> = emptyList(),
    /** 全量课程，供 Pager 邻页即时过滤，也用来算非本周灰态 */
    val allCourses: List<Course> = emptyList(),
    /** 日期覆盖（调休 / 停课，§4.40）；网格渲染时摊平成 plan 再查课 */
    val overrides: List<ScheduleOverride> = emptyList(),
    val totalCourseCount: Int = 0,
    /**
     * 可见的星期列（1=周一 … 7=周日），按显示顺序排列。
     *
     * 根因：此前只有一个 `showWeekend` 布尔，列数只能是 7 或 5 两种，
     * 「显示周日、不显示周六」表达不出来；而列宽与课块定位又都依赖「第几列」，
     * 所以不能用「列数」近似，必须显式给出可见星期序列（见 ScheduleCalculator.visibleDays）。
     */
    val visibleDays: List<Int> = (1..7).toList(),
    val showSaturday: Boolean = true,
    val showSunday: Boolean = true,
    val showNonCurrentWeek: Boolean = false,
    /**
     * 只看某类课程。
     * 过滤发生在 ViewModel 而不是渲染层：`courses` / `allCourses` / `totalCourseCount` 三处
     * 必须口径一致，否则会出现「网格是空的、但提示说课表不为空」这种自相矛盾的状态。
     */
    val courseFilter: CourseFilter = CourseFilter.All,
    /** 课名目标字号（dp）；null = 跟随系统。范围与换算见 `GridFont`。 */
    val gridFontDp: Float? = null,
    /** 教室（地点）行目标字号（dp）；null = 跟随课名等比缩放。 */
    val gridRoomDp: Float? = null,
    /** 教师行目标字号（dp）；null = 跟随课名等比缩放。 */
    val gridTeacherDp: Float? = null,
    /** 时间轴（节次 + 起止时间）目标字号（dp）；null = 跟随系统。独立于课名字号。 */
    val gridRailDp: Float? = null,
    /** 月份 / 日期表头目标字号（dp）；null = 跟随系统。独立于课名字号。 */
    val gridDateDp: Float? = null,
    /** 格子高度倍率（乘在自适应行高上），1.1f = 默认。 */
    val rowHeightScale: Float = 1.1f,
    /** 左侧时间轴栏宽（dp）。范围与默认值见 TimetablePrefs companion。 */
    val railWidthDp: Float = TimetablePrefs.DefaultRailWidthDp,
    /** 顶部表头高度（dp），参与自适应行高计算。 */
    val dayHeaderHeightDp: Float = TimetablePrefs.DefaultDayHeaderHeightDp,
    /** 格子圆角半径（dp）。 */
    val cellRadiusDp: Float = 6f,
    /** 格子不透明度（0.3–1）。 */
    val cellOpacity: Float = 1f,
    /** 格子文字水平居中。 */
    val cellCenterH: Boolean = false,
    /** 格子文字竖直居中。 */
    val cellCenterV: Boolean = false,
    /** 色块内显示授课教师。 */
    val showTeacher: Boolean = true,
    /** 显示当前时刻线。 */
    val showNowLine: Boolean = true,
    /** 显示课程色块虚线描边。 */
    val showCellBorder: Boolean = true,
    /** 显示网格行分隔辅助线。 */
    val showGridLines: Boolean = true,
    /** 地点前显示「@」前缀。 */
    val showAtSign: Boolean = true,
    /** 点击空白格新建课程。 */
    val tapBlankToAdd: Boolean = true,
    /** 背景图文件名（DESIGN §4.21）；null = 无背景。 */
    val bgImageName: String? = null,
    /** 背景图自身不透明度（下限到 1）。 */
    val bgImageOpacity: Float = 1f,
    /** 背景图压暗遮罩强度（0 到上限）。 */
    val bgImageDim: Float = 0f,
    /** 背景图模糊强度（0–1）。 */
    val bgImageBlur: Float = 0f,
    /** 背景图缩放方式。 */
    val bgImageScale: BgScale = BgScale.Fill,

    // ---- 多课表（DESIGN §4.9）----
    /** 当前课表名，顶栏切换入口显示用。 */
    val timetableName: String = "",
    /** 全部课表，切换弹层用。 */
    val timetables: List<Timetable> = emptyList(),
    val currentTimetableId: Long = 0L,
)

private data class WeekConfig(
    val semester: SemesterConfig? = null,
    val slots: List<TimeSlot> = emptyList(),
    val prefs: DisplayPrefs = DisplayPrefs(),
    val timetables: List<Timetable> = emptyList(),
    val currentTimetableId: Long = 0L,
)

/**
 * 课表页（周视图）的状态源。
 *
 * ## 两条互不干扰的出口
 *
 * - [uiState]：数据类状态（课程 / 学期 / 显示偏好 / 课表列表…）。**只在数据真的变了时重建**。
 * - [now]：时刻线专用「现在」，每 30 秒变一次。它**不在** [uiState] 里，读取点收在
 *   `WeekGrid.NowLine`，所以一次 tick 只让那一小块重组。
 *
 * 为什么必须分开：`courses` / `visibleDays` 每次都是新 `List`，Compose 视为 unstable；
 * 把「现在」塞进状态对象等于每 30 秒重建整页并让全部课块重新组合一遍。
 *
 * ## 显示偏好只在这里读，不在这里写
 *
 * 课表显示偏好（行高 / 圆角 / 不透明度 / 居中 / 教师行 / 时刻线…）的**唯一写入通道是
 * `MeViewModel`**（实现见 `MeViewModel.setRowHeightScale` 一族，UI 在 `DisplaySettingsContent`）。
 * 本类此前还留着一套同名 setter，没有任何调用点（真 UI 早就在「我的」侧），已删除——
 * **不要在这里再加写方法**：两套 setter 会让人以为「课表页的设置写这里、我的页的设置写那里」，
 * 实际两边写的是同一份 DataStore，只是多了一条会漂移的路径。
 */
class WeekViewModel(
    private val repo: ScheduleRepository,
    private val todayProvider: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {

    private val weekFlow = MutableStateFlow(1)

    /**
     * 时刻线专用的「现在」：**独立于 [uiState]**，每 30 秒 tick 只更新它。
     *
     * 为什么不并进 [uiState]：并进去的话每次 tick 都要重建整个状态对象
     * （`courses` / `visibleDays` 是新 `List`，unstable），于是整页 + 全部课块重组，
     * 而真正要变的只有一条 1.5dp 的时刻线。现在读取点收在 `WeekGrid.NowLine`，
     * tick 只让那一小块失效。
     */
    private val nowFlow = MutableStateFlow(LocalTimeLike.now())

    /** [nowFlow] 的只读出口；界面用 `produceState` 收成 `State` 往下传，不在父级读它。 */
    val now: StateFlow<LocalTimeLike> = nowFlow.asStateFlow()

    /**
     * 「今天」。**只有跨零点才变**——它进 [uiState] 的组合，所以日期翻页时状态会重建一次
     * （「本周」标记、今日列高亮、`todayDay` 都得跟着换天）。
     *
     * 为什么不能干脆去掉：`todayWeek` / `todayDay` 都由「今天」算出，若状态只在数据变化时
     * 重建，App 挂着过零点后「本周」会停在前一天，直到下次改课表才纠正。用日期做键
     * 是让 tick 每天最多触发一次重建，而不是每 30 秒一次。
     */
    private val todayFlow = MutableStateFlow(todayProvider())

    /**
     * 首帧门闸。
     * 根因：初始周次要等学期配置从库里读出来才能算，此前数据流不设防——
     * 先以「第 1 周」发帧、init 里再改周次，Pager 先渲染第 1 周再平移到本周，肉眼可见跳变。
     * 方案：ensureDefaults + 初始周次都就绪后才对外发第一帧，首帧直接落在本周
     * （配合 WeekScreen 侧「loading 期间不创建 Pager」，initialPage 一次到位）。
     */
    private val ready = MutableStateFlow(false)

    /** 五个「配置类」源先合成一个：combine 的类型安全重载最多到 5 个参数。 */
    private val configFlow = combine(
        repo.semester,
        repo.timeSlots,
        repo.displayPrefs,
        repo.timetables,
        repo.currentTimetableId,
    ) { semester, slots, prefs, timetables, currentTimetableId ->
        WeekConfig(semester, slots, prefs, timetables, currentTimetableId)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<WeekUiState> = ready.flatMapLatest { ready ->
        if (!ready) {
            flowOf(WeekUiState())
        } else {
            combine(
                weekFlow,
                repo.courses,
                repo.overrides,
                configFlow,
                // 第五路是「今天」而不是「现在」：见 todayFlow 的注释——每 30 秒 tick 不该重建
                // 这个状态对象，只有跨零点才需要。
                todayFlow,
            ) { week, loaded, overrides, config, today ->
                val semester = config.semester
                val slots = config.slots
                val prefs = config.prefs
                // 学期外给 null：裸周次（学期结束后 30+）不能当「本周」，见 WeekUiState.todayWeek
                val todayWeek = ScheduleCalculator.todayWeekOf(semester, today)
                val visible = loaded.filter { prefs.courseFilter.matches(it.kind) }
                WeekUiState(
                    loading = false,
                    week = week,
                    todayWeek = todayWeek,
                    semester = semester,
                    slots = slots,
                    courses = ScheduleCalculator.coursesInWeek(visible, week),
                    allCourses = visible,
                    // 日期覆盖（调休 / 停课，§4.40）：网格渲染用，摊平后走 coursesOnDay
                    overrides = overrides,
                    totalCourseCount = visible.size,
                    showSaturday = prefs.showSaturday,
                    showSunday = prefs.showSunday,
                    visibleDays = ScheduleCalculator.visibleDays(
                        prefs.showSaturday,
                        prefs.showSunday,
                    ),
                    showNonCurrentWeek = prefs.showNonCurrentWeek,
                    courseFilter = prefs.courseFilter,
                    gridFontDp = prefs.gridFontDp,
                    gridRoomDp = prefs.gridRoomDp,
                    gridTeacherDp = prefs.gridTeacherDp,
                    gridRailDp = prefs.gridRailDp,
                    gridDateDp = prefs.gridDateDp,
                    rowHeightScale = prefs.rowHeightScale,
                    railWidthDp = prefs.railWidthDp,
                    dayHeaderHeightDp = prefs.dayHeaderHeightDp,
                    cellRadiusDp = prefs.cellRadiusDp,
                    cellOpacity = prefs.cellOpacity,
                    cellCenterH = prefs.cellCenterH,
                    cellCenterV = prefs.cellCenterV,
                    showTeacher = prefs.showTeacher,
                    showNowLine = prefs.showNowLine,
                    showCellBorder = prefs.showCellBorder,
                    showGridLines = prefs.showGridLines,
                    showAtSign = prefs.showAtSign,
                    tapBlankToAdd = prefs.tapBlankToAdd,
                    bgImageName = prefs.bgImageName,
                    bgImageOpacity = prefs.bgImageOpacity,
                    bgImageDim = prefs.bgImageDim,
                    bgImageBlur = prefs.bgImageBlur,
                    bgImageScale = prefs.bgImageScale,
                    timetableName = config.timetables
                        .firstOrNull { it.id == config.currentTimetableId }?.name.orEmpty(),
                    timetables = config.timetables,
                    currentTimetableId = config.currentTimetableId,
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WeekUiState())

    init {
        viewModelScope.launch {
            repo.ensureDefaults()
            // 打开课表默认落在本周。此前固定从第 1 周开始，
            // 开学几周后每次进课表都要手动点一次「回到本周」，与主流课表习惯不符。
            // 周次必须在 ready 置位（对外发第一帧）之前定好，否则首帧仍是第 1 周。
            // 学期外（todayWeekOf 给 null）没有「本周」可落，停在默认的第 1 周。
            val semester = repo.semester.first()
            ScheduleCalculator.todayWeekOf(semester, todayProvider())?.let { weekFlow.value = it }
            ready.value = true
        }
    }

    /**
     * 界面侧每 30 秒调一次（`ResumedTickerEffect`，退后台/灭屏即停）。
     *
     * 只推 [nowFlow]——它**不参与** [uiState] 的组合，所以这条路径上没有任何状态对象重建，
     * 只有读它的那条时刻线会重组。顺带看一眼日期：跨零点时推一次 [todayFlow]，让「本周」
     * 标记与今日列换到新的一天（这也是本状态每天唯一一次重建）。
     *
     * **别把 nowFlow 加回 uiState 的组合**：那会让这里退回「每 30 秒重建整页 + 全部课块」，
     * 也就是这个类当初被拆开的原因。
     */
    fun refreshNow() {
        nowFlow.value = LocalTimeLike.now()
        val today = todayProvider()
        if (today != todayFlow.value) todayFlow.value = today
    }

    /**
     * 清掉背景图配置（DESIGN §4.21）。两处调用：
     * 渲染前发现偏好里的文件名在磁盘上已经不存在（清数据 / 恢复备份不完整），
     * 以及用户在显示设置面板里点「移除背景」后由 MeViewModel 侧收尾。
     * 文件本身的删除归 ScheduleBackgroundStore，这里只改偏好。
     */
    fun clearBackgroundImage() {
        viewModelScope.launch { repo.setBackgroundImage(null) }
    }

    fun setWeek(week: Int) {
        val total = uiState.value.semester?.totalWeeks ?: 20
        weekFlow.value = week.coerceIn(1, total.coerceAtLeast(1))
    }

    /** 切换当前课表：courses/slots/semester/prefs 全部随之换流（DESIGN §4.9）。 */
    fun selectTimetable(id: Long) {
        viewModelScope.launch { repo.setCurrentTimetable(id) }
    }

    fun shiftWeek(delta: Int) = setWeek(weekFlow.value + delta)

    /** 回到这张课表的「本周」；今天不在学期内时没有可回的周次，动作是空操作。 */
    fun goToToday() {
        val week = uiState.value.todayWeek ?: return
        weekFlow.value = week
    }

    fun upsert(course: Course) {
        viewModelScope.launch { repo.upsertCourse(course) }
    }

    fun delete(course: Course) {
        viewModelScope.launch {
            repo.deleteCourse(course)
            _undoableMessage.value = UndoableMessage("已删除「${course.name}」") {
                // 原课程对象带着原 id/颜色，upsertCourse 对 id>0 原样落库
                repo.upsertCourse(course)
            }
        }
    }

    // ---- 撤销（DESIGN §3.3）：破坏性动作执行后给可撤销的 Snackbar ----

    private val _undoableMessage = MutableStateFlow<UndoableMessage?>(null)

    /** 带撤销动作的反馈；UI 侧消费后调 [consumeUndoableMessage] 置空。 */
    val undoableMessage: StateFlow<UndoableMessage?> = _undoableMessage

    fun consumeUndoableMessage() {
        _undoableMessage.value = null
    }

    // ---- 分享（DESIGN §4.12）：日历同步 / CSV / JSON ----

    // 结果带语气（成功/失败/警告），提示卡据此选图标与强调色；
    // 只传文案的话 UI 侧就得靠字符串猜哪条是失败，那是把判断塞回展示层
    private val _shareMessage = MutableStateFlow<NoticeFeedback?>(null)

    /** 分享动作结果，UI 侧消费后置空（提示卡一次性展示）。 */
    val shareMessage: StateFlow<NoticeFeedback?> = _shareMessage

    fun consumeShareMessage() {
        _shareMessage.value = null
    }

    private suspend fun expandedEvents(): List<ScheduleExporter.CourseEvent>? =
        repo.expandedCalendarEvents()

    fun exportCsv(context: Context, uri: Uri) {
        viewModelScope.launch {
            val result = runCatching {
                val events = expandedEvents() ?: error("请先在「我的 → 课表设置」配置学期，并确认课表不为空")
                val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
                val bytes = bom + ScheduleExporter.toGoogleCalendarCsv(events).toByteArray(Charsets.UTF_8)
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                        out.write(bytes)
                    } ?: error("无法打开输出流")
                }
                "已导出 ${events.size} 条课程（CSV）"
            }
            _shareMessage.value = result.fold(
                onSuccess = { NoticeFeedback(it, NoticeTone.Success) },
                onFailure = { NoticeFeedback("导出失败：${it.message}", NoticeTone.Error) },
            )
        }
    }

    fun exportJson(context: Context, uri: Uri) {
        viewModelScope.launch {
            val result = runCatching {
                val json = repo.exportJson()
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                        out.write(json.toByteArray(Charsets.UTF_8))
                    } ?: error("无法打开输出流")
                }
                "已导出课表 JSON"
            }
            _shareMessage.value = result.fold(
                onSuccess = { NoticeFeedback(it, NoticeTone.Success) },
                onFailure = { NoticeFeedback("导出失败：${it.message}", NoticeTone.Error) },
            )
        }
    }

    fun syncToCalendar(context: Context) {
        viewModelScope.launch {
            val events = expandedEvents()
            if (events == null) {
                _shareMessage.value = NoticeFeedback(
                    "请先在「我的 → 课表设置」配置学期，并确认课表不为空",
                    NoticeTone.Warning,
                )
                return@launch
            }
            val reminder = repo.calendarReminderMinutes.first()
            _shareMessage.value = when (val r = CalendarSyncer.sync(context, events, reminder)) {
                is CalendarSyncer.CalendarSyncResult.Success -> NoticeFeedback(
                    CalendarSyncDefaults.syncSuccessMessage(r.count, reminder, r.reminderMissing),
                    if (r.reminderMissing > 0) NoticeTone.Warning else NoticeTone.Success,
                )
                is CalendarSyncer.CalendarSyncResult.Deleted -> NoticeFeedback(
                    "已删除 ${r.count} 条课程日历",
                    NoticeTone.Success,
                )
                is CalendarSyncer.CalendarSyncResult.NoCalendarAccount -> NoticeFeedback(
                    "手机上没有可用日历账户，请先在系统日历中登录或添加账户",
                    NoticeTone.Warning,
                )
                is CalendarSyncer.CalendarSyncResult.NoPermission -> NoticeFeedback(
                    "日历权限未授予，无法同步",
                    NoticeTone.Warning,
                )
                is CalendarSyncer.CalendarSyncResult.Error -> NoticeFeedback(
                    "同步失败：${r.message}",
                    NoticeTone.Error,
                )
            }
        }
    }

    class Factory(private val repo: ScheduleRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            WeekViewModel(repo) as T
    }
}
