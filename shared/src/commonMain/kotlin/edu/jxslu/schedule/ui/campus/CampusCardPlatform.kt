package edu.jxslu.schedule.ui.campus

import edu.jxslu.schedule.domain.BalanceAlertSource

/**
 * 校园卡设置页 / 付款码页要的**平台动作**（iOS 移植 Y1，2026-11-25）。
 *
 * 三个接口都不进 `platform/AppServices`：按 `design/ios-port.md` §11.2 ④，它们不是
 * 「一个服务」，而是「本平台有没有这项能力」。三处调用点（Android 的 `SubpageActivity` /
 * 侧栏 / 充值宿主，iOS 的 `IosRouteContent`）各自传 `null` 或一个实现，页面据此决定
 * 渲染与否——给一个点了没反应的入口比没有更糟。
 *
 * | 槽位 | Android | iOS（本版） | null 的后果 |
 * |---|---|---|---|
 * | [CampusAlertScheduler] | `ui/reminder/BalanceAlertReminder`（AlarmManager + 通知渠道） | 无 | 「余额提醒」整节不渲染 |
 * | [CampusNotificationPermission] | `rememberLauncherForActivityResult` + `POST_NOTIFICATIONS` | 无 | 同上（它与提醒同生共死） |
 * | [PayCodeWindowGuard] | `FLAG_SECURE` + `screenBrightness` | 无 | 付款码页不加防截屏、不调亮度，码本身照常显示 |
 *
 * **不做什么**：不按这三个能力新造 `expect/actual`。两端行为差异已经在接口外面表达完了
 * （Android 有实现、iOS 传 null），再套一层平台缝只会得到一份「两边都得写、写错不报」的空壳。
 */

/**
 * 余额提醒的排程钩子：设置改完要按新规则重排，凭证关掉要撤销周期任务。
 *
 * 两个动作在 Android 侧都是 `suspend`（`BalanceAlertReminder` 要读偏好、写 WorkManager），
 * 所以这里也声明成挂起，调用方原样 `alerts?.xxx()` 即可。
 *
 * **null = 本平台没有余额提醒**（iOS 还没有 `UNUserNotificationCenter` 与后台周期任务，
 * 见 `design/ios-port.md` §12.1）：两个开关整节不渲染，六个偏好也不会被写——
 * 写了没有读者，只会变成一堆「亮着但永远不生效的死开关」。
 */
interface CampusAlertScheduler {

    /** 某个来源（电费 / 一卡通）的阈值或开关变了，按新规则重排一次。 */
    suspend fun onSettingsChanged(source: BalanceAlertSource)

    /** 关凭证后两个提醒都关了，撤销周期任务（不白唤醒设备）。 */
    suspend fun ensurePeriodicWork()
}

/**
 * 通知权限申请（Android `POST_NOTIFICATIONS`，API 33+）。
 *
 * [request] 在用户打开提醒开关时调用一次；被拒时回调 `onDenied`，页面给一句
 * 「未授予通知权限，提醒不会显示」——**不阻塞开关本身**（口径同「我的 → 上课提醒」：
 * 功能在系统设置里授权后自动生效）。
 *
 * **null = 本平台没有可申请的通知权限**：与 [CampusAlertScheduler] 成对出现，
 * 两个都传 null 时「余额提醒」整节不渲染。
 */
interface CampusNotificationPermission {

    /** 发起申请；用户拒绝时回调 [onDenied]。 */
    fun request(onDenied: () -> Unit)
}

/**
 * 付款码页的窗口能力：进页防截屏 + 亮度拉满，离页恢复。
 *
 * 为什么收成一个成对的对象而不是两个 lambda：`enter` 要记住「进入前的亮度」，
 * 只能由实现方自己持有并在 [exit] 里恢复——拆成「进 / 出」两个独立回调，
 * 「谁负责恢复」就变成调用方的口头约定（`design/ios-port.md` §11.2 ④ 的唯一例外是
 * 「成对副作用由实现方收口」）。
 *
 * Android 实现见 `SubpageActivity` 的 `payCodeWindowGuard`：两个属性各管各的
 * （`FLAG_SECURE` 与 `screenBrightness`），离页两者都复位。
 *
 * **null = 本平台没有这两项能力**（iOS 这版没接 `UIScreen.brightness`，也没做录屏防护）：
 * 付款码页照常出码，只是不加这两道保护。**不要**给空实现——那会让人以为做了防护。
 */
interface PayCodeWindowGuard {

    /** 进页：禁止截屏、把屏幕调到最亮，并记住原值。 */
    fun enter()

    /** 离页：撤销 [enter] 的两项改动，恢复原值。 */
    fun exit()
}
