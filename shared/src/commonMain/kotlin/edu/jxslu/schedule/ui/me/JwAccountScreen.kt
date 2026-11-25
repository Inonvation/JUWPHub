package edu.jxslu.schedule.ui.me

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import edu.jxslu.schedule.data.session.LoginState
import edu.jxslu.schedule.data.session.LoginStateRules
import edu.jxslu.schedule.data.session.LoginTarget
import edu.jxslu.schedule.data.session.SessionStatus
import edu.jxslu.schedule.platform.Services
import edu.jxslu.schedule.platform.collectAsStateWithLifecycle
import edu.jxslu.schedule.ui.common.AccountCard
import edu.jxslu.schedule.ui.common.AppConfirmDialog
import edu.jxslu.schedule.ui.common.AppSnackbarHost
import edu.jxslu.schedule.ui.common.CasLoginDialog
import edu.jxslu.schedule.ui.common.SettingItem
import edu.jxslu.schedule.ui.common.SettingsSection
import edu.jxslu.schedule.ui.common.rememberResumeTick
import edu.jxslu.schedule.ui.icon.AppIcons
import edu.jxslu.schedule.ui.jwvw.JwImportOutcomeEffect
import kotlinx.coroutines.launch

/**
 * 教务账户页（DESIGN §3.3）。
 *
 * 为什么要有它：「我的」页的教务那一行原先一点就开教务导入的 WebView 窗口——而隔壁
 * 「一卡通」点进去是原生的账户页。同样是一行账户，行为不一致；而且用户想看的是
 * 「我是谁、这个账号现在什么状态」，不是立刻跳进一个网页。
 *
 * 这里只展示与账号相关的只读信息 + 几个明确动作（保存账号密码 / 更新密码 / 导入 / 退出），
 * 具体取数交给各自的页面。
 *
 * ## iOS 移植 W1（2026-11-25）搬进 `commonMain`
 *
 * 三件平台物各走各的缝，不再从 `Graph` 直取：
 *
 * | 原来 | 现在 |
 * |---|---|
 * | `Graph.credentialVault(context).readCas()` | `Services.current.accountCredentials()?.readJwUsername()`（null = 本平台没有加密凭证存储） |
 * | `Graph.casSession(context).logout()` | `Services.current.jwSessionLogout()`（null = 没有原生教务会话） |
 * | `WebViewCookieBridge.hasAnyCookie()` | `Services.current.hasJwWebSession()`（null = 判断不了，按「没有」算） |
 * | `Graph.dormRepairStore(context).clear()` | `Services.current.dormRepairStore()` |
 * | `OnboardingActivity` / `JwImportActivity` / `TranscriptActivity` | 可空回调，null = 入口整行不渲染（§11.2 ④） |
 *
 * 2026-11-25 补「保存账号密码」：两端现在都能存教务凭证（Android 加密 prefs / iOS Keychain），
 * 所以这一格用共享层的 [CasLoginDialog]（先验后存、错误分类、冷却都是既有的那一份）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JwAccountScreen(
    onBack: () -> Unit,
    /** 「我的」页的共享 ViewModel，**由宿主创建**（`design/ios-port.md` §11.1）。 */
    viewModel: MeViewModel,
    /** 打开引导页重填统一认证账号密码；null = 本平台没有那个窗口。 */
    onUpdatePassword: (() -> Unit)? = null,
    /** 打开教务课表导入窗口。 */
    onImportTimetable: (() -> Unit)? = null,
    /** 打开教务成绩导入窗口。 */
    onImportScores: (() -> Unit)? = null,
    /** 打开盖章成绩单出单窗口。 */
    onExportTranscript: (() -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    val prefs = remember { Services.current.displayPrefs() }
    val credentials = remember { Services.current.accountCredentials() }
    val jwLogout = remember { Services.current.jwSessionLogout() }
    val snackbar = remember { SnackbarHostState() }

    // 本页有两个教务导入入口（课表 / 成绩），导入窗口 finish 后落回的就是这一页
    JwImportOutcomeEffect(snackbar)

    // 登录态快照只在组合期读一次：从引导页（更新账号密码）回来时组合不重建，
    // 用「回到前台」计数当 key 重读，否则卡片会停在旧状态（2026-09-27）
    val resumeTick = rememberResumeTick()
    // 保存成功后要就地刷新这一格（不重建组合），所以是可变状态而不是 val
    var casUsername by remember(resumeTick) { mutableStateOf(credentials?.readJwUsername()) }
    val profileName by prefs.profileName.collectAsStateWithLifecycle(initialValue = "")
    val profileClass by prefs.profileClass.collectAsStateWithLifecycle(initialValue = "")
    val profileStudentId by prefs.profileStudentId.collectAsStateWithLifecycle(initialValue = "")
    val suspendedTargets by SessionStatus.suspended.collectAsStateWithLifecycle()
    // 升级用户没存凭证、但 WebView 里可能还有会话：只读本机 cookie，不联网。
    // `hasJwWebSession()` 为 null（本平台判断不了，iOS 的 cookie 只能异步读）时按「没有」算。
    val webSession = remember(resumeTick) { Services.current.hasJwWebSession() ?: false }
    val jwState = LoginStateRules.derive(
        credentialExists = casUsername != null,
        webSessionExists = webSession,
        suspended = LoginTarget.Jw in suspendedTargets,
    )

    val state by viewModel.uiState.collectAsStateWithLifecycle()

    var confirmLogout by remember { mutableStateOf(false) }
    var showSaveCas by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets.statusBars,
                title = { Text("学校统一认证") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        // 本页 contentWindowInsets 归零，Scaffold 不替提示条让开手势条，这里自己垫
        snackbarHost = { AppSnackbarHost(snackbar, Modifier.navigationBarsPadding()) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AccountCard(
                username = casUsername ?: profileStudentId,
                name = profileName,
                subtitle = profileClass,
                statusText = when (jwState) {
                    // 没存密码也进了登录态 = 靠 WebView 用出来的网页会话（DESIGN §3.16）。
                    // 这两种「已登录」的后续行为不同，文案要分开，否则用户以为已经能自动续登。
                    LoginState.LoggedIn -> if (casUsername != null) {
                        "已登录 · 会话过期时自动重新登录"
                    } else {
                        "已登录 · 网页会话；填一次账号密码可自动续登"
                    }
                    LoginState.Expired -> "登录状态已失效，请更新账号密码"
                    LoginState.NotLoggedIn -> "未登录 · 没有保存密码"
                },
                statusState = jwState,
            )

            // 「自动续登」只有真能存凭据的平台才出现（`accountCredentials()` 为 null 时整节不渲染）
            if (credentials != null) {
                SettingsSection(title = "自动续登") {
                    SettingItem(
                        title = "保存账号密码",
                        subtitle = if (casUsername == null) {
                            "填一次学号与统一认证密码，会话过期时自动重新登录"
                        } else {
                            "已保存；学校改过密码后在这里重填"
                        },
                        icon = AppIcons.LockPassword,
                        onClick = { showSaveCas = true },
                    )
                }
            }

            SettingsSection(title = "学业信息") {
                InfoRow("当前学期", state.semester?.startDate?.let { "$it 起" } ?: "未配置")
                // 0 = 今天不在这张课表的学期内（未开学 / 已结束 / 未配置），不是「还没开学」
                InfoRow("教学周", if (state.currentWeek > 0) "第 ${state.currentWeek} 周" else "不在学期内")
                InfoRow("课表", "${state.timetableName.ifBlank { "—" }} · ${state.courseCount} 门课")
            }

            // 四个入口都是平台窗口：本平台没有的那个整行不渲染（§11.2 ④）。
            // 四个都没有时整节不渲染，免得留一张空卡。
            val hasAnyAction = onUpdatePassword != null || onImportTimetable != null ||
                onImportScores != null || onExportTranscript != null
            if (hasAnyAction) {
                SettingsSection(title = "账号与数据") {
                    onUpdatePassword?.let { action ->
                        SettingItem(
                            title = "更新账号密码",
                            subtitle = "学校改过密码后在这里重填，自动续登立即恢复",
                            icon = AppIcons.Settings01,
                            onClick = action,
                        )
                    }
                    onImportTimetable?.let { action ->
                        SettingItem(
                            title = "导入课表",
                            subtitle = "打开教务学期课表页并导入",
                            icon = AppIcons.Book01,
                            onClick = action,
                        )
                    }
                    onImportScores?.let { action ->
                        SettingItem(
                            title = "导入成绩",
                            subtitle = "抓全部学期成绩，按学期替换",
                            icon = AppIcons.GridView,
                            onClick = action,
                        )
                    }
                    onExportTranscript?.let { action ->
                        SettingItem(
                            title = "导出盖章成绩单",
                            subtitle = "签章系统出单，含教务处章",
                            icon = AppIcons.CalendarSetting01,
                            onClick = action,
                        )
                    }
                }
            }

            // 退出 = 清原生会话；本平台没有原生会话（iOS 登录只在 WKWebView 里）时不给这个入口
            if (jwLogout != null) {
                OutlinedButton(
                    onClick = { confirmLogout = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("退出登录并清除密码")
                }
            }
            if (casUsername == null) {
                Text(
                    "当前没有保存密码：导入课表时需要手动登录，也不会自动续登。" +
                        "在上面填一次学号密码即可自动续登。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
        }
    }

    // 保存凭据走共享层那个前置登录弹层：**先经 `verifyJw` 验证、通过才落库**
    // （错密码存进来会让自动续登反复撞 CAS 失败计数直到账号停用）。
    // 弹层的错误分类、3~5 秒冷却、取消异常穿透都是既有的，别在这里另写一套表单。
    if (showSaveCas && credentials != null) {
        CasLoginDialog(
            title = "保存教务账号密码",
            description = "填一次学号与统一认证密码，密码只存本机加密存储；" +
                "之后会话过期时 App 会自己重新登录，不必再进导入页手登。",
            onVerify = { username, password -> credentials.verifyJw(username, password) },
            onSave = { username, password -> credentials.saveJw(username, password) },
            onSaved = {
                showSaveCas = false
                casUsername = credentials.readJwUsername() ?: casUsername
                scope.launch { snackbar.showSnackbar("已保存：会话过期时会自动重新登录") }
            },
            onSkip = { showSaveCas = false },
        )
    }

    if (confirmLogout && jwLogout != null) {
        AppConfirmDialog(
            title = "退出教务登录？",
            message = "将清除本机保存的学号与统一认证密码，自动续登随之失效。" +
                "已导入的课表、成绩不受影响。",
            confirmLabel = "退出并清除",
            danger = true,
            onConfirm = {
                confirmLogout = false
                // logout 是挂起调用：清 WebView cookie 要切主线程（桥内部处理）。
                // 清完再返回，状态卡重读时看到的才是真的「未登录」
                scope.launch {
                    jwLogout.logout()
                    // 换账号后不要把上一位用户的报修记录留在今日页（DESIGN §4.49）
                    Services.current.dormRepairStore().clear()
                    onBack()
                }
            },
            onDismiss = { confirmLogout = false },
        )
    }
}

/** 一行「标签 —— 值」，学业信息用。 */
@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        )
        Spacer(Modifier.weight(1f))
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}
