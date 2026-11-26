package edu.jxslu.schedule

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import edu.jxslu.schedule.ui.theme.Motion

/**
 * iOS 的「左缘右滑返回」（2026-11-26 晚）。
 *
 * ## 为什么必须有
 *
 * Android 靠实体返回键，iOS 靠**屏幕左缘往右滑**。此前 `AppBackHandler` 在 iOS 是空实现
 * （`ui/common/BackHandler.ios.kt`），二级页只能点顶栏那只箭头——这是 iOS 用户第一个会骂的点。
 *
 * ## 为什么不是 `AppBackHandler` 的 actual
 *
 * 那个缝的语义是「拦截系统返回键」，iOS 根本没有这个事件源；把它实现成手势会把「返回键」
 * 和「手势区域」两件事混成一个。这里是壳层的一个手势修饰符，只挂在二级页容器上，
 * 与 `AppBackHandler` 互不相干。
 *
 * ## 与抽屉手势的关系
 *
 * 抽屉（`ModalNavigationDrawer`）自己也监听左缘右滑。两者同时在场时同一个手势会打架，
 * 所以宿主在二级页打开时把抽屉手势关掉（`gesturesEnabled = stack.isEmpty()`），
 * 这条才生效。**顺序不能反**：先关抽屉手势，再挂这条。
 *
 * ## 交互口径
 *
 * - 只有**按下点**落在左缘 [IOS_EDGE_BACK_ZONE] 以内才算，避免误吃页面内的横向滑动
 *   （课表周切换、分页器）。
 * - 先看方向再看是否接管：**向左滑不接**（那是页面自己的手势），只有越过 touch slop 时
 *   已经在往右，才 consume 后续事件。
 * - 跟手位移 1:1，上限屏宽 42%；松手时超过屏宽 26% 就返回。两个阈值按**容器宽度**算而
 *   不是固定 dp——iPhone SE 与 Pro Max 的宽度差 1.4 倍，写死 dp 会让大屏上「滑很长一段
 *   还不返回」。
 * - 没超过阈值就动画回到 0（`Motion.state()`），不是硬吸附——硬吸附读起来像卡了一下。
 *
 * ## 为什么位移用 `mutableFloatStateOf` 而不是 `Animatable`
 *
 * `awaitEachGesture` 的接收者是 `AwaitPointerEventScope`，那是一个**受限挂起作用域**：
 * 在它里面只能调它自己的挂起函数，`Animatable.snapTo` / `animateTo` 一律编不过
 * （CI 实测：`Restricted suspending functions can invoke member or extension suspending
 * functions only on their restricted coroutine scope`）。所以跟手阶段只写一个 float 状态，
 * 回弹动画交给组合作用域的 `launch` 去驱动。
 *
 * 跟手位移读在 `graphicsLayer` 的 lambda 里（绘制期求值），拖动过程中不触发重组。
 */
@Composable
internal fun Modifier.iosEdgeBackSwipe(
    enabled: Boolean,
    onBack: () -> Unit,
): Modifier {
    var dragX by remember { mutableFloatStateOf(0f) }
    val latestBack = rememberUpdatedState(onBack)
    val scope = rememberCoroutineScope()
    val edgeZonePx = with(LocalDensity.current) { IOS_EDGE_BACK_ZONE.toPx() }
    return this
        .graphicsLayer { translationX = dragX }
        .pointerInput(enabled, edgeZonePx) {
            if (!enabled) return@pointerInput
            // 阈值与上限按节点尺寸现算（`size` 是 px，已含左右安全区）
            val commitAt = size.width * 0.26f
            val maxDrag = size.width * 0.42f
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                // 落点不在左缘：整段交给页面自己
                if (down.position.x > edgeZonePx) return@awaitEachGesture
                var rightward = false
                val slop = awaitHorizontalTouchSlopOrCancellation(down.id) { change, _ ->
                    if (change.position.x > down.position.x) {
                        rightward = true
                        change.consume()
                    }
                } ?: return@awaitEachGesture
                // 左滑不接管：把这一段留给页面
                if (!rightward) return@awaitEachGesture
                dragX = 0f
                var dragged = 0f
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == slop.id } ?: break
                    dragged = (change.position.x - down.position.x).coerceIn(0f, maxDrag)
                    dragX = dragged
                    change.consume()
                    if (!change.pressed) break
                }
                if (dragged >= commitAt) {
                    latestBack.value()
                    // 栈已经退了一层：立刻归零，否则「上一页」会带着这段位移出现在屏外
                    dragX = 0f
                } else {
                    // 回弹：从松手位置滑回 0。协程在组合作用域里起，不在受限挂起作用域内。
                    scope.launch {
                        val from = dragX
                        Animatable(from).animateTo(
                            targetValue = 0f,
                            animationSpec = tween(Motion.stateMillis, easing = Motion.decelerate),
                        ) { dragX = value }
                    }
                }
            }
        }
}

/** 生效的左缘宽度。与 iOS 系统的边缘手势区（约 20pt）同量级，略宽是为了手指更好按上。 */
private val IOS_EDGE_BACK_ZONE = 28.dp
