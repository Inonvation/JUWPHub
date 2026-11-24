package edu.jxslu.schedule.data.wenqu

/**
 * 问渠登录凭证（DESIGN §4.45）。**密码可选**——「记住登录」默认开；关掉时只落 token，
 * 存了密码才能在 JWT 到期前自动续期（见 [canRefresh]）。
 *
 * [expiresAt] 从 JWT 的 `exp` 解析；[hours] / [starLevel] 是**上次成功取到的展示影子**，
 * 用于「我的」页账号卡一行与问渠页首屏，进页后会再刷一次。
 * 平台响应里的学号 / 姓名 / 负责人姓名电话**一律不落盘**（负责人只在详情弹层展示，见 [WenquActivity]）。
 *
 * iOS 移植 S15（2026-11-24）：模型随仓库搬进 `:shared`，落盘实现留在 `:app`
 * （`WenquCredentialStore`，EncryptedSharedPreferences + Keystore 自愈，见 [WenquSessionStore]）。
 */
data class WenquSession(
    val phone: String,
    val password: String,
    val token: String,
    val expiresAt: Long,
    val starLevel: Int = 0,
    /** 上次取到的总工时；[hoursFetchedAt] 为 0 表示还没取过。 */
    val hours: Double = 0.0,
    val hoursFetchedAt: Long = 0L,
) {
    /**
     * 会话可用的判据：手机号 + token。**密码不是必要条件**——「记住登录」关掉时只存 token
     * （默认开，2026-10-21），JWT 有效期内照常可用。
     */
    val isComplete: Boolean get() = phone.isNotBlank() && token.isNotBlank()

    /** 存了密码才能自动续期；没存密码的会话在 JWT 到期后要求重新登录。 */
    val canRefresh: Boolean get() = password.isNotBlank()
}
