package edu.jxslu.schedule.data.qzxy

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * iOS 侧的蓝牙传输「未实现」桩（iOS 移植 S12，2026-11-24）。
 *
 * 共享层的水控协议、帧编解码与用水状态机（`QzxyWaterFlow`，`:app`）已经可用，缺的只是射频那一层：
 * iOS 上要接 CoreBluetooth（`CBCentralManager` 扫描、`CBPeripheral.discoverServices`、
 * `writeValue` 与通知回调）。本轮不做，这里给出**立刻回失败**的实现，让装配根
 * （`IosServices` 接这条链路时）拿到的是明确的「不支持」而不是崩溃。
 *
 * 升级条件：接 CoreBluetooth 后换掉本类——分包口径照 Android 侧按协商 MTU 切
 * （默认 23 → 每包 20 字节），回包按换行切，超时与重连语义照
 * `:app` 的 `QzxyGattLink`（连上就复用、写回执超时不重发）。
 *
 * 编译状态：**未在本机验证**（Windows 没有 Kotlin/Native），靠 CI 的 macOS 构建。
 * 本文件不含任何 ObjC 调用——只有共享层类型与协程原语，符号风险与
 * [edu.jxslu.schedule.data.session.IosJwReadSession] 同量级。
 */
class IosQzxyBluetoothLink : QzxyBluetoothLink {

    private val _connectionState = MutableStateFlow(QzxyLinkState.Disconnected)

    override val connectionState: StateFlow<QzxyLinkState> = _connectionState.asStateFlow()

    override suspend fun ensureConnected(address: String): Result<Unit> =
        Result.failure(UnsupportedOperationException(NOT_IMPLEMENTED))

    override suspend fun exchange(frame: ByteArray, timeoutMillis: Int): Result<ByteArray?> =
        Result.failure(UnsupportedOperationException(NOT_IMPLEMENTED))

    /** 断开对没连过的链路是空操作：状态本来就是「未连接」。 */
    override fun disconnect() {
        _connectionState.value = QzxyLinkState.Disconnected
    }

    private companion object {
        const val NOT_IMPLEMENTED = "iOS 侧还没接 CoreBluetooth：蓝牙水控暂不可用"
    }
}
