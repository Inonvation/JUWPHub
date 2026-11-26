package edu.jxslu.schedule

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import edu.jxslu.schedule.ui.common.rememberAppHaptics
import edu.jxslu.schedule.ui.theme.Motion

/**
 * iOS 的悬浮底栏（2026-11-26 晚，用户要求「适配苹果的液态玻璃」+「底部白边」）。
 *
 * ## 形态
 *
 * 一颗**悬浮胶囊**：左右各 18dp 边距、距底 8dp + 安全区，半透明容器色 + 发丝描边 +
 * 一层顶部高光渐变，选中项是主色淡胶囊。三个 Tab（今日 / 课表 / 教务）与 Android 的
 * `AppBottomBar` 逐格对齐。
 *
 * ## 为什么不是真的「液态玻璃」
 *
 * iOS 26 的 `UIGlassEffect`（真背景模糊）这里用不了，两个硬约束：
 *
 * 1. **SDK 不够**：CI 是 macOS 15 + Xcode 16.4，`UIGlassEffect` 要 Xcode 26 的 SDK；
 *    写了就是一个只在用户手机上存在、CI 编不过的符号。
 * 2. **UIKit 互操作视图画在 Compose 之上**：就算能用 `UIVisualEffectView`，它也会盖住
 *    Compose 画的图标与文字（CMP 1.7 的 interop 是原生子视图，层级在 Metal 画布之上）。
 *    要做真模糊，整个底栏得用 UIKit 重写一遍——本机（Windows）验不了任何一帧。
 *
 * 所以这里用「半透明 + 描边 + 高光 + 阴影 + 悬浮」表达同一层语义：**底栏浮在内容之上**，
 * 内容能透出来一点。等 CMP 的 iOS 渲染层支持 backdrop blur（或 CI 升到 Xcode 26 且
 * interop 层级可控）再换成真材质。
 *
 * ## 内容让位
 *
 * 胶囊是**盖在内容上**的（不是把内容挤上去），所以共享层页面要把最后一屏内容顶出胶囊：
 * 宿主用 [IosBottomBarClearance] 提供 `LocalBottomBarClearance`，`TodayScreen` /
 * `SettingsScreen` / `JwHubScreen` 等已在读它（Android 的悬浮底栏同一口径）。
 */
@Composable
internal fun IosFloatingTabBar(
    items: List<IosBarItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val capsule = RoundedCornerShape(percent = 50)
    Box(
        modifier = modifier
            .fillMaxWidth()
            // 安全区归底栏自己吃：内容铺到屏幕底，胶囊落在 home indicator 之上
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
            .padding(horizontal = 18.dp)
            .padding(top = 6.dp, bottom = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .shadow(
                    elevation = 12.dp,
                    shape = capsule,
                    ambientColor = Color.Black.copy(alpha = 0.18f),
                    spotColor = Color.Black.copy(alpha = 0.22f),
                )
                .clip(capsule)
                .background(scheme.surfaceContainer.copy(alpha = 0.82f))
                // 玻璃的三层：**受光边**（白 30% 发丝线）→ **外轮廓线**（onSurface 6%，
                // 让胶囊在半透明背景上也有边界）→ **受光面**（上高光 + 下反光）。
                // 只留一层的话，深色壁纸下会读成「一块脏灰」，浅色下会完全消失。
                .border(0.7.dp, Color.White.copy(alpha = if (scheme.surface.luminance() > 0.5f) 0.55f else 0.28f), capsule)
                .border(0.6.dp, scheme.onSurface.copy(alpha = 0.06f), capsule)
                .background(
                    Brush.verticalGradient(
                        0f to Color.White.copy(alpha = 0.26f),
                        0.42f to Color.Transparent,
                        // 底部一条极淡的反光：玻璃压在内容上时，下缘会吃到一点环境光
                        0.94f to Color.White.copy(alpha = 0.03f),
                        1f to Color.Transparent,
                    ),
                )
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEachIndexed { index, item ->
                IosBarItemView(
                    item = item,
                    selected = index == selectedIndex,
                    onClick = { onSelect(index) },
                )
            }
        }
    }
}

/** 底栏一格：图标 + 文字，选中态是主色淡胶囊（与 M3 `NavigationBarItem` 的信息层级一样）。 */
@Composable
private fun IosBarItemView(
    item: IosBarItem,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val tint = if (selected) scheme.primary else scheme.onSurface.copy(alpha = 0.62f)
    // 选中胶囊淡入淡出：直接换色会「跳」，动画化后与 iOS 的 tab 选中同一手感
    val pill by animateColorAsState(
        targetValue = if (selected) scheme.primary.copy(alpha = 0.14f) else Color.Transparent,
        animationSpec = Motion.state(),
        label = "IosBarPill",
    )
    val interaction = remember { MutableInteractionSource() }
    // 底栏切换触感（2026-11-26 晚）：iOS 用户对 tab 点击的触感有预期；
    // 开关在设置 → 触感里（`HapticsSettings`），关掉时 `tap()` 自己返回。
    val haptics = rememberAppHaptics()
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(pill)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                onClickLabel = item.label,
                onClick = {
                    haptics.tap()
                    onClick()
                },
            )
            .padding(horizontal = 18.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = item.label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = tint,
        )
    }
}

/** 底栏一格的显示数据：文字 + 图标。枚举（`IosTab`）留在宿主文件里，这里只收展示所需的两样。 */
internal class IosBarItem(val label: String, val icon: ImageVector)

/**
 * 底栏的显隐壳（淡入淡出）。
 *
 * **为什么要单独一层**：`AnimatedVisibility` 有 `ColumnScope` / `RowScope` 两个扩展重载，
 * 直接写在根布局的 `Column` 里、又在 `BoxScope` 内调 `align`，解析会落到
 * `ColumnScope.AnimatedVisibility` 上并报「不能在隐式接收者上调用」。放进这个没有作用域的
 * 普通 composable，解析到的是顶层重载。
 */
@Composable
internal fun IosFloatingTabBarHost(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(Motion.state()),
        exit = fadeOut(Motion.state()),
    ) {
        content()
    }
}

/**
 * 悬浮底栏给内容留的净空：胶囊本体约 62dp + 上下外边距 ≈ 76dp，取 [IosBottomBarClearance]。
 *
 * 只用于 `LocalBottomBarClearance`；**不要**把它当「底栏高度」去做别的布局计算——底栏自己
 * 用安全区 padding，实际占位随设备变。
 */
internal val IosBottomBarClearance = 80.dp
