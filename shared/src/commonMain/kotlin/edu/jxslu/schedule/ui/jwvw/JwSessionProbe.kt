package edu.jxslu.schedule.ui.jwvw

/**
 * 教务页「登录态就位没有」的 DOM 探针（2026-11-26 从 `:app` 的 `JwImportScreen` 上移）。
 *
 * ## 为什么不能按 URL 判
 *
 * 未登录访问 `xsMainV` / `xskb_list` 时，教务**不跳转**，而是以 HTTP 200 把登录页
 * **就地渲染在同一个 URL 上**（`#loginDiv` + `type=password` 输入框）。所以「URL 是教务主页」
 * 完全不能说明会话可用；按 URL 自动跳课表会与课表页的登录页互跳成环（实测 18 次导航）。
 *
 * ## 三条特征都是实测来的（2026-09-18，手机 5G 无代理）
 *
 * - 未登录的 `xsMainV` / `xskb_list` / `Logon.do`：含 `id="loginDiv"` 与密码框，
 *   `<title>登录</title>`；已登录的课表 / 主页快照里这两者都是 0 处。
 * - 账号在别处登录（强智单会话）：回一段**裸 JSON**
 *   `{"flag1":3,"msgContent":"您的账号在其它地方登录"}`——页面很小，登录页那两个特征都不出现，
 *   不单独认就会被当成正常页一路放行。
 *
 * ⚠️ 不要改回按文案判（曾用「用户没有登录」，而该页面上此文案出现 **0 次**，判据永不命中）。
 * `conflict` token 与 [edu.jxslu.schedule.data.jw.JwImportDiagnosis.SESSION_CONFLICT_TOKEN]
 * 逐字对齐，两边别各改一份。
 *
 * 调用方的判据在 [edu.jxslu.schedule.data.jw.JwImportDiagnosis.looksLikeLoginPage] /
 * `looksLikeSessionConflict`，本文件只负责页面侧的特征采集。
 */
object JwSessionProbe {

    /** 注入到教务页上跑的探针；返回一行特征串（解析见 [edu.jxslu.schedule.data.jw.JwImportDiagnosis]）。 */
    val SCRIPT: String = """
(function(){
  try{
    var hasLoginDiv = !!document.getElementById('loginDiv');
    var hasPwd = !!document.querySelector('input[type=password]');
    var title = (document.title||'').trim();
    var text = (document.body && document.body.textContent) || '';
    var conflict = text.length < 4000 &&
        text.indexOf('其它地方登录') >= 0 && text.indexOf('flag1') >= 0;
    return (conflict?'conflict ':'') + (hasLoginDiv?'loginDiv ':'') + (hasPwd?'password ':'') + 'title=' + title;
  }catch(e){ return ''; }
})()
""".trimIndent()
}
