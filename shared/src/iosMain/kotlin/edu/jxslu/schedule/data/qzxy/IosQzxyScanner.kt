package edu.jxslu.schedule.data.qzxy

/**
 * iOS 侧的 BLE 扫描（iOS 移植，2026-11-28）。
 *
 * 只做一件 Android 侧同名类做过的事：把广播包翻成 [QzxyNearbyDevice]。过滤规则逐条照搬
 * `QzxyBluetoothScanner`：广播名以 `KLCXKJ` 开头，或广播包里带透传服务 UUID，二者命中其一即可；
 * 名字明确是别的设备时直接排除（透传服务 UUID 是通用 UUID，不能因为带了它就认成本家水表）。
 *
 * ## 权限
 *
 * iOS 没有「请求蓝牙权限」这个 API：第一次建 `CBCentralManager` 时系统自己弹框。
 * 所以 [startScan] 在状态未知时返回 true（请求已收下），真正开扫发生在
 * `centralManagerDidUpdateState` 回调里；被拒绝时报一句能照做的中文。
 * 页面上的 `onRequestScan` 因此可以直接放行（宿主传 `{ it(true) }`），不需要等一个不存在的授权回调。
 *
 * ## 扫描期间不做连接
 *
 * 与 Android 同一个理由：扫描与建链抢同一个无线电，同时跑会让建链慢好几倍。
 * `QzxyViewModel.selectDevice` 会先 `stopScan()` 再连，这条不变。
 */
internal class IosQzxyScanner : QzxyDeviceScanner {

    private val central: IosQzxyCentral
        get() = IosQzxyBle.central

    override fun startScan(
        durationMillis: Long,
        onDevice: (QzxyNearbyDevice) -> Unit,
        onFinished: (Boolean) -> Unit,
        onError: (String) -> Unit,
    ): Boolean = central.startScan(durationMillis, onDevice, onFinished, onError)

    override fun stopScan() = central.stopScan()
}
