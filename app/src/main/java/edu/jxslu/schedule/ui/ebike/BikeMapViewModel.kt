package edu.jxslu.schedule.ui.ebike

import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import edu.jxslu.schedule.Graph
import edu.jxslu.schedule.BuildConfig
import edu.jxslu.schedule.data.kqcx.KqcxBikeClient
import edu.jxslu.schedule.data.kqcx.KqcxSessionCore
import edu.jxslu.schedule.data.kqcx.KvcxZoneSource
import edu.jxslu.schedule.data.kqcx.ZoneCacheStore
import edu.jxslu.schedule.data.prefs.DisplayPrefsStore
import edu.jxslu.schedule.domain.BikeCluster
import edu.jxslu.schedule.domain.BikeFailure
import edu.jxslu.schedule.domain.BikeMapViewport
import edu.jxslu.schedule.domain.BikeNearby
import edu.jxslu.schedule.domain.CarDetailResult
import edu.jxslu.schedule.domain.EbikeUseMode
import edu.jxslu.schedule.domain.KvcxZones
import edu.jxslu.schedule.domain.NearbyBike
import edu.jxslu.schedule.domain.NearbyBikeCache
import edu.jxslu.schedule.domain.NearbyBikeCacheEntry
import edu.jxslu.schedule.domain.NearbyParseResult
import edu.jxslu.schedule.domain.ZoneCache
import edu.jxslu.schedule.domain.ZoneCacheEntry
import edu.jxslu.schedule.domain.capabilities
import edu.jxslu.schedule.ui.common.NoticeTone
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.math.abs

// [CameraRequest] 与地图层的其余参数契约已搬进 `shared/commonMain`
// （`ui/ebike/BikeMapContract.kt`，2026-11-25）：那份类型要同时喂给高德、osmdroid 与 iOS 的
// MapKit，留在 `:app` 的话 iOS 拿不到。包名相同，所以这个文件不需要 import。

/** 地图页的一次性提示（用车动作结果等）；[tone] 决定提示语气。 */
sealed interface BikeMapEvent {
    data class Notice(val text: String, val tone: NoticeTone) : BikeMapEvent

    /** 开锁成功：页面补一次成功触感（与出码页同口径）。 */
    data object Unlocked : BikeMapEvent

    /**
     * 扫码识别出的车已**确认可用**（见 `autoUnlockCar`）：页面据此走开锁的同一套闸
     * （二次确认 / 定位前置）。不可用 / 查不到不发这条——那种情况只展示车辆卡与原因。
     */
    data class AutoUnlockReady(val carNum: String) : BikeMapEvent
}

/**
 * 「按车号查单车」被服务端明确拒绝（[BikeMapUiState.carDetailIssue]）：车号 + 服务端原文
 * （如「车辆已出租」）。车辆卡按车号认领，换车 / 清高亮时一起清。
 */
data class CarDetailIssue(val carNum: String, val message: String)

/**
 * 附近单车地图页状态（DESIGN §3.9）。
 *
 * [failure] 非空时**不清空** [clusters]：拿用户刚看到的那批车配一条失败提示，
 * 比把列表清成空白有用。空列表 + 无失败 = 这一带确实没车，两件事在 UI 上得分开说。
 */
data class BikeMapUiState(
    /** 请求进行中。 */
    val loading: Boolean = false,
    /** 正在取定位（最长 8 秒）。按钮据此显示进行中状态，别让用户以为点了没反应。 */
    val locating: Boolean = false,
    /** 至少完成过一次请求（含失败）；false = 刚进页面，还没结果。 */
    val queried: Boolean = false,
    val clusters: List<BikeCluster> = emptyList(),
    /**
     * 过滤后（**未封顶**）的车总数：头部「本校可用 N 辆」的口径。
     *
     * 与 [clusters] 的长度**分开**（2026-10-01）：列表与地图只画最近的
     * [MAX_CLUSTER_COUNT] 处（每帧成本与列表长度都跟簇数走），但"这一带有多少辆能骑"
     * 这个数字不该跟着被裁掉。多出来的簇数见 [clusterOverflow]。
     */
    val bikeCount: Int = 0,
    /** 因封顶没画出来的簇数；> 0 时列表尾部给一行「还有 N 处更远」。 */
    val clusterOverflow: Int = 0,
    val failure: BikeFailure? = null,
    /** 最近一次成功刷新的时刻（epoch 毫秒）；0 = 还没成功过。 */
    val updatedAtMillis: Long = 0L,
    /** 当前展开的簇键；null = 全部收起。 */
    val expandedKey: String? = null,
    /** 待执行的镜头移动；null = 不动。 */
    val camera: CameraRequest? = null,
    /** 已取到的用户位置（GCJ-02）；null = 还没定位过。 */
    val userLat: Double? = null,
    val userLng: Double? = null,
    /**
     * 用户位置的定位精度（米，半径）；0 = 系统没给 / 还没定位（2026-10-13）。
     *
     * 地图据此画精度圈：固定大小的光晕说明不了"这个点准不准"，而网络定位能偏出上百米
     * ——圈大就是"别太信这个点"。
     */
    val userAccuracyMeters: Float = 0f,
    /**
     * 镜头是否跟着用户走（2026-10-19 用户要求）。
     *
     * 开着时连续定位的每条有效读数都会把镜头平移过去（位移 ≥
     * [FOLLOW_RECENTER_METERS] 才动，站定的小抖动不推镜头）。**数据只在换了片区域时跟上**
     * （2026-11-19）：离开上一次查询中心 [BikeNearby.FOLLOW_REQUERY_METERS] 以上才重查一次
     * ——近处走动不打扰第三方接口，红线是「不轮询」，不是「跟随期间绝不查」。
     *
     * 进页自动定位、点「定位」按钮、**人就在车边时**开始骑行都会打开它；用户单指拖动地图、
     * 点「回到校区」、点某个停车点 / 车辆定位时会关掉——那些动作表达的是"我现在要看别处"，
     * 再跟着人走只会把用户刚挪到的视野抢回去。远程开锁（车在几百米外）不打开跟随：
     * 那不是在骑车，镜头留在原地（判定在 `RideScreen` 的 phase effect）。
     */
    val followUser: Boolean = true,
    /**
     * 只看可用的车（离线、电量低于运营方阈值的都不显示）。
     * 默认开（2026-09-24 用户拍板）：找车就是要找能骑的；开关态进 DataStore，下次进页保持。
     */
    val onlyAvailable: Boolean = true,
    /**
     * 只看本校的车（快趣同时服务隔壁江西师大，两校坐标只隔一条马路）。
     * 默认开（2026-09-27 用户拍板）：筛的是车队归属 + 校区围栏双条件
     * （[BikeNearby.isOurCampusBike]），开关态进 DataStore，下次进页保持。
     */
    val onlyOurCampus: Boolean = true,
    /**
     * 地图的**显示方式**（2026-10-29 用户要求）：true = 按车辆逐个画图标，
     * false = 按停车点聚成数字圈（默认）。
     *
     * 「按车辆」时地图不再画聚合圈，每辆车画一枚小车图标、点图标直接出这辆车的详情
     * （车辆卡 + 主按钮）；列表同时换成**平铺的车辆列表**（[bikes]），与地图同一粒度。
     * 开关态进 DataStore（`ebike_map_by_bike`），下次进页保持。
     */
    val byBike: Boolean = false,
    /**
     * 过滤后（未封顶）的车辆列表，按距离升序：**「按车辆」模式的唯一数据源**。
     *
     * 与 [clusters] 同一批输入（同一套筛选 + 800 米展示上限，缩小视图 600 米），只是不做聚簇、
     * 也不吃 [MAX_CLUSTER_COUNT] 封顶——按车辆看就是要把这一带的车都摆出来。
     * 聚合模式不用它（地图与列表都走 [clusters]），所以只在 [byBike] 时被消费。
     */
    val bikes: List<NearbyBike> = emptyList(),
    /**
     * 当前这批结果来自**缩小视图的轻量查询**（2026-11-18）：地图缩到整座校区都看得见时
     * 只查中心一页、不再撒点（见 [BikeNearby.isZoomedOut]），展示范围也收小到
     * [BikeNearby.ZOOMED_OUT_MAX_DISTANCE_METERS]。
     *
     * 它是**这批数据的口径**，不是当前地图的缩放：拖动 / 缩放改了范围要等下一次查询落地才翻，
     * 列表与地图才始终说同一件事。UI 只拿它解释"为什么缩小时只有中心附近的车"。
     */
    val zoomedOut: Boolean = false,
    /**
     * 需要在列表里定位过去的停车点；[focusNonce] 递增用来区分"又点了一次同一个点"。
     *
     * 点地图标记时列表要滚到对应的卡片并高亮（DESIGN §3.9）——标记在屏幕中央，
     * 对应的卡片可能在列表里翻了好几屏，不滚过去用户不知道点中的是哪一条。
     */
    val focusKey: String? = null,
    val focusNonce: Long = 0L,
    /**
     * 底部车辆面板的高度（dp）。
     *
     * 放在状态里而不是页面局部 `remember`：局部状态首帧只能给默认值，等 DataStore 读回来
     * 时面板会跳一下，转屏还会再跳一次（用户明确要求过面板别跳）。上下限不在这里——
     * 夹取要用**当前窗口高度**算，那只有页面知道，所以 VM 只做「有限且为正」这一道校验。
     */
    val panelHeightDp: Float = DEFAULT_PANEL_HEIGHT_DP,
    /**
     * 还车点 / 禁停区图层（DESIGN §3.9，2026-09-28）：与车辆列表同一次用户动作驱动
     * （进页 / 拖动停稳 / 刷新 / 定位成功后各拉一次），**不额外轮询**。
     * 失败保留上一层：图层是装饰，空白比旧值更糟。
     */
    val zones: KvcxZones = KvcxZones.EMPTY,
    /**
     * 被识别条「定位」的车号（2026-09-29「车号识别联动」）：地图上画高亮圈。
     * 存车号不存坐标——刷新换批后车可能不在了，按号在最新结果里重找，找得到才画。
     */
    val highlightCarNum: String? = null,
    /** [highlightCarNum] 在当前结果里对应的车；null = 还没找到（等下一笔查询）。 */
    val highlightedCar: NearbyBike? = null,
    /**
     * 「按车号查单车」被服务端明确拒绝时的原因（2026-10-11）：车已出租 / 正在维修 /
     * 不存在……存**车号 + 文案**，车辆卡按车号认领（扫到一辆正被别人骑的车，卡上当场
     * 就该写「车辆已出租」，不能等点开锁才由 `createOrder` 报出来）。
     */
    val carDetailIssue: CarDetailIssue? = null,
    /**
     * 正在向快趣点名查这辆车（车号）；null = 没有在飞的查询。
     * 车辆卡据此显「正在查询这辆车…」——查询那零点几秒里别再摆那句"不在附近这批结果里"，
     * 那是误导（车可能就在附近，只是正被别人骑着）。
     */
    val carDetailQuerying: String? = null,
    /**
     * 列表的**距离参照点**（2026-09-30）：距离数字与排序都用它，两者必须同源——
     * 旧版数字按「距你」算、排序却按查询中心算，拖远之后顺序就和地图对不上了。
     *
     * 只在**一次查询落地时**重算，拖动过程中不会翻：
     * - 用户位置已知且查询中心离用户不超过 [USER_ANCHOR_RADIUS_METERS] → 用用户位置（标「距你」）；
     * - 否则用查询中心（标「距中心」）——地图拖远了就按"你正在看的这一片"排，顺序才和地图对得上。
     */
    val anchorLat: Double? = null,
    val anchorLng: Double? = null,
    val anchorFromUser: Boolean = false,
) {
    /** 距离是否以用户位置为参照。false = 以查询中心为参照。 */
    val distanceFromUser: Boolean get() = anchorFromUser
}

/**
 * 附近单车地图页（DESIGN §3.9 / §4.23）。
 *
 * 刷新只由**用户动作**与**真实位移**驱动，**不做后台轮询**：进页一次（[initialViewport]）、
 * 拖动停稳一次、点刷新一次、定位成功后一次、跟随态下换了一片区域一次（
 * [BikeNearby.FOLLOW_REQUERY_METERS]）。拖动由 View 层判定"停手"（osmdroid 走 `SettleWatcher`，
 * 高德走 `onCameraChangeFinish`）后回调 [onMapSettled]，再经 [QUERY_CONFIRM_MS] 的合并窗口
 * 发出——滑行途中一次都不发；与上次实际请求过的中心点距离不足 30 米同样跳过。
 *
 * 还车点 / 禁停区图层在用户动作基础上多一层**落盘缓存**（2026-09-28，[ZoneCacheStore]）：
 * 命中就先摆上、新鲜就不联网，只有「刷新」按钮强制重拉——缓存省的是重复往返，不是替代接口。
 *
 * 一次刷新可能发多个请求（见 [query] / [mergeSamples]）：接口只给"离查询点最近的 20 辆"，
 * 单点查不全。稀疏区域只发 1 个，车多的区域才会加撒一圈采样点。中心 + 环 + 大桩补全
 * **并行跑、合并后只落一次**（见 [query]），数字不会一段一段往上跳。
 */
class BikeMapViewModel(
    private val client: KqcxBikeClient,
    private val prefs: DisplayPrefsStore,
    /**
     * 快趣会话（DESIGN §4.32）：本机开锁 / 锁车 / 还车与「当前用车」标记都靠它；
     * 「按车号查单车」（2026-09-30）也走它——那是个不需要凭证的读接口。
     *
     * 类型是会话**本体** [KqcxSessionCore]（iOS 移植 E2，2026-11-25）：原先是 `:app` 的
     * `KqcxSessionRepository` 兼容壳，而壳只是 core 的 `YearMonth` 视图——本页早就没有
     * `YearMonth` 交接了，吃壳只会让这一页搬不动。壳仍留在 `:app` 给还没搬的两页用。
     */
    private val kqcx: KqcxSessionCore? = null,
    /**
     * 还车点 / 禁停区图层的数据源（只读；未登录时为 null）。
     *
     * 与 [kqcx] 是同一个实例（[KqcxSessionCore] 同时实现 [KvcxRideSession] 与
     * [KvcxZoneSource]）：分成两个参数是为了在单测 / 特殊宿主里只喂其中一面。
     */
    private val zoneSource: KvcxZoneSource? = null,
    /**
     * 图层的落盘缓存（DESIGN §3.9「停车点缓存」）：命中就先摆上、新鲜就不联网；
     * 每次成功拉取写回。null = 不缓存（只影响图层，车辆列表照旧）。
     */
    private val zoneCache: ZoneCacheStore? = null,
    /**
     * 本机用车控制器（2026-10-21 单例化）：Factory 注入 [Graph.kvcxRide]；
     * 传 null（本文件外的测试 / 特殊路径）时走旧路——本 VM 自建一份实例。
     */
    private val rideController: KvcxRideController? = null,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BikeMapUiState())
    val uiState: StateFlow<BikeMapUiState> = _uiState.asStateFlow()

    /**
     * 蓝点的罗盘朝向（度，0–360，北 = 0 顺时针，按屏幕顶边计；2026-10-08）。
     *
     * **单独一条流、不进 [BikeMapUiState]**（2026-10-13）：`RideScreen` 整页读的是 `uiState`，
     * 朝向若挂在状态对象上，罗盘每跳一下（≤10Hz）就重组整个页面（含底部车辆列表），而不只是
     * 地图那一层。放这里由 `OsmMapView` 自己收集，重组范围就收在地图内。
     * null = 没有朝向（无磁力计 / 还没读到），蓝点只画圆点不画扇形。
     */
    private val _userHeading = MutableStateFlow<Float?>(null)
    val userHeading: StateFlow<Float?> = _userHeading.asStateFlow()

    private val _events = Channel<BikeMapEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    /**
     * 本机用车（DESIGN §4.32，与出码页共用同一份编排）：骑行状态与写动作。
     * 地图页在本页就能开锁、锁车、还车——官方小程序也是在地图上直接操作当前用车。
     * **只在账号登录方式下使用**，见 [useMode] / [queryRideQuietly]。
     *
     * 2026-10-21 起控制器是 **Graph 单例**（[Graph.kvcxRide]）：今日页「进行中服务」卡
     * 与骑行页必须看到同一份骑行状态，两个实例会各查各的、互相覆盖。本页只负责把
     * 单例的事件流接进自己的事件通道（notice → Snackbar、unlocked → 成功触感）；
     * [rideController] 为 null 时退回自建实例（旧消费形态，事件直走本通道）。
     */
    val kvcx: KvcxRideController

    init {
        kvcx = rideController ?: KvcxRideController(
            session = kqcx,
            scope = viewModelScope,
            onNotice = { text, tone -> _events.trySend(BikeMapEvent.Notice(text, tone)) },
            onUnlocked = { _events.trySend(BikeMapEvent.Unlocked) },
        )
        if (rideController != null) {
            viewModelScope.launch {
                kvcx.notices.collect { event ->
                    _events.trySend(BikeMapEvent.Notice(event.text, event.tone))
                }
            }
            viewModelScope.launch {
                kvcx.unlockedSignal.collect { _events.trySend(BikeMapEvent.Unlocked) }
            }
        }
    }

    /**
     * 使用方式（DESIGN §3.9 / §4.32，2026-09-29）：地图上的「开锁」按钮与「当前用车」卡
     * 只属于账号登录方式，小程序方式只保留查车与图层。
     *
     * 初值阻塞读一次（同 `MeViewModel.initialPrefs` 模式）：模式决定首帧露不露出用车入口，
     * 初值给错会先按另一档画一帧再翻过来——那是看得见的闪。DataStore 读过一次后常驻内存。
     */
    val useMode: StateFlow<EbikeUseMode> = prefs.ebikeUseMode.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        runBlocking {
            runCatching { prefs.ebikeUseMode.first() }.getOrDefault(EbikeUseMode.Default)
        },
    )

    /**
     * 进页那一刻的视野（2026-11-19）：**建图中心与首屏查询中心共用这一份**。
     *
     * 中心优先级：进程内五分钟内的定位 → 落盘的上次定位 → 上次查看的视野 → 校区中心；
     * 缩放用上次视野的（没有就默认）。前两者都是"我在哪"——这个页面第一件事就是看身边的车；
     * 只有完全没有定位（没授权 / 从没成功过）时才退回上次拖到哪看哪。
     *
     * 阻塞读一次（同 [useMode] 的模式），因为地图第一帧就要用它：异步读回来再挪过去就是
     * 用户看得见的一跳。
     *
     * 为什么要收进来（2026-11-19 修用户报的「刚进快趣出行有时不显示停车点和附近车辆」）：
     * 旧版建图中心在页面里读（定位优先）、首屏查询中心在 ViewModel 里另读一份（上次视野），
     * 两者不同源——人已经走动过时，地图落在人身上、数据却查在几百米外，屏幕上就是空的；
     * 再加上 `AmapMapView` 里还有一次「定位回来就直接 moveCamera 到人身上」的独立镜头，
     * 镜头动了、数据没动。现在镜头与查询只有这一个中心，谁先到都是查同一片。
     */
    val initialViewport: BikeMapViewport = runBlocking {
        val saved = runCatching { prefs.ebikeMapViewport.first() }.getOrNull()
        val center = EbikeUserLocation.fresh()?.let { it.lat to it.lng }
            ?: runCatching { prefs.ebikeLastLocation.first() }.getOrNull()?.let { it.lat to it.lng }
            ?: saved?.let { it.lat to it.lng }
            ?: (BikeNearby.DEFAULT_CENTER_LAT to BikeNearby.DEFAULT_CENTER_LNG)
        BikeMapViewport(center.first, center.second, saved?.zoom ?: BikeNearby.DEFAULT_ZOOM)
    }

    /**
     * 查一次骑行状态（仅账号登录方式）。小程序方式直接不查：地图上根本没有用车入口，
     * 查了没人看，还要为一次登录态查询打扰第三方接口。
     *
     * 读原始流判定（不读 [useMode] 的 StateFlow）——行为判定按仓库既有口径走真值，
     * 读失败按「不是账号方式」处理（宁可不查，也不拿不准就打扰接口）。
     */
    fun queryRideQuietly() {
        viewModelScope.launch {
            if (!accountMode()) return@launch
            kvcx.query(quiet = true)
        }
    }

    /** 手动刷新骑行状态（「当前用车」卡上的刷新按钮）：失败给出提示，仍不做自动轮询。 */
    fun refreshRide() {
        viewModelScope.launch {
            if (!accountMode()) return@launch
            kvcx.query(quiet = false)
        }
    }

    private suspend fun accountMode(): Boolean =
        runCatching { prefs.ebikeUseMode.first().capabilities().inAppRide }.getOrDefault(false)

    /** 防抖与查询**必须分两个 Job**，见 [startQuery] 的注释。 */
    private var debounceJob: Job? = null
    private var queryJob: Job? = null

    /**
     * 最近一次抓到的原始列表：距离以**查询中心**为参照，且没过筛选。
     *
     * 展示用的簇每次都由它重新算（[rebuildClusters]），这样换了参照点（定位）或改了筛选
     * 都不用重新请求接口。
     */
    private var fetched: List<NearbyBike> = emptyList()

    /**
     * 最近一次拉到的原始图层（未过滤）：「只看本校」开关变化时从这里重算，
     * 与车辆列表同一套口径——开关不重新请求接口。
     */
    private var fetchedZones: KvcxZones = KvcxZones.EMPTY

    /** 落盘缓存的当前内容（进页读一次；之后每次成功拉取追加，判定见 [ZoneCache]）。 */
    private var zoneEntries: List<ZoneCacheEntry> = emptyList()

    /** 本次会话是否已经拿到过网络图层：拿到之后缓存只用于"换片区的即时展示"，不再补种。 */
    private var zonesFromNetwork = false

    /**
     * 上一次图层请求用的车号上下文（车号 + 它所属校区）。
     *
     * `queryZoneList` 的还车点 / 禁停区是**按车号所属校区**出的（2026-09-28 实测：拿本校
     * 车号、查询点放到 6 公里外，回来的仍是本校那批还车点；换一辆师大车号问同一个点，
     * 回的是师大校区的点）。所以换校区要换车号重拉——官方就是拿查询点附近那辆车的车号
     * 当上下文（`loadNearbyElements` 里 `carNum: list[0].name`）。
     *
     * 2026-09-29 追加：**这一带没车时继续拿上一次的上下文**，而不是不发请求。接口只需要
     * "哪个校区"，不需要车真的在旁边；旧规则（没车就不拉）会让"视图落在没车的角落、
     * 但看得到还车点"的情况下一个「P」都没有（用户报「移到有停车区的地方却刷不出来」）。
     */
    private data class ZoneCarContext(val carNum: String, val campusName: String)

    private var lastZoneCar: ZoneCarContext? = null

    /** 图层请求序号：旧响应回来时若已有更新的请求发出，直接作废（别盖掉新区域的图层）。 */
    private var zoneNonce = 0L

    /** 在飞的图层请求：新的一发就把旧的取消（图层只跟最新中心点）。 */
    private var zoneJob: Job? = null

    /**
     * 用户点开锁 / 锁车 / 还车时让地图查询让路：这些请求和写动作共用连接池，
     * 高峰期继续并发只会让开锁/还车排在后面。取消后把加载态收干净，动作完成再按需刷新。
     */
    fun cancelQueriesForRideAction() {
        // 在飞的查询会被掐掉：这一片等于**没查过**，闸门与 `queried` 都得按没查过处理，
        // 否则动作结束后同片的停手 / 定位一律被吞（见 [resumeQueryAfterAction]）。
        val cancelledInFlight = _uiState.value.loading || queryJob?.isActive == true ||
            debounceJob?.isActive == true
        debounceJob?.cancel()
        queryJob?.cancel()
        zoneJob?.cancel()
        // 写动作会改车辆可用性（开锁后这辆车已在骑、还车后才回到桩上）：旧结果不能再被
        // 平移回来时复用（2026-11-18）
        nearbyCache = emptyList()
        _uiState.update { it.copy(loading = false) }
        if (cancelledInFlight) {
            queryCancelledByAction = true
            lastAttemptLat = null
            lastAttemptLng = null
            lastAttemptZoom = null
        }
    }

    /**
     * 写动作结束（`busy` 由非空变空）后，把 [cancelQueriesForRideAction] 掐掉的那一次查询补上
     * （2026-11-19）。
     *
     * 为什么必须有它：取消查询是为了让写请求不排在读请求后面，但**取消不等于查过了**。
     * 旧版只清了 `loading` 与内存缓存，闸门记着"这一片查过"，于是动作结束后：
     * 拖到新片区再开锁 → 屏幕上是上一片的车、计数也不对，之后 30 米内的微调全被静默吞掉；
     * 首查就被取消时更糟——`queried` 永远 false，列表停在「正在查附近的车」。
     * 查询中心用 `pendingLat/Lng`（取消那一刻要查的那一片），走网络不吃缓存：
     * 刚开锁 / 刚还车的车状态变了，旧快照不能用。
     */
    private fun resumeQueryAfterAction() {
        if (!queryCancelledByAction) return
        queryCancelledByAction = false
        scheduleQuery(immediate = true, useCache = false)
    }

    /** 还没有车当上下文时的待发图层请求（只有本次会话第一次会用到）。 */
    private data class ZonePending(val lat: Double, val lng: Double, val force: Boolean)

    private var pendingZone: ZonePending? = null
    private var fetchedLat: Double? = null
    private var fetchedLng: Double? = null

    private var pendingLat = initialViewport.lat
    private var pendingLng = initialViewport.lng
    private var pendingZoom = initialViewport.zoom
    private var lastAttemptLat: Double? = null
    private var lastAttemptLng: Double? = null
    /** 上一次真正发出查询时的缩放（2026-11-18）：原地缩放也要能触发重查，见 [movedEnough]。 */
    private var lastAttemptZoom: Double? = null
    private var cameraNonce = 0L

    /**
     * 附近车辆的短缓存（[NearbyBikeCache]，随本页面会话存活）：拖开又拖回同一片时不再发请求。
     *
     * 只有拖动停手那条路吃它（[scheduleQuery] 的 `useCache`）；「刷新」按钮、写动作与
     * 还车后的补查都会清空（见 [cancelQueriesForRideAction] / [refresh] / [refreshNearUser]）。
     */
    private var nearbyCache: List<NearbyBikeCacheEntry> = emptyList()

    /**
     * 写动作掐掉过一次在飞的查询（见 [cancelQueriesForRideAction] / [resumeQueryAfterAction]）。
     * 动作结束那一刻补查一次，值即清。
     */
    private var queryCancelledByAction = false

    /**
     * 最近一次量到的视口半对角线（米；0 = 还没量到）。采样环半径按它缩放
     * （[BikeNearby.sampleRadiusMeters]），跟随的回中距离也按它算
     * （[BikeNearby.followRecenterMeters]）。
     *
     * **只有一个写入点** [onViewportMeasured]（View 层回报，与"停手"无关，2026-11-21）。
     * 旧版在 [onMapSettled] 里顺带学，而进页那次定位是程序性移动、停手回调被静默区吞掉
     * ——用户没亲手拖过地图之前它一直是 0，跟随阈值退到 8 米下限。
     */
    private var viewportHalfDiagonalMeters = 0.0

    /**
     * 上一次跟随镜头落到的用户坐标（2026-10-19）。
     *
     * 只在跟随态记账：新读数与它相差不足 [FOLLOW_RECENTER_METERS] 时不推镜头，否则
     * 站定时的几米抖动（精度提升那一路也会发读数）会让地图一直轻微滑动。
     * 退出跟随时清空——重新打开跟随要无条件把镜头拉回人身上。
     */
    private var followCameraLat: Double? = null
    private var followCameraLng: Double? = null

    /** 导航带入、还没在查询结果里找到的待定位车号（见 [setPendingFocusCar]）。 */
    private var pendingFocusCar: String? = null

    /**
     * 扫码识别出车号后「确认可用就自动开锁」的目标车号（见 [focusCar] / [setPendingFocusCar]
     * 的 `autoUnlock` 参数）。
     *
     * 只在**确认可用**时发一次 [BikeMapEvent.AutoUnlockReady]：附近列表里命中且
     * [NearbyBike.available]，或按车号点名查（`queryOneCar`）返回 [CarDetailResult.Ok] 且可用。
     * 其余一律只清掉目标、不发事件——已出租 / 维修 / 离线 / 查不到 / 网络失败都**不自动开锁**
     * （宁可不自动开锁，也不误开；车辆卡照常展示状态与服务端原因，用户可手动点开锁）。
     */
    private var autoUnlockCar: String? = null

    init {
        // 自动还车 Worker 结束订单后，页面状态不会自己变——订阅进程内信号把骑行卡收起
        // （2026-10-19 用户报「自动还车后卡片还在，只是没有时长条」）。
        viewModelScope.launch {
            EbikeRideSignal.ended.collect { notice -> kvcx.onRideEndedExternally(notice) }
        }
        // 骑行态吞掉识别高亮（2026-10-08 修「开始骑的位置残留一个奇怪的圆圈」）：
        // 高亮是「找这辆车」的指示（双层圈 + 车号标签），只对"车还停在那儿"成立；
        // 车一旦成了在骑的这辆，旧高亮圈就只剩误导——「我的车」标记用车辆坐标表达
        // "车在哪"。高亮按车号在最近一次查询结果里重找，
        // 骑行中不轮询、结果不换批，它就会一直钉在开骑前的位置——所以在案订单快照
        // 带出车号时比对一次、命中即清。还车后 ride 归 null，高亮早已清掉不会复活；
        // 若骑的是另一辆车，高亮说的是"那辆还停在那儿"，保留不动。
        viewModelScope.launch {
            kvcx.state.collect { snapshot ->
                val rideCar = snapshot.ride?.carNum ?: return@collect
                val num = _uiState.value.highlightCarNum ?: return@collect
                if (num == rideCar) clearHighlight()
            }
        }
        // 骑行结束（手动还车 / 自动还车 / 订单在别处结束）→ 查询中心拉回用户脚下重查一次
        // （理由见 [refreshNearUser]）。只在 ride 由非空变空的**转折点**触发：进页时 ride 本来
        // 就是空，那不是"骑完了"，不该在这里查第二次。
        viewModelScope.launch {
            var riding = kvcx.state.value.ride != null
            kvcx.state.collect { snapshot ->
                val nowRiding = snapshot.ride != null
                if (riding && !nowRiding) refreshNearUser()
                riding = nowRiding
            }
        }
        // 写动作把在飞的查询掐掉之后，**动作一结束就补查一次**（2026-11-19）：那一次查询
        // 没有落地，而闸门（`lastAttempt*`）已经记成"这一片查过了"——不补的话用户拖到新
        // 片区、点开锁/还车之后，屏幕上是上一片的车与计数，微调 30 米内一律被静默吞掉。
        viewModelScope.launch {
            var busy = kvcx.state.value.busy != null
            kvcx.state.collect { snapshot ->
                val nowBusy = snapshot.busy != null
                if (busy && !nowBusy) resumeQueryAfterAction()
                busy = nowBusy
            }
        }
        // 图层缓存先读回来（几十 KB 的文件，毫秒级）：进页那一刻就把上次那片还车点摆上，
        // 命中的话连请求都不发（判定在 domain/ZoneCache）。与下面几条并行，互不依赖
        viewModelScope.launch {
            zoneEntries = zoneCache?.load().orEmpty()
            seedZonesFromCache()
        }
        // 面板高度与筛选先读回来：首帧就用上用户上次的值，否则进页面会先按默认值画一帧
        // 再跳一下（用户明确说过面板不要跳）
        viewModelScope.launch {
            prefs.ebikePanelHeightDp.first()?.let(::setPanelHeight)
        }
        viewModelScope.launch {
            setOnlyAvailable(prefs.ebikeMapOnlyAvailable.first())
        }
        viewModelScope.launch {
            setOnlyOurCampus(prefs.ebikeMapOnlyOurCampus.first())
        }
        viewModelScope.launch {
            setByBike(prefs.ebikeMapByBike.first())
        }
        // 首屏查询：中心与建图中心同源（[initialViewport]），地图第一帧落在哪就查哪一片。
        // 旧版这里是「异步读上次视野 → pushCamera → 查」，定位先回来时整条被跳过，
        // 于是首屏可能一次查询都没有（用户报的「刚进页面时有时一片空白」就是这么来的）。
        scheduleQuery(immediate = true)
    }

    /**
     * 地图**停手**了（View 层判定：事件安静 + `scroller.isFinished`，见 `SettleWatcher`）。
     *
     * 拖动 / 惯性滑动期间不会走到这里，所以不需要在这里再等一个防抖窗口；[QUERY_CONFIRM_MS]
     * 只用来合并"停手信号 + 紧随其后的一两个收尾事件"。
     *
     * 程序性移动（定位、回到校区、点分组联动）的事件被镜头静默区吃掉，走不到这里，
     * 那些路径各自安排查询（[onLocated] / [onResetToCampus] / [onClusterTap]）。
     *
     * 视口尺寸**不从这里带**（2026-11-21 改）：它只在"没被静默区吞掉"的停手时才会到这里，
     * 而进页那次定位正好是程序性移动——见 [onViewportMeasured]。
     */
    fun onMapSettled(
        lat: Double,
        lng: Double,
        zoom: Double,
    ) {
        if (!lat.isFinite() || !lng.isFinite()) return
        if (zoom.isFinite() && zoom > 0) pendingZoom = zoom
        pendingLat = lat
        pendingLng = lng
        scheduleQuery()
    }

    /**
     * 视口半对角线的最新测量值（米；View 层在底图就绪与每次相机停稳时回报，2026-11-21）。
     *
     * 为什么单独一条：它是个**几何量**，与"这次停手是不是用户动作"无关。旧版只在
     * [onMapSettled] 里学到，而进页那次定位是程序性移动、回调被 `CameraSuppressor` 吃掉
     * ——于是用户没亲手拖过地图之前它一直是 0：跟随阈值退到
     * [BikeNearby.FOLLOW_RECENTER_MIN_METERS]（8 米）下限，镜头每走 8 米就滑一次
     * （正是 2026-11-19 修掉的"地图一直在追"）；采样环退到半径上限，放大到一条路也不收窄。
     *
     * **只记数，不安排查询**：换个视口尺寸不是用户"要看这一片"的意思表达。
     */
    fun onViewportMeasured(meters: Double) {
        if (!meters.isFinite() || meters <= 0.0) return
        viewportHalfDiagonalMeters = meters
    }

    /**
     * 刷新按钮：跳过位移判断，问就是重查。**图层也强制重拉**（缓存只替用户动作省请求，
     * 「刷新」就是用户要看最新的意思）。
     */
    fun refresh() {
        // 大桩补全与附近车辆的缓存一并作废：手动刷新要看最新的，不能吃几十秒内的旧快照
        spotFills.clear()
        nearbyCache = emptyList()
        scheduleQuery(immediate = true, force = true, forceZones = true, useCache = false)
    }

    /**
     * 骑行结束后把查询中心拉回**用户当前位置**并重查一次（2026-11-09）。
     *
     * 为什么需要：骑行期间镜头跟着用户走（[onUserLocationChanged] → [followCameraIfMoved]），
     * 而跟随只在**离开上一片查询中心 [BikeNearby.FOLLOW_REQUERY_METERS] 以上**时才重查，
     * 骑完最后几百米往往还留在上一片数据里；还车那一刻车辆恢复可用、还车点也最有参考价值，
     * 用这个转折点补一次对齐（2026-11-09 用户报的「还车后停在 8 秒结算卡那段时间，当前位置
     * 周围一片空白」与 2026-11-19 的「骑远了新位置不刷新」是同一族问题，各修一半）。
     *
     * **只在跟随态对齐**：跟随关着说明用户自己把地图拖到了别处，那一片的视野与数据是配对的
     * （拖动停稳就重查过），这时把数据拽回人身上，地图反而整片空白。没有定位（未授权 /
     * 还没取到）时同样什么都不做——没有"当前位置"可言。
     *
     * 触发点在 `init` 里对 [kvcx] 状态的 ride 非空 → 空转折（手动还车 / 自动还车 /
     * 订单在别处结束都走那一条），**不是轮询**。
     */
    fun refreshNearUser() {
        val state = _uiState.value
        if (!state.followUser) return
        val lat = state.userLat ?: return
        val lng = state.userLng ?: return
        if (!lat.isFinite() || !lng.isFinite()) return
        // 查询中心跟着人走：之后「继续找车」/ 8 秒自动收尾那次 refresh() 也查这一片
        pendingLat = lat
        pendingLng = lng
        // 刚还的车状态变了，这一查必须走网络，不能吃平移缓存
        nearbyCache = emptyList()
        scheduleQuery(immediate = true, force = true, useCache = false)
    }

    /** 取定位期间置位，让按钮有个"在做事"的样子。 */
    fun setLocating(value: Boolean) {
        _uiState.update { it.copy(locating = value) }
    }

    /**
     * 拖动把手改底部面板高度（dp）。调用方传进来的值已经按当前窗口夹过一道，
     * 这里只挡 NaN / 负数这类不可能值。
     */
    fun setPanelHeight(value: Float) {
        if (!value.isFinite() || value <= 0f) return
        _uiState.update { it.copy(panelHeightDp = value) }
    }

    /**
     * 把手拖动：按**状态里的当前值**加一个增量再夹取，而不是收"算好的结果值"。
     *
     * 收增量是为了避开一个真实的坑：拖动事件一帧可能来好几个（还有多指），
     * 调用方手里的 `panelHeightDp` 只是上一帧组合时的快照，用它连算两次会丢掉一次位移，
     * 表现为拖了不走。夹取在这里做，读的是同一份状态，增量不会丢。
     *
     * [maxDp] 由页面按当前窗口高度算（VM 看不到窗口尺寸），窗口太矮时下限会高过上限，
     * 那时保持原值不动。
     */
    fun resizePanelBy(deltaDp: Float, minDp: Float, maxDp: Float) {
        if (!deltaDp.isFinite() || !minDp.isFinite() || !maxDp.isFinite()) return
        if (maxDp < minDp) return
        _uiState.update { current ->
            current.copy(
                panelHeightDp = (current.panelHeightDp + deltaDp).coerceIn(minDp, maxDp),
            )
        }
    }

    /** 把手松手才落盘：拖动途中每帧都写 DataStore 没必要。 */
    fun persistPanelHeight() {
        val value = _uiState.value.panelHeightDp
        viewModelScope.launch { prefs.setEbikePanelHeightDp(value) }
    }

    /**
     * 把面板高度收进 [minDp]..[maxDp]（页面在窗口尺寸变化后调用）。
     *
     * 渲染时虽然也夹了一道，但状态本身不会跟着变，于是会出现"拖了不动"：存着 560dp 的
     * 人转成横屏（上限只剩 280dp），面板画在 280dp，而拖动是从 560 开始算的，
     * 手指得先走完那 280dp 才见效。让状态自己收敛就没这回事。
     *
     * 已经在范围内时原样返回同一个实例：StateFlow 按相等去重，不会多触发一次重组。
     */
    fun clampPanelHeight(minDp: Float, maxDp: Float) {
        if (!minDp.isFinite() || !maxDp.isFinite() || maxDp < minDp) return
        _uiState.update { current ->
            val clamped = current.panelHeightDp.coerceIn(minDp, maxDp)
            if (clamped == current.panelHeightDp) current else current.copy(panelHeightDp = clamped)
        }
    }

    /**
     * 只看可用的车。不重新请求接口——数据已经在手上，重算一遍簇即可。
     *
     * 进页时也会拿它同步 DataStore 存的值（读回来的就是存的，写回等于空操作），
     * 这样 UI 只需要一条口径，不必区分「用户点的」与「进页恢复的」。
     */
    fun setOnlyAvailable(value: Boolean) {
        if (_uiState.value.onlyAvailable == value) return
        _uiState.update { it.copy(onlyAvailable = value) }
        rebuildClusters()
        viewModelScope.launch { prefs.setEbikeMapOnlyAvailable(value) }
    }

    /**
     * 只看本校的车。与 [setOnlyAvailable] 同一套口径：数据已在手上，重算簇即可，
     * 不重新请求接口；进页时也会拿 DataStore 存的值同步一遍。
     */
    fun setOnlyOurCampus(value: Boolean) {
        if (_uiState.value.onlyOurCampus == value) return
        _uiState.update { it.copy(onlyOurCampus = value) }
        rebuildClusters()
        // 图层同吃这一份筛选（还车点按围栏过滤，见 KvcxZones.campusOnly）
        applyZones()
        viewModelScope.launch { prefs.setEbikeMapOnlyOurCampus(value) }
    }

    /**
     * 切换地图显示方式（按停车点 ↔ 按车辆，2026-10-29）。
     *
     * 纯展示切换：数据已在手上，只重算展示用的那一份（[BikeMapUiState.bikes] /
     * [BikeMapUiState.clusters]），**不重新请求接口**（与两个筛选开关同一口径）。
     * 切回聚合时顺手收起已展开的停车点卡——那张卡在「按车辆」模式下根本不存在。
     */
    fun setByBike(value: Boolean) {
        if (_uiState.value.byBike == value) return
        _uiState.update { it.copy(byBike = value) }
        rebuildClusters()
        viewModelScope.launch { prefs.setEbikeMapByBike(value) }
    }

    /**
     * 点标记或列表分组：展开/收起该停车点。
     *
     * 展开时顺手把地图移过去：列表给的是查询半径内的车，远的那些确实在屏幕外，
     * 只展开列表用户还是看不到它在哪。移动是程序性的，不会触发重查
     * （那批数据已经取回来了，没必要再问一次接口）。
     */
    fun onClusterTap(key: String) {
        val cluster = _uiState.value.clusters.firstOrNull { it.key == key }
        val expanding = _uiState.value.expandedKey != key
        _uiState.update {
            it.copy(
                expandedKey = if (expanding) key else null,
                focusKey = if (expanding) key else null,
                // 每次点都递增：同一个点连点两次也要能再滚一次
                focusNonce = it.focusNonce + 1,
            )
        }
        if (expanding && cluster != null) {
            // 用户明确要看这个停车点：退出自动跟随，否则下一条定位读数会把镜头抢回去
            stopFollowing()
            pushCamera(cluster.lat, cluster.lng, zoom = null, animated = true)
        }
    }

    /**
     * 把镜头移到某个点（点「当前用车」标记 / 卡片上的「定位到车」）。
     * 程序性移动，静默窗口会吃掉随之而来的滚动回调，因此**不触发重查**——
     * 那批车已经取回来了，重查只会让列表当场换一批内容。
     */
    fun onFocusPoint(lat: Double, lng: Double) {
        if (!lat.isFinite() || !lng.isFinite()) return
        // 用户点名看某个点：跟随让位（点「定位」或开始骑行会重新打开）
        stopFollowing()
        pushCamera(lat, lng, zoom = null, animated = true)
    }

    /**
     * 找一辆车（按完整车号）并展开所在簇、把镜头移过去；列表里没有就向快趣点名查一次。
     *
     * [autoUnlock] = 扫码那条路（内置相机「扫车身码」）：确认这辆车可用后发一次
     * [BikeMapEvent.AutoUnlockReady]，页面据此走开锁的同一套闸（见 `autoUnlockCar`）。
     * 其余调用（车辆卡定位 / 地图选车）传默认 false。
     */
    fun focusCar(carNum: String, autoUnlock: Boolean = false) {
        if (!carNum.isNotBlank()) return
        // 目标只在"这次就是扫它"或"还是同一辆车"时保留；换了车就作废——免得一个没结论的
        // 旧目标被后来的定位动作意外触发（自动开锁只认最近一次扫码）
        autoUnlockCar = if (autoUnlock) carNum else autoUnlockCar?.takeIf { it == carNum }
        // 这辆车就是在骑的这辆：不再按旧结果的坐标画高亮、也不把镜头拽向过期位置——
        // 「我的车」标记用车辆坐标表达它在哪（与 init 里骑行吞高亮同一条判据）。
        // 还车之后 ride 已归 null，从这里带车号进来仍照常定位（车又停回某个点位了）。
        if (kvcx.state.value.ride?.carNum == carNum) {
            clearHighlight()
            clearAutoUnlock(carNum) // 正在骑这辆：不自动开锁
            return
        }
        // 找车 / 点名定位是"我要看这辆车"：跟随让位（点「定位」或开始骑行会重新打开）
        stopFollowing()
        _uiState.update {
            it.copy(highlightCarNum = carNum, carDetailIssue = null, carDetailQuerying = null)
        }
        rebuildClusters()
        val cluster = _uiState.value.clusters.firstOrNull { c -> c.bikes.any { it.carNum == carNum } }
        if (cluster == null) {
            // 当前列表里没有：可能是筛掉了、车被骑走了或还没查到这一片。
            // 向快趣按车号点一次名（`queryOneCarV2`，官方 loadOneCar 同款），
            // 拿到真实坐标就高亮定位；服务端说它不可用就把原因摆到车辆卡上
            fetchCarDetail(carNum)
            return
        }
        // 车就在这批结果里（可用）——上一轮那条「不可用」结论作废
        _uiState.update {
            it.copy(
                expandedKey = cluster.key,
                focusKey = cluster.key,
                focusNonce = it.focusNonce + 1,
                carDetailIssue = null,
            )
        }
        pushCamera(cluster.lat, cluster.lng, zoom = null, animated = true)
        val bike = cluster.bikes.firstOrNull { it.carNum == carNum }
        if (bike?.available == false) {
            // 扫到列表里一辆离线/停用/低电量的车：不弹车辆卡，但给一句原因，
            // 用户可以直接再扫下一辆。
            _events.trySend(
                BikeMapEvent.Notice(
                    "车 $carNum ${bike.status.label}，换一辆试试",
                    NoticeTone.Warning,
                ),
            )
        }
        maybeAutoUnlock(carNum, bike)
    }

    /**
     * 确认到目标车可用：清目标并发一次 [BikeMapEvent.AutoUnlockReady]。
     * [bike] 为 null 或不可用时只清目标、不发事件（见 `autoUnlockCar`）。
     */
    private fun maybeAutoUnlock(carNum: String, bike: NearbyBike?) {
        if (autoUnlockCar != carNum) return
        autoUnlockCar = null
        if (bike?.available == true) _events.trySend(BikeMapEvent.AutoUnlockReady(carNum))
    }

    /** 目标车没有结论（不可用 / 查不到 / 查不动）：清掉，不自动开锁。 */
    private fun clearAutoUnlock(carNum: String) {
        if (autoUnlockCar == carNum) autoUnlockCar = null
    }

    /**
     * 按车号向快趣查单辆车（2026-09-30「单车详情」）：附近列表里没有它时的兜底。
     * 查到就高亮 + 移镜头（电量与位置随车辆卡展示）；**服务端明确说不可用**（车已出租 /
     * 维修 / 不存在）就把原文摆到车辆卡上（2026-10-11：扫到别人正在骑的车，当场就能看见，
     * 不用等点开锁才被 `createOrder` 报出来）；网络失败才给"查不到"的旧提示。
     *
     * 查询中心用**当前视野中心**（官方传扫当时的定位，同是"以哪为上下文"）。
     * 不轮询：一次查不到就到此为止，等用户下一次动作；连点期间只放一个在飞。
     */
    private var fetchedCarDetail: NearbyBike? = null
    private var carDetailJob: Job? = null

    /**
     * 清掉识别高亮（骑行吞高亮 / focusCar 撞上在骑车辆时走这里）：
     * 标记、按号查回的那份单车详情、以及那条「不可用」结论一起清。
     */
    private fun clearHighlight() {
        if (_uiState.value.highlightCarNum == null) return
        val num = _uiState.value.highlightCarNum
        if (fetchedCarDetail?.carNum == num) fetchedCarDetail = null
        _uiState.update {
            it.copy(
                highlightCarNum = null,
                highlightedCar = null,
                carDetailIssue = it.carDetailIssue?.takeIf { issue -> issue.carNum != num },
                carDetailQuerying = it.carDetailQuerying?.takeIf { q -> q != num },
            )
        }
    }

    private fun fetchCarDetail(carNum: String) {
        // 「按车号找车」优先于自动跟随：查到会移镜头，别让下一条定位读数把它拽回去
        stopFollowing()
        val session = kqcx ?: run {
            clearAutoUnlock(carNum)
            _events.trySend(
                BikeMapEvent.Notice("附近列表里暂时没有这辆车；移动地图或点刷新后再试", NoticeTone.Info),
            )
            return
        }
        if (carDetailJob?.isActive == true) {
            // 已有一次点名查询在飞：这一次不发也不等，自动开锁目标随之作废（保守：
            // 宁可少自动开一次，也不留一个没结论的目标被后面的动作意外触发）
            clearAutoUnlock(carNum)
            return
        }
        // 同步置位（不放进 launch 里）：车辆卡要**当场**翻成「正在查询这辆车…」，
        // 晚一帧就会先闪一下"不在附近这批结果里"——那句正是要替掉的误导文案。
        _uiState.update { it.copy(carDetailQuerying = carNum) }
        carDetailJob = viewModelScope.launch {
            val result = runCatching {
                session.queryCarDetail(carNum, pendingLat, pendingLng)
            }.getOrNull()
            when (result) {
                is CarDetailResult.Ok -> {
                    val detail = result.bike
                    fetchedCarDetail = detail
                    _uiState.update {
                        it.copy(
                            highlightCarNum = carNum,
                            carDetailIssue = null,
                            carDetailQuerying = null,
                        )
                    }
                    rebuildClusters()
                    pushCamera(detail.lat, detail.lng, zoom = null, animated = true)
                    _events.trySend(
                        BikeMapEvent.Notice(
                            "已在地图标出车 $carNum（${detail.batteryText}）",
                            NoticeTone.Info,
                        ),
                    )
                    // 扫码自动开锁：点名查到的这份详情就是"确认可用"的判据
                    maybeAutoUnlock(carNum, detail)
                }

                // 服务端明确说这辆车不可用（已出租 / 维修 / 不存在）：把原文摆到车辆卡上，
                // 顺手提示一句。这不是"没查到"，用户该当场知道原因。
                is CarDetailResult.Unavailable -> {
                    clearAutoUnlock(carNum)
                    _uiState.update {
                        it.copy(
                            carDetailIssue = CarDetailIssue(carNum, result.message),
                            carDetailQuerying = null,
                        )
                    }
                    _events.trySend(BikeMapEvent.Notice(result.message, NoticeTone.Warning))
                }

                else -> {
                    clearAutoUnlock(carNum)
                    _uiState.update { it.copy(carDetailQuerying = null) }
                    _events.trySend(
                        BikeMapEvent.Notice(
                            "快趣上也查不到车 $carNum；确认车号后再试，或移动地图找它",
                            NoticeTone.Info,
                        ),
                    )
                }
            }
        }
    }

    /**
     * 导航带入的待定位车号（侧栏「扫一扫」扫到车身码经 focusItemId 传进来）：
     * 第一笔查询结果里找到就自动定位（[focusCar]）；查过一轮还是没有（车被骑走 / 不在这一带
     * / **正被别人骑着**）就退到按车号点名查一次（[fetchCarDetail]），服务端说不可用就把原因
     * 摆到车辆卡上——与「扫车身码」那条路同一条兜底（2026-10-11）。
     *
     * [autoUnlock] 同 [focusCar]：确认可用后发一次 [BikeMapEvent.AutoUnlockReady]。
     */
    fun setPendingFocusCar(carNum: String, autoUnlock: Boolean = false) {
        if (autoUnlock) autoUnlockCar = carNum
        pendingFocusCar = carNum
        tryResolvePendingFocusCar()
    }

    private fun tryResolvePendingFocusCar() {
        val num = pendingFocusCar ?: return
        val found = _uiState.value.clusters.any { c -> c.bikes.any { it.carNum == num } }
        if (found) {
            pendingFocusCar = null
            focusCar(num)
            return
        }
        // 这一轮查询已经落定（中心 + 撒点 + 补全一起回来的那一拍）还是没有它：按车号点名查一次。
        // 现在是一次落地（见 [query]），`queried` 为 true 就代表结果完整，不用再判中间态。
        if (_uiState.value.queried) {
            pendingFocusCar = null
            fetchCarDetail(num)
        }
    }

    /** 「回到校区」：拉回默认中心并重查。 */
    fun onResetToCampus() {
        // 用户选择"看校区"：退出自动跟随（点「定位」或开始骑行会重新打开）
        stopFollowing()
        pushCamera(
            lat = BikeNearby.DEFAULT_CENTER_LAT,
            lng = BikeNearby.DEFAULT_CENTER_LNG,
            zoom = BikeNearby.DEFAULT_ZOOM,
            animated = true,
        )
        pendingLat = BikeNearby.DEFAULT_CENTER_LAT
        pendingLng = BikeNearby.DEFAULT_CENTER_LNG
        pendingZoom = BikeNearby.DEFAULT_ZOOM
        scheduleQuery(immediate = true)
    }

    /**
     * 定位成功（坐标已是 GCJ-02）。记下蓝点位置（含精度），把镜头移过去并重查。
     *
     * 同时**换参照点**重算距离：距离从此以"你"为准，拖动地图不会让这些数字变形。
     * 也**重新打开跟随**（2026-10-19）：用户点「定位」就是"我要看着自己"，进页自动定位
     * 同理；之后连续定位的读数会把镜头一路带过去（[onUserLocationChanged]）。
     *
     * [animated] 为 false 用于进页面那一次：直接落位。进页面又没有起点可看，
     * 从校园中心滑过去纯属多此一举；用户主动点「定位」才需要一个缓动告诉他镜头动了。
     *
     * [fromCache] = 这次定位拿的是**缓存位置**（`BikeLocator.currentLocation` 的
     * 「五分钟内的上次定位」）。缓存点也把镜头带过去（比停在旧视野强），但**不当成镜头
     * 锚点记账**（[followCameraLat] 保持 null）——缓存点与实时读数能差三四十米，2026-11-19
     * 修用户报「进页视角不在我的位置」：记了它，回中阈值就把实时读数压在阈值内、镜头
     * 永远不再向蓝点收敛。第一条实时读数会无条件把镜头补到蓝点上（见
     * [followCameraIfMoved] 的 lastLat==null 路径）。
     *
     * 查询**仍过位移闸门**（2026-11-19）：进页那一刻的定位常常就落在建图中心上
     * （[initialViewport] 用的也是这份缓存定位），同一个中心再查一遍只是多 5 个请求；
     * 人真在别处时距离早超过 [BikeNearby.MIN_REQUERY_SHIFT_METERS]，照样会查。
     */
    fun onLocated(
        lat: Double,
        lng: Double,
        accuracyMeters: Float = 0f,
        animated: Boolean = true,
        fromCache: Boolean = false,
    ) {
        if (!lat.isFinite() || !lng.isFinite()) return
        EbikeUserLocation.update(lat, lng)
        if (fromCache) {
            // 缓存点不是镜头锚点：留给第一条实时读数做无条件收敛
            followCameraLat = null
            followCameraLng = null
        } else {
            followCameraLat = lat
            followCameraLng = lng
        }
        _uiState.update {
            it.copy(
                userLat = lat,
                userLng = lng,
                userAccuracyMeters = sanitizeAccuracy(accuracyMeters),
                followUser = true,
            )
        }
        rebuildClusters()
        pushCamera(lat, lng, zoom = null, animated = animated)
        pendingLat = lat
        pendingLng = lng
        scheduleQuery(immediate = true, useCache = false)
    }

    /**
     * 连续定位的最新读数（坐标已是 GCJ-02，DESIGN §3.9「蓝点实时更新」）：
     * 挪蓝点（含精度圈）；**跟随态下也把镜头平移过去**（2026-10-19 用户要求：
     * 骑车换了地方，地图不能还停在开锁那一刻的位置）。
     *
     * 跟随的刷新口径（2026-11-19 修订）：镜头随时跟着走，**数据只在离开上一片查询中心
     * [BikeNearby.FOLLOW_REQUERY_METERS]（300 米）以上时重查一次**（见 [followCameraIfMoved]）。
     * 近处走动不发请求——红线是「不轮询第三方接口」，不是「跟随期间绝不查」；旧口径一路
     * 只挪镜头，骑到几百米外的另一片时屏幕上就只剩一张没有车的图（用户 2026-11-19 报的就是它）。
     *
     * 用户单指拖动地图即退出跟随（[onUserPanned]），点「定位」/ 开始骑行再打开。
     *
     * **不重设列表的参照点**（2026-09-30）：蓝点每动一下就把整张列表按新位置重排，
     * 用户站着不动也会看到顺序在变。参照点只在查询落地时定一次（见 [BikeMapUiState.anchorLat]）。
     *
     * **也不重算簇**（2026-10-13）：簇的距离用的是那个定死的参照点，与蓝点实时位置无关，
     * 重算结果逐字段相同——纯浪费（还在主线程，走动时每 2 秒一次）。停车点距离读的是
     * `state.userLat`，不经过簇，照常跟着蓝点走。
     *
     * [fromCache] = 这条读数来自缓存定位（`BikeLocator.updates` 开头那条「五分钟内的
     * 上次定位」，source="session"）。缓存点只挪蓝点、**不参与镜头收敛记账**（2026-11-19
     * 修用户报「进页视角不在我的位置」）：缓存点与实时读数能差三四十米，若拿它当镜头锚点，
     * 回中阈值（最多 60 米）就会把实时读数压在阈值内、镜头永远不再向蓝点收敛——
     * 蓝点挂着精度圈停在偏离中心的位置。缓存读数等实时读数来收敛。
     */
    fun onUserLocationChanged(
        lat: Double,
        lng: Double,
        accuracyMeters: Float = 0f,
        fromCache: Boolean = false,
    ) {
        if (!lat.isFinite() || !lng.isFinite()) return
        EbikeUserLocation.update(lat, lng)
        _uiState.update {
            it.copy(
                userLat = lat,
                userLng = lng,
                userAccuracyMeters = sanitizeAccuracy(accuracyMeters),
            )
        }
        if (_uiState.value.followUser && !fromCache) followCameraIfMoved(lat, lng)
    }

    /**
     * 打开自动跟随（2026-10-19）：镜头立刻回到用户身上（已有位置时），之后由
     * [onUserLocationChanged] 接管。页面在「人就在车边」的骑行开始、点「定位」时调它
     * （见 `RideScreen` 的 phase effect）；远程开锁不走这条路。
     */
    fun startFollowing() {
        val current = _uiState.value
        val lat = current.userLat
        val lng = current.userLng
        val wasFollowing = current.followUser
        if (!wasFollowing) {
            _uiState.update { it.copy(followUser = true) }
        }
        followCameraLat = lat
        followCameraLng = lng
        // 已经在跟随时镜头就在人身上，不重复推（连点定位 / 骑行态每轮 phase 都会调到这里）
        if (!wasFollowing && lat != null && lng != null) {
            pushCamera(lat, lng, zoom = null, animated = true)
        }
    }

    /** 退出自动跟随（用户拖图 / 点名看别处）；点「定位」或开始骑行会重新打开。 */
    fun stopFollowing() {
        if (_uiState.value.followUser) _uiState.update { it.copy(followUser = false) }
        followCameraLat = null
        followCameraLng = null
    }

    /** 用户单指拖动了地图（View 层识别）：从这一刻起不再自动跟随。 */
    fun onUserPanned() {
        stopFollowing()
        // 用户接管地图：把**还没执行**的镜头请求一起丢掉。否则定位 / 跟随刚好推来的
        // 一次 `camera` 会在手指下面再补一段动画（异步一拍，stopAnimation 拦不到）。
        _uiState.update { current ->
            if (current.camera == null) current else current.copy(camera = null)
        }
    }

    /**
     * 跟随态下把镜头带向最新读数：蓝点离开视口中心够远才推一次
     * （[BikeNearby.followRecenterMeters]，下限 8 米、随缩放收到 60 米以内，
     * 2026-11-19 把固定 8 米改成按视口算）——站定时的几米抖动、以及"原地精度提升"那一路
     * 读数都不该让镜头一直轻滑。
     *
     * **镜头到了新地方，数据跟上**（2026-11-19 用户报「骑车到较远的另一个地方后，新位置的
     * 窗口不刷新附近的停车点和车辆」）：离开**上一次查询中心**超过
     * [BikeNearby.FOLLOW_REQUERY_METERS]（300 米）就为这一片重查一次，查询中心随之更新。
     * 三条口径：
     * - **只在跟随态**走这里：用户自己拖图看别处时跟随已经关掉，那一片的视野与数据配对；
     * - **查询在飞时不叠加**（`loading`）：查询落地要 1~2 秒，骑得快时这段时间里还会来一两条
     *   读数，不加这道闸会反复取消上一笔，页面一直转圈、一次都落不了地；
     * - 还没查过（`lastAttempt*` 为空）也不在这里补：首屏查询与 [onLocated] 各自会发，
     *   跟随时补一条只会在进页那一刻多打一轮。
     *
     * **[pendingLat/Lng] 跟着镜头走，与"要不要发查询"分开**（2026-11-21）：「下一次查询的
     * 中心」是"镜头现在看着哪"，而 300 米那道闸门只决定"这一片的数据要不要现在换"。旧版
     * 只在闸门放行时才写它，于是跟随骑出两百米后点「刷新」/ 开锁后的补查，查的还是上一片的
     * 中心——列表与地图差出一段，正是"镜头与数据不同源"那一族。
     */
    private fun followCameraIfMoved(lat: Double, lng: Double) {
        val lastLat = followCameraLat
        val lastLng = followCameraLng
        val recenterMeters = BikeNearby.followRecenterMeters(viewportHalfDiagonalMeters)
        if (lastLat != null && lastLng != null &&
            BikeNearby.distanceMeters(lastLat, lastLng, lat, lng) < recenterMeters
        ) {
            return
        }
        followCameraLat = lat
        followCameraLng = lng
        pendingLat = lat
        pendingLng = lng
        pushCamera(lat, lng, zoom = null, animated = true)

        val queryLat = lastAttemptLat
        val queryLng = lastAttemptLng
        if (queryLat == null || queryLng == null) return
        if (_uiState.value.loading) return
        if (!BikeNearby.followQueryDue(queryLat, queryLng, lat, lng)) return
        scheduleQuery(immediate = true, useCache = false)
    }

    /** 精度读数的清洗：非有限 / 非正一律当"系统没给"（0）。 */
    private fun sanitizeAccuracy(meters: Float): Float =
        if (meters.isFinite() && meters > 0f) meters else 0f

    /**
     * 罗盘朝向的最新读数（2026-10-08）：**只转蓝点上的朝向箭头**。
     *
     * 与 [onUserLocationChanged] 同一条纪律——不移镜头、不重查、不动参照点。
     * 传感器流已在 `BikeCompass` 内做过低通 + 节流（>2° 且 ≥100ms 才发），
     * 到这里的频率对 `MapOverlayInputs` 的值比对与整幅重绘都是安全的。
     *
     * 写进独立的 [userHeading]（不动 `uiState`）：罗盘的跳动不该重组整页，见那条流的注释。
     */
    fun onUserHeadingChanged(degrees: Float) {
        if (!degrees.isFinite()) return
        _userHeading.value = degrees
    }

    /**
     * 地图执行完一次镜头移动后回调，把请求清掉。
     *
     * 不清的话，Activity 一重建（转屏、内存回收、改字号）新组合就会拿同一个 nonce
     * 再放一次 LaunchedEffect —— 表现为「我把地图拖到别处，回来它自己跳回去了」。
     * 镜头请求是一次性动作，不是状态。
     */
    fun onCameraApplied(nonce: Long) {
        _uiState.update { current ->
            if (current.camera?.nonce == nonce) current.copy(camera = null) else current
        }
    }

    private fun pushCamera(lat: Double, lng: Double, zoom: Double?, animated: Boolean) {
        cameraNonce += 1
        _uiState.update {
            it.copy(camera = CameraRequest(lat, lng, zoom, cameraNonce, animated))
        }
    }

    /**
     * 用 [fetched] 重算展示用的簇：换参照点（有定位就用用户位置）、按上限与筛选过一遍、再聚簇。
     *
     * 簇的顺序来自距离，而距离以用户为参照时**不随地图中心变化**——用户拖地图时列表
     * 不会重排，只会换一批（服务端返回范围内）内容。
     */
    private fun rebuildClusters() {
        val state = _uiState.value
        val originLat = state.anchorLat ?: fetchedLat
        val originLng = state.anchorLng ?: fetchedLng
        val anchored = if (originLat != null && originLng != null) {
            BikeNearby.reanchor(fetched, originLat, originLng)
        } else {
            fetched
        }
        // 展示范围（2026-11-18 收小，见 BikeNearby.displayMaxDistanceMeters）：缩小视图最紧
        // （大范围撒点已停，只留中心附近），「按车辆」次之，聚合模式最宽。
        val maxDistance = BikeNearby.displayMaxDistanceMeters(
            byBike = state.byBike,
            zoomedOut = state.zoomedOut,
        )
        val shown = anchored
            // 撒点采样会把上限外的车也捞回来，那些不算"附近"
            .filter { it.distanceMeters <= maxDistance }
            // 只看本校：车队归属 + 围栏双条件（师大校园的车在这里被挡掉）
            .let { list ->
                if (state.onlyOurCampus) {
                    list.filter { BikeNearby.isOurCampusBike(it.lat, it.lng, it.campusName) }
                } else {
                    list
                }
            }
            .let { list -> if (state.onlyAvailable) list.filter { it.available } else list }
        val allClusters = BikeNearby.cluster(shown)
        // 卡片与标记封顶（2026-10-01）：列表布局与地图每帧的成本都跟簇数成正比，密度高的
        // 校区能摞到几十处；只画最近的一批，多出来的在列表尾部给一行「还有 N 处更远」。
        // 头部的车辆总数走 bikeCount（未封顶），所以那个数字不会被裁小
        //
        // **要聚焦的那辆车必须进榜**：识别条「地图查看」与高亮是"找这辆"的动作，
        // 它落在第 25 个簇里时不能因为封顶而找不着——把它的簇换掉榜尾那一个。
        val priority = _uiState.value.highlightCarNum ?: pendingFocusCar
        val clusters = run {
            val ranked = if (priority == null) {
                -1
            } else {
                allClusters.indexOfFirst { cluster -> cluster.bikes.any { it.carNum == priority } }
            }
            when {
                allClusters.size <= MAX_CLUSTER_COUNT -> allClusters
                ranked < 0 || ranked < MAX_CLUSTER_COUNT -> allClusters.take(MAX_CLUSTER_COUNT)
                else -> allClusters.take(MAX_CLUSTER_COUNT - 1) + allClusters[ranked]
            }
        }
        _uiState.update { current ->
            // 高亮车按号在最新结果里重找：找得到才画（车被骑走 / 被筛掉时高亮自然消失）。
            // 列表里没有时退回按车号查到的那一份（`fetchCarDetail`，2026-09-30）——
            // 它就是为"不在这一批里"的车兜底的
            val highlighted = current.highlightCarNum?.let { num ->
                clusters.asSequence().flatMap { it.bikes }.firstOrNull { it.carNum == num }
                    ?: fetchedCarDetail?.takeIf { it.carNum == num }
            }
            current.copy(
                clusters = clusters,
                // 「按车辆」模式的唯一数据源：同一套筛选、不吃簇封顶（见 BikeMapUiState.bikes）
                bikes = shown,
                bikeCount = shown.size,
                clusterOverflow = allClusters.size - clusters.size,
                // 列表换了一批，展开态只在那个停车点还在时保留；按车辆模式没有卡片可展开
                expandedKey = if (current.byBike) {
                    null
                } else {
                    current.expandedKey?.takeIf { key -> clusters.any { it.key == key } }
                },
                highlightedCar = highlighted,
            )
        }
    }

    /**
     * 安排一次查询。三个开关各管一件事，别混：
     *
     * [immediate] = 跳过 [QUERY_CONFIRM_MS] 合并窗口（用户动作：进页 / 定位 / 刷新 / 回到校区 /
     * 跟随时换了片区域）。拖动走的那条（[onMapSettled]）已经由 View 层确认"地图停了"，
     * 留那个窗口只是合并紧随其后的收尾事件。
     *
     * [force] = 跳过位移闸门（[movedEnough]）。只有**用户明确要最新**的路径用：刷新按钮、
     * 骑行结束的补查。其余一律过闸门——进页那一刻的定位常常就落在建图中心上，同一个中心
     * 再查一遍只是多 5 个请求。
     *
     * [forceZones] = 图层不吃新鲜缓存（只有「刷新」按钮用；「回到校区」是换视野，不是要最新）。
     *
     * [useCache] = 允许吃附近车辆的 30 秒内存缓存（2026-11-18）：只有拖动停手这条路用，
     * 其余路径都关掉——用户明确要最新、或换了片区域时不能拿旧快照顶替。
     */
    private fun scheduleQuery(
        immediate: Boolean = false,
        force: Boolean = false,
        forceZones: Boolean = false,
        useCache: Boolean = true,
    ) {
        debounceJob?.cancel()
        debounceJob = viewModelScope.launch {
            if (!immediate) delay(QUERY_CONFIRM_MS)
            val lat = pendingLat
            val lng = pendingLng
            val zoom = pendingZoom
            if (!force && !movedEnough(lat, lng, zoom)) return@launch
            val cached = if (useCache) {
                cachedNearby(lat, lng, BikeNearby.isZoomedOut(zoom))
            } else {
                null
            }
            if (cached != null) {
                // 命中缓存也要掐掉上一笔在飞的查询（与 [startQuery] 同一条纪律）：用户拖回
                // 旧视野时，上一笔别的区域的响应晚到会把刚摆上的这批盖掉
                queryJob?.cancel()
                applyCachedNearby(cached, lat, lng, forceZones)
                return@launch
            }
            startQuery(lat, lng, forceZones)
        }
    }

    /**
     * 起一次查询。
     *
     * 只取消上一次**查询**，不碰防抖 Job：镜头移动期间地图每一帧都会走
     * `onMapSettled`，两者共用一个 Job 的话，刚发出去的请求会被下一帧掐掉，
     * 页面就一直转圈。
     */
    private fun startQuery(lat: Double, lng: Double, forceZones: Boolean) {
        queryJob?.cancel()
        queryJob = viewModelScope.launch { query(lat, lng, forceZones) }
    }

    /**
     * 与上次**尝试过**的中心点 / 缩放比，要不要真发一次查询。从没查过一律算够。
     *
     * 缩放也算（2026-11-18）：原地放大 / 缩小虽然中心没动，但该查的范围变了——放大回来
     * 要把大范围那批车补上，缩到整座校区可见则改成只查中心一页。判据两条：**跨过
     * 缩小视图线无条件查**，同一档里变化 ≥ [ZOOM_REQUERY_STEP] 级才查（捏合过程里每一小步
     * 都发请求没有意义）。
     *
     * 上一次**失败**时无条件重查（2026-11-19）：同一片再查一次不是浪费，用户拖一拖或点
     * 「定位」就是在重试；不认这条的话，首查失败后再点定位会被闸门静默吞掉。
     */
    private fun movedEnough(lat: Double, lng: Double, zoom: Double): Boolean {
        if (_uiState.value.failure != null) return true
        val lastLat = lastAttemptLat ?: return true
        val lastLng = lastAttemptLng ?: return true
        if (BikeNearby.distanceMeters(lastLat, lastLng, lat, lng) >=
            BikeNearby.MIN_REQUERY_SHIFT_METERS
        ) {
            return true
        }
        val lastZoom = lastAttemptZoom ?: return true
        if (BikeNearby.isZoomedOut(lastZoom) != BikeNearby.isZoomedOut(zoom)) return true
        return abs(zoom - lastZoom) >= ZOOM_REQUERY_STEP
    }

    /** 覆盖这个点的新鲜结果（见 [NearbyBikeCache]）；口径（缩小视图与否）必须一致。 */
    private fun cachedNearby(
        lat: Double,
        lng: Double,
        zoomedOut: Boolean,
    ): NearbyBikeCacheEntry? =
        NearbyBikeCache.bestCovering(nearbyCache, lat, lng, System.currentTimeMillis(), zoomedOut)

    /**
     * 用上一批结果落地：车辆接口一个请求都不发，图层照旧走它自己的落盘缓存 / 网络。
     * 落地走 [applyResult] 同一条路（参照点、聚簇、待定位车都由它统一处理）。
     *
     * [lat] / [lng] 是**这次请求的中心**（与缓存中心最多差 [NearbyBikeCache.COVER_RADIUS_METERS]）：
     * 按它落状态，距离与「距中心」才对应现在看到的这一片；缓存条目的时间仍按原查询时刻记。
     */
    private suspend fun applyCachedNearby(
        entry: NearbyBikeCacheEntry,
        lat: Double,
        lng: Double,
        forceZones: Boolean,
    ) {
        fetchZones(lat, lng, forceZones)
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "缓存命中（${entry.bikes.size} 条 · 缩小视图=${entry.zoomedOut}）")
        }
        applyResult(
            lat = lat,
            lng = lng,
            bikes = entry.bikes,
            stillLoading = false,
            zoom = pendingZoom,
            zoomedOut = entry.zoomedOut,
            updatedAtMillis = entry.fetchedAt,
        )
        // 缓存命中也补齐图层编排（与正常查询同一套）：本次会话第一次查询若曾把图层请求挂在
        // 「还没有车当上下文」上，这里趁 fetched 已就位补发；换校区时也用这片的车号纠正
        flushPendingZone()
        reloadZonesForSite(lat, lng, entry.bikes)
    }

    /** 记一条结果供下一次平移复用（只在内存，见 [NearbyBikeCache]）。 */
    private fun rememberNearby(
        lat: Double,
        lng: Double,
        bikes: List<NearbyBike>,
        zoomedOut: Boolean,
    ) {
        nearbyCache = NearbyBikeCache.put(
            nearbyCache,
            NearbyBikeCacheEntry(
                lat = lat,
                lng = lng,
                fetchedAt = System.currentTimeMillis(),
                bikes = bikes,
                zoomedOut = zoomedOut,
            ),
        )
    }

    private suspend fun query(lat: Double, lng: Double, forceZones: Boolean) {
        // 两段耗时只在 debug 打：量「松手 → 首屏落地」与「→ 撒点合并落地」，用法见 rules/ebike.md
        val startedAt = SystemClock.elapsedRealtime()
        // 缩放**开工时定一次**：查询期间用户可能又捏了一下，这批结果该按开工时那个范围解释
        val zoom = pendingZoom
        val zoomedOut = BikeNearby.isZoomedOut(zoom)
        // 「上次尝试过的中心 / 缩放」在**开工时**就记下（2026-11-19）：一笔查询要 0.5~2 秒才落地，
        // 这期间进页那次定位刚好回来（[onLocated]）或用户点了一下「定位」，位移闸门必须看得见
        // 这笔在飞的请求——否则会再发一轮、把这一笔掐掉：进页白打 5 个请求，首屏还要再晚半秒。
        // 落地时 [applyResult] / [failQuery] 会再写一遍同样的值（缓存命中的那条路只有它们写）。
        lastAttemptLat = lat
        lastAttemptLng = lng
        lastAttemptZoom = zoom
        _uiState.update { it.copy(loading = true, failure = null) }
        // 图层与车辆列表**并行**发（2026-09-28）：它只需要坐标与一个"哪类车"的上下文，
        // 车号用上一次那批车里的任意一辆即可——旧写法等中心结果回来才发，白多一个往返
        fetchZones(lat, lng, forceZones)

        val centerBikes = try {
            // 解析挪到 Default（2026-10-01）：响应回来后的 JSON 解析跑在主线程，
            // 一次刷新最多 5 份 × 20 个对象，那几毫秒正好压在拖动停手那一帧上
            val parsed = withContext(Dispatchers.Default) {
                BikeNearby.parse(client.queryNearbyJson(lat, lng), lat, lng)
            }
            when (parsed) {
                is NearbyParseResult.Ok -> parsed.bikes
                NearbyParseResult.ServiceError -> {
                    failQuery(lat, lng, BikeFailure.Service, zoom)
                    return
                }
                NearbyParseResult.Malformed -> {
                    failQuery(lat, lng, BikeFailure.Malformed, zoom)
                    return
                }
            }
        } catch (cancel: CancellationException) {
            // 取消要原样抛出去：吞掉它会让协程的取消变成一次"查询失败"，页面留下假错误
            throw cancel
        } catch (error: Exception) {
            failQuery(lat, lng, KqcxBikeClient.classify(error), zoom)
            return
        }

        // 中心点没返回满，说明这一带能查到的就这么多，不用再撒点浪费请求
        val sampling = centerBikes.size >= SAMPLE_PAGE_SIZE
        // 记视野。写完这一次就够了，不需要在退出时再写一遍。
        // 不占关键路径：DataStore 写入是磁盘事务，压在两批请求之间只是白等它
        viewModelScope.launch { prefs.setEbikeMapViewport(lat, lng, zoom) }
        // 没有车当上下文时（本次会话第一次查询）在这里补发一次图层
        flushPendingZone()
        // 换校区了（比如从本校拖到隔壁师大）：图层要用这一带的车号重拉，官方同口径
        reloadZonesForSite(lat, lng, centerBikes)

        // 缩小视图（2026-11-18 用户要求）：整座校区都在屏幕里时，用户是在看方位、不是找车，
        // 不再撒点 + 补全（那一次要 5~13 个请求），只留中心这一页、展示范围也收小。
        // 放大回来会因缩放变化触发一次正常查询（见 [movedEnough]），大范围那批车会补上。
        if (zoomedOut) {
            applyResult(lat, lng, centerBikes, stillLoading = false, zoom = zoom)
            rememberNearby(lat, lng, centerBikes, zoomedOut = true)
            if (BuildConfig.DEBUG) {
                Log.d(
                    TAG,
                    "落地 +${SystemClock.elapsedRealtime() - startedAt}ms" +
                        "（缩小视图中心 ${centerBikes.size} 条 · z${"%.1f".format(zoom)}）",
                )
            }
            return
        }

        if (!sampling) {
            applyResult(lat, lng, centerBikes, stillLoading = false, zoom = zoom)
            rememberNearby(lat, lng, centerBikes, zoomedOut = false)
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "落地 +${SystemClock.elapsedRealtime() - startedAt}ms（${centerBikes.size} 条）")
            }
            return
        }

        // 中心已回满：**环 + 大桩补全并行跑，合并后只落一次**（2026-10-29 用户口径
        // 「车辆数量一段一段递增刷新，不够稳定」）。旧版中心先落、环再落、补全再落，
        // 同一处停车点的数字会 20 → 33 → 45 跳三下；宁可多等一个环的往返（头部就是
        // 「刷新中…」），一次落定。撒点期间不摘 loading，正是这个意思。
        //
        // 环半径按视口缩放（[BikeNearby.sampleRadiusMeters]）：放大到只看一条路时不再查
        // 视界之外的几百米（2026-10-29 用户口径「范围跟着视图比例缩小」）。
        // 「按车辆」模式上限更小（250 米，见 [BikeNearby.BIKE_MODE_SAMPLE_RADIUS_METERS]）。
        //
        // 采样点固定 4 个（2026-11-18）：更小的缩放已经走上面的缩小视图分支（只查中心），
        // 原来的「z < 16.5 撒 8 个方位」不再有可达区间，一并删掉——少 4 个请求。
        val byBike = _uiState.value.byBike
        val ring = RING_SAMPLE_POINTS
        val radius = BikeNearby.sampleRadiusMeters(viewportHalfDiagonalMeters, byBike = byBike)
        val (ringBikes, fillBikes) = coroutineScope {
            val ringJob = async { mergeSamples(lat, lng, centerBikes, ring, radius) }
            // 补全的桩从**中心结果**里挑：用户正看着的那处大桩一定在最近的 20 辆里，
            // 不必等环回来（等了就又是两拍落地）。
            // **「按车辆」模式不补**（2026-10-30 用户口径「请求少一点」）：地图上没有聚合圈、
            // 那个数字没人看，4 个偏移查询纯属白花——一次刷新从 9 个请求降到 5 个。
            val fillJob = if (byBike) null else async { fillBigSpot(lat, lng, centerBikes) }
            ringJob.await() to (fillJob?.await().orEmpty())
        }
        val merged = BikeNearby.mergeBikes(ringBikes, fillBikes)
        applyResult(lat, lng, merged, stillLoading = false, zoom = zoom)
        rememberNearby(lat, lng, merged, zoomedOut = false)
        if (BuildConfig.DEBUG) {
            Log.d(
                TAG,
                "落地 +${SystemClock.elapsedRealtime() - startedAt}ms" +
                    "（中心 ${centerBikes.size} → 合并 ${ringBikes.size} → 补全 ${merged.size} 条" +
                    " · 环 $ring 点 · 半径 ${radius.toInt()} 米 · z${"%.1f".format(zoom)}）",
            )
        }
    }

    /**
     * 拉还车点 / 禁停区图层。[fetched] 里挑第一辆车当 `carNum` 上下文——没有车就不发请求
     * （接口要车号，编一个出来没有意义）。失败静默、保留上一层。
     *
     * 2026-09-28 起**先吃落盘缓存**（用户口径：停车点缓存）：命中覆盖当前中心的缓存就
     * **立即**摆上（不等网络，"P" 与车辆列表同时出现），新鲜的话连请求都不发——图层是
     * 最不容易变的一层，而旧写法拖动一下就要等一个往返才有 "P"，正是用户报的慢。
     * 判定全在 [ZoneCache]（纯逻辑、有单测），这里只管编排。
     *
     * **两种使用方式都拉**（2026-10-01 改）：图层接口不需要凭证（见
     * `KvcxSessionRepository.queryZones`），小程序方式也能看还车点；缓存那一层照旧
     * 先吃（本地数据，摆上比空白好），新鲜就不联网。
     */
    private fun fetchZones(lat: Double, lng: Double, force: Boolean) {
        val source = zoneSource ?: return
        val needFetch = applyCachedZones(lat, lng, force)
        // 这一带有车就用这一带的车号（官方同口径）；没车就用上一次的上下文——
        // 接口只要"哪个校区"，没车不等于没还车点（见 ZoneCarContext 的注释）
        val context = zoneContextOf(fetched) ?: lastZoneCar
        if (context == null) {
            // 本次会话还没拿到过任何车：先记下这次的中心，等中心结果回来再发
            if (needFetch) pendingZone = ZonePending(lat, lng, force)
            return
        }
        if (needFetch) launchZoneRequest(source, lat, lng, context)
    }

    /**
     * 这一带的图层上下文该用哪辆车。
     *
     * **「只看本校」开着时优先本校车**（2026-09-29）：图层是按车号所属校区出的，若这一带
     * 最近的是隔壁师大（或别的校区）的车，拿它当上下文就会把图层换成那个校区的还车点——
     * 再经「只看本校」的围栏过滤，本片的「P」会**整片消失**（用户 2026-09-29 报的
     * 「加载出来又突然消失」就是这个）。开着只看本校时固定用本校车，图层就始终是本校校区的，
     * 围栏只削掉边缘那一两个点，不会整片空掉。
     *
     * 关着时用这一带最近的那辆（官方口径：`carNum: list[0].name`），图层跟着所看区域走。
     *
     * **本校一辆车都没有时退回最近那辆**（2026-11-18，用户报「骑到很远的另一个地方，
     * 那片红色还车区一个都没有」）：原来这里直接回 null，于是 `reloadZonesForSite` 整条
     * 不跑，图层永远停在出发时那个校区——人已经在另一个校区了，屏幕上当然一片空白。
     * 现在拿得到"哪个校区"就问哪个校区，**围栏过滤由 `applyZones` 按图层所属校区决定**
     * （别人校区的点用自己的围栏筛必然筛空，那种情况下不筛）。
     */
    private fun zoneContextOf(bikes: List<NearbyBike>): ZoneCarContext? {
        val car = if (_uiState.value.onlyOurCampus) {
            bikes.firstOrNull { BikeNearby.isOurCampus(it.campusName) } ?: bikes.firstOrNull()
        } else {
            bikes.firstOrNull()
        }
        return car?.let { ZoneCarContext(it.carNum, it.campusName) }
    }

    /**
     * 缓存命中就立刻把图层摆进状态；返回 true = 还要联网。
     *
     * 缓存里有覆盖当前中心的条目、但已过期时不返回 true 也不行——过期就该刷新，
     * 只是刷新期间用户看的是旧图层而不是空白（[fetchZones] 调用点不阻塞它）。
     * [force]（用户点了「刷新」）直接要求联网。
     */
    private fun applyCachedZones(lat: Double, lng: Double, force: Boolean): Boolean {
        val cached = ZoneCache.bestCovering(zoneEntries, lat, lng) ?: return true
        // 在飞的旧请求（更早的中心）回来时不该盖掉刚摆上的缓存：序号往前推一格让它作废，
        // 顺手取消——那发的数据已经用不上了，别白占一次往返
        zoneNonce += 1
        zoneJob?.cancel()
        fetchedZones = cached.zones
        applyZones()
        if (force) return true
        return !ZoneCache.isFresh(cached, System.currentTimeMillis())
    }

    /** 缓存读回来 / 补发之前，把覆盖当前中心的图层先摆上（网上还没结果时才摆）。 */
    private fun seedZonesFromCache() {
        // 网络结果（哪怕是空图层）已经是"当前的说法"，别拿旧缓存把它盖回去
        if (zonesFromNetwork || fetchedZones != KvcxZones.EMPTY) return
        val best = ZoneCache.bestCovering(zoneEntries, pendingLat, pendingLng) ?: return
        fetchedZones = best.zones
        applyZones()
    }

    /** 中心结果落地后补发那次被挂起的图层请求（[pendingZone]）。 */
    private fun flushPendingZone() {
        val source = zoneSource ?: return
        val pending = pendingZone ?: return
        pendingZone = null
        val context = zoneContextOf(fetched) ?: lastZoneCar ?: return
        // 补发前再吃一次缓存：等中心结果这段时间里，缓存可能刚读回来（进页那次）
        if (applyCachedZones(pending.lat, pending.lng, pending.force)) {
            launchZoneRequest(source, pending.lat, pending.lng, context)
        }
    }

    /**
     * 新一带的图层上下文与当前那层**不同校区**时，用它重拉一遍（官方同口径）。
     *
     * 只在换校区时多发一次：同校区拖动时 [lastZoneCar] 的校区与它一致，直接返回。
     * 缓存命中（没联网）时也走这里——缓存条目不记校区，跨校区命中旧图层时靠这一步纠正。
     */
    private fun reloadZonesForSite(
        lat: Double,
        lng: Double,
        bikes: List<NearbyBike>,
    ) {
        val source = zoneSource ?: return
        val context = zoneContextOf(bikes) ?: return
        if (context.campusName == lastZoneCar?.campusName) return
        launchZoneRequest(source, lat, lng, context)
    }

    private fun launchZoneRequest(
        source: KvcxZoneSource,
        lat: Double,
        lng: Double,
        context: ZoneCarContext,
    ) {
        val nonce = ++zoneNonce
        lastZoneCar = context
        zoneJob?.cancel()
        zoneJob = viewModelScope.launch {
            // 取消要原样抛出去（与 `sampleAt` 同一口径）：吞掉它会让取消变成一次"图层失败"
            val zones = try {
                source.queryZones(lat, lng, context.carNum)
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                null
            } ?: return@launch
            // 旧响应作废：拖动期间可能有更新的请求已经发出（各自并发，回来的顺序不保证）
            if (nonce != zoneNonce) return@launch
            // **空图层不覆盖上一层**（2026-09-29 用户报「加载出来又突然消失」）：解析失败
            // （非 JSON / 结构不符）与业务错误码都会回空，拿它盖掉整片「P」是最糟的表现
            // ——「失败静默保留上一层」的同一口径。真的没有还车点的校区也走这条：旧图层画在
            // 它自己的坐标上，视野移过去自然看不见，不比整片空白差
            if (zones.isEmpty) return@launch
            zonesFromNetwork = true
            fetchedZones = zones
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "zone net ok: spots=${zones.parkSpots.size} campus=${context.campusName}")
            }
            applyZones()
            // 成功结果写回缓存（只存非空，上面已挡掉空表）
            zoneEntries = ZoneCache.put(
                zoneEntries,
                ZoneCacheEntry(lat, lng, System.currentTimeMillis(), zones),
            )
            zoneCache?.save(zoneEntries)
        }
    }

    /** 按当前筛选口径把 [fetchedZones] 落进状态（开关变化 / 新数据到达时都走这里）。 */
    private fun applyZones() {
        // 围栏只对**本校校区的图层**有意义（2026-11-18）：`campusOnly` 用的是本校那条围栏，
        // 拿它去筛别人校区的点必然筛空——人到了另一个校区，屏幕就什么都没了（用户报的
        // 「骑远了红色区域一个都不出来」）。图层不是本校的就原样画，筛选只管车型/围栏那条口径。
        val zonesOfOurCampus = lastZoneCar?.let { BikeNearby.isOurCampus(it.campusName) } ?: true
        val zones = if (_uiState.value.onlyOurCampus && zonesOfOurCampus) {
            fetchedZones.campusOnly(BikeNearby::inCampusFence)
        } else {
            fetchedZones
        }
        // 还车点图层的自证日志（debug 才有）：用户报「一片 P 都没有」时先看这一行——
        // fetched=0 是没拿到数据，fetched>0 而落进状态为 0 是筛选吃掉的，两者修法完全不同。
        if (BuildConfig.DEBUG) {
            Log.d(
                TAG,
                "zones: fetched=${fetchedZones.parkSpots.size}/${fetchedZones.nogoZones.size}" +
                    " -> applied=${zones.parkSpots.size}/${zones.nogoZones.size}" +
                    "（onlyOurCampus=${_uiState.value.onlyOurCampus}" +
                    " ourCampus=${zonesOfOurCampus} campus=${lastZoneCar?.campusName}）",
            )
        }
        _uiState.update { it.copy(zones = zones) }
    }

    /**
     * 把一批车落成当前结果：状态、聚类、锚点记账都走这一条路。
     *
     * [stillLoading] = true 用于撒点在飞的中间态：列表已经可用，头部留着进度圈。
     * 聚簇与排序（最多一百多辆车）放 Default：它跟解析一样压在同一帧上。
     *
     * [zoom] 是**这批查询开工时的缩放**：结果口径（[BikeMapUiState.zoomedOut]）与下一次
     * 「要不要重查」的缩放闸门都用它，不用落地那一刻的 `pendingZoom`（查询期间用户又捏了
     * 一下的话，那已经不是这批数据的口径了）。
     */
    private suspend fun applyResult(
        lat: Double,
        lng: Double,
        bikes: List<NearbyBike>,
        stillLoading: Boolean,
        zoom: Double,
        zoomedOut: Boolean = BikeNearby.isZoomedOut(zoom),
        updatedAtMillis: Long = System.currentTimeMillis(),
    ) {
        withContext(Dispatchers.Default) {
            fetched = bikes
            fetchedLat = lat
            fetchedLng = lng
            lastAttemptLat = lat
            lastAttemptLng = lng
            lastAttemptZoom = zoom
            // 参照点只在**查询落地时**定一次：拖动过程中不翻，列表顺序才稳（2026-09-30 用户反馈）
            val current = _uiState.value
            val userLat = current.userLat
            val userLng = current.userLng
            val fromUser = userLat != null && userLng != null &&
                BikeNearby.distanceMeters(userLat, userLng, lat, lng) <= USER_ANCHOR_RADIUS_METERS
            _uiState.update {
                it.copy(
                    loading = stillLoading,
                    queried = true,
                    failure = null,
                    zoomedOut = zoomedOut,
                    updatedAtMillis = updatedAtMillis,
                    anchorLat = if (fromUser) userLat else lat,
                    anchorLng = if (fromUser) userLng else lng,
                    anchorFromUser = fromUser,
                )
            }
            rebuildClusters()
            // 出码页带车号跳进来的「地图查看」：结果到了就自动定位
            tryResolvePendingFocusCar()
        }
    }

    private fun failQuery(lat: Double, lng: Double, failure: BikeFailure, zoom: Double) {
        lastAttemptLat = lat
        lastAttemptLng = lng
        lastAttemptZoom = zoom
        // 带车号进页那条路若还卡在「等首查」上，这一轮没查成就等于没结论——自动开锁目标
        // 随之作废（别留一个悬空目标被后面某次成功刷新意外触发；见 `autoUnlockCar`）。
        // `pendingFocusCar` 本身保留：下次查询成功仍照旧定位 / 高亮（那是只读动作）
        if (pendingFocusCar != null) autoUnlockCar = null
        _uiState.update { it.copy(loading = false, queried = true, failure = failure) }
    }

    /**
     * 撒点补齐：中心的结果不够（返回满 20 辆，说明还有更远的没给），
     * 就在周围各查一次，按车号合并。[ringCount] 固定为
     * [RING_SAMPLE_POINTS]（缩小视图不撒点，走 [query] 里的轻量分支）；
     * [radiusMeters] 由 [BikeNearby.sampleRadiusMeters] 按视口给（放大时环跟着缩）。
     *
     * 采样请求并行发，单个点失败只丢这个点——少看到一辆车，总比整页报错强。
     */
    private suspend fun mergeSamples(
        lat: Double,
        lng: Double,
        center: List<NearbyBike>,
        ringCount: Int,
        radiusMeters: Double,
    ): List<NearbyBike> {
        val merged = LinkedHashMap<String, NearbyBike>()
        center.forEach { bike -> merged[bike.carNum] = bike }
        val rings = BikeNearby.samplePoints(lat, lng, ringCount, radiusMeters).drop(1)
        val extra = coroutineScope {
            rings.map { point -> async { sampleAt(point.lat, point.lng) } }.awaitAll()
        }
        extra.forEach { list -> list.forEach { bike -> merged.putIfAbsent(bike.carNum, bike) } }
        return merged.values.toList()
    }

    /**
     * 大桩补全（2026-10-29 用户口径 A）：服务端一次只回最近 20 辆，成员数 ≥ 20 的停车点
     * 一定被截断过——它的数字是撒点并集的产物，地图中心挪一点就从 28 变 33 / 35。
     *
     * 做法：挑离查询中心最近的一处大桩（[BikeNearby.nearestTruncatedSpot]），以它的落点
     * 为中心补 4 个偏移查询（±[SPOT_FILL_OFFSET_METERS] 米），并集就能覆盖整桩
     * （2026-09-27 实测的 38 辆大桩：只查桩心 20/38，加 4 个偏移点 38/38）。查询几何锚在
     * 桩上、不随地图中心动，数字因此稳定。**返回的只是补到的车**，由调用方与环结果合并
     * （两批并行跑、一次落地，见 [query]）。
     *
     * **缓存 [SPOT_FILL_TTL_MS]**：平移时同一处桩不重发；过期重拉（车会流动）。
     * **只补最近的一处**、且只在它离查询中心 ≤ [SPOT_FILL_MAX_DISTANCE_METERS] 时补——
     * 视野里两处大桩都补是 8 个请求，代价超过收益。刷新按钮会清缓存（[refresh]）。
     */
    private suspend fun fillBigSpot(
        lat: Double,
        lng: Double,
        bikes: List<NearbyBike>,
    ): List<NearbyBike> {
        val spot = withContext(Dispatchers.Default) {
            BikeNearby.nearestTruncatedSpot(
                clusters = BikeNearby.cluster(bikes),
                lat = lat,
                lng = lng,
                minBikes = SPOT_FILL_MIN_BIKES,
                maxDistanceMeters = SPOT_FILL_MAX_DISTANCE_METERS,
            )
        } ?: return emptyList()
        val now = System.currentTimeMillis()
        val cached = spotFills[spot.key]?.takeIf { now - it.at in 0..SPOT_FILL_TTL_MS }
        if (cached != null) return cached.bikes
        val fetched = fetchSpotFill(spot.lat, spot.lng)
        // 空结果不缓存：4 个点全失败时下一次刷新还要重试（与图层「空不覆盖」同一口径）
        if (fetched.isNotEmpty()) {
            spotFills[spot.key] = SpotFill(now, fetched)
            spotFills.entries.removeAll { (_, value) -> now - value.at > SPOT_FILL_TTL_MS }
        }
        return fetched
    }

    /** 按桩补点：桩心 + 四个对角偏移点各查一次，失败的点只丢它自己（与撒点同口径）。 */
    private suspend fun fetchSpotFill(lat: Double, lng: Double): List<NearbyBike> {
        val points = BikeNearby.samplePoints(
            lat,
            lng,
            ringCount = SPOT_FILL_SAMPLES,
            radiusMeters = SPOT_FILL_OFFSET_METERS,
        ).drop(1)
        return coroutineScope {
            points.map { point -> async { sampleAt(point.lat, point.lng) } }.awaitAll().flatten()
        }
    }

    /** 大桩补全的会话缓存（按簇键；超过 [SPOT_FILL_TTL_MS] 过期）。 */
    private data class SpotFill(val at: Long, val bikes: List<NearbyBike>)
    private val spotFills = HashMap<String, SpotFill>()

    /**
     * 单个采样点。服务端报错、结构不符、网络异常一律算空——采样点失败不该拖垮整页。
     * 解析同样走 Default：并行回来的几份 payload 一起在主线程解，正好顶在停手那一帧上。
     */
    private suspend fun sampleAt(lat: Double, lng: Double): List<NearbyBike> = try {
        val parsed = withContext(Dispatchers.Default) {
            BikeNearby.parse(client.queryNearbyJson(lat, lng), lat, lng)
        }
        when (parsed) {
            is NearbyParseResult.Ok -> parsed.bikes
            NearbyParseResult.ServiceError -> emptyList()
            NearbyParseResult.Malformed -> emptyList()
        }
    } catch (cancel: CancellationException) {
        throw cancel
    } catch (_: Exception) {
        emptyList()
    }

    companion object {
        /**
         * 拖动停手后到发请求之间的一小段合并窗口（毫秒）。
         *
         * View 层（`SettleWatcher`）已经确认"地图停了"才调 [onMapSettled]，所以这里**不再做
         * 停手判定**，只用来合并"停手信号 + 紧随其后的收尾事件"：osmdroid 在滑动结束前后会补
         * 一两个滚动回调，没有这个窗口就会多发一次请求。官方小程序在 drag-end 上是零延迟发，
         * 我们这 80ms 就是那点差距。
         */
        private const val QUERY_CONFIRM_MS = 80L

        /** 接口一次返回的封顶条数（2026-09-23 实测）。返回满这个数就认为还有更远的没给。 */
        private const val SAMPLE_PAGE_SIZE = 20

        /**
         * 列表与地图最多画多少处停车点（2026-10-01）。
         *
         * 列表每项都是一张 `AppCard`，地图每帧给每簇画圆 + 数字，成本都跟簇数成正比；
         * 校园里密度高的时段能到几十处。取 24（约 4 屏）之外的多半没人看，尾部给一行
         * 「还有 N 处更远」。**车辆总数不受它影响**（`bikeCount` 走未封顶的那份）。
         */
        private const val MAX_CLUSTER_COUNT = 24

        /**
         * 正常查询撒几个采样点（2026-11-18 固定 4）。
         *
         * 更小的缩放整档走缩小视图的轻量查询（只查中心，见 `query`），所以这里不再有
         * 「z < 16.5 撒 8 个方位」那一档。
         */
        private const val RING_SAMPLE_POINTS = 4

        /**
         * 同一档缩放里变化多少级才重查（2026-11-18）。捏合过程会连续触发停手回调，
         * 每一小步都发请求纯属浪费；0.75 级已是肉眼可见的视野变化。
         */
        private const val ZOOM_REQUERY_STEP = 0.75

        /**
         * 大桩补全（2026-10-29，见 [fillBigSpot]）的**挑桩**门槛：中心那一页只有 20 条，
         * 最近那处大桩常常占不满 20（末尾混进旁边桩的车），按 20 挑会漏；取 10 = 这一页
         * 里**占了一半以上**，足以认定"用户正看着一处大桩、它一定被截断过"。
         */
        private const val SPOT_FILL_MIN_BIKES = 10

        /** 大桩补全：桩心之外补几个偏移查询、偏移多少米（覆盖一跨几十米的大车桩）。 */
        private const val SPOT_FILL_SAMPLES = 4
        private const val SPOT_FILL_OFFSET_METERS = 25.0

        /** 大桩补全只补离查询中心这么近的那一处（用户正在看的那处桩）。 */
        private const val SPOT_FILL_MAX_DISTANCE_METERS = 500.0

        /** 大桩补全结果的缓存时长：平移时同一处桩不重发，过期重拉。 */
        private const val SPOT_FILL_TTL_MS = 60_000L

        /** 查询耗时日志的 tag（只在 debug 打）。 */
        private const val TAG = "BikeMapQuery"

        /** 失败类别的用户文案（UI 层唯一出处）。 */
        fun failureText(failure: BikeFailure): String = when (failure) {
            BikeFailure.Network -> "网络连接失败，请检查网络后重试"
            BikeFailure.Timeout -> "请求超时，请稍后重试"
            BikeFailure.Service -> "车辆服务暂时不可用，请稍后重试"
            BikeFailure.Malformed -> "车辆数据解析失败，请稍后重试"
        }
    }

    class Factory(
        private val client: KqcxBikeClient,
        private val prefs: DisplayPrefsStore,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            // 快趣会话按 context 现取（Graph 单例）；不可用时用户只是看不到本机用车入口。
            // 图层缓存同样包在 runCatching 里：它只是"省一个往返"的优化，取不到就不缓存，
            // 不该把 VM 的创建整个拖崩（旁边那条 kqcx 也是这个口径）
            BikeMapViewModel(
                client,
                prefs,
                runCatching { Graph.kqcxCore(Graph.appContext) }.getOrNull(),
                runCatching { Graph.kqcxCore(Graph.appContext) }.getOrNull(),
                runCatching { Graph.zoneCacheStore(Graph.appContext) }.getOrNull(),
                runCatching { Graph.kvcxRide(Graph.appContext) }.getOrNull(),
            ) as T
    }
}

/**
 * 底部车辆面板的高度档位（dp），页面与状态共用一套口径。
 *
 * 存**定值**而不是屏幕比例：比例在窗口变化时会自己变，面板跟着跳，用户明确要求过别跳。
 * 上限由页面按「窗口高度 − 固定预留」算（`RideScreen.BOTTOM_RESERVE_DP`），预留是**常数**、
 * 不跟底部块实测高度走——跟着走会让卡片进出时面板被夹小（2026-10-14 修的「弹窗跳动」）。
 *
 * **2026-10-01 起面板只装列表**：把手 + 头行 + 列表。主动作条与免责那行搬到面板**之外**
 * （页面底部的常驻块，见 `RideScreen`）。旧版把动作条塞进这个定高面板的 footer 里，
 * 骑行态的仪表盘一长，面板最矮时的把手 + 仪表盘 + 免责就已经超过面板高度，
 * 底部按钮被面板的圆角裁掉——高度是定值，内容却是变量，这个组合迟早出事。
 *
 * 下限 240 = 把手 22 + 头行约 48 + 列表约 170。默认值从 380 收到 270：动作条搬出去之后
 * 底部的总高度是「面板 + 常驻块」，不把默认值收回来，地图会比改动前少一块。
 * 这只是默认值，用户拖过的档位（[panelHeightDp]）照旧保留，只受上限约束。
 */
internal const val DEFAULT_PANEL_HEIGHT_DP = 270f
internal const val MIN_PANEL_HEIGHT_DP = 240f
internal const val MAX_PANEL_HEIGHT_DP = 620f

/**
 * 列表用「距你」而不是「距查询中心」排的半径（米，2026-09-30）。
 *
 * 查询中心离用户在这个半径内 = 用户在看自己周围，距离按用户位置算（能直接决定走哪辆）；
 * 超出 = 用户把地图拖到别处了，按查询中心算——顺序才和地图对得上，也不会因为蓝点在动
 * 而反复重排。
 */
internal const val USER_ANCHOR_RADIUS_METERS = 150.0
