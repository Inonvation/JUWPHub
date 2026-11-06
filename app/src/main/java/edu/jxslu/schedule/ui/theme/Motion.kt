package edu.jxslu.schedule.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * 全 App 动效口径（DESIGN §3.2「动效」）。
 *
 * 为什么要有这个文件：2026-11-06 审计实测 `tween(数字)` 有 **142 处、23 个不同值**
 * （90/100/110/120/140/150/160/170/180/190/200/210/220/240/250/260/280/300/320/340/380/400/450），
 * 同一件事在不同页面用不同时长——「展开」在课表页 250ms、在设置页 200ms、在今日页 340ms。
 * 用户感受到的不是"快慢有别"，而是**同一个 App 的手感不统一**；改一次统一口径也无处下手。
 *
 * ## 四档时长
 *
 * 按「用户在看什么」分档，不按控件类型分：
 *
 * | 档 | 时长 | 用在哪 |
 * |----|------|--------|
 * | [tapMillis] 90 | 按下/抬起反馈、颜色与描边的瞬间态 | 用户手指还在屏幕上，必须立刻有回应 |
 * | [stateMillis] 180 | 状态切换：选中、开关、展开/收起、勾选 | 一次操作对应一次可见变化 |
 * | [revealMillis] 220 | 内容出现/消失：淡入淡出、错峰出现、骨架→内容 | 新内容进场，要让眼睛跟上 |
 * | [pageMillis] 280 | 页面级：进出场、弹层升降、大块位移 | 换"在哪一页"，要交代方位 |
 *
 * ## 曲线
 *
 * 取自 Material 3 的 emphasized 家族（`CubicBezierEasing` 手写，不用
 * `MaterialTheme.motionScheme`——那套在 material3 1.3 仍标 `@ExperimentalMaterial3Api`，
 * 且只给 spec 不给曲线，调用点没法混用 `animateColorAsState`）：
 *
 * - [standard]：两头都缓，默认就用它（等价 M3 emphasized）
 * - [decelerate]：进场专用，起步快、收尾慢——「东西进来了」
 * - [accelerate]：退场专用，起步慢、收尾快——「东西走了」
 *
 * ## 归并口径（把字面量换成这四档时照这张表）
 *
 * | 原字面量 | 归到 | 说明 |
 * |----------|------|------|
 * | ≤ 120 | [tapMillis] 90 | 原来写 100/110/120 的多是按下反馈 |
 * | 130–200 | [stateMillis] 180 | 原来 150/160/180/190/200 |
 * | 201–250 | [revealMillis] 220 | 原来 210/220/240/250 |
 * | 251–450 | [pageMillis] 280 | 原来 260/280/300/320/340/380/400/450 |
 * | 退场淡出 | [exit] 90 | 原来 90~200 的 fadeOut；退场要**更快更急**（accelerate） |
 *
 * 归并会改动个别页面的实际时长（多在 ±40ms 内，肉眼不可分辨）。**不要**为了"保持原值"
 * 在调用点另写常量——那正是这次要清掉的东西；确有必须例外的（例如跟随手势的连续动画），
 * 在调用点写 `// 例外：<原因>`，并说明为什么四档不够用。
 */
object Motion {

    /** 按下/抬起、颜色与描边的瞬间态。手指还在屏幕上，迟一帧就是"没反应"。 */
    const val tapMillis: Int = 90

    /** 状态切换：选中、开关、展开/收起。 */
    const val stateMillis: Int = 180

    /** 内容出现/消失：淡入淡出、错峰出现、骨架→内容。 */
    const val revealMillis: Int = 220

    /** 页面级：进出场、弹层升降、大块位移。 */
    const val pageMillis: Int = 280

    /** 默认曲线（M3 emphasized）：两头都缓。 */
    val standard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** 进场（M3 emphasized-decelerate）：起步快、收尾慢。 */
    val decelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    /** 退场（M3 emphasized-accelerate）：起步慢、收尾快。 */
    val accelerate: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    /** 四档 spec 工厂：`animateFloatAsState(…, animationSpec = Motion.state())`。 */
    fun <T> tap(): FiniteAnimationSpec<T> = tween(tapMillis, easing = standard)

    fun <T> state(): FiniteAnimationSpec<T> = tween(stateMillis, easing = standard)

    fun <T> reveal(): FiniteAnimationSpec<T> = tween(revealMillis, easing = decelerate)

    /**
     * 退场淡出一档：90ms + [accelerate]。
     *
     * 为什么单独一档：全仓 20+ 处原本写 `fadeOut(Motion.tap())`，用的是 [tap]（90ms + standard），
     * 时长对（退场就该比进场快），曲线不对（standard 两头都缓，退场看起来「黏」）。
     * 另写一个函数而不是让调用点自己拼 `tween(90, accelerate)`，是为了让「退场」在代码里
     * 有个名字——否则下一个人照归并表把退场改回 [reveal] 220ms，手感会明显变拖沓。
     */
    fun <T> exit(): FiniteAnimationSpec<T> = tween(tapMillis, easing = accelerate)

    /**
     * 退场用的 reveal 档（曲线换成 [accelerate]）。
     *
     * 为什么不能直接复用 [reveal]：它带的是 decelerate（起步快、收尾慢），那是"东西进来了"的
     * 曲线。同一个 spec 拿去做 `AnimatedVisibility(exit = shrinkVertically(Motion.reveal()))`，
     * 退场会先猛地缩一截再慢慢收——手感是"卡住"，不是"走了"。与 [pageExit] 同一个理由。
     */
    fun <T> revealExit(): FiniteAnimationSpec<T> = tween(revealMillis, easing = accelerate)

    fun <T> page(): FiniteAnimationSpec<T> = tween(pageMillis, easing = standard)

    /** 退场用的 page 档（曲线换成 accelerate）。`AnimatedVisibility(exit = …)` 用这个。 */
    fun <T> pageExit(): FiniteAnimationSpec<T> = tween(pageMillis, easing = accelerate)
}

/**
 * 系统「动画时长缩放」是否为 0（开发者选项「动画程序时长缩放」关 / 无障碍「移除动画」）。
 *
 * 为什么需要：`rememberInfiniteTransition` 的 7 处真实调用（地图脉冲、扫描波纹、倒计时环、
 * 骨架微光等）不会跟随系统设置——用户明确关掉动画后，屏幕上仍有东西一直在动。系统级
 * `ValueAnimator` 会读这个值，Compose 的无限动画不会，所以这里自己读一次。
 *
 * 用法：`if (rememberAnimationsEnabled()) { …无限动画… } else { …静止态… }`。
 * 注意**别**用它去关一次性过渡（`AnimatedVisibility` / `animateColorAsState`）——那些是
 * 状态变化的交代，关掉反而让人看不出发生了什么；系统设置的本意也是"少动"，不是"别交代"。
 */
@Composable
fun rememberAnimationsEnabled(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            ) > 0f
        }.getOrDefault(true)
    }
}
