package edu.jxslu.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.ComposeUIViewController
import edu.jxslu.schedule.data.jw.JwUrls
import edu.jxslu.schedule.data.local.CourseEntity
import edu.jxslu.schedule.data.local.DB_VERSION
import edu.jxslu.schedule.data.local.juwDatabase
import edu.jxslu.schedule.data.local.juwDatabasePath
import edu.jxslu.schedule.data.repo.ScheduleTermImport
import edu.jxslu.schedule.domain.CourseKind
import edu.jxslu.schedule.platform.Services
import edu.jxslu.schedule.ui.DataSelfTestResult
import edu.jxslu.schedule.ui.DataSelfTestStrip
import edu.jxslu.schedule.ui.homework.HomeworkLibraryScreen
import edu.jxslu.schedule.ui.campus.StatementScreen
import edu.jxslu.schedule.ui.campus.StatementViewModel
import edu.jxslu.schedule.ui.inbox.InboxScreen
import edu.jxslu.schedule.ui.jw.JwHubScreen
import edu.jxslu.schedule.ui.jw.JwScheduleListScreen
import edu.jxslu.schedule.ui.jw.TeachingCalendarScreen
import edu.jxslu.schedule.ui.jw.TextbookScreen
import edu.jxslu.schedule.ui.life.PowerBillScreen
import edu.jxslu.schedule.ui.life.PowerBillViewModel
import edu.jxslu.schedule.ui.me.DisplaySettingsContent
import edu.jxslu.schedule.ui.me.MeViewModel
import edu.jxslu.schedule.ui.me.SettingsScreen
import edu.jxslu.schedule.ui.notes.NoteLibraryScreen
import edu.jxslu.schedule.ui.selection.SelectionScreen
import edu.jxslu.schedule.ui.exam.ExamScreen
import edu.jxslu.schedule.ui.score.ScoreScreen
import edu.jxslu.schedule.ui.theme.JuwTheme
import edu.jxslu.schedule.ui.weather.WeatherScreen
import edu.jxslu.schedule.ui.weather.WeatherViewModel
import edu.jxslu.schedule.ui.web.WebContentView
import edu.jxslu.schedule.ui.web.WebViewController
import edu.jxslu.schedule.ui.week.WeekScreen
import edu.jxslu.schedule.ui.week.WeekViewModel
import kotlinx.coroutines.launch
import platform.UIKit.UIViewController

/**
 * iOS 侧入口（P3，2026-11-24；M1 起是产品页面）。
 *
 * Swift 侧通过 `MainViewControllerKt.MainViewController()` 拿到这个 UIViewController，
 * 再用 `UIViewControllerRepresentable` 塞进 SwiftUI。这是 Compose Multiplatform 在 iOS 上的
 * 标准接法：Compose 负责画，Swift 只负责窗口与生命周期。
 *
 * ## 现在的页面结构
 *
 * | 区域 | 内容 | 说明 |
 * |---|---|---|
 * | 主体 | [WeekScreen]（共享层同一个页面） | 真实 Room 数据；空库时显示「去导入课表」 |
 * | 底部 | [DbSelfTestStrip] 一行 | 读写探针，**保留是因为它是「数据链路通了」的唯一屏上证据** |
 * | 覆盖层 | [JwLoginScreen] | 点「去导入」进来的教务登录 WebView（WKWebView） |
 *
 * 搬掉的两个旧面板：`WeekGridPreview`（样例网格）与 `LinkValidationScreen`（定点小数对照）。
 * 它们的验收目的都已达成——真网格取代样例网格，小数口径由 2418 条 JVM 单测继续钉住。
 *
 * ## 装配
 *
 * [IosServices] 必须在**任何**共享 ViewModel 构造之前安装，所以放在
 * `ComposeUIViewController` 之前——那是 Swift 调用进来的第一行。
 * 与 Android 侧 `JuwApplication.onCreate` 里的 `Services.install(GraphServices(this))` 对称。
 */
fun MainViewController(): UIViewController {
    val services = IosServices()
    Services.install(services)
    // 首启把作息表与默认学期写进库（Android 在 JuwApplication.ensureDefaults 里做同一件事）
    services.warmUp()
    return ComposeUIViewController { IosRoot(services) }
}

@Composable
private fun IosRoot(services: IosServices) {
    JuwTheme {
        var jwOpen by remember { mutableStateOf(false) }
        var page by remember { mutableStateOf(IosPage.Week) }
        // 教务 Tab 的一层子页（S18）：理论 / 实验课表、教材、教学日历都已经在共享层，
        // iOS 侧没有 `SubpageActivity` 那套栈，这一版只要一层——非 null = 正在看那一页，
        // 返回键清回 null。
        var jwSub by remember { mutableStateOf<IosJwSubPage?>(null) }
        // 详情页（笔记 / 作业）还在 :app，iOS 点条目时给一句确定的话，不留「点了没反应」
        var hint by remember { mutableStateOf<String?>(null) }
        // 「我的」与课表显示设置共用同一个 VM 实例（Android 侧是 Activity 作用域，两边等价）。
        // iOS 没有配置变更重建，`remember` 的生命周期就是这一屏；见 design/ios-port.md §11.2 ①。
        val meViewModel = remember { MeViewModel(services.scheduleRepository()) }
        if (jwOpen) {
            JwLoginScreen(services = services, onClose = { jwOpen = false })
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
            ) {
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    when (page) {
                        // ViewModel 挂在组合上即可：iOS 侧没有 Android 的配置变更重建，
                        // `remember` 的生命周期就等于这一屏的生命周期。
                        IosPage.Week -> {
                            val viewModel = remember { WeekViewModel(services.scheduleRepository()) }
                            WeekScreen(
                                viewModel = viewModel,
                                // 空态的「去导入课表」→ 教务登录页（M2 第一步：先把登录页跑起来）
                                onOpenJwImport = { jwOpen = true },
                                // 显示设置面板（S17 搬进共享层）：顶栏那只眼睛从此在 iOS 上也有入口。
                                // 面板里的「背景」整组不出现——iOS 还没有 UIImage 解码实现，
                                // 装配根的 scheduleBackgroundFiles() 返回 null。
                                displaySettings = { headerMinDp, dismiss ->
                                    IosDisplaySettingsOverlay(
                                        viewModel = meViewModel,
                                        headerMinDp = headerMinDp,
                                        onDismiss = dismiss,
                                    )
                                },
                                // 其余平台动作在 iOS 侧都还没有实现，留空即可——
                                // 传 null / 空 lambda 的入口本来就不渲染（见 WeekScreen 头部参数表）。
                            )
                        }
                        // 教务 Tab（S18 搬进共享层）：概览数字读本地表（Room），两端同源。
                        //
                        // 十四个二级页回调里接了七个，落点都是**真页面**：
                        // - 成绩 / 考试 / 选课：`IosPage` 里本来就有；
                        // - 理论 / 实验课表、教材、教学日历：本次一起搬进共享层，
                        //   落到本 Tab 的一层子页（[jwSub]）里，返回键回汇总页。
                        // 其余七个（学业、培养方案、实习基地、评教、请假、报修、校历）的页面
                        // 还在 `:app`，传 null = 整行不渲染（`design/ios-port.md` §11.2 ④：
                        // 给空 lambda 留死入口比没有更糟）。顶栏「校园门户」同理：iOS 还没有
                        // 通用浏览窗口，传 null 后图标不出现。
                        IosPage.Jw -> {
                            val sub = jwSub
                            if (sub != null) {
                                when (sub) {
                                    IosJwSubPage.TheorySchedule -> JwScheduleListScreen(
                                        kind = CourseKind.Theory,
                                        onBack = { jwSub = null },
                                    )
                                    IosJwSubPage.LabSchedule -> JwScheduleListScreen(
                                        kind = CourseKind.Lab,
                                        onBack = { jwSub = null },
                                    )
                                    IosJwSubPage.Textbooks -> TextbookScreen(
                                        onBack = { jwSub = null },
                                    )
                                    IosJwSubPage.TeachingCalendar -> TeachingCalendarScreen(
                                        onBack = { jwSub = null },
                                    )
                                }
                            } else {
                                JwHubScreen(
                                    onOpenTheorySchedule = { jwSub = IosJwSubPage.TheorySchedule },
                                    onOpenLabSchedule = { jwSub = IosJwSubPage.LabSchedule },
                                    onOpenTextbooks = { jwSub = IosJwSubPage.Textbooks },
                                    onOpenTeachingCalendar = {
                                        jwSub = IosJwSubPage.TeachingCalendar
                                    },
                                    onOpenScores = { page = IosPage.Score },
                                    onOpenExams = { page = IosPage.Exam },
                                    onOpenSelection = { page = IosPage.Selection },
                                )
                            }
                        }
                        // 「我的」：设置首页（S17 搬进共享层）。二级页栈是 Android 的
                        // `SubpageActivity`，iOS 侧还没有，所以每个入口先给一句确定的话。
                        IosPage.Me -> SettingsScreen(
                            onOpen = { screen ->
                                hint = "「${screen.name}」还在 Android 侧，下一批搬过来"
                            },
                            viewModel = meViewModel,
                        )
                        // 笔记库（S13 搬进共享层）：仓库是本地 Room 表，iOS 直接可读。
                        // `images` 传 null = 本平台还没有附件图解码，缩略图整行不出现；
                        // 详情页仍在 :app，条目点开先给提示（见 [hint]）。
                        IosPage.Notes -> NoteLibraryScreen(
                            onBack = { page = IosPage.Week },
                            onOpenNote = { _, _, _ ->
                                hint = "笔记详情页还在 Android 侧，下一批搬过来"
                            },
                        )
                        // 作业库（S13）：同上。`markdown` 传 null 时正文退化成纯文本，
                        // 不会因为「还没有渲染器」就把作业内容藏掉。
                        IosPage.Homework -> HomeworkLibraryScreen(
                            onBack = { page = IosPage.Week },
                            onOpenHomework = { _, _, _ ->
                                hint = "作业详情页还在 Android 侧，下一批搬过来"
                            },
                        )
                        // 选课（S13）：iOS 没有教务直取链（`IosServices.selectionSync()` 返回 null），
                        // 轮次卡会说「本平台还没有教务直取能力」；已同步过的记录仍可读。
                        // 二级页（选课中心 / 预选清单 / 已选课程）在 iOS 侧还没有路由，全部传 null，
                        // 对应入口整行不渲染。
                        IosPage.Selection -> SelectionScreen(onBack = { page = IosPage.Week })
                        // 成绩（S10 搬进共享层）：读的是本地 Room 表，iOS 直接可用。
                        // 四个平台动作全传 null——「从教务导入」「成绩设置」「盖章成绩单」
                        // 三个入口在 iOS 侧还没有对应实现，对应行整行不渲染。
                        IosPage.Score -> ScoreScreen(onBack = { page = IosPage.Week })
                        // 考试（S10）：同上。考试数据来自教务同步，iOS 已有 IosJwReadSession，
                        // 顶栏的「刷新」走 ExamSync，导入页登录后即可拉到。
                        IosPage.Exam -> ExamScreen(onBack = { page = IosPage.Week })
                        // 收件箱（S19 搬进共享层）：单表 Room，iOS 直接可读。
                        // `onOpenSubpage` 传 null = iOS 还没有二级页栈：点行**仍会标已读**，
                        // 只是不跳页（见 InboxScreen 头部参数表）。传个空 lambda 会变成
                        // 「点了没反应」，比没有更糟。
                        IosPage.Inbox -> InboxScreen(onBack = { page = IosPage.Week })
                        // 天气（S19）：仓库是纯 Ktor + 进程内缓存，iOS 与 Android 同一份实现。
                        // ViewModel 挂在组合上——iOS 没有配置变更重建，`remember` 就等于这一屏。
                        IosPage.Weather -> {
                            val viewModel = remember {
                                WeatherViewModel(services.weatherRepository())
                            }
                            WeatherScreen(viewModel = viewModel, onBack = { page = IosPage.Week })
                        }
                        // 电费账单（S20 搬进共享层）：iOS 没有加密凭证存储
                        // （`IosServices.yktCredentialStore()` 返回 null），所以进页落在
                        // 「凭证未配置」态——「用电统计」分页照样画本机攒下的读数，充值账单为空。
                        // 「去设置」「在缴费平台打开」两个入口传 null，整行不渲染（§11.2 ④）。
                        IosPage.PowerBill -> {
                            val vm = remember {
                                PowerBillViewModel(
                                    repo = services.powerRepository(),
                                    credentialStore = services.yktCredentialStore(),
                                    readingStore = services.powerReadingStore(),
                                )
                            }
                            PowerBillScreen(viewModel = vm, onBack = { page = IosPage.Week })
                        }
                        // 消费流水（S20）：同上。凭证为 null 时只显示本地 `ykt_turnovers` 表，
                        // 空库就是空态；`postInbox` 不传 = iOS 没有收件箱落点，同步照跑但不留痕。
                        IosPage.Statement -> {
                            val vm = remember {
                                StatementViewModel(
                                    repo = services.yktRepository(),
                                    credentialStore = services.yktCredentialStore(),
                                    db = services.database(),
                                )
                            }
                            StatementScreen(viewModel = vm, onBack = { page = IosPage.Week })
                        }
                        }
                    }
                }
                hint?.let { text ->
                    Text(
                        text = text,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    IosPage.entries.forEach { entry ->
                        TextButton(
                            onClick = {
                                hint = null
                                // 点底栏 = 回到该 Tab 的根页（教务 Tab 正在看子页时退回汇总页）
                                jwSub = null
                                page = entry
                            },
                        ) { Text(entry.label) }
                    }
                }
                DbSelfTestStrip()
            }
        }
    }
}

/**
 * iOS 侧的显示设置覆盖面板（S17，2026-11-25）。
 *
 * 与 Android 的 `DisplaySettingsOverlay`（`:app`）是**同一个面板的两种壳**：
 * 里面都是共享层的 `DisplaySettingsContent`，改的全是同一份 `DisplayPrefs`。
 *
 * 壳为什么不能共用：Android 那份挂着 `PredictiveBackHandler`（`androidx.activity`）与
 * `LocalConfiguration`，两者在 `commonMain` 不可见，而且 iOS 没有「系统返回手势」这回事。
 *
 * 这一版只做「盖住下半屏 + 点遮罩关闭 + 完成按钮」：拖拽调高（把手）与预测性返回是
 * Android 的交互习惯，iOS 用户预期的是「弹层铺到底、点外部关闭」，硬搬反而别扭。
 * 面板里「背景」整组不出现——iOS 还没有 `UIImage` 解码实现（见 `IosServices`）。
 */
@Composable
private fun IosDisplaySettingsOverlay(
    viewModel: MeViewModel,
    headerMinDp: Float,
    onDismiss: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        // 遮罩：点它就关（与 Android 同一手感：无波纹点击）
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.32f))
                .clickable(
                    onClickLabel = "关闭显示设置",
                    role = Role.Button,
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onDismiss,
                ),
        )
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.7f),
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp,
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 12.dp, top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "显示设置",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("完成") }
                }
                DisplaySettingsContent(
                    viewModel = viewModel,
                    headerMinDp = headerMinDp,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * iOS 侧目前能进的一级页面（S13 起有笔记 / 作业 / 选课；S18 起有教务；
 * S19 起有收件箱与天气；S20 起有电费与流水；S17 起有「我的」）。
 *
 * 为什么用枚举而不是导航库：iOS 侧还没有二级页栈（`SubpageActivity` 那一套是 Android 的），
 * 这一版只要能切页就够验证「共享页面在 iOS 上真的画得出来」。等详情页搬完再换真正的路由。
 */
private enum class IosPage(val label: String) {
    Week("课表"),
    // 教务 Tab 的入口汇总页；与 Android 底栏同序（课表之后就是教务）。
    Jw("教务"),
    /** 「我的」= 设置首页（S17 起在共享层）。 */
    Me("我的"),
    Notes("笔记"),
    Homework("作业"),
    Selection("选课"),
    Score("成绩"),
    Exam("考试"),
    Inbox("收件箱"),
    Weather("天气"),
    PowerBill("电费"),
    Statement("流水"),
}

/**
 * 教务 Tab 下的一层子页（S18）。这些页面都已搬进 `shared/commonMain`，iOS 直接画得出来；
 * 它们的平台动作（浏览窗口 / 导入窗口）iOS 侧还没有，传 null 后对应入口不渲染。
 */
private enum class IosJwSubPage {
    TheorySchedule,
    LabSchedule,
    Textbooks,
    TeachingCalendar,
}

/**
 * 教务导入页（M2，2026-11-25）：WKWebView 登录 → 自动抓取并落库。
 *
 * ## 流程
 *
 * 1. `WKWebView` 打开统一身份认证（[JwUrls.ENTRY]），用户自己填账号密码 —— iOS 没有凭证
 *    保险箱（`EncryptedSharedPreferences` 是 Android 专有），**不做自动填表**，也不存密码。
 * 2. 页面落在教务域（[JwUrls.isJwHost]）就说明 CAS 回跳完成、Cookie 已经进
 *    `WKWebsiteDataStore`：这时跑一次 `ScheduleTermImport.archive(manual = true)`，
 *    它用 `IosJwReadSession` 读 Cookie 发 HTTP，走的是**与 Android 同一条共享抓取链**。
 * 3. 结果写成顶栏一行字；返回课表页即可看到，课表是 Room 的流，不需要手动刷新。
 *
 * ## 为什么只抓一次
 *
 * `archive` 自己会遍历教务下拉里的全部学期，重复触发只是白撞教务（强智是单会话，
 * 撞多了会被顶掉）。所以用 `importing` 闸门；失败后闸门放开，下一次页面加载会自然重试。
 *
 * ## 这一版还没有的
 *
 * 没有成绩 / 考试 / 教材 / 选课的单页导入——那些在 Android 侧是 `JwImportScreen` 里
 * 的多个入口，要逐页搬。当前只打通「课表导入」这一条主链路。
 */
@Composable
private fun JwLoginScreen(services: IosServices, onClose: () -> Unit) {
    val controller = remember { WebViewController() }
    val scope = rememberCoroutineScope()
    var currentUrl by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var importing by remember { mutableStateOf(false) }

    // 回调挂在**控制器**上：控制器比组合活得久（视图销毁后仍在），装一次即可。
    // 内层读的 `importing` / `status` 是 remember 的 State 委托，读到的是当前值。
    remember(controller) {
        controller.onPageFinished = { url ->
            currentUrl = url
            error = null
            if (!importing && JwUrls.isJwHost(url)) {
                importing = true
                status = "已登录，正在从教务导入课表…"
                scope.launch {
                    val failure = services.scheduleTermImport()
                        .archive(manual = true) as? ScheduleTermImport.Result.Failed
                    status = if (failure == null) {
                        "导入完成，返回课表即可看到"
                    } else {
                        "导入失败：${failure.reason}"
                    }
                    importing = false
                }
            }
        }
        controller.onError = { message -> error = message }
        controller
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onClose) { Text("返回课表") }
            Text(
                // 有状态就说状态：导入中 / 导入结果比 URL 有用得多
                text = error ?: status ?: currentUrl ?: JwUrls.ENTRY,
                fontSize = 11.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = when {
                    error != null -> MaterialTheme.colorScheme.error
                    status != null -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                },
                modifier = Modifier.padding(end = 8.dp),
            )
        }
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background),
        ) {
            WebContentView(
                controller = controller,
                url = JwUrls.ENTRY,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * 数据库自检（P2 验收，2026-11-24）：真实写入一门探针课 → 按 id 读回 → 删掉 → 再数一遍。
 *
 * 界面与状态机在 `ui/DataSelfTestStrip.kt`（commonMain），本机门禁能编；这里只留平台相关的
 * 十几行。探针用固定 `timetableId = 999999` 且写完即删，不会污染真实数据。
 */
@Composable
private fun DbSelfTestStrip() {
    DataSelfTestStrip(
        probe = {
            val dao = juwDatabase().courseDao()
            val probe = CourseEntity(
                timetableId = 999_999,
                name = "iOS 数据自检",
                teacher = "-",
                position = "-",
                day = 1,
                startSection = 1,
                endSection = 1,
                weeksCsv = "1",
            )
            val id = dao.upsert(probe)
            val readBack = dao.getById(id)
            dao.delete(probe.copy(id = id))
            val left = dao.getForTimetable(999_999).size
            if (readBack?.name == probe.name && left == 0) {
                DataSelfTestResult(ok = true, text = "DB v$DB_VERSION · 写入并读回 1 门 · 清理后剩 $left 门")
            } else {
                DataSelfTestResult(ok = false, text = "读写不一致：读回「${readBack?.name}」，残留 $left 门")
            }
        },
        footnote = juwDatabasePath(),
        emptyHint = "数据库自检：进行中…",
    )
}
