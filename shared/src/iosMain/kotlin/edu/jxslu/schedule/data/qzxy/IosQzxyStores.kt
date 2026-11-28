package edu.jxslu.schedule.data.qzxy

import edu.jxslu.schedule.data.local.IosKeyValuePrefs
import edu.jxslu.schedule.domain.QzxyWatering
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 趣智校园的五份本机存储与一份链路诊断（iOS 移植，2026-11-28）。
 *
 * ## 为什么是 `NSUserDefaults` 而不是 JSON 文件
 *
 * 这五份在 Android 上都是 `SharedPreferences`（`qzxy_devices` / `qzxy_debug` / `qzxy_clear` /
 * `qzxy_watering`），内容是「几个键 + 几行字符串」，没有整份 JSON 可以交给 `JsonFileStore`。
 * [IosKeyValuePrefs] 正是同一个语义的 iOS 介质（U净 三份存储已经这么做），键名与 Android 逐字对齐，
 * 排查时两边对得上号。
 *
 * ## 与 Android 的**唯一**差异
 *
 * 设备地址在这端是 `CBPeripheral.identifier` 的 UUID，不是 MAC。所以「已绑定设备」换机、
 * 换端都读不到——本来也不共享（`SharedPreferences` 与 `NSUserDefaults` 是两台设备各自的沙箱）。
 * 同一台 iPhone 上重装 App，UUID 可能变化，届时旧绑定会连不上，用户重扫一次即可
 * （连接失败文案是「找不到这台设备，请先扫描一次再连接」，不是静默失败）。
 */

// ---------------------------------------------------------------- 已绑定设备

/**
 * 已绑定设备 + 上次使用（对应 Android 的 `QzxyDeviceStore`）。
 *
 * 存法与 Android 逐字一致：一条 `address|name`、按地址（忽略大小写）去重、按名字排序；
 * 「上次使用」是 address + name 两个键。设备地址与名字不是凭证，不上加密。
 *
 * [state] 必须可订阅：绑定发生在趣智页，今日页卡片是另一份 ViewModel 实例，
 * 只读一次快照会让卡片一直显示「未绑定」。
 */
internal class IosQzxyDeviceRegistry : QzxyDeviceRegistry {

    private val prefs = IosKeyValuePrefs("qzxy_devices")

    private val _state = MutableStateFlow(readState())

    override val state: StateFlow<QzxyDeviceSnapshot> = _state.asStateFlow()

    override fun list(): List<QzxyDeviceRef> = _state.value.bound

    override fun add(device: QzxyDeviceRef) {
        val next = prefs.getStringSet(KEY_DEVICES)
            .filterNot { it.substringBefore(SEPARATOR).equals(device.address, ignoreCase = true) }
            .toMutableSet()
        next += device.address + SEPARATOR + device.name
        prefs.putStringSet(KEY_DEVICES, next)
        refresh()
    }

    override fun remove(address: String) {
        val next = prefs.getStringSet(KEY_DEVICES)
            .filterNot { it.substringBefore(SEPARATOR).equals(address, ignoreCase = true) }
            .toSet()
        prefs.putStringSet(KEY_DEVICES, next)
        refresh()
    }

    override fun lastUsed(): QzxyDeviceRef? = _state.value.lastUsed

    override fun setLastUsed(device: QzxyDeviceRef) {
        prefs.putString(KEY_LAST_ADDRESS, device.address)
        prefs.putString(KEY_LAST_NAME, device.name)
        refresh()
    }

    private fun refresh() {
        _state.value = readState()
    }

    private fun readState(): QzxyDeviceSnapshot {
        val bound = prefs.getStringSet(KEY_DEVICES)
            .mapNotNull { raw ->
                val separator = raw.indexOf(SEPARATOR)
                if (separator <= 0) {
                    null
                } else {
                    QzxyDeviceRef(raw.substring(0, separator), raw.substring(separator + 1))
                }
            }
            .sortedBy { it.name }
        val lastAddress = prefs.getString(KEY_LAST_ADDRESS)?.takeIf { it.isNotBlank() }
        val lastUsed = lastAddress?.let { address ->
            QzxyDeviceRef(address, prefs.getString(KEY_LAST_NAME).orEmpty())
        }
        return QzxyDeviceSnapshot(bound = bound, lastUsed = lastUsed)
    }

    private companion object {
        const val KEY_DEVICES = "bound_devices"
        const val KEY_LAST_ADDRESS = "last_address"
        const val KEY_LAST_NAME = "last_name"
        const val SEPARATOR = "|"
    }
}

// ---------------------------------------------------------------- 本机偏好

/**
 * 调试开关 / 自动结算 / 记住的手机号 / 清除命令候选（对应 Android 的 `QzxyDebugStore` + `QzxyClearStore`）。
 *
 * 四项的默认值与 Android 一致：日志**关**、自动结算**开**、手机号空、清除候选空。
 * 前三项写 `qzxy_debug`，第四项写 `qzxy_clear`——文件边界照 Android 保持，不合并。
 *
 * 布尔值存 `"1"` / `"0"`：`KeyValuePrefs` 只声明了字符串读写，多立一条缝不值得。
 */
internal class IosQzxyPrefs : QzxyPrefs {

    private val debug = IosKeyValuePrefs("qzxy_debug")
    private val clear = IosKeyValuePrefs("qzxy_clear")

    /** 记录调试日志（默认关）：日志含设备地址与接口原始数据，日常没必要留。 */
    override var logEnabled: Boolean
        get() = debug.getString(KEY_LOG_ENABLED) == FLAG_ON
        set(value) {
            debug.putString(KEY_LOG_ENABLED, if (value) FLAG_ON else FLAG_OFF)
        }

    /** 设备关停后自动结算（默认开）：开是既有口径，关掉后结算全部手动。 */
    override var autoSettleEnabled: Boolean
        get() = debug.getString(KEY_AUTO_SETTLE)?.let { it == FLAG_ON } ?: true
        set(value) {
            debug.putString(KEY_AUTO_SETTLE, if (value) FLAG_ON else FLAG_OFF)
        }

    /** 上次登录的手机号（明文；不是凭据，密码与 loginCode 只进 Keychain）。 */
    override var rememberedPhone: String
        get() = debug.getString(KEY_PHONE).orEmpty()
        set(value) {
            debug.putString(KEY_PHONE, value)
        }

    /** 清除命令（0x86）试出来的可用参数标识；null = 还没试出来。 */
    override var preferredClearKey: String?
        get() = clear.getString(KEY_PREFERRED)?.takeIf { it.isNotBlank() }
        set(value) {
            if (value == null) clear.remove(KEY_PREFERRED) else clear.putString(KEY_PREFERRED, value)
        }

    private companion object {
        const val KEY_LOG_ENABLED = "debug_log_enabled"
        const val KEY_AUTO_SETTLE = "auto_settle_enabled"
        const val KEY_PHONE = "remembered_phone"
        const val KEY_PREFERRED = "preferred_candidate"
        const val FLAG_ON = "1"
        const val FLAG_OFF = "0"
    }
}

// ---------------------------------------------------------------- 用水记账

/**
 * 进行中的用水状态（对应 Android 的 `QzxyWateringStore`）。
 *
 * 先落盘再更新流（与 Android 同序）：订阅方拿到的永远是已经落过盘的值。
 * 读到 `startedAtMillis <= 0` 当「没有在用水」——进程死在流程中时残留的就是这种半条记录。
 */
internal class IosQzxyWateringRegistry : QzxyWateringRegistry {

    private val prefs = IosKeyValuePrefs("qzxy_watering")

    private val _watering = MutableStateFlow(read())

    override val watering: StateFlow<QzxyWatering?> = _watering.asStateFlow()

    override fun begin(watering: QzxyWatering) {
        prefs.putString(KEY_STARTED_AT, watering.startedAtMillis.toString())
        prefs.putString(KEY_ADDRESS, watering.deviceAddress)
        prefs.putString(KEY_NAME, watering.deviceName)
        // 预扣金额可空：`putString` 只收非空，空值就删键（读回来同样是 null）
        val preDeduct = watering.preDeductMilli
        if (preDeduct == null) prefs.remove(KEY_PRE_DEDUCT) else prefs.putString(KEY_PRE_DEDUCT, preDeduct)
        _watering.value = watering
    }

    override fun clear() {
        prefs.clear()
        _watering.value = null
    }

    private fun read(): QzxyWatering? {
        val startedAt = prefs.getString(KEY_STARTED_AT)?.toLongOrNull() ?: 0L
        if (startedAt <= 0L) return null
        return QzxyWatering(
            startedAtMillis = startedAt,
            deviceAddress = prefs.getString(KEY_ADDRESS).orEmpty(),
            deviceName = prefs.getString(KEY_NAME).orEmpty(),
            preDeductMilli = prefs.getString(KEY_PRE_DEDUCT),
        )
    }

    private companion object {
        const val KEY_STARTED_AT = "started_at"
        const val KEY_ADDRESS = "device_address"
        const val KEY_NAME = "device_name"
        const val KEY_PRE_DEDUCT = "pre_deduct_milli"
    }
}

// ---------------------------------------------------------------- 链路诊断

/**
 * 链路诊断读数（对应 Android 的 `QzxyGattLink.negotiatedMtu` / `serviceTable`）。
 *
 * **这不是「估计值」**：iOS 不提供 ATT MTU 协商，能问到的只有「单次最多能写多少字节」，
 * 所以 [negotiatedMtu] 是那个上限 + 3，用于和 Android 的诊断页对照时有个同量级的数字，
 * 别当成真实 MTU 去做协议判断（分包用的是引擎里那个原始上限）。
 */
internal class IosQzxyDiagnostics(
    private val central: IosQzxyCentral = IosQzxyBle.central,
) : QzxyLinkDiagnostics {

    override val negotiatedMtu: Int get() = central.negotiatedMtu

    override val serviceTable: List<QzxyServiceEntry> get() = central.serviceTable
}
