package edu.jxslu.schedule.ui.wenqu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import edu.jxslu.schedule.data.wenqu.WenquRepository
import edu.jxslu.schedule.domain.WenquRules
import edu.jxslu.schedule.platform.Services
import edu.jxslu.schedule.platform.collectAsStateWithLifecycle
import edu.jxslu.schedule.ui.common.AppConfirmDialog
import edu.jxslu.schedule.ui.common.AppNoticeVisuals
import edu.jxslu.schedule.ui.common.AppSnackbarHost
import edu.jxslu.schedule.ui.common.FooterDisclaimer
import edu.jxslu.schedule.ui.common.NoticeTone
import edu.jxslu.schedule.ui.common.SettingItem
import edu.jxslu.schedule.ui.common.SettingsNote
import edu.jxslu.schedule.ui.common.SettingSwitchRow
import edu.jxslu.schedule.ui.common.SettingsSection
import edu.jxslu.schedule.ui.common.WheelValueDialog
import edu.jxslu.schedule.ui.common.showNotice
import edu.jxslu.schedule.ui.icon.AppIcons
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import edu.jxslu.schedule.data.prefs.setWenquActivityAlertEnabled
import edu.jxslu.schedule.data.prefs.setWenquActivityIntervalHours
import edu.jxslu.schedule.data.prefs.setWenquHideFull
import edu.jxslu.schedule.data.prefs.setWenquSort
import edu.jxslu.schedule.data.prefs.setWenquWeeklyAlertEnabled
import edu.jxslu.schedule.data.prefs.setWenquWeeklyAlertOffsetMinutes
import edu.jxslu.schedule.data.prefs.wenquActivityAlertEnabled
import edu.jxslu.schedule.data.prefs.wenquActivityIntervalHours
import edu.jxslu.schedule.data.prefs.wenquHideFull
import edu.jxslu.schedule.data.prefs.wenquSort
import edu.jxslu.schedule.data.prefs.wenquWeeklyAlertEnabled
import edu.jxslu.schedule.data.prefs.wenquWeeklyAlertOffsetMinutes

/**
 * 问渠设置页（DESIGN §3.28）——问渠页顶栏那枚齿轮进来的二级页。
 *
 * 2026-10-08 用户口径：**主页只管展示工时数**，记录入口、活动提醒、退出登录都收在这里。
 *
 * 三块：
 * 1. **账号**——登录的手机号（脱敏）+「我的志愿服务记录」入口（记录页 `WENQU_RECORDS`）；
 * 2. **提醒**——新增志愿活动提醒开关 + 检测间隔（默认 24 小时，档位与成绩/考试那几链同一套）；
 * 3. **登录**——退出登录（二次确认，清本机凭证）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WenquSettingsScreen(
    onBack: () -> Unit,
    onOpenRecords: () -> Unit,
    /**
     * 问渠仓库（会话 / 工时 / 退出登录 / 星级证书）。**由宿主传进来**（Android =
     * `Graph.wenqu(this)`）：它需要一个 `WenquSessionStore`（Android 是
     * EncryptedSharedPreferences），共享层拿不到那条凭证链，所以不做成 `Services` 成员。
     */
    repository: WenquRepository,
    /**
     * 「新增志愿活动提醒」开关变了之后的重排（Android = `WenquActivityAlertReminder
     * .onSettingsChanged`）。`suspend`：它要读到刚落盘的开关值，不能与写盘并发。
     * `null` = 本平台没有后台提醒链 → 那一整段「提醒」不渲染。
     */
    onAlertSettingsChanged: (suspend () -> Unit)? = null,
    /** 「每周日 12:00 抢活动提醒」开关 / 提前量变了之后的重排（Android = `WenquWeeklyAlertReminder`）。 */
    onWeeklyAlertSettingsChanged: (suspend () -> Unit)? = null,
    /** 请求通知权限（Android 13+ 运行时权限；iOS 是系统授权弹窗）。`null` = 本平台没有。 */
    onRequestNotifPermission: (() -> Unit)? = null,
    /**
     * **下载星级证书**：取 PDF → 落盘 → 打开（Android 走 `WenquCertStore` + 系统 PDF 查看器）。
     * 失败时抛异常，页面把 `message` 当提示发出去；[onNotice] 收打开环节的回执。
     * `null` = 本平台没有这条链 → 那一行不渲染。
     */
    onDownloadCert: (suspend (onNotice: (String) -> Unit) -> Unit)? = null,
) {
    val prefs = remember { Services.current.displayPrefs() }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val session by remember { repository.session }.collectAsStateWithLifecycle()

    val alertEnabled by prefs.wenquActivityAlertEnabled.collectAsState(initial = false)
    val intervalHours by prefs.wenquActivityIntervalHours
        .collectAsState(initial = WenquRules.ACTIVITY_ALERT_INTERVAL_DEFAULT)
    // 每周日 12:00「抢活动」提醒（2026-10-11）：开关 + 提前量
    val weeklyEnabled by prefs.wenquWeeklyAlertEnabled.collectAsState(initial = false)
    val weeklyOffsetMinutes by prefs.wenquWeeklyAlertOffsetMinutes
        .collectAsState(initial = WenquRules.WEEKLY_ALERT_OFFSET_DEFAULT_MIN)
    // 浏览偏好（2026-10-11）：与列表页那份落盘值同一处
    val sortName by prefs.wenquSort.collectAsState(initial = WenquRules.Sort.RecruitingFirst.name)
    val hideFull by prefs.wenquHideFull.collectAsState(initial = false)
    var intervalPickerOpen by remember { mutableStateOf(false) }
    var weeklyOffsetPickerOpen by remember { mutableStateOf(false) }
    var sortPickerOpen by remember { mutableStateOf(false) }
    var confirmingLogout by remember { mutableStateOf(false) }
    /** 星级证书下载中（防连点；动作只读，但仍别并发打）。 */
    var downloadingCert by remember { mutableStateOf(false) }
    /** 记录条数：进页读一次仓库缓存（冷缓存时它自己会抓一次），失败就不显示数字。 */
    var recordsCount by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(Unit) {
        recordsCount = runCatching { repository.volunteer().list.size }.getOrNull()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("问渠设置", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        snackbarHost = { AppSnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SettingsSection(
                title = "账号",
                subtitle = "问渠实践教育平台（第三方系统）· 账号是手机号",
            ) {
                SettingItem(
                    title = "已登录手机号",
                    subtitle = "本机保存，退出登录即清除",
                    value = session?.phone?.takeIf { it.isNotBlank() }?.let(WenquRules::maskPhone),
                )
                SettingItem(
                    title = "我的志愿服务记录",
                    subtitle = "我参加过的活动 · 每条可点开看详情",
                    icon = AppIcons.Note01,
                    value = recordsCount?.let { "共 $it 条" },
                    onClick = onOpenRecords,
                )
                // 证书下载是「取 PDF → 落盘 → 交给系统打开」，三步都在平台侧；
                // 本平台没有这条链时整行不渲染（`design/ios-port.md` §11.2 ④）。
                onDownloadCert?.let { download ->
                    SettingItem(
                        title = "下载星级证书",
                        subtitle = "导出志愿星级证书 PDF（随时可重下）",
                        icon = AppIcons.Certificate01,
                        value = if (downloadingCert) "下载中…" else null,
                        enabled = !downloadingCert,
                        onClick = {
                            downloadingCert = true
                            scope.launch {
                                try {
                                    download { msg ->
                                        scope.launch {
                                            snackbar.showNotice(AppNoticeVisuals(message = msg))
                                        }
                                    }
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    snackbar.showNotice(
                                        AppNoticeVisuals(
                                            message = e.message ?: "下载失败",
                                            tone = NoticeTone.Error,
                                        ),
                                    )
                                } finally {
                                    downloadingCert = false
                                }
                            }
                        },
                    )
                }
            }

            // 「提醒」整段都是平台能力（周期任务 / 精确闹钟 / 通知权限）。两端都没有那条链时
            // 整段不渲染，而不是留几个拨了没反应的开关。
            if (onAlertSettingsChanged != null || onWeeklyAlertSettingsChanged != null) {
                SettingsSection(
                    title = "提醒",
                    subtitle = "问渠平台上出现新的可报名志愿活动时通知",
                ) {
                    onAlertSettingsChanged?.let { onChanged ->
                        SettingSwitchRow(
                            title = "新增志愿活动提醒",
                            subtitle = "检测到新的「招募中」活动就发一条通知",
                            icon = AppIcons.Notification03,
                            checked = alertEnabled,
                            onCheckedChange = { want ->
                                // 开关变了要重排周期任务（这一路可能是五条链里最勤的那条）+ 立刻评估一次
                                scope.launch {
                                    prefs.setWenquActivityAlertEnabled(want)
                                    onChanged()
                                }
                                if (want) onRequestNotifPermission?.invoke()
                            },
                        )
                        SettingItem(
                            title = "检测间隔",
                            subtitle = "多久在后台扫一次新活动（与其它提醒共用周期任务）",
                            value = WenquRules.activityIntervalLabel(intervalHours),
                            enabled = alertEnabled,
                            onClick = { intervalPickerOpen = true },
                        )
                        SettingsNote("首次开启只记基线、不立刻通知；间隔别调太短")
                    }

                    if (onAlertSettingsChanged != null && onWeeklyAlertSettingsChanged != null) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }

                    // 每周日 12:00「抢活动」（2026-10-11，用户口径）：与上面那条是两件事——
                    // 那条是事后发现（周期扫列表），这条是事前蹲点（固定时刻，不扫接口）。
                    onWeeklyAlertSettingsChanged?.let { onChanged ->
                        SettingSwitchRow(
                            title = "每周日 12:00 抢活动提醒",
                            subtitle = "学校常在这时发布新志愿活动，到点前提醒你去蹲",
                            icon = AppIcons.AlarmClock,
                            checked = weeklyEnabled,
                            onCheckedChange = { want ->
                                scope.launch {
                                    prefs.setWenquWeeklyAlertEnabled(want)
                                    onChanged()
                                }
                                if (want) onRequestNotifPermission?.invoke()
                            },
                        )
                        SettingItem(
                            title = "提前提醒",
                            subtitle = "到点前多久响一声",
                            value = WenquRules.weeklyOffsetLabel(weeklyOffsetMinutes),
                            enabled = weeklyEnabled,
                            onClick = { weeklyOffsetPickerOpen = true },
                        )
                        SettingsNote("到点前提醒一次，抢活动要自己去问渠")
                    }
                }
            }

            SettingsSection(
                title = "浏览偏好",
                subtitle = "列表页也能改，两处同一份",
            ) {
                SettingItem(
                    title = "默认排序",
                    subtitle = "招募中优先 / 报名截止临近 / 最新发布",
                    value = WenquRules.coerceSort(sortName).label,
                    onClick = { sortPickerOpen = true },
                )
                SettingSwitchRow(
                    title = "默认隐藏已满",
                    subtitle = "招募人数没填的不算满",
                    checked = hideFull,
                    onCheckedChange = { want -> scope.launch { prefs.setWenquHideFull(want) } },
                )
            }

            SettingsSection(title = "登录", subtitle = "凭证只存本机（加密存储，不进云备份）") {
                SettingItem(
                    title = "退出登录",
                    subtitle = "清除本机保存的问渠手机号、密码与会话",
                    icon = AppIcons.Logout03,
                    onClick = { confirmingLogout = true },
                )
            }

            FooterDisclaimer(
                WENQU_FOOTER,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }

    if (intervalPickerOpen) {
        WheelValueDialog(
            title = "检测间隔",
            values = WenquRules.ACTIVITY_ALERT_INTERVAL_CHOICES.map(WenquRules::activityIntervalLabel),
            initialIndex = WenquRules.activityIntervalChoiceIndex(intervalHours),
            onConfirm = { index ->
                intervalPickerOpen = false
                val value = WenquRules.ACTIVITY_ALERT_INTERVAL_CHOICES[index]
                scope.launch {
                    prefs.setWenquActivityIntervalHours(value)
                    onAlertSettingsChanged?.invoke()
                    snackbar.showNotice("检测间隔已设为${WenquRules.activityIntervalLabel(value)}")
                }
            },
            onDismiss = { intervalPickerOpen = false },
        )
    }

    if (weeklyOffsetPickerOpen) {
        WheelValueDialog(
            title = "提前提醒",
            values = WenquRules.WEEKLY_ALERT_OFFSET_CHOICES.map(WenquRules::weeklyOffsetLabel),
            initialIndex = WenquRules.weeklyOffsetChoiceIndex(weeklyOffsetMinutes),
            onConfirm = { index ->
                weeklyOffsetPickerOpen = false
                val value = WenquRules.WEEKLY_ALERT_OFFSET_CHOICES[index]
                scope.launch {
                    prefs.setWenquWeeklyAlertOffsetMinutes(value)
                    // 改提前量要重排闹钟（提醒时刻变了），但**不动已发键**——同一周不会重复报
                    onWeeklyAlertSettingsChanged?.invoke()
                    snackbar.showNotice("已设为${WenquRules.weeklyOffsetLabel(value)}")
                }
            },
            onDismiss = { weeklyOffsetPickerOpen = false },
        )
    }

    if (sortPickerOpen) {
        WheelValueDialog(
            title = "默认排序",
            values = WenquRules.SORT_CHOICES.map { it.label },
            initialIndex = WenquRules.SORT_CHOICES.indexOf(WenquRules.coerceSort(sortName)).coerceAtLeast(0),
            onConfirm = { index ->
                sortPickerOpen = false
                val value = WenquRules.SORT_CHOICES[index]
                scope.launch {
                    prefs.setWenquSort(value.name)
                    snackbar.showNotice("默认排序已设为${value.label}")
                }
            },
            onDismiss = { sortPickerOpen = false },
        )
    }

    if (confirmingLogout) {
        AppConfirmDialog(
            title = "退出登录",
            message = "退出后将清除本机保存的问渠凭证，工时卡片与活动列表都会隐藏。",
            confirmLabel = "退出",
            danger = true,
            onConfirm = {
                confirmingLogout = false
                repository.logout()
                // 退完直接回问渠页——那一页订阅着登录态，此刻已经是登录表单了；
                // 留在设置页只会看到一张空的「已登录手机号」
                onBack()
            },
            onDismiss = { confirmingLogout = false },
        )
    }
}
