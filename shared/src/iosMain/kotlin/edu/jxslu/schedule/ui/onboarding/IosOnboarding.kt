package edu.jxslu.schedule.ui.onboarding

import edu.jxslu.schedule.data.session.CasEnsureResult
import edu.jxslu.schedule.platform.AccountCredentials

/**
 * 首启引导第 2 步在 iOS 侧的装配（2026-11-25 晚）。
 *
 * ## 为什么现在才有了
 *
 * 上一版 iOS 这一格是 `null`，引导第 2 步只能渲染「去导入页登录」一个按钮——点了打开
 * `WKWebView`，登完关回来，**引导页并不知道到底成没成**（`jwOk` 永远是 false），
 * 于是用户看到的是「刚导入完，回来还让我去登录」。这是用户 2026-11-25 报的原话。
 *
 * 现在 `CasSession` 搬进了 `shared/commonMain`，iOS 有 Ktor 传输（[IosCasTransport]）与
 * Keychain 凭证（[IosCredentialVault]），所以这一格可以填真实现：**App 内原生登录 +
 * 保存凭据**，与 Android 逐条一致。引导页拿到 `Ready` 就勾上「已完成」，不再靠猜。
 *
 * ## 为什么转给 `AccountCredentials` 而不是自己持 `CasSession`
 *
 * 「我的 → 教务账户」那一页保存密码走的也是同两个动作（`verifyJw` / `saveJw`），
 * 而 `AccountCredentials` 的实现里已经写好了「先清失败闸门再验」这条顺序
 * （`.agents/rules/login-session.md` 第 ⑤ 条）。两处各持一份 `CasSession` 调用序列
 * 迟早会走偏，所以这里只做一层转接，动作的唯一实现在 `IosServices` 那边。
 */
internal class IosOnboardingJwLogin(
    private val credentials: AccountCredentials,
) : OnboardingJwLogin {

    /** 清闸门与「先验后存」的顺序在 [AccountCredentials.verifyJw] 里，这里不再重复。 */
    override suspend fun verify(username: String, password: String): CasEnsureResult =
        credentials.verifyJw(username, password)

    override fun save(username: String, password: String) =
        credentials.saveJw(username, password)
}
