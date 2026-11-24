package edu.jxslu.schedule

import android.content.Context
import edu.jxslu.schedule.data.qzxy.QzxyBluetoothScanner
import edu.jxslu.schedule.data.qzxy.QzxyBoundDevice
import edu.jxslu.schedule.data.qzxy.QzxyClearStore
import edu.jxslu.schedule.data.qzxy.QzxyDebugStore
import edu.jxslu.schedule.data.qzxy.QzxyDeviceRef
import edu.jxslu.schedule.data.qzxy.QzxyDeviceRegistry
import edu.jxslu.schedule.data.qzxy.QzxyDeviceSnapshot
import edu.jxslu.schedule.data.qzxy.QzxyDeviceScanner
import edu.jxslu.schedule.data.qzxy.QzxyDeviceStore
import edu.jxslu.schedule.data.qzxy.QzxyGattCharacteristicInfo
import edu.jxslu.schedule.data.qzxy.QzxyGattLink
import edu.jxslu.schedule.data.qzxy.QzxyLinkDiagnostics
import edu.jxslu.schedule.data.qzxy.QzxyNearbyDevice
import edu.jxslu.schedule.data.qzxy.QzxyPrefs
import edu.jxslu.schedule.data.qzxy.QzxyServiceEntry
import edu.jxslu.schedule.data.qzxy.QzxyWateringRegistry
import edu.jxslu.schedule.data.qzxy.QzxyWateringStore
import edu.jxslu.schedule.domain.QzxyWatering
import edu.jxslu.schedule.ui.qzxy.QzxyViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * 趣智校园页面层（`shared/commonMain` 的 `ui/qzxy`）在 Android 侧的适配器
 * （iOS 移植 V3，2026-11-25）。
 *
 * ## 为什么需要这一层
 *
 * 页面搬进共享层后，`QzxyViewModel` 只认 `data/qzxy/QzxyPlatformSeams.kt` 里的六条缝；
 * 而 `:app` 的那几个类（`QzxyBluetoothScanner` / `QzxyDeviceStore` / …）以 `Context`
 * 为构造参数、且**明确留在 `:app`**（`data/` 目录本轮不动，今日页与侧栏还在读它们）。
 * 于是这里做两件事：
 *
 * 1. **转发**：方法逐个转调原类，`SharedPreferences` 的文件名与键一个不改
 *    （老用户的绑定设备、未结算记账、清除命令候选原地可读）；
 * 2. **换类型**：共享层的镜像类型（[QzxyNearbyDevice] / `QzxyDeviceRef` /
 *    [QzxyServiceEntry]）与 `:app` 的同义类型之间转一次。跨模块同名类会撞 dex 的
 *    `Duplicate program class`，所以镜像类型换了名字，边界就在这几行。
 *
 * ## 链路本身不用适配
 *
 * `QzxyGattLink` 自 S12 起就实现了 `QzxyBluetoothLink`，`qzxyBluetoothLink()` 直接给
 * 那个单例。只有「服务表 / MTU」这两项 GATT 专有读数包在 [AppQzxyLinkDiagnostics] 里。
 *
 * 装配入口见 `GraphServices` 的 `qzxy*()` 与 [QzxyPlatform.viewModel]。
 */
object QzxyPlatform {

    /** 宿主一行建 VM：依赖全从 `Graph` 取，与搬迁前 `QzxyViewModel.Factory` 逐项对应。 */
    fun viewModel(context: Context, autoLoad: Boolean = true): QzxyViewModel = QzxyViewModel(
        repo = Graph.qzxy(context),
        scanner = scanner(context),
        link = Graph.qzxyLink(context),
        diagnostics = AppQzxyLinkDiagnostics(Graph.qzxyLink(context)),
        flowLock = Graph.qzxyFlowLock,
        devices = deviceRegistry(context),
        prefs = prefs(context),
        watering = wateringRegistry(context),
        autoLoad = autoLoad,
    )

    fun scanner(context: Context): QzxyDeviceScanner = AppQzxyScanner(QzxyBluetoothScanner(context))

    fun deviceRegistry(context: Context): QzxyDeviceRegistry =
        AppQzxyDeviceRegistry(Graph.qzxyDevices(context), Graph.appScope)

    fun prefs(context: Context): QzxyPrefs =
        AppQzxyPrefs(Graph.qzxyDebug(context), Graph.qzxyClear(context))

    fun wateringRegistry(context: Context): QzxyWateringRegistry =
        AppQzxyWateringRegistry(Graph.qzxyWatering(context))
}

/** `QzxyBluetoothScanner` → 共享层的扫描缝（只换类型，判定逻辑一行不改）。 */
class AppQzxyScanner(private val scanner: QzxyBluetoothScanner) : QzxyDeviceScanner {

    override fun startScan(
        durationMillis: Long,
        onDevice: (QzxyNearbyDevice) -> Unit,
        onFinished: (Boolean) -> Unit,
        onError: (String) -> Unit,
    ): Boolean = scanner.startScan(
        durationMillis = durationMillis,
        onDevice = { device ->
            onDevice(
                QzxyNearbyDevice(
                    name = device.name,
                    address = device.address,
                    rssi = device.rssi,
                    deviceKey = device.deviceKey,
                ),
            )
        },
        onFinished = onFinished,
        onError = onError,
    )

    override fun stopScan() = scanner.stopScan()
}

/**
 * `QzxyDeviceStore` → 共享层的设备登记缝。
 *
 * [state] 用 `map` + `stateIn(Eagerly)` 转一次：`QzxyDeviceState` 与 `QzxyDeviceSnapshot`
 * 是同一个快照的两个类型，共享层要能订阅（今日页卡片那份实例靠它同步）。
 * 用进程级作用域（`Graph.appScope`）而不是页面作用域：绑定发生在页面里，
 * 而订阅方可能是另一个窗口的 ViewModel。
 */
class AppQzxyDeviceRegistry(
    private val store: QzxyDeviceStore,
    scope: CoroutineScope,
) : QzxyDeviceRegistry {

    override val state: StateFlow<QzxyDeviceSnapshot> =
        store.state
            .map { snapshot ->
                QzxyDeviceSnapshot(
                    bound = snapshot.bound.map(::refOf),
                    lastUsed = snapshot.lastUsed?.let(::refOf),
                )
            }
            .stateIn(
                scope = scope,
                started = SharingStarted.Eagerly,
                initialValue = QzxyDeviceSnapshot(
                    bound = store.list().map(::refOf),
                    lastUsed = store.lastUsed()?.let(::refOf),
                ),
            )

    override fun list(): List<QzxyDeviceRef> = store.list().map(::refOf)

    override fun add(device: QzxyDeviceRef) =
        store.add(QzxyBoundDevice(device.address, device.name))

    override fun remove(address: String) = store.remove(address)

    override fun lastUsed(): QzxyDeviceRef? =
        store.lastUsed()?.let(::refOf)

    override fun setLastUsed(device: QzxyDeviceRef) =
        store.setLastUsed(QzxyBoundDevice(device.address, device.name))

    private fun refOf(device: QzxyBoundDevice) =
        QzxyDeviceRef(device.address, device.name)
}

/**
 * `QzxyDebugStore` + `QzxyClearStore` → 共享层的偏好缝。
 *
 * 两个原类合成一个接口是刻意的：三项偏好（调试日志开关 / 自动结算 / 记住的手机号）
 * 与清除命令候选都是「一个键、读一次、写一次」，介质是同一批 `SharedPreferences`。
 * 落盘文件名与键名逐字沿用（`qzxy_debug` / `qzxy_clear`）。
 */
class AppQzxyPrefs(
    private val debug: QzxyDebugStore,
    private val clear: QzxyClearStore,
) : QzxyPrefs {

    override var logEnabled: Boolean
        get() = debug.logEnabled
        set(value) {
            debug.logEnabled = value
        }

    override var autoSettleEnabled: Boolean
        get() = debug.autoSettleEnabled
        set(value) {
            debug.autoSettleEnabled = value
        }

    override var rememberedPhone: String
        get() = debug.rememberedPhone
        set(value) {
            debug.rememberedPhone = value
        }

    override var preferredClearKey: String?
        get() = clear.preferredKey
        set(value) {
            clear.preferredKey = value
        }
}

/** `QzxyWateringStore` → 共享层的用水记账缝（类型本来就是共享的，只转发）。 */
class AppQzxyWateringRegistry(
    private val store: QzxyWateringStore,
) : QzxyWateringRegistry {

    override val watering: StateFlow<QzxyWatering?> = store.watering

    override fun begin(watering: QzxyWatering) = store.begin(watering)

    override fun clear() = store.clear()
}

/**
 * `QzxyGattLink` 的两项 GATT 专有读数 → 共享层的诊断缝。
 *
 * [QzxyGattCharacteristicInfo.propertyText] 在 `:app` 里按
 * `BluetoothGattCharacteristic.PROPERTY_*` 的位常量算好，这里原样带走——
 * 那些位常量是 Android 专有，共享层不该认识它们。
 */
class AppQzxyLinkDiagnostics(
    private val link: QzxyGattLink,
) : QzxyLinkDiagnostics {

    override val negotiatedMtu: Int get() = link.negotiatedMtu

    override val serviceTable: List<QzxyServiceEntry>
        get() = link.serviceTable.map { entry ->
            QzxyServiceEntry(
                serviceUuid = entry.serviceUuid,
                characteristicUuid = entry.characteristicUuid,
                propertyText = entry.propertyText,
                role = entry.role,
            )
        }
}
