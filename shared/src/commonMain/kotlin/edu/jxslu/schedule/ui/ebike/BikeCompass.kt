package edu.jxslu.schedule.ui.ebike

import edu.jxslu.schedule.platform.Services
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlin.math.abs
import kotlin.math.min

/**
 * 骑行页蓝点的罗盘朝向（2026-10-08，DESIGN §3.9「蓝点朝向」）。
 *
 * 与 [BikeLocator] 同一层取向：放在 UI 层的小动作，没有状态、不给别的页面用。
 * 语义是**罗盘朝向**——手机顶边指向的地理北偏角（0 = 北、90 = 东），站着不动也实时
 * 显示；不是 GPS 行进方向（那条链 2 秒一跳还要过 `shouldMoveDot` 的 ≥4 米挪点闸门，
 * 站定就没方向，本页「找车」场景下体验差）。
 *
 * 2026-11-25 起整份在共享层：纯函数（低通 / 节流 / 轴向映射）两端共用，
 * 传感器本身走装配根给的流（[Services.current] 的 `bikeHeading`）。
 * 设备没有磁力计时那条流给 null，[heading] 退化成空流，蓝点自然退回无方向圆点，
 * 调用方零降级分支。
 *
 * **频率纪律**：传感器原生速率远高于 UI 需要的，而 heading 的终点是
 * `MapOverlayInputs` 的值比对 + 一次整幅地图重绘（`OsmMapView.update`）——
 * 直接把每帧读数灌进状态就复刻了 2026-10-01「点确认开锁后卡」的整幅重绘 bug。
 * 所以流内先低通压手抖、再按「变化 > [EMIT_ANGLE_STEP]° 且距上次发射 ≥ [EMIT_INTERVAL_MS]」
 * 节流（约 ≤10Hz），正常不动时根本不发。
 */
object BikeCompass {

    /** 低通系数：0 = 完全不动，1 = 完全跟手。0.15 平衡「压抖」与「跟手」。 */
    const val SMOOTHING_FACTOR = 0.15f

    /** 角度变化超过这个度数才值得往 UI 发（静止时罗盘常漂 ±1~2°，别跟着漂）。 */
    const val EMIT_ANGLE_STEP = 2f

    /** 两次发射的最小间隔（毫秒）：给整幅重绘留出喘息，压到约 ≤10Hz。 */
    const val EMIT_INTERVAL_MS = 100L

    /**
     * 屏幕旋转档（取值与 Android `android.view.Surface.ROTATION_*` 一致）。
     *
     * 各端平台层把「当前屏幕朝向」映射成这四个数：Android 直接读
     * `DisplayManager.getDisplay(Display.DEFAULT_DISPLAY).rotation`，iOS 没有「屏幕旋转档」
     * 这个概念（只有 `UIInterfaceOrientation`），需要时按它换算。
     */
    const val ROTATION_0 = 0
    const val ROTATION_90 = 1
    const val ROTATION_180 = 2
    const val ROTATION_270 = 3

    /** 坐标轴（取值与 Android `android.hardware.SensorManager.AXIS_*` 一致）。 */
    const val AXIS_X = 0
    const val AXIS_Y = 1
    const val AXIS_MINUS_X = 2
    const val AXIS_MINUS_Y = 3

    /**
     * 页面可见期间的罗盘朝向流（度，0–360，北 = 0 顺时针）。
     *
     * 订阅即注册平台传感器监听，取消（页面退后台 / 离开页面，收口在
     * `repeatOnLifecycle(STARTED)`）自动注销。无传感器 / 本平台没接罗盘 → 空流。
     */
    fun heading(): Flow<Float> = Services.current.bikeHeading() ?: emptyFlow()

    // ---------- 以下为纯函数，JVM 单测钉住（BikeCompassTest） ----------

    /**
     * 从 rotation vector 读数算屏幕顶边的北偏角（度，0–360）。
     *
     * 链路：`getRotationMatrixFromVector`（设备坐标系）→ `remapCoordinateSystem`
     * （重投影到**屏幕坐标系**——骑行页不锁竖屏，横屏时必须让方位角按屏幕顶边计，
     * 而不是设备 y 轴）→ `getOrientation` 取方位角。
     *
     * 矩阵那三步是平台 API（Android `SensorManager`），走 [sensorHeadingDegrees]；
     * 本函数只做出口防线（[normalizeHeading]），两端同一份。
     *
     * remap 的轴向映射是官方 `getOrientation` 文档的示例口径，见 [remapAxes]。
     *
     * 读数非法（矩阵退化 / NaN，如全零 rotation vector）返回 null，调用方直接丢帧——
     * 不丢的话 NaN 会顺着 `smoothed`/`lastEmitted` 污染状态机（`NaN > 2f` 恒 false），
     * 罗盘流从此整段哑掉。
     */
    fun headingFromRotation(rotationVector: FloatArray, displayRotation: Int): Float? {
        if (rotationVector.isEmpty()) return null
        val degrees = sensorHeadingDegrees(rotationVector, displayRotation) ?: return null
        return normalizeHeading(degrees)
    }

    /**
     * 方位角出口的唯一防线（可 JVM 测）：NaN / 无穷（矩阵退化，如全零 rotation vector）
     * 返回 null 丢帧——不丢的话 NaN 会顺着 `smoothed`/`lastEmitted` 污染状态机
     * （`NaN > 2f` 恒 false），罗盘流从此整段哑掉；有限值归一化到 [0, 360)。
     */
    fun normalizeHeading(radiansToDegrees: Float): Float? {
        if (!radiansToDegrees.isFinite()) return null
        return (radiansToDegrees + 360f) % 360f
    }

    /** 各屏幕旋转档对应的 remap 轴向；见 [headingFromRotation] 注释。 */
    fun remapAxes(displayRotation: Int): Pair<Int, Int> = when (displayRotation) {
        ROTATION_90 -> AXIS_Y to AXIS_MINUS_X
        ROTATION_180 -> AXIS_MINUS_X to AXIS_MINUS_Y
        ROTATION_270 -> AXIS_MINUS_Y to AXIS_X
        else -> AXIS_X to AXIS_Y
    }

    /**
     * 低通滤波，角度按**环形**插值：359° 与 2° 之间走 3° 的短路，不绕 357° 的远路。
     * 首帧（previous 为 null）直接落位，不从 0° 慢慢爬。
     */
    fun smoothAngle(previous: Float?, next: Float, factor: Float): Float {
        val prev = previous ?: return normalize(next)
        var delta = next - prev
        if (delta > 180f) delta -= 360f
        if (delta < -180f) delta += 360f
        return normalize(prev + delta * factor)
    }

    /** 归一化到 [0, 360)。 */
    fun normalize(degrees: Float): Float = (degrees % 360f + 360f) % 360f

    /**
     * 节流判定：与上个**已发射**值的角度差（环形）超过 [EMIT_ANGLE_STEP]、
     * 且距上次发射 ≥ [EMIT_INTERVAL_MS] 才发。首个值（previous 为 null）立即发，
     * 蓝点别等两秒才长出扇形（间隔闸门只对后续帧生效）。
     */
    fun shouldEmit(
        previousEmitted: Float?,
        next: Float,
        now: Long,
        lastEmitAt: Long,
        angleStep: Float = EMIT_ANGLE_STEP,
        intervalMs: Long = EMIT_INTERVAL_MS,
    ): Boolean {
        if (previousEmitted == null) return true
        return emittedAngleDistance(previousEmitted, next) > angleStep &&
            now - lastEmitAt >= intervalMs
    }

    /** 两个方位角的最短环形距离（0–180）。 */
    fun emittedAngleDistance(a: Float, b: Float): Float {
        val diff = abs(a - b)
        return min(diff, 360f - diff)
    }
}

/**
 * rotation vector → 屏幕顶边北偏角（度）的**平台那三步**：`getRotationMatrixFromVector`
 * → `remapCoordinateSystem`（按 [BikeCompass.remapAxes]）→ `getOrientation`。
 *
 * 这是本次搬迁里唯一没有跨平台等价物的一步：Android 是 `SensorManager` 的静态方法，
 * iOS 要 CoreMotion 的四元数（`CMAttitude`）。把它留在平台侧，纯函数与出口防线就都在
 * 共享层（[BikeCompass.headingFromRotation]）。
 *
 * `null` = 本平台不提供罗盘（iOS 本轮返回 null：`BikeMapInput.userHeadingFlow` 在那边
 * 本来就被忽略，见 `BikeMapContract.kt` 第四节）。
 */
internal expect fun sensorHeadingDegrees(
    rotationVector: FloatArray,
    displayRotation: Int,
): Float?
