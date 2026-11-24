package edu.jxslu.schedule.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import edu.jxslu.schedule.domain.AppClock
import edu.jxslu.schedule.domain.Money
import edu.jxslu.schedule.domain.TextFmt
import edu.jxslu.schedule.ui.icon.AppIcons
import edu.jxslu.schedule.ui.theme.JuwTheme

/**
 * iOS 侧的**链路验证屏**（2026-11-24，P3 第一步）。
 *
 * 它不是产品界面，也不打算是：作用是在 iPhone 上把整条链路一次性点亮——
 * Kotlin 共享代码 → `Shared.framework` → SwiftUI 容器 → Compose 渲染 → 像素。
 *
 * 为什么值得单独存在：下面每一行都对应一个「本机 Windows 无法验证」的点，
 * 装上 IPA 一眼就能看出链路是否通、以及唯一一个已知的运行期风险到底有没有发生：
 *
 * 1. **Compose 能不能在 iOS 上渲染**（`JuwTheme` + Material3 + 滚动）；
 * 2. **图标能不能画出来** —— 167 个图标是从 HugeIcons 的 Android AAR 里导出的
 *    `ImageVector`，iOS 上从没渲染过；
 * 3. **[TextFmt.decimals] 的舍入口径** —— iOS 走 `NSString(format:locale:)`，Android 走
 *    `String.format(Locale.ROOT)`。两边的值并排显示，**不一致就是这里看得见**；
 * 4. **`Money` / 整数金额格式化**是否与 Android 一致；
 * 5. **时区与当前时刻** —— `AppClock` 在 Native 上取的是不是本地时区。
 *
 * 验收方式（用户点验）：第 3 行左右两列的值必须完全相同。若右侧多一位/差一分，
 * 说明 `NSString` 的 printf 口径与 `String.format` 不同，要回去改 `PlatformFormat.ios.kt`。
 */
@Composable
fun LinkValidationScreen(modifier: Modifier = Modifier) {
    JuwTheme(darkTheme = false) {
        Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("JUWPHub · iOS 链路验证", style = MaterialTheme.typography.titleLarge)
                Text(
                    "这一屏只验「共享代码 → framework → SwiftUI → Compose」能不能通，" +
                        "不是产品界面。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                SectionTitle("1. Compose 与主题")
                InfoRow("Material3 容器", "已渲染（看到的这条就是）")
                InfoRow("当前主题色", MaterialTheme.colorScheme.primary.toString())
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ColorChip(MaterialTheme.colorScheme.primary)
                    ColorChip(MaterialTheme.colorScheme.secondary)
                    ColorChip(MaterialTheme.colorScheme.tertiary)
                    ColorChip(MaterialTheme.colorScheme.surfaceVariant)
                }

                SectionTitle("2. 图标（从 Android AAR 导出的 ImageVector）")
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(AppIcons.Calendar01, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Icon(AppIcons.Droplet, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Icon(AppIcons.Wallet01, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Icon(AppIcons.Settings01, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Icon(AppIcons.QrCode01, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Icon(AppIcons.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }

                SectionTitle("3. 定点小数（唯一的已知运行期风险）")
                Text(
                    "左：Android 的期望值（本文件里写死）。右：iOS 实际算出来的。" +
                        "两列必须完全一致。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // 这几个值挑得有针对性：12.345 与 1.045 是「乘 100 再 round 会差一分」的经典反例，
                // 0.05 / 2.675 是 double 二进制表示的边界。全都来自 TextFmtTest 的既有用例。
                DecimalRow(12.345, 2, "12.34")
                DecimalRow(1.045, 2, "1.04")
                DecimalRow(2.675, 2, "2.67")
                DecimalRow(0.05, 1, "0.1")
                DecimalRow(8.835, 2, "8.84")

                SectionTitle("4. 金额与时间")
                InfoRow("Money 解析 12.34", Money.parse("12.34")?.text() ?: "null")
                InfoRow("Money 1200 分", Money.ofFen(1200).text())
                InfoRow("当前本地时刻", AppClock.nowDateTime().toString())
                InfoRow("今天", AppClock.today().toString())
                InfoRow("TextFmt.pad(7)", TextFmt.pad(7))
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Spacer(Modifier.size(4.dp))
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(160.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun DecimalRow(value: Double, places: Int, expected: String) {
    val actual = TextFmt.decimals(value, places)
    val ok = actual == expected
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "decimals(${value.toString()}, $places)",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(160.dp),
        )
        Text(expected, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(64.dp))
        Text(actual, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(64.dp))
        // 用文案而不是 emoji 表达结果：AGENTS 禁止 emoji 当功能图标
        Text(
            if (ok) "一致" else "不一致",
            style = MaterialTheme.typography.bodyMedium,
            color = if (ok) Color(0xFF2E7D32) else Color(0xFFC62828),
        )
    }
}

@Composable
private fun ColorChip(color: Color) {
    Spacer(Modifier.size(36.dp).background(color))
}
