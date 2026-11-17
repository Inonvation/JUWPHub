package edu.jxslu.schedule.ui.ebike

import android.graphics.Color
import android.util.Log
import android.view.MotionEvent
import android.view.animation.DecelerateInterpolator
import android.view.ViewConfiguration
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.layout.onSizeChanged
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.MapView
import com.amap.api.maps.model.BitmapDescriptorFactory
import com.amap.api.maps.model.CameraPosition
import com.amap.api.maps.model.Circle
import com.amap.api.maps.model.CircleOptions
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.Marker
import com.amap.api.maps.model.MarkerOptions
import com.amap.api.maps.model.Polygon
import com.amap.api.maps.model.PolygonOptions
import edu.jxslu.schedule.BuildConfig
import edu.jxslu.schedule.domain.BikeCluster
import edu.jxslu.schedule.domain.BikeNearby
import edu.jxslu.schedule.domain.GcjPoint
import edu.jxslu.schedule.domain.KvcxZones
import edu.jxslu.schedule.domain.NearbyBike
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

/**
 * 高德地图层（DESIGN §3.9 / §4.23，2026-11-17）。
 *
 * 与 [OsmMapView] **同一份参数契约**，调用方（[BikeMapHost]）二选一，切换时不用改页面。
 * 差异只有两处：
 * - 坐标：高德底图就是 GCJ-02，应用内部也是 GCJ-02，**整条显示基准转换链都不需要**
 *   （osmdroid 那套 `MapDatum` / `Gcj02.toWgs84` 只在天地图 HD 下用过）。
 * - 停手判定：高德给 `onCameraChangeFinish`（相机真的停住才回调），比 osmdroid 那个
 *   120ms 轮询准，也不再需要 [SettleWatcher]；程序性移动仍由 [CameraSuppressor] 吃掉。
 *
 * 标记全部走 `Marker` + 离屏位图（见 [AmapMarkerBitmaps]），围栏 / 禁停区 / 还车点范围走
 * `Polygon`，定位精度圈走 `Circle`（半径单位是米，比 osmdroid 那边折像素更准）。
 */
@Composable
internal fun AmapMapView(
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
    colors: BikeMarkerColors,
    camera: CameraRequest?,
    onCenterSettled: (Double, Double, Double, Double) -> Unit,
    zones: KvcxZones,
    onClusterTap: (String) -> Unit,
    onRideTap: () -> Unit,
    onCameraApplied: (Long) -> Unit,
    onUserPan: () -> Unit = {},
    /**
     * SDK 拿不到底图（key 校验失败 / 网络不通 / 服务不可用）时回调一次，调用方据此整层
     * 回退 osmdroid 栅格。**判据是「[SDK_LOAD_TIMEOUT_MS] 内地图没加载完」**：
     * `MapsInitializer.initialize` 返回 void、key 校验又在服务端异步做，问不出结论，
     * 唯一可靠的信号就是 `onMapLoaded` 一直不来。
     */
    onSdkUnavailable: () -> Unit = {},
    /**
     * 建图时就用这个中心（GCJ-02），null 回落到校区中心。
     *
     * 为什么要它（2026-11-17 用户报「进页面不会自动到我位置」）：页面每次进入都会新建地图，
     * 而「移到用户位置」是一次性的 camera 请求——它可能在 `locate()` 返回之前就被消费，
     * 也可能因为地图还没就绪被吞掉。把**上次已知位置**直接当建图中心，第一帧就落在人身上，
     * 不再依赖那次请求。只在创建时读一次，之后的变化走 camera 请求。
     */
    initialCenter: GcjPoint? = null,
    /** 建图时的缩放；null 用默认级别。 */
    initialZoom: Double? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val userHeading by userHeadingFlow.collectAsStateWithLifecycle()

    val settleCallback by rememberUpdatedState(onCenterSettled)
    val clusterTapCallback by rememberUpdatedState(onClusterTap)
    val bikeTapCallback by rememberUpdatedState(onBikeTap)
    val rideTapCallback by rememberUpdatedState(onRideTap)
    val appliedCallback by rememberUpdatedState(onCameraApplied)
    val panCallback by rememberUpdatedState(onUserPan)

    val suppressor = remember { CameraSuppressor() }
    // 用户拖动时把正在跑的逐帧缓动停掉（见 animateCameraEased）
    val interrupted = remember { AtomicBoolean(false) }
    val bitmaps = remember(density) { AmapMarkerBitmaps(density) }

    var mapSize by remember { mutableStateOf(IntSize.Zero) }
    val drawn = remember { DrawnOverlayInputs() }

    val mapView = remember {
        // 仍然用 MapView（SurfaceView）而不是 TextureMapView（2026-11-17 两次实测）：
        // TextureMapView 参与 Compose 合成、可以整层淡入，但全屏相机动画会掉帧——
        // 用户直接反馈「定位/回校区的移动不如以前顺」。闪屏改用「盖一层不透明占位」
        // 解决（见下面 mapLoaded 那段），SurfaceView 的动画流畅度留住。
        MapView(context).apply {
            onCreate(null)
            map.uiSettings.apply {
                isZoomControlsEnabled = false
                isCompassEnabled = false
                isScaleControlsEnabled = false
                isRotateGesturesEnabled = false
                isTiltGesturesEnabled = false
            }
            map.mapType = AMap.MAP_TYPE_NORMAL
            map.setMaxZoomLevel(MAX_ZOOM)
            map.setMinZoomLevel(MIN_ZOOM)
            map.moveCamera(
                CameraUpdateFactory.newLatLngZoom(
                    LatLng(
                        initialCenter?.lat ?: BikeNearby.DEFAULT_CENTER_LAT,
                        initialCenter?.lng ?: BikeNearby.DEFAULT_CENTER_LNG,
                    ),
                    (initialZoom ?: BikeNearby.DEFAULT_ZOOM).toFloat(),
                ),
            )
            map.setOnCameraChangeListener(object : AMap.OnCameraChangeListener {
                override fun onCameraChange(position: CameraPosition?) = Unit

                override fun onCameraChangeFinish(position: CameraPosition?) {
                    val target = position?.target ?: return
                    if (suppressor.shouldSwallow(target.latitude, target.longitude)) return
                    settleCallback(
                        target.latitude,
                        target.longitude,
                        position.zoom.toDouble(),
                        viewportHalfDiagonalMeters(this@apply.map),
                    )
                }
            })
            // 只读不拦：返回 false，地图自己的手势照常处理（监听器在下面挂）
        }
    }
    val aMap = remember(mapView) { mapView.map }
    val touchTracker = remember {
        MapTouchTracker(ViewConfiguration.get(context).scaledTouchSlop.toFloat()) {
            // 用户接管地图：先停掉程序性相机动画，别让缓动继续和手指抢（与 osmdroid 版同口径）
            interrupted.set(true)
            aMap.stopAnimation()
            panCallback()
        }
    }
    DisposableEffect(mapView) {
        mapView.setOnTouchListener { _, event ->
            touchTracker.onTouch(event)
            false
        }
        onDispose { mapView.setOnTouchListener(null) }
    }
    val layers = remember(aMap, bitmaps) { AmapLayers(aMap, bitmaps) }

    // 底图真的画出来了才认为 SDK 可用（key 校验在服务端做，initialize 的返回值靠不住）
    var mapLoaded by remember { mutableStateOf(false) }
    val unavailableCallback by rememberUpdatedState(onSdkUnavailable)
    DisposableEffect(aMap) {
        aMap.setOnMapLoadedListener { mapLoaded = true }
        onDispose { aMap.setOnMapLoadedListener(null) }
    }
    LaunchedEffect(aMap) {
        delay(SDK_LOAD_TIMEOUT_MS)
        if (!mapLoaded) unavailableCallback()
    }

    // 首帧之前盖一层不透明占位（2026-11-17 第二次修「进页面闪白/闪黑」）。
    //
    // MapView 是 SurfaceView，首帧之前那块是它自己的底色：高德这里偏白，主题深色下看着
    // 就是一记白闪；把地图整层调透明又变成「一记黑闪」（占位换成页面底色，一样是跳变）。
    // 现在改成：地图正常不透明，**上面盖一块和页面同色的占位**，底图真出来了再淡出——
    // 用户看到的是「地图区在加载」，不是「闪了一下」。
    val placeholderVisible by animateFloatAsState(
        targetValue = if (mapLoaded) 0f else 1f,
        animationSpec = tween(durationMillis = 180),
        label = "amapPlaceholder",
    )

    /**
     * 进页面自动跟到我的位置（2026-11-17 用户口径：别绕，进界面就把镜头落到人身上）。
     *
     * 为什么不用 ViewModel 那次 `camera` 请求：它是一次性的，可能在定位回来之前就被消费，
     * 也可能被「恢复上次视野」那条请求盖掉。这里直接盯 `userLat/userLng`：地图画出来了、
     * 拿到一次定位，就落一次镜头，**只做一次**。用户先自己拖过图（[interrupted]）就不抢。
     */
    var centeredOnUser by remember { mutableStateOf(false) }
    LaunchedEffect(mapLoaded, userLat, userLng) {
        if (centeredOnUser || !mapLoaded) return@LaunchedEffect
        val lat = userLat ?: return@LaunchedEffect
        val lng = userLng ?: return@LaunchedEffect
        centeredOnUser = true
        if (interrupted.get()) return@LaunchedEffect
        suppressor.aim(lat, lng)
        suppressor.quietFor(MOVE_QUIET_MS)
        aMap.moveCamera(
            CameraUpdateFactory.newLatLngZoom(
                LatLng(lat, lng),
                aMap.cameraPosition?.zoom ?: BikeNearby.DEFAULT_ZOOM.toFloat(),
            ),
        )
        if (BuildConfig.DEBUG) Log.d("BikeMapAmap", "首帧落到用户位置: $lat,$lng")
    }

    // 点标记：按 Marker 上挂的 payload 分派，不依赖任何绘制顺序
    DisposableEffect(aMap) {
        aMap.setOnMarkerClickListener { marker ->
            when (val payload = marker.`object`) {
                is MarkerPayload.Cluster -> {
                    clusterTapCallback(payload.key)
                    true
                }

                is MarkerPayload.Bike -> {
                    bikeTapCallback(payload.carNum)
                    true
                }

                is MarkerPayload.Ride -> {
                    rideTapCallback()
                    true
                }

                else -> false
            }
        }
        onDispose { aMap.setOnMarkerClickListener(null) }
    }

    Box(modifier = modifier) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { mapSize = it },
            update = {
            val inputs = MapOverlayInputs(
                colors = colors,
                clusters = clusters,
                selectedKey = selectedKey,
                userPoint = if (userLat != null && userLng != null) {
                    GcjPoint(userLat, userLng)
                } else {
                    null
                },
                userAccuracyMeters = userAccuracyMeters,
                userHeading = userHeading,
                ridePoint = if (rideLat != null && rideLng != null) {
                    GcjPoint(rideLat, rideLng)
                } else {
                    null
                },
                highlightPoint = if (highlightLat != null && highlightLng != null) {
                    GcjPoint(highlightLat, highlightLng)
                } else {
                    null
                },
                highlightLabel = highlightLabel,
                zones = zones,
                bikes = bikes,
                byBike = byBike,
                selectedBikeCarNum = selectedBikeCarNum,
            )
            // 只比「静态图层」那一份：蓝点 / 精度 / 朝向不进这份比对（2026-11-17）。
            // 罗盘 10Hz 更新，把它们算进来会每 100ms 全量重建一次标记（几十个 remove + add），
            // 拖动与相机动画都会卡——蓝点由下面的 updateUser 单独增量更新。
            val static = inputs.copy(
                userPoint = null,
                userHeading = null,
                userAccuracyMeters = 0f,
            )
            if (drawn.value != static) {
                layers.rebuild(inputs)
                drawn.value = static
            }
            // 蓝点单独更新：罗盘 10Hz 跳动不该触发整层重建
            layers.updateUser(inputs.userPoint, userAccuracyMeters, userHeading, colors)
            },
        )

        // 中心浮标：钉在地图视图正中，随整幅平移一起走（与 osmdroid 版同一套几何）
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCenterPin(colors, density)
        }

        // 加载占位：盖在地图之上（SurfaceView 在窗口之下，不透明内容能盖住），底图出来才淡出。
        // 底色取**高德底图在校区尺度的主色**（#A4D5A9，从实机截图里采的），不是主题深色：
        // 深色底看着就是「黑屏加载中」，一秒钟后突然变亮，反而更刺眼；用底图自己的绿色打底，
        // 淡入时几乎看不出切换。深色主题下也一样——地图本身就是浅色的。
        if (placeholderVisible > 0.01f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(placeholderVisible)
                    .background(ComposeColor(AMAP_PLACEHOLDER_COLOR)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "地图加载中…",
                    style = MaterialTheme.typography.labelMedium,
                    color = ComposeColor(0x8A4A5A4C),
                )
            }
        }
    }

    DisposableEffect(mapView) {
        mapView.onResume()
        onDispose {
            mapView.onPause()
            mapView.onDestroy()
        }
    }

    LaunchedEffect(camera?.nonce, mapSize, mapLoaded) {
        val request = camera ?: return@LaunchedEffect
        if (mapSize.width == 0 || mapSize.height == 0) return@LaunchedEffect
        // **底图还没画出来时不要动相机**（2026-11-17 用户报「进页不跟到我的位置」）：
        // 高德在首帧渲染时会把相机重置回创建那一刻的位置，早发的 moveCamera 会被直接吞掉，
        // 而请求已经被消费——表现就是进页面停在校园中心、不再跟到人。等 onMapLoaded 之后
        // 再来（mapLoaded 在 key 里，翻转时会重跑这个 effect）。
        if (!mapLoaded) return@LaunchedEffect
        val target = LatLng(request.lat, request.lng)
        val zoom = request.zoom
            ?.coerceIn(MIN_ZOOM.toDouble(), MAX_ZOOM.toDouble())
            ?.toFloat()
        suppressor.aim(request.lat, request.lng)
        suppressor.quietFor(if (request.animated) MOVE_QUIET_MS else RESTORE_QUIET_MS)
        if (!request.animated) {
            aMap.moveCamera(
                if (zoom != null) {
                    CameraUpdateFactory.newLatLngZoom(target, zoom)
                } else {
                    CameraUpdateFactory.newLatLng(target)
                },
            )
        } else {
            // 逐帧缓动（2026-11-17 用户报「没有缓动、纯直线的死板移动」）：
            // 高德自带的 animateCamera 是**线性**插值，这里自己按帧推进，收尾减速。
            val duration = cameraAnimMs(aMap.cameraPosition?.target, request.lat, request.lng)
            val targetZoom = zoom ?: aMap.cameraPosition?.zoom ?: BikeNearby.DEFAULT_ZOOM.toFloat()
            interrupted.set(false)
            animateCameraEased(aMap, target, targetZoom, duration, interrupted)
        }
        appliedCallback(request.nonce)
    }
}

/**
 * 带缓动的相机动画（2026-11-17）：逐帧 `moveCamera`，位移与缩放都走
 * [DecelerateInterpolator]（起步快、收尾慢），而不是高德 `animateCamera` 的线性插值。
 *
 * 为什么不用 SDK 自带的：`animateCamera(update, duration, cb)` 只有线性插值这一个档，
 * 长距离移动看着像「匀速平移」，用户直接反馈「死板」。
 *
 * 三个细节：
 * - 用 `withFrameMillis` 对齐屏幕帧，不自己 sleep，掉帧时不会追帧；
 * - 每帧算的是**绝对**插值（`start + (end-start)*e`），不是累加，不会漂移；
 * - [interrupted] 被用户拖动置位时立刻停手，别和手指抢。
 */
private suspend fun animateCameraEased(
    aMap: AMap,
    target: LatLng,
    targetZoom: Float,
    durationMs: Long,
    interrupted: AtomicBoolean,
) {
    val start = aMap.cameraPosition
    if (start == null) {
        aMap.moveCamera(CameraUpdateFactory.newLatLngZoom(target, targetZoom))
        return
    }
    val startLat = start.target.latitude
    val startLng = start.target.longitude
    val startZoom = start.zoom
    val interpolator = DecelerateInterpolator(1.8f)
    val begin = withFrameMillis { it }
    while (!interrupted.get()) {
        val now = withFrameMillis { it }
        val t = ((now - begin).toFloat() / durationMs).coerceIn(0f, 1f)
        val eased = interpolator.getInterpolation(t)
        aMap.moveCamera(
            CameraUpdateFactory.newLatLngZoom(
                LatLng(
                    startLat + (target.latitude - startLat) * eased,
                    startLng + (target.longitude - startLng) * eased,
                ),
                startZoom + (targetZoom - startZoom) * eased,
            ),
        )
        if (t >= 1f) break
    }
    // 收尾落到精确目标：浮点累加会留几厘米误差，最后一帧直接给定值
    if (!interrupted.get()) {
        aMap.moveCamera(CameraUpdateFactory.newLatLngZoom(target, targetZoom))
    }
}

/**
 * 相机动画时长（毫秒）：近距离 420ms、远距离最多 720ms，按两点间的球面距离线性插值。
 *
 * 取这个区间是因为两头的观感都被否过一次：400ms 跨校区像「猛甩一下」，800ms 以上又
 * 明显「慢半拍」。上限压到 720ms，配合 `MOVE_QUIET_MS` 的 1200ms 静默窗口仍然盖得住。
 */
private fun cameraAnimMs(from: LatLng?, lat: Double, lng: Double): Long {
    if (from == null) return CAMERA_ANIM_MS
    val meters = BikeNearby.distanceMeters(from.latitude, from.longitude, lat, lng)
    val ratio = (meters / CAMERA_LONG_MOVE_METERS).coerceIn(0.0, 1.0)
    return (CAMERA_ANIM_MS + (CAMERA_MAX_ANIM_MS - CAMERA_ANIM_MS) * ratio).toLong()
}

/** 标记上挂的点击载荷。 */
private sealed interface MarkerPayload {
    data class Cluster(val key: String) : MarkerPayload
    data class Bike(val carNum: String) : MarkerPayload
    data object Ride : MarkerPayload
}

/** 地图加载占位的底色：高德底图在校区尺度的主色（实机截图采样）。 */
private const val AMAP_PLACEHOLDER_COLOR = 0xFFA4D5A9

/** 高德相机允许的级别：官方矢量底图到 20 级，超过没有更多细节。 */
private const val MAX_ZOOM = 20f
private const val MIN_ZOOM = 3f

/** 近距离相机动画时长（毫秒）：与 osmdroid 那边同一个量级。 */
private const val CAMERA_ANIM_MS = 420L

/** 远距离相机动画时长上限（毫秒）。 */
private const val CAMERA_MAX_ANIM_MS = 720L

/** 超过这个距离（米）就算「远距离」，动画时长线性加到 [CAMERA_MAX_ANIM_MS]。 */
private const val CAMERA_LONG_MOVE_METERS = 1200.0

/** 等底图首帧的上限：超过就认定 key / 服务不可用，整层回退栅格。 */
private const val SDK_LOAD_TIMEOUT_MS = 10_000L

private const val MOVE_QUIET_MS = 1_200L
private const val RESTORE_QUIET_MS = 300L

/**
 * 图层的增删（2026-11-17）。
 *
 * 重建策略是**整层清掉再按输入画一遍**：输入变化只发生在「查询结果回来 / 换筛选 / 展开
 * 停车点」这些低频时刻，几十个 Marker 的增删在 高德 SDK 里是一次性开销，比维护 diff 简单
 * 得多。蓝点与精度圈**不进这层**，它们随罗盘 10Hz 更新，单独走 [updateUser]。
 */
private class AmapLayers(
    private val aMap: AMap,
    private val bitmaps: AmapMarkerBitmaps,
) {

    private val dynamic = mutableListOf<Any>()
    private var userMarker: Marker? = null
    private var accuracyCircle: Circle? = null

    /** 上一次写进蓝点图层的输入（见 [updateUser] 的短路）。 */
    private var lastUserKey: String? = null

    fun rebuild(inputs: MapOverlayInputs) {
        dynamic.forEach { item ->
            when (item) {
                is Marker -> item.remove()
                is Polygon -> item.remove()
                is Circle -> item.remove()
            }
        }
        dynamic.clear()

        val colors = inputs.colors
        // 围栏压在最底层：它是背景信息，盖住任何一辆车都是本末倒置
        if (BikeNearby.CAMPUS_FENCE.size >= 3) {
            addPolygon(BikeNearby.CAMPUS_FENCE, colors.fenceFill, colors.fenceStroke, 2f, Z_FENCE)
        }
        // 图层压在围栏之上、车辆之下（与官方 zIndex：服务区 < 禁停 < 还车点 < 车 同序）
        inputs.zones.nogoZones.forEach { outline ->
            if (outline.size >= 3) {
                addPolygon(outline, colors.nogoFill, colors.nogoStroke, 1f, Z_NOGO)
            }
        }
        inputs.zones.parkSpots.forEach { spot ->
            if (spot.outline.size >= 3) {
                addPolygon(spot.outline, colors.spotFill, colors.spotStroke, 1.5f, Z_SPOT)
            }
            addMarker(
                lat = spot.lat,
                lng = spot.lng,
                icon = bitmaps.spotBadge(colors),
                zIndex = Z_SPOT_BADGE,
            )
        }
        // 车辆：聚合圈 / 逐辆图标
        if (inputs.byBike) {
            inputs.bikes.forEach { bike ->
                addMarker(
                    lat = bike.lat,
                    lng = bike.lng,
                    icon = bitmaps.bike(bike, colors),
                    zIndex = Z_BIKE,
                    payload = MarkerPayload.Bike(bike.carNum),
                )
                if (bike.carNum == inputs.selectedBikeCarNum) {
                    addMarker(
                        lat = bike.lat,
                        lng = bike.lng,
                        icon = bitmaps.bikeSelection(colors),
                        zIndex = Z_BIKE + 1,
                    )
                }
            }
        } else {
            inputs.clusters.forEach { cluster ->
                addMarker(
                    lat = cluster.lat,
                    lng = cluster.lng,
                    icon = bitmaps.cluster(cluster, cluster.key == inputs.selectedKey, colors),
                    zIndex = Z_BIKE,
                    payload = MarkerPayload.Cluster(cluster.key),
                )
            }
        }
        // 识别高亮：画在车标之上、我的车之下
        inputs.highlightPoint?.let { point ->
            addMarker(
                lat = point.lat,
                lng = point.lng,
                icon = bitmaps.highlight(inputs.highlightLabel, colors),
                zIndex = Z_HIGHLIGHT,
                anchorY = bitmaps.highlightAnchorY(inputs.highlightLabel),
            )
        }
        // 当前用车
        inputs.ridePoint?.let { point ->
            addMarker(
                lat = point.lat,
                lng = point.lng,
                icon = bitmaps.ride(colors),
                zIndex = Z_RIDE,
                payload = MarkerPayload.Ride,
            )
        }
    }

    /**
     * 蓝点 / 精度圈：只在位置、精度或朝向变化时动，不进整层重建。
     * 朝向用 `rotateAngle` 转整张位图——圆点与光晕旋转对称，肉眼只看到箭头在转。
     */
    fun updateUser(point: GcjPoint?, accuracyMeters: Float, heading: Float?, colors: BikeMarkerColors) {
        if (point == null) {
            if (userMarker != null || accuracyCircle != null) {
                userMarker?.remove()
                userMarker = null
                accuracyCircle?.remove()
                accuracyCircle = null
                lastUserKey = null
            }
            return
        }
        // 这层每次重组都会被调到（罗盘 10Hz）：值没变就什么都不做，别让高德每帧重排标记
        val key = "${point.lat},${point.lng},$accuracyMeters,${heading ?: -1f},${colors.userDot}"
        if (key == lastUserKey) return
        lastUserKey = key

        val latLng = LatLng(point.lat, point.lng)
        val marker = userMarker ?: aMap.addMarker(
            MarkerOptions()
                .position(latLng)
                .icon(BitmapDescriptorFactory.fromBitmap(bitmaps.user(colors)))
                .anchor(0.5f, 0.5f)
                .zIndex(Z_USER),
        ).also { userMarker = it }
        marker.position = latLng
        // 罗盘方位角 0 = 正北、顺时针；位图里的箭头画成朝上（正北），所以直接转同一角度。
        // **别写成负号**：那会让箭头指反（2026-11-17 复核 `drawHeadingArrow` 的 `canvas.rotate` 口径）
        marker.rotateAngle = heading ?: 0f

        // 精度圈：半径就是米，不必像 osmdroid 那样投影两个点折算
        if (accuracyMeters > 0f) {
            val circle = accuracyCircle ?: aMap.addCircle(
                CircleOptions()
                    .center(latLng)
                    .radius(accuracyMeters.toDouble())
                    .fillColor(withAlpha(colors.userHalo, 0.45f))
                    .strokeColor(withAlpha(colors.userHalo, 1.1f))
                    .strokeWidth(1f)
                    .zIndex(Z_ACCURACY),
            ).also { accuracyCircle = it }
            circle.center = latLng
            circle.radius = accuracyMeters.toDouble()
        } else {
            accuracyCircle?.remove()
            accuracyCircle = null
        }
    }

    private fun addMarker(
        lat: Double,
        lng: Double,
        icon: android.graphics.Bitmap,
        zIndex: Float,
        payload: MarkerPayload? = null,
        anchorY: Float = 0.5f,
    ) {
        val marker = aMap.addMarker(
            MarkerOptions()
                .position(LatLng(lat, lng))
                .icon(BitmapDescriptorFactory.fromBitmap(icon))
                .anchor(0.5f, anchorY)
                .zIndex(zIndex),
        )
        marker?.setObject(payload)
        marker?.let { dynamic.add(it) }
    }

    private fun addPolygon(
        points: List<GcjPoint>,
        fillColor: Int,
        strokeColor: Int,
        strokeWidthDp: Float,
        zIndex: Float,
    ) {
        val options = PolygonOptions()
            .fillColor(fillColor)
            .strokeColor(strokeColor)
            .strokeWidth(strokeWidthDp)
            .zIndex(zIndex)
        points.forEach { options.add(LatLng(it.lat, it.lng)) }
        aMap.addPolygon(options)?.let { dynamic.add(it) }
    }

    private fun withAlpha(color: Int, factor: Float): Int {
        val alpha = (Color.alpha(color) * factor).roundToInt().coerceIn(0, 255)
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
    }

    private companion object {
        const val Z_FENCE = 1f
        const val Z_NOGO = 2f
        const val Z_SPOT = 3f
        const val Z_SPOT_BADGE = 4f
        const val Z_BIKE = 5f
        const val Z_HIGHLIGHT = 6f
        const val Z_RIDE = 7f
        const val Z_ACCURACY = 8f
        const val Z_USER = 9f
    }
}

/** 视口半对角线（米）：拿高德的可见区域四个角现算，喂给采样半径的缩放。 */
private fun viewportHalfDiagonalMeters(aMap: AMap): Double {
    val bounds = runCatching { aMap.projection.visibleRegion.latLngBounds }.getOrNull()
        ?: return 0.0
    val northEast = bounds.northeast ?: return 0.0
    val southWest = bounds.southwest ?: return 0.0
    val centerLat = (northEast.latitude + southWest.latitude) / 2
    val centerLng = (northEast.longitude + southWest.longitude) / 2
    return BikeNearby.distanceMeters(
        centerLat,
        centerLng,
        northEast.latitude,
        northEast.longitude,
    )
}

/**
 * 手指状态跟踪（高德版）：只读不拦。
 *
 * 高德没有 `onCameraChangeFinish` 之外的「用户是不是还在拖」信号，而单指拖动要用来退出
 * 自动跟随，所以仍然自己看 touch 事件；判据与 osmdroid 版一致（超过系统 slop 才算拖动，
 * 双指缩放不算）。
 */
private class MapTouchTracker(
    private val touchSlop: Float,
    private val onPan: () -> Unit,
) {
    private var downX = 0f
    private var downY = 0f
    private var panned = false

    fun onTouch(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                panned = false
                downX = event.x
                downY = event.y
            }

            MotionEvent.ACTION_POINTER_DOWN -> panned = true

            MotionEvent.ACTION_MOVE -> {
                if (!panned && event.pointerCount == 1) {
                    val dx = event.x - downX
                    val dy = event.y - downY
                    if (dx * dx + dy * dy > touchSlop * touchSlop) {
                        panned = true
                        onPan()
                    }
                }
            }
        }
    }
}

/** 中心浮标：黑色圆头 + 薄荷绿针心 + 一根细杆，尖端落在视图正中。 */
private fun DrawScope.drawCenterPin(colors: BikeMarkerColors, density: Float) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val headRadius = 9f * density
    val stemLength = 17f * density
    val headY = cy - stemLength - headRadius
    drawLine(
        color = androidx.compose.ui.graphics.Color(colors.centerMark),
        start = Offset(cx, cy),
        end = Offset(cx, headY + headRadius),
        strokeWidth = 0.8f * density,
    )
    drawCircle(
        color = androidx.compose.ui.graphics.Color(colors.centerMark),
        radius = headRadius,
        center = Offset(cx, headY),
    )
    drawCircle(
        color = androidx.compose.ui.graphics.Color(colors.centerGlyph),
        radius = 3f * density,
        center = Offset(cx, headY),
    )
}
