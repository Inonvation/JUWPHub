package edu.jxslu.schedule.ui.common

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 充值跳转前的风险小字（2026-11-06 用户要求）。
 *
 * 四个充值入口——电费（平台聚合收银台）、一卡通（微信）、胖乖生活 / 趣智校园（支付宝
 * 小程序）——在**真正跳转下单**前的那张确认弹窗里都要带这两句：先核对账户（充错退不
 * 回来），以及「官方渠道也挡不住上游接口变更」。文案单一来源放这里，改口径只改一处。
 *
 * 与 `RechargeDisclaimerDialog`（进入充值功能前的整屏声明，可静默一周）分工不同：
 * 那份管「要不要用这个功能」，这份管「这一笔钱马上要出去」。入口处的三条声明不重复
 * 这两句，避免同一句话在两个弹窗里各说一遍。
 */
@Composable
fun RechargeRiskNote(modifier: Modifier = Modifier) {
    Text(
        text = "请先核对充值账户与金额，充错无法撤回。" +
            "充值方式是官方渠道，但无法避免上游接口变更，由此造成的损失请自行承担。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
    )
}
