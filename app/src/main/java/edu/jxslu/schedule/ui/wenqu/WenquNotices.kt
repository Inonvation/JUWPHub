package edu.jxslu.schedule.ui.wenqu

import edu.jxslu.schedule.ui.common.NoticeFeedback
import edu.jxslu.schedule.ui.common.NoticeTone

/**
 * 页面级一次性提示的构造入口（三个问渠 ViewModel 共用）。
 *
 * 为什么要有语气这一维（2026-11-06 审计 §3.8）：改之前三个 VM 的 `message` 都是裸
 * `String?`，页面统一 `snackbar.showNotice(text)` 落成 [NoticeTone.Info]——于是「取数失败」
 * 「登录失败」「请输入密码」与「报名成功」「验证码已发送」长得一模一样，用户分不出
 * 刚才那条是**出事了**还是**做成了**。失败/校验一律显式给 [NoticeTone.Warning]
 * （全 App 的失败提示口径，见 `CampusCalendarScreen` 的「刷新失败，请检查网络后重试」）。
 *
 * 默认 [NoticeTone.Info] 是给播报类留的省事路径：**成功播报保持 Info**，别顺手改成
 * Success——那会把用户已经习惯的成功提示观感整体换一遍，不属于这次要解决的问题。
 */
internal fun wenquNotice(
    text: String,
    tone: NoticeTone = NoticeTone.Info,
): NoticeFeedback = NoticeFeedback(text, tone)

/**
 * 问渠页共用的页尾声明（主页 / 记录 / 设置共用一份，避免四处各写一段）。
 * 蹲点页另有自己的「不自动提交」说明，见 [WENQU_GRAB_FOOTER]。
 *
 * `**...**` 由 `ui/common/emphasizedText` 渲染成加粗。
 */
internal const val WENQU_FOOTER =
    "**问渠为第三方平台（非学校官方功能）**；工时与活动数据以平台为准，" +
        "报名 / 取消报名会直接提交到该平台，签到、提交材料等其它操作请到微信小程序内完成。"

/** 蹲点页：先讲本页行为边界，再补平台归属。 */
internal const val WENQU_GRAB_FOOTER =
    "**蹲点只在打开本页时进行**（离开页面即停止）；命中后仍需你点「确认报名」，本页不会自动提交" +
        "（自动提交只发生在详情里单独开启的「预报名」）。**问渠为第三方平台，非学校官方功能。**"
