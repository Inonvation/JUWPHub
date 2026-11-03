package edu.jxslu.schedule.ui.me.hub

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import edu.jxslu.schedule.BuildConfig
import edu.jxslu.schedule.Graph
import edu.jxslu.schedule.data.prefs.NoticeConsentRecord
import edu.jxslu.schedule.data.repo.AppCacheCleaner
import edu.jxslu.schedule.data.repo.CrashReporter
import edu.jxslu.schedule.data.repo.UpdateCheckClient
import edu.jxslu.schedule.data.repo.UpdateCheckResult
import edu.jxslu.schedule.domain.FirstRunNotice
import edu.jxslu.schedule.domain.FirstRunNotices
import edu.jxslu.schedule.domain.NoticeConsent
import edu.jxslu.schedule.domain.RechargeDisclaimer
import edu.jxslu.schedule.domain.UpdateManifest
import edu.jxslu.schedule.startActivityOutsideApp
import edu.jxslu.schedule.ui.common.AppNoticeVisuals
import edu.jxslu.schedule.ui.common.AppPermissions
import edu.jxslu.schedule.ui.common.AppSnackbarHost
import edu.jxslu.schedule.ui.common.DisclaimerDialog
import edu.jxslu.schedule.ui.common.DialogWindowFadeEffect
import edu.jxslu.schedule.ui.common.LicensesDialog
import edu.jxslu.schedule.ui.common.NoticeDialog
import edu.jxslu.schedule.ui.common.NoticeTone
import edu.jxslu.schedule.ui.common.PermissionRow
import edu.jxslu.schedule.ui.common.SettingItem
import edu.jxslu.schedule.ui.common.SettingSwitchRow
import edu.jxslu.schedule.ui.common.SettingsSection
import edu.jxslu.schedule.ui.common.pinnedStatusBars
import edu.jxslu.schedule.ui.common.showNotice
import edu.jxslu.schedule.ui.ebike.EbikeMapCache
import edu.jxslu.schedule.ui.me.WidgetCapabilities
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Agreement01
import me.rerere.hugeicons.stroke.Alert02
import me.rerere.hugeicons.stroke.BatteryCharging01
import me.rerere.hugeicons.stroke.Bug01
import me.rerere.hugeicons.stroke.CalendarSetting01
import me.rerere.hugeicons.stroke.Delete02
import me.rerere.hugeicons.stroke.Energy
import me.rerere.hugeicons.stroke.Github
import me.rerere.hugeicons.stroke.Image01
import me.rerere.hugeicons.stroke.InformationCircle
import me.rerere.hugeicons.stroke.MapsLocation02
import me.rerere.hugeicons.stroke.Megaphone01
import me.rerere.hugeicons.stroke.Notification01
import me.rerere.hugeicons.stroke.Refresh
import me.rerere.hugeicons.stroke.SoftwareLicense
import me.rerere.hugeicons.stroke.SystemUpdate01

/** 公开仓库地址（GPL-3.0）；「开源仓库」点击后经系统浏览器打开。 */
private const val REPO_URL = "https://github.com/Inonvation/JUWPHub"

/**
 * 设置 → 关于 → 关于与更新：版本、手动 / 自动检查更新、开源仓库与许可。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutUpdateScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val repo = remember { Graph.repository(context) }
    val autoUpdateCheck by remember { repo.autoUpdateCheckEnabled }.collectAsStateWithLifecycle(
        initialValue = remember { runBlocking { repo.autoUpdateCheckEnabled.first() } },
    )
    var showLicenses by remember { mutableStateOf(false) }
    val showNotice: (String) -> Unit = { message ->
        scope.launch {
            snackbar.showNotice(AppNoticeVisuals(message, tone = NoticeTone.Warning))
        }
    }

    val updateClient = remember { UpdateCheckClient() }
    var updateChecking by remember { mutableStateOf(false) }
    var updateAvailable by remember { mutableStateOf<UpdateManifest?>(null) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    fun checkForUpdate() {
        if (updateChecking) return
        updateChecking = true
        updateAvailable = null
        scope.launch {
            val result = updateClient.check()
            updateChecking = false
            when (result) {
                is UpdateCheckResult.UpdateAvailable -> {
                    updateAvailable = result.latest
                    showUpdateDialog = true
                }
                is UpdateCheckResult.UpToDate -> scope.launch {
                    snackbar.showNotice(
                        AppNoticeVisuals(
                            "当前 v${BuildConfig.VERSION_NAME} 已是最新版本",
                            tone = NoticeTone.Success,
                        ),
                    )
                }
                is UpdateCheckResult.Failed -> showNotice("检查失败：网络不可用或更新清单暂时无法访问，稍后再试")
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets.statusBars,
                title = { Text("关于与更新") },
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
            SettingsSection(title = "应用") {
                SettingItem(
                    title = "关于 JUWPHub",
                    subtitle = "非学校官方应用 · 教务与第三方接口风险自负",
                    icon = HugeIcons.InformationCircle,
                    value = "v${BuildConfig.VERSION_NAME}",
                    showArrow = false,
                )
                SettingItem(
                    title = "检查更新",
                    subtitle = when {
                        updateChecking -> "正在检查…"
                        updateAvailable != null ->
                            "发现新版本 v${updateAvailable!!.version} · 点此查看"
                        else -> "从发布仓库检查新版本 · 可选国内镜像下载"
                    },
                    icon = HugeIcons.Refresh,
                    enabled = !updateChecking,
                    onClick = {
                        if (updateAvailable != null) showUpdateDialog = true else checkForUpdate()
                    },
                )
                SettingSwitchRow(
                    title = "自动检查更新",
                    subtitle = "启动时静默检查新版本；发现更新弹窗提醒，可忽略此版本",
                    checked = autoUpdateCheck,
                    onCheckedChange = { value ->
                        scope.launch { repo.setAutoUpdateCheckEnabled(value) }
                    },
                    icon = HugeIcons.SystemUpdate01,
                )
            }

            SettingsSection(title = "开源") {
                SettingItem(
                    title = "开源仓库",
                    subtitle = "github.com/Inonvation/JUWPHub",
                    icon = HugeIcons.Github,
                    onClick = { openUrl(context, REPO_URL)?.let(showNotice) },
                )
                SettingItem(
                    title = "开源许可",
                    subtitle = "本应用 GPL-3.0 · 第三方组件与参考项目",
                    icon = HugeIcons.SoftwareLicense,
                    onClick = { showLicenses = true },
                )
            }
        }
    }

    if (showLicenses) {
        LicensesDialog(onDismiss = { showLicenses = false })
    }
    if (showUpdateDialog) {
        updateAvailable?.let { manifest ->
            UpdateAvailableDialog(
                manifest = manifest,
                onDismiss = { showUpdateDialog = false },
                onIgnore = {
                    showUpdateDialog = false
                    scope.launch { repo.setIgnoredUpdateVersion(manifest.version) }
                    scope.launch {
                        snackbar.showNotice(
                            AppNoticeVisuals(
                                "已忽略该版本，启动时不再自动提醒",
                                tone = NoticeTone.Success,
                            ),
                        )
                    }
                },
            )
        }
    }
}

/**
 * 设置 → 数据与权限 → 权限与存储（DESIGN §3.12 / §3.3，2026-11-01 合并）。
 *
 * 权限与存储合并为一页：权限部分沿用「进页只读检测、点了才申请」的口径，
 * 存储部分保留缓存清理与诊断日志。通知 / 日历 / 定位 / 相册都从这里申请，
 * 提醒页不再重复放「系统通知权限」行。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionsStorageScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val showNotice: (String) -> Unit = { message ->
        scope.launch {
            snackbar.showNotice(AppNoticeVisuals(message, tone = NoticeTone.Warning))
        }
    }

    // ---- 权限状态（自原权限设置页合并）----
    var batteryWhitelisted by remember {
        mutableStateOf(WidgetCapabilities.isIgnoringBatteryOptimizations(context))
    }
    var notifEnabled by remember {
        mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled())
    }
    var calendarGranted by remember { mutableStateOf(AppPermissions.calendarGranted(context)) }
    var locationGranted by remember { mutableStateOf(AppPermissions.locationGranted(context)) }
    // 权限给全了不代表定位能用：系统「位置信息」总开关另算，两种失败要分开讲
    var locationServiceOn by remember { mutableStateOf(AppPermissions.locationServiceEnabled(context)) }
    var albumWriteGranted by remember { mutableStateOf(AppPermissions.albumWriteGranted(context)) }

    // 从系统设置 / 厂商管家页回来时重读徽标（小组件设置页同口径）
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                batteryWhitelisted = WidgetCapabilities.isIgnoringBatteryOptimizations(context)
                notifEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
                calendarGranted = AppPermissions.calendarGranted(context)
                locationGranted = AppPermissions.locationGranted(context)
                locationServiceOn = AppPermissions.locationServiceEnabled(context)
                albumWriteGranted = AppPermissions.albumWriteGranted(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        // 以实际系统状态为准重读（拒绝后徽标也要跟着变）
        notifEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        // 拒绝后不再原地重弹：系统第二次起会静默拒绝，直接带去系统通知设置页
        if (!granted) AppPermissions.jumpNotificationSettings(context)
    }

    val calendarPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        calendarGranted = grants.values.all { it }
        if (!grants.values.all { it }) AppPermissions.jumpAppDetails(context)
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        // 精确给不给都算可用，粗略那一档也够定位到那一片
        locationGranted = grants.values.any { it }
        locationServiceOn = AppPermissions.locationServiceEnabled(context)
        if (!locationGranted) AppPermissions.jumpAppDetails(context)
    }

    val albumWriteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        albumWriteGranted = granted
        if (!granted) AppPermissions.jumpAppDetails(context)
    }

    val requestNotif = {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            AppPermissions.jumpNotificationSettings(context)
        }
    }

    // 申请前只挑缺的那几个：已经给过的权限再申请一次，系统会当拒绝处理（框都不弹）
    val requestCalendar = {
        val needed = AppPermissions.missing(context, AppPermissions.calendar)
        if (needed.isEmpty()) {
            AppPermissions.jumpAppDetails(context)
        } else {
            calendarPermissionLauncher.launch(needed.toTypedArray())
        }
    }

    val requestLocation = {
        val needed = AppPermissions.missing(context, AppPermissions.location)
        if (needed.isEmpty()) {
            AppPermissions.jumpAppDetails(context)
        } else {
            locationPermissionLauncher.launch(needed.toTypedArray())
        }
    }

    // ---- 存储与诊断（原 PermissionsStorageScreen）----
    var cacheBytes by remember { mutableStateOf<Long?>(null) }
    var showClearCache by remember { mutableStateOf(false) }
    var clearingCache by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        cacheBytes = AppCacheCleaner.measure(context)
    }

    var crashRefresh by remember { mutableStateOf(0) }
    val crashFiles = remember(crashRefresh) { CrashReporter.listFiles(context) }
    val crashFileCount = crashFiles.size
    fun runClearCache() {
        if (clearingCache) return
        clearingCache = true
        scope.launch {
            val result = AppCacheCleaner.clear(context)
            cacheBytes = AppCacheCleaner.measure(context)
            crashRefresh++
            clearingCache = false
            showClearCache = false
            scope.launch {
                snackbar.showNotice(
                    AppNoticeVisuals(
                        if (result.allOk) "已清理缓存" else "部分缓存没清干净，可稍后重试",
                        tone = if (result.allOk) NoticeTone.Success else NoticeTone.Warning,
                    ),
                )
            }
        }
    }

    var shareLatestCrash by remember { mutableStateOf(false) }
    LaunchedEffect(shareLatestCrash) {
        if (!shareLatestCrash) return@LaunchedEffect
        shareLatestCrash = false
        val files = CrashReporter.listFiles(context)
        if (files.isEmpty()) return@LaunchedEffect
        runCatching {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                files.first(),
            )
            context.startActivityOutsideApp(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND)
                        .setType("text/plain")
                        .putExtra(Intent.EXTRA_STREAM, uri)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                    "崩溃记录",
                ),
            )
        }.onFailure { showNotice("无法发起分享") }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                windowInsets = pinnedStatusBars(),
                title = { Text("权限与存储") },
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
                title = "保持后台可用",
                subtitle = "不开也能用；开启后提醒与小组件更及时",
            ) {
                PermissionRow(
                    title = "忽略电池优化",
                    detail = "防止系统冻结后台刷新；不同手机叫「电池优化白名单 / 省电策略无限制」",
                    granted = batteryWhitelisted,
                    icon = HugeIcons.BatteryCharging01,
                    actionText = if (batteryWhitelisted) "查看" else "去开启",
                    onClick = { WidgetCapabilities.jumpBatteryOptimization(context) },
                )
                PermissionRow(
                    title = "允许自启动 / 锁后台",
                    detail = "开机与被杀后能自行恢复；在厂商设置里找「自启动 / 允许后台运行 / 锁后台」",
                    granted = null,
                    icon = HugeIcons.Energy,
                    actionText = "去设置",
                    onClick = { WidgetCapabilities.jumpAutoStart(context) },
                )
            }
            SettingsSection(
                title = "通知",
                subtitle = "上课提醒、作业截止与免费骑行提醒都要通知权限",
            ) {
                PermissionRow(
                    title = "允许通知",
                    detail = if (notifEnabled) {
                        "已开启；在系统设置里可按渠道细分"
                    } else {
                        "关闭时提醒不会弹出；点「去开启」授权"
                    },
                    granted = notifEnabled,
                    icon = HugeIcons.Notification01,
                    actionText = if (notifEnabled) "去设置" else "去开启",
                    onClick = { requestNotif() },
                )
            }
            SettingsSection(
                title = "日历",
                subtitle = "把课表同步进手机日历要用这一对权限",
            ) {
                PermissionRow(
                    title = "日历读写",
                    detail = if (calendarGranted) {
                        "已开启；课表能同步进系统日历"
                    } else {
                        "未开启时日历同步会停在提示上；点「去开启」授权"
                    },
                    granted = calendarGranted,
                    icon = HugeIcons.CalendarSetting01,
                    actionText = if (calendarGranted) "查看" else "去开启",
                    onClick = { requestCalendar() },
                )
            }
            SettingsSection(
                title = "定位",
                subtitle = "只给「附近单车」看车在哪用；不给也能手动拖地图找车",
            ) {
                PermissionRow(
                    title = "位置信息",
                    detail = when {
                        !locationGranted -> "未开启时「附近单车」定位不动；点「去开启」授权，选「大致位置」也够用"
                        !locationServiceOn -> "App 已授权，但系统的「位置信息」总开关关着，定位仍会失败"
                        else -> "已开启；只在单车地图页取一次坐标，不做后台跟踪"
                    },
                    granted = locationGranted && locationServiceOn,
                    icon = HugeIcons.MapsLocation02,
                    actionText = if (locationGranted) "查看" else "去开启",
                    // 只有权限真的缺了才申请；总开关关着时带用户去应用详情页没有意义
                    onClick = {
                        if (locationGranted) AppPermissions.jumpAppDetails(context) else requestLocation()
                    },
                )
            }
            // Android 10 起系统相册走 MediaStore，不需要权限；这一段只服务老系统的设备
            if (AppPermissions.albumWriteNeeded) {
                SettingsSection(
                    title = "相册",
                    subtitle = "把共享单车二维码存进系统相册；Android 10 起系统相册不再需要权限",
                ) {
                    PermissionRow(
                        title = "相册写入",
                        detail = if (albumWriteGranted) {
                            "已开启；保存二维码能落到相册目录"
                        } else {
                            "未开启时「保存到相册」会提示没有相册写入权限，二维码出得来但存不下"
                        },
                        granted = albumWriteGranted,
                        icon = HugeIcons.Image01,
                        actionText = if (albumWriteGranted) "查看" else "去开启",
                        onClick = {
                            if (albumWriteGranted) {
                                AppPermissions.jumpAppDetails(context)
                            } else {
                                albumWriteLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                            }
                        },
                    )
                }
            }
            SettingsSection(title = "存储与诊断") {
                SettingItem(
                    title = "清理缓存",
                    subtitle = buildString {
                        append("网页缓存 · 地图瓦片 · 崩溃记录等")
                        cacheBytes?.let { append(" · 共 ").append(EbikeMapCache.sizeLabel(it)) }
                    },
                    icon = HugeIcons.Delete02,
                    onClick = { showClearCache = true },
                )
                SettingItem(
                    title = "导出诊断日志",
                    subtitle = crashFileCount.takeIf { it > 0 }?.let { "$it 份崩溃记录 · 经系统分享发送" }
                        ?: "没有崩溃记录",
                    icon = HugeIcons.Bug01,
                    showArrow = crashFileCount > 0,
                    onClick = { shareLatestCrash = crashFileCount > 0 },
                )
            }
        }
    }

    if (showClearCache) {
        AlertDialog(
            onDismissRequest = { if (!clearingCache) showClearCache = false },
            title = { Text("清理缓存？") },
            text = {
                DialogWindowFadeEffect()
                Text(
                    buildString {
                        append("将删除网页缓存与登录状态、离线地图瓦片、崩溃记录、校历图片等临时数据")
                        cacheBytes?.let { append("（共 ").append(EbikeMapCache.sizeLabel(it)).append("）") }
                        append("。不影响课表成绩、笔记作业、账号密码等数据；下次打开教务网页需要重新登录。")
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { runClearCache() },
                    enabled = !clearingCache,
                ) {
                    Text(
                        text = if (clearingCache) "清理中…" else "清理",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showClearCache = false },
                    enabled = !clearingCache,
                ) { Text("取消") }
            },
        )
    }
}

/**
 * 设置 → 关于 → 条款与声明：用户须知、免责声明与充值风险。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TermsDeclarationsScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { Graph.displayPrefs(context) }
    val userNoticeConsent by remember {
        prefs.noticeConsent(FirstRunNotice.UserNotice)
    }.collectAsStateWithLifecycle(initialValue = NoticeConsentRecord(version = 0, atMs = 0L))
    val userNoticeConfirmed =
        NoticeConsent.isConsented(userNoticeConsent.version, FirstRunNotices.VERSION)
    var showDisclaimer by remember { mutableStateOf(false) }
    var showUserNotice by remember { mutableStateOf(false) }
    var showRechargeNotice by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets.statusBars,
                title = { Text("条款与声明") },
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
            SettingsSection(title = "条款") {
                SettingItem(
                    title = "用户须知",
                    subtitle = "非官方 · 数据只存本机 · 第三方接口可能随时失效",
                    icon = HugeIcons.Megaphone01,
                    value = if (userNoticeConfirmed) "已确认" else "未确认",
                    onClick = { showUserNotice = true },
                )
                SettingItem(
                    title = "免责声明",
                    subtitle = "非学校官方应用 · 第三方接口风险 · 仅供学习",
                    icon = HugeIcons.Agreement01,
                    onClick = { showDisclaimer = true },
                )
                SettingItem(
                    title = "充值风险提醒",
                    subtitle = "电费与校园卡充值的风险提示全文",
                    icon = HugeIcons.Alert02,
                    onClick = { showRechargeNotice = true },
                )
            }
        }
    }

    if (showDisclaimer) {
        DisclaimerDialog(onDismiss = { showDisclaimer = false })
    }
    if (showUserNotice) {
        NoticeDialog(
            title = FirstRunNotices.title(FirstRunNotice.UserNotice),
            intro = FirstRunNotices.intro(FirstRunNotice.UserNotice),
            items = FirstRunNotices.items(FirstRunNotice.UserNotice),
            onDismiss = {
                showUserNotice = false
                scope.launch {
                    prefs.markNoticeConsented(FirstRunNotice.UserNotice, FirstRunNotices.VERSION)
                }
            },
        )
    }
    if (showRechargeNotice) {
        NoticeDialog(
            title = "充值风险提醒",
            intro = "以下三条在每个充值入口都会再提醒一次；这里是随时可查的全文。",
            items = RechargeDisclaimer.ITEMS,
            onDismiss = { showRechargeNotice = false },
        )
    }
}
