package edu.jxslu.schedule.ui.onboarding

import edu.jxslu.schedule.data.session.CasEnsureResult

/**
 * 首启引导的平台槽位（iOS 移植 S22，2026-11-25）。
 *
 * 这一屏原来在 `:app`，靠 `LocalContext` + `Graph.*` 直接拿 Activity、资源 id 与各仓库。
 * 搬进 `shared/commonMain` 之后，**只有一件事必须由宿主给**：本平台到底有没有这个能力。
 * 按 `design/ios-port.md` §11.2 ④，一律做成可空；null 的语义是「本平台确实没有」，
 * 不是「以后补」的占位——对应入口整块不渲染，绝不给一个点了没反应的按钮。
 *
 * 页面从 `Services.current` 取得到的依赖（偏好、各业务仓库与同步器）不在这里：
 * 那些两端都建得起来，再包一层槽位只是多一条会走偏的路。
 */

/**
 * 第 2 步「学校统一认证」的原生登录能力。
 *
 * ## 为什么两个动作捏在一个接口里，而不是像 `CasLoginDialog` 那样两个函数参数
 *
 * 那两个动作**必须成对出现**：只验不存，自动续登永远拿不到密码（会话一过期就得手登）；
 * 只存不验，会把错密码写进保险箱，之后自动填表反复撞 CAS 失败计数直到账号被停用
 * （`.agents/rules/login-session.md`「先验后存」）。两个各自可空的参数会产生四种组合，
 * 其中两种是错的，而编译器不会说话。一个非空接口 + 一个可空参数只剩「有 / 没有」两种。
 *
 * ## null 的含义
 *
 * **null = 本平台没有原生教务传输层**（iOS：登录只发生在 `WKWebView` 里，没有 OkHttp 会话
 * 也没有 `EncryptedSharedPreferences`）。这时这一步改渲染「去导入页登录」一个按钮，
 * 出口是 [OnboardingScreen] 的 `onOpenJwLogin`；两个都缺时这一步整步不出现。
 */
interface OnboardingJwLogin {

    /**
     * 验证一对凭据。
     *
     * **实现里必须先清失败闸门再登**（Android：`CasSession.onCredentialsUpdated()` 后
     * `tryLogin`）：用户在这一步重新输入等于重新开始计数，不清的话上一次的失败次数
     * 会把这次刚敲对的密码顶掉（`.agents/rules/login-session.md` 第 ⑤ 条）。
     *
     * 取消异常（`CancellationException`）必须原样抛：用户在登录途中退出引导，
     * 这条链不该在后台跑完再落一次库。
     */
    suspend fun verify(username: String, password: String): CasEnsureResult

    /**
     * 验证通过后落库（Android：`CredentialVault.saveCas`）。
     *
     * **只在 [verify] 返回 `Ready` 之后调用**，顺序不能倒——理由见接口 KDoc。
     */
    fun save(username: String, password: String)
}

/**
 * 各步品牌位图的资源 id。
 *
 * ## 为什么是一包 id、而不是 `Painter`
 *
 * 这套位图（`app/src/main/res/drawable/ic_brand_*` 与校徽）是**宿主模块**的素材，
 * 共享层既没有它也不该复制一份；`R` 只有 Android 有，跨模块唯一能传的句柄就是 id。
 * 与 `ui/common/BrandMark` 的既有缝同一形状（那边传入的是单个 id）。
 *
 * ## null 的含义
 *
 * **null = 本平台没有这套素材**（iOS）。图标位**保留原尺寸但不画**——引导每屏的步骤头
 * 都是「60dp 图标 + 标题」，位图消失后留一块与图标等大的空白，标题与表单不挪位；
 * 与 `AccountHeroCard` 的「整块不占位」不同，那里省掉头像位不影响阅读顺序，
 * 这里省掉会让每步的标题横向移 74dp，六屏之间还会跳。
 */
data class OnboardingBrandIcons(
    /** 校徽：第 2 步与完成页「学校统一认证」。 */
    val school: Int,
    /**
     * 问渠图标：完成页「其他可选账号 · 问渠」。
     *
     * 注意**第 3 步「一卡通 · 电费」与完成页那一行也用它**——2026-10-24 起的现状就是这样
     * （当初手滑选了 `ic_brand_wenqu`），这里照搬：改图标是产品决定，不该混在移植里顺手改，
     * 否则「行为与视觉不变」这条验收就说不清了。
     */
    val wenqu: Int,
    /** 胖乖生活：第 4 步与完成页。 */
    val pangguai: Int,
    /** 趣智校园：第 5 步与完成页。 */
    val qzxy: Int,
    /** U净：完成页「其他可选账号」。 */
    val ujing: Int,
    /** 快趣出行：完成页「其他可选账号」。 */
    val kqcx: Int,
)
