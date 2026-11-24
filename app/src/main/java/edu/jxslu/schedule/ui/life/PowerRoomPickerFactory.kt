package edu.jxslu.schedule.ui.life

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import edu.jxslu.schedule.Graph

/**
 * 「选择寝室」弹层的 ViewModel 工厂（iOS 移植 S14，2026-11-25）。
 *
 * ## 为什么工厂留在 `:app`
 *
 * `PowerRoomPickerViewModel` 已经搬进 `shared/commonMain`，类本身零平台依赖；但
 * **造它**要两个单例（电费仓库、凭证存储），那两个只有 `Graph` 知道怎么装配，
 * 而 `Graph` 在 `:app`。这与 `ui/week` 的做法一致：共享页收 `viewModel` 参数，
 * 由宿主建好传进去（`WeekScreen(viewModel = weekVm, …)`）。
 *
 * ## 为什么不是 `PowerRoomPickerViewModel.factory(context)`
 *
 * 搬走的是整个类，伴生对象不能留一个读 `Graph` 的成员（那是 `:app` 的类型）。
 * 所以这个函数从伴生对象搬成同包的顶层函数，两个调用点各改一行——
 * `RechargeHost`（侧栏电费卡）与 `CampusCardSettingsScreen`（「寝室电费房间」行）。
 */
fun powerRoomPickerFactory(context: Context): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = PowerRoomPickerViewModel(
        Graph.powerRepository(context.applicationContext),
        Graph.yktCredentialStore(context.applicationContext),
    ) as T
}
