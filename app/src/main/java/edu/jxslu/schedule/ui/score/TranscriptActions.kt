package edu.jxslu.schedule.ui.score

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import edu.jxslu.schedule.startActivityOutsideApp
import edu.jxslu.schedule.ui.common.NoticeTone
import edu.jxslu.schedule.ui.common.shareFile
import java.io.File

/**
 * 打开 / 分享成绩单一类本机文件（2026-11-26 导出页搬进 `shared/commonMain` 后收窄到本文件）。
 *
 * 迁移前这些函数都在 `:app` 的 `TranscriptScreen` 里直接调 `FileProvider.getUriForFile`；
 * 页面搬走后，共享层只交出**绝对路径**（`data/repo/TranscriptStore` 的返回值），
 * 「路径 → `content://` → `Intent`」这一段留在 Android 侧，由 `ui/score/TranscriptHosts.kt`
 * 装成页面参数传回去（`design/ios-port.md` §11.2 ④）。
 *
 * 为什么路径不能直接当 `file://` 用：`FileUriExposedException` 是必崩的（API 24 起禁
 * 跨应用传 `file://`），而 `transcripts/` 已经由 `res/xml/file_paths.xml` 授权给
 * `<applicationId>.fileprovider`——白名单只有这一个目录，别的文件借不出去。
 *
 * [openPdf] 仍保留 Uri 形态：[openPdf] 还有第二个调用方（问渠的记录页下载证明，走
 * `WenquCertStore.uriOf`），那边的 Uri 不是 `transcripts/` 里的成绩单，不能拿路径去反推。
 * 全部函数都不**抛**：拿不到 Uri 只说明文件在动作之间被删了，给一句提示即可。
 */

/** 成绩单的 FileProvider 临时授权地址；文件已被删或不在白名单目录里时返回 null。 */
internal fun transcriptUri(context: Context, path: String): Uri? = runCatching {
    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(path))
}.getOrNull()

/** 打开导出好的成绩单（共享层交来的是沙箱里的绝对路径）。 */
internal fun openTranscriptPdf(
    context: Context,
    path: String,
    notify: (String, NoticeTone) -> Unit,
) {
    val uri = transcriptUri(context, path)
    if (uri == null) {
        notify("成绩单文件已不在了，请重新导出", NoticeTone.Warning)
        return
    }
    openPdf(context, uri, notify)
}

/** 分享导出好的成绩单（同上）。 */
internal fun shareTranscriptPdf(
    context: Context,
    path: String,
    notify: (String, NoticeTone) -> Unit,
) {
    val uri = transcriptUri(context, path)
    if (uri == null) {
        notify("成绩单文件已不在了，请重新导出", NoticeTone.Warning)
        return
    }
    val ok = shareFile(context, uri, TRANSCRIPT_PDF_MIME, "分享成绩单")
    if (!ok) notify("没有可用的分享目标", NoticeTone.Warning)
}

/**
 * 打开一个 PDF：交给系统里能看 PDF 的应用。
 *
 * Uri 必须是 FileProvider 的临时授权地址（`FLAG_GRANT_READ_URI_PERMISSION`），
 * 目标应用只在这一次读取里有权限。失败只有一种情况：设备上没有任何 PDF 阅读器，
 * 这时给一句提示并引导改用「分享」，而不是静默什么都不发生。
 */
internal fun openPdf(context: Context, uri: Uri, notify: (String, NoticeTone) -> Unit) {
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, TRANSCRIPT_PDF_MIME)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    val ok = runCatching { context.startActivityOutsideApp(intent) }.isSuccess
    if (!ok) notify("没有能打开 PDF 的应用，可以改用「分享」", NoticeTone.Warning)
}
