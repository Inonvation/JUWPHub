package edu.jxslu.schedule.ui.common

import edu.jxslu.schedule.domain.AppClock
import edu.jxslu.schedule.domain.DormRepairTone
import edu.jxslu.schedule.domain.TextFmt

/**
 * 宿舍报修卡 / 详情页共用的两个小口径（DESIGN §4.49）：
 * 语气映射只此一处；时间格式只此一处。
 *
 * ## 为什么整份搬进共享层（S16，2026-11-25）
 *
 * 今日页的收件箱卡（`ui/today/InboxTodayCard`）是这些口径的第二个消费方，而它要搬进
 * `commonMain` 才能给 iOS 用。**分成两处各写一份时间格式**正是这轮要消掉的东西
 * （报修行与报修详情页显示同一条记录，格式漂了就是两套说法），所以整份文件跟着上移，
 * 而不是把要用的两个函数抄到共享层。
 *
 * 三个函数从 `internal` 放开成 `public`：跨模块 `internal` 不可见，而 `:app` 的
 * `ui/campus/DormRepairScreen` 还在原地调它们（包名不变，调用点零改动）。
 */
fun DormRepairTone.toHeroTone(): HeroTone = when (this) {
    DormRepairTone.Primary -> HeroTone.Primary
    DormRepairTone.Neutral -> HeroTone.Neutral
    DormRepairTone.Success -> HeroTone.Success
    DormRepairTone.Error -> HeroTone.Error
}

/**
 * epoch 毫秒 → `MM-dd HH:mm`（本地时区）。
 *
 * 原来是 `DateTimeFormatter.ofPattern("MM-dd HH:mm", Locale.CHINA)`；kotlinx-datetime 没有
 * `DateTimeFormatter` 等价物，只能手拼。`Locale.CHINA` 在那个格式器里只管数字/文本的本地化，
 * 纯数字模式不受影响，所以手拼结果逐字相同。时区走 [AppClock.dateTimeOf] 的系统默认，
 * 与原来的 `ZoneId.systemDefault()` 同口径（不写死东八区）。
 */
fun formatDormRepairTime(epochMillis: Long): String {
    val time = AppClock.dateTimeOf(epochMillis)
    return "${TextFmt.pad(time.monthNumber)}-${TextFmt.pad(time.dayOfMonth)} " +
        "${TextFmt.pad(time.hour)}:${TextFmt.pad(time.minute)}"
}

/** 审批人列表太长时只留前 3 个；完整名单在详情页流程卡里也按这个口径收。 */
fun approverSummary(names: List<String>, max: Int = 3): String {
    if (names.isEmpty()) return ""
    val head = names.take(max).joinToString("、")
    return if (names.size > max) "$head 等 ${names.size} 人" else head
}
