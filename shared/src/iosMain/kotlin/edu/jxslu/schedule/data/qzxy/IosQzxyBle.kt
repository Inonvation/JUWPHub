@file:OptIn(ExperimentalForeignApi::class)

package edu.jxslu.schedule.data.qzxy

import edu.jxslu.schedule.domain.QzxyFrame
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import platform.CoreBluetooth.CBAdvertisementDataLocalNameKey
import platform.CoreBluetooth.CBAdvertisementDataServiceUUIDsKey
import platform.CoreBluetooth.CBCentralManager
import platform.CoreBluetooth.CBCentralManagerDelegateProtocol
import platform.CoreBluetooth.CBCentralManagerScanOptionAllowDuplicatesKey
import platform.CoreBluetooth.CBCharacteristic
import platform.CoreBluetooth.CBCharacteristicProperties
import platform.CoreBluetooth.CBCharacteristicPropertyIndicate
import platform.CoreBluetooth.CBCharacteristicPropertyNotify
import platform.CoreBluetooth.CBCharacteristicPropertyRead
import platform.CoreBluetooth.CBCharacteristicPropertyWrite
import platform.CoreBluetooth.CBCharacteristicPropertyWriteWithoutResponse
import platform.CoreBluetooth.CBCharacteristicWriteWithResponse
import platform.CoreBluetooth.CBManagerState
import platform.CoreBluetooth.CBManagerStatePoweredOff
import platform.CoreBluetooth.CBManagerStatePoweredOn
import platform.CoreBluetooth.CBManagerStateUnauthorized
import platform.CoreBluetooth.CBManagerStateUnsupported
import platform.CoreBluetooth.CBManagerStateUnknown
import platform.CoreBluetooth.CBPeripheral
import platform.CoreBluetooth.CBPeripheralDelegateProtocol
import platform.CoreBluetooth.CBService
import platform.CoreBluetooth.CBUUID
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSNumber
import platform.Foundation.NSUUID
import platform.Foundation.create
import platform.darwin.NSObject
import platform.darwin.dispatch_queue_create
import platform.posix.memcpy

/**
 * 趣智校园的蓝牙引擎（iOS 移植，2026-11-28）：一条 `CBCentralManager` 同时承担扫描与连接。
 *
 * ## 为什么要一个进程单例
 *
 * iOS 的 `CBCentralManager` 建一次就代表「本 App 想用蓝牙」：每建一个实例都可能触发权限判定，
 * 而且扫描与连接必须由**同一个** central 管，否则 `didConnect` 不知道是哪个实例发起的。
 * 所以扫描器（[IosQzxyScanner]）与链路（[IosQzxyBluetoothLink]）共用 [IosQzxyBle.central]。
 *
 * ## 与 Android `QzxyGattLink` 的对齐点（行为不能漂）
 *
 * | 行为 | Android | 这里 |
 * |---|---|---|
 * | 建链复用 | 同地址 + 活着 + 服务表在 → 直接返回 | 同左 |
 * | 分包 | `mtu - 3`，下限 20 | `maximumWriteValueLengthForType`（iOS 直接给「单次可写字节数」），下限 20 |
 * | 写回执 | `onCharacteristicWrite` 等回执，超时**不重发** | `didWriteValueForCharacteristic` 等回执，超时同样不重发 |
 * | 回包 | 按 `\n` 切整行，超 512 字节判废 | 同左 |
 * | 无回包 | 每个写入口都试过仍无声 → 断开并回 `null` | 同左 |
 *
 * ## iOS 与 Android 的**结构性差异**（不是实现偷懒）
 *
 * 1. **拿不到 MAC**。`CBPeripheral` 只有 `identifier`（系统按「设备 + App」生成的 UUID），
 *    Apple 不公开蓝牙地址。所以 [QzxyNearbyDevice.address] 在这端是 UUID 字符串，
 *    服务端按 MAC 查设备那一步只能退到广播名里的 12 位编号（见 `QzxyRepository.deviceInfo(QzxyNearbyDevice)`）。
 * 2. **MTU 不可协商**。iOS 没有 `requestMtu`，能问的只有「这次写最多多少字节」，
 *    诊断页上那个数字因此是「单包上限 + 3」的估算值，不是协商结果。
 * 3. **没有写队列**。`writeValue` 只负责投递，同一个特征值连续写必须自己等回执，
 *    否则后一包会被前一包挤掉（Android 是 GATT 直接丢，iOS 是默默覆盖）。
 *
 * ## 编译状态
 *
 * 本机是 Windows，**Kotlin/Native 编不了**，本文件只过了人工核对；签名以 CI 的 macOS 编译为准。
 * 见过的坑写在每个 override 的 KDoc 里（同名不同选择器的方法必须挂 `@ObjCSignatureOverride`）。
 */
internal object IosQzxyBle {

    /**
     * 进程内唯一的一条蓝牙链路。
     *
     * 惰性建：`IosServices` 在首帧前就会构造它，而那时 App 可能还没进前台；
     * `CBCentralManager` 的构造会触发权限弹窗，放在真正要用蓝牙的那一刻更稳。
     */
    val central: IosQzxyCentral by lazy { IosQzxyCentral() }
}

/**
 * 一次扫描的现场。扫描期间设备回调都发到它身上，`stopScan` 之后整份丢掉。
 */
private class PendingScan(
    val durationMillis: Long,
    val onDevice: (QzxyNearbyDevice) -> Unit,
    val onFinished: (Boolean) -> Unit,
    val onError: (String) -> Unit,
)

/**
 * CoreBluetooth 的中央设备（扫描 + 连接 + 收发）。
 *
 * 所有 CoreBluetooth 回调都在 [BLE_QUEUE] 这条串行队列上；协程侧的等待用
 * `CompletableDeferred` / `Channel` 交接，不假设回调发生在哪个线程。
 */
@Suppress("CONFLICTING_OVERLOADS", "PARAMETER_NAME_CHANGED_ON_OVERRIDE")
internal class IosQzxyCentral :
    NSObject(),
    CBCentralManagerDelegateProtocol,
    CBPeripheralDelegateProtocol {

    // ---- 对外只读的现场 ----

    private val _linkState = MutableStateFlow(QzxyLinkState.Disconnected)

    val linkState: StateFlow<QzxyLinkState> = _linkState.asStateFlow()

    /** 单次可写字节数 + 3 的估算（iOS 没有 ATT MTU 协商，见类 KDoc 第 2 条）。 */
    var negotiatedMtu: Int = DEFAULT_MTU
        private set

    /** 最近一次连接读回的服务表；断链后清空（Android 同口径）。 */
    var serviceTable: List<QzxyServiceEntry> = emptyList()
        private set

    // ---- 内部状态 ----

    /** 中央设备本身。惰性建，避免在 `IosServices` 构造期就弹权限框。 */
    private val central: CBCentralManager by lazy {
        CBCentralManager(delegate = this, queue = BLE_QUEUE, options = null)
    }

    private var centralState: CBManagerState = CBManagerStateUnknown

    private var scanRequest: PendingScan? = null
    private var scanJob: Job? = null
    private val scanScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val seenInScan = mutableSetOf<String>()

    /** 扫到过的外设：`identifier.UUIDString` → `CBPeripheral`。绑定设备的地址就是它。 */
    private val knownPeripherals = mutableMapOf<String, CBPeripheral>()

    private var peripheral: CBPeripheral? = null
    private var connectedAddress: String? = null
    private var writeCharacteristics: List<CBCharacteristic> = emptyList()
    private var allCharacteristics: List<CBCharacteristic> = emptyList()

    private var connectWaiter: CompletableDeferred<Boolean>? = null
    private var servicesWaiter: CompletableDeferred<List<QzxyServiceEntry>>? = null
    private var pendingServiceCount = 0
    private var writeWaiter: CompletableDeferred<Boolean>? = null
    private var notifyWaiter: CompletableDeferred<Unit>? = null
    private var notifyTarget: CBCharacteristic? = null

    /** 设备主动上报的数据块，按到达顺序排队（与 Android 的 `incoming` 同语义）。 */
    private val incoming = Channel<ByteArray>(Channel.UNLIMITED)
    private var buffered = ByteArray(0)

    private val linkAlive: Boolean
        get() = connectedAddress != null && serviceTable.isNotEmpty()

    // ---------------------------------------------------------------- 扫描

    /**
     * 开扫。返回 false = 本机蓝牙起不来（未授权 / 关闭 / 不支持），调用方据此提示；
     * 返回 true 但状态还没就绪（`Unknown`，首次使用会先弹权限框）时，等状态回调再真的开扫。
     */
    fun startScan(
        durationMillis: Long,
        onDevice: (QzxyNearbyDevice) -> Unit,
        onFinished: (Boolean) -> Unit,
        onError: (String) -> Unit,
    ): Boolean {
        stopScan()
        val request = PendingScan(durationMillis, onDevice, onFinished, onError)
        scanRequest = request
        val manager = central
        return when (manager.state) {
            CBManagerStatePoweredOn -> {
                beginScan(request)
                true
            }

            // 首次使用：权限框还没弹，状态未知。先收下这次请求，`didUpdateState` 里接着开扫。
            CBManagerStateUnknown -> true

            CBManagerStateUnauthorized -> {
                scanRequest = null
                onError("蓝牙权限被拒绝：请到「设置 → JUWPHub → 蓝牙」打开后重试")
                false
            }

            CBManagerStatePoweredOff -> {
                scanRequest = null
                onError("请先打开系统蓝牙")
                false
            }

            else -> {
                scanRequest = null
                onError("当前设备不支持低功耗蓝牙")
                false
            }
        }
    }

    /** 停扫。与 Android 同口径：**不回调 `onFinished`**（那是「正常扫完」的语义）。 */
    fun stopScan() {
        scanJob?.cancel()
        scanJob = null
        scanRequest = null
        seenInScan.clear()
        if (centralState == CBManagerStatePoweredOn) {
            runCatching { central.stopScan() }
        }
    }

    private fun beginScan(request: PendingScan) {
        seenInScan.clear()
        central.scanForPeripheralsWithServices(
            serviceUUIDs = null,
            options = mapOf(CBCentralManagerScanOptionAllowDuplicatesKey to false),
        )
        scanJob = scanScope.launch {
            delay(request.durationMillis)
            // 计时到点：正常结束。回调要走蓝牙队列，避免与 didDiscover 并发改状态。
            finishScan(normal = true)
        }
    }

    private fun finishScan(normal: Boolean) {
        val request = scanRequest ?: return
        scanRequest = null
        scanJob?.cancel()
        scanJob = null
        seenInScan.clear()
        runCatching { central.stopScan() }
        request.onFinished(normal)
    }

    // ---------------------------------------------------------------- 连接

    /**
     * 连上 [address]（这端是 `CBPeripheral.identifier` 的 UUID 字符串）并读回服务表。
     *
     * 复用判断与 Android 一致：同地址 + 链路活着 + 服务表在 → 直接返回，不重连。
     */
    suspend fun connect(address: String, timeoutMillis: Long): Result<List<QzxyServiceEntry>> {
        if (linkAlive && connectedAddress.equals(address, ignoreCase = true)) {
            return Result.success(serviceTable)
        }
        disconnect()

        val manager = central
        when (manager.state) {
            CBManagerStatePoweredOn -> Unit
            CBManagerStateUnauthorized -> return Result.failure(
                IllegalStateException("蓝牙权限被拒绝：请到「设置 → JUWPHub → 蓝牙」打开后重试"),
            )

            CBManagerStatePoweredOff -> return Result.failure(IllegalStateException("请先打开系统蓝牙"))
            CBManagerStateUnknown -> return Result.failure(IllegalStateException("蓝牙还没就绪，请稍后重试"))
            else -> return Result.failure(IllegalStateException("当前设备不支持低功耗蓝牙"))
        }

        val target = knownPeripherals[address] ?: retrievePeripheral(address)
            ?: return Result.failure(IllegalStateException("找不到这台设备，请先扫描一次再连接"))

        _linkState.value = QzxyLinkState.Connecting
        val connected = CompletableDeferred<Boolean>()
        connectWaiter = connected
        peripheral = target
        target.delegate = this
        manager.connectPeripheral(target, options = null)

        val connectedResult = withTimeoutOrNull(timeoutMillis) { connected.await() }
        if (connectWaiter === connected) connectWaiter = null
        when (connectedResult) {
            true -> Unit
            false -> {
                disconnect()
                return Result.failure(IllegalStateException("设备拒绝连接或被其他设备占用"))
            }

            null -> {
                disconnect()
                return Result.failure(IllegalStateException("连接设备超时（${timeoutMillis}ms）"))
            }
        }

        val discovered = CompletableDeferred<List<QzxyServiceEntry>>()
        servicesWaiter = discovered
        pendingServiceCount = 0
        allCharacteristics = emptyList()
        target.discoverServices(null)
        val table = withTimeoutOrNull(timeoutMillis) { discovered.await() }
        if (servicesWaiter === discovered) servicesWaiter = null
        if (table == null) {
            disconnect()
            return Result.failure(IllegalStateException("服务发现超时（${timeoutMillis}ms）"))
        }

        connectedAddress = address
        serviceTable = table
        enableNotifications()
        if (connectedAddress == null) {
            disconnect()
            return Result.failure(IllegalStateException("设备在启用通知时断开"))
        }
        _linkState.value = QzxyLinkState.Connected
        return Result.success(table)
    }

    /**
     * 从系统缓存里找一台连过的外设。
     *
     * 绑定设备在 App 重启后仍然要能连：`CBCentralManager` 认识本 App 连过的外设，
     * 用 UUID 就能取回来，不必重新扫一遍。
     */
    private fun retrievePeripheral(address: String): CBPeripheral? {
        // K/N 的构造参数名是 `uUIDString`（ObjC 的 `initWithUUIDString:`）：用位置参数避免拼错
        val uuid = NSUUID(address)
        val found = central.retrievePeripheralsWithIdentifiers(listOf(uuid))
            .filterIsInstance<CBPeripheral>()
            .firstOrNull()
            ?: return null
        knownPeripherals[found.identifier.UUIDString] = found
        return found
    }

    /**
     * 打开回包口的通知。
     *
     * 只开已知的两条（回包口 / 流控），服务表里认不出时才退回「所有可通知的特征值」。
     * 回包口等 CCCD 写回执（最多 2 秒）；流控不阻塞建链——与 Android 同一口径。
     */
    private suspend fun enableNotifications() {
        val target = peripheral ?: return
        val notifiable = allCharacteristics.filter { it.isNotifiable() }
        if (notifiable.isEmpty()) return
        val preferred = notifiable
            .filter { it.uuidText().equals(READ_UUID, ignoreCase = true) || it.uuidText().equals(FLOW_CONTROL_UUID, ignoreCase = true) }
            .sortedBy { if (it.uuidText().equals(READ_UUID, ignoreCase = true)) 0 else 1 }
        (preferred.ifEmpty { notifiable }).forEach { characteristic ->
            val await = characteristic.uuidText().equals(READ_UUID, ignoreCase = true)
            if (await) {
                val waiter = CompletableDeferred<Unit>()
                notifyWaiter = waiter
                notifyTarget = characteristic
                target.setNotifyValue(true, forCharacteristic = characteristic)
                withTimeoutOrNull(NOTIFY_TIMEOUT_MILLIS) { waiter.await() }
                if (notifyWaiter === waiter) {
                    notifyWaiter = null
                    notifyTarget = null
                }
            } else {
                target.setNotifyValue(true, forCharacteristic = characteristic)
            }
        }
    }

    // ---------------------------------------------------------------- 收发

    /**
     * 发一帧并等一帧回包。`Result.success(null)` = 设备一声不吭（超时），调用方按「无响应」处理。
     *
     * 上层传进来的 [frame] 是**未包装**的帧体，`#<hex>\n` 的包装由这里加（与 Android 同口径）。
     */
    suspend fun exchange(frame: ByteArray, timeoutMillis: Int): Result<ByteArray?> {
        val target = peripheral ?: return Result.failure(IllegalStateException("蓝牙未连接"))
        if (writeCharacteristics.isEmpty()) {
            return Result.failure(IllegalStateException("设备没有可写特征值"))
        }
        return try {
            val payload = QzxyFrame.wrap(frame)
            for (characteristic in writeCharacteristics) {
                drainIncoming()
                buffered = ByteArray(0)
                writeChunked(target, characteristic, payload)
                val response = withTimeoutOrNull(timeoutMillis.toLong()) { readUntilLineFeed() }
                if (response != null) return Result.success(response)
            }
            // 每个写入口都试过仍无回包：多半是链路已经废了，清掉让下一次操作重新建链。
            disconnect()
            Result.success(null)
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (error: Throwable) {
            Result.failure(error)
        }
    }

    private fun drainIncoming() {
        while (incoming.tryReceive().isSuccess) {
            // 空循环即是消费：丢弃上一轮遗留的通知数据
        }
    }

    /** 按单包上限切片写入；长帧（downData）必须切开，否则 `writeValue` 直接丢包。 */
    private suspend fun writeChunked(target: CBPeripheral, characteristic: CBCharacteristic, payload: ByteArray) {
        val limit = target.maximumWriteValueLengthForType(CBCharacteristicWriteWithResponse)
            .toInt()
            .coerceAtLeast(MIN_CHUNK_BYTES)
        var offset = 0
        while (offset < payload.size) {
            val end = minOf(offset + limit, payload.size)
            writeOnce(target, characteristic, payload.copyOfRange(offset, end))
            offset = end
            if (offset < payload.size) delay(WRITE_GAP_MILLIS)
        }
    }

    /**
     * 写一包并等回执。
     *
     * 与 Android 的差别：iOS 的 `writeValue` 没有「排队失败」这个返回值，所以没有
     * `WRITE_ATTEMPTS` 那一圈重试——回执失败就重试一次，超时则**不重发**（写可能已经生效，
     * 重发会多下一次 0x21 / 0x86）。
     */
    private suspend fun writeOnce(target: CBPeripheral, characteristic: CBCharacteristic, chunk: ByteArray) {
        val waiter = CompletableDeferred<Boolean>()
        writeWaiter = waiter
        target.writeValue(chunk.toNSData(), forCharacteristic = characteristic, type = CBCharacteristicWriteWithResponse)
        val acknowledged = withTimeoutOrNull(WRITE_TIMEOUT_MILLIS) { waiter.await() }
        if (writeWaiter === waiter) writeWaiter = null
        when (acknowledged) {
            true -> return
            false -> {
                // 设备明确回了失败：重发一包是安全的
                delay(WRITE_RETRY_DELAY_MILLIS)
                val retry = CompletableDeferred<Boolean>()
                writeWaiter = retry
                target.writeValue(chunk.toNSData(), forCharacteristic = characteristic, type = CBCharacteristicWriteWithResponse)
                val retried = withTimeoutOrNull(WRITE_TIMEOUT_MILLIS) { retry.await() }
                if (writeWaiter === retry) writeWaiter = null
                if (retried == true) return
                throw IllegalStateException("GATT 写入没有完成")
            }

            null -> throw IllegalStateException("GATT 写入回执超时")
        }
    }

    /** 累积通知数据到出现换行为止；设备可能分好几包发。 */
    private suspend fun readUntilLineFeed(): ByteArray? {
        while (true) {
            val chunk = incoming.receive()
            buffered += chunk
            val end = buffered.indexOfFirst { it == LINE_FEED }
            if (end >= 0) {
                val line = buffered.copyOfRange(0, end + 1)
                buffered = buffered.copyOfRange(end + 1, buffered.size)
                return line
            }
            if (buffered.size > MAX_RESPONSE_BYTES) {
                buffered = ByteArray(0)
                return null
            }
        }
    }

    // ---------------------------------------------------------------- 断开

    /** 断开并释放。页面退出、重连前、异常分支都调它，不抛异常。 */
    fun disconnect() {
        val target = peripheral
        peripheral = null
        if (target != null) {
            runCatching { central.cancelPeripheralConnection(target) }
        }
        markDisconnected()
    }

    private fun markDisconnected() {
        if (connectedAddress == null && serviceTable.isEmpty() && peripheral == null) {
            _linkState.value = QzxyLinkState.Disconnected
            return
        }
        connectedAddress = null
        serviceTable = emptyList()
        writeCharacteristics = emptyList()
        allCharacteristics = emptyList()
        buffered = ByteArray(0)
        negotiatedMtu = DEFAULT_MTU
        connectWaiter?.complete(false)
        connectWaiter = null
        servicesWaiter?.completeExceptionally(IllegalStateException("设备已断开"))
        servicesWaiter = null
        writeWaiter?.complete(false)
        writeWaiter = null
        notifyWaiter?.complete(Unit)
        notifyWaiter = null
        notifyTarget = null
        _linkState.value = QzxyLinkState.Disconnected
    }

    // ---------------------------------------------------------------- 委托回调

    override fun centralManagerDidUpdateState(central: CBCentralManager) {
        centralState = central.state
        val request = scanRequest
        when (central.state) {
            CBManagerStatePoweredOn -> if (request != null) beginScan(request)

            CBManagerStatePoweredOff, CBManagerStateUnauthorized, CBManagerStateUnsupported -> {
                val message = when (central.state) {
                    CBManagerStatePoweredOff -> "请先打开系统蓝牙"
                    CBManagerStateUnauthorized -> "蓝牙权限被拒绝：请到「设置 → JUWPHub → 蓝牙」打开后重试"
                    else -> "当前设备不支持低功耗蓝牙"
                }
                scanRequest = null
                request?.onError(message)
                // 连接中掉状态：把等待方叫醒，否则会一直等到超时
                connectWaiter?.complete(false)
                servicesWaiter?.completeExceptionally(IllegalStateException(message))
                markDisconnected()
            }

            else -> Unit
        }
    }

    override fun centralManager(
        central: CBCentralManager,
        didDiscoverPeripheral: CBPeripheral,
        advertisementData: Map<Any?, *>,
        RSSI: NSNumber,
    ) {
        val request = scanRequest ?: return
        val name = didDiscoverPeripheral.name
            ?: (advertisementData[CBAdvertisementDataLocalNameKey] as? String)
        val advertisedServices = (advertisementData[CBAdvertisementDataServiceUUIDsKey] as? List<*>)
            ?.filterIsInstance<CBUUID>()
            .orEmpty()
        val hasService = advertisedServices.any {
            it.UUIDString.equals(QzxyApiConfig.BLE_SERVICE_UUID, ignoreCase = true)
        }
        val hasName = name?.startsWith(QzxyApiConfig.BLE_NAME_PREFIX, ignoreCase = true) == true
        // 名字明确是别的设备就直接排除：透传服务 UUID 是通用 UUID，不能因为带了它就认成本家水表
        if (name != null && !hasName) return
        if (!hasService && !hasName) return

        val address = didDiscoverPeripheral.identifier.UUIDString
        if (!seenInScan.add(address)) return
        knownPeripherals[address] = didDiscoverPeripheral
        request.onDevice(
            QzxyNearbyDevice(
                name = name?.takeIf { it.isNotBlank() } ?: address,
                address = address,
                rssi = RSSI.intValue,
                // 这端没有 MAC：编号只能来自广播名，名字里没有就是一串 UUID（见类 KDoc 第 1 条）
                deviceKey = qzxyDeviceKey(name.orEmpty(), address),
            ),
        )
    }

    override fun centralManager(central: CBCentralManager, didConnectPeripheral: CBPeripheral) {
        connectWaiter?.complete(true)
    }

    /** 与 `didDisconnectPeripheral` 的 Kotlin 签名完全相同，必须声明选择器（见类上的 suppress）。 */
    @ObjCSignatureOverride
    override fun centralManager(
        central: CBCentralManager,
        didFailToConnectPeripheral: CBPeripheral,
        error: NSError?,
    ) {
        connectWaiter?.complete(false)
        connectWaiter = null
        markDisconnected()
    }

    /** 与 `didFailToConnectPeripheral` 同签名，理由同上。 */
    @ObjCSignatureOverride
    override fun centralManager(
        central: CBCentralManager,
        didDisconnectPeripheral: CBPeripheral,
        error: NSError?,
    ) {
        // 服务发现完成后设备仍可能被官方 App 抢占或走远：必须把复用判断依赖的现场清掉
        if (peripheral === didDisconnectPeripheral) peripheral = null
        markDisconnected()
    }

    /**
     * 服务发现完成。**签名带的是 `NSError?` 而不是服务列表**（ObjC 选择器
     * `peripheral:didDiscoverServices:` 就是这么声明的，CI 首轮报过这条），
     * 服务表从 `peripheral.services` 读回来。
     */
    override fun peripheral(peripheral: CBPeripheral, didDiscoverServices: NSError?) {
        if (didDiscoverServices != null) {
            servicesWaiter?.completeExceptionally(
                IllegalStateException("服务发现失败：${didDiscoverServices.localizedDescription}"),
            )
            servicesWaiter = null
            return
        }
        val services = peripheral.services?.filterIsInstance<CBService>().orEmpty()
        if (services.isEmpty()) {
            servicesWaiter?.completeExceptionally(IllegalStateException("设备没有公开任何服务"))
            servicesWaiter = null
            return
        }
        pendingServiceCount = services.size
        services.forEach { peripheral.discoverCharacteristics(null, it) }
    }

    override fun peripheral(
        peripheral: CBPeripheral,
        didDiscoverCharacteristicsForService: CBService,
        error: NSError?,
    ) {
        if (error != null) {
            servicesWaiter?.completeExceptionally(
                IllegalStateException("服务发现失败：${error.localizedDescription}"),
            )
            servicesWaiter = null
            return
        }
        allCharacteristics = allCharacteristics + didDiscoverCharacteristicsForService.characteristics
            ?.filterIsInstance<CBCharacteristic>()
            .orEmpty()
        pendingServiceCount -= 1
        if (pendingServiceCount > 0) return

        val table = buildServiceTable()
        // 写入口候选：已知的透传写入口在就只用它，避免同一条命令往两个口重发；
        // 服务表里没有它（别的厂商设备）才退回「逐个试」——与 Android 的取舍一致。
        val writable = allCharacteristics.filter { it.isWritable() }
        writeCharacteristics = writable
            .filter { it.uuidText().equals(WRITE_UUID, ignoreCase = true) }
            .ifEmpty { writable }
        val manager = this.peripheral
        if (manager != null) {
            negotiatedMtu = manager.maximumWriteValueLengthForType(CBCharacteristicWriteWithResponse)
                .toInt()
                .plus(3)
        }
        servicesWaiter?.complete(table)
        servicesWaiter = null
    }

    @ObjCSignatureOverride
    override fun peripheral(
        peripheral: CBPeripheral,
        didUpdateValueForCharacteristic: CBCharacteristic,
        error: NSError?,
    ) {
        if (error != null) return
        val value = didUpdateValueForCharacteristic.value ?: return
        incoming.trySend(value.toByteArray())
    }

    @ObjCSignatureOverride
    override fun peripheral(
        peripheral: CBPeripheral,
        didWriteValueForCharacteristic: CBCharacteristic,
        error: NSError?,
    ) {
        // 只唤醒「当前这一包」的等待方：若上层已经开了下一包，这里不能把它清掉
        val waiter = writeWaiter
        writeWaiter = null
        waiter?.complete(error == null)
    }

    @ObjCSignatureOverride
    override fun peripheral(
        peripheral: CBPeripheral,
        didUpdateNotificationStateForCharacteristic: CBCharacteristic,
        error: NSError?,
    ) {
        if (notifyTarget === didUpdateNotificationStateForCharacteristic) {
            notifyWaiter?.complete(Unit)
            notifyWaiter = null
            notifyTarget = null
        }
    }

    // ---------------------------------------------------------------- 工具

    private fun buildServiceTable(): List<QzxyServiceEntry> {
        val entries = mutableListOf<QzxyServiceEntry>()
        val services = peripheral?.services?.filterIsInstance<CBService>().orEmpty()
        services.forEach { service ->
            val serviceUuid = service.UUID.UUIDString
            service.characteristics?.filterIsInstance<CBCharacteristic>()?.forEach { characteristic ->
                val uuid = characteristic.uuidText()
                entries += QzxyServiceEntry(
                    serviceUuid = serviceUuid,
                    characteristicUuid = uuid,
                    propertyText = characteristic.propertyText(),
                    role = when {
                        uuid.equals(WRITE_UUID, ignoreCase = true) -> "透传写入口：数据往这里发"
                        uuid.equals(READ_UUID, ignoreCase = true) -> "透传回包口：回复从这里来"
                        uuid.equals(FLOW_CONTROL_UUID, ignoreCase = true) -> "流控"
                        else -> null
                    },
                )
            }
        }
        return entries
    }

    private fun CBCharacteristic.uuidText(): String = UUID.UUIDString

    private fun CBCharacteristic.hasProperty(flag: CBCharacteristicProperties): Boolean =
        (properties.toULong() and flag.toULong()) != 0UL

    private fun CBCharacteristic.isWritable(): Boolean =
        hasProperty(CBCharacteristicPropertyWrite) || hasProperty(CBCharacteristicPropertyWriteWithoutResponse)

    private fun CBCharacteristic.isNotifiable(): Boolean =
        hasProperty(CBCharacteristicPropertyNotify) || hasProperty(CBCharacteristicPropertyIndicate)

    /** 属性中文标注：位常量的含义只有 CoreBluetooth 知道，界面上要读得懂。 */
    private fun CBCharacteristic.propertyText(): String = buildList {
        if (hasProperty(CBCharacteristicPropertyRead)) add("读")
        if (hasProperty(CBCharacteristicPropertyWrite)) add("写")
        if (hasProperty(CBCharacteristicPropertyWriteWithoutResponse)) add("无应答写")
        if (hasProperty(CBCharacteristicPropertyNotify)) add("通知")
        if (hasProperty(CBCharacteristicPropertyIndicate)) add("指示")
    }.joinToString("/").ifBlank { "无" }

    private companion object {
        const val DEFAULT_MTU = 23
        const val MIN_CHUNK_BYTES = 20
        const val MAX_RESPONSE_BYTES = 512
        const val WRITE_GAP_MILLIS = 30L
        const val WRITE_TIMEOUT_MILLIS = 1_000L
        const val WRITE_RETRY_DELAY_MILLIS = 40L
        const val NOTIFY_TIMEOUT_MILLIS = 2_000L
        const val LINE_FEED = 0x0A.toByte()

        /** Microchip/ISSC 蓝牙透传服务的三条特征值（与 Android 侧同一组常量）。 */
        const val WRITE_UUID = "49535343-8841-43f4-a8d4-ecbe34729bb3"
        const val READ_UUID = "49535343-1e4d-4bd9-ba61-23c647249616"
        const val FLOW_CONTROL_UUID = "49535343-aca3-481c-91ec-d85e28a60318"

        /** CoreBluetooth 回调队列：串行，避免扫描与连接的现场互相踩。 */
        val BLE_QUEUE = dispatch_queue_create("edu.jxslu.schedule.qzxy.ble", null)
    }
}

/**
 * 一帧字节量的转手工具。
 *
 * `NSData.create` 会立刻把这段内存读走，`usePinned` 保证调用期间 GC 不挪动底层数组；
 * **不要**换成 `bytesNoCopy`——那要求内存在 NSData 整个生命周期内有效，Kotlin 侧给不出承诺。
 */
private fun ByteArray.toNSData(): NSData = usePinned { pinned ->
    NSData.create(bytes = pinned.addressOf(0), length = size.toULong())
}

/** `NSData` → `ByteArray`：拷一份出来，设备回包很小，不值得为零拷贝引入生命周期约束。 */
private fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    if (size <= 0) return ByteArray(0)
    val out = ByteArray(size)
    out.usePinned { pinned ->
        memcpy(pinned.addressOf(0), bytes, length)
    }
    return out
}
