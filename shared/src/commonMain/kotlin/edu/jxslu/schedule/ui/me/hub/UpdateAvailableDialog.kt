package edu.jxslu.schedule.ui.me.hub

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import edu.jxslu.schedule.domain.UpdateCheck
import edu.jxslu.schedule.domain.UpdateManifest
import edu.jxslu.schedule.ui.common.DialogWindowFadeEffect
import edu.jxslu.schedule.ui.common.InlineNoticeRow
import edu.jxslu.schedule.ui.common.NoticeTone
import edu.jxslu.schedule.ui.common.copyPlainText
import kotlinx.coroutines.delay

/**
 * 「发现新版本」弹窗（DESIGN §3.3）：更新日志可滚（长日志小屏不裁切）+ SHA-256 行
 * （点按复制）+ 下载双通道 + 「忽略此次更新」。镜像链接来自仓库镜像清单里维护的
 * `mirrorPrefix`，前缀缺失/不合法时只出「GitHub 下载」一个出口。下载都是交给系统浏览器
 * 打开，App 不落 APK。
 *
 * ## 2026-11-25：搬进 `shared/commonMain`
 *
 * 弹窗本体只有「文案 + 复制 + 打开链接」三件事，两端都成立；留在 `:app` 的只剩
 * [UpdateAvailableDialogHost]（要 `Graph.appScope` 与 `Graph.repository`）和 `openUrl`
 * （`ACTION_VIEW` 是平台能力）。所以把两件平台动作收成参数：
 *
 * - 复制：走 `ui/common/Clipboard` 那条缝（`copyPlainText`），不认 `Context`；
 * - 打开链接：[onOpenUrl] 由宿主注入，**返回值就是失败文案**（null = 打开成功）。
 *   为什么不让共享层自己给文案：Android 那句是「没有可打开链接的应用」
 *   （`ActivityNotFoundException` 的实话），iOS 侧对应的是 `canOpenURL` 判否，
 *   两端的失败原因不同，文案得跟着各平台走。
 *
 * @param onIgnore 点「忽略此次更新」：调用方负责落「已忽略的版本号」并关闭弹窗
 *   （自动检查不再弹该版本；手动检查不读它，永远弹）。
 * @param onOpenUrl 打开下载页 / 开源仓库；返回 null = 打开成功，非 null = 失败文案。
 */
@Composable
fun UpdateAvailableDialog(
    manifest: UpdateManifest,
    onDismiss: () -> Unit,
    onIgnore: () -> Unit,
    onOpenUrl: (String) -> String?,
) {
    val mirrorUrl = UpdateCheck.mirrorDownloadUrl(manifest)
    // 复制反馈：每次点按重新计时（计数翻转触发重新延时），2 秒后自动收
    var copiedAt by remember { mutableStateOf(0) }
    var openFailure by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(copiedAt) {
        if (copiedAt > 0) {
            delay(2000)
            copiedAt = 0
        }
    }

    fun openAndClose(url: String) {
        val failure = onOpenUrl(url)
        if (failure == null) onDismiss() else openFailure = failure
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("发现新版本 v${manifest.version}") },
        text = {
            // DialogWindowFadeEffect 必须放内容槽第一行才生效（放 AlertDialog 外是死代码）
            DialogWindowFadeEffect()
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (manifest.notes.isNotBlank()) {
                    Text(
                        manifest.notes,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .heightIn(max = 320.dp)
                            .verticalScroll(rememberScrollState()),
                    )
                }
                if (UpdateCheck.hasUsableDigest(manifest)) {
                    Text(
                        "SHA-256：${manifest.sha256}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                copyPlainText(label = "SHA-256", text = manifest.sha256)
                                copiedAt++
                            }
                            .padding(vertical = 2.dp),
                    )
                    Text(
                        "点按复制 · 镜像下载为第三方通道，可核对此值确认安装包完整",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                    )
                }
                if (copiedAt > 0) InlineNoticeRow(message = "已复制 SHA-256", tone = NoticeTone.Success)
                openFailure?.let { InlineNoticeRow(message = it, tone = NoticeTone.Warning) }
            }
        },
        confirmButton = {
            if (mirrorUrl != null) {
                TextButton(onClick = { openAndClose(mirrorUrl) }) { Text("通过国内镜像下载") }
            } else {
                TextButton(onClick = { openAndClose(UpdateCheck.githubDownloadUrl(manifest)) }) {
                    Text("GitHub 下载")
                }
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onIgnore) { Text("忽略此次更新") }
                if (mirrorUrl != null) {
                    TextButton(onClick = { openAndClose(UpdateCheck.githubDownloadUrl(manifest)) }) {
                        Text("GitHub 下载")
                    }
                }
            }
        },
    )
}
