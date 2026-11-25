package edu.jxslu.schedule.ui.score

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import edu.jxslu.schedule.Graph
import edu.jxslu.schedule.JwImportActivity
import edu.jxslu.schedule.SubpageActivity
import edu.jxslu.schedule.SubpageScreen
import edu.jxslu.schedule.data.jw.JwAutoLogin
import edu.jxslu.schedule.ui.common.NoticeTone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 成绩单两页在 Android 侧的宿主包装（2026-11-26 两页搬进 `shared/commonMain` 之后）。
 *
 * ## 这两个函数为什么在 `:app`
 *
 * 共享层的两页（[TranscriptScreen] / [TranscriptHistoryContent]）按 `design/ios-port.md`
 * §11.2 ④ 把**一切平台动作**收成可空回调：打开 / 分享 / 另存要 `Context`、`FileProvider`、
 * SAF 的 `rememberLauncherForActivityResult`，共享层一样都没有。这里把它们逐个接上，
 * 一个不落地交给页面——**不给 null**，因为 Android 这边每一条都真的能执行。
 *
 * ## 为什么 `TranscriptHistoryScreen` 与共享层的函数不同名
 *
 * 这一页的调用点在 `SubpageActivity`（`SubpageScreen.TRANSCRIPTS`），而那个文件正被另一条
 * 并行分支改着，本轮不许动它；它调的是 `TranscriptHistoryScreen(onBack)`。所以这里保留
 * **同名同签名**的宿主包装，共享层那份叫 `TranscriptHistoryContent`——两个 FQN 不同，
 * `SubpageActivity` 一个字都不用改，同时 Android 侧的行为与搬移前逐字一致。
 *
 * ## 失败提示为什么用 `Toast`
 *
 * 「没有 PDF 阅读器」「没有分享目标」这两句原来走页面的 Snackbar，而 Snackbar 归页面所有，
 * 宿主拿不到。共享层不该为这两种边角情况多开一条提示通道（§11.2 ④：宁可少一个入口），
 * Android 侧就用手边最轻的 Toast；页面自己那几条提示（保存成功 / 取消 / 失败）仍然走
 * 共享层的 Snackbar。
 */
@Composable
fun TranscriptHistoryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val notify = rememberAndroidNotify(context)
    TranscriptHistoryContent(
        onBack = onBack,
        onOpenPdf = { path -> openTranscriptPdf(context, path, notify) },
        onSharePdf = { path -> shareTranscriptPdf(context, path, notify) },
        // 顶栏「成绩单」：与搬移前同一跳（教务网页窗口，电脑版/手机版/统一认证都现成）
        onOpenWebPage = { title, url -> JwImportActivity.startBrowse(context, title, url) },
    )
}

/**
 * 导出成绩单窗口的内容（`TranscriptActivity` 调它）。
 *
 * 除三个文件动作外还接了两处：转「最近导出」二级页；以及自动填表脚本——**密码只在这条闭包里
 * 从保险箱直接拼进 JS**，不出 `CredentialVault`，也不经共享层的 `AccountCredentials`
 * （那边按设计只回学号）。
 */
@Composable
fun TranscriptScreenHost(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val notify = rememberAndroidNotify(context)

    // 待写入的目标：`CreateDocument` 的回调里拿不到「点下去那一刻的文件路径」，
    // 只能先存下来（同 `ui/platform/FilePickers.android.kt` 的 pendingText 手法）
    var pendingPath by remember { mutableStateOf<String?>(null) }
    var pendingResult by remember { mutableStateOf<((TranscriptSaveOutcome) -> Unit)?>(null) }

    // 合同里的 mime 在建 launcher 那一刻固化，事后改不了——所以本页只有一个 PDF launcher
    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(TRANSCRIPT_PDF_MIME),
    ) { uri: Uri? ->
        val path = pendingPath
        val onResult = pendingResult
        pendingPath = null
        pendingResult = null
        // 没人点过返回的那个动作（launcher 只能由它拉起）：不回调
        if (path == null || onResult == null) return@rememberLauncherForActivityResult
        if (uri == null) {
            // 用户取消
            onResult(TranscriptSaveOutcome.Cancelled)
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            onResult(withContext(Dispatchers.IO) { copyToUri(context, uri, path) })
        }
    }

    TranscriptScreen(
        onBack = onBack,
        onOpenPdf = { path -> openTranscriptPdf(context, path, notify) },
        onSharePdf = { path -> shareTranscriptPdf(context, path, notify) },
        onSavePdf = { path, suggestedName, onResult ->
            pendingPath = path
            pendingResult = onResult
            saveLauncher.launch(suggestedName)
        },
        onOpenHistory = { SubpageActivity.start(context, SubpageScreen.TRANSCRIPTS) },
        casAutoFillScript = {
            Graph.credentialVault(context).readCas()
                ?.let { cred -> JwAutoLogin.fillJs(cred.username, cred.password) }
        },
    )
}

/** 宿主侧的两句边角提示（理由见文件头「失败提示为什么用 Toast」）。 */
@Composable
private fun rememberAndroidNotify(context: Context): (String, NoticeTone) -> Unit = remember(context) {
    { message, _ -> Toast.makeText(context, message, Toast.LENGTH_SHORT).show() }
}

/**
 * 把沙箱里的 PDF 抄进用户选的 `content://`。
 *
 * 三档结果而不是布尔：用户取消（上层已单独处理）与真的写失败，用户该看到的话不一样。
 * 异常原文保留下来——`openOutputStream` 失败时那句话（例如「找不到所选项」）比一句
 * 笼统的「保存失败」有用得多。
 */
private fun copyToUri(context: Context, uri: Uri, path: String): TranscriptSaveOutcome {
    val failure = runCatching {
        context.contentResolver.openOutputStream(uri)?.use { out ->
            File(path).inputStream().use { it.copyTo(out) }
        } ?: error("无法写入所选位置")
    }.exceptionOrNull()
    return if (failure == null) {
        TranscriptSaveOutcome.Saved
    } else {
        TranscriptSaveOutcome.Failed(failure.message.orEmpty())
    }
}
