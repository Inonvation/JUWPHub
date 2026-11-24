package edu.jxslu.schedule.ui.jw

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import edu.jxslu.schedule.JwImportActivity

/**
 * 教务那几页要宿主代劳的平台事（iOS 移植 S18，2026-11-25）。
 *
 * `ui/jw` 的页面已搬进 `shared/commonMain`，那里拿不到 `Context` / `Activity`。
 * 原来内联在页面里的两件平台动作收成这组回调，形状照 `WeekScreenPlatform.kt`：
 *
 * | 字段 | Android 实现 |
 * |---|---|
 * | [openWebPage] | `JwImportActivity.startBrowse`——与课表导入共用同一个浏览窗口（电脑版 / 手机版、统一认证、返回先关弹层） |
 * | [openJwImport] | `JwImportActivity.start`——导入窗口本身 |
 *
 * 两项都**只在这一层**出现，共享层的参数可空：iOS 侧没有二级页栈也没有这个浏览窗口，
 * 那边传 null，对应入口整行不渲染（口径见 `design/ios-port.md` §11.2 ④）。
 *
 * 为什么两件事不合成一个 `openUrl(url)`：导入窗口与「在教务网页核对」在 Android 上是
 * 同一套 WebView 的两个入口模式，但**用户意图不同**——前者是「拉数据」，后者是「看页面」，
 * 空态文案与文案里的动词都不一样，合成一个反而要在共享层再分叉。
 */
class JwScreenPlatformActions(
    /** 在内置浏览窗口打开一个教务 / 门户页面。 */
    val openWebPage: (title: String, url: String) -> Unit,
    /** 打开教务导入窗口。 */
    val openJwImport: () -> Unit,
)

@Composable
fun rememberJwScreenPlatformActions(): JwScreenPlatformActions {
    val context = LocalContext.current
    // `JwImportActivity` 的两个入口都是静态方法，拿 Activity 起 Intent 不持有引用，
    // 所以这里按 context 记忆即可（换 Activity 时重建，避免用旧 context 起 Intent）。
    return remember(context) {
        JwScreenPlatformActions(
            openWebPage = { title, url -> JwImportActivity.startBrowse(context, title, url) },
            openJwImport = { JwImportActivity.start(context) },
        )
    }
}
