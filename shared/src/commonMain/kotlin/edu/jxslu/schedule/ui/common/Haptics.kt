package edu.jxslu.schedule.ui.common

import androidx.compose.runtime.Composable

/**
 * 触感反馈的跨平台缝（iOS 移植，2026-11-24）。
 *
 * 根因：Compose 的 LocalHapticFeedback 只暴露 LongPress/TextHandleMove 两种语义，
 * 盖不住「轻点按钮」这类普通点击；而设置里的触感开关是全局偏好（DisplayPrefs.hapticsEnabled），
 * 分散在各个调用点各自查开关会漏。所以统一在这里收口：组件只管调 [tap]/[toggle]，
 * 开关状态由 [HapticsSettings] 在每次调用时读最新值，关闭时直接短路，不产生振动。
 *
 * 平台实现在 `androidMain`（`HapticFeedbackConstants`）与 `iosMain`（UIFeedbackGenerator），
 * 调用点只认这个接口，60+ 个按钮/列表行一行都不用改。
 *
 * ## 四档映射表（2026-11-06 补：此前只有"每档是什么"，没有"哪档用在哪"）
 *
 * | 档 | 用在哪 | Android 落地常量 | iOS 落地 |
 * |----|--------|------------------|----------|
 * | [tap] | 普通点击：列表行、卡片、图标按钮、课程块、底栏 tab | CONTEXT_CLICK | LIGHT 冲击 |
 * | [toggle] | 选中切换：开关、单选、筛选 chip、侧栏条目高亮 | CONTEXT_CLICK | 选择反馈 |
 * | [success] | 动作成功：开锁成功、支付完成、提交成功这类"等了几秒终于成了" | CONFIRM（API 30+）/ CONTEXT_CLICK | SUCCESS 通知反馈 |
 * | [tick] | 刻度感：滚轮停稳到新档位、周次切换 | CLOCK_TICK | 选择反馈 |
 *
 * 三条口径：
 *
 * 1. **失败不震**。触感是"操作被接受了"的确认，报错交给 Snackbar 和文案；失败还震一下，
 *    用户会以为点上了，反而更困惑。
 * 2. **同一次操作只震一次**。典型坑：`selectable`/`clickable` 里震一次、外层 `onSelect`
 *    再震一次，手感就是双击。所以组件层给了触感，调用方就别再给（见 `AppCard` 的 KDoc）。
 * 3. [tap] 与 [toggle] 现在落到**同一个**系统常量，区别只在语义位——留着两档是为了以后
 *    真能分化时不用改 60+ 个调用点。别以为换了 [toggle] 手感会变，它不会。
 *
 * iOS 侧只有三档语义（之所以这样降级，见 `Haptics.ios.kt`）。
 */
interface AppHaptics {

    /** 普通点击（设置入口、图标按钮、课程块）。 */
    fun tap()

    /** 状态切换（开关、选项选中）。当前与 [tap] 同常量，见接口 KDoc 第 3 条口径。 */
    fun toggle()

    /** 动作成功（开锁成功这类"等了几秒终于成了"的时刻）。 */
    fun success()

    /** 极轻的刻度感（滚轮停稳到新档位）。 */
    fun tick()
}

/**
 * 组装 [AppHaptics]：开关状态来自 [HapticsSettings]（默认开）。
 *
 * 做成 `@Composable` + expect/actual，是因为 Android 侧要拿 `LocalView` 才能发
 * `performHapticFeedback`，而 Compose 的 `LocalView` 只能在组合内读；iOS 侧虽然不需要 `View`，
 * 但保留同一个签名，调用点才不用分成两种写法。
 */
@Composable
expect fun rememberAppHaptics(): AppHaptics

/**
 * 触感总开关的注入点。`:app` 启动时把 DataStore 里的显示偏好接进来；
 * iOS 侧暂时没有这个设置项，保持默认开启。
 *
 * ### 为什么是函数式对象、而不是 `rememberAppHaptics(enabled: Boolean)` 这类普通参数
 *
 * 全应用 60+ 个调用点（列表行、卡片、图标按钮、课程块、底栏 tab……）分布在几十个文件里，
 * 让每个调用点自己把开关传进来就是 60+ 处重复接线，漏一处就是"关掉全局开关那几个按钮还在震"。
 * 收口成进程级的一个布尔，调用点签名保持零参数。
 *
 * ### 为什么是可变的 `var`、而不是 CompositionLocal
 *
 * 开关值只在**手指按下那一刻**读一次（`if (!isEnabled()) return`），读到最新的那一份就够；
 * 它不参与任何 UI 输出，改它不需要触发重组。用 CompositionLocal 反而要把它塞进 Composition
 * 并引起订阅它的组合重算——为了一个"读一次就走"的布尔付出重组成本，没必要。
 * 也正因如此，开关切换后**不需要**重建任何 `AppHaptics` 实例。
 *
 * 读方：两个平台的 `rememberAppHaptics` actual。写方：`:app` 的 `JuwApplication.onCreate`
 * （收集 `ScheduleRepository.hapticsEnabled`，每次发射覆盖这个 lambda）。
 */
object HapticsSettings {
    var isEnabled: () -> Boolean = { true }
}
