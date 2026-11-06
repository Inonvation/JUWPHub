package edu.jxslu.schedule.ui.me

import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import edu.jxslu.schedule.Graph
import edu.jxslu.schedule.ui.widget.LifeWidgetSync
import edu.jxslu.schedule.data.prefs.DisplayPrefs
import edu.jxslu.schedule.data.repo.ImportPreview
import edu.jxslu.schedule.data.repo.ImportResult
import edu.jxslu.schedule.data.repo.ScheduleRepository
import edu.jxslu.schedule.domain.BgScale
import edu.jxslu.schedule.domain.Course
import edu.jxslu.schedule.domain.CourseFilter
import edu.jxslu.schedule.domain.ScheduleCalculator
import edu.jxslu.schedule.domain.SemesterConfig
import edu.jxslu.schedule.domain.StartPage
import edu.jxslu.schedule.domain.ThemeMode
import edu.jxslu.schedule.domain.ThemePalette
import edu.jxslu.schedule.domain.TimeSlot
import edu.jxslu.schedule.domain.TimeSlotRules
import edu.jxslu.schedule.domain.Timetable
import edu.jxslu.schedule.ui.common.ImportTarget
import edu.jxslu.schedule.ui.common.importSuccessText
import edu.jxslu.schedule.ui.common.readTextFromUri
import edu.jxslu.schedule.ui.common.resolveImportTarget
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.time.LocalDate

data class MeUiState(
    val loading: Boolean = true,
    val semester: SemesterConfig? = null,
    val courseCount: Int = 0,
    val currentWeek: Int = 0,
    /** 当前作息表，供课表设置子页展示与编辑 */
    val timeSlots: List<TimeSlot> = emptyList(),
    /** 主题模式，供设置页三选一 */
    val themeMode: ThemeMode = ThemeMode.System,
    /** 合并后的显示偏好（2026-09-19 起全部为全局项），供显示设置子页 */
    val displayPrefs: DisplayPrefs = DisplayPrefs(),
    /** 当前课表全量课程：显示设置页上半区实时预览用（已按 courseFilter 过滤） */
    val courses: List<Course> = emptyList(),
    /** 当前课表名与全部课表：设置页归属标注与导入目标弹窗用（DESIGN §4.9） */
    val timetableName: String = "",
    val timetables: List<Timetable> = emptyList(),
    val currentTimetableId: Long = 0L,
    /** 课表设置子页：正在配置的课表（默认 = 当前课表）及其学期口径（DESIGN §4.9）。 */
    val configTargetId: Long = 0L,
    val configSemester: SemesterConfig? = null,
    val configCourseCount: Int = 0,
    val configWeek: Int = 0,
    val message: String? = null,
)

sealed interface OneShot {
    /** [undo] 非 null 时 UI 以「撤销」Snackbar 呈现，用户点撤销后执行。 */
    data class Message(val text: String, val undo: (suspend () -> Unit)? = null) : OneShot
    data class ConfirmImport(val preview: ImportPreview.Ok, val text: String) : OneShot
}

@OptIn(ExperimentalCoroutinesApi::class)
class MeViewModel(private val repo: ScheduleRepository) : ViewModel() {

    /** 六源合成：combine 无类型安全重载超过 5 个，课表配置先收进一个 data class。 */
    private val configFlow = combine(
        repo.semester,
        repo.courses,
        repo.timeSlots,
        repo.displayPrefs,
        repo.timetables,
    ) { semester, courses, timeSlots, prefs, timetables ->
        MeConfig(semester, courses, timeSlots, prefs, timetables)
    }

    /** 课表设置子页的编辑目标：0 = 跟随当前课表（进页默认）；用户点 chips 后固定选中。 */
    private val configTargetOverride = MutableStateFlow(0L)

    fun selectConfigTarget(timetableId: Long) {
        configTargetOverride.value = timetableId
    }

    /** 解析后的编辑目标：override 优先，否则跟随当前课表。 */
    private val configTargetId: Flow<Long> = configTargetOverride.flatMapLatest { override ->
        if (override == 0L) repo.currentTimetableId else flowOf(override)
    }

    /** 目标课表的学期配置与课程数：chips 切到哪张就观察哪张（DESIGN §4.9）。 */
    private val targetConfigFlow = configTargetId.flatMapLatest { id ->
        combine(repo.observeSemesterFor(id), repo.observeCourseCountFor(id)) { semester, count ->
            TargetConfig(id, semester, count)
        }
    }

    /**
     * uiState 的首帧初值。DataStore 的第一份数据必然晚于 UI 首帧，stateIn 的初值
     * 若写 `DisplayPrefs()` 默认值，外观与交互页的开关（动态取色、悬浮导航栏等）会先按
     * 默认渲染、真值到达后再跳一次（2026-09-28 用户反馈）。构造时阻塞读一次真值作初值，
     * 与 MainActivity 启动链的 runBlocking 读同一模式；DataStore 读过一次后常驻内存，
     * 这次收集走的是缓存，不额外读盘。
     *
     * **本类只保留这一次阻塞读**（2026-11-06 审计 §3.7）：同一份 DataStore 文件拆成多条流
     * 各读一次不会更快——它们串行排队，每次 `runBlocking` 的代价照付。所以天气提示开关
     * （单键流，进不了 `DisplayPrefs` 的合并模型）也在这**同一次**里取出来，
     * 见 [firstFrame]；要加新开关就并进这里，别再加第二个 `runBlocking`。
     */
    private val firstFrame: FirstFrame = runBlocking {
        FirstFrame(
            prefs = repo.displayPrefs.first(),
            weatherEnabled = repo.weatherEnabled.first(),
        )
    }

    private val initialPrefs: DisplayPrefs get() = firstFrame.prefs

    val uiState: StateFlow<MeUiState> = combine(
        configFlow,
        repo.currentTimetableId,
        targetConfigFlow,
    ) { config, currentTimetableId, target ->
        // 「本周」只认学期内的周次（学期外 0 = 不显示 / 不在学期内）：
        // 裸 weekNumberOf 在学期结束后会涨到 30+，设置页与教务账号页会显示「第 37 周」
        val week = ScheduleCalculator.todayWeekOf(config.semester, LocalDate.now()) ?: 0
        MeUiState(
            loading = false,
            semester = config.semester,
            courseCount = config.courses.size,
            currentWeek = week,
            timeSlots = config.timeSlots,
            themeMode = config.prefs.themeMode,
            displayPrefs = config.prefs,
            courses = config.courses.filter { config.prefs.courseFilter.matches(it.kind) },
            timetableName = config.timetables.firstOrNull { it.id == currentTimetableId }?.name.orEmpty(),
            timetables = config.timetables,
            currentTimetableId = currentTimetableId,
            configTargetId = target.id,
            configSemester = target.semester,
            configCourseCount = target.courseCount,
            configWeek = ScheduleCalculator.todayWeekOf(target.semester, LocalDate.now()) ?: 0,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        // 只冻结偏好两件（开关行首帧直接渲染的就是它们）；课表/学期等 Room 派生字段
        // 首帧保持默认空值，loading 仍是 true，页面自己的骨架逻辑照旧
        MeUiState(displayPrefs = initialPrefs, themeMode = initialPrefs.themeMode),
    )

    private val _oneShot = MutableStateFlow<OneShot?>(null)
    val oneShot: StateFlow<OneShot?> = _oneShot

    /**
     * 今日页顶栏天气提示开关（DESIGN §3.29）。**默认关**，单独一条流——
     * `baseDisplayPrefs` 的 combine 已满员（AGENTS.md 口径），不进合并模型。
     *
     * 首帧真值取 [firstFrame]（同一次阻塞读），开关不先按默认值渲染再跳一次。
     *
     * **不能删**：`ui/weather/WeatherSettingsScreen.kt:54/80` 订的就是本类的这一条与
     * [setWeatherEnabled]——天气页**没有**自己的 ViewModel。2026-11-06 曾因误判"零引用"
     * 删掉，`compileDebugKotlin` 立刻报 `Unresolved reference 'weatherEnabled'`。
     * 删成员前 grep 必须覆盖整个 `app/src/main`，不能只看自己写域。
     */
    val weatherEnabled: StateFlow<Boolean> = repo.weatherEnabled
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            firstFrame.weatherEnabled,
        )

    // 2026-11-06：这里原有 `strongReminderVibration` 的 StateFlow（一次 runBlocking 读
    // DataStore），**零引用**——提醒页订阅的是 `ReminderSettingsScreen.kt:270` 文件内自己的
    // 那一份（`ui/me/ReminderSettingsScreen.kt:84` 的 `viewModel.strongReminderVibration`
    // 解析到的是它，不是本类）。删掉后设置页少一次主线程阻塞读。
    // **不要再加回来**：需要这个值的页面自己从 `repo` 取，别在共用的 MeViewModel 上挂
    // 只有某一页用的流（它一被实例化就付阻塞读的代价）。

    // ---- 背景图（DESIGN §4.21）：选图与移除都在 VM 里跑，转屏不丢进行中的导入 ----

    private val _bgImporting = MutableStateFlow(false)

    /** 背景图正在落盘：面板里「选择」按钮据此转成「处理中…」并禁用。 */
    val bgImporting: StateFlow<Boolean> = _bgImporting

    private val _bgNotice = MutableStateFlow<String?>(null)

    /**
     * 背景图操作的结果提示。**只给显示设置面板内部看**（`InlineNoticeRow`）：
     * 那个面板是自绘的覆盖层，Snackbar 落在它下面，用户看不见（同导入弹层的检测结果）。
     */
    val bgNotice: StateFlow<String?> = _bgNotice

    fun consumeBgNotice() {
        _bgNotice.value = null
    }

    /**
     * 选一张背景图（DESIGN §4.21）：落盘 → 写偏好 → 删旧文件。
     * 顺序不可乱：先删旧文件再写偏好，中间失败就是偏好指向一个不存在的文件。
     */
    fun importScheduleBackground(context: Context, uri: Uri) {
        if (_bgImporting.value) return
        _bgImporting.value = true
        _bgNotice.value = null
        viewModelScope.launch {
            try {
                val app = context.applicationContext
                val store = Graph.scheduleBackground(app)
                // 直接读一次偏好，不读 uiState：uiState 是 WhileSubscribed 的，
                // 没有订阅者时它的 value 是默认值，旧文件名会读成 null，旧图就删不掉了
                val previous = repo.displayPrefs.first().bgImageName
                val name = store.importUri(uri)
                if (name == null) {
                    _bgNotice.value = "这张图读不出来，换一张试试"
                    return@launch
                }
                repo.setBackgroundImage(name)
                if (previous != null && previous != name) store.delete(previous)
            } finally {
                _bgImporting.value = false
            }
        }
    }

    /** 移除背景：先清偏好再删文件（渲染层立即回到无背景，文件删失败也只是留个孤儿，冷启动会扫掉）。 */
    fun removeScheduleBackground(context: Context) {
        viewModelScope.launch {
            val store = Graph.scheduleBackground(context.applicationContext)
            val name = repo.displayPrefs.first().bgImageName
            repo.setBackgroundImage(null)
            store.delete(name)
        }
    }

    private val _pendingImportText = MutableStateFlow<String?>(null)

    fun consumeOneShot() {
        _oneShot.value = null
    }

    /**
     * 保存学期配置到**当前选中的课表**（课表设置页可切换编辑目标，DESIGN §4.9）：
     * 目标课表没配过学期时以本次输入直接建行。
     */
    fun saveSemester(startDate: String, totalWeeks: Int) {
        viewModelScope.launch {
            val ok = runCatching { ScheduleCalculator.parseDate(startDate) }.isSuccess
            if (!ok) {
                _oneShot.value = OneShot.Message("开学日期格式应为 yyyy-MM-dd，例如 2026-09-07")
                return@launch
            }
            val weeks = totalWeeks.coerceIn(1, 30)
            val state = uiState.value
            val targetId = state.configTargetId.takeIf { it != 0L } ?: state.currentTimetableId
            val old = repo.semesterFor(targetId) ?: SemesterConfig(startDate, weeks, 1)
            repo.updateSemesterFor(targetId, old.copy(startDate = startDate, totalWeeks = weeks))
            val name = state.timetables.firstOrNull { it.id == targetId }?.name.orEmpty()
            _oneShot.value = OneShot.Message("已保存「${name.ifBlank { "课表" }}」的学期设置")
        }
    }

    fun exportJson(context: Context, uri: Uri) {
        viewModelScope.launch {
            runCatching {
                val json = repo.exportJson()
                context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                    out.write(json.toByteArray(Charsets.UTF_8))
                } ?: error("无法打开输出流")
            }.onSuccess {
                _oneShot.value = OneShot.Message("已导出课表 JSON")
            }.onFailure {
                _oneShot.value = OneShot.Message("导出失败：${it.message}")
            }
        }
    }

    fun importFromUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            val text = readTextFromUri(context, uri)
            if (text.isNullOrBlank()) {
                _oneShot.value = OneShot.Message("读取文件失败")
                return@launch
            }
            prepareImport(text)
        }
    }

    fun importFromClipboard(context: Context) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = cm.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
        if (text.isNullOrBlank()) {
            _oneShot.value = OneShot.Message("剪贴板为空")
            return
        }
        viewModelScope.launch { prepareImport(text) }
    }

    private suspend fun prepareImport(text: String) {
        when (val preview = repo.previewImport(text)) {
            is ImportPreview.Error -> _oneShot.value = OneShot.Message(preview.message)
            is ImportPreview.Ok -> {
                _pendingImportText.value = text
                _oneShot.value = OneShot.ConfirmImport(preview, text)
            }
        }
    }

    /**
     * 按弹窗选定的目标导入（DESIGN §4.9）：目标可为已有课表，也可新建
     * （新建时配置取默认配置源）；导入后切到目标课表让用户直接看到结果。
     */
    fun confirmImport(target: ImportTarget, merge: Boolean) {
        val text = _pendingImportText.value ?: return
        _pendingImportText.value = null
        _oneShot.value = null
        viewModelScope.launch {
            val targetId = resolveImportTarget(repo, target)
            // 写库异常必须在这里收住：`ImportResult.Failure` 只覆盖解析/校验阶段，
            // 事务抛异常（磁盘满 / SQLite 异常）会一路冒到 viewModelScope——进程崩，
            // 而数据其实已经整体回滚（2026-11-06 复核）。
            val r = try {
                repo.importJson(text, merge, targetId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _oneShot.value = OneShot.Message("写入失败：${e.message ?: "未知错误"}，未改动任何数据")
                return@launch
            }
            when (r) {
                is ImportResult.Success -> {
                    repo.setCurrentTimetable(targetId)
                    // 成功提示收口在 `ui/common/BackupImportText.kt`：两个导入入口
                    // （我的 → 数据 / 课表页 → 导入 JSON）必须报同样的内容——课程数之外还要
                    // 报出顺手恢复了多少成绩/笔记/作业/调休，否则用户以为只动了课表
                    // （2026-11-06 审计 D2）。手拼两份必然漂移，别再写回来。
                    _oneShot.value = OneShot.Message(importSuccessText(r))
                }
                is ImportResult.Failure ->
                    _oneShot.value = OneShot.Message(r.message)
            }
        }
    }

    fun cancelPendingImport() {
        _pendingImportText.value = null
        consumeOneShot()
    }

    fun clearCourses() {
        viewModelScope.launch {
            // 快照先行：清空后 Snackbar 里给「撤销」，恢复按原 id/颜色逐门写回
            val snapshot = repo.courses.first()
            repo.clearCourses()
            _oneShot.value = OneShot.Message(
                if (snapshot.isEmpty()) "课表已经是空的" else "已清空 ${snapshot.size} 门课程",
                undo = snapshot.takeIf { it.isNotEmpty() }?.let { list ->
                    { repo.restoreCourses(list) }
                },
            )
        }
    }

    /**
     * 保存作息表。
     * 校验不通过只提示、不写入：脏作息不会让页面崩，只会让课表安静地显示错的时间。
     */
    fun saveTimeSlots(slots: List<TimeSlot>) {
        viewModelScope.launch {
            val err = TimeSlotRules.validate(slots)
            if (err != null) {
                _oneShot.value = OneShot.Message(err)
                return@launch
            }
            repo.saveTimeSlots(slots)
            _oneShot.value = OneShot.Message("作息表已保存")
        }
    }

    fun resetTimeSlots() {
        viewModelScope.launch {
            repo.resetTimeSlotsToDefault()
            _oneShot.value = OneShot.Message("已恢复默认作息")
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { repo.setThemeMode(mode) }
    }

    /** 触感反馈开关（全局，写入 DataStore）。 */
    fun setHapticsEnabled(value: Boolean) {
        viewModelScope.launch { repo.setHapticsEnabled(value) }
    }

    /**
     * 字体缩放锁定（「固定字体大小」，全局，默认开）。
     * 即时生效：Compose 根（JuwRoot）跟着 displayPrefs 重组，覆盖 LocalDensity。
     */
    fun setFontScaleLocked(value: Boolean) {
        viewModelScope.launch { repo.setFontScaleLocked(value) }
    }

    /** 动态取色开关（全局，Material You）。 */
    fun setDynamicColor(value: Boolean) {
        viewModelScope.launch { repo.setDynamicColor(value) }
    }

    /** 内置主题配色（全局）。动态取色关闭时生效，改完实时生效；小组件胶囊色跟着重渲染。 */
    fun setThemePalette(value: ThemePalette) {
        viewModelScope.launch {
            repo.setThemePalette(value)
            // 推送点先例：写完立即重渲染（LifeWidgetSync 零网络重跑快照），桌面马上换色
            runCatching { LifeWidgetSync.onThemePaletteChanged(Graph.appContext) }
        }
    }

    /** 悬浮导航栏（DESIGN §4.22）。 */
    fun setFloatingNavBar(value: Boolean) {
        viewModelScope.launch { repo.setFloatingNavBar(value) }
    }

    /** 百宝箱开关（DESIGN §4.47）：默认开，关掉后今日页「+」菜单不显示这一行。 */
    fun setToolboxEnabled(value: Boolean) {
        viewModelScope.launch { repo.setToolboxEnabled(value) }
    }

    /**
     * 启动页（DESIGN §3.3）：写入用户的选择本身——旧版「生活 / 我的」的值不在这里改写，
     * 生效页由 `StartPage.effectivePage` 落回今日。重启应用后生效，调用点负责提示。
     */
    fun setStartPage(page: StartPage) {
        viewModelScope.launch { repo.setStartPage(page) }
    }

    /** 胖乖「打开软件自动签到」（全局；默认关）。 */
    fun setQiekjAutoSignIn(value: Boolean) {
        viewModelScope.launch { repo.setQiekjAutoSignIn(value) }
    }

    // ---- 显示设置子页写入口（2026-09-19 起全部写全局，见 DESIGN §4.9 / §3.3） ----

    fun setShowSaturday(value: Boolean) = viewModelScope.launch { repo.setShowSaturday(value) }

    fun setShowSunday(value: Boolean) = viewModelScope.launch { repo.setShowSunday(value) }

    fun setShowAtSign(value: Boolean) = viewModelScope.launch { repo.setShowAtSign(value) }

    fun setTapBlankToAdd(value: Boolean) = viewModelScope.launch { repo.setTapBlankToAdd(value) }

    /**
     * 今日页顶栏天气提示开关（DESIGN §3.29）。关掉即从顶栏撤下，不残留占位。
     *
     * 入口在 `ui/weather/WeatherSettingsScreen.kt:80`（`viewModel::setWeatherEnabled`），
     * 与本类的 [weatherEnabled] 是一对，**不能只删其中一个**。
     */
    fun setWeatherEnabled(value: Boolean) = viewModelScope.launch { repo.setWeatherEnabled(value) }


    fun setShowNonCurrentWeek(value: Boolean) =
        viewModelScope.launch { repo.setShowNonCurrentWeek(value) }

    fun setCourseFilter(value: CourseFilter) = viewModelScope.launch { repo.setCourseFilter(value) }

    // ── 视图偏好的连续值写入（滑杆节流，2026-10-02）──
    // 一根滑杆拖一次会发几十上百个 onValueChange，直写就是同次数的「读-改-写整份
    // view_prefs JSON + 全量 DisplayPrefs 重发」。节流成 ≥[VIEW_PREFS_WRITE_MIN_INTERVAL_MS]
    // 一次并保证末值必落：这些设置全是绝对值语义（copy 覆盖），丢中间帧只丢过程，
    // 网格跟随的视觉延迟在一档之内；「重置」按钮走同一条路，150ms 延迟无感。
    private var viewPrefsWriteJob: Job? = null
    private var viewPrefsWriteEarliest = 0L

    private fun throttledViewPrefsWrite(write: suspend () -> Unit) {
        viewPrefsWriteJob?.cancel()
        viewPrefsWriteJob = viewModelScope.launch {
            val now = System.currentTimeMillis()
            if (now < viewPrefsWriteEarliest) delay(viewPrefsWriteEarliest - now)
            viewPrefsWriteEarliest = System.currentTimeMillis() + VIEW_PREFS_WRITE_MIN_INTERVAL_MS
            write()
        }
    }

    fun setGridFontDp(value: Float?) = throttledViewPrefsWrite { repo.setGridFontDp(value) }

    fun setGridRoomDp(value: Float?) = throttledViewPrefsWrite { repo.setGridRoomDp(value) }

    fun setGridTeacherDp(value: Float?) = throttledViewPrefsWrite { repo.setGridTeacherDp(value) }

    fun setGridRailDp(value: Float?) = throttledViewPrefsWrite { repo.setGridRailDp(value) }

    fun setGridDateDp(value: Float?) = throttledViewPrefsWrite { repo.setGridDateDp(value) }

    fun setRowHeightScale(value: Float) = throttledViewPrefsWrite { repo.setRowHeightScale(value) }

    fun setRailWidthDp(value: Float) = throttledViewPrefsWrite { repo.setRailWidthDp(value) }

    fun setDayHeaderHeightDp(value: Float) = throttledViewPrefsWrite { repo.setDayHeaderHeightDp(value) }

    fun setCellRadiusDp(value: Float) = throttledViewPrefsWrite { repo.setCellRadiusDp(value) }

    fun setCellOpacity(value: Float) = throttledViewPrefsWrite { repo.setCellOpacity(value) }

    fun setCellCenterH(value: Boolean) = viewModelScope.launch { repo.setCellCenterH(value) }

    fun setCellCenterV(value: Boolean) = viewModelScope.launch { repo.setCellCenterV(value) }

    fun setShowTeacher(value: Boolean) = viewModelScope.launch { repo.setShowTeacher(value) }

    fun setShowNowLine(value: Boolean) = viewModelScope.launch { repo.setShowNowLine(value) }

    fun setShowCellBorder(value: Boolean) = viewModelScope.launch { repo.setShowCellBorder(value) }

    fun setShowGridLines(value: Boolean) = viewModelScope.launch { repo.setShowGridLines(value) }

    // ---- 课表页背景图的数值参数（DESIGN §4.21）。选图与移除见 importScheduleBackground ----

    fun setBgImageOpacity(value: Float) = throttledViewPrefsWrite { repo.setBgImageOpacity(value) }

    fun setBgImageDim(value: Float) = throttledViewPrefsWrite { repo.setBgImageDim(value) }

    fun setBgImageBlur(value: Float) = throttledViewPrefsWrite { repo.setBgImageBlur(value) }

    fun setBgImageScale(value: BgScale) = throttledViewPrefsWrite { repo.setBgImageScale(value) }

    class Factory(private val repo: ScheduleRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = MeViewModel(repo) as T
    }

    private companion object {
        /** 滑杆连续写入的最小落盘间隔：网格跟随肉眼无感，磁盘写从每帧降到 ~7 次/秒。 */
        const val VIEW_PREFS_WRITE_MIN_INTERVAL_MS = 150L
    }
}

/** [MeViewModel.uiState] 的配置切片：combine 类型安全重载最多 5 个参数。 */
private data class MeConfig(
    val semester: SemesterConfig?,
    val courses: List<Course>,
    val timeSlots: List<TimeSlot>,
    val prefs: DisplayPrefs,
    val timetables: List<Timetable>,
)

/** [MeViewModel.targetConfigFlow] 的切片：正在配置的课表及其学期口径。 */
private data class TargetConfig(
    val id: Long,
    val semester: SemesterConfig?,
    val courseCount: Int,
)

/**
 * 设置页首帧快照：构造时**一次** `runBlocking` 读出来的真值。
 *
 * 为什么打包而不是各读各的：`displayPrefs` 与 `weatherEnabled` 在同一个 DataStore 文件里，
 * 拆成两次阻塞读就是两次串行排队、两次主线程阻塞（2026-11-06 审计 §3.7）。加新开关并进这里。
 */
private data class FirstFrame(
    val prefs: DisplayPrefs,
    val weatherEnabled: Boolean,
)

fun meFactory(context: Context) = MeViewModel.Factory(Graph.repository(context))
