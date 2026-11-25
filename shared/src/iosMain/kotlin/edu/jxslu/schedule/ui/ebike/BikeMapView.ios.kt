package edu.jxslu.schedule.ui.ebike

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * iOS 的地图槽位（快趣单车）。
 *
 * ## 现状：**故意留成占位**（2026-11-25 晚）
 *
 * 真实现（`MKMapView` + `MKAnnotationView` + `MKPolygonRenderer`，约 990 行）已经写过一版，
 * 在 CI 上暴露了约 20 处 Kotlin/Native 绑定不符——`MKMapView.addOverlay/removeOverlays`
 * 的 K/N 名字、`CLLocationCoordinate2D` 的 `latitude/longitude` 取值方式、
 * `MKMarkerAnnotationView` 的 initializer 形态，都和 `platform.MapKit` 的实际绑定对不上。
 * 那类错误**本机编不了**（Windows 无 Kotlin/Native），只能靠 macOS 每轮 8 分钟往返猜，
 * 一轮修 2–3 处——不值得在这时候烧。
 *
 * 而且它**当前没有任何调用方**：`RideScreen` / `BikeMapViewModel` 还在 `:app` 的 `ui/ebike`
 * （被高德 Android SDK 挡着，见 `design/ios-port.md` §12.1）。地图槽位先留占位，
 * 等页面搬过来时再一次性对着 macOS 编译器写——那时每个错误都能当场改，不用排队。
 *
 * ## 升级条件（到了就换掉本文件）
 *
 * 1. `RideScreen` / `BikeMapViewModel` 搬进 `commonMain`（它们通过 `BikeMapSlot` 拿地图），
 * 2. 或者需要先看地图：把 `IosBikeMap` 换成 `MKMapView` 实现，逐个符号对着 CI 编。
 *
 * 占位当前的行为是**诚实的空态**：一块底色 + 一句「地图暂不可用，车辆列表在下方」。
 * 不假装有地图，也不把 `BikeMapSlot` 传成 `null`——传 null 会让页面以为「本平台没有这个
 * 能力」，而 iOS 是有地图能力的（用的是 MapKit），只是还没写完。
 */
fun iosBikeMapSlot(): BikeMapSlot = BikeMapSlot { input, modifier -> IosBikeMap(input, modifier) }

/**
 * 占位地图。**不消费** [BikeMapInput] 的任何字段——车辆、围栏、相机指令都不画。
 *
 * 保留 `input` 形参是为了将来换真实现时调用方不用改：契约已经是两端共用的
 * `BikeMapInput`（`shared/.../ui/ebike/BikeMapContract.kt`）。
 */
@Composable
fun IosBikeMap(
    @Suppress("UNUSED_PARAMETER") input: BikeMapInput,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(24.dp),
        ) {
            Text(
                text = "地图暂不可用",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "车辆列表在下方，可正常查看与用车",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
            )
        }
    }
}
