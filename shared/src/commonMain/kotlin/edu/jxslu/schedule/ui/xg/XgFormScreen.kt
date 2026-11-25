package edu.jxslu.schedule.ui.xg

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import edu.jxslu.schedule.data.xg.XgForm
import edu.jxslu.schedule.data.xg.XgUrls
import edu.jxslu.schedule.ui.common.PageBackButton
import edu.jxslu.schedule.ui.icon.AppIcons
import edu.jxslu.schedule.ui.web.WebContentView
import edu.jxslu.schedule.ui.web.WebViewController

/**
 * 学工表单窗口（DESIGN §3.15 / §4.26），共享层版本。
 *
 * 报修、请假 / 销假 / 续假共用这一页，差别只有 [form] 里的表单标识。填表、提交、查流程
 * 都由学校官方页面自己完成，App 不代填：申请请求里带着一组服务端每次下发的动态值
 * （`pageEnc` / `traceId` / `nodeUniqueId`），复刻一遍的代价与失效风险都远大于收益
 * （`.agents/rules/xg-transcript.md`）。
 *
 * 起点是统一认证入口 [XgUrls.SSO_LOGIN] 而不是表单页本身，顺序不能反：表单页匿名可访问，
 * 它的登录引导指向超星 passport（手机号 + 学习通密码），先开表单会把人带到一套他从没用过的
 * 账号上。所以「先 `/sfrz/`、落到学工域后再进表单页」这条顺序由本页在
 * [WebViewController.onPageFinished] 里执行一次（判据唯一实现在 [XgUrls.shouldEnterForm]）。
 *
 * ## 与 `:app` 那张页的差别（为什么不共用一个实现）
 *
 * Android 的 `XgFormScreen` 还压着四件本缝（`ui/web/WebViewController`）**给不了的**平台动作，
 * 任何一件都要求动那条缝，而本轮那条缝归别的流：`canGoBack` / `goBack()`（系统返回逐页回退）、
 * `settings.cacheMode` + `clearCache(true)`（首页壳坏缓存自愈）、`onShowFileChooser` 直取
 * `ClipData` 的选文件、`onGeolocationPermissionsShowPrompt` 的定位授权。所以本轮**不删**那张页
 * （删了 Android 会丢附件上传与返回回退），两张页各自保留；接缝补齐之后按
 * `// shortcut:` 的升级条件合回一份。
 *
 * iOS 侧本页给得出的能力按 `design/ios-port.md` §11.4 的现状表：**能开、能填、能提交**；
 * 网页里的上传按钮点了没反应（`onPickFiles` 在 iOS 的 Kotlin/Native 绑定里没有
 * `WKOpenPanelParameters`，见 `WebViewController.ios.kt` 的说明），销假的位置签到同样没有
 * （`onGeolocationPrompt` 未接）。这两处都不在本页另造兜底：造一个点了没反应的按钮更糟。
 *
 * // shortcut: 没有缓存自愈与网页历史回退（接缝缺 goBack / cacheMode）；
 * // 接缝补上这两件、且 Android 的选文件走进 onPickFiles 之后，:app 的 XgFormScreen 删掉、两端共用本页。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun XgFormScreen(form: XgForm, onBack: () -> Unit) {
    // 视图是围着句柄建的，必须留在组合里：每次重组 new 一个等于每次重组重建 WebView。
    val controller = remember { WebViewController() }
    var statusNote by remember(form.id) { mutableStateOf(XgUrls.statusHint(null, form.title)) }
    var loadError by remember(form.id) { mutableStateOf<String?>(null) }
    // 直达表单只做一次：落到学工首页之后还要再跳一层，之后用户自己点回首页不该再被弹进表单。
    var directAttempted by remember(form.id) { mutableStateOf(false) }
    val startUrl = remember(form.id) { XgUrls.SSO_LOGIN }

    // 回调挂在**控制器**上而不是每次重组重装：控制器比这一屏活得久，装一次即可。
    // 内层读的是 remember 的 State 委托，读到的是当前值。
    remember(controller, form) {
        controller.onPageFinished = { url ->
            statusNote = XgUrls.statusHint(url, form.title)
            // 失败页的 onPageFinished 也会到，但 iOS 的 actual 只在主 frame 失败时回调 onError，
            // 到这里说明这一页确实加载成功了，把上一轮的错误条收掉。
            loadError = null
            if (!directAttempted && XgUrls.shouldEnterForm(url)) {
                directAttempted = true
                controller.loadUrl(form.applyUrl)
            }
        }
        controller.onError = { message ->
            loadError = message
            statusNote = message
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = form.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = { PageBackButton(onBack) },
                actions = {
                    IconButton(onClick = {
                        loadError = null
                        controller.reload()
                    }) {
                        Icon(AppIcons.Refresh, contentDescription = "刷新")
                    }
                    // 直达就没有网页历史可退。这张表单若被管理员改过（页面会提示「表单信息不存在」），
                    // 或者用户要去看已提交的流程，得有路回学工首页。
                    IconButton(onClick = {
                        loadError = null
                        controller.loadUrl(XgUrls.HOME)
                    }) {
                        Icon(AppIcons.Home01, contentDescription = "学工首页")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // 状态条与 Android 那张页同高（单行 bodySmall，min 36dp）：提示出现 / 消失不能
            // 改变 WebView 的高度——MIUI 上 resize 会让整面刷白，Android 侧为此刻意用 overlay。
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 36.dp)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = statusNote,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                WebContentView(
                    controller = controller,
                    url = startUrl,
                    modifier = Modifier.fillMaxSize(),
                )
                // 主 frame 失败（断网 / 证书 / HTTP 4xx-5xx）：WebView 自己那张错误页只有一句英文，
                // 这里补一条中文 + 重试。必须是 overlay：出现 / 消失不改变 WebView 尺寸（同上）。
                loadError?.let { message ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(start = 16.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = {
                            loadError = null
                            controller.reload()
                        }) { Text("重试") }
                    }
                }
            }
        }
    }
}
