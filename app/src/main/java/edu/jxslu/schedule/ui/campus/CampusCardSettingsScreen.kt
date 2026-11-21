package edu.jxslu.schedule.ui.campus

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import edu.jxslu.schedule.Graph
import edu.jxslu.schedule.domain.BalanceAlert
import edu.jxslu.schedule.domain.MoneyFormat
import edu.jxslu.schedule.domain.RechargeDisclaimer
import edu.jxslu.schedule.domain.YktArrival
import edu.jxslu.schedule.data.ykt.rechargeTargetCard
import edu.jxslu.schedule.data.session.LoginState
import edu.jxslu.schedule.data.session.LoginStateRules
import edu.jxslu.schedule.data.session.LoginTarget
import edu.jxslu.schedule.data.session.SessionStatus
import edu.jxslu.schedule.startActivityOutsideApp
import edu.jxslu.schedule.ui.common.AccountCard
import edu.jxslu.schedule.ui.common.AppConfirmDialog
import edu.jxslu.schedule.ui.common.AppNoticeVisuals
import edu.jxslu.schedule.ui.common.AppSnackbarHost
import edu.jxslu.schedule.ui.common.FooterDisclaimer
import edu.jxslu.schedule.ui.common.NoticeFeedback
import edu.jxslu.schedule.ui.common.NoticeTone
import edu.jxslu.schedule.ui.common.RechargeDisclaimerDialog
import edu.jxslu.schedule.ui.common.SettingsNote
import edu.jxslu.schedule.ui.common.SettingItem
import edu.jxslu.schedule.ui.common.SettingSwitchRow
import edu.jxslu.schedule.ui.common.SettingsSection
import edu.jxslu.schedule.ui.common.WheelValueDialog
import edu.jxslu.schedule.ui.common.pinnedStatusBars
import edu.jxslu.schedule.ui.common.rememberAppHaptics
import edu.jxslu.schedule.ui.common.showNotice
import edu.jxslu.schedule.ui.life.PowerRoomPickerSheet
import edu.jxslu.schedule.ui.life.PowerRoomPickerViewModel
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.CreditCard
import me.rerere.hugeicons.stroke.Home01
import me.rerere.hugeicons.stroke.View
import me.rerere.hugeicons.stroke.ViewOff
import kotlinx.coroutines.launch
import androidx.compose.ui.text.font.FontWeight

/**
 * 我的 → 校园卡（DESIGN §3.10）：管开启状态、凭证与两个余额提醒，不看付款码。
 *
 * 页面顺序固定为 身份 → 余额 → 账号与凭证 → 寝室电费 → 余额提醒 → 页尾免责：
 * 未开启时余额卡不画，其余卡片不换位，用户每次进页都在同一处找同一个设置。
 *
 * 凭证交互：默认未开启；填学号 + 查询密码点「保存并开启」，真实登录验证成功才落库并开启。
 * 已开启时本卡只显示学号与「查询密码 · 更换」，改密码或换学号才展开输入框；
 * 「关闭并清除」在卡尾（危险操作，确认后清凭证并回落两个提醒）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampusCardSettingsScreen(
    onBack: () -> Unit,
    /** 跳消费流水页（DESIGN §4.19 B4/B5） */
    onOpenStatement: () -> Unit = {},
    viewModel: CampusCardViewModel = viewModel(
        factory = CampusCardViewModel.Factory(LocalContext.current.applicationContext),
    ),
) {
    val enabled by viewModel.enabled.collectAsStateWithLifecycle()
    val balance by viewModel.balance.collectAsStateWithLifecycle()
    val arrivalState by viewModel.arrivalState.collectAsStateWithLifecycle()
    val snackbar = remember { androidx.compose.material3.SnackbarHostState() }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    // Activity 窗口 context：通知权限申请、外部跳转（微信/浏览器）都用它
    val context = LocalContext.current

    // 超时安抚提示（只弹一次：Timeout 状态被确认后转 Idle）
    androidx.compose.runtime.LaunchedEffect(arrivalState) {
        val st = arrivalState
        if (st is CampusCardViewModel.ArrivalState.Timeout) {
            snackbar.showNotice(
                AppNoticeVisuals(
                    "暂未检测到到账——充值常有延迟，到账后「消费流水」会自动显示",
                    // 钱相关的不确定态用 Warning：这条是「没查到到账」，不是播报；
                    // Info 的灰字在充值场景里会被读成「一切正常」
                    tone = NoticeTone.Warning,
                ),
            )
            viewModel.dismissArrival()
        }
    }

    // 从微信/浏览器返回的瞬间立即补检一轮（不等 5 秒周期）；同时兜进程被杀重启恢复
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                viewModel.onHostResume()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 已保存学号**明文回填**（2026-09-24 用户拍板）：进页时读一次加密存储填进输入框，
    // 之后的编辑由 rememberSaveable 管（转屏/重建不丢）。改了就覆盖，留空才报错。
    // 同一份值也供账户卡显示；开启 / 关闭 / 覆盖保存后由 [feedback] 重读（2026-09-27）。
    var savedUsername by remember { mutableStateOf(viewModel.savedUsername) }
    var username by rememberSaveable { mutableStateOf(savedUsername.orEmpty()) }
    var password by rememberSaveable { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var confirmDisable by remember { mutableStateOf(false) }
    // 已开启时凭证区默认只读，点「更换」才展开输入框
    var editing by rememberSaveable { mutableStateOf(false) }
    var showRechargeSheet by remember { mutableStateOf(false) }
    var showPowerPicker by remember { mutableStateOf(false) }
    var showYktPicker by remember { mutableStateOf(false) }
    var showPowerIntervalPicker by remember { mutableStateOf(false) }
    var showYktIntervalPicker by remember { mutableStateOf(false) }
    // 「选择寝室」弹层（DESIGN §4.24「未设置寝室」，2026-10-08）：入口在下方「寝室电费」卡。
    // 房号显示取本机最新读数（零网络；权威状态以生活页电费卡为准），改绑成功后就地更新
    var showRoomPicker by remember { mutableStateOf(false) }
    var roomLabel by remember { mutableStateOf<String?>(null) }
    val roomPickerViewModel: PowerRoomPickerViewModel = viewModel(
        factory = PowerRoomPickerViewModel.factory(context.applicationContext),
    )
    androidx.compose.runtime.LaunchedEffect(Unit) {
        roomLabel = runCatching { Graph.powerReadingStore(context.applicationContext).latest() }
            .getOrNull()?.roomName?.takeIf { it.isNotBlank() }
    }

    // 充值免责声明（与生活页同一条口径，domain/RechargeDisclaimer）：充值入口打开
    // 弹层前先过提醒，勾「一周内不再提醒」静默 7 天；首次（从未确认过）锁 5 秒
    // 才能关，之后任何一次弹出立即可关。继续或取消都算「已确认」。
    val displayPrefs = remember { Graph.displayPrefs(context) }
    val disclaimerSuppressUntil by displayPrefs.rechargeDisclaimerSuppressUntil
        .collectAsStateWithLifecycle(initialValue = 0L)
    val disclaimerSeenAt by displayPrefs.rechargeDisclaimerSeenAt
        .collectAsStateWithLifecycle(initialValue = 0L)
    var showRechargeDisclaimer by remember { mutableStateOf(false) }

    val powerAlertEnabled by viewModel.powerAlertEnabled.collectAsStateWithLifecycle()
    val powerAlertYuan by viewModel.powerAlertYuan.collectAsStateWithLifecycle()
    val yktAlertEnabled by viewModel.yktAlertEnabled.collectAsStateWithLifecycle()
    val yktAlertYuan by viewModel.yktAlertYuan.collectAsStateWithLifecycle()
    val powerAlertIntervalHours by viewModel.powerAlertIntervalHours.collectAsStateWithLifecycle()
    val yktAlertIntervalHours by viewModel.yktAlertIntervalHours.collectAsStateWithLifecycle()

    fun feedback(notice: NoticeFeedback) {
        busy = false
        if (notice.tone != NoticeTone.Error) {
            password = ""
            // 保存 / 开启 / 关闭都从这里收尾：重读一次凭证，凭证区跟着换回只读行
            savedUsername = viewModel.savedUsername
            editing = false
        }
        scope.launch {
            snackbar.showNotice(AppNoticeVisuals(notice.text, tone = notice.tone))
        }
    }

    fun showNotice(text: String, tone: NoticeTone = NoticeTone.Info) {
        scope.launch { snackbar.showNotice(AppNoticeVisuals(text, tone = tone)) }
    }

    // 两个提醒开关共用：开启那一刻请求 POST_NOTIFICATIONS（API 33+），拒绝不阻塞开关本身
    // （口径同「我的 → 上课提醒」页：功能在系统设置里授权后自动生效）
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (!granted) showNotice("未授予通知权限，提醒不会显示；可在系统设置里重新开启", NoticeTone.Warning)
    }
    val requestNotifPermission = {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // 开关入口共用：提醒要拿这份凭证去读余额，没凭证就别让开关亮起来
    val toggleAlert: (Boolean, (Boolean) -> Unit) -> Unit = { want, apply ->
        if (want && !viewModel.hasSavedAccount) {
            showNotice("请先在上方开启并保存学号密码，提醒需要用它读取余额", NoticeTone.Warning)
        } else {
            apply(want)
            if (want) requestNotifPermission()
        }
    }

    Scaffold(
        // 与其余二级页同口径：M3 默认 inset（本页跑在 SubpageActivity 独立窗口里，无外层垫 inset）
        snackbarHost = { AppSnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                windowInsets = pinnedStatusBars(),
                title = { Text("水宝宝一卡通") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val profileName by remember { Graph.displayPrefs(context).profileName }
                .collectAsStateWithLifecycle(initialValue = "")
            val profileStudentId by remember { Graph.displayPrefs(context).profileStudentId }
                .collectAsStateWithLifecycle(initialValue = "")
            val suspendedTargets by SessionStatus.suspended.collectAsStateWithLifecycle()
            val yktState = LoginStateRules.derive(
                credentialExists = savedUsername != null,
                webSessionExists = false,
                suspended = LoginTarget.Ykt in suspendedTargets,
            )
            // 身份卡（DESIGN §3.3）：状态收进右上角胶囊，副行说清这份凭证能干什么
            AccountCard(
                username = savedUsername ?: profileStudentId,
                name = profileName,
                statusText = null,
                statusState = yktState,
                subtitle = if (enabled) {
                    "余额 · 付款码 · 电费共用这一份凭证"
                } else {
                    "填学号与查询密码，验证通过即开启"
                },
                badgeText = when (yktState) {
                    LoginState.LoggedIn -> "已开启"
                    LoginState.Expired -> "已失效"
                    LoginState.NotLoggedIn -> "未开启"
                },
            )

            // 余额（DESIGN §4.19 B5）：开启才显示。大字余额打头，主按钮「充值」、次按钮「消费流水」
            val balanceSnapshot = balance
            if (enabled) {
                val balanceRefreshing by viewModel.balanceRefreshing.collectAsStateWithLifecycle()
                val watching = arrivalState as? CampusCardViewModel.ArrivalState.Watching
                SettingsSection(
                    title = "余额",
                    subtitle = "充值后回到本页，余额与流水会自动刷新",
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                balanceSnapshot?.let { MoneyFormat.fromFen(it.cardFen) } ?: "获取中…",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (balanceSnapshot == null) {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            )
                            Text(
                                when {
                                    balanceSnapshot != null -> buildList {
                                        add("正式卡余额")
                                        if (balanceSnapshot.accountFen > 0) {
                                            add("电子账户 ${MoneyFormat.fromFen(balanceSnapshot.accountFen)}")
                                        }
                                    }.joinToString(" · ")

                                    balanceRefreshing -> "读取中…"
                                    else -> "没取到，点右侧刷新重试"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                            )
                        }
                        // 进页只拉一次、失败静默，给一条显式重试出路（2026-10-07）
                        TextButton(
                            onClick = { viewModel.refreshBalance(force = true) },
                            enabled = !balanceRefreshing,
                        ) { Text("刷新") }
                    }
                    if (watching != null) {
                        Row(
                            modifier = Modifier.padding(top = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "正在等待充值到账（${MoneyFormat.fromFen(watching.orderFen)}）· 约 " +
                                    "${YktArrival.arrivalPollIntervalMs(System.currentTimeMillis() - watching.startedAt) / 1000} 秒检测一次",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.padding(top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = {
                                if (RechargeDisclaimer.isSuppressed(
                                        disclaimerSuppressUntil,
                                        System.currentTimeMillis(),
                                    )
                                ) {
                                    showRechargeSheet = true
                                } else {
                                    showRechargeDisclaimer = true
                                }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = watching == null,
                        ) { Text("充值") }
                        OutlinedButton(
                            onClick = onOpenStatement,
                            modifier = Modifier.weight(1f),
                        ) { Text("消费流水") }
                    }
                }
            }

            SettingsSection(
                title = "账号与凭证",
                subtitle = if (enabled) null else "账号即学号；密码通常为身份证后六位",
            ) {
                if (enabled && !editing) {
                    SettingItem(
                        title = "学号",
                        subtitle = "账号即学号，改学号等于换人",
                        value = savedUsername.orEmpty(),
                    )
                    SettingItem(
                        title = "查询密码",
                        subtitle = "已保存 · 与统一认证密码不是同一个",
                        onClick = { editing = true },
                        showArrow = false,
                        trailing = {
                            Text(
                                "更换",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        },
                    )
                    Text(
                        "付款码等同现金，请勿截图或分享。凭证 Keystore 加密只存本机。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    TextButton(
                        onClick = { confirmDisable = true },
                        enabled = !busy,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    ) { Text("关闭并清除凭证") }
                } else {
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it.filter { c -> c.isDigit() } },
                        label = { Text(if (viewModel.hasSavedAccount) "学号（已保存，可覆盖）" else "学号") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it.filter { c -> c.isDigit() } },
                        label = { Text(if (viewModel.hasSavedAccount) "密码（留空沿用已保存）" else "查询密码") },
                        singleLine = true,
                        visualTransformation =
                            if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(
                                    if (showPassword) HugeIcons.ViewOff else HugeIcons.View,
                                    contentDescription = if (showPassword) "隐藏密码" else "显示密码",
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                    // 保存即开启：先真实登录验证一次，成功才落库
                    Button(
                        onClick = {
                            if (busy) return@Button
                            busy = true
                            viewModel.saveAndEnable(username, password, ::feedback)
                        },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    ) {
                        if (busy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(if (enabled) "保存" else "保存并开启")
                    }
                    if (enabled) {
                        TextButton(
                            onClick = {
                                editing = false
                                password = ""
                            },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("取消") }
                    }
                }
            }

            // 寝室房间（DESIGN §4.24）：官方要求先在缴费平台确认寝室，电费查询与充值才可用
            SettingsSection(
                title = "寝室电费",
                subtitle = "电费查询与充值都以缴费平台确认的房间为准",
            ) {
                SettingValueRow(
                    title = "房间",
                    value = roomLabel ?: "未设置",
                    enabled = enabled,
                    onClick = {
                        roomPickerViewModel.start()
                        showRoomPicker = true
                    },
                )
            }

            // 余额提醒（DESIGN §3.13）：两个来源共用上面那份凭证；参数行只在开关打开时出现
            SettingsSection(
                title = "余额提醒",
                subtitle = "低于阈值发通知，共用这份凭证",
            ) {
                SettingSwitchRow(
                    title = "寝室电费",
                    icon = HugeIcons.Home01,
                    checked = powerAlertEnabled,
                    onCheckedChange = { want -> toggleAlert(want) { viewModel.setPowerAlertEnabled(it) } },
                )
                AlertParams(
                    visible = powerAlertEnabled,
                    threshold = BalanceAlert.powerLabel(powerAlertYuan),
                    interval = BalanceAlert.intervalLabel(powerAlertIntervalHours),
                    onThreshold = { showPowerPicker = true },
                    onInterval = { showPowerIntervalPicker = true },
                )
                SettingSwitchRow(
                    title = "一卡通余额",
                    icon = HugeIcons.CreditCard,
                    checked = yktAlertEnabled,
                    onCheckedChange = { want -> toggleAlert(want) { viewModel.setYktAlertEnabled(it) } },
                )
                AlertParams(
                    visible = yktAlertEnabled,
                    threshold = BalanceAlert.yktLabel(yktAlertYuan),
                    interval = BalanceAlert.intervalLabel(yktAlertIntervalHours),
                    onThreshold = { showYktPicker = true },
                    onInterval = { showYktIntervalPicker = true },
                )
                SettingsNote(
                    "开关打开后可设置阈值与检查间隔；后台按间隔检查（默认 6 小时），" +
                        "可能被省电推迟，余额持续偏低只提醒一次。",
                )
            }

            // 安全说明与免责集中在页尾（DESIGN §3.10）：付款码、凭证存储、风险自负三件事逐字保留
            FooterDisclaimer(
                body = "**付款码等同现金**，会随时变更，**请勿截图或分享**。" +
                    "学号与密码**只存本机**（Keystore 加密，排除云备份），登录时只发给学校对应接口；" +
                    "**启用风险自负**。完整条款见「设置 → 条款与声明」。",
            )
        }
    }

    if (showPowerPicker) {
        WheelValueDialog(
            title = "电费低于多少时提醒",
            values = BalanceAlert.POWER_CHOICES.map { "¥$it" },
            initialIndex = BalanceAlert.powerChoiceIndex(powerAlertYuan),
            onConfirm = { index ->
                showPowerPicker = false
                viewModel.setPowerAlertYuan(BalanceAlert.POWER_CHOICES[index])
            },
            onDismiss = { showPowerPicker = false },
        )
    }

    if (showPowerIntervalPicker) {
        WheelValueDialog(
            title = "电费检查间隔",
            values = BalanceAlert.INTERVAL_CHOICES_HOURS.map { BalanceAlert.intervalLabel(it) },
            initialIndex = BalanceAlert.intervalChoiceIndex(powerAlertIntervalHours),
            onConfirm = { index ->
                showPowerIntervalPicker = false
                viewModel.setPowerAlertIntervalHours(BalanceAlert.INTERVAL_CHOICES_HOURS[index])
            },
            onDismiss = { showPowerIntervalPicker = false },
        )
    }

    if (showYktPicker) {
        WheelValueDialog(
            title = "余额低于多少时提醒",
            values = BalanceAlert.YKT_CHOICES.map { "¥$it" },
            initialIndex = BalanceAlert.yktChoiceIndex(yktAlertYuan),
            onConfirm = { index ->
                showYktPicker = false
                viewModel.setYktAlertYuan(BalanceAlert.YKT_CHOICES[index])
            },
            onDismiss = { showYktPicker = false },
        )
    }

    if (showYktIntervalPicker) {
        WheelValueDialog(
            title = "余额检查间隔",
            values = BalanceAlert.INTERVAL_CHOICES_HOURS.map { BalanceAlert.intervalLabel(it) },
            initialIndex = BalanceAlert.intervalChoiceIndex(yktAlertIntervalHours),
            onConfirm = { index ->
                showYktIntervalPicker = false
                viewModel.setYktAlertIntervalHours(BalanceAlert.INTERVAL_CHOICES_HOURS[index])
            },
            onDismiss = { showYktIntervalPicker = false },
        )
    }

    // 选择寝室（设置 / 改绑）：与生活页同一个弹层与状态机；成功后更新行上的房号
    if (showRoomPicker) {
        PowerRoomPickerSheet(
            viewModel = roomPickerViewModel,
            onDismiss = { showRoomPicker = false },
            onBound = { room ->
                showRoomPicker = false
                roomLabel = room
                scope.launch {
                    snackbar.showNotice(AppNoticeVisuals("已设置寝室：$room", tone = NoticeTone.Success))
                }
            },
        )
    }

    if (confirmDisable) {
        AppConfirmDialog(
            title = "关闭并清除凭证？",
            message = "将清除已保存的学号密码，左侧栏的付款码与钱包回到未开启状态，寝室电费与余额提醒也会一并关闭" +
                "（它们都要用这份凭证）；重新开启时需要重新输入并验证。",
            confirmLabel = "关闭并清除",
            onConfirm = {
                confirmDisable = false
                busy = true
                viewModel.disable {
                    busy = false
                    username = ""
                    scope.launch {
                        snackbar.showNotice(
                            AppNoticeVisuals("已关闭并清除凭证", tone = NoticeTone.Info),
                        )
                    }
                }
            },
            onDismiss = { confirmDisable = false },
            // 清掉已保存的学号密码、连带关掉付款码/钱包/电费与提醒：不可撤销，确认键染红
            danger = true,
        )
    }

    // 充值流程（DESIGN §4.19「充值」）：金额弹层 → 二次确认 → 下单 → 直拉微信 → 等待到账
    if (showRechargeDisclaimer) {
        RechargeDisclaimerDialog(
            closableAfterMs = RechargeDisclaimer.closeLockMs(disclaimerSeenAt, System.currentTimeMillis()),
            onContinue = { suppressWeek ->
                showRechargeDisclaimer = false
                if (suppressWeek) {
                    scope.launch {
                        displayPrefs.setRechargeDisclaimerSuppressUntil(
                            RechargeDisclaimer.suppressUntil(System.currentTimeMillis()),
                        )
                    }
                }
                scope.launch { displayPrefs.markRechargeDisclaimerSeen() }
                showRechargeSheet = true
            },
            onDismiss = {
                showRechargeDisclaimer = false
                scope.launch { displayPrefs.markRechargeDisclaimerSeen() }
            },
        )
    }

    if (showRechargeSheet) {
        RechargeSheet(
            balanceFen = balance?.totalFen,
            // 电子账户余额来自同一份 queryCard（ACCOUNT 行）；null = 不显示账户切换
            accountFen = balance?.accountFen,
            // 二次确认弹窗里加粗展示的目标卡号：与下单链路同一份选取口径
            cardAccount = balance?.cards?.rechargeTargetCard()?.account,
            onDismiss = { showRechargeSheet = false },
            onLaunch = { yuan, toElectric ->
                showRechargeSheet = false
                busy = true
                scope.launch {
                    // 下单到回执最长 [RECHARGE_TIMEOUT_MS]（农行收银台那跳更久），
                    // 弹层已关、页面无任何变化——先给一条常驻的进行中提示（Indefinite），
                    // 结束回调里 busy=false 后由结果 Snackbar 顶掉它
                    val progressJob = scope.launch {
                        snackbar.showNotice(
                            AppNoticeVisuals(
                                "正在发起充值…请稍候",
                                tone = NoticeTone.Info,
                                duration = SnackbarDuration.Indefinite,
                            ),
                        )
                    }
                    val target = if (toElectric) viewModel.electricAccountType() else null
                    viewModel.recharge(
                        yuan,
                        targetAccount = target,
                        // 统一走 startActivityOutsideApp：拉起前把本窗口的推入/滑出过渡换成
                        // 静止，防止从外部应用返回时重放右推动画（微信是 singleTask，开不进
                        // 本 task，「不设 NEW_TASK」只救得了浏览器——2026-09-26 修页面跳动）
                        launchExternal = { intent ->
                            runCatching { context.startActivityOutsideApp(intent) }.isSuccess
                        },
                    ) { notice ->
                        busy = false
                        progressJob.cancel()
                        scope.launch {
                            snackbar.showNotice(AppNoticeVisuals(notice.text, tone = notice.tone))
                        }
                    }
                }
            },
        )
    }

    // 「正在确认到账」弹窗（微信返回且未立即到账时；关闭不影响轮询）
    val pendingConfirm by viewModel.pendingConfirmVisible.collectAsStateWithLifecycle()
    val watchingForDialog = arrivalState as? CampusCardViewModel.ArrivalState.Watching
    if (pendingConfirm && watchingForDialog != null) {
        CampusPendingConfirmDialog(
            orderFen = watchingForDialog.orderFen,
            onDismiss = { viewModel.dismissPendingConfirm() },
            onNotPaid = { viewModel.notPaid() },
        )
    }

    // 到账弹窗**只由常驻的 RechargeHost 弹**（`ui/life/RechargeHost.kt`），本页不再重复弹。
    // 为什么不能在这里也弹：本页是 SubpageActivity 里的另一个 CampusCardViewModel 实例，
    // 两个实例各自跑一份到账轮询、各自持有自己的 ArrivalState——在这里弹一次、用户点掉，
    // 回到主窗口 RechargeHost 那份仍是「未确认」，于是同一个到账会**再弹一次**。
    // 而两个实例之间没有共享状态，谁先到账谁后到账不可控，只能让「弹」这件事只归常驻宿主。
    // 本页保留「正在确认到账」弹窗与超时提示——那是「还没到账」的交代，
    // 属于本页自己的轮询，不与宿主重复。

    // 「支付成功」：付款码页检测到扣款会自己退出，由共享宿主弹在退回来的这一页
    // （`CampusPayResultDialogHost`，2026-10-07 起本页与 MainActivity 根层共用一份实现）
    CampusPayResultDialogHost()
}


/**
 * 提醒参数两行（阈值 / 检查间隔）：开关关着就整段不出现，卡片末尾那行注记说明怎么打开。
 * 收起而不是置灰——这两个值关着也不生效，常驻只会把页面撑长。
 */
@Composable
private fun AlertParams(
    visible: Boolean,
    threshold: String,
    interval: String,
    onThreshold: () -> Unit,
    onInterval: () -> Unit,
) {
    if (!visible) return
    Column(Modifier.padding(start = 46.dp)) {
        SettingValueRow(title = "提醒阈值", value = threshold, enabled = true, onClick = onThreshold)
        SettingValueRow(title = "检查间隔", value = interval, enabled = true, onClick = onInterval)
    }
}

/** 值行：标题 + 右侧当前值，可点换值；`enabled = false` 时置灰且不响应。 */
@Composable
private fun SettingValueRow(
    title: String,
    value: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val haptics = rememberAppHaptics()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .then(
                if (enabled) {
                    Modifier.clickable {
                        haptics.tap()
                        onClick()
                    }
                } else {
                    Modifier
                }
            )
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium,
            color = if (enabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            },
            modifier = Modifier.weight(1f),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = if (enabled) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            },
        )
    }
}
