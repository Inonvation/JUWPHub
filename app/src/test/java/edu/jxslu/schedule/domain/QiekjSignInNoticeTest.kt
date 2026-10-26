package edu.jxslu.schedule.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/** 自动签到底部提示文案口径：自带「胖乖签到成功」前缀，积分有哪个说哪个，缺位不猜数。 */
class QiekjSignInNoticeTest {

    @Test
    fun 两个积分都有_加号与当前并列() {
        assertEquals("胖乖签到成功：今日 +2 积分，当前 44 积分", QiekjSignInNotice.text("2", "44"))
    }

    @Test
    fun 只有当前积分() {
        assertEquals("胖乖签到成功：当前 44 积分", QiekjSignInNotice.text(null, "44"))
    }

    @Test
    fun 只有本次积分() {
        assertEquals("胖乖签到成功：今日 +2 积分", QiekjSignInNotice.text("2", null))
    }

    @Test
    fun 都缺位只报成功() {
        assertEquals("胖乖签到成功", QiekjSignInNotice.text(null, null))
        assertEquals("胖乖签到成功", QiekjSignInNotice.text("", " "))
    }

    @Test
    fun 脏串两侧空白裁掉() {
        assertEquals("胖乖签到成功：今日 +2 积分，当前 44 积分", QiekjSignInNotice.text(" 2 ", " 44"))
    }
}
