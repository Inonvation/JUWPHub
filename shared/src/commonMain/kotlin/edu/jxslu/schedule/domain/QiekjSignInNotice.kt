package edu.jxslu.schedule.domain

/**
 * 胖乖「自动签到成功」底部提示的文案口径（2026-10-24）。
 *
 * 提示卡是单行自足的，没有标题位——「胖乖签到成功」要并进正文。
 * 两个积分都可能缺位（`todaySignIntegral` / `totalIntegral` 解析失败都不猜数），
 * 有哪个说哪个；都没有就只报成功。文案只在这里一份，壳层提示与测试共用。
 */
object QiekjSignInNotice {

    fun text(gained: String?, total: String?): String = buildString {
        append("胖乖签到成功")
        val details = buildList {
            if (!gained.isNullOrBlank()) add("今日 +${gained.trim()} 积分")
            if (!total.isNullOrBlank()) add("当前 ${total.trim()} 积分")
        }
        if (details.isNotEmpty()) append("：").append(details.joinToString("，"))
    }
}
