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
    onCenterSettled: (Double, Double, Double, Double) -> Unit,
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
    /** 高德版权标识的底边距（px），见 [AmapMapView.logoBottomMarginPx]。 */
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

    if (sdkReady && !unavailable) {
        AmapMapView(
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
            zones = zones,
            onClusterTap = onClusterTap,
            onRideTap = onRideTap,
            onCameraApplied = onCameraApplied,
            onUserPan = onUserPan,
            onSdkUnavailable = {
                unavailable = true
                fallbackCallback()
            },
            initialCenter = initialCenter,
            initialZoom = initialZoom,
            logoBottomMarginPx = logoBottomMarginPx,
            modifier = modifier,
        )
    } else {
        OsmMapView(
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
            zones = zones,
            onClusterTap = onClusterTap,
            onRideTap = onRideTap,
            onCameraApplied = onCameraApplied,
            onUserPan = onUserPan,
            initialCenter = initialCenter,
            initialZoom = initialZoom,
            modifier = modifier,
        )
    }
}
