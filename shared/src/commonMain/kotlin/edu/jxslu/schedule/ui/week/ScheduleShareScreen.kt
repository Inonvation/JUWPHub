package edu.jxslu.schedule.ui.week

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import edu.jxslu.schedule.domain.AppClock
import edu.jxslu.schedule.domain.BgScale
import edu.jxslu.schedule.domain.LocalTimeLike
import edu.jxslu.schedule.domain.ScheduleShare
import edu.jxslu.schedule.platform.collectAsStateWithLifecycle
import edu.jxslu.schedule.ui.common.AppBackHandler
import edu.jxslu.schedule.ui.common.AppNoticeVisuals
import edu.jxslu.schedule.ui.common.AppSnackbarHost
import edu.jxslu.schedule.ui.common.GallerySaveOutcome
import edu.jxslu.schedule.ui.common.GridCellStyle
import edu.jxslu.schedule.ui.common.LoadingHint
import edu.jxslu.schedule.ui.common.NoticeTone
import edu.jxslu.schedule.ui.common.showNotice
import edu.jxslu.schedule.ui.icon.AppIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 板内边距。 */
private val BoardPadding = 12.dp

/** 板头（课表名 + 周次）高度与到网格的间距。 */
private val BoardHeaderHeight = 44.dp
private val BoardHeaderGap = 8.dp

/** 脚注一行的高度与到网格的间距。 */
private val BoardFooterHeight = 18.dp
private val BoardFooterGap = 6.dp

/** 脚注文案（DESIGN §4.12）：仓库纪律「对外文案必须免责声明」，分享出去的图就是对外。 */
private const val BoardFooterText = "JUWPHub · 非官方课表工具，内容仅供参考"

/**
 * 板减掉的「页面底部净空」里与底栏形态无关的那一段：悬浮胶囊高 64 + 离底间距 12
 * （手势条另算，现读 inset）。与 MainActivity 的 `FloatingPillHeight` / `FloatingBarBottomMargin`
 * 同一口径——那两枚是主壳私有常量，这里按值复刻；普通底栏形态是 80 + inset，差 4dp
 * 只让行高差约 0.4dp，不分模式。
 *
 * 它不为「板要躲底栏」（板里没有底栏），而是让**板的行高与课表页逐 dp 相同**：
 * 课表页的网格可用高度 = 窗口 − 状态栏 − 顶栏 − 底部净空，板取同一笔预算，
 * 见 [shareBoardGridHeightDp] 与下方函数注释。
 */
private val BoardBottomChrome = 76.dp

/**
 * 导出为图片（DESIGN §4.12）：预览本周课表 → 勾选背景图 / 脚注 → 保存到相册或分享。
 *
 * **独立二级页而不是弹层**：预览要占整屏高度，且只有板会被截图——弹层（Dialog）在这个
 * Compose 版本上拿不到可靠的窗口 insets（实测内容比可视区高一个状态栏，底部按钮被挤出屏幕），
 * 二级页的 Activity 窗口 insets 是本项目既有链路里已经跑通的（与其余二级页同形态）。
 *
 * 尺寸口径：**导出板 = 课表页的网格在同一个窗口下的样子**。板宽按窗口宽算（列宽与屏幕上
 * 逐 dp 相同），行高走与课表页同一份 [buildGridLayout] 输入（可用高 = 窗口高 − 状态栏 −
 * 顶栏 − 底部净空），字号走同一份 [rememberGridTypeSpec]——三者的比例与屏幕上一致：
 * 长课名在哪换行、教室占几行、字相对格子多大，都与页面对得上。预览区装不下整张板时
 * **只缩显示**（`graphicsLayer`，不改布局），截图仍是板原始分辨率（整屏级：1080 屏上实测
 * 1101×2289 px）；不做离屏超采样。
 *
 * 根因（2026-10-10 用户报「图里教室被截断成『@工程训练中心…』、格子文字明显偏大」）：
 * 此前板宽按**预览区**算、行高按**预览区可用高**反推并夹 44–64dp，字号却还是课表页那一份。
 * 预览区比课表页的网格区窄（左右各多 16dp 留白 + 板内边距）又矮（还要让出板头 / 选项 /
 * 按钮），于是列宽少约一成半、行高被下限压到 44dp（页面约 65–71dp），字没跟着变——
 * 文字相对格子明显偏大，长课名多换一行就把教室挤出可见区。现在几何与页面同源，不会再漂移。
 *
 * [initialWeek] 来自 `SubpageScreen.SCHEDULE_SHARE` 的 focusItemId（进页时正在看的那一周）；
 * 缺省落到本周。切周是**页内局部状态**，不动主页面当前周。
 *
 * ## iOS 移植 M2（2026-11-26）搬进 `commonMain`
 *
 * 这一页原来把四件平台事写死在页内：建 `WeekViewModel`（要 `Context`）、取窗口长边（要
 * `DisplayMetrics`）、把截到的 `android.graphics.Bitmap` 存相册、把 PNG 字节交给
 * `FileProvider` + `ACTION_SEND`。按 `design/ios-port.md` §11.2 收成**必填 ViewModel + 三个可空槽位**：
 *
 * | 参数 | 搬迁前的写法 | `null` 的后果 |
 * |---|---|---|
 * | [viewModel] | `viewModel { WeekViewModel(Graph.repository(context)) }` | 必填，由宿主建（Android `viewModel {}` / iOS `remember {}`） |
 * | [loadBackground] | `loadScheduleBackground(context, …)`（`BitmapFactory` 解码） | 「保留课表背景图」整行不渲染，板也不画背景 |
 * | [onSaveToGallery] | `saveBitmapToGallery(context, bitmap, …)`（`MediaStore`） | 「保存到相册」按钮不渲染 |
 * | [onShareImage] | `ScheduleShareStore` 落盘 + `FileProvider` + `ACTION_SEND` | 「分享」按钮不渲染 |
 *
 * 截图本身**不再**是平台事：`GraphicsLayer.toImageBitmap()` 在两端都可用，`ImageBitmap` 也是
 * Compose 的公共类型。所以共享层交出去的是「一张图 + 文件名」，怎么编码、写到哪、交给谁
 * 全在宿主——共享层看不出 `Bitmap` / `ContentResolver` / `UIImage` 的存在。
 *
 * 传 `null` 不是「以后补」的占位：iOS 侧这三件要的是另一套原生 API（`UIImagePNGRepresentation`、
 * `PHPhotoLibrary`、`UIActivityViewController`），接上之后这一页一行都不用改。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleShareScreen(
    /** 与课表页同一份状态源，由宿主创建（见上方参数表）。 */
    viewModel: WeekViewModel,
    onBack: () -> Unit,
    initialWeek: Int? = null,
    /**
     * 解一张背景图给预览板用；`null` = 本平台没有背景图解码。
     *
     * 三个入参就是 `WeekUiState` 里那三格：文件名、缩放方式、模糊强度。解码目标尺寸由宿主算
     * （Android 侧要读 `DisplayMetrics` 的长边，见 `ui/week/ScheduleBackground.kt`），共享层不猜。
     */
    loadBackground: (suspend (fileName: String, scale: BgScale, blur: Float) -> ImageBitmap?)? = null,
    /** 落相册；结果由本页翻成提示。`null` = 本平台没有相册写入。 */
    onSaveToGallery: (suspend (image: ImageBitmap, displayName: String) -> GallerySaveOutcome)? = null,
    /**
     * 分享这张图；返回 `null` = 已把分享面板交给系统，非 `null` = 给用户看的失败原因。
     *
     * `null` 槽位 = 本平台没有分享出口。成功/失败用「返回值 + 一句话」表达，不传 sealed 类型：
     * 失败的两种原因（写不出文件 / 没有可用目标）只有宿主分得清，共享层只负责显示。
     */
    onShareImage: (suspend (image: ImageBitmap, displayName: String) -> String?)? = null,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    val totalWeeks = maxOf(state.semester?.totalWeeks ?: 20, 1)
    val sectionCount = maxOf(11, state.slots.maxOfOrNull { it.number } ?: 11)
    val visibleDays = state.visibleDays
    val days = visibleDays.size
    val typeSpec = rememberGridTypeSpec(
        gridFontDp = state.gridFontDp,
        gridRoomDp = state.gridRoomDp,
        gridTeacherDp = state.gridTeacherDp,
        gridRailDp = state.gridRailDp,
        gridDateDp = state.gridDateDp,
        days = days,
    )

    // 0 = 还没定：initialWeek 给了就用它，否则等数据就绪后落到本周
    var week by remember { mutableStateOf(initialWeek?.takeIf { it > 0 } ?: 0) }
    LaunchedEffect(state.loading, state.week, totalWeeks) {
        if (week <= 0 && !state.loading) week = state.week.coerceIn(1, totalWeeks)
    }
    val previewWeek = week.coerceIn(1, totalWeeks)

    var includeBackground by remember { mutableStateOf(false) }
    var includeFooter by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }

    // 宿主给不出解码器就等于这个平台没有背景图功能：勾选行整行不渲染，板也不画背景。
    val backgroundLoader = loadBackground
    val hasBackground = state.bgImageName != null && backgroundLoader != null
    val wantBackground = includeBackground && hasBackground

    // 背景位图：只给预览板用。宿主按 `cache = false` 解码——板上的目标尺寸与整屏渲染不同，
    // 进平台侧的单槽缓存会把整屏那张挤掉（与显示设置里的缩略图同一个坑）。
    val backgroundImage by produceState<ImageBitmap?>(null, state.bgImageName, wantBackground) {
        val name = if (wantBackground) state.bgImageName else null
        value = if (name == null || backgroundLoader == null) {
            null
        } else {
            backgroundLoader(name, state.bgImageScale, state.bgImageBlur)
        }
    }
    val backgroundLoading = wantBackground && backgroundImage == null

    val graphicsLayer = rememberGraphicsLayer()
    val fileName = ScheduleShare.exportFileName(compactDate(AppClock.today()), previewWeek)

    // 生成 / 写盘 / 拉起分享期间不接受退出：写到一半被退出会把协程连同 IO 一起取消，
    // 相册里可能留下半张图（与旧弹层 `if (!busy) onDismiss()` 同一口径）
    AppBackHandler(enabled = busy) {}

    suspend fun capture(): ImageBitmap? =
        runCatching { graphicsLayer.toImageBitmap() }.getOrNull()

    fun notice(text: String, tone: NoticeTone) {
        scope.launch { snackbar.showNotice(AppNoticeVisuals(text, tone = tone)) }
    }

    fun startSave() {
        val save = onSaveToGallery ?: return
        if (busy || backgroundLoading) return
        busy = true
        scope.launch {
            val image = capture()
            if (image == null) {
                notice("生成图片失败，请重试", NoticeTone.Error)
            } else {
                // 编码 + 落盘是纯计算与文件 IO：共享层只用 Default 池（`Dispatchers.IO` 是 JVM 专有）
                when (val result = withContext(Dispatchers.Default) { save(image, fileName) }) {
                    is GallerySaveOutcome.Saved ->
                        notice("已保存到相册", NoticeTone.Success)
                    is GallerySaveOutcome.Failed -> notice(result.message, NoticeTone.Error)
                }
            }
            busy = false
        }
    }

    fun startShare() {
        val share = onShareImage ?: return
        if (busy || backgroundLoading) return
        busy = true
        scope.launch {
            val image = capture()
            if (image == null) {
                notice("生成图片失败，请重试", NoticeTone.Error)
                busy = false
                return@launch
            }
            // 失败原因由宿主给（写不出图片 / 没有可用目标），本层只负责显示
            val failure = withContext(Dispatchers.Default) { share(image, fileName) }
            if (failure != null) notice(failure, NoticeTone.Error)
            busy = false
        }
    }

    // 整窗尺寸（含状态栏 / 手势条那一圈）：导出板的几何按窗口算，见上方「尺寸口径」。
    // 不拿预览区反推（比窗口窄、高度也让不出网格区那一份），也不读 Configuration 的
    // screenHeightDp（edge-to-edge 下扣不扣系统栏因版本而异）；量到的就是窗口本身。
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val windowWidth = maxWidth
        val windowHeight = maxHeight

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = { Text("导出为图片") },
                    navigationIcon = {
                        IconButton(onClick = { if (!busy) onBack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    },
                )
            },
            snackbarHost = { AppSnackbarHost(snackbar) },
        ) { padding ->
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                if (state.loading) {
                    LoadingHint("正在准备课表", modifier = Modifier.fillMaxSize())
                    return@Column
                }

                // ---- 周次条：切换只影响预览
                WeekStepper(
                    week = previewWeek,
                    totalWeeks = totalWeeks,
                    rangeText = ScheduleShare.weekRangeText(
                        weekDate(state.semester, previewWeek, visibleDays.firstOrNull() ?: 1),
                        weekDate(state.semester, previewWeek, visibleDays.lastOrNull() ?: 7),
                    ),
                    enabled = !busy,
                    onChange = { week = it },
                )

                // ---- 预览区：板按**窗口**布局（与课表页同一份几何），装不下时只缩显示。
                // 缩放只影响**显示**：截图取的是板自己那一层（`ShareBoard` 的 graphicsLayer），
                // 分辨率不随预览缩放变化——板比预览区大照样出整屏级分辨率的图。
                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    val dayHeaderHeight = maxOf(state.dayHeaderHeightDp.dp, typeSpec.headerMinDp.dp)
                    // 与课表页逐 dp 相同的网格几何：宽 = 窗口宽；可用高 = 课表页那一份预算
                    //（窗口 − 状态栏 − 顶栏 − 底部净空，口径见 shareBoardGridHeightDp）。
                    // 字号本来就同源（typeSpec），三者比例一致 = 图里就是屏幕上那一眼。
                    val statusBarDp = with(LocalDensity.current) {
                        WindowInsets.statusBars.getTop(this).toDp()
                    }
                    val navBarDp = with(LocalDensity.current) {
                        WindowInsets.navigationBars.getBottom(this).toDp()
                    }
                    val layout = buildGridLayout(
                        maxHeight = shareBoardGridHeightDp(
                            windowHeight = windowHeight,
                            statusBarHeight = statusBarDp,
                            topBarHeight = TopBarHeight,
                            bottomChrome = BoardBottomChrome + navBarDp,
                        ),
                        maxWidth = windowWidth,
                        sectionCount = sectionCount,
                        dayCount = days,
                        rowHeightScale = state.rowHeightScale,
                        railWidth = state.railWidthDp.dp,
                        dayHeaderHeight = dayHeaderHeight,
                    )
                    val chrome = BoardPadding * 2 + BoardHeaderHeight + BoardHeaderGap +
                        (if (includeFooter) BoardFooterGap + BoardFooterHeight else 0.dp)
                    val boardWidth = layout.railWidth + layout.dayWidth * days + BoardPadding * 2
                    val boardHeight = chrome + dayHeaderHeight + layout.gridHeight
                    val scale = minOf(1f, maxWidth / boardWidth, maxHeight / boardHeight)
                    val board: @Composable () -> Unit = {
                        ShareBoard(
                            graphicsLayer = graphicsLayer,
                            state = state,
                            typeSpec = typeSpec,
                            week = previewWeek,
                            layout = layout,
                            boardWidth = boardWidth,
                            days = days,
                            visibleDays = visibleDays,
                            background = backgroundImage,
                            includeFooter = includeFooter,
                            cellStyle = gridCellStyle(state, typeSpec),
                            // 星期几：项目统一用 `dayOfWeek.ordinal + 1`（1=周一），
                            // 与 `ExamMapper` / `HomeworkCenter` / `InboxMessage` 同一写法。
                            // `DayOfWeek.value` 在这个 kotlinx-datetime 版本上不存在。
                            todayDay = AppClock.today().dayOfWeek.ordinal + 1,
                        )
                    }
                    // 板按原始尺寸布局、只把**绘制**缩下来：`graphicsLayer` 不改布局，
                    // 所以缩放不会串到截图分辨率上（截图取的是板自己那一层）。
                    // 原点取中心：板在预览区里居中，缩完仍然居中，不会偏向一角。
                    Box(
                        Modifier
                            .requiredSize(boardWidth, boardHeight)
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                transformOrigin = TransformOrigin.Center
                            },
                    ) {
                        board()
                    }
                }

                // ---- 选项
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                ) {
                    if (hasBackground) {
                        ToggleRow(
                            label = "保留课表背景图",
                            checked = includeBackground,
                            enabled = !busy,
                            onChecked = { includeBackground = it },
                        )
                    }
                    ToggleRow(
                        label = "包含「JUWPHub」脚注",
                        checked = includeFooter,
                        enabled = !busy,
                        onChecked = { includeFooter = it },
                    )
                    if (backgroundLoading) {
                        Text(
                            "背景图载入中…",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        )
                    }
                }

                // ---- 动作：两个都是平台能力，槽位为 null 的那一个不渲染
                val canSave = onSaveToGallery != null
                val canShare = onShareImage != null
                if (canSave || canShare) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (canSave) {
                            OutlinedButton(
                                onClick = { startSave() },
                                enabled = !busy && !backgroundLoading,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp),
                            ) {
                                Icon(
                                    AppIcons.ImageDownload,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("保存到相册")
                            }
                        }
                        if (canShare) {
                            Button(
                                onClick = { startShare() },
                                enabled = !busy && !backgroundLoading,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp),
                            ) {
                                Icon(
                                    AppIcons.Share01,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("分享")
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 网格课块样式：与 WeekScreen 同一份映射（导出图要跟屏幕上看到的一致）。 */
private fun gridCellStyle(state: WeekUiState, typeSpec: GridTypeSpec) = GridCellStyle(
    cornerRadiusDp = state.cellRadiusDp,
    opacity = state.cellOpacity,
    centerHorizontal = state.cellCenterH,
    centerVertical = state.cellCenterV,
    showTeacher = state.showTeacher,
    showBorder = state.showCellBorder,
    roomFontSp = typeSpec.roomFontSp,
    teacherFontSp = typeSpec.teacherFontSp,
    showAtSign = state.showAtSign,
)

/** 周次切换条：`◀ 第 N 周 10/6 – 10/12 ▶`。 */
@Composable
private fun WeekStepper(
    week: Int,
    totalWeeks: Int,
    rangeText: String?,
    enabled: Boolean,
    onChange: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { onChange(week - 1) }, enabled = enabled && week > 1) {
            Icon(AppIcons.ArrowLeft01, contentDescription = "上一周", modifier = Modifier.size(20.dp))
        }
        Text(
            text = buildString {
                append("第 ").append(week).append(" 周")
                if (rangeText != null) append("　").append(rangeText)
            },
            style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { onChange(week + 1) }, enabled = enabled && week < totalWeeks) {
            Icon(AppIcons.ArrowRight01, contentDescription = "下一周", modifier = Modifier.size(20.dp))
        }
    }
}

/** 勾选行：整行可点、命中区 ≥44dp。 */
@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onChecked: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled) { onChecked(!checked) }
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = onChecked, enabled = enabled)
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.5f),
        )
    }
}

/**
 * 导出板：**唯一**截图目标。
 *
 * 结构 = 可选背景图 + 板头（课表名 / 周次）+ 网格（[TimeRail] + [WeekPage]）+ 可选脚注。
 * 不含顶栏、底栏、悬浮按钮、Snackbar 等任何 App 要素——这是「分享图只含本周课表」的落点。
 *
 * `drawWithContent` 必须挂在**最外层**：`record { drawContent() }` 记录的是它内部所有绘制，
 * 把 `clip`/`background` 放在它之后才会被一起截进图里（放前面就只截到孩子，底色变透明）。
 */
@Composable
private fun ShareBoard(
    graphicsLayer: GraphicsLayer,
    state: WeekUiState,
    typeSpec: GridTypeSpec,
    week: Int,
    layout: GridLayout,
    boardWidth: Dp,
    days: Int,
    visibleDays: List<Int>,
    background: ImageBitmap?,
    includeFooter: Boolean,
    cellStyle: GridCellStyle,
    todayDay: Int,
) {
    val outline = MaterialTheme.colorScheme.outlineVariant
    Box(
        modifier = Modifier
            .width(boardWidth)
            .drawWithContent {
                graphicsLayer.record { this@drawWithContent.drawContent() }
                drawLayer(graphicsLayer)
            }
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, outline.copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
    ) {
        if (background != null) {
            BoardBackground(
                image = background,
                opacity = state.bgImageOpacity,
                dim = state.bgImageDim,
                scale = state.bgImageScale,
                modifier = Modifier.matchParentSize(),
            )
        }
        Column(Modifier.padding(BoardPadding)) {
            BoardHeader(
                timetableName = state.timetableName,
                week = week,
                rangeText = ScheduleShare.weekRangeText(
                    weekDate(state.semester, week, visibleDays.firstOrNull() ?: 1),
                    weekDate(state.semester, week, visibleDays.lastOrNull() ?: 7),
                ),
            )
            Spacer(Modifier.height(BoardHeaderGap))
            // 整行（时间轴 + 网格）都包在 GridTypography 里：课表页就是这么包的，
            // 时间轴 / 日期表头字号按课名倍率预除过（`GridTypeSpec` 口径）——只包 WeekPage
            // 会让时间轴在「课名字号改过」时差一个倍率（默认档位看不出来）。
            GridTypography(typeSpec.gridScale) {
                Row(Modifier.height(layout.dayHeaderHeight + layout.gridHeight)) {
                    TimeRail(
                        layout = layout,
                        slots = state.slots,
                        monthLabel = weekDate(state.semester, week, visibleDays.firstOrNull() ?: 1)
                            ?.let { "${it.monthNumber}月" },
                        railFontSp = typeSpec.railFontSp,
                        dateFontSp = typeSpec.dateFontSp,
                    )
                    Box(
                        Modifier
                            .width(layout.dayWidth * days)
                            .height(layout.dayHeaderHeight + layout.gridHeight),
                    ) {
                        WeekPage(
                            week = week,
                            layout = layout,
                            visibleDays = visibleDays,
                            allCourses = state.allCourses,
                            slots = state.slots,
                            semester = state.semester,
                            overrides = state.overrides,
                            isTodayWeek = week == state.todayWeek,
                            todayDay = todayDay,
                            // 导出板不画时刻线（showNowLine = false），但入参类型是 State：
                            // 传一个固定「现在」，板上的渲染与截图都不受 tick 影响。
                            // 取当前时间而不是 state.now：后者已随 tick 拆分从状态里移除
                            // （30 秒变一次的值待在状态快照里会让整页重组）。
                            now = rememberUpdatedState(LocalTimeLike.now()),
                            // 导出图里不画「非本周灰态」与当前时刻线：那是屏幕上的辅助信息，
                            // 一张要发出去的图只该有这一周的课
                            showNonCurrentWeek = false,
                            cellStyle = cellStyle,
                            showNowLine = false,
                            showGridLines = state.showGridLines,
                            dateFontSp = typeSpec.dateFontSp,
                            // 板上的空白格不是入口
                            tapBlankToAdd = false,
                            onAddEmpty = { _, _ -> },
                            onOpenCourse = {},
                            onOpenOverrideInfo = { _, _ -> },
                        )
                    }
                }
            }
            if (includeFooter) {
                Spacer(Modifier.height(BoardFooterGap))
                Text(
                    text = BoardFooterText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                    textAlign = TextAlign.End,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(BoardFooterHeight),
                )
            }
        }
        // 触摸屏蔽：网格里的课块自带 clickable（会出涟漪、会震），而预览只是一张图，
        // 点它不该有任何反应。放一层不吃事件的同级覆盖层——命中最上层兄弟即止，
        // 下面的课块不再进入命中路径；因为不消费事件，外层滚动照常工作。
        Box(
            Modifier
                .matchParentSize()
                .pointerInput(Unit) {
                    awaitPointerEventScope { while (true) { awaitPointerEvent() } }
                },
        )
    }
}

/** 板头：左侧课表名 + 日期范围，右侧「第 N 周」。 */
@Composable
private fun BoardHeader(
    timetableName: String,
    week: Int,
    rangeText: String?,
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(BoardHeaderHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = timetableName.ifBlank { "课表" },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (rangeText != null) {
                Text(
                    text = rangeText,
                    style = MaterialTheme.typography.labelSmall,
                    color = onSurface.copy(alpha = 0.55f),
                    maxLines = 1,
                )
            }
        }
        Text(
            text = "第 $week 周",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/**
 * 板内背景图：与课表页 `ScheduleBackgroundLayer` 同一套画法（Fill/Fit/Tile + 压暗），
 * 但不画顶部状态栏渐变——那层是给整屏页面托顶栏文字的，板里没有顶栏。
 *
 * **这里保留 `Image(alpha = opacity)`，与课表页不同**（2026-11-06）：课表页那张是常驻的
 * 整屏图层，挂 alpha 会永久占一个离屏合成层，所以那边改成「不透明图 + 底色 scrim」；
 * 板是**一次性快照**（截图前组合一次、`record` 进 graphicsLayer 就结束），没有逐帧成本，
 * 且板的底色是 `surface`（课表页是 `background`），换算 scrim 要换颜色、换错就改变导出图观感。
 * 收益为零、风险不为零，故不动。
 *
 * 背景图的**解码**已不在本层：`ImageBitmap` 由宿主的 `loadBackground` 槽位给
 * （Android 是 `BitmapFactory`，iOS 是另一套图像 API）。
 */
@Composable
private fun BoardBackground(
    image: ImageBitmap,
    opacity: Float,
    dim: Float,
    scale: BgScale,
    modifier: Modifier = Modifier,
) {
    Box(modifier) {
        when (scale) {
            BgScale.Fill -> Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = opacity,
                modifier = Modifier.fillMaxSize(),
            )
            BgScale.Fit -> Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                alpha = opacity,
                modifier = Modifier.fillMaxSize(),
            )
            BgScale.Tile -> Canvas(Modifier.fillMaxSize()) {
                val src = IntSize(image.width, image.height)
                var y = 0
                while (y < size.height) {
                    var x = 0
                    while (x < size.width) {
                        drawImage(
                            image = image,
                            srcOffset = IntOffset.Zero,
                            srcSize = src,
                            dstOffset = IntOffset(x, y),
                            dstSize = src,
                            alpha = opacity,
                            filterQuality = FilterQuality.None,
                        )
                        x += image.width
                    }
                    y += image.height
                }
            }
        }
        if (dim > 0f) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = dim)))
        }
    }
}
