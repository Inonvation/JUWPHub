package edu.jxslu.schedule.data.qzxy

import edu.jxslu.schedule.domain.QzxyWatering
import kotlinx.coroutines.flow.StateFlow

/**
 * 趣智校园**页面层**要的平台件（iOS 移植 V3，2026-11-25）。
 *
 * ## 为什么除了 `QzxyBluetoothLink` 还要这一组
 *
 * 页面搬进 `commonMain` 之后，`QzxyViewModel` 除了「把一帧送到设备」还要五样东西，
 * 而它们原来都以 `android.content.Context` 为构造参数：
 *
 * | 原实现（`:app`） | 介质 | 这里的形状 |
 * |---|---|---|
 * | `QzxyBluetoothScanner` | `android.bluetooth.le` | [QzxyDeviceScanner] |
 * | `QzxyDeviceStore` | `SharedPreferences`（`qzxy_devices`） | [QzxyDeviceRegistry] |
 * | `QzxyDebugStore` / `QzxyClearStore` | `SharedPreferences`（`qzxy_debug` / `qzxy_clear`） | [QzxyPrefs] |
 * | `QzxyWateringStore` | `SharedPreferences`（`qzxy_watering`） | [QzxyWateringRegistry] |
 * | `QzxyGattLink.serviceTable` / `negotiatedMtu` | GATT 服务发现结果 | [QzxyLinkDiagnostics] |
 *
 * **介质一律沿用 `SharedPreferences`**：这四份里装着老用户已绑定的设备、未结算的用水记账
 * 与试出来的清除命令参数，换成 `appDataDir()` 下的 JSON 等于当场读不出来
 * （与 `QiekjOrderHistoryStore` 同一条口径，见 `design/ios-port.md` §10.5）。
 * 所以共享层只声明接口，实现留在各端：Android 包一层原类，iOS 是
 * `IosQzxyStores.kt`（`NSUserDefaults`，键名与 Android 逐字对齐；2026-11-28 补齐，
 * 五个接口在 iOS 上都不再是 null）。
 *
 * ## 为什么数据类换了名字
 *
 * `QzxyBoundDevice` / `QzxyScannedDevice` / `QzxyGattCharacteristicInfo` 三个类型还留在
 * `:app`（今日页卡片、侧栏、诊断页之外还有读者），同名类在两个模块里会撞成 dex 的
 * `Duplicate program class`（`assembleDebug` 当场失败）。所以这里的镜像类型叫
 * [QzxyDeviceRef] / [QzxyNearbyDevice] / [QzxyServiceEntry]，字段逐一对应，
 * 边界上由 `:app` 的适配器转一次。等 `:app` 那三份没人用了，可以把名字收回来。
 *
 * ## null 的语义（照 `design/ios-port.md` §11.2 ④）
 *
 * 五个接口在装配根上都可空：**null = 本平台没有这项能力**，不是「以后补」的占位。
 * 页面在 null 时的行为逐条写在各自的 KDoc 里，统一口径是「入口不渲染 / 这一步跳过」，
 * 而不是留一个点了没反应的按钮。（2026-11-28 起 Android 与 iOS 两端都给非空实现，
 * 这条约定仍成立：将来的平台若少一块，照这个语义退，不要造假实现。）
 */

// ── 设备 ──

/** 已绑定的设备（镜像 `:app` 的 `QzxyBoundDevice`）。 */
data class QzxyDeviceRef(
    val address: String,
    val name: String,
)

/** 设备快照：已绑定的那几台 + 上次用过的那台（镜像 `QzxyDeviceState`）。 */
data class QzxyDeviceSnapshot(
    val bound: List<QzxyDeviceRef> = emptyList(),
    val lastUsed: QzxyDeviceRef? = null,
)

/**
 * 已绑定设备与「上次使用」的本机记录。
 *
 * **null = 本平台没有这份落点**：设备列表按空处理（只剩「附近设备」那一路），
 * 绑定 / 解绑按钮不渲染——写下去也留不住，比留一个假按钮诚实。
 *
 * [state] 必须是可订阅的：绑定发生在趣智页，而今日页卡片是另一份 ViewModel 实例，
 * 只读一次快照的写法会让卡片一直显示「未绑定」。
 */
interface QzxyDeviceRegistry {

    val state: StateFlow<QzxyDeviceSnapshot>

    fun list(): List<QzxyDeviceRef>

    fun add(device: QzxyDeviceRef)

    fun remove(address: String)

    fun lastUsed(): QzxyDeviceRef?

    fun setLastUsed(device: QzxyDeviceRef)
}

/**
 * 扫到的一台趣智设备（镜像 `:app` 的 `QzxyScannedDevice`）。
 *
 * [deviceKey] 是广播名里那串编号（`KLCXKJ-Water,G,490067305242` 里的 `490067305242`）；
 * 名字里没有就退回 MAC 去冒号大写。它是设备侧的业务标识，与 MAC 不一定相等。
 */
data class QzxyNearbyDevice(
    val name: String,
    val address: String,
    val rssi: Int,
    val deviceKey: String,
) {
    /** 去冒号大写的地址，用作设备信息缓存的键（同一台设备在不同回调里大小写未必一致）。 */
    val addressKey: String get() = address.replace(":", "").uppercase()
}

/**
 * BLE 广播扫描。
 *
 * **null = 本平台没有蓝牙扫描**：设备弹层里的「扫描」按钮整行不渲染，
 * 只剩「已绑定」那一段（若 [QzxyDeviceRegistry] 也给 null，这一层就是空的——
 * 那种平台上趣智页本来就进不去）。
 */
interface QzxyDeviceScanner {

    /**
     * 开扫。返回 false 表示蓝牙不可用或没权限，调用方据此提示。
     * [onFinished] 的参数是「是否正常扫完」：正常扫完但一台没有也算 true。
     */
    fun startScan(
        durationMillis: Long,
        onDevice: (QzxyNearbyDevice) -> Unit,
        onFinished: (Boolean) -> Unit,
        onError: (String) -> Unit,
    ): Boolean

    fun stopScan()
}

/**
 * 从广播名取设备编号：取最后一个逗号之后那段，12 位十六进制才算；
 * 否则退回 MAC 去冒号大写。
 *
 * 纯函数，从 `:app` 的 `QzxyBluetoothScanner.extractDeviceKey` 原样搬来
 * （那边还留着同名实现给今日页用，两处判据必须一致；`Character.digit` 换成了
 * `digitToIntOrNull(16)`，语义相同）。
 */
fun qzxyDeviceKey(name: String, address: String): String {
    val tail = name.substringAfterLast(',', "").trim()
    if (tail.length == 12 && tail.all { it.digitToIntOrNull(16) != null }) {
        return tail.uppercase()
    }
    return address.replace(":", "").uppercase()
}

/**
 * 查服务端设备（`device/info/mac`）时要试的键，顺序即尝试顺序。
 *
 * ## 为什么需要多个候选
 *
 * 服务端要的是**登记 MAC**，而两端能拿到的「地址」不是一回事：
 * Android 的 `address` 就是 MAC；iOS 只有 `CBPeripheral.identifier`（36 位 UUID），
 * Apple 不公开蓝牙地址。广播名里的那 12 位编号（[qzxyDeviceKey] 取出来的尾巴）是
 * iOS 上**唯一**可能被服务端认下的键。
 *
 * ## 顺序为什么是「像 MAC 的 address → deviceKey」
 *
 * Android 保持原样：`address` 是 MAC，第一个就是它，命中即停，不多打请求。
 * iOS 的 `address` 不是 12 位十六进制，直接被跳过，于是只剩 `deviceKey`。
 * 两个都不像 MAC 时（真拿不到编号的设备）退回 `address` 原样试一次——宁可让服务端
 * 明确回「查不到」，也不要在这里静默当成「这台设备不存在」。
 *
 * 纯函数，单测钉在 `QzxyDeviceLookupTest`。
 */
fun qzxyDeviceLookupKeys(device: QzxyNearbyDevice): List<String> = buildList {
    if (looksLikeMac(device.address)) add(device.address)
    if (looksLikeMac(device.deviceKey) &&
        !device.deviceKey.replace(":", "").equals(device.address.replace(":", ""), ignoreCase = true)
    ) {
        add(device.deviceKey)
    }
    if (isEmpty()) add(device.address)
}

/** 12 位十六进制（可带冒号 / 短横线）= 一个像 MAC 的键。 */
private fun looksLikeMac(raw: String): Boolean {
    val hex = raw.replace(":", "").replace("-", "")
    return hex.length == 12 && hex.all { it.digitToIntOrNull(16) != null }
}

// ── 本机偏好 ──

/**
 * 趣智校园的三项本机偏好 + 清除命令候选。
 *
 * 合成一个接口而不是拆四个：四项都是「一个键、读一次、写一次」的本机偏好，
 * 介质是同一批 `SharedPreferences`；拆开只会让装配根多写三行 null。
 *
 * **null = 本平台没有这份落点**：调试日志开关按「关」处理（不记录），
 * 自动结算按「开」处理（既有默认），记住的手机号为空，清除命令每次都从候选表头开始试。
 * 四项都有安全默认值，所以 null 不会让任何一步失败——只是每次多试几次。
 */
interface QzxyPrefs {

    /** 记录调试日志（默认关）。 */
    var logEnabled: Boolean

    /** 设备关停后自动结算（默认开）。 */
    var autoSettleEnabled: Boolean

    /** 上次登录的手机号（明文，退出登录也留着；不是凭据）。 */
    var rememberedPhone: String

    /** 清除命令（0x86）试出来的可用参数标识；null = 还没试出来。 */
    var preferredClearKey: String?
}

// ── 用水记账 ──

/**
 * 进行中的用水状态（DESIGN §3.18）。
 *
 * [watering] 必须是可订阅的流：开阀发生在页面里，今日页卡片要立刻变成「用水中 12:35」。
 *
 * **null = 本平台没有这份落点**：页面照常能开阀，但「结束用水」之后没有记账可对，
 * 状态卡不会出现「待核对」。iOS 侧因此也不会把用水写进今日页卡片。
 */
interface QzxyWateringRegistry {

    val watering: StateFlow<QzxyWatering?>

    fun begin(watering: QzxyWatering)

    fun clear()
}

// ── 链路诊断 ──

/**
 * 设备 GATT 表里的一项（镜像 `:app` 的 `QzxyGattCharacteristicInfo`）。
 *
 * [propertyText] 由实现方算好（「读/写/通知」这类中文标注要 `BluetoothGattCharacteristic`
 * 的位常量，那是 Android 专有）。[role] 是已知用途的标注（透传写入口 / 回包口 / 流控），
 * 认不出来就是 null。
 */
data class QzxyServiceEntry(
    val serviceUuid: String,
    val characteristicUuid: String,
    val propertyText: String,
    val role: String? = null,
)

/**
 * 链路的**诊断**读数：协商到的 MTU 与设备声明的服务表。
 *
 * ## 为什么单独一条缝，而不是塞进 `QzxyBluetoothLink`
 *
 * `QzxyBluetoothLink` 的四个成员是「把一帧送到设备」的最小面（`design/ios-port.md` §12.1
 * 明确写了它本轮不改形状）。而服务表与 MTU 是 GATT 服务发现的产物，只有诊断页与
 * 「探测服务」用它，iOS 侧就算接上 CoreBluetooth 也未必按同一形状暴露。
 * 单独立一条可空缝，链路本身的口径一行不动。
 *
 * **null = 本平台不报告诊断读数**：诊断页的「设备服务表」整块不渲染，
 * 「探测服务」退化成「连一下、读一次状态」，日志里不写 MTU。
 */
interface QzxyLinkDiagnostics {

    /** 协商到的 ATT MTU。透传模块对分包敏感，排查时要知道实际值。 */
    val negotiatedMtu: Int

    /** 当前连接读回的服务表；链路是进程单例，可能是上一次连接的产物。 */
    val serviceTable: List<QzxyServiceEntry>
}
