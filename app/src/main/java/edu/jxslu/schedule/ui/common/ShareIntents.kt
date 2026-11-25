package edu.jxslu.schedule.ui.common

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import edu.jxslu.schedule.startActivityOutsideApp
import java.io.File

/**
 * 用系统分享面板发一个文件；返回 false = 没有可用目标。
 *
 * 走 [startActivityOutsideApp]：从分享面板返回时二级页不会重放右推入过渡
 * （`.agents/rules/nav-window.md`「二级页里拉起外部应用一律走它」）。
 */
fun shareFile(context: Context, uri: Uri, mime: String, chooserTitle: String): Boolean =
    runCatching {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivityOutsideApp(Intent.createChooser(send, chooserTitle))
    }.isSuccess

/**
 * 把 `ScheduleShareStore` 落下的**绝对路径**换成 `content://` 内容 Uri。
 *
 * authority 带 `applicationId`（debug 变体是 `edu.jxslu.schedule.debug.fileprovider`），
 * 只能在宿主拼——共享层拿不到包名，所以这一步永远留在 `:app`。
 *
 * 根由 `res/xml/file_paths.xml` 限定（`cache-path` 的 `schedule_share` / `toolbox_share` /
 * `wenqu_cert`，`files-path` 的 `transcripts` / `crash`）：**目录必须在里面**，否则这里抛
 * `IllegalArgumentException`，调用方一律包 `runCatching`。
 *
 * 为什么是函数而不是 `ScheduleShareStore` 的方法：那个类 2026-11-26 搬进了
 * `shared/commonMain`，共享层里没有 `Uri`。两个调用方（课表导出、百宝箱）共用这一份，
 * 别各自拼一遍 authority。
 */
fun shareUriOf(context: Context, path: String): Uri =
    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(path))
