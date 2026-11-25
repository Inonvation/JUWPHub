package edu.jxslu.schedule.ui.life

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import edu.jxslu.schedule.Graph
import edu.jxslu.schedule.R
import edu.jxslu.schedule.data.ujing.UjingHouseStore
import edu.jxslu.schedule.data.ujing.UjingRepository
import edu.jxslu.schedule.domain.MoneyFormat
import edu.jxslu.schedule.domain.ShortcutSettings
import edu.jxslu.schedule.ui.campus.CampusCardViewModel
import edu.jxslu.schedule.ui.common.NoticeTone
import edu.jxslu.schedule.ui.common.ShortcutLauncher
import edu.jxslu.schedule.ui.common.ShortcutPinner
import edu.jxslu.schedule.ui.common.ShortcutSheet
import edu.jxslu.schedule.ui.common.postNotice
import edu.jxslu.schedule.ui.common.rememberResumeTick
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

/** 侧栏 U净 行右侧空闲状态的刷新节流：空闲看板本身约 30 秒滞后，刷更勤没意义。 */
private const val UJING_FREE_REFRESH_TTL_MILLIS = 30_000L

/** 侧栏 U净 聚合的并发上限（与 U净 页看板同口径，别一次打满收藏店）。 */
private const val UJING_FREE_CONCURRENCY = 4

/** 侧栏寝室电费读数的刷新节流（2026-10-22：旧生活页删除后，侧栏是它的唯一展示位）。 */
private const val DRAWER_POWER_REFRESH_TTL_MILLIS = 60_000L

/** 侧栏校园卡余额的刷新节流（余额仓库自身还有 60 秒缓存）。 */
private const val DRAWER_CAMPUS_REFRESH_TTL_MILLIS = 60_000L

/**
 * 拉收藏洗衣房的洗衣机空闲聚合（侧栏 U净 行右侧的展示数）。
 * 多店求和（free / total），数据源与 U净 页看板同源：`stores/near?keyword=<店名>`
 * 的门店统计（`devices/reserve` 在本校已被服务端关闭）。
 * 未登录 / 没收藏店 / 没有任何成功数据 → null：右侧不显示状态，整行点击直接进页。
 */
private suspend fun loadUjingFreeText(
    repo: UjingRepository,
    houseStore: UjingHouseStore,
): String? {
    if (!repo.loggedIn.value) return null
    val houses = houseStore.list()
    if (houses.isEmpty()) return null
    // 多店并发、单店失败只算这一家不参与求和（`runCatching` 会把取消一起吞掉，
    // 这里手写 catch：侧栏收起时必须照常向上抛，不把取消当失败）。
    val limiter = Semaphore(UJING_FREE_CONCURRENCY)
    val lines = coroutineScope {
        houses.map { house ->
            async {
                try {
                    val groups = limiter.withPermit { repo.storeCounts(house.name) }
                    repo.boardLine(groups)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null
                }
            }
        }.awaitAll()
    }.filterNotNull()
    val free = lines.sumOf { it.free }
    val total = lines.sumOf { it.total }
    if (lines.isEmpty() || total <= 0) return null
    return if (free > 0) "空闲 $free/$total" else "暂无空闲"
}

/**
 * 左侧栏内容（2026-10-20 第二轮）。
 *
 * - 头部用「我的」页同一份身份数据（姓名 / 遮罩学号 / 班级），不再写死应用名；
 *   头像 + 姓名 + 班级整块可点，进学籍信息页（2026-10-21，入口从教务 Tab 挪来）；
 * - 校园卡与电费都是可点的余额行，详情弹层里放账单与充值；右侧一枚充值入口开选择弹层；
 *   电子账户与寝室电费并排一行（2026-10-21），省纵向空间；
 * - 生活（取快递 / 百宝箱）· 江水生活（快趣 / 胖乖 / 趣智 / U净 / 问渠）
 *   · 学习（笔记 / 作业）三段，2026-10-21 起取消「工具」段：百宝箱并入生活，
 *   五张服务卡单列品牌段；天气行 2026-11-21 删除，天气页只从今日页顶栏那行进
 *   （§3.29），侧栏不再重复挂入口；
 *   收件箱不进侧栏（2026-10-21 收口：唯一展示入口在今日页收件箱卡）；
 * - 底部左设置、右付款码，`navigationBarsPadding` 让开手势条，不再贴屏幕底。
 *
 * ## 2026-11-25：内容件与分组数据已搬进 `shared/commonMain`
 *
 * 本文件现在只剩**壳**：手势视差、`Graph` 装配、`viewModel()`、品牌位图资源 id 与刷新闸门。
 * 头部 / 钱包区 / 行 / 段 / 两个弹层在 `ui/life/SideDrawerParts.kt`，三段的内容
 * （哪几行、写什么、点开什么）在 `ui/life/SideDrawerGroups.kt` 的 [lifeDrawerGroups]。
 * 分界线的判据写在 `SideDrawerParts.kt` 的文件头：要 `LocalContext` / `Graph` / `R` /
 * `viewModel()` 的留在这里，收「字符串 + 布尔 + 回调」的搬走。
 */
@Composable
fun SideDrawerContent(
    onOpenAccounts: () -> Unit,
    /** 头像 / 姓名 / 班级那一块（2026-10-21）：点开教务学籍信息页。 */
    onOpenStudentInfo: () -> Unit,
    onOpenPayCode: () -> Unit,
    onOpenShortcuts: (String?) -> Unit,
    onOpenCainiao: () -> Unit,
    /** U净：整行进二级页；右侧空闲状态点开的是入口弹窗（在案订单 + 扫码 + 概览）。 */
    onOpenUjing: () -> Unit,
    onOpenUjingSheet: () -> Unit,
    onOpenRide: () -> Unit,
    /** 胖乖生活：整行进二级页；右侧余额点开的是入口弹窗（余额 / 积分 / 开水 / 结算）。 */
    onOpenWater: () -> Unit,
    onOpenWaterSheet: () -> Unit,
    /** 趣智校园：整行进二级页；右侧余额点开的是入口弹窗（余额 / 设备 / 用水状态 / 结算）。 */
    onOpenQzxy: () -> Unit,
    onOpenQzxySheet: () -> Unit,
    /** 一卡通账号：进校园卡设置页（学号 + 查询密码、余额 / 电费提醒，DESIGN §3.10）。 */
    onOpenCampusAccount: () -> Unit,
    /** 胖乖小票余额文案（null = 不显示）。 */
    waterBalance: String?,
    /** 趣智余额文案（null = 不显示）。 */
    qzxyBalance: String?,
    /** 问渠总工时文案（如「12.5 工时」；null = 未登录 / 还没取到，不显示）。 */
    wenquHours: String?,
    /**
     * 四行服务各自的「进行中」标记（2026-10-21）：行描边高亮的依据。
     * 由调用侧（MainActivity）从各真相源收集——快趣骑行/免费计时、胖乖 Working、
     * 趣智 watering、U净 在案订单；这里只消费布尔，不发任何请求。
     */
    serviceActive: ServiceActiveFlags = ServiceActiveFlags(),
    /** 侧栏当前是否可见（U净 空闲摘要只在可见时拉，不空发请求）。 */
    drawerVisible: Boolean,
    onOpenWenqu: () -> Unit,
    onOpenNotes: () -> Unit,
    onOpenHomework: () -> Unit,
    onOpenToolbox: () -> Unit,
    onOpenStatement: () -> Unit,
    onOpenPowerBill: () -> Unit,
    onOpenCampusRecharge: () -> Unit,
    onOpenPowerRecharge: () -> Unit,
    onOpenSettings: () -> Unit,
    onScan: () -> Unit,
    /**
     * 壳层提示通道（2026-11-06）：侧栏自己**不再建** `SnackbarHostState`。
     *
     * 为什么必须是外面这一份：侧栏是 300dp 宽的 Surface，自建宿主会被裁掉右侧；
     * 更要紧的是全 App 其余 54 处都走 `AppSnackbarHost` + `showNotice`（语气图标、
     * 「新提示顶掉旧提示」），侧栏用 M3 原生 `showSnackbar` 就是两套观感 + 排队语义
     * ——用户在这里连点两次，第二条要等第一条过期才露面。
     * 由 `MainActivity` 传壳层的 `shellSnackbar`（它的宿主挂在根层，不被侧栏裁）。
     */
    snackbar: SnackbarHostState,
) {
    val context = LocalContext.current
    val prefs = remember { Graph.displayPrefs(context) }
    val profileName by prefs.profileName.collectAsStateWithLifecycle(initialValue = "")
    val profileClass by prefs.profileClass.collectAsStateWithLifecycle(initialValue = "")
    val profileStudentId by prefs.profileStudentId.collectAsStateWithLifecycle(initialValue = "")
    val resumeTick = rememberResumeTick()
    val vault = remember { Graph.credentialVault(context) }
    val casUsername = remember(resumeTick) { vault.readCas()?.username }
    val yktUsername = remember(resumeTick) { vault.readYkt()?.username }
    val username = casUsername ?: yktUsername ?: profileStudentId

    val campusVm: CampusCardViewModel = viewModel(
        // 侧栏是常驻组合：不在冷启动取数，展开时由下面的 effect 显式刷新
        factory = CampusCardViewModel.Factory(context.applicationContext, autoFetch = false),
    )
    val lifeVm: LifeViewModel = viewModel(factory = lifeViewModelFactory(context))
    val campusEnabled by campusVm.enabled.collectAsStateWithLifecycle()
    // 百宝箱总开关（设置 → 功能）：2026-10-22 顶栏「+」删除后，入口只剩侧栏这一行，
    // 开关必须继续生效——关掉时整行不渲染（不是置灰）。
    val toolboxEnabled by prefs.toolboxEnabled.collectAsStateWithLifecycle(initialValue = true)
    val balance by campusVm.balance.collectAsStateWithLifecycle()
    val balanceLoaded by campusVm.balanceLoaded.collectAsStateWithLifecycle()
    // 到账等待标识（2026-10-22）：旧生活页钱包卡的状态文案随页面删除，挪到侧栏钱包区。
    // campusVm 与壳层 RechargeHost 是同一个实例（同 key 的默认 viewModel），这里订阅的
    // 就是充值弹层发起的那条轮询。
    val arrivalWatching =
        campusVm.arrivalState.collectAsStateWithLifecycle().value is CampusCardViewModel.ArrivalState.Watching
    val lifeState by lifeVm.uiState.collectAsStateWithLifecycle()
    val power = lifeState.power
    val snapshot = balance
    val remain = power.snapshot?.meter?.remain
    val balanceText = when {
        !campusEnabled -> "未开启"
        snapshot != null -> MoneyFormat.fromFen(snapshot.cardFen)
        balanceLoaded -> "—"
        else -> "读取中…"
    }
    val accountValue = snapshot?.accountFen
        ?.let { MoneyFormat.fromFen(it) }
        ?: "—"
    val powerText = when {
        // 有读数就先留着：刷新期间不再退回「读取中…」（侧栏展开即静默刷一轮，
        // 每次把已显示的电量抹掉再填回来，读起来像在闪）。无凭证 / 未绑定房间
        // 是终态，优先于旧读数——那份读数已经不属于当前房间。
        power.noCredentials || power.roomNotBound -> "—"
        remain != null -> "%.1f 度".format(remain)
        power.loading -> "读取中…"
        power.error != null -> "读取失败"
        else -> "—"
    }

    // U净 行右侧的空闲摘要（2026-10-21）：侧栏**展开时**拉一次收藏店聚合，带 30 秒节流
    // （与空闲看板「云端数据约 30 秒滞后」同频，刷更勤没有意义）。未登录 / 无收藏店
    // 直接置 null：不显示状态、整行点击只进页，右侧弹窗入口随之隐藏。
    // **判据是 drawerVisible 不是组合存活**：侧栏是常驻组合（被上层盖住不离树），
    // 冷启动就发请求等于「没人看的查询打扰第三方接口」——与 MainActivity 余额刷新
    // 同一道闸（drawer.isVisible）。
    val ujingRepo = remember { Graph.ujing(context) }
    val ujingHouseStore = remember { Graph.ujingHouses(context) }
    val ujingLoggedIn by ujingRepo.loggedIn.collectAsStateWithLifecycle()
    var ujingFreeText by remember { mutableStateOf<String?>(null) }
    var ujingFreeLoading by remember { mutableStateOf(false) }
    var ujingFreeRefreshedAt by remember { mutableStateOf(0L) }
    // 在途请求的序号：侧栏收起会取消这个 effect，`finally` 里只有「仍是最新一次请求」
    // 才能把 loading 抹掉——否则收起瞬间取消，转圈会一直挂到下一次节流窗口结束。
    val ujingFreeRequest = remember { AtomicInteger() }
    LaunchedEffect(ujingLoggedIn, resumeTick, drawerVisible) {
        if (!drawerVisible) return@LaunchedEffect
        if (!ujingLoggedIn) {
            ujingFreeText = null
            // 退出登录清掉节流起点：重新登录后应立刻能取到空闲数，不等旧的 30 秒窗口
            ujingFreeRefreshedAt = 0
            return@LaunchedEffect
        }
        val now = System.currentTimeMillis()
        // 节流窗口内不重拉（resumeTick 驱动的回来刷新也走同一道闸）
        if (now - ujingFreeRefreshedAt < UJING_FREE_REFRESH_TTL_MILLIS) return@LaunchedEffect
        val token = ujingFreeRequest.incrementAndGet()
        ujingFreeLoading = true
        try {
            ujingFreeText = withContext(Dispatchers.IO) {
                loadUjingFreeText(ujingRepo, ujingHouseStore)
            }
            // 只在正常走完时落节流时刻：侧栏收起取消掉的请求不算数，下次展开要补拉
            ujingFreeRefreshedAt = now
        } finally {
            if (ujingFreeRequest.get() == token) ujingFreeLoading = false
        }
    }

    // 寝室电费读数：旧生活页删除后，侧栏是它唯一的常驻展示位——展开侧栏时静默刷一轮
    // （60 秒节流；仓库还有 120 秒缓存，切来切去不会真的重复打平台）。
    var powerRefreshedAt by remember { mutableStateOf(0L) }
    LaunchedEffect(drawerVisible, resumeTick) {
        if (!drawerVisible) return@LaunchedEffect
        val now = System.currentTimeMillis()
        if (now - powerRefreshedAt < DRAWER_POWER_REFRESH_TTL_MILLIS) return@LaunchedEffect
        powerRefreshedAt = now
        lifeVm.refreshPower(force = false)
    }

    // 校园卡余额：侧栏展开时静默补一轮（60 秒节流，仓库自身还有 60 秒缓存）。
    // 侧栏 VM 的 autoFetch=false，冷启动不再为没人看的卡片打两条请求。
    var campusRefreshedAt by remember { mutableStateOf(0L) }
    LaunchedEffect(drawerVisible, resumeTick, campusEnabled) {
        if (!drawerVisible || !campusEnabled) return@LaunchedEffect
        val now = System.currentTimeMillis()
        if (now - campusRefreshedAt < DRAWER_CAMPUS_REFRESH_TTL_MILLIS) return@LaunchedEffect
        campusRefreshedAt = now
        campusVm.refreshBalance(force = false)
    }

    var showShortcutSheet by remember { mutableStateOf(false) }
    var showRechargeChoice by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<WalletDetail?>(null) }
    val shortcutSettings by remember(context) { Graph.repository(context).shortcutSettings }
        .collectAsStateWithLifecycle(initialValue = ShortcutSettings())
    // 「其他」分类的展开态（2026-11-01）：默认折叠，用户的显式选择落 DataStore。
    val shortcutOtherExpanded by prefs.shortcutOtherExpanded
        .collectAsStateWithLifecycle(initialValue = false)
    // 提示走壳层那一份 [snackbar]（由 `MainActivity` 传 `shellSnackbar`）：
    // 侧栏是 300dp 宽的 Surface，自建宿主会被裁、观感也与全 App 其余 54 处不同
    // （M3 原生浮条近黑无图标、还是排队语义）。见 `AppNotice.postNotice` 的 KDoc。
    val scope = rememberCoroutineScope()
    val notice: (String) -> Unit = { message ->
        scope.postNotice(snackbar, message, NoticeTone.Warning)
    }

    Box(
        Modifier
            .fillMaxSize()
            // 侧栏收起时是被主页面盖住的后层，但它仍在组合树里：不清语义的话
            // TalkBack 会读到看不见的入口。展开（含动画中）才恢复。
            .then(if (drawerVisible) Modifier else Modifier.clearAndSetSemantics {}),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            val scrollState = rememberScrollState()
            // 底部 16dp 渐隐只在「下面还有内容」时出现：短屏滚动到一半能看出来还能滑。
            val drawerBg = MaterialTheme.colorScheme.surfaceContainerLow
            Column(
                modifier = Modifier
                    .weight(1f)
                    .drawWithContent {
                        drawContent()
                        if (scrollState.canScrollForward) {
                            val fade = 16.dp.toPx()
                            drawRect(
                                brush = Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, drawerBg),
                                    startY = size.height - fade,
                                    endY = size.height,
                                ),
                                topLeft = Offset(0f, size.height - fade),
                                size = Size(size.width, fade),
                            )
                        }
                    }
                    .verticalScroll(scrollState),
            ) {
                DrawerHeader(
                    name = profileName,
                    studentId = username,
                    className = profileClass,
                    onOpenStudentInfo = onOpenStudentInfo,
                    onScan = onScan,
                )
                AccountRow(onClick = onOpenAccounts)
                WalletHero(
                    balanceText = balanceText,
                    accountValue = accountValue,
                    powerText = powerText,
                    arrivalWatching = arrivalWatching,
                    onCampusDetail = { detail = WalletDetail.Campus },
                    onPowerDetail = { detail = WalletDetail.Power },
                    onRecharge = { showRechargeChoice = true },
                )
                // 2026-10-21 用户口径：百宝箱 / 天气从「工具」并入「生活」；
                // 快趣 / 胖乖 / 趣智 / U净 / 问渠五张服务卡单列「江水生活」。
                // 天气行 2026-11-21 删除：天气页只从今日页顶栏天气句进，侧栏不重复挂入口。
                // 2026-11-25：三段的行与徽标数据在共享层 `lifeDrawerGroups`（iOS 要同一份）。
                DrawerGroups(
                    lifeDrawerGroups(
                        toolboxEnabled = toolboxEnabled,
                        serviceActive = serviceActive,
                        brands = DrawerBrandIds(
                            kqcx = R.drawable.ic_brand_kqcx,
                            pangguai = R.drawable.ic_brand_pangguai,
                            qzxy = R.drawable.ic_brand_qzxy,
                            ujing = R.drawable.ic_brand_ujing,
                            wenqu = R.drawable.ic_brand_wenqu,
                        ),
                        waterBalance = waterBalance,
                        qzxyBalance = qzxyBalance,
                        ujingFreeText = ujingFreeText,
                        ujingFreeLoading = ujingFreeLoading,
                        wenquHours = wenquHours,
                        onOpenShortcutSheet = { showShortcutSheet = true },
                        onOpenToolbox = onOpenToolbox,
                        onOpenCampusAccount = onOpenCampusAccount,
                        onOpenRide = onOpenRide,
                        onOpenWater = onOpenWater,
                        onOpenWaterSheet = onOpenWaterSheet,
                        onOpenQzxy = onOpenQzxy,
                        onOpenQzxySheet = onOpenQzxySheet,
                        onOpenUjing = onOpenUjing,
                        onOpenUjingSheet = onOpenUjingSheet,
                        onOpenWenqu = onOpenWenqu,
                        onOpenNotes = onOpenNotes,
                        onOpenHomework = onOpenHomework,
                    ),
                )
                Spacer(Modifier.height(16.dp))
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SettingsEntry(onClick = onOpenSettings)
                PaymentCodeEntry(onClick = onOpenPayCode)
            }
        }
    }

    if (showShortcutSheet) {
        ShortcutSheet(
            settings = shortcutSettings,
            otherExpanded = shortcutOtherExpanded,
            onOtherExpandedChange = { expanded ->
                scope.launch { prefs.setShortcutOtherExpanded(expanded) }
            },
            onDismiss = { showShortcutSheet = false },
            onLaunchItem = { item -> ShortcutLauncher.launch(context, item)?.let(notice) },
            onPinItem = { item -> ShortcutPinner.pin(context, item)?.let(notice) },
            onEdit = { itemId ->
                showShortcutSheet = false
                onOpenShortcuts(itemId)
            },
            onAdd = {
                showShortcutSheet = false
                onOpenShortcuts(null)
            },
            onOpenCainiao = {
                showShortcutSheet = false
                onOpenCainiao()
            },
        )
    }

    if (showRechargeChoice) {
        RechargeChoiceSheet(
            onDismiss = { showRechargeChoice = false },
            onPickCampus = {
                showRechargeChoice = false
                onOpenCampusRecharge()
            },
            onPickPower = {
                showRechargeChoice = false
                onOpenPowerRecharge()
            },
        )
    }

    detail?.let { which ->
        WalletDetailSheet(
            detail = which,
            campusCardFen = snapshot?.cardFen,
            campusAccountFen = snapshot?.accountFen,
            campusAt = snapshot?.fetchedAtMs,
            powerRemain = remain,
            powerRoom = power.snapshot?.meter?.room?.room,
            powerAt = power.snapshot?.meter?.fetchedAtMs,
            campusRechargeLocked = arrivalWatching,
            onDismiss = { detail = null },
            // 弹层退场由 WalletDetailSheet 的 dismisser 负责，这里只做动作
            onOpenBill = {
                if (which == WalletDetail.Campus) onOpenStatement() else onOpenPowerBill()
            },
            // 详情弹层已经知道是哪一项：直接进对应充值，不再多问一次「充哪个」
            onRecharge = {
                if (which == WalletDetail.Campus) onOpenCampusRecharge() else onOpenPowerRecharge()
            },
        )
    }
}
