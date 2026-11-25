package edu.jxslu.schedule.ui.ebike

import edu.jxslu.schedule.domain.AppClock
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLAuthorizationStatusDenied
import platform.CoreLocation.kCLAuthorizationStatusRestricted

/**
 * [BikeLocationSource] 的 iOS 实现（iOS 移植 P4，2026-11-25）。
 *
 * ## 为什么**不**用 `CLLocationManagerDelegate`
 *
 * 委托是并行流最贵的一种缝：`locationManager(_:didUpdateLocations:)` 等方法在 Kotlin/Native
 * 上的确切签名（`List<*>` 还是 `NSArray`、`NSError` 还是 `NSError?`）只能靠一次真机编译确认，
 * 写错了是把整条 iOS 构建卡住的一票否决。而页面要的只是「一条读数」——`CLLocationManager`
 * 的 `location` 属性本身就是最近一次读数，**轮询它**与「等回调」拿到的是同一个对象。
 *
 * 所以这里只碰三件最稳的 API：`requestWhenInUseAuthorization()` /
 * `startUpdatingLocation()` / `location`，外加 `authorizationStatus()` 判权限。
 *
 * shortcut: 等 iOS 真机链路验过一次后，可以把这条轮询换成委托（更省电：轮询窗口只在
 * 授权刚变完的那几秒）。升级条件是「有人在真机上量出这段轮询的耗电」，不是"看起来更正规"。
 *
 * ## Info.plist 必须有 `NSLocationWhenInUseUsageDescription`
 *
 * 缺了这条，`requestWhenInUseAuthorization()` 是**静默无效**（不弹框、状态一直
 * `NotDetermined`），现象与「用户拒绝」一模一样。宿主要求见本轮报告。
 *
 * ## 与 Android 的口径差异（都在共享层消化，页面不用分支）
 *
 * | 项 | Android | iOS |
 * |---|---|---|
 * | 权限 | 精确 / 粗略两个运行时权限 | 一个开关（`WhenInUse` 即算授权） |
 * | 「定位总开关」 | `LocationManager.isLocationEnabled` | `locationServicesEnabled()` |
 * | 缓存读数 | `getLastKnownLocation`（五分钟内） | `location` 属性（可能很旧，按下限兜底） |
 * | 坐标系 | WGS84，共享层转 GCJ-02 | 同左（CoreLocation 给的就是 WGS84） |
 */
@OptIn(ExperimentalForeignApi::class)
class IosBikeLocation : BikeLocationSource {

    private val manager: CLLocationManager by lazy { CLLocationManager() }

    /** 授权轮询的步长（只在「刚请求完授权」的窗口里用）。 */
    private val authPollMs = 200L

    /** 读读数的轮询步长：与共享层的 2 秒节奏同一档，再密只是白耗电。 */
    private val fixPollMs = 500L

    override fun hasPermission(): Boolean {
        if (!locationServicesEnabled()) return false
        val status = CLLocationManager.authorizationStatus()
        return status == kCLAuthorizationStatusAuthorizedWhenInUse ||
            status == kCLAuthorizationStatusAuthorizedAlways
    }

    override suspend fun currentFix(timeoutMs: Long, acceptCached: Boolean): FixOutcome {
        if (!locationServicesEnabled()) {
            return FixOutcome.Failed(LocateResult.Issue.ServiceOff)
        }
        if (!hasPermission()) {
            // 还没问过 → 问一次，等用户点完（最多等到这次定位自己的超时）
            requestAuthorizationThenWait(timeoutMs)
            if (!hasPermission()) {
                val status = CLLocationManager.authorizationStatus()
                val denied = status == kCLAuthorizationStatusDenied ||
                    status == kCLAuthorizationStatusRestricted
                return FixOutcome.Failed(LocateResult.Issue.Permission, if (denied) null else NO_ANSWER_TEXT)
            }
        }

        if (acceptCached) cachedFix()?.let { return FixOutcome.Ok(it, cached = true) }

        manager.startUpdatingLocation()
        try {
            val fix = withTimeoutOrNull(timeoutMs) {
                var last: LocationFix? = null
                while (last == null) {
                    last = readFix()
                    if (last == null) delay(fixPollMs)
                }
                last
            }
            return fix?.let { FixOutcome.Ok(it, cached = false) }
                ?: FixOutcome.Failed(LocateResult.Issue.NoFix)
        } finally {
            manager.stopUpdatingLocation()
        }
    }

    override fun fixUpdates(): Flow<FixOutcome> = flow {
        if (!locationServicesEnabled()) {
            emit(FixOutcome.Failed(LocateResult.Issue.ServiceOff))
            // 等它恢复（从「设置 → 隐私 → 定位服务」打开后不用退页重进）
            while (!locationServicesEnabled()) delay(BikeLocator.PROVIDER_POLL_MS)
        }
        if (!hasPermission()) {
            emit(FixOutcome.Failed(LocateResult.Issue.Permission))
            return@flow
        }
        cachedFix()?.let { emit(FixOutcome.Ok(it, cached = true)) }
        manager.startUpdatingLocation()
        try {
            while (true) {
                delay(BikeLocator.STREAM_MIN_TIME_MS)
                readFix()?.let { emit(FixOutcome.Ok(it, cached = false)) }
            }
        } finally {
            manager.stopUpdatingLocation()
        }
    }

    /** 弹一次授权框并等它有个结论；超时（用户没点）就当没授权，由调用方给提示。 */
    private suspend fun requestAuthorizationThenWait(timeoutMs: Long) {
        manager.requestWhenInUseAuthorization()
        withTimeoutOrNull(timeoutMs) {
            while (!hasPermission()) {
                delay(authPollMs)
                val status = CLLocationManager.authorizationStatus()
                if (status == kCLAuthorizationStatusDenied ||
                    status == kCLAuthorizationStatusRestricted
                ) {
                    return@withTimeoutOrNull
                }
            }
        }
    }

    /**
     * 系统的「上次定位」：五分钟内才算数（与 Android `getLastKnownLocation` 同口径）。
     * `CLLocation.timestamp` 是这次读数产生的时刻，不是我们读它的时刻。
     */
    private fun cachedFix(): LocationFix? {
        val fix = readFix() ?: return null
        val at = lastTimestampMillis ?: return null
        val age = AppClock.nowMillis() - at
        return fix.takeIf { age in 0..BikeLocator.MAX_LAST_KNOWN_AGE_MS }
    }

    /** 上一次 [readFix] 拿到的读数时刻（`CLLocation.timestamp` 的 epoch 毫秒）。 */
    private var lastTimestampMillis: Long? = null

    private fun readFix(): LocationFix? {
        val location: CLLocation = manager.location ?: return null
        val coordinate = location.coordinate
        val accuracy = location.horizontalAccuracy
        if (accuracy < 0) return null
        // `CLLocation.timestamp` 是这次读数产生的时刻（秒，含小数）；乘 1000 取整成 epoch 毫秒。
        // 不用 `NSDate.description` 再解析——那个字符串的格式不保证，"yyyy-MM-dd HH:mm:ss +0000"，
        // 时区后缀还要另外处理。
        lastTimestampMillis = ((location.timestamp?.timeIntervalSince1970 ?: 0.0) * 1000.0).toLong()
        return coordinate.useContents { LocationFix(latitude, longitude, accuracy.toFloat()) }
    }

    private fun locationServicesEnabled(): Boolean = CLLocationManager.locationServicesEnabled()

    private companion object {
        /** 授权框还挂着（用户没点）时的说法：与「拒绝」区分开，别让人白找设置。 */
        const val NO_ANSWER_TEXT = "还没授予定位权限，请在弹出的提示里允许后重试"
    }
}

/**
 * [sensorHeadingDegrees] 的 iOS 实现：**本轮返回 null**。
 *
 * 理由不是「做不到」而是「没地方用」：`BikeMapInput.userHeadingFlow` 在 iOS 侧被忽略
 * （见 `BikeMapContract.kt` 第四节：系统气球旋转后造型别扭），而 CoreMotion 的
 * `CMAttitude` → 屏幕顶边方位角多一步 `UIInterfaceOrientation` 换算，没有消费方就先不写。
 *
 * 调用方零分支：`BikeCompass.heading()` 退化成空流，蓝点画成无方向圆点。
 *
 * shortcut: iOS 地图层开始用朝向时，在这里接 `CMMotionManager.startDeviceMotionUpdates`
 * （`attitude.yaw` 即北偏角，注意 `CMAttitudeReferenceFrame` 要选 `xTrueNorthZVertical`
 * 才与 Android 的 `TYPE_ROTATION_VECTOR` 同基准）。
 */
internal actual fun sensorHeadingDegrees(
    rotationVector: FloatArray,
    displayRotation: Int,
): Float? = null
