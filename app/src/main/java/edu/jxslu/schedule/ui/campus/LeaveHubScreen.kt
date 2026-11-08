package edu.jxslu.schedule.ui.campus

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import edu.jxslu.schedule.XgFormActivity
import edu.jxslu.schedule.data.xg.XgForm
import edu.jxslu.schedule.data.xg.XgUrls
import edu.jxslu.schedule.ui.common.AppCard
import edu.jxslu.schedule.ui.common.AppCardDivider
import edu.jxslu.schedule.ui.common.PageScaffoldColumn
import edu.jxslu.schedule.ui.common.rememberAppHaptics
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.CalendarCheckIn01
import me.rerere.hugeicons.stroke.CalendarCheckOut01
import me.rerere.hugeicons.stroke.CalendarPlus01
import me.rerere.hugeicons.stroke.ChevronRight

/**
 * 请假系统（2026-11-08 用户口径）：学工「新版请销假」分组的三个入口页。
 *
 * 学校把它配成三张**各自独立**的审批表单（不是同一个流程的三步），所以这里只做
 * 分发：每行直接开对应的学工表单 WebView（[XgFormActivity]），表单标识在
 * [XgUrls.FORMS]（2026-11-08 从工作台应用列表实测取得）。App 不代填表。
 */
@Composable
fun LeaveHubScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    PageScaffoldColumn(
        title = "请假系统",
        onBack = onBack,
        snackbar = snackbar,
    ) {
        AppCard(contentPadding = PaddingValues(0.dp)) {
            LeaveEntry(
                icon = HugeIcons.CalendarCheckIn01,
                form = XgUrls.LEAVE,
                onClick = { XgFormActivity.start(context, XgUrls.LEAVE) },
            )
            AppCardDivider(Modifier.padding(start = 48.dp))
            LeaveEntry(
                icon = HugeIcons.CalendarCheckOut01,
                form = XgUrls.LEAVE_CANCEL,
                onClick = { XgFormActivity.start(context, XgUrls.LEAVE_CANCEL) },
            )
            AppCardDivider(Modifier.padding(start = 48.dp))
            LeaveEntry(
                icon = HugeIcons.CalendarPlus01,
                form = XgUrls.LEAVE_EXTEND,
                onClick = { XgFormActivity.start(context, XgUrls.LEAVE_EXTEND) },
            )
        }
        Text(
            text = "三个入口都是学工系统的官方页面，需学校统一认证账号（与教务同一个）；" +
                "销假页要取手机定位时系统会另弹权限。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 2.dp),
        )
    }
}

/** 「新版请销假」分组里的一行：图标 + 表单名 + 副标题 + 右箭头。 */
@Composable
private fun LeaveEntry(
    icon: ImageVector,
    form: XgForm,
    onClick: () -> Unit,
) {
    val haptics = rememberAppHaptics()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = form.title) {
                haptics.tap()
                onClick()
            }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = form.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = form.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            imageVector = HugeIcons.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
            modifier = Modifier.size(16.dp),
        )
    }
}
