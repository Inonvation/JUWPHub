package edu.jxslu.schedule.ui.toolbox

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.dp
import edu.jxslu.schedule.domain.ToolboxImage
import edu.jxslu.schedule.ui.common.AppBackHandler
import edu.jxslu.schedule.ui.common.AppCard
import edu.jxslu.schedule.ui.common.AppNoticeVisuals
import edu.jxslu.schedule.ui.common.AppSnackbarHost
import edu.jxslu.schedule.ui.common.GallerySaveOutcome
import edu.jxslu.schedule.ui.common.NoticeTone
import edu.jxslu.schedule.ui.common.SettingChoiceRow
import edu.jxslu.schedule.ui.common.pinnedStatusBars
import edu.jxslu.schedule.ui.common.showNotice
import edu.jxslu.schedule.ui.platform.PickedImage
import edu.jxslu.schedule.ui.platform.rememberImagePicker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 预览用降采样源的长边（成品按原图另解一遍，两遍用同一组比例参数，预览即所得）。 */
private const val PreviewLongSide = 1200

/** 成品解码的长边上限：再大对截图/照片这类输入没有意义，只是内存风险。 */
private const val FullLongSide = 2600

/** 水印颜色预设（深色为主，浅底深色图上都压得住）。 */
private val WatermarkColors = listOf(
    "白" to 0xFFFFFFFF.toInt(),
    "黑" to 0xFF000000.toInt(),
    "红" to 0xFFE53935.toInt(),
    "橙" to 0xFFFB8C00.toInt(),
    "绿" to 0xFF43A047.toInt(),
    "蓝" to 0xFF1E88E5.toInt(),
    "紫" to 0xFF8E24AA.toInt(),
    "灰" to 0xFF757575.toInt(),
)

/**
 * 图片水印（DESIGN §4.47，实现思路借鉴「一个木函」的平铺水印；iOS 移植 V4 搬进共享层）：
 * 文字先渲染成一小块平铺单元（多行、密度档位 = 尾部空行），旋转并扩边后铺满整幅；
 * 不透明度 / 旋转 / 字号 / 颜色 / 密度五档可调。
 *
 * 预览（长边 ≤ [PreviewLongSide]）与成品（长边 ≤ [FullLongSide]）都走
 * [ToolboxRenderer.watermarkTile] + [ToolboxRenderer.watermark]，尺寸只按图宽取比例，
 * 所以两边观感一致。
 *
 * ## 平台边界（两端同一份页面）
 *
 * | 动作 | 走哪条缝 |
 * |---|---|
 * | 选图 | `ui/platform/ImagePickers` 的 `rememberImagePicker`（Android Photo Picker / iOS 文件选择器） |
 * | 解码 | `PickedImage.decode`（Android `BitmapFactory` 采样 / iOS skia） |
 * | 出图 | [ToolboxRenderer]（Compose 离屏绘制，`commonMain`） |
 * | 保存 / 分享 | `saveToolboxImage` / `shareToolboxImage`（`ui/toolbox/ToolboxExport.kt`） |
 *
 * 宿主只要 `WatermarkScreen(onBack = …)` 一行——与本页搬走之前 `:app` 的调用点逐字相同。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatermarkScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    // 文本测量在组合线程：这个 measurer 是 Density(1f) 的，理由见 rememberToolboxTextMeasurer
    val measurer = rememberToolboxTextMeasurer()

    var source by remember { mutableStateOf<PickedImage?>(null) }
    var previewSource by remember { mutableStateOf<ImageBitmap?>(null) }
    var text by remember { mutableStateOf("JUWPHub") }
    var density by remember { mutableStateOf(ToolboxImage.Density.Normal) }
    var alphaPercent by remember { mutableStateOf(35f) }
    var rotationDeg by remember { mutableStateOf(-30f) }
    var scalePercent by remember { mutableStateOf(100f) }
    // 默认黑（[WatermarkColors] 的第 2 个）：截图这类浅底输入是常见来源，白字压不住
    // （2026-10-12 用户口径）
    var colorIndex by remember { mutableStateOf(1) }
    var busy by remember { mutableStateOf(false) }

    fun notice(message: String, tone: NoticeTone) {
        scope.launch { snackbar.showNotice(AppNoticeVisuals(message, tone = tone)) }
    }

    val pickImage = rememberImagePicker(maxItems = 1) { picked ->
        picked.firstOrNull()?.let { source = it }
    }

    LaunchedEffect(source) {
        val picked = source ?: return@LaunchedEffect
        val decoded = picked.decode(PreviewLongSide)
        previewSource = decoded
        // 解码失败不再静默（2026-10-12）：读不出来当场说，预览区不空等
        if (decoded == null) notice("图片读不出来，换一张试试", NoticeTone.Error)
    }

    // 预览重算：参数一动就整幅重渲染。源是降采样图，几十毫秒级，不做防抖。
    // 七个参数收成一个列表 key（produceState 最多五个 key 参数，列表按结构比较）。
    val previewKeys = listOf(
        previewSource, text, density, alphaPercent, rotationDeg, scalePercent, colorIndex,
    )
    val preview by produceState<ImageBitmap?>(initialValue = null, previewKeys) {
        val src = previewSource
        if (src == null) {
            value = null
            return@produceState
        }
        // 平铺块要摸 TextMeasurer，留在组合线程；铺满整幅是纯图形，丢到 Default
        val tile = ToolboxRenderer.watermarkTile(
            textMeasurer = measurer,
            text = text,
            color = WatermarkColors[colorIndex].second,
            imageWidthPx = src.width,
            scale = scalePercent / 100f,
            density = density,
            rotationDeg = rotationDeg,
        )
        value = withContext(Dispatchers.Default) {
            ToolboxRenderer.watermark(src, tile, alphaPercent)
        }
    }

    fun runExport(share: Boolean) {
        val picked = source
        if (picked == null) {
            notice("先选一张图片", NoticeTone.Warning)
            return
        }
        if (text.isBlank()) {
            notice("先写上水印文字", NoticeTone.Warning)
            return
        }
        busy = true
        scope.launch {
            // busy 必须由 finally 兜底复位：解码 / 绘制 OOM、写相册抛异常时若逃逸出去，
            // busy 会永久停在 true —— 返回键此后只会提示「正在处理」，整页变死页
            try {
                val fullSource = picked.decode(FullLongSide)
                if (fullSource == null) {
                    notice("读取图片失败，请重试", NoticeTone.Error)
                    return@launch
                }
                val tile = ToolboxRenderer.watermarkTile(
                    textMeasurer = measurer,
                    text = text,
                    color = WatermarkColors[colorIndex].second,
                    imageWidthPx = fullSource.width,
                    scale = scalePercent / 100f,
                    density = density,
                    rotationDeg = rotationDeg,
                )
                val rendered = withContext(Dispatchers.Default) {
                    ToolboxRenderer.watermark(fullSource, tile, alphaPercent)
                }
                val fileName = ToolboxExport.fileName("水印")
                if (share) {
                    val error = shareToolboxImage(rendered, fileName)
                    if (error != null) notice(error, NoticeTone.Warning)
                } else {
                    when (val result = saveToolboxImage(rendered, fileName)) {
                        is GallerySaveOutcome.Saved ->
                            notice("已保存到相册", NoticeTone.Success)
                        is GallerySaveOutcome.Failed -> notice(result.message, NoticeTone.Error)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // OOM 的 message 是一长串英文分配信息，直接透给用户等于没解释
                notice(
                    if (e.looksLikeOutOfMemory()) {
                        "图片太大，内存不足：试试先裁剪"
                    } else {
                        "保存失败：${e.message ?: "未知错误"}"
                    },
                    NoticeTone.Error,
                )
            } finally {
                busy = false
            }
        }
    }

    // 生成 / 写盘期间不接受退出（与课表导出同一口径）
    val requestBack: () -> Unit = {
        if (busy) notice("正在处理，请稍候", NoticeTone.Warning) else onBack()
    }
    AppBackHandler(enabled = busy) { requestBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                windowInsets = pinnedStatusBars(),
                title = { Text("图片水印") },
                navigationIcon = {
                    IconButton(onClick = requestBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        // 出口按钮冻结在底部（DESIGN §4.47）：还没选图时是「选图」，选好后是「分享 / 保存到相册」
        bottomBar = {
            ToolboxActionBar(progress = busy) {
                if (source == null) {
                    Button(
                        onClick = pickImage,
                        enabled = !busy,
                        modifier = Modifier.weight(1f),
                    ) { Text("选图") }
                } else {
                    OutlinedButton(
                        onClick = { runExport(share = true) },
                        enabled = !busy,
                        modifier = Modifier.weight(1f),
                    ) { Text("分享") }
                    Button(
                        onClick = { runExport(share = false) },
                        enabled = !busy,
                        modifier = Modifier.weight(1f),
                    ) { Text("保存到相册") }
                }
            }
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
            ToolboxPreview(
                image = preview,
                placeholder = "选一张图片，水印会平铺满图",
                onPick = pickImage,
            )
            // 换图是输入动作，留在内容里顶在预览下方；出口按钮在底部动作条
            if (source != null) {
                TextButton(
                    onClick = pickImage,
                    enabled = !busy,
                    modifier = Modifier.align(Alignment.End),
                ) { Text("换一张图") }
            }
            AppCard {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        label = { Text("水印文字") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    SettingChoiceRow(
                        title = "密度",
                        subtitle = "平铺单元的疏密",
                        options = listOf("无", "标准", "大"),
                        selectedIndex = density.ordinal,
                        onSelect = { density = ToolboxImage.Density.entries[it] },
                    )
                    ToolboxSliderRow(
                        title = "不透明度",
                        valueText = "${alphaPercent.toInt()}%",
                        value = alphaPercent,
                        valueRange = 5f..100f,
                        onValueChange = { alphaPercent = it },
                    )
                    ToolboxSliderRow(
                        title = "旋转角度",
                        valueText = "${rotationDeg.toInt()}°",
                        value = rotationDeg,
                        valueRange = -90f..90f,
                        onValueChange = { rotationDeg = it },
                    )
                    ToolboxSliderRow(
                        title = "字号",
                        valueText = "${scalePercent.toInt()}%",
                        value = scalePercent,
                        valueRange = 30f..250f,
                        onValueChange = { scalePercent = it },
                    )
                    Spacer(Modifier.height(4.dp))
                    Text("颜色", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(6.dp))
                    ColorDotRow(
                        colors = WatermarkColors.map { it.second },
                        selectedIndex = colorIndex,
                        onSelect = { colorIndex = it },
                        // 名字要传给读屏：只说「单选按钮」用户分不出是哪个颜色
                        labels = WatermarkColors.map { it.first },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}
