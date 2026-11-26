package edu.jxslu.schedule

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.ComposeUIViewController
import edu.jxslu.schedule.data.jw.JwImportDiagnosis
import edu.jxslu.schedule.data.jw.JwUrls
import edu.jxslu.schedule.data.repo.ScheduleTermImport
import edu.jxslu.schedule.data.session.IosJwSessionFlag
import edu.jxslu.schedule.data.ujing.UjingOrderSnapshot
import edu.jxslu.schedule.domain.AppClock
import edu.jxslu.schedule.domain.GuessTarget
import edu.jxslu.schedule.domain.ScheduleCalculator
import edu.jxslu.schedule.domain.ThemeMode
import edu.jxslu.schedule.domain.ThemePalette
import edu.jxslu.schedule.platform.Logger
import edu.jxslu.schedule.platform.Services
import edu.jxslu.schedule.platform.collectAsStateWithLifecycle
import edu.jxslu.schedule.platform.installCrashHook
import edu.jxslu.schedule.ui.common.AppBackHandler
import edu.jxslu.schedule.ui.common.drawerCardTransform
import edu.jxslu.schedule.ui.common.rememberDrawerCardTransform
import edu.jxslu.schedule.ui.icon.AppIcons
import edu.jxslu.schedule.ui.jw.JwHubScreen
import edu.jxslu.schedule.ui.jwvw.JwWebFit
import edu.jxslu.schedule.ui.jwvw.TeachingEvalScreen
import edu.jxslu.schedule.ui.me.DisplaySettingsContent
import edu.jxslu.schedule.ui.me.MeViewModel
import edu.jxslu.schedule.ui.me.SettingsScreen
import edu.jxslu.schedule.ui.onboarding.IosOnboardingJwLogin
import edu.jxslu.schedule.ui.onboarding.OnboardingScreen
import edu.jxslu.schedule.ui.theme.JuwTheme
import edu.jxslu.schedule.ui.theme.Motion
import edu.jxslu.schedule.ui.theme.ProvideFixedFontScale
import edu.jxslu.schedule.ui.today.ActiveServiceCard
import edu.jxslu.schedule.ui.today.TodayScreen
import edu.jxslu.schedule.ui.today.TodayServiceSlot
import edu.jxslu.schedule.ui.today.TodayViewModel
import edu.jxslu.schedule.ui.web.WebContentView
import edu.jxslu.schedule.ui.web.WebViewController
import edu.jxslu.schedule.ui.common.IosSharePresenter
import edu.jxslu.schedule.ui.week.iosSaveScheduleImageToGallery
import edu.jxslu.schedule.ui.week.iosShareScheduleImage
import edu.jxslu.schedule.ui.week.IosScheduleBackgroundLayer
import edu.jxslu.schedule.ui.week.IosScheduleBackgroundThumb
import edu.jxslu.schedule.ui.week.iosImportScheduleBackground
import edu.jxslu.schedule.ui.week.ScheduleShareScreen
import edu.jxslu.schedule.ui.week.WeekScreen
import edu.jxslu.schedule.ui.week.WeekViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import platform.UIKit.UIViewController

@Composable
private fun IosRoot(services: IosServices) {
    // ⑥ 组合开始（B2 启动留痕）：这一句只要出现，说明首帧已经在画了，
    // 闪退就不在「构造 / 装配」那几步上。`remember` 保证每次进程只写一条。
    remember { IosBootSteps.step("⑥ IosRoot 开始组合"); true }
    // ---- 外观：主题 / 配色 / 动态取色 / 字体缩放（2026-11-26 补） ----
    //
    // 与 Android 的 `JuwRoot`（`MainActivity.kt`）逐条对齐。2026-11-26 之前这里是裸的
    // `JuwTheme { }`：恒晴空蓝、只跟随系统深浅、`sp` 恒 1.0×——比 Android 的 0.9× 大 11%，
    // 而「设置 → 外观」里那四个开关在 iOS 上全是死的（拨了没用）。
    //
    // 首帧保真：iOS 没有 Android 那种「进程快照 + 阻塞读」的先手（这里在组合期，不能阻塞），
    // 所以极端情况下选了深色的用户会看到一帧浅色。DataStore 的首个值通常在第一帧内到达，
    // 拿这个换「不阻塞首帧」是值得的。
    // 偏好库本身也可能在建 DataStore 的那一下抛（`DisplayPrefsStore` 的属性是即时求值的，
    // 每个都去碰 `displayDataStore` 的 `lazy`）。**这里不能写 `try`**：Compose 编译器对
    // 「composable 调用出现在 try/catch 里」直接报错（2026-11-26 CI 实测
    // `Try catch is not supported around composable function invocations`），
    // 所以启动期一律用「可空 + 早返回」表达失败。
    val prefsResult = remember { runCatching { services.displayPrefs() } }
    val prefsStore = prefsResult.getOrNull()
    if (prefsStore == null) {
        IosBootCrashScreen(
            "偏好库（DisplayPrefsStore）",
            prefsResult.exceptionOrNull() ?: IllegalStateException("偏好库为空"),
        )
        return
    }
    // 四处都走 `rememberSafeFlowState`（IosBootGuard.kt）而不是 `collectAsStateWithLifecycle`：
    // 首帧正是偏好库第一次打开文件的那一下，采集期抛异常在 Kotlin/Native 上等于终止进程。
    // 采集失败就停在默认值上——外观退回晴空蓝 + 跟随系统，界面照常画。
    val themeMode = rememberSafeFlowState("themeMode", ThemeMode.System) { prefsStore.themeMode }
    val dynamicColor = rememberSafeFlowState("dynamicColor", false) { prefsStore.dynamicColor }
    val palette = rememberSafeFlowState("themePalette", ThemePalette.SkyBlue) {
        prefsStore.themePalette
    }
    val fontScaleLocked = rememberSafeFlowState("fontScaleLocked", true) {
        prefsStore.fontScaleLocked
    }
    // 「我的」与课表显示设置共用同一个 VM（Android 侧是 Activity 作用域，两边等价）。
    // iOS 没有配置变更重建，`remember` 的生命周期就是这一屏；见 design/ios-port.md §11.2 ①。
    //
    // **它构造时会开 Room 库**（`services.scheduleRepository()` → `juwDatabase()`），
    // 是根组合里除偏好库之外唯一碰真磁盘的构造点，所以同样收成「可空 + 早返回」。
    // 放这里而不是 `ProvideFixedFontScale` 的 lambda 里：那边是 `@Composable () -> Unit`，
    // 不是 inline lambda，`return` 在里面不合法。
    val meVmResult = remember { runCatching { MeViewModel(services.scheduleRepository()) } }
    val meViewModel = meVmResult.getOrNull()
    if (meViewModel == null) {
        IosBootCrashScreen(
            "我的页 ViewModel（Room 开库）",
            meVmResult.exceptionOrNull() ?: IllegalStateException("ViewModel 为空"),
        )
        return
    }
    val darkTheme = when (themeMode) {
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
        ThemeMode.System -> isSystemInDarkTheme()
    }
    // 动态取色默认关：iOS 没有 Material You 那套壁纸取色，`platformDynamicColorScheme`
    // 在这端返回 null，`JuwTheme` 自己落到内置配色——所以这个开关在 iOS 上等价于「永远走内置配色」。
    JuwTheme(darkTheme = darkTheme, dynamicColor = dynamicColor, palette = palette) {
        // 字体缩放锁定（「固定字体大小」，默认开）：与 Android 同一份实现（`ui/theme/FontScale.kt`），
        // 全 App 唯一的字形缩放覆盖点。
        ProvideFixedFontScale(locked = fontScaleLocked) {
            var jwOpen by remember { mutableStateOf(false) }
            var tab by remember { mutableStateOf(IosTab.Today) }
            // 二级页栈：Android 是 SubpageActivity 的任务栈，iOS 用一份列表顶上（见 IosNavigation.kt）
            val stack = remember { mutableStateListOf<IosRoute>() }
            // 左侧抽屉（2026-11-25 晚改版）：交给 Material3 的 `ModalNavigationDrawer`，
            // 手势（左缘右滑）、遮罩、动画、无障碍都是现成的——与 Android 的
            // `StackedDrawerState` 是两种实现、同一种手感。自己写一套手势只会多一处没法真机验的代码。
            val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
            val shellScope = rememberCoroutineScope()
            val openDrawer: () -> Unit = { shellScope.launch { drawerState.open() } }
            // 主卡的缩放 / 圆角 / 阴影（2026-11-25 晚）：与 Android 的层叠抽屉**同一份算式与同一组
            // 数值**，都在 `ui/common/DrawerCardTransform.kt`。位移上限就是侧栏宽度——侧栏开到底时
            // 主卡左缘正好落在侧栏右缘（Android 那条不变量，见该文件的类 KDoc）。
            val drawerWidthPx = with(LocalDensity.current) { IosDrawerWidth.toPx() }
            val cardTransform = rememberDrawerCardTransform(maxSlidePx = drawerWidthPx)
            // `meViewModel` 在 `IosRoot` 顶端就建好了（那里才能早返回），这里直接用。
            // 栈的「形状」= 深度 + 栈顶。**深度进键**是为了让 push 与 pop 走不同的转场：
            // `stack` 是 `mutableStateListOf`，直接拿它当 `AnimatedContent` 的键永远判成「没变」
            //（SnapshotStateList 没有结构相等），所以这里取两个快照值拼一个 `Pair`。
            val stackShape = stack.size to stack.lastOrNull()

            // 首启引导（2026-11-25 晚接上）：Android 在 `onboarding_seen` 为 false 时用独立
            // `OnboardingActivity` 拉起，iOS 没有第二个窗口，所以盖在根上。
            //
            // 默认 false→null 三态：null = 还没读到（DataStore 是异步的，组合期不能 runBlocking）。
            // 读到 true 才渲染主界面；读到 false 渲染引导；仍是 null 时主界面照常渲染——
            // 宁可直接进主界面，也不要先闪一屏空白再跳。
            val prefs = remember { services.displayPrefs() }
            var onboardingSeen by remember { mutableStateOf<Boolean?>(null) }
            LaunchedEffect(prefs) {
                onboardingSeen = runCatching { prefs.onboardingSeen.first() }.getOrDefault(true)
            }

            // 已下线的页面不进栈（`IosNavigation.IOS_REMOVED_SCREENS`）。这是最后一道闸门：
            // 白名单只挡抽屉，页面内部（账号中心 / 设置 / 收件箱）还有别的入口能推到同一页。
            val pushRoute: (IosRoute) -> Unit = { route ->
                if (route.screen !in IOS_REMOVED_SCREENS) stack.add(route)
            }

            // iOS 没有系统返回手势：二级页栈非空时把「返回」接到这个缝上（Android 是实体返回键）
            AppBackHandler(enabled = stack.isNotEmpty()) { stack.removeAt(stack.lastIndex) }

            // 顺序有意义：**登录页压在引导之上**。引导第 2 步点「去导入页登录」会打开 `jwOpen`，
            // 若把引导判在前面，那个按钮点了就毫无反应（用户会被卡在第 2 步）。
            // 关掉登录页回来，引导还在（`onboardingSeen` 仍是 false），接着走。
            if (jwOpen) {
                JwLoginScreen(services = services, onClose = { jwOpen = false })
            } else if (onboardingSeen == false) {
                // 首启引导：六屏里 iOS 现在给得起三样（`jwLogin` / `onOpenJwLogin` / `onFinish`）。
                //
                // `jwLogin` 非空 ⇒ 第 2 步是**页面内的原生表单**（与 Android 一样：填学号密码 →
                // 真登一次 CAS → 过了才存进 Keychain）。这是 2026-11-25 用户报的那个问题的正解：
                // 上一版只给「去导入页登录」一个按钮，点开 WebView 登完回来，引导页不知道成没成，
                // 于是永远显示「去登录」，用户以为白登了。
                //
                // `onOpenJwLogin` 仍然传：只在 `jwLogin` 为 null（凭证存储建不起来）时才会渲染成
                // 按钮，作为兜底出口。
                //
                // 其余三个槽位传 null：品牌位图（iOS 没有那套素材）、完成页「其他可选账号」
                // （二级页栈已由抽屉承担）、欢迎页返回 / 声明弹窗退出（iOS 没有返回键、也没有
                // `finishAffinity` 这种「整任务退场」）——对应内容整块不渲染（§11.2 ④）。
                val onboardingCredentials = remember { services.accountCredentials() }
                val onboardingJwLogin = remember(onboardingCredentials) {
                    onboardingCredentials?.let { IosOnboardingJwLogin(it) }
                }
                OnboardingScreen(
                    onFinish = { onboardingSeen = true },
                    jwLogin = onboardingJwLogin,
                    onOpenJwLogin = { jwOpen = true },
                )
            } else {
                ModalNavigationDrawer(
                    drawerState = drawerState,
                    // 后层底色 = 侧栏底色（`ModalDrawerSheet` 的默认容器色也是它）：主卡缩小后
                    // 露出的上下留白读起来要是「同一层」，而不是两套颜色。Android 的根 Box 同理。
                    // 挂在 `ModalNavigationDrawer` 的 modifier 上而不是再套一层 Box：内容层与
                    // 侧栏层都画在它上面，一层就够。
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceContainerLow),
                    // 手势开着：左缘右滑随时可用。三个 Tab 页的顶栏也都有汉堡
                    //（`TodayScreen` / `WeekScreen` / `SettingsScreen` 的 `onOpenDrawer`），
                    // 手势只是第二条路，不再是唯一的入口。
                    gesturesEnabled = true,
                    // **遮罩透明**（2026-11-25）：Android 的侧栏展开时不压暗主卡，而是把主卡缩小
                    // 让位（`ui/common/DrawerCardTransform.kt`）。同一个效果在 iOS 上如果叠一层
                    // M3 默认的 32% 黑遮罩，主卡会一边缩小一边变暗，观感是「弹了个模态框」。
                    // 遮罩本身仍留着：点它关抽屉的行为与命中区都不变，只是不画颜色。
                    scrimColor = Color.Transparent,
                    drawerContent = {
                        IosDrawer(
                            // 身份区（顶部）读的是 `services.displayPrefs()` 里学籍卡落库的三行，
                            // 与 Android 侧栏同一个 `DisplayPrefsStore`
                            services = services,
                            onPick = { route ->
                                shellScope.launch { drawerState.close() }
                                pushRoute(route)
                            },
                            // 「我的 / 设置」那一行：Android 的侧栏里也是这一行进设置首页，
                            // 不是底栏。切换完顺手关抽屉。
                            onOpenMe = {
                                shellScope.launch { drawerState.close() }
                                tab = IosTab.Me
                            },
                        )
                    },
                ) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                            // 主卡层级效果（与 Android 的层叠抽屉同一份算式）。进度 = 0 合上、1 全开。
                            //
                            // **符号是 `1 + offset/宽`，不是 `-offset/宽`**（2026-11-26 真机截图修正）。
                            // Material3 的 `DrawerState` 把侧栏面板放在 `x = offset` 处，于是
                            // **合上 = −侧栏宽、全开 = 0**。上一版按「全开 = −侧栏宽」写的，符号正好反了：
                            // 抽屉合着的时候进度被算成 1，主卡被整条侧栏宽推到右边——真机首屏就是
                            // 左边一大片空白、页面挤在右半屏。这个 bug 一直存在，只是之前被首帧
                            // NaN 崩溃挡住，修掉崩溃之后才第一次露出来。
                            //
                            // **读在绘制期**：`drawerCardTransform` 内部把它放在 `graphicsLayer` 的
                            // lambda 里求值，侧栏拖动时不会让整棵页面树重组。
                            .drawerCardTransform(cardTransform) {
                                if (drawerWidthPx > 0f) {
                                    1f + drawerState.currentOffset / drawerWidthPx
                                } else {
                                    // 密度未就绪：当合上处理（`drawerCardProgress` 也会拦 NaN/±Inf）
                                    0f
                                }
                            },
                    ) {
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    ) {
                        // 二级页栈与根层 Tab 合成**一个** AnimatedContent：`null` 就是根层。
                        // 键取「深度 + 栈顶」而不是 `stack` 本身——`mutableStateListOf` 没有结构相等，
                        // 拿它当键永远判成"没变"。
                        AnimatedContent(
                            targetState = stackShape,
                            // 铺满：滑入的位移量按容器宽度算（`{ it }` 就是整屏），不铺满会滑不到位
                            modifier = Modifier.fillMaxSize(),
                            transitionSpec = {
                                // 浅了是 pop、深了是 push。同一深度换页（不经过根层）也算 push。
                                if (targetState.first > initialState.first) {
                                    (slideInHorizontally(Motion.page()) { it } + fadeIn(Motion.page()))
                                        .togetherWith(fadeOut(Motion.exit()))
                                } else {
                                    fadeIn(Motion.exit()).togetherWith(
                                        slideOutHorizontally(Motion.pageExit()) { it } +
                                            fadeOut(Motion.pageExit()),
                                    )
                                }
                            },
                            label = "IosStack",
                        ) { shape ->
                            val route = shape.second
                            if (route != null) {
                                IosRouteContent(
                                    route = route,
                                    services = services,
                                    meViewModel = meViewModel,
                                    push = pushRoute,
                                    // 退栈走 `stack`：与 `AppBackHandler` 同一条路径
                                    pop = { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) },
                                )
                            } else {
                                AnimatedContent(
                                    targetState = tab,
                                    // 同上：上浮量按容器高度算（`it / 32`），不铺满会偏
                                    modifier = Modifier.fillMaxSize(),
                                    transitionSpec = {
                                        // 淡入 + 轻微上浮（屏高的 1/32，约 26pt）：Android 那边是纯
                                        // 交叉淡入（`MainActivity.tabEnter()`），这里多给一点方向感。
                                        // 退场只淡出、不再位移——两段都动会被读成"页面在滑动"。
                                        (fadeIn(Motion.state()) +
                                            slideInVertically(Motion.state()) { it / 32 }).togetherWith(
                                            fadeOut(Motion.exit()),
                                        )
                                    },
                                    label = "IosTab",
                                ) { currentTab ->
                                    when (currentTab) {
                                        // 今日 Tab（V1 搬进共享层）：主屏。课程 / 作业 / 收件箱 /
                                        // 猜你想用 / 开水面板 / 进行中服务卡都在共享层，直接可用；
                                        // 快趣骑行、趣智开水、趣智面板、扫码、品牌位图在 iOS 上没有实现，
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
                                            // 进行中服务卡（V2，2026-11-25 晚）：卡片本体已在共享层，
                                            // iOS 只接**开水（胖乖，HTTP）**与 **U净 洗衣**两条链；
                                            // 快趣骑行（高德 SDK）/ 趣智开水（蓝牙）/ 品牌位图在 iOS 上没有
                                            // 实现，对应槽位传 null = 整行不渲染（§11.2 ④）。
                                            // `ujingRepository()` 是可空的（落点缺失时为 null）：没有仓库就是
                                            // 一条恒空的订单流，卡片照旧只显示开水那行。
                                            val ujingRepo = remember { services.ujingRepository() }
                                            val ujingOrderFlow = remember(ujingRepo) {
                                                ujingRepo?.orderState
                                                    ?: MutableStateFlow<UjingOrderSnapshot?>(null)
                                            }
                                            val ujingOrder by ujingOrderFlow.collectAsStateWithLifecycle()
                                            val wateringFlow = remember {
                                                services.qiekjWateringSessions().watering
                                            }
                                            val watering by wateringFlow.collectAsStateWithLifecycle()
                                            TodayScreen(
                                                viewModel = viewModel,
                                                // 顶栏汉堡 → 左侧抽屉（Android 同一入口）
                                                onOpenDrawer = openDrawer,
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
                                                // 进行中服务卡：只给开水与 U净。
                                                // `pinned` 恒 false——位置判据是「有在案快趣骑行」，iOS 没有快趣。
                                                serviceCards = TodayServiceSlot(
                                                    pinned = false,
                                                    content = { cardModifier ->
                                                        ActiveServiceCard(
                                                            waterSession = watering,
                                                            ujingOrder = ujingOrder,
                                                            onOpenWater = {
                                                                stack.add(IosRoute(SubpageScreen.WATER))
                                                            },
                                                            onOpenUjing = {
                                                                stack.add(IosRoute(SubpageScreen.UJING))
                                                            },
                                                            // 「我已取走」只清订单快照：iOS 没有通知链路，
                                                            // 没有「撤完成闹钟 / 撤已发通知」那一步可做
                                                            onUjingDismiss = { ujingRepo?.dismissOrder() },
                                                            modifier = cardModifier,
                                                        )
                                                    },
                                                ),
                                                // 扫码（onScanCode）与品牌位图（brandIcon）在 iOS 侧还没有实现，
                                                // 留 null：U净 弹窗的扫码钮点不动、猜你想用卡片没有图标位。
                                                // 升级条件写在 ui/today/TodaySlots.kt 的 KDoc 里。
                                            )
                                        }

                                        IosTab.Week -> {
                                            val viewModel = remember { WeekViewModel(services.scheduleRepository()) }
                                            // 课表分享（2026-11-26 搬进共享层）：非空 = 分享板盖在课表页上。
                                            // 与课表页共用**同一个** `WeekViewModel`——分享板渲染的就是课表页
                                            // 那份状态（同一周、同一份课程与配色），另建一个会读到别的周。
                                            var shareWeek by remember { mutableStateOf<Int?>(null) }
                                            WeekScreen(
                                                viewModel = viewModel,
                                                // 顶栏左侧汉堡 → 左侧抽屉（三个 Tab 页同一入口）
                                                onOpenDrawer = openDrawer,
                                                // 空态的「去导入课表」→ 教务登录页（WKWebView 登录后自动抓取落库）
                                                onOpenJwImport = { jwOpen = true },
                                                // 切换课表弹层里的「管理课表…」与顶栏「检测 → 查看」：
                                                // 两页都已在 iOS 上可进，只是上一版没接线，点了落在空 lambda 上
                                                onOpenTimetableManage = {
                                                    stack.add(IosRoute(SubpageScreen.TIMETABLE_MANAGE))
                                                },
                                                onOpenScheduleChanges = {
                                                    stack.add(IosRoute(SubpageScreen.SCHEDULE_CHANGES))
                                                },
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
                                                // 课表分享弹层里的「导出为图片」：挂上之后那一行才渲染
                                                //（`WeekScreen` 第 4 行入口的可见性由它判，见 shared 侧）。
                                                onOpenScheduleShare = { shareWeek = it },
                                                // 课表背景图（2026-11-26 夜间，M4）：`WeekScreen` 的背景层槽位。
                                                // iOS 上画不出一张 Compose 位图（`ImageBitmap` 在 CMP 1.7.3 只读不写，
                                                // 见 design/ios-port.md §12.14），所以这一层用 `UIKitView` 垫
                                                // `UIImageView`，绕开 `ImageBitmap`。
                                                //
                                                // 三格参数取自 `WeekUiState`：文件名 / 缩放方式 / 模糊强度。
                                                // **不透明度与压暗遮罩这两根滑块在 iOS 上不生效**——前者要给 UIImageView 设
                                                // `alpha`，后者要一层 UIKit 的黑色蒙版，都超出本轮；它们在设置面板里仍在，
                                                // 拖了没反应，已知不一致。
                                                backgroundLayer = { layerModifier ->
                                                    val weekState by viewModel.uiState.collectAsStateWithLifecycle()
                                                    val name = weekState.bgImageName
                                                    if (name != null) {
                                                        IosScheduleBackgroundLayer(
                                                            fileName = name,
                                                            scale = weekState.bgImageScale,
                                                            blur = weekState.bgImageBlur,
                                                            modifier = layerModifier,
                                                        )
                                                    }
                                                },
                                                // 其余平台动作（时间表管理 / 调课 / 日历同步 / 文件选择）
                                                // 在 iOS 侧还没有实现，留默认值——对应入口本来就不渲染。
                                            )
                                            // 分享板盖在课表页之上：同一容器内的兄弟节点，全屏不透明，
                                            // 所以不需要额外的层级处理。不占二级页栈——返回即回课表，
                                            // 与请假表单（`LEAVE_HUB`）同一做法。
                                            shareWeek?.let { week ->
                                                ScheduleShareScreen(
                                                    viewModel = viewModel,
                                                    onBack = { shareWeek = null },
                                                    initialWeek = week,
                                                    // 存相册：`ImageBitmap` → PNG → `UIImage` →
                                                    // `UIImageWriteToSavedPhotosAlbum`（`Info.plist` 的
                                                    // `NSPhotoLibraryAddUsageDescription` 已在）。
                                                    onSaveToGallery = ::iosSaveScheduleImageToGallery,
                                                    // 分享：落 `Documents/exports/` 再交给系统面板。
                                                    onShareImage = ::iosShareScheduleImage,
                                                    // 背景图解码还没做（`scheduleBackgroundFiles()` 返回 null），
                                                    // 所以预览板用纯色底——与课表页一致。
                                                )
                                            }
                                        }

                                        // 教务 Tab（S18 搬进共享层）：概览数字读本地表（Room），两端同源。
                                        // 十二个入口全部接真页面；评教与「在教务网页核对」要 WebView 浏览窗口，
                                        // 传 null 即整条不渲染。
                                        // 教务 Tab（S18 搬进共享层）：概览数字读本地表（Room），两端同源。
                                        // 十三个入口；评教 2026-11-26 搬进共享层后也接上了（见下）。
                                        // 「在教务网页核对」仍旧没有内置浏览窗口 → 传 null，那条不渲染。
                                        IosTab.Jw -> {
                                        // 学生评教（2026-11-26）：整页在共享层，走同一条 WebView 缝。
                                        // 本地状态顶掉 hub 列表，返回即回列表——与请假表单、课表分享同一做法。
                                        var evalOpen by remember { mutableStateOf(false) }
                                        JwHubScreen(
                                            onOpenTeachingEval = { evalOpen = true },
                                            onOpenTheorySchedule = {
                                                pushRoute(IosRoute(SubpageScreen.THEORY_SCHEDULE))
                                            },
                                            onOpenLabSchedule = {
                                                pushRoute(IosRoute(SubpageScreen.LAB_SCHEDULE))
                                            },
                                            onOpenScores = { pushRoute(IosRoute(SubpageScreen.SCORES)) },
                                            onOpenExams = { pushRoute(IosRoute(SubpageScreen.EXAMS)) },
                                            onOpenTextbooks = { pushRoute(IosRoute(SubpageScreen.TEXTBOOKS)) },
                                            onOpenScholar = { pushRoute(IosRoute(SubpageScreen.SCHOLAR)) },
                                            onOpenCurriculum = { pushRoute(IosRoute(SubpageScreen.CURRICULUM)) },
                                            onOpenSelection = { pushRoute(IosRoute(SubpageScreen.SELECTIONS)) },
                                            onOpenInternship = {
                                                pushRoute(IosRoute(SubpageScreen.INTERNSHIP_BASES))
                                            },
                                            onOpenTeachingCalendar = {
                                                pushRoute(IosRoute(SubpageScreen.TEACHING_CALENDAR))
                                            },
                                            onOpenLeaveHub = { pushRoute(IosRoute(SubpageScreen.LEAVE_HUB)) },
                                            onOpenRepair = { pushRoute(IosRoute(SubpageScreen.DORM_REPAIR)) },
                                            onOpenCalendar = { pushRoute(IosRoute(SubpageScreen.CAMPUS_CALENDAR)) },
                                        )
                                        // 评教整页盖在 hub 之上（同一容器内的兄弟节点，全屏不透明）。
                                        // 两个平台槽位都传 null：
                                        // - `casFillScript`：iOS 的教务登录在 WKWebView 里由用户自己完成，
                                        //   没有「注入脚本自动填表」这条通道（`IosJwReadSession` 只管取数）；
                                        // - `onJwSessionReady`：那是给人看的「登录已就绪」钩子，iOS 没有对应的
                                        //   toast 位；评教本身不依赖它就能跑（会话在 `JwReadSession` 里）。
                                        if (evalOpen) {
                                            TeachingEvalScreen(onBack = { evalOpen = false })
                                        }
                                        }

                                        // 「我的」= 设置首页（S17 搬进共享层）。**不在底栏**，从抽屉进。
                                        // 每个入口都 push 一个路由，没搬的页面落在 IosNotPortedScreen。
                                        IosTab.Me -> SettingsScreen(
                                            onOpen = { screen -> pushRoute(IosRoute(screen)) },
                                            viewModel = meViewModel,
                                            // 顶栏左侧汉堡 → 左侧抽屉。这一页在 iOS 上是根 Tab（没有返回键），
                                            // 所以 navigationIcon 位归汉堡；`onBack` 不传 = null
                                            onOpenDrawer = openDrawer,
                                            // 精简版：AI 调课与百宝箱整块下线，两行不渲染；
                                            // 桌面小组件是 Glance（Android 的 Compose-for-appwidget），
                                            // iOS 要 Swift 扩展 target + App Group，本轮不做 → 整行不渲染
                                            showAiSchedule = false,
                                            showToolbox = false,
                                            showWidget = false,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    // 底栏只在根层出现：二级页有自己的返回键，再压一条底栏会把返回路径搞混。
                    // 三格 = 今日 / 课表 / 教务，与 Android `AppBottomBar` 逐格对齐；
                    // 「我的」不在底栏（`inBottomBar = false`），从左侧抽屉进，与 Android 一致。
                    // 显隐走淡入淡出（`Motion.state()`，与 Android 壳层收底栏那一处同一档）：
                    // 硬切的话，退栈时底栏会在页面滑走之前先蹦出来。
                    AnimatedVisibility(
                        visible = stack.isEmpty(),
                        enter = fadeIn(Motion.state()),
                        exit = fadeOut(Motion.state()),
                    ) {
                        // 选中主色 / 未选中 `onSurface` 0.6（用户口径）；指示块仍是 M3 默认的
                        // `secondaryContainer` 胶囊，与 Android 的选中块同一族。
                        val selectedTint = MaterialTheme.colorScheme.primary
                        val unselectedTint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        NavigationBar(
                            // 底色与高度跟 Android 的 `AppBottomBar` 走同一套 token
                            // （`NavigationBarDefaults.containerColor` / `.Elevation`）：
                            // 上一版写死 `colorScheme.surface`，浅色下底栏和页面连成一片，
                            // 看起来像「没有底栏」——这正是「外形粗糙」里最明显的一条。
                            containerColor = NavigationBarDefaults.containerColor,
                            tonalElevation = NavigationBarDefaults.Elevation,
                        ) {
                            IosTab.entries.filter { it.inBottomBar }.forEach { entry ->
                                NavigationBarItem(
                                    selected = tab == entry,
                                    onClick = { tab = entry },
                                    icon = {
                                        Icon(
                                            imageVector = entry.icon,
                                            contentDescription = entry.label,
                                        )
                                    },
                                    label = { Text(entry.label) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = selectedTint,
                                        selectedTextColor = selectedTint,
                                        unselectedIconColor = unselectedTint,
                                        unselectedTextColor = unselectedTint,
                                    ),
                                )
                            }
                        }
                    }
                    }
                }
            }
        }
    }
}

/**
 * iOS 的 Tab 清单（2026-11-25 晚，用户指定）。
 *
 * **底栏三格 = 今日 / 课表 / 教务**，与 Android `MainActivity.AppBottomBar` 逐格对齐
 * （`Calendar01` / `Book01` / `School01`，图标名在 `shared/.../ui/icon/AppIcons.kt` 里查得到）。
 *
 * 「我的」不在底栏：Android 那边它是**左侧抽屉里的一行**，iOS 现在也照这个走——
 * [inBottomBar] = false 让它只从抽屉进。这不是降级：设置首页在 Android 上本来也不是 Tab，
 * 上一版把它放进底栏是我自己的取舍，与用户要的「对齐 Android」不符。
 *
 * 抽屉里点「我的」→ `tab = IosTab.Me`；用户再点底栏任一格就离开。底栏那一排在「我的」页
 * 仍然渲染（三格都不高亮），因为 iOS 没有 Android 的独立 SettingsActivity 窗口可以退回去。
 */
private enum class IosTab(
    val label: String,
    val icon: ImageVector,
    /** 是否出现在底部导航栏。false = 只能从左侧抽屉进。 */
    val inBottomBar: Boolean = true,
) {
    Today("今日", AppIcons.Calendar01),
    Week("课表", AppIcons.Book01),
    Jw("教务", AppIcons.School01),
    Me("我的", AppIcons.UserAccount, inBottomBar = false),
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
 * | 底栏 | 今日 / 课表 / 我的（图标 + 文字，M3 `NavigationBar`） | 只在栈空时出现，三格与 Android 一致 |
 * | 抽屉 | [IosDrawer]（`ModalNavigationDrawer`） | 各页顶栏的汉堡或左缘右滑打开；分组列出**已经搬过来**的二级页（白名单在 `IosNavigation.kt`） |
 * | 覆盖层 | [JwLoginScreen] | 点「去导入」进来的教务登录 WebView（WKWebView） |
 *
 * 路由键用的是共享层的 [SubpageScreen]（Android 的 `SubpageActivity` 消费同一份），
 * 所以两端不会出现「一边有入口、一边没有」。还没搬的页面落在 `IosNotPortedScreen`，
 * 看到的是确定的一句话而不是点了没反应；完整清单见 `design/ios-port.md` §12。
 *
 * 与 Android 的**已知差异**：教务汇总页在 iOS 上不再是 Tab（它下面的每个入口都在抽屉的
 * 「教务」分组里）；其余三格与 Android 同序。**进行中服务卡** 2026-11-25 晚接上
 * （只接开水与 U净），快趣骑行 / 趣智开水 / 趣智面板 / 扫码仍是空的（槽位 `null`），
 * 见 `design/ios-port.md` §12.3。
 *
 * 搬掉的两个旧面板：`WeekGridPreview`（样例网格）与 `LinkValidationScreen`（定点小数对照）。
 * 它们的验收目的都已达成——真网格取代样例网格，小数口径由 2419 条 JVM 单测继续钉住。
 *
 * ## 装配
 *
 * [IosServices] 必须在**任何**共享 ViewModel 构造之前安装；与 Android 侧
 * `JuwApplication.onCreate` 里的 `Services.install(GraphServices(this))` 对称。
 *
 * 2026-11-26 起它挪进了根组合的 `remember { }` 里，而不是 `ComposeUIViewController` 之前。
 * 理由只有一条：**要能被捕获**。装配与首启写库都在主线程上同步跑，放进组合期后行为不变，
 * 但抛异常时能落到 [IosBootCrashScreen] 而不是让进程静默消失（见 `IosBootGuard.kt`）。
 * 顺序的不变量仍然成立——它依旧先于根组合里的第一个 ViewModel（`MeViewModel`）。
 */
fun MainViewController(): UIViewController {
    // ---- 启动阶段留痕（B2，2026-11-26） ----
    //
    // 真机闪退时唯一能拿到的东西是沙箱里 `logs/app.log` 的最后几行（「设置 → 权限与存储 →
    // 导出诊断日志」）或**出错时代替首帧显示的整屏提示**（[IosBootCrashScreen]）。
    // 所以启动路径上每一道关都留一句话：**死在哪一句之后，就是哪一步**。
    // 序号不要重排，加了新的就往后续，用户截图里的「⑤」和代码里的「⑤」必须是同一件事。
    IosBootSteps.step("① 进入 MainViewController")
    // 崩溃钩子必须是 Swift 进来之后的**第一件事**：2026-11-26 之前它挂在 `IosServices.init` 里，
    // 而服务装配一旦挪进组合期（见下），`ComposeUIViewController` 自身的初始化就跑到它前面去了
    // ——那时再崩，钩子还没装上，日志里连栈都没有。纯 Kotlin 调用，不碰系统框架，符合 §12.12。
    runCatching { installCrashHook() }
    return ComposeUIViewController {
        remember { IosBootSteps.step("⑤ Compose 根开始组合") }
        // 服务装配与首启写库都挪进组合期，唯一的目的是**让它们可被捕获**：
        // 2026-11-25/26 那几次闪退里，`IosServices()` 与 `warmUp()` 都在
        // `ComposeUIViewController` 之前同步跑，一旦抛异常进程直接消失，屏幕上什么也不留。
        // 组合期同样在主线程上，行为不变，但失败时能落到 [IosBootCrashScreen] 上。
        val booted = remember {
            runCatching {
                IosServices().also { services ->
                    Services.install(services)
                    // 首启把作息表与默认学期写进库（Android 在 JuwApplication.ensureDefaults 里
                    // 做同一件事）。这一步只碰本地库与 WebKit 的 cookie 库（后者自己切主队列），
                    // **不碰通知中心**。
                    services.warmUp()
                }
            }
        }
        val services = booted.getOrNull()
        if (services == null) {
            val error = booted.exceptionOrNull() ?: IllegalStateException("IosServices 构造后为空")
            IosBootSteps.step("② 服务装配失败：$error")
            Logger.e(TAG_BOOT, "服务装配失败", error)
            IosBootCrashScreen("服务装配（IosServices）", error)
        } else {
            remember { IosBootSteps.step("② IosServices 构造完成 / ③ Services 已安装 / ④ warmUp 已派发") }
            // 分享面板要一个「当前在屏」的 UIViewController，共享层拿不到它。
            // 这一步在根组合里把 `LocalUIViewController` 装进 `IosShareHost`，之后
            // `ShareIntents` 的两个 actual（`sharePlainText` / `shareFile`）就能自己弹面板了
            // —— 不装的话它们恒回 false，页面上表现为「点了没反应」。
            IosSharePresenter()
            // 提醒三件（前台横幅代理 / 作业截止重排 / 成绩核对）挪到**首帧之后、主线程**上。
            // 2026-11-26 之前它们藏在 `IosServices.init` 与 `warmUp` 里，等于在 Swift
            // `makeUIViewController` 执行期间就去碰 `UNUserNotificationCenter`，而那时 App
            // 还没进 active——这是真机「一开就闪退」的直接嫌疑。延迟不是为了慢，
            // 是为了让第一帧真的渲染出来（`LaunchedEffect` 在首次组合之后才跑）。
            LaunchedEffect(services) {
                delay(REMINDERS_DELAY_MS)
                runCatching { services.warmUpReminders() }
            }
            IosRoot(services)
        }
    }
}

/**
 * 启动留痕的 tag：`grep IosBoot logs/app.log` 就是一条完整的启动时间线。
 */
internal const val TAG_BOOT = "IosBoot"

/**
 * 首帧之后隔多久再动提醒。
 *
 * 1.2 秒是「首帧已经画完」的经验值：`LaunchedEffect` 本身在首次组合之后才启动，
 * 这段时间只是留给首帧上屏与页面首个数据流落地，避免刚进来就和系统通知队列抢主线程。
 */
private const val REMINDERS_DELAY_MS = 1_200L

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
        // 选图是一条挂起链（弹选择器 → 读字节 → 缩图 → 落盘 → 写偏好），
        // 由面板自己的作用域发起：面板关掉就取消，与 Android 那边 `MeViewModel.beginBackgroundImport`
        // 之后由宿主 launcher 回填的生命周期等价。
        val scope = rememberCoroutineScope()
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
                    // 背景图（2026-11-26 夜间）：三个槽位都给了真实现之后「背景」整组才会渲染
                    //（`DisplaySettingsContent` 的渲染条件就是 `onPickBackground != null`）。
                    // 选图器在 `iosImportScheduleBackground` 里（`UIDocumentPickerViewController`
                    // + `UTTypeImage`），落盘到 `appDataDir()/schedule_bg/`，与 Android 同一目录名。
                    onPickBackground = {
                        scope.launch { iosImportScheduleBackground(viewModel) }
                    },
                    // 缩略图走 `UIKitView` + `UIImageView`：这条路不需要 `ImageBitmap`
                    //（那个在 CMP 1.7.3 上只读不写，见 §12.14）。
                    backgroundThumb = { name, thumbModifier ->
                        IosScheduleBackgroundThumb(fileName = name, modifier = thumbModifier)
                    },
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
                // 刚落到学生端 = 教务会话此刻确实可用：立刻记下，账号中心/教务账号页据此显示已登录
                IosJwSessionFlag.markUsable()
                status = "已登录，正在从教务导入课表…"
                scope.launch {
                    // 归档会遍历教务下拉里的全部学期（每个学期两张页 + 请求间隔），几十秒起步。
                    // 顶栏跟着报进度：用户才知道「它在干活、别关页面」——WebView 一关，
                    // 同源取页的桥就没了（见 IosWebFetch）。
                    val result = services.scheduleTermImport().archive(manual = true) { done, total, term ->
                        status = "正在导入 $term（$done/$total）…"
                    }
                    // 读库只为把数字说清楚；读失败不能把「导入」本身判死，也不能让 importing
                    // 永远挂在 true（那会把闸门焊死，之后再也不自动重试）
                    status = runCatching { describeImport(result, services) }
                        .getOrElse { "导入结束（结果读取失败：${it.message ?: "未知"}）" }
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
                // 4 行：导入结果要能同时容下「库里几门 / 开学日 / 第几周 / 示例一门课」
                fontSize = 10.sp,
                maxLines = 4,
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
 * 把归档结果说成一句人话（2026-11-25 晚补）。
 *
 * ## 为什么必须写清数字
 *
 * 上一版只判 `is Result.Failed`，于是 `Archived(createdTerms = [], failed = [...])`——
 * 一个学期都没导入成功——也显示「导入完成，返回课表即可看到」。用户看到的就是
 * 「说成功了但课表是空的」，比报错更糟。
 *
 * 现在三种结局分开说，并且**把库里真实条数、开学日、当前周次、第一门课的例子一起打出来**：
 * 这张屏是唯一能同时看到「教务那边有什么」和「库里落了什么」的地方，用户截一张图就能定位
 * 是没导进来、还是周次/星期对不上、还是今天本来没课。
 */
private suspend fun describeImport(
    result: ScheduleTermImport.Result,
    services: IosServices,
): String = when (result) {
    is ScheduleTermImport.Result.Failed -> "导入失败：${result.reason}"

    ScheduleTermImport.Result.InProgress -> "上一次导入还在进行，等它跑完再看"

    is ScheduleTermImport.Result.Archived -> {
        if (result.createdTerms.isEmpty() && result.failed.isNotEmpty()) {
            // 一个学期都没落地：把教务给的第一条原因说出来，再带上「教务当前学期」与跳过数
            // ——这两项能区分「学期下拉没解析出来」和「页面结构变了」
            "导入失败：${result.failed.first().second}" +
                "（教务当前学期 ${result.currentTerm ?: "未识别"} · 跳过 ${result.skipped}）"
        } else {
            val repo = services.scheduleRepository()
            val courses = repo.courses.first()
            val semester = repo.semester.first()
            val week = ScheduleCalculator.todayWeekOf(semester, AppClock.today()) ?: 0
            val sample = courses.firstOrNull()?.let { c ->
                "${c.name.take(10)} ${weekdayLabel(c.day)} ${c.startSection}-${c.endSection}节 " +
                    ScheduleCalculator.formatWeeks(c.weeks)
            }
            buildString {
                if (result.createdTerms.isEmpty()) {
                    // 全部跳过（每个学期库里都已有）：这不是失败，但「本次什么都没新建」必须说出来，
                    // 否则用户以为刚导了一遍
                    append("导入完成：${result.skipped} 个学期库里已有，本次没有新建")
                } else {
                    append("导入完成：库里 ${courses.size} 门")
                    append(" · 新建 ${result.createdTerms.joinToString("、")}")
                }
                append(" · 开学 ${semester?.startDate ?: "—"} · 现第 $week 周")
                append(" · 教务 ${result.currentTerm ?: "未识别"}")
                if (result.failed.isNotEmpty()) append(" · ${result.failed.size} 个学期失败")
                if (sample != null) append("\n示例：$sample")
            }
        }
    }
}

/** 1..7 → 周一..周日（只用于上面那句示例，越界回「—」）。 */
private fun weekdayLabel(day: Int): String =
    listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日").getOrNull(day - 1) ?: "—"

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
