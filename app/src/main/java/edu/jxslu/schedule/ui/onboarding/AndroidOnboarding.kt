package edu.jxslu.schedule.ui.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import edu.jxslu.schedule.Graph
import edu.jxslu.schedule.R
import edu.jxslu.schedule.data.session.CasEnsureResult
import edu.jxslu.schedule.data.session.CasSession
import edu.jxslu.schedule.data.session.CredentialVault

/**
 * 首启引导在 Android 侧的装配（iOS 移植 S22，2026-11-25）。
 *
 * 引导页已经搬进 `shared/commonMain`，平台差异全在 [OnboardingScreen] 的可空槽位里。
 * 本文件只做一件事：把 Android 真有的那几件东西（CAS 会话 + 凭证保险箱、`R.drawable`、
 * `SubpageActivity`、`Activity` 的后台 / 退场）填进那些槽位。**Android 侧六个槽位全部非空**，
 * 所以六屏一屏不少，行为与搬迁前一致。
 */

/**
 * 第 2 步的原生教务登录：`CasSession` 负责验证，`CredentialVault` 负责落库。
 *
 * **先把闸门清零再验证**（`.agents/rules/login-session.md` 第 ⑤ 条）：用户在这一步重新输入
 * 等于重新开始计数，不清的话上一次的失败次数会把这次刚敲对的密码顶掉。这条顺序不能挪到
 * 调用点——调用点只有这一次，而闸门的存在意义是「每个入口都一样」。
 */
private class AndroidOnboardingJwLogin(
    private val cas: CasSession,
    private val vault: CredentialVault,
) : OnboardingJwLogin {

    override suspend fun verify(username: String, password: String): CasEnsureResult {
        cas.onCredentialsUpdated()
        // 取消异常原样抛：用户在登录途中退出引导，这条链不该在后台跑完再落一次库
        return cas.tryLogin(username, password)
    }

    override fun save(username: String, password: String) = vault.saveCas(username, password)
}

/**
 * 各步品牌位图的 id：与搬迁前散在 `BrandTile(R.drawable.…)` 里的取值逐一对齐。
 *
 * 注意 `wenqu` 同时服务第 3 步「一卡通 · 电费」与完成页那一行——那是 2026-10-24 起的现状，
 * 见 [OnboardingBrandIcons.wenqu] 的 KDoc，别在移植里顺手「修正」。
 */
private fun onboardBrandIcons() = OnboardingBrandIcons(
    school = R.drawable.ic_school_badge,
    wenqu = R.drawable.ic_brand_wenqu,
    pangguai = R.drawable.ic_brand_pangguai,
    qzxy = R.drawable.ic_brand_qzxy,
    ujing = R.drawable.ic_brand_ujing,
    kqcx = R.drawable.ic_brand_kqcx,
)

/** [onboardBrandIcons] 的组合期版本（一张引导窗内不变，`remember` 住即可）。 */
@Composable
internal fun rememberOnboardingBrandIcons(): OnboardingBrandIcons =
    remember { onboardBrandIcons() }

/**
 * [AndroidOnboardingJwLogin] 的组合期版本。
 *
 * `Graph.casSession` / `Graph.credentialVault` 都是进程级单例（`Graph` 里各自 synchronized），
 * 这里 `remember` 只是省掉每次重组的两跳查表；换成不 remember 也不会建出第二份。
 */
@Composable
internal fun rememberOnboardingJwLogin(): OnboardingJwLogin {
    val context = LocalContext.current
    return remember(context) {
        AndroidOnboardingJwLogin(
            cas = Graph.casSession(context),
            vault = Graph.credentialVault(context),
        )
    }
}
