package edu.jxslu.schedule.ui.ebike

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.view.Display
import androidx.core.content.ContextCompat
import edu.jxslu.schedule.domain.AppClock
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeoutOrNull

/**
 * [BikeLocationSource] 的 Android 实现（iOS 移植 P4，2026-11-25）。
 *
 * 代码从搬迁前的 `ui/ebike/BikeLocator.kt` 原样搬来，只删掉三件已经上移到共享层的事：
 * 坐标转换（`Gcj02`）、挪点判据（`DotGate`）、失败文案（`BikeLocator.failure`）。
 * 只认平台 [LocationManager]——不引 Google Play Services 的融合定位，那套在国内机型上
 * 本来也不可用。
 *
 * 由 `GraphServices` 装配（它持有 `applicationContext`）；共享层通过
 * `AppServices.bikeLocation()` 拿到。
 */
class AndroidBikeLocation(context: Context) : BikeLocationSource {

    private val appContext: Context = context.applicationContext

    /** 缓存定位的三个来源，`PASSIVE` 是别的应用请求定位时顺带拿到的结果。 */
    private val allProviders = listOf(
        LocationManager.GPS_PROVIDER,
        LocationManager.NETWORK_PROVIDER,
        LocationManager.PASSIVE_PROVIDER,
    )

    /**
     * 精确或粗略任一授权即可（API 31+ 用户可能只给「大致位置」）。
     */
    override fun hasPermission(): Boolean =
        isGranted(Manifest.permission.ACCESS_FINE_LOCATION) ||
            isGranted(Manifest.permission.ACCESS_COARSE_LOCATION)

    private fun isGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(appContext, permission) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * 取一次定位（WGS84 原始读数）。
     *
     * 顺序：先看五分钟内的缓存定位（瞬时返回），没有再同时挂网络与 GPS 两条更新，
     * 谁先给结果用谁。网络定位先到也无所谓——把地图中心落到用户那一片，粗略坐标就够。
     */
    override suspend fun currentFix(timeoutMs: Long, acceptCached: Boolean): FixOutcome {
        if (!hasPermission()) {
            return FixOutcome.Failed(LocateResult.Issue.Permission)
        }
        val manager = appContext.getSystemService(LocationManager::class.java)
            ?: return FixOutcome.Failed(LocateResult.Issue.Unsupported)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && !manager.isLocationEnabled) {
            return FixOutcome.Failed(LocateResult.Issue.ServiceOff)
        }

        if (acceptCached) freshLastKnown(manager)?.let {
            return FixOutcome.Ok(it.toFix(), cached = true)
        }

        val providers = enabledProviders(manager)
        val fix = awaitFix(manager, providers, timeoutMs)
            ?: return FixOutcome.Failed(LocateResult.Issue.NoFix)
        return FixOutcome.Ok(fix.toFix(), cached = false)
    }

    /**
     * 连续定位（DESIGN §3.9「蓝点实时更新」）。
     *
     * 先发五分钟内的缓存定位（蓝点立刻出现，不等 GPS 慢慢找），再挂网络与 GPS 两条更新。
     * **定位服务关着 / 没有可用 provider 时这条流不结束**（2026-11-19 修）：它原来发一条
     * 失败就 `return@flow`，而页面的收集跑在 `repeatOnLifecycle(STARTED)` 里——从通知栏
     * 把定位打开、Activity 只走 ON_PAUSE 不走 STOP，流不会重启，于是蓝点、跟随、「距你」
     * 整页停摆，只能退出页面再进。现在原地等它恢复（页面不可见时由生命周期的取消收掉）。
     */
    override fun fixUpdates(): Flow<FixOutcome> = flow {
        if (!hasPermission()) {
            emit(FixOutcome.Failed(LocateResult.Issue.Permission))
            return@flow
        }
        val manager = appContext.getSystemService(LocationManager::class.java)
        if (manager == null) {
            emit(FixOutcome.Failed(LocateResult.Issue.Unsupported))
            return@flow
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && !manager.isLocationEnabled) {
            emit(FixOutcome.Failed(LocateResult.Issue.ServiceOff))
            while (!manager.isLocationEnabled) delay(BikeLocator.PROVIDER_POLL_MS)
        }

        freshLastKnown(manager)?.let { emit(FixOutcome.Ok(it.toFix(), cached = true)) }

        var providers = enabledProviders(manager)
        if (providers.isEmpty()) {
            emit(
                FixOutcome.Failed(
                    LocateResult.Issue.ServiceOff,
                    message = "正在等待可用的定位来源",
                ),
            )
            while (true) {
                delay(BikeLocator.PROVIDER_POLL_MS)
                providers = enabledProviders(manager)
                if (providers.isNotEmpty()) break
            }
        }
        locationUpdates(manager, providers, BikeLocator.STREAM_MIN_TIME_MS, 0f)
            .collect { location -> emit(FixOutcome.Ok(location.toFix(), cached = false)) }
    }

    /** GPS 与网络定位都给上次结果，取最新的那条；只看五分钟以内的。 */
    private fun freshLastKnown(manager: LocationManager): Location? =
        allProviders.mapNotNull { provider ->
            try {
                if (manager.isProviderEnabled(provider)) manager.getLastKnownLocation(provider) else null
            } catch (_: SecurityException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            }
        }
            .filter { AppClock.nowMillis() - it.time <= BikeLocator.MAX_LAST_KNOWN_AGE_MS }
            .maxByOrNull { it.time }

    /** 网络定位排在前面：出结果快，粗略坐标就够把地图中心落过去。 */
    private fun enabledProviders(manager: LocationManager): List<String> =
        listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .filter { provider ->
                try {
                    manager.isProviderEnabled(provider)
                } catch (_: IllegalArgumentException) {
                    false
                }
            }

    private suspend fun awaitFix(
        manager: LocationManager,
        providers: List<String>,
        timeoutMs: Long,
    ): Location? =
        withTimeoutOrNull(timeoutMs) {
            if (providers.isEmpty()) return@withTimeoutOrNull null
            locationUpdates(manager, providers, minTimeMs = 0L, minDistanceMeters = 0f).firstOrNull()
        }

    /**
     * 两个 provider 的更新汇成一条流，谁先来算谁。
     *
     * 四个回调方法**都要写全**：这些方法在 API 30 的 `android.jar` 里才是 default 方法，
     * 编译期不写也能过；但 26~29 的设备上框架会真的调用 `onStatusChanged` 等，
     * 类里没有实现就抛 `AbstractMethodError`。这类崩溃只在低版本机器上出现。
     */
    private fun locationUpdates(
        manager: LocationManager,
        providers: List<String>,
        minTimeMs: Long,
        minDistanceMeters: Float,
    ): Flow<Location> =
        callbackFlow {
            val registered = providers.map { provider ->
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        trySend(location)
                    }

                    @Deprecated("API 29 起废弃，但 26~29 的框架仍会调它")
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

                    override fun onProviderEnabled(provider: String) = Unit

                    override fun onProviderDisabled(provider: String) = Unit
                }
                val ok = try {
                    manager.requestLocationUpdates(
                        provider,
                        minTimeMs,
                        minDistanceMeters,
                        listener,
                        Looper.getMainLooper(),
                    )
                    true
                } catch (_: SecurityException) {
                    false
                } catch (_: IllegalArgumentException) {
                    false
                }
                provider to (listener to ok)
            }

            if (registered.none { (_, value) -> value.second }) {
                // 一个都没挂上：别让调用方白等满超时
                close()
            }

            awaitClose {
                registered.forEach { (_, value) ->
                    try {
                        manager.removeUpdates(value.first)
                    } catch (_: Exception) {
                        // 注销失败没有补救手段，也不影响结果
                    }
                }
            }
        }

    private fun Location.toFix(): LocationFix = LocationFix(latitude, longitude, accuracy)
}

/**
 * 罗盘朝向流（Android）：`TYPE_ROTATION_VECTOR` + `SENSOR_DELAY_UI`，页面可见期间订阅，
 * 取消自动注销。设备没有该传感器（无磁力计的平板、部分模拟器）时给空流。
 *
 * 低通与节流在共享层（[BikeCompass]），这里只负责「拿到读数、算出方位角、发出去」。
 */
class AndroidBikeHeading(context: Context) {

    private val appContext: Context = context.applicationContext

    fun heading(): Flow<Float> = callbackFlow {
        val manager = appContext.getSystemService(SensorManager::class.java)
        val displayManager = appContext.getSystemService(DisplayManager::class.java)
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (manager == null || displayManager == null || sensor == null) {
            close()
            return@callbackFlow
        }
        val listener = object : SensorEventListener {
            private var smoothed: Float? = null
            private var lastEmitted: Float? = null
            private var lastEmitAt = 0L

            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return
                // 屏幕旋转档取 DisplayManager（API 17+，minSdk 26 全覆盖）：
                // `SensorManager.getDisplay` 要 API 30，低版本直接 NoSuchMethodError
                val display = displayManager.getDisplay(Display.DEFAULT_DISPLAY) ?: return
                val next = BikeCompass.headingFromRotation(event.values, display.rotation) ?: return
                val value = BikeCompass.smoothAngle(
                    smoothed,
                    next,
                    BikeCompass.SMOOTHING_FACTOR,
                )
                smoothed = value
                val now = AppClock.nowMillis()
                if (BikeCompass.shouldEmit(lastEmitted, value, now, lastEmitAt)) {
                    lastEmitted = value
                    lastEmitAt = now
                    trySend(value)
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        awaitClose { manager.unregisterListener(listener) }
    }
}

/**
 * [sensorHeadingDegrees] 的 Android 实现：`SensorManager` 的三步矩阵链。
 *
 * 轴向映射走共享层的 [BikeCompass.remapAxes]（那边有的单测 `BikeCompassTest` 钉住四档取值，
 * 与这里的 `SensorManager.AXIS_*` 数值一致）。
 */
internal actual fun sensorHeadingDegrees(
    rotationVector: FloatArray,
    displayRotation: Int,
): Float? {
    val rotationMatrix = FloatArray(9)
    val remapped = FloatArray(9)
    val orientation = FloatArray(3)
    SensorManager.getRotationMatrixFromVector(rotationMatrix, rotationVector)
    val (axisX, axisY) = BikeCompass.remapAxes(displayRotation)
    SensorManager.remapCoordinateSystem(rotationMatrix, axisX, axisY, remapped)
    SensorManager.getOrientation(remapped, orientation)
    return Math.toDegrees(orientation[0].toDouble()).toFloat()
}
