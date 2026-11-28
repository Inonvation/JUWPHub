package edu.jxslu.schedule.data.qzxy

import kotlinx.coroutines.flow.StateFlow

/**
 * iOS 侧的蓝牙链路（iOS 移植，2026-11-28）：把 [QzxyBluetoothLink] 四个动作转给 CoreBluetooth 引擎。
 *
 * 上一版（S12，2026-11-24）是「立刻回失败」的桩，原因写在当时的 KDoc 里：协议与状态机已在
 * 共享层，缺的只是射频那一层。现在那一层由 [IosQzxyCentral] 实现，本类回到「只做转接」的位置——
 * 超时、复用、分包、写回执的语义全在引擎里，`QzxyWaterFlow` 与 `QzxyViewModel` 一行没改。
 *
 * ## 地址口径（与 Android 不同，且改不了）
 *
 * [ensureConnected] 的参数在 Android 是 MAC（`AA:BB:…`），在这端是
 * `CBPeripheral.identifier` 的 UUID 字符串。`IosQzxyScanner` 产出的 `address` 就是它，
 * 已绑定设备存的也是它——所以「用一次扫出来的地址去连」这条链在两端都自洽。
 * Apple 不公开蓝牙地址，别试图在这里把 UUID 换回 MAC。
 */
internal class IosQzxyBluetoothLink : QzxyBluetoothLink {

    /** 进程内唯一引擎：扫描与连接必须共用同一个 `CBCentralManager`，见 [IosQzxyBle]。 */
    private val central: IosQzxyCentral
        get() = IosQzxyBle.central

    override val connectionState: StateFlow<QzxyLinkState>
        get() = central.linkState

    override suspend fun ensureConnected(address: String): Result<Unit> =
        central.connect(address, CONNECT_TIMEOUT_MILLIS).map { }

    override suspend fun exchange(frame: ByteArray, timeoutMillis: Int): Result<ByteArray?> =
        central.exchange(frame, timeoutMillis)

    /** 断开对没连过的链路是空操作（引擎内部自己判），与 Android 同口径。 */
    override fun disconnect() = central.disconnect()

    private companion object {
        /** 与 Android 的 `QzxyGattLink.CONNECT_TIMEOUT_MILLIS` 同值：建链 + 服务发现都在这段预算里。 */
        const val CONNECT_TIMEOUT_MILLIS = 15_000L
    }
}
