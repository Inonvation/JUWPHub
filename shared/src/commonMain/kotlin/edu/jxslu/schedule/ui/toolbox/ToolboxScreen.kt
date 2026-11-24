package edu.jxslu.schedule.ui.toolbox

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import edu.jxslu.schedule.ui.common.AppCardRow
import edu.jxslu.schedule.ui.common.SettingsIconBadge
import edu.jxslu.schedule.ui.common.pinnedStatusBars
import edu.jxslu.schedule.ui.icon.AppIcons

/**
 * 百宝箱首页（DESIGN §4.47）：工具卡片**竖排**（一行一张，整宽）。
 *
 * 入口在今日页顶栏「+」下拉（受「设置 → 功能 → 百宝箱」开关控制，默认开）。
 * 每加一个工具就加一张卡 + 一个 `SubpageScreen` 值，本页不做其它逻辑——
 * 各工具的重活都在自己的页面里（水印 / 拼接 / 菜鸟批量查）。
 *
 * 2026-10-12 用户口径：卡片竖排（最初做成了两列方卡网格，用户要一行一张）。
 *
 * ## 入口为什么可空（iOS 移植 V3，2026-11-25）
 *
 * 水印与拼接**整块是位图管线**（`android.graphics.Bitmap` 解码 → 画 → 编码 → 相册 /
 * 分享），`commonMain` 里既没有解码器也没有相册。按 `design/ios-port.md` §11.2 ④：
 * **null = 本平台没有图像处理能力，那张卡整行不渲染**——不是「以后补」的占位。
 * 两个都传 null 时本页只剩一句说明，iOS 侧因此不出现百宝箱入口（`SubpageScreen` 那侧
 * 也不给路由），而不是点进去看一屏空白。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolboxScreen(
    onBack: () -> Unit,
    onOpenWatermark: (() -> Unit)? = null,
    onOpenStitch: (() -> Unit)? = null,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                // 钉住版 insets：工具页会拉起相册 / 分享面板这类外部界面
                // （口径见 ui/common/StableInsets.kt 与 .agents/rules/nav-window.md）
                windowInsets = pinnedStatusBars(),
                title = { Text("百宝箱") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    text = "小工具：给图片加水印、把多张图拼成长图。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            item {
                ToolboxTile(
                    icon = AppIcons.Stamp01,
                    title = "图片水印",
                    subtitle = "文字平铺满图",
                    onClick = onOpenWatermark,
                )
            }
            item {
                ToolboxTile(
                    icon = AppIcons.ImageComposition,
                    title = "图片拼接",
                    subtitle = "多图竖拼长图",
                    onClick = onOpenStitch,
                )
            }
        }
    }
}

/** 工具卡：整宽一行（图标底座 + 标题与说明 + 右箭头）。加工具就再加一张。 */
@Composable
private fun ToolboxTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    /** null = 本平台没有这个工具，整行不渲染（见 `ToolboxScreen` 的 KDoc）。 */
    onClick: (() -> Unit)?,
) {
    if (onClick == null) return
    AppCardRow(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
        SettingsIconBadge(icon)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }
        Icon(
            AppIcons.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
