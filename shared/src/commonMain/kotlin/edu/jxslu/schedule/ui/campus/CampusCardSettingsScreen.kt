package edu.jxslu.schedule.ui.campus

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import edu.jxslu.schedule.data.session.LoginState
import edu.jxslu.schedule.data.session.LoginStateRules
import edu.jxslu.schedule.data.session.LoginTarget
import edu.jxslu.schedule.data.session.SessionStatus
import edu.jxslu.schedule.data.ykt.rechargeTargetCard
import edu.jxslu.schedule.domain.AppClock
import edu.jxslu.schedule.domain.BalanceAlert
import edu.jxslu.schedule.domain.MoneyFormat
import edu.jxslu.schedule.domain.RechargeDisclaimer
import edu.jxslu.schedule.domain.YktArrival
import edu.jxslu.schedule.platform.Services
import edu.jxslu.schedule.platform.collectAsStateWithLifecycle
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
import edu.jxslu.schedule.ui.common.rememberResumeTick
import edu.jxslu.schedule.ui.common.showNotice
import edu.jxslu.schedule.ui.icon.AppIcons
import edu.jxslu.schedule.ui.life.PowerRoomPickerSheet
import edu.jxslu.schedule.ui.life.PowerRoomPickerViewModel
import kotlinx.coroutines.launch

/**
 * 我的 → 校园卡（DESIGN §3.10）：管开启状态、凭证与两个余额提醒，不看付款码。
 *
 * 页面顺序固定为 身份 → 余额 → 账号与凭证 → 寝室电费 → 余额提醒 → 页尾免责：
 * 未开启时余额卡不画，其余卡片不换位，用户每次进页都在同一处找同一个设置。
 *
 * 凭证交互：默认未开启；填学号 + 查询密码点「保存并开启」，真实登录验证成功才落库并开启。
 * 已开启时本卡只显示学号与「查询密码 · 更换」，改密码或换学号才展开输入框；
 * 「关闭并清除」在卡尾（危险操作，确认后清凭证并回落两个提醒）。
 *
 * ## 三处平台槽位（iOS 移植 Y1，2026-11-25）
 *
 * 搬进 `shared/commonMain` 后，页面里那些「只有 Android 有」的动作按
 * `design/ios-port.md` §11.2 ④ 收成可空参数，**null = 本平台确实没有，对应那块不渲染**：
 *
 * | 槽位 | 谁给 | null 时 iOS 上少什么 |
 * |---|---|---|
 * | [onOpenPaymentUrl] | Android：`openExternal`（`Intent.ACTION_VIEW` + 过渡静止） | 余额卡上的「充值」按钮、充值免责声明、充值弹层 → **这一版不做充值** |
 * | [notificationPermission] | Android：`POST_NOTIFICATIONS` launcher | 「余额提醒」整节（阈值 / 间隔 / 两个开关） |
 * | [powerRoomPicker] | 两端都给（电费仓库 + 凭证存储两端都有） | 拿不到仓库时才为 null → 「寝室电费」整节的房间行不渲染 |
 *
 * 余额提醒的**排程**钩子不在页面上，在 `CampusCardViewModel(alerts = …)`：页面只管两件事
 * 「要不要显示这一节」与「点开关时请一次通知权限」，排程归 VM。
 *
 * iOS 的「回到前台补检到账」走 `rememberResumeTick()`（`ui/common/ResumedTicker` 的既有
 * expect/actual，iOS 侧恒 0 = 只在进页时做一次），与搬迁前 `LifecycleEventObserver` 的
 * 观感一致：进页一次 + 每次回到前台一次。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampusCardSettingsScreen(
    viewModel: CampusCardViewModel,
    onBack: () -> Unit,
    /** 跳消费流水页（DESIGN §4.19 B4/B5） */
    onOpenStatement: () -> Unit = {},
    /**
     * 打开外部支付链接（微信 / 官方收银台），返回 false = 打不开（页面已给文案）。
     *
     * **null = 本平台不做充值**：余额卡上的「充值」按钮整块不渲染，充值免责声明与充值弹层
     * 也随之不出现（iOS 这版没有 `LSApplicationQueriesSchemes`，拉起微信必被系统拦下）。
     * 消费流水入口照常在——它不依赖任何平台能力。
     */
    onOpenPaymentUrl: ((String) -> Boolean)? = null,
    /**
     * 通知权限申请；**null = 本平台没有余额提醒的通知渠道** → 「余额提醒」整节不渲染。
     *
     * 与 `CampusCardViewModel(alerts = null)` 成对：一个管显示，一个管排程。
     * 只给其中一个会出现「开关点了、排程不生效」的死开关。
     */
    notificationPermission: CampusNotificationPermission? = null,
    /**
     * 「选择寝室」弹层的状态机，由宿主建（Android `viewModel { … }`、iOS `remember { … }`）。
     * **null = 宿主拿不到电费仓库 / 凭证存储** → 「寝室电费」整节不渲染。
     */
    powerRoomPicker: PowerRoomPickerViewModel? = null,
) {
    val enabled by viewModel.enabled.collectAsStateWithLifecycle()
    val balance by viewModel.balance.collectAsStateWithLifecycle()
    val arrivalState by viewModel.arrivalState.collectAsStateWithLifecycle()
    val snackbar = remember { androidx.compose.material3.SnackbarHostState() }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    // 超时安抚提示（只弹一次：Timeout 状态被确认后转 Idle）
    LaunchedEffect(arrivalState) {
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

    // 进页一次 + 每次回到前台一次（不改搬迁前的口径：`LifecycleEventObserver` 注册时
    // 会补发一次 ON_RESUME，等价于这里的「进页也跑一次」）。从微信/浏览器返回的瞬间
    // 立即补检一轮，不等 5 秒周期；进程被杀重启的恢复路径在 VM 的 init 里。
    LaunchedEffect(rememberResumeTick()) {
        viewModel.onHostResume()
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
    LaunchedEffect(Unit) {
        roomLabel = runCatching { Services.current.powerReadingStore().latest() }
            .getOrNull()?.roomName?.takeIf { it.isNotBlank() }
    }

    // 充值免责声明（与生活页同一条口径，domain/RechargeDisclaimer）：充值入口打开
    // 弹层前先过提醒，勾「一周内不再提醒」静默 7 天；首次（从未确认过）锁 5 秒
    // 才能关，之后任何一次弹出立即可关。继续或取消都算「已确认」。
    val displayPrefs = remember { Services.current.displayPrefs() }
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

    // 两个提醒开关共用：开启那一刻请求通知权限，拒绝不阻塞开关本身
    // （口径同「我的 → 上课提醒」页：功能在系统设置里授权后自动生效）。
    // 本平台没有通知渠道（notificationPermission = null）时这一节整块不渲染，走不到这里。
    val requestNotifPermission = {
        notificationPermission?.request {
            showNotice("未授予通知权限，提醒不会显示；可在系统设置里重新开启", NoticeTone.Warning)
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
        // 与其余二级页同口径：M3 默认 inset（本页跑在二级页窗口里，无外层垫 inset）
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
            val profileName by remember { Services.current.displayPrefs().profileName }
                .collectAsStateWithLifecycle(initialValue = "")
            val profileStudentId by remember { Services.current.displayPrefs().profileStudentId }
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
                                    "${YktArrival.arrivalPollIntervalMs(AppClock.nowMillis() - watching.startedAt) / 1000} 秒检测一次",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    // 充值入口（2026-10-08 起「充值」与「消费流水」并排）：本平台没有
                    // 拉起外部支付的能力（onOpenPaymentUrl = null）时只留「消费流水」一条，
                    // 别给一个点了没反应的按钮（design/ios-port.md §11.2 ④）
                    val openPayment = onOpenPaymentUrl
                    Row(
                        modifier = Modifier.padding(top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (openPayment != null) {
                            Button(
                                onClick = {
                                    if (RechargeDisclaimer.isSuppressed(
                                            disclaimerSuppressUntil,
                                            AppClock.nowMillis(),
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
                        }
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
                        "付款码等同现金，请勿截图或分享。凭证加密后只存本机。",
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
                                    if (showPassword) AppIcons.ViewOff else AppIcons.View,
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

            // 寝室房间（DESIGN §4.24）：官方要求先在缴费平台确认寝室，电费查询与充值才可用。
            // 宿主没给选择器（拿不到电费仓库或凭证）时整节不渲染——它是唯一改绑入口。
            if (powerRoomPicker != null) {
                SettingsSection(
                    title = "寝室电费",
                    subtitle = "电费查询与充值都以缴费平台确认的房间为准",
                ) {
                    SettingValueRow(
                        title = "房间",
                        value = roomLabel ?: "未设置",
                        enabled = enabled,
                        onClick = {
                            powerRoomPicker.start()
                            showRoomPicker = true
                        },
                    )
                }
            }

            // 余额提醒（DESIGN §3.13）：两个来源共用上面那份凭证；参数行只在开关打开时出现。
            // 本平台没有通知渠道（notificationPermission = null）时整节不渲染：
            // 开关写了偏好也没人执行，只会是一堆亮着但永远不生效的死开关。
            if (notificationPermission != null) {
                SettingsSection(
                    title = "余额提醒",
                    subtitle = "低于阈值发通知，共用这份凭证",
                ) {
                    SettingSwitchRow(
                        title = "寝室电费",
                        icon = AppIcons.Home01,
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
                        icon = AppIcons.CreditCard,
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
            }

            // 安全说明与免责集中在页尾（DESIGN §3.10）：付款码、凭证存储、风险自负三件事逐字保留
            FooterDisclaimer(
                body = "**付款码等同现金**，会随时变更，**请勿截图或分享**。" +
                    "学号与密码**只存本机**（加密存储，排除云备份），登录时只发给学校对应接口；" +
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
    if (showRoomPicker && powerRoomPicker != null) {
        PowerRoomPickerSheet(
            viewModel = powerRoomPicker,
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
            closableAfterMs = RechargeDisclaimer.closeLockMs(disclaimerSeenAt, AppClock.nowMillis()),
            onContinue = { suppressWeek ->
                showRechargeDisclaimer = false
                if (suppressWeek) {
                    scope.launch {
                        displayPrefs.setRechargeDisclaimerSuppressUntil(
                            RechargeDisclaimer.suppressUntil(AppClock.nowMillis()),
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

    // 充值弹层只在能拉起外部支付时出现（onOpenPaymentUrl 非空才会把 showRechargeSheet 点亮）
    val openPayment = onOpenPaymentUrl
    if (showRechargeSheet && openPayment != null) {
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
                        // 平台动作由宿主注入：Android 那边顺手把本窗口的推入/滑出过渡换成静止，
                        // 防止从外部应用返回时重放右推动画（2026-09-26 修页面跳动）
                        launchExternal = openPayment,
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
    // 为什么不能在这里也弹：本页是另一个窗口里的另一个 CampusCardViewModel 实例，
    // 两个实例各自跑一份到账轮询、各自持有自己的 ArrivalState——在这里弹一次、用户点掉，
    // 回到主窗口 RechargeHost 那份仍是「未确认」，于是同一个到账会**再弹一次**。
    // 而两个实例之间没有共享状态，谁先到账谁后到账不可控，只能让「弹」这件事只归常驻宿主。
    // 本页保留「正在确认到账」弹窗与超时提示——那是「还没到账」的交代，
    // 属于本页自己的轮询，不与宿主重复。

    // 「支付成功」：付款码页检测到扣款会自己退出，由共享宿主弹在退回来的这一页
    // （`CampusPayResultDialogHost`，2026-10-07 起本页与主窗口根层共用一份实现）
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
