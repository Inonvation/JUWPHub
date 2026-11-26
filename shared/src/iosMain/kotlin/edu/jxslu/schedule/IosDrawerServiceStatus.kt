package edu.jxslu.schedule

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import edu.jxslu.schedule.data.wenqu.WenquSession
import edu.jxslu.schedule.domain.AppClock
import edu.jxslu.schedule.domain.WenquRules
import edu.jxslu.schedule.ui.life.ujingFreeText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext

/**
 * 抽屉「江水生活」服务行右侧那几格状态（2026-11-26 晚）。
 *
 * Android 侧由 `MainActivity` 收集三个文案（开水余额 / U净 空闲数 / 问渠工时）再交给
 * `SideDrawerContent`；iOS 没有那个装配点，所以在这里按同一口径各自取一次。
 *
 * ## 三格的口径（与 Android 逐字一致）
 *
 * | 格 | Android 算法 | 这里 |
 * |---|---|---|
 * | 胖乖余额 | `"¥${balance.ticketText}"`，未登录不给 | 同（`QiekjRepository.queryBalance`） |
 * | U净空闲 | `loadUjingFreeText` 多店求和 → `空闲 N/M` / `暂无空闲` | 调**共享层**同一个函数（`ui/life/DrawerServiceStatus.kt`） |
 * | 问渠工时 | `${WenquRules.formatHours(hours)} 工时`，`hours <= 0` 不给 | 同（`WenquRepository.session`） |
 *
 * ## 什么时候取
 *
 * 只在抽屉展开时取（[visible]），与 Android 的 `LaunchedEffect(drawerVisible, …)` 同一闸门：
 * 抽屉是常驻组合，不看这个闸就会在用户根本没打开侧栏时也去打三次平台。
 *
 * U净那份要打收藏店的多个请求，另加 30 秒节流（`UJING_FREE_TTL_MILLIS`，与 Android 同值）。
 *
 * ## 没接的
 *
 * `ServiceActiveFlags`（四行服务各自的「进行中」描边高亮）没接：那四个布尔要聚合快趣骑行、
 * 胖乖 Working、趣智 watering、U净在案订单四份真相源，而 iOS 的趣智链路还没搬。
 * 少的是行描边，不是行本身——三行照常可点。
 */
@Composable
internal fun rememberIosDrawerServiceStatus(
    services: IosServices,
    visible: Boolean,
): IosDrawerServiceStatus {
    val qiekj = remember { services.qiekjRepository() }
    val ujing = remember { services.ujingRepository() }
    val ujingHouses = remember { services.ujingHouseStore() }
    val wenqu = remember { services.wenquRepository() }

    var waterBalance by remember { mutableStateOf<String?>(null) }
    var freeText by remember { mutableStateOf<String?>(null) }
    var ujingAt by remember { mutableStateOf(0L) }

    // 胖乖：展开时取一次余额。未登录 / 取不到 → null（行右侧不显示，与 Android 同）。
    LaunchedEffect(qiekj, visible) {
        if (!visible || qiekj == null) return@LaunchedEffect
        if (!qiekj.loggedIn.value) {
            waterBalance = null
            return@LaunchedEffect
        }
        waterBalance = withContext(Dispatchers.Default) {
            runCatching { qiekj.queryBalance() }.getOrNull()?.let { "¥${it.ticketText}" }
        }
    }

    // U净 空闲数：30 秒节流；退出登录时清空并清零节流起点（重新登录后立刻能取）。
    LaunchedEffect(ujing, visible) {
        if (!visible || ujing == null) return@LaunchedEffect
        if (!ujing.loggedIn.value) {
            freeText = null
            ujingAt = 0L
            return@LaunchedEffect
        }
        // 用共享层的 `AppClock`（唯一的时间入口）：它内部才是 `Clock.System`，
        // iosMain 直接写 `kotlinx.datetime.Clock.System` 在这条依赖线上编不过（CI 实测）。
        val now = AppClock.nowMillis()
        if (now - ujingAt < UJING_FREE_TTL_MILLIS) return@LaunchedEffect
        val text = withContext(Dispatchers.Default) { ujingFreeText(ujing, ujingHouses) }
        freeText = text
        // 只在正常走完时落节流时刻：抽屉收起取消掉的请求不算数，下次展开要补拉
        ujingAt = now
    }

    // 问渠工时：会话里带工时影子（后台刷新会推），所以订阅流即可，不必每次展开重取。
    // 仓库为 null（本平台没接）时用一条恒 null 的流顶着：不要在这里写 `?.let` 里套
    // `collectAsStateWithLifecycle`——那是条件组合，槽位会随 null 与否变化。
    val noSession = remember { MutableStateFlow<WenquSession?>(null) }
    val sessionFlow = wenqu?.session ?: noSession
    val wenquSession by sessionFlow.collectAsStateWithLifecycle(initialValue = null)
    val wenquHours = wenquSession?.takeIf { it.hours > 0.0 }
        ?.let { "${WenquRules.formatHours(it.hours)} 工时" }

    return IosDrawerServiceStatus(
        waterBalance = waterBalance,
        ujingFreeText = freeText,
        wenquHours = wenquHours,
    )
}

/** 抽屉服务行右侧的三格文案。null = 该格不显示（语义与 `lifeServiceRows` 的参数一致）。 */
internal data class IosDrawerServiceStatus(
    val waterBalance: String? = null,
    val ujingFreeText: String? = null,
    val wenquHours: String? = null,
)

/** U净 空闲数的刷新节流。与 Android `UJING_FREE_REFRESH_TTL_MILLIS` 同值。 */
private const val UJING_FREE_TTL_MILLIS = 30_000L
