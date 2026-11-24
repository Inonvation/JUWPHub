package edu.jxslu.schedule.data.qzxy

import kotlinx.coroutines.flow.StateFlow

/** 蓝牙链路的三种状态（缝只表达这三态：没连 / 在建 / 连着）。 */
enum class QzxyLinkState {
    Disconnected,
    Connecting,
    Connected,
}

/**
 * 趣智校园的**蓝牙传输缝**（iOS 移植 S12，2026-11-24）。
 *
 * ## 为什么立这条缝
 *
 * 水控协议与帧编解码（`domain/QzxyProtocol` / `QzxyFrame`）、账单统计（`domain/QzxyBillStats`）
 * 与仓库（[QzxyRepository]）都已在 `shared/commonMain`；用水状态机 `QzxyWaterFlow` 与
 * 射频那一层还在 `:app`——后者是因为 `android.bluetooth` 的 GATT 回调、MTU 协商、
 * CCCD 写入、分包与写回执全是 Android 专有 API，iOS 上对应 CoreBluetooth 的一整套。
 * 所以把「怎么把一帧送到设备、怎么把回包读回来」收成四个动作，状态机与协议层只认这个接口。
 *
 * ## 形态照 [edu.jxslu.schedule.data.session.JwReadSession]
 *
 * 只放共享层真正需要的能力，多一个都不加：
 *
 * | 成员 | 对应真机动作 |
 * |---|---|
 * | [ensureConnected] | 连接（含复用判断与重连，见实现方） |
 * | [exchange] | 发一帧 + 读一帧回包（**成对**，见下） |
 * | [disconnect] | 断开并释放 |
 * | [connectionState] | 连接状态流 |
 *
 * **为什么「发一帧」与「读通知」不拆成两个方法**：GATT 同一时刻只允许一个操作在飞，
 * 写与读必须成对；拆开后并发调用会把 A 的回包配给 B 的请求。现有
 * `QzxyGattLink.request` 就是这一对（内含排空旧回包、按 MTU 分包、等写回执），
 * 拆开等于重写它的超时与重连语义——那正是本轮明确不许做的事。
 *
 * ## 两端实现
 *
 * - **Android**：`QzxyGattLink`（`:app` 的 `data/qzxy`）直接实现本接口，
 *   四个成员逐个转调它已有的 `connect` / `request` / `close`，超时、重连、分包、
 *   回执逻辑一行没改；
 * - **iOS**：`QzxyUnimplementedBluetoothLink`（`shared/src/iosMain`）——
 *   连接与收发**立刻回失败**，状态恒为 [QzxyLinkState.Disconnected]。
 *   升级条件：接上 CoreBluetooth（`CBCentralManager` 扫描 + `CBPeripheral` 的
 *   `discoverServices` / `writeValue` / 通知回调）后换掉这个桩，
 *   分包口径照 Android 侧按协商 MTU 切、回包按换行切。
 *
 * 本轮**没有接线消费者**：唯一组装用水流程的地方是 `:app` 的 `QzxyViewModel`，
 * 它把 `QzxyWaterFlow.Link` 直接实现在自己身上（页面本轮冻结）。这条缝是给
 * 平台装配根用的——接 iOS 时由 `IosServices` 装失败桩，Android 侧装 `QzxyGattLink`。
 */
interface QzxyBluetoothLink {

    /** 连接状态流：供页面显示「已连接 / 连接中」，以及判断能否复用。 */
    val connectionState: StateFlow<QzxyLinkState>

    /**
     * 保证连上 [address]（大写不敏感）。
     *
     * 复用与重连的判断由实现方负责——Android 侧沿用 `QzxyGattLink.connect` 的
     * 「连着同一台且服务表还在就直接复用，否则先 close 再建链」。
     * 失败把原因装进 [Result]（页面要原样显示「请先打开系统蓝牙」这类文案）。
     */
    suspend fun ensureConnected(address: String): Result<Unit>

    /**
     * 发一帧并读一帧回包。返回 `null`（在成功的 [Result] 里）= 设备没回包
     * （超时 / 发送失败 / 功能码不符），由调用方按「设备无响应」处理。
     *
     * [frame] 是**未包装**的帧体（`QzxyProtocol` 组出来的那几字节），
     * `#…\n` 的文本包装由实现方加——与 `QzxyGattLink.request` 同口径。
     */
    suspend fun exchange(frame: ByteArray, timeoutMillis: Int): Result<ByteArray?>

    /** 断开并释放。页面退出、重连前、异常分支都调它，不抛异常。 */
    fun disconnect()
}
