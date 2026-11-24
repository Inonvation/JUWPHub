package edu.jxslu.schedule.ui.selection

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import edu.jxslu.schedule.data.repo.SelectionSync
import edu.jxslu.schedule.domain.AppClock
import edu.jxslu.schedule.domain.CourseSelection
import edu.jxslu.schedule.domain.ScoreAlertDefaults
import edu.jxslu.schedule.domain.SelectionRound
import edu.jxslu.schedule.domain.SelectionRoundPhase
import edu.jxslu.schedule.domain.SelectionRounds
import edu.jxslu.schedule.domain.TextFmt
import edu.jxslu.schedule.platform.Services
import edu.jxslu.schedule.platform.collectAsStateWithLifecycle
import edu.jxslu.schedule.ui.common.AppCard
import edu.jxslu.schedule.ui.common.AppSnackbarHost
import edu.jxslu.schedule.ui.common.SettingItem
import edu.jxslu.schedule.ui.common.SettingSwitchRow
import edu.jxslu.schedule.ui.common.SettingsSection
import edu.jxslu.schedule.ui.common.WheelValueDialog
import edu.jxslu.schedule.ui.common.showNotice
import edu.jxslu.schedule.ui.icon.AppIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDateTime

/**
 * 选课（DESIGN §3.19 / §4.35，我的 → 学习 → 选课）。2026-10-01 排版收口：
 * 主页只放「要做的事」，记录与细节全部收进二级页。
 *
 * 三块内容：
 * 1. **轮次卡**：进行中/即将开始的轮次置顶（状态标签 + 确定性时间文案），唯一主按钮
 *    「进入选课」开应用内选课中心（§3.22，轮次 id 随行直达）；其余轮次收成紧凑行。
 *    **教务 WebView 全页只留一个入口**——下方「教务网页」行（备用）；轮次行不再直达教务页。
 * 2. **功能入口**：已选课程（`SELECTION_RECORDS`，记录列表整体挪入）/ 预选清单（预购匹配，
 *    命中课程在选课中心优先显示）/ 教务网页（备用）。
 * 3. **提醒**：轮次开始前/截止前的定时通知（[SelectionAlertReminder]），默认关。
 *
 * 数据闸门：进页自动同步一次、30 分钟内重复进页直接用缓存（`SelectionSync.MIN_REFRESH_MS`）；
 * 顶栏「更新」= 强制同步。失败不弹窗：轮次卡空态处说明，记录的展示在已选课程页。
 *
 * ## 平台动作全在参数里（iOS 移植 S13，2026-11-25）
 *
 * 本文件在共享层，拿不到 `Context` / `Activity`，也拿不到 `AlarmManager`。原来四处
 * `XxxActivity.start(context, …)` / `XxxReminder.xxx(context)` 收成可空回调，
 * **null = 这个平台没有这项能力，入口整行不渲染**（口径同 `WeekScreen` / `ScoreScreen`）：
 *
 * | 参数 | null 的含义 |
 * |---|---|
 * | [onOpenCenter] | 平台没有二级页路由 → 轮次卡不画主按钮、轮次行不可点 |
 * | [onOpenRecords] | 隐藏「已选课程」入口行 |
 * | [onOpenWishes] | 隐藏「预选清单」入口行 |
 * | [onOpenJwWeb] | 隐藏顶栏「在教务网页核对」 |
 * | [onOpenJwPage] | 隐藏「教务网页」备用入口行 |
 * | [onAlertsChanged] | 平台没有定时提醒 → 隐藏整段「选课提醒」 |
 *
 * 数据本身两端不同源：[SelectionRepository] 是本地表，哪一端都有；[SelectionSync]
 * 要教务直取链，`AppServices.selectionSync()` 在 iOS 侧返回 null——那时轮次卡改说
 * 「本平台还没有教务直取能力」，**不画**「当前没有开放的选课轮次」这种假结论。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectionScreen(
    onBack: () -> Unit,
    /** 进入应用内选课中心；[roundId] 非空时直达该轮次（走二级页 `focusItemId` 通道）。 */
    onOpenCenter: ((roundId: String?) -> Unit)? = null,
    /** 已选课程（记录列表页）。 */
    onOpenRecords: (() -> Unit)? = null,
    /** 预选清单。 */
    onOpenWishes: (() -> Unit)? = null,
    /** 顶栏「在教务网页核对」（`JwImportActivity.startBrowse`）。 */
    onOpenJwWeb: (() -> Unit)? = null,
    /** 「功能」区的教务网页备用入口（`JwImportActivity.start(context, JwImportMode.Selection)`）。 */
    onOpenJwPage: (() -> Unit)? = null,
    /** 提醒开关 / 间隔改动后重排定时提醒（清检查时刻 + 重排闹钟）；suspend 是因为它要读快照文件。 */
    onAlertsChanged: (suspend () -> Unit)? = null,
) {
    val repo = remember { Services.current.selectionRepository() }
    // null = 本平台没有教务直取链（iOS 现状）：轮次读不到、也不能「更新」
    val sync = remember { Services.current.selectionSync() }
    val prefs = remember { Services.current.displayPrefs() }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    // 已选课程入口行的副标题口径：null = Room 流首帧未到
    val allState by repo.observeAll().collectAsStateWithLifecycle(initialValue = null)

    // 轮次快照是 filesDir 里的一份 JSON：**读取必须离开主线程**（2026-11-06 审计 2-B）。
    // 原实现把 `cachedRounds()` 放在 `remember` 的初值里 —— 那是组合期，等于主线程读盘；
    // 现在初值给空，由下面那个 LaunchedEffect 在后台线程读回来。
    // 代价是首帧没有数据，所以配一个 [snapshotLoaded]：读完之前轮次卡按「读取中」画，
    // **不画**「当前没有开放的选课轮次」那段假结论（那才是会闪的那一下）。
    var rounds by remember { mutableStateOf<List<SelectionRound>>(emptyList()) }
    var lastSyncAt by remember { mutableLongStateOf(0L) }
    /** 本地轮次快照是否已读回（false = 首帧，还没读到磁盘）。 */
    var snapshotLoaded by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    var lastError by remember { mutableStateOf<String?>(null) }
    var showIntervalPicker by remember { mutableStateOf(false) }

    val alertEnabled by prefs.selectionAlertEnabled.collectAsStateWithLifecycle(initialValue = false)
    val intervalHours by prefs.selectionAlertIntervalHours
        .collectAsStateWithLifecycle(initialValue = ScoreAlertDefaults.INTERVAL_DEFAULT)
    val wishes by prefs.selectionWishes.collectAsStateWithLifecycle(initialValue = emptyList())

    // 页面可能被等轮次开始的用户长时间停着：每分钟重算一次相位，
    // 「即将开始 → 进行中」「剩余时间」不至于停在进页那一刻
    var now by remember { mutableLongStateOf(AppClock.nowMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            now = AppClock.nowMillis()
        }
    }
    val target = remember(rounds, now) { SelectionRounds.pickTarget(rounds, now) }
    val others = remember(rounds, target) { rounds.filter { it.id != target?.id } }

    fun refresh(force: Boolean) {
        // 没有教务直取链的平台：一次都不打网络，轮次卡改说「本平台没有这项能力」
        val service = sync ?: return
        if (refreshing) return
        refreshing = true
        scope.launch {
            val outcome = service.refresh(force = force)
            // 快照读取是文件 IO：`scope.launch` 默认就在 Main，必须显式换线程。
            // 用 `Dispatchers.Default` 而不是 `Dispatchers.IO`——后者是 JVM 专有，
            // commonMain 要按 Kotlin 公共标准库编译（iOS 侧也没有这个调度器）。
            val (cached, at) = withContext(Dispatchers.Default) {
                service.cachedRounds() to service.cachedFetchedAt()
            }
            rounds = cached
            lastSyncAt = at
            snapshotLoaded = true
            refreshing = false
            lastError = outcome.failedReason
            // 轮次快照刚变过：重排提醒闹钟（开关关着时是撤销闹钟的空跑）
            // 它内部要读一次轮次快照（文件）+ 调系统闹钟，别留在 Main
            withContext(Dispatchers.Default) { onAlertsChanged?.invoke() }
            // 进页那次自动刷新**失败不进气泡**（轮次卡空态处说明）；手动「更新」才回报结果
            if (force) {
                val results = outcome.results
                snackbar.showNotice(
                    when {
                        outcome.failedReason != null -> "同步失败：${outcome.failedReason}"
                        results is SelectionSync.Result.Updated -> "已更新：${results.rowCount} 条选课记录"
                        outcome.rounds is SelectionSync.RoundsResult.Updated -> "选课轮次已更新"
                        else -> "已是最新"
                    },
                )
            }
        }
    }

    LaunchedEffect(Unit) {
        val service = sync
        if (service == null) {
            // 没有直取链：别停在「正在读取选课轮次…」，直接把快照判为已读完
            snapshotLoaded = true
            return@LaunchedEffect
        }
        // 先读本地快照（后台线程）再决定要不要打网络：缓存还新时 refresh 是零网络的空跑。
        // 读不到也只当空快照（`cachedRounds()` 内部 runCatching 兜底），不会卡在「读取中」。
        val (cached, at) = withContext(Dispatchers.Default) {
            service.cachedRounds() to service.cachedFetchedAt()
        }
        rounds = cached
        lastSyncAt = at
        snapshotLoaded = true
        refresh(force = false)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("选课", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(AppIcons.ArrowLeft01, contentDescription = "返回")
                    }
                },
                actions = {
                    if (onOpenJwWeb != null) {
                        IconButton(onClick = onOpenJwWeb) {
                            Icon(AppIcons.ExternalLink, contentDescription = "在教务网页核对选课")
                        }
                    }
                    if (sync != null) {
                        IconButton(onClick = { refresh(force = true) }, enabled = !refreshing) {
                            Icon(AppIcons.Refresh, contentDescription = "更新")
                        }
                    }
                },
            )
        },
        snackbarHost = { AppSnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                RoundsCard(
                    target = target,
                    others = others,
                    now = now,
                    // 本地快照还没读回来也算「同步中」：按钮首帧就画「同步中…」，
                    // 不先画「打开选课中心」再跳（2026-11-06 审计 2-B）
                    syncing = refreshing || !snapshotLoaded,
                    snapshotLoaded = snapshotLoaded,
                    lastSyncAt = lastSyncAt,
                    lastError = lastError,
                    /** 没有教务直取链的平台：轮次读不到，措辞要换掉，不能报「当前没有开放的轮次」。 */
                    syncAvailable = sync != null,
                    onOpenCenter = onOpenCenter?.let { open ->
                        { round: SelectionRound? ->
                            // 轮次 id 走二级页 focusItemId 通道直达（§3.22）：中心页先认它，
                            // 认不出（快照已过期）再退回 pickTarget
                            open(round?.id)
                        }
                    },
                )
            }
            item {
                SettingsSection(title = "功能") {
                    if (onOpenRecords != null) {
                        SettingItem(
                            title = "已选课程",
                            subtitle = recordsSubtitle(allState),
                            icon = AppIcons.CheckList,
                            onClick = onOpenRecords,
                        )
                    }
                    if (onOpenWishes != null) {
                        SettingItem(
                            title = "预选清单",
                            subtitle = if (wishes.isEmpty()) {
                                "录入想选的课程与教师 · 命中后优先显示，选课仍由你确认"
                            } else {
                                "已有 ${wishes.size} 条 · 命中课程在选课中心优先显示"
                            },
                            icon = AppIcons.AddToList,
                            onClick = onOpenWishes,
                        )
                    }
                    if (onOpenJwPage != null) {
                        SettingItem(
                            title = "教务网页",
                            subtitle = "选课/退课尽量在应用内完成 · 此为备用入口",
                            icon = AppIcons.Globe,
                            onClick = onOpenJwPage,
                        )
                    }
                }
            }
            // 提醒靠系统闹钟 + 通知：平台没有这套能力时整段不出现，别给一个点了没反应的开关
            if (onAlertsChanged != null) {
                item {
                    ReminderSection(
                        enabled = alertEnabled,
                        intervalHours = intervalHours,
                        onToggle = { value ->
                            scope.launch {
                                prefs.setSelectionAlertEnabled(value)
                                // 清检查时刻 + 周期任务存废 + 即时核对 + 重排闹钟都收在这一个回调里
                                withContext(Dispatchers.Default) { onAlertsChanged() }
                                snackbar.showNotice(if (value) "已开启选课提醒" else "已关闭选课提醒")
                            }
                        },
                        onPickInterval = { showIntervalPicker = true },
                    )
                }
            }
        }
    }

    if (showIntervalPicker && onAlertsChanged != null) {
        WheelValueDialog(
            title = "自动检查间隔",
            values = ScoreAlertDefaults.INTERVAL_CHOICES.map { ScoreAlertDefaults.intervalLabel(it) },
            initialIndex = ScoreAlertDefaults.intervalChoiceIndex(intervalHours),
            onConfirm = { index ->
                showIntervalPicker = false
                scope.launch {
                    prefs.setSelectionAlertIntervalHours(ScoreAlertDefaults.INTERVAL_CHOICES[index])
                    withContext(Dispatchers.Default) { onAlertsChanged() }
                }
            },
            onDismiss = { showIntervalPicker = false },
        )
    }
}

/**
 * 轮次卡（紧凑）：目标轮次（进行中 > 最近即将开始，[SelectionRounds.pickTarget]）置顶 +
 * 其余轮次紧凑行 + 唯一主按钮。
 *
 * 按钮总在：没有轮次时按钮开应用内中心（页内会说明状态），文案换成「打开选课中心」。
 * 轮次行只有「可操作」（进行中/即将开始）的能点，直达应用内中心对应轮次；
 * 已结束的行是纯信息。同步失败时在空态下补一句原因（不弹窗）。
 *
 * [syncAvailable] = false 是「本平台没有教务直取链」，与「教务这学期没开放轮次」是两件事：
 * 前者不该说成后者，主按钮也不该出现（点进去只会看到空中心）。
 */
@Composable
private fun RoundsCard(
    target: SelectionRound?,
    others: List<SelectionRound>,
    now: Long,
    syncing: Boolean,
    /** 本地快照是否已读回（首帧为 false，见调用点注释）。只影响「上次同步」那一行的措辞。 */
    snapshotLoaded: Boolean,
    lastSyncAt: Long,
    lastError: String?,
    /** 本平台有没有教务直取链（[edu.jxslu.schedule.data.repo.SelectionSync]）。 */
    syncAvailable: Boolean,
    /** null = 平台没有二级页路由：主按钮与轮次行都不画。 */
    onOpenCenter: ((SelectionRound?) -> Unit)?,
) {
    AppCard {
        if (target == null) {
            Text(
                "选课轮次",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            // 判据用 [snapshotLoaded] 而不是 [syncing]：手动刷新期间（syncing=true）轮次确实
            // 可能是「没有开放」，那时不能改口说「正在读取」；只有还没读到磁盘时才说读取中。
            // 首帧那句假结论（「当前没有开放的轮次」）就是靠这一条挡掉的（2026-11-06 审计 2-B）
            Text(
                when {
                    !syncAvailable -> "本平台还没有教务直取能力，选课轮次读不到；" +
                        "已同步过的选课记录仍在「已选课程」里。"
                    !snapshotLoaded -> "正在读取选课轮次…"
                    else -> "当前没有开放的选课轮次（非选课期属正常）。选课开始后这里会显示轮次与时间。"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                modifier = Modifier.padding(top = 4.dp),
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    target.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                PhaseTag(SelectionRounds.phase(target, now))
            }
            val detail = roundTimeLine(target, now)
                ?: listOf(target.term, target.timeText).filter { it.isNotBlank() }.joinToString(" · ")
            if (detail.isNotBlank()) {
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        others.forEach { round ->
            val phase = SelectionRounds.phase(round, now)
            RoundRow(
                round = round,
                now = now,
                // 只有可操作的轮次能点（直达应用内中心）；已结束/时间未知的是纯信息
                onOpen = if (onOpenCenter != null &&
                    (phase == SelectionRoundPhase.Active || phase == SelectionRoundPhase.Upcoming)
                ) {
                    { onOpenCenter(round) }
                } else {
                    null
                },
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        // 同步状态对轮次非空时也要可见：快照旧了 / 刚才那趟同步挂了，不能只靠空态说
        if (lastError != null) {
            Text(
                "同步失败：$lastError",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        // 没有直取链就谈不上「上次同步」，那一行整行不画（否则会说「尚未同步过」，像用户没操作）
        if (syncAvailable) {
            Text(
                when {
                    // 首帧还没读到磁盘：说「读取中」，不说「尚未同步过」（后者是结论，不是状态）
                    !snapshotLoaded -> "正在读取本地快照…"
                    lastSyncAt > 0 -> "上次同步 ${syncTimeLabel(lastSyncAt)}"
                    else -> "尚未同步过"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        if (onOpenCenter != null) {
            Button(
                onClick = { onOpenCenter(target) },
                // 同步中不可点：首帧快照没读出来时 target 还是 null，点进去会以「无目标轮次」
                // 打开选课中心（页面显示得像没有轮次），文案却已经写着「同步中…」（2026-11-06 复核）。
                enabled = !syncing,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    // min 而不是固定高：系统大字体档位下按钮要能撑高，别把字裁了
                    .heightIn(min = 44.dp),
            ) {
                Text(
                    when {
                        syncing -> "同步中…"
                        target != null -> "进入选课"
                        else -> "打开选课中心"
                    },
                )
            }
        }
    }
}

@Composable
private fun RoundRow(
    round: SelectionRound,
    now: Long,
    onOpen: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val phase = SelectionRounds.phase(round, now)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onOpen != null) Modifier.clickable(onClick = onOpen) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(round.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val detail = roundTimeLine(round, now)
                ?: listOf(round.term, round.timeText).filter { it.isNotBlank() }.joinToString(" · ")
            if (detail.isNotBlank()) {
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        PhaseTag(phase)
        if (onOpen != null) {
            Icon(
                AppIcons.ArrowRight01,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(18.dp),
            )
        }
    }
}

/** 轮次状态小标签；时间解析不出来（[SelectionRoundPhase.Unknown]）不画标签，不编状态。 */
@Composable
private fun PhaseTag(phase: SelectionRoundPhase) {
    val label = when (phase) {
        SelectionRoundPhase.Active -> "进行中"
        SelectionRoundPhase.Upcoming -> "即将开始"
        SelectionRoundPhase.Ended -> "已结束"
        SelectionRoundPhase.Unknown -> return
    }
    val color: Color = when (phase) {
        SelectionRoundPhase.Active -> MaterialTheme.colorScheme.primary
        SelectionRoundPhase.Upcoming -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
    }
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = color,
        modifier = Modifier.padding(start = 8.dp),
    )
}

/** 墙上时刻 → 「HH:mm」。搬迁前是 `DateTimeFormatter.ofPattern("HH:mm")`（`java.time` 专有）。 */
private fun clockOf(value: LocalDateTime): String =
    "${TextFmt.pad(value.hour)}:${TextFmt.pad(value.minute)}"

/** 墙上时刻 → 「MM-dd HH:mm」。月份/日期补零是原口径（与 `MM-dd` 一致）。 */
private fun monthDayClock(value: LocalDateTime): String =
    "${TextFmt.pad(value.monthNumber)}-${TextFmt.pad(value.dayOfMonth)} ${clockOf(value)}"

/** 同步脚注的时间：今天给「HH:mm」，更早给「MM-dd HH:mm」。 */
private fun syncTimeLabel(ms: Long): String {
    val at = AppClock.dateTimeOf(ms)
    return if (at.date == AppClock.today()) clockOf(at) else monthDayClock(at)
}

/**
 * 轮次的确定性时间文案：能解析出起止时刻就写「MM-dd HH:mm 开始/截止/已结束」，
 * 解析不出回退 null（调用方退回教务原文），不猜。
 */
private fun roundTimeLine(round: SelectionRound, now: Long): String? {
    fun at(ms: Long): String = monthDayClock(AppClock.dateTimeOf(ms))
    return when (SelectionRounds.phase(round, now)) {
        SelectionRoundPhase.Upcoming -> round.startAt?.let { "${at(it)} 开始" }
        SelectionRoundPhase.Active -> round.endAt?.let { "${at(it)} 截止" }
        SelectionRoundPhase.Ended -> round.endAt?.let { "${at(it)} 已结束" }
        SelectionRoundPhase.Unknown -> null
    }
}

/** 已选课程入口行的副标题：学期数 + 门数；没同步过就说明来源。 */
private fun recordsSubtitle(rows: List<CourseSelection>?): String = when {
    rows == null -> "从教务「选课日志」同步后展示"
    rows.isEmpty() -> "暂无记录 · 点开自动同步"
    else -> "${rows.map { it.term }.distinct().size} 个学期 · 共 ${rows.size} 门"
}

@Composable
private fun ReminderSection(
    enabled: Boolean,
    intervalHours: Int,
    onToggle: (Boolean) -> Unit,
    onPickInterval: () -> Unit,
) {
    SettingsSection(
        title = "选课提醒",
        subtitle = "轮次开始前 30 分钟、截止前 6 小时各通知一次",
    ) {
        SettingSwitchRow(
            title = "开启提醒",
            subtitle = "按教务发布的轮次时间排定时提醒",
            checked = enabled,
            onCheckedChange = onToggle,
        )
        SettingItem(
            title = "自动检查间隔",
            subtitle = "与「成绩与考试」提醒共用",
            value = ScoreAlertDefaults.intervalLabel(intervalHours),
            enabled = enabled,
            onClick = onPickInterval,
        )
        Text(
            "提醒需要「我的 → 教务账户」已保存统一认证账号；轮次时间解析不出来的轮次不会提醒。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
        )
    }
}
