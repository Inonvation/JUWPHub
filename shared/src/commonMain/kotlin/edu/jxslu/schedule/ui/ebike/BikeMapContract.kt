package edu.jxslu.schedule.ui.ebike

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import edu.jxslu.schedule.domain.BikeCluster
import edu.jxslu.schedule.domain.GcjPoint
import edu.jxslu.schedule.domain.KvcxZones
import edu.jxslu.schedule.domain.NearbyBike
import edu.jxslu.schedule.ui.theme.semanticColors
import kotlinx.coroutines.flow.StateFlow

/**
 * 共享单车地图层的**参数契约**（iOS 移植 P4，2026-11-25）。
 *
 * ## 一、为什么它在 `commonMain`
 *
 * 地图是快趣出行最重的一块，也是最不可能「一套代码两端跑」的一块：底图是原生视图，
 * Android 是高德 SDK / osmdroid，iOS 是 MapKit。但**页面与地图之间交换的东西**全是纯数据
 * ——车辆、聚合点、还车点、相机指令、回调。把这层数据契约抬进共享层之后：
 *
 * ```
 * 页面（ViewModel）→ BikeMapInput → ┬ AmapMapView（:app，官方矢量）
 *                                   ├ OsmMapView （:app，栅格回退）
 *                                   └ IosBikeMap （shared/iosMain，MapKit）
 * ```
 *
 * 三个实现吃**同一个类型**，页面不需要知道底下是谁。Android 侧的选择器仍是
 * `app/.../ui/ebike/BikeMapHost.kt`（它同时管 `sdkReady` / `unavailable` / `onSdkUnavailable`
 * 那套回退逻辑，那是 Android 独有的）。
 *
 * ## 二、`BikeMapSlot` 与 `null` 的语义
 *
 * 槽位类型是 [BikeMapSlot]。**`null` = 本平台确实没有地图能力**，页面据此只渲染底部的
 * 车辆列表、不渲染地图（`design/ios-port.md` §11.2 ④：给一个点了没反应的入口比没有更糟）。
 * 现在两端都填得上这个槽（Android 高德 / osmdroid，iOS MapKit），`null` 留给将来某个
 * 没有地图实现的宿主——不要为了让签名好看而传一个空实现。
 *
 * ## 三、坐标基准：直接传，**不转换**
 *
 * 应用内部一律 GCJ-02（`Gcj02.kt` 的 KDoc）：运营方给的车辆 / 还车点 / 围栏是 GCJ-02，
 * 手机定位在进应用时已经转成 GCJ-02。三条底图也都是 GCJ-02：
 * - 高德官方矢量（Android 主路径）；
 * - 高德栅格瓦片（Android 回退路径）；
 * - **中国区 Apple 地图（iOS）—— Apple 在中国大陆的底图数据即高德提供，坐标系同为 GCJ-02**。
 *
 * 所以 [BikeMapInput] 里的坐标**原样交给底图**，谁都不许再转一次（转两次就是几百米的
 * 双重偏移，见 `Gcj02.kt` 里「反向转换已删」那段）。
 *
 * > 万一 iOS 真机上看到约几百米的**系统性**偏移（不是零星的定位抖动）：说明这份接口数据
 * > 其实是 WGS-84，那时才在 `BikeMapView.ios.kt` 里过一遍 `Gcj02.toGcj02`。**先量，
 * > 不要凭感觉加**——同一批车在高德上是对的、在 MapKit 上偏一个固定方向才说明是坐标系问题，
 * > 方向乱跳是定位精度问题。
 *
 * ## 四、平台差异（同一契约，不保证像素级一致）
 *
 * | 项 | Android | iOS |
 * |---|---|---|
 * | 底图 | 高德矢量 / 高德栅格 | MapKit（系统件） |
 * | 指北针 | `BikeCompass` 自绘（磁力计 + 屏幕朝向） | `MKMapView.showsCompass` 系统件 |
 * | 相机动画曲线 | 逐帧缓动（400ms 带缩放 / 1000ms 只平移，照抄 osmdroid） | 系统 `setRegion(animated = true)`，曲线与时长不可控 |
 * | 聚合 / 车标 / P 标的**造型** | 离屏位图自绘（`AmapMarkerBitmaps` / `BikeMarkerOverlay`） | 系统气球（`MKMarkerAnnotationView`），靠 `BikeMarkerColors` 上色 |
 * | [BikeMapInput.logoBottomMarginPx] | 生效（高德版权标识必须不被面板遮挡） | **忽略**：MapKit 没有可挪的版权标识 |
 * | [BikeMapInput.userHeadingFlow] | 生效（蓝点上的朝向箭头） | **忽略**：系统气球旋转后造型别扭，见 `BikeMapView.ios.kt` |
 * | [BikeMapInput.onFallback] | 生效（key 失效 → 整层降级，页面给一行说明） | 不会回调（MapKit 没有「底图拿不到」这一档） |
 *
 * ## 五、别拿 `BikeMapInput` 做相等比较
 *
 * 里面装着 `StateFlow` 与一堆 lambda：每次重组都是新实例，`==` 永远为 false。
 * Android 侧因此保留了 `MapOverlayInputs`（只比「画上去的东西」），
 * 见 `OsmMapView.kt` 里那份注释；iOS 侧按 key 增量增删标注，不重建整层。
 */

/**
 * 一次「把镜头移过去」的请求（原在 `app/.../BikeMapViewModel.kt`，2026-11-25 抬进共享层）。
 *
 * 用 [nonce] 当 LaunchedEffect 的 key：同样的坐标连点两次也要能动，比较坐标做不到这点。
 * [zoom] 为 null 表示保持当前缩放；[animated] 为 false 用于恢复上次视野，那一下不该
 * 从校园中心慢慢滑过去，直接落位。
 */
data class CameraRequest(
    val lat: Double,
    val lng: Double,
    val zoom: Double?,
    val nonce: Long,
    val animated: Boolean = true,
)

/**
 * 标记配色（原在 `app/.../BikeMarkerOverlay.kt`，`internal`；2026-11-25 抬进共享层并放开成
 * `public` —— 跨模块看不见 `internal`，而两端都要按同一份配色上色）。
 *
 * 值是 **ARGB int**，不是 Compose `Color`：这份数据要同时喂给
 * `android.graphics`（Android 的位图与多边形）与 `UIColor`（iOS 的 MapKit 标注），
 * 用 int 是两边都能零成本接收的那一种。Android 侧读法 `Color(colorInt)`，iOS 侧走
 * `BikeMapView.ios.kt` 里的 `argbToUIColor`。
 *
 * 配色只从主题来，**唯一构造入口是 [rememberBikeMarkerColors]**（原来那段 20 行字面量写在
 * `RideScreen` 里，iOS 再抄一份必然两边漂移）。
 */
data class BikeMarkerColors(
    /** 圈里有一辆能骑的车。 */
    val available: Int,
    /** 圈里没有能骑的，但还有电。 */
    val lowBattery: Int,
    /** 圈里全是离线或被停用的车。 */
    val unavailable: Int,
    /** 圈内数字的颜色。 */
    val label: Int,
    /** 选中态的描边颜色。 */
    val selectedRing: Int,
    /** 用户位置圆点的实心色（官方小程序绿，2026-10-19）。 */
    val userDot: Int,
    /** 用户位置圆点的淡绿光晕（半透明平铺圆，官方小程序同款）。 */
    val userHalo: Int,
    /** 正中那枚「当前查的是这里」的浮标：黑色针头 + 针杆。 */
    val centerMark: Int,
    /** 浮标针头中央的小圆点（薄荷绿，官方小程序同款）。 */
    val centerGlyph: Int,
    /** 校区围栏描边色（半透明主色）。 */
    val fenceStroke: Int,
    /** 校区围栏填充色（更淡的主色）。 */
    val fenceFill: Int,
    /** 当前用车标记的实心色（主色）。 */
    val rideMarker: Int,
    /** 当前用车标记的外晕（半透明主色）。 */
    val rideHalo: Int,
    /** 当前用车标记里那辆小车的颜色。 */
    val rideGlyph: Int,
    /** 禁停区描边 / 填充（深灰，官方口径 `#333333`，透明度压淡一档）。 */
    val nogoStroke: Int,
    val nogoFill: Int,
    /** 还车点范围描边 / 填充（官方口径 `#D7535D`）。 */
    val spotStroke: Int,
    val spotFill: Int,
    /** 还车点图标：圆角方块底色 + 里面的「P」。 */
    val spotBadge: Int,
    val spotGlyph: Int,
)

/**
 * 定位点与中心浮标的官方小程序色板（2026-10-19 对齐，值从官方截图取样）：
 * 绿色实心圆点 + 淡绿平铺光晕 + 黑色圆头浮标（针心薄荷绿）。
 *
 * **不跟主题走**：地图瓦片始终是浅色底图，官方这套绿/黑在浅底上对比稳定；旧版跟着
 * `scheme.primary` 走，换主题时定位点会变色，和官方观感也对不上。
 * 改色值前先看 DESIGN §3.9「定位」「地图中心针」两行。
 */

/** 蓝点绿（官方小程序同款，2026-10-19 用户口径）。 */
private val USER_DOT_GREEN = Color(0xFF55B837)

/** 蓝点光晕：同色 30% 透明度。 */
private val USER_HALO_GREEN = Color(0xFF63C46B).copy(alpha = 0.30f)

/** 中心浮标的针头 / 针杆（近黑）。 */
private val CENTER_PIN_BLACK = Color(0xFF1F1F1F)

/** 中心浮标针心（薄荷绿）。 */
private val CENTER_PIN_DOT = Color(0xFF63D099)

/**
 * 按当前主题算一份地图配色（两端的唯一入口）。
 *
 * `remember` 的 key 只取**真正参与配色**的那几个色位：主题不变就不重算，与原来写在
 * `RideScreen` 里的那份 key 一致。
 */
@Composable
fun rememberBikeMarkerColors(): BikeMarkerColors {
    val scheme = MaterialTheme.colorScheme
    val semantic = MaterialTheme.semanticColors
    return remember(
        scheme.primary,
        scheme.onPrimary,
        scheme.outline,
        scheme.onSurface,
        semantic.warning,
    ) {
        BikeMarkerColors(
            available = scheme.primary.toArgb(),
            lowBattery = semantic.warning.toArgb(),
            unavailable = scheme.outline.toArgb(),
            label = scheme.onPrimary.toArgb(),
            selectedRing = scheme.onSurface.toArgb(),
            userDot = USER_DOT_GREEN.toArgb(),
            userHalo = USER_HALO_GREEN.toArgb(),
            centerMark = CENTER_PIN_BLACK.toArgb(),
            centerGlyph = CENTER_PIN_DOT.toArgb(),
            fenceStroke = Color(0xFF3D8BEF).copy(alpha = 0.85f).toArgb(),
            fenceFill = Color(0xFF64B5F6).copy(alpha = 0.30f).toArgb(),
            rideMarker = scheme.primary.toArgb(),
            rideHalo = scheme.primary.copy(alpha = 0.22f).toArgb(),
            rideGlyph = scheme.onPrimary.toArgb(),
            nogoStroke = Color(0xFF333333).copy(alpha = 0.45f).toArgb(),
            nogoFill = Color(0xFF333333).copy(alpha = 0.22f).toArgb(),
            spotStroke = Color(0xFFD7535D).copy(alpha = 0.9f).toArgb(),
            spotFill = Color(0xFFD7535D).copy(alpha = 0.26f).toArgb(),
            spotBadge = Color(0xFFD7535D).toArgb(),
            spotGlyph = Color(0xFFFFFFFF).toArgb(),
        )
    }
}

/**
 * 地图层要的全部参数（字段名与语义逐条对齐 `BikeMapHost` 的形参）。
 *
 * 每个字段的语义**只在原来那一处写过**，这里不重抄一遍——下面只写契约层面新出现的东西：
 * 哪一端生效、`null` 是什么意思。
 *
 * @property clusters 聚合圈：常规模式画这些（[byBike] = true 时不消费）。
 * @property selectedKey 当前展开的簇键，图上画选中描边；`null` = 全部收起。
 * @property bikes 「按车辆」模式逐辆画的车（已按距离升序）；[byBike] = false 时不消费。
 * @property byBike 显示方式：true = 每辆车一枚图标，false = 停车点聚合圈。
 * @property selectedBikeCarNum 选中的车号；`null` = 没选中。
 * @property onBikeTap 点中某辆车图标（仅 [byBike] 模式）。
 * @property userLat / @property userLng 已取到的用户位置（GCJ-02）；任一为 null 不画蓝点。
 * @property userAccuracyMeters 定位精度（米，半径）；0 = 不画精度圈。
 * @property userHeadingFlow 蓝点朝向（度，0–360，北 = 0 顺时针）的独立流。
 *   **独立的理由**：罗盘 ≤10Hz，收在页面状态里会让整页跟着重组；地图层自己收集，
 *   只有地图这一层重组。iOS 收下但不画（见文件头第四节）。
 * @property rideLat / @property rideLng 当前用车的车位置（GCJ-02）；任一为 null 不画。
 * @property highlightLat / @property highlightLng 识别高亮车的坐标（GCJ-02）；null 不画。
 * @property highlightLabel 高亮标签（如「车 …669」）；null 只画标记不画文字。
 * @property showCampusFence 校区围栏画不画：与「只看本校」开关同步（2026-11-18 用户要求，
 *   关掉筛选时连校区描边一起隐藏）。
 * @property colors 标记配色，唯一来源 [rememberBikeMarkerColors]。
 * @property camera 待执行的镜头移动；按 `nonce` 触发，连点同一个坐标也生效。
 * @property onCenterSettled 地图**停手**后回调一次（中心纬 / 中心经 / 缩放）。程序性移动
 *   （定位 / 回到校区 / 点分组联动）的回调会被吞掉，那些路径各自安排查询。
 * @property onViewportMeasured 视口半对角线（米）：底图就绪时一次、之后每次相机停稳一次，
 *   **先于静默区判断**（它是几何量，与"这次停手是不是用户动作"无关）。靠它按视口缩放采样
 *   半径与跟随回中距离，否则整段会话都按 0 算。
 * @property zones 还车点 / 禁停区图层（只读；空 = 不画）。「P」**不接点击**。
 * @property onClusterTap 点中某个聚合圈。
 * @property onRideTap 点中「我的车」标记（把镜头移过去）。
 * @property onCameraApplied 镜头移动执行完（把请求清掉，别让它被重放）。
 * @property onUserPan 用户接管地图：调用方据此退出「镜头跟着用户走」。
 * @property onFallback 本次会话底图降级了（Android：高德拿不到底图 → 栅格）；iOS 不会回调。
 * @property initialCenter 建图中心（GCJ-02），只在建图时读一次；null 回落到校区中心。
 *   为什么要它：页面每次进入都会新建地图，而「移到用户位置」是一次性的 camera 请求——
 *   它可能在定位返回之前就被消费、也可能因为地图还没就绪被吞掉。把上次已知位置直接当建图
 *   中心，第一帧就落在人身上。
 * @property initialZoom 建图缩放；null 用默认级别。与 [initialCenter] 同源。
 * @property logoBottomMarginPx **Android 专用，iOS 忽略**：高德版权标识的底边距（px）。
 *   底部面板会盖住地图下沿，而 logo 默认钉在左下角，服务协议不允许遮挡版权声明；页面按
 *   「面板高度 + 底部常驻块」算好传进来，把 logo 顶到面板上沿之上（2026-11-18）。
 *   MapKit 没有可挪的版权标识（Apple 地图的版权署名由系统画在右下角，不受宿主控制），
 *   所以 iOS 收下这个字段但什么都不做。**不要删**——Android 还要用。
 */
data class BikeMapInput(
    val clusters: List<BikeCluster>,
    val selectedKey: String?,
    val bikes: List<NearbyBike> = emptyList(),
    val byBike: Boolean = false,
    val selectedBikeCarNum: String? = null,
    val onBikeTap: (String) -> Unit = {},
    val userLat: Double?,
    val userLng: Double?,
    val userAccuracyMeters: Float = 0f,
    val userHeadingFlow: StateFlow<Float?>,
    val rideLat: Double?,
    val rideLng: Double?,
    val highlightLat: Double? = null,
    val highlightLng: Double? = null,
    val highlightLabel: String? = null,
    val showCampusFence: Boolean = true,
    val colors: BikeMarkerColors,
    val camera: CameraRequest?,
    val onCenterSettled: (Double, Double, Double) -> Unit,
    val onViewportMeasured: (Double) -> Unit = {},
    val zones: KvcxZones,
    val onClusterTap: (String) -> Unit,
    val onRideTap: () -> Unit,
    val onCameraApplied: (Long) -> Unit,
    val onUserPan: () -> Unit = {},
    val onFallback: () -> Unit = {},
    val initialCenter: GcjPoint? = null,
    val initialZoom: Double? = null,
    val logoBottomMarginPx: Int = 0,
)

/**
 * 地图槽位：页面的 `bikeMap` 参数用它，`null` = 本平台没有地图能力（见文件头第二节）。
 *
 * ## 为什么是一个带 `@Composable` 字段的类，而不是 `fun interface`
 *
 * 1. `fun interface` 的抽象方法标 `@Composable` 时，SAM 转换要对着一个 composable lambda
 *    做，Kotlin 2.1 + Compose 编译器插件这条路上有过编译期坑；本机（Windows）编不了
 *    Kotlin/Native，iOS 那半边一旦踩上只能等 macOS 才发现，不值得。
 * 2. 与仓里既有的槽位同形（`ui/today/TodaySlots.kt` 的 `TodayServiceSlot` 也是类 + 
 *    `@Composable` 字段），后来的人不用学第二种写法。
 * 3. 以后要给槽位补能力（例如「这个平台的地图能不能自己定位」），加字段即可，
 *    不用改所有宿主的实现。
 *
 * ## 谁填
 *
 * - Android：`app/.../ui/ebike/BikeMapHost.kt`（内部再选高德 / osmdroid）；
 * - iOS：`shared/src/iosMain/.../ui/ebike/BikeMapView.ios.kt` 的 [iosBikeMapSlot]。
 *
 * @property content 真正的渲染。`Modifier` 由页面给：整幅上移（被面板盖住那一段的一半）
 *   是页面侧的图层变换，地图层自己不该知道面板有多高。
 */
class BikeMapSlot(
    val content: @Composable (input: BikeMapInput, modifier: Modifier) -> Unit,
)
