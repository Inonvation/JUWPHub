package edu.jxslu.schedule.ui.qzxy

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import edu.jxslu.schedule.Graph
import edu.jxslu.schedule.R
import edu.jxslu.schedule.data.qzxy.QzxyBluetoothScanner
import edu.jxslu.schedule.data.qzxy.QzxyDeviceInfo
import edu.jxslu.schedule.data.qzxy.QzxyScannedDevice
import edu.jxslu.schedule.data.session.SessionService
import edu.jxslu.schedule.domain.entrySummary
import edu.jxslu.schedule.domain.QzxyWatering
import edu.jxslu.schedule.domain.QzxyWateringFormat
import edu.jxslu.schedule.domain.QzxyPhoneMask
import edu.jxslu.schedule.ui.common.AccountHeroCard
import edu.jxslu.schedule.ui.common.AppNoticeVisuals
import edu.jxslu.schedule.ui.common.AppPermissions
import edu.jxslu.schedule.ui.common.AppConfirmDialog
import edu.jxslu.schedule.ui.common.AppSnackbarHost
import edu.jxslu.schedule.ui.common.FooterDisclaimer
import edu.jxslu.schedule.ui.common.HeroActionButton
import edu.jxslu.schedule.ui.common.MetricCell
import edu.jxslu.schedule.ui.common.MetricsRow
import edu.jxslu.schedule.ui.common.NoticeTone
import edu.jxslu.schedule.SubpageActivity
import edu.jxslu.schedule.SubpageScreen
import edu.jxslu.schedule.ui.common.SettingItem
import edu.jxslu.schedule.ui.common.SettingSwitchRow
import edu.jxslu.schedule.ui.common.SessionExpiredNotice
import edu.jxslu.schedule.ui.common.SettingsDivider
import edu.jxslu.schedule.ui.common.SettingsNote
import edu.jxslu.schedule.ui.common.SettingsSection
import edu.jxslu.schedule.ui.common.postNotice
import edu.jxslu.schedule.ui.common.RechargeRiskNote
import edu.jxslu.schedule.ui.common.rememberAppHaptics
import edu.jxslu.schedule.ui.common.rememberRefreshHold
import edu.jxslu.schedule.ui.common.rememberSheetDismisser
import androidx.compose.runtime.rememberCoroutineScope
import edu.jxslu.schedule.ui.common.showNotice
import edu.jxslu.schedule.ui.theme.Motion
import kotlinx.coroutines.launch
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Droplet
import me.rerere.hugeicons.stroke.InformationCircle
import me.rerere.hugeicons.stroke.View
import me.rerere.hugeicons.stroke.ViewOff
import me.rerere.hugeicons.stroke.WalletAdd01
import java.math.BigDecimal

/**
 * 趣智校园开热水页（DESIGN §3.18）。
 *
 * 页面按「此刻要做什么」排，一屏一个主动作：账号一行、设备一行、中间是随状态变化的
 * 状态卡，账单在下，协议调试整块搬到「诊断与调试」二级页。
 *
 * 为什么不再把调试信息留在页内：链路 2026-09-27 真机跑通之后，设备现场十四行、
 * 服务表、试签名、试清除、日志都只在协议对不上时才有用，常显会把日常动作挤到
 * 看不见的地方。需要时点页尾那一行进诊断页，内容一行没少。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QzxyScreen(
    onBack: () -> Unit = {},
    /** 打开诊断页。带当前设备地址过去，诊断页据此选中同一台。 */
    onOpenDiagnostics: (String?) -> Unit = {},
    /** 小组件「去开水」直达（WaterAutoStart）：进页自动开阀一次；普通入口保持 false。 */
    autoStart: Boolean = false,
    viewModel: QzxyViewModel = viewModel(
        factory = QzxyViewModel.Factory(
            Graph.qzxy(LocalContext.current),
            QzxyBluetoothScanner(LocalContext.current),
            Graph.qzxyLink(LocalContext.current),
            Graph.qzxyFlowLock,
            Graph.qzxyDevices(LocalContext.current),
            Graph.qzxyDebug(LocalContext.current),
            Graph.qzxyClear(LocalContext.current),
            Graph.qzxyWatering(LocalContext.current),
        ),
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 上次在别处判定登录过期：进页说一句为什么退回登录表单（只提示一次）
    SessionExpiredNotice(SessionService.Qzxy, state.loggedIn, snackbar)
    val haptics = rememberAppHaptics()
    var showDeviceSheet by remember { mutableStateOf(false) }
    var confirmLogout by remember { mutableStateOf(false) }
    // 充值会跳出本应用、且充错账户退不回来：先在弹窗里说清账号口径，确认后才跳转
    var confirmRecharge by remember { mutableStateOf(false) }
    // 发起过充值才在下次 ON_RESUME 刷余额：页面里还有权限弹窗等其他 resume 路径，
    // 无条件刷新会多打两轮请求（见下面的生命周期观察者）
    var awaitingRecharge by remember { mutableStateOf(false) }

    // 桌面胶囊直达：首帧触发一次（VM 内有防重入，未登录 / 用水中 / 流程进行中会自行跳过）
    LaunchedEffect(autoStart) {
        if (autoStart) viewModel.requestAutoOpen()
    }

    // 蓝牙扫描要运行时权限（Android 12+ 是「附近的设备」，更低版本是定位）。
    // 授权完直接接上这次点击要做的扫描，省得用户再点一次。
    val scanPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        if (result.values.all { granted -> granted }) {
            viewModel.startScan()
        } else {
            scope.launch {
                snackbar.showNotice(
                    AppNoticeVisuals(
                        "缺少「附近的设备」权限，扫不到热水器。可到系统设置里为本应用打开该权限后重试。",
                        tone = NoticeTone.Warning,
                    ),
                )
            }
        }
    }
    val requestScan: () -> Unit = {
        val missing = AppPermissions.missingBluetoothScan(context)
        if (missing.isEmpty()) {
            viewModel.startScan()
        } else {
            scanPermissionLauncher.launch(missing.toTypedArray())
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                // 事件流用 postNotice 单开协程发：收集协程不卡在「等这条提示收场」上，
                // 事件成串来时新的提示才能当场顶掉旧的（见 AppNotice.kt）
                is QzxyEvent.Notice -> scope.postNotice(snackbar, event.text, event.tone)
            }
        }
    }

    // 开阀与结算是一串「连蓝牙 → 下单 → 写设备」的动作，中途退出会把协程掐断：
    // 服务端可能已经预扣，设备却没收到开阀数据。处理期间拦一下返回，只提示不退出；
    // 真卡住了流程会自己超时，几秒后按钮就回来了。
    val requestBack: () -> Unit = {
        if (state.flow is QzxyFlowState.Working) {
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
    BackHandler(enabled = state.flow is QzxyFlowState.Working) { requestBack() }

    // 账单只在页面里拉：卡片那份实例不需要它，见 QzxyViewModel.loadBillsOnce。
    // 跟着登录态跑：首启引导里登录过之后进页面、或在页面里登录，都要拉到当月账单
    LaunchedEffect(state.loggedIn) {
        if (state.loggedIn) viewModel.loadBillsOnce()
    }

    // 页面可见期间对账（DESIGN §3.18）：设备自动关停后自动结算 / 记账切「待核对」。
    // 没有本地记账时不跑；页面进前台的每一拍由它驱动（见 WateringReconcileEffect）
    WateringReconcileEffect(viewModel = viewModel, enabled = state.watering != null)

    // 充值在支付宝里完成，回到本页（ON_RESUME）时余额要重拉，否则还显示充值前的数。
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && awaitingRecharge) {
                awaitingRecharge = false
                viewModel.refreshAccount()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val openRecharge: () -> Unit = {
        if (QzxyRecharge.open(context)) {
            awaitingRecharge = true
        } else {
            scope.launch {
                snackbar.showNotice(
                    AppNoticeVisuals(
                        "没找到能打开支付宝的应用。充值在支付宝的趣智校园小程序里完成，先装一个支付宝。",
                        tone = NoticeTone.Warning,
                    ),
                )
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("趣智校园") },
                navigationIcon = {
                    IconButton(onClick = requestBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                // 退出登录已挪到页尾（2026-11-05 改版）：同一个危险动作不再出现两次
                actions = {},
            )
        },
        snackbarHost = { AppSnackbarHost(snackbar) },
    ) { padding ->
        // 下拉刷新：与消费流水页 / 缴费账单页同一套 PullToRefreshBox（DESIGN §3.18）。
        // padding 收在刷新容器上而不是列表上：Scaffold 的内容从 (0,0) 铺满整屏、顶栏压在
        // 它上面，指示器挂在容器顶边时整条滑入轨迹都在顶栏后面，得拖过阈值一大截才露出半圈。
        // 指示器驻留口径见 ui/common/RefreshHold.kt（2026-11-05）：点亮只认 onRefresh，
        // busy 只续命——直接接 state.refreshingBalance 会让「秒失败 / 逐请求抖动」漏成抽搐回弹。
        val refreshHold = rememberRefreshHold(busy = state.refreshingBalance)
        PullToRefreshBox(
            isRefreshing = refreshHold.isRefreshing,
            onRefresh = {
                refreshHold.begin()
                haptics.tap()
                viewModel.refreshAccount()
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
                if (!state.loggedIn) {
                    LoginSection(state, viewModel)
                    // 登出或会话失效时水可能还在流、预扣还挂着，这条提醒不能跟着登录态一起消失
                    state.watering?.let { UnsettledNotice(it, viewModel) }
                } else {
                    QzxyHeroCard(
                        state = state,
                        onTogglePhone = viewModel::toggleShowPhone,
                        onRecharge = { confirmRecharge = true },
                    )
                    BalanceSection(state = state)
                    StatusCard(
                        state = state,
                        viewModel = viewModel,
                        onOpenDiagnostics = onOpenDiagnostics,
                        onPickDevice = { showDeviceSheet = true },
                    )
                    BillsEntrySection(state = state)
                }
                PreferencesSection(
                    state = state,
                    viewModel = viewModel,
                    onOpenDiagnostics = { onOpenDiagnostics(state.selected?.address) },
                )
                FooterActions(
                    loggedIn = state.loggedIn,
                    onLogout = { confirmLogout = true },
                )
                Disclaimer()
            }
        }
    }

    if (showDeviceSheet) {
        DevicePickerSheet(
            state = state,
            viewModel = viewModel,
            onRequestScan = requestScan,
            onDismiss = { showDeviceSheet = false },
        )
    }

    // 退出二次确认（2026-09-27）：顶栏图标就在返回键旁边，一次误触就得重新收短信才能回来
    if (confirmLogout) {
        AppConfirmDialog(
            title = "退出趣智校园登录？",
            message = "将清除本机保存的趣智校园会话，侧栏入口回到未登录态。" +
                "如果水还在流，退出前先点「结束用水」结算。",
            confirmLabel = "退出登录",
            onConfirm = {
                confirmLogout = false
                viewModel.logout()
            },
            onDismiss = { confirmLogout = false },
            // 清会话不可撤销：确认键染红，和「取消」区分开
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
            // 钱付给趣智运营方，本应用只跳转；不是危险动作，确认键保持主色
            danger = false,
        ) {
            Text("将跳到支付宝的「趣智校园」小程序，钱进你在趣智的账户。")
            Text(
                "先核对支付宝登录的手机号与本应用登录的一致，充错账户退不回来。",
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                "充值与退款由学校运营方负责，与本应用无关。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 8.dp),
            )
            // 跳转前的风险小字：账户核对 + 上游接口变更（口径单一来源，见 RechargeRiskNote）
            RechargeRiskNote()
        }
    }
}

// ── 登录 ──

@Composable
private fun LoginSection(state: QzxyUiState, viewModel: QzxyViewModel) {
    SettingsSection(
        title = "登录趣智校园",
        subtitle = "用趣智校园 App / 小程序注册的手机号登录；本应用不提供注册，充值走官方小程序",
    ) {
        OutlinedTextField(
            value = state.phone,
            onValueChange = viewModel::updatePhone,
            label = { Text("手机号") },
            singleLine = true,
            isError = state.phoneError != null,
            supportingText = state.phoneError?.let { { Text(it) } },
            modifier = Modifier.fillMaxWidth(),
        )
        if (state.useSmsLogin) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = state.code,
                    onValueChange = viewModel::updateCode,
                    label = { Text("短信验证码") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = { viewModel.sendCode() },
                    enabled = !state.sendingCode,
                    modifier = Modifier.padding(start = 8.dp),
                ) {
                    Text(if (state.sendingCode) "发送中…" else "获取验证码")
                }
            }
        } else {
            OutlinedTextField(
                value = state.password,
                onValueChange = viewModel::updatePassword,
                label = { Text("密码") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )
        }
        Button(
            onClick = { viewModel.login() },
            enabled = !state.loggingIn,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
                .height(48.dp),
        ) {
            Text(if (state.loggingIn) "登录中…" else "登录")
        }
        Text(
            "只保存登录状态，不保存密码。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            modifier = Modifier.padding(top = 4.dp),
        )
        TextButton(
            onClick = { viewModel.toggleUseSmsLogin() },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (state.useSmsLogin) "改用密码登录" else "改用验证码登录")
        }

        // 会话串登录：与胖乖的 Token 登录同一个交互。差别在凭据形态——
        // 那边服务端签发的 token 单独可用，这边得凑齐 loginCode + projectId 等字段。
        if (!state.showSessionLogin) {
            OutlinedButton(
                onClick = { viewModel.toggleSessionLogin() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("会话串登录")
            }
            Text(
                text = "已从其他渠道拿到会话串时可直接粘贴登录；手机号登录会使旧会话失效。" +
                    "会话串等于账号通行证，别外发。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            )
        } else {
            OutlinedTextField(
                value = state.sessionLoginInput,
                onValueChange = viewModel::updateSessionLoginInput,
                label = { Text("会话串") },
                minLines = 2,
                maxLines = 4,
                supportingText = {
                    Text("粘登录响应原文即可，JSON / 表单 / 逐行键值三种写法都认")
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { viewModel.loginWithSession() },
                enabled = !state.sessionLoggingIn && state.sessionLoginInput.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            ) {
                Text(if (state.sessionLoggingIn) "验证中…" else "会话登录")
            }
            TextButton(
                onClick = { viewModel.toggleSessionLogin() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("收起会话串登录")
            }
        }
    }
}

/** 未登录时的未结算提醒：水可能还在流，得让人看见，也得有个出口。 */
@Composable
private fun UnsettledNotice(watering: QzxyWatering, viewModel: QzxyViewModel) {
    SettingsSection(
        title = "上次开阀还没结算",
        subtitle = "${QzxyWateringFormat.clockText(watering.startedAtMillis)} 在" +
            "「${watering.deviceName}」开的阀。登录后到设备旁点「结算核对」；" +
            "记录已不在的话，这笔单由平台统一处理——「消费记录」里先显示 ¥0、" +
            "处理后显示实际金额（一般次日）。",
    ) {
        TextButton(onClick = { viewModel.abandonWatering() }) {
            Text("已到账或不再处理，清除本地记账")
        }
    }
}

// ── 账号与设备 ──

/**
 * 身份卡（2026-11-05 改版）：学校名 + 手机号（默认遮蔽，点眼睛展开）+ 「已登录」+ 充值。
 *
 * 原先是「余额与充值」一张卡里塞余额大字、账号行、充值按钮，余额与账号抢同一个位置；
 * 现在身份归身份、数字归三列指标（[BalanceSection]），充值仍是卡片底部的主动作。
 * 充值会跳出本应用，点之前先过一道确认（见 `QzxyScreen` 里的 `confirmRecharge`）。
 */
@Composable
private fun QzxyHeroCard(
    state: QzxyUiState,
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
        brandIcon = R.drawable.ic_brand_qzxy,
        title = state.schoolName.ifBlank { "趣智校园" },
        subtitle = buildString {
            if (hasPhone) {
                append(if (state.showPhone) state.accountPhone else QzxyPhoneMask.mask(state.accountPhone))
                append(" · ")
            }
            append("会话仅存本机")
        },
        pill = "已登录",
        trailing = eye,
        action = {
            HeroActionButton(
                text = "充值到趣智账户",
                icon = HugeIcons.WalletAdd01,
                onClick = onRecharge,
            )
        },
    )
}

/** 手机号显隐的眼睛：页面会被截图、会被旁人瞥见，默认遮蔽（与「我的」页学号同口径）。 */
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
 * 余额与账单三列（2026-11-05）：余额 / 账单合计 / 账单笔数。
 *
 * 合计只把服务端这一页账单的金额相加，不做本地推算——预扣与实扣是两笔账，
 * 口径只有一个来源才不会再错（同 [BillSection] 的 KDoc）。
 */
@Composable
private fun BalanceSection(state: QzxyUiState) {
    val total = state.bills
        .mapNotNull { bill -> bill.consumeMoney?.trim()?.toBigDecimalOrNull() }
        .fold(BigDecimal.ZERO, BigDecimal::add)
    val balanceText = when {
        state.balance != null -> "¥${state.balance?.text}"
        state.balanceLoaded -> "—"
        else -> "…"
    }
    SettingsSection(title = "余额与账单") {
        MetricsRow(Modifier.padding(top = 6.dp)) {
            MetricCell(
                label = "余额",
                value = balanceText,
                caption = "趣智账户",
                modifier = Modifier.weight(1f),
            )
            MetricCell(
                label = "账单合计",
                value = if (state.billLoaded) "¥%.2f".format(total) else "—",
                caption = state.billMonth.ifBlank { "—" },
                modifier = Modifier.weight(1f),
            )
            MetricCell(
                label = "账单笔数",
                value = if (state.billLoaded) "${state.bills.size} 笔" else "—",
                caption = "热水消费",
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * 设备一行 + 「更换」。
 *
 * 只有**真正用水中**（记账没过期、设备也没被确认结束）不给换：那一台正在放水，
 * 切到别的设备会让结束用水的按钮连错机器。过期的记账是「待核对」——水已经停了，
 * 结算核对按记账里的地址走（不是按选中），换设备不受它挡。
 */
@Composable
private fun DeviceRowItem(state: QzxyUiState, onPick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val watering = state.watering
    val wateringActive = watering != null &&
        !state.wateringDeviceEnded &&
        !isQzxyWateringExpired(watering.startedAtMillis)
    val name = (if (wateringActive) watering?.deviceName else null)
        ?: state.selected?.let { device ->
            state.deviceInfos[device.addressKey]?.deviceName?.takeIf { it.isNotBlank() }
                ?: device.name
        }
        // 没选中也要认得上次那台：进页面时设备名是空的会让人以为绑定丢了
        ?: state.lastUsedDevice?.name
    val subtitle = when {
        wateringActive -> "用水中，暂时不能换设备"
        state.flow is QzxyFlowState.Working -> "正在处理，等这一步跑完再换"
        watering != null -> "上次用水待核对 · 点右侧可换"
        state.selected != null -> "已选中 · 点右侧可换"
        state.lastUsedDevice != null -> "上次用过 · 点右侧可换"
        else -> "还没选设备，点右侧挑一台"
    }
    // 进行中不许换：换设备会走 preconnect，它会把正在用的那条链路关掉重建，
    // 把开阀/结算打断在半路——而那时服务端可能已经下过单、扣过预扣
    val canPick = !wateringActive && state.flow !is QzxyFlowState.Working
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = canPick, onClickLabel = "选择热水器") { onPick() }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            HugeIcons.Droplet,
            contentDescription = null,
            tint = if (name != null) primary else onSurface.copy(alpha = 0.45f),
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = name ?: "未选择热水器",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = onSurface.copy(alpha = 0.55f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(4.dp))
        Text(
            text = if (canPick) "更换 ›" else "用水中",
            style = MaterialTheme.typography.bodySmall,
            color = if (canPick) primary else onSurface.copy(alpha = 0.35f),
            maxLines = 1,
        )
    }
}

// ── 状态卡 ──

/**
 * 主操作卡「开热水」：设备 → 状态 → 一个主动作。
 *
 * 2026-11-05 起设备行并进本卡顶部（原先是独立一张卡）：换设备、看状态、点按钮
 * 是同一个动作的三步，拆成两张卡要来回翻。固定副标题也去掉了——标题 + 固定副标题
 * + 动态状态三行小字叠在一起，按钮被挤到看不见；计费口径降为卡底一条注记。
 *
 * 分支顺序即优先级——进行中的流程盖过一切（此时点别的按钮没有意义），
 * 结算结果盖过用水中（结算完 store 已经清了，这一条只是防竞态），
 * 用水中盖过失败（结束用水失败时水还在流，主按钮仍是结束用水）。
 */
@Composable
private fun StatusCard(
    state: QzxyUiState,
    viewModel: QzxyViewModel,
    onOpenDiagnostics: (String?) -> Unit,
    onPickDevice: () -> Unit,
) {
    var failureDetail by remember { mutableStateOf<QzxyFlowState.Failed?>(null) }
    val flow = state.flow
    SettingsSection(title = "开热水") {
        DeviceRowItem(state = state, onPick = onPickDevice)
        SettingsDivider(Modifier.padding(top = 2.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // 待机态两行、用水中六行，高度差一百多 dp。给个下限兜住最短的两个状态，
                // 再让内容淡入淡出、高度平滑过渡（AnimatedContent 自带尺寸动画）。
                // 之前是硬切，点「开始用水」时整页会往上顶一截。
                .heightIn(min = 104.dp)
                .padding(top = 8.dp),
        ) {
            Text(
                text = statusSubtitle(state),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
            AnimatedContent(
                targetState = statusContentOf(state),
                transitionSpec = {
                    fadeIn(animationSpec = Motion.state()) togetherWith
                        fadeOut(animationSpec = Motion.exit())
                },
                label = "qzxy-status",
            ) { content ->
                // 每个形态都是好几行（按钮、说明、提示），必须自己竖着排：
                // AnimatedContent 的内容槽是 Box，多个子元素会直接叠在一起
                Column(Modifier.fillMaxWidth()) {
                    when (content) {
                        // 过渡期间旧内容还在，读最新的 flow 只影响文案，不影响类型安全
                        QzxyStatusContent.Working ->
                            WorkingBlock((state.flow as? QzxyFlowState.Working)?.step ?: "正在处理")
                        is QzxyStatusContent.Settlement ->
                            SettlementBlock(content.settlement, state, viewModel)
                        is QzxyStatusContent.Watering ->
                            WateringStatusBlock(
                                watering = content.watering,
                                deviceEnded = state.wateringDeviceEnded,
                                failure = state.flow as? QzxyFlowState.Failed,
                                onStop = { viewModel.stopWater() },
                                onAbandon = { viewModel.abandonWatering() },
                                onClearDeviceRecord = { viewModel.clearDeviceRecord() },
                                modifier = Modifier.padding(top = 12.dp),
                            )
                        is QzxyStatusContent.Failed -> FailureBlock(
                            failure = content.failure,
                            state = state,
                            viewModel = viewModel,
                            onShowDetail = { failureDetail = content.failure },
                        )
                        QzxyStatusContent.Idle -> IdleBlock(state, viewModel)
                    }
                }
            }
        }
        SettingsNote("出水按实际用量计费，结束后从账户扣")
    }
    failureDetail?.let { failure ->
        FailedDetailDialog(
            failure = failure,
            onOpenDiagnostics = { onOpenDiagnostics(state.selected?.address) },
            onDismiss = { failureDetail = null },
        )
    }
    // 开阀失败的原因 + 自动重试（DESIGN §3.18）。与今日页面板挂的是同一个实现，
    // 由 ViewModel 的 recovery 状态驱动，两处不会各弹一个
    state.recovery?.let { recovery ->
        QzxyRecoveryDialog(
            recovery = recovery,
            onRetry = { viewModel.retryOpenAfterFailure() },
            onManualClear = { viewModel.clearFromRecovery() },
            onOpenDiagnostics = { onOpenDiagnostics(state.selected?.address) },
            onDismiss = { viewModel.dismissRecovery() },
        )
    }
}

/**
 * 状态卡的展示形态。
 *
 * 带数据的三个形态把数据装进来（而不是在内容里读最新的 `state`）：AnimatedContent
 * 过渡期间旧内容仍在合成，那时 `state` 已经换成新状态，`as Working` 这类强转会直接崩。
 * Working 只带标记、步骤文字现读——步骤每变一次就触发一次淡入淡出反而更闪。
 */
private sealed interface QzxyStatusContent {
    data object Working : QzxyStatusContent
    data class Settlement(val settlement: QzxySettlement) : QzxyStatusContent
    data class Watering(val watering: QzxyWatering) : QzxyStatusContent
    data class Failed(val failure: QzxyFlowState.Failed) : QzxyStatusContent
    data object Idle : QzxyStatusContent
}

private fun statusContentOf(state: QzxyUiState): QzxyStatusContent {
    val flow = state.flow
    val settlement = state.lastSettlement
    val watering = state.watering
    return when {
        flow is QzxyFlowState.Working -> QzxyStatusContent.Working
        settlement != null -> QzxyStatusContent.Settlement(settlement)
        watering != null -> QzxyStatusContent.Watering(watering)
        flow is QzxyFlowState.Failed -> QzxyStatusContent.Failed(flow)
        else -> QzxyStatusContent.Idle
    }
}

private fun statusSubtitle(state: QzxyUiState): String = when {
    state.flow is QzxyFlowState.Working -> "正在处理，别离开这一页"
    state.lastSettlement != null -> "本次用水已结算"
    state.watering != null && !state.wateringDeviceEnded &&
        !isQzxyWateringExpired(state.watering.startedAtMillis) ->
        "设备在放水，用完点结束用水结算"
    state.watering != null -> "上次用水待核对，站到设备旁点「结算核对」"
    state.flow is QzxyFlowState.Failed -> "上一次操作没成功，按下面的提示来"
    !state.hasUsableDevice -> "先在上面选一台热水器"
    else -> "站在热水器旁边点开始用水"
}

@Composable
private fun IdleBlock(state: QzxyUiState, viewModel: QzxyViewModel) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    Button(
        // 没主动选过设备时用上次那台兜底（与今日页面板同口径），
        // 省掉「进页面 → 选设备 → 再点开始」这三步
        onClick = { viewModel.openValveFromCard() },
        enabled = state.hasUsableDevice,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .height(48.dp),
    ) {
        Text("开始用水")
    }
    if (!state.hasUsableDevice) {
        Text(
            text = "还没选设备，先点上面那行「更换」挑一台。",
            style = MaterialTheme.typography.bodySmall,
            color = onSurface.copy(alpha = 0.55f),
            modifier = Modifier.padding(top = 8.dp),
        )
    } else {
        // 待机态填最近一次用水：既省下卡片底部那块空白，也是开机后最想知道的一条
        // （上次花了多少、什么时候）。数据来自服务端账单，当月没有就直说。
        val lastBill = state.bills.firstOrNull()
        Text(
            text = when {
                lastBill != null ->
                    "最近一次 ${billTimeText(lastBill.consumeDate)} · ¥${lastBill.consumeMoney ?: "-"}"
                // 加载中与「确实没有」不能都说成没有，那是在报假消息
                state.billLoaded -> "本月还没有用水记录"
                else -> "正在读取用水记录…"
            },
            style = MaterialTheme.typography.bodySmall,
            color = onSurface.copy(alpha = 0.55f),
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

/**
 * 账单时间压成「9月27日 22:11」。服务端给的是 `2026-09-27 22:11:53` 这种定长串，
 * 格式对不上就原样返回，不猜。
 */
private fun billTimeText(raw: String?): String {
    val text = raw?.trim().orEmpty()
    if (text.length < 16) return text
    val month = text.substring(5, 7).toIntOrNull() ?: return text
    val day = text.substring(8, 10).toIntOrNull() ?: return text
    return "${month}月${day}日 ${text.substring(11, 16)}"
}

@Composable
private fun WorkingBlock(step: String) {
    Row(
        modifier = Modifier.padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(10.dp))
        Text(
            text = step,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
        )
    }
    // 给个时间预期：这一段要连蓝牙再下单，几秒内没动才是异常
    Text(
        text = "连接热水器并下单，通常几秒",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun SettlementBlock(
    settlement: QzxySettlement,
    state: QzxyUiState,
    viewModel: QzxyViewModel,
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    Text(
        text = "已结束用水",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 12.dp),
    )
    Text(
        text = QzxyWateringFormat.money(settlement.consumeMoneyMilli),
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp),
    )
    val parts = buildList {
        settlement.durationMillis?.let { add("本次用水 ${QzxyWateringFormat.duration(it)}") }
        state.balance?.let { add("余额 ¥${it.text}") }
    }
    if (parts.isNotEmpty()) {
        Text(
            text = parts.joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = onSurface.copy(alpha = 0.6f),
        )
    }
    settlement.note?.let { note ->
        Text(
            text = note,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
    Text(
        text = "金额来自服务端账单，不是本地推算",
        style = MaterialTheme.typography.bodySmall,
        color = onSurface.copy(alpha = 0.5f),
        modifier = Modifier.padding(top = 6.dp),
    )
    OutlinedButton(
        onClick = { viewModel.dismissSettlement() },
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .height(48.dp),
    ) {
        Text("完成")
    }
}

@Composable
private fun FailureBlock(
    failure: QzxyFlowState.Failed,
    state: QzxyUiState,
    viewModel: QzxyViewModel,
    onShowDetail: () -> Unit,
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    Text(
        text = failure.reason,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(top = 12.dp),
    )
    failure.detail?.let { detail ->
        Text(
            text = detail,
            style = MaterialTheme.typography.bodySmall,
            color = onSurface.copy(alpha = 0.6f),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
    Button(
        // 开阀被「未结算的老记录」挡住时，主出口是先去结算——提示让点「结束用水」，
        // 这里就必须真的给出那个按钮（旧版只有「重试开阀」，用户只能卡住）
        onClick = {
            when {
                failure.clearThenOpen -> viewModel.clearAndOpen()
                failure.resendRecovery -> viewModel.resendOpenData()
                failure.settleRecovery -> viewModel.stopWater()
                else -> viewModel.openValveFromCard()
            }
        },
        enabled = state.hasUsableDevice,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .height(48.dp),
    ) {
        Text(
            when {
                failure.clearThenOpen -> "清除并继续开阀"
                failure.resendRecovery -> "重发开阀数据"
                failure.settleRecovery -> "结算设备记录"
                else -> "重试开阀"
            },
        )
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(
            onClick = { viewModel.clearDeviceRecord() },
            enabled = state.selected != null,
            modifier = Modifier
                .weight(1f)
                .height(42.dp),
        ) {
            Text(if (failure.clearThenOpen) "只清除记录" else "清除设备记录")
        }
        OutlinedButton(
            onClick = onShowDetail,
            modifier = Modifier
                .weight(1f)
                .height(42.dp),
        ) {
            Text("详情")
        }
    }
}

/**
 * 失败详情：原始数据不铺在页面上，要看的自己点开，或者进诊断页看全。
 *
 * 今日页的开水面板复用这一份（同包，2026-10-02）：面板与页面的失败态各写一个
 * 详情弹窗迟早会分叉，而两边要展示的字段完全一样。
 *
 * 2026-11-06 起骨架走 `AppConfirmDialog`（正文 slot 版）：淡入、`heightIn(max)` + 滚动、
 * 危险色只在公共件里各有一处；这里只留「原因 + 原始数据 + 去诊断页」三段正文。
 */
@Composable
internal fun FailedDetailDialog(
    failure: QzxyFlowState.Failed,
    onOpenDiagnostics: () -> Unit,
    onDismiss: () -> Unit,
) {
    AppConfirmDialog(
        title = "失败详情",
        confirmLabel = "去诊断页",
        onConfirm = {
            onDismiss()
            onOpenDiagnostics()
        },
        onDismiss = onDismiss,
        // 只读提示，不是破坏性动作
        danger = false,
        dismissLabel = "关闭",
    ) {
        Text(failure.reason, style = MaterialTheme.typography.bodyMedium)
        failure.detail?.let { detail ->
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        Text(
            text = "设备服务表、原始数据体与调试日志都在「诊断与调试」页。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

// ── 设备弹层 ──

/**
 * 选择热水器：已绑定在上、附近设备在下。
 *
 * 扫描搬进弹层的原因：一次能扫出十几台，列表边扫边长，铺在页面上会把下面的账单
 * 与入口一路推走。放在弹层里，列表怎么长都不影响页面。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DevicePickerSheet(
    state: QzxyUiState,
    viewModel: QzxyViewModel,
    onRequestScan: () -> Unit,
    onDismiss: () -> Unit,
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    // 选中设备后调用方直接把弹层从组合里摘掉，退场来不及播（"啪"地消失）。
    // 先 hide() 播完再落状态，与 WaterScreen 的 DeviceSheet 同一口径。
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val dismiss = rememberSheetDismisser(sheetState, onDismiss)
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
                text = "选择热水器",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "绑定过的不用扫蓝牙，点一下就能连",
                style = MaterialTheme.typography.bodySmall,
                color = onSurface.copy(alpha = 0.55f),
            )

            if (state.boundDevices.isNotEmpty()) {
                Text(
                    text = "已绑定",
                    style = MaterialTheme.typography.labelLarge,
                    color = onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 14.dp),
                )
                state.boundDevices.forEach { bound ->
                    DeviceRow(
                        title = bound.name,
                        subtitle = "已绑定 · 不用扫描直接连",
                        selected = state.selected?.address.equals(bound.address, ignoreCase = true),
                        onClick = {
                            viewModel.selectBoundDevice(bound)
                            dismiss {}
                        },
                        action = {
                            TextButton(onClick = { viewModel.unbindDevice(bound.address) }) {
                                Text("解绑")
                            }
                        },
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "附近设备",
                    style = MaterialTheme.typography.labelLarge,
                    color = onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.weight(1f),
                )
                if (state.scanning) {
                    TextButton(onClick = { viewModel.stopScan() }) { Text("停止") }
                } else {
                    TextButton(onClick = onRequestScan) { Text("扫描") }
                }
            }
            if (state.scanning) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "扫描中，站到热水器旁边",
                        style = MaterialTheme.typography.bodySmall,
                        color = onSurface.copy(alpha = 0.6f),
                    )
                }
            }
            state.devices.forEach { device ->
                val info = state.deviceInfos[device.addressKey]
                // 扫到的可能正是已绑定的那台，右侧别再给一个「绑定」按钮
                val alreadyBound = state.boundDevices.any { bound ->
                    bound.address.equals(device.address, ignoreCase = true)
                }
                DeviceRow(
                    // 服务端有登记就用它的设备名（含楼栋楼层房间），没有才退回广播名
                    title = info?.deviceName?.takeIf { it.isNotBlank() } ?: device.name,
                    subtitle = deviceSubtitle(device, info),
                    selected = state.selected?.address == device.address,
                    onClick = {
                        viewModel.selectDevice(device)
                        dismiss {}
                    },
                    action = {
                        if (alreadyBound) {
                            Text(
                                text = "已绑定",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                            )
                        } else {
                            TextButton(onClick = { viewModel.bindDevice(device) }) { Text("绑定") }
                        }
                    },
                )
            }
            if (!state.scanning && state.devices.isEmpty()) {
                Text(
                    text = "还没扫到设备。若一直为空，确认手机蓝牙已开、已授予「附近的设备」权限，" +
                        "并站在热水器旁边重试。",
                    style = MaterialTheme.typography.bodySmall,
                    color = onSurface.copy(alpha = 0.55f),
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

/**
 * 设备列表项的副行：查到了服务端信息就把位置补上，并把广播名留在最前面——
 * 它是现场唯一不会骗人的标识，服务端那条记录有可能是别人登记错的。
 */
private fun deviceSubtitle(device: QzxyScannedDevice, info: QzxyDeviceInfo?): String = buildList {
    if (info?.deviceName?.isNotBlank() == true) add(device.name)
    info?.let { detail ->
        val place = listOfNotNull(detail.buildingName, detail.floorName, detail.roomName)
            .filter { it.isNotBlank() }
            .joinToString("")
        if (place.isNotBlank()) add(place)
    }
    add(device.address)
    add("信号 ${device.rssi} dBm")
    add("编号 ${device.deviceKey}")
}.joinToString(" · ")

@Composable
private fun DeviceRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    action: (@Composable () -> Unit)? = null,
) {
    val haptics = rememberAppHaptics()
    val accent = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .background(
                color = if (selected) accent.copy(alpha = 0.12f) else Color.Transparent,
                shape = RoundedCornerShape(10.dp),
            )
            .clickable {
                haptics.tap()
                onClick()
            }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            HugeIcons.Droplet,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            )
        }
        action?.invoke()
    }
}

// ── 账单与页尾 ──

/**
 * 消费记录入口卡（2026-11-05 用户要求：消费记录做成入口卡片，明细单独一页）。
 *
 * 原来这一整块就摆在趣智页里：月份左右箭头 + 默认 5 笔 + 「展开全部 N 笔」。一个月
 * 二三十笔很常见，展开后主页被拉长两三屏，真正的主动作「开热水」被挤下去。
 * 现在只留一行摘要（本月笔数与合计），点进去是独立页 `QZXY_BILLS`，那边支持
 * 今日 / 本周 / 本月 / 全部四个时段与按日分组。
 *
 * 摘要只统计**本月**：入口卡这里没有时段概念，说「本月」才不会让人以为那是全部；
 * 数字仍然只来自服务端账单（`order/query/account/bill/list`），本地不推算。
 */
@Composable
private fun BillsEntrySection(state: QzxyUiState) {
    val context = LocalContext.current
    SettingsSection(title = "消费记录") {
        SettingItem(
            title = "账单明细",
            subtitle = "今日 / 本周 / 本月 / 全部 · 按日分组",
            value = if (state.billLoaded) entrySummary(state.bills) else null,
            onClick = { SubpageActivity.start(context, SubpageScreen.QZXY_BILLS) },
        )
    }
}

/**
 * 设置卡（2026-11-05 合并）：自动结算开关 + 诊断入口。
 *
 * 原来两件事各占一张卡（「用水」整卡一个开关、「诊断与调试」整卡一行），
 * 合起来只是两条设置项，却把消费记录挤下去两屏。
 *
 * 自动结算（DESIGN §4.30，2026-10-11 起可关，默认开）：关掉后的变化全在对账链路
 * （[WateringReconcileEffect]）——读到「消费完成，待采集」不再自动发起结算。
 * 登录与未登录两态都显示：这是行为偏好，不是账号状态，提前关也该能关。
 */
@Composable
private fun PreferencesSection(
    state: QzxyUiState,
    viewModel: QzxyViewModel,
    onOpenDiagnostics: () -> Unit,
) {
    SettingsSection(title = "设置") {
        SettingSwitchRow(
            title = "设备关停后自动结算",
            subtitle = "页面可见时自动结算并记账；关掉后要你手动结算",
            checked = state.autoSettleEnabled,
            onCheckedChange = viewModel::setAutoSettleEnabled,
        )
        SettingItem(
            title = "诊断与调试",
            subtitle = "设备现场 · 服务表 · 调试日志",
            icon = HugeIcons.InformationCircle,
            onClick = onOpenDiagnostics,
        )
    }
}

/** 页尾退出（二次确认在宿主里）：登录态才出现，未登录时不留一个点了没反应的入口。 */
@Composable
private fun FooterActions(loggedIn: Boolean, onLogout: () -> Unit) {
    if (!loggedIn) return
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
        body = "**趣智校园开热水为第三方非官方功能**，协议来自公开逆向资料，与校方及设备厂商无关；" +
            "开阀照常从你的账户扣费，**本应用不绕过计费**，接口或固件变更可能使其失效。",
    )
}
