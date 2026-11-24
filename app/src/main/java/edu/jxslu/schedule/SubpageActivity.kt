package edu.jxslu.schedule

import android.app.Activity
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.zxing.client.android.Intents
import edu.jxslu.schedule.R
import edu.jxslu.schedule.data.jw.JwVpnDetector
import edu.jxslu.schedule.data.session.SessionService
import edu.jxslu.schedule.data.ujing.UjingAlipay
import edu.jxslu.schedule.domain.CourseKind
import edu.jxslu.schedule.platform.Logger
import edu.jxslu.schedule.platform.Services
import edu.jxslu.schedule.platform.UjingLocateOutcome
import edu.jxslu.schedule.platform.UjingPay
import edu.jxslu.schedule.platform.UjingPayOutcome
import edu.jxslu.schedule.platform.UjingPayResult
import edu.jxslu.schedule.ui.me.CalendarSettingsScreen
import edu.jxslu.schedule.ui.me.MeViewModel
import edu.jxslu.schedule.ui.me.DataSettingsScreen
import edu.jxslu.schedule.ui.me.JwAccountScreen
import edu.jxslu.schedule.ui.me.ReminderSettingsScreen
import edu.jxslu.schedule.ui.me.ShortcutSettingsScreen
import edu.jxslu.schedule.ui.me.WidgetSettingsScreen
import edu.jxslu.schedule.ui.me.hub.AboutUpdateScreen
import edu.jxslu.schedule.ui.me.hub.AccountsHubScreen
import edu.jxslu.schedule.ui.me.hub.AppearanceSettingsScreen
import edu.jxslu.schedule.ui.me.hub.PermissionsStorageScreen
import edu.jxslu.schedule.ui.me.hub.QuietHoursScreen
import edu.jxslu.schedule.ui.me.hub.SupportDevScreen
import edu.jxslu.schedule.ui.me.hub.TermsDeclarationsScreen
import edu.jxslu.schedule.ui.me.hub.TimetableHubScreen
import edu.jxslu.schedule.ui.campus.CampusCalendarScreen
import edu.jxslu.schedule.ui.campus.CampusCardSettingsScreen
import edu.jxslu.schedule.ui.campus.DormRepairScreen
import edu.jxslu.schedule.ui.campus.LeaveHubScreen
import edu.jxslu.schedule.ui.campus.PayCodeScreen
import edu.jxslu.schedule.ui.campus.StatementScreen
import edu.jxslu.schedule.ui.campus.StatementViewModel
import edu.jxslu.schedule.ui.campus.postYktPaymentInbox
import edu.jxslu.schedule.ui.common.rememberAttachmentImageUi
import edu.jxslu.schedule.ui.common.rememberMarkdownUi
import edu.jxslu.schedule.ui.common.AppPermissions
import edu.jxslu.schedule.ui.common.SessionExpiredNotice
import edu.jxslu.schedule.ui.common.washerScanIntent
import edu.jxslu.schedule.ui.curriculum.CurriculumScreen
import edu.jxslu.schedule.ui.ebike.KvcxOrdersScreen
import edu.jxslu.schedule.ui.ebike.KvcxScreen
import edu.jxslu.schedule.ui.ebike.RideScreen
import edu.jxslu.schedule.ui.ebike.BikeLocator
import edu.jxslu.schedule.ui.ebike.LocateResult
import edu.jxslu.schedule.ui.exam.ExamScreen
import edu.jxslu.schedule.ui.exam.rememberExamScreenActions
import edu.jxslu.schedule.ui.exam.ExamSettingsScreen
import edu.jxslu.schedule.ui.life.PowerBillScreen
import edu.jxslu.schedule.ui.life.PowerBillViewModel
import edu.jxslu.schedule.ui.life.openExternal
import edu.jxslu.schedule.ui.homework.HomeworkCourseScreen
import edu.jxslu.schedule.ui.homework.HomeworkDetailScreen
import edu.jxslu.schedule.ui.homework.HomeworkLibraryScreen
import edu.jxslu.schedule.ui.homework.HomeworkTodoScreen
import edu.jxslu.schedule.ui.homework.openLink
import edu.jxslu.schedule.ui.inbox.InboxScreen
import edu.jxslu.schedule.ui.internship.InternshipBasesScreen
import edu.jxslu.schedule.ui.jw.StudentInfoScreen
import edu.jxslu.schedule.ui.jw.JwScheduleListScreen
import edu.jxslu.schedule.ui.jw.TeachingCalendarScreen
import edu.jxslu.schedule.ui.jw.TextbookScreen
import edu.jxslu.schedule.ui.jw.rememberJwScreenPlatformActions
import edu.jxslu.schedule.ui.notes.NoteCourseScreen
import edu.jxslu.schedule.ui.notes.NoteDetailScreen
import edu.jxslu.schedule.ui.notes.NoteLibraryScreen
import edu.jxslu.schedule.ui.reminder.ClassReminder
import edu.jxslu.schedule.ui.score.ScoreScreen
import edu.jxslu.schedule.ui.score.rememberScoreScreenActions
import edu.jxslu.schedule.ui.score.ScoreSettingsScreen
import edu.jxslu.schedule.ui.score.TranscriptHistoryScreen
import edu.jxslu.schedule.ui.qzxy.QzxyScreen
import edu.jxslu.schedule.ui.qzxy.QzxyBillScreen
import edu.jxslu.schedule.ui.qzxy.QzxyDebugScreen
import edu.jxslu.schedule.ui.scholar.ScholarScreen
import edu.jxslu.schedule.ui.selection.SelectionCenterScreen
import edu.jxslu.schedule.ui.selection.SelectionRecordsScreen
import edu.jxslu.schedule.ui.selection.SelectionScreen
import edu.jxslu.schedule.ui.selection.SelectionWishesScreen
import edu.jxslu.schedule.ui.selection.rememberSelectionCenterClient
import edu.jxslu.schedule.ui.selection.rememberSelectionScreenActions
import edu.jxslu.schedule.ui.ai.AiChatScreen
import edu.jxslu.schedule.ui.ai.AiHistoryScreen
import edu.jxslu.schedule.ui.ai.AiSettingsScreen
import edu.jxslu.schedule.ui.timetable.ScheduleChangesScreen
import edu.jxslu.schedule.ui.timetable.TimetableManageScreen
import edu.jxslu.schedule.ui.tweak.CourseTweakScreen
import edu.jxslu.schedule.ui.ujing.UjingHistoryScreen
import edu.jxslu.schedule.ui.ujing.UjingOrderScreen
import edu.jxslu.schedule.ui.ujing.UjingScreen
import edu.jxslu.schedule.ui.ujing.UjingViewModel
import edu.jxslu.schedule.ui.water.WaterScreen
import edu.jxslu.schedule.ui.weather.WeatherScreen
import edu.jxslu.schedule.ui.weather.WeatherSettingsScreen
import edu.jxslu.schedule.ui.weather.WeatherViewModel
import edu.jxslu.schedule.ui.toolbox.CainiaoQueryScreen
import edu.jxslu.schedule.ui.toolbox.StitchScreen
import edu.jxslu.schedule.ui.toolbox.ToolboxScreen
import edu.jxslu.schedule.ui.toolbox.WatermarkScreen
import edu.jxslu.schedule.ui.week.ScheduleShareScreen
import edu.jxslu.schedule.ui.wenqu.WenquGrabScreen
import edu.jxslu.schedule.ui.wenqu.WenquRecordsScreen
import edu.jxslu.schedule.ui.wenqu.WenquScreen
import edu.jxslu.schedule.ui.wenqu.WenquSettingsScreen
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


/**
 * 设置类二级页的统一独立窗口（与 [JwImportActivity] 同形态）。
 *
 * 根因：这些二级页此前与三个 tab 同 NavHost，底栏在子页上仍然可达，
 * 切 tab 会把子页和主界面栈混在一起（返回栈语义含糊，且子页期间改数据可能撞上
 * 正在重组的主界面）。改为新窗口覆盖：底栏天然不可达，返回键/页内返回都只是关窗口。
 * 数据仍走 Graph 单例 + 响应式流，窗口关闭后主界面自动刷新。
 *
 * 动画（res/anim，平台只有 fade 与左滑资源，右推入必须自备）：
 * 打开 = 新窗口从右缘平移推入覆盖主窗口（slide_in_right，主窗口 stay_still、原地不动）；
 * 关闭（页内返回或系统返回）= 向右滑出露出主窗口（slide_out_right），统一从
 * [finish] 出口生效。打开侧由 [start] 出口触发，三个主 Tab 与全部二级页同一规格。
 */
class SubpageActivity : ComponentActivity() {

    /**
     * 本窗口的定位参数。onCreate 解析一次，[onResume] / [finish] 拿它记账
     * （见 [SubpageStack]）。用 `by lazy` 不用 lateinit：解析只发生在 intents 上，
     * 与 onCreate 的读取口径必须完全一致，写成两处迟早跑偏。
     */
    private val request: SubpageRequest by lazy {
        // 脏 extra 回退到第一个入口：宁可开对一半的页，也不要崩溃
        SubpageRequest.from(intent) ?: SubpageRequest(SubpageScreen.TIMETABLE_MANAGE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val request = request
        SubpageStack.onWindowCreated(request)
        // 收件箱（2026-11-12）：**打开消息指向的页面 = 这条消息看过了**。
        // 通知（调课 / 考试 / 成绩 / 余额 / 轮次 / 问渠 / 报修）点进来直接落二级页，
        // 页面上没有「点行标已读」这个动作，此前看完返回今日页红点还在（用户报障）。
        // 收口在本窗口：通知跳板、小组件、页内入口最终都从这里开页，按 route 清未读即可，
        // 不必让每个目标页各写一遍。写库在 IO 线程、失败静默，不挡窗口创建。
        Graph.appScope.launch {
            Graph.inboxRepository(this@SubpageActivity).markRouteRead(request.screen.name)
        }
        // 小组件「去开水」直达令牌（WaterAutoStart）：进页前武装、本窗口首帧消费。
        // 在 composable 外消费一次——组合重组再读会拿到恒定值，消费语义就没了
        val autoStart = WaterAutoStart.consume(request.screen)
        // 今日页「+ → 扫一扫」扫到洗衣机机身码（UjingScanHandoff）：同一条口径，
        // 原文交给 U净 页自己识别，本窗口首帧消费一次
        val ujingScanRaw = UjingScanHandoff.consume(request.screen)
        enableEdgeToEdge()
        // 预测性返回（DESIGN §3.1）：34+ 由本窗口声明过渡，系统才会把手势进度交给它
        enablePredictiveBackTransitions()
        setContent {
            JuwRoot {
                SubpageContent(
                    screen = request.screen,
                    onBack = { finish() },
                    focusItemId = request.focusItemId,
                    courseName = request.courseName,
                    itemId = request.itemId,
                    startEditing = request.startEditing,
                    autoStart = autoStart,
                    ujingScanRaw = ujingScanRaw,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 本页成为「离开 App 时看到的页」，见 SubpageStack 的类 KDoc
        SubpageStack.onWindowResumed()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // 从外部应用（微信 / 浏览器 / 系统设置…）回来时，要等返回过渡放完、窗口重新
        // 拿到焦点，才能还原推入/滑出覆盖——更早还原会把拉起前换上的静止过渡又改回去，
        // 被抑制的右推入就重回返回动画（见 WindowTransitions.startActivityOutsideApp）。
        // 平时焦点恢复（系统弹框关掉、上层二级页关闭）只是重复声明同样的覆盖，无副作用。
        if (hasFocus) enablePredictiveBackTransitions()
    }

    override fun onDestroy() {
        SubpageStack.onWindowDestroyed(request)
        super.onDestroy()
    }

    @Composable
    private fun SubpageContent(
        screen: SubpageScreen,
        onBack: () -> Unit,
        focusItemId: String? = null,
        courseName: String? = null,
        itemId: Long = 0L,
        /** 笔记/作业详情：true = 直接进编辑态（列表铅笔入口）。 */
        startEditing: Boolean = false,
        /** 小组件「去开水」直达：仅 WATER / QZXY 两个页面响应，见 [WaterAutoStart]。 */
        autoStart: Boolean = false,
        /** 今日页扫到的洗衣机机身码原文：仅 UJING 页响应，见 [UjingScanHandoff]。 */
        ujingScanRaw: String? = null,
    ) {
        when (screen) {
            SubpageScreen.TIMETABLE_MANAGE -> TimetableManageScreen(onBack = onBack)
            SubpageScreen.DATA_SETTINGS -> DataSettingsScreen(
                onBack = onBack,
                // ViewModel 由宿主创建（design/ios-port.md §11.2 ①）
                viewModel = viewModel(factory = MeViewModel.Factory(Graph.repository(this))),
            )
            SubpageScreen.COURSE_TWEAK -> CourseTweakScreen(onBack = onBack)
            SubpageScreen.WATER -> WaterScreen(onBack = onBack, autoStart = autoStart)
            SubpageScreen.KVCX -> KvcxScreen(onBack = onBack)
            SubpageScreen.KVCX_ORDERS -> KvcxOrdersScreen(onBack = onBack)
            SubpageScreen.CAMPUS_CALENDAR -> CampusCalendarScreen(onBack = onBack)
            // 宿舍报修进度（DESIGN §4.49）：focusItemId = 学工记录 id；收件箱通知按下标定位
            SubpageScreen.DORM_REPAIR -> DormRepairScreen(
                onBack = onBack,
                focusItemId = focusItemId,
            )
            // 请假系统（2026-11-08）：三张学工表单的入口页，各自行里直接开 XgFormActivity
            SubpageScreen.LEAVE_HUB -> LeaveHubScreen(onBack = onBack)
            // U净 三页链（DESIGN §3.23）：主页（看板 / 扫码）→ 下单页（档位 / 投放）→ 历史账单。
            // 下单页与主页间的大对象交接不走 extra，走 `UjingDraftStore`（进程内草稿）。
            // iOS 移植 S21 起三页都在共享层：ViewModel 与平台动作由这里注入（§11.2 ①⑦）。
            SubpageScreen.UJING -> {
                val services = Services.current
                // 这三个在 Android 侧恒非空（`GraphServices` 全部转接 `Graph` 的现有单例）；
                // 可空是给 iOS 的（那边还没有凭证存储 / `KeyValuePrefs`，返回 null）。
                val ujingRepo = requireNotNull(services.ujingRepository()) {
                    "Android 侧 U净 仓库恒非空（GraphServices.ujingRepository）"
                }
                val ujingHouses = requireNotNull(services.ujingHouseStore()) {
                    "Android 侧 U净 收藏仓恒非空（GraphServices.ujingHouseStore）"
                }
                val ujingHistory = requireNotNull(services.ujingHistoryStore()) {
                    "Android 侧 U净 账单仓恒非空（GraphServices.ujingHistoryStore）"
                }
                // 支付缝：`PayTask` 只能在 Activity 上拉起，装配根只有 applicationContext，
                // 所以在组合期造一个（`UjingPay` 的 KDoc 写了为什么不进 AppServices）
                val ujingPay = remember(this@SubpageActivity) {
                    ujingAlipayPay(this@SubpageActivity)
                }
                // `viewModel {}` 而不是 `remember`：本页跑在独立 Activity 里，旋转时保住 VM
                val ujingViewModel: UjingViewModel = viewModel {
                    UjingViewModel(
                        repo = ujingRepo,
                        houseStore = ujingHouses,
                        historyStore = ujingHistory,
                        draftStore = services.ujingDraftStore(),
                        prefs = services.displayPrefs(),
                        pay = ujingPay,
                        reminder = services.ujingReminder(),
                    )
                }
                // 扫码：复用内置取景窗口的原文模式（相机权限由取景窗口自己处理，与骑行页同一条通道）
                val scanLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.StartActivityForResult(),
                ) { result ->
                    val raw = result.data?.getStringExtra(Intents.Scan.RESULT)
                    if (result.resultCode == Activity.RESULT_OK && !raw.isNullOrBlank()) {
                        ujingViewModel.onQrScanned(raw)
                    }
                }
                UjingScreen(
                    viewModel = ujingViewModel,
                    onBack = onBack,
                    onOpenOrder = { SubpageActivity.start(this, SubpageScreen.UJING_ORDER) },
                    onOpenHistory = { SubpageActivity.start(this, SubpageScreen.UJING_HISTORY) },
                    scanRaw = ujingScanRaw,
                    brandIconRes = R.drawable.ic_brand_ujing,
                    onScan = { scanLauncher.launch(washerScanIntent(this)) },
                    onLocate = rememberUjingLocator(),
                    // 登录过期提示还留在 :app（`SessionExpiryStore` 的介质是 SharedPreferences），
                    // 以槽位注入；页面把自己那份 SnackbarHostState 交出来
                    sessionExpiredNotice = { snackbar ->
                        val ujingState by ujingViewModel.uiState.collectAsStateWithLifecycle()
                        SessionExpiredNotice(SessionService.Ujing, ujingState.loggedIn, snackbar)
                    },
                )
            }
            SubpageScreen.UJING_ORDER -> UjingOrderScreen(
                draftStore = Services.current.ujingDraftStore(),
                onBack = onBack,
            )
            SubpageScreen.UJING_HISTORY -> Services.current.ujingHistoryStore()?.let { store ->
                UjingHistoryScreen(store = store, onBack = onBack)
            }
            SubpageScreen.WENQU -> WenquScreen(
                onBack = onBack,
                onOpenSettings = { SubpageActivity.start(this, SubpageScreen.WENQU_SETTINGS) },
                // 工时行右侧「记录 ›」（2026-10-11）：与设置页那个入口同一去处
                onOpenRecords = { SubpageActivity.start(this, SubpageScreen.WENQU_RECORDS) },
                // 顶栏「蹲点」图标（2026-10-11）：到点自动检测、命中弹确认框
                onOpenGrab = { SubpageActivity.start(this, SubpageScreen.WENQU_GRAB) },
            )
            SubpageScreen.WENQU_RECORDS -> WenquRecordsScreen(onBack = onBack)
            SubpageScreen.WENQU_SETTINGS -> WenquSettingsScreen(
                onBack = onBack,
                onOpenRecords = { SubpageActivity.start(this, SubpageScreen.WENQU_RECORDS) },
            )
            SubpageScreen.WENQU_GRAB -> WenquGrabScreen(onBack = onBack)
            SubpageScreen.QZXY -> QzxyScreen(
                onBack = onBack,
                onOpenDiagnostics = { address ->
                    SubpageActivity.start(this, SubpageScreen.QZXY_DEBUG, focusItemId = address)
                },
                autoStart = autoStart,
            )
            SubpageScreen.QZXY_DEBUG -> QzxyDebugScreen(
                focusAddress = focusItemId,
                onBack = onBack,
            )
            // 趣智消费记录（2026-11-05）：从趣智页入口卡进来，页面自带 VM（按月拉 + 本地按时段筛）
            SubpageScreen.QZXY_BILLS -> QzxyBillScreen(onBack = onBack)
            SubpageScreen.WIDGET_SETTINGS -> WidgetSettingsScreen(onBack = onBack)
            SubpageScreen.CALENDAR_SETTINGS -> CalendarSettingsScreen(onBack = onBack)
            SubpageScreen.REMINDER_SETTINGS -> ReminderSettingsScreen(onBack = onBack)
            SubpageScreen.SHORTCUTS ->
                ShortcutSettingsScreen(onBack = onBack, focusItemId = focusItemId)
            // 成绩与考试四页已在 shared/commonMain（iOS 移植 S10），平台动作用
            // `rememberScoreScreenActions` / `rememberExamScreenActions` 补齐；
            // 回调里 null 的语义是「本平台没这项能力」，入口整行不渲染。
            SubpageScreen.SCORES -> {
                val actions = rememberScoreScreenActions()
                ScoreScreen(
                    onBack = onBack,
                    onImportScores = actions.openImport,
                    onOpenSettings = actions.openSettings,
                    onOpenTranscript = actions.openTranscript,
                )
            }
            SubpageScreen.SCORE_SETTINGS -> {
                val actions = rememberScoreScreenActions()
                ScoreSettingsScreen(
                    onBack = onBack,
                    onRequestNotifPermission = actions.requestNotifPermission,
                    onAlertSettingsChanged = actions.alertSettingsChanged,
                )
            }
            SubpageScreen.EXAMS -> {
                val actions = rememberExamScreenActions()
                ExamScreen(
                    onBack = onBack,
                    onOpenJwPage = actions.openJwPage,
                    onOpenSettings = actions.openSettings,
                )
            }
            SubpageScreen.EXAM_SETTINGS -> {
                val actions = rememberExamScreenActions()
                ExamSettingsScreen(
                    onBack = onBack,
                    onRequestNotifPermission = actions.requestNotifPermission,
                    onAlertSettingsChanged = actions.alertSettingsChanged,
                    onOpenImport = actions.openImport,
                )
            }
            // 教材 / 教学日历已搬进 shared/commonMain（iOS 移植 S18）：平台动作（浏览窗口 /
            // 导入窗口）由本侧给，共享层只认可空回调。
            SubpageScreen.TEXTBOOKS -> {
                val actions = rememberJwScreenPlatformActions()
                TextbookScreen(
                    onBack = onBack,
                    onOpenWebPage = actions.openWebPage,
                    onOpenJwImport = actions.openJwImport,
                )
            }
            // 课表清单已搬进 shared/commonMain（iOS 移植 S18）：平台动作（浏览窗口 / 导入窗口）
            // 由本侧给，共享层只认可空回调。
            SubpageScreen.THEORY_SCHEDULE -> {
                val actions = rememberJwScreenPlatformActions()
                JwScheduleListScreen(
                    kind = CourseKind.Theory,
                    onBack = onBack,
                    onOpenWebPage = actions.openWebPage,
                    onOpenJwImport = actions.openJwImport,
                )
            }
            SubpageScreen.LAB_SCHEDULE -> {
                val actions = rememberJwScreenPlatformActions()
                JwScheduleListScreen(
                    kind = CourseKind.Lab,
                    onBack = onBack,
                    onOpenWebPage = actions.openWebPage,
                    onOpenJwImport = actions.openJwImport,
                )
            }
            // 学籍信息已搬进 shared/commonMain（iOS 移植 S18）：只有浏览窗口一件平台事。
            SubpageScreen.STUDENT_INFO -> {
                val actions = rememberJwScreenPlatformActions()
                StudentInfoScreen(onBack = onBack, onOpenWebPage = actions.openWebPage)
            }
            SubpageScreen.TEACHING_CALENDAR -> {
                val actions = rememberJwScreenPlatformActions()
                TeachingCalendarScreen(
                    onBack = onBack,
                    onOpenWebPage = actions.openWebPage,
                    onOpenJwImport = actions.openJwImport,
                )
            }
            SubpageScreen.SCHOLAR -> ScholarScreen(onBack = onBack)
            SubpageScreen.CURRICULUM -> CurriculumScreen(onBack = onBack)
            SubpageScreen.INTERNSHIP_BASES -> InternshipBasesScreen(onBack = onBack)
            // 选课四页已在 shared/commonMain（iOS 移植 S13），平台动作用
            // `rememberSelectionScreenActions` 补齐；`client` 是选课接口（真身仍在 :app）。
            SubpageScreen.SELECTIONS -> {
                val actions = rememberSelectionScreenActions()
                SelectionScreen(
                    onBack = onBack,
                    onOpenCenter = actions.openCenter,
                    onOpenRecords = actions.openRecords,
                    onOpenWishes = actions.openWishes,
                    onOpenJwWeb = actions.openJwWeb,
                    onOpenJwPage = actions.openJwPage,
                    onAlertsChanged = actions.alertsChanged,
                )
            }
            // focusItemId 对 SELECTION_CENTER 复用为「直达轮次 id」（选课页点轮次行）
            SubpageScreen.SELECTION_CENTER ->
                SelectionCenterScreen(
                    onBack = onBack,
                    focusRoundId = focusItemId,
                    client = rememberSelectionCenterClient(),
                )
            SubpageScreen.SELECTION_WISHES -> {
                val actions = rememberSelectionScreenActions()
                SelectionWishesScreen(
                    onBack = onBack,
                    onOpenCenter = actions.openCenter.let { open -> { open(null) } },
                    client = rememberSelectionCenterClient(),
                )
            }
            SubpageScreen.SELECTION_RECORDS -> SelectionRecordsScreen(onBack = onBack)
            SubpageScreen.TRANSCRIPTS -> TranscriptHistoryScreen(onBack = onBack)
            // focusItemId 对 RIDE 复用为「进页即定位的车号」（2026-09-29）：识别 / 深链
            // 带车号进来，第一笔查询找到就高亮定位（不轮询）
            SubpageScreen.RIDE -> RideScreen(onBack = onBack, initialFocusCarNum = focusItemId)
            SubpageScreen.CAMPUS_CARD_SETTINGS -> CampusCardSettingsScreen(
                onBack = onBack,
                onOpenStatement = { SubpageActivity.start(this, SubpageScreen.CAMPUS_STATEMENT) },
            )
            SubpageScreen.JW_ACCOUNT -> JwAccountScreen(onBack = onBack)
            SubpageScreen.TIMETABLE_HUB -> TimetableHubScreen(
                onBack = onBack,
                // ViewModel 由宿主创建（design/ios-port.md §11.2 ①）
                viewModel = viewModel(factory = MeViewModel.Factory(Graph.repository(this))),
            )
            SubpageScreen.SCHEDULE_CHANGES -> ScheduleChangesScreen(
                onBack = onBack,
                onOpenHistory = { SubpageActivity.start(this, SubpageScreen.AI_HISTORY) },
            )
            SubpageScreen.AI_SCHEDULE -> AiChatScreen(
                onBack = onBack,
                onOpenSettings = { SubpageActivity.start(this, SubpageScreen.AI_SETTINGS) },
                onOpenHistory = { SubpageActivity.start(this, SubpageScreen.AI_HISTORY) },
            )
            SubpageScreen.AI_SETTINGS -> AiSettingsScreen(onBack = onBack)
            SubpageScreen.AI_HISTORY -> AiHistoryScreen(onBack = onBack)
            // 收件箱（S19 搬进共享层）：route 解析留在页内，跳页这一件平台事由宿主给。
            // 参数是「已经解析好的 SubpageScreen」，认不出 route 的消息在页内就被挡掉了。
            SubpageScreen.INBOX -> InboxScreen(
                onBack = onBack,
                onOpenSubpage = { target, focus ->
                    SubpageActivity.start(this, target, focusItemId = focus)
                },
            )
            // focusItemId 对 SCHEDULE_SHARE 复用为「进页时预览哪一周」（课表页分享弹层第 4 行）
            SubpageScreen.SCHEDULE_SHARE ->
                ScheduleShareScreen(
                    onBack = onBack,
                    initialWeek = focusItemId?.toIntOrNull(),
                )
            SubpageScreen.APPEARANCE_SETTINGS -> AppearanceSettingsScreen(
                onBack = onBack,
                // ViewModel 由宿主创建（design/ios-port.md §11.2 ①）
                viewModel = viewModel(factory = MeViewModel.Factory(Graph.repository(this))),
            )
            // 天气（DESIGN §3.29）：详情与设置各自独立窗口，与其它二级页同规格。
            // 详情页（S19 搬进共享层）的 ViewModel 由宿主建：`viewModel { }` 保留配置变更
            // （旋转）不重建，仓库从共享层的 Services 取（与今日页顶栏提示同一份单例）。
            SubpageScreen.WEATHER -> WeatherScreen(
                viewModel = viewModel { WeatherViewModel(Services.current.weatherRepository()) },
                onBack = onBack,
            )
            SubpageScreen.WEATHER_SETTINGS -> WeatherSettingsScreen(
                onBack = onBack,
                onOpenWeather = { SubpageActivity.start(this, SubpageScreen.WEATHER) },
            )
            // 百宝箱（DESIGN §4.47）：工具列表页 → 各工具独立窗口，与其它二级页同规格。
            // 菜鸟批量查 2026-10-13 移出百宝箱（入口改在侧栏「取快递」弹窗），
            // 但 TOOLBOX_CAINIAO 路由保留（仍从那里进）
            SubpageScreen.TOOLBOX -> ToolboxScreen(
                onBack = onBack,
                onOpenWatermark = {
                    SubpageActivity.start(this, SubpageScreen.TOOLBOX_WATERMARK)
                },
                onOpenStitch = {
                    SubpageActivity.start(this, SubpageScreen.TOOLBOX_STITCH)
                },
            )
            SubpageScreen.TOOLBOX_WATERMARK -> WatermarkScreen(onBack = onBack)
            SubpageScreen.TOOLBOX_STITCH -> StitchScreen(onBack = onBack)
            SubpageScreen.TOOLBOX_CAINIAO -> CainiaoQueryScreen(onBack = onBack)
            SubpageScreen.QUIET_HOURS -> QuietHoursScreen(onBack = onBack)
            SubpageScreen.ACCOUNTS -> AccountsHubScreen(
                onBack = onBack,
                onOpenJw = { SubpageActivity.start(this, SubpageScreen.JW_ACCOUNT) },
                onOpenYkt = {
                    SubpageActivity.start(this, SubpageScreen.CAMPUS_CARD_SETTINGS)
                },
                onOpenQiekj = { SubpageActivity.start(this, SubpageScreen.WATER) },
                onOpenQzxy = { SubpageActivity.start(this, SubpageScreen.QZXY) },
                onOpenUjing = { SubpageActivity.start(this, SubpageScreen.UJING) },
                onOpenKvcx = { SubpageActivity.start(this, SubpageScreen.KVCX) },
                // 小程序方式下快趣没有账号可登：点行进骑行页切使用方式
                onOpenRide = { SubpageActivity.start(this, SubpageScreen.RIDE) },
                onOpenWenqu = { SubpageActivity.start(this, SubpageScreen.WENQU) },
                onOpenAiSettings = { SubpageActivity.start(this, SubpageScreen.AI_SETTINGS) },
            )
            SubpageScreen.PERMISSIONS_STORAGE -> PermissionsStorageScreen(onBack = onBack)
            SubpageScreen.ABOUT_UPDATE -> AboutUpdateScreen(onBack = onBack)
            SubpageScreen.TERMS_DECLARATIONS -> TermsDeclarationsScreen(onBack = onBack)
            SubpageScreen.SUPPORT_DEV -> SupportDevScreen(onBack = onBack)
            SubpageScreen.PAY_CODE -> PayCodeScreen(
                onBack = onBack,
                onOpenStatement = { SubpageActivity.start(this, SubpageScreen.CAMPUS_STATEMENT) },
            )
            // 缴费账单（iOS 移植 S20 搬进共享层）：VM 由宿主建（共享层没有 `viewModel()`），
            // 凭证存储 / VPN 探测 / 拉起浏览器都是 Android 侧能力，从这里注入。
            // 用 `viewModel {}` 而不是 `remember`：本页跑在独立 Activity 里，旋转时保住 VM。
            SubpageScreen.POWER_BILL -> {
                val services = Services.current
                val billViewModel: PowerBillViewModel = viewModel {
                    PowerBillViewModel(
                        repo = services.powerRepository(),
                        credentialStore = services.yktCredentialStore(),
                        readingStore = services.powerReadingStore(),
                        isVpnActive = { JwVpnDetector.isVpnActive(this@SubpageActivity) },
                    )
                }
                PowerBillScreen(
                    viewModel = billViewModel,
                    onBack = onBack,
                    onOpenSettings = { SubpageActivity.start(this, SubpageScreen.CAMPUS_CARD_SETTINGS) },
                    // 打不开时失败原因进日志，屏上由页面给一句通用提示（页面不该知道 Intent 细节）
                    onOpenPlatformUrl = { url ->
                        openExternal(this, url) { message, _ -> Logger.w("PowerBill", message) }
                    },
                )
            }
            // 消费流水（S20）：同样由宿主建 VM；新消费入收件箱是 Android 侧的落点
            // （`InboxRepository` + `SubpageScreen` 路由），以回调注入，iOS 侧传 null 即不留痕。
            SubpageScreen.CAMPUS_STATEMENT -> {
                val services = Services.current
                val statementViewModel: StatementViewModel = viewModel {
                    StatementViewModel(
                        repo = services.yktRepository(),
                        credentialStore = services.yktCredentialStore(),
                        db = services.database(),
                        postInbox = { row -> postYktPaymentInbox(this@SubpageActivity, row) },
                    )
                }
                StatementScreen(
                    viewModel = statementViewModel,
                    onBack = onBack,
                    onOpenSettings = { SubpageActivity.start(this, SubpageScreen.CAMPUS_CARD_SETTINGS) },
                )
            }
            // 笔记·课件（DESIGN §3.11）：课程库 → 课程列表 → 详情/编辑
            // 库与课程列表已在 shared/commonMain（iOS 移植 S13）；附件图能力由
            // `rememberAttachmentImageUi` 注入（缩略图解码 + 全屏查看器仍在 :app）。
            SubpageScreen.NOTES -> NoteLibraryScreen(
                onBack = onBack,
                onOpenNote = { name, id, startEditing ->
                    SubpageActivity.start(
                        this,
                        SubpageScreen.NOTE_DETAIL,
                        courseName = name,
                        itemId = id,
                        startEditing = startEditing,
                    )
                },
                images = rememberAttachmentImageUi(),
            )
            SubpageScreen.NOTES_COURSE -> NoteCourseScreen(
                courseName = courseName.orEmpty(),
                onBack = onBack,
                onOpenNote = { id, startEditing ->
                    SubpageActivity.start(
                        this,
                        SubpageScreen.NOTE_DETAIL,
                        courseName = courseName,
                        itemId = id,
                        startEditing = startEditing,
                    )
                },
                images = rememberAttachmentImageUi(),
            )
            SubpageScreen.NOTE_DETAIL -> NoteDetailScreen(
                courseName = courseName.orEmpty(),
                noteId = itemId,
                startEditing = startEditing,
                onBack = onBack,
            )
            // 作业：课程库 → 课程列表 → 详情/编辑 + 作业中心
            // 库 / 课程列表 / 作业中心已在 shared/commonMain（iOS 移植 S13）；附件图、Markdown
            // 渲染、开链接、截止提醒排程由这里注入（四件都在 :app）。
            SubpageScreen.HOMEWORK -> HomeworkLibraryScreen(
                onBack = onBack,
                onOpenHomework = { name, id, startEditing ->
                    SubpageActivity.start(
                        this,
                        SubpageScreen.HOMEWORK_DETAIL,
                        courseName = name,
                        itemId = id,
                        startEditing = startEditing,
                    )
                },
                images = rememberAttachmentImageUi(),
                markdown = rememberMarkdownUi(),
                onOpenLink = { url -> openLink(this, url) },
                onHomeworkChanged = { ClassReminder.enqueueCheck(this) },
            )
            SubpageScreen.HOMEWORK_COURSE -> HomeworkCourseScreen(
                courseName = courseName.orEmpty(),
                onBack = onBack,
                onOpenHomework = { id, startEditing ->
                    SubpageActivity.start(
                        this,
                        SubpageScreen.HOMEWORK_DETAIL,
                        courseName = courseName,
                        itemId = id,
                        startEditing = startEditing,
                    )
                },
                images = rememberAttachmentImageUi(),
                markdown = rememberMarkdownUi(),
                onOpenLink = { url -> openLink(this, url) },
                onHomeworkChanged = { ClassReminder.enqueueCheck(this) },
            )
            SubpageScreen.HOMEWORK_DETAIL -> HomeworkDetailScreen(
                courseName = courseName.orEmpty(),
                homeworkId = itemId,
                startEditing = startEditing,
                onBack = onBack,
            )
            SubpageScreen.HOMEWORK_TODO -> HomeworkTodoScreen(
                onBack = onBack,
                onOpenHomework = { name, id, startEditing ->
                    SubpageActivity.start(
                        this,
                        SubpageScreen.HOMEWORK_DETAIL,
                        courseName = name,
                        itemId = id,
                        startEditing = startEditing,
                    )
                },
                onOpenLibrary = { SubpageActivity.start(this, SubpageScreen.HOMEWORK) },
                images = rememberAttachmentImageUi(),
                markdown = rememberMarkdownUi(),
                onOpenLink = { url -> openLink(this, url) },
                onHomeworkChanged = { ClassReminder.enqueueCheck(this) },
            )
        }
    }

    override fun finish() {
        // 用户主动关窗（页内返回 / 系统返回键 / 扫码完成自动退出）才从记录里摘掉。
        // clearTop 与系统回收不走这里，记录必须留着，否则恢复不了。
        SubpageStack.onWindowFinished(request)
        super.finish()
        // 顶层窗口向右滑出；露出的主窗口原地不动（stay_still / ≤33 的 legacy 路径传 0）。
        // 34+ 由 enablePredictiveBackTransitions 声明，这里不碰旧 API
        applySubpageCloseTransition(this)
    }

    companion object {
        /**
         * [focusItemId] 的复用面（各页自解释，解析代码在各 Screen）：
         * SHORTCUTS = 打开后直接展开该条目的编辑弹层（Snackbar「去设置」的就近修正闭环）；
         * RIDE = 进页即定位的车号（2026-09-29）；SELECTION_CENTER = 直达轮次 id（2026-10-01）。
         *
         * [courseName] / [itemId] 只对笔记·作业的二级页生效（DESIGN §3.11）：
         * 前者是归属课程名（课程库 → 课程列表的必带参数），后者是条目 id（0 = 新建）。
         */
        fun start(
            context: Context,
            screen: SubpageScreen,
            focusItemId: String? = null,
            courseName: String? = null,
            itemId: Long = 0L,
            startEditing: Boolean = false,
        ) {
            openSubpage(
                context,
                SubpageRequest(
                    screen = screen,
                    focusItemId = focusItemId,
                    courseName = courseName,
                    itemId = itemId,
                    startEditing = startEditing,
                ),
            )
        }
    }
}

// ── U净 的两条 Android 平台缝（iOS 移植 S21；共享层的 `platform/UjingPlatform.kt` 定义了契约） ──

/**
 * U净 的支付宝支付缝：把服务端签发的 `orderInfo` 交给官方 SDK 并等结果。
 *
 * 为什么在宿主里造而不是 `GraphServices`：`PayTask` 只能在 `Activity` 上拉起，而装配根
 * 只持有 `applicationContext`（见 `GraphServices` 的 KDoc）。本页跑在独立 Activity 里，
 * 这里现造一个、随窗口销毁，不留长命引用。
 *
 * `PayTask.payV2` 是**阻塞调用**（内部建网络连接），所以切到 `Dispatchers.IO`——
 * 共享层的 `UjingViewModel` 不再自己指定调度器（那边原来写的 `Dispatchers.IO` 是 JVM 专有）。
 */
private fun ujingAlipayPay(activity: Activity): UjingPay = object : UjingPay {
    override suspend fun pay(orderInfo: String): UjingPayResult =
        withContext(Dispatchers.IO) {
            val (outcome, memo) = UjingAlipay.pay(activity, orderInfo)
            UjingPayResult(
                outcome = when (outcome) {
                    UjingAlipay.Outcome.Success -> UjingPayOutcome.Success
                    UjingAlipay.Outcome.Pending -> UjingPayOutcome.Pending
                    UjingAlipay.Outcome.Canceled -> UjingPayOutcome.Canceled
                    UjingAlipay.Outcome.Failed -> UjingPayOutcome.Failed
                },
                memo = memo,
            )
        }
}

/**
 * U净「添加洗衣房」的定位缝。
 *
 * 权限申请必须走 `ActivityResultLauncher`（只能在组合期注册），所以只能在宿主侧造；
 * 拿到授权后复用单车地图那条定位链路（`BikeLocator`：`LocationManager` 一次读数、
 * 8 秒超时、WGS84 → GCJ-02），失败原因原样交给页面显示。
 *
 * 权限请求与定位调用之间用 [CompletableDeferred] 搭桥：launcher 的回调不在
 * 挂起调用里，只能这样把结果交回去。
 */
@Composable
private fun rememberUjingLocator(): suspend () -> UjingLocateOutcome {
    val context = LocalContext.current
    // 一次权限请求的等待者；launcher 回调到达时唤醒它
    var pendingGrant by remember { mutableStateOf<CompletableDeferred<Boolean>?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        pendingGrant?.complete(result.values.any { it })
        pendingGrant = null
    }
    return remember(context, permissionLauncher) {
        {
            val granted = if (BikeLocator.hasPermission(context)) {
                true
            } else {
                val deferred = CompletableDeferred<Boolean>()
                pendingGrant = deferred
                permissionLauncher.launch(AppPermissions.location.toTypedArray())
                deferred.await()
            }
            if (!granted) {
                UjingLocateOutcome.Failed("没有定位权限，无法获取附近洗衣房")
            } else {
                when (val fix = BikeLocator.currentLocation(context)) {
                    is LocateResult.Ok -> UjingLocateOutcome.Ok(fix.lat, fix.lng)
                    is LocateResult.Failed -> UjingLocateOutcome.Failed(fix.message)
                }
            }
        }
    }
}
