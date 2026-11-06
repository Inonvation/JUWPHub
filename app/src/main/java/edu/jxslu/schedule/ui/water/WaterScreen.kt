package edu.jxslu.schedule.ui.water

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import edu.jxslu.schedule.Graph
import edu.jxslu.schedule.R
import edu.jxslu.schedule.data.prefs.DisplayPrefs
import edu.jxslu.schedule.data.qiekj.DeviceItem
import edu.jxslu.schedule.data.qiekj.OrderHistoryItem
import edu.jxslu.schedule.data.qiekj.ServerOrderItem
import edu.jxslu.schedule.data.session.SessionService
import edu.jxslu.schedule.domain.UnlockFlowState
import edu.jxslu.schedule.domain.UnlockResult
import edu.jxslu.schedule.domain.QzxyPhoneMask
import edu.jxslu.schedule.domain.calculateActualCost
import edu.jxslu.schedule.domain.cashPaidAmount
import edu.jxslu.schedule.domain.ticketPaidAmount
import edu.jxslu.schedule.ui.common.AppCardRow
import edu.jxslu.schedule.ui.common.AccountHeroCard
import edu.jxslu.schedule.ui.common.AppConfirmDialog
import edu.jxslu.schedule.ui.common.AppInfoDialog
import edu.jxslu.schedule.ui.common.AppNoticeVisuals
import edu.jxslu.schedule.ui.common.AppSnackbarHost
import edu.jxslu.schedule.ui.common.FooterDisclaimer
import edu.jxslu.schedule.ui.common.HeroActionButton
import edu.jxslu.schedule.ui.common.MetricCell
import edu.jxslu.schedule.ui.common.MetricsRow
import edu.jxslu.schedule.ui.common.NoticeTone
import edu.jxslu.schedule.ui.common.copyPlainText
import edu.jxslu.schedule.ui.common.SettingChoiceRow
import edu.jxslu.schedule.ui.common.SettingItem
import edu.jxslu.schedule.ui.common.SettingSwitchRow
import edu.jxslu.schedule.ui.common.SessionExpiredNotice
import edu.jxslu.schedule.ui.common.SettingsDivider
import edu.jxslu.schedule.ui.common.SettingsSection
import edu.jxslu.schedule.ui.common.WaterUnlockButton
import edu.jxslu.schedule.ui.common.postNotice
import edu.jxslu.schedule.ui.common.rememberAppHaptics
import edu.jxslu.schedule.ui.common.rememberRefreshHold
import edu.jxslu.schedule.ui.common.rememberSheetDismisser
import edu.jxslu.schedule.ui.common.showNotice
import edu.jxslu.schedule.ui.me.MeViewModel
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Copy01
import me.rerere.hugeicons.stroke.Droplet
import me.rerere.hugeicons.stroke.GlassWater
import me.rerere.hugeicons.stroke.History
import me.rerere.hugeicons.stroke.View
import me.rerere.hugeicons.stroke.ViewOff
import me.rerere.hugeicons.stroke.WalletAdd01

private val DATE_FORMAT = SimpleDateFormat("MM-dd HH:mm", Locale.CHINA)

/**
 * 胖乖生活页（DESIGN §3.4 / §4.16，页内核心动作是开水）。
 *
 * 2026-11-05 改版（与快趣 / 趣智同一骨架）：身份卡 → 账户资产三列 → 开水（设备 + 状态 +
 * 一个主动作）→ 用水记录 → 签到与偏好 → 页尾。原先 7 块等权卡片并列、余额挤在一行
 * titleMedium 里；「开水卡设置 · 点击方式」整节删除，开水按钮固定单击。
 *
 * 交互：未登录 → 登录卡（验证码 / Token 两种）；已登录 → 余额、设备、开水、
 * 状态原地切换（Idle/进行中/成功/失败）、订单快照。错误一行主因 + 详情弹窗。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WaterScreen(
    onBack: () -> Unit = {},
    /** 小组件「去开水」直达（WaterAutoStart）：进页自动开水一次；普通入口保持 false。 */
    autoStart: Boolean = false,
    viewModel: WaterViewModel = viewModel(
        factory = WaterViewModel.Factory(
            Graph.qiekj(LocalContext.current),
            Graph.qiekjWatering(LocalContext.current),
        ),
    ),
    // 开水卡显示与点击方式的读写（原 WaterSettingsScreen 的 ViewModel，2026-09-20 并入）
    prefsViewModel: MeViewModel = viewModel(
        factory = MeViewModel.Factory(Graph.repository(LocalContext.current)),
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val prefsState by prefsViewModel.uiState.collectAsStateWithLifecycle()
    val haptics = rememberAppHaptics()
    val context = LocalContext.current
    var showDeviceSheet by remember { mutableStateOf(false) }
    var detailItem by remember { mutableStateOf<Any?>(null) }
    var confirmLogout by remember { mutableStateOf(false) }
    // 充值会跳出本应用、且充错账户退不回来：先在弹窗里说清账号口径，确认后才跳转
    var confirmRecharge by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // 上次在别处判定登录过期：进页说一句为什么退回登录表单（只提示一次）
    SessionExpiredNotice(SessionService.Qiekj, state.loggedIn, snackbar)

    // 桌面胶囊直达：首帧触发一次（VM 内有防重入，未登录 / 流程进行中会自行跳过）
    LaunchedEffect(autoStart) {
        if (autoStart) viewModel.requestAutoUnlock()
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                // 登录/出水/查询的结果都在页面自己的提示卡里显示（不再用系统 Toast 的黑框）；
                // 事件都源自页面内的点击与随后的异步回调，此刻不会有我们的弹层盖在上面
                // postNotice 单开协程发：收集协程不卡在「等这条提示收场」上，
                // 事件成串来时新的提示才能当场顶掉旧的（见 AppNotice.kt）
                is WaterEvent.Notice -> scope.postNotice(snackbar, event.text, event.tone)
            }
        }
    }

    // 开阀与结算是一串「连平台 → 下单 → 出水 → 等设备回报账单」的动作，中途退出会把协程
    // 掐断：服务端可能已经预扣，而账单只有流程回报才拿得到。处理期间拦一下返回，只提示不退出；
    // 真卡住了流程会自己超时（出水 165 秒兜底 + OkHttp 总超时），之后按钮就回来了。
    // **与趣智页同一口径**（`QzxyScreen.requestBack`）：系统返回与顶栏返回共用这一个闸门——
    // 只拦顶栏的话，手势返回照样能把窗口关掉，等于没拦。
    // 比趣智页多拦一个 `PreChecking`：那边 PreChecking 只是读设备，这边它已经开始下单了。
    val flowInProgress = state.flow is UnlockFlowState.Working || state.flow is UnlockFlowState.PreChecking
    val requestBack: () -> Unit = {
        if (flowInProgress) {
            scope.launch {
                snackbar.showNotice(
                    AppNoticeVisuals(
                        "正在处理，等这一步跑完再退出（卡住的话它会自己超时）",
                        tone = NoticeTone.Warning,
                    ),
                )
            }
        } else {
            onBack()
        }
    }
    BackHandler(enabled = flowInProgress) { requestBack() }

    // 充值在支付宝里完成，回到本页（ON_RESUME）时余额要重拉，否则还显示充值前的数。
    // 与趣智页（QzxyScreen）同一套 awaiting + ON_RESUME 模式；走 refresh() 让下拉指示器跟着转。
    var awaitingRecharge by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && awaitingRecharge) {
                awaitingRecharge = false
                viewModel.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val openRecharge: () -> Unit = {
        if (QiekjRecharge.open(context)) {
            awaitingRecharge = true
        } else {
            scope.launch {
                snackbar.showNotice(
                    AppNoticeVisuals(
                        "没找到能打开支付宝的应用。充值在支付宝的胖乖生活小程序里完成，先装一个支付宝。",
                        tone = NoticeTone.Warning,
                    ),
                )
            }
        }
    }

    // 复制 Token，供另一台设备在「Token 登录」里粘贴（换机不用再收一次短信）。
    // 与趣智「复制会话串」同口径：提示按凭据写——它等于账号通行证，别让用户顺手外发。
    val copyToken: () -> Unit = {
        val token = viewModel.exportToken()
        if (token == null) {
            scope.launch {
                snackbar.showNotice(
                    AppNoticeVisuals("当前没有登录 Token", tone = NoticeTone.Warning),
                )
            }
        } else {
            copyPlainText(context, "胖乖生活 Token", token)
            scope.launch {
                snackbar.showNotice(
                    AppNoticeVisuals(
                        "Token 已复制。它等于账号通行证，只粘到本应用的「Token 登录」，别发到别处",
                        tone = NoticeTone.Warning,
                    ),
                )
            }
        }
    }

    // 根因：迁到 SubpageActivity 独立窗口后没有外层 Scaffold 垫状态栏，
    // windowInsets 归零（嵌 NavHost 时期防双倍空白的老规避）会让顶栏顶进状态栏；
    // 现走 M3 默认——TopAppBar 自行消费状态栏，contentWindowInsets 管住手势条。
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("胖乖生活") },
                navigationIcon = {
                    // 走 requestBack：流程进行中只提示不退出（见上面那个闸门）
                    IconButton(onClick = requestBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (state.loggedIn) {
                        // 退出登录已挪到页尾（2026-11-05 改版）：顶栏只留复制 Token 这个
                        // 排障入口，避免同一个危险动作在页面上出现两次
                        IconButton(onClick = copyToken) {
                            Icon(HugeIcons.Copy01, contentDescription = "复制 Token")
                        }
                    }
                },
            )
        },
        snackbarHost = { AppSnackbarHost(snackbar) },
    ) { padding ->
        if (!state.loggedIn) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                LoginSection(state = state, viewModel = viewModel)
                Disclaimer()
            }
        } else {
            // 下拉刷新：与趣智页 / 消费流水页同一套 PullToRefreshBox（2026-09-28 起，
            // 余额行的刷新按钮随之删除）。padding 收在刷新容器上而不是列表上：Scaffold 的
            // 内容从 (0,0) 铺满整屏、顶栏压在它上面，指示器挂在容器顶边时整条滑入轨迹
            // 都在顶栏后面，得拖过阈值一大截才露出半圈。
            // 指示器驻留口径见 ui/common/RefreshHold.kt（2026-11-05）：点亮只认 onRefresh，
            // busy 只续命——直接接 state.refreshing 会让「秒失败 / 逐请求抖动」漏成抽搐回弹。
            val refreshHold = rememberRefreshHold(busy = state.refreshing)
            PullToRefreshBox(
                isRefreshing = refreshHold.isRefreshing,
                onRefresh = {
                    refreshHold.begin()
                    haptics.tap()
                    viewModel.refresh()
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    QiekjHeroCard(
                        state = state,
                        onTogglePhone = { viewModel.toggleShowPhone() },
                        onRecharge = { confirmRecharge = true },
                    )

                    AccountMetricsSection(state = state)

                    UnlockCard(
                        state = state,
                        onPickDevice = { showDeviceSheet = true },
                        onToggleUsePoints = { viewModel.toggleUsePoints() },
                        onShowDetail = { detailItem = it },
                        onUnlock = { viewModel.unlock() },
                        onEndSession = { viewModel.endSession() },
                        onDismissFlow = { viewModel.dismissFlow() },
                    )

                    OrderSection(
                        orders = state.orderHistory,
                        serverOrders = state.serverOrders,
                        serverLoading = state.loadingServerOrders,
                        serverLoaded = state.serverOrdersLoaded,
                        onLoadServer = { viewModel.loadServerOrders() },
                        onClick = { detailItem = it },
                        onClickServer = { viewModel.openServerOrder(it) },
                    )

                    SignInSection(
                        state = state,
                        autoSignIn = prefsState.displayPrefs.qiekjAutoSignIn,
                        onRun = { viewModel.runDailySignIn() },
                        onSetAutoSignIn = prefsViewModel::setQiekjAutoSignIn,
                    )

                    FooterActions(onLogout = { confirmLogout = true })
                    Disclaimer()
                }
            }
        }
    }

    if (showDeviceSheet) {
        DeviceSheet(
            devices = state.devices,
            selected = state.selectedDevice,
            onSelect = {
                viewModel.selectDevice(it)
                showDeviceSheet = false
            },
            onDismiss = { showDeviceSheet = false },
        )
    }

    when (val d = detailItem) {
        is UnlockFlowState.Failed -> ErrorDetailDialog(item = d, onDismiss = { detailItem = null })
        is edu.jxslu.schedule.domain.UnlockResult -> OrderDetailDialog(item = d, onDismiss = { detailItem = null })
        is OrderHistoryItem -> OrderDetailDialog(item = toResult(d), onDismiss = { detailItem = null })
        null -> {}
    }

    // 服务端账单明细（点了「服务端账单」某一条后由 VM 拉到）
    state.serverOrderDetail?.let { result ->
        OrderDetailDialog(item = result, onDismiss = { viewModel.dismissServerOrderDetail() })
    }

    // 退出二次确认（2026-09-27）：顶栏图标就在返回键旁边，一次误触就得重新收短信才能回来
    if (confirmLogout) {
        AppConfirmDialog(
            title = "退出胖乖登录？",
            message = "将清除本机保存的胖乖 Token 与开水订单快照。" +
                "今日页开水卡回到未登录态，重新登录需要再收一次短信验证码。",
            confirmLabel = "退出登录",
            onConfirm = {
                confirmLogout = false
                viewModel.logout()
            },
            onDismiss = { confirmLogout = false },
            // 清 Token 与订单快照不可撤销：确认键染红，和「取消」区分开
            danger = true,
        )
    }

    // 充值前确认（2026-11-05）：钱进的是支付宝里的小程序账户，本应用只负责跳过去。
    // 原来这段口径写成卡片底下的小字，没人看；改成跳转前的确认弹窗，确认了才离开应用。
    if (confirmRecharge) {
        AppConfirmDialog(
            title = "前往支付宝充值？",
            confirmLabel = "去充值",
            onConfirm = {
                confirmRecharge = false
                openRecharge()
            },
            onDismiss = { confirmRecharge = false },
            // 钱是付给胖乖的，本应用只跳转；不是危险动作，确认键保持主色
            danger = false,
        ) {
            Text("将跳到支付宝的「胖乖生活」小程序，钱进你在胖乖的账户。")
            Text(
                "小程序按支付宝的登录态进账号：先核对它的手机号与本页登录的一致，充错账户退不回来。",
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                "充值与退款由胖乖官方负责，与本应用无关；由此造成的损失开发者不承担责任。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

// ── 登录 ──

/**
 * 登录卡（2026-10-07 卡片化）：验证码 / Token 两种方式收进同一张 SettingsSection，
 * 与趣智 / 快趣 / U净 的登录卡同一形态——本页其余区块都是卡片，表单裸在页面背景上
 * 会显得「没装进容器」。
 */
@Composable
private fun LoginSection(state: WaterUiState, viewModel: WaterViewModel) {
    SettingsSection(
        title = "登录",
        subtitle = "手机号短信登录；只保存登录状态，不保存密码。充值与退款走官方渠道。",
    ) {
        OutlinedTextField(
            value = state.phone,
            onValueChange = viewModel::updatePhone,
            label = { Text("手机号") },
            isError = state.phoneError != null,
            supportingText = state.phoneError?.let { { Text(it) } },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = state.code,
                onValueChange = viewModel::updateCode,
                label = { Text("验证码") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = { viewModel.sendCode() }, enabled = !state.sendingCode && state.phone.isNotBlank()) {
                Text(if (state.sendingCode) "发送中" else "发送验证码")
            }
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { viewModel.login() },
            enabled = !state.loggingIn && state.phone.isNotBlank() && state.code.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
            Text("登录", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(8.dp))
        if (!state.showTokenLogin) {
            OutlinedButton(onClick = { viewModel.toggleTokenLogin() }, modifier = Modifier.fillMaxWidth()) {
                Text("Token 登录")
            }
            Text(
                "已从其他渠道拿到 Token 时可直接粘贴登录；手机号登录会使旧 Token 失效。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                modifier = Modifier.padding(top = 6.dp),
            )
        } else {
            OutlinedTextField(
                value = state.tokenLoginInput,
                onValueChange = viewModel::updateTokenLoginInput,
                label = { Text("Token") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { viewModel.loginWithToken() },
                enabled = !state.tokenLoggingIn && state.tokenLoginInput.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) { Text("Token 登录") }
            // 切回短信登录的出口（2026-10-07 补）：此前切进 Token 模式只有输入框 + 登录键，
            // 想回去只能杀页面重进；趣智的会话串模式一直有「收起」按钮，这里对齐
            TextButton(
                onClick = { viewModel.toggleTokenLogin() },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("改用短信验证码登录") }
        }
    }
}

/**
 * 页尾退出（二次确认在宿主里）。
 *
 * 2026-11-05：原来的「开水卡设置 · 点击方式」整节删除（用户要求）——整节只有一个
 * 单击/双击选择，且只对页内大按钮生效。开水按钮现在固定单击，与今日页余额面板同口径；
 * `DisplayPrefs.waterRequireDoubleClick` 字段保留（老备份里可能带着它），但本页不再读写。
 */
@Composable
private fun FooterActions(onLogout: () -> Unit) {
    TextButton(
        onClick = onLogout,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
    ) {
        Text(
            text = "退出登录",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun Disclaimer() {
    FooterDisclaimer(
        body = "**胖乖为第三方非官方功能**，与学校及胖乖官方均无关；" +
            "模拟客户端操作可能违反平台条款，**账号与封禁风险自负**。",
    )
}

// ── 已登录内容 ──

/**
 * 身份卡（2026-11-05 改版）：登录手机号 + 「已登录」+ Token 去向 + 充值。
 *
 * 原先是「余额与充值」一张卡里塞「小票 ¥x · 积分 y」一行大字、手机号、充值按钮，
 * 数字与账号抢同一个位置；现在身份归身份、数字归三列指标（[AccountMetricsSection]）。
 */
@Composable
private fun QiekjHeroCard(
    state: WaterUiState,
    onTogglePhone: () -> Unit,
    onRecharge: () -> Unit,
) {
    val hasPhone = state.accountPhone.isNotBlank()
    val eye: (@Composable () -> Unit)? = if (hasPhone) {
        { PhoneEye(shown = state.showPhone, onToggle = onTogglePhone) }
    } else {
        null
    }
    AccountHeroCard(
        brandIcon = R.drawable.ic_brand_pangguai,
        title = if (hasPhone) {
            if (state.showPhone) state.accountPhone else QzxyPhoneMask.mask(state.accountPhone)
        } else {
            "胖乖生活"
        },
        subtitle = "Token 仅存本机 · 不进云备份",
        pill = "已登录",
        trailing = eye,
        action = {
            HeroActionButton(
                text = "充值到胖乖账户",
                icon = HugeIcons.WalletAdd01,
                onClick = onRecharge,
            )
        },
    )
}

/** 手机号显隐的眼睛（与趣智页同款）：页面会被截图、会被旁人瞥见，默认遮蔽。 */
@Composable
private fun PhoneEye(shown: Boolean, onToggle: () -> Unit) {
    Icon(
        imageVector = if (shown) HugeIcons.ViewOff else HugeIcons.View,
        contentDescription = if (shown) "隐藏手机号" else "显示手机号",
        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        modifier = Modifier
            .padding(start = 6.dp)
            .size(16.dp)
            .clickable { onToggle() },
    )
}

/**
 * 账户资产三列（2026-11-05）：小票 / 积分 / 本月用水。
 *
 * 三个数字都在手上：小票与积分来自余额接口，本月用水是本地快照里当月已完成的单数
 * （只数本月、只数完成态；快照最多 20 笔，跨月多到 20 笔以上时会少算——这也是它写在
 * 说明行「本地记录」里的原因，服务端全量在「历史订单」弹层里）。
 */
@Composable
private fun AccountMetricsSection(state: WaterUiState) {
    val balance = state.balance
    val monthStart = remember {
        java.time.LocalDate.now().withDayOfMonth(1).atStartOfDay(java.time.ZoneId.systemDefault())
            .toInstant().toEpochMilli()
    }
    val monthlyOrders = state.orderHistory.count { it.completedAt >= monthStart }
    SettingsSection(title = "账户资产") {
        MetricsRow(Modifier.padding(top = 6.dp)) {
            MetricCell(
                label = "小票",
                value = balance?.ticketText?.let { "¥$it" } ?: "—",
                caption = "可开水的余额",
                modifier = Modifier.weight(1f),
            )
            MetricCell(
                label = "积分",
                value = balance?.pointsText ?: "—",
                caption = "可抵水费",
                modifier = Modifier.weight(1f),
            )
            MetricCell(
                label = "本月用水",
                value = "$monthlyOrders 次",
                caption = "本地记录",
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * 签到与偏好（2026-11-05 收成一张卡）：签到降级为一行（状态 + 「签到」按钮），
 * 自动签到开关紧随其后。
 *
 * 原先是整卡：标题 + 副标题 + 结果行 + 44dp 大按钮 + 开关 + 免责句，一天只用一次的动作
 * 占掉三分之一屏，把真正的主动作「开水」挤到第三屏。
 *
 * 只做签到，不碰任务/领奖（2026-10-23 用户授权范围）；「打开软件自动签到」默认关闭，
 * 开启后只在每天首次前台检查一次，已签到就不再请求。手动按钮与自动签到共用同一条链路。
 */
@Composable
private fun SignInSection(
    state: WaterUiState,
    autoSignIn: Boolean,
    onRun: () -> Unit,
    onSetAutoSignIn: (Boolean) -> Unit,
) {
    SettingsSection(title = "签到与偏好") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("每日签到", style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = state.signInError
                        ?: state.signInResult?.signInText
                        ?: "每天一次，积分可在开水时抵扣",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.signInError != null) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(10.dp))
            TextButton(onClick = onRun, enabled = !state.signInRunning) {
                Text(if (state.signInRunning) "签到中…" else "签到")
            }
        }
        SettingsDivider()
        SettingSwitchRow(
            title = "打开软件自动签到",
            subtitle = "每天首次打开时检查一次，已签到不再请求",
            checked = autoSignIn,
            onCheckedChange = onSetAutoSignIn,
        )
    }
}

/**
 * 设备行（2026-11-05 并进「开水」卡）：图标 + 设备名 + 「切换 ›」。
 * 用水中 / 流程进行中不可换（与趣智同一条纪律：换设备会打断正在跑的那条链路）。
 */
@Composable
private fun DeviceRowItem(state: WaterUiState, onPick: () -> Unit) {
    val canPick = state.flow is UnlockFlowState.Idle && state.devices.isNotEmpty()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = canPick, onClickLabel = "切换开水设备") { onPick() }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            HugeIcons.Droplet,
            contentDescription = null,
            tint = if (state.selectedDevice != null) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = state.selectedDevice?.goodsName?.ifBlank { "未命名设备" } ?: "暂无设备",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = when {
                    state.flow !is UnlockFlowState.Idle -> "正在处理，等这一步跑完再换"
                    state.selectedDevice != null -> "历史设备 · 点右侧切换"
                    else -> "还没选设备，点右侧挑一台"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(4.dp))
        if (state.loadingDevices) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        } else {
            Text(
                text = if (canPick) "切换 ›" else "处理中",
                style = MaterialTheme.typography.bodySmall,
                color = if (canPick) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                maxLines = 1,
            )
        }
    }
}

/**
 * 主操作卡「开水」：设备 → 状态 → 一个主动作。
 *
 * 2026-11-05 起设备行并进本卡（原先独立一张卡），按钮固定单击
 * （用户要求；原「开水卡设置 · 点击方式」整节已删）。动作位随状态原地切换：
 * 待机「开水」→ 进行中「结束用水」→ 完成「完成」→ 失败「重试 / 关闭」。
 */
@Composable
private fun UnlockCard(
    state: WaterUiState,
    onPickDevice: () -> Unit,
    onToggleUsePoints: () -> Unit,
    onShowDetail: (Any) -> Unit,
    onUnlock: () -> Unit,
    onEndSession: () -> Unit,
    onDismissFlow: () -> Unit,
) {
    SettingsSection(title = "开水") {
        DeviceRowItem(state = state, onPick = onPickDevice)
        SettingsDivider(Modifier.padding(top = 2.dp))
        Column(Modifier.padding(top = 6.dp)) {
            UnlockStatusArea(
                state = state,
                onToggleUsePoints = onToggleUsePoints,
                onShowDetail = onShowDetail,
            )
        }
        when (val flow = state.flow) {
            is UnlockFlowState.Idle -> WaterUnlockButton(
                enabled = state.selectedDevice != null,
                onUnlock = onUnlock,
                // 单击：与今日页余额面板同口径（2026-11-05 用户要求）
                requireDoubleClick = false,
                fillMaxWidth = true,
                height = 48.dp,
                modifier = Modifier.padding(top = 8.dp),
            )
            // 出水满 5 秒，「开水进行中…」原地换成「结束用水」（动作位共用组件）
            is UnlockFlowState.PreChecking -> WaterWorkingAction(
                canEndSession = false,
                onEndSession = onEndSession,
                modifier = Modifier.padding(top = 8.dp),
            )
            is UnlockFlowState.Working -> WaterWorkingAction(
                canEndSession = flow.elapsedSeconds >= WaterViewModel.END_SESSION_MIN_SECONDS,
                onEndSession = onEndSession,
                stopping = flow.stopping,
                modifier = Modifier.padding(top = 8.dp),
            )
            is UnlockFlowState.Success -> OutlinedButton(
                onClick = onDismissFlow,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .height(48.dp),
            ) { Text("完成") }
            is UnlockFlowState.Failed -> Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            ) {
                Button(
                    onClick = onUnlock,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                ) { Text("重试") }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(
                    onClick = onDismissFlow,
                    modifier = Modifier.height(48.dp),
                ) { Text("关闭") }
            }
        }
    }
}

/** 开水状态区：Idle 显示积分抵扣开关，其余状态原地切换，不弹新卡。 */
@Composable
private fun UnlockStatusArea(
    state: WaterUiState,
    onToggleUsePoints: () -> Unit,
    onShowDetail: (Any) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // 状态切换带过渡（2026-10-05 用户反馈「突变了都」）：待机↔准备↔出水↔结算之间淡入淡出，
        // 高度差跟着 AnimatedContent 的尺寸动画走；同相位内每秒走字不触发动画
        WaterFlowStatusHost(flow = state.flow) { flow ->
            PageFlowStatus(
                flow = flow,
                usePoints = state.usePoints,
                onToggleUsePoints = onToggleUsePoints,
                onShowDetail = onShowDetail,
            )
        }
    }
}

/** 开水页状态区各相位的内容；过渡由 [WaterFlowStatusHost] 负责。 */
@Composable
private fun PageFlowStatus(
    flow: UnlockFlowState,
    usePoints: Boolean,
    onToggleUsePoints: () -> Unit,
    onShowDetail: (Any) -> Unit,
) {
    when (flow) {
        is UnlockFlowState.Idle -> Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("使用积分抵扣", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "关闭后开水不消耗积分",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                )
            }
            Switch(checked = usePoints, onCheckedChange = { onToggleUsePoints() })
        }
        is UnlockFlowState.PreChecking -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(10.dp))
            Text(
                flow.step,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
        }
        is UnlockFlowState.Working -> Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "正在出水 ${formatClock(flow.elapsedSeconds)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "${(WaterViewModel.AUTO_SETTLE_SECONDS - flow.elapsedSeconds).coerceAtLeast(0)} 秒后自动关闭",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                )
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = {
                    (flow.elapsedSeconds.toFloat() / WaterViewModel.AUTO_SETTLE_SECONDS).coerceIn(0f, 1f)
                },
                modifier = Modifier.fillMaxWidth().height(4.dp),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                flow.step,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            )
        }
        is UnlockFlowState.Success -> Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "花费 ¥${calculateActualCost(flow.result)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "订单详情 ›",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { onShowDetail(flow.result) },
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                // 支付构成副行（DESIGN §4.10 账单口径）：小票是账户余额支付，
                // 现金实付 0.00 只代表没走在线支付，不能当作「本次花费 0」
                costBreakdownText(flow.result),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            )
        }
        is UnlockFlowState.Failed -> Column {
            Text(flow.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "失败步骤：${flow.step}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "查看详情 ›",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { onShowDetail(flow) },
                )
            }
        }
    }
}

private fun formatClock(totalSeconds: Int): String {
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return "%02d:%02d".format(m, s)
}

// ── 弹层与列表 ──

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeviceSheet(
    devices: List<DeviceItem>,
    selected: DeviceItem?,
    onSelect: (DeviceItem) -> Unit,
    onDismiss: () -> Unit,
) {
    val haptics = rememberAppHaptics()
    // 选完设备后调用方直接把 showDeviceSheet 置 false，弹层会被瞬间抽掉（无退场动画）。
    // 这里先 hide() 播完退场再落状态，与 QzxyEntrySheet / WaterEntrySheet 同一口径。
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val dismiss = rememberSheetDismisser(sheetState, onDismiss)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp),
        ) {
            Text("选择开水设备", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            // 2026-11-05：用户问「能不能扫码换饮水机」，核实结论是不能——胖乖服务端没有
            // 绑定/更换端点（设备只来自 `goods/latestUsed` 这条纯读），所以这里必须说清
            // 「列表是官方历史记录」，否则用户会以为是本 App 的绑定列表、找不到「加一台」。
            Text(
                "列表来自官方胖乖生活的历史设备：需要先在官方 App / 小程序扫码用过这台饮水机，" +
                    "它才会出现在这里；本应用不支持添加或绑定新设备。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            )
            Spacer(Modifier.height(12.dp))
            if (devices.isEmpty()) {
                Text(
                    "暂无设备。请先用官方胖乖生活扫码开一次水，再回本页下拉刷新。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                )
            }
            devices.forEach { device ->
                val isSelected = device.effectiveGoodsId == selected?.effectiveGoodsId
                // 行规格与趣智设备弹层（DeviceRow）一致：选中态主色淡底 + 触感反馈
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            else Color.Transparent,
                            shape = RoundedCornerShape(10.dp),
                        )
                        .clickable {
                            haptics.tap()
                            dismiss { onSelect(device) }
                        }
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        HugeIcons.Droplet,
                        contentDescription = null,
                        tint = if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        device.goodsName.ifBlank { "未命名设备" },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

/**
 * 历史订单：卡片入口 + 底部弹层明细（2026-10-07 收口）。
 *
 * 订单列表原先整块平铺在页面上（十几笔时把「开水卡设置」一路顶出屏外），现在
 * 收成一行入口，明细装进与设备选择同规格的底部弹层。
 *
 * 2026-10-14：弹层里分两段——**本地快照**（本机结算留痕，最多 20 笔，含支付构成）与
 * **服务端账单**（`order/list`，账号下全部订单，点单笔拉 `order/detail` 看构成）。
 * 两段的分工与生活页「流水两段」同一约定：来源不同就分开列，别混。
 */
@Composable
private fun OrderSection(
    orders: List<OrderHistoryItem>,
    serverOrders: List<ServerOrderItem>,
    serverLoading: Boolean,
    serverLoaded: Boolean,
    onLoadServer: () -> Unit,
    onClick: (OrderHistoryItem) -> Unit,
    onClickServer: (ServerOrderItem) -> Unit,
) {
    var showOrders by remember { mutableStateOf(false) }
    SettingsSection(title = "历史订单", subtitle = "本地快照最多 20 笔；服务端账单看账号全部") {
        SettingItem(
            title = if (orders.isEmpty()) "还没有本地订单" else "本地 ${orders.size} 笔",
            subtitle = "点看明细",
            icon = HugeIcons.History,
            onClick = { showOrders = true },
        )
    }
    if (showOrders) {
        OrderListSheet(
            orders = orders,
            serverOrders = serverOrders,
            serverLoading = serverLoading,
            serverLoaded = serverLoaded,
            onLoadServer = onLoadServer,
            onClick = onClick,
            onClickServer = onClickServer,
            onDismiss = { showOrders = false },
        )
    }
}

/** 订单明细弹层：本地快照 + 服务端账单两段，行点击各开订单详情弹窗。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OrderListSheet(
    orders: List<OrderHistoryItem>,
    serverOrders: List<ServerOrderItem>,
    serverLoading: Boolean,
    serverLoaded: Boolean,
    onLoadServer: () -> Unit,
    onClick: (OrderHistoryItem) -> Unit,
    onClickServer: (ServerOrderItem) -> Unit,
    onDismiss: () -> Unit,
) {
    // 进弹层按需拉一次服务端账单（VM 里已拉过就跳过，不会重复请求）
    LaunchedEffect(Unit) { onLoadServer() }
    // 只补 sheetState（skipPartiallyExpanded）：弹层直接全展开，不会先停在半高再要用户往上拖。
    // 行点击刻意不套 dismiss —— 点单笔是「在弹层上叠一层详情弹窗」，弹层要留着让用户接着翻下一笔。
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            Text(
                "历史订单",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

            // ── 段一：本地快照 ──
            Spacer(Modifier.height(10.dp))
            Text(
                "本地快照 · 点单笔看支付构成",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            )
            Spacer(Modifier.height(4.dp))
            if (orders.isEmpty()) {
                Text(
                    "还没有本地订单",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            } else {
                val shown = orders.take(20)
                shown.forEachIndexed { index, order ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onClick(order) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                order.goodsName,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                            )
                            Text(
                                DATE_FORMAT.format(Date(order.completedAt)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            )
                        }
                        Text(
                            "¥${calculateActualCost(toResult(order))}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    if (index < shown.size - 1) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    }
                }
            }

            // ── 段二：服务端账单（order/list，2026-10-14） ──
            Spacer(Modifier.height(18.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Spacer(Modifier.height(12.dp))
            Text(
                "服务端账单 · 账号全部订单，点单笔看支付构成",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            )
            Spacer(Modifier.height(4.dp))
            when {
                serverLoading -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "读取中…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }
                !serverLoaded -> Text(
                    "还没拉取服务端账单",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                serverOrders.isEmpty() -> Text(
                    "服务端没有账单",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                else -> serverOrders.forEachIndexed { index, order ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onClickServer(order) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                order.machineName?.takeIf { it.isNotBlank() }
                                    ?: order.shopName?.takeIf { it.isNotBlank() }
                                    ?: "订单",
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                            )
                            Text(
                                order.createTime.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                maxLines = 1,
                            )
                        }
                        Text(
                            "¥${order.markPrice ?: "-"}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    if (index < serverOrders.size - 1) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    }
                }
            }
        }
    }
}

private fun toResult(item: OrderHistoryItem) = UnlockResult(
    orderNo = item.orderNo,
    orderId = item.orderId,
    originPrice = item.originPrice,
    ticketCost = item.ticketCost,
    integralCost = item.integralCost,
    otherPromotions = item.otherPromotions,
    completedAt = item.completedAt,
    realPrice = item.realPrice,
    payTypeName = item.payTypeName,
    tokenCoinDiscount = item.tokenCoinDiscount,
)

/**
 * 失败详情（单按钮「知道了」）。
 *
 * 2026-11-06 起是本包**唯一实现**：`WaterEntrySheet` 里原本有一份逐行等价的私有副本
 * （`EntryFailDetailDialog`，那边 KDoc 自己写着「与开水页同形态，本地等价实现」），
 * 两份各改一处就会分叉。所以改成 `internal` 供同包直接调用，不再各自维护。
 *
 * 正文比入口弹层那份多一行 [UnlockFlowState.Failed.message]：两边都走这里之后，
 * 入口弹层也显示这句人话，与「失败步骤 + 原始错误 + 建议」并列，信息只多不少。
 */
@Composable
internal fun ErrorDetailDialog(item: UnlockFlowState.Failed, onDismiss: () -> Unit) {
    AppInfoDialog(title = "失败详情", onDismiss = onDismiss, dismissLabel = "知道了") {
        Text(item.message, style = MaterialTheme.typography.bodyMedium)
        Text(
            "失败步骤：${item.step}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
        )
        if (item.rawError.isNotBlank() && item.rawError != item.message) {
            Text(
                "错误详情：${item.rawError}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            )
        }
        item.suggestions.forEach { s ->
            Spacer(Modifier.height(4.dp))
            Text("• $s", style = MaterialTheme.typography.bodySmall)
        }
    }
}

/**
 * 订单详情（单按钮「关闭」，DESIGN §4.10 账单口径）。
 *
 * 同样是本包唯一实现：`WaterEntrySheet` 的 `EntryOrderDetailDialog` 已删（两份逐行相同，
 * 连 `DetailRow` 的 64dp 标签宽与 0.55 透明度都一模一样）。改字段只改这一处。
 */
@Composable
internal fun OrderDetailDialog(item: UnlockResult, onDismiss: () -> Unit) {
    AppInfoDialog(title = "订单详情", onDismiss = onDismiss, dismissLabel = "关闭") {
        DetailRow("订单号", item.orderNo)
        DetailRow("原价", "¥${item.originPrice}")
        // 账单构成（DESIGN §4.10 账单口径）：小票是账户余额，扣了就是花了；
        // 「现金支付 ¥0.00」只代表没走在线支付，不叫「实付 0」
        ticketPaidAmount(item)?.takeIf { it > BigDecimal.ZERO }
            ?.let { DetailRow("小票支付", "¥${money(it)}") }
        if (item.integralCost != "-") DetailRow("积分抵扣", item.integralCost)
        item.otherPromotions.forEach { p ->
            DetailRow("其他优惠", p.discountAmount ?: "-")
        }
        cashPaidAmount(item)?.let { cash ->
            val channel = item.payTypeName?.let { "（$it）" } ?: ""
            DetailRow("现金支付", "¥${money(cash)}$channel")
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        DetailRow("本次花费", "¥${calculateActualCost(item)}")
    }
}

/** 金额统一显示两位小数（服务端金额字符串可能缺位）。 */
private fun money(value: BigDecimal): String =
    value.setScale(2, RoundingMode.HALF_UP).toPlainString()

/**
 * 成功卡的支付构成副行（DESIGN §4.10 账单口径）：
 * 「小票支付 ¥0.16 · 积分抵扣 0.10 · 支付宝-代扣 ¥0.06」，全空兜底「开水成功」。
 */
private fun costBreakdownText(result: UnlockResult): String {
    val parts = buildList {
        ticketPaidAmount(result)?.takeIf { it > BigDecimal.ZERO }
            ?.let { add("小票支付 ¥${money(it)}") }
        result.integralCost.takeIf { it != "-" }?.let { add("积分抵扣 $it") }
        cashPaidAmount(result)?.takeIf { it > BigDecimal.ZERO }?.let { cash ->
            add("${result.payTypeName ?: "现金"} ¥${money(cash)}")
        }
    }
    return parts.joinToString(" · ").ifBlank { "开水成功" }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.padding(vertical = 2.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            modifier = Modifier.width(64.dp),
        )
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}
