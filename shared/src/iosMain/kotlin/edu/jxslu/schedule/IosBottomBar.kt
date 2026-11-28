package edu.jxslu.schedule

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import edu.jxslu.schedule.ui.common.rememberAppHaptics
import edu.jxslu.schedule.ui.theme.Motion
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.exp

/**
 * iOS 的悬浮底栏：iOS 26/27 的「液态玻璃」观感（2026-11-28 重做）。
 *
 * ## 形态
 *
 * 一颗悬浮胶囊：左右 18dp 边距、距底 8dp + 安全区；三格（今日 / 课表 / 教务）与 Android 的
 * `AppBottomBar` 逐格对齐。选中态不是一个慢慢变色的格子，而是一颗**会流动的液滴**：胶囊在
 * 格子之间滑过去，滑动时横向拉长、纵向压扁，落位后回弹。
 *
 * ## 这套「玻璃」由哪几层假出来
 *
 * 真正的液态玻璃是**折射**：边缘把后面的内容折进来、边界由光而不是暗线勾出来。没有背景采样，
 * 就只能逐层叠出同一组光学信号。下面五条是从实测数据里挑出来的，不是随手调的：
 *
 * 1. **受光斜面（bevel lit, not outlined）**：沿内缘叠 7 圈白色描边，透明度按
 *    `0.135·e^(-d/1.5dp)` 衰减。这个剖面来自对 iOS 27 的逐像素实测——导航胶囊的边在黑底上
 *    2px 处 +33、4px 处 +13、6px 处 +8、16px 处归零，且**没有内侧暗线**。所以这里不画
 *    「一条白描边 + 一条深描边」，画的是往内衰减的光。
 * 2. **外轮廓（iOS 27 新增的 outerContour）**：贴着边的一根 0.9dp 暗发丝，下重上轻。
 *    浅色主题里玻璃比页面亮，光靠高光是画不出边界的，全靠这一根；深色主题里它几乎看不见，
 *    边界交给第 1 条。
 * 3. **主体色调**：浅色走 iOS 应用内玻璃的 241 灰（`0.82/0.74/0.78` 三段竖直渐变，上亮下沉，
 *    读作「厚度」）；深色走「近黑 + 一点抬升」。**不透明度比 iOS 高**（iOS 是 0.43–0.73）：
 *    那边有背景模糊兜底，这里没有，字压在课程卡上会脏。
 * 4. **斜向受光带 + 底缘环境光反射**：一道很宽的斜向白带（左上亮、右下暗）给玻璃一个光源方向；
 *    贴底 6dp 处一条极淡的亮带、亮带下面才是暗边——这是「玻璃有厚度、下缘吃到地面反光」的读法。
 * 5. **液滴是一块玻璃里的第二块玻璃**，不是第二层 backdrop：只有渐变 + 自己的受光斜面 +
 *    一点点主色色散（上缘一道 0.5dp 的主色环），绝不叠第三个 backdrop（`Kyant0/AndroidLiquidGlass`
 *    的红线：glass on glass 会糊）。
 *
 * ## 为什么不是真的液态玻璃
 *
 * iOS 26 的 `UIGlassEffect`（真背景模糊 + 边缘折射）这里用不了，两个硬约束：
 *
 * 1. **SDK 不够**：CI 是 macOS 15 + Xcode 16.4，`UIGlassEffect` 要 Xcode 26 的 SDK；
 *    写了就是一个只在用户手机上存在、CI 编不过的符号。
 * 2. **UIKit 互操作视图画在 Compose 之上**：就算能用 `UIVisualEffectView`，它也会盖住
 *    Compose 画的图标与文字（CMP 1.7 的 interop 是原生子视图，层级在 Metal 画布之上）。
 *    要做真模糊，整个底栏得用 UIKit 重写一遍——本机（Windows）验不了任何一帧。
 *
 * 另外两条也不能用：`Modifier.blur` 模糊的是**自己画的内容**，够不到身后的页面；Skia 上没有
 * AGSL/SkSL 着色器通道（CMP 1.7 没开放 `RuntimeShader` 给公共 API），所以折射只能靠渐变与
 * 描边近似。
 *
 * ## 借了哪些实现
 *
 * - [Kyant0/AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass)：玻璃的受光边
 *   用「光」不用「线」；一块玻璃里不要再叠玻璃，选中态用渐变「material chip」代替第二个 backdrop。
 * - [shayann07/liquidglass 的 how-it-works](https://liquidglass.shayxo.dev/how-it-works/)：
 *   iOS 27 的实测剖面（见上第 1、2、3 条）、深色玻璃是「黑 + lift」、手指按下时镜片把整条栏
 *   放大 1.05 并把被按的格子再放大一次（本文件的 1.015 + 1.05 就是它的弱化版）。
 * - [elyesmansour/compose-floating-tab-bar](https://github.com/elyesmansour/compose-floating-tab-bar)：
 *   悬浮胶囊 + 等宽格 + 选中胶囊在两格间移动的布局口径。
 * - [NadeemIqbal/liquid-glass](https://github.com/NadeemIqbal/liquid-glass)：它的 `Fallback`
 *   档就是本文件的处境——没有 backdrop 时退成「平铺色调 + 边缘高光」，而这一档反而是 iOS 上
 *   唯一稳的档。
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
    // 深浅两套玻璃的参数差得比「换个底色」多（见 KDoc 第 1、2 条），所以按主题整体换一套，
    // 不在绘制里逐处 if。
    val dark = scheme.surface.luminance() < 0.5f
    val glass = remember(scheme, dark) { iosGlassPalette(scheme, dark) }
    val capsule = RoundedCornerShape(percent = 50)
    val density = LocalDensity.current

    // 哪一格被按住（-1 = 没有）：按下时整条栏轻抬，液滴朝被按的格子探一点。
    var pressedIndex by remember { mutableStateOf(-1) }
    val lift by animateFloatAsState(
        targetValue = if (pressedIndex >= 0) 1.015f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 720f),
        label = "IosBarLift",
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            // 安全区归底栏自己吃：内容铺到屏幕底，胶囊落在 home indicator 之上
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
            .padding(horizontal = 18.dp)
            .padding(top = 6.dp, bottom = 8.dp),
    ) {
        // 三格等宽：液滴的几何必须与 Row 的排布算式逐字一致，否则落位会偏半个像素。
        val count = items.size.coerceAtLeast(1)
        val cellGap = 2.dp
        val cellWidth = (maxWidth - IosBarInnerPadding * 2 - cellGap * (count - 1)) / count
        val cellWidthPx = with(density) { cellWidth.toPx() }
        val cellGapPx = with(density) { cellGap.toPx() }
        val innerPadPx = with(density) { IosBarInnerPadding.toPx() }
        val selected = selectedIndex.coerceIn(0, count - 1)
        val selectedLeft = innerPadPx + (cellWidthPx + cellGapPx) * selected
        val pressedLeft = if (pressedIndex in 0 until count) {
            innerPadPx + (cellWidthPx + cellGapPx) * pressedIndex
        } else {
            selectedLeft
        }

        // 液滴的位移与形变：位移用偏硬的弹簧（落位要准），形变用偏软的（拉长后自己弹回来）。
        val slide = remember { Animatable(selectedLeft) }
        val tension = remember { Animatable(0f) }
        LaunchedEffect(selectedLeft) {
            if (abs(selectedLeft - slide.value) < 0.5f) {
                slide.snapTo(selectedLeft)
            } else {
                launch {
                    tension.snapTo(1f)
                    tension.animateTo(0f, spring(dampingRatio = 0.32f, stiffness = 240f))
                }
                slide.animateTo(selectedLeft, spring(dampingRatio = 0.8f, stiffness = 560f))
            }
        }
        val lean = remember { Animatable(0f) }
        LaunchedEffect(pressedIndex) {
            lean.animateTo(if (pressedIndex >= 0) 1f else 0f, Motion.state())
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = lift
                    scaleY = lift
                }
                .shadow(
                    elevation = 16.dp,
                    shape = capsule,
                    ambientColor = Color.Black.copy(alpha = if (dark) 0.32f else 0.10f),
                    spotColor = Color.Black.copy(alpha = if (dark) 0.46f else 0.17f),
                )
                .clip(capsule)
                .drawBehind {
                    drawGlassBody(glass)
                    drawLiquidSelection(
                        palette = glass,
                        left = slide.value,
                        cellWidth = cellWidthPx,
                        // 按住的格子不是选中格时，液滴朝它探 18%——「水被手指吸过去」的那一下
                        lean = (pressedLeft - selectedLeft) * 0.18f * lean.value,
                        tension = tension.value,
                    )
                }
                // 受光斜面与外轮廓画在内容**之上**：玻璃表面的高光本来就压着里面的字，
                // 而且这一圈只在离边 4dp 内，碰不到图标与文字。
                .drawWithContent {
                    drawContent()
                    drawBarRim(glass)
                }
                .padding(horizontal = IosBarInnerPadding, vertical = IosBarInnerVPadding),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(cellGap),
            ) {
                items.forEachIndexed { index, item ->
                    IosBarItemView(
                        item = item,
                        selected = index == selected,
                        palette = glass,
                        modifier = Modifier.weight(1f),
                        onPressedChange = { down -> pressedIndex = if (down) index else -1 },
                        onClick = { onSelect(index) },
                    )
                }
            }
        }
    }
}

/**
 * 底栏一格：图标 + 文字。
 *
 * 按下态是**放大**不是缩小：iOS 26 的手指镜片会把被按的格子放大、提亮，缩小的那种是 Android
 * 的触摸反馈语汇。这里放大 5%，并叠一层很淡的圆形高光（画在图标下面，不压字形）。
 */
@Composable
private fun IosBarItemView(
    item: IosBarItem,
    selected: Boolean,
    palette: IosGlassPalette,
    modifier: Modifier = Modifier,
    onPressedChange: (Boolean) -> Unit,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val haptics = rememberAppHaptics()
    // 例外（Motion.kt 的四档之外）：这里要的是「有水」的回弹，tween 的减速收尾看不出弹性。
    // 只用于跟随手指的连续量，不用于状态切换。
    val press by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.42f, stiffness = 900f),
        label = "IosBarPress",
    )
    LaunchedEffect(pressed) { onPressedChange(pressed) }
    val tint by animateColorAsState(
        targetValue = if (selected) scheme.primary else scheme.onSurfaceVariant.copy(alpha = 0.72f),
        animationSpec = Motion.state(),
        label = "IosBarTint",
    )
    Column(
        modifier = modifier
            .graphicsLayer {
                scaleX = 1f + 0.05f * press
                scaleY = 1f + 0.05f * press
            }
            .clip(RoundedCornerShape(percent = 50))
            .drawBehind {
                if (press > 0.01f) {
                    val center = Offset(size.width / 2f, size.height * 0.34f)
                    val radius = 26.dp.toPx()
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                palette.pressGlow.copy(alpha = 0.14f * press),
                                Color.Transparent,
                            ),
                            center = center,
                            radius = radius,
                        ),
                        radius = radius,
                        center = center,
                    )
                }
            }
            // `selectable` 而不是 `clickable`：VoiceOver 要能读出「第几项、已选中」，
            // 这层语义是 `Role.Tab` + `selected` 一起给的。indication 传 null——Android 的
            // 水波纹是另一套语汇，这里用上面的放大 + 高光代替。
            .selectable(
                selected = selected,
                interactionSource = interaction,
                indication = null,
                role = Role.Tab,
            ) {
                // 底栏切换触感（2026-11-26 晚）：iOS 用户对 tab 点击的触感有预期；
                // 开关在设置 → 触感里（`HapticsSettings`），关掉时 `tap()` 自己返回。
                haptics.tap()
                onClick()
            }
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(IosBarIconSize),
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = item.label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ---------------------------------------------------------------------------------------------
// 绘制
// ---------------------------------------------------------------------------------------------

/**
 * 玻璃本体：主体渐变 + 斜向受光带 + 底缘环境光反射。画在图标**下面**。
 *
 * 三层都是整块矩形，靠外面那圈 `clip(capsule)` 收边，所以这里不需要路径。
 */
private fun DrawScope.drawGlassBody(palette: IosGlassPalette) {
    // ① 主体：上亮下沉的三段竖直渐变 = 玻璃的厚度。两段的话中间会有一道可见的过渡线。
    drawRect(
        Brush.verticalGradient(
            0f to palette.veilTop,
            0.48f to palette.veilMiddle,
            1f to palette.veilBottom,
        ),
    )
    // ② 斜向受光带：给「光从左上打过来」一个方向。宽、极淡、右下角再补一点点，
    //    免得整块读成均匀的一坨。
    drawRect(
        Brush.linearGradient(
            0f to Color.White.copy(alpha = palette.sheen),
            0.34f to Color.White.copy(alpha = palette.sheen * 0.16f),
            0.62f to Color.Transparent,
            0.88f to Color.Transparent,
            1f to Color.White.copy(alpha = palette.sheen * 0.30f),
            start = Offset(0f, 0f),
            end = Offset(size.width * 0.72f, size.height * 1.4f),
        ),
    )
    // ③ 底缘环境光反射：亮带在暗边**上面**（离底 1.2dp 起、6dp 宽），最外那圈留暗——
    //    这样才读得出「玻璃是块有厚度的东西」，而不是贴了张纸。
    val band = 6.dp.toPx()
    drawRect(
        Brush.verticalGradient(
            0f to Color.Transparent,
            0.6f to Color.White.copy(alpha = palette.reflection),
            1f to Color.Transparent,
        ),
        topLeft = Offset(0f, size.height - band - 1.2.dp.toPx()),
        size = Size(size.width, band),
    )
}

/**
 * 受光斜面 + 外轮廓。画在图标**之上**。
 *
 * 循环里的透明度剖面不是随手配的：`0.135·e^(-d/1.5dp)` 在 0.7dp / 1.4dp / 2.1dp 处分别落在
 * 0.085 / 0.053 / 0.033，对应实测的 2px +33、4px +13、6px +8（@3x）。改这几个数之前先重看
 * [IosFloatingTabBar] 的 KDoc 第 1 条。
 */
private fun DrawScope.drawBarRim(palette: IosGlassPalette) {
    drawLitRim(
        topLeft = Offset.Zero,
        area = size,
        peak = palette.rimPeak,
        bottomShare = palette.rimBottomShare,
        step = 0.7.dp,
        rings = 7,
        falloff = palette.rimFalloff,
    )
    // 外轮廓：iOS 27 才有的一根暗发丝，下缘最重（「不受光的那两侧最强」）。
    val hair = 0.9.dp.toPx()
    drawRoundRect(
        brush = Brush.verticalGradient(
            0f to Color.Black.copy(alpha = palette.contourTop),
            1f to Color.Black.copy(alpha = palette.contourBottom),
        ),
        topLeft = Offset(hair / 2f, hair / 2f),
        size = Size(size.width - hair, size.height - hair),
        cornerRadius = CornerRadius((size.height - hair) / 2f),
        style = Stroke(hair),
    )
}

/**
 * 液滴（选中态）。
 *
 * 几何全部来自动画值，不进布局：布局只负责三格等宽，液滴是画上去的——这样移动一帧只重绘，
 * 不重组、不重新测量。
 *
 * @param left 液滴左缘（px，含 [cellWidth] 的格子起点）
 * @param tension 1 → 0：刚被拖动时 1（横向拉长 16%、纵向压 5%），落位后衰减到 0
 */
private fun DrawScope.drawLiquidSelection(
    palette: IosGlassPalette,
    left: Float,
    cellWidth: Float,
    lean: Float,
    tension: Float,
) {
    val innerV = IosBarInnerVPadding.toPx()
    val slot = size.height - innerV * 2f
    // tension 用低阻尼弹簧衰减，会冲到负值：那是「回弹」的一半。纵向让它只多 1%，
    // 否则液滴会溢出自己那条槽、压到栏边上。
    val height = slot * (1f - 0.05f * tension.coerceIn(-0.2f, 1f))
    val width = cellWidth * (1f + 0.16f * tension)
    val centerX = left + cellWidth / 2f + lean
    val top = (size.height - height) / 2f
    val rect = Rect(centerX - width / 2f, top, centerX + width / 2f, top + height)
    val radius = height / 2f
    val path = Path().apply {
        addRoundRect(RoundRect(rect.left, rect.top, rect.right, rect.bottom, radius, radius))
    }

    // 液滴自己的软投影。用「5 圈往外扩、透明度递减的填充」假模糊，并且**只画在液滴外面**
    // （Difference）：画在里面会把半透明的液滴压成一块灰。
    clipPath(path, ClipOp.Difference) {
        for (i in 1..5) {
            val grow = 0.9.dp.toPx() * i
            drawRoundRect(
                color = Color.Black.copy(alpha = palette.dropShadow * (1f - i / 6f)),
                topLeft = Offset(rect.left - grow, rect.top - grow + 0.8.dp.toPx()),
                size = Size(rect.width + grow * 2f, rect.height + grow * 2f),
                cornerRadius = CornerRadius(radius + grow),
            )
        }
    }

    clipPath(path) {
        // 液滴的纵向渐变要按**液滴自己的**上下取，所以给死 startY/endY：
        // 走默认值的话会摊成整条栏的高度，液滴顶部就不再是最亮的那一点。
        drawRect(
            Brush.verticalGradient(
                0f to palette.dropTop,
                0.55f to palette.dropMiddle,
                1f to palette.dropBottom,
                startY = rect.top,
                endY = rect.bottom,
            ),
        )
        // 镜面高光：偏左上的一颗，圆润、极软。它不是「一个白色圆点」，半径给到液滴宽度
        // 的 0.7 倍，边缘自然落到 0。
        val glowCenter = Offset(rect.left + width * 0.33f, rect.top + height * 0.16f)
        val glowRadius = width * 0.7f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(palette.dropSpecular, Color.Transparent),
                center = glowCenter,
                radius = glowRadius,
            ),
            radius = glowRadius,
            center = glowCenter,
        )
    }

    // 液滴的受光斜面：比栏的更亮（它是「第二块玻璃」），底部衰减到 0.25。
    drawLitRim(
        topLeft = Offset(rect.left, rect.top),
        area = Size(rect.width, rect.height),
        peak = palette.dropRimPeak,
        bottomShare = 0.25f,
        step = 0.7.dp,
        rings = 5,
        falloff = 1.2.dp,
    )
    // 色散：上缘一道主色环，模拟折射把品牌色掰出来的那点彩色边。透明度低到只在深色主题
    // 的近距离下看得出来——多了就是「加了一圈彩色边框」。
    drawRoundRect(
        brush = Brush.verticalGradient(
            0f to palette.dropChroma,
            0.6f to Color.Transparent,
        ),
        topLeft = Offset(rect.left, rect.top),
        size = Size(rect.width, rect.height),
        cornerRadius = CornerRadius(radius),
        style = Stroke(0.5.dp.toPx()),
    )
}

/**
 * 叠 [rings] 圈白色描边，透明度按 `peak·e^(-inset/falloff)` 衰减——受光的斜面。
 *
 * 为什么不用一根描边：一根描边的边是**硬**的，读起来是「贴了个白框」；连续的几圈衰减光
 * 才是玻璃被照亮的边缘。圈宽略大于步进（1.1×）让相邻两圈咬合，避免留下扫描线。
 */
private fun DrawScope.drawLitRim(
    topLeft: Offset,
    area: Size,
    peak: Float,
    bottomShare: Float,
    step: Dp,
    rings: Int,
    falloff: Dp,
) {
    val stepPx = step.toPx()
    val falloffPx = falloff.toPx()
    val radius = area.height / 2f
    for (i in 0 until rings) {
        // 从半圈宽处起：第 0 圈的描边中心落在边上，会有一半被 clip 切掉，起手就细一截。
        val inset = stepPx * (i + 0.5f)
        val alpha = peak * exp(-inset / falloffPx)
        if (alpha < 0.004f) break
        drawRoundRect(
            brush = Brush.verticalGradient(
                0f to Color.White.copy(alpha = alpha),
                0.55f to Color.White.copy(alpha = alpha * 0.5f),
                1f to Color.White.copy(alpha = alpha * bottomShare),
            ),
            topLeft = topLeft + Offset(inset, inset),
            size = Size(area.width - inset * 2f, area.height - inset * 2f),
            cornerRadius = CornerRadius((radius - inset).coerceAtLeast(0.1f)),
            style = Stroke(stepPx * 1.1f),
        )
    }
}

// ---------------------------------------------------------------------------------------------
// 参数
// ---------------------------------------------------------------------------------------------

/** 胶囊内部左右内边距。液滴的几何按它算，改这里要同时确认 [IosFloatingTabBar] 的算式。 */
private val IosBarInnerPadding = 5.dp

/** 胶囊内部上下内边距（液滴与栏边之间那一圈玻璃）。 */
private val IosBarInnerVPadding = 5.dp

/** 图标边长。22dp 与 Android 底栏的 24dp 差一点，是因为这里的图标上面还压着文字。 */
private val IosBarIconSize = 22.dp

/**
 * 一套玻璃参数。深浅两个主题差的不只是颜色：浅色靠**暗轮廓**定边界、高光几乎看不见
 * （白压白，与 iOS 一致），深色靠**受光斜面**定边界、轮廓退到几乎为零。
 *
 * 全是 float 的 alpha 而不是带 alpha 的 Color：绘制里要拿 alpha 再乘衰减系数。
 */
private class IosGlassPalette(
    val veilTop: Color,
    val veilMiddle: Color,
    val veilBottom: Color,
    val sheen: Float,
    val reflection: Float,
    val rimPeak: Float,
    val rimBottomShare: Float,
    val rimFalloff: Dp,
    val contourTop: Float,
    val contourBottom: Float,
    val dropTop: Color,
    val dropMiddle: Color,
    val dropBottom: Color,
    val dropSpecular: Color,
    val dropRimPeak: Float,
    val dropChroma: Color,
    val dropShadow: Float,
    val pressGlow: Color,
)

private fun iosGlassPalette(scheme: ColorScheme, dark: Boolean): IosGlassPalette {
    val white = Color.White
    val black = Color.Black
    return if (dark) {
        // 深色：近黑的主体 + 一层抬升（iOS 深色玻璃是「黑 + 35/255 的白」）。页面底是 #0F1419
        // 这一档，所以顶层必须比页面**亮**才读得出是一块面：抬到 #22 上下。
        val body = lerp(scheme.surfaceContainerHigh, black, 0.55f)
        val drop = lerp(scheme.primary, white, 0.30f)
        IosGlassPalette(
            veilTop = lerp(body, white, 0.16f).copy(alpha = 0.76f),
            veilMiddle = lerp(body, white, 0.08f).copy(alpha = 0.72f),
            veilBottom = body.copy(alpha = 0.76f),
            sheen = 0.07f,
            reflection = 0.05f,
            rimPeak = 0.17f,
            rimBottomShare = 0.30f,
            rimFalloff = 1.5.dp,
            contourTop = 0f,
            contourBottom = 0.35f,
            dropTop = drop.copy(alpha = 0.22f),
            dropMiddle = drop.copy(alpha = 0.16f),
            dropBottom = drop.copy(alpha = 0.20f),
            dropSpecular = white.copy(alpha = 0.10f),
            dropRimPeak = 0.26f,
            dropChroma = scheme.primary.copy(alpha = 0.10f),
            dropShadow = 0.34f,
            pressGlow = white,
        )
    } else {
        // 浅色：应用内玻璃的 241 灰，掺一点主题容器色让它跟着调色板走，不发紫。
        // 页面底是 #F9F9FF 这一档（几乎纯白），所以玻璃要比它**暗**几级才有「一块面」的感觉；
        // 顶部再亮回去，就是受光的那一面。
        val body = lerp(scheme.surfaceContainer, Color(0xFFF1F1F1), 0.55f)
        val drop = lerp(scheme.primary, white, 0.72f)
        IosGlassPalette(
            veilTop = lerp(body, white, 0.25f).copy(alpha = 0.86f),
            veilMiddle = body.copy(alpha = 0.80f),
            veilBottom = lerp(body, white, 0.10f).copy(alpha = 0.82f),
            sheen = 0.10f,
            reflection = 0.07f,
            rimPeak = 0.55f,
            rimBottomShare = 0.22f,
            rimFalloff = 1.6.dp,
            contourTop = 0.05f,
            contourBottom = 0.16f,
            dropTop = drop.copy(alpha = 0.78f),
            dropMiddle = drop.copy(alpha = 0.68f),
            dropBottom = drop.copy(alpha = 0.72f),
            dropSpecular = white.copy(alpha = 0.34f),
            dropRimPeak = 0.70f,
            dropChroma = scheme.primary.copy(alpha = 0.06f),
            dropShadow = 0.10f,
            // 浅色底上白色高光等于没有，按下的「提亮」得靠主色。
            pressGlow = scheme.primary,
        )
    }
}

// ---------------------------------------------------------------------------------------------

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
 * 悬浮底栏给内容留的净空：胶囊本体约 64–66dp（图标 22 + 间隙 2 + 文字行高 + 上下内边距各 8，
 * 外加栏内 5dp × 2）+ 上下外边距 6 + 8 ≈ 80dp。字体锁定档（`FontScalePolicy` 默认 0.9×）下
 * 文字行高是 14.4dp，整条约 78dp；未锁定时随系统字号变高，这个常量不跟，让位的余量会少几 dp。
 *
 * 只用于 `LocalBottomBarClearance`；**不要**把它当「底栏高度」去做别的布局计算——底栏自己
 * 用安全区 padding，实际占位随设备变。
 */
internal val IosBottomBarClearance = 80.dp
