package edu.jxslu.schedule.ui.jw

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import edu.jxslu.schedule.ui.icon.AppIcons

/**
 * 教务那几页顶栏的「在教务网页核对」动作（iOS 移植 S18，2026-11-25）。
 *
 * Android 上它统一走 `JwImportActivity.startBrowse`：网页窗口与课表导入共用同一套能力
 * （电脑版 / 手机版、统一认证、返回先关弹层、错误态），不再另起一套 WebView。
 *
 * ## 为什么带一个可空回调
 *
 * 本文件在共享层，拿不到 `Context` / `Activity`。回调为 **null 时整只图标不渲染**——
 * 那是「这个平台没有内置浏览窗口」，不是「还没做」（口径见 `design/ios-port.md` §11.2 ④）。
 *
 * ## 为什么 `:app` 里还留着一份同名同形的 `ui/jwvw/JwWebActionButton`
 *
 * 那一份服务的是**仍在 `:app` 的页面**（成绩设置 / 考试设置 / 学业 / 培养方案 / 实习基地
 * 等），它们自己拿得到 `Context`，不必经过回调。等这些页面搬过来，那份就该删掉，
 * 只留这一份——**别把两份并存当成常态**。
 *
 * shortcut: 上面那批页面搬完即删 `:app` 的 `ui/jwvw/JwWebActionButton`；
 * 升级条件 = 引用它的页面全部进 `shared/commonMain`。
 */
@Composable
fun JwWebActionButton(
    title: String,
    url: String,
    /** null = 本平台没有内置浏览窗口，图标不渲染。 */
    onOpenWebPage: ((title: String, url: String) -> Unit)?,
    description: String? = null,
) {
    if (onOpenWebPage == null) return
    IconButton(onClick = { onOpenWebPage(title, url) }) {
        Icon(AppIcons.ExternalLink, contentDescription = description ?: "在教务网页核对$title")
    }
}
