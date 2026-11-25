package edu.jxslu.schedule.ui.common

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 侧栏展开时「主内容卡」的层级效果：缩小 + 圆角 + 阴影 + 跟手视差。
 *
 * ## 一份实现，两端共用
 *
 * 2026-10-20 在 `:app` 的 `MainActivity` 里按用户参考的形态做出来；2026-11-25 搬进共享层，
 * Android 的层叠抽屉（`StackedDrawerState`）与 iOS 的 `ModalNavigationDrawer` 从此吃同一份
 * 数值与同一套算式——两边各写一遍必然分叉，而这个效果的观感恰恰是用户最先注意到的东西。
 *
 * ## 算式只有一处
 *
 * ```
 * eased = FastOutSlowInEasing(|p|)
 * scale = 1 − Shrink·eased
 * translationX = 侧栏宽·p − (1−scale)·宽/2
 * ```
 *
 * 位移里那半份收缩量补的是「缩放绕中心」少走的路：少了它，页面左缘会离侧栏差一截。
 * **改缩放比例时这条式子要一起改**（不变量：`p = 1` 时左缘正好落在侧栏右缘上）。
 *
 * ## 静止态必须是「零尺寸」的层
 *
 * `eased == 0` 时形状是按整屏矩形建的 0dp 圆角、阴影 0，所以静止态既没有裁角也没有离屏层。
 * **不要图省事把 shape 换成常量 `RoundedCornerShape(CornerRadius)`**：主卡是 `fillMaxSize()`，
 * 静止态会裁掉屏幕四个角、露出后层的底色，观感就是屏幕上凭空多四个圆角缺口。
 *
 * ## 用法
 *
 * ```kotlin
 * val cardTransform = rememberDrawerCardTransform(maxSlidePx = 300.dp.toPx())
 * Surface(
 *     modifier = Modifier
 *         .fillMaxSize()
 *         .drawerCardTransform(cardTransform) { drawer.progress },   // 读在绘制期
 * ) { … }
 * ```
 *
 * `progress` 是 **lambda** 而不是 `Float`：调用点在 `graphicsLayer` 的绘制 lambda 里求值，
 * 每帧只失效绘制、不触发重组。写成 `Float` 实参等于把「抽屉每拖一像素」提到组合期，
 * 整个页面跟着重组（Android 侧 2026-11-06 交叉复核过这条）。
 */
class DrawerCardTransform internal constructor(
    /** `progress = 1` 时主卡的横向位移（= 侧栏宽度）。 */
    internal val slideMaxPx: Float,
    /** 组合期一次建好的量化圆角档（见 [rememberDrawerCardTransform]）。 */
    internal val cornerShapes: List<RoundedCornerShape>,
    /** 圆角全开时的阴影（px），绘制时乘 `eased`。 */
    internal val shadowMaxPx: Float,
)

/**
 * 侧栏展开时主页面的缩放比例（2026-10-20 用户参考的层级效果）。
 *
 * 0.06 = 缩小到 94%：上下各露出约 3% 的后层，够读出「卡片浮在侧栏之上」，
 * 又不至于让字明显变小。
 */
const val DrawerCardShrink: Float = 0.06f

/** 主卡在抽屉完全展开时的圆角上限（2026-10-20 第二版定的 24dp，观感不要动）。 */
val DrawerCardCornerRadius = 24.dp

/** 主卡在抽屉完全展开时的阴影上限（同上）。 */
val DrawerCardShadowElevation = 12.dp

/**
 * 圆角的量化档数：0…[DrawerCardCornerSteps] 共 N+1 个 [RoundedCornerShape]，组合期建一次。
 *
 * 为什么量化而不是逐帧 `RoundedCornerShape(24.dp * eased)`：那个写法在
 * `graphicsLayer` 的绘制 lambda 里**每帧新建一个形状对象**。按 1dp 一档后，形状对象变成
 * 组合期建好的常量数组，绘制 lambda 里只剩一次下标 + 一次乘法，一帧少一次小对象分配；
 * 台阶 ≤1dp（280ms 里每档约 12ms），肉眼不可见。
 *
 * shortcut: 收益就到此为止——**别**指望「outline 取值次数下降」：`graphicsLayer` 的 shape
 * 本来就是逐帧读，一次 spring 落定约 20~25 帧，量化前后差别可以忽略（2026-11-06 交叉复核
 * 纠正了这条注释原来的说法）。真正可感的是**拖动期间**（`draggable` 每帧都在改 eased），
 * 若哪天这条路径不热了，退回逐帧 `RoundedCornerShape(24.dp * eased)` 两行也完全正确。
 */
private const val DrawerCardCornerSteps = 24

/**
 * 预备主卡的形状与阴影（组合期一次）：量化圆角数组 + 阴影上限 px。
 *
 * 放在组合期是为了让 [drawerCardTransform] 的绘制 lambda 里**不再新建任何对象**、
 * 也不再做 `Dp → px` 换算（那是一次 `LocalDensity` 查找）。
 */
@Composable
fun rememberDrawerCardTransform(maxSlidePx: Float): DrawerCardTransform {
    val density = LocalDensity.current
    val shapes = remember {
        List(DrawerCardCornerSteps + 1) { i ->
            RoundedCornerShape(DrawerCardCornerRadius * (i.toFloat() / DrawerCardCornerSteps))
        }
    }
    val shadowMaxPx = with(density) { DrawerCardShadowElevation.toPx() }
    return remember(maxSlidePx, shadowMaxPx, shapes) {
        DrawerCardTransform(slideMaxPx = maxSlidePx, cornerShapes = shapes, shadowMaxPx = shadowMaxPx)
    }
}

/**
 * 把 [DrawerCardTransform] 挂到主内容卡上。`progress` 在绘制期求值（见类 KDoc 的用法）。
 *
 * `progress` 的口径是「合上 = 0、全开 = 1」。两端的取值方式不同，归一化留在调用点：
 * Android 的 [StackedDrawerState.progress] 本来就是 0…1；iOS 的 `DrawerState.offset`
 * 是「全开 = −侧栏宽」，调用点取 `-offset / 侧栏宽`。
 *
 * 内部对 `p` 取 `abs` 再喂曲线：拖动时 `progress` 会短暂越界（阻尼、回弹），
 * 缩放与圆角只认幅度，横向位移按带符号的 `p` 走——越界那一帧不该让圆角重算一个更大的值。
 */
fun Modifier.drawerCardTransform(
    transform: DrawerCardTransform,
    progress: () -> Float,
): Modifier = graphicsLayer {
    val p = progress()
    val eased = FastOutSlowInEasing.transform(abs(p))
    val scale = 1f - DrawerCardShrink * eased
    scaleX = scale
    scaleY = scale
    translationX = transform.slideMaxPx * p - (1f - scale) * size.width / 2f
    shape = transform.cornerShapes[
        (eased * DrawerCardCornerSteps).roundToInt().coerceIn(0, DrawerCardCornerSteps)
    ]
    shadowElevation = transform.shadowMaxPx * eased
    clip = true
}
