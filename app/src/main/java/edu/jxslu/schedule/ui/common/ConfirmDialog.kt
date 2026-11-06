package edu.jxslu.schedule.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 确认型弹窗的唯一实现（纯文案版）。
 *
 * 根因（为什么不各页自建）：确认弹窗有三条硬要求，每份自建实现都各漏一条——
 * ① 窗口淡入（[DialogWindowFadeEffect]）漏了就是「内容瞬间出现、窗再抽掉」的突变；
 * ② 长正文要 `heightIn(max)` + `verticalScroll`，漏了小屏/大字号档位下按钮被挤出可视区，
 *    用户只能返回键退出、以为操作没生效；
 * ③ 危险动作（删除、清空、退课）确认键要用 error 色，漏了「删除」和「取消」长得一模一样。
 *
 * [danger] 只影响确认键颜色，不改文案——文案由调用方按动作写（「删除」「清空」「退课」），
 * 统一在这里生成会让「取消」和「确定」失去具体含义。
 */
@Composable
fun AppConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    danger: Boolean = false,
    dismissLabel: String = "取消",
    confirmEnabled: Boolean = true,
    dismissEnabled: Boolean = true,
    onTapOutside: (() -> Unit)? = null,
) {
    AppConfirmDialog(
        title = title,
        confirmLabel = confirmLabel,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        danger = danger,
        dismissLabel = dismissLabel,
        confirmEnabled = confirmEnabled,
        dismissEnabled = dismissEnabled,
        onTapOutside = onTapOutside,
    ) {
        Text(message, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * 确认型弹窗（自定义正文版）：正文要列编号要点、带勾选框、嵌列表时用这个，
 * 不要为了一点版式再抄一份 `AlertDialog`——淡入、滚动兜底、危险色都在这儿。
 *
 * [content] 在弹窗正文槽内，外层已经套好纵向滚动与最大高度，内部不要再套一层
 * `verticalScroll`（嵌套滚动会让长内容只滚一小段就停）。
 *
 * [dismissEnabled] 给「进行中的写操作」用：导入/重试期间取消键要真的按不动，
 * 否则点下去没反应，用户会以为按钮坏了。禁用时 `onDismissRequest` 也一并挡掉——
 * 只挡按钮不挡返回键/点外部，等于没挡。
 */
@Composable
fun AppConfirmDialog(
    title: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    danger: Boolean = false,
    dismissLabel: String = "取消",
    confirmEnabled: Boolean = true,
    dismissEnabled: Boolean = true,
    /**
     * 点遮罩 / 按返回键时的动作。不传 = 与 [onDismiss] 相同。
     *
     * 存在的理由：有些确认框里「关掉窗口」和「取消这件事」不是同一件事——校园卡到账确认
     * 点空白只该收窗（到账轮询继续跑），点「我没有付款」才停轮询并清落盘记录。共用一个
     * 出口会让其中一条语义被静默改掉。
     */
    onTapOutside: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    AlertDialog(
        modifier = modifier,
        onDismissRequest = {
            if (dismissEnabled) (onTapOutside ?: onDismiss).invoke()
        },
        title = { Text(title) },
        text = {
            // 弹窗过渡落在内容槽第一行（见 .agents/rules/ui-common.md）
            DialogWindowFadeEffect()
            Column(
                modifier = Modifier
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState()),
                content = content,
            )
        },
        confirmButton = {
            // 48dp 命中区：弹窗按钮默认 40dp，低于无障碍最小触控目标
            TextButton(
                enabled = confirmEnabled,
                onClick = onConfirm,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(
                    confirmLabel,
                    color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
        },
        dismissButton = {
            TextButton(
                enabled = dismissEnabled,
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text(dismissLabel) }
        },
    )
}

/**
 * 单按钮提示弹窗（「知道了」/「关闭」）。
 *
 * 为什么不用 [AppConfirmDialog]：它会渲染取消键，套在详情/错误提示上等于凭空多出一个
 * 什么也不做的按钮。开水页与开水入口弹层里原本各写了一份逐行相同的详情弹窗
 * （后者 KDoc 自己写着「与开水页同形态，那边私有，本地等价实现」），这里是唯一实现。
 */
@Composable
fun AppInfoDialog(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    dismissLabel: String = "知道了",
    content: @Composable ColumnScope.() -> Unit,
) {
    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            // 弹窗过渡落在内容槽第一行（见 .agents/rules/ui-common.md）
            DialogWindowFadeEffect()
            Column(
                modifier = Modifier
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState()),
                content = content,
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(dismissLabel)
            }
        },
    )
}
