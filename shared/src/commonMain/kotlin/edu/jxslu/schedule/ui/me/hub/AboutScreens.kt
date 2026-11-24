package edu.jxslu.schedule.ui.me.hub

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import edu.jxslu.schedule.data.prefs.NoticeConsentRecord
import edu.jxslu.schedule.data.repo.UpdateCheckClient
import edu.jxslu.schedule.data.repo.UpdateCheckResult
import edu.jxslu.schedule.domain.Disclaimer
import edu.jxslu.schedule.domain.FirstRunNotice
import edu.jxslu.schedule.domain.FirstRunNotices
import edu.jxslu.schedule.domain.NoticeConsent
import edu.jxslu.schedule.domain.RechargeDisclaimer
import edu.jxslu.schedule.domain.UpdateManifest
import edu.jxslu.schedule.platform.Services
import edu.jxslu.schedule.platform.collectAsStateWithLifecycle
import edu.jxslu.schedule.ui.common.AppNoticeVisuals
import edu.jxslu.schedule.ui.common.AppSnackbarHost
import edu.jxslu.schedule.ui.common.NoticeTone
import edu.jxslu.schedule.ui.common.SettingItem
import edu.jxslu.schedule.ui.common.SettingSwitchRow
import edu.jxslu.schedule.ui.common.SettingsSection
import edu.jxslu.schedule.ui.common.showNotice
import edu.jxslu.schedule.ui.icon.AppIcons
import kotlinx.coroutines.launch

/** 公开仓库地址（GPL-3.0）；「开源仓库」点击后经系统浏览器打开。 */
private const val REPO_URL = "https://github.com/Inonvation/JUWPHub"

/**
 * 设置 → 关于 → 关于与更新：版本、手动 / 自动检查更新、开源仓库与许可。
 *
 * ## iOS 移植 W1（2026-11-25）搬进 `commonMain`
 *
 * 三件平台物各走各的缝：
 *
 * | 原来 | 现在 | null 的后果 |
 * |---|---|---|
 * | `BuildConfig.VERSION_NAME` | `Services.current.appVersion()`（两端装配根各给一份） | 空串，页面显示 `v—` |
 * | `openUrl(context, url)`（`ACTION_VIEW` + 窗口过渡） | [onOpenUrl] 槽位 | 「开源仓库」整行不渲染 |
 * | `LicensesDialog`（仍在 `:app` 的 `ui/common/LegalDialogs.kt`） | [licensesDialog] 槽位 | 「开源许可」整行不渲染 |
 *
 * 后两项都是「本平台有没有这个能力」而不是「以后补」：iOS 没有 `ACTION_VIEW` 这套窗口事务，
 * 许可弹窗也要另写一份（`design/ios-port.md` §11.2 ④）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutUpdateScreen(
    onBack: () -> Unit,
    /** 打开站外链接；返回 null = 成功，非 null = 失败文案（与 [UpdateAvailableDialog] 同口径）。 */
    onOpenUrl: ((url: String) -> String?)? = null,
    /** 开源许可弹窗；null = 本平台没有这个弹窗，入口整行不渲染。 */
    licensesDialog: (@Composable (onDismiss: () -> Unit) -> Unit)? = null,
) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val repo = remember { Services.current.scheduleRepository() }
    val versionName = remember { Services.current.appVersion() }
    // 可空初值 = 「还没读到」：**不要**在组合期 runBlocking 读 DataStore（2026-11-06 审计 §3.7）。
    // 也不给 Boolean 默认值当首帧——那会让开过自动检查的用户先看到开关是关的、再跳成开，
    // 正是当初加 runBlocking 要躲的那一下；现在改成「没读到就整行不渲染」，永不显示错值。
    val autoUpdateCheck by remember { repo.autoUpdateCheckEnabled }
        .collectAsStateWithLifecycle(initialValue = null)
    var showLicenses by remember { mutableStateOf(false) }
    val showNotice: (String) -> Unit = { message ->
        scope.launch {
            snackbar.showNotice(AppNoticeVisuals(message, tone = NoticeTone.Warning))
        }
    }

    val updateClient = remember { UpdateCheckClient(versionName) }
    var updateChecking by remember { mutableStateOf(false) }
    var updateAvailable by remember { mutableStateOf<UpdateManifest?>(null) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    fun checkForUpdate() {
        if (updateChecking) return
        updateChecking = true
        updateAvailable = null
        scope.launch {
            val result = updateClient.check()
            updateChecking = false
            when (result) {
                is UpdateCheckResult.UpdateAvailable -> {
                    updateAvailable = result.latest
                    showUpdateDialog = true
                }
                is UpdateCheckResult.UpToDate -> scope.launch {
                    snackbar.showNotice(
                        AppNoticeVisuals(
                            "当前 v$versionName 已是最新版本",
                            tone = NoticeTone.Success,
                        ),
                    )
                }
                is UpdateCheckResult.Failed -> showNotice("检查失败：网络不可用或更新清单暂时无法访问，稍后再试")
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets.statusBars,
                title = { Text("关于与更新") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        snackbarHost = { AppSnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SettingsSection(title = "应用") {
                SettingItem(
                    title = "关于 JUWPHub",
                    subtitle = "非学校官方应用 · 教务与第三方接口风险自负",
                    icon = AppIcons.InformationCircle,
                    value = if (versionName.isBlank()) "v—" else "v$versionName",
                    showArrow = false,
                )
                SettingItem(
                    title = "检查更新",
                    subtitle = when {
                        updateChecking -> "正在检查…"
                        updateAvailable != null ->
                            "发现新版本 v${updateAvailable!!.version} · 点此查看"
                        else -> "从发布仓库检查新版本 · 可选国内镜像下载"
                    },
                    icon = AppIcons.Refresh,
                    enabled = !updateChecking,
                    onClick = {
                        if (updateAvailable != null) showUpdateDialog = true else checkForUpdate()
                    },
                )
                // 首帧未就绪（null）时这一行不渲染：宁可少一行一帧，也不让开关先显示错的
                // 状态再跳（见上方 autoUpdateCheck 的注释）。
                autoUpdateCheck?.let { checked ->
                    SettingSwitchRow(
                        title = "自动检查更新",
                        subtitle = "启动时静默检查新版本；发现更新弹窗提醒，可忽略此版本",
                        checked = checked,
                        onCheckedChange = { value ->
                            scope.launch { repo.setAutoUpdateCheckEnabled(value) }
                        },
                        icon = AppIcons.SystemUpdate01,
                    )
                }
            }

            SettingsSection(title = "开源") {
                // 打不开链接的平台（没有 ACTION_VIEW 等价物）不给这一行
                onOpenUrl?.let { open ->
                    SettingItem(
                        title = "开源仓库",
                        subtitle = "github.com/Inonvation/JUWPHub",
                        icon = AppIcons.Github,
                        onClick = { open(REPO_URL)?.let(showNotice) },
                    )
                }
                if (licensesDialog != null) {
                    SettingItem(
                        title = "开源许可",
                        subtitle = "本应用 GPL-3.0 · 第三方组件与参考项目",
                        icon = AppIcons.SoftwareLicense,
                        onClick = { showLicenses = true },
                    )
                }
            }
        }
    }

    if (showLicenses) {
        licensesDialog?.invoke({ showLicenses = false })
    }
    if (showUpdateDialog) {
        updateAvailable?.let { manifest ->
            UpdateAvailableDialog(
                manifest = manifest,
                onDismiss = { showUpdateDialog = false },
                onOpenUrl = { url ->
                    // 打不开链接的平台在这里给一句确定的话，而不是让按钮点了没反应
                    onOpenUrl?.invoke(url) ?: "本平台不支持打开外部链接"
                },
                onIgnore = {
                    showUpdateDialog = false
                    scope.launch { repo.setIgnoredUpdateVersion(manifest.version) }
                    scope.launch {
                        snackbar.showNotice(
                            AppNoticeVisuals(
                                "已忽略该版本，启动时不再自动提醒",
                                tone = NoticeTone.Success,
                            ),
                        )
                    }
                },
            )
        }
    }
}

/**
 * 设置 → 关于 → 条款与声明：用户须知、免责声明与充值风险。
 *
 * ## iOS 移植 W1（2026-11-25）搬进 `commonMain`
 *
 * 三条正文（[Disclaimer] / [FirstRunNotices] / [RechargeDisclaimer]）本来就在共享层，
 * 缺的只是弹窗外壳——`NoticeDialog` 仍在 `:app` 的 `ui/common/LegalDialogs.kt`
 * （那个包本轮不在可改范围），所以按 `design/ios-port.md` §11.2 ④ 收成 [noticeDialog] 槽位。
 *
 * **null = 本平台没有条款弹窗**：此时「条款」整节不渲染——三条入口点下去都是弹窗，
 * 留着就是三个点了没反应的死入口。宿主若给不出弹窗，就不该把这一页放进路由。
 *
 * @param noticeDialog 通用条款弹窗（标题 + 导语 + 条目 + 关闭回调）。
 *   免责声明就是同一个形状，所以不再单开一个槽（`DisclaimerDialog` 内部也只是把
 *   `Disclaimer.INTRO` / `Disclaimer.ITEMS` 喂给 `NoticeDialog`）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TermsDeclarationsScreen(
    onBack: () -> Unit,
    noticeDialog: (
        @Composable (
            title: String,
            intro: String,
            items: List<String>,
            onDismiss: () -> Unit,
        ) -> Unit
    )? = null,
) {
    val scope = rememberCoroutineScope()
    val prefs = remember { Services.current.displayPrefs() }
    val userNoticeConsent by remember {
        prefs.noticeConsent(FirstRunNotice.UserNotice)
    }.collectAsStateWithLifecycle(initialValue = NoticeConsentRecord(version = 0, atMs = 0L))
    val userNoticeConfirmed =
        NoticeConsent.isConsented(userNoticeConsent.version, FirstRunNotices.VERSION)
    var showDisclaimer by remember { mutableStateOf(false) }
    var showUserNotice by remember { mutableStateOf(false) }
    var showRechargeNotice by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets.statusBars,
                title = { Text("条款与声明") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (noticeDialog != null) {
                SettingsSection(title = "条款") {
                    SettingItem(
                        title = "用户须知",
                        subtitle = "非官方 · 数据只存本机 · 第三方接口可能随时失效",
                        icon = AppIcons.Megaphone01,
                        value = if (userNoticeConfirmed) "已确认" else "未确认",
                        onClick = { showUserNotice = true },
                    )
                    SettingItem(
                        title = "免责声明",
                        subtitle = "非学校官方应用 · 第三方接口风险 · 仅供学习",
                        icon = AppIcons.Agreement01,
                        onClick = { showDisclaimer = true },
                    )
                    SettingItem(
                        title = "充值风险提醒",
                        subtitle = "电费与校园卡充值的风险提示全文",
                        icon = AppIcons.Alert02,
                        onClick = { showRechargeNotice = true },
                    )
                }
            }
        }
    }

    if (showDisclaimer && noticeDialog != null) {
        noticeDialog.invoke(
            "免责声明",
            Disclaimer.INTRO,
            Disclaimer.ITEMS,
            { showDisclaimer = false },
        )
    }
    if (showUserNotice && noticeDialog != null) {
        noticeDialog.invoke(
            FirstRunNotices.title(FirstRunNotice.UserNotice),
            FirstRunNotices.intro(FirstRunNotice.UserNotice),
            FirstRunNotices.items(FirstRunNotice.UserNotice),
            {
                showUserNotice = false
                scope.launch {
                    prefs.markNoticeConsented(FirstRunNotice.UserNotice, FirstRunNotices.VERSION)
                }
            },
        )
    }
    if (showRechargeNotice && noticeDialog != null) {
        noticeDialog.invoke(
            "充值风险提醒",
            "以下三条在每个充值入口都会再提醒一次；这里是随时可查的全文。",
            RechargeDisclaimer.ITEMS,
            { showRechargeNotice = false },
        )
    }
}
