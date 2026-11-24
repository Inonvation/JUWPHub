package edu.jxslu.schedule.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp

/**
 * 服务品牌位图（`ic_brand_*`）的渲染缝。
 *
 * ## 为什么参数是 `Int` 而不是 `Painter`
 *
 * 这套位图是**宿主模块**的素材（`app/src/main/res/drawable/ic_brand_*`），与侧栏
 * 「江水生活」各行同一份；共享层既没有它，也不该为它复制一份位图。`R` 只有 Android 有，
 * 所以跨模块唯一能传的句柄就是这个资源 id。
 *
 * 另一个写法是让调用方传 `Painter?`（`GuessYouWantCard` 走的就是那条路），此处**不能用**：
 * [AccountHeroCard] 的四个调用页（快趣 / 趣智 / 胖乖 / U净）还在 `:app`，其中
 * `ui/ebike/KvcxScreen.kt` 在本次改造的禁改清单里，改签名会当场编不过。等那些页搬进
 * 共享层时，这条缝可以连同 `Int` 参数一起换成 `Painter?`（届时要改的只有四个调用点）。
 *
 * ## iOS
 *
 * iOS 侧没有这套品牌位图，actual 渲染为空：卡片本体（标题 / 胶囊 / 说明行 / 动作按钮）
 * 照常，只是头像位不出现。这与「平台动作可空回调」同一语义——没有的能力不占位，而不是
 * 画一个假图标。
 */
@Composable
expect fun BrandMark(resId: Int, size: Dp)
