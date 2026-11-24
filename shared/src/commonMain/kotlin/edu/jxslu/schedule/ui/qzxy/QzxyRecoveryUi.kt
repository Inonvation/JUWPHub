package edu.jxslu.schedule.ui.qzxy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import edu.jxslu.schedule.domain.QzxyRecovery
import edu.jxslu.schedule.ui.common.AppConfirmDialog

/** 阶梯一步的执行状态。界面只区分「还没做 / 在做 / 做成了 / 没做成」。 */
enum class QzxyRecoveryStepStatus { PENDING, RUNNING, DONE, FAILED }

/** 弹窗里的一步：步骤 + 当前状态。同一类步骤只出现一次（重复读状态就地改状态）。 */
data class QzxyRecoveryStepUi(
    val step: QzxyRecovery.Step,
    val status: QzxyRecoveryStepStatus = QzxyRecoveryStepStatus.PENDING,
)

/**
 * 开阀失败后的自动修复弹窗状态（DESIGN §3.18，2026-11-06）。
 *
 * 只在开阀失败时出现；用户点「重试」之后才跑自动阶梯（[running]），
 * 跑完仍失败转 [exhausted]，主按钮交给「手动清除设备记录」。
 */
data class QzxyRecoveryUi(
    val failure: QzxyFlowState.Failed,
    /** 失败分类：决定弹窗文案，也决定阶梯允不允许动设备上的记录（HTTP 类不动）。 */
    val kind: QzxyRecovery.Failure,
    val causes: List<QzxyRecovery.Cause>,
    val steps: List<QzxyRecoveryStepUi> = emptyList(),
    val round: Int = 1,
    val running: Boolean = false,
    val exhausted: Boolean = false,
    /** 收尾那句话：自动处理到哪一步停的、为什么停。 */
    val note: String? = null,
)

/**
 * 开阀失败的「原因 + 自动重试」弹窗。
 *
 * 为什么要有这个弹窗：失败卡上只能放一两个按钮，而共享水表的失败要靠**一串**
 * 动作才能自愈（重建链路 → 读状态 → 结算/清除 → 重发同一单）。把顺序写死在
 * 界面里用户更容易懂：他能看到每一步在做什么、做到哪、为什么停。
 *
 * 进行中（[QzxyRecoveryUi.running]）两个键都禁用：此时蓝牙与流程锁都被这条
 * 重试占着，允许关窗再点别处只会得到「上一个操作还没结束」。
 */
@Composable
fun QzxyRecoveryDialog(
    recovery: QzxyRecoveryUi,
    onRetry: () -> Unit,
    onManualClear: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onDismiss: () -> Unit,
) {
    val touchesRecord = recovery.steps.any { it.step == QzxyRecovery.Step.CLEAR_RECORD }
    // 只有「设备记录挡路」的失败才把「手动清除」当兜底；下单接口类失败清记录没有意义，
    // 兜底按钮仍是「再试一次」
    val manualClear = recovery.exhausted && recovery.kind != QzxyRecovery.Failure.ORDER
    AppConfirmDialog(
        title = if (recovery.exhausted) "自动处理没走通" else "开阀没成功",
        confirmLabel = when {
            recovery.running -> "正在自动处理…"
            manualClear -> "手动清除设备记录"
            recovery.exhausted -> "再试一次"
            else -> "重试（自动处理）"
        },
        onConfirm = { if (manualClear) onManualClear() else onRetry() },
        onDismiss = onDismiss,
        danger = manualClear,
        dismissLabel = if (recovery.exhausted) "关闭" else "先不弄了",
        confirmEnabled = !recovery.running,
        dismissEnabled = !recovery.running,
    ) {
        Text(
            text = "可能是这些原因",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(6.dp))
        recovery.causes.forEach { cause ->
            Text(
                text = "· ${cause.title}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                text = cause.hint,
                style = MaterialTheme.typography.bodySmall,
                color = if (cause.warning) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f)
                },
            )
        }

        if (recovery.steps.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = "自动处理（第 ${recovery.round}/${QzxyRecovery.MAX_ROUNDS} 轮）",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            recovery.steps.forEach { stage ->
                Text(
                    text = "${stage.status.mark()} ${stage.step.label()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = when (stage.status) {
                        QzxyRecoveryStepStatus.FAILED -> MaterialTheme.colorScheme.error
                        QzxyRecoveryStepStatus.DONE -> MaterialTheme.colorScheme.onSurface
                        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f)
                    },
                )
            }
        }

        // 只要这一轮会动设备上的记录，就把那句提醒摆在正文里：清除是「放弃结算」的
        // 显式出口，误清别人的单等于直接终止对方正在进行的用水
        if (touchesRecord) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = "清除会动设备上的记录：请先确认室友这次的用水已经结束，" +
                    "否则可能强制终止对方的订单。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        recovery.note?.let { note ->
            Spacer(Modifier.height(10.dp))
            Text(
                text = note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
            )
        }

        if (manualClear) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = "清除只解设备、不解订单：那笔预扣仍由平台超时处理（一般次日到账）。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
        ) {
            TextButton(onClick = onOpenDiagnostics) { Text("去诊断页") }
        }
    }
}

private fun QzxyRecoveryStepStatus.mark(): String = when (this) {
    QzxyRecoveryStepStatus.PENDING -> "·"
    QzxyRecoveryStepStatus.RUNNING -> "…"
    QzxyRecoveryStepStatus.DONE -> "✓"
    QzxyRecoveryStepStatus.FAILED -> "×"
}

private fun QzxyRecovery.Step.label(): String = when (this) {
    QzxyRecovery.Step.RESCAN -> "确认设备还在附近"
    QzxyRecovery.Step.RECONNECT -> "断开并重新连接设备"
    QzxyRecovery.Step.READ_STATE -> "读取设备状态"
    QzxyRecovery.Step.WAIT -> "等设备把账结完"
    QzxyRecovery.Step.SETTLE_OURS -> "先结算自己上次的记录"
    QzxyRecovery.Step.CLEAR_RECORD -> "清除设备上的残留记录"
    QzxyRecovery.Step.OPEN -> "重新开阀"
}
