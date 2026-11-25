package edu.jxslu.schedule.ui.ebike

import edu.jxslu.schedule.domain.TextFmt
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * 时刻文案 `HH:mm`（骑行卡上的「更新于」「计时起点」「14:27 自动还车」）。
 *
 * 搬迁前这是 `:app` 的 `clockText`（`java.time` + `DateTimeFormatter.ofPattern("HH:mm")`），
 * 两处都是 JVM 专有；共享层改成 kotlinx-datetime 取本地时分 + [TextFmt.clock] 补零，
 * 输出与旧实现逐字相同（`09:05` 而不是 `9:05`）。
 *
 * `:app` 里还留着一个 `clockText`（快趣账号页 / 订单页在用），它转调本函数——
 * **不要**再把 `DateTimeFormatter` 那份抄回来。
 */
fun rideClockText(millis: Long): String {
    val local = Instant.fromEpochMilliseconds(millis)
        .toLocalDateTime(TimeZone.currentSystemDefault())
    return TextFmt.clock(local.hour * 60 + local.minute)
}
