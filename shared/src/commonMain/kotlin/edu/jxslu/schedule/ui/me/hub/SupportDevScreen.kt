package edu.jxslu.schedule.ui.me.hub

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import edu.jxslu.schedule.ui.common.AppCard
import edu.jxslu.schedule.ui.common.AppNoticeVisuals
import edu.jxslu.schedule.ui.common.GallerySaveOutcome
import edu.jxslu.schedule.ui.common.NoticeTone
import edu.jxslu.schedule.ui.common.PageScaffoldColumn
import edu.jxslu.schedule.ui.common.StaggeredReveal
import edu.jxslu.schedule.ui.common.emphasizedText
import edu.jxslu.schedule.ui.common.showNotice
import edu.jxslu.schedule.ui.icon.AppIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 赞赏码打包资源固定 1037×1037；换图要同步这行（与宿主注入的那张图同口径）。 */
private const val REWARD_QR_RATIO = 1f

/**
 * 设置 → 关于 → 支持开发（2026-11-01）：赞赏码 + 保存后拉起微信扫一扫。
 *
 * 三段式布局：顶部 hero（爱心圆底 + 「感谢支持」）→ 赞赏码大图 → 说明文案卡，
 * 依次错峰入场（StaggeredReveal）。底部主操作「保存赞赏码并去微信扫码」：
 * 落盘相册成功才拉微信，跳转复用快趣出码页的三级入口兜底；保存失败不跳转。
 *
 * ## iOS 移植 W1（2026-11-25）搬进 `commonMain`
 *
 * 这一页除了排版全在宿主手里，三件平台物各一个槽位（`design/ios-port.md` §11.2 ④）：
 *
 * | 参数 | 原来 | null 的后果 |
 * |---|---|---|
 * | [rewardQr] | `painterResource(R.drawable.ic_reward_qr)`（`:app` 的打包资源） | 赞赏码大图不渲染 |
 * | [onSaveToGallery] | `BitmapFactory.decodeResource` + `saveBitmapToGallery`（`MediaStore`） | 「保存并去微信扫码」按钮与下方说明不渲染 |
 * | [onOpenWechatScan] | `ui/ebike/openWechatScan`（三级 `Intent` 兜底） | 保存成功后只提示、不跳转 |
 *
 * 三者是「本平台有没有这套资源与落盘」而不是待补的空实现：iOS 侧还没接相册写入
 * （见 `ui/common/BitmapSave.kt` 文件头），接上之后这个页面不用改一行。
 */
@Composable
fun SupportDevScreen(
    onBack: () -> Unit,
    /** 赞赏码大图；宿主按给定 Modifier 画（比例已定，不要自行改尺寸）。 */
    rewardQr: (@Composable (Modifier) -> Unit)? = null,
    /** 落相册；结果由本页翻成提示。null = 本平台没有相册写入。 */
    onSaveToGallery: (suspend () -> GallerySaveOutcome)? = null,
    /** 保存成功后拉起微信扫一扫；失败回调一句提示。null = 本平台拉不起微信。 */
    onOpenWechatScan: ((onError: (String) -> Unit) -> Unit)? = null,
) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    fun showPageNotice(message: String, tone: NoticeTone) {
        scope.launch { snackbar.showNotice(AppNoticeVisuals(message, tone = tone)) }
    }

    val save = onSaveToGallery
    fun saveAndOpenWechat() {
        if (saving || save == null) return
        saving = true
        scope.launch {
            // 解码 + 编码写盘是纯计算，共享层只用 Default 池（`Dispatchers.IO` 不在公共标准库）
            val result = withContext(Dispatchers.Default) { save() }
            saving = false
            when (result) {
                is GallerySaveOutcome.Saved -> {
                    showPageNotice("赞赏码已保存到相册", NoticeTone.Success)
                    onOpenWechatScan?.invoke { message ->
                        showPageNotice(message, NoticeTone.Warning)
                    }
                }
                is GallerySaveOutcome.Failed -> showPageNotice(result.message, NoticeTone.Warning)
            }
        }
    }

    PageScaffoldColumn(
        title = "支持开发",
        onBack = onBack,
        snackbar = snackbar,
    ) {
        StaggeredReveal(index = 0) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        AppIcons.Heart,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(30.dp),
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text("感谢支持", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(4.dp))
                Text(
                    "扫码赞赏 · 完全自愿",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (rewardQr != null) {
            StaggeredReveal(index = 1) {
                rewardQr(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(REWARD_QR_RATIO)
                        .clip(RoundedCornerShape(12.dp)),
                )
            }
        }
        StaggeredReveal(index = 2) {
            AppCard {
                Text(
                    text = emphasizedText(
                        "JUWPHub **完全免费开源**，所有功能**无需捐赠即可正常使用**。" +
                            "如果你觉得它帮到了你，愿意的话可以扫码表达一下心意，" +
                            "你的支持会成为持续维护与更新的动力~",
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        if (save != null) {
            StaggeredReveal(index = 3) {
                Column {
                    Button(
                        onClick = { saveAndOpenWechat() },
                        enabled = !saving,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                    ) {
                        Icon(
                            AppIcons.ImageDownload,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(if (saving) "正在保存…" else "保存赞赏码并去微信扫码")
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "若未直达扫一扫，在微信「发现 → 扫一扫 → 相册」里选刚保存的图识别",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
