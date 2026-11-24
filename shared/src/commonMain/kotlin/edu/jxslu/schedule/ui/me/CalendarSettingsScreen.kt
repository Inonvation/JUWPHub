package edu.jxslu.schedule.ui.me

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import edu.jxslu.schedule.data.repo.ScheduleRepository
import edu.jxslu.schedule.domain.CalendarSyncDefaults
import edu.jxslu.schedule.domain.ScheduleExporter
import edu.jxslu.schedule.platform.collectAsStateWithLifecycle
import edu.jxslu.schedule.ui.common.AppConfirmDialog
import edu.jxslu.schedule.ui.common.AppSnackbarHost
import edu.jxslu.schedule.ui.common.SettingItem
import edu.jxslu.schedule.ui.common.SettingsSection
import edu.jxslu.schedule.ui.common.WheelValueDialog
import edu.jxslu.schedule.ui.common.rememberResumeTick
import edu.jxslu.schedule.ui.common.showNotice
import edu.jxslu.schedule.ui.icon.AppIcons
import edu.jxslu.schedule.ui.platform.CalendarSyncOutcome
import kotlinx.coroutines.Dispatchers
import kotlin.reflect.KClass
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/** 同步前置条件检测结果：驱动同步行的可用态与说明文案（DESIGN §4.12 的就绪口径）。 */
sealed interface SyncReadiness {
    /** 学期与作息齐全，可展开出 N 条课程事件。 */
    data class Ready(val eventCount: Int) : SyncReadiness

    /** 学期未配置（展开需要学期起始日）。 */
    data object NoSemester : SyncReadiness

    /** 当前课表没有课程。 */
    data object Empty : SyncReadiness
}

/**
 * 日历同步子页（DESIGN §4.12）：同步 / 一键删除已同步 / 提前提醒时长。
 *
 * 从「我的 → 日历同步」进入。同步口径与课表页分享弹层完全一致
 * （[ScheduleRepository.expandedCalendarEvents]），权限也由本页运行时申请。
 * 页面进阶反馈：进页即展示就绪状态（学期/课程/事件数）；同步与删除期间整页忙碌
 * （进度条 + 动作禁用），防止重复触发——写整学期事件在低端机上要好几秒。
 *
 * ## iOS 移植 W1（2026-11-25）搬进 `commonMain`
 *
 * 系统日历写入与运行时权限都不在共享层，收成 [CalendarSyncActions] 槽位交给宿主；
 * **null = 本平台没有系统日历**（iOS），此时同步 / 删除 / 权限三行整行不渲染。
 * 该槽位挂在 [CalendarSettingsViewModel.actions] 上而不是再当参数传一遍：
 * 宿主建 VM 时已经把它给了 VM（忙碌标志由它的回调复位），两处各拿一份容易给成两个实例。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarSettingsScreen(
    onBack: () -> Unit,
    /** 由宿主创建（Android `viewModel {}`、iOS `remember {}`），见 `design/ios-port.md` §11.1。 */
    viewModel: CalendarSettingsViewModel,
) {
    val actions = viewModel.actions
    val reminder by viewModel.reminderMinutes.collectAsStateWithLifecycle()
    val readiness by viewModel.readiness.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showReminderPicker by remember { mutableStateOf(false) }

    // 授权后是否继续同步：只有从「同步」入口发起的授权才续跑，直接点权限行不附带写动作
    var syncAfterGrant by remember { mutableStateOf(false) }
    var pendingSync by remember { mutableStateOf(false) }

    // 日历权限状态：进页与从系统设置返回（回到前台）时各读一次。
    // `rememberResumeTick` 是 `LocalLifecycleOwner` + `LifecycleEventObserver` 的共享层替代
    //（`design/ios-port.md` §11.2 ⑥ 的替换表）。
    val resumeTick = rememberResumeTick()
    var calendarGranted by remember(resumeTick) {
        mutableStateOf(actions?.isGranted() == true)
    }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showNotice(it)
            viewModel.consumeMessage()
        }
    }

    // 权限授予后的续跑放 LaunchedEffect，避免在 launcher 回调里直接调 VM 引发重组竞态
    //（与 WeekScreen 分享弹层的同步入口同一模式）
    LaunchedEffect(pendingSync) {
        if (pendingSync) {
            pendingSync = false
            viewModel.sync()
        }
    }

    fun startSync() {
        val a = actions ?: return
        syncAfterGrant = true
        if (a.isGranted()) {
            viewModel.sync()
        } else {
            a.request { granted ->
                calendarGranted = granted
                // syncAfterGrant 是一次性意图：无论授予与否都消费掉，
                // 避免「点同步→拒绝→事后从权限行授权」被误判为要续跑同步
                val continueSync = syncAfterGrant
                syncAfterGrant = false
                if (granted) {
                    pendingSync = continueSync
                } else {
                    viewModel.onPermissionDenied()
                }
            }
        }
    }

    fun requestCalendarPermission() {
        val a = actions ?: return
        syncAfterGrant = false
        if (!a.isGranted()) {
            a.request { granted -> calendarGranted = granted }
        }
    }

    // 未授权不禁用同步行：点「同步」本身就是授权入口（运行时申请后续跑）
    val syncEnabled = readiness is SyncReadiness.Ready && !busy

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("日历同步") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        snackbarHost = { AppSnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            // 分区卡之间的 12dp 换气：漏了这行三张卡会上下紧贴（其余设置页统一口径）
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 三节并两节（2026-10-07 收口）：提前提醒并入同步节（它只对同步事件生效）；
            // 日历权限是单行节，行副标题已把授权出路说清，节卡没有增量信息
            SettingsSection(
                title = "同步到手机日历",
                subtitle = "把当前课表写入系统日历（课程名 / 教室 / 教师）",
            ) {
                if (busy) {
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Spacer(Modifier.height(6.dp))
                }
                // 三行同步动作只在有系统日历时才渲染：null = 本平台做不到（iOS），
                // 给个点了没反应的入口比没有更糟（`design/ios-port.md` §11.2 ④）
                if (actions != null) {
                    SettingItem(
                        title = "同步课表到日历",
                        subtitle = when {
                            busy -> "正在写入日历…"
                            readiness is SyncReadiness.Ready ->
                                "将写入 ${(readiness as SyncReadiness.Ready).eventCount} 条日程 · 以 App 课表为准"
                            readiness is SyncReadiness.NoSemester -> "先到「我的 → 课表设置」配置学期"
                            readiness == null -> "正在统计…"
                            else -> "当前课表还没有课程"
                        },
                        onClick = { startSync() },
                        enabled = syncEnabled,
                        icon = AppIcons.CalendarAdd01,
                    )
                    SettingItem(
                        title = "一键删除已同步课程",
                        subtitle = if (busy) "请等当前动作完成" else "只删除本应用写入的日历事件",
                        onClick = { showDeleteConfirm = true },
                        enabled = !busy,
                        icon = AppIcons.Delete02,
                    )
                }
                SettingItem(
                    title = "提醒时长",
                    subtitle = "修改后重新同步即可更新已写入的提醒",
                    value = CalendarSyncDefaults.reminderLabel(reminder),
                    onClick = { showReminderPicker = true },
                    enabled = !busy,
                    icon = AppIcons.CalendarClock,
                )
                if (actions != null) {
                    SettingItem(
                        title = "日历读写权限",
                        subtitle = if (calendarGranted) "已授予" else "未授予 · 点此授权",
                        onClick = if (calendarGranted) {
                            null
                        } else {
                            { requestCalendarPermission() }
                        },
                        icon = AppIcons.CalendarSetting01,
                        showArrow = !calendarGranted,
                    )
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AppConfirmDialog(
            title = "删除已同步课程",
            message = "将从手机日历中删除全部由 JUWPHub 同步的课程事件（不影响你自己添加的日程）。",
            confirmLabel = "删除",
            danger = true,
            // 删除期间按键要按不动，否则连点会重复发起系统日历的批量删除
            confirmEnabled = !busy,
            onConfirm = {
                showDeleteConfirm = false
                viewModel.deleteSynced()
            },
            onDismiss = { showDeleteConfirm = false },
        )
    }

    if (showReminderPicker) {
        WheelValueDialog(
            title = "提前提醒时长",
            values = CalendarSyncDefaults.REMINDER_CHOICES.map { CalendarSyncDefaults.reminderLabel(it) },
            initialIndex = CalendarSyncDefaults.reminderChoiceIndex(reminder),
            onConfirm = { index ->
                showReminderPicker = false
                viewModel.setReminderMinutes(CalendarSyncDefaults.REMINDER_CHOICES[index])
            },
            onDismiss = { showReminderPicker = false },
        )
    }
}

/**
 * 日历同步页的状态机。
 *
 * 构造只吃共享层类型（`design/ios-port.md` §11.2 ③）：仓库来自 `Services.current.scheduleRepository()`，
 * 系统日历能力由宿主在构造时把 [CalendarSyncActions] 传进来（可为 null）。
 */
class CalendarSettingsViewModel(
    private val repo: ScheduleRepository,
    /** 系统日历能力；null = 本平台没有（iOS），同步与删除入口据此不渲染。 */
    val actions: CalendarSyncActions?,
) : ViewModel() {

    /** 提前提醒分钟数（全局）。初值阻塞读真值，改过的用户进页不会先显示默认再跳。 */
    val reminderMinutes: StateFlow<Int> = repo.calendarReminderMinutes
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            runBlocking { repo.calendarReminderMinutes.first() },
        )

    /** 就绪状态随学期/课表数据自动刷新；null = 还在算。展开是纯计算，放 Default 池。 */
    val readiness: StateFlow<SyncReadiness?> = combine(
        repo.semester,
        repo.courses,
        repo.timeSlots,
        repo.overrides,
    ) { semester, courses, slots, overrides ->
        when {
            semester == null -> SyncReadiness.NoSemester
            courses.isEmpty() -> SyncReadiness.Empty
            else -> SyncReadiness.Ready(
                ScheduleExporter.expandEvents(courses, slots, semester, overrides).ok.size,
            )
        }
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _busy = MutableStateFlow(false)

    /** 同步/删除进行中：UI 据此禁用入口并显示进度。 */
    val busy: StateFlow<Boolean> = _busy

    private val _message = MutableStateFlow<String?>(null)

    /** 动作结果，UI 侧消费后置空（Snackbar 一次性展示）。 */
    val message: StateFlow<String?> = _message

    fun consumeMessage() {
        _message.value = null
    }

    fun setReminderMinutes(minutes: Int) {
        viewModelScope.launch {
            repo.setCalendarReminderMinutes(minutes)
            _message.value = "提醒时长已改为${CalendarSyncDefaults.reminderLabel(minutes)}，重新同步后生效"
        }
    }

    fun sync() {
        val a = actions ?: return
        // 忙碌标志在进入协程前同步置位：viewModelScope 的调度有延迟，
        // 放协程体内会让同帧内的第二次点击也通过防线
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try {
                val events = repo.expandedCalendarEvents()
                if (events == null) {
                    _message.value = "请先在「我的 → 课表设置」配置学期，并确认课表不为空"
                    return@launch
                }
                _message.value = a.sync(events, reminderMinutes.value)
                    .describeSync(reminderMinutes.value)
            } catch (e: Exception) {
                _message.value = "同步失败：${e.message}"
            } finally {
                _busy.value = false
            }
        }
    }

    fun deleteSynced() {
        val a = actions ?: return
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try {
                _message.value = a.deleteSynced().describeDelete()
            } catch (e: Exception) {
                _message.value = "删除失败：${e.message}"
            } finally {
                _busy.value = false
            }
        }
    }

    fun onPermissionDenied() {
        _message.value = "未授予日历权限，无法同步"
    }

    class Factory(
        private val repo: ScheduleRepository,
        private val actions: CalendarSyncActions?,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: KClass<T>, extras: CreationExtras): T =
            CalendarSettingsViewModel(repo, actions) as T
    }
}

/** 同步结果 → 提示文案（口径与课表页分享弹层共用 [CalendarSyncDefaults.syncSuccessMessage]）。 */
private fun CalendarSyncOutcome.describeSync(reminderMinutes: Int): String = when (this) {
    is CalendarSyncOutcome.Synced ->
        CalendarSyncDefaults.syncSuccessMessage(count, reminderMinutes, reminderMissing)
    is CalendarSyncOutcome.Deleted -> "已删除 $count 条课程日历"
    CalendarSyncOutcome.NoCalendarAccount -> "手机上没有可用日历账户，请先在系统日历中登录或添加账户"
    CalendarSyncOutcome.NoPermission -> "日历权限未授予，无法同步"
    CalendarSyncOutcome.Unsupported -> "本平台不支持写入系统日历"
    is CalendarSyncOutcome.Error -> "同步失败：$message"
}

/** 删除结果 → 提示文案。删除是同步的一个分支，文案却不能说「已同步」，所以单列一份。 */
private fun CalendarSyncOutcome.describeDelete(): String = when (this) {
    is CalendarSyncOutcome.Deleted ->
        if (count == 0) "日历中没有本应用同步的课程" else "已从日历删除 $count 条课程"
    CalendarSyncOutcome.NoPermission -> "日历权限未授予，无法删除"
    CalendarSyncOutcome.NoCalendarAccount -> "手机上没有可用日历账户"
    CalendarSyncOutcome.Unsupported -> "本平台不支持写入系统日历"
    is CalendarSyncOutcome.Synced -> "已从日历删除课程"
    is CalendarSyncOutcome.Error -> "删除失败：$message"
}
