package edu.jxslu.schedule.ui.campus

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import edu.jxslu.schedule.Graph
import edu.jxslu.schedule.data.jw.JwVpnDetector
import edu.jxslu.schedule.data.local.juwDatabase
import edu.jxslu.schedule.domain.BalanceAlertSource
import edu.jxslu.schedule.ui.reminder.BalanceAlertReminder

/**
 * 校园卡两页三个平台槽位的 **Android 实现**（iOS 移植 Y1，2026-11-25）。
 *
 * 页面与 ViewModel 现在都在 `shared/commonMain`，那边只声明「要什么能力」
 * （`ui/campus/CampusCardPlatform.kt` 三个接口）；这份文件负责把 Android 的真实实现装进去：
 *
 * | 槽位 | 这里给什么 | 原来写在哪 |
 * |---|---|---|
 * | 余额提醒排程 | [BalanceAlertReminder]（`AlarmManager` + 通知渠道） | `CampusCardViewModel` 里直接调 |
 * | 通知权限 | `rememberLauncherForActivityResult` + `POST_NOTIFICATIONS` | `CampusCardSettingsScreen` 里直接申请 |
 * | 付款码窗口 | `FLAG_SECURE` + `screenBrightness` | `PayCodeScreen` 的 `DisposableEffect` |
 *
 * 三份都是原样搬运：请求时机、拒绝后的文案、亮度恢复的原值一个字没改，
 * 「离页恢复」的成对语义收在 [PayCodeWindowGuard] 的实现里（它自己记原亮度）。
 *
 * 为什么留在这里而不是进共享层：`BalanceAlertReminder` 是 `AlarmManager` + `NotificationCompat`，
 * 权限申请要 `ActivityResultLauncher`，窗口属性要 `android.view.Window`——三样都只有 Android 有。
 */

/**
 * Android 侧装配一份 [CampusCardViewModel]（校园卡设置页 / 侧栏 / 充值宿主三个调用点共用）。
 *
 * 三个调用点原先各写 `CampusCardViewModel.Factory(context, autoFetch)`；搬进共享层后
 * 那个工厂不存在了（它的构造签名里不能有 `Context`），改由宿主在这里拼一次。
 * **必须仍走 `viewModel { … }`**：配置变更（旋转）时要保住 VM，否则到账轮询会被重置。
 */
fun campusCardViewModel(
    context: Context,
    /** 侧栏 / 充值宿主传 false：冷启动没人看，展开或打开弹层时再刷。 */
    autoFetch: Boolean = true,
): CampusCardViewModel = CampusCardViewModel(
    repo = Graph.yktRepository(context),
    credentialStore = Graph.yktCredentialStore(context),
    prefs = Graph.displayPrefs(context),
    db = juwDatabase(context),
    autoFetch = autoFetch,
    alerts = androidCampusAlertScheduler(context),
    vpnActive = { JwVpnDetector.isVpnActive(context) },
)

/** 余额提醒的排程：两个动作转给 `BalanceAlertReminder`（它内部读偏好、写 WorkManager）。 */
private fun androidCampusAlertScheduler(context: Context): CampusAlertScheduler =
    object : CampusAlertScheduler {
        override suspend fun onSettingsChanged(source: BalanceAlertSource) {
            BalanceAlertReminder.onSettingsChanged(context, source)
        }

        override suspend fun ensurePeriodicWork() {
            BalanceAlertReminder.ensurePeriodicWork(context)
        }
    }

/**
 * 通知权限申请（`POST_NOTIFICATIONS`，API 33+）。
 *
 * 与搬迁前逐字一致：已授予 / 系统低于 33 就什么都不做（开关照常打开，用户事后在系统设置里
 * 授权即生效）；拒绝时把通知权交回页面，由页面给那句「未授予通知权限，提醒不会显示」。
 */
@Composable
fun rememberCampusNotificationPermission(): CampusNotificationPermission {
    val context = LocalContext.current
    // 每次申请时替换：launcher 的回调是组合期注册的，要通知的是「这一次」的页面
    val deniedCallback = remember { mutableStateOf<(() -> Unit)?>(null) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val callback = deniedCallback.value
        deniedCallback.value = null
        if (!granted) callback?.invoke()
    }
    return remember(context, launcher) {
        object : CampusNotificationPermission {
            override fun request(onDenied: () -> Unit) {
                if (Build.VERSION.SDK_INT < 33) return
                val granted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED
                if (granted) return
                deniedCallback.value = onDenied
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

/**
 * 付款码页的窗口能力：[FLAG_SECURE] 与亮度拉满。
 *
 * 两个属性各管各的，[exit] 里都复位——`screenBrightness` 要恢复成**进入前那个值**
 * （可能是「跟随系统」的 `BRIGHTNESS_OVERRIDE_NONE`），所以原值由实现自己持有。
 * `context` 不是 Activity（理论上不会发生，本页只在二级页窗口里组合）时返回 null =
 * 这页不加防护，而不是崩。
 */
@Composable
fun rememberPayCodeWindowGuard(): PayCodeWindowGuard? {
    val window = (LocalContext.current as? Activity)?.window ?: return null
    return remember(window) {
        object : PayCodeWindowGuard {
            /** 进入前的亮度（`BRIGHTNESS_OVERRIDE_NONE` = 跟随系统）。 */
            private var previousBrightness: Float =
                WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE

            override fun enter() {
                previousBrightness = window.attributes.screenBrightness
                window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                window.attributes = window.attributes.apply {
                    screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
                }
            }

            override fun exit() {
                window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                window.attributes = window.attributes.apply {
                    screenBrightness = previousBrightness
                }
            }
        }
    }
}
