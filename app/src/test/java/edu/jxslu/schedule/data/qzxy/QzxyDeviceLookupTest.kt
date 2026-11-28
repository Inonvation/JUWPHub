package edu.jxslu.schedule.data.qzxy

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 「查服务端设备时用哪个键」的契约测试（2026-11-28，iOS 蓝牙移植）。
 *
 * ## 为什么这条必须有单测
 *
 * iOS 拿不到蓝牙 MAC（`CBPeripheral` 只有系统生成的 UUID），服务端 `device/info/mac`
 * 却要登记 MAC。唯一还能用的键是广播名里的 12 位编号。这段候选顺序一旦写反，
 * Android 会凭空多打一轮请求，iOS 会拿 36 位 UUID 去查、永远查不到设备。
 *
 * 本机（Windows）跑不了 iOS，所以这里钉的是**两端共用的纯函数**，不是平台实现。
 */
class QzxyDeviceLookupTest {

    @Test
    fun androidAddressIsTriedFirstThenNameTail() {
        // Android 的 address 就是 MAC：第一个候选必须是它，命中即停，不多打请求
        val device = QzxyNearbyDevice(
            name = "KLCXKJ-Water,G,001122334455",
            address = "C0:11:22:33:44:55",
            rssi = -60,
            deviceKey = "001122334455",
        )
        assertEquals(
            listOf("C0:11:22:33:44:55", "001122334455"),
            qzxyDeviceLookupKeys(device),
        )
    }

    @Test
    fun iosUuidIsSkippedAndNameTailIsUsed() {
        // iOS 的 address 是 CBPeripheral.identifier：36 位 UUID，不是 MAC，必须被跳过
        val device = QzxyNearbyDevice(
            name = "KLCXKJ-Water,G,490067305242",
            address = "E621E1F8-C36C-495A-93FC-0C247A3E6E5F",
            rssi = -60,
            deviceKey = "490067305242",
        )
        assertEquals(listOf("490067305242"), qzxyDeviceLookupKeys(device))
    }

    @Test
    fun nameWithoutTailFallsBackToRawAddress() {
        // 名字里没有 12 位编号：只剩 address 可用。这时宁可让服务端明确回「查不到」，
        // 也不要静默跳过（跳过会让页面显示「设备不存在」而没有任何尝试记录）
        val device = QzxyNearbyDevice(
            name = "KLCXKJ-Water",
            address = "E621E1F8-C36C-495A-93FC-0C247A3E6E5F",
            rssi = -60,
            deviceKey = "E621E1F8C36C495A93FC0C247A3E6E5F",
        )
        assertEquals(listOf("E621E1F8-C36C-495A-93FC-0C247A3E6E5F"), qzxyDeviceLookupKeys(device))
    }

    @Test
    fun sameKeyIsNotTriedTwice() {
        // 广播名里没有编号时 deviceKey 退化成 address 去冒号大写：两者同一个设备，只试一次
        val device = QzxyNearbyDevice(
            name = "KLCXKJ-Water",
            address = "C01122 334455".replace(" ", ""),
            rssi = -60,
            deviceKey = "C01122334455",
        )
        assertEquals(listOf("C01122334455"), qzxyDeviceLookupKeys(device))
    }
}
