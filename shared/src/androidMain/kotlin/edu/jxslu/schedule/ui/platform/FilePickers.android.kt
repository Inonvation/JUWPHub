package edu.jxslu.schedule.ui.platform

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * [rememberTextFilePicker] / [rememberFileSaver] 的 Android 实现（iOS 移植 S8，2026-11-25）：
 * SAF 的 `OpenDocument` 与 `CreateDocument`。
 *
 * 这两段是从 `:app/.../ui/week/WeekScreenPlatform.kt` **原样搬进来**的，行为一字不改：
 *
 * | 动作 | 合同 | 细节 |
 * |---|---|---|
 * | 选文件 | `ActivityResultContracts.OpenDocument()` | mime 给一串（json / text / 八位字节流，最后一位是任意类型兜底），让系统把 .json 也列出来；**读文件在 IO 线程**（大备份 JSON 有几 MB） |
 * | 另存 | `ActivityResultContracts.CreateDocument(mimeType)` | `openOutputStream(uri, "wt")` 写 UTF-8；写失败或用户取消一律 `false` |
 *
 * ## mime 为什么是 [rememberFileSaver] 的参数
 *
 * `CreateDocument` 的 mime 在建 launcher 那一刻就固化进合同里了，事后改不了。所以「存 CSV」与
 * 「存 JSON」在 Android 上是两个 launcher，调用方按 mime 分派——这条约束顺着缝的形状传到了公共层。
 *
 * ## 为什么读文件要切线程、写文件也要
 *
 * 搬迁前 `WeekScreenPlatform.kt` 就是这个口径：`ContentResolver` 的 IO 读写在主线程跑会掉帧
 * （几 MB 的 JSON 足够看出卡顿），所以读走 `Dispatchers.IO`，写用 `withContext(Dispatchers.IO)`
 * 包住一次完整的开流 + 写完 + 关闭。**不要**为了少一层 while 把它挪回主线程。
 */
@Composable
actual fun rememberTextFilePicker(onPicked: (String?) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // 回调可能随重组换新实例（`:app` 每次点击都会塞一个新的转发闭包），launcher 却只注册一次，
    // 所以读的那一侧要拿「最新」的那个。
    val currentOnPicked = rememberUpdatedState(onPicked)

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) {
            // 用户取消：与原实现同一个口径（给 null，不给空串）
            currentOnPicked.value(null)
        } else {
            scope.launch {
                currentOnPicked.value(readUriText(context, uri))
            }
        }
    }

    return remember(launcher) {
        {
            launcher.launch(
                arrayOf("application/json", "text/plain", "application/octet-stream", "*/*"),
            )
        }
    }
}

@Composable
actual fun rememberFileSaver(
    mimeType: String,
    onSaved: (Boolean) -> Unit,
): (fileName: String, text: String) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentOnSaved = rememberUpdatedState(onSaved)

    // 待写入的文本：`CreateDocument` 的回调里拿不到调用时刻的文本，只能先存下来。
    var pendingText by remember { mutableStateOf<String?>(null) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(mimeType),
    ) { uri: Uri? ->
        val text = pendingText
        pendingText = null
        if (text == null) {
            // 没人点过返回的那个动作（launcher 只能由它拉起）：不回调，与搬迁前 pending == null 同口径
            return@rememberLauncherForActivityResult
        }
        if (uri == null) {
            // 用户取消
            currentOnSaved.value(false)
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    // "wt" 的 t 是「truncate」：覆盖同名文件时不能留旧内容的尾巴
                    context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                        out.write(text.toByteArray(Charsets.UTF_8))
                    } != null
                }.getOrDefault(false)
            }
            currentOnSaved.value(ok)
        }
    }

    return remember(launcher) {
        { fileName, text ->
            pendingText = text
            launcher.launch(fileName)
        }
    }
}

/**
 * SAF `Uri` → UTF-8 文本；打不开或读取失败回 `null`。
 *
 * 与 `:app/.../ui/common/UriText.kt` 的 `readTextFromUri` 逐字同义（那边还有「我的 → 课表数据」
 * 一个调用方，所以不能直接删）。跨模块也共用不了那一份：`:shared` 看不见 `:app`——
 * 两边各留一份是模块边界决定的，不是又抄了一遍。
 */
private suspend fun readUriText(context: Context, uri: Uri): String? =
    withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openInputStream(uri)?.use {
                it.readBytes().toString(Charsets.UTF_8)
            }
        }.getOrNull()
    }
