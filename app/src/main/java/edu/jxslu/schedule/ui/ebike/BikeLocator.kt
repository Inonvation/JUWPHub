package edu.jxslu.schedule.ui.ebike

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import edu.jxslu.schedule.domain.BikeNearby
import edu.jxslu.schedule.domain.Gcj02
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeoutOrNull

/** 一次定位的结果。 */
internal sealed interface LocateResult {
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
internal data class LocationFix(
    val lat: Double,
    val lng: Double,
    /** 精度半径（米）；0 = 系统没给。 */
    val accuracyMeters: Float,
)

/**
 * 单车地图页的一次性定位（DESIGN §3.9）。放在 UI 层而不是 `data/`：
 * 它是「点按钮取一次坐标」的界面动作，没有状态、没有缓存、也不给别的页面用。
 *
 * 只用平台 [LocationManager]，不引 Google Play Services 的融合定位——那套在国内机型上
 * 本来也不可用。坐标转换在 [Gcj02]（纯 JVM 可测）。
 *
 * 权限由调用方（页面）负责申请：本类只在**已授权**的前提下工作，没有权限直接返回失败，
 * 不会中途弹系统框。
 */
internal object BikeLocator {

    /** 等一次有效定位的上限。超了就给失败提示，不要挂着转圈。 */
    private const val FIX_TIMEOUT_MS = 8_000L

    /** 缓存定位可接受的新鲜度：五分钟内的上次定位直接拿来用，省一次等待。 */
    private const val MAX_LAST_KNOWN_AGE_MS = 5 * 60_000L

    /** 连续定位流的请求节奏：2 秒一次对步行速度绰绰有余，再密只是费电。 */
    private const val STREAM_MIN_TIME_MS = 2_000L

    /** 位移门槛交给 [shouldMoveDot] 在内存里滤，平台层不再按距离截流。 */
    private const val STREAM_MIN_DISTANCE_M = 0f

    /**
     * 定位服务还没开 / 没有可用 provider 时的重看间隔。
     *
     * 只在这两种状态下跑（页面可见 + 定位不可用），一旦有 provider 就转成平台回调，
     * 不再轮询；页面退后台由收集方的生命周期取消掉。
     */
    private const val PROVIDER_POLL_MS = 3_000L

    /** 精确或粗略任一授权即可（API 31+ 用户可能只给「大致位置」）。 */
    fun hasPermission(context: Context): Boolean =
        isGranted(context, Manifest.permission.ACCESS_FINE_LOCATION) ||
            isGranted(context, Manifest.permission.ACCESS_COARSE_LOCATION)

    private fun isGranted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    /**
     * 取一次定位并转成 GCJ-02。
     *
     * 顺序：先看五分钟内的缓存定位（瞬时返回），没有再同时挂网络与 GPS 两条更新，
     * 谁先给结果用谁。网络定位先到也无所谓——把地图中心落到用户那一片，粗略坐标就够。
     *
     * [acceptCached] = false 时**跳过缓存**，只等一条实时读数。页面在缓存读数落地之后
     * 再取一次用（见 `RideScreen.locate`）：连续定位流里的同一批实时读数要过 [DotGate]，
     * 精度从 GPS 掉到网络点（楼里只有网络定位）时会被判成「明显更粗」拒掉，等它的
     * 逃生口要 30 秒——那段时间镜头就停在缓存点上。
     */
    suspend fun currentLocation(
        context: Context,
        timeoutMs: Long = FIX_TIMEOUT_MS,
        acceptCached: Boolean = true,
    ): LocateResult {
        if (!hasPermission(context)) {
            return LocateResult.Failed("没有定位权限，请在系统设置里允许后再试", LocateResult.Issue.Permission)
        }
        val manager = context.getSystemService(LocationManager::class.java)
            ?: return LocateResult.Failed("这台设备没有定位服务", LocateResult.Issue.Unsupported)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && !manager.isLocationEnabled) {
            return LocateResult.Failed("手机定位服务未开启，请打开后重试", LocateResult.Issue.ServiceOff)
        }

        if (acceptCached) freshLastKnown(manager)?.let { return toResult(it.toFix(), "session") }

        val providers = enabledProviders(manager)
        val fix = awaitFix(manager, providers, timeoutMs)
            ?: return LocateResult.Failed("暂时取不到位置，可手动拖动地图找车", LocateResult.Issue.NoFix)
        return toResult(fix.toFix(), "realtime")
    }

    /**
     * 页面可见期间的连续定位（DESIGN §3.9「蓝点实时更新」）。与 [currentLocation] 的分工：
     * 一次性定位管「把镜头带过去 + 重查」，这条流只管用户走动时蓝点不掉队——
     * 挪镜头、重查车辆接口都不是它的事（镜头会把用户拖好的视野抢回来；接口禁止轮询）。
     *
     * 先发五分钟内的缓存定位（蓝点立刻出现，不等 GPS 慢慢找），再挂网络与 GPS 两条更新，
     * 经 [DotGate]（[shouldMoveDot] + 粗读数逃生口）滤掉原地抖动后逐条发 GCJ-02 结果。
     *
     * **定位服务关着 / 没有可用 provider 时这条流不结束**（2026-11-19 修）：它原来发一条
     * [LocateResult.Failed] 就 `return@flow`，而页面的收集跑在 `repeatOnLifecycle(STARTED)`
     * 里——从通知栏把定位打开、Activity 只走 ON_PAUSE 不走 STOP，流不会重启，于是蓝点、
     * 跟随、「距你」整页停摆，只能退出页面再进。现在原地等它恢复（页面不可见时由
     * 生命周期的取消收掉）。
     */
    fun updates(context: Context): Flow<LocateResult> = flow {
        if (!hasPermission(context)) {
            emit(LocateResult.Failed("没有定位权限，请在系统设置里允许后再试", LocateResult.Issue.Permission))
            return@flow
        }
        val manager = context.getSystemService(LocationManager::class.java)
        if (manager == null) {
            emit(LocateResult.Failed("这台设备没有定位服务", LocateResult.Issue.Unsupported))
            return@flow
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && !manager.isLocationEnabled) {
            emit(LocateResult.Failed("手机定位服务未开启，请打开后重试", LocateResult.Issue.ServiceOff))
            while (!manager.isLocationEnabled) delay(PROVIDER_POLL_MS)
        }

        val cached = freshLastKnown(manager)?.toFix()
        val gate = DotGate()
        if (cached != null && gate.accept(cached, System.currentTimeMillis())) {
            emit(toResult(cached, "session"))
        }

        var providers = enabledProviders(manager)
        if (providers.isEmpty()) {
            emit(LocateResult.Failed("正在等待可用的定位来源", LocateResult.Issue.ServiceOff))
            while (true) {
                delay(PROVIDER_POLL_MS)
                providers = enabledProviders(manager)
                if (providers.isNotEmpty()) break
            }
        }
        locationUpdates(manager, providers, STREAM_MIN_TIME_MS, STREAM_MIN_DISTANCE_M)
            .collect { location ->
                val next = location.toFix()
                if (gate.accept(next, System.currentTimeMillis())) {
                    emit(toResult(next, "realtime"))
                }
            }
    }

    /** GPS 与网络定位都给上次结果，取最新的那条；只看五分钟以内的。 */
    private fun freshLastKnown(manager: LocationManager): Location? =
        ALL_PROVIDERS.mapNotNull { provider ->
            try {
                if (manager.isProviderEnabled(provider)) manager.getLastKnownLocation(provider) else null
            } catch (_: SecurityException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            }
        }
            .filter { System.currentTimeMillis() - it.time <= MAX_LAST_KNOWN_AGE_MS }
            .maxByOrNull { it.time }

    /** 网络定位排在前面：出结果快，粗略坐标就够把地图中心落过去。 */
    private fun enabledProviders(manager: LocationManager): List<String> =
        listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .filter { provider ->
                try {
                    manager.isProviderEnabled(provider)
                } catch (_: IllegalArgumentException) {
                    false
                }
            }

    private suspend fun awaitFix(
        manager: LocationManager,
        providers: List<String>,
        timeoutMs: Long,
    ): Location? =
        withTimeoutOrNull(timeoutMs) {
            if (providers.isEmpty()) return@withTimeoutOrNull null
            locationUpdates(manager, providers, minTimeMs = 0L, minDistanceMeters = 0f).firstOrNull()
        }

    /**
     * 两个 provider 的更新汇成一条流，谁先来算谁。
     *
     * 四个回调方法**都要写全**：这些方法在 API 30 的 `android.jar` 里才是 default 方法，
     * 编译期不写也能过；但 26~29 的设备上框架会真的调用 `onStatusChanged` 等，
     * 类里没有实现就抛 `AbstractMethodError`。这类崩溃只在低版本机器上出现。
     */
    private fun locationUpdates(
        manager: LocationManager,
        providers: List<String>,
        minTimeMs: Long,
        minDistanceMeters: Float,
    ): Flow<Location> =
        callbackFlow {
            val registered = providers.map { provider ->
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        trySend(location)
                    }

                    @Deprecated("API 29 起废弃，但 26~29 的框架仍会调它")
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

                    override fun onProviderEnabled(provider: String) = Unit

                    override fun onProviderDisabled(provider: String) = Unit
                }
                val ok = try {
                    manager.requestLocationUpdates(
                        provider,
                        minTimeMs,
                        minDistanceMeters,
                        listener,
                        Looper.getMainLooper(),
                    )
                    true
                } catch (_: SecurityException) {
                    false
                } catch (_: IllegalArgumentException) {
                    false
                }
                provider to (listener to ok)
            }

            if (registered.none { (_, value) -> value.second }) {
                // 一个都没挂上：别让调用方白等满超时
                close()
            }

            awaitClose {
                registered.forEach { (_, value) ->
                    try {
                        manager.removeUpdates(value.first)
                    } catch (_: Exception) {
                        // 注销失败没有补救手段，也不影响结果
                    }
                }
            }
        }

    private fun toResult(fix: LocationFix, source: String): LocateResult {
        val gcj = Gcj02.toGcj02(fix.lat, fix.lng)
        return LocateResult.Ok(gcj.lat, gcj.lng, fix.accuracyMeters, source)
    }

    private fun Location.toFix(): LocationFix =
        LocationFix(latitude, longitude, accuracy)

    /** 缓存定位的三个来源，`PASSIVE` 是别的应用请求定位时顺带拿到的结果。 */
    private val ALL_PROVIDERS = listOf(
        LocationManager.GPS_PROVIDER,
        LocationManager.NETWORK_PROVIDER,
        LocationManager.PASSIVE_PROVIDER,
    )
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
 * 把镜头挪过去，站定不动的人会看到地图/蓝点被拽走——正是 `shouldMoveDot` 那条「更粗不认」
 * 要防的事。所以门槛取**这条读数自己的精度**（下限 [RECENTER_FROM_CACHE_MIN_METERS]）：
 * 差得还没它自己承认的误差大，说明缓存点与它并不矛盾，镜头本来就够准，不动；
 * 差得超出噪声（人确实挪了、或缓存点是上次别处的），才值得收敛。
 */
internal fun shouldRecenterFromCache(
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
internal const val DOT_MOVE_MIN_METERS = 4f

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
internal fun shouldMoveDot(previous: LocationFix?, next: LocationFix): Boolean {
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
internal class DotGate {

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
