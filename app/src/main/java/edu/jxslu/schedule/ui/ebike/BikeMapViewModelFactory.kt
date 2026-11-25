package edu.jxslu.schedule.ui.ebike

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import edu.jxslu.schedule.Graph
import edu.jxslu.schedule.data.kqcx.KqcxBikeClient
import edu.jxslu.schedule.data.prefs.DisplayPrefsStore

/**
 * `BikeMapViewModel` 的 Android 侧 Factory（iOS 移植 E2，2026-11-25）。
 *
 * 为什么单独一个文件：`ViewModelProvider` 是 JVM 类型、装配要 `Graph` + `Context`，
 * 两者都进不了 `commonMain`——ViewModel 本体已经搬进共享层，只把这个「Android 怎么建它」
 * 留在 `:app`。原来是 `BikeMapViewModel` 的嵌套类 `BikeMapViewModel.Factory`，搬不动的那一半
 * 就顺势提出来，**口径一字未改**（依赖取不到时一律 `getOrNull()`，VM 自己按 null 降级）。
 *
 * 配置变更不重建：`viewModel { ... }` 的这句话由 `RideScreen` 的默认参数完成。iOS 侧没有
 * 配置变更，按 `design/ios-port.md` §11.2 ① 用 `remember { BikeMapViewModel(...) }` 自己建。
 */
class BikeMapViewModelFactory(
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
            AppKvcxSideEffects,
        ) as T
}
