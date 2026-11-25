package edu.jxslu.schedule.ui.jwvw

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 电脑版 / 手机版开关（DESIGN §4.4）。2026-11-26 从 `:app` 的 `JwImportScreen` 上移。
 *
 * 形态照骑行页「使用方式」chip：纯文字胶囊、**不用图标**（这是两态开关不是动作，当前态
 * 直接写在胶囊上；也免了猜图标名）。开 = 主色底「电脑版」，关 = 中性底「手机版」，点一下切到另一态。
 *
 * 命中区 48dp（胶囊本体约 32dp 高，外面套一层 [heightIn] + 点击），与右侧刷新
 * `IconButton` 的触摸高度对齐。
 *
 * 只画与控制权有关的部分：**注入时机仍在各页面**（每页加载后按
 * [JwWebFit.fitScript] 注入、切换时当场重注入）。两个窗口的注入点不同，判据与脚本共用这一份。
 */
@Composable
fun JwDesktopModeChip(desktop: Boolean, onToggle: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clickable(onClick = onToggle)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(
                    if (desktop) scheme.primaryContainer else scheme.onSurface.copy(alpha = 0.07f),
                )
                .padding(horizontal = 12.dp, vertical = 7.dp),
        ) {
            Text(
                text = if (desktop) "电脑版" else "手机版",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (desktop) {
                    scheme.onPrimaryContainer
                } else {
                    scheme.onSurface.copy(alpha = 0.7f)
                },
            )
        }
    }
}
