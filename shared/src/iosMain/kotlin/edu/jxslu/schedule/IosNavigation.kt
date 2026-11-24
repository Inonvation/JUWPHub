package edu.jxslu.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import edu.jxslu.schedule.domain.CourseKind
import edu.jxslu.schedule.ui.ai.AiChatScreen
import edu.jxslu.schedule.ui.ai.AiChatViewModel
import edu.jxslu.schedule.ui.ai.AiHistoryScreen
import edu.jxslu.schedule.ui.ai.AiHistoryViewModel
import edu.jxslu.schedule.ui.ai.AiSettingsScreen
import edu.jxslu.schedule.ui.ai.AiSettingsViewModel
import edu.jxslu.schedule.ui.me.hub.AccountsHubScreen
import edu.jxslu.schedule.ui.campus.DormRepairScreen
import edu.jxslu.schedule.ui.campus.StatementScreen
import edu.jxslu.schedule.ui.campus.StatementViewModel
import edu.jxslu.schedule.ui.exam.ExamScreen
import edu.jxslu.schedule.ui.exam.ExamSettingsScreen
import edu.jxslu.schedule.ui.homework.HomeworkCourseScreen
import edu.jxslu.schedule.ui.homework.HomeworkLibraryScreen
import edu.jxslu.schedule.ui.homework.HomeworkTodoScreen
import edu.jxslu.schedule.ui.icon.AppIcons
import edu.jxslu.schedule.ui.inbox.InboxScreen
import edu.jxslu.schedule.ui.jw.JwScheduleListScreen
import edu.jxslu.schedule.ui.jw.StudentInfoScreen
import edu.jxslu.schedule.ui.jw.TeachingCalendarScreen
import edu.jxslu.schedule.ui.jw.TextbookScreen
import edu.jxslu.schedule.ui.life.PowerBillScreen
import edu.jxslu.schedule.ui.life.PowerBillViewModel
import edu.jxslu.schedule.ui.me.DataSettingsScreen
import edu.jxslu.schedule.ui.me.MeViewModel
import edu.jxslu.schedule.ui.notes.NoteCourseScreen
import edu.jxslu.schedule.ui.notes.NoteLibraryScreen
import edu.jxslu.schedule.ui.score.ScoreScreen
import edu.jxslu.schedule.ui.score.ScoreSettingsScreen
import edu.jxslu.schedule.ui.selection.SelectionCenterScreen
import edu.jxslu.schedule.ui.selection.SelectionRecordsScreen
import edu.jxslu.schedule.ui.selection.SelectionScreen
import edu.jxslu.schedule.ui.selection.SelectionWishesScreen
import edu.jxslu.schedule.ui.weather.WeatherScreen
import edu.jxslu.schedule.ui.weather.WeatherViewModel

/**
 * iOS 的二级页路由（2026-11-25）。
 *
 * ## 为什么复用 `SubpageScreen` 而不是另立一套
 *
 * Android 侧的路由枚举就是 [SubpageScreen]（`shared/commonMain`，`SubpageActivity` 消费）。
 * iOS 另立一套枚举的后果是：以后每加一个页面都要改两处、漏一处就出现「Android 有入口、
 * iOS 没有」。所以这里直接用同一个枚举当路由键，抽屉里的分组与条目也照 Android 的来。
 *
 * ## 抽屉里只放已经搬过来的页面
 *
 * [iosDrawerEntries] 是**手工维护的白名单**：搬完一个页面就往这里加一行，同时
 * [IosRouteContent] 里把对应的 `else` 分支换成真页面。没进白名单的页面不会出现在抽屉里
 * ——`design/ios-port.md` §11.2 ④：留一个点了没反应的入口比没有入口更糟。
 *
 * 但 [IosRouteContent] 仍然要覆盖枚举的全部取值（`else` 落到 [IosNotPortedScreen]），
 * 因为 `SettingsScreen` / `JwHubScreen` 这类页面的回调参数类型就是 `SubpageScreen`，
 * 编译器不认「白名单之外不存在」。
 *
 * ## 参数
 *
 * 少数页面要一个字符串参数（`NOTES_COURSE` / `HOMEWORK_COURSE` 要课程名）。路由条目因此是
 * [IosRoute]（screen + 可选 arg），不是裸枚举。
 */
internal data class IosRoute(val screen: SubpageScreen, val arg: String? = null)

/**
 * 抽屉分组，顺序与 Android 左侧栏一致（身份 / 钱包 / 生活 / 江水生活 / 学习 / 设置）。
 *
 * 「江水生活」整组目前是空的——开水房 / 问渠 / U净 / 趣智 / AI 的页面还在 `:app`。
 * 空组不渲染，所以这里先不列。
 */
internal data class IosDrawerEntry(
    val section: String,
    val route: IosRoute,
    val label: String,
)

/**
 * 抽屉里能进的页面白名单（2026-11-25）。
 *
 * 加条目的同时必须让 [IosRouteContent] 认识它——两处都要改，这是有意的：白名单与渲染分支
 * 对不上时，点进去会落在「还在 Android 侧」的兜底页，一眼能看出来，而不是静默无反应。
 */
internal fun iosDrawerEntries(): List<IosDrawerEntry> = listOf(
    // 学习
    IosDrawerEntry("学习", IosRoute(SubpageScreen.NOTES), "笔记"),
    IosDrawerEntry("学习", IosRoute(SubpageScreen.HOMEWORK), "作业"),
    IosDrawerEntry("学习", IosRoute(SubpageScreen.HOMEWORK_TODO), "待办"),
    IosDrawerEntry("学习", IosRoute(SubpageScreen.THEORY_SCHEDULE), "理论课表"),
    IosDrawerEntry("学习", IosRoute(SubpageScreen.LAB_SCHEDULE), "实验课表"),
    IosDrawerEntry("学习", IosRoute(SubpageScreen.TEXTBOOKS), "教材"),
    IosDrawerEntry("学习", IosRoute(SubpageScreen.TEACHING_CALENDAR), "教学日历"),
    // 教务
    IosDrawerEntry("教务", IosRoute(SubpageScreen.SCORES), "成绩"),
    IosDrawerEntry("教务", IosRoute(SubpageScreen.SCORE_SETTINGS), "成绩设置"),
    IosDrawerEntry("教务", IosRoute(SubpageScreen.EXAMS), "考试"),
    IosDrawerEntry("教务", IosRoute(SubpageScreen.EXAM_SETTINGS), "考试设置"),
    IosDrawerEntry("教务", IosRoute(SubpageScreen.SELECTIONS), "选课"),
    IosDrawerEntry("教务", IosRoute(SubpageScreen.SELECTION_CENTER), "选课中心"),
    IosDrawerEntry("教务", IosRoute(SubpageScreen.SELECTION_RECORDS), "已选课程"),
    IosDrawerEntry("教务", IosRoute(SubpageScreen.SELECTION_WISHES), "预选清单"),
    IosDrawerEntry("教务", IosRoute(SubpageScreen.STUDENT_INFO), "学籍信息"),
    // 钱包
    IosDrawerEntry("钱包", IosRoute(SubpageScreen.POWER_BILL), "电费"),
    IosDrawerEntry("钱包", IosRoute(SubpageScreen.CAMPUS_STATEMENT), "消费流水"),
    // 生活
    IosDrawerEntry("生活", IosRoute(SubpageScreen.INBOX), "收件箱"),
    IosDrawerEntry("生活", IosRoute(SubpageScreen.DORM_REPAIR), "宿舍报修"),
    IosDrawerEntry("生活", IosRoute(SubpageScreen.WEATHER), "天气"),
    // 设置
    IosDrawerEntry("设置", IosRoute(SubpageScreen.DATA_SETTINGS), "数据与备份"),
    // 账号
    IosDrawerEntry("账号", IosRoute(SubpageScreen.ACCOUNTS), "账号中心"),
    // 江水生活（已搬的三块）
    IosDrawerEntry("江水生活", IosRoute(SubpageScreen.AI_SCHEDULE), "AI 调课"),
    IosDrawerEntry("江水生活", IosRoute(SubpageScreen.AI_SETTINGS), "AI 设置"),
    IosDrawerEntry("江水生活", IosRoute(SubpageScreen.AI_HISTORY), "调课记录"),
)

/**
 * 渲染一个二级页。
 *
 * `push` / `pop` 由宿主（[IosRoot]）提供：页面自己的返回键走 `pop`，页面里的二级入口走 `push`。
 * `onHint` 用于「这一页能显示但某个动作本平台没有」的说明——比静默无反应好。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun IosRouteContent(
    route: IosRoute,
    services: IosServices,
    meViewModel: MeViewModel,
    push: (IosRoute) -> Unit,
    pop: () -> Unit,
) {
    when (route.screen) {
        // ---------------------------------------------------------------- 学习
        SubpageScreen.NOTES -> NoteLibraryScreen(
            onBack = pop,
            onOpenNote = { courseName, _, _ ->
                push(IosRoute(SubpageScreen.NOTES_COURSE, courseName))
            },
            // null = 本平台还没有附件图解码（见 IosServices 的说明），缩略图整行不出现
            images = null,
        )

        SubpageScreen.NOTES_COURSE -> NoteCourseScreen(
            courseName = route.arg.orEmpty(),
            onBack = pop,
            // 笔记详情页还在 :app（Markdown 栈已搬，但详情页的编辑接线没搬）
            onOpenNote = { _, _ -> },
            images = null,
        )

        SubpageScreen.HOMEWORK -> HomeworkLibraryScreen(
            onBack = pop,
            onOpenHomework = { courseName, _, _ ->
                push(IosRoute(SubpageScreen.HOMEWORK_COURSE, courseName))
            },
            images = null,
            markdown = null,
        )

        SubpageScreen.HOMEWORK_TODO -> HomeworkTodoScreen(
            onBack = pop,
            onOpenHomework = { courseName, _, _ ->
                push(IosRoute(SubpageScreen.HOMEWORK_COURSE, courseName))
            },
            onOpenLibrary = { push(IosRoute(SubpageScreen.HOMEWORK)) },
            images = null,
            markdown = null,
        )

        SubpageScreen.HOMEWORK_COURSE -> HomeworkCourseScreen(
            courseName = route.arg.orEmpty(),
            onBack = pop,
            onOpenHomework = { _, _ -> },
            images = null,
            markdown = null,
        )

        SubpageScreen.THEORY_SCHEDULE -> JwScheduleListScreen(
            kind = CourseKind.Theory,
            onBack = pop,
        )

        SubpageScreen.LAB_SCHEDULE -> JwScheduleListScreen(
            kind = CourseKind.Lab,
            onBack = pop,
        )

        SubpageScreen.TEXTBOOKS -> TextbookScreen(onBack = pop)

        SubpageScreen.TEACHING_CALENDAR -> TeachingCalendarScreen(onBack = pop)

        // ---------------------------------------------------------------- 教务
        SubpageScreen.SCORES -> ScoreScreen(
            onBack = pop,
            onOpenSettings = { push(IosRoute(SubpageScreen.SCORE_SETTINGS)) },
            // 盖章成绩单要 WebView + PDF 导出，iOS 还没有；传 null 入口不渲染
        )

        SubpageScreen.SCORE_SETTINGS -> ScoreSettingsScreen(onBack = pop)

        SubpageScreen.EXAMS -> ExamScreen(
            onBack = pop,
            onOpenSettings = { push(IosRoute(SubpageScreen.EXAM_SETTINGS)) },
        )

        SubpageScreen.EXAM_SETTINGS -> ExamSettingsScreen(onBack = pop)

        SubpageScreen.SELECTIONS -> SelectionScreen(
            onBack = pop,
            onOpenCenter = { roundId ->
                // 选课中心带轮次参数：iOS 侧路由只带字符串参数，轮次 id 落在 arg 里
                push(IosRoute(SubpageScreen.SELECTION_CENTER, roundId))
            },
            onOpenRecords = { push(IosRoute(SubpageScreen.SELECTION_RECORDS)) },
            onOpenWishes = { push(IosRoute(SubpageScreen.SELECTION_WISHES)) },
        )

        SubpageScreen.SELECTION_CENTER -> SelectionCenterScreen(
            onBack = pop,
            focusRoundId = route.arg,
            // null = 本平台还没有选课接口客户端（iOS 的教务直取链只做了只读同步）
            client = null,
        )

        SubpageScreen.SELECTION_RECORDS -> SelectionRecordsScreen(onBack = pop)

        SubpageScreen.SELECTION_WISHES -> SelectionWishesScreen(
            onBack = pop,
            onOpenCenter = { push(IosRoute(SubpageScreen.SELECTION_CENTER)) },
            client = null,
        )

        SubpageScreen.STUDENT_INFO -> StudentInfoScreen(onBack = pop)

        // ---------------------------------------------------------------- 钱包
        SubpageScreen.POWER_BILL -> {
            val vm = remember {
                PowerBillViewModel(
                    repo = services.powerRepository(),
                    credentialStore = services.yktCredentialStore(),
                    readingStore = services.powerReadingStore(),
                )
            }
            PowerBillScreen(viewModel = vm, onBack = pop)
        }

        SubpageScreen.CAMPUS_STATEMENT -> {
            val vm = remember {
                StatementViewModel(
                    repo = services.yktRepository(),
                    credentialStore = services.yktCredentialStore(),
                    db = services.database(),
                )
            }
            StatementScreen(viewModel = vm, onBack = pop)
        }

        // ---------------------------------------------------------------- 生活
        SubpageScreen.INBOX -> InboxScreen(onBack = pop)

        SubpageScreen.WEATHER -> {
            val vm = remember { WeatherViewModel(services.weatherRepository()) }
            WeatherScreen(viewModel = vm, onBack = pop)
        }

        // ---------------------------------------------------------------- 设置
        SubpageScreen.DATA_SETTINGS -> DataSettingsScreen(onBack = pop, viewModel = meViewModel)

        // ---------------------------------------------------------------- 宿舍报修
        //
        // 页面读本机快照（`dormRepairStore()`，两端同一份 JSON）；两个平台动作传 null：
        // 「去学工报修页核对」要打开学工 WebView（iOS 还没搬），「下拉刷新」要
        // `DormRepairSync`（带 WebView 登录态，还在 `:app`）。对应入口整行不渲染，
        // 本机缓存照常展示。
        SubpageScreen.DORM_REPAIR -> DormRepairScreen(
            onBack = pop,
            focusItemId = route.arg,
        )

        // ---------------------------------------------------------------- 账号中心
        //
        // 十个入口全部 push 路由：已搬的进真页面，没搬的落在 IosNotPortedScreen。
        // 账号中心自己会按 `accountCredentials()` / 各仓库是否为 null 决定显示什么，
        // 所以 iOS 上它表现为「全部未配置」——那是真实结论（凭证存储还没接全）。
        SubpageScreen.ACCOUNTS -> AccountsHubScreen(
            onBack = pop,
            onOpenJw = { push(IosRoute(SubpageScreen.JW_ACCOUNT)) },
            onOpenYkt = { push(IosRoute(SubpageScreen.CAMPUS_CARD_SETTINGS)) },
            onOpenQiekj = { push(IosRoute(SubpageScreen.WATER)) },
            onOpenQzxy = { push(IosRoute(SubpageScreen.QZXY)) },
            onOpenUjing = { push(IosRoute(SubpageScreen.UJING)) },
            onOpenKvcx = { push(IosRoute(SubpageScreen.KVCX)) },
            onOpenRide = { push(IosRoute(SubpageScreen.RIDE)) },
            onOpenWenqu = { push(IosRoute(SubpageScreen.WENQU)) },
            onOpenAiSettings = { push(IosRoute(SubpageScreen.AI_SETTINGS)) },
        )

        // ---------------------------------------------------------------- AI 调课
        //
        // 三页在 iOS 上都能跑：客户端与调课历史仓都是 `appDataDir()` 落盘（两端同一份），
        // API Key 走 Keychain。图片那套（截图 / 相册）iOS 没有，`imageUi = null`
        // 让输入区的图片入口整行不渲染。
        SubpageScreen.AI_SCHEDULE -> {
            val vm = remember {
                AiChatViewModel(
                    repo = services.scheduleRepository(),
                    client = services.aiScheduleClient(),
                    historyStore = services.aiApplyHistoryStore(),
                    imageStore = null,
                    apiKeys = services.aiKeys,
                )
            }
            AiChatScreen(
                viewModel = vm,
                onBack = pop,
                onOpenSettings = { push(IosRoute(SubpageScreen.AI_SETTINGS)) },
                onOpenHistory = { push(IosRoute(SubpageScreen.AI_HISTORY)) },
                imageUi = null,
            )
        }

        SubpageScreen.AI_SETTINGS -> {
            val vm = remember {
                AiSettingsViewModel(
                    repo = services.scheduleRepository(),
                    client = services.aiScheduleClient(),
                    apiKeys = services.aiKeys,
                )
            }
            AiSettingsScreen(viewModel = vm, onBack = pop)
        }

        SubpageScreen.AI_HISTORY -> {
            val vm = remember {
                AiHistoryViewModel(
                    repo = services.scheduleRepository(),
                    historyStore = services.aiApplyHistoryStore(),
                )
            }
            AiHistoryScreen(viewModel = vm, onBack = pop)
        }

        else -> IosNotPortedScreen(screen = route.screen, onBack = pop)
    }
}

/**
 * 「这一页还在 Android 侧」的兜底页。
 *
 * 有意做成**能看见的页面**而不是空实现：抽屉里不会列出它，但 `SettingsScreen` /
 * `JwHubScreen` 的回调参数是 `SubpageScreen`，编译器不认白名单，所以必须有一个兜底。
 * 落在这一页时用户看到的是确定的一句话，不是「点了没反应」。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IosNotPortedScreen(screen: SubpageScreen, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("iOS 移植中", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(AppIcons.ArrowLeft01, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "这一页还在 Android 侧",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.padding(top = 8.dp))
            Text(
                text = "「${screen.name}」还没有搬进共享层。\n" +
                    "完整清单见 design/ios-port.md §12。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * 左侧抽屉（iOS 版）：分组列表，点条目 push 一个二级页。
 *
 * 与 Android 的 `SideDrawer` 是**同一份内容、不同外壳**：Android 用 `StackedDrawerState`
 * 做手势与视差，iOS 这一版先给「遮罩 + 面板 + 点外部关闭」，手势等页面搬齐再补。
 */
@Composable
internal fun IosDrawer(
    onPick: (IosRoute) -> Unit,
    onDismiss: () -> Unit,
) {
    val entries = remember { iosDrawerEntries() }
    val sections = remember(entries) { entries.groupBy { it.section } }

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
                .clickable(onClick = onDismiss),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(end = 64.dp)
                .background(MaterialTheme.colorScheme.surface),
        ) {
            Text(
                text = "全部功能",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 8.dp),
            )
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            ) {
                sections.forEach { (section, rows) ->
                    item(key = "h-$section") {
                        Text(
                            text = section,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 4.dp),
                        )
                    }
                    items(rows, key = { it.route.screen.name }) { entry ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(entry.route) }
                                .padding(horizontal = 12.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(entry.label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }
    }
}
