package edu.jxslu.schedule.ui.ebike

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import edu.jxslu.schedule.data.net.SharedHttp
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.readBytes
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image as SkiaImage

/**
 * iOS 的底图画布瓦片来源（2026-11-26 晚）。
 *
 * ## 它解决的问题
 *
 * 画布地图（[BikeMapCanvas]）在没有底图时只有尺度网格——车标、围栏、还车点都在，但看不出
 * 「那是哪栋楼、路在哪」。用户装完第一版的原话是「地图不显示」，说的就是这个。Android 的
 * 底图由高德 / osmdroid 视图提供，iOS 上只能自己取瓦片、解码、贴进画布。
 *
 * ## 数据源与 Android 回退底图是同一份
 *
 * URL 逐字照 `app/.../ui/ebike/OsmMapView.kt` 的 `AMAP_TILE_SOURCE`：
 * `appmaptile?x=&y=&z=&lang=zh_cn&size=1&style=9`，坐标基准 GCJ-02，与运营方下发的车辆
 * 坐标同一个基准，所以**不做任何坐标转换**。四台 `wprd01..04` 按瓦片号轮换，和 Android 一样。
 *
 * 两个实测结论（2026-11-26，本机直接 curl 过接口，不需要真机）：
 *
 * | 参数 | 结果 |
 * |---|---|
 * | `size=1`（默认） | 256×256，**带路名与楼名** |
 * | `scl=2` | 512×512，但**整张图没有任何注记**（纯路网绿地）——所以宁可 3x 放大，不换清晰度 |
 * | `z=19` | 179 字节的占位图（空瓦片），所以 [BikeMapTiles.MAX_TILE_ZOOM] = 18 |
 *
 * ## 为什么在 iosMain 里解 PNG
 *
 * `ImageBitmap` 在本仓锁的 Compose Multiplatform 1.7.3 里**造不出来**（`decodeToImageBitmap`
 * 是 1.8+；`ImageBitmap(width, height)` 只能从空白位图画出内容）。iOS 侧唯一的入口是 skia：
 * `Image.makeFromEncoded(bytes).toComposeImageBitmap()`（接收者是 `org.jetbrains.skia.Image`；
 * 2026-11-26 CI 实测 `skia.Bitmap` 那个重载**不存在**，别改回去）。iOS 的 Compose 本来就跑在
 * skia 上，这些符号不是新增依赖；但在本仓是**第一次**出现 `org.jetbrains.skia.*`，
 * 所以列在这里：两个符号只经 macOS CI 编译验证过，真机行为（解码失败、内存压力）未验。
 *
 * ## 线程、缓存与取消
 *
 * - [load] 由画布的 `LaunchedEffect` 在主线程调用；网络请求本身是挂起的，解码丢到
 *   `Dispatchers.Default`，解完回到主线程写缓存与 [revision]（Compose 的状态只能在主线程写）。
 * - 内存缓存按插入顺序淘汰（[MAX_CACHED_TILES] 块 ≈ 16MB）：校园尺度来回拖动就是这十几块，
 *   不落磁盘（页面上的「地图缓存」卡在 iOS 上是 null，见 `RideScreen` 的 `mapCache`）。
 * - 同一块瓦片的并发请求去重（[inFlight]）；视野变化导致协程取消时，`finally` 会把它从
 *   在途集合里摘掉，回来还能重新请求。
 */
internal class IosBikeMapTiles : BikeMapTileSource {

    private val revisionState = mutableIntStateOf(0)

    override val revision: State<Int> get() = revisionState

    /** 插入序缓存：满了从头淘汰。瓦片是幂等的，重新取一次只是多一个请求。 */
    private val cache = LinkedHashMap<BikeTileKey, ImageBitmap>()
    private val order = ArrayDeque<BikeTileKey>()
    private val inFlight = mutableSetOf<BikeTileKey>()

    override fun cached(key: BikeTileKey): ImageBitmap? = cache[key]

    override suspend fun load(keys: List<BikeTileKey>) {
        if (keys.isEmpty()) return
        val wanted = keys.filter { cache[it] == null && inFlight.add(it) }
        if (wanted.isEmpty()) return
        try {
            // 一轮最多 4 个并发：一屏瓦片是 6..12 块，串行太慢、全并发对瓦片服务器不礼貌
            coroutineScope {
                wanted.chunked(MAX_PARALLEL_REQUESTS).forEach { chunk ->
                    chunk.map { key -> async { fetchAndDecode(key) } }.awaitAll()
                }
            }
        } finally {
            inFlight.removeAll(wanted.toSet())
        }
    }

    private suspend fun fetchAndDecode(key: BikeTileKey) {
        val bytes = fetchTileBytes(key) ?: return
        val bitmap = withContext(Dispatchers.Default) {
            runCatching {
                SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap()
            }.getOrNull()
        } ?: return
        // 回到主线程：Compose 的快照状态不能从后台线程写
        cache[key] = bitmap
        order.addLast(key)
        while (order.size > MAX_CACHED_TILES) {
            cache.remove(order.removeFirst())
        }
        revisionState.intValue = revisionState.intValue + 1
    }
}

/**
 * 取一块瓦片的字节。失败（网络、非 2xx、超时）一律回 `null`，这一块这一帧就不画——
 * 不重试、不弹提示：底图缺一块是可接受的降级，网格与车标照常。
 */
private suspend fun fetchTileBytes(key: BikeTileKey): ByteArray? = runCatching {
    val host = TILE_HOSTS[(key.x + key.y) % TILE_HOSTS.size]
    val url = "https://$host/appmaptile?x=${key.x}&y=${key.y}&z=${key.z}" +
        "&lang=zh_cn&size=1&style=9"
    val response = SharedHttp.base.get(url) {
        header("User-Agent", TILE_USER_AGENT)
    }
    if (!response.status.isSuccess()) return@runCatching null
    response.readBytes()
}.getOrNull()

/**
 * 取一个 iOS 侧的瓦片来源。**每一屏地图一个实例**：缓存只服务这一张图，
 * 离开页面就跟着组合一起回收（不常驻进程，也不跨页面共享）。
 */
@Composable
fun rememberIosBikeMapTiles(): BikeMapTileSource = remember { IosBikeMapTiles() }

/** 四台瓦片服务器，与 Android `AMAP_TILE_SOURCE` 的 `baseUrl` 列表逐字一致。 */
private val TILE_HOSTS = listOf(
    "wprd01.is.autonavi.com",
    "wprd02.is.autonavi.com",
    "wprd03.is.autonavi.com",
    "wprd04.is.autonavi.com",
)

/**
 * UA 用包名：Android 的 osmdroid 设的是 `context.packageName`（注释里写明不设会有 403），
 * iOS 沿用同一个字符串口径。**不要**删——换成一个空 UA 在真机上可能就是整屏空白。
 */
private const val TILE_USER_AGENT = "edu.jxslu.schedule.ios"

/** 一轮并发取图的上限。 */
private const val MAX_PARALLEL_REQUESTS = 4

/** 内存里最多留多少块瓦片：262KB × 64 ≈ 16MB。 */
private const val MAX_CACHED_TILES = 64
