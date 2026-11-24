package edu.jxslu.schedule.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 数据层自检条（iOS 移植 P2 验收，2026-11-24）。
 *
 * 只显示「编译通过」证明不了 Room KMP 能在目标平台上跑。真正要验的是运行期：KSP 生成的
 * 实现、BundledSQLiteDriver 在本机 ABI 上能否跑、应用数据目录是否可写、suspend DAO 的
 * 查询上下文对不对。所以这里让调用方传一个 [probe]——它做一次真实的写入 → 读回 → 删除，
 * 把结论变成一行字。
 *
 * ## 为什么这个 Composable 在 commonMain，而探测在平台侧
 *
 * 探测本身离不开平台（Android 要 `Context`，iOS 要 Application Support 目录），但**这段
 * 界面与状态机不该跟着进平台源集**：本机是 Windows，编不了 `iosMain`，写在那里的每一处
 * 类型错误都要花一次 20 分钟的 CI 往返才能发现（2026-11-24 已经为此白等过一轮）。
 * 把可编译的部分留在 commonMain、让本地门禁 `:shared:metadataCommonMainClasses` 覆盖它，
 * 平台侧只剩十几行直线调用。
 *
 * 探针失败不抛给组合：这里兜住并以红字显示，免得一次自检把验收屏整个打崩。
 */
@Composable
fun DataSelfTestStrip(
    probe: suspend () -> DataSelfTestResult,
    footnote: String,
    emptyHint: String = "数据自检：进行中…",
) {
    // 三个独立状态而不是一个数据类：避免「data class 装进 mutableStateOf」时对相等性的隐含依赖，
    // 也让这段代码更容易被门禁在无平台环境下编过。
    var done by remember { mutableStateOf(false) }
    var ok by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val result = try {
            probe()
        } catch (e: Exception) {
            // 一次性探针没有后续工作，这里连 CancellationException 一起兜住也无害。
            DataSelfTestResult(ok = false, text = "开库或读写失败：$e")
        }
        ok = result.ok
        text = result.text
        done = true
    }

    val tone = when {
        !done -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        ok -> Color(0xFF2E7D32)
        else -> Color(0xFFC62828)
    }
    val line = when {
        !done -> emptyHint
        ok -> "数据自检：通过 · $text"
        else -> "数据自检：失败 · $text"
    }
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Text(text = line, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = tone)
        if (footnote.isNotEmpty()) {
            Text(
                text = footnote,
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 自检结论。[text] 是给人看的一行话，成功失败都要有信息量。 */
class DataSelfTestResult(val ok: Boolean, val text: String)
