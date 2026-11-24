package edu.jxslu.schedule.ui.jwvw

import edu.jxslu.schedule.data.jw.JwTeachingEval

/**
 * 教务导入窗口的模式（DESIGN §4.14 / §4.15）。
 *
 * ## 为什么单独一个文件、且在共享层（iOS 移植 V2，2026-11-25）
 *
 * 它原来是 `JwImportScreen.kt` 里的一个顶层枚举，而那个文件整份搬不动（WebView 外壳，
 * 见 `design/ios-port.md` §11.3）。但**模式本身是纯契约**：八个 `:app` 调用点用它选入口
 * （`JwImportActivity` / `MainActivity` / 成绩页 / 选课页 / 账号中心 …），
 * iOS 侧做教务入口时同样要按它分派。所以把它单独拆出来上移，
 * 让「窗口有哪些形态」这件事与「窗口怎么实现」分开。
 *
 * 包名不变，八个调用点的导入语句零改动（`:app` 依赖 `:shared`）。
 */
enum class JwImportMode {
    /** 课表导入：理论 / 实验 / 考试安排，结果写进课表 Course。 */
    Schedule,
    /** 成绩导入：抓全部学期成绩，按学期替换入库（DESIGN §4.15）。 */
    Scores,
    /**
     * 选课（DESIGN §4.35）：登录后落到学生选课中心 `/jsxsd/xsxk/xklc_list`，
     * **只当浏览器用**——选课/退课操作由用户在教务页面上自己完成，App 不代选、
     * 不注入、不写库。没有导入动作，底部只给「回到选课中心」一个入口。
     */
    Selection,
    /**
     * 学生评教（DESIGN §4.44）：登录后落教务主页，注入「取 token 再跳」
     * （[JwTeachingEval]）到评教平台（`jiaowupj.juwp.edu.cn` 的 SPA）。
     * **只当浏览器用第二例**——评教全部由用户在平台上自己完成，App 不代评、
     * 不提交任何接口；没有导入动作，底部不出现任何按钮。
     */
    TeachingEval,
    /**
     * 只读网页核对：从各业务页顶部的「网页」图标进来，登录后直接打开目标页。
     * **只当浏览器用第三例**——没有导入动作、不写库，复用本窗口的电脑版预览、
     * 统一认证、返回语义与错误态。
     */
    Browse,
}
