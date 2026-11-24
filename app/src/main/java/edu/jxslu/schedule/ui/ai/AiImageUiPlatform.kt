package edu.jxslu.schedule.ui.ai

import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import edu.jxslu.schedule.Graph
import edu.jxslu.schedule.data.repo.AiImageStore
import edu.jxslu.schedule.ui.common.ImageSource
import edu.jxslu.schedule.ui.common.ImageViewerDialog

/**
 * AI 调课页两件平台能力的 Android 装配（iOS 移植 S15 收尾，2026-11-25）。
 *
 * 与 `ui/common/AttachmentImageUiPlatform.kt` 同一个形态：共享层的 `ui/ai` 只认
 * [AiImageUi] / [AiApiKeys] 这两个可空对象，实现留在 `:app`，由宿主一行装进去。
 *
 * 为什么这两样**不**搬进共享层：
 *
 * | 能力 | 卡在哪 |
 * |---|---|
 * | 图片（[AiImageUi]） | `AiImageStore` = `Uri` + `BitmapFactory` + `ImageImport`；缩略图 `AiThumb` 是 `android.graphics.Bitmap` + `LruCache`；查看器 `ImageViewerDialog` 也是 Android 件 |
 * | API Key（[AiApiKeys]） | `AiCredentialStore` = `EncryptedSharedPreferences`，没有接口，也不该为一次页面搬移去改它（它绑着 `secure_ai.xml` 的备份排除规则） |
 *
 * 全屏查看器的状态留在**这里**（`viewer`）：弹窗是 Android 件，状态就该跟着能画它的那一层，
 * 共享页面只负责「要看这张」。
 *
 * 用法（`SubpageActivity` 里三行）：
 * ```
 * val images = rememberAiImageStore()
 * val vm = viewModel { AiChatViewModel(…, images = images, apiKeys = apiKeys) }
 * val imageUi = rememberAiImageUi(images) { uris -> uris.forEach(vm::addImage) }
 * ```
 * 拆成「先建 store、再建 UI」两步是因为 ViewModel 与 UI 都要它，而 UI 又要 ViewModel
 * （选图结果直接喂 `addImage`）——一次建完会绕成环。
 */
@Composable
fun rememberAiImageStore(): AiImageAttachments {
    val context = LocalContext.current
    return remember(context) { AiImageStoreAttachments(Graph.aiImageStore(context)) }
}

@Composable
fun rememberAiImageUi(store: AiImageAttachments, onPicked: (List<Any>) -> Unit): AiImageUi {
    val context = LocalContext.current
    var viewer by remember { mutableStateOf<String?>(null) }
    // 宿主每次重组都会给一个新 lambda，但选图回调是延迟触发的：取最新的那个，别留旧的
    val onPickedState = rememberUpdatedState(onPicked)
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(AiImageStore.MAX_IMAGES),
    ) { uris -> onPickedState.value(uris.toList()) }
    val ui = remember(store, picker) {
        AiImageUi(
            store = store,
            thumb = { fileName, modifier, contentDescription ->
                AiThumb(
                    fileName = fileName,
                    modifier = modifier,
                    contentDescription = contentDescription,
                )
            },
            openViewer = { viewer = it },
            pickImages = {
                picker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            },
            pasteImages = { readClipboardImages(context) },
        )
    }
    viewer?.let { name ->
        ImageViewerDialog(
            fileName = name,
            onDismiss = { viewer = null },
            // ai_img 目录的口径在 ImageSource.AiNotice（与迁移前一致）
            source = ImageSource.AiNotice,
        )
    }
    return ui
}

/** API Key 的读写（`AiCredentialStore` = EncryptedSharedPreferences）。 */
@Composable
fun rememberAiApiKeys(): AiApiKeys {
    val context = LocalContext.current.applicationContext
    return remember(context) {
        // `Graph.aiCredentialStore` 是进程单例：Keystore 解密整个进程只付一次
        val store = Graph.aiCredentialStore(context)
        AiApiKeys(
            read = { store.readApiKey() },
            save = { store.saveApiKey(it) },
            clear = { store.clear() },
        )
    }
}

/** 从系统剪贴板取图片 URI（截图后直接粘，不用去相册）。 */
private fun readClipboardImages(context: Context): List<Uri> {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        ?: return emptyList()
    val clip = manager.primaryClip ?: return emptyList()
    return (0 until clip.itemCount).mapNotNull { index ->
        clip.getItemAt(index).uri?.takeIf { it.scheme == "content" }
    }
}

/**
 * [AiImageAttachments] 的 Android 实现：转接 `AiImageStore`。
 *
 * `clear()` 连**缩略图的内存缓存**一起清（`clearAiImageCache`）：迁移前这件事由
 * `AiChatViewModel` 自己调，现在 ViewModel 在共享层看不见那个缓存，
 * 口径并进「清空」这一个动作，避免出现「文件清了、缩略图还挂在内存里」的半清状态。
 */
private class AiImageStoreAttachments(private val store: AiImageStore) : AiImageAttachments {

    override val maxImages: Int get() = AiImageStore.MAX_IMAGES

    override suspend fun import(source: Any): String? =
        (source as? Uri)?.let { store.importUri(it)?.fileName }

    override suspend fun delete(fileName: String) {
        store.fileFor(fileName).delete()
    }

    override suspend fun clear() {
        store.clear()
        clearAiImageCache()
    }

    override fun payload(fileName: String): Any? = store.fileFor(fileName).takeIf { it.isFile }
}
