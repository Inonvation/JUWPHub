package edu.jxslu.schedule.ui.ebike

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import edu.jxslu.schedule.data.prefs.AMAP_CHOICE_AGREED
import edu.jxslu.schedule.domain.BikeCluster
import edu.jxslu.schedule.domain.GcjPoint
import edu.jxslu.schedule.domain.KvcxZones
import edu.jxslu.schedule.domain.NearbyBike
import kotlinx.coroutines.flow.StateFlow

/**
 * 地图底图的选择器（DESIGN §3.9 / §4.23，2026-11-17）。
 *
 * 两条渲染路径**同一份参数契约**，页面只跟这里打交道：
 * - 用户同意过、且 SDK 能初始化 → [AmapMapView]（高德官方矢量底图，楼栋与 POI 名称新、
 *   任意缩放清晰）；
 * - 其余情况 → [OsmMapView]（osmdroid + 高德栅格，**回退路径**）。
 *
 * 回退有两个触发点：
 * 1. `MapsInitializer.initialize` 返回 false（key 空 / 包名或 SHA1 对不上）；
 * 2. [AmapMapView] 在 [AmapMapView] 的超时窗口内没等到 `onMapLoaded`（key 被停用、
 *    高德服务不可达）。这一条才是 key 失效的主要表现——`initialize` 对失效 key 仍返回 true。
 *
 * 回退是**本进程内**的：下次冷启动会重新试一次官方 SDK，用户不必自己去设置里改。
 *
 * ## 2026-11-25：参数契约抬进 `commonMain`
 *
 * 逐条的形参收成一份 [BikeMapInput]（`shared/commonMain/.../ui/ebike/BikeMapContract.kt`），
 * 目的是让 iOS 的 MapKit 实现（`BikeMapView.ios.kt`）吃**同一个类型**。这里只做三件 Android
 * 特有的事，其余全部原样透传：
 *
 * 1. `amapChoice` 判据（用户同意隐私政策没有）——只有 Android 有这套开关；
 * 2. `sdkReady` / `unavailable` 两个状态与 [AmapMapView] 的 `onSdkUnavailable` 回退；
 * 3. 把 [BikeMapInput.onFallback] 转成上面那条回退（Android 侧唯一的降级信号）。
 *
 * **形参顺序与默认值一格没动**（页面的调用点不用改）；两个渲染实现也保持原来的形参表
 * 不动。等页面本身搬进共享层时，[BikeMapInput] 会直接成为页面与槽位之间的货币，
 * 这一份转手就会消失。
 */
@Composable
internal fun BikeMapHost(
    amapChoice: String,
    clusters: List<BikeCluster>,
    selectedKey: String?,
    bikes: List<NearbyBike> = emptyList(),
    byBike: Boolean = false,
    selectedBikeCarNum: String? = null,
    onBikeTap: (String) -> Unit = {},
    userLat: Double?,
    userLng: Double?,
    userAccuracyMeters: Float = 0f,
    userHeadingFlow: StateFlow<Float?>,
    rideLat: Double?,
    rideLng: Double?,
    highlightLat: Double? = null,
    highlightLng: Double? = null,
    highlightLabel: String? = null,
    /** 校区围栏画不画：与「只看本校」开关同步（2026-11-18 用户要求）。 */
    showCampusFence: Boolean = true,
    colors: BikeMarkerColors,
    camera: CameraRequest?,
    onCenterSettled: (Double, Double, Double) -> Unit,
    /**
     * 视口半对角线（米）的测量值：底图就绪时一次、之后每次相机停稳一次。
     * 与「停手」无关（程序性移动的停稳回调会被静默区吃掉），见 `BikeMapViewModel.onViewportMeasured`。
     */
    onViewportMeasured: (Double) -> Unit = {},
    zones: KvcxZones,
    onClusterTap: (String) -> Unit,
    onRideTap: () -> Unit,
    onCameraApplied: (Long) -> Unit,
    onUserPan: () -> Unit = {},
    /** 本次会话里降级成了栅格底图；页面据此显示一行说明。 */
    onFallback: () -> Unit = {},
    /** 建图中心（GCJ-02）；null 回落到校区中心。见 [AmapMapView] 同名参数的说明。 */
    initialCenter: GcjPoint? = null,
    /** 建图缩放；null 用默认级别。 */
    initialZoom: Double? = null,
    /**
     * 高德版权标识的底边距（px），见 [AmapMapView.logoBottomMarginPx]。
     * **Android 专用，iOS 忽略**（MapKit 没有可挪的版权标识），理由写在 [BikeMapInput] 上。
     */
    logoBottomMarginPx: Int = 0,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val fallbackCallback by rememberUpdatedState(onFallback)

    // 同意过才碰 SDK；agreePrivacy 幂等，重复调没有副作用。
    // 注意 prepare() 只说明「条件具备」，key 好不好用要等 AmapMapView 的 onMapLoaded。
    val sdkReady = remember(amapChoice) {
        if (amapChoice != AMAP_CHOICE_AGREED) {
            false
        } else {
            // 关掉再打开 = 用户手动重试：清掉上一次 initialize 的结论，重新走一遍
            AmapMaps.reset()
            AmapMaps.agreePrivacy(context)
            AmapMaps.prepare(context)
        }
    }
    // 降级标记跟着选择走：设置里重新打开开关要能再试一次官方 SDK
    var unavailable by remember(amapChoice) { mutableStateOf(false) }

    // 页面参数 → 共享契约。**一字段一行、两边同名**（`x = x`）：抄错一个字段编译器帮不上忙，
    // 所以这里刻意不做任何重排或改名，便于逐行对照 `BikeMapContract.kt` 的字段表复核。
    val input = BikeMapInput(
        clusters = clusters,
        selectedKey = selectedKey,
        bikes = bikes,
        byBike = byBike,
        selectedBikeCarNum = selectedBikeCarNum,
        onBikeTap = onBikeTap,
        userLat = userLat,
        userLng = userLng,
        userAccuracyMeters = userAccuracyMeters,
        userHeadingFlow = userHeadingFlow,
        rideLat = rideLat,
        rideLng = rideLng,
        highlightLat = highlightLat,
        highlightLng = highlightLng,
        highlightLabel = highlightLabel,
        showCampusFence = showCampusFence,
        colors = colors,
        camera = camera,
        onCenterSettled = onCenterSettled,
        onViewportMeasured = onViewportMeasured,
        zones = zones,
        onClusterTap = onClusterTap,
        onRideTap = onRideTap,
        onCameraApplied = onCameraApplied,
        onUserPan = onUserPan,
        onFallback = onFallback,
        initialCenter = initialCenter,
        initialZoom = initialZoom,
        logoBottomMarginPx = logoBottomMarginPx,
    )

    if (sdkReady && !unavailable) {
        AmapMapView(
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
            onSdkUnavailable = {
                unavailable = true
                fallbackCallback()
            },
            initialCenter = input.initialCenter,
            initialZoom = input.initialZoom,
            logoBottomMarginPx = input.logoBottomMarginPx,
            modifier = modifier,
        )
    } else {
        OsmMapView(
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
            initialCenter = input.initialCenter,
            initialZoom = input.initialZoom,
            modifier = modifier,
        )
    }
}
