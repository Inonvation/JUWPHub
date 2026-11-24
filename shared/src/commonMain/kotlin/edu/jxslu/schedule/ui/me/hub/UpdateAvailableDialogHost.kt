package edu.jxslu.schedule.ui.me.hub

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import edu.jxslu.schedule.platform.Services
import edu.jxslu.schedule.platform.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

/**
 * 启动自动检查的弹窗宿主：collect [UpdateAvailableBus]，非空就弹。
 * 挂在 MainActivity 根层（`CampusPayResultDialogHost` 旁）；用户关掉弹窗
 * （稍后 / 忽略 / 下载）时才消费——转屏重建时弹窗还在。
 *
 * ## iOS 移植 W1（2026-11-25）搬进 `commonMain`
 *
 * 原来靠 `Graph.appScope` / `Graph.repository(context)` 两样装配期单例，现在都在
 * `platform/AppServices` 里（[Services.current] 的 `appScope()` / `scheduleRepository()`），
 * 所以这一页不再需要 `Context`。唯一剩下的平台物是打开链接：[onOpenUrl] 由宿主注入
 * （Android 是 `ACTION_VIEW` + `startActivityOutsideApp` 的窗口过渡，iOS 是 `UIApplication.open`）。
 *
 * 「忽略此版本」这条写必须挂在**进程级** scope 上：换成组合级 `rememberCoroutineScope` 的话，
 * 转屏 / 关窗会把还没落盘的忽略记录一起取消，下次冷启动又会弹同一个版本。
 *
 * @param enabled 展示闸门——引导未走完（onboardingSeen=false）或「同步完成」
 *   结果弹窗还没消费时先不抢镜（防叠窗），条件满足后自动接着弹。
 * @param onOpenUrl 打开下载页 / 开源仓库；返回 null = 成功，非 null = 失败文案。
 */
@Composable
fun UpdateAvailableDialogHost(
    enabled: Boolean,
    onOpenUrl: (url: String) -> String?,
) {
    val manifest by UpdateAvailableBus.manifest.collectAsStateWithLifecycle()
    val current = manifest
    if (enabled && current != null) {
        UpdateAvailableDialog(
            manifest = current,
            onDismiss = { UpdateAvailableBus.consume() },
            onIgnore = {
                UpdateAvailableBus.consume()
                // 写偏好走 appScope：不随页面组合销毁；与关弹窗同刻落盘
                Services.current.appScope().launch {
                    Services.current.scheduleRepository().setIgnoredUpdateVersion(current.version)
                }
            },
            onOpenUrl = onOpenUrl,
        )
    }
}
