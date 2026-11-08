package edu.jxslu.schedule.ui.me.hub

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import edu.jxslu.schedule.R
import edu.jxslu.schedule.ui.common.AppCard
import edu.jxslu.schedule.ui.common.AppNoticeVisuals
import edu.jxslu.schedule.ui.common.GallerySaveResult
import edu.jxslu.schedule.ui.common.NoticeTone
import edu.jxslu.schedule.ui.common.PageScaffoldColumn
import edu.jxslu.schedule.ui.common.StaggeredReveal
import edu.jxslu.schedule.ui.common.emphasizedText
import edu.jxslu.schedule.ui.common.saveBitmapToGallery
import edu.jxslu.schedule.ui.common.showNotice
import edu.jxslu.schedule.ui.ebike.openWechatScan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Heart
import me.rerere.hugeicons.stroke.ImageDownload

/** 赞赏码打包资源（res/drawable-nodpi/ic_reward_qr.webp），固定 1037×1037；改图要同步这行。 */
private const val REWARD_QR_RATIO = 1f

/** 赞赏码落相册的文件名（`Pictures/JUWPHub`，目录口径见 domain/GalleryPaths）。 */
private const val REWARD_FILE_NAME = "JUWPHub-赞赏码.png"

/**
 * 设置 → 关于 → 支持开发（2026-11-01）：赞赏码 + 保存后拉起微信扫一扫。
 *
 * 三段式布局：顶部 hero（爱心圆底 + 「感谢支持」）→ 赞赏码大图 → 说明文案卡，
 * 依次错峰入场（StaggeredReveal）。底部主操作「保存赞赏码并去微信扫码」：
 * 落盘相册（`Pictures/JUWPHub`）成功才拉微信，跳转复用快趣出码页的
 * [openWechatScan]（三级入口兜底，统一走 startActivityOutsideApp）；保存失败不跳转。
 */
@Composable
fun SupportDevScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    fun showPageNotice(message: String, tone: NoticeTone) {
        scope.launch { snackbar.showNotice(AppNoticeVisuals(message, tone = tone)) }
    }

    fun saveAndOpenWechat() {
        if (saving) return
        saving = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                // decodeResource 可能命中资源缓存返回共享位图，不要 recycle
                val bitmap = BitmapFactory.decodeResource(context.resources, R.drawable.ic_reward_qr)
                saveBitmapToGallery(
                    context = context,
                    bitmap = bitmap,
                    displayName = REWARD_FILE_NAME,
                    format = Bitmap.CompressFormat.PNG,
                    quality = 100,
                )
            }
            saving = false
            when (result) {
                is GallerySaveResult.Saved -> {
                    showPageNotice("赞赏码已保存到相册", NoticeTone.Success)
                    openWechatScan(context) { showPageNotice(it, NoticeTone.Warning) }
                }
                is GallerySaveResult.Failed -> showPageNotice(result.message, NoticeTone.Warning)
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
                        HugeIcons.Heart,
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
        StaggeredReveal(index = 1) {
            Image(
                painter = painterResource(R.drawable.ic_reward_qr),
                contentDescription = "赞赏码",
                contentScale = ContentScale.FillWidth,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(REWARD_QR_RATIO)
                    .clip(RoundedCornerShape(12.dp)),
            )
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
                        HugeIcons.ImageDownload,
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
