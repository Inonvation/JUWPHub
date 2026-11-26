package edu.jxslu.schedule.ui.qzxy

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import edu.jxslu.schedule.ui.common.AppPermissions

/**
 * 趣智校园页在 Android 宿主侧的两件平台事（iOS 移植 V3，2026-11-25）。
 *
 * 页面层已搬进 `shared/commonMain`（`QzxyScreen` / `QzxyDebugScreen` / `QzxyBillScreen`
 * 都改成「VM 必填 + 平台动作可空回调」）。这两件留在 `:app` 是因为它们都要 `Context`：
 *
 * | 函数 | 为什么必须在 Android 侧 |
 * |---|---|
 * | [rememberQzxyScanPermission] | `rememberLauncherForActivityResult` + `AppPermissions`（`ContextCompat.checkSelfPermission`） |
 * | [rememberQzxyRecharge] | `openQzxyRecharge(context)`：`Intent` + `alipays://` 深链 |
 *
 * 两个都返回 `null` 语义明确的回调：宿主给不起能力时页面整行不渲染。
 */

/**
 * 蓝牙扫描的运行时权限。
 *
 * 返回的回调接一个 `(granted) -> Unit`：已经有权限就**同步**回调 true（与搬迁前一致——
 * 用户点「扫描」立刻开扫，不再多一次系统弹窗）；缺权限就拉起系统对话框，结果回来再回调。
 * 提示文案仍由页面负责，这里只回答「授权了没有」。
 */
@Composable
fun rememberQzxyScanPermission(): (onPermissionResult: (Boolean) -> Unit) -> Unit {
    val context = LocalContext.current
    // 权限结果先落到这里：launcher 的回调拿不到「点扫描那一刻」的那个 lambda
    val pending = remember { PendingScanPermission() }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        val granted = result.values.all { it }
        pending.result?.invoke(granted)
        pending.result = null
    }
    return { onPermissionResult ->
        val missing = AppPermissions.missingBluetoothScan(context)
        if (missing.isEmpty()) {
            onPermissionResult(true)
        } else {
            pending.result = onPermissionResult
            launcher.launch(missing.toTypedArray())
        }
    }
}

/** 一次待回填的权限结果（同一时刻只可能有一次扫描请求在等）。 */
private class PendingScanPermission {
    var result: ((Boolean) -> Unit)? = null
}

/**
 * 拉起支付宝的趣智校园小程序充值页。
 *
 * 返回 false = 本机没有能处理 `alipays://` 的应用（通常是没装支付宝），页面据此给提示。
 * 能否解析取决于 manifest `<queries>` 里 `alipays` scheme 的声明——缺了这条会恒回 false。
 */
@Composable
fun rememberQzxyRecharge(): () -> Boolean {
    val context = LocalContext.current
    return { openQzxyRecharge(context) }
}
