package edu.jxslu.schedule.ui.inbox

import androidx.compose.ui.graphics.vector.ImageVector
import edu.jxslu.schedule.domain.InboxKind
import edu.jxslu.schedule.ui.icon.AppIcons

/**
 * 收件箱各来源的行首图标（图标名全部查过本地 JAR 或项目已在用，勿在此替换成没查过的名字）。
 * 收件箱页与今日页收件箱卡共用——两处各写迟早漂。
 *
 * **public 而不是 internal**（iOS 移植 S19）：今日页的收件箱卡（`ui/today/InboxTodayCard`）
 * 还留在 `:app`，跨模块看不见 `internal`。等今日页搬过来再收窄也不迟。
 */
fun inboxKindIcon(kind: String): ImageVector = when (
    runCatching { InboxKind.valueOf(kind) }.getOrDefault(InboxKind.EXAM)
) {
    InboxKind.EXAM -> AppIcons.Calendar01
    InboxKind.SCORE -> AppIcons.GraduationScroll
    InboxKind.SCHEDULE_CHANGE -> AppIcons.Exchange01
    InboxKind.SCHEDULE_ARCHIVE -> AppIcons.CalendarSetting01
    InboxKind.SCHEDULE_NEW_TERM -> AppIcons.CalendarAdd01
    InboxKind.SELECTION_ROUND -> AppIcons.BookEdit
    InboxKind.POWER_LOW -> AppIcons.Alert02
    InboxKind.YKT_LOW -> AppIcons.Wallet01
    InboxKind.WENQU_ACTIVITY -> AppIcons.HeartHandshake
    InboxKind.DORM_REPAIR -> AppIcons.Repair
    InboxKind.KVCX_RIDE -> AppIcons.Bicycle
    InboxKind.WATER_SESSION -> AppIcons.Droplet
    InboxKind.QZXY_SESSION -> AppIcons.ShowerHead
    InboxKind.UJING_ORDER -> AppIcons.WashingMachine
    InboxKind.YKT_RECHARGE -> AppIcons.WalletAdd01
    InboxKind.YKT_PAYMENT -> AppIcons.ShoppingBag01
    InboxKind.POWER_RECHARGE -> AppIcons.Flash
}
