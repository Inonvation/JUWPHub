package edu.jxslu.schedule.ui.common

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 「标签 + 值」的详情行，全 App 唯一的版式实现（2026-11-19 收口，对应审计 C6）。
 *
 * 此前 10 个页面各写一份 `private fun DetailRow(label, value)`，其中 6 份逐字相同、
 * 差异只在标签宽度 / 透明度 / 行内边距 / 字号。这些差异是**每页的排版选择**，
 * 现在写成调用点的参数，不再复制整段函数。
 *
 * 值一律 `weight(1f)` 换行、标签顶对齐：多行值时标签不会浮在中间。
 * 四种版式不在这里，各自保留：标签可伸缩、值右对齐（侧栏余额卡）、
 * 标签是固定 44×20 盒子且值不占权（课程详情）、带复制按钮（问渠活动详情）。
 */
@Composable
fun LabelValueRow(
    label: String,
    value: String,
    labelWidth: Dp = 84.dp,
    labelAlpha: Float = 0.6f,
    verticalPadding: Dp = 6.dp,
    textStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    skipBlank: Boolean = true,
) {
    if (skipBlank && value.isBlank()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = verticalPadding),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = textStyle,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = labelAlpha),
            modifier = Modifier.width(labelWidth),
        )
        Text(
            text = value,
            style = textStyle,
            modifier = Modifier.weight(1f),
        )
    }
}
