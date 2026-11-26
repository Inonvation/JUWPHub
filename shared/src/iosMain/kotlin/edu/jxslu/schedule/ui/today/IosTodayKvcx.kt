package edu.jxslu.schedule.ui.today

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import edu.jxslu.schedule.domain.AppClock
import edu.jxslu.schedule.platform.Services
import edu.jxslu.schedule.platform.collectAsStateWithLifecycle
import edu.jxslu.schedule.ui.common.AppConfirmDialog
import edu.jxslu.schedule.ui.common.rememberAppHaptics
import edu.jxslu.schedule.ui.ebike.KvcxAction
import edu.jxslu.schedule.ui.ebike.KvcxRideController
import edu.jxslu.schedule.ui.ebike.kvcxConfirmDialog

/**
 * 今日页的静默查询闸门（与 Android `ui/today/TodayHost.kt` 的 `KVCX_RIDE_QUERY_TTL_MILLIS`
 * 同一个值、同一个用途）：切 Tab / 从二级页返回都会重建组合，不挡的话一次来回就打扰第三方
 * 接口一次。**不是轮询**——本文件只在进页与登录态翻真时各打一拍。
 */
private const val KVCX_RIDE_QUERY_TTL_MILLIS = 60_000L

/**
 * 今日页服务卡的快趣骑行槽位。null = 本平台没有快趣登录/控制器（整行不渲染）。
 *
 * ## 与 Android 哪一段同源
 *
 * 逐条来自 `app/.../ui/today/TodayHost.kt`：162–266 行（控制器状态收集、动作分发、槽位判据、
 * 退场留住最后一份数据）与 344–358 行（还车确认弹窗）。行本体不在这里——`KvcxActiveRow` /
 * `KvcxDoneRow` 早已在共享层（`ui/today/KvcxActiveRows.kt`），本文件只填槽位的**内容与判据**。
 *
 * ## null 的两种来路
 *
 * ① 宿主没给控制器（`controller == null`）：本平台没有快趣这条链；
 * ② 控制器在、但账号没登录：Android 的判据是 `kvcxInAppRide && kvcxLoggedIn`，这里只认后者。
 *
 * 对卡片是同一件事：`ActiveServiceSlot?` 为 null 就整行不渲染（`ActiveServiceCard` 的
 * `kvcx` 槽位语义，`design/ios-port.md` §11.2 ④）。**不要**在未登录时也返回一个 active=false
 * 的槽位：那会让卡片多一条「零高度但存在」的分隔线判据。
 *
 * ## 刻意的简化（都与 Android 有差别，原因逐条）
 *
 * 1. **能力闸只认 `loggedIn`**：Android 还要求 `ebikeUseMode.capabilities().inAppRide`（微信
 *    小程序方式不露这一行）。iOS 上先按登录态判定——小程序方式在 iOS 侧还没有入口，多读一条
 *    DataStore 冷流只会让首帧判据晚一拍（Android 2026-11-12 报的正是这种「卡片从底下跳上来」）。
 *    将来接小程序方式的免费计时行时，把 `ebikeUseMode` 加进这里即可（`TodayViewModel` 已有同款流）。
 * 2. **完成态停留仍按 8 秒算**：`state.returnSummary` 本身不会被清（它由骑行页的结算卡点掉），
 *    直接拿 `returnSummary != null` 当 `done` 会让「已还车」一直挂在今日页。所以停留用共享层的
 *    `rememberDoneLinger`（Android 同一个闸），两者同时成立才亮完成态。
 * 3. **没有 ON_RESUME**：Android 在 `LifecycleEventObserver` 里补一拍，iOS 侧 CMP 1.7.3 没有
 *    LifecycleOwner 口径（`rememberResumeTick()` 恒 0，见 `platform/LifecycleState.kt`），
 *    「进页」就等于「这份组合建立」——闸门因此主要防同一屏内登录态反复翻面。
 * 4. **卡片位置（`TodayServiceSlot.pinned`）不在这里算**：它要「同步状态」判据（见
 *    `TodaySlots.kt` 的 KDoc），由宿主的 `pinned` 参数给，槽位只管行内容。
 *
 * ## 调用点
 *
 * 必须在**今日页的页面根**上调用一次（与 `serviceCards` 同一处），**不要**放进
 * `TodayServiceSlot.content`：那个 lambda 只在卡片滚进视野时组合，滚出去就被取消，
 * 于是还车确认弹窗会在滚动时凭空消失、静默查询也会随滚动反复触发（同一件事写在
 * `TodayServiceSlot.effects` 的 KDoc 里）。
 *
 * @param controller 进程级单例（`IosServices.kvcxRideController()`）；与出行页用的是同一份，
 *   所以今日页开锁、骑行页立刻看到，反之亦然。
 * @param onOpenRide 整行落点（行内按钮管动作，行本体管「去看详情」）：宿主推
 *   `SubpageScreen.RIDE`。
 *
 * ## 未核实
 *
 * 本文件属 iosMain，Windows 上编不了 Kotlin/Native，只能由 macOS CI 编。已核到的是**形状**：
 * `collectAsStateWithLifecycle` / `rememberDoneLinger` / `rememberLastValue` / `AppConfirmDialog`
 * 都在 commonMain（CI 每个平台都编），`rememberAppHaptics` 的 iOS actual 已有
 * （`ui/common/Haptics.ios.kt`）；调用点写法与同目录的 `IosWeekActions` / `IosScheduleBackground*`
 * 同族。**未验**的是真机行为：登录后骑行中今日页是否出现这一行、还车确认与冷却的观感。
 */
@Composable
fun rememberIosKvcxServiceSlot(
    controller: KvcxRideController?,
    onOpenRide: () -> Unit,
): ActiveServiceSlot? {
    if (controller == null) return null

    // 状态与登录态都读控制器（进程单例）：今日页与出行页是同一份，各自查询会互相覆盖。
    val state by controller.state.collectAsStateWithLifecycle()
    val loggedIn by controller.loggedIn.collectAsStateWithLifecycle()
    // 免费窗口起点（`ebikeRideStartAt`，DataStore 冷流）：行上的倒计时条与「超时 X 分钟」读它。
    // flow 要 `remember` 住——每次重组都新建一个 `map` 出来的 Flow，收集协程会跟着重启一次。
    val prefs = remember { Services.current.displayPrefs() }
    val freeRideStartFlow = remember(prefs) { prefs.ebikeRideStartAt }
    val freeRideStartAt by freeRideStartFlow.collectAsStateWithLifecycle(initialValue = 0L)

    // 还车确认：确认键才是真正的动作点，触感在那里发一次（Android 同一口径；
    // 行内按钮 `ActionChip` / `ActiveRow` 自己已经 tap 过，这里再发就是用户报的「弹两次」）。
    val haptics = rememberAppHaptics()
    var pendingReturn by remember { mutableStateOf(false) }

    // 进页静默查一次（失败完全静默：页面主职责不是它）。**不轮询**——调用点只有这里，
    // 加上动作后的控制器自己刷新。
    var queriedAt by remember { mutableStateOf(0L) }
    LaunchedEffect(controller, loggedIn) {
        if (!loggedIn) return@LaunchedEffect
        val now = AppClock.nowMillis()
        if (now - queriedAt <= KVCX_RIDE_QUERY_TTL_MILLIS) return@LaunchedEffect
        queriedAt = now
        controller.query(quiet = true)
    }

    // 还车完成态的 8 秒停留（判据见类 KDoc 的简化第 2 条）：`ending` 只在还车动作跑过时成立，
    // 所以后台还车 / 到期结束不会误报成「刚还完」。
    val lingering = rememberDoneLinger(
        visible = state.ride != null,
        ending = state.busy == KvcxAction.RETURN,
    )
    val done = lingering && state.returnSummary != null

    // 退场那 160ms 卡片还会再画一次进行中内容，而那一刻 `ride` 已经是 null——留住最后一份
    // 非空值，否则淡出途中会变成空白行（Android 2026-10-31 的老坑，共享层已有 `rememberLastValue`）。
    val lastRide = rememberLastValue(state.ride)
    val lastSummary = rememberLastValue(state.returnSummary)

    if (pendingReturn) {
        IosKvcxConfirmAlertDialog(
            action = KvcxAction.RETURN,
            carLabel = state.ride?.carNum?.let { "车 $it" } ?: "选中的车",
            onConfirm = {
                haptics.tap()
                pendingReturn = false
                controller.returnBike()
            },
            onDismiss = { pendingReturn = false },
        )
    }

    // 只给「账号方式」（见类 KDoc 的简化第 1 条）。
    if (!loggedIn) return null

    return ActiveServiceSlot(
        active = state.ride != null,
        done = done,
        // 明确未锁车才点亮卡边：临时锁车 / 锁状态未知不点亮（与卡片里那条同口径）
        highlighted = state.ride?.locked == false,
        activeContent = {
            lastRide?.let { ride ->
                KvcxActiveRow(
                    ride = ride,
                    state = state,
                    freeStartAt = freeRideStartAt,
                    // 行内「开锁」= 对在案订单重发解锁（Android 的 `KvcxAction.RESUME` 档：
                    // 两段式未开锁与临时锁后继续骑是同一条调用）。`RETRY_UNLOCK` 在今日页没有
                    // 入口——行上只有「开锁 / 锁车 / 还车」三枚钮，别为它加一条死分支。
                    onUnlock = { controller.resumeRide() },
                    // 锁车免确认：订单与计费继续，误触代价低于多一次确认
                    onLock = { controller.tempLock() },
                    onReturn = { pendingReturn = true },
                    onOpen = onOpenRide,
                )
            }
        },
        doneContent = {
            KvcxDoneRow(summary = state.returnSummary ?: lastSummary, onOpen = onOpenRide)
        },
    )
}

/**
 * 今日页的还车确认弹窗（排版）。
 *
 * 规格与文案的唯一出处是 `ui/ebike` 的 `kvcxConfirmDialog`（`KvcxAction.RETURN` 档）；这里只做
 * 排版，**没有**公开的现成包装可借：Android 那份 `KvcxConfirmAlertDialog` 是
 * `app/.../ui/today/TodayHost.kt` 里的私有 Composable，跨模块看不见，重导出它等于为一行排版
 * 动 `:app`。所以这份是逐行同构的 iOS 私有副本：正文逐条编号 + `AppConfirmDialog(danger = true)`
 * （还车是花钱那一刻，确认键用错误色）。
 *
 * 与骑行页那份的**唯一差异**同样是「没有『以后开锁不再确认』勾选」：那个勾选只对开锁有意义，
 * 而今日页只弹还车确认——别为了「三处统一」把勾选搬过来，还车是花钱那一刻，一次性跳过等于
 * 把这次确认永久关掉。
 *
 * 正文的滚动与最大高度由 [AppConfirmDialog] 的正文槽套好，这里**不要**再包一层 `verticalScroll`
 * （嵌套滚动会让长文只滚一小段就停）。
 */
@Composable
private fun IosKvcxConfirmAlertDialog(
    action: KvcxAction,
    carLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val dialog = kvcxConfirmDialog(action, carLabel) ?: return
    AppConfirmDialog(
        title = dialog.title,
        confirmLabel = dialog.confirmLabel,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        danger = true,
    ) {
        dialog.points.forEachIndexed { index, point ->
            Row(modifier = Modifier.padding(bottom = 8.dp)) {
                Text(
                    text = "${index + 1}.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.width(20.dp),
                )
                Text(text = point, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
