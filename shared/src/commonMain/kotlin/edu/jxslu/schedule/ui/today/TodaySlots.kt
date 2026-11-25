package edu.jxslu.schedule.ui.today

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import edu.jxslu.schedule.ui.common.NoticeTone

/**
 * 今日页的槽位类型（iOS 移植 V1，2026-11-25）。
 *
 * ## 为什么今日页需要槽位
 *
 * 今日页是**所有能力的汇合点**：它原来直接 import 八个 ui 包。其中
 * `ui/ebike`（高德 SDK）、`ui/qzxy`（蓝牙传输层）、`ui/reminder`（通知 + WorkManager）、
 * `ui/jwvw`（WebView）在 iOS 上**结构性做不到**（`design/ios-port.md` §12.1）。
 * 搬不动的那些包，页面自己不能 import——于是把它们的**渲染 + 状态**一起交给宿主，
 * 页面只留一个槽。
 *
 * 「进行中服务卡」的**本体**已搬进共享层（`ui/today/ActiveServiceCard.kt`，V2 2026-11-25）：
 * 开水 / 趣智 / U净 三行与整卡状态机都在那里，只有快趣那两行（数据在 `ui/ebike`）仍由宿主
 * 填 `ActiveServiceSlot`。所以这个槽位现在只负责**位置判据 + 页面级副作用**，
 * `content` 里填的是共享层那张卡（iOS 也一样，只接开水与 U净）。
 *
 * 这是 `design/ios-port.md` §11.2 ④ 的落地口径：**`null` = 本平台确实没有这块能力**
 * （不是「以后补」的占位），对应内容整块不渲染。给一个点了没反应的入口比没有更糟。
 *
 * ## 槽位清单（本文件只定义形状复杂的那一个，其余是 `TodayScreen` 的普通参数）
 *
 * | 槽位 | 形状 | null 的含义 | 谁填 |
 * |---|---|---|---|
 * | `serviceCards` | [TodayServiceSlot] | 本平台一项进行中服务都没有 → 整卡不渲染 | 两端宿主（iOS 只填开水 / U净） |
 * | `qzxyPanel` | `@Composable (onDismiss, onNotice) -> Unit` | 没有趣智开水面板 | Android `ui/today/TodayHost.kt` |
 * | `brandIcon` | `@Composable (GuessTarget) -> Painter?` | 没有 `ic_brand_*` 位图素材 | Android `GuessYouWantCardPlatform.kt` |
 * | `onScanCode` | `(onResult: (String?) -> Unit) -> Unit` | 没有扫码能力 | Android `ui/today/TodayHost.kt` |
 * | `onOpenSubpage` | `(SubpageScreen, String?) -> Unit` | 没有二级页栈 | 两端宿主 |
 * | `onRefreshDormRepair` | `() -> Unit` | 没有报修同步链路 | Android `MainActivity` |
 *
 * ## 升级条件（什么时候可以删掉某个槽位）
 *
 * 某个服务包整体搬进 `shared/commonMain` 之后，那个槽位就可以拆回页面自己的参数：
 * - `ui/ebike` → 需要 iOS 高德 SDK（`AMap3DMap`）或换成别的地图底图；
 * - `ui/qzxy` → 需要 `CoreBluetooth` 版的 `QzxyGattLink`（协议帧已在共享层，可复用）；
 * - 洗好提醒 / 导入回执 → 需要 `UNUserNotificationCenter` 那份实现。
 *
 * 在那之前不要「为了对称」给槽位补空实现：空实现会让页面以为能力存在。
 */

/**
 * 今日页「进行中服务」卡的槽位（快趣骑行 / 胖乖开水 / 趣智洗澡 / U净 洗烘）。
 *
 * ## 为什么整块是一个槽位而不是四块
 *
 * 四项服务共用**同一张卡、同一套行间分隔与完成态停留**（`ActiveServiceCard`：呼吸点、
 * 行退场沿用最后形态、8 秒完成态停留、卡边高亮）。拆成四个槽位等于把那套状态机也拆开——
 * 分隔线的显隐依赖「上一行在不在」，完成态的停留跨行共用一份闸门，拆开必然改行为。
 * 所以按「整块卡片」反转，而不是按服务反转。
 *
 * ## [pinned] 为什么必须由宿主算
 *
 * 位置判据是「有没有在案骑行」：骑行是最需要一眼看到的状态，有骑行时卡片提到课程区之前
 * （2026-10-22 用户口径）。这个判据必须用**同步**状态算——快趣单例的 `state.ride != null`
 * 加登录态，**不是**过了能力闸的那份（能力闸读 DataStore 冷流，新组合首帧恒为默认档）。
 * 判据晚一帧，`animateItem` 就会把卡片从列表下方滑到顶部（2026-11-12 用户报
 * 「切 Tab 时从底部跳到顶部」）。
 *
 * 也就是说 [pinned] 与卡片本体读的必须是同一份状态，而那份状态在 `:app` 的控制器里。
 * 页面只读这个布尔，不猜。
 *
 * ## 谁填
 *
 * Android：`ui/today/TodayHost.kt` 的 `rememberTodayHostSlots`（它收四项服务的真相源、
 * 挂 RESUMED 静默查询、转发动作与确认弹窗，并把快趣 / 趣智两个槽位交给卡片）。
 * iOS：`MainViewController` 的今日 Tab——只接开水（胖乖，HTTP）与 U净，快趣 / 趣智 /
 * 品牌位图传 `null`，那两行整行不渲染。
 *
 * @property pinned 卡片是否置顶到课程区之前（有在案骑行 / 完成态停留 / 冷启动位置提示）。
 * @property content 卡片本体。`Modifier` 由列表层给（带 `animateItem`），
 *   卡片自己决定渲染不渲染——四项全无时它整卡收平，页面不需要知道判据。
 * @property effects 四项服务的**页面级副作用**：快趣控制器的事件流（`notices` → 页面 Snackbar）、
 *   RESUMED 时的静默查询与对表、趣智用水对账、还车 / 结束免费计时两张确认弹窗。
 *
 *   **必须与 [content] 分开**：`content` 只在卡片滚进视野时组合，滚出去就被取消——
 *   把事件流收在那里，用户往下翻一屏就会漏掉「开锁失败」这类提示；确认弹窗更得挂在
 *   页面根上，不然卡片一滚动弹窗就消失。页面在根上无条件调用一次，并把手里的
 *   Snackbar 出口（`onNotice`）传进来。
 * @property onRefresh 下拉刷新时补一拍：快趣在案订单（**绕过** 60 秒节流）+ U净 订单对表。
 *   页面自己的 `onRefresh` 只管课表 / 开水 / 报修，这两项的真值源在 `:app`。
 *   `null` = 没有需要补拍的第三方查询。
 */
class TodayServiceSlot(
    val pinned: Boolean,
    val content: @Composable (Modifier) -> Unit,
    val effects: (@Composable (onNotice: (String, NoticeTone) -> Unit) -> Unit)? = null,
    val onRefresh: (() -> Unit)? = null,
)
