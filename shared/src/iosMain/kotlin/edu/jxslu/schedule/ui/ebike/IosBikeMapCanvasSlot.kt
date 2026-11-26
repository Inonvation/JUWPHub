package edu.jxslu.schedule.ui.ebike

/**
 * iOS 的地图槽位：**纯 Compose 画布**（2026-11-26）。
 *
 * 与同目录下 `BikeMapView.ios.kt` 的 `iosBikeMapSlot()` 是两回事：那个是「地图暂不可用」的占位
 * （MapKit 在 Kotlin 2.1.21 的绑定里放不了标注 / 覆盖物，见该文件的对照表），本文件给的是
 * 能画出车标的真实现——[BikeMapCanvas] 用 `DrawScope` 自己画，**完全不碰 MapKit**。
 *
 * ```kotlin
 * RideScreen(
 *     …,
 *     bikeMap = iosCanvasBikeMapSlot(),   // 原来传的 null 换成这一行
 * )
 * ```
 *
 * 本文件只有一行实现是刻意的：槽位契约（[BikeMapSlot]）与画布都在 `commonMain`，
 * iOS 这侧不需要任何平台件——没有 UIKit、没有 MapKit、没有 CoreLocation（定位仍由
 * `IosBikeLocation` 那条既有链路供 `BikeMapInput.userLat/userLng`）。
 *
 * 占位那份 `iosBikeMapSlot()` **暂不删除**（同批另一条工作线可能还在引用它）；
 * 集成方接线时把 `bikeMap = null` 换成这里，占位就没有调用方了，可以随下一批清掉。
 */
fun iosCanvasBikeMapSlot(): BikeMapSlot = BikeMapSlot { input, modifier ->
    BikeMapCanvas(input, modifier)
}
