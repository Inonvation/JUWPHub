package edu.jxslu.schedule.ui.me

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import edu.jxslu.schedule.Graph
import edu.jxslu.schedule.R
import edu.jxslu.schedule.domain.LocalTimeLike
import edu.jxslu.schedule.domain.buildTodayState
import edu.jxslu.schedule.ui.common.AppConfirmDialog
import edu.jxslu.schedule.ui.common.AppNoticeVisuals
import edu.jxslu.schedule.ui.common.AppSnackbarHost
import edu.jxslu.schedule.ui.common.NoticeTone
import edu.jxslu.schedule.ui.common.PermissionRow
import edu.jxslu.schedule.ui.common.SettingsSection
import edu.jxslu.schedule.ui.common.courseColor
import edu.jxslu.schedule.ui.common.rememberAppHaptics
import edu.jxslu.schedule.ui.common.showNotice
import edu.jxslu.schedule.ui.tile.PayCodeTileService
import edu.jxslu.schedule.ui.tile.QiekjWaterTileService
import edu.jxslu.schedule.ui.tile.QzxyWaterTileService
import edu.jxslu.schedule.ui.widget.CampusCardWidgetReceiver
import edu.jxslu.schedule.ui.widget.LifeWidgetSync
import edu.jxslu.schedule.ui.widget.QiekjWaterWidgetReceiver
import edu.jxslu.schedule.ui.widget.QzxyWaterWidgetReceiver
import edu.jxslu.schedule.ui.widget.ScheduleWidgetReceiver
import edu.jxslu.schedule.ui.widget.WidgetDay
import edu.jxslu.schedule.ui.widget.WidgetFocus
import edu.jxslu.schedule.ui.widget.WidgetLayout
import edu.jxslu.schedule.ui.widget.WidgetModel
import edu.jxslu.schedule.ui.widget.WidgetSnapshot
import edu.jxslu.schedule.ui.widget.WidgetSnapshotStore
import edu.jxslu.schedule.ui.widget.buildWidgetSnapshot
import edu.jxslu.schedule.ui.widget.buildWidgetWeek
import edu.jxslu.schedule.ui.widget.forSize
import edu.jxslu.schedule.ui.widget.widgetMetricsFor
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.BatteryCharging01
import me.rerere.hugeicons.stroke.Calendar01
import me.rerere.hugeicons.stroke.Droplet
import me.rerere.hugeicons.stroke.Energy
import me.rerere.hugeicons.stroke.GlassWater
import me.rerere.hugeicons.stroke.Wallet03
import java.time.LocalDate

/**
 * 「我的 → 桌面小组件」设置页（DESIGN §3.6）。
 *
 * 2026-09-27 **二条目改版**：可添加条目为课表（2026-09-20 单条目自适应，不变）+
 * 「校园卡 · 电费」合并卡（当天先从一条拆成三条、再合并回一条：拆开后 2 格宽的卡片
 * 只放得下一块信息，桌面看着空）。2026-09-28 **开水两卡**：再加胖乖开水 / 趣智开水
 * 两条固定 2×2 卡（DESIGN §3.6「开水两卡」）。条目仍是**按内容拆**，不是按尺寸拆。页面结构：
 *
 * 1. 添加到桌面：四行添加条目，各自 `requestPinAppWidget`、各自计数徽标；
 * 2. 课表尺寸形态：四张预览（同一份快照按四档裁剪，只作说明用）收进 2×2 网格省纵向空间；
 * 3. 校园卡（主区）：口径 / 点击行为 bullets + 「在小组件中隐藏余额」开关（默认关）；
 * 4. 电费（副行）：口径 / 点击行为 bullets；
 * 5. 开水卡（胖乖 · 趣智）：口径 / 胶囊直达与「点其余位置只进页面」bullets；
 * 6. 后台及时性（可选）/ 说明（更新时机在此各条目共用）：各条目共用。
 *
 * 2026-11-01 **文案精简**：更新时机合并进「说明」一次说清，各条 bullet 压到一行；
 * 只删说明文字，分区结构与交互规则不动。
 *
 * 交互规则不变（用户已拍板「自动检测 + 逐项申请」）：进页只读检测；**首次进入**自动弹
 * 一次说明弹层（含一键添加课表条目），此后不再自动弹；不主动拉任何系统框。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    // 快捷设置磁贴（DESIGN §3.32）：应用内添加要 API 33+ 的 StatusBarManager；
    // 低版本没有这个 API，只能走系统「编辑快捷设置」。
    val canAddTile = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        context.getSystemService(StatusBarManager::class.java) != null

    // 能力/权限状态：从系统设置返回（onResume）时重读，保证徽标与真实状态一致
    var caps by remember { mutableStateOf(readCaps(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) caps = readCaps(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 「在小组件中隐藏余额」（校园卡条目）：进页读一次，开关切换走 LifeWidgetSync
    // （内部写完立即重渲染，桌面马上变样）
    var hideBalance by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        hideBalance = runCatching { LifeWidgetSync.campusSnapshot(context).hideBalance }
            .getOrDefault(false)
    }

    // 首次进入的说明弹层（只弹一次，DataStore 标记）
    var showIntro by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val prefs = Graph.displayPrefs(context)
        if (!prefs.widgetSetupSeen()) {
            showIntro = true
            prefs.setWidgetSetupSeen()
        }
    }

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    // 四张尺寸预览**共用这一份**快照（2026-11-06 审计 §3.1）。
    // 改之前每个 `PreviewTile` 自己调 `rememberPreviewSnapshot()`：4 份快照、4 个 30 秒定时器、
    // 4 次 `buildTodayState` + `buildWidgetWeek`（整周网格）全跑在主线程上。提到页顶算一次，
    // 四个预览传同一个 State——内容与刷新节奏不变（仍是 30 秒一档），只是不再乘 4。
    // **不要**把它挪回 `PreviewTile` 里（那是「按尺寸各算一份」的老写法），也**不要**在这里
    // `by` 解包：页级读值会让整页每 30 秒重组一次。
    val previewSnapshot = rememberPreviewSnapshot()
    // 添加失败（桌面不支持应用内添加）的提示出口，走全 App 统一卡片
    val showNotice: (String) -> Unit = { message ->
        scope.launch {
            snackbar.showNotice(AppNoticeVisuals(message, tone = NoticeTone.Warning))
        }
    }
    // 磁贴添加的结果有三种语气（已添加 / 已在 / 失败），不能都套 Warning
    val showTileNotice: (String, NoticeTone) -> Unit = { message, tone ->
        scope.launch {
            snackbar.showNotice(AppNoticeVisuals(message, tone = tone))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("桌面小组件") },
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SettingsSection(
                title = "添加到桌面",
                subtitle = if (caps.canPin) {
                    "点「添加」后在系统确认框里一键放到桌面"
                } else {
                    "当前桌面不支持应用内添加，请长按桌面空白处 → 小组件 → JUWPHub"
                },
            ) {
                Spacer(Modifier.height(4.dp))
                entries.forEach { entry ->
                    AddRow(
                        label = entry.label,
                        description = entry.description,
                        icon = entry.icon,
                        iconTint = entry.iconTint,
                        added = entry.countOf(caps),
                        canPin = caps.canPin,
                        onAdd = {
                            onAddClicked(context, entry.receiver)?.let(showNotice)
                        },
                    )
                }
                Spacer(Modifier.height(6.dp))
            }

            SettingsSection(
                title = "快捷设置磁贴",
                subtitle = if (canAddTile) {
                    "下拉通知栏 → 编辑，把磁贴拖出来；也可以在这里直接添加"
                } else {
                    "下拉通知栏 → 编辑快捷设置，把 JUWPHub 的磁贴拖出来"
                },
            ) {
                Spacer(Modifier.height(4.dp))
                tileEntries.forEach { entry ->
                    AddRow(
                        label = entry.label,
                        description = entry.description,
                        icon = entry.icon,
                        iconTint = entry.iconTint,
                        added = 0,
                        canPin = canAddTile,
                        onAdd = {
                            requestTile(context, entry) { message, tone ->
                                showTileNotice(message, tone)
                            }
                        },
                    )
                }
                Spacer(Modifier.height(2.dp))
                Bullet("磁贴固定灰色，只做动作入口；灰色也能点。")
                Spacer(Modifier.height(6.dp))
            }

            SettingsSection(
                title = "课表尺寸形态",
                subtitle = "拖到以下大致尺寸时的样子；中间尺寸会自动落在最合适的一档",
            ) {
                Spacer(Modifier.height(6.dp))
                Row {
                    FormGridItem(
                        modifier = Modifier.weight(1f),
                        size = WidgetPreviewSize.Small,
                        label = "紧凑",
                        caption = "日期 + 正在上 / 下一节",
                        snapshot = previewSnapshot,
                    )
                    Spacer(Modifier.width(12.dp))
                    FormGridItem(
                        modifier = Modifier.weight(1f),
                        size = WidgetPreviewSize.Wide,
                        label = "横条",
                        caption = "焦点课课名更宽，地名全显示",
                        snapshot = previewSnapshot,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row {
                    FormGridItem(
                        modifier = Modifier.weight(1f),
                        size = WidgetPreviewSize.Tall,
                        label = "竖条",
                        caption = "焦点课 + 今日剩余",
                        snapshot = previewSnapshot,
                    )
                    Spacer(Modifier.width(12.dp))
                    FormGridItem(
                        modifier = Modifier.weight(1f),
                        size = WidgetPreviewSize.Large,
                        label = "大方",
                        caption = "摘要 + 本周课表（今天 / 明天列高亮）",
                        snapshot = previewSnapshot,
                    )
                }
                Spacer(Modifier.height(6.dp))
            }

            SettingsSection(title = "校园卡", subtitle = "数据与点击") {
                Spacer(Modifier.height(4.dp))
                Bullet("只显示食堂 / 门禁的正式卡余额，电子账户不上卡片。")
                Bullet("点卡片出示全屏付款码；没配置凭证时进校园卡设置。")
                HideBalanceSwitch(
                    checked = hideBalance,
                    onCheckedChange = { checked ->
                        hideBalance = checked
                        scope.launch {
                            runCatching { LifeWidgetSync.setCampusHideBalance(context, checked) }
                        }
                    },
                )
                Spacer(Modifier.height(2.dp))
            }

            SettingsSection(title = "电费（卡片副行）", subtitle = "数据与点击") {
                Spacer(Modifier.height(4.dp))
                Bullet("副行显示最近读数：剩余电量与折合金额。")
                Bullet("点电费行进用电统计，点其余位置出示付款码。")
                Spacer(Modifier.height(2.dp))
            }

            SettingsSection(title = "开水卡（胖乖 · 趣智）", subtitle = "固定 2×2，不可拖动；数据与点击") {
                Spacer(Modifier.height(4.dp))
                Bullet("胖乖显示小票余额 + 积分；趣智显示余额，用水中时整卡切换。")
                Bullet("趣智「用水中」即时上桌面，超过 1 小时未结算自动隐藏。")
                Bullet("点「去开水」直达开水 / 开阀（趣智需在水器旁连蓝牙），点其余位置只进页面。")
                Spacer(Modifier.height(2.dp))
            }

            SettingsSection(
                title = "后台及时性（可选）",
                subtitle = "小组件在上下课时刻自动更新；不开也能用，开启后刷新更及时",
            ) {
                Spacer(Modifier.height(4.dp))
                PermissionRow(
                    title = "忽略电池优化",
                    detail = "防止系统冻结后台刷新；不同手机叫「电池优化白名单 / 省电策略无限制」",
                    granted = caps.batteryWhitelisted,
                    icon = HugeIcons.BatteryCharging01,
                    actionText = if (caps.batteryWhitelisted) "查看" else "去开启",
                    onClick = { WidgetCapabilities.jumpBatteryOptimization(context) },
                )
                WidgetEntryDivider()
                PermissionRow(
                    title = "允许自启动",
                    detail = "开机与被杀后能自行恢复刷新；在厂商设置里找「自启动 / 允许后台运行」",
                    granted = null,
                    icon = HugeIcons.Energy,
                    actionText = "去设置",
                    onClick = { WidgetCapabilities.jumpAutoStart(context) },
                )
                Spacer(Modifier.height(4.dp))
            }

            SettingsSection(title = "说明") {
                Spacer(Modifier.height(6.dp))
                Bullet("卡片自身不联网，数据在打开对应页面时即时更新。")
                Bullet("后台约每 2 小时补一次，失败时保留上次数据。")
                Bullet("四个条目可同时上桌面，内容与 App 内一致，跟随系统深浅色。")
                Bullet("课表卡倒计时每秒跳动，上下课 / 课间自动切换。")
                Spacer(Modifier.height(6.dp))
                LauncherNoteRow()
                Spacer(Modifier.height(6.dp))
            }
        }
    }

    if (showIntro) {
        IntroDialog(
            canPin = caps.canPin,
            onAdd = {
                showIntro = false
                // 弹层里的「现在添加」默认钉一个课表 4×2（推荐）：横条在桌面上信息密度与
                // 可读性平衡最好，之后可随时拖动改大小。「校园卡 · 电费」条目由用户按需自行添加。
                // 弹层刚关（它也是独立窗口），提示改由页面宿主展示，不被盖住
                onAddClicked(context, ScheduleWidgetReceiver::class.java)?.let(showNotice)
            },
            onDismiss = { showIntro = false },
        )
    }
}

/** 可添加的两条目（DESIGN §3.6 二条目改版）；顺序即页面里的顺序。 */
private data class WidgetEntry(
    val label: String,
    val description: String,
    val receiver: Class<*>,
    val icon: ImageVector,
    val iconTint: Color,
    val countOf: (WidgetCaps) -> Int,
)

private val entries = listOf(
    WidgetEntry(
        label = "JUWPHub · 课表",
        description = "小尺寸看正在上 / 下一节，拖到 4×4 变本周课表",
        receiver = ScheduleWidgetReceiver::class.java,
        icon = HugeIcons.Calendar01,
        iconTint = Color(0xFF0F7C7C),
        countOf = { it.scheduleAdded },
    ),
    WidgetEntry(
        label = "JUWPHub · 校园卡 · 电费",
        description = "余额大字 + 电费小字；点卡片出示付款码，点电费行进用电统计",
        receiver = CampusCardWidgetReceiver::class.java,
        icon = HugeIcons.Wallet03,
        iconTint = Color(0xFF1F7A4D),
        countOf = { it.campusAdded },
    ),
    WidgetEntry(
        label = "JUWPHub · 胖乖开水",
        description = "固定 2×2；小票余额大字 + 积分，点「去开水」直达开水",
        receiver = QiekjWaterWidgetReceiver::class.java,
        icon = HugeIcons.Droplet,
        iconTint = Color(0xFF0F7C7C),
        countOf = { it.qiekjAdded },
    ),
    WidgetEntry(
        label = "JUWPHub · 趣智开水",
        description = "固定 2×2；余额 / 用水中 + 设备，点「去开水」直达开阀",
        receiver = QzxyWaterWidgetReceiver::class.java,
        icon = HugeIcons.GlassWater,
        iconTint = Color(0xFF2A6489),
        countOf = { it.qzxyAdded },
    ),
)

/** 快捷设置磁贴条目（DESIGN §3.32）：系统侧要 component + 单色 drawable，页面侧用 HugeIcons。 */
private data class TileEntry(
    val label: String,
    val description: String,
    val service: Class<*>,
    val icon: ImageVector,
    val iconTint: Color,
    val drawableRes: Int,
)

private val tileEntries = listOf(
    TileEntry(
        label = "付款码",
        description = "点磁贴直达一卡通付款码；未配置凭证时进校园卡设置",
        service = PayCodeTileService::class.java,
        icon = HugeIcons.Wallet03,
        iconTint = Color(0xFF1F7A4D),
        drawableRes = R.drawable.ic_tile_pay_code,
    ),
    TileEntry(
        label = "胖乖开水",
        description = "点磁贴进胖乖生活并自动开水；未登录时停在登录页",
        service = QiekjWaterTileService::class.java,
        icon = HugeIcons.Droplet,
        iconTint = Color(0xFF0F7C7C),
        drawableRes = R.drawable.ic_tile_qiekj_water,
    ),
    TileEntry(
        label = "趣智开水",
        description = "点磁贴进趣智校园并自动开阀；未登录 / 没设备时停在页面引导",
        service = QzxyWaterTileService::class.java,
        icon = HugeIcons.GlassWater,
        iconTint = Color(0xFF2A6489),
        drawableRes = R.drawable.ic_tile_qzxy_water,
    ),
)

/**
 * 应用内添加磁贴（API 33+）：系统弹确认框，结果码映射成一句话。
 * 低版本 / 没有 StatusBarManager 的 ROM 直接给手动路径，不假装成功。
 */
private fun requestTile(
    context: Context,
    entry: TileEntry,
    onResult: (String, NoticeTone) -> Unit,
) {
    val manager = context.getSystemService(StatusBarManager::class.java)
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || manager == null) {
        onResult("当前系统不支持应用内添加，请下拉通知栏 → 编辑快捷设置手动添加", NoticeTone.Info)
        return
    }
    // 部分 ROM 的 StatusBarManager 会直接抛 SecurityException / 空实现；
    // 兜住后给手动路径，不让设置页崩在一个便利入口上。
    runCatching {
        manager.requestAddTileService(
            ComponentName(context, entry.service),
            entry.label,
            Icon.createWithResource(context, entry.drawableRes),
            ContextCompat.getMainExecutor(context),
        ) { result ->
            when (result) {
                StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED ->
                    onResult("已添加「${entry.label}」", NoticeTone.Success)

                StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED ->
                    onResult("「${entry.label}」已在快捷设置里", NoticeTone.Info)

                else ->
                    onResult("没有添加成功，请下拉通知栏 → 编辑快捷设置手动添加", NoticeTone.Warning)
            }
        }
    }.onFailure {
        onResult("当前系统不支持应用内添加，请下拉通知栏 → 编辑快捷设置手动添加", NoticeTone.Warning)
    }
}

private fun readCaps(context: Context): WidgetCaps = WidgetCaps(
    canPin = WidgetCapabilities.canPin(context),
    batteryWhitelisted = WidgetCapabilities.isIgnoringBatteryOptimizations(context),
    scheduleAdded = WidgetCapabilities.addedCountOf(context, ScheduleWidgetReceiver::class.java),
    campusAdded = WidgetCapabilities.addedCountOf(context, CampusCardWidgetReceiver::class.java),
    qiekjAdded = WidgetCapabilities.addedCountOf(context, QiekjWaterWidgetReceiver::class.java),
    qzxyAdded = WidgetCapabilities.addedCountOf(context, QzxyWaterWidgetReceiver::class.java),
)

private data class WidgetCaps(
    val canPin: Boolean,
    val batteryWhitelisted: Boolean,
    val scheduleAdded: Int,
    val campusAdded: Int,
    val qiekjAdded: Int,
    val qzxyAdded: Int,
)

/**
 * 添加指定条目。返回 null = 已提交系统确认框；非 null = 用户可读错误，
 * 由调用方走页面统一的 [AppSnackbarHost]（此前是系统 Toast，与本页其余提示两套观感）。
 */
private fun onAddClicked(context: Context, receiver: Class<*>): String? =
    if (WidgetCapabilities.requestPinOf(context, receiver)) null else WidgetCapabilities.manualAddHint

/** 卡内两行之间的换气线（与分区卡的克制风格一致）。 */
@Composable
private fun WidgetEntryDivider() {
    androidx.compose.material3.HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    )
}

/**
 * 一条添加行：图标徽标 + 名称 + 摘要 + 已添加徽标 + 「添加」。
 *
 * 三条各指向自己的 receiver；「已添加 N 个」按各自 receiver 计数，互不相干。
 */
@Composable
private fun AddRow(
    label: String,
    description: String,
    icon: ImageVector,
    iconTint: Color,
    added: Int,
    canPin: Boolean,
    onAdd: () -> Unit,
) {
    val haptics = rememberAppHaptics()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(iconTint.copy(alpha = 0.13f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                if (added > 0) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (added > 1) "已添加 $added 个" else "已添加",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .background(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                shape = MaterialTheme.shapes.small,
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (canPin) {
            Spacer(Modifier.width(8.dp))
            TextButton(
                onClick = {
                    haptics.tap()
                    onAdd()
                },
            ) { Text("添加") }
        }
        // 桌面不支持应用内 pin（DESIGN §3.6）：按钮隐藏，只留分区副标题里的手动引导
    }
}

/** 「在小组件中隐藏余额」开关行（校园卡节内）：状态在 DataStore，写完桌面立即重渲染。 */
@Composable
private fun HideBalanceSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = "在小组件中隐藏余额",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "隐藏后桌面显示 ¥ ••••，点击仍可出示付款码",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun Bullet(text: String) {
    Text(
        text = "· $text",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        modifier = Modifier.padding(vertical = 2.dp),
    )
}

/**
 * 负一屏说明行（DESIGN §3.6「负一屏」）。
 *
 * 单独成块而不是混在 Bullet 里：这是用户明确问过的点，值得给一句能照着做的话
 * （去哪儿搜、搜不到怎么办），而不是一句「不支持」。
 */
@Composable
private fun LauncherNoteRow() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(
            text = "负一屏",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = WidgetCapabilities.launcherNote,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        )
    }
}

// ---------- 预览缩略图：用真实课表数据画一个迷你 widget ----------

/**
 * 设置页展示用的快照（与小组件同一套状态推导），30 秒随时间重算一次。
 *
 * 与 widget 侧的差别只有一处：**周网格直接构建**（不按尺寸跳过），
 * 因为四张预览里有一张就是周网格档。渲染仍走 `forSize`，与桌面所见同源。
 *
 * 返回 `State` 而不是快照本身，是因为四张预览**共用**这一份（2026-11-06 审计 §3.1：
 * 改之前每个 `PreviewTile` 各调一次本函数，= 4 份快照 + 4 个 30 秒定时器 + 4 遍
 * `buildTodayState`/`buildWidgetWeek` 全压在主线程）。把 `.value` 的读取留在
 * `PreviewTile` 里，30 秒一跳就只重组那四格；调用处**不要** `by` 解包——在页级读值
 * 会让整页每 30 秒跟着重组一次，白拿开销。
 */
@Composable
private fun rememberPreviewSnapshot(): State<WidgetSnapshot?> {
    val context = LocalContext.current
    val repo = remember { Graph.repository(context) }
    val snapshot = remember { mutableStateOf<WidgetSnapshot?>(null) }
    // 先算再等：进页立刻有内容，之后每 30 秒一档（与改之前的刷新节奏一致）
    LaunchedEffect(Unit) {
        while (true) {
            snapshot.value = runCatching {
                val semester = repo.semester.first()
                val slots = repo.timeSlots.first()
                val courses = repo.courses.first()
                val overrides = repo.overridesFor()
                val prefs = repo.displayPrefs.first()
                val today = LocalDate.now()
                val now = LocalTimeLike.now()
                val state = buildTodayState(semester, slots, courses, today, now, overrides)
                val week = if (state.inTerm) state.week else 1
                val highlight = state.focus?.day
                    ?: state.tomorrowDay.takeIf { state.tomorrowVisible }
                buildWidgetSnapshot(
                    state,
                    buildWidgetWeek(
                        courses = courses,
                        week = week,
                        showSaturday = prefs.showSaturday,
                        showSunday = prefs.showSunday,
                        filter = prefs.courseFilter,
                        highlightDay = highlight,
                        slots = slots,
                        semester = semester,
                        overrides = overrides,
                        now = now,
                        markNow = state.focus != null || state.remaining.isNotEmpty(),
                        nowDay = state.day,
                    ),
                )
            }.getOrNull()
            delay(30_000)
        }
    }
    return snapshot
}

/** 2×2 网格里的一格预览：缩略图在上、名称与说明在下（不是可添加条目，纯形态参考）。 */
@Composable
private fun FormGridItem(
    modifier: Modifier,
    size: WidgetPreviewSize,
    label: String,
    caption: String,
    snapshot: State<WidgetSnapshot?>,
) {
    Column(modifier) {
        PreviewTile(size, snapshot)
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(1.dp))
        Text(
            text = caption,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 迷你预览：按该档实测 dp 走同一套 [widgetMetricsFor] 分档；课表为空时给占位文案。 */
@Composable
private fun PreviewTile(size: WidgetPreviewSize, snapshot: State<WidgetSnapshot?>) {
    val (w, h) = when (size) {
        WidgetPreviewSize.Small -> 84.dp to 84.dp
        WidgetPreviewSize.Wide -> 116.dp to 58.dp
        WidgetPreviewSize.Tall -> 58.dp to 116.dp
        WidgetPreviewSize.Large -> 128.dp to 128.dp
    }
    Box(
        modifier = Modifier
            .width(w)
            .height(h)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .padding(7.dp),
    ) {
        // 读 `.value` 只发生在这里（PreviewTile 的重组范围），页级不读，30 秒一跳不拖整页
        val model = snapshot.value?.forSize(widgetMetricsFor(size.widthDp, size.heightDp))
        if (model == null) {
            Text(
                text = "课表\n为空",
                fontSize = 8.sp,
                lineHeight = 9.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = model.header,
                    fontSize = 7.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                PreviewBody(model)
            }
        }
    }
}

@Composable
private fun PreviewBody(model: WidgetModel) {
    // 周网格档：画一个极简的 7×5 网格示意（真实网格在 128dp 缩略图里画不出可读信息，
    // 这里只表达「大尺寸会给周课表」这件事；实际效果以桌面为准）
    val week = model.week
    if (week != null) {
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                week.days.forEach { day ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .background(
                                if (day == week.highlightDay) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)
                                },
                            ),
                    ) {}
                }
            }
            repeat(4) { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                    week.days.forEach { day ->
                        val hasCourse = week.blocks.any {
                            it.day == day && row == (it.startSection - 1) / 3
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(7.dp)
                                .background(
                                    if (hasCourse) {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.06f)
                                    },
                                ),
                        ) {}
                    }
                }
            }
        }
        return
    }
    PreviewFocus(model.focus)
    // 明日接棒时列表标题也画出来（正是「不留空白」的落点）
    model.listTitle?.let { title ->
        Text(
            text = title,
            fontSize = 6.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    model.rows.take(2).forEach { row ->
        PreviewRow(row.colorIndex, row.name, row.clock)
    }
}

@Composable
private fun PreviewFocus(focus: WidgetFocus) {
    when (focus) {
        is WidgetFocus.Course -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(courseColor(focus.colorIndex).copy(alpha = 0.16f))
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            ) {
                Column {
                    Text(
                        text = focus.label,
                        fontSize = 6.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = courseColor(focus.colorIndex),
                    )
                    Text(
                        text = focus.name,
                        fontSize = 8.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }

        is WidgetFocus.Idle -> Text(
            text = focus.title,
            fontSize = 8.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PreviewRow(colorIndex: Int, name: String, clock: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = clock,
            fontSize = 6.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(3.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(4.dp))
                .background(courseColor(colorIndex).copy(alpha = 0.16f))
                .padding(horizontal = 3.dp, vertical = 1.dp),
        ) {
            Text(
                text = name,
                fontSize = 7.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 首次进入的说明弹层（DESIGN §3.6「进入设置界面自动申请」的落点）。 */
@Composable
private fun IntroDialog(
    canPin: Boolean,
    onAdd: () -> Unit,
    onDismiss: () -> Unit,
) {
    AppConfirmDialog(
        title = "把 JUWPHub 放上桌面",
        message = "课表：小到 2×2 看下一节，拖到 4×4 变本周课表。\n\n" +
            "校园卡 · 电费：桌面看余额与电费，点卡片出示付款码。\n\n" +
            "小组件上下课自动更新；页面下方可开启更及时的后台刷新（可选）。",
        // 桌面不允许直接加小组件时（启动器不支持 pin 请求）主键退化成「知道了」，
        // 与「稍后」同义——宁可两键都关窗，也不留一个点了没反应的主键
        confirmLabel = if (canPin) "现在添加" else "知道了",
        onConfirm = { if (canPin) onAdd() else onDismiss() },
        onDismiss = onDismiss,
        dismissLabel = "稍后",
    )
}
