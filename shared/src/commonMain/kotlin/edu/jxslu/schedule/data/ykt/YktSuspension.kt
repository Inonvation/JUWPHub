package edu.jxslu.schedule.data.ykt

/**
 * 「一卡通的登录态已被判失效」这条运行时事实的跨平台缝（iOS 移植 S14，2026-11-25）。
 *
 * ## 为什么需要它
 *
 * [YktRepository] 与 [edu.jxslu.schedule.data.power.PowerRepository] 都会在**凭证确实不对**
 * 时把该平台标成「已失效」，登录成功后再清掉——两个平台共用同一份凭证（学号 + 查询密码），
 * 所以在 Android 侧它们是同一个 `LoginTarget.Ykt`、同一行状态卡。
 *
 * 那份状态现在住在 `:app` 的 `data/session/SessionStatus`（进程级 `StateFlow`，
 * 给「我的」页的账户卡读）。共享层看不见它，于是把「标 / 清」两个动作收成这一个方法。
 *
 * ## 为什么是 `fun interface` 而不是可空回调两个
 *
 * 两个方向落在同一个布尔上（`true` = 标失效，`false` = 清标记），一个方法就够；
 * `fun interface` 让平台侧可以直接写 lambda（Android 装配根就是
 * `{ suspended -> SessionStatus.syncSuspended(LoginTarget.Ykt, suspended) }`）。
 *
 * ## null = 本平台没有状态卡
 *
 * iOS 侧还没有登录态卡片，装配时传 null，两个仓库就不做标记动作。这不是「降级」：
 * 失效判定本身仍由服务端 401 驱动，仓库照常重登一次、照常抛分类异常给页面。
 */
fun interface YktSuspension {

    /** `true` = 凭证被判错、标记失效；`false` = 登录成功 / 清凭证，撤销标记。 */
    fun setSuspended(suspended: Boolean)
}
