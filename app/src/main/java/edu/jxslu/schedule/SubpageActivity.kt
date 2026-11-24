package edu.jxslu.schedule

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import edu.jxslu.schedule.domain.CourseKind
import edu.jxslu.schedule.platform.Services
import edu.jxslu.schedule.ui.me.CalendarSettingsScreen
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
import edu.jxslu.schedule.ui.common.rememberAttachmentImageUi
import edu.jxslu.schedule.ui.common.rememberMarkdownUi
import edu.jxslu.schedule.ui.curriculum.CurriculumScreen
import edu.jxslu.schedule.ui.ebike.KvcxOrdersScreen
import edu.jxslu.schedule.ui.ebike.KvcxScreen
import edu.jxslu.schedule.ui.ebike.RideScreen
import edu.jxslu.schedule.ui.exam.ExamScreen
import edu.jxslu.schedule.ui.exam.rememberExamScreenActions
import edu.jxslu.schedule.ui.exam.ExamSettingsScreen
import edu.jxslu.schedule.ui.life.PowerBillScreen
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
import kotlinx.coroutines.launch


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
            SubpageScreen.DATA_SETTINGS -> DataSettingsScreen(onBack = onBack)
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
            // 下单页与主页间的大对象交接不走 extra，走 Graph 的 UjingDraftStore（进程内草稿）
            SubpageScreen.UJING -> UjingScreen(
                onBack = onBack,
                onOpenOrder = { SubpageActivity.start(this, SubpageScreen.UJING_ORDER) },
                onOpenHistory = { SubpageActivity.start(this, SubpageScreen.UJING_HISTORY) },
                scanRaw = ujingScanRaw,
            )
            SubpageScreen.UJING_ORDER -> UjingOrderScreen(onBack = onBack)
            SubpageScreen.UJING_HISTORY -> UjingHistoryScreen(onBack = onBack)
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
            SubpageScreen.TEXTBOOKS -> TextbookScreen(onBack = onBack)
            SubpageScreen.THEORY_SCHEDULE ->
                JwScheduleListScreen(kind = CourseKind.Theory, onBack = onBack)
            SubpageScreen.LAB_SCHEDULE ->
                JwScheduleListScreen(kind = CourseKind.Lab, onBack = onBack)
            SubpageScreen.STUDENT_INFO -> StudentInfoScreen(onBack = onBack)
            SubpageScreen.TEACHING_CALENDAR -> TeachingCalendarScreen(onBack = onBack)
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
            SubpageScreen.TIMETABLE_HUB -> TimetableHubScreen(onBack = onBack)
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
            SubpageScreen.CAMPUS_STATEMENT -> StatementScreen(
                onBack = onBack,
                onOpenSettings = { SubpageActivity.start(this, SubpageScreen.CAMPUS_CARD_SETTINGS) },
            )
            SubpageScreen.POWER_BILL -> PowerBillScreen(
                onBack = onBack,
                onOpenSettings = { SubpageActivity.start(this, SubpageScreen.CAMPUS_CARD_SETTINGS) },
            )
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
