package edu.jxslu.schedule.data.session

/**
 * 进程内「教务网页会话可用」标记（2026-11-25 晚补）。
 *
 * ## 为什么需要它
 *
 * `AppServices.hasJwWebSession()` 是**同步**返回 `Boolean?` 的（Android 读 `CookieManager`，
 * 那是同步 API）。iOS 读 Cookie 只能走 `WKWebsiteDataStore.getAllCookies`（异步回调），
 * 装配根没法在组合期同步给一个布尔，于是上一版直接返回 `null`。
 *
 * 后果不是「少一个功能」，而是**给出错误结论**：用户刚在导入页登录成功、课表都落库了，
 * 账号中心仍显示「登录」胶囊、教务账号页仍写「未登录 · 没有保存密码」。审计把它列为 P0。
 *
 * 所以改成「异步探一次 → 记在这个标记里 → 同步读标记」：
 * - 冷启动：`IosServices.warmUp()` 探一次 Cookie；
 * - 每次抓取成功 / 失败：`IosJwReadSession` 顺手更新；
 * - 导入页落到学生端：立刻置真（用户刚登录完，这一条最该准）。
 *
 * 标记**不落盘**：它只是「这次进程里还活着吗」，持久化等于把过期会话当成有效的。
 */
object IosJwSessionFlag {

    /** 当前进程里教务网页会话是否可用。初值 false = 还没探过，与「已失效」同义。 */
    var hasSession: Boolean = false
        private set

    fun markUsable() {
        hasSession = true
    }

    fun markLost() {
        hasSession = false
    }
}
