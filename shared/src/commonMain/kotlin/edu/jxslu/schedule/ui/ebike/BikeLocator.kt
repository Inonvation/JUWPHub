package edu.jxslu.schedule.ui.ebike

import edu.jxslu.schedule.domain.AppClock
import edu.jxslu.schedule.domain.BikeNearby
import edu.jxslu.schedule.domain.Gcj02
import edu.jxslu.schedule.platform.Services
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf

/** 一次定位的结果。 */
sealed interface LocateResult {
    /**
     * 成功。[lat] / [lng] **已经是 GCJ-02**，可直接当瓦片与接口的中心点。
     * [accuracyMeters] 是这次读数的精度半径（米）；0 = 系统没给，地图据此画精度圈。
     */
    data class Ok(
        val lat: Double,
        val lng: Double,
        val accuracyMeters: Float = 0f,
        /** 传给快趣 `locationSource`：新定位 = realtime，五分钟内的缓存 = session。 */
        val source: String = "realtime",
    ) : LocateResult

    /** 失败，[message] 直接给 Snackbar 用。 */
    data class Failed(
        val message: String,
        /**
         * 失败分档（2026-11-21）：页面据此决定提示里的「去设置」跳到哪一页，
         * 以及**进页那条自动定位失败要不要说一句**（只有 [actionable] 的两档值得打扰）。
         */
        val issue: Issue = Issue.NoFix,
    ) : LocateResult {

        /** 用户自己能改的两档：放开定位权限 / 打开系统定位总开关。其余只能等信号或换地方。 */
        val actionable: Boolean get() = issue == Issue.Permission || issue == Issue.ServiceOff
    }

    /** 定位失败的成因（见 [Failed.issue]）。 */
    enum class Issue {
        /** 没给定位权限。 */
        Permission,

        /** 有权，但系统「位置信息」总开关关着 / 没有可用 provider。 */
        ServiceOff,

        /** 这台设备没有定位服务。 */
        Unsupported,

        /** 权限与服务都正常，只是等不到一条有效读数（室内 / 信号差）。 */
        NoFix,
    }
}

/** 一次定位的原始读数（WGS84），只留挪蓝点要看的字段，纯数据可单测。 */
data class LocationFix(
    val lat: Double,
    val lng: Double,
    /** 精度半径（米）；0 = 系统没给。 */
    val accuracyMeters: Float,
)

/**
 * 平台定位源给的一次**原始**读数（WGS84）。
 *
 * 两个实现：Android（`LocationManager`，`shared/androidMain` 的 `AndroidBikeLocation`）、
 * iOS（`CLLocationManager`，`shared/iosMain` 的 `IosBikeLocation`）。
 * 坐标系转换、挪点判据、失败文案**都不在这里**——它们是两端的同一份口径，
 * 见 [BikeLocator] 的 `toResult` / [DotGate]。
 */
sealed interface FixOutcome {
    /** [cached] = 这条来自系统缓存（五分钟内的上次读数），快趣侧记 `session`。 */
    data class Ok(val fix: LocationFix, val cached: Boolean = false) : FixOutcome

    /**
     * 失败。[message] 只在平台有**比通用文案更准**的说法时给（例如「正在等待可用的定位来源」），
     * 否则留 null 用 [BikeLocator] 的通用文案——文案的归属留在共享层，不让两端各写一份。
     */
    data class Failed(val issue: LocateResult.Issue, val message: String? = null) : FixOutcome
}

/**
 * 平台定位源的缝（iOS 移植 P4，2026-11-25）。
 *
 * `android.location` 与 CoreLocation 是两套模型，但页面要的东西是同一件：**一条 WGS84 读数
 * 或一条失败成因**。投影到这两件之后，`BikeLocator` 里那套「蓝点怎么挪、坐标怎么转、
 * 失败怎么说」就是两端共用的唯一实现。
 *
 * `null`（装配根给不出来）= 本平台没有定位能力：地图仍可用（建图中心落在校园中心、
 * 用户可手动拖图），只是没有蓝点与「距你」距离。
 */
interface BikeLocationSource {

    /** 精确或粗略任一授权即可（API 31+ 用户可能只给「大致位置」；iOS 只有一种开关）。 */
    fun hasPermission(): Boolean

    /**
     * 取一次读数。
     *
     * [acceptCached] = false 时**跳过缓存**，只等一条实时读数（`RideScreen.locate` 的第二段：
     * 缓存读数落地后再收敛一次镜头，理由见 [shouldRecenterFromCache]）。
     */
    suspend fun currentFix(timeoutMs: Long, acceptCached: Boolean): FixOutcome

    /**
     * 连续读数流（页面可见期间收集）。平台按 2 秒节奏发，先补一条缓存读数；
     * 定位服务关着时**不结束**（原地等它恢复），由收集方的生命周期取消。
     */
    fun fixUpdates(): Flow<FixOutcome>
}

/**
 * 单车地图页的一次性定位（DESIGN §3.9）。
 *
 * 2026-11-25 起整份逻辑在共享层：**平台只给 WGS84 读数**（[BikeLocationSource]），
 * 坐标转换（[Gcj02]）、失败文案、挪点判据（[DotGate] / [shouldMoveDot]）留在这里一份。
 * 搬迁前它直调 `LocationManager`，那段搬进了 `shared/androidMain`。
 *
 * 权限由调用方（页面）负责申请：本类只在**已授权**的前提下工作，没有权限直接返回失败，
 * 不会中途弹系统框。
 */
object BikeLocator {

    /** 等一次有效定位的上限。超了就给失败提示，不要挂着转圈。 */
    const val FIX_TIMEOUT_MS = 8_000L

    /** 缓存定位可接受的新鲜度：五分钟内的上次定位直接拿来用，省一次等待。 */
    const val MAX_LAST_KNOWN_AGE_MS = 5 * 60_000L

    /** 连续定位流的请求节奏：2 秒一次对步行速度绰绰有余，再密只是费电。 */
    const val STREAM_MIN_TIME_MS = 2_000L

    /** 定位服务还没开 / 没有可用 provider 时的重看间隔（平台内部用）。 */
    const val PROVIDER_POLL_MS = 3_000L

    /** 本平台没有定位能力时的说法（装配根给 null，见 [BikeLocationSource]）。 */
    const val NO_SOURCE_TEXT = "本平台还没有定位能力，可手动拖动地图找车"

    /** 平台定位源；null = 本平台没有定位能力。 */
    private fun source(): BikeLocationSource? = Services.current.bikeLocation()

    /** 精确或粗略任一授权即可（与平台源同口径，见 [BikeLocationSource.hasPermission]）。 */
    fun hasPermission(): Boolean = source()?.hasPermission() == true

    /**
     * 取一次定位并转成 GCJ-02。
     *
     * 顺序由平台源决定（Android：先看五分钟内的缓存定位，没有就同时挂网络与 GPS 两条更新，
     * 谁先给结果用谁）。转换与文案在这里——两端同一份。
     */
    suspend fun currentLocation(
        timeoutMs: Long = FIX_TIMEOUT_MS,
        acceptCached: Boolean = true,
    ): LocateResult {
        val source = source()
            ?: return LocateResult.Failed(NO_SOURCE_TEXT, LocateResult.Issue.Unsupported)
        return when (val outcome = source.currentFix(timeoutMs, acceptCached)) {
            is FixOutcome.Ok ->
                toResult(outcome.fix, if (outcome.cached) "session" else "realtime")

            is FixOutcome.Failed -> failure(outcome)
        }
    }

    /**
     * 页面可见期间的连续定位（DESIGN §3.9「蓝点实时更新」）。与 [currentLocation] 的分工：
     * 一次性定位管「把镜头带过去 + 重查」，这条流只管用户走动时蓝点不掉队——
     * 挪镜头、重查车辆接口都不是它的事（镜头会把用户拖好的视野抢回来；接口禁止轮询）。
     *
     * 过滤（[DotGate]：挪点闸门 + 粗读数逃生口）留在这里；平台源只管按节奏给读数。
     * **定位源关着时这条流不结束**的语义在平台源里（见 [BikeLocationSource.fixUpdates]）。
     */
    fun updates(): Flow<LocateResult> {
        val source = source()
            ?: return flowOf(
                LocateResult.Failed(NO_SOURCE_TEXT, LocateResult.Issue.Unsupported),
            )
        return flow {
            val gate = DotGate()
            source.fixUpdates().collect { outcome ->
                when (outcome) {
                    is FixOutcome.Failed -> emit(failure(outcome))
                    is FixOutcome.Ok -> {
                        if (gate.accept(outcome.fix, AppClock.nowMillis())) {
                            emit(
                                toResult(
                                    outcome.fix,
                                    if (outcome.cached) "session" else "realtime",
                                ),
                            )
                        }
                    }
                }
            }
        }
    }

    /** 失败成因 → 对用户说的话（唯一出处；平台源只给成因，必要时给更准的一句）。 */
    internal fun failure(outcome: FixOutcome.Failed): LocateResult.Failed {
        val text = outcome.message ?: when (outcome.issue) {
            LocateResult.Issue.Permission -> "没有定位权限，请在系统设置里允许后再试"
            LocateResult.Issue.ServiceOff -> "手机定位服务未开启，请打开后重试"
            LocateResult.Issue.Unsupported -> "这台设备没有定位服务"
            LocateResult.Issue.NoFix -> "暂时取不到位置，可手动拖动地图找车"
        }
        return LocateResult.Failed(text, outcome.issue)
    }

    private fun toResult(fix: LocationFix, source: String): LocateResult {
        val gcj = Gcj02.toGcj02(fix.lat, fix.lng)
        return LocateResult.Ok(gcj.lat, gcj.lng, fix.accuracyMeters, source)
    }
}

/**
 * 缓存读数之后再拿一条实时读数时，两者至少差这么多米才值得为它挪镜头
 * （实时读数自己没给精度时也用它当门槛，口径见 [shouldRecenterFromCache]）。
 */
private const val RECENTER_FROM_CACHE_MIN_METERS = 30.0

/**
 * 缓存定位落地之后，要不要用这条实时读数再收敛一次镜头与蓝点
 * （`RideScreen.locate` 的两段用法，2026-11-21）。
 *
 * 为什么不能无条件收敛：一次性定位的第一段吃的是系统缓存（准确说是一个 GPS 点），第二段
 * 等到的第一条实时读数常常是**网络点**（先回），精度 30~2000 米、位置能偏出几十米。无条件
 * 把镜头挪过去，站定不动的人会看到地图/蓝点被拽走——正是 [shouldMoveDot] 那条「更粗不认」
 * 要防的事。所以门槛取**这条读数自己的精度**（下限 [RECENTER_FROM_CACHE_MIN_METERS]）：
 * 差得还没它自己承认的误差大，说明缓存点与它并不矛盾，镜头本来就够准，不动；
 * 差得超出噪声（人确实挪了、或缓存点是上次别处的），才值得收敛。
 */
fun shouldRecenterFromCache(
    cacheLat: Double,
    cacheLng: Double,
    liveLat: Double,
    liveLng: Double,
    liveAccuracyMeters: Float,
): Boolean {
    val moved = BikeNearby.distanceMeters(cacheLat, cacheLng, liveLat, liveLng)
    val threshold = if (liveAccuracyMeters > 0f) {
        liveAccuracyMeters.toDouble().coerceAtLeast(RECENTER_FROM_CACHE_MIN_METERS)
    } else {
        RECENTER_FROM_CACHE_MIN_METERS
    }
    return moved >= threshold
}

/** 蓝点至少挪了这么多米才真的动。 */
const val DOT_MOVE_MIN_METERS = 4f

/** 原地没动时，新读数比正在画的那个准到这个倍数（如 20m → 10m）才值得换。 */
private const val DOT_ACCURACY_GAIN_RATIO = 0.6f

/**
 * 新读数比正在画的**粗**到这个倍数以上就不认（[shouldMoveDot]）。
 *
 * 防的是 GPS 与网络双源交替：网络定位在楼群里能偏出几十上百米，而每两秒一条的读数
 * 位移都远超 [DOT_MOVE_MIN_METERS]，光靠位移闸门拦不住——蓝点会在准的 GPS 点与粗的
 * 网络点之间来回跳（用户报的「定位乱跳」）。两者都粗（只有网络可用）时这条不触发，
 * 照下面的位移口径正常跟随。
 */
private const val DOT_COARSER_RATIO = 2.5f

/**
 * 连续定位流里决定「这个新读数要不要真的挪蓝点」（DESIGN §3.9，纯逻辑可单测）。
 *
 * 三条口径：
 * 1. 明显更粗的读数（精度比正在画的差 [DOT_COARSER_RATIO] 倍以上）**一律不认**——双源
 *    交替里的网络点，别拿它把蓝点从准位置上拽走；
 * 2. 挪了够远（≥ [DOT_MOVE_MIN_METERS]）——走路时蓝点跟着人走；
 * 3. 原地没动但精度显著提升——从网络点换到 GPS 点时换上更准的位置。
 *
 * 其余一律不挪：站定时每个读数都带几米随机抖动，不滤的话蓝点会在原地颤，列表里的
 * 「距你」也跟着一跳一跳。
 */
fun shouldMoveDot(previous: LocationFix?, next: LocationFix): Boolean {
    if (previous == null) return true
    val accuracy = next.accuracyMeters
    val shownAccuracy = previous.accuracyMeters
    if (accuracy > 0f && shownAccuracy > 0f && accuracy > shownAccuracy * DOT_COARSER_RATIO) {
        return false
    }
    val moved = BikeNearby.distanceMeters(previous.lat, previous.lng, next.lat, next.lng)
    if (moved >= DOT_MOVE_MIN_METERS) return true
    return accuracy > 0f && shownAccuracy > 0f &&
        accuracy <= shownAccuracy * DOT_ACCURACY_GAIN_RATIO
}

/** 两条**连续的**粗读数彼此离这么近，才算「同一片」而不是单条跳点。 */
private const val DOT_COARSE_CONFIRM_METERS = 80.0

/**
 * 多久没有任何读数被采纳之后，才允许粗读数接管（见 [DotGate]）。
 *
 * 站定时 GPS 每两秒还在刷新基准，这个计时永远到不了门槛；只有「GPS 彻底没了」才会走到。
 */
private const val DOT_STALE_ACCEPT_MS = 30_000L

/**
 * 蓝点的挪点判据 + 一条**逃生口**（2026-11-19 修「进楼后蓝点冻住」）。
 *
 * [shouldMoveDot] 的第 1 条是硬规则：明显更粗的读数一律不认，拦的是双源交替里的单条跳点。
 * 但基准（`shown`）**只在读数被采纳时前进**：人走进楼里、GPS 彻底没有，剩下的只有精度
 * 60~2000 米的网络点，每条都比 20 米的旧基准粗 2.5 倍以上 → 全部被拒 → 蓝点冻在原地，
 * 跟随、精度圈、「距你」一起停，直到重新拿到一条 ≤50 米的 GPS 读数（可能要走出这栋楼）。
 *
 * 逃生口**两条同时成立**才放行，缺一条都会把「定位乱跳」放回来：
 * 1. 连续两条粗读数彼此相距 ≤ [DOT_COARSE_CONFIRM_METERS]——单条跳点凑不出这一对；
 * 2. 已经 [DOT_STALE_ACCEPT_MS] 没有任何读数被采纳——站定时 GPS 还在刷新基准，到不了门槛。
 */
class DotGate {

    private var shown: LocationFix? = null
    private var lastAcceptedAt = 0L

    /** 上一条被拒的粗读数（只为凑「连续两条」这一对）。 */
    private var coarseCandidate: LocationFix? = null

    /** 这个读数要不要真的挪蓝点；true 时内部基准一起前进。 */
    fun accept(next: LocationFix, nowMillis: Long): Boolean {
        val previous = shown
        if (previous == null) {
            adopt(next, nowMillis)
            return true
        }
        if (shouldMoveDot(previous, next)) {
            adopt(next, nowMillis)
            return true
        }
        val coarser = next.accuracyMeters > 0f && previous.accuracyMeters > 0f &&
            next.accuracyMeters > previous.accuracyMeters * DOT_COARSER_RATIO
        if (!coarser) {
            coarseCandidate = null
            return false
        }
        val candidate = coarseCandidate
        coarseCandidate = next
        if (candidate == null) return false
        if (BikeNearby.distanceMeters(
                candidate.lat,
                candidate.lng,
                next.lat,
                next.lng,
            ) > DOT_COARSE_CONFIRM_METERS
        ) {
            return false
        }
        if (nowMillis - lastAcceptedAt < DOT_STALE_ACCEPT_MS) return false
        adopt(next, nowMillis)
        return true
    }

    private fun adopt(fix: LocationFix, nowMillis: Long) {
        shown = fix
        lastAcceptedAt = nowMillis
        coarseCandidate = null
    }
}
