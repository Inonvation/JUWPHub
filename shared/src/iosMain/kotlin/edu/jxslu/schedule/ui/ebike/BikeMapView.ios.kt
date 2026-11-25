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
 * ## 现状：**故意留成占位**（2026-11-25 晚，两轮 CI 失败之后的结论）
 *
 * 真实现写过两版（`MKMapView` + 标注 + 覆盖物，约 850–990 行），**都在 CI 上卡在同一类问题上**：
 * 这个 Kotlin 版本（2.1.21）的 `platform.MapKit` 绑定有两处根本性缺口，不是写法问题：
 *
 * | 想做的事 | CI 的报错 | 结论 |
 * |---|---|---|
 * | 自己实现 `MKAnnotationProtocol` 提供 `coordinate` | 同时报「`coordinate` is final and cannot be overridden」与「does not implement abstract member」 | 协议成员在绑定里是 final，实现不了 |
 * | 继承 `MKPointAnnotation` 再赋值 `coordinate` | `'val' cannot be reassigned` | 具体类的那个属性也被暴露成 `val`，**放不了标注** |
 * | `addOverlay(s)` / `removeOverlay(s)`（单数复数都试过） | 一律 `Unresolved reference` | 覆盖物那一族方法在绑定里不存在，画不了围栏 / 精度圈 |
 *
 * 也就是说：**在当前 Kotlin 版本下，MapKit 的标注与覆盖物都用不了**。底图、缩放、相机、
 * 用户蓝点能画，但那等于一张没有车的空地图——不如诚实留占位。
 *
 * ## 为什么现在留占位不亏
 *
 * 它**没有任何调用方**：`RideScreen` / `RidePanels` 还在 `:app`（快趣的业务逻辑已经全在
 * `commonMain`，但那一页没搬）。所以这个文件现在的正确性对用户零影响，
 * 而它编不过会让**整批**（通知、日志、设置页、抽屉层级、过渡）都发不出去——不值得。
 *
 * ## 升级条件（到了就换掉本文件）
 *
 * 1. `RideScreen` 搬进 `commonMain`（它通过 `BikeMapSlot` 拿地图）——那时必须有真地图；
 * 2. 换实现前**先在 macOS 上跑通一次编译**（不能靠 CI 猜）：标注要么等 Kotlin 升级修好绑定，
 *    要么改走「`MKAnnotationView` 当普通子视图 + `convertCoordinate(_:toPointToView:)` 手工定位」，
 *    要么自己用 Compose `Canvas` 在地图上叠一层，坐标投影自己算。
 *
 * 占位当前的行为是**诚实的空态**：一块底色 + 一句「地图暂不可用，车辆列表在下方」。
 * 不假装有地图，也不把 `BikeMapSlot` 传成 `null`——传 null 会让页面以为「本平台没有这个
 * 能力」，而 iOS 是有地图能力的（MapKit 底图是真的能画），只是标注这一层被绑定挡住了。
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
