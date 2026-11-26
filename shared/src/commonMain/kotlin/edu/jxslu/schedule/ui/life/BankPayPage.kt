package edu.jxslu.schedule.ui.life

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import edu.jxslu.schedule.data.jw.JwUrls
import edu.jxslu.schedule.ui.common.pinnedStatusBars
import edu.jxslu.schedule.ui.icon.AppIcons
import edu.jxslu.schedule.ui.web.WebContentView
import edu.jxslu.schedule.ui.web.WebViewController

/**
 * 平台收银台的内嵌页（一卡通 / 电费共用；iOS 移植 2026-11-26）。
 *
 * ## 为什么共享层需要这一页
 *
 * Android 的两条支付链各自有宿主：电费是 `:app` 的 `PowerBankPayScreen`（`android.webkit`
 * + 注入 `AndroidFunc` 桥），一卡通是让系统浏览器拉开收银台。iOS 没有第二套浏览器可控，
 * 而 `design/ios-port.md` §10.4 已经论证过一卡通**不能**在原生侧直拉 `weixin://`
 * （商户域名与 referer 对不上，微信会拒）——唯一能走的是「把收银台 URL 放进
 * `WKWebView`，让页面自己跳微信」。所以共享层要有一页 `WebContentView` 的容器。
 *
 * ## 身份边界（与 Android 收银台页同一条，不许越）
 *
 * 本页只当容器：**不注入脚本、不读页面输入、不落盘**。只有加载 / 刷新 / 重试 /
 * 把非 http(s) 导航交给系统。卡号、手机号、短信验证码、支付密码全部只进对方页面
 * （口径见 `design/04e-modules-services.md` §4.24）。也**不要**在这里加输入框。
 *
 * ## 与 Android `PowerBankPayScreen` 的差异（都是能力差异，不是选择）
 *
 * | 项 | Android 那页 | 本页 |
 * |---|---|---|
 * | 农行支付的原生桥（`window.AndroidFunc.SynObject`） | 有（`CashierBridge`），选了「农行缴电费专用」才完整 | **没有**。iOS 侧不做 JS 桥（任务口径 + `WKScriptMessageHandler` 要另写一节），所以那一档可能停在「加载中」；校园卡 / 电子帐户两种支付方式不经这座桥 |
 * | UA / 三方 Cookie / 缩放 | 页内自己配（移动 Chrome UA + 允许三方 Cookie） | 用 `WebContentView` 那条缝的默认配置（JS、DOM Storage 开；UA 是系统默认） |
 * | 进度条 | 页面级进度（`onProgressChanged`） | 只有「首帧前转圈」，缝里没有进度事件 |
 * | 日志 | `PowerPayWeb` 脱敏日志 | 无（iOS 侧没有对应 logcat 通道） |
 *
 * 差异的后果写在 `design/ios-port.md` §10.4：iOS 上「电费 → 农行卡」这一档可能卡在
 * 加载中，校园卡 / 电子帐户两档不受影响；出问题时的兜底是顶栏「用浏览器打开」。
 *
 * ## 用法
 *
 * 挂在根组合的最后一个子节点（整屏、无背景时的透明点击穿透不成立，所以它自己带
 * [Surface] 铺底）：只有 `onClose` 才收页，收页这件事由宿主决定（宿主还要顺手查单）。
 *
 * @param url 收银台地址（一次性会话：ticket / token 在 query 里，**现取现用，别缓存复用**）。
 * @param onClose 用户点关闭（或宿主想收页）：宿主负责收页并查单。
 * @param title 顶栏标题。别用页面自己的 `<title>`——那通常是主机名或「聚合支付」，
 *   拿来当标题只会让人以为进了浏览器。
 * @param merchantReturnHost 回到商户域的判据 host（电费是 `charge.juwp.edu.cn`）。
 *   **null = 不判**：一卡通收银台本身就在商户域上，判了会把用户从收银台里踢出去。
 * @param onMerchantReturn 落到 [merchantReturnHost] 时回调（宿主据此收页 + 查单）。
 * @param openExternalUrl 非 http(s) 导航与「用浏览器打开」共用的出口；
 *   null = 本平台打不开外部链接，顶栏与失败浮层都不渲染那颗按钮（§11.2 ④）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BankPayPage(
    url: String,
    onClose: () -> Unit,
    title: String = "支付",
    merchantReturnHost: String? = null,
    onMerchantReturn: (() -> Unit)? = null,
    openExternalUrl: ((String) -> Boolean)? = null,
) {
    val controller = remember { WebViewController() }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }

    // 回调每轮组合都可能换（宿主 lambda 不稳定），用 rememberUpdatedState 取当前值，
    // 免得把整组回调每次重组都重装一遍、也免得闭包抓住旧 lambda
    val currentOnClose by rememberUpdatedState(onClose)
    val currentMerchantReturn by rememberUpdatedState(onMerchantReturn)
    val currentOpenExternal by rememberUpdatedState(openExternalUrl)
    val currentMerchantHost by rememberUpdatedState(merchantReturnHost)

    // 事件出口只在组合里装（与 WebViewController 的 KDoc 第 4 条一致：装一次、之后读当前值）
    SideEffect {
        controller.onPageFinished = { finishedUrl ->
            loading = false
            val host = currentMerchantHost
            if (host != null && JwUrls.hostOf(finishedUrl) == host) currentMerchantReturn?.invoke()
        }
        controller.onError = {
            loading = false
            failed = true
        }
        // 非 http(s)（掌银 scheme / intent:// / 微信 / 支付宝）：留在 WebView 里只会报
        // ERR_UNKNOWN_URL_SCHEME。返回值平台侧不读——无论如何都取消这次导航。
        controller.onExternalScheme = { target ->
            currentOpenExternal?.invoke(target) ?: false
        }
    }

    Surface(Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    windowInsets = pinnedStatusBars(),
                    title = { Text(title, maxLines = 1) },
                    navigationIcon = {
                        IconButton(onClick = { currentOnClose() }) {
                            Icon(AppIcons.ArrowLeft01, contentDescription = "关闭")
                        }
                    },
                    actions = {
                        IconButton(onClick = { controller.reload() }) {
                            Icon(AppIcons.Refresh, contentDescription = "刷新")
                        }
                        // 兜底出口：同一个链接交给系统浏览器。收银台页在 WebView 里出任何
                        // 幺蛾子（最典型：农行那一档等原生桥）时，用户都有一条确定能走的路
                        if (currentOpenExternal != null) {
                            IconButton(onClick = { currentOpenExternal?.invoke(url) }) {
                                Icon(AppIcons.Browser, contentDescription = "用浏览器打开")
                            }
                        }
                    },
                )
            },
        ) { padding ->
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                WebContentView(
                    controller = controller,
                    url = url,
                    modifier = Modifier.fillMaxSize(),
                )
                if (loading && !failed) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter),
                    )
                }
                if (failed) {
                    // 失败浮层铺满**内容区**（外层 Box 已经让过顶栏），不重复垫 padding
                    Box(
                        Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp),
                        ) {
                            Text(
                                "支付页没打开（网络或页面改版）",
                                style = MaterialTheme.typography.titleSmall,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "可以重试，或改用系统浏览器打开——付款链接在本次下单里一直有效。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    failed = false
                                    loading = true
                                    controller.reload()
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("重试") }
                            if (currentOpenExternal != null) {
                                Spacer(Modifier.height(8.dp))
                                OutlinedButton(
                                    onClick = { currentOpenExternal?.invoke(url) },
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text("用浏览器打开") }
                            }
                        }
                    }
                }
            }
        }
    }
}
