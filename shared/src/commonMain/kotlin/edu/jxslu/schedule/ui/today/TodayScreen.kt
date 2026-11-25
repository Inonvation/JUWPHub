package edu.jxslu.schedule.ui.today

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
// M3 1.3.0 的下拉刷新不在 material3 根包，而在 pulltorefresh 子包
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import edu.jxslu.schedule.SubpageScreen
import edu.jxslu.schedule.domain.AppClock
import edu.jxslu.schedule.domain.Course
import edu.jxslu.schedule.domain.DormRepairRecord
import edu.jxslu.schedule.domain.DormRepairRules
import edu.jxslu.schedule.domain.GuessPick
import edu.jxslu.schedule.domain.GuessTarget
import edu.jxslu.schedule.domain.PendingHomework
import edu.jxslu.schedule.domain.QuickScanTarget
import edu.jxslu.schedule.domain.TimeSlot
import edu.jxslu.schedule.domain.TodayState
import edu.jxslu.schedule.domain.TodayTip
import edu.jxslu.schedule.domain.clockOf
import edu.jxslu.schedule.domain.dayLabel
import edu.jxslu.schedule.domain.placeTeacherLine
import edu.jxslu.schedule.domain.quickScanTargetOf
import edu.jxslu.schedule.domain.remarkPreview
import edu.jxslu.schedule.domain.sectionRange
import edu.jxslu.schedule.domain.sectionShortLabel
import edu.jxslu.schedule.domain.textbookTitlesLine
import edu.jxslu.schedule.platform.Services
import edu.jxslu.schedule.platform.collectAsStateWithLifecycle
import edu.jxslu.schedule.ui.common.AppCardDefaults
import edu.jxslu.schedule.ui.common.AppCardRow
import edu.jxslu.schedule.ui.common.AppConfirmDialog
import edu.jxslu.schedule.ui.common.AppNoticeVisuals
import edu.jxslu.schedule.ui.common.AppSnackbarHost
import edu.jxslu.schedule.ui.common.CourseDetailSheet
import edu.jxslu.schedule.ui.common.CourseEditSheet
import edu.jxslu.schedule.ui.common.EmptyHint
import edu.jxslu.schedule.ui.common.LoadingHint
import edu.jxslu.schedule.ui.common.LocalBottomBarClearance
import edu.jxslu.schedule.ui.common.NoticeTone
import edu.jxslu.schedule.ui.common.ResumedTickerEffect
import edu.jxslu.schedule.ui.common.SectionHeader
import edu.jxslu.schedule.ui.common.courseColor
import edu.jxslu.schedule.ui.common.pinnedStatusBars
import edu.jxslu.schedule.ui.common.postNotice
import edu.jxslu.schedule.ui.common.rememberAppHaptics
import edu.jxslu.schedule.ui.common.rememberRefreshHold
import edu.jxslu.schedule.ui.common.showNotice
import edu.jxslu.schedule.ui.homework.HomeworkTodayCard
import edu.jxslu.schedule.ui.icon.AppIcons
import edu.jxslu.schedule.ui.inbox.inboxKindIcon
import edu.jxslu.schedule.ui.jwvw.JwImportOutcomeEffect
import edu.jxslu.schedule.ui.theme.Motion
import edu.jxslu.schedule.ui.ujing.UjingEntrySheet
import edu.jxslu.schedule.ui.water.WaterEntrySheet
import edu.jxslu.schedule.ui.water.WaterEvent
import edu.jxslu.schedule.ui.water.WaterUiState
import edu.jxslu.schedule.ui.water.WaterViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * 今日课表。
 *
 * 页面骨架：**顶部焦点卡（正在上课 / 下一节）+ 一列时间轴课程行 + 贴底固定区**。
 * - 焦点课只出现一次：焦点卡拿走第一门课，[TodayState.listCourses] 已把该课剔除，
 *   旧版「状态卡 + 列表首项」显示同一节课的问题不复存在。
 * - 「下一节」有 60 分钟准入窗口（[TodayState.next]）：更远的课不冒充「下一节」，
 *   页面直接从列表开始 + 一行「今天的课 XX:XX 开始」轻提示。
 * - 焦点卡与时间轴行同为**满宽卡片**（同外边距/内边距/圆角，缘对缘对齐）；
 *   区分靠底色（主色 vs 课程色）与焦点卡状态行/进度条。节次编号只在焦点卡出现。
 * - 已结束的课不显示（保持既有取舍）；今天没有待上课程时才轮到「明天」上桌。
 * - 点课程（焦点卡/课程行）弹**只读详情**（[CourseDetailSheet]，与课表页同口径），
 *   编辑/删除是详情里的二级动作——直跳编辑器易误触。
 *
 * 底部固定区（2026-10-20）：**整块 dock 已收进左侧栏**（取快递 / U净 /
 * 快趣出行 / 胖乖 / 趣智 / 问渠 / 笔记 / 作业 / 百宝箱 / 天气）。
 * 今日页只留课程与提示，课程区拿回全部高度；列表最后一项自己让开悬浮胶囊
 * （[LocalBottomBarClearance]）。旧的 dock 组件与 `todayDockExpanded` 偏好一并删除。
 *
 * 收件箱（2026-10-21 收口）：展示入口在页面列表的收件箱卡（[InboxTodayCard]，
 * 只列今天 + 「查看更多」进收件箱页）。顶栏快捷入口已删（2026-10-22）：扫一扫 / 付款码 /
 * 百宝箱都在左侧栏，顶栏不再挂「+」。
 * 宿舍报修状态（在途优先 / 3 天内完成）是卡内置顶行，原独立报修卡同轮删除。
 *
 * ## iOS 移植 V1（2026-11-25）：搬进 `shared/commonMain` 时的槽位反转
 *
 * 这一页是**所有能力的汇合点**：原来直接 import 八个 ui 包，其中
 * `ui/ebike`（高德 SDK）、`ui/qzxy`（蓝牙）、`ui/reminder`（通知）、`ui/jwvw`（WebView）
 * 搬不动，`ui/qiekj` 的开水会话存储也还在 `:app`（`design/ios-port.md` §10.5）。
 * 所以这里把「渲染 + 状态」一起交给宿主，页面自己不再 import 它们：
 *
 * | 槽位 | 形状 | null 的含义 | 谁填 |
 * |---|---|---|---|
 * | [serviceCards] | [TodayServiceSlot] | 本平台没有这四项服务 → 整卡不渲染 | Android `TodayHost.kt` |
 * | [qzxyPanel] | `@Composable (onDismiss, onNotice) -> Unit` | 没有趣智开水面板 → 猜你想用退回整页 | Android `TodayHost.kt` |
 * | [brandIcon] | `@Composable (GuessTarget) -> Painter?` | 没有品牌位图 → 卡片只留功能名与箭头 | Android `GuessYouWantCardPlatform.kt` |
 * | [onScanCode] | `(onResult: (String?) -> Unit) -> Unit` | 没有扫码 → U净 弹窗的扫码钮点不动 | Android `TodayHost.kt` |
 * | [onOpenSubpage] | `(SubpageScreen, String?) -> Unit` | 没有二级页栈 → 收件箱点行只标已读 | 两端宿主 |
 * | [onRefreshDormRepair] | `() -> Unit` | 没有报修同步 → 下拉刷新不拉报修 | Android `MainActivity` |
 *
 * 开水面板不在此列：`WaterViewModel` 与 `WaterEntrySheet` 都已在共享层（S15 / T2）。
 * 四种服务的**事件流**（快趣 `notices`、趣智 `events`）也随卡片一起交给宿主，
 * 页面不再订阅——它们是那四个包的内部类型。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    /**
     * 今日页 ViewModel。**必填，宿主创建**（Android `viewModel { TodayViewModel(...) }`、
     * iOS `remember { TodayViewModel(...) }`），见 `design/ios-port.md` §11.2 ①。
     */
    viewModel: TodayViewModel,
    /** 顶栏标题（原 `R.string.tab_today`）；`R` 只有 Android 有，收成字符串参数。 */
    title: String = "今日",
    onOpenJwImport: () -> Unit = {},
    /** 左侧栏入口（2026-10-20）：顶栏汉堡；开合与动画在壳层 */
    onOpenDrawer: () -> Unit = {},
    /** 「尚未开学」空态的 CTA：跳课表设置（学期起止） */
    onOpenTimetableSettings: () -> Unit = {},
    onOpenWater: () -> Unit = {},
    /** 趣智校园开热水页（DESIGN §4.30）：与胖乖生活并排的半行卡，独立窗口 */
    onOpenQzxy: () -> Unit = {},
    /** 「猜你想用」推荐问渠：进入问渠 · 志愿服务页（2026-11-17，DESIGN §3.33） */
    onOpenWenqu: () -> Unit = {},
    /** 快趣出行 · 骑行页（DESIGN §3.9，SubpageActivity 独立窗口；地图 + 抽屉三态） */
    onOpenEbike: () -> Unit = {},
    /** 扫到洗衣机机身码：原文交给 U净 页识别（DESIGN §3.23；江水生活 U净 弹窗的扫码按钮） */
    onOpenUjingScan: (String) -> Unit = {},
    /** 江水生活 U净 弹窗的「查看洗衣房空闲情况」：U净 · 洗衣房页 */
    onOpenUjing: () -> Unit = {},
    /** 今日页宿舍报修卡：原生进度页（DESIGN §4.49） */
    onOpenDormRepair: () -> Unit = {},
    /** 收件箱卡「查看更多」：打开收件箱页（DESIGN §3.24，2026-10-21 起唯一入口在卡片上） */
    onOpenInbox: () -> Unit = {},
    /** 顶栏天气提示那一行（DESIGN §3.29）：天气句点它进天气详情页 */
    onOpenWeather: () -> Unit = {},
    /** 顶栏课表提示（南北楼 / 实验课）：课表句点它进课表页（2026-10-20） */
    onOpenWeek: () -> Unit = {},
    /** 作业中心（今日页作业卡入口，DESIGN §3.11） */
    onOpenHomeworkTodo: () -> Unit = {},
    /** 某课程的笔记·课件（课程详情弹窗入口，DESIGN §3.11）。null = 本平台没有笔记，按钮不渲染。 */
    onOpenCourseNotes: ((Course) -> Unit)? = null,
    /** 某课程的作业（课程详情弹窗入口，DESIGN §3.11）。null 的语义同上。 */
    onOpenCourseHomework: ((Course) -> Unit)? = null,
    /** 与开水页共享的 Activity 作用域实例；开水卡的解锁进度与登录态两页一致 */
    waterViewModel: WaterViewModel? = null,
    // ── 槽位（见类 KDoc 的表；null = 本平台没有这块能力）──
    /** 进行中服务卡（快趣 / 胖乖 / 趣智 / U净）。null = 本平台没有这四项服务，整卡不渲染。 */
    serviceCards: TodayServiceSlot? = null,
    /** 趣智校园开水面板。`onNotice` 是页面给的 Snackbar 出口（缺蓝牙权限时用）。 */
    qzxyPanel: (@Composable (onDismiss: () -> Unit, onNotice: (String) -> Unit) -> Unit)? = null,
    /** 「猜你想用」品牌位图。null = 没有这套素材，卡片只留功能名 + 副文案 + 箭头。 */
    brandIcon: (@Composable (GuessTarget) -> Painter?)? = null,
    /** 扫码取原文（洗衣机机身码）。null = 本平台没有扫码能力。 */
    onScanCode: ((onResult: (String?) -> Unit) -> Unit)? = null,
    /** 收件箱卡点消息行跳二级页。null = 本平台没有二级页栈，点行只标已读。 */
    onOpenSubpage: ((SubpageScreen, String?) -> Unit)? = null,
    /** 下拉刷新时顺带拉一次宿舍报修进度。null = 本平台没有报修同步链路。 */
    onRefreshDormRepair: (() -> Unit)? = null,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // 顶栏天气提示（DESIGN §3.29）：功能关着 / 取不到数据时是 null，顶栏不占位
    val weatherLine by viewModel.weatherLine.collectAsStateWithLifecycle()
    // 「猜你想用」（DESIGN §3.33/§4.51）：每个 30 分钟槽一次的推荐；null = 当前不出卡
    val guessPick by viewModel.guessPick.collectAsStateWithLifecycle()
    // 开水面板（WaterEntrySheet）的实时状态：弹窗打开时逐帧跟随流程态，
    // 不能用打开瞬间的快照（Working 计时不会动）
    val waterEntryState by waterViewModel?.uiState
        ?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(WaterUiState()) }
    // 页面根作用域**只读派生的布尔**，不读整份 uiState（2026-11-18）：
    // 那份状态里含着每秒自增的出水计时，根上读一次 = 出水期间整页每秒重组一遍，
    // 而根上真正要的只是「在刷新余额吗」「在用水吗」。派生布尔去重后，计时每秒都不惊动
    // 根作用域（`MainActivity` 里只订阅余额是同一套写法）。
    val waterRefreshing by remember(waterViewModel) {
        waterViewModel?.uiState
            ?.map { it.loadingBalance || it.loadingDevices }
            ?.distinctUntilChanged()
            ?: flowOf(false)
    }.collectAsStateWithLifecycle(initialValue = false)
    // 侧栏那份 Activity 作用域 VM 关掉了 init 自动取数（从课表/教务启动时不白拉）；
    // 今日页才是开水/趣智卡片的实际展示位，进页补一次。已加载过则不再重复。
    LaunchedEffect(waterViewModel) {
        val vm = waterViewModel ?: return@LaunchedEffect
        if (!vm.uiState.value.balanceLoaded) vm.refreshBalance()
        if (vm.uiState.value.devices.isEmpty()) vm.refreshDevices()
    }
    val homework by viewModel.homeworkPending.collectAsStateWithLifecycle()
    // 课程卡的教材名（DESIGN §4.31，2026-10-07）：当前课表学期下「课程名 → 书名列表」，
    // 挂在课名右边（2026-10-08 收口，不再另起一行）；单独订阅不掺课表状态（与 homework
    // 同纪律）；空映射 = 无教材数据，卡片不占位
    val textbookTitles by viewModel.textbookTitles.collectAsStateWithLifecycle()
    // 宿舍报修进度（DESIGN §4.49）：有在途记录优先，没有则显示 3 天内已完成的。
    // 2026-10-21 起渲染在收件箱卡内的报修状态行（原独立报修卡并入删除）。
    // 快照是本地 JSON（`DormRepairStore` 已在共享层），两端同源，所以页面自己订阅。
    val dormRepairStore = remember { Services.current.dormRepairStore() }
    val dormRepairSnapshot by dormRepairStore.snapshot.collectAsStateWithLifecycle()
    val dormRepair = DormRepairRules.cardRecord(
        snapshot = dormRepairSnapshot,
        now = AppClock.nowMillis(),
    )
    var showWaterEntrySheet by remember { mutableStateOf(false) }
    // 趣智校园的余额面板（DESIGN §3.18）：与开水面板同形态，入口也是卡片右侧的余额
    var showQzxyEntrySheet by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Course?>(null) }
    var editorOpen by remember { mutableStateOf(false) }
    var detailCourse by remember { mutableStateOf<Course?>(null) }
    var pendingDelete by remember { mutableStateOf<Course?>(null) }
    val snackbar = remember { SnackbarHostState() }

    val scope = rememberCoroutineScope()

    // 江水生活 U净 弹窗（2026-10-19 自生活页迁入）：打开弹窗时对一次服务端
    // （同生活页原口径，节流窗口内不重复请求）
    var showUjingSheet by remember { mutableStateOf(false) }
    // U净 仓库在 iOS 侧为 null（没有凭证存储与 KeyValuePrefs）。弹窗组件默认值会
    // `requireNotNull`，所以入口本身要先判一次：仓库不在就退回 U净 页，不弹。
    val ujingAvailable = remember { Services.current.ujingRepository() != null }

    // 单条结果提示（钉桌面失败等）：与快捷方式拉起失败共用同一条 Snackbar 队列，
    // 后到的消息自动排队，不会互相顶掉
    val showNotice: (String, NoticeTone) -> Unit = { message, tone ->
        scope.launch { snackbar.showNotice(AppNoticeVisuals(message, tone = tone)) }
    }

    // 「猜你想用」点击分发（2026-11-17，DESIGN §3.33）：胖乖 / 趣智 / U净 走现有入口弹窗，
    // 快趣 / 问渠 进二级页。弹层依赖的槽位缺失时退回整页——卡片不能点不动。
    val openGuess: (GuessPick) -> Unit = { pick ->
        when (pick.target) {
            GuessTarget.QIEKJ -> if (waterViewModel != null) {
                showWaterEntrySheet = true
            } else {
                onOpenWater()
            }

            GuessTarget.QZXY -> if (qzxyPanel != null) {
                showQzxyEntrySheet = true
            } else {
                onOpenQzxy()
            }

            GuessTarget.UJING -> if (ujingAvailable) showUjingSheet = true else onOpenUjing()
            GuessTarget.KVCX -> onOpenEbike()
            GuessTarget.WENQU -> onOpenWenqu()
        }
    }

    // 教务导入成功的气泡（DESIGN §3.3）：今日页空态的「从教务导入」也是入口，落点就是这一页
    JwImportOutcomeEffect(snackbar)

    // 「还剩 X 分钟」要跟着时间走；RESUMED 才 tick——退后台/灭屏不再每 30 秒唤醒重组
    ResumedTickerEffect(intervalMillis = 30_000L) { viewModel.refreshTick() }

    // 余额面板/开水页发起解锁与查询的结果事件得有人收——此前只有开水页收，
    // 今日页侧的超时/失效提示会全部静默丢弃（Channel 无人消费即无处可去）。
    // 今日页与开水二级页各持一份 WaterViewModel（两个 Activity，登录态/订单走仓库共享），
    // 所以两边各收自己那份的事件，不会重复消费。
    LaunchedEffect(waterViewModel) {
        waterViewModel?.events?.collect { event ->
            when (event) {
                // 事件流用 postNotice 单开协程发：收集协程不卡在「等这条提示收场」上，
                // 事件成串来时新的提示才能当场顶掉旧的（见 AppNotice.kt）
                is WaterEvent.Notice -> scope.postNotice(snackbar, event.text, event.tone)
            }
        }
    }

    // 四项服务的页面级副作用（快趣事件流 → Snackbar、RESUMED 静默查询、趣智用水对账、
    // 还车 / 结束免费计时两张确认弹窗）。**必须在页面根上无条件调用**：
    // 放在 `serviceCards.content` 里就只有卡片滚进视野时才跑，用户往下翻一屏就漏提示。
    serviceCards?.effects?.invoke(showNotice)

    // 江水生活 U净 弹窗的扫码按钮：三档判据与侧栏扫一扫同一套（domain/QuickScan），
    // 洗衣机机身码原文交给 U净 页识别；车身码 / 别家码就地提示，不跳页。
    val quickHaptics = rememberAppHaptics()
    // null = 本平台没有扫码能力：`UjingEntrySheet` 的扫码钮与那句提示整块不渲染
    // （2026-11-25 审计 P1；此前槽位为 null 时按钮还在，点了没反应）
    val startUjingScan: (() -> Unit)? = onScanCode?.let { scan ->
        {
            quickHaptics.tap()
            scan { raw ->
                when (val target = quickScanTargetOf(raw)) {
                    null -> Unit
                    is QuickScanTarget.Washer -> {
                        showUjingSheet = false
                        quickHaptics.tap()
                        onOpenUjingScan(target.raw)
                    }

                    is QuickScanTarget.Ebike ->
                        showNotice("这是快趣车身码，不是洗衣机机身码", NoticeTone.Warning)

                    is QuickScanTarget.Foreign -> showNotice(
                        "这是${target.label}的码，不是洗衣机机身码",
                        NoticeTone.Warning,
                    )
                }
            }
        }
    }

    // 撤销型反馈（DESIGN §3.3）：删除课程后给「撤销」
    val undoable by viewModel.undoable.collectAsStateWithLifecycle()
    LaunchedEffect(undoable) {
        undoable?.let { m ->
            val result = snackbar.showNotice(m.text, actionLabel = "撤销", duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) m.undo()
            viewModel.consumeUndoable()
        }
    }

    Scaffold(
        // 顶部 inset 由外层消费（防顶栏双倍空白）；底部导航栏 inset 已由外层底栏
        // 高度提供，内层 contentWindowInsets 归零防底部双倍空白。
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                // 顶部 inset 自取（DESIGN §4.22）：外层 JuwApp Scaffold 的 contentWindowInsets 已归零，
                // 不再垫状态栏高度——课表页要把背景图铺到状态栏，顶部就只能由各页自己让位。
                // 这里取 statusBars 后顶栏总高与改动前一致，不会出现双倍空白。
                // 用钉住版：今日页的快捷方式会拉起外部应用，过渡期间系统栏瞬时归零的
                // insets 不落地（2026-09-26 修「顶栏跳动」，见 pinnedStatusBars 的 KDoc）。
                windowInsets = pinnedStatusBars(),
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(
                            imageVector = AppIcons.Menu01,
                            contentDescription = "打开生活侧栏",
                        )
                    }
                },
                title = {
                    Column {
                        Text(title)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = buildString {
                                    // 日期用 M/d 短式（2026-10-20）：同一行还要放提示句，
                                    // 「10月20日」在 360dp 上会先被挤掉周次
                                    append("${state.date.monthNumber}/${state.date.dayOfMonth}")
                                    append(" 周${dayLabel(state.day)}")
                                    if (state.week > 0) append(" · 第 ${state.week} 周")
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                // 日期**恒为内容宽、不参与让位**（2026-10-20 反转了 2026-10-12 的口径）：
                                // 早先提示行先拿满整行、日期只分剩余宽度，结果是日期被压成「10月…」，
                                // 而 24 字的提示自己仍然截断——两头都没救。日期是定位信息，
                                // 提示是锦上添花；现在提示拿剩余宽度、超了省略，日期始终可读。
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            // 天气 / 课表提示（DESIGN §3.29）：**接在次标题同一行的后面**，
                            // emoji + 一句话（18:00 前讲今天、之后讲明天）。
                            // 天气句点它进天气详情页，课表句进课表页（2026-10-20 按 kind 分流）。
                            //
                            // 为什么不做成顶栏里的第三行：M3 的 TopAppBar 高度是固定的 64dp，
                            // 标题区塞三行、用户又关掉「固定字体大小」时会被裁掉底边
                            // （现有标题两行合计 44sp，加上第三行 16sp 已到 60sp，没有余量）。
                            // 同一行渲染则任何字号下都只是省略号收尾，不会裁字。
                            //
                            // 2026-10-12 加动画：轮换时**交叉淡入淡出**（不硬切）；首次出现
                            // （打开软件 / 数据刚到）额外加一点上浮，避免突然蹦出来。
                            // 空态（功能关着 / 取不到数据）渲染零尺寸占位，不占位也不闪。
                            AnimatedContent(
                                targetState = weatherLine,
                                transitionSpec = {
                                    if (initialState == null) {
                                        // 入场：淡入 + 轻微上浮
                                        (fadeIn(Motion.page()) +
                                            slideInVertically(Motion.page()) { it / 3 }) togetherWith
                                            fadeOut(Motion.state())
                                    } else {
                                        // 轮换：交叉淡入淡出
                                        fadeIn(Motion.page()) togetherWith fadeOut(Motion.page())
                                    }
                                },
                                contentAlignment = Alignment.CenterStart,
                                // 只拿日期之外剩下的宽度（日期先量），超了省略
                                modifier = Modifier.weight(1f),
                                label = "todayWeatherTip",
                            ) { line ->
                                if (line == null) {
                                    // 首帧 / 无数据：零尺寸占位
                                    Box(Modifier)
                                } else {
                                    Text(
                                        text = line.text,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .clickable {
                                                quickHaptics.tap()
                                                when (line.kind) {
                                                    TodayTip.Kind.Weather -> onOpenWeather()
                                                    TodayTip.Kind.Schedule -> onOpenWeek()
                                                }
                                            }
                                            // 竖向 4dp 是把命中区从一行字（约 14dp）撑到约 22dp，
                                            // 标题区三行合计 62dp 仍在 TopAppBar 的 64dp 之内；
                                            // 再加大就会顶出顶栏被裁，别动这个数
                                            .padding(start = 6.dp, top = 4.dp, bottom = 4.dp),
                                    )
                                }
                            }
                        }
                    }
                },
                // 顶栏不再挂快捷入口（2026-10-22 用户口径）：扫一扫 / 付款码 / 百宝箱
                // 在左侧栏都有，顶栏那枚「+」是重复入口；收件箱入口在页面上的收件箱卡。
            )
        },
        snackbarHost = { AppSnackbarHost(snackbar) },
    ) { padding ->
        // 下拉刷新（2026-09-23）：并行刷新胖乖余额/设备 + 今日时间状态。
        // 包住三态内容：空态（假期）也要能刷胖乖余额。
        // （2026-09-24：一卡通余额自今日页移除，不再参与刷新聚合。）
        //
        // **指示器驻留**收进 ui/common/RefreshHold.kt（2026-11-05）：原来这里是手写的一份
        // 650ms 驻留（manualRefreshing），守卫读的是捕获值、计时器又被 waterRefreshing 的
        // 每次变化重启，仍会漏出一次「点亮 → 收 → 再点亮」——用户报的「下拉刷新抽搐回弹」。
        // 现在点亮只认 onRefresh、busy 只续命，见 RefreshHoldGate 的 KDoc。
        // busy 读的是**派生的布尔**（页面根上面那条 waterRefreshing），不是整份 uiState：
        // 这里在 Scaffold 内容 lambda 里，读整份状态会让整个内容区每秒重组一遍。
        val refreshHold = rememberRefreshHold(busy = waterRefreshing)
        val pullRefreshState = rememberPullToRefreshState()
        // 触感由 rememberAppHaptics 统一查开关（DisplayPrefs.hapticsEnabled），关掉时短路，
        // 调用点不用自己判断。onRefresh 只在拉过阈值松手时回调——它响就等于「下拉成功」，
        // 与消费流水页同一个语义（那里也是 tap）。
        val haptics = rememberAppHaptics()
        // 与消费流水页同口径：padding 收在刷新容器上，不再由三态内容各自让位。
        // Scaffold 的 content 从 (0,0) 铺满整屏、TopAppBar 压在它上面，容器不带 padding 时
        // 指示器整条滑入轨迹都在顶栏后面（`pullToRefreshIndicator` 里
        // translationY = fraction × threshold − 自身高度，拉满也只到阈值处，比顶栏矮），
        // 要拖过阈值一大截才露出半圈，读起来就是「拉了半天没反应」——2026-09-23 定位。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .pullToRefresh(
                    isRefreshing = refreshHold.isRefreshing,
                    state = pullRefreshState,
                    threshold = TodayPullRefreshThreshold,
                    onRefresh = {
                        refreshHold.begin()
                        haptics.tap()
                        viewModel.refreshTick()
                        waterViewModel?.refreshBalance()
                        waterViewModel?.refreshDevices()
                        // 进行中服务：快趣在案订单静默一拍（下拉强制，绕过 60 秒节流）、
                        // U净 按节流对表，都不阻塞课表刷新
                        serviceCards?.onRefresh?.invoke()
                        // 宿舍报修进度（DESIGN §4.49）：下拉时顺带刷新，不阻塞课表刷新
                        onRefreshDormRepair?.invoke()
                    },
                ),
        ) {
            when {
                state.loading -> TodayLoadingContent()

                // 学期已配好、但今天已过最后一周：多半是切到了旧学期的课表。
                // 这里**不能**再报「尚未开学或未配置学期」——学期明明配好了，那句是假话
                // （2026-11-05 用户报障）。点名这张课表的学期范围，两条出路都写清：
                // 旧课表 → 去课表页换课表；日期填错 → 去课表设置改开学日期。
                state.afterTerm -> {
                    // afterTerm 蕴含 semester 非空；仍按可空写，避免正文里冒出 "null"。
                    // 两段分开拼：`if … else … + "…"` 的优先级只把后半截挂到 else 分支上。
                    val semester = state.semester
                    val range = if (semester != null) {
                        "这张课表按「${semester.startDate} 起 · 共 ${semester.totalWeeks} 周」算，" +
                            "今天已超出它的学期范围。"
                    } else {
                        "今天不在这张课表的学期范围内。"
                    }
                    val body = range +
                        "若是上学期的课表，去「课表」页顶部的课表名处切换到本学期；" +
                        "若这张就是本学期课表，请到「课表设置」改正开学日期。"
                    TodayEmptyContent(
                        title = "当前课表不在本学期",
                        body = body,
                        actionLabel = "去课表页",
                        onAction = onOpenWeek,
                        onOpenInbox = onOpenInbox,
                        onOpenRepair = onOpenDormRepair,
                        repair = dormRepair,
                        guessPick = guessPick,
                        onOpenGuess = openGuess,
                        onOpenSubpage = onOpenSubpage,
                        brandIcon = brandIcon,
                    )
                }

                !state.inTerm -> TodayEmptyContent(
                    "尚未开学或未配置学期",
                    "先在「课表设置」里填好开学日期与周数，也能手动加课。",
                    actionLabel = "去设置学期",
                    onAction = onOpenTimetableSettings,
                    onOpenInbox = onOpenInbox,
                    onOpenRepair = onOpenDormRepair,
                    repair = dormRepair,
                    guessPick = guessPick,
                    onOpenGuess = openGuess,
                    onOpenSubpage = onOpenSubpage,
                    brandIcon = brandIcon,
                )

                state.totalCourseCount == 0 -> TodayEmptyContent(
                    "课表为空",
                    "课表默认为空，请登录教务系统导入「学期理论课表」，也可手动加课。",
                    actionLabel = "从教务导入",
                    onAction = onOpenJwImport,
                    onOpenInbox = onOpenInbox,
                    onOpenRepair = onOpenDormRepair,
                    repair = dormRepair,
                    guessPick = guessPick,
                    onOpenGuess = openGuess,
                    onOpenSubpage = onOpenSubpage,
                    brandIcon = brandIcon,
                )

                else -> TodayContent(
                    state = state,
                    homework = homework,
                    textbookTitles = textbookTitles,
                    onOpenCourse = { detailCourse = it },
                    onOpenHomeworkTodo = onOpenHomeworkTodo,
                    onOpenInbox = onOpenInbox,
                    onOpenRepair = onOpenDormRepair,
                    repair = dormRepair,
                    guessPick = guessPick,
                    onOpenGuess = openGuess,
                    onOpenSubpage = onOpenSubpage,
                    serviceCards = serviceCards,
                    brandIcon = brandIcon,
                )
            }
            PullToRefreshDefaults.Indicator(
                state = pullRefreshState,
                isRefreshing = refreshHold.isRefreshing,
                modifier = Modifier.align(Alignment.TopCenter),
                threshold = TodayPullRefreshThreshold,
            )
        }
    }

    // 只读详情（与课表页同口径）：编辑/删除是详情里的二级动作；
    // 2026-09-21 起弹窗内还有「笔记·课件 / 作业」两个入口（DESIGN §3.11）
    detailCourse?.let { course ->
        // 教材（DESIGN §4.31）：按当前课表学期 + 课程名查；无学期号的课表给空，不显示
        val detailTextbooks by remember(course.name) {
            Services.current.scheduleRepository().textbooksFor(course.name)
        }.collectAsStateWithLifecycle(initialValue = emptyList())
        CourseDetailSheet(
            course = course,
            slots = state.slots,
            currentWeek = state.week,
            textbooks = detailTextbooks,
            onEdit = {
                detailCourse = null
                editing = course
                editorOpen = true
            },
            onDelete = {
                detailCourse = null
                pendingDelete = course
            },
            onOpenNotes = if (onOpenCourseNotes == null) {
                null
            } else {
                {
                    detailCourse = null
                    onOpenCourseNotes(course)
                }
            },
            onOpenHomework = if (onOpenCourseHomework == null) {
                null
            } else {
                {
                    detailCourse = null
                    onOpenCourseHomework(course)
                }
            },
            onDismiss = { detailCourse = null },
        )
    }

    if (editorOpen) {
        CourseEditSheet(
            course = editing,
            onDismiss = { editorOpen = false },
            onSave = { c ->
                viewModel.upsert(c)
                editorOpen = false
            },
            onDelete = editing?.let { c ->
                {
                    pendingDelete = c
                    editorOpen = false
                }
            },
        )
    }

    pendingDelete?.let { c ->
        AppConfirmDialog(
            title = "删除课程",
            message = "确认删除「${c.name}」？",
            confirmLabel = "删除",
            danger = true,
            onConfirm = {
                viewModel.deleteCourse(c)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }

    // 开水卡：点余额的开水操作面板（2026-09-23，DESIGN §3.3）——
    // 余额/积分/开水按钮/流程态/结算明细，与开水页共享同一 WaterViewModel。
    // 状态在 [WaterEntrySheetHost] 里边收：面板里的计时每秒变，读在页面根上会把整页带走。
    if (showWaterEntrySheet && waterViewModel != null) {
        WaterEntrySheetHost(vm = waterViewModel, onDismiss = { showWaterEntrySheet = false })
    }

    // 趣智校园：点余额的开水面板（DESIGN §3.18）——余额/设备/用水状态/结算，
    // 与趣智校园页共享同一个 QzxyViewModel，面板里开的阀在页面上状态一致。
    // ViewModel 与面板本体都在 `:app`（蓝牙传输层搬不动），整块由宿主填槽位。
    if (showQzxyEntrySheet && qzxyPanel != null) {
        qzxyPanel.invoke(
            { showQzxyEntrySheet = false },
            { message -> showNotice(message, NoticeTone.Warning) },
        )
    }

    // 江水生活「取快递」弹窗在左侧栏（SideDrawer），今日页不再承载

    // 江水生活 U净 弹窗（2026-10-19 自生活页迁入）：在案订单状态 + 扫码 + 进页副行
    if (showUjingSheet && ujingAvailable) {
        UjingEntrySheet(
            onDismiss = { showUjingSheet = false },
            onScan = startUjingScan,
            onOpenPage = onOpenUjing,
        )
    }
}

/**
 * 开水面板宿主（2026-11-18）：**状态在这里收**，不在今日页根上收。
 *
 * 为什么要单独一层：面板里的 `flow.elapsedSeconds` 每秒自增一次，而 `uiState` 是整份
 * `StateFlow`——在页面根作用域读它等于「面板开着的时候整页每秒重组一遍」。收在这一层后，
 * 每秒的失效范围只有面板自己。回调原地转发，语义与摊在调用处完全一致。
 */
@Composable
private fun WaterEntrySheetHost(vm: WaterViewModel, onDismiss: () -> Unit) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    WaterEntrySheet(
        state = state,
        onUnlock = { vm.unlock() },
        onDismissFlow = { vm.dismissFlow() },
        onToggleUsePoints = { vm.toggleUsePoints() },
        onEndSession = { vm.endSessionLocally() },
        onDismiss = onDismiss,
    )
}

/**
 * 今日页居中态（加载/空态）共用外壳：`weight(1f)` 居中区 + 贴底固定区。
 * 抽出统一壳是为了防再犯「Box 少 fillMaxWidth 贴左」的错——居中容器只有一处定义。
 */
@Composable
private fun TodayCenteredShell(
    /** 贴底常驻内容（收件箱卡，含报修状态行）：空态也要有（余额预警不依赖学期）。 */
    bottomContent: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    // 顶部/底部的避让由外层刷新容器给（2026-09-23 起），这里不再吃 Scaffold 的 padding
    val clearance = LocalBottomBarClearance.current
    // 净空垫在整列底部，而不是只垫居中区：空态/加载态的贴底收件箱卡也在这列里，
    // 只垫居中区时卡的最后一行会被悬浮胶囊压住（2026-11-05 用户报「界面被挤压」）。
    // 两种写法下居中区的高度相同（都少掉 clearance 这一段），居中内容的位置不变。
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = clearance),
    ) {
        // fillMaxWidth 必须带：Box 默认 wrap 内容、crossAxis 左对齐，
        // 少了它整个居中态贴左（旧版加载态左偏的根因）。
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
        bottomContent?.invoke()
    }
}

/** 加载态：水滴呼吸居中（DESIGN §3.2），底部固定区照常在位（DESIGN §3.3「加载中不留白屏」）。 */
@Composable
private fun TodayLoadingContent() {
    TodayCenteredShell {
        LoadingHint("正在读取本机课表")
    }
}

/**
 * 空态（未开学 / 课表为空）：居中提示 + 底部固定区。
 * 快捷方式网格与开水卡在空态也上桌（DESIGN §3.3/§3.8）——假期恰是取件码高频时段，
 * 课表为空不等于入口该消失。
 */
@Composable
private fun TodayEmptyContent(
    title: String,
    body: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    onOpenInbox: () -> Unit = {},
    /** 报修行点击：进原生报修详情页（收件箱卡内的报修行，2026-10-21）。 */
    onOpenRepair: () -> Unit = {},
    /** 报修当前该展示的那条（收件箱卡内置顶行）；null = 无可见记录。 */
    repair: DormRepairRecord? = null,
    /** 「猜你想用」推荐（DESIGN §3.33）；null = 当前时段不出卡。 */
    guessPick: GuessPick? = null,
    onOpenGuess: (GuessPick) -> Unit = {},
    /** 收件箱卡点消息行跳二级页（`SubpageActivity`）；null = 本平台没有二级页栈。 */
    onOpenSubpage: ((SubpageScreen, String?) -> Unit)? = null,
    /** 品牌位图；null = 本平台没有这套素材，图标位整块不渲染。 */
    brandIcon: (@Composable (GuessTarget) -> Painter?)? = null,
) {
    val bottom: @Composable () -> Unit = {
        Column {
            // 推荐卡的落点与正常内容一致：收件箱列表正上方；空态也挂，因为它只依赖时间 / 课程 / 订单
            guessPick?.let { pick ->
                GuessYouWantCard(
                    pick = pick,
                    onClick = { onOpenGuess(pick) },
                    brandIcon = brandIcon?.invoke(pick.target),
                )
            }
            InboxTodayCard(
                kindIcon = ::inboxKindIcon,
                repair = repair,
                onOpenInbox = onOpenInbox,
                onOpenRepair = onOpenRepair,
                onOpenSubpage = onOpenSubpage,
            )
        }
    }
    TodayCenteredShell(bottomContent = bottom) {
        EmptyHint(title, body, actionLabel, onAction)
    }
}

@Composable
private fun TodayContent(
    state: TodayState,
    homework: PendingHomework,
    textbookTitles: Map<String, List<String>>,
    onOpenCourse: (Course) -> Unit,
    onOpenHomeworkTodo: () -> Unit,
    onOpenInbox: () -> Unit = {},
    /** 报修行点击：进原生报修详情页（收件箱卡内的报修行，2026-10-21）。 */
    onOpenRepair: () -> Unit = {},
    /** 报修当前该展示的那条（收件箱卡内置顶行）；null = 无可见记录。 */
    repair: DormRepairRecord? = null,
    /** 「猜你想用」推荐（DESIGN §3.33）；null = 当前时段不出卡。 */
    guessPick: GuessPick? = null,
    onOpenGuess: (GuessPick) -> Unit = {},
    /** 收件箱卡点消息行跳二级页（`SubpageActivity`）；null = 本平台没有二级页栈。 */
    onOpenSubpage: ((SubpageScreen, String?) -> Unit)? = null,
    /** 品牌位图；null = 本平台没有这套素材，图标位整块不渲染。 */
    brandIcon: (@Composable (GuessTarget) -> Painter?)? = null,
    /**
     * 进行中服务卡（2026-10-21）：快趣骑行 / 胖乖开水 / 趣智洗澡 / U净 洗烘。
     * null = 本平台没有这四项服务（iOS），整块不渲染。
     */
    serviceCards: TodayServiceSlot? = null,
) {
    val sectionColumnWidth = rememberSectionColumnWidth(state.timeline)
    // dock 收进侧栏后，列表最后一项要自己让开悬浮胶囊（2026-10-20）
    val clearance = LocalBottomBarClearance.current
    // 有在案骑行时把「进行中服务」卡提到课程区之前（2026-10-22 用户口径）：骑行是最需要
    // 一眼看到的状态，课程 / 作业多的时候原位置会落到首屏之外；没有在案订单时留在原位。
    //
    // 位置判据由宿主算好传进来（[TodayServiceSlot.pinned]，同步状态）：服务卡里的骑行行
    // 过了能力闸，而能力闸读的是 DataStore 冷流、新组合首帧恒为默认档——拿闸后的状态判位置，
    // 新组合的第二帧才翻真值，`animateItem` 会把卡片从列表下方滑到顶部
    // （2026-11-12 用户报「切 Tab 时从底部跳到顶部」）。判据与卡片本体必须同处一地，
    // 所以整块随槽位交给宿主，页面只读 [TodayServiceSlot.pinned]。
    val servicePinned = serviceCards?.pinned == true
    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 16.dp + clearance),
        ) {
            // 在案骑行：服务卡置顶（见上面 servicePinned 的说明）。无骑行时它在作业卡之后。
            if (servicePinned) {
                serviceCards?.let { slot ->
                    item(key = "active-services") { slot.content(Modifier.animateItem()) }
                }
            }
            // 今日课程：一列到底。正在上的课**留在原位**、就地套焦点卡形态；最近一节已上完的
            // 课置灰保留（更早的不出现）——三态切换的动画在 TodayCourseCard 里。
            // 今天没课 / 不在学期 / 全部上完：一行状态卡（DoneBlock）。
            if (state.timeline.isNotEmpty() && !state.todayAllDone) {
                item(key = "courses-header") {
                    // 计数 = 列表里的卡片数（不是当天总节数）：已上完的会收起、正在上的会在位，
                    // 标题数字必须和下面看到的卡数对上（2026-10-13 用户反馈）
                    SectionHeader("今日课程", trailing = "${state.timeline.size} 节课")
                }
                items(state.timeline, key = { it.id }) { course ->
                    val isOngoing = state.ongoing?.id == course.id
                    TodayCourseCard(
                        course = course,
                        slots = state.slots,
                        textbookTitle = textbookTitles[course.name],
                        sectionColumnWidth = sectionColumnWidth,
                        ongoing = isOngoing,
                        isNext = state.ongoing == null && state.next?.id == course.id,
                        done = state.endedRecent?.id == course.id,
                        countdown = if (isOngoing) {
                            state.ongoingCountdown
                                ?: state.minutesToOngoingEnd?.let { "还有 $it 分钟下课" }
                        } else {
                            state.minutesToNext?.let { "还有 $it 分钟上课" }
                        },
                        progress = if (isOngoing) state.ongoingProgress else null,
                        onClick = { onOpenCourse(course) },
                        // 焦点态会在卡内展开状态行 / 进度条（280ms 高度动画）：位移动画必须关掉，
                        // 否则下面每张卡每帧都被判成「位置变了」并开始一段弹簧位移（文字叠文字、
                        // 落定后再滑下来——培养方案 / 学业卡同一根因，见 ui-common.md）。加 / 删课
                        // 的进出场仍由 animateItem 的淡入淡出交代。
                        modifier = Modifier.animateItem(placementSpec = null),
                    )
                }
            } else {
                item(key = "done") { DoneBlock(state) }
            }

            // 明天只在今天没有待上课程（上完 / 没课）时上桌，今天的信息优先
            if (state.tomorrowVisible) {
                item(key = "tomorrow") { TomorrowBlock(state, textbookTitles, onOpenCourse) }
            }

            // 作业卡（DESIGN §3.3/§3.11）：有未完成作业才占位，位置 = **滚动区最后一项**
            // （「今天还有 N 节」与「明天」块之后）、贴底固定区（快捷方式网格）之上——
            // 2026-09-21 用户拍板两次：放焦点卡之下会把当天课程整段下推；定在明天课表之下，
            // 作业是"顺带看一眼"的信息，不抢课表的位置。
            if (!homework.isEmpty) {
                item(key = "homework") {
                    HomeworkTodayCard(
                        pending = homework,
                        today = state.date,
                        onClick = onOpenHomeworkTodo,
                    )
                }
            }

            // 进行中服务卡（2026-10-21）：快趣骑行 / 胖乖开水 / 趣智洗澡 / U净 洗烘，
            // 谁在进行中谁占一行；全无则整卡不渲染（返回 false 的判据在卡内）。
            // 位置 = 作业卡与收件箱卡之间（没有在案骑行时）；有骑行时已在课程区之前渲染。
            if (!servicePinned) {
                serviceCards?.let { slot ->
                    item(key = "active-services") { slot.content(Modifier.animateItem()) }
                }
            }

            // 「猜你想用」（DESIGN §3.33/§4.51）：收件箱列表**正上方**。
            // 空档 / 开关关 / 无候选时整个 item 不渲染，不留空隙；跨槽进出场走 animateItem。
            // 位移动画关掉（2026-11-18）：它上面的服务卡 / 课程卡高度会动画，位移会让推荐卡
            // 逐帧追弹簧（看起来像卡片自己重组）；进出场淡入淡出保留。
            guessPick?.let { pick ->
                item(key = "guess") {
                    GuessYouWantCard(
                        pick = pick,
                        onClick = { onOpenGuess(pick) },
                        modifier = Modifier.animateItem(placementSpec = null),
                        brandIcon = brandIcon?.invoke(pick.target),
                    )
                }
            }

            // 收件箱卡（DESIGN §3.24，2026-10-21 收口）：收件箱的唯一展示入口。
            // 常驻（今天没消息也渲染单行空态）——「查看更多」要能到达更早的消息。
            // 报修状态行内置（在途优先 / 3 天内完成，原独立报修卡并入，同轮删除）。
            // 放在作业卡之后：与作业同属「顺带看一眼」，不抢课程区的位置。
            item(key = "inbox") {
                InboxTodayCard(
                    kindIcon = ::inboxKindIcon,
                    repair = repair,
                    onOpenInbox = onOpenInbox,
                    onOpenRepair = onOpenRepair,
                    onOpenSubpage = onOpenSubpage,
                )
            }
        }

        // 底部固定区（DESIGN §3.3）：恒贴底，不随上面的课表滚动
    }
}

/** 左列节次标签右侧的呼吸位（在实测最宽标签之外再加这一点，避免文字贴住竖分隔）。 */
private val SectionColumnTrailingGap = 8.dp

/**
 * 左列（节次 + 时间）宽度：按这一批课里**最宽的节次标签**实测取齐，再留一点呼吸位。
 *
 * 为什么不写死一个值：写死时短标签（如「3-4 节」）右侧会空出一大截，看着像排版坏了
 * （2026-10-13 真机反馈「节数右侧留有好大的空白」）。取齐最宽标签既保证各卡竖分隔对齐，
 * 又不留多余空白。实测走 `TextMeasurer`，跟随系统字体缩放。
 */
@Composable
private fun rememberSectionColumnWidth(courses: List<Course>): Dp {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
    return remember(courses, style, density) {
        val maxPx = courses.maxOfOrNull {
            measurer.measure(sectionShortLabel(it), style).size.width
        } ?: 0
        with(density) { maxPx.toDp() } + SectionColumnTrailingGap
    }
}

/**
 * 今日页统一课程卡（2026-10-13）：一列到底，三种形态**就地**切换。
 *
 * - **未上**：课程色 16% 底 + 左列节次/时间 + 竖分隔 + 课名（+ 教材）/ 地点·教师（+ 备注）
 * - **正在上 / 下一节**：现版焦点卡形态——主色 8% 底 + 左缘主色竖条 + 状态行（含倒计时）
 *   + 进度条（仅正在上）；左列节次/时间收起（宽度与透明度过渡）
 * - **已上完**：整卡置灰 + 右端「已下课」
 *
 * 形态切换全部走 animate*（底色 / 左缘竖条 / 左列宽度 / 展开区高度），焦点在课程间
 * 转移时不硬跳。点卡片弹只读详情（编辑/删除是详情里的二级动作，与课表页同口径）。
 */
@Composable
private fun TodayCourseCard(
    course: Course,
    slots: List<TimeSlot>,
    textbookTitle: List<String>?,
    /** 左列（节次 + 时间）宽度：由 [rememberSectionColumnWidth] 按当天最宽的节次标签实测取齐 */
    sectionColumnWidth: Dp,
    ongoing: Boolean,
    isNext: Boolean,
    done: Boolean,
    countdown: String?,
    progress: Float?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val primary = MaterialTheme.colorScheme.primary
    val accent = courseColor(course.colorIndex)
    val focused = ongoing || isNext
    val haptics = rememberAppHaptics()

    val background by animateColorAsState(
        targetValue = when {
            focused -> primary.copy(alpha = 0.08f)
            done -> onSurface.copy(alpha = 0.04f)
            else -> accent.copy(alpha = 0.16f)
        },
        animationSpec = Motion.page(),
        label = "todayCourseBg",
    )
    // 已上完：整卡内容淡出（底色另算）
    val faded by animateFloatAsState(
        targetValue = if (done) 0.55f else 1f,
        animationSpec = Motion.page(),
        label = "todayCourseFaded",
    )
    // 左缘主色竖条：仅焦点态显示，走透明度过渡（避免切换时竖条突然出现 / 消失）
    val barAlpha by animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = Motion.page(),
        label = "todayCourseBar",
    )
    // 左列（节次 + 时间）：焦点态宽度收成 0、内容淡出。宽度按实测的最宽节次标签取齐，
    // 不写死——写死时「3-4 节」右侧会空出一大截（2026-10-13 真机反馈）
    //
    // **这条仍是布局动画**（`animateDpAsState` 逐帧改左列宽、每一帧都要重新测量这张卡）：
    // 2026-11-06 只把时长归并到 [Motion.page]，没有顺手换成 `graphicsLayer` 缩放——那会把
    // 「文字换行位置随宽度走」变成「整块被拉伸」，是观感重构，得单独排、单独验。
    val leftWidth by animateDpAsState(
        targetValue = if (focused) 0.dp else sectionColumnWidth,
        animationSpec = Motion.page(),
        label = "todayCourseLeftWidth",
    )
    val leftAlpha by animateFloatAsState(
        targetValue = if (focused) 0f else 1f,
        animationSpec = Motion.reveal(),
        label = "todayCourseLeftAlpha",
    )
    val textbook = textbookTitlesLine(textbookTitle.orEmpty())
    val meta = placeTeacherLine(course)
    val remark = remarkPreview(course.remark)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(AppCardDefaults.Shape)
            .background(background)
            // 左缘主色竖条用绘制而不是子节点：无界高度下 fillMaxHeight 是 no-op，
            // 而 IntrinsicSize.Min 与 AnimatedVisibility 的固有尺寸测量不兼容，故画出来。
            .drawBehind {
                if (barAlpha > 0f) {
                    val inset = 11.dp.toPx()
                    drawRect(
                        color = primary.copy(alpha = barAlpha),
                        topLeft = Offset(0f, inset),
                        size = Size(3.dp.toPx(), (size.height - inset * 2).coerceAtLeast(0f)),
                    )
                }
            }
            .clickable(onClickLabel = "查看课程") {
                haptics.tap()
                onClick()
            }
            // 卡内统一内缩（与 AppCardDefaults.Padding 同档）：左列节次/时间与课名都从这里起，
            // 不再让节次贴住卡片左缘（2026-10-13 真机反馈「节数和课程都偏左」）
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 左列：节次 + 起始时刻。焦点态宽度收成 0（外框裁剪，内容不压缩）
        Box(
            modifier = Modifier
                .width(leftWidth)
                .clipToBounds(),
        ) {
            Column(
                modifier = Modifier
                    .width(sectionColumnWidth)
                    .alpha(leftAlpha),
            ) {
                Text(
                    text = sectionShortLabel(course),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = onSurface,
                    maxLines = 1,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = clockOf(slots, course),
                    style = MaterialTheme.typography.labelSmall,
                    color = onSurface.copy(alpha = 0.5f),
                    maxLines = 1,
                )
            }
        }
        // 竖分隔：焦点态淡出并让位
        if (leftWidth > 0.dp) {
            Box(
                Modifier
                    .width(1.dp)
                    .height(38.dp)
                    .alpha(leftAlpha)
                    .background(onSurface.copy(alpha = 0.12f)),
            )
            Spacer(Modifier.width(12.dp))
        }
        Column(
            Modifier
                .weight(1f)
                .alpha(faded),
        ) {
            // 状态行（仅焦点态）：正在上课 / 下一节 + 倒计时
            AnimatedVisibility(
                visible = focused,
                enter = expandVertically(Motion.page()) + fadeIn(Motion.page()),
                exit = shrinkVertically(Motion.pageExit()) + fadeOut(Motion.state()),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = (if (ongoing) "正在上课" else "下一节") + " · " + sectionRange(course),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = primary,
                    )
                    if (countdown != null) {
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = countdown,
                            style = MaterialTheme.typography.labelMedium,
                            color = primary,
                            textAlign = TextAlign.End,
                            maxLines = 1,
                        )
                    }
                }
            }
            // 课名 + 教材名（DESIGN §4.31）：教材名挂在课名**右边**同一行，不再另起一行抬卡高。
            // 课名先量（优先保完整、最长两行），教材名吃余量、不够省略号
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = course.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = onSurface,
                )
                if (textbook != null) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = textbook,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = onSurface.copy(alpha = 0.55f),
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
            }
            // meta 行（地点·教师）+ 备注预览（贴卡片右下角）；左列已单列时刻，这里不再重复时间段
            if (meta.isNotBlank() || remark != null) {
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (meta.isNotBlank()) {
                        Text(
                            text = meta,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = onSurface.copy(alpha = 0.68f),
                        )
                    }
                    if (remark != null) {
                        Text(
                            text = remark,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = onSurface.copy(alpha = 0.45f),
                            textAlign = TextAlign.End,
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 8.dp),
                        )
                    }
                }
            }
            // 进度条（仅正在上）
            AnimatedVisibility(
                visible = ongoing && progress != null,
                enter = expandVertically(Motion.page()) + fadeIn(Motion.page()),
                exit = shrinkVertically(Motion.pageExit()) + fadeOut(Motion.state()),
            ) {
                Column {
                    Spacer(Modifier.height(8.dp))
                    ProgressBar(progress ?: 0f)
                }
            }
        }
        // 「已下课」标签（仅已上完）
        if (done) {
            Text(
                text = "已下课",
                style = MaterialTheme.typography.labelSmall,
                color = onSurface.copy(alpha = 0.4f),
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.CenterVertically)
                    .padding(start = 8.dp),
            )
        }
    }
}

/** 自绘进度条：不引 M3 的 LinearProgressIndicator，免去两端圆角/端点圆点的版本差异。 */
@Composable
private fun ProgressBar(progress: Float) {
    val primary = MaterialTheme.colorScheme.primary
    val fraction = progress.coerceIn(0f, 1f)
    // 进度值每 30 秒 tick 才更新一次：直接换宽度会是一格一跳，走 Motion.page 平滑推进
    // （与进行中服务卡的进度条同一口径；低频数值不做 crossfade）。
    val animated by animateFloatAsState(
        targetValue = fraction,
        animationSpec = Motion.page(),
        label = "todayCourseProgress",
    )
    Box(
        Modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(primary.copy(alpha = 0.18f)),
    ) {
        if (animated > 0f) {
            Box(
                Modifier
                    .fillMaxWidth(animated)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(2.dp))
                    .background(primary),
            )
        }
    }
}

/**
 * 今天结束后的落点：上完课 / 本来就没课。
 *
 * 2026-09-22 由「居中两行裸文字」改卡片：居中块与下方课程卡的左缘互不相干，
 * 页面顶部看着像一句留言而不是一屏内容。现在与课程行同宽同左缘，图标交代语义。
 * 2026-10-20 收成单行：标题 + 右侧「共 N 节」，不再占两行高度。
 */
@Composable
private fun DoneBlock(state: TodayState) {
    val primary = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface
    AppCardRow(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Icon(
            imageVector = if (state.todayAllDone) {
                AppIcons.CheckmarkCircle02
            } else {
                AppIcons.CalendarOff
            },
            contentDescription = null,
            tint = primary,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = if (state.todayAllDone) "今天的课都上完了" else "今天没有课",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (state.todayAllDone) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = "共 ${state.todayTotal} 节",
                style = MaterialTheme.typography.labelMedium,
                color = onSurface.copy(alpha = 0.55f),
                maxLines = 1,
            )
        }
    }
}

/**
 * 明日预告。只在 [TodayState.tomorrowVisible]（今天已无待上课程）时渲染，
 * 用与今天相同的行组件，保证「明天也是课表」而不是另一套排版。
 */
@Composable
private fun TomorrowBlock(
    state: TodayState,
    textbookTitles: Map<String, List<String>>,
    onOpenCourse: (Course) -> Unit,
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val sectionColumnWidth = rememberSectionColumnWidth(state.tomorrowCourses)

    Column(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(
            title = "明天 · 周${dayLabel(state.tomorrowDay)}",
            trailing = if (state.tomorrowCourses.isEmpty()) {
                "没有课"
            } else {
                "${state.tomorrowCourses.size} 节"
            },
        )
        if (state.tomorrowCourses.isEmpty()) {
            // 标题行已交代「没有课」，这里只补一句收尾，不再复述一遍
            Text(
                text = "可以放松一下",
                style = MaterialTheme.typography.bodySmall,
                color = onSurface.copy(alpha = 0.55f),
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            return@Column
        }
        // 课程行自带 vertical 4dp 外间距，这里不再叠一层。明天用与今天同一张卡
        // （非焦点、非已上完形态）：左列节次/时间 + 课名 + 地点·教师
        state.tomorrowCourses.forEach { course ->
            TodayCourseCard(
                course = course,
                slots = state.slots,
                textbookTitle = textbookTitles[course.name],
                sectionColumnWidth = sectionColumnWidth,
                ongoing = false,
                isNext = false,
                done = false,
                countdown = null,
                progress = null,
                onClick = { onOpenCourse(course) },
            )
        }
    }
}

// monthDayFmt 已收拢为 domain/TodayFormat.kt 的 MONTH_DAY_FORMAT（与调课页共用）
/**
 * 今日页下拉刷新阈值（2026-09-23）。M3 默认 `PullToRefreshDefaults.PositionalThreshold` 是 80dp，
 * 而 `PullToRefreshModifierNode` 里计入拖动的只有实际位移的一半
 * （`adjustedDistancePulled = distancePulled * DragMultiplier`，`DragMultiplier = 0.5f`），
 * 也就是真拖 160dp 才触发。今日页顶部可拖空间比消费流水页的列表短，收到 56dp（真拖 112dp）。
 * modifier 的 threshold 与 `PullToRefreshDefaults.Indicator` 的 threshold **必须同值**：
 * 前者决定松手触发点，后者决定图标滑出的落位，写岔了会出现「图标到位了却没刷新」。
 */
private val TodayPullRefreshThreshold = 56.dp
