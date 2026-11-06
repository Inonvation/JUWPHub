package edu.jxslu.schedule.ui.life

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import edu.jxslu.schedule.data.power.PowerEntryState
import edu.jxslu.schedule.ui.common.AmountHeroText
import edu.jxslu.schedule.ui.common.AppConfirmDialog
import edu.jxslu.schedule.ui.common.ImeAwareModalBottomSheet
import edu.jxslu.schedule.ui.common.RechargeRiskNote

/**
 * 电费充值弹层（DESIGN §4.24）：金额 → 下单 → 平台聚合收银台网页（选支付方式并付款）→ 受理。
 *
 * - **无状态组件**：流程状态在 [LifeViewModel.powerRecharge]（VM 持有，转屏不丢）；
 * - **支付方式在网页里选**（2026-11-06 用户拍板改版）：平台把农行那条换成了学校自己的
 *   聚合收银台，页面里能切「农行缴电费专用 / 校园卡 / 电子帐户」——所以 App 不再有渠道
 *   FilterChip、也没有 6 位消费密码键盘（`PasswordStep` 整块删除，连带
 *   `LifeViewModel.submitPowerPassword`）。App 只负责：填金额、下单、把收银台链接交给
 *   `PowerBankPayActivity` 的内嵌页、等查单结果。
 * - [BankStep] 只等结果：支付动作全在网页里，这一页只有状态与按钮，没有任何输入框。
 * - 键盘遮挡与退场时序走 [ImeAwareModalBottomSheet]（`skipPartiallyExpanded = true` + 「先收
 *   键盘、键盘收完再滑走」两段退场）：金额步有输入框，键盘弹起后 M3 会把弹层改判到半高
 *   锚点、底部按钮被盖住——与校园卡充值弹层同一坑（定位过程见 DESIGN §4.19）。
 *   不要自己再垫键盘高度。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PowerRechargeSheet(
    state: LifeViewModel.PowerRechargeUi,
    roomLabel: String?,
    remainText: String?,
    onDismiss: () -> Unit,
    onPlaceOrder: (yuan: String) -> Unit,
    onReopenCashier: () -> Unit,
    onCheckPaid: () -> Unit,
) {
    // 键盘遮挡与退场时序都在 ImeAwareModalBottomSheet 里（`skipPartiallyExpanded = true`；
    // 退场「先收键盘、键盘收完再滑走」两段，见 ui/common/SheetDismissIme.kt）
    ImeAwareModalBottomSheet(onDismiss = onDismiss) {
        // 收键盘必须用弹窗窗口这一份（写在弹层内容里）——见 ui/common/SheetDismissIme.kt
        val keyboard = LocalSoftwareKeyboardController.current
        // 受理成功后自动收起键盘：这一步没有输入，键盘留着只会挡住「完成」
        LaunchedEffect(state.step) {
            if (state.step == LifeViewModel.PowerRechargeUi.Step.Accepted) keyboard?.hide()
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // 2026-11-06 改版：渠道（电子账户/农行）与「电子账户余额 + 去充值电子账户」信息区
            // 一并删除——支付方式改在平台收银台网页里选，余额在那边自己会显示。
            Text(
                text = "电费充值",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (roomLabel != null || remainText != null) {
                Text(
                    text = listOfNotNull(roomLabel, remainText).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
            // 打开弹层时清掉的未支付单（平台不自动清，堆积会让新下单 500）。
            // 提示落在弹层里：页面 Scaffold 的 Snackbar 会被这个独立窗口盖住。
            if (state.cleanedOrders > 0) {
                Text(
                    text = "已清理 ${state.cleanedOrders} 笔未支付订单",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }

            when (state.step) {
                LifeViewModel.PowerRechargeUi.Step.Amount -> AmountStep(
                    state = state,
                    roomLabel = roomLabel,
                    onPlaceOrder = onPlaceOrder,
                    onReopenCashier = onReopenCashier,
                )

                LifeViewModel.PowerRechargeUi.Step.BankPay -> BankStep(
                    state = state,
                    roomLabel = roomLabel,
                    onReopenCashier = onReopenCashier,
                    onCheckPaid = onCheckPaid,
                )

                LifeViewModel.PowerRechargeUi.Step.Accepted -> AcceptedStep(
                    amountYuan = state.amountYuan,
                    paidStatus = state.paidStatus,
                    entryStatus = state.entryStatus,
                    onDismiss = onDismiss,
                )
            }
        }
    }
}

@Composable
private fun AmountStep(
    state: LifeViewModel.PowerRechargeUi,
    /** 绑定房间号（读数里的 room，如 9Z999）；二次确认弹窗要加粗它。 */
    roomLabel: String?,
    onPlaceOrder: (String) -> Unit,
    /** 下单成功但收银台没打开时的重试（同一单，不重复下单）。 */
    onReopenCashier: () -> Unit,
) {
    var amount by rememberSaveable { mutableStateOf("") }
    var confirm by remember { mutableStateOf(false) }
    val parsed = amount.toBigDecimalOrNull()
    val valid = parsed != null && parsed >= java.math.BigDecimal("0.01") &&
        parsed <= java.math.BigDecimal("500.00")

    // 说明文案默认中性灰（2026-11-06：渠道已删，支付方式在收银台网页里选）。
    Text(
        text = "下一步在 App 内打开平台收银台，用页面里的支付方式付款（农行缴电费专用 / 校园卡 / 电子帐户）。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
    )
    OutlinedTextField(
        value = amount,
        onValueChange = { raw ->
            val cleaned = raw.filter { c -> c.isDigit() || c == '.' }
            amount = if (cleaned.count { it == '.' } > 1) amount else {
                val dot = cleaned.indexOf('.')
                if (dot >= 0 && cleaned.length - dot - 1 > 2) amount else cleaned
            }
        },
        label = { Text("充值金额（元）") },
        supportingText = {
            Text(
                when {
                    amount.isEmpty() -> "0.01 – 500.00 元；直接缴房间电费"
                    !valid -> "金额需在 0.01 – 500.00 元之间"
                    else -> "确认后下单，再在收银台网页里付款"
                },
            )
        },
        isError = amount.isNotEmpty() && !valid,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("10", "50", "100").forEach { preset ->
            OutlinedButton(
                onClick = { amount = preset },
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(0.dp),
            ) { Text(preset) }
        }
    }
    state.error?.let {
        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
    // 下单成功、收银台没打开（平台慢响应超时）：先在同一单上重试；这笔单若已被清理
    // （打开弹层会清未支付单），VM 会照原金额重下一单——两种都由这一个按钮兜住。
    if (state.orderId != null && state.error != null) {
        OutlinedButton(
            onClick = onReopenCashier,
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("重新打开支付页") }
    }
    Button(
        onClick = { confirm = true },
        enabled = valid && !state.busy,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (state.busy) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp))
        } else {
            Text("下一步")
        }
    }

    if (confirm) {
        AppConfirmDialog(
            title = "确认充值金额？",
            confirmLabel = "去下单",
            onConfirm = {
                confirm = false
                onPlaceOrder(amount)
            },
            onDismiss = { confirm = false },
            // 钱还没出去：这一步只是把订单提交给平台，未支付订单 30 分钟自动失效，
            // 所以确认键保持主色（红色留给「退出登录 / 清凭证」那类不可撤销动作）
            danger = false,
        ) {
            // 房间号**加粗**（2026-09-28 用户要求）：这一屏盖住了弹层头部的房间行，
            // 而「充到哪个寝室」比「充多少」更容易错——一个房间一个电表，充错就是别人的。
            // 房间号取不到（读数没回来）时不显示数字，宁缺勿错。
            Text(
                buildAnnotatedString {
                    append("将为")
                    if (roomLabel.isNullOrBlank()) {
                        append("当前绑定房间")
                    } else {
                        append("房间 ")
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(roomLabel) }
                    }
                    append(" 的电费缴纳 ¥$amount。\n\n")
                    append("下一步在 App 内打开平台收银台，按页面提示选支付方式付款；")
                    append("没付款就离开不会扣款，订单 30 分钟自动失效。")
                },
            )
            // 跳转前的风险小字：账户核对 + 上游接口变更（口径单一来源，见 RechargeRiskNote）
            RechargeRiskNote()
        }
    }
}

/**
 * 支付步（2026-09-28 起是「农行收银台」，2026-11-06 起是平台聚合收银台）：页面已在
 * App 内嵌页打开，这里只等支付结果。
 *
 * 三件事：说清「在哪付、付完做什么」；给一个**重开支付页**的兜底（链接是一次性会话，
 * 重开走 VM 现取新链接）；支付完成后点「检查到账」——从内嵌页回到本页也会
 * 自动查一次（`RechargeHost` 的 resume 计数）。
 *
 * 支付动作全在网页里：这一页只有状态与按钮，没有任何输入框（口径见 DESIGN §4.24）。
 */
@Composable
private fun BankStep(
    state: LifeViewModel.PowerRechargeUi,
    /** 绑定房间号；付款前再报一次「充的是哪个寝室」。 */
    roomLabel: String?,
    onReopenCashier: () -> Unit,
    onCheckPaid: () -> Unit,
) {
    Text(
        text = "请在支付页完成付款",
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
    )
    state.amountYuan?.let {
        Text(
            text = buildAnnotatedString {
                roomLabel?.takeIf { label -> label.isNotBlank() }?.let { label ->
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(label) }
                    append(" · ")
                }
                append("本次充值 ¥$it")
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
    }
    Text(
        text = "支付页已在 App 内打开，按页面提示完成付款（可能先让你选支付方式）。" +
            "页面关了就点下面的「重新打开」。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
    )
    state.bankNote?.let {
        Text(
            text = it,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
    }
    state.error?.let {
        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = onReopenCashier,
            enabled = !state.busy,
            modifier = Modifier.weight(1f),
        ) { Text("重新打开") }
        Button(
            onClick = onCheckPaid,
            enabled = !state.busy,
            modifier = Modifier.weight(1f),
        ) {
            if (state.busy) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp))
            } else {
                Text("检查到账")
            }
        }
    }
}

@Composable
private fun AcceptedStep(
    amountYuan: String?,
    paidStatus: Int?,
    entryStatus: PowerEntryState?,
    onDismiss: () -> Unit,
) {
    // 金额大字（2026-10-07）：钱已动的这一屏必须能看到扣了多少——此前金额只在密码步
    // 与农行等待步出现，出结果的一步反而没有。样式走 AmountHeroText 唯一口径。
    amountYuan?.takeIf { it.isNotBlank() }?.let {
        AmountHeroText(
            text = "¥$it",
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
    }
    Text(
        // 查单确认过才敢说「已扣款」；没确认到就如实说「已受理」（不编）
        text = if (paidStatus == 1) "支付成功，已确认扣款" else "支付已受理，正在确认到账",
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
    )
    when (entryStatus) {
        // 2026-09-29 事故（三笔「支付成功」的订单永远没入账）后，扣款与入账分开报：
        // 入账位有结论就说结论，还在处理就如实说「处理中」，失败就明说找管理员。
        // 2026-11-06 用户订正：平台记账 ≠ 电表读数已更新——到账常见 1–3 小时延迟，
        // 所以这里不写「电量已入账」这类看起来钱已经变成电的话。
        PowerEntryState.ENTERED -> EntryLine("平台已记账；电量到账通常有 1–3 小时延迟，稍后回来看剩余电量。")
        PowerEntryState.PENDING -> EntryLine("电量入账处理中（通常 1–3 小时）——稍后在左侧栏钱包查看电费，或到「缴费账单」核对。")
        PowerEntryState.FAILED -> Text(
            text = "扣款已成功，但电量入账失败——请联系缴费平台管理员处理。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        // 入账位没查到：只说时延，不替平台保证结果
        null -> Text(
            text = "电费到账通常有 1–3 小时延迟；稍后重新展开左侧栏即可看到最新剩余电量。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
    }
    Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("完成") }
}

/** 受理步的入账状态行（灰色小字，与通用提示同款式）。 */
@Composable
private fun EntryLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
    )
}
