package edu.jxslu.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import edu.jxslu.schedule.data.jw.JwImportDiagnosis
import edu.jxslu.schedule.data.jw.JwUrls
import edu.jxslu.schedule.data.local.CourseEntity
import edu.jxslu.schedule.data.local.DB_VERSION
import edu.jxslu.schedule.data.local.juwDatabase
import edu.jxslu.schedule.data.local.juwDatabasePath
import edu.jxslu.schedule.data.repo.ScheduleTermImport
import edu.jxslu.schedule.domain.GuessTarget
import edu.jxslu.schedule.platform.Services
import edu.jxslu.schedule.ui.DataSelfTestResult
import edu.jxslu.schedule.ui.DataSelfTestStrip
import edu.jxslu.schedule.ui.common.AppBackHandler
import edu.jxslu.schedule.ui.jw.JwHubScreen
import edu.jxslu.schedule.ui.jwvw.JwWebFit
import edu.jxslu.schedule.ui.me.DisplaySettingsContent
import edu.jxslu.schedule.ui.me.MeViewModel
import edu.jxslu.schedule.ui.me.SettingsScreen
import edu.jxslu.schedule.ui.theme.JuwTheme
import edu.jxslu.schedule.ui.today.TodayScreen
import edu.jxslu.schedule.ui.today.TodayViewModel
import edu.jxslu.schedule.ui.web.WebContentView
import edu.jxslu.schedule.ui.web.WebViewController
import edu.jxslu.schedule.ui.week.WeekScreen
import edu.jxslu.schedule.ui.week.WeekViewModel
import kotlinx.coroutines.launch
import platform.UIKit.UIViewController

@Composable
private fun IosRoot(services: IosServices) {
    JuwTheme {
        var jwOpen by remember { mutableStateOf(false) }
        var tab by remember { mutableStateOf(IosTab.Today) }
        // 二级页栈：Android 是 SubpageActivity 的任务栈，iOS 用一份列表顶上（见 IosNavigation.kt）
        val stack = remember { mutableStateListOf<IosRoute>() }
        var drawerOpen by remember { mutableStateOf(false) }
        // 「我的」与课表显示设置共用同一个 VM（Android 侧是 Activity 作用域，两边等价）。
        // iOS 没有配置变更重建，`remember` 的生命周期就是这一屏；见 design/ios-port.md §11.2 ①。
        val meViewModel = remember { MeViewModel(services.scheduleRepository()) }
        val top = stack.lastOrNull()

        // 已下线的页面不进栈（`IosNavigation.IOS_REMOVED_SCREENS`）。这是最后一道闸门：
        // 白名单只挡抽屉，页面内部（账号中心 / 设置 / 收件箱）还有别的入口能推到同一页。
        val pushRoute: (IosRoute) -> Unit = { route ->
            if (route.screen !in IOS_REMOVED_SCREENS) stack.add(route)
        }

        // iOS 没有系统返回手势：二级页栈非空时把「返回」接到这个缝上（Android 是实体返回键）
        AppBackHandler(enabled = stack.isNotEmpty()) { stack.removeAt(stack.lastIndex) }

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
                    if (top != null) {
                        IosRouteContent(
                            route = top,
                            services = services,
                            meViewModel = meViewModel,
                            push = pushRoute,
                            pop = { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) },
                        )
                    } else {
                        when (tab) {
                            // 今日 Tab（V1 搬进共享层）：主屏。课程 / 作业 / 收件箱 /
                            // 猜你想用 / 开水面板都在共享层，直接可用；
                            // 四项服务卡、趣智面板、扫码、品牌位图在 iOS 上没有实现，
                            // 槽位留 null = 整块不渲染（design/ios-port.md §11.2 ④）。
                            IosTab.Today -> {
                                // iOS 没有配置变更重建，`remember` 的生命周期就是这一屏（§11.2 ①）
                                val viewModel = remember {
                                    TodayViewModel(
                                        repo = services.scheduleRepository(),
                                        homeworkRepo = services.homeworkRepository(),
                                        weatherRepo = services.weatherRepository(),
                                        // 精简版去掉了问渠：推荐卡不能推一个点不动的入口
                                        blockedGuessTargets = setOf(GuessTarget.WENQU),
                                    )
                                }
                                TodayScreen(
                                    viewModel = viewModel,
                                    // 顶栏汉堡 → 左侧抽屉（Android 同一入口）
                                    onOpenDrawer = { drawerOpen = true },
                                    // 顶栏课表句 / 「当前课表不在本学期」的 CTA → 课表 Tab
                                    onOpenWeek = { tab = IosTab.Week },
                                    // 空态「从教务导入」→ 教务登录页（WKWebView 登录后自动抓取落库）
                                    onOpenJwImport = { jwOpen = true },
                                    // 其余入口一律进二级页栈；还没搬的页面落在
                                    // `IosNotPortedScreen`（一句确定的话，不是点了没反应）。
                                    onOpenTimetableSettings = {
                                        stack.add(IosRoute(SubpageScreen.TIMETABLE_HUB))
                                    },
                                    onOpenWater = { stack.add(IosRoute(SubpageScreen.WATER)) },
                                    onOpenQzxy = { stack.add(IosRoute(SubpageScreen.QZXY)) },
                                    onOpenEbike = { stack.add(IosRoute(SubpageScreen.RIDE)) },
                                    onOpenUjing = { stack.add(IosRoute(SubpageScreen.UJING)) },
                                    onOpenDormRepair = { stack.add(IosRoute(SubpageScreen.DORM_REPAIR)) },
                                    onOpenInbox = { stack.add(IosRoute(SubpageScreen.INBOX)) },
                                    onOpenWeather = { stack.add(IosRoute(SubpageScreen.WEATHER)) },
                                    // 笔记 / 作业 / 问渠在精简版下线：可空回调传 null（= 入口不渲染），
                                    // 不再像上一版那样传一个「推了也不进栈」的 lambda
                                    // 收件箱卡点消息行：iOS 有二级页栈，所以这个槽位填得上
                                    onOpenSubpage = { screen, focus ->
                                        pushRoute(IosRoute(screen, focus))
                                    },
                                    // 扫码（onScanCode）与品牌位图（brandIcon）在 iOS 侧还没有实现，
                                    // 留 null：U净 弹窗的扫码钮点不动、猜你想用卡片没有图标位。
                                    // 升级条件写在 ui/today/TodaySlots.kt 的 KDoc 里。
                                )
                            }

                            IosTab.Week -> {
                                val viewModel = remember { WeekViewModel(services.scheduleRepository()) }
                                WeekScreen(
                                    viewModel = viewModel,
                                    // 空态的「去导入课表」→ 教务登录页（WKWebView 登录后自动抓取落库）
                                    onOpenJwImport = { jwOpen = true },
                                    // 课程长按菜单里的「笔记 / 作业」在精简版下线：两个回调传 null，
                                    // 弹窗里那两行不渲染（shared 侧 CourseDetailSheet 的可空槽位）
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
                                    // 其余平台动作（时间表管理 / 调课 / 分享 / 日历同步 / 文件选择）
                                    // 在 iOS 侧还没有实现，留默认值——对应入口本来就不渲染。
                                )
                            }

                            // 教务 Tab（S18 搬进共享层）：概览数字读本地表（Room），两端同源。
                            // 七个入口接真页面；学业 / 培养方案 / 实习基地 / 评教 / 请假 / 报修 /
                            // 校历的页面还在 `:app`，传 null = 整行不渲染（§11.2 ④）。
                            IosTab.Jw -> JwHubScreen(
                                onOpenTheorySchedule = {
                                    stack.add(IosRoute(SubpageScreen.THEORY_SCHEDULE))
                                },
                                onOpenLabSchedule = {
                                    stack.add(IosRoute(SubpageScreen.LAB_SCHEDULE))
                                },
                                onOpenScores = { stack.add(IosRoute(SubpageScreen.SCORES)) },
                                onOpenExams = { stack.add(IosRoute(SubpageScreen.EXAMS)) },
                                onOpenTextbooks = { stack.add(IosRoute(SubpageScreen.TEXTBOOKS)) },
                                onOpenSelection = { stack.add(IosRoute(SubpageScreen.SELECTIONS)) },
                                onOpenTeachingCalendar = {
                                    stack.add(IosRoute(SubpageScreen.TEACHING_CALENDAR))
                                },
                            )

                            // 「我的」= 设置首页（S17 搬进共享层）。每个入口都 push 一个路由，
                            // 没搬的页面落在 IosNotPortedScreen（一句确定的话，不是点了没反应）。
                            IosTab.Me -> SettingsScreen(
                                onOpen = { screen -> pushRoute(IosRoute(screen)) },
                                viewModel = meViewModel,
                                // 精简版：AI 调课与百宝箱整块下线，两行不渲染
                                showAiSchedule = false,
                                showToolbox = false,
                            )
                        }
                    }
                }
                // 底栏只在根层出现：二级页有自己的返回键，再压一条底栏会把返回路径搞混
                if (top == null) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        IosTab.entries.forEach { entry ->
                            TextButton(onClick = { tab = entry }) { Text(entry.label) }
                        }
                        // Android 的入口在左侧抽屉里；iOS 这一版把抽屉收到「更多」按钮后面
                        TextButton(onClick = { drawerOpen = true }) { Text("更多") }
                    }
                }
                DbSelfTestStrip()
            }
        }

        if (drawerOpen) {
            IosDrawer(
                onPick = { route ->
                    drawerOpen = false
                    pushRoute(route)
                },
                onDismiss = { drawerOpen = false },
            )
        }
    }
}

/**
 * iOS 底栏的三个 Tab。
 *
 * 2026-11-25（V1）：今日页搬进共享层后改回 Android 的原序——**今日 / 课表 / 教务**，
 * 「我的」仍在第三格（Android 是从左侧抽屉进的，iOS 这一版还没有抽屉那一格，
 * 见 `design/ios-port.md` §12.3 第一条）。今日页的四项服务卡 / 趣智面板 / 扫码
 * 在 iOS 上没有实现，槽位传 `null`，对应内容整块不渲染（§11.2 ④）。
 */
private enum class IosTab(val label: String) {
    Today("今日"),
    Week("课表"),
    Jw("教务"),
    Me("我的"),
}

/**
 * iOS 侧入口（P3，2026-11-24；M1 起是产品页面）。
 *
 * Swift 侧通过 `MainViewControllerKt.MainViewController()` 拿到这个 UIViewController，
 * 再用 `UIViewControllerRepresentable` 塞进 SwiftUI。这是 Compose Multiplatform 在 iOS 上的
 * 标准接法：Compose 负责画，Swift 只负责窗口与生命周期。
 *
 * ## 现在的页面结构（2026-11-25 起是真正的导航栈）
 *
 * | 区域 | 内容 | 说明 |
 * |---|---|---|
 * | 主体 | 二级页栈顶（[IosRouteContent]）或三个 Tab 之一 | 栈空时显示 Tab，栈非空时显示栈顶 |
 * | 底栏 | 今日 / 课表 / 教务 / 我的 + 「更多」 | 只在栈空时出现；「更多」打开左侧抽屉 |
 * | 抽屉 | [IosDrawer] | 分组列出**已经搬过来**的二级页（白名单在 `IosNavigation.kt`） |
 * | 覆盖层 | [JwLoginScreen] | 点「去导入」进来的教务登录 WebView（WKWebView） |
 * | 底部 | [DbSelfTestStrip] 一行 | 读写探针，**保留是因为它是「数据链路通了」的唯一屏上证据** |
 *
 * 路由键用的是共享层的 [SubpageScreen]（Android 的 `SubpageActivity` 消费同一份），
 * 所以两端不会出现「一边有入口、一边没有」。还没搬的页面落在 `IosNotPortedScreen`，
 * 看到的是确定的一句话而不是点了没反应；完整清单见 `design/ios-port.md` §12。
 *
 * 与 Android 的**已知差异**：iOS 是四格底栏（今日 / 课表 / 教务 / 我的），Android 是三格
 * 底栏加左侧抽屉里的「我的」。今日页 2026-11-25（V1）搬进共享层后已回到首格；四项服务卡、
 * 趣智面板与扫码在 iOS 上仍是空的（槽位 `null`），见 `design/ios-port.md` §12.3。
 *
 * 搬掉的两个旧面板：`WeekGridPreview`（样例网格）与 `LinkValidationScreen`（定点小数对照）。
 * 它们的验收目的都已达成——真网格取代样例网格，小数口径由 2419 条 JVM 单测继续钉住。
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
 * 教务导入页（M2，2026-11-25；2026-11-25 晚修：入口与重试，见下）。
 *
 * ## 流程
 *
 * 1. `WKWebView` 从**预热入口** [JwUrls.SSO_WARMUP] 起跳（不是 CAS 直链 [JwUrls.ENTRY]），
 *    用户自己填账号密码 —— iOS 没有凭证保险箱（`EncryptedSharedPreferences` 是 Android 专有），
 *    **不做自动填表**，也不存密码。
 * 2. 页面落在**学生端**（[isStudentSide]）就说明 CAS 回跳完成、Cookie 已经进
 *    `WKWebsiteDataStore`：这时跑一次 `ScheduleTermImport.archive(manual = true)`，
 *    它用 `IosJwReadSession` 读 Cookie 发 HTTP，走的是**与 Android 同一条共享抓取链**。
 * 3. 结果写成顶栏一行字；返回课表页即可看到，课表是 Room 的流，不需要手动刷新。
 *
 * ## 为什么从 SSO_WARMUP 起、而不是 CAS 直链（2026-11-25 用户报障的根因）
 *
 * 用户报「登录后要么 403 要么白屏，还没办法刷新」。上一版开的是 `ENTRY`（CAS 直链），
 * 于是第一次 `sso.jsp?ticket=` 校验时教务域**一个 cookie 都没有**——票据校验依赖预热写下的
 * `bzb_njw`，缺了就 500 / 403 或一张空页。这条根因在 [JwUrls.SSO_WARMUP] 的 KDoc 里写了半年，
 * Android 的 `JwImportScreen` 一直是按它起的（`startUrl`），只有 iOS 这一版漏了。
 *
 * 同一次报障还暴露两件事，一起补：
 *
 * - **没有刷新按钮**：Android 顶栏有，iOS 只有「返回课表」。403 那一屏本来刷一下就过去了，
 *   没按钮等于死路。现在顶栏有「刷新」（`WebViewController.reload()`）。
 * - **落地判据太宽**：上一版用 `JwUrls.isJwHost(url)` 触发导入，而 `sso.jsp` 本身就在教务域
 *   → 预热页一加载就开抓，必然「没有可用的教务会话」。现在只认学生端（端口 8080）。
 *
 * ## 自动重试
 *
 * 空白页 / 主文档加载失败 → 自动重走一次 [JwImportDiagnosis.retryUrl]（认证链上的失败回
 * `SSO_WARMUP`，因为 CAS ticket 是一次性票据，重放必然失败）。只重试 [MAX_AUTO_RETRY] 次，
 * 之后把话说明白交给用户点「刷新」——真故障时不能打转。
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
    val startUrl = JwUrls.SSO_WARMUP
    var currentUrl by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var importing by remember { mutableStateOf(false) }
    /** 已自动重试次数。非空白页落地就清零，所以「走两步又白屏」还能再救一次。 */
    var retries by remember { mutableStateOf(0) }

    // 回调挂在**控制器**上：控制器比组合活得久（视图销毁后仍在），装一次即可。
    // 内层读的 `importing` / `status` / `retries` 是 remember 的 State 委托，读到的是当前值。
    remember(controller) {
        // 重试或把话说明白：两条路都要给用户一个能动手的下一步
        fun retryOrReport(reason: String) {
            if (retries < MAX_AUTO_RETRY) {
                retries += 1
                error = null
                status = "$reason，正在重试（第 $retries 次）…"
                controller.loadUrl(JwImportDiagnosis.retryUrl(currentUrl) ?: startUrl)
            } else {
                status = null
                error = "$reason。点右上角「刷新」重试"
            }
        }

        controller.onPageFinished = { url ->
            currentUrl = url
            error = null
            // 页面适配（与 Android 同一份脚本）：课表页横向可滚、登录页表单收窄。
            // 拿不到脚本（about:blank 之类）就什么都不注入。
            JwWebFit.fitScript(url, desktop = true)?.let { script ->
                controller.evaluateJavascript(script)
            }
            if (!importing && isStudentSide(url)) {
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
            } else {
                // 空白页判据：主文档加载完但正文一个字都没有（403 / 500 的空响应就是这样）
                controller.evaluateJavascript(BLANK_PROBE_JS) { result ->
                    if (result == "0") {
                        retryOrReport("页面是空白的")
                    } else if (result != null) {
                        retries = 0
                    }
                }
            }
        }
        controller.onError = { message -> retryOrReport("加载失败：$message") }
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
                text = error ?: status ?: currentUrl ?: startUrl,
                fontSize = 11.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = when {
                    error != null -> MaterialTheme.colorScheme.error
                    status != null -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                },
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
            )
            // 403 / 白屏的手动出口：Android 顶栏一直有这一只，iOS 上一版漏了
            TextButton(onClick = { controller.reload() }) { Text("刷新") }
        }
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background),
        ) {
            WebContentView(
                controller = controller,
                url = startUrl,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** 自动重试上限：两次之后交给用户点「刷新」，真故障时不能打转。 */
private const val MAX_AUTO_RETRY = 2

/**
 * 空白页探针：返回正文字符数（去掉空白），`"0"` = 空白页。
 *
 * 用 `String(...)` 包一层是因为 iOS 的 `evaluateJavaScript` 把 JS 数字桥成 `NSNumber`，
 * 而 `WebViewController.Impl` 只 `as? String`——不转成字符串就永远拿不到值。
 */
private const val BLANK_PROBE_JS = """
(function(){
  try{
    var t = (document.body && document.body.innerText) ? document.body.innerText : '';
    return String(t.replace(/\s/g, '').length);
  }catch(e){ return 'err'; }
})()
"""

/**
 * 是否已经落在**学生端**（CAS 回跳成功之后的页面）。
 *
 * 只认端口 8080 那一套部署（[JwUrls.XSD_BASE]），外加两个只有登录后才出现的学生端路径。
 * **不能用 `JwUrls.isJwHost`**：`sso.jsp` 本身就在教务域，预热页一加载就会被当成「已登录」。
 */
private fun isStudentSide(url: String?): Boolean {
    val u = url.orEmpty()
    if (!JwUrls.isJwHost(u)) return false
    return ":8080" in u || "xsMain" in u || "xskb" in u
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
