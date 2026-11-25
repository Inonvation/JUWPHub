package edu.jxslu.schedule.ui.jwvw

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import edu.jxslu.schedule.Graph
import edu.jxslu.schedule.data.jw.JwAutoLogin
import kotlinx.coroutines.launch

/**
 * 学生评教窗口在 Android 侧的宿主（2026-11-26 评教搬进 `shared/commonMain` 之后）。
 *
 * ## 为什么这两个回调在 `:app`
 *
 * 共享层的 [TeachingEvalScreen] 按 `design/ios-port.md` §11.2 ④ 把两件平台动作收成可空回调：
 * 自动填表要**密码**（`CredentialVault` 只在平台侧）、会话回灌要 `CasSession`（装配在
 * `Graph` 里）。这里把它们接上，一个不落地交给页面。
 *
 * ## 凭证为什么在这里读一次
 *
 * 与搬移前的导入窗口同一口径（那边也是进窗读一次 `savedCas`）：**不要在每页加载里重复读
 * 加密存储**，也不要把密码留在闭包之外。读到的脚本整段只含「填表 + 提交」，凭证明文只在
 * 这条字符串里从保险箱直接拼进 JS，不经共享层的 `AccountCredentials`（那边按设计只回学号）。
 *
 * 读不到凭证时给 `null`（而不是给一个永远返回 null 的 lambda）：共享层据此把
 * 「自动填表」和「会话失效自动重登」两条**一起**关掉——没有密码，回预热入口重走一遍认证
 * 只会多一次 CAS 往返，最后仍要用户手输。
 */
@Composable
fun TeachingEvalHost(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cas = remember { Graph.casSession(context) }
    val script = remember {
        Graph.credentialVault(context).readCas()
            ?.let { cred -> JwAutoLogin.fillJs(cred.username, cred.password) }
    }

    TeachingEvalScreen(
        onBack = onBack,
        // 读不到凭证就传 null：共享层据此把「自动填表」与「会话失效自动重登」一起关掉
        casFillScript = if (script == null) null else ({ script }),
        // 网页里登出来的那份会话才是最新的（服务端可能在这期间轮换过）：抄回会话层，
        // 关掉评教窗口之后成绩 / 课表的同步就不必再登一次。失败不打断用户——评教本身不受影响。
        onJwSessionReady = {
            scope.launch { runCatching { cas.adoptFromWebView() } }
        },
    )
}
