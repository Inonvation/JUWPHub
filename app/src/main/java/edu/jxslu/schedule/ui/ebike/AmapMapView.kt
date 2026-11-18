package edu.jxslu.schedule.ui.ebike

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Point
import android.util.Log
import android.view.MotionEvent
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.ViewConfiguration
import androidx.compose.animation.core.animateFloatAsState
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
import com.amap.api.maps.model.BitmapDescriptor
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
import edu.jxslu.schedule.ui.theme.Motion
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.hypot
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
    /** 校区围栏画不画（见 [MapOverlayInputs.showCampusFence]，2026-11-18）。 */
    showCampusFence: Boolean = true,
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
    /**
     * 高德版权标识的底边距（px）。0 = 用 SDK 默认（钉在地图左下角）。
     *
     * 为什么需要它：底部面板会盖住地图下沿，而 logo 默认就在左下角——服务协议 4.10.7
     * 不允许遮挡版权声明。页面按「面板高度 + 底部常驻块」算好这个值传进来，把 logo 顶到
     * 面板上沿之上（2026-11-18）。
     */
    logoBottomMarginPx: Int = 0,
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
    // 每次「相机停稳」+1。P 图标的避让要看当前缩放（见 AmapLayers.refreshSpotBadges），
    // 而缩放变化不一定带来新数据，所以得由停手事件单独驱动一次重算。
    var settleTick by remember { mutableStateOf(0) }

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
            // 底图上每帧都要重算、而这张图用不到的图层先关掉（见 trimBasemapLayers）
            map.trimBasemapLayers()
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
                    // 停手就重算一次 P 图标的避让（缩放变了，重叠关系也变了）。
                    // 放在吞掉回调的判断**之前**：程序性移动同样会让缩放变化。
                    settleTick += 1
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
    // logo 位置随面板高度变，所以不能只在建图时设一次
    LaunchedEffect(aMap, logoBottomMarginPx) {
        if (logoBottomMarginPx > 0) {
            runCatching { aMap.uiSettings.setLogoBottomMargin(logoBottomMarginPx) }
        }
    }
    val touchTracker = remember {
        MapTouchTracker(ViewConfiguration.get(context).scaledTouchSlop.toFloat()) {
            // 用户接管地图：先停掉程序性相机动画，别让缓动继续和手指抢（与 osmdroid 版同口径）
            interrupted.set(true)
            aMap.stopAnimation()
            panCallback()
        }
    }
    // 最近一次触摸的屏幕坐标。高德的 `OnMarkerClickListener` 只给 Marker、不给手指位置，
    // 而「P 被点中时该不该转给它旁边的聚合圈」必须按**真实手指位置**判（2026-11-18）：
    // 拿 P 自己的坐标当手指位置会偏出半个图标，把旁边的数字也一起算进去。
    val lastTouch = remember { FloatArray(2) }
    DisposableEffect(mapView) {
        mapView.setOnTouchListener { _, event ->
            touchTracker.onTouch(event)
            if (event.actionMasked == MotionEvent.ACTION_DOWN ||
                event.actionMasked == MotionEvent.ACTION_UP
            ) {
                lastTouch[0] = event.x
                lastTouch[1] = event.y
            }
            false
        }
        onDispose { mapView.setOnTouchListener(null) }
    }
    val layers = remember(aMap, bitmaps, density) { AmapLayers(aMap, bitmaps, density) }
    // 相机停稳后重算 P 图标的避让（见 AmapLayers.refreshSpotBadges）
    LaunchedEffect(layers, settleTick) {
        if (settleTick > 0) layers.refreshSpotBadges()
    }

    // 底图真的画出来了才认为 SDK 可用（key 校验在服务端做，initialize 的返回值靠不住）
    var mapLoaded by remember { mutableStateOf(false) }
    val unavailableCallback by rememberUpdatedState(onSdkUnavailable)
    DisposableEffect(aMap) {
        aMap.setOnMapLoadedListener {
            // 3D 楼块要在底图就绪后再关（官方示例就写在 onMapLoaded 里）：早调会被首帧
            // 渲染重置回默认。校园尺度下这一层是纯装饰，每帧都要重新画一遍楼体与贴图。
            runCatching { aMap.showBuildings(false) }
                .onFailure { Log.w(TAG_MAP, "关闭 3D 楼块失败", it) }
            mapLoaded = true
        }
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
        animationSpec = Motion.state(),
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
        if (BuildConfig.DEBUG) Log.d(TAG_MAP, "首帧落到用户位置: $lat,$lng")
    }

    // 点标记：按 Marker 上挂的 payload 分派，不依赖任何绘制顺序
    DisposableEffect(aMap, layers) {
        aMap.setOnMarkerClickListener { marker ->
            val payload = marker.`object` as? MarkerPayload
            if (payload != null) {
                dispatchTap(payload, clusterTapCallback, bikeTapCallback, rideTapCallback)
                true
            } else {
                // 没有载荷的标记（还车点「P」/ 选中圈 / 高亮 chip）**不接点击**，与 osmdroid
                // 那条路径同口径——`BikeMarkerOverlay.onSingleTapConfirmed` 的注释写得很清楚：
                // 「P」常和聚合圈压在一起，接点击只会挡着选车（用户 2026-09-29 报过，2026-11-18
                // 又提了一次）。高德没有「这个 Marker 不参与命中」的开关，所以自己按**手指真实
                // 位置**重算一遍命中：只有图标真的盖住手指的那个可点标记才算命中。
                //
                // **别用宽松半径**（第一版用过 24dp）：P 与聚合圈通常只隔十几 dp，宽半径会把
                // "点了个 P"变成"打开了旁边的数字"，用户看到的就是「停车点还是能点」。
                val target = layers.clickTargetAt(lastTouch[0], lastTouch[1])
                if (target != null) {
                    dispatchTap(target, clusterTapCallback, bikeTapCallback, rideTapCallback)
                }
                // 一律消费：P 上的一下什么都不该发生，也别让 SDK 再走它自己的默认动作
                true
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
                showCampusFence = showCampusFence,
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
            // 逐帧缓动（2026-11-17 起；2026-11-18 对齐栅格路径的观感）。
            // 高德自带的 animateCamera 只有线性插值，这里自己按帧推进。
            // 时长与曲线**照抄 osmdroid**（见 [cameraAnimMs] 的注释）：用户要求"视角移动
            // 和栅格地图体验一致"，两边参数一样才不会一快一慢、一个匀速一个缓。
            val targetZoom = zoom ?: aMap.cameraPosition?.zoom ?: BikeNearby.DEFAULT_ZOOM.toFloat()
            interrupted.set(false)
            animateCameraEased(aMap, target, targetZoom, cameraAnimMs(zoom != null), interrupted)
        }
        appliedCallback(request.nonce)
    }
}

/**
 * 相机动画时长：与 osmdroid 那条路径逐项对齐。
 *
 * osmdroid 6.1.18 的 `MapController.animateTo` 走 `ValueAnimator`，时长只有两档
 * （反编译 `MapController` / `DefaultConfigurationProvider` 确认）：
 * - 带缩放（`animateTo(target, zoom, 400L)`）→ **400 ms**；
 * - 只平移（`animateTo(target)`，时长为 null）→ `animationSpeedDefault` = **1000 ms**。
 *
 * 曲线也是 `ValueAnimator` 的默认值 `AccelerateDecelerateInterpolator`（两端慢、中间快），
 * 见 [animateCameraEased]。**别改成按距离插值**：那会让"定位"与"回校区"两条路各走各的手感，
 * 正是这次要消掉的不一致。
 */
private fun cameraAnimMs(withZoom: Boolean): Long =
    if (withZoom) CAMERA_ANIM_ZOOM_MS else CAMERA_ANIM_PAN_MS

/**
 * 带缓动的相机动画：逐帧 `moveCamera`，位移与缩放共用同一个缓动进度。
 *
 * 为什么不用 SDK 自带的：`animateCamera(update, duration, cb)` 只有线性插值这一个档，
 * 长距离移动看着像「匀速平移」，用户直接反馈「死板」。
 *
 * 曲线用 [AccelerateDecelerateInterpolator]（= osmdroid 那边 `ValueAnimator` 的默认值），
 * 起步与收尾都慢，与栅格路径的观感一致。
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
    val interpolator = AccelerateDecelerateInterpolator()
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

/** 标记上挂的点击载荷。 */
private sealed interface MarkerPayload {
    data class Cluster(val key: String) : MarkerPayload
    data class Bike(val carNum: String) : MarkerPayload
    data object Ride : MarkerPayload
}

/** 把一次点击按载荷分派给页面（点击监听与「P 转给邻近目标」两条路共用）。 */
private fun dispatchTap(
    payload: MarkerPayload,
    onCluster: (String) -> Unit,
    onBike: (String) -> Unit,
    onRide: () -> Unit,
) {
    when (payload) {
        is MarkerPayload.Cluster -> onCluster(payload.key)
        is MarkerPayload.Bike -> onBike(payload.carNum)
        MarkerPayload.Ride -> onRide()
    }
}

/**
 * 关掉底图上**每帧都要重算、而这张图用不到**的图层（2026-11-18 用户报「拖动卡顿」）。
 *
 * 高德 3D 底图是矢量渲染：拖动时每帧都要重新走一遍几何装配与标注排版，图层越多越贵。
 * 这里关的都是纯装饰层，关掉不改变「哪条路、哪个地名、哪辆车」这些用户真要读的信息
 * （楼栋名还在，只是不再画 3D 楼体，见下面那条）：
 * - 室内图：3D SDK 默认就关，显式写一次防上游改默认值；
 * - 路面方向箭头、施工路段：行车导航用的，我们只有校园内步行尺度；
 * - 导航类标注：同上；
 * - POI 触摸命中：我们只点自己的 Marker，不需要 SDK 再维护一份 POI 命中索引。
 *
 * **3D 楼块不在这里关**：官方要求在 `onMapLoaded` 之后调，见 [AmapMapView] 里那个监听器。
 * 每项单独 `runCatching`：某个开关在别的 SDK 版本上没了，不该把其余几项一起带走。
 */
private fun AMap.trimBasemapLayers() {
    runCatching { showIndoorMap(false) }
        .onFailure { Log.w(TAG_MAP, "关闭室内图失败", it) }
    runCatching { setRoadArrowEnable(false) }
        .onFailure { Log.w(TAG_MAP, "关闭路面箭头失败", it) }
    runCatching { setConstructingRoadEnable(false) }
        .onFailure { Log.w(TAG_MAP, "关闭施工路段失败", it) }
    runCatching { setNaviLabelEnable(false, 0, 0) }
        .onFailure { Log.w(TAG_MAP, "关闭导航标注失败", it) }
    runCatching { setTouchPoiEnable(false) }
        .onFailure { Log.w(TAG_MAP, "关闭 POI 触摸命中失败", it) }
    runCatching { setRenderFps(AMAP_RENDER_FPS) }
        .onFailure { Log.w(TAG_MAP, "设置渲染帧率失败", it) }
}

/** 地图加载占位的底色：高德底图在校区尺度的主色（实机截图采样）。 */
private const val AMAP_PLACEHOLDER_COLOR = 0xFFA4D5A9

/** 日志 tag：与 [AmapMapView] 里其余几处保持一致。 */
private const val TAG_MAP = "BikeMapAmap"

/**
 * 底图渲染循环的目标帧率。
 *
 * **60 就是 SDK 默认值，写在这里只是把 A/B 开关摆到明面上**：设备拖动时如果稳定在
 * 40 帧上下、帧时间忽长忽短，把这里改成 45 或 30 往往观感更稳（稳定的 30 比抖动的 45
 * 看着顺），代价是整体更"慢"。没有真机对比之前不要凭感觉改。
 */
private const val AMAP_RENDER_FPS = 60

/** 高德相机允许的级别：官方矢量底图到 20 级，超过没有更多细节。 */
private const val MAX_ZOOM = 20f
private const val MIN_ZOOM = 3f

/** 带缩放的相机动画时长（毫秒）：osmdroid 那边传的就是 `400L`，照抄。 */
private const val CAMERA_ANIM_ZOOM_MS = 400L

/** 只平移的相机动画时长（毫秒）：osmdroid 的 `animationSpeedDefault`，照抄。 */
private const val CAMERA_ANIM_PAN_MS = 1_000L

/** 等底图首帧的上限：超过就认定 key / 服务不可用，整层回退栅格。 */
private const val SDK_LOAD_TIMEOUT_MS = 10_000L

private const val MOVE_QUIET_MS = 1_200L
private const val RESTORE_QUIET_MS = 300L

/**
 * 图层的增删（2026-11-17；2026-11-18 由「全清 + 全画」改成按 key 复用）。
 *
 * 为什么不再整层重建：数据每次刷新都要 remove 掉几十上百个 Marker 再重新 add
 * （「按车辆」模式最多 150 个），高德那边每个都要新建对象、生成纹理，刷新时肉眼能看出顿一下。
 * 现在同一辆车 / 同一个停车点始终复用同一个 Marker，只改 position / icon / zIndex；
 * 只有「这批数据里不再出现的 key」才 remove。
 *
 * 多边形（围栏 / 禁停区 / 还车点范围）按 [PolygonsKey] 指纹整段跳过：这三层几乎不变，
 * 而聚合圈随每次拖动停手刷新，原来每刷一次都要 remove + add 一遍全部多边形（每个都要重新
 * 上传顶点）。要改这几层时记得把新输入加进指纹，否则画面不会动。
 *
 * 蓝点与精度圈**不进这层**，它们随罗盘 10Hz 更新，单独走 [updateUser]。
 */
private class AmapLayers(
    private val aMap: AMap,
    private val bitmaps: AmapMarkerBitmaps,
    private val density: Float,
) {

    private val polygons = mutableListOf<Polygon>()

    /** key → Marker。key 带类型前缀，见 [rebuild] 里的调用点。 */
    private val markers = LinkedHashMap<String, Marker>()

    /**
     * key → 上一次真正设上去的位图。**图标没换就别再 `setIcon`**（2026-11-18）：
     * 每次刷新都重设一遍，SDK 要重新绑一次纹理，而绝大多数标记的图标根本没变。
     */
    private val icons = HashMap<String, Bitmap>()

    /**
     * 上一次画上去的多边形输入（围栏 / 禁停区 / 还车点范围 + 六个颜色）。
     *
     * 为什么记它：聚合圈随每次拖动停手刷新，而这三层几乎不变。原来每次刷新都
     * `remove` + `add` 一遍全部多边形（每个都要重新上传顶点），是白花的时间。
     */
    private var polygonsKey: PolygonsKey? = null

    /**
     * 位图 → descriptor。`BitmapDescriptorFactory.fromBitmap` 每次都会新建一个对象，
     * 原来每个 Marker 每次重建都调一次；位图本身是缓存的，descriptor 也该跟着缓存。
     */
    private val descriptors = HashMap<Bitmap, BitmapDescriptor>()

    private var userMarker: Marker? = null
    private var accuracyCircle: Circle? = null

    /** 上一次写进蓝点图层的输入（见 [updateUser] 的短路）。 */
    private var lastUserKey: String? = null

    fun rebuild(inputs: MapOverlayInputs) {
        val colors = inputs.colors
        drawPolygons(inputs, colors)

        val live = HashSet<String>()

        /**
         * 按 key 落一个 Marker：已存在就改，不存在才建。
         *
         * [payload] 决定点击时走哪条路（见 `MarkerPayload`），变了才 `setObject`。
         * **每一项都先比再写**：值没变就不碰，SDK 才不会为一次刷新重排整个标记表。
         */
        fun marker(
            key: String,
            lat: Double,
            lng: Double,
            icon: Bitmap,
            zIndex: Float,
            payload: MarkerPayload? = null,
            anchorY: Float = 0.5f,
        ) {
            live += key
            val position = LatLng(lat, lng)
            val existing = markers[key]
            if (existing == null) {
                val created = aMap.addMarker(
                    MarkerOptions()
                        .position(position)
                        .icon(descriptorFor(icon))
                        .anchor(0.5f, anchorY)
                        .zIndex(zIndex),
                )
                if (created != null) {
                    // 再显式设一次 zIndex：`MarkerOptions.zIndex` 在某些版本上不生效，
                    // 补一次没有成本（2026-11-18 用户报「P 图标遮住车辆数量图标」时的兜底）
                    created.zIndex = zIndex
                    created.setObject(payload)
                    markers[key] = created
                    icons[key] = icon
                }
            } else {
                if (existing.position != position) existing.position = position
                if (icons[key] !== icon) {
                    existing.setIcon(descriptorFor(icon))
                    icons[key] = icon
                }
                if (existing.zIndex != zIndex) existing.zIndex = zIndex
                if (existing.`object` != payload) existing.setObject(payload)
            }
        }

        inputs.zones.parkSpots.forEachIndexed { index, spot ->
            // 还车点没有稳定 id，用下标做 key：同一次查询里顺序固定，够用
            marker(
                key = "spot:$index",
                lat = spot.lat,
                lng = spot.lng,
                icon = bitmaps.spotBadge(colors),
                zIndex = Z_SPOT_BADGE,
            )
        }
        // 车辆：聚合圈 / 逐辆图标
        if (inputs.byBike) {
            inputs.bikes.forEach { bike ->
                marker(
                    key = "bike:" + bike.carNum,
                    lat = bike.lat,
                    lng = bike.lng,
                    icon = bitmaps.bike(bike, colors),
                    zIndex = Z_BIKE,
                    payload = MarkerPayload.Bike(bike.carNum),
                )
                if (bike.carNum == inputs.selectedBikeCarNum) {
                    marker(
                        key = "sel:" + bike.carNum,
                        lat = bike.lat,
                        lng = bike.lng,
                        icon = bitmaps.bikeSelection(colors),
                        zIndex = Z_BIKE + 1,
                    )
                }
            }
        } else {
            inputs.clusters.forEach { cluster ->
                marker(
                    key = "cluster:" + cluster.key,
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
            marker(
                key = "hl",
                lat = point.lat,
                lng = point.lng,
                icon = bitmaps.highlight(inputs.highlightLabel, colors),
                zIndex = Z_HIGHLIGHT,
                anchorY = bitmaps.highlightAnchorY(inputs.highlightLabel),
            )
        }
        // 当前用车
        inputs.ridePoint?.let { point ->
            marker(
                key = "ride",
                lat = point.lat,
                lng = point.lng,
                icon = bitmaps.ride(colors),
                zIndex = Z_RIDE,
                payload = MarkerPayload.Ride,
            )
        }

        // 这批数据里不再出现的，才真的删掉
        markers.keys.filter { it !in live }.forEach { stale ->
            markers.remove(stale)?.remove()
            icons.remove(stale)
        }
        refreshSpotBadges()
    }

    /**
     * 围栏 / 禁停区 / 还车点范围三层多边形。**输入没变就整段跳过**（见 [polygonsKey]）。
     *
     * 顺序（也是 zIndex）：围栏在最底层（背景信息，盖住任何一辆车都是本末倒置），
     * 禁停区其次，还车点范围在车辆之下——与官方 zIndex 同序（服务区 < 禁停 < 还车点 < 车）。
     */
    private fun drawPolygons(inputs: MapOverlayInputs, colors: BikeMarkerColors) {
        val key = PolygonsKey(
            fence = inputs.showCampusFence && BikeNearby.CAMPUS_FENCE.size >= 3,
            nogo = inputs.zones.nogoZones,
            spots = inputs.zones.parkSpots.map { it.outline },
            fills = listOf(
                colors.fenceFill,
                colors.fenceStroke,
                colors.nogoFill,
                colors.nogoStroke,
                colors.spotFill,
                colors.spotStroke,
            ),
        )
        if (key == polygonsKey) return
        polygons.forEach { it.remove() }
        polygons.clear()

        // 「只看本校」关掉时不画（2026-11-18 用户要求：连校区描边一起隐藏）
        if (key.fence) {
            addPolygon(BikeNearby.CAMPUS_FENCE, colors.fenceFill, colors.fenceStroke, 2f, Z_FENCE)
        }
        inputs.zones.nogoZones.forEach { outline ->
            if (outline.size >= 3) {
                addPolygon(outline, colors.nogoFill, colors.nogoStroke, 1f, Z_NOGO)
            }
        }
        inputs.zones.parkSpots.forEach { spot ->
            if (spot.outline.size >= 3) {
                addPolygon(spot.outline, colors.spotFill, colors.spotStroke, 1.5f, Z_SPOT)
            }
        }
        polygonsKey = key
    }

    /**
     * 「P」还车点图标避让（2026-11-18 用户报「点击后 P 图标遮住车辆数量图标」）。
     *
     * 数字才是用户要找的东西，P 只是背景提示：P 与聚合圈 / 车标挤在一起时把 P 收起来。
     * 判据是**屏幕距离**（跟着缩放走），不是经纬度——同一对标记在 z16 和 z19 下的间距差十倍。
     *
     * 由 [rebuild] 与「相机停稳」两处驱动：数据没变但缩放变了，重叠关系也会变。
     * 顺带把被收起的 P 从命中测试里一起摘掉（`isVisible=false` 的 Marker 不会被点到）。
     */
    fun refreshSpotBadges() {
        if (markers.isEmpty()) return
        val projection = runCatching { aMap.projection }.getOrNull() ?: return
        val suppressPx = SPOT_BADGE_SUPPRESS_DP * density
        val occupied = ArrayList<Point>(markers.size)
        markers.forEach { (key, marker) ->
            if (key.startsWith(SPOT_KEY_PREFIX)) return@forEach
            // 只有「可点」的标记才值得让位：选中圈 / 高亮 chip 也是装饰
            if (marker.`object` !is MarkerPayload) return@forEach
            val point = runCatching { projection.toScreenLocation(marker.position) }.getOrNull()
                ?: return@forEach
            occupied += point
        }
        markers.forEach { (key, marker) ->
            if (!key.startsWith(SPOT_KEY_PREFIX)) return@forEach
            val point = runCatching { projection.toScreenLocation(marker.position) }.getOrNull()
                ?: return@forEach
            val covered = occupied.any {
                hypot((it.x - point.x).toFloat(), (it.y - point.y).toFloat()) <= suppressPx
            }
            if (marker.isVisible == covered) marker.isVisible = !covered
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
                .icon(descriptorFor(bitmaps.user(colors)))
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

    private fun descriptorFor(bitmap: Bitmap): BitmapDescriptor =
        descriptors.getOrPut(bitmap) { BitmapDescriptorFactory.fromBitmap(bitmap) }

    /**
     * 按**手指屏幕坐标**判命中了哪个「可点」标记（挂了 [MarkerPayload] 的那些：聚合圈 / 车 /
     * 我的车），只认**图标自己盖住手指**的情况。
     *
     * 用途见 `AmapMapView` 里的点击监听：高德**没有**「这个 Marker 不参与命中」的开关，而
     * 「P」还车点必须不接点击。P 被点中时用这个函数重算一遍——命中就转发（选中圈挡住自己
     * 车标那种情况靠它），没命中就什么都不做。
     *
     * 半径按各类图标自己的半径给（[hitRadiusDp]），**不要放大**：P 与聚合圈通常只隔十几 dp，
     * 半径一大，点 P 就会打开旁边的数字。
     */
    fun clickTargetAt(screenX: Float, screenY: Float): MarkerPayload? {
        val projection = runCatching { aMap.projection }.getOrNull() ?: return null
        var best: MarkerPayload? = null
        var bestDistance = Float.MAX_VALUE
        markers.values.forEach { marker ->
            val payload = marker.`object` as? MarkerPayload ?: return@forEach
            val point = runCatching { projection.toScreenLocation(marker.position) }.getOrNull()
                ?: return@forEach
            val distance = hypot((point.x - screenX), (point.y - screenY))
            if (distance <= hitRadiusPx(payload) && distance < bestDistance) {
                bestDistance = distance
                best = payload
            }
        }
        return best
    }

    /** 各类可点标记的命中半径（px）：取图标位图的一半，与 [AmapMarkerBitmaps] 的尺寸对齐。 */
    private fun hitRadiusPx(payload: MarkerPayload): Float = when (payload) {
        // 聚合圈：12dp 圆 + 2dp 描边
        is MarkerPayload.Cluster -> 15f * density
        // 车标：24dp 图标
        is MarkerPayload.Bike -> 12f * density
        // 当前用车：42dp 图标
        MarkerPayload.Ride -> 21f * density
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
        aMap.addPolygon(options)?.let { polygons.add(it) }
    }

    private fun withAlpha(color: Int, factor: Float): Int {
        val alpha = (Color.alpha(color) * factor).roundToInt().coerceIn(0, 255)
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
    }

    /** 多边形那一层的输入指纹（见 [polygonsKey]）：任一项变了才重画。 */
    private data class PolygonsKey(
        val fence: Boolean,
        val nogo: List<List<GcjPoint>>,
        val spots: List<List<GcjPoint>>,
        val fills: List<Int>,
    )

    private companion object {
        /** 还车点图标的 key 前缀（见 [rebuild]）。 */
        const val SPOT_KEY_PREFIX = "spot:"

        /**
         * P 图标给数字让位的屏幕距离（dp）：聚合圈半径 15dp + P 半宽 8.5dp 再留一点余量。
         * **别放大**：放大到几十 dp 会让整片还车点的 P 都不见了。
         */
        const val SPOT_BADGE_SUPPRESS_DP = 24f

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
