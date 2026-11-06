package edu.jxslu.schedule.ui.selection

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import edu.jxslu.schedule.domain.SelectionCourse
import edu.jxslu.schedule.ui.common.AppConfirmDialog

/**
 * 选课 / 退课的二次确认弹窗（DESIGN §3.22，2026-10-08 v3 重做；2026-11-06 换骨架）。
 *
 * 口径：**关键信息放大**——课程名大字主视觉；「开设校区」与「时间冲突」单独一块
 * 高亮（冲突有内容时 error 色，无冲突给中性「无时间冲突」；退课场景给「当前已选」
 * 标记）；其余（编号/类别/学分/教师/时间/地点/已选·容量/余量）退成次级小字。
 *
 * 骨架（窗口淡入、正文滚动兜底、危险色、确认/取消按钮）由 [AppConfirmDialog] 提供，
 * 这里只剩「正文长什么样」。
 *
 * **为什么必须只换骨架、不动内容与判定**：选课 / 退课是打向教务的**写操作**，风险在
 * 教务侧（选课规则、退课次数限制、并发抢课、处理结果异步），弹窗这一层只要多一处判定
 * 变化，就可能变成「点了确认却没提交」或「误退」——这类问题本地复现不了、只能等用户
 * 踩到，而且退课往往不可逆。所以本次逐字保留：课程名 / 高亮块的两行标签 / 次级行顺序 /
 * 退课风险说明 / 按钮文案（「选课·退课」「取消」）/ `danger = dropping`。
 *
 * `confirmEnabled` 保持默认 `true`：本弹窗只在「已经有目标课程」时由调用方
 * （`SelectionCenterScreen.kt:592/604` 的 `confirmSubmit` / `confirmDrop`）弹出，
 * 不存在「未选目标」的禁用态——**不要为了对称补一个假的禁用条件**。
 */
@Composable
fun SelectionConfirmDialog(
    course: SelectionCourse,
    dropping: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AppConfirmDialog(
        title = if (dropping) "确认退课？" else "确认选课？",
        confirmLabel = if (dropping) "退课" else "选课",
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        // 退课的确认键用 error 色（与原实现同一口径）；选课保持主色
        danger = dropping,
    ) {
        // 正文槽：外层已套 `heightIn(max)` + `verticalScroll`，这里**不要**再套滚动
        Text(
            course.name,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        // 高亮块：开设校区 + 冲突状态（退课场景换成「当前已选」标记）
        Surface(
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                KeyValueRow(
                    label = "开设校区",
                    value = course.campus.ifBlank { "未标注" },
                )
                if (dropping) {
                    KeyValueRow(
                        label = "当前状态",
                        value = "已选（本次将退掉这门课）",
                        valueColor = MaterialTheme.colorScheme.primary,
                    )
                } else if (course.conflictNote.isNotBlank()) {
                    KeyValueRow(
                        label = "时间冲突",
                        value = course.conflictNote,
                        valueColor = MaterialTheme.colorScheme.error,
                    )
                } else {
                    KeyValueRow(
                        label = "时间冲突",
                        value = "无时间冲突",
                    )
                }
            }
        }
        val meta = listOfNotNull(
            course.courseNo.takeIf { it.isNotBlank() },
            course.courseType.takeIf { it.isNotBlank() },
            course.credit?.let { "${creditText(it)} 学分" },
        ).joinToString(" · ")
        if (meta.isNotBlank()) {
            SecondaryLine(meta)
        }
        if (course.teacher.isNotBlank()) {
            SecondaryLine("教师：${course.teacher}")
        }
        course.timeText.lines().filter { it.isNotBlank() }.forEach { line ->
            SecondaryLine("时间：$line")
        }
        course.placeText.lines().filter { it.isNotBlank() }.forEach { line ->
            SecondaryLine("地点：$line")
        }
        val seats = when {
            course.enrolled != null && course.capacity != null ->
                "已选 ${course.enrolled} / 容量 ${course.capacity}"
            course.enrolled != null -> "已选 ${course.enrolled}"
            course.capacity != null -> "容量 ${course.capacity}"
            else -> null
        }
        seats?.let { SecondaryLine(it) }
        course.remaining?.let { SecondaryLine("余量：$it") }
        if (dropping) {
            Text(
                "退课以教务处理结果为准，可能涉及选课规则（如退课次数限制）。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

@Composable
private fun KeyValueRow(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.padding(top = 2.dp),
        )
        Text(
            value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = valueColor,
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp),
        )
    }
}

@Composable
private fun SecondaryLine(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
        modifier = Modifier.padding(top = 6.dp),
    )
}
