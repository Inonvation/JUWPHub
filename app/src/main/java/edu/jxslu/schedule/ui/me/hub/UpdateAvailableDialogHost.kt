package edu.jxslu.schedule.ui.me.hub

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import edu.jxslu.schedule.Graph
import edu.jxslu.schedule.startActivityOutsideApp
import kotlinx.coroutines.launch

/**
 * 打开站外链接（下载页 / 开源仓库）；失败返回提示文案，成功 null。同包内（关于页）共用。
 *
 * 留在 `:app` 的理由：`ACTION_VIEW` 是平台能力，且拉起外部应用前要先把本窗口的推入/滑出
 * 过渡换成静止（`startActivityOutsideApp`，2026-09-26 修「页面跳动」）——那是 Android 的窗口
 * 事务，共享层没有对应物。共享层的 [UpdateAvailableDialog] 收一个 `onOpenUrl` 参数拿它。
 */
internal fun openUrl(context: Context, url: String): String? = try {
    context.startActivityOutsideApp(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    null
} catch (_: ActivityNotFoundException) {
    "没有可打开链接的应用"
}

/**
 * 启动自动检查的弹窗宿主：collect [UpdateAvailableBus]，非空就弹。
 * 挂在 MainActivity 根层（`CampusPayResultDialogHost` 旁）；用户关掉弹窗
 * （稍后 / 忽略 / 下载）时才消费——转屏重建时弹窗还在。
 *
 * 为什么宿主留在 `:app`（iOS 移植 U1，2026-11-25）：它要 `Graph.appScope` 与
 * `Graph.repository` 两样装配期单例，而「忽略此版本」这条写必须挂在进程级 scope 上——
 * 换成组合级 `rememberCoroutineScope` 的话，转屏/关窗会把还没落盘的忽略记录一起取消，
 * 下次冷启动又会弹同一个版本。弹窗本体在共享层（`ui/me/hub/UpdateAvailableDialog.kt`）。
 *
 * @param enabled 展示闸门——引导未走完（onboardingSeen=false）或「同步完成」
 *   结果弹窗还没消费时先不抢镜（防叠窗），条件满足后自动接着弹。
 */
@Composable
fun UpdateAvailableDialogHost(enabled: Boolean) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val manifest by UpdateAvailableBus.manifest.collectAsStateWithLifecycle()
    val current = manifest
    if (enabled && current != null) {
        UpdateAvailableDialog(
            manifest = current,
            onDismiss = { UpdateAvailableBus.consume() },
            onIgnore = {
                UpdateAvailableBus.consume()
                // 写偏好走 appScope：不随页面组合销毁；与关弹窗同刻落盘
                Graph.appScope.launch {
                    Graph.repository(appContext).setIgnoredUpdateVersion(current.version)
                }
            },
            onOpenUrl = { url -> openUrl(context, url) },
        )
    }
}
