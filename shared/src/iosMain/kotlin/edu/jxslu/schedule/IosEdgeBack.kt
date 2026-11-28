package edu.jxslu.schedule

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import edu.jxslu.schedule.ui.theme.Motion
import kotlin.math.abs
import kotlinx.coroutines.launch

/**
 * iOS 的「左缘右滑返回」（2026-11-26 晚；2026-11-27 重写）。
 *
 * ## 为什么必须有
 *
 * Android 靠实体返回键，iOS 靠**屏幕左缘往右滑**。此前 `AppBackHandler` 在 iOS 是空实现
 * （`ui/common/BackHandler.ios.kt`），二级页只能点顶栏那只箭头——这是 iOS 用户第一个会骂的点。
 *
 * ## 为什么不是 `AppBackHandler` 的 actual
 *
 * 那个缝的语义是「拦截系统返回键」，iOS 根本没有这个事件源；把它实现成手势会把「返回键」
 * 和「手势区域」两件事混成一个。这里是壳层的一个手势修饰符，只挂在页面容器上，
 * 与 `AppBackHandler` 互不相干。
 *
 * ## 上一版为什么真的滑不动（2026-11-27 用户报「无法通过系统返回来返回上一个界面」）
 *
 * 三条独立缺陷，缺一条都够让手势失效：
 *
 * 1. **命中区跟着位移跑**。上一版的链是
 *    `graphicsLayer { translationX = dragX }.pointerInput { … }`：`pointerInput` 在位移层
 *    **里面**，它收到的 `change.position` 是位移后的坐标系。内容右移 `dragX` 后，手指在屏幕上
 *    再右移同样一段，节点本地坐标几乎不动 → 算出来的位移在「真实位移」与「0」之间来回跳，
 *    阈值永远攒不够。修法是把 `pointerInput` 放在位移层**外面**（本文件末尾的链序），
 *    节点不动、内容动。
 * 2. **嵌套滚动先把事件吃掉**。`awaitHorizontalTouchSlopOrCancellation` 在 Main 遍里等，
 *    页面自己的横向手势（课表周切换、分页器）也用同一遍、同一套 slop；谁先 consume 谁赢，
 *    而子节点先收到事件。这里改成在 **Initial 遍**自己判 slop：父节点先看到事件，在左缘
 *    起步的右滑上抢先 consume，页面内的横向滚动收到的就是「已被消费」，自动让位。
 * 3. **根页把开关关了**。设置覆盖层里此前是 `enabled = stack.isNotEmpty()`，设置首页
 *    （栈空）右滑什么都不做——用户点进设置后第一屏正好就是这一屏。现在覆盖层常开：
 *    栈空时右滑 = 关覆盖层（iOS 习惯：右滑退出本层）。
 *
 * ## 交互口径
 *
 * - 只有**按下点**落在左缘 [IOS_EDGE_BACK_ZONE] 以内才算，避免误吃页面内的横向滑动。
 * - 只有**右滑**才接管：左滑直接放手（那是页面自己的手势）；以竖向为主的拖动也放手
 *   （列表滚动）。判据是同一帧的位移，不是「先猜方向再等 slop」。
 * - 跟手位移 1:1，上限屏宽 42%；松手时超过屏宽 26% 就返回。两个阈值按**节点宽度**算
 *   而不是固定 dp——iPhone SE 与 Pro Max 的宽度差 1.4 倍，写死 dp 会让大屏上「滑很长一段
 *   还不返回」。
 * - 没超过阈值就动画回到 0（`Motion.state()`），不是硬吸附——硬吸附读起来像卡了一下。
 *
 * ## 为什么位移用 `mutableFloatStateOf` 而不是 `Animatable`
 *
 * `AwaitPointerEventScope` 是**受限挂起作用域**：在它里面只能调它自己的挂起函数，
 * `Animatable.snapTo` / `animateTo` 一律编不过（CI 实测：
 * `Restricted suspending functions can invoke member or extension suspending functions only on
 * their restricted coroutine scope`）。所以跟手阶段只写一个 float 状态，回弹动画交给
 * 组合作用域的 `launch` 去驱动。
 *
 * 跟手位移读在 `graphicsLayer` 的 lambda 里（绘制期求值），拖动过程中不触发重组。
 *
 * ## 宿主契约（挂载点必须满足的两条）
 *
 * 1. 这个修饰符要挂在**页面容器**上，且容器外面得有不透明底色。位移期间页面让出的位置
 *    露出的是宿主画的底，不是 `ComposeUIViewController` 的 UIView 白底。
 * 2. 不能让它在 `ModalNavigationDrawer` 的抽屉手势之外再挂一份：抽屉开着时（栈空）
 *    这里 `enabled = false`，否则两个手势抢同一段左缘。
 */
@Composable
internal fun Modifier.iosEdgeBackSwipe(
    enabled: Boolean,
    onBack: () -> Unit,
): Modifier {
    var dragX by remember { mutableFloatStateOf(0f) }
    val latestBack = rememberUpdatedState(onBack)
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val edgeZonePx = with(density) { IOS_EDGE_BACK_ZONE.toPx() }
    return this
        // **链序不能反**：`pointerInput` 必须在 `graphicsLayer` 之前（= 外层）。
        // 位置在位移层里面时，它读到的坐标会随 `dragX` 一起漂移，见 KDoc 第 1 条。
        .pointerInput(enabled, edgeZonePx) {
            // 系统 touch slop（约 8dp）：页面内的横向手势用的也是它，两边门槛一致才不会
            // 出现「我比页面早/晚一帧接管」的手感差。
            val touchSlopPx = viewConfiguration.touchSlop
            // 开关翻掉或节点重建时把残留位移归零：否则内容会停在滑出去一半的位置。
            dragX = 0f
            if (!enabled) return@pointerInput
            awaitEachGesture {
                // Initial 遍：比页面里的滚动容器 / `pointerInput` 先看到事件，这是「左缘优先」
                // 能成立的原因（Main 遍里子节点先收到）。
                val down = awaitFirstDown(
                    requireUnconsumed = false,
                    pass = PointerEventPass.Initial,
                )
                // 落点在左缘之外：整段交给页面自己（这一条是「不与课表周切换打架」的闸门）
                if (down.position.x > edgeZonePx) return@awaitEachGesture
                // 阈值按节点现算：旋转 / 分屏后宽度会变
                val commitAt = size.width * IOS_EDGE_BACK_COMMIT
                val maxDrag = size.width * IOS_EDGE_BACK_MAX
                var claimed = false
                var dragged = 0f
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!claimed) {
                        // 没越过 slop 就松手 = 点按，不是手势
                        if (!change.pressed) break
                        val dx = change.position.x - down.position.x
                        val dy = change.position.y - down.position.y
                        // 左滑：页面自己的手势（课表周切换往回翻一类）
                        if (dx < -touchSlopPx) break
                        // 以竖向为主：列表滚动，别抢
                        if (abs(dy) > touchSlopPx && abs(dy) >= abs(dx)) break
                        if (dx <= touchSlopPx) continue
                        claimed = true
                    }
                    dragged = (change.position.x - down.position.x).coerceIn(0f, maxDrag)
                    dragX = dragged
                    // 接管这一段（含抬起）：页面里的点按与横向滚动看到 `isConsumed` 会自行放弃，
                    // 不会在滑到一半松手时又触发一次点击。
                    change.consume()
                    if (!change.pressed) break
                }
                if (!claimed) return@awaitEachGesture
                if (dragged >= commitAt) {
                    latestBack.value()
                    // 栈已经退了一层：立刻归零，否则「上一页」会带着这段位移出现在屏外
                    dragX = 0f
                } else {
                    // 回弹：从松手位置滑回 0。协程在组合作用域里起，不在受限挂起作用域内。
                    scope.launch {
                        Animatable(dragX).animateTo(
                            targetValue = 0f,
                            animationSpec = tween(Motion.stateMillis, easing = Motion.decelerate),
                        ) { dragX = value }
                    }
                }
            }
        }
        .graphicsLayer { translationX = dragX }
}

/** 生效的左缘宽度。与 iOS 系统的边缘手势区（约 20pt）同量级，略宽是为了手指更好按上。 */
private val IOS_EDGE_BACK_ZONE = 28.dp

/** 跟手位移上限（屏宽占比）。超过这段再滑也不动，给出「已经到底」的反馈。 */
private const val IOS_EDGE_BACK_MAX = 0.42f

/** 松手判定线（屏宽占比）：过了就返回，没过就回弹。 */
private const val IOS_EDGE_BACK_COMMIT = 0.26f
