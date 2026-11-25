package edu.jxslu.schedule.ui.me.hub

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import edu.jxslu.schedule.data.repo.AppCacheCleaner
import edu.jxslu.schedule.data.repo.CrashReporter
import edu.jxslu.schedule.domain.SizeText
import edu.jxslu.schedule.startActivityOutsideApp
import edu.jxslu.schedule.ui.common.AppPermissions
import edu.jxslu.schedule.ui.common.PermissionRow
import edu.jxslu.schedule.ui.common.SettingsSection
import edu.jxslu.schedule.ui.icon.AppIcons
import edu.jxslu.schedule.ui.me.WidgetCapabilities

/**
 * 「权限与存储」页留在 `:app` 的两半（iOS 移植 W1 收尾，2026-11-25）。
 *
 * 页框与「存储与诊断」那一节的排版搬进了共享层
 * （`shared/commonMain/.../ui/me/hub/PermissionsStorageScreen.kt`），这里只剩两端**必然不同**
 * 的两块：
 *
 * | 成员 | 是什么 | iOS 有没有 |
 * |---|---|---|
 * | [AndroidPermissionSections] | 六个运行时权限的检测 / 申请 / 跳系统设置 | 没有（另一套授权模型） |
 * | [androidStorageDiagnostics] | `AppCacheCleaner` / `CrashReporter` / `FileProvider` | 没有（换成沙箱目录 + `logs/`，见 iosMain 的 `IosPermissionsStorage`） |
 *
 * ## 为什么权限那一块整块搬给宿主，而不是拆成数据
 *
 * 这里的每一项都是「系统 API + 一次授权回调」：`WidgetCapabilities.isIgnoringBatteryOptimizations`、
 * `NotificationManagerCompat.areNotificationsEnabled`、四个 `ActivityResultContracts` launcher、
 * `AppPermissions.albumWriteNeeded`（Android 10 以下才有相册权限）。抽成数据模型就得把这些
 * 状态机也一起抽走，而 iOS 侧根本没有对应的三档（电池优化白名单 / 自启动在 iOS 不存在，
 * 通知走 `UNUserNotificationCenter` 的显式授权）。按 `design/ios-port.md` §11.2 ④，
 * 平台没有的入口就传 `null` 整块不渲染，比造一个「点了没反应」的行好。
 *
 * Android 侧行为**一字未变**：整段是从原 `PermissionsStorageScreen.kt` 原样切出来的
 * （连同 `ON_RESUME` 重读徽标、拒绝后跳系统设置的每一处注释）。
 */
@Composable
fun PermissionsStorageScreenAndroid(onBack: () -> Unit) {
    PermissionsStorageScreen(
        onBack = onBack,
        storage = rememberAndroidStorageDiagnostics(),
        permissionSections = { AndroidPermissionSections() },
    )
}

/**
 * 权限那一块的平台动作（原 `PermissionsStorageScreen` 的权限半页，逐字搬出）。
 *
 * 六个分区：保持后台可用 / 通知 / 日历 / 定位 / 相册（Android 10 以下才渲染）。
 * 「进页只读检测、点了才申请」的口径没变——已经给过的权限再申请一次，系统会当拒绝处理。
 */
@Composable
fun AndroidPermissionSections() {
    val context = LocalContext.current

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

    SettingsSection(
        title = "保持后台可用",
        subtitle = "不开也能用；开启后提醒与小组件更及时",
    ) {
        PermissionRow(
            title = "忽略电池优化",
            detail = "防止系统冻结后台刷新；不同手机叫「电池优化白名单 / 省电策略无限制」",
            granted = batteryWhitelisted,
            icon = AppIcons.BatteryCharging01,
            actionText = if (batteryWhitelisted) "查看" else "去开启",
            onClick = { WidgetCapabilities.jumpBatteryOptimization(context) },
        )
        PermissionRow(
            title = "允许自启动 / 锁后台",
            detail = "开机与被杀后能自行恢复；在厂商设置里找「自启动 / 允许后台运行 / 锁后台」",
            granted = null,
            icon = AppIcons.Energy,
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
            icon = AppIcons.Notification01,
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
            icon = AppIcons.CalendarSetting01,
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
            icon = AppIcons.MapsLocation02,
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
                icon = AppIcons.Image01,
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
}

/** 进页量一次、清完再量一次的那份描述器；同一份 Context 只造一次。 */
@Composable
fun rememberAndroidStorageDiagnostics(): StorageDiagnostics {
    val context = LocalContext.current.applicationContext
    return remember(context) { androidStorageDiagnostics(context) }
}

/**
 * 「存储与诊断」的 Android 实现：`AppCacheCleaner` + `CrashReporter` + `FileProvider`。
 *
 * 文案与搬移前**逐字一致**（清理缓存的副标题、「共 X」、「N 份崩溃记录 · 经系统分享发送」、
 * 确认弹窗正文），因为这段只在 Android 上出现，改一个字都是用户可见的回归。
 *
 * [StorageDiagnostics.storageLines] 没给：Android 侧的占用已经并进「清理缓存」的副标题
 * （2026-10-08 起的口径），再列三行等于把同一个数字说两遍。
 */
fun androidStorageDiagnostics(context: Context): StorageDiagnostics = StorageDiagnostics(
    cacheScope = "网页缓存 · 地图瓦片 · 崩溃记录等",
    measureCacheBytes = { AppCacheCleaner.measure(context) },
    clearCache = { AppCacheCleaner.clear(context).allOk },
    confirmMessage = { bytes ->
        buildString {
            append("将删除网页缓存与登录状态、离线地图瓦片、崩溃记录、校历图片等临时数据")
            bytes?.let { append("（共 ").append(SizeText.format(it)).append("）") }
            append("。不影响课表成绩、笔记作业、账号密码等数据；下次打开教务网页需要重新登录。")
        }
    },
    diagnostics = DiagnosticsExport(
        count = { CrashReporter.listFiles(context).size },
        subtitle = { count ->
            if (count > 0) "$count 份崩溃记录 · 经系统分享发送" else "没有崩溃记录"
        },
        export = { shareLatestCrash(context) },
    ),
)

/**
 * 把最新一份崩溃记录交给系统分享；`false` = 打不开（没有文件 / FileProvider 拒绝 / 没有可用目标）。
 *
 * 与搬移前同一段：`FileProvider` 的 authority 是 `${applicationId}.fileprovider`，
 * debug 与 release 两个包名不同，**不能在共享层写死**——所以这条缝留在 `:app`。
 */
private fun shareLatestCrash(context: Context): Boolean {
    val latest = CrashReporter.listFiles(context).firstOrNull() ?: return false
    return runCatching {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            latest,
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
        true
    }.getOrDefault(false)
}
