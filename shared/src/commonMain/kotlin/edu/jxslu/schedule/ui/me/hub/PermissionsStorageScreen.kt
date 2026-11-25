package edu.jxslu.schedule.ui.me.hub

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import edu.jxslu.schedule.domain.SizeText
import edu.jxslu.schedule.ui.common.AppConfirmDialog
import edu.jxslu.schedule.ui.common.AppNoticeVisuals
import edu.jxslu.schedule.ui.common.AppSnackbarHost
import edu.jxslu.schedule.ui.common.NoticeTone
import edu.jxslu.schedule.ui.common.SettingItem
import edu.jxslu.schedule.ui.common.SettingsSection
import edu.jxslu.schedule.ui.common.pinnedStatusBars
import edu.jxslu.schedule.ui.common.showNotice
import edu.jxslu.schedule.ui.icon.AppIcons
import kotlinx.coroutines.launch

/**
 * 设置 → 数据与权限 → 权限与存储（DESIGN §3.12 / §3.3，2026-11-01 合并权限与存储两页）。
 *
 * 权限部分沿用「进页只读检测、点了才申请」的口径，存储部分保留缓存清理与诊断日志。
 *
 * ## 搬进共享层之后的形状（2026-11-25，W1 收尾）
 *
 * 原文件整页在 `:app`，理由是「没有一行是可共享的」。这个判断只对**权限那一半**成立：
 * 四个运行时权限 launcher（`ActivityResultContracts`）、`NotificationManagerCompat`、
 * `WidgetCapabilities` 的电池优化 / 自启动跳转、`AppPermissions` 的相册写入，全是 Android
 * 专有机制，iOS 上要么不存在（电池优化白名单、自启动），要么是另一套授权模型
 * （`UNUserNotificationCenter.requestAuthorization`）。所以权限那一块收成
 * [permissionSections] 槽位，由宿主整块给——`null` = 本平台没有这些入口，整块不渲染
 * （`design/ios-port.md` §11.2 ④）。
 *
 * 页框（顶栏 / 滚动容器 / 一次性提示通道）与「存储与诊断」这一节是两端共同的：
 * 清理缓存 + 导出诊断日志两行的**结构与交互**在这里，一行都不该各写一份（弹窗的
 * 「进行中按不动」「危险色」「关窗与取消是同一件事」这三条口径见
 * `.agents/rules/ui-common.md`）。平台差异（量哪儿、清哪儿、文案怎么说、日志长什么样）
 * 全部由 [StorageDiagnostics] 描述，页面只认它。
 *
 * [StorageDiagnostics] 而不是「可空槽位」：两端**都**给得起这一节（Android 是
 * `AppCacheCleaner` / `CrashReporter`，iOS 是沙箱目录 + `logs/`），给 null 只会让页面
 * 多一条永远走不到的分支。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionsStorageScreen(
    onBack: () -> Unit,
    /** 「存储与诊断」这一节的平台能力，见 [StorageDiagnostics]。 */
    storage: StorageDiagnostics,
    /**
     * 运行时权限那一块（保持后台可用 / 通知 / 日历 / 定位 / 相册）。
     * `null` = 本平台没有这些权限入口。
     */
    permissionSections: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val showNotice: (String) -> Unit = { message ->
        scope.launch {
            snackbar.showNotice(AppNoticeVisuals(message, tone = NoticeTone.Warning))
        }
    }

    // ---- 存储与诊断 ----
    var cacheBytes by remember { mutableStateOf<Long?>(null) }
    var storageLines by remember { mutableStateOf<List<StorageLine>>(emptyList()) }
    var showClearCache by remember { mutableStateOf(false) }
    var clearingCache by remember { mutableStateOf(false) }
    // 诊断日志的条数读盘（Android 侧是 `CrashReporter.listFiles`），只在进页与清完之后取
    // ——与搬移前 `remember(crashRefresh)` 完全相同的一处刷新点
    var diagnosticTick by remember { mutableStateOf(0) }
    val diagnosticCount = remember(diagnosticTick) { storage.diagnostics?.count() ?: 0 }

    LaunchedEffect(Unit) {
        cacheBytes = storage.measureCacheBytes()
        storageLines = storage.storageLines()
    }

    fun runClearCache() {
        if (clearingCache) return
        clearingCache = true
        scope.launch {
            val ok = storage.clearCache()
            // 先重读体积再落状态：搬移前就是这个顺序，「已清理缓存」的提示出现时数字已经是新的
            cacheBytes = storage.measureCacheBytes()
            storageLines = storage.storageLines()
            diagnosticTick++
            clearingCache = false
            showClearCache = false
            scope.launch {
                snackbar.showNotice(
                    AppNoticeVisuals(
                        if (ok) "已清理缓存" else "部分缓存没清干净，可稍后重试",
                        tone = if (ok) NoticeTone.Success else NoticeTone.Warning,
                    ),
                )
            }
        }
    }

    var exportRequested by remember { mutableStateOf(false) }
    LaunchedEffect(exportRequested) {
        if (!exportRequested) return@LaunchedEffect
        exportRequested = false
        val export = storage.diagnostics ?: return@LaunchedEffect
        if (!export.export()) showNotice("无法发起分享")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                windowInsets = pinnedStatusBars(),
                title = { Text("权限与存储") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(AppIcons.ArrowLeft01, contentDescription = "返回")
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
            permissionSections?.invoke(this)

            SettingsSection(title = "存储与诊断") {
                // 存储占用细分（iOS：数据库 / 缓存 / 日志三行）。Android 侧这块并进
                // 「清理缓存」的副标题，这里返回空表，一行都不多渲染。
                storageLines.forEach { line ->
                    SettingItem(
                        title = line.title,
                        subtitle = line.detail,
                        icon = line.icon,
                        value = line.size,
                        showArrow = false,
                    )
                }
                SettingItem(
                    title = "清理缓存",
                    subtitle = buildString {
                        append(storage.cacheScope)
                        cacheBytes?.let { append(" · 共 ").append(SizeText.format(it)) }
                    },
                    icon = AppIcons.Delete02,
                    onClick = { showClearCache = true },
                )
                storage.diagnostics?.let { export ->
                    SettingItem(
                        title = "导出诊断日志",
                        subtitle = export.subtitle(diagnosticCount),
                        icon = AppIcons.Bug01,
                        showArrow = diagnosticCount > 0,
                        onClick = { if (diagnosticCount > 0) exportRequested = true },
                    )
                }
            }
        }
    }

    if (showClearCache) {
        AppConfirmDialog(
            title = "清理缓存？",
            message = storage.confirmMessage(cacheBytes),
            // 清理进行中主键变「清理中…」并连同取消键一起禁用。dismissEnabled = false 时
            // 公共件把点外部/返回键也一并挡掉，与原来 onDismissRequest 里的 if (!clearingCache) 等价
            confirmLabel = if (clearingCache) "清理中…" else "清理",
            confirmEnabled = !clearingCache,
            dismissEnabled = !clearingCache,
            danger = true,
            onConfirm = { runClearCache() },
            onDismiss = { showClearCache = false },
        )
    }
}

/**
 * 「存储占用」的一行（iOS 侧：数据库 / 缓存 / 日志）。
 *
 * 用 [SettingItem] 的纯展示形态渲染（不可点、无箭头），与同一张卡里的两个动作行同一套
 * 行高与图标底座——这里不画新组件，`ui-common.md` 的「设置行统一网格」只认那一处。
 */
class StorageLine(
    /** 行标题，≤ 6 字（如「数据库」）。 */
    val title: String,
    /** 一句话说这一行是什么，≤ 25 字。 */
    val detail: String,
    /** 右侧体积文案，由平台按 `domain/SizeText` 的格式给。 */
    val size: String,
    /** 行首图标（`AppIcons` 里的名字，不许猜）。 */
    val icon: ImageVector,
)

/**
 * 「导出诊断日志」这一行的平台能力。
 *
 * 与 [StorageDiagnostics] 分开是因为它**可以没有**：Android 有崩溃留痕目录
 * （`CrashReporter`），iOS 有 `logs/`；将来某个平台没有诊断日志时给 `null`，
 * 这一行整行不渲染，而不是留一个点了没反应的入口。
 */
class DiagnosticsExport(
    /** 当前有几份记录；0 = 没有（这时箭头也不画）。 */
    val count: () -> Int,
    /** 计数对应的行副标题（「N 份…」/「没有…」两说），文案由平台给。 */
    val subtitle: (Int) -> String,
    /** 把日志交给系统分享；`false` = 没有可用目标（页面提示「无法发起分享」）。 */
    val export: suspend () -> Boolean,
)

/**
 * 「存储与诊断」这一节的平台能力（2026-11-25 从 `:app` 的 `PermissionsStorageScreen` 抽出）。
 *
 * 为什么是「一组动作 + 两段文案」而不是可空槽位：这一节的 UI（两行、确认弹窗、进行中禁用）
 * 两端一模一样，只有「量哪儿 / 清哪儿 / 怎么说」不同。把 UI 也交给平台，等于把
 * `.agents/rules/ui-common.md` 里那三条确认弹窗口径各抄一份。
 *
 * ## 两端的落点
 *
 * | 成员 | Android（`data/repo/AppCacheCleaner`、`CrashReporter`） | iOS（沙箱） |
 * |---|---|---|
 * | [measureCacheBytes] | WebView 数据 + 地图瓦片 + 图片/校历/快照 | `appDataDir()/cache` |
 * | [clearCache] | 上列各项 + 崩溃记录目录 | 只清 `cache/`，**不动数据库与 `logs/`** |
 * | [storageLines] | 空（体积并进「清理缓存」副标题，口径不变） | 数据库 / 缓存 / 日志三行 |
 * | [diagnostics] | `crash/` 五份崩溃记录，`FileProvider` 分享 | `logs/` 三个日志文件，文本分享 |
 *
 * ## 为什么 [confirmMessage] 由平台整段给
 *
 * 两端清掉的东西不一样，写出的话就必须不一样（Android 要说「下次打开教务网页需要重新登录」，
 * iOS 清的是自己的缓存目录，跟教务登录态无关）。塞一个模板让页面往里拼体积，等于让页面
 * 替平台承诺「清了什么」——那是平台才知道的事。
 */
class StorageDiagnostics(
    /** 「清理缓存」行副标题的前半句：这一块里都有什么（如「网页缓存 · 地图瓦片 · 崩溃记录等」）。 */
    val cacheScope: String,
    /** 量一次可清理的占用（字节）；`null` = 量不出来，副标题不拼「共 …」。 */
    val measureCacheBytes: suspend () -> Long?,
    /** 清理缓存；`true` = 全清干净（`false` 时页面提示「部分缓存没清干净」）。 */
    val clearCache: suspend () -> Boolean,
    /** 清理确认弹窗的正文（体积由平台插进自己的文案里）。 */
    val confirmMessage: (Long?) -> String,
    /** 存储占用细分行；空 = 不渲染这一组。 */
    val storageLines: suspend () -> List<StorageLine> = { emptyList() },
    /** 「导出诊断日志」；`null` = 本平台没有诊断日志，整行不渲染。 */
    val diagnostics: DiagnosticsExport? = null,
)
