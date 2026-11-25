@file:OptIn(ExperimentalForeignApi::class)

package edu.jxslu.schedule.ui.ebike

import edu.jxslu.schedule.domain.BikeNearby
import edu.jxslu.schedule.domain.GcjPoint
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import platform.CoreLocation.CLLocationCoordinate2D
import platform.MapKit.MKCircle
import platform.MapKit.MKCircleRenderer
import platform.MapKit.MKMapView
import platform.MapKit.MKOverlayPathRenderer
import platform.MapKit.MKOverlayProtocol
import platform.MapKit.MKOverlayRenderer
import platform.MapKit.MKPolygon
import platform.MapKit.MKPolygonRenderer
import kotlin.math.roundToInt

/*
 * 文件头说明（不是 KDoc：它不属于某一个声明，是这一层的说明）
 * ==========================================================================
 *
 * 快趣出行地图的 **iOS 叠加层**（第二批，2026-11-25）：校区围栏 / 禁停区 / 还车点范围三层
 * 多边形 + 定位精度圈。数据来自 `BikeMapInput.showCampusFence` / `zones` /
 * `userAccuracyMeters`，与 `AmapMapView.AmapLayers.drawPolygons` 同一口径。
 *
 * ## 一、为什么单独一个文件、单独一次提交
 *
 * 上一版（commit `1db5dcc2`）在 CI 上被 Kotlin/Native 打回约 20 处，**头四个**就是
 * `addOverlay` / `addOverlays` / `removeOverlay` / `removeOverlays` 解析不到。本机是 Windows、
 * 没有 Kotlin/Native（`:shared:compileKotlinIosArm64` 是 SKIPPED），这一族到底怎么调**无法
 * 在本地核对**，只能靠一次 CI 往返。所以：
 *
 * - 底图 + 点位 + 相机 → `BikeMapView.ios.kt`（第一批，不碰覆盖物，能独立编译）；
 * - 覆盖物 → 这个文件（第二批）。**万一 CI 只打回这一批，`git revert <第二批的 sha>` 就回到
 *   「有底图、有车、能点选」的可编译版本**，代价只有一次往返而不是从头再来。
 *
 * 取消这一批时要连带删的（第一批里的五处引用，都是 ② 加的）：`MapKitHost.overlayLayer` 的
 * 字段与两处调用、`MapDelegate` 的 `rendererFor` 参数与 `rendererForOverlay` 重写、
 * `IosOverlayLayer` 本身。
 *
 * ## 二、`addOverlay` 那一族最终怎么绕开的
 *
 * 按「不动那四个方法名」的要求，这里选的是**批量 + 整层重建**这一条：
 *
 * | 想要 | 这里怎么做 | 为什么不是逐枚 |
 * |---|---|---|
 * | 加一层 | 自己维护 `MutableList<MKOverlayProtocol>`，攒齐后 `view.addOverlays(list)` **一次**加 | 逐枚 `addOverlay` 就是被打回的那四个方法名；而且这一层的输入本来就是一整批（围栏 / 禁停区 / 全部还车点范围同时变） |
 * | 删一层 | `view.removeOverlays(旧的那一批)` **一次**摘掉 | 同上。这一层几乎不变（只有换查询中心时才可能变），重建的代价可以忽略 |
 * | 精度圈 | 它是**单独一枚**、每次定位读数都可能变，所以自己记一个槽位，换的时候 `removeOverlays(listOf(旧))` + `addOverlays(listOf(新))` | 与上面同一对方法，只是列表里只有一个元素 |
 *
 * 也就是说：**一个「加」入口、一个「删」入口，全在这里，都不可能被逐枚调用绕开**。
 * 万一 `addOverlays` / `removeOverlays` 也解析不到，按顺序试下面两条（都在这一族里选，不引新概念）：
 *
 * 1. 换单数形态：`addOverlay(x)` / `removeOverlay(x)`（只是同一批 API 的另一半，若只有单数可用，
 *    把 [replace] 换成循环即可，一行改动）；
 * 2. 连单数都没有：这一层就**别画了**——`git revert` 第二批，把 `showCampusFence` /
 *    `zones` / `userAccuracyMeters` 在 iOS 上降级为「不画」（Android 不受影响，契约字段保留）。
 *    **不要**为它去写自定义 `MKOverlay` 子类：那要 `boundingMapRect` + `coordinate` + 渲染器，
 *    比这一族更没把握。
 *
 * ## 三、本文件用到的平台符号与替代写法（本机没编译过，CI 报错照这张表改）
 *
 * | 符号 | 这里的写法 | 依据 | 万一不对怎么改 |
 * |---|---|---|---|
 * | `view.addOverlays(list)` / `view.removeOverlays(list)` | 唯一的两个入口（见第二节） | MapKit 的标准批量 API；上一版打回的是**逐枚**那四个名字 | 换 `addOverlay` / `removeOverlay` 单数形态；再不行整层不画（见第二节第 2 条） |
 * | `MKPolygon.polygonWithCoordinates(ptr, count)` | `memScoped { allocArray<CLLocationCoordinate2D>(n) }` 填坐标后一次传 | Apple 文档：这些构造器**会拷贝坐标**，所以 `memScoped` 退出后释放是安全的；Kotlin 社区同形写法（`cPointer[i].latitude = …`）也确认可写 | 换 `MKPolygon(points = …, count = …)`（`MKMapPoint` 版，要多一次经纬度→`MKMapPoint` 换算） |
 * | `allocArray<CLLocationCoordinate2D>(n)[i].latitude = …` | 逐点填 | 「结构体数组按下标取元素再写字段」是 K/N 处理 C struct 数组的标准姿势（`allocArray` 返回 `CPointer<T>`，`ptr[i]` 是那块内存本身的左值） | 写不进去就先 `alloc<CLLocationCoordinate2D>()` 单点填完再 `raw[i].useContents {}` 复制——**别**改成 `cValue`，`cValue` 不能进数组 |
 * | `MKCircle.circleWithCenterCoordinate(coord, radius)` | 精度圈 | Apple 文档同名的类工厂 | 换 `MKCircle(circleWithCenterCoordinate = …, radius = …)` 构造器形态 |
 * | `MKPolygonRenderer(polygon = …)` / `MKCircleRenderer(circle = …)` | 覆盖物渲染器 | 两个子类各自的 `initWithPolygon:` / `initWithCircle:` | 换父类 `MKOverlayPathRenderer(overlay = overlay)`（不认形状，最少要能编过） |
 * | `MKOverlayPathRenderer.fillColor` / `strokeColor` / `lineWidth` | 上色 | 都是 ObjC 读写属性 | 报 `'val' cannot be reassigned` 的那一个删掉（只丢填充或描边） |
 * | `MKMapViewDelegateProtocol.mapView(mapView:rendererForOverlay:)` | 交渲染器 | 代理里唯一的覆盖物回调 | 这个方法编不过就整层不画（没有第二个入口） |
 *
 * ## 四、叠放顺序与「指纹」跳过
 *
 * 顺序 = 加入顺序（MapKit 同等级下后加的盖在上面），与 Android 的 zIndex 同序：
 * 校区围栏（最底层，背景信息）→ 禁停区 → 还车点范围。车辆与标注是另一套（第一批），
 * MapKit 总是把标注画在覆盖物之上，与 Android 的 zIndex 结论一致。
 *
 * 多边形那一层按 [PolygonsKey] 指纹整段跳过：这三层几乎不变，而 `update` 每轮重组都会跑一次，
 * 每次都重传顶点是白花的时间。要改这几层时记得把新输入加进指纹，否则画面不会动。
 */

/** 一个覆盖物要长什么样：填充 / 描边都带 alpha（ARGB 里已经压过）。 */
internal class OverlayStyle(val fill: Int, val stroke: Int, val lineWidth: Double)

/** 多边形那一层的输入指纹（任一项变了才重画）。 */
private data class PolygonsKey(
    val fence: Boolean,
    val nogo: List<List<GcjPoint>>,
    val spots: List<List<GcjPoint>>,
    val colors: List<Int>,
)

/**
 * 覆盖物层：自己维护「已经加上去的覆盖物」清单，加 / 删都只走批量入口（见文件头第二节）。
 *
 * 样式按**对象身份**认回去（[styleOf] 用的是 `===`），不给覆盖物设 `title`：
 * `MKShape.title` 是用户可见文案，且上一版的 `'val' cannot be reassigned` 很可能就出在
 * 往它上面写。
 */
internal class IosOverlayLayer {

    /** 已加进地图的多边形（还车点范围 / 禁停区 / 校区围栏）。 */
    private val polygons = mutableListOf<MKOverlayProtocol>()

    /** 覆盖物 → 样式（身份比对，见类注释）。 */
    private val styles = mutableListOf<Pair<MKOverlayProtocol, OverlayStyle>>()

    private var polygonsKey: PolygonsKey? = null

    private var accuracyOverlay: MKOverlayProtocol? = null
    private var accuracyKey: String? = null

    /** 每轮重组调一次；两层各自按指纹短路（见文件头第四节）。 */
    fun update(view: MKMapView, inputs: DrawableInputs) {
        drawPolygons(view, inputs)
        drawAccuracy(view, inputs)
    }

    /** 视图要走了：把自己的覆盖物摘干净（否则下一个地图视图会背着一批孤儿对象）。 */
    fun clear(view: MKMapView) {
        replace(view, polygons, emptyList())
        polygonsKey = null
        val circle = accuracyOverlay
        if (circle != null) {
            dropStyles(circle)
            view.removeOverlays(listOf(circle))
            accuracyOverlay = null
        }
        accuracyKey = null
    }

    // ---- 渲染器 ----

    /** 代理来要渲染器：按对象身份取样式，几何由 MapKit 的现成类画。 */
    fun renderer(overlay: MKOverlayProtocol): MKOverlayRenderer {
        val style = styleOf(overlay)
            ?: OverlayStyle(fill = 0x00000000, stroke = 0x00000000, lineWidth = 0.0)
        return when (overlay) {
            is MKCircle -> MKCircleRenderer(circle = overlay).apply { applyStyle(this, style) }
            is MKPolygon -> MKPolygonRenderer(polygon = overlay).apply { applyStyle(this, style) }
            // 认不出形状的只可能是我们自己没登记的，理论上不会出现；协议要求必须回一个渲染器，
            // 给父类实例比抛异常安全（代理里抛会直接把 App 带崩）
            else -> MKOverlayRenderer(overlay = overlay)
        }
    }

    private fun applyStyle(renderer: MKOverlayPathRenderer, style: OverlayStyle) {
        renderer.fillColor = argbToUIColor(style.fill)
        renderer.strokeColor = argbToUIColor(style.stroke)
        renderer.lineWidth = style.lineWidth
    }

    private fun styleOf(overlay: MKOverlayProtocol): OverlayStyle? =
        styles.firstOrNull { (candidate, _) -> candidate === overlay }?.second

    private fun dropStyles(removed: MKOverlayProtocol) {
        styles.removeAll { (overlay, _) -> overlay === removed }
    }

    // ---- 多边形 ----

    private fun drawPolygons(view: MKMapView, inputs: DrawableInputs) {
        val colors = inputs.colors
        val fence = inputs.showCampusFence && BikeNearby.CAMPUS_FENCE.size >= 3
        val key = PolygonsKey(
            fence = fence,
            nogo = inputs.zones.nogoZones,
            spots = inputs.zones.parkSpots.map { it.outline },
            colors = listOf(
                colors.fenceFill,
                colors.fenceStroke,
                colors.nogoFill,
                colors.nogoStroke,
                colors.spotFill,
                colors.spotStroke,
            ),
        )
        if (key == polygonsKey) return

        val fresh = mutableListOf<MKOverlayProtocol>()
        // 「只看本校」关掉时不画（2026-11-18 用户要求：连校区描边一起隐藏）
        if (fence) {
            addPolygon(
                into = fresh,
                points = BikeNearby.CAMPUS_FENCE,
                style = OverlayStyle(
                    fill = colors.fenceFill,
                    stroke = colors.fenceStroke,
                    lineWidth = 2.0,
                ),
            )
        }
        inputs.zones.nogoZones.forEach { outline ->
            if (outline.size >= 3) {
                addPolygon(
                    into = fresh,
                    points = outline,
                    style = OverlayStyle(
                        fill = colors.nogoFill,
                        stroke = colors.nogoStroke,
                        lineWidth = 1.0,
                    ),
                )
            }
        }
        inputs.zones.parkSpots.forEach { spot ->
            if (spot.outline.size >= 3) {
                addPolygon(
                    into = fresh,
                    points = spot.outline,
                    style = OverlayStyle(
                        fill = colors.spotFill,
                        stroke = colors.spotStroke,
                        lineWidth = 1.5,
                    ),
                )
            }
        }
        replace(view, polygons, fresh)
        // 指纹最后才记：真出了「一个都没造出来」这种状况，下一轮还会重试，而不是永远跳过
        polygonsKey = key
    }

    /** 把 [target] 这一批换成一整批新的：摘旧的、记样式、加新的，各一次调用（见文件头第二节）。 */
    private fun replace(
        view: MKMapView,
        target: MutableList<MKOverlayProtocol>,
        fresh: List<MKOverlayProtocol>,
    ) {
        val old = target.toList()
        if (old.isNotEmpty()) {
            view.removeOverlays(old)
            old.forEach { dropStyles(it) }
        }
        target.clear()
        target += fresh
        if (fresh.isNotEmpty()) view.addOverlays(fresh)
    }

    private fun addPolygon(into: MutableList<MKOverlayProtocol>, points: List<GcjPoint>, style: OverlayStyle) {
        val polygon = polygonOf(points) ?: return
        styles += polygon to style
        into += polygon
    }

    /**
     * 顶点 → `MKPolygon`。顶点就是 `scopeArray` 原样（GCJ-02，不转换，见 `BikeMapView.ios.kt`
     * 文件头第一节）。
     *
     * 坐标数组用 `memScoped` 里的 `allocArray` 现填现传：Apple 文档写明 `MKPolygon` 会**拷贝**
     * 这些坐标，所以出作用域后释放是安全的。
     */
    private fun polygonOf(points: List<GcjPoint>): MKPolygon? {
        if (points.size < 3) return null
        return memScoped {
            val raw = allocArray<CLLocationCoordinate2D>(points.size)
            points.forEachIndexed { index, point ->
                raw[index].latitude = point.lat
                raw[index].longitude = point.lng
            }
            val polygon: MKPolygon? = MKPolygon.polygonWithCoordinates(raw, points.size.toULong())
            polygon
        }
    }

    // ---- 精度圈 ----

    /**
     * 定位精度圈：半径就是米（`MKCircle` 也按米算，不必像 osmdroid 那样折像素）。
     *
     * 填充比描边淡一档（与 Android 的 `withAlpha(colors.userHalo, 0.45f)` 同值）；罗盘朝向不进这一层
     * （iOS 不画朝向，见 `BikeMapView.ios.kt` 文件头第三节）。
     */
    private fun drawAccuracy(view: MKMapView, inputs: DrawableInputs) {
        val point = inputs.userPoint
        val meters = inputs.userAccuracyMeters
        val key = if (point == null || meters <= 0f) {
            null
        } else {
            "${point.lat},${point.lng},$meters,${inputs.colors.userHalo}"
        }
        if (key == accuracyKey) return

        val previous = accuracyOverlay
        if (previous != null) {
            view.removeOverlays(listOf(previous))
            dropStyles(previous)
            accuracyOverlay = null
        }
        // 先把指纹清掉：造圈失败时下一轮还会重试（见 drawPolygons 同一处口径）
        accuracyKey = null
        if (point == null || key == null) return

        val circle: MKCircle? = MKCircle.circleWithCenterCoordinate(
            coordinateOf(point.lat, point.lng),
            meters.toDouble(),
        )
        if (circle == null) return
        styles += circle to OverlayStyle(
            fill = withAlpha(inputs.colors.userHalo, 0.45),
            stroke = withAlpha(inputs.colors.userHalo, 1.1),
            lineWidth = 1.0,
        )
        accuracyOverlay = circle
        accuracyKey = key
        view.addOverlays(listOf(circle))
    }
}

/** 只改透明度（镜像 `AmapMapView` 的 `withAlpha`）：精度圈的填充比描边淡一档。 */
internal fun withAlpha(argb: Int, factor: Double): Int {
    val alpha = (((argb ushr 24) and 0xFF) * factor).roundToInt().coerceIn(0, 255)
    return (alpha shl 24) or (argb and 0x00FFFFFF)
}
