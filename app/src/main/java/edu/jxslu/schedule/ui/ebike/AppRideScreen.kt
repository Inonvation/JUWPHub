package edu.jxslu.schedule.ui.ebike

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import edu.jxslu.schedule.Graph
import edu.jxslu.schedule.SubpageActivity
import edu.jxslu.schedule.SubpageScreen
import edu.jxslu.schedule.data.prefs.AMAP_CHOICE_UNSET
import edu.jxslu.schedule.platform.Services
import edu.jxslu.schedule.ui.common.AppPermissions

/**
 * 快趣出行页的**Android 宿主**（iOS 移植 P4，2026-11-25）。
 *
 * 页面本体（[RideScreen]）已经在 `shared/commonMain`，它按 `design/ios-port.md` §11.2
 * 把每个平台动作都收成可空参数。这个文件就是 Android 那一侧的**全部装配**：
 *
 * | 共享层要的 | Android 给谁 |
 * |---|---|
 * | `viewModel` / `ebikeViewModel` | `viewModel { }`（配置变更不重建；VM 由宿主建） |
 * | `bikeMap` | [amapBikeMapSlot]（高德矢量 / osmdroid 栅格，见 `BikeMapHost`） |
 * | `onScanBodyCode` | zxing `ScanContract` + `EbikeScanActivity` 取景页 |
 * | `onOpenWechatScan` / `onOpenWechatForSettle` | `openWechatScan` / `openWechatForSettle`（拉起微信） |
 * | `onRequestLocationPermission` | `RequestMultiplePermissions`（`AppPermissions.location`） |
 * | `requestNotificationPermission` | `rememberNotificationPermissionGate`（`POST_NOTIFICATIONS`） |
 * | 三个「去设置」 | `AppPermissions.jumpLocationSettings` / `jumpAppDetails` / `jumpNotificationSettings` |
 * | `onOpenKvcxAccount` | `SubpageActivity.start(SubpageScreen.KVCX)` |
 * | `mapCache` / `showMapSourceChoice` | [AndroidBikeMapCache] / `true`（高德 ↔ 栅格两档） |
 *
 * 微信那条路的提示文案原先落在页面 Snackbar 上，页面已经搬走，这里用 Toast 原样转达
 * （文案本身仍由 `openWechatScan` / `openWechatForSettle` 出，没有第二份）。
 */
@Composable
internal fun AppRideScreen(
    onBack: () -> Unit,
    initialFocusCarNum: String?,
) {
    val context = LocalContext.current
    // 快趣会话 / 图层缓存 / 本机用车控制器的装配口径全在 Factory 里（含取不到时的降级）
    val mapViewModel: BikeMapViewModel = viewModel(
        factory = BikeMapViewModelFactory(
            Graph.kqcxBikeClient(context),
            Graph.displayPrefs(context),
        ),
    )
    val ebikeViewModel: EbikeViewModel = viewModel {
        // initializer 不是组合函数，读不了 CompositionLocal——用装配根持有的 application context
        EbikeViewModel(Graph.displayPrefs(Graph.appContext), AndroidEbikeCodePlatform)
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { _ ->
        // 结果不用回传：页面的「回到前台」effect 会重读 `BikeLocator.hasPermission()`
        // 并据此启动连续定位流（与「用户去系统设置里改」同一条路）
    }
    // 扫车身码：相机与取景页是 Android 的事，识别到的原文交回共享层解析
    var pendingScan by remember { mutableStateOf<((String?) -> Unit)?>(null) }
    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val callback = pendingScan
        pendingScan = null
        callback?.invoke(result.contents)
    }
    val onScanBodyCode: (onResult: (String?) -> Unit) -> Unit = { onResult ->
        pendingScan = onResult
        scanLauncher.launch(
            ScanOptions().apply {
                setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                setPrompt("对准车身上的二维码")
                setBeepEnabled(false)
                setOrientationLocked(true)
                // 自带的 CaptureActivity 只有取景框 + 提示，没有手电筒与相册入口
                setCaptureActivity(EbikeScanActivity::class.java)
            },
        )
    }
    val notificationPermission = rememberNotificationPermissionGate()
    val toastHint: (String) -> Unit = { message ->
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    RideScreen(
        viewModel = mapViewModel,
        ebikeViewModel = ebikeViewModel,
        onBack = onBack,
        initialFocusCarNum = initialFocusCarNum,
        bikeMap = AmapBikeMapSlot,
        onScanBodyCode = onScanBodyCode,
        onOpenWechatScan = { openWechatScan(context, toastHint) },
        onOpenWechatForSettle = { openWechatForSettle(context, toastHint) },
        onOpenKvcxAccount = { SubpageActivity.start(context, SubpageScreen.KVCX) },
        onRequestLocationPermission = {
            locationPermissionLauncher.launch(AppPermissions.location.toTypedArray())
        },
        onOpenLocationSettings = { AppPermissions.jumpLocationSettings(context) },
        onOpenAppDetailsSettings = { AppPermissions.jumpAppDetails(context) },
        requestNotificationPermission = notificationPermission,
        onOpenNotificationSettings = { AppPermissions.jumpNotificationSettings(context) },
        mapCache = AndroidBikeMapCache,
        showMapSourceChoice = true,
    )
}

/**
 * 地图槽位的 Android 实现（原 `RideScreen` 里那段 `BikeMapHost(...)` 调用整段搬到这里）。
 *
 * 底图选择（`amapChoice`）在页面那一侧没有意义了（iOS 只有一个底图），所以只在
 * **Android 这个实现内部**读一次偏好：同意过高德走官方矢量，否则 / SDK 不可用时走 osmdroid 栅格。
 * 页面传进来的 [BikeMapInput] 与 `BikeMapHost` 的形参是一一对应的（那份 KDoc 里写明的口径）。
 */
private val AmapBikeMapSlot = BikeMapSlot { input, modifier ->
    val amapChoice by Services.current.displayPrefs().ebikeAmapChoice
        .collectAsStateWithLifecycle(AMAP_CHOICE_UNSET)
    BikeMapHost(
        amapChoice = amapChoice,
        clusters = input.clusters,
        selectedKey = input.selectedKey,
        bikes = input.bikes,
        byBike = input.byBike,
        selectedBikeCarNum = input.selectedBikeCarNum,
        onBikeTap = input.onBikeTap,
        userLat = input.userLat,
        userLng = input.userLng,
        userAccuracyMeters = input.userAccuracyMeters,
        userHeadingFlow = input.userHeadingFlow,
        rideLat = input.rideLat,
        rideLng = input.rideLng,
        highlightLat = input.highlightLat,
        highlightLng = input.highlightLng,
        highlightLabel = input.highlightLabel,
        showCampusFence = input.showCampusFence,
        colors = input.colors,
        camera = input.camera,
        onCenterSettled = input.onCenterSettled,
        onViewportMeasured = input.onViewportMeasured,
        zones = input.zones,
        onClusterTap = input.onClusterTap,
        onRideTap = input.onRideTap,
        onCameraApplied = input.onCameraApplied,
        onUserPan = input.onUserPan,
        onFallback = input.onFallback,
        initialCenter = input.initialCenter,
        initialZoom = input.initialZoom,
        logoBottomMarginPx = input.logoBottomMarginPx,
        modifier = modifier,
    )
}
