package edu.jxslu.schedule.ui.common

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import edu.jxslu.schedule.platform.requireAppContext

/**
 * [sharePlainText] / [shareFile] 的 Android 实现（iOS 移植 P2，2026-11-24）：
 * `ACTION_SEND` + `Intent.createChooser`，Context 走 `platform/AppDirs.android.kt` 那一个注入点。
 *
 * ## 与 `:app` 里原实现的两处差异（都是「拿不到 Activity」的直接后果）
 *
 * 原来的 `ui/common/ShareIntents.kt` 走 `startActivityOutsideApp`：它能判出 Context 是不是
 * `SubpageActivity`，拉起外部应用前把二级页的开/关过渡换成静止，避免从分享面板返回时
 * 重放一次右推入。
 *
 * 1. 这里拿到的是 **applicationContext**（不是 Activity），所以必须加
 *    `FLAG_ACTIVITY_NEW_TASK`，否则 `startActivity` 直接抛
 *    `AndroidRuntimeException: Calling startActivity() from outside of an Activity context`。
 * 2. 因此**过渡抑制这一版没有**：从二级页里的分享面板返回时，可能出现一次多余的推入动画。
 *    功能不受影响，只是观感。
 *
 * shortcut: 上面第 2 条在「分享/导出页搬进 commonMain」那一批解决——届时把
 * `startActivityOutsideApp` 抽成与 [ShareTarget] 同形状的一个可选回调（`var` 注入），
 * 这里优先用它、没有就退回本文件这套 NEW_TASK 路径。现在不做，是因为这条缝还没有调用方
 * 搬过来，提前注入等于凭空造一个没人写的接线点。
 */
actual fun sharePlainText(text: String, chooserTitle: String): Boolean = runCatching {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    requireAppContext("sharePlainText()").launchOutsideActivity(
        Intent.createChooser(send, chooserTitle),
    )
}.isSuccess

/**
 * [shareFile] 的 Android 实现。
 *
 * **只接受 `content://`**：分享一个 `file://` 或裸路径在 Android 7+ 会抛
 * `FileUriExposedException`（必崩，不是「可能失败」），所以这里先判掉并返回 false。
 * 文件要先由 `:app` 的 FileProvider 转成 uri——authority 是 `${applicationId}.fileprovider`，
 * debug 与 release 两个包名不同，**不能在共享层写死**。
 *
 * 授予读权限：`FLAG_GRANT_READ_URI_PERMISSION` 让目标 App 能读到那个 uri。没有它，目标 App
 * 打开就是 `SecurityException`，表现为「分享出去了但附件是空的」。
 */
actual fun shareFile(fileRef: String, mime: String, chooserTitle: String): Boolean = runCatching {
    val uri = Uri.parse(fileRef)
    if (uri.scheme != ContentResolver.SCHEME_CONTENT) {
        false
    } else {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        requireAppContext("shareFile()").launchOutsideActivity(
            Intent.createChooser(send, chooserTitle),
        )
        true
    }
}.getOrDefault(false)

/**
 * 从非 Activity 的 Context 拉起外部应用：加 `FLAG_ACTIVITY_NEW_TASK` 后 `startActivity`。
 *
 * 抛出的异常交给调用方的 `runCatching`：没装能处理这个 MIME 的 App 时系统抛
 * `ActivityNotFoundException`，对调用方就是「没有可用目标」。
 */
private fun Context.launchOutsideActivity(intent: Intent) {
    startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
