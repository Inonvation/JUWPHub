package edu.jxslu.schedule.ui.ebike

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import edu.jxslu.schedule.domain.BikeCluster
import edu.jxslu.schedule.domain.BikeMapViewport
import edu.jxslu.schedule.domain.BikeNearby
import edu.jxslu.schedule.domain.BikeStatus
import edu.jxslu.schedule.domain.GcjPoint
import edu.jxslu.schedule.domain.NearbyBike
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * 纯 Compose 的单车地图（2026-11-26，填 iOS 上那块「没有车标的地图」）。
 *
 * ## 为什么是画布而不是原生地图
 *
 * iOS 那侧本仓 Kotlin 2.1.21 的 MapKit 绑定**放不了标注也放不了覆盖物**
 * （`MKAnnotationProtocol.coordinate` 是 final、`MKPointAnnotation.coordinate` 是 `val`、
 * `addOverlay(s)` 全 Unresolved，见 `BikeMapView.ios.kt` 的对照表）。没有车标的地图不如不摆，
 * 所以改由 Compose 自己画：经纬度经 [BikeMapProjection] 落到画布坐标，车标 / 围栏 /
 * 还车点 / 蓝点全部是 `DrawScope` 的图形。**不碰 MapKit**，一个字符号都不引。
 *
 * 代价写在明处：**没有原生地图的那套交互**（楼体拉伸、路名随缩放重排、导航箭头）。
 * 画布上是底图瓦片（有 [tiles] 时）+ 业务图层——能看出「哪栋楼、哪片有车、离我多远」，
 * 车辆列表、开锁、还车、结算全部照常，地图只是找车的入口。
 *
 * ## 与 Android 那份的关系
 *
 * 同一份契约（[BikeMapInput] / [BikeMapSlot]，见 `BikeMapContract.kt`），**不同实现**：
 * Android 是高德 / osmdroid 原生视图，这里是共享层的画布（不需要 Context，也就能放 commonMain）。
 * 造型按 Android `BikeMarkerOverlay` 的尺寸与配色（簇 12/15dp、蓝点 5dp + 11dp 光晕、
 * 中心浮标 9dp 针头 + 17dp 针杆、`BikeMarkerColors` 上色），坐标口径同一份
 * [BikeMapProjection]——所以 `onViewportMeasured` 的半对角线在两端是同一个量纲。
 *
 * 三处 iOS 契约允许的降级（`BikeMapContract.kt` 第四节）：版权标识位置、罗盘朝向箭头、
 * 底图降级回调。另外一处是**画布自己带来的**：**没有相机动画**——[CameraRequest.animated]
 * 收下但不做缓动，镜头直接落位。画布的「动画」只能靠 `Animatable` 逐帧改视野，而拖动与
 * 动画同时改同一个 `viewport` 会互相打架；先做对位置，再谈手感。
 *
 * ## 底图瓦片：为什么画在画布里，而不是垫一个原生地图视图
 *
 * 用户装机第一句是「地图不显示」——只有网格与车标，看不出那是哪栋楼。补底图有两条路：
 * 垫一个 `MKMapView`（原生瓦片）或把瓦片直接画进这块画布。选后者，因为**几何只有一份**：
 * 车标、围栏、还车点、手柄全部走 [BikeMapProjection]，瓦片用同一个 `toScreen` 定位，
 * 两者错位这种事在结构上不会发生；垫原生地图则要面对「MapKit 的 region 与我们自己的
 * 投影怎么对齐」——那件事在本机（Windows）验不了，而错位比没有底图更糟。
 *
 * 代价写在明处：
 *
 * 1. **瓦片是 256 px 的栅格，而画布用设备像素投影**（与 Android 同尺度）。1:1 贴上去，
 *    3x 屏上 11px 的楼名只有约 3.7pt，读不出来；所以 [BikeMapTiles.tileZoom] 比相机低一级，
 *    按 2 倍放大贴（注记约 7pt）。高德另有 `scl=2` 的 512 px 瓦片，实测**不带任何注记**
 *    （纯路网），所以宁可放大也不要「没有楼名」。
 * 2. **瓦片到货前先画网格**：一帧空白会让人以为地图坏了；有一块瓦片就不画网格。
 *
 * ## 手势帧不进组合期（2026-11-26 真机反馈：拖动 / 捏合不跟手）
 *
 * 根因是同一个 `viewport` 被**组合期**读了三次：`remember(...)` 的键、`LaunchedEffect(gestureTick)`
 * （每帧 +1）、以及 `LaunchedEffect(canvasSize)` 的键。组合期读 `mutableStateOf` = 订阅，
 * 于是拖动时每帧都重组一次整段 + 重算瓦片清单 + 重启两个 effect。
 *
 * 现在这条链路上**组合期一次都不读视野**：
 *
 * 1. `viewport` 存的是**State 对象本身**（不是 `by` 委托——委托的 `get` 就是组合期读），
 *    手势与镜头请求只写 `viewport.value`，`Canvas` 的 draw lambda 只读 `.value`。
 *    绘制期读状态只失效绘制，不重组，于是拖动 = 一帧一次 draw。
 * 2. 瓦片清单由 `LaunchedEffect(tiles) { snapshotFlow { … } }` 在协程里算（`snapshotFlow` 的读取
 *    登记的是快照依赖，不是组合依赖），`distinctUntilChanged` 挡掉「同视野重复算」，
 *    `debounce` 把拖动中的连续变化收成一次请求。
 * 3. 「停手」不再靠组合期计数：`settleSignal`（`MutableSharedFlow`）由手势 / 镜头 `tryEmit`，
 *    收集侧 `debounce(SETTLE_DELAY_MS)` 后回报——语义与旧的「effect 被计数重启、静默 450ms 才跑完」
 *    一致，但一次重组都不需要。
 *
 * 回调时机刻意保持不变：`onCameraApplied` 仍在镜头写入后**立即**回报；`onViewportMeasured`
 * 仍在量到尺寸 / 停手时各报一次；`onCenterSettled` 读的仍是**收尾那一刻**的视野。
 */
@OptIn(FlowPreview::class) // debounce：瓦片请求与停手判定都用它（FlowPreview API）
@Composable
fun BikeMapCanvas(
    input: BikeMapInput,
    modifier: Modifier = Modifier,
    /**
     * 底图瓦片来源。**`null` = 本平台不贴底图**（Android 的底图在高德 / osmdroid 视图里，
     * 画布只负责车标那一层）；iOS 传 `IosBikeMapTiles`。
     */
    tiles: BikeMapTileSource? = null,
) {
    val density = LocalDensity.current.density
    val measurer = rememberTextMeasurer()
    val background = MaterialTheme.colorScheme.surfaceContainerLow
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)

    // 视野只在建图时读一次 initialCenter / initialZoom（契约：它们「只在建图时读一次」）。
    // 存着的级别 ≤ 0 按「没存过」处理：钳到下限会得到一张整个中国的地图，车在几百公里外。
    //
    // **存 State 对象，不用 `by` 委托**：委托的 `get` 是组合期读，拖动时每帧都会让这段组合
    // 失效一次（见类头 KDoc）。这里的约定是手势帧只写，绘制与 effect 体内才读 `.value`。
    val viewport = remember {
        mutableStateOf(
            BikeMapViewport(
                lat = input.initialCenter?.lat ?: BikeNearby.DEFAULT_CENTER_LAT,
                lng = input.initialCenter?.lng ?: BikeNearby.DEFAULT_CENTER_LNG,
                zoom = BikeMapProjection.clampZoom(
                    input.initialZoom?.takeIf { it > 0.0 } ?: BikeNearby.DEFAULT_ZOOM,
                ),
            ),
        )
    }
    /** 画布尺寸（设备像素）：只由 `onSizeChanged` 写，只在 effect / 手势回调里读。 */
    val canvasSize = remember { mutableStateOf(IntSize.Zero) }
    /**
     * 「停手」信号：用户拖 / 捏、镜头落位各 `tryEmit` 一次。
     *
     * 不用组合期计数（旧的 `gestureTick++`）：那意味着每帧重组一次、每帧重启一个 `LaunchedEffect`。
     * `extraBufferCapacity = 1` + 丢最旧，连发时只留一次——收集侧只关心「还在不在动」。
     * 无 replay：建图那一下不该凭空报一次「已停手」（旧实现在我 `gestureTick == 0` 时跳过）。
     */
    val settleSignal = remember {
        MutableSharedFlow<Unit>(
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
    }
    // 回调每次重组都可能换新实例，pointerInput(Unit) 里的闭包必须读最新的那份
    val latest = rememberUpdatedState(input)
    /** 命中半径（设备像素）：24dp 是拇指点得中又不吞邻点的档位。 */
    val hitRadiusPx = HIT_RADIUS_DP * density

    // 可见瓦片：**在协程里读视野**（`snapshotFlow` 的读取只登记快照依赖，不触发重组），
    // 静默 TILE_DEBOUNCE_MS 才发一次请求——拖动中每帧都会重算，但连续变化被 debounce 收成一次。
    // 首帧与尺寸变化都走同一条 flow：画布尺寸就是它的输入之一。
    LaunchedEffect(tiles) {
        val source = tiles ?: return@LaunchedEffect
        snapshotFlow {
            visibleKeysFor(
                viewport = viewport.value,
                widthPx = canvasSize.value.width.toFloat(),
                heightPx = canvasSize.value.height.toFloat(),
            )
        }
            .distinctUntilChanged()
            .debounce(TILE_DEBOUNCE_MS)
            .collectLatest { keys -> if (keys.isNotEmpty()) source.load(keys) }
    }

    // 视口尺寸是几何量，与「是不是用户动作」无关：量到布局就报一次（尺寸变了再报）。
    // 尺寸仍然只在 `onSizeChanged` 里写；用 flow 读它，组合期不订阅。
    LaunchedEffect(Unit) {
        snapshotFlow { canvasSize.value }
            .filter { it.width > 0 && it.height > 0 }
            .collect {
                latest.value.onViewportMeasured(halfDiagonalMetersFor(viewport.value, it))
            }
    }

    // 「停手」回报：SETTLE_DELAY_MS 内没有新动作才算停（与 Android 的静默区同一意图）。
    // 缩放变了半对角线也变，所以顺手再报一次视口——拖动过程中不必逐帧汇报。
    // 声明在镜头 effect 之前：`settleSignal` 无 replay，先订上才不会漏掉建图那次的镜头落位。
    LaunchedEffect(Unit) {
        settleSignal
            .debounce(SETTLE_DELAY_MS)
            .collect {
                val size = canvasSize.value
                val settled = viewport.value
                if (size.width > 0 && size.height > 0) {
                    latest.value.onViewportMeasured(halfDiagonalMetersFor(settled, size))
                }
                latest.value.onCenterSettled(settled.lat, settled.lng, settled.zoom)
            }
    }

    // 镜头请求：按 nonce 触发（同坐标连点两次也要动），执行完回报 onCameraApplied
    val camera = input.camera
    LaunchedEffect(camera?.nonce) {
        if (camera == null) return@LaunchedEffect
        val current = viewport.value
        viewport.value = current.copy(
            lat = camera.lat,
            lng = camera.lng,
            zoom = camera.zoom?.takeIf { it > 0.0 }?.let(BikeMapProjection::clampZoom)
                ?: current.zoom,
        )
        settleSignal.tryEmit(Unit)
        latest.value.onCameraApplied(camera.nonce)
    }

    val gestures = Modifier
        .onSizeChanged { canvasSize.value = it }
        .pointerInput(Unit) {
            detectTapGestures { offset ->
                val size = canvasSize.value
                val width = size.width.toFloat()
                val height = size.height.toFloat()
                if (width <= 0f || height <= 0f) return@detectTapGestures
                val data = latest.value
                val tapped = BikeMapPoint(offset.x, offset.y)
                // 一次点击内视野不会变（这一笔手势里没有写视野的代码），读一次与逐次读等价
                val current = viewport.value
                fun screenOf(lat: Double, lng: Double): BikeMapPoint =
                    BikeMapProjection.toScreen(
                        centerLat = current.lat,
                        centerLng = current.lng,
                        zoom = current.zoom,
                        lat = lat,
                        lng = lng,
                        widthPx = width,
                        heightPx = height,
                    )

                fun hitTest(point: GcjPoint): Boolean =
                    screenOf(point.lat, point.lng).distanceTo(tapped) <= hitRadiusPx

                // 顺序 = 图层顺序：我的车 → 车标 → 聚合圈（近的优先，取最近的那一枚）
                val rideLat = data.rideLat
                val rideLng = data.rideLng
                if (rideLat != null && rideLng != null &&
                    hitTest(GcjPoint(rideLat, rideLng))
                ) {
                    data.onRideTap()
                    return@detectTapGestures
                }
                if (data.byBike) {
                    val bike = data.bikes
                        .minByOrNull { screenOf(it.lat, it.lng).distanceTo(tapped) }
                    if (bike != null && hitTest(GcjPoint(bike.lat, bike.lng))) {
                        data.onBikeTap(bike.carNum)
                        return@detectTapGestures
                    }
                } else {
                    val cluster = data.clusters
                        .minByOrNull { screenOf(it.lat, it.lng).distanceTo(tapped) }
                    if (cluster != null && hitTest(GcjPoint(cluster.lat, cluster.lng))) {
                        data.onClusterTap(cluster.key)
                        return@detectTapGestures
                    }
                }
            }
        }
        .pointerInput(Unit) {
            detectTransformGestures { _, pan, zoom, _ ->
                val panned = pan != Offset.Zero
                val pinched = zoom != 1f
                if (!panned && !pinched) return@detectTransformGestures
                val current = viewport.value
                val shifted = if (panned) {
                    BikeMapProjection.shiftCenter(
                        centerLat = current.lat,
                        centerLng = current.lng,
                        dxPx = pan.x.toDouble(),
                        dyPx = pan.y.toDouble(),
                        zoom = current.zoom,
                    )
                } else {
                    null
                }
                viewport.value = current.copy(
                    lat = shifted?.lat ?: current.lat,
                    lng = shifted?.lng ?: current.lng,
                    zoom = BikeMapProjection.zoomAfterPinch(current.zoom, zoom),
                )
                settleSignal.tryEmit(Unit)
                // 单指拖图 = 用户接管镜头（页面据此退出「跟着用户走」）
                if (panned) latest.value.onUserPan()
            }
        }

    Canvas(
        modifier = modifier
            .semantics { contentDescription = MAP_CONTENT_DESCRIPTION }
            .then(gestures),
    ) {
        drawRect(color = background)
        val widthPx = size.width
        val heightPx = size.height
        if (widthPx <= 0f || heightPx <= 0f) return@Canvas
        // 投影用**设备像素**：口径与 Android 高德一致（zoom 17 一屏约 1.2 km，正好装下校区），
        // 不要改成 dp 尺度——那会让同一 zoom 级别在 3x 屏上放大三倍，和 Android 对不上。
        //
        // `viewport.value` 在**绘制期**读：手势帧只让这块绘制失效，不重建这段组合（见类头 KDoc）。
        val vp = viewport.value
        val zoom = vp.zoom
        val metersPerPixel = BikeMapProjection.metersPerPixel(vp.lat, zoom)
        val colors = input.colors
        val cullMargin = CULL_MARGIN_DP.dp.toPx()

        fun project(lat: Double, lng: Double): BikeMapPoint = BikeMapProjection.toScreen(
            centerLat = vp.lat,
            centerLng = vp.lng,
            zoom = zoom,
            lat = lat,
            lng = lng,
            widthPx = widthPx,
            heightPx = heightPx,
        )

        fun screenOf(lat: Double, lng: Double): Offset {
            val point = project(lat, lng)
            return Offset(point.x, point.y)
        }

        fun onScreen(point: Offset): Boolean =
            point.x >= -cullMargin && point.x <= widthPx + cullMargin &&
                point.y >= -cullMargin && point.y <= heightPx + cullMargin

        // 1) 底图：有瓦片就贴瓦片，一块都没有才画尺度网格（首帧 / 断网时的兜底）。
        //    瓦片在绘制期读缓存：到货只失效这块绘制，不触发整页重组。
        val tileSource = tiles
        val readyTiles = if (tileSource == null) {
            emptyList()
        } else {
            // 计数恒 ≥ 0；这一行只是让绘制订阅「瓦片到货」
            val revision = tileSource.revision.value
            if (revision < 0) {
                emptyList()
            } else {
                // 「画哪些」与「拉哪些」共用同一个纯函数：请求到的就是画得到的
                visibleKeysFor(vp, widthPx, heightPx)
                    .mapNotNull { key -> tileSource.cached(key)?.let { key to it } }
            }
        }
        if (readyTiles.isEmpty()) {
            drawScaleGrid(
                viewport = vp,
                metersPerPixel = metersPerPixel,
                widthPx = widthPx,
                heightPx = heightPx,
                color = gridColor,
                project = ::screenOf,
            )
        } else {
            // 同一屏的瓦片级别相同；尺寸按「相机级别 - 瓦片级别」放大（见 BikeMapTiles 的 2x 说明），
            // 边界向上取整盖住相邻瓦片之间的半像素缝
            val span = BikeMapTiles.tileSpanPx(zoom, readyTiles.first().first.z)
            val spanInt = ceil(span).toInt().coerceAtLeast(1)
            readyTiles.forEach { (key, image) ->
                val corner = BikeMapTiles.tileTopLeft(
                    key = key,
                    centerLat = vp.lat,
                    centerLng = vp.lng,
                    zoom = zoom,
                    widthPx = widthPx,
                    heightPx = heightPx,
                )
                drawImage(
                    image = image,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(image.width, image.height),
                    dstOffset = IntOffset(corner.x.roundToInt(), corner.y.roundToInt()),
                    dstSize = IntSize(spanInt, spanInt),
                    filterQuality = FilterQuality.Low,
                )
            }
        }

        // 2) 校区围栏（关掉「只看本校」时连描边一起隐藏，与 Android 同口径）
        if (input.showCampusFence) {
            drawGeoPolygon(
                points = BikeNearby.CAMPUS_FENCE,
                project = ::screenOf,
                onScreen = ::onScreen,
                fill = null,
                stroke = Color(colors.fenceStroke),
                strokeWidth = 1.5.dp.toPx(),
                dash = PathEffect.dashPathEffect(
                    floatArrayOf(7.dp.toPx(), 4.dp.toPx()),
                    0f,
                ),
            )
        }

        // 3) 禁停区（压在围栏之上、还车点之下，与官方 zIndex 同序）
        input.zones.nogoZones.forEach { outline ->
            drawGeoPolygon(
                points = outline,
                project = ::screenOf,
                onScreen = ::onScreen,
                fill = Color(colors.nogoFill),
                stroke = Color(colors.nogoStroke),
                strokeWidth = 1.dp.toPx(),
            )
        }

        // 4) 还车点：范围多边形 + 落点徽标（徽标里那个「P」与 Android 同一造型；
        //    地图上没有可点的入口，它只负责「这里是还车点」）
        input.zones.parkSpots.forEach { spot ->
            drawGeoPolygon(
                points = spot.outline,
                project = ::screenOf,
                onScreen = ::onScreen,
                fill = Color(colors.spotFill),
                stroke = Color(colors.spotStroke),
                strokeWidth = 1.dp.toPx(),
            )
            val badge = screenOf(spot.lat, spot.lng)
            if (onScreen(badge)) {
                val center = Offset(badge.x, badge.y)
                val radius = SPOT_BADGE_RADIUS_DP.dp.toPx()
                drawCircle(color = Color(colors.spotBadge), radius = radius, center = center)
                val glyph = measurer.measure(
                    AnnotatedString("P"),
                    TextStyle(
                        fontSize = 9.dp.toSp(),
                        fontWeight = FontWeight.Bold,
                        color = Color(colors.spotGlyph),
                    ),
                )
                drawText(
                    textLayoutResult = glyph,
                    topLeft = Offset(
                        center.x - glyph.size.width / 2f,
                        center.y - glyph.size.height / 2f,
                    ),
                )
            }
        }

        // 5) 车标：按车辆逐辆画，否则画停车点聚合圈
        if (input.byBike) {
            input.bikes.forEach { bike ->
                val point = screenOf(bike.lat, bike.lng)
                if (!onScreen(point)) return@forEach
                val center = Offset(point.x, point.y)
                val selected = bike.carNum == input.selectedBikeCarNum
                val radius = if (selected) {
                    BIKE_SELECTED_RADIUS_DP.dp.toPx()
                } else {
                    BIKE_RADIUS_DP.dp.toPx()
                }
                drawCircle(color = Color(bikeColor(bike, colors)), radius = radius, center = center)
                drawCircle(
                    color = Color.White,
                    radius = radius,
                    center = center,
                    style = Stroke(width = if (selected) 2.5.dp.toPx() else 1.5.dp.toPx()),
                )
            }
        } else {
            input.clusters.forEach { cluster ->
                val point = screenOf(cluster.lat, cluster.lng)
                if (!onScreen(point)) return@forEach
                val selected = cluster.key == input.selectedKey
                drawCluster(
                    cluster = cluster,
                    center = Offset(point.x, point.y),
                    selected = selected,
                    colors = colors,
                    measurer = measurer,
                )
            }
        }

        // 6) 识别高亮：双层定位圈（比实心标记轻，不该压过正在骑的那辆）
        val highlightLat = input.highlightLat
        val highlightLng = input.highlightLng
        if (highlightLat != null && highlightLng != null) {
            val point = screenOf(highlightLat, highlightLng)
            if (onScreen(point)) {
                val center = Offset(point.x, point.y)
                drawCircle(
                    color = Color(colors.available),
                    radius = HIGHLIGHT_RING_RADIUS_DP.dp.toPx(),
                    center = center,
                    style = Stroke(width = 2.5.dp.toPx()),
                )
                drawCircle(
                    color = Color(colors.available),
                    radius = HIGHLIGHT_DOT_RADIUS_DP.dp.toPx(),
                    center = center,
                )
                input.highlightLabel?.takeIf { it.isNotBlank() }?.let { label ->
                    drawLabelChip(
                        text = label,
                        anchor = center,
                        topOffsetPx = HIGHLIGHT_RING_RADIUS_DP.dp.toPx() + 12.dp.toPx(),
                        fill = Color(colors.available),
                        textColor = Color(colors.label),
                        measurer = measurer,
                    )
                }
            }
        }

        // 7) 当前用车：比聚合圈大、主色实心 + 白描边（「我的车」比一圈停车点重要）
        val rideLat = input.rideLat
        val rideLng = input.rideLng
        if (rideLat != null && rideLng != null) {
            val point = screenOf(rideLat, rideLng)
            if (onScreen(point)) {
                val center = Offset(point.x, point.y)
                drawCircle(
                    color = Color(colors.rideHalo),
                    radius = RIDE_HALO_RADIUS_DP.dp.toPx(),
                    center = center,
                )
                drawCircle(
                    color = Color(colors.rideMarker),
                    radius = RIDE_RADIUS_DP.dp.toPx(),
                    center = center,
                )
                drawCircle(
                    color = Color.White,
                    radius = RIDE_RADIUS_DP.dp.toPx(),
                    center = center,
                    style = Stroke(width = 3.dp.toPx()),
                )
            }
        }

        // 8) 蓝点最后画（压在所有标记之上：「我在哪」是点定位之后要确认的事）
        val userLat = input.userLat
        val userLng = input.userLng
        if (userLat != null && userLng != null) {
            val point = screenOf(userLat, userLng)
            if (onScreen(point)) {
                val center = Offset(point.x, point.y)
                if (input.userAccuracyMeters > 0f) {
                    drawCircle(
                        color = Color(colors.userHalo),
                        radius = (input.userAccuracyMeters / metersPerPixel).toFloat(),
                        center = center,
                    )
                }
                drawCircle(
                    color = Color(colors.userHalo),
                    radius = USER_HALO_RADIUS_DP.dp.toPx(),
                    center = center,
                )
                drawCircle(
                    color = Color(colors.userDot),
                    radius = USER_DOT_RADIUS_DP.dp.toPx(),
                    center = center,
                )
                drawCircle(
                    color = Color.White,
                    radius = USER_DOT_RADIUS_DP.dp.toPx(),
                    center = center,
                    style = Stroke(width = 1.dp.toPx()),
                )
            }
        }

        // 9) 中心浮标：屏幕正中，与视野中心重合（拖动时它不动，地图在下面走）
        drawCenterPin(center = Offset(widthPx / 2f, heightPx / 2f), colors = colors)
    }
}

/**
 * 「这个视野 + 这个画布尺寸要哪些瓦片」的唯一算法。
 *
 * 两处共用：绘制期算**要画哪些**（`Canvas` 的 draw lambda）、瓦片请求 flow 算**要拉哪些**。
 * 也是 `distinctUntilChanged` 的前提——它必须是**纯函数**：同输入同输出、结果可比。
 * 掺进时间 / 随机数 / 可变状态，拖动时就会每帧都判定「视野变了」，把去重和节流全废掉
 * （`app/src/test/.../BikeMapTilesTest.kt` 有一条用例钉这一点）。
 */
private fun visibleKeysFor(
    viewport: BikeMapViewport,
    widthPx: Float,
    heightPx: Float,
): List<BikeTileKey> {
    if (widthPx <= 0f || heightPx <= 0f) return emptyList()
    return BikeMapTiles.visibleKeys(
        centerLat = viewport.lat,
        centerLng = viewport.lng,
        zoom = viewport.zoom,
        widthPx = widthPx,
        heightPx = heightPx,
    )
}

/** 半对角线（米）：`onViewportMeasured` 报的就是这个量，两处回报共用一个算式。 */
private fun halfDiagonalMetersFor(viewport: BikeMapViewport, size: IntSize): Double =
    BikeMapProjection.halfDiagonalMeters(
        lat = viewport.lat,
        zoom = viewport.zoom,
        widthPx = size.width.toFloat(),
        heightPx = size.height.toFloat(),
    )

/**
 * 尺度网格。经 / 纬两条方向都画，间距取 [BikeMapProjection.gridStepMeters] 那一档，
 * 网格线按世界坐标对齐（拖动时线跟着地图走，不会像屏幕坐标网格那样原地闪烁）。
 *
 * 只在**一块瓦片都没有**时调用（首帧 / 断网）：它是「没有底图时唯一看得懂多大」的参照。
 */
private fun DrawScope.drawScaleGrid(
    viewport: BikeMapViewport,
    metersPerPixel: Double,
    widthPx: Float,
    heightPx: Float,
    color: Color,
    project: (lat: Double, lng: Double) -> Offset,
) {
    val stepMeters = BikeMapProjection.gridStepMeters(metersPerPixel)
    if (stepMeters <= 0.0) return
    val latStep = BikeMapProjection.latStepForMeters(stepMeters)
    val lngStep = BikeMapProjection.lngStepForMeters(stepMeters, viewport.lat)
    if (latStep <= 0.0 || lngStep <= 0.0) return
    val strokeWidth = 0.6.dp.toPx()

    val latLines = BikeMapProjection.gridLineCount(heightPx, stepMeters, metersPerPixel)
    if (latLines > 0) {
        // 从当前中心的那一格往两侧铺；`floor` 保证第一格在视野外，边缘不留空
        val firstLatIndex = floor(viewport.lat / latStep).toLong() - latLines / 2
        for (i in 0 until latLines) {
            val lat = (firstLatIndex + i) * latStep
            val y = project(lat, viewport.lng).y
            if (y >= 0f && y <= heightPx) {
                drawLine(
                    color = color,
                    start = Offset(0f, y),
                    end = Offset(widthPx, y),
                    strokeWidth = strokeWidth,
                )
            }
        }
    }

    val lngLines = BikeMapProjection.gridLineCount(widthPx, stepMeters, metersPerPixel)
    if (lngLines > 0) {
        val firstLngIndex = floor(viewport.lng / lngStep).toLong() - lngLines / 2
        for (i in 0 until lngLines) {
            val lng = (firstLngIndex + i) * lngStep
            val x = project(viewport.lat, lng).x
            if (x >= 0f && x <= widthPx) {
                drawLine(
                    color = color,
                    start = Offset(x, 0f),
                    end = Offset(x, heightPx),
                    strokeWidth = strokeWidth,
                )
            }
        }
    }
}

/** 一条地理多边形：先填后描（[fill] 为 null 就只描边，如校区围栏）。屏幕外的点直接跳过。 */
private fun DrawScope.drawGeoPolygon(
    points: List<GcjPoint>,
    project: (lat: Double, lng: Double) -> Offset,
    onScreen: (Offset) -> Boolean,
    fill: Color?,
    stroke: Color,
    strokeWidth: Float,
    dash: PathEffect? = null,
) {
    if (points.size < 3) return
    if (points.none { onScreen(project(it.lat, it.lng)) }) return
    val path = Path()
    points.forEachIndexed { index, gcj ->
        val point = project(gcj.lat, gcj.lng)
        if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
    }
    path.close()
    fill?.let { drawPath(path = path, color = it) }
    drawPath(
        path = path,
        color = stroke,
        style = Stroke(width = strokeWidth, pathEffect = dash),
    )
}

/** 一个停车点聚合圈：底圆 + 描边 + 圈内数字（数字是「这里几辆能骑」的唯一可见来源）。 */
private fun DrawScope.drawCluster(
    cluster: BikeCluster,
    center: Offset,
    selected: Boolean,
    colors: BikeMarkerColors,
    measurer: TextMeasurer,
) {
    val radius = if (selected) CLUSTER_SELECTED_RADIUS_DP.dp.toPx() else CLUSTER_RADIUS_DP.dp.toPx()
    drawCircle(color = Color(clusterColor(cluster, colors)), radius = radius, center = center)
    drawCircle(
        color = if (selected) Color(colors.selectedRing) else Color.White,
        radius = radius,
        center = center,
        style = Stroke(width = if (selected) 3.dp.toPx() else 2.dp.toPx()),
    )
    val labelStyle = TextStyle(
        fontSize = if (selected) 13.dp.toSp() else 12.dp.toSp(),
        fontWeight = FontWeight.Bold,
        color = Color(colors.label),
    )
    val layout = measurer.measure(AnnotatedString(cluster.bikes.size.toString()), labelStyle)
    drawText(
        textLayoutResult = layout,
        topLeft = Offset(
            center.x - layout.size.width / 2f,
            center.y - layout.size.height / 2f,
        ),
    )
}

/** 高亮点上方的小文字条（如「车 …669」）：底一块圆角矩形，字压在上面。 */
private fun DrawScope.drawLabelChip(
    text: String,
    anchor: Offset,
    topOffsetPx: Float,
    fill: Color,
    textColor: Color,
    measurer: TextMeasurer,
) {
    val style = TextStyle(fontSize = 11.dp.toSp(), fontWeight = FontWeight.Medium, color = textColor)
    val layout = measurer.measure(AnnotatedString(text), style)
    val chipWidth = layout.size.width.toFloat() + 14.dp.toPx()
    val chipHeight = layout.size.height.toFloat() + 6.dp.toPx()
    val left = anchor.x - chipWidth / 2f
    val top = anchor.y - topOffsetPx - chipHeight
    drawRoundRect(
        color = fill,
        topLeft = Offset(left, top),
        size = Size(chipWidth, chipHeight),
        cornerRadius = CornerRadius(chipHeight / 2f),
    )
    drawText(
        textLayoutResult = layout,
        topLeft = Offset(
            left + (chipWidth - layout.size.width) / 2f,
            top + (chipHeight - layout.size.height) / 2f,
        ),
    )
}

/**
 * 中心浮标：针杆 + 针头 + 针心（官方小程序那个 lollipop）。**尖端落在画布中心**,
 * 与 `onCenterSettled` 报的坐标一致——嘴上说中心在哪，画上就得指在哪。
 */
private fun DrawScope.drawCenterPin(center: Offset, colors: BikeMarkerColors) {
    val headRadius = CENTER_PIN_HEAD_RADIUS_DP.dp.toPx()
    val stem = CENTER_PIN_STEM_LENGTH_DP.dp.toPx()
    drawLine(
        color = Color(colors.centerMark),
        start = Offset(center.x, center.y),
        end = Offset(center.x, center.y - stem),
        strokeWidth = CENTER_PIN_STEM_WIDTH_DP.dp.toPx(),
    )
    drawCircle(
        color = Color(colors.centerMark),
        radius = headRadius,
        center = Offset(center.x, center.y - stem - headRadius),
    )
    drawCircle(
        color = Color(colors.centerGlyph),
        radius = CENTER_PIN_DOT_RADIUS_DP.dp.toPx(),
        center = Offset(center.x, center.y - stem - headRadius),
    )
}

/** 簇里只要有一辆能骑就画「可用」，否则退「电量低」，再退灰（与 `BikeMarkerOverlay` 同口径）。 */
private fun clusterColor(cluster: BikeCluster, colors: BikeMarkerColors): Int = when {
    cluster.bikes.any { it.available } -> colors.available
    cluster.bikes.any { it.status == BikeStatus.LowBattery } -> colors.lowBattery
    else -> colors.unavailable
}

/** 单车图标颜色：同一套语义，按这一辆自己的状态判。 */
private fun bikeColor(bike: NearbyBike, colors: BikeMarkerColors): Int = when (bike.status) {
    BikeStatus.Available -> colors.available
    BikeStatus.LowBattery -> colors.lowBattery
    BikeStatus.Offline, BikeStatus.Disabled -> colors.unavailable
}

/** 命中半径（dp）：与 Android `BikeMarkerOverlay` 的 24dp 同值，拇指点得中又不吞掉邻点。 */
private const val HIT_RADIUS_DP = 24f

/** 屏幕外剔除的余量（dp）：盖住标记半径与还车点多边形，拖动时把离屏的绘制挡在前面。 */
private const val CULL_MARGIN_DP = 48f

/** 「停手」判定的静默时长（毫秒）：比 Android 的 400ms 稍长，给捏合留下一口气。 */
private const val SETTLE_DELAY_MS = 450L

/**
 * 瓦片请求的静默时长（毫秒）：拖动中每帧都会重算视野，连续变化靠它收成一次请求。
 * 取 120ms 是「手指换向 / 短暂停顿」与「一屏十几块瓦片别被反复取消」之间的折中——
 * 比 [SETTLE_DELAY_MS] 短，所以先补瓦片、再报停手。
 *
 * 代价：**首次请求也晚 120ms**（`debounce` 对第一次发射一视同仁），首屏多一帧网格。
 * 换来的是拖动期间不再每帧重启一次取图（旧实现是 `LaunchedEffect(tiles, tileKeys)` 被重启）。
 */
private const val TILE_DEBOUNCE_MS = 120L

private const val CLUSTER_RADIUS_DP = 12f
private const val CLUSTER_SELECTED_RADIUS_DP = 15f
private const val BIKE_RADIUS_DP = 11f
private const val BIKE_SELECTED_RADIUS_DP = 13f
private const val HIGHLIGHT_RING_RADIUS_DP = 16f
private const val HIGHLIGHT_DOT_RADIUS_DP = 5f
private const val RIDE_RADIUS_DP = 14f
private const val RIDE_HALO_RADIUS_DP = 21f
private const val SPOT_BADGE_RADIUS_DP = 8f
private const val USER_HALO_RADIUS_DP = 11f
private const val USER_DOT_RADIUS_DP = 5f
private const val CENTER_PIN_HEAD_RADIUS_DP = 9f
private const val CENTER_PIN_STEM_LENGTH_DP = 17f
private const val CENTER_PIN_STEM_WIDTH_DP = 0.8f
private const val CENTER_PIN_DOT_RADIUS_DP = 3f

private const val MAP_CONTENT_DESCRIPTION = "附近单车地图"
