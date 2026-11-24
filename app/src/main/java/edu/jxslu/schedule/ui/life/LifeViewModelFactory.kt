package edu.jxslu.schedule.ui.life

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import edu.jxslu.schedule.Graph
import edu.jxslu.schedule.data.jw.JwVpnDetector
import edu.jxslu.schedule.ui.widget.LifeWidgetSync

/**
 * 侧栏电费卡 ViewModel 的工厂（iOS 移植 U1，2026-11-25）。
 *
 * ## 为什么工厂留在 `:app`
 *
 * `LifeViewModel` 已经搬进 `shared/commonMain`，类本身零平台依赖；但**造它**要三样只有
 * `Graph` 与 Android 知道怎么给的东西：
 *
 * 1. 电费仓库 / 凭证存储 / 读数表三个单例（装配在 `Graph`）；
 * 2. `JwVpnDetector`（`android.net.ConnectivityManager`）——网络类失败的文案要据此分岔；
 * 3. `LifeWidgetSync.refreshLifeWidgets`（`androidx.glance` 桌面小组件）。
 *
 * 与 `powerRoomPickerFactory` 同一套做法：共享类收 `viewModel` 参数，工厂是同包顶层函数，
 * 由宿主（`SideDrawerContent` / `RechargeHost`）调用。
 *
 * ## iOS 侧怎么造
 *
 * `IosServices` 装完之后在 `remember { }` 里直接 `LifeViewModel(...)`，`isVpnActive` 给默认的
 * `{ false }`（iOS 没有这条探测）、`onPowerRead` 用默认空实现（iOS 没有桌面小组件）。
 */
fun lifeViewModelFactory(context: Context): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = LifeViewModel(
        Graph.powerRepository(context.applicationContext),
        Graph.yktCredentialStore(context.applicationContext),
        Graph.powerReadingStore(context.applicationContext),
        isVpnActive = { JwVpnDetector.isVpnActive(context.applicationContext) },
        // 读数已落 Room：顺手镜像一次桌面生活小组件的电费副行（零网络）
        onPowerRead = { LifeWidgetSync.refreshLifeWidgets(context.applicationContext) },
    ) as T
}
