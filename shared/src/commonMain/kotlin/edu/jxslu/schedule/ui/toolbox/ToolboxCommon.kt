package edu.jxslu.schedule.ui.toolbox

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import edu.jxslu.schedule.ui.common.rememberAppHaptics

/**
 * 百宝箱各工具页的**纯布局**公共件（DESIGN §4.47）：滑杆行、贴底动作条、颜色圆点行。
 *
 * ## 为什么本文件只剩这三件（iOS 移植 V3，2026-11-25）
 *
 * 这个文件原来还装着单图选择器、`inJustDecodeBounds` 量尺寸、预览区与
 * `ToolboxExport`（保存相册 / 分享）。那四件全是 `android.graphics.Bitmap` +
 * `ContentResolver` + `MediaStore` + `FileProvider` 的管线，`commonMain` 里拿不到，
 * 留在 `:app` 的 `ToolboxBitmap.kt`（同包，调用点不用改 import）。
 *
 * 分界线的判据是「有没有位图」：滑杆、按钮条、色点只是 Compose 布局与主题色，
 * 两端一字不差；预览区一旦要 `asImageBitmap()` 就已经是 Android 类型了。
 *
 * ## 出口口径（仍在 `:app`，见 `ToolboxBitmap.kt` 的 KDoc）
 *
 * 保存到相册走 `ui/common/BitmapSave`（MediaStore `Pictures/JUWPHub`）；分享走
 * `ScheduleShareStore` 的机制（cacheDir 落盘 + FileProvider 临时授权 Uri），
 * 目录 `toolbox_share`。格式统一 JPEG q92。
 */

/**
 * 工具页的滑杆行：上行「标题 + 当前值」，下行整宽滑杆。
 * 与课表显示设置里的滑块同一形态（`DisplaySettingsContent.RangeLabeledSlider`），
 * 但那只是那个面板的私有件；工具页需要的是带标题+值的简版，不复用私有件也不动它。
 */
@Composable
fun ToolboxSliderRow(
    title: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                valueText,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * 工具页的贴底动作条（DESIGN §4.47）：主操作按钮**冻结在屏幕底部**，不随内容滚动。
 * 底色 `surfaceContainer`、`navigationBarsPadding` 自理；按钮由调用方给（整宽一颗或成对）。
 * [progress] 为真时在按钮上方加一条进度线（拼接 / 写盘期间）。
 */
@Composable
fun ToolboxActionBar(
    progress: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
            if (progress) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = content,
            )
        }
    }
}

/**
 * 颜色圆点行（观感对齐主题配色弹层里的色点：圆 + 描边，选中套主色圈）。
 *
 * [labels] 给读屏用（如「白」「黑」）；不给就退化成「颜色 1、颜色 2」。
 */
@Composable
fun ColorDotRow(
    colors: List<Int>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    labels: List<String> = emptyList(),
) {
    val haptics = rememberAppHaptics()
    // 命中区 36dp 而不是 48dp：色点行嵌在页面 16dp 边距 + AppCard 14dp 内距里，360dp 屏的实际
    // 可用宽度是 360 − 32 − 28 = **300dp**（不是 328dp，2026-11-06 复核改正了这处算错的注释）。
    // 8 个色点 × 48dp = 384dp 会被裁掉三个；× 40dp = 320dp 仍放不下最后一个（"灰"要靠横滑才看得见，
    // 而横滑没有滚动条提示）；× 36dp = 288dp 稳放，又远好过原先 28dp 的圆点本体。
    // shortcut: 色点数超过 8 个（或更窄的屏）时靠横向滚动兜底；真要更多色板就改成两行。
    Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
        colors.forEachIndexed { index, color ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.RadioButton) {
                        haptics.toggle()
                        onSelect(index)
                    }
                    // 读屏要能说出「哪个颜色、是否选中」；原先连 role 都没有，只报一个可点方块
                    .semantics {
                        this.selected = selected
                        contentDescription = labels.getOrNull(index) ?: "颜色 ${index + 1}"
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color(color))
                        .border(
                            width = if (selected) 3.dp else 1.dp,
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            },
                            shape = CircleShape,
                        ),
                )
            }
        }
    }
}
