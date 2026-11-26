package edu.jxslu.schedule.ui.ebike

/**
 * iOS 的地图槽位：**纯 Compose 画布 + 高德栅格底图**（2026-11-26）。
 *
 * 与同目录下 `BikeMapView.ios.kt` 的 `iosBikeMapSlot()` 是两回事：那个是「地图暂不可用」的占位
 * （MapKit 在 Kotlin 2.1.21 的绑定里放不了标注 / 覆盖物，见该文件的对照表），本文件给的是
 * 能画出车标与路网的实现——[BikeMapCanvas] 用 `DrawScope` 自己画，**完全不碰 MapKit**；
 * 底图瓦片由 [rememberIosBikeMapTiles] 取（高德栅格，与 Android 回退底图同一接口）。
 *
 * ```kotlin
 * RideScreen(
 *     …,
 *     bikeMap = iosCanvasBikeMapSlot(),
 * )
 * ```
 *
 * 本文件只有一行实现是刻意的：槽位契约（[BikeMapSlot]）、画布与瓦片数学都在 `commonMain`，
 * 平台差异只剩「瓦片字节怎么来、怎么解成位图」——那件事在 `IosBikeMapTiles.kt`。
 *
 * 占位那份 `iosBikeMapSlot()` **暂不删除**：它记录着 MapKit 绑定为什么用不了（那张对照表
 * 是下次升级 Kotlin 版本时要回来看的），删了就得重新踩一遍。等真有人要试 MapKit 再删。
 */
fun iosCanvasBikeMapSlot(): BikeMapSlot = BikeMapSlot { input, modifier ->
    BikeMapCanvas(
        input = input,
        modifier = modifier,
        tiles = rememberIosBikeMapTiles(),
    )
}
