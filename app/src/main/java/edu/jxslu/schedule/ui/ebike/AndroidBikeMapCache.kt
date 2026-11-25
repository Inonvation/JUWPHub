package edu.jxslu.schedule.ui.ebike

import edu.jxslu.schedule.Graph

/**
 * 快趣地图缓存统计与清理的 **Android 实现**（iOS 移植 P4，2026-11-25）。
 *
 * 转接 `EbikeMapCache`（`:app` 的 `internal object`）：它按 `cacheDir` / 高德 SDK 的瓦片
 * 目录统计与清理，`AppCacheCleaner` 的「清理全部缓存」聚合入口也在用它，所以实现留在
 * `:app`，共享层只认 `EbikeMapCachePort` 两个方法（设置弹层的「地图缓存」卡）。
 */
internal object AndroidBikeMapCache : EbikeMapCachePort {

    override suspend fun measure(): EbikeMapCachePort.Usage {
        val usage = EbikeMapCache.measure(Graph.appContext)
        return EbikeMapCachePort.Usage(
            tileBytes = usage.tileBytes,
            zoneBytes = usage.zoneBytes,
            amapBytes = usage.amapBytes,
        )
    }

    override suspend fun clear(): Boolean = EbikeMapCache.clear(Graph.appContext)
}
