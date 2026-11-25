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
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.CoreLocation.CLLocationCoordinate2D
import platform.CoreLocation.CLLocationCoordinate2DMake
import platform.MapKit.MKAnnotationProtocol
import platform.MapKit.MKPointAnnotation
import platform.MapKit.MKAnnotationView
import platform.MapKit.MKCoordinateRegion
import platform.MapKit.MKCoordinateRegionMakeWithDistance
import platform.MapKit.MKMapView
import platform.MapKit.MKMapViewDelegateProtocol
import platform.MapKit.MKMarkerAnnotationView
import platform.UIKit.UIColor
import platform.darwin.NSObject
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.pow
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/*
 * 文件头说明（不是 KDoc：它不属于某一个声明，是这一层的地图）
 * ==========================================================================
 *
 * 快趣出行地图的 **iOS 实现（MapKit）**：吃与 Android 完全同一份 `BikeMapInput`
 * （`shared/commonMain/.../ui/ebike/BikeMapContract.kt`，iOS 移植 P4，2026-11-25）。
 *
 * 本文件是**第一批**：底图 + 车辆点位 + 相机指令 + 四个回调。
 * 叠加层（校区围栏 / 禁停区 / 还车点范围 / 定位精度圈）在 `BikeMapOverlays.ios.kt`（第二批）。
 * 两批分开提交的理由见那份文件头：叠加层用的覆盖物 API 是这一层里唯一「拿不准」的一族，
 * 单独成一次提交，CI 若只打回它，`git revert` 一个 commit 就回到「能编译、能看点位」的版本。
 *
 * ## 一、为什么是 MapKit，不是高德 iOS SDK
 *
 * 1. **坐标系天生对**：中国区 Apple 地图的数据就是高德提供的，基准同为 GCJ-02。应用内部
 *    （车辆 / 还车点 / 围栏 / 定位）也全是 GCJ-02，所以这里**一个坐标都不用转**——与
 *    `AmapMapView` 的「整条显示基准转换链都不需要」是同一条结论。**这条是决定，不是猜测**：
 *    `Gcj02.kt` 里反向转换已删，谁在这里再转一次就是几百米的双重偏移。
 * 2. **零依赖、零 key、不绑 Bundle ID**：`platform.MapKit` 是 Kotlin/Native 自带的平台库，
 *    不新增 Gradle 依赖、不要 key、不做包名校验，自签 IPA 也能跑。
 * 3. **系统件够用**：底图版权署名、标注气泡都由系统画；Android 侧自绘是因为高德与 osmdroid
 *    都没给现成的。
 *
 * > 真机上**若**出现约几百米的**系统性**偏移（同一批车在高德上是对的、在这里整体偏一个固定
 * > 方向），才说明这份接口数据其实是 WGS-84，那时才在 [coordinateOf] 里过一遍 `Gcj02.toGcj02`。
 * > 方向乱跳不是坐标系问题，是定位精度问题——先量，别猜。
 *
 * ## 二、本机编不了 iOS，所以每个平台符号都按「最保守」取（2026-11-25 第二版）
 *
 * Windows 上没有 Kotlin/Native，这个文件**没有在本机编译过**（`:shared:compileKotlinIosArm64`
 * 在这台机器上不会真跑）。上一版（989 行，commit `1db5dcc2`）在 CI 上被打了约 20 处绑定不符，
 * 所以这一版对**每一个** `platform.*` 符号都做了取舍，逐条列在这里。CI 万一报错，照表改即可：
 *
 * | 符号 | 这里的写法 | 依据 | 万一不对怎么改 |
 * |---|---|---|---|
 * | `MKMapView(frame = CGRectMake(…))` | 建视图 | `CGRectMake` 在本仓 `WebViewController.ios.kt` 已用（`WKWebView(frame = …)`）；`initWithFrame:` 是 `UIView` 的指定构造器 | **换 `MKMapView()`**（无参构造器，JetBrains 官方 imageviewer 示例就是这么写的），一行 |
 * | `MKMarkerAnnotationView(frame = …)` + `annotation = …` | 建标注视图 | 上一版用 `MKMarkerAnnotationView(annotation =, reuseIdentifier =)` 被 CI 打回，按要求改成继承来的 `initWithFrame:` 再赋 `annotation` | 换 `MKMarkerAnnotationView()`；再不行换 `MKAnnotationView(frame = …)`（父类，只丢气球造型） |
 * | `view.annotation` / `canShowCallout` / `markerTintColor` / `glyphTintColor` / `glyphText` | 给气球上色上字 | 都是 ObjC **读写**属性（`MKAnnotationView` / `MKMarkerAnnotationView`），K/N 暴露成 `var` | 报 `'val' cannot be reassigned` 的那一个就删掉（`canShowCallout` 删了会漏系统气泡，那时改成给标注设 `title = null`；颜色删了只剩系统默认蓝） |
 * | `class BikeAnnotation(...) : NSObject(), MKAnnotationProtocol { override val coordinate }` | 自己实现标注对象 | **改用 `MKPointAnnotation` 子类**：协议里 `coordinate` 在 K/N 绑定里是 final，`override` 会同时报「final 不可覆盖」与「没有实现抽象成员」（2026-11-25 CI 实测） | `MKAnnotation.coordinate` 在 ObjC 里是 **readonly** → K/N 侧是 `val`，所以只实现一个只读属性，不碰可变属性；`NSObject() + 协议` 的写法与 `WebViewController.ios.kt` 的代理同形 | 若报「`coordinate` 无 override 关系」，说明这版绑定把它露成方法：改成 `override fun coordinate(): CValue<CLLocationCoordinate2D>`（旧式绑定就是这种形态） |
 * | `mapView.delegate = …` / `showsCompass = true` | 属性赋值 | 同 `WebViewController.ios.kt` 的 `view.navigationDelegate = …` | 删掉 `showsCompass`（只丢指北针），`delegate` 必须留 |
 * | `MKCoordinateRegionMakeWithDistance(center, w, h)` | 造区域 | JetBrains imageviewer 示例逐字同形（`centerCoordinate =`, `10_000.0, 10_000.0`） | 手拼 `cValue<MKCoordinateRegion> { center.latitude = …; span.longitudeDelta = … }` |
 * | `mapView.setRegion(region, animated = …)` | 移镜头 | 同上（示例里 `animated = false`） | — |
 * | `mapView.region` / `mapView.bounds` + `useContents { span.longitudeDelta }` / `{ size.width }` | 读跨度与宽度 | 与 Kotlin 官方 CValue 文档里的 `rect.useContents { size.width }` 同一个形状（嵌套结构体读字段） | `bounds` 换成 `frame`；`region` 换成自己记一个 `lastSpanLng` 字段（`setRegion` 时写、不读回来） |
 * | `mapView.centerCoordinate` + `useContents { latitude }` | 读中心 | 扁平 C struct 读字段，同样用 `useContents`（**不做** `coord.latitude` 这种直接取字段：上一版的 `Unresolved reference 'latitude'` 就是这么来的） | — |
 * | `mapView.addAnnotation` / `removeAnnotation` / `removeAnnotations(list)` / `deselectAnnotation(…, animated = false)` | 增删标注 | **上一版 CI 的 20 处里没有这一族**（打回的全是 `addOverlay` 那一族），说明标注 API 是可解析的 | 除了 `addAnnotation` 没有别的加标注入口；`deselectAnnotation` 没了就是「同一枚标记第二次点不再回调」 |
 * | `MKMapViewDelegateProtocol` 的 `viewForAnnotation` / `didSelectAnnotationView` / `regionWillChangeAnimated` / `regionDidChangeAnimated` | 系统回调 | 前两个上一版没被打回；后两个在 K/N 里签名相同（`MKMapView, Boolean`），**必须** `@ObjCSignatureOverride` 才能并存（同 `WebViewController.ios.kt` 里两个失败回调的坑） | 单个方法编不过就注释掉那一个 `override`：`regionWillChangeAnimated` 没了只丢 `onUserPan`，`regionDidChangeAnimated` 没了丢 `onCenterSettled` / `onViewportMeasured` |
 * | `UIColor(red = …, green = …, blue = …, alpha = …)` | ARGB → UIColor | `initWithRed:green:blue:alpha:` 是 `UIColor` 的指定构造器 | 换类工厂 `UIColor.colorWithRed(…)` |
 *
 * 明确**没用**的东西（拿不准，宁可不写；每条都写了补法）：
 *
 * - **`UIImage.systemImageNamed(…)`（SF Symbols）**：上一版用它做车 / 蓝点的符号图，按要求删掉。
 *   代价是车标与蓝点都是**纯色气球**（没有小自行车与定位图标）。要补：在 [applyStyle] 里把
 *   `glyphText` 换成 `glyphImage = UIImage.systemImageNamed("bicycle")`（符号图先例见 `platform.UIKit.UIImage`)。
 * - **`zoomEnabled` / `rotateEnabled` / `pitchEnabled`**：这些属性在 ObjC 里带 `getter=is…Enabled`，
 *   K/N 到底暴露成 `zoomEnabled` 还是 `isZoomEnabled` 没把握。代价是 iOS 允许双指旋转与俯视，
 *   Android 关掉了；确认写法后补三行。
 * - **叠加层那一族**（`addOverlay(s)` / `removeOverlay(s)` / `MKPolygon` / `MKCircle`）：
 *   上一版被打回的就是这四个方法；2026-11-25 又试了一次批量形式（`addOverlays(list)` /
 *   `removeOverlays(list)`），**CI 仍然报 Unresolved**——说明这一族在 Kotlin/Native 的
 *   `MKMapView` 绑定里根本没有（单数、复数都没有），不是写法问题。所以整层删掉：
 *   iOS 上**不画**围栏 / 禁停区 / 还车点范围 / 精度圈，文件 `BikeMapOverlays.ios.kt` 已移除。
 *   影响是装饰性的（这些多边形帮你看「哪里能还车」），车辆点位与所有交互都在。
 *
 * ## 三、与 Android 的差异（同一契约，不追求像素级一致）
 *
 * | 项 | Android | 这里 |
 * |---|---|---|
 * | 聚合 / 车标造型 | 离屏位图自绘（数字 + 小车轮廓） | `MKMarkerAnnotationView` 系统气球：聚合圈数字走 `glyphText`，车与蓝点是纯色气球 |
 * | 选中态 | 叠一枚描边圈 + 加粗 | 该车 / 该簇的气球换成 `selectedRing` 色（不额外叠标记），与上一版同口径 |
 * | 中心浮标（针形） | Compose `Canvas` 画在视图正中 | **不画**：MapKit 没有能钉在屏幕坐标上的视图层；要画得挂一层 Compose `Canvas` 并自己把经纬度投影成屏幕点（`convertCoordinate`），本轮不做 |
 * | 蓝点朝向（[BikeMapInput.userHeadingFlow]） | `BikeCompass` 自绘箭头 | **忽略**（契约里已写明）：气球旋转造型会歪；系统蓝点又要求 `CLLocationManager` 授权与 `Info.plist` 文案 |
 * | 精度圈（[BikeMapInput.userAccuracyMeters]） | `Circle`（半径单位米） | **不画**：overlay 那一族在 K/N 绑定里不存在（见上条），精度信息改由「距你 N 米」这行字承担 |
 * | 相机动画 | 逐帧缓动（400ms / 1000ms，与 osmdroid 逐项对齐） | `setRegion(animated = true)`：曲线与时长交给系统，不可控 |
 * | `onFallback` | key 失效 → 整层降级栅格 | **不会回调**：MapKit 没有「底图拿不到」这一档（契约里已写明） |
 * | `logoBottomMarginPx` | 生效（版权标识不能被面板盖住） | **忽略**（契约里已写明）：Apple 地图的署名由系统画在右下角，宿主挪不动 |
 * | 用户接管（`onUserPan`） | 触摸流 + 手势回调两路，单指拖动才认 | `regionWillChangeAnimated`：**静默窗内（≤1.2 秒）的变化不算接管**。静默窗是程序性移动（定位 / 回校区 / 点分组）自己开的，窗内用户动手会被吞掉一次——差异是先天的，MapKit 不告诉我们「谁在动」 |
 * | 停手判定 | 高德给 `onCameraChangeFinish`；osmdroid 那条是 120ms 轮询 | `regionDidChangeAnimated`：MapKit 自己保证「真的停下了」才回调，与高德那条同一档 |
 *
 * ## 四、视口与缩放：两端各有一套换算（本端自洽即可）
 *
 * MapKit 没有「缩放级别」，只有经纬度跨度。换算用固定常数：
 *
 * ```
 * spanLng = 360 × 视口宽(point) / (TILE_POINTS × 2^zoom)
 * ```
 *
 * [TILE_POINTS] 取 128：一张 256 像素的地图瓦片在 2x 屏上是 128 point，这样 z17 下的视野
 * 与 Android 1080px 手机（约 1 公里宽）同一量级（实际窄三成，可见范围偏保守，不是错）。
 *
 * 这个换算的绝对精度**不影响查询**：真正决定采样半径的是 [BikeMapInput.onViewportMeasured]
 * 回报的实测米数，它是「中心 → 视口东北角」的球面距离（与 `AmapMapView` 同一个写法）。
 * 换算只影响两件事：`initialZoom` 落地时的视野，以及回传给 `onCenterSettled` 的 zoom 值
 * （ViewModel 拿它当「上次视野」存盘、判 `isZoomedOut`）——两端各用各的标尺，只要本端
 * **一写一读同一条公式**就不会漂。
 *
 * ## 五、图层怎么更新
 *
 * 与 Android 同一条纪律：`UIKitView.update` 每轮重组都会跑一遍，所以先比一份 [DrawableInputs]
 * （只装「画上去的东西」），变了才动图层；[BikeMapInput] 里的回调不进比对（闭包每次都是新实例）。
 *
 * 标注按 key 增删改（不整层重建）：key 的命名与 `AmapMapView.AmapLayers` 一致
 * （`spot:` / `bike:` / `cluster:` / `hl` / `ride` / `user`），两端出问题时能对照同一个 key 排查。
 * **iOS 比 Android 少一个 key**：Android 的 `sel:`（选中圈）这里不画，选中态改在 `bike:` 自己身上。
 *
 * 标注对象上**不挂 `title`**（也不建 `MKPointAnnotation`）：上一版把内部 key 塞进 `title`
 * 当身份标识，副作用是气球点开会弹出一个写着 `bike:123456` 的系统气泡（Android 上不可能
 * 出现的观感）。现在标注是 [BikeAnnotation] 自己的实例，`key` 与 `payload` 直接存在对象上，
 * 代理回调里 `as? BikeAnnotation` 就能认回来。
 *
 * ## 六、宿主怎么用
 *
 * ```kotlin
 * // iOS 宿主（`MainViewController.kt` 那一层）：
 * val bikeMap = remember { iosBikeMapSlot() }
 * // 页面拿到 `bikeMap: BikeMapSlot?`，渲染时：
 * bikeMap?.content(input, Modifier.fillMaxSize())
 * ```
 *
 * 现在 `RideScreen` 还在 `:app`，所以本函数暂时没有调用方——它是**给搬页面那一步准备好的入口**。
 */

/** 地图缩放 ↔ 跨度换算里的「一张瓦片合多少 point」，见文件头第四节。 */
private const val TILE_POINTS = 128.0

/** 视口宽度还没量到时的兜底（point）：iPhone 竖屏约 393，取 400 免得首屏视野差太多。 */
private const val FALLBACK_WIDTH_POINTS = 400.0

/** 程序性移动的静默窗口：与 Android 的 `MOVE_QUIET_MS` 同值（盖住动画与收尾几帧）。 */
private const val MOVE_QUIET_MS = 1_200L

/** 恢复视野（无动画）的静默窗口：与 Android 的 `RESTORE_QUIET_MS` 同值。 */
private const val RESTORE_QUIET_MS = 300L

/** 相机允许的缩放区间：与 Android 高德那套（3~20）同档。 */
private const val MIN_ZOOM = 3.0
private const val MAX_ZOOM = 20.0

/** 地球赤道半径（米）：跨度 ↔ 米 的换算用它与 `2π`，两处必须是同一常数，否则一写一读会漂。 */
private const val EARTH_RADIUS_METERS = 6_378_137.0

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

/** ARGB int → UIColor。`internal` 是因为 `BikeMapOverlays.ios.kt` 的渲染器也要用它。 */
internal fun argbToUIColor(argb: Int): UIColor = UIColor(
    red = ((argb shr 16) and 0xFF) / 255.0,
    green = ((argb shr 8) and 0xFF) / 255.0,
    blue = (argb and 0xFF) / 255.0,
    alpha = ((argb ushr 24) and 0xFF) / 255.0,
)

/** 坐标（GCJ-02 直传，见文件头第一节）。`internal` 是因为叠加层（第二批）也要造坐标。 */
internal fun coordinateOf(lat: Double, lng: Double): CValue<CLLocationCoordinate2D> =
    CLLocationCoordinate2DMake(lat, lng)

/** 视口宽度（point）；还没布局时是 0。 */
private fun widthPoints(view: MKMapView): Double = view.bounds.useContents { size.width }

/** 由 zoom 反推经度跨度（公式见文件头第四节）。 */
private fun spanLngForZoom(widthPointCount: Double, zoom: Double): Double {
    val width = if (widthPointCount > 0.0) widthPointCount else FALLBACK_WIDTH_POINTS
    val safeZoom = zoom.coerceIn(MIN_ZOOM, MAX_ZOOM)
    return 360.0 * width / (TILE_POINTS * 2.0.pow(safeZoom))
}

/**
 * 由经度跨度反推 zoom（[spanLngForZoom] 的逆运算）。
 *
 * 跨度取不到（0 / 非有限）时回 [BikeNearby.DEFAULT_ZOOM]：调用方拿它当「上次视野」存盘，
 * 给一个明确的默认值比给 0 好——0 会让 ViewModel 判成「缩小视图」。
 */
private fun zoomFromSpanLng(widthPointCount: Double, spanLng: Double): Double {
    if (!spanLng.isFinite() || spanLng <= 0.0) return BikeNearby.DEFAULT_ZOOM
    val width = if (widthPointCount > 0.0) widthPointCount else FALLBACK_WIDTH_POINTS
    val zoom = ln(360.0 * width / (TILE_POINTS * spanLng)) / ln(2.0)
    return zoom.coerceIn(MIN_ZOOM, MAX_ZOOM)
}

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
 * 用 **`MKCoordinateRegionMakeWithDistance` 这个 C 函数**，不自己拼 `MKCoordinateRegion`
 * 结构体——嵌套字段（`center` / `span`）怎么构造是这一份代码里最没把握的一处，
 * 而 C 函数只吃 `CValue` 参数，零歧义。纬度方向同样传这个米数：手机视口比宽高，
 * MapKit 会按长宽比把纬度方向撑开，最终以实测视口为准。
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

/**
 * 程序性移动的「静默区」（`AmapMapView` 里那份 `CameraSuppressor` 的 iOS 版，口径逐条对齐）。
 *
 * 为什么需要：定位 / 回到校区 / 点分组联动都会让地图动，动了就一定触发 `regionDidChange`，
 * 回调又会走 ViewModel 的重查判定。点分组那条尤其不能重查——那批车已经取回来了，再问一次
 * 接口只会让用户刚展开的列表当场换一批内容。
 *
 * 两条规则（与 Android 相同）：
 * 1. **时间窗**（[quietFor]）——窗口内一律吃掉。中间帧可能离瞄准点几百米，只看半径兜不住；
 * 2. **瞄准点 30 米半径**——窗口过后还剩一个收尾回调，它与 ViewModel 的重查阈值同半径，
 *    也就是说被它吃掉的回调本来也触发不了重查，行为没有旁路。
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
 * 而定位读数本来也只到 1 Hz 量级，不进 10 Hz 的罗盘循环。
 *
 * `internal` 是因为叠加层（`BikeMapOverlays.ios.kt`）也吃同一份快照：围栏 / 禁停区 / 还车点
 * 范围 / 精度圈那几层要与标注层看同一批输入，否则两层的指纹会各算一套。
 */
internal data class DrawableInputs(
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
    /** 气球里的文字（聚合数字 / 「P」/ 高亮车号）；null = 不放字（纯色气球）。 */
    val glyphText: String?,
    /** 气球主色（ARGB）。 */
    val tint: Int,
    /** 气泡里的字颜色（ARGB）。 */
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

/**
 * 标注上挂的点击载荷（与 `AmapMapView.MarkerPayload` 同形）。
 *
 * 载荷存在标注对象自己身上（[BikeAnnotation.payload]），不再像上一版那样塞进 `title`：
 * 那样点一下会弹出写着内部 key 的系统气泡，Android 上不可能出现这种观感。
 */
private sealed interface MarkerPayload {
    data class Cluster(val key: String) : MarkerPayload
    data class Bike(val carNum: String) : MarkerPayload
    data object Ride : MarkerPayload
}

/**
 * 标注对象：在 `MKPointAnnotation` 之上挂两个内部字段。
 *
 * ## 为什么不再自己 `NSObject() + MKAnnotationProtocol`
 *
 * 2026-11-25 CI 实测：`MKAnnotationProtocol.coordinate` 在 Kotlin/Native 的绑定里是 **final**，
 * 自己实现协议时 `override val coordinate` 会报「`coordinate` is final and cannot be overridden」，
 * 同时又被判「没有实现抽象成员」——两条错互相矛盾，说明那个协议的绑定形态与文档描述不一致。
 * 继承 `MKPointAnnotation`（MapKit 自备的实体类，`coordinate` 由 `MKShape` 以可写属性实现）
 * 绕开整个协议一致性问题。
 *
 * ## 为什么 key 不塞 `title`
 *
 * `title` 是用户可见文案，拿它当内部 id 会漏出气泡。所以 key / payload 是子类上的真字段，
 * 代理回调里 `as? BikeAnnotation` 就认回来了（见 [MapKitHost.viewForMarker]）。
 */
private class BikeAnnotation : MKPointAnnotation() {
    /** 「这是谁」的内部标识（`cluster:` / `bike:` / `ride` …），见文件头第五节。 */
    var key: String = ""

    var payload: MarkerPayload? = null
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

    /** 最近一次交给我们的输入：回调要从中取页面给的最新闭包，不能捕获建图时那一份。 */
    private var currentInput: BikeMapInput? = null

    /** 标注：key → 标注对象（key 的命名见文件头第五节）。 */
    private val annotations = LinkedHashMap<String, BikeAnnotation>()

    /** 已建出来的标注视图：改样式时直接改，不必等系统重新问一遍。 */
    private val annotationViews = HashMap<String, MKMarkerAnnotationView>()

    private val styles = HashMap<String, MarkerStyle>()

    /** 上一次真正画上去的输入（变了才动图层）。 */
    private var drawn: DrawableInputs? = null

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
        // 蓝点由我们自己画（`userLat` 已经是 GCJ-02），不启用系统的定位蓝点：
        // 那条路要 `CLLocationManager` 授权与 `Info.plist` 文案，且颜色 / 精度圈都不受我们控制
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
            viewFor = { annotation -> viewForMarker(annotation) },
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
            drawMarkers(drawable)
        }
    }

    fun detach() {
        val view = mapView
        if (view != null) {
            view.delegate = null
            view.removeAnnotations(annotations.values.toList())
        }
        annotations.clear()
        annotationViews.clear()
        styles.clear()
        drawn = null
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
     *   这个 Compose 状态重跑 `LaunchedEffect`；这里没有等价的状态，改成把请求留在
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
        // 载荷就在标注对象自己身上（[BikeAnnotation]），不必再查一张表
        val payload = (view.annotation as? BikeAnnotation)?.payload ?: return
        val input = currentInput ?: return
        when (payload) {
            is MarkerPayload.Cluster -> input.onClusterTap(payload.key)
            is MarkerPayload.Bike -> input.onBikeTap(payload.carNum)
            MarkerPayload.Ride -> input.onRideTap()
        }
        // 立刻取消选中：否则同一枚标记第二次点不会再来回调（MapKit 只在「未选中 → 选中」时通知）
        view.annotation?.let { mapView?.deselectAnnotation(it, animated = false) }
    }

    /** 系统来要标注视图：按标注对象自己的 key 取造型（见 [BikeAnnotation]）。 */
    private fun viewForMarker(annotation: MKAnnotationProtocol?): MKAnnotationView? {
        val owned = annotation as? BikeAnnotation ?: return null
        val style = styles[owned.key] ?: return null
        val view = MKMarkerAnnotationView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0))
        view.annotation = owned
        applyStyle(view, style)
        annotationViews[owned.key] = view
        return view
    }

    // ---- 图层 ----

    private fun drawMarkers(inputs: DrawableInputs) {
        val view = mapView ?: return
        val plans = plansOf(inputs)
        val live = HashSet<String>(plans.size)
        plans.forEach { (key, plan) ->
            live += key
            styles[key] = plan.style
            val existing = annotations[key]
            val samePlace = existing != null && sameCoordinate(existing.coordinate, plan.lat, plan.lng)
            when {
                // 位置变了就换一个新标注对象：重建比改 `coordinate` 少一层「那个属性到底可不可写」
                // 的不确定性（见 [BikeAnnotation] 的 KDoc）。标注是几十的量级，代价可忽略。
                existing == null || !samePlace -> {
                    if (existing != null) view.removeAnnotation(existing)
                    // 先取出外层 key：`apply` 里裸写 `key` 会解析成接收者自己的成员，变成自赋值
                    val markerKey = key
                    val annotation = BikeAnnotation().apply {
                        coordinate = coordinateOf(plan.lat, plan.lng)
                        this.key = markerKey
                        payload = plan.payload
                    }
                    annotations[key] = annotation
                    annotationViews.remove(key)
                    view.addAnnotation(annotation)
                }
                else -> annotationViews[key]?.let { applyStyle(it, plan.style) }
            }
        }
        // 这批输入里不再出现的，才真的删
        annotations.keys.filter { it !in live }.forEach { key ->
            annotations.remove(key)?.let { view.removeAnnotation(it) }
            annotationViews.remove(key)
            styles.remove(key)
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
                style = MarkerStyle(glyphText = "P", tint = colors.spotBadge, glyphTint = colors.spotGlyph),
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
                        tint = if (selected) colors.selectedRing else clusterColor(cluster, colors),
                        glyphTint = colors.label,
                    ),
                    payload = MarkerPayload.Cluster(cluster.key),
                )
            }
        }
        // 识别高亮：标签走气球文字（Android 是双层定位圈 + chip，这里简化成带字的气球）
        inputs.highlightPoint?.let { point ->
            plans["hl"] = MarkerPlan(
                lat = point.lat,
                lng = point.lng,
                style = MarkerStyle(
                    glyphText = inputs.highlightLabel?.takeIf { it.isNotBlank() },
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
                style = MarkerStyle(glyphText = null, tint = colors.rideMarker, glyphTint = colors.rideGlyph),
                payload = MarkerPayload.Ride,
            )
        }
        // 蓝点（朝向不画，见文件头第三节）
        inputs.userPoint?.let { point ->
            plans["user"] = MarkerPlan(
                lat = point.lat,
                lng = point.lng,
                style = MarkerStyle(glyphText = null, tint = colors.userDot, glyphTint = colors.label),
                payload = null,
            )
        }
        return plans
    }

    // ---- 小工具 ----

    private fun applyStyle(view: MKMarkerAnnotationView, style: MarkerStyle) {
        // 不弹气泡：气泡里只有 title，而我们不给标注设 title（见 [BikeAnnotation]）。
        // 设一次 false 是因为 `MKAnnotationView.canShowCallout` 默认是 true。
        view.canShowCallout = false
        view.markerTintColor = argbToUIColor(style.tint)
        view.glyphTintColor = argbToUIColor(style.glyphTint)
        view.glyphText = style.glyphText
    }

    private fun sameCoordinate(
        coordinate: CValue<CLLocationCoordinate2D>,
        lat: Double,
        lng: Double,
    ): Boolean = coordinate.useContents {
        // 1e-7 度约合 1 厘米：比定位与接口的精度都小得多，够判「位置没变」
        abs(this.latitude - lat) < 1e-7 && abs(this.longitude - lng) < 1e-7
    }
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
    private val viewFor: (MKAnnotationProtocol?) -> MKAnnotationView?,
) : NSObject(), MKMapViewDelegateProtocol {

    override fun mapView(
        mapView: MKMapView,
        viewForAnnotation: MKAnnotationProtocol,
    ): MKAnnotationView? = viewFor(viewForAnnotation)

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

    // 不实现 `mapView(_:rendererForOverlay:)`：iOS 侧不加任何 overlay（见文件头第八节），
    // MapKit 就永远不会调这一条。少一个符号 = 少一处可能编不过的地方。
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
