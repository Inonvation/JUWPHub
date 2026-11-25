@file:OptIn(ExperimentalForeignApi::class)

package edu.jxslu.schedule.ui.ebike

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import edu.jxslu.schedule.domain.BikeCluster
import edu.jxslu.schedule.domain.BikeNearby
import edu.jxslu.schedule.domain.BikeStatus
import edu.jxslu.schedule.domain.GcjPoint
import edu.jxslu.schedule.domain.KvcxZones
import edu.jxslu.schedule.domain.NearbyBike
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.CoreLocation.CLLocationCoordinate2D
import platform.CoreLocation.CLLocationCoordinate2DMake
import platform.MapKit.MKAnnotationProtocol
import platform.MapKit.MKAnnotationView
import platform.MapKit.MKCircle
import platform.MapKit.MKCircleRenderer
import platform.MapKit.MKCoordinateRegion
import platform.MapKit.MKCoordinateRegionMakeWithDistance
import platform.MapKit.MKMapView
import platform.MapKit.MKMapViewDelegateProtocol
import platform.MapKit.MKMarkerAnnotationView
import platform.MapKit.MKOverlayProtocol
import platform.MapKit.MKOverlayRenderer
import platform.MapKit.MKPointAnnotation
import platform.MapKit.MKPolygon
import platform.MapKit.MKPolygonRenderer
import platform.MapKit.MKShape
import platform.UIKit.UIColor
import platform.UIKit.UIImage
import platform.darwin.NSObject
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/*
 * 文件头说明（不是 KDoc：它不属于某一个声明，是这一层的地图）
 * ==========================================================================
 *
 * 快趣出行地图的 **iOS 实现（MapKit）**：吃与 Android 完全同一份 `BikeMapInput`
 * （iOS 移植 P4，2026-11-25）。
 *
 * ## 一、为什么是 MapKit，不是高德 iOS SDK
 *
 * 1. **坐标系天生对**：中国区 Apple 地图的数据就是高德提供的，基准同为 GCJ-02。应用内部
 *    （车辆 / 还车点 / 围栏 / 定位）也全是 GCJ-02，所以这里**一个坐标都不用转**——与
 *    `AmapMapView` 的「整条显示基准转换链都不需要」是同一条结论。
 * 2. **零依赖、零 key、不绑 Bundle ID**：`platform.MapKit` 是 Kotlin/Native 自带的平台库，
 *    不新增 Gradle 依赖、不要 key、不做包名校验，自签 IPA 也能跑（高德 iOS SDK 要 key 且
 *    key 与 Bundle ID 绑定）。
 * 3. **系统件够用**：指北针、底图版权署名、标注气泡、多边形/圆渲染都是系统画的，
 *    这一层不需要自绘（Android 侧自绘是因为高德与 osmdroid 都没给现成的）。
 *
 * > **若真机出现约几百米的系统性偏移**（同一批车在高德上是对的、在这里整体偏一个固定方向），
 * > 说明这份接口数据其实是 WGS-84，那时才在 [coordinateOf]/[polygonOf] 里过一遍
 * > `Gcj02.toGcj02`。方向乱跳不是坐标系问题，是定位精度问题——先量，别猜。
 *
 * ## 二、本机编不了，所以符号取保守写法
 *
 * Windows 上没有 Kotlin/Native，这个文件**没有在本机编译过**。用到的平台符号按风险从高到低：
 *
 * | 符号 | 用法 | 万一编不过怎么改 |
 * |---|---|---|
 * | `MKPolygon.polygonWithCoordinates(coords, count)` | `memScoped { allocArray(...) }` 传坐标数组 | 换成 `MKPolygon(points = …, count = …)`（`MKMapPoint` 版）；或把坐标数组改成堆分配并让持有者释放。**注意**：Apple 文档说这些构造器会拷贝坐标，`memScoped` 退出后释放是安全的——若真机出现多边形错乱，先怀疑这一条 |
 * | `UIImage.systemImageNamed(name)` | 车 / 蓝点的符号图（`bicycle` / `location.fill`） | 删掉 `glyphImageName`，改用 `glyphText`（车标留空、蓝点用「我」），只丢造型不丢功能 |
 * | `MKMarkerAnnotationView(annotation = …, reuseIdentifier = …)` | 标注视图 | 换成 `MKMarkerAnnotationView(frame = CGRectMake(…))` + `view.annotation = annotation` |
 * | `UIColor(red = …, green = …, blue = …, alpha = …)` | ARGB → UIColor | 换成类工厂 `UIColor.colorWithRed(…)` |
 * | `MKCircleRenderer(circle = …)` / `MKPolygonRenderer(polygon = …)` | 覆盖物渲染器 | 换成父类 `MKOverlayPathRenderer(overlay = …)`（两个子类只是多设了形状） |
 *
 * 明确**没用**的东西（拿不准属性名，宁可不写）：
 *
 * - `zoomEnabled` / `rotateEnabled` / `pitchEnabled`（MKMapView 用 `getter=is…Enabled` 声明，
 *   Kotlin 侧到底叫 `zoomEnabled` 还是 `isZoomEnabled` 没把握）。代价是 iOS 允许双指旋转与
 *   俯视，Android 关掉了；确认写法后补三行即可。
 * - `MKOverlayLevel`（多边形叠放等级）：三层多边形按**加入顺序**叠（围栏 → 禁停区 → 还车点
 *   范围），MapKit 同等级下后加的画在上面，顺序已经对了。
 *
 * ## 三、与 Android 的差异（同一契约，不追求像素级一致）
 *
 * | 项 | Android | 这里 |
 * |---|---|---|
 * | 聚合 / 车标造型 | 离屏位图自绘（数字 + 小车轮廓） | `MKMarkerAnnotationView` 系统气球：数字用 `glyphText`，车用 `bicycle` 符号图 |
 * | 选中态 | 叠一枚描边圈 + 加粗 | 该车/该簇的气球换成 `selectedRing` 色（不额外叠标记） |
 * | 中心浮标（针形） | Compose `Canvas` 画在视图正中 | **不画**：MapKit 没有可挂的 Overlay 视图层，值不值得用 `MKAnnotation` 假装一个针，留待真机看过再说 |
 * | 蓝点朝向（[BikeMapInput.userHeadingFlow]） | `BikeCompass` 自绘箭头 | **忽略**：气球旋转造型会歪，系统蓝点又要求定位授权 |
 * | 相机动画 | 逐帧缓动（400ms / 1000ms，与 osmdroid 逐项对齐） | `setRegion(animated = true)`：曲线与时长交给系统，不可控 |
 * | `onFallback` | key 失效 → 整层降级栅格 | **不会回调**：MapKit 没有「底图拿不到」这一档 |
 * | `logoBottomMarginPx` | 生效（版权标识不能被面板盖住） | **忽略**：Apple 地图的署名由系统画在右下角 |
 * | 用户接管（`onUserPan`） | 触摸流 + 手势回调两路，单指拖动才认 | 只有 `regionWillChangeAnimated`：**程序性移动的静默窗内（≤1.2 秒）用户拖动不算接管**。要补成和 Android 一样准，得挂 `UIPanGestureRecognizer`（多三个拿不准的符号），留给真机验过再说 |
 * | 停手判定 | 高德给 `onCameraChangeFinish`；osmdroid 那条是 120ms 轮询 | `regionDidChangeAnimated`：MapKit 自己保证「真的停下了」才回调，与高德那条同一档 |
 *
 * ## 四、视口与缩放：两端各有一套换算
 *
 * MapKit 没有「缩放级别」，只有经纬度跨度。这里的换算是**固定常数**的：
 *
 * ```
 * spanLng = 360 × 视口宽(point) / (TILE_POINTS × 2^zoom)
 * ```
 *
 * [TILE_POINTS] 取 128 而不是 256：一张 256 像素的地图瓦片在 2x 屏上就是 128 point，
 * 这样算出来的视野与 Android 1080px 手机在 z17 下（约 1 公里宽）是同一量级。
 *
 * 这个换算的绝对精度**不影响查询**：真正决定采样半径的是 [BikeMapInput.onViewportMeasured]
 * 回报的实测米数，而它由「中心点 → 视口东北角」的球面距离现算（与 `AmapMapView` 同一个写法）。
 * 换算只影响两件事：`initialZoom` 落地时的视野，以及回传给 `onCenterSettled` 的 zoom 值
 * （ViewModel 拿它当「上次视野」存盘、判 `isZoomedOut`）——两端各用各的标尺，只要**本端自洽**
 * （写进去的 zoom 与读回来的 zoom 同一条公式）就不会漂。
 *
 * ## 五、图层怎么更新
 *
 * 与 Android 同一条纪律：`UIKitView.update` 每轮重组都会跑一遍，所以先比一份
 * [DrawableInputs]（只装「画上去的东西」），变了才动图层；[BikeMapInput] 里的回调不进比对
 * （它们是闭包，每换一次实例都很正常）。
 *
 * 标注按 key 增删改（不整层重建）：key 的命名与 `AmapMapView.AmapLayers` 一致
 * （`spot:` / `bike:` / `cluster:` / `hl` / `ride` / `user`），两端出问题时能对照同一个 key
 * 排查。**注意 iOS 比 Android 少一个 key**：Android 的 `sel:`（选中圈）这里不画，选中态改在
 * `bike:` 自己身上体现。
 *
 * ## 六、宿主怎么用
 *
 * ```kotlin
 * // iOS 宿主（MainViewController 那一层）：
 * val bikeMap = remember { iosBikeMapSlot() }
 * // 页面拿到 `bikeMap: BikeMapSlot?`，渲染时：
 * bikeMap?.content(input, Modifier.fillMaxSize())
 * // 槽位为 null 时页面只渲染底部车辆列表、不渲染地图（design/ios-port.md §11.2 ④）
 * ```
 *
 * 现在 `RideScreen` 还在 `:app`，所以本函数暂时没有调用方——它是**给搬页面那一步准备好的入口**。
 * Android 侧对应的是 `app/.../ui/ebike/BikeMapHost.kt`（它还多管一层 Android 独有的
 * 高德 key 回退），两者吃同一个 [BikeMapInput]。
 */

/** 地图缩放 ↔ 跨度换算里的「一张瓦片合多少 point」，见文件头第四节。 */
private const val TILE_POINTS = 128.0

/** 视口宽度还没量到时的兜底（point）：iPhone 竖屏约 393，取 400 免得首屏视野差太多。 */
private const val FALLBACK_WIDTH_POINTS = 400.0

/** 程序性移动的静默窗口：与 Android 的 `MOVE_QUIET_MS` 同值（盖住动画与收尾几帧）。 */
private const val MOVE_QUIET_MS = 1_200L

/** 恢复视野（无动画）的静默窗口：与 Android 的 `RESTORE_QUIET_MS` 同值。 */
private const val RESTORE_QUIET_MS = 300L

/** 标注复用标识：造型由 [applyStyle] 每次重设，所以一个标识够用。 */
private const val REUSE_ID = "bike"

/** 精度圈在覆盖物里的标记（MapKit 的渲染器回调只给覆盖物对象，靠 title 认回样式）。 */
private const val ACCURACY_TITLE = "accuracy"

/** 校区围栏在覆盖物里的标记。 */
private const val FENCE_TITLE = "fence"

/**
 * 地图槽位入口：iOS 宿主把它包成 [BikeMapSlot] 交给页面（用法见文件头第六节）。
 *
 * 写成函数而不是 `val`：槽位是个持有 composable lambda 的对象，让宿主在 `remember` 里
 * 建一次就够了，别做成全局单例（多一个地图视图时会共用同一个对象，语义上说不清）。
 */
fun iosBikeMapSlot(): BikeMapSlot = BikeMapSlot { input, modifier -> IosBikeMap(input, modifier) }

/**
 * 把 [BikeMapInput] 渲染成一张 MapKit 地图（见文件头）。
 *
 * `modifier` 由页面给：整幅上移（被底部面板盖住那一段的一半）是页面侧的图层变换，
 * 地图层不知道面板有多高——与 Android 侧 `BikeMapHost` 的 `modifier` 同一分工。
 */
@Composable
fun IosBikeMap(input: BikeMapInput, modifier: Modifier = Modifier) {
    val host = remember { MapKitHost() }
    UIKitView(
        factory = { host.attach(input) },
        modifier = modifier,
        // update 在视图创建后也会跑一次，所以「首次落图层」不需要额外副作用——那反而会和
        // factory 抢时序（地图还没建好就被调用）
        update = { host.update(input) },
    )
    DisposableEffect(host) {
        onDispose { host.detach() }
    }
}

// ---------- 颜色与几何小工具 ----------

/** ARGB int → UIColor。[alphaScale] 用于把配色里的半透明再压一档（与 Android 的 `withAlpha` 同口径）。 */
private fun argbToUIColor(argb: Int, alphaScale: Double = 1.0): UIColor = UIColor(
    red = ((argb shr 16) and 0xFF) / 255.0,
    green = ((argb shr 8) and 0xFF) / 255.0,
    blue = (argb and 0xFF) / 255.0,
    alpha = (((argb ushr 24) and 0xFF) / 255.0) * alphaScale,
)

/** 只改透明度（镜像 `AmapMapView` 的 `withAlpha`）：精度圈的填充比描边淡一档。 */
private fun withAlpha(argb: Int, factor: Double): Int {
    val alpha = (((argb ushr 24) and 0xFF) * factor).roundToInt().coerceIn(0, 255)
    return (alpha shl 24) or (argb and 0x00FFFFFF)
}

/** 坐标（GCJ-02 直传，见文件头第一节）。 */
private fun coordinateOf(lat: Double, lng: Double): CValue<CLLocationCoordinate2D> =
    CLLocationCoordinate2DMake(lat, lng)

/** 视口宽度（point）；还没布局时是 0。 */
private fun widthPoints(view: MKMapView): Double = view.bounds.useContents { size.width }

/** 由 zoom 反推经度跨度（公式见文件头第四节）。 */
private fun spanLngForZoom(widthPoints: Double, zoom: Double): Double {
    val width = if (widthPoints > 0.0) widthPoints else FALLBACK_WIDTH_POINTS
    val safeZoom = zoom.coerceIn(MIN_ZOOM, MAX_ZOOM)
    return 360.0 * width / (TILE_POINTS * 2.0.pow(safeZoom))
}

/**
 * 由经度跨度反推 zoom（[spanLngForZoom] 的逆运算）。
 *
 * 跨度取不到（0 / 非有限）时回 [BikeNearby.DEFAULT_ZOOM]：调用方拿它当「上次视野」存盘，
 * 给一个明确的默认值比给 0 好——0 会让 ViewModel 判成「缩小视图」。
 */
private fun zoomFromSpanLng(widthPoints: Double, spanLng: Double): Double {
    if (!spanLng.isFinite() || spanLng <= 0.0) return BikeNearby.DEFAULT_ZOOM
    val width = if (widthPoints > 0.0) widthPoints else FALLBACK_WIDTH_POINTS
    val zoom = ln(360.0 * width / (TILE_POINTS * spanLng)) / ln(2.0)
    return zoom.coerceIn(MIN_ZOOM, MAX_ZOOM)
}

/** 地球赤道半径（米）：跨度 ↔ 米 的换算用它与 `2π`，两处必须是同一常数，否则一写一读会漂。 */
private const val EARTH_RADIUS_METERS = 6_378_137.0

/**
 * 经度跨度 → 地面宽度（米）。
 *
 * 同一个 zoom 在不同纬度上换算出的米数不同（经线向两极收敛），所以**必须**带纬度：
 * 纬度 28.7° 下 1 度经度约 97.6 公里，赤道上则是 111.3 公里。
 */
private fun metersForSpanLng(lat: Double, spanLng: Double): Double =
    spanLng * (PI / 180.0) * EARTH_RADIUS_METERS * cos(lat * (PI / 180.0))

/**
 * 构造一个区域：中心 + 视野宽度（米）。
 *
 * 用 **`MKCoordinateRegionMakeWithDistance` 这个 C 函数**，不自己拼 `MKCoordinateRegion` 结构体
 * ——结构体的嵌套字段（`center` / `span`）在 Kotlin/Native 里怎么构造是这一份代码里最没把握的
 * 一处（见文件头第二节），而 C 函数只吃 `CValue` 参数，零歧义。纬度方向同样传这个米数：
 * 手机视口比宽高，MapKit 会按长宽比把纬度方向撑开，最终以实测视口为准。
 */
private fun regionOf(lat: Double, lng: Double, widthMeters: Double): CValue<MKCoordinateRegion> =
    MKCoordinateRegionMakeWithDistance(coordinateOf(lat, lng), widthMeters, widthMeters)

/** 某个 zoom 在某个视口宽度下对应的区域（[spanLngForZoom] + [metersForSpanLng] + [regionOf]）。 */
private fun regionForZoom(
    lat: Double,
    lng: Double,
    widthPointCount: Double,
    zoom: Double,
): CValue<MKCoordinateRegion> = regionOf(
    lat = lat,
    lng = lng,
    widthMeters = metersForSpanLng(lat, spanLngForZoom(widthPointCount, zoom)),
)

/** 相机允许的缩放区间：与 Android 的高德那套（3~20）同档。 */
private const val MIN_ZOOM = 3.0
private const val MAX_ZOOM = 20.0

/**
 * 程序性移动的「静默区」（`CameraSuppressor` 的 iOS 版，口径逐条对齐）。
 *
 * 为什么需要：定位 / 回到校区 / 点分组联动都会让地图动，动了就一定触发 `regionDidChange`，
 * 回调又会走 ViewModel 的重查判定。点分组那条尤其不能重查——那批车已经取回来了。
 *
 * 两条规则（与 Android 相同）：
 * 1. **时间窗**：窗口内一律吃掉（中间帧可能离瞄准点几百米，只看半径兜不住）；
 * 2. **瞄准点 30 米半径**：窗口过后剩下的收尾回调与 ViewModel 的重查阈值同半径，
 *    被它吃掉的回调本来也触发不了重查。
 *
 * 计时用 [TimeSource.Monotonic]（`design/ios-port.md` §11.2 ⑥：别用 `System.nanoTime`，
 * 这里也没有 `SystemClock`）。
 */
private class SettleSuppressor {
    private var quietUntil: TimeMark? = null
    private var aimLat: Double? = null
    private var aimLng: Double? = null

    fun aim(lat: Double, lng: Double) {
        aimLat = lat
        aimLng = lng
    }

    fun quietFor(millis: Long) {
        quietUntil = TimeSource.Monotonic.markNow() + millis.milliseconds
    }

    /** 当前是否还在静默窗内（用户接管的判定要用它，见文件头第三节）。 */
    fun isQuiet(): Boolean = quietUntil?.hasPassedNow()?.not() ?: false

    fun clear() {
        quietUntil = null
        aimLat = null
        aimLng = null
    }

    /** true = 这次回调要吃掉。 */
    fun shouldSwallow(lat: Double, lng: Double): Boolean {
        if (isQuiet()) return true
        val aimLat = aimLat ?: return false
        val aimLng = aimLng ?: return false
        if (BikeNearby.distanceMeters(aimLat, aimLng, lat, lng) < SUPPRESS_RADIUS_METERS) {
            return true
        }
        clear()
        return false
    }

    private companion object {
        /** 与 Android 同值：`BikeNearby.MIN_REQUERY_SHIFT_METERS`。 */
        const val SUPPRESS_RADIUS_METERS = BikeNearby.MIN_REQUERY_SHIFT_METERS
    }
}

// ---------- 图层的输入快照与样式 ----------

/**
 * `update` 块里用来比对「画上去的东西」的那一份（镜像 `MapOverlayInputs`）。
 *
 * 与 Android 那版的差别：这里把 [userPoint] / [userAccuracyMeters] 也算进比对——
 * iOS 没有「蓝点单独增量更新」那条路（MapKit 的标注视图由系统管，改坐标比建新视图便宜），
 * 一位小数级的定位跳动本来也只到 1 Hz，不进 10 Hz 的罗盘循环。
 */
private data class DrawableInputs(
    val colors: BikeMarkerColors,
    val clusters: List<BikeCluster>,
    val selectedKey: String?,
    val bikes: List<NearbyBike>,
    val byBike: Boolean,
    val selectedBikeCarNum: String?,
    val userPoint: GcjPoint?,
    val userAccuracyMeters: Float,
    val ridePoint: GcjPoint?,
    val highlightPoint: GcjPoint?,
    val highlightLabel: String?,
    val zones: KvcxZones,
    val showCampusFence: Boolean,
)

/** 一枚标注要长什么样（造型 + 上色）。 */
private class MarkerStyle(
    /** 气球里的文字（聚合数字 / 「P」/ 高亮车号）；null = 用 [glyphImageName]。 */
    val glyphText: String?,
    /** 气球里的符号图（SF Symbols 名）；null = 用 [glyphText]。 */
    val glyphImageName: String?,
    /** 气球主色（ARGB）。 */
    val tint: Int,
    /** 气泡里的字 / 图颜色（ARGB）。 */
    val glyphTint: Int,
)

/** 一枚标注的位置、造型与点击载荷。 */
private class MarkerPlan(
    val lat: Double,
    val lng: Double,
    val style: MarkerStyle,
    /** 可点标记（聚合圈 / 车 / 我的车）才有载荷；装饰标记为 null（**不接点击**）。 */
    val payload: MarkerPayload?,
)

/** 覆盖物样式：填充 / 描边都带 alpha（ARGB 里已经压过）。 */
private class PolygonStyle(val fill: Int, val stroke: Int, val lineWidth: Double)

/** 标注上挂的点击载荷（与 `AmapMapView.MarkerPayload` 同形）。 */
private sealed interface MarkerPayload {
    data class Cluster(val key: String) : MarkerPayload
    data class Bike(val carNum: String) : MarkerPayload
    data object Ride : MarkerPayload
}

// ---------- 地图宿主 ----------

/**
 * 一张 MapKit 地图的持有者：建图、按输入增量更新图层、把系统回调转给页面。
 *
 * 与 `IosWebHost` 同一分工：Compose 只负责把新输入递进来，视图与代理活多久由这里管
 * （`MKMapView.delegate` 是 **weak**，代理必须由本类强引用，否则会被立刻回收、一个回调都不来）。
 */
private class MapKitHost {

    private var mapView: MKMapView? = null
    private var delegate: MapDelegate? = null

    /** 最近一次交给我们的输入：回调要从这里取页面给的最新闭包，不能捕获建图时那一份。 */
    private var currentInput: BikeMapInput? = null

    /** 标注：key → 标注对象（key 的命名见文件头第五节）。 */
    private val annotations = LinkedHashMap<String, MKPointAnnotation>()

    /** 已建出来的标注视图：改样式时直接改，不必等系统重新问一遍。 */
    private val annotationViews = HashMap<String, MKMarkerAnnotationView>()

    private val styles = HashMap<String, MarkerStyle>()
    private val payloads = HashMap<String, MarkerPayload?>()

    /** 多边形覆盖物（样式按 title 存在 [polygonStyles] 里）。 */
    private val polygons = ArrayList<MKPolygon>()
    private val polygonStyles = HashMap<String, PolygonStyle>()

    private var accuracyOverlay: MKCircle? = null
    private var accuracyStyle: PolygonStyle? = null

    /** 上一次真正画上去的输入 / 两层多边形的指纹（变了才重画）。 */
    private var drawn: DrawableInputs? = null
    private var polygonsKey: Any? = null
    private var accuracyKey: String? = null

    /** 已执行的相机请求 nonce；null = 还没有。 */
    private var appliedNonce: Long? = null

    /** 相机请求等视口尺寸：尺寸没量到时先不消费（与 Android 的 `mapSize == 0` 同口径）。 */
    private var pendingCamera: CameraRequest? = null

    private val suppressor = SettleSuppressor()

    fun attach(input: BikeMapInput): MKMapView {
        currentInput = input
        val view = MKMapView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0))
        // 指北针用系统件（Android 侧 `BikeCompass` 是自绘的，iOS 没这个必要）
        view.showsCompass = true
        // 3D 楼块：与 Android 的 `showBuildings(true)` 对齐——校园尺度下用户靠楼体认方位
        view.showsBuildings = true
        // 蓝点由我们自己画（[BikeMapInput.userLat] 已经是 GCJ-02），不启用系统的定位蓝点：
        // 那条路要 CLLocationManager 授权与 Info.plist 文案，且颜色/精度圈都不受我们控制
        view.showsUserLocation = false
        val center = input.initialCenter
            ?: GcjPoint(BikeNearby.DEFAULT_CENTER_LAT, BikeNearby.DEFAULT_CENTER_LNG)
        val zoom = input.initialZoom ?: BikeNearby.DEFAULT_ZOOM
        view.setRegion(
            regionForZoom(center.lat, center.lng, widthPoints(view), zoom),
            animated = false,
        )
        // 代理要在**首次 setRegion 之后**挂：否则建图这一下也会走一轮 regionDidChange，
        // 把「进页面」当成一次用户停手（会立刻触发一次重查，而首屏查询本来就有一份）
        val delegate = MapDelegate(
            onSelect = { selectedView -> onSelect(selectedView) },
            onRegionWillChange = { onRegionWillChange() },
            onRegionDidChange = { onRegionDidChange() },
            viewFor = { key -> viewForMarker(key) },
            rendererFor = { overlay -> rendererFor(overlay) },
        )
        view.delegate = delegate
        mapView = view
        this.delegate = delegate
        return view
    }

    fun update(input: BikeMapInput) {
        currentInput = input
        val view = mapView ?: return
        applyCameraIfPossible(view, input.camera)
        val drawable = input.toDrawable()
        if (drawn != drawable) {
            drawn = drawable
            rebuild(drawable)
        }
    }

    fun detach() {
        val view = mapView
        if (view != null) {
            view.delegate = null
            // 标注 / 覆盖物随视图一起走，这里只把强引用放掉
            view.removeOverlays(polygons.toList())
            accuracyOverlay?.let { view.removeOverlay(it) }
            view.removeAnnotations(annotations.values.toList())
        }
        polygons.clear()
        polygonStyles.clear()
        accuracyOverlay = null
        accuracyStyle = null
        annotations.clear()
        annotationViews.clear()
        styles.clear()
        payloads.clear()
        drawn = null
        polygonsKey = null
        accuracyKey = null
        pendingCamera = null
        mapView = null
        delegate = null
        currentInput = null
    }

    // ---- 相机 ----

    /**
     * 执行一次相机请求（每个 nonce 一次）。
     *
     * 与 Android 同口径的两条前置：
     * - **视口尺寸还没量到就不消费**（Android 那边是 `mapSize == 0` 直接 return，靠 `mapSize`
     *   这个 Compose 状态重跑 LaunchedEffect；这里没有等价的状态，改成把请求留在
     *   [pendingCamera] 里，由第一次 `regionDidChange` 补上）；
     * - 静默窗按 [CameraRequest.animated] 分档，与 Android 同值。
     *
     * 为什么在 `update` 里直接做、不开 `LaunchedEffect(camera.nonce)`：Android 那个 effect 的
     * key 里有 `mapSize` 与 `mapLoaded` 两个**状态**，MapKit 这边只有一个「视图能不能用了」的
     * 判据（bounds 非 0）而没有对应的状态可当 key——真要开 effect，尺寸到时它也不会重跑。
     */
    private fun applyCameraIfPossible(view: MKMapView, request: CameraRequest?) {
        if (request == null || request.nonce == appliedNonce) return
        if (widthPoints(view) <= 0.0) {
            pendingCamera = request
            return
        }
        pendingCamera = null
        appliedNonce = request.nonce
        // 坐标就是 GCJ-02，直传（文件头第一节）
        suppressor.aim(request.lat, request.lng)
        suppressor.quietFor(if (request.animated) MOVE_QUIET_MS else RESTORE_QUIET_MS)
        val currentSpan = view.region.useContents { span.longitudeDelta }
        val targetSpan = request.zoom?.let { spanLngForZoom(widthPoints(view), it) }
            ?: currentSpan.takeIf { it > 0.0 }
            ?: spanLngForZoom(widthPoints(view), BikeNearby.DEFAULT_ZOOM)
        view.setRegion(
            regionOf(
                lat = request.lat,
                lng = request.lng,
                widthMeters = metersForSpanLng(request.lat, targetSpan),
            ),
            animated = request.animated,
        )
        currentInput?.onCameraApplied?.invoke(request.nonce)
    }

    // ---- 系统回调 ----

    private fun onRegionWillChange() {
        // 程序性移动（定位 / 回到校区 / 点分组）自己开了静默窗，窗内的变化不算用户动手；
        // 窗**外**的变化一定是用户动的（拖动 / 双指缩放 / 双击）——MapKit 不告诉我们「谁在动」，
        // 这条判据就是它能给的最准的一刀（差异见文件头第三节）
        if (suppressor.isQuiet()) return
        suppressor.clear()
        currentInput?.onUserPan?.invoke()
    }

    private fun onRegionDidChange() {
        val view = mapView ?: return
        val center = view.centerCoordinate.useContents { latitude to longitude }
        val span = view.region.useContents { span.latitudeDelta to span.longitudeDelta }
        // 视口实测**先于**静默判断（与 Android 同序）：它是几何量，与「这次停手是不是用户动作」
        // 无关；进页那次定位的程序性移动会被静默区吃掉，不先回报的话整段会话都按 0 算
        val halfDiagonal = BikeNearby.distanceMeters(
            center.first,
            center.second,
            center.first + span.first / 2,
            center.second + span.second / 2,
        )
        currentInput?.onViewportMeasured?.invoke(halfDiagonal)
        if (!suppressor.shouldSwallow(center.first, center.second)) {
            currentInput?.onCenterSettled?.invoke(
                center.first,
                center.second,
                zoomFromSpanLng(widthPoints(view), span.second),
            )
        }
        // 尺寸到位后补一次还没执行的相机请求（见 applyCameraIfPossible）
        val pending = pendingCamera
        if (pending != null) applyCameraIfPossible(view, pending)
    }

    private fun onSelect(view: MKAnnotationView) {
        val key = view.annotation?.title ?: return
        val payload = payloads[key] ?: return
        val input = currentInput ?: return
        when (payload) {
            is MarkerPayload.Cluster -> input.onClusterTap(payload.key)
            is MarkerPayload.Bike -> input.onBikeTap(payload.carNum)
            MarkerPayload.Ride -> input.onRideTap()
        }
        // 立刻取消选中：否则同一枚标记第二次点不会再来回调（MapKit 只在「未选中 → 选中」时通知）
        view.annotation?.let { mapView?.deselectAnnotation(it, animated = false) }
    }

    private fun viewForMarker(key: String?): MKAnnotationView? {
        if (key == null) return null
        val style = styles[key] ?: return null
        val annotation = annotations[key] ?: return null
        val view = MKMarkerAnnotationView(annotation = annotation, reuseIdentifier = REUSE_ID)
        applyStyle(view, style)
        annotationViews[key] = view
        return view
    }

    private fun rendererFor(overlay: MKOverlayProtocol): MKOverlayRenderer {
        val title = (overlay as? MKShape)?.title
        val style = when {
            title == ACCURACY_TITLE -> accuracyStyle
            title != null -> polygonStyles[title]
            else -> null
        }
        // 认不出样式的覆盖物只可能是我们自己没登记的，理论上不会出现；协议要求必须回一个
        // 渲染器，给一个不画东西的父类实例（比抛异常安全：代理里抛会直接把 App 带崩）
        val resolved = style ?: PolygonStyle(
            fill = 0x00000000,
            stroke = 0x00000000,
            lineWidth = 0.0,
        )
        if (overlay is MKCircle) {
            return MKCircleRenderer(circle = overlay).apply {
                fillColor = argbToUIColor(resolved.fill)
                strokeColor = argbToUIColor(resolved.stroke)
                lineWidth = resolved.lineWidth
            }
        }
        if (overlay is MKPolygon) {
            return MKPolygonRenderer(polygon = overlay).apply {
                fillColor = argbToUIColor(resolved.fill)
                strokeColor = argbToUIColor(resolved.stroke)
                lineWidth = resolved.lineWidth
            }
        }
        return MKOverlayRenderer(overlay = overlay)
    }

    // ---- 图层 ----

    private fun rebuild(inputs: DrawableInputs) {
        drawPolygons(inputs)
        drawAccuracy(inputs)
        drawMarkers(inputs)
    }

    private fun drawMarkers(inputs: DrawableInputs) {
        val view = mapView ?: return
        val plans = plansOf(inputs)
        val live = HashSet<String>(plans.size)
        plans.forEach { (key, plan) ->
            live += key
            styles[key] = plan.style
            payloads[key] = plan.payload
            val existing = annotations[key]
            if (existing == null) {
                val annotation = MKPointAnnotation()
                annotation.coordinate = coordinateOf(plan.lat, plan.lng)
                // title 兼作「这枚标注是谁」的标识：代理回调只给标注对象，靠它认回样式与载荷
                annotation.title = key
                annotations[key] = annotation
                view.addAnnotation(annotation)
            } else {
                if (!sameCoordinate(existing.coordinate, plan.lat, plan.lng)) {
                    existing.coordinate = coordinateOf(plan.lat, plan.lng)
                }
                annotationViews[key]?.let { applyStyle(it, plan.style) }
            }
        }
        // 这批输入里不再出现的，才真的删
        annotations.keys.filter { it !in live }.forEach { key ->
            annotations.remove(key)?.let { view.removeAnnotation(it) }
            annotationViews.remove(key)
            styles.remove(key)
            payloads.remove(key)
        }
    }

    /** 按 key 算这一轮要画哪些标注（顺序 = 叠放顺序：还车点 → 车 → 高亮 → 我的车 → 蓝点）。 */
    private fun plansOf(inputs: DrawableInputs): LinkedHashMap<String, MarkerPlan> {
        val colors = inputs.colors
        val plans = LinkedHashMap<String, MarkerPlan>()
        // 还车点「P」：**只是信息，不接点击**（2026-09-29 用户口径，两端同一条）
        inputs.zones.parkSpots.forEachIndexed { index, spot ->
            plans["spot:$index"] = MarkerPlan(
                lat = spot.lat,
                lng = spot.lng,
                style = MarkerStyle(
                    glyphText = "P",
                    glyphImageName = null,
                    tint = colors.spotBadge,
                    glyphTint = colors.spotGlyph,
                ),
                payload = null,
            )
        }
        if (inputs.byBike) {
            inputs.bikes.forEach { bike ->
                val selected = bike.carNum == inputs.selectedBikeCarNum
                plans["bike:${bike.carNum}"] = MarkerPlan(
                    lat = bike.lat,
                    lng = bike.lng,
                    style = MarkerStyle(
                        glyphText = null,
                        glyphImageName = "bicycle",
                        tint = if (selected) colors.selectedRing else bikeColor(bike, colors),
                        glyphTint = colors.label,
                    ),
                    payload = MarkerPayload.Bike(bike.carNum),
                )
            }
        } else {
            inputs.clusters.forEach { cluster ->
                val selected = cluster.key == inputs.selectedKey
                plans["cluster:${cluster.key}"] = MarkerPlan(
                    lat = cluster.lat,
                    lng = cluster.lng,
                    style = MarkerStyle(
                        // 数字才是用户要找的东西；气球里放得下两三位数
                        glyphText = cluster.bikes.size.toString(),
                        glyphImageName = null,
                        tint = if (selected) colors.selectedRing else clusterColor(cluster, colors),
                        glyphTint = colors.label,
                    ),
                    payload = MarkerPayload.Cluster(cluster.key),
                )
            }
        }
        // 识别高亮：标签走 chip 文字（Android 是双层定位圈 + chip，这里简化成带字的气球）
        inputs.highlightPoint?.let { point ->
            plans["hl"] = MarkerPlan(
                lat = point.lat,
                lng = point.lng,
                style = MarkerStyle(
                    glyphText = inputs.highlightLabel?.takeIf { it.isNotBlank() },
                    glyphImageName = null,
                    tint = colors.available,
                    glyphTint = colors.label,
                ),
                payload = null,
            )
        }
        // 当前用车
        inputs.ridePoint?.let { point ->
            plans["ride"] = MarkerPlan(
                lat = point.lat,
                lng = point.lng,
                style = MarkerStyle(
                    glyphText = null,
                    glyphImageName = "bicycle",
                    tint = colors.rideMarker,
                    glyphTint = colors.rideGlyph,
                ),
                payload = MarkerPayload.Ride,
            )
        }
        // 蓝点（朝向不画，见文件头第三节）
        inputs.userPoint?.let { point ->
            plans["user"] = MarkerPlan(
                lat = point.lat,
                lng = point.lng,
                style = MarkerStyle(
                    glyphText = null,
                    glyphImageName = "location.fill",
                    tint = colors.userDot,
                    glyphTint = colors.spotGlyph,
                ),
                payload = null,
            )
        }
        return plans
    }

    /**
     * 围栏 / 禁停区 / 还车点范围三层多边形。**输入没变就整段跳过**。
     *
     * 叠放顺序 = 加入顺序（MapKit 同等级下后加的画在上面），与 Android 的 zIndex 同序：
     * 围栏在最底层（背景信息），禁停区其次，还车点范围在车辆之下。
     */
    private fun drawPolygons(inputs: DrawableInputs) {
        val view = mapView ?: return
        val fence = inputs.showCampusFence && BikeNearby.CAMPUS_FENCE.size >= 3
        val key = PolygonsKey(
            fence = fence,
            nogo = inputs.zones.nogoZones,
            spots = inputs.zones.parkSpots.map { it.outline },
            colors = listOf(
                inputs.colors.fenceFill,
                inputs.colors.fenceStroke,
                inputs.colors.nogoFill,
                inputs.colors.nogoStroke,
                inputs.colors.spotFill,
                inputs.colors.spotStroke,
            ),
        )
        if (key == polygonsKey) return
        polygonsKey = key
        view.removeOverlays(polygons.toList())
        polygons.clear()
        polygonStyles.clear()
        if (fence) {
            addPolygon(FENCE_TITLE, BikeNearby.CAMPUS_FENCE, PolygonStyle(
                fill = inputs.colors.fenceFill,
                stroke = inputs.colors.fenceStroke,
                lineWidth = 2.0,
            ))
        }
        inputs.zones.nogoZones.forEachIndexed { index, outline ->
            if (outline.size >= 3) {
                addPolygon("nogo:$index", outline, PolygonStyle(
                    fill = inputs.colors.nogoFill,
                    stroke = inputs.colors.nogoStroke,
                    lineWidth = 1.0,
                ))
            }
        }
        inputs.zones.parkSpots.forEachIndexed { index, spot ->
            if (spot.outline.size >= 3) {
                addPolygon("spotarea:$index", spot.outline, PolygonStyle(
                    fill = inputs.colors.spotFill,
                    stroke = inputs.colors.spotStroke,
                    lineWidth = 1.5,
                ))
            }
        }
    }

    /**
     * 定位精度圈：半径就是米（MapKit 的 `MKCircle` 也按米算，不必像 osmdroid 那样折像素）。
     *
     * 填充比描边淡一档（与 Android 的 `withAlpha(colors.userHalo, 0.45f)` 同值）。
     */
    private fun drawAccuracy(inputs: DrawableInputs) {
        val view = mapView ?: return
        val point = inputs.userPoint
        val meters = inputs.userAccuracyMeters
        val key = if (point == null || meters <= 0f) {
            null
        } else {
            "${point.lat},${point.lng},$meters,${inputs.colors.userHalo}"
        }
        if (key == accuracyKey) return
        accuracyKey = key
        accuracyOverlay?.let { view.removeOverlay(it) }
        accuracyOverlay = null
        accuracyStyle = null
        if (point == null || key == null) return
        val circle = MKCircle.circleWithCenterCoordinate(
            coordinateOf(point.lat, point.lng),
            meters.toDouble(),
        ) ?: return
        circle.title = ACCURACY_TITLE
        val style = PolygonStyle(
            fill = withAlpha(inputs.colors.userHalo, 0.45),
            stroke = withAlpha(inputs.colors.userHalo, 1.1),
            lineWidth = 1.0,
        )
        accuracyStyle = style
        accuracyOverlay = circle
        view.addOverlay(circle)
    }

    private fun addPolygon(title: String, points: List<GcjPoint>, style: PolygonStyle) {
        val view = mapView ?: return
        val polygon = polygonOf(points) ?: return
        polygon.title = title
        polygonStyles[title] = style
        polygons += polygon
        view.addOverlay(polygon)
    }

    // ---- 小工具 ----

    private fun applyStyle(view: MKMarkerAnnotationView, style: MarkerStyle) {
        view.canShowCallout = false
        view.markerTintColor = argbToUIColor(style.tint)
        view.glyphTintColor = argbToUIColor(style.glyphTint)
        // `glyphText` 与 `glyphImage` 互斥（设一个会清掉另一个）。同一个 key 的造型从不在这两种
        // 之间切换（数字类永远是字、车与蓝点永远是图），所以按「先字后图」写就够，不必显式清空
        view.glyphText = style.glyphText
        view.glyphImage = style.glyphImageName?.let { UIImage.systemImageNamed(it) }
    }

    private fun sameCoordinate(
        coordinate: CValue<CLLocationCoordinate2D>,
        lat: Double,
        lng: Double,
    ): Boolean = coordinate.useContents {
        // 1e-7 度约合 1 厘米：比定位与接口的精度都小得多，够判「位置没变」
        abs(this.latitude - lat) < 1e-7 && abs(this.longitude - lng) < 1e-7
    }

    /** 多边形：`scopeArray` 的顶点直传（GCJ-02，不转换）。 */
    private fun polygonOf(points: List<GcjPoint>): MKPolygon? {
        if (points.size < 3) return null
        return memScoped {
            val coordinates = allocArray<CLLocationCoordinate2D>(points.size)
            points.forEachIndexed { index, point ->
                coordinates[index].latitude = point.lat
                coordinates[index].longitude = point.lng
            }
            MKPolygon.polygonWithCoordinates(coordinates, points.size.toULong())
        }
    }

    /** 多边形那一层的输入指纹（任一项变了才重画）。 */
    private data class PolygonsKey(
        val fence: Boolean,
        val nogo: List<List<GcjPoint>>,
        val spots: List<List<GcjPoint>>,
        val colors: List<Int>,
    )
}

/**
 * `MKMapView` 的代理：只做转发，判据全在 [MapKitHost] 里。
 *
 * `regionWillChangeAnimated` 与 `regionDidChangeAnimated` 在 Kotlin 里是**同一个签名**
 * （`MKMapView, Boolean`），靠 `@ObjCSignatureOverride` 才能并存——少了它报 Conflicting
 * overloads（见 `WebViewController.ios.kt` 里同一处坑的注释）。
 */
private class MapDelegate(
    private val onSelect: (MKAnnotationView) -> Unit,
    private val onRegionWillChange: () -> Unit,
    private val onRegionDidChange: () -> Unit,
    private val viewFor: (String?) -> MKAnnotationView?,
    private val rendererFor: (MKOverlayProtocol) -> MKOverlayRenderer,
) : NSObject(), MKMapViewDelegateProtocol {

    override fun mapView(
        mapView: MKMapView,
        viewForAnnotation: MKAnnotationProtocol,
    ): MKAnnotationView? = viewFor(viewForAnnotation.title)

    override fun mapView(mapView: MKMapView, didSelectAnnotationView: MKAnnotationView) {
        onSelect(didSelectAnnotationView)
    }

    @ObjCSignatureOverride
    override fun mapView(mapView: MKMapView, regionWillChangeAnimated: Boolean) {
        onRegionWillChange()
    }

    @ObjCSignatureOverride
    override fun mapView(mapView: MKMapView, regionDidChangeAnimated: Boolean) {
        onRegionDidChange()
    }

    override fun mapView(
        mapView: MKMapView,
        rendererForOverlay: MKOverlayProtocol,
    ): MKOverlayRenderer = rendererFor(rendererForOverlay)
}

/** [BikeMapInput] → 图层输入的投影（只保留「画上去的东西」）。 */
private fun BikeMapInput.toDrawable(): DrawableInputs = DrawableInputs(
    colors = colors,
    clusters = clusters,
    selectedKey = selectedKey,
    bikes = bikes,
    byBike = byBike,
    selectedBikeCarNum = selectedBikeCarNum,
    userPoint = if (userLat != null && userLng != null) GcjPoint(userLat, userLng) else null,
    userAccuracyMeters = userAccuracyMeters,
    ridePoint = if (rideLat != null && rideLng != null) GcjPoint(rideLat, rideLng) else null,
    highlightPoint = if (highlightLat != null && highlightLng != null) {
        GcjPoint(highlightLat, highlightLng)
    } else {
        null
    },
    highlightLabel = highlightLabel,
    zones = zones,
    showCampusFence = showCampusFence,
)

/** 簇里只要有一辆能骑就画「可用」，否则退到「电量低」，再退到灰（与 Android 同口径）。 */
private fun clusterColor(cluster: BikeCluster, colors: BikeMarkerColors): Int = when {
    cluster.bikes.any { it.available } -> colors.available
    cluster.bikes.any { it.status == BikeStatus.LowBattery } -> colors.lowBattery
    else -> colors.unavailable
}

/** 单车颜色（与 Android 同口径）。 */
private fun bikeColor(bike: NearbyBike, colors: BikeMarkerColors): Int = when (bike.status) {
    BikeStatus.Available -> colors.available
    BikeStatus.LowBattery -> colors.lowBattery
    BikeStatus.Offline, BikeStatus.Disabled -> colors.unavailable
}
