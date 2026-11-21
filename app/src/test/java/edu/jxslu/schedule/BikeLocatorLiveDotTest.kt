package edu.jxslu.schedule

import edu.jxslu.schedule.ui.ebike.DotGate
import edu.jxslu.schedule.ui.ebike.LocationFix
import edu.jxslu.schedule.ui.ebike.LocateResult
import edu.jxslu.schedule.ui.ebike.shouldMoveDot
import edu.jxslu.schedule.ui.ebike.shouldRecenterFromCache
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 蓝点实时更新的挪点口径（DESIGN §3.9）：挪远了要跟、精度大涨要换、原地抖动不动。
 *
 * 纬度 0.0001° ≈ 11 米，样本位移都按这个折算。
 */
class BikeLocatorLiveDotTest {

    private fun fix(
        lat: Double,
        lng: Double,
        accuracy: Float = 20f,
    ): LocationFix = LocationFix(lat, lng, accuracy)

    /** 第一次有读数：不管准不准，蓝点先画上再说。 */
    @Test
    fun firstFixAlwaysMoves() {
        assertTrue(shouldMoveDot(null, fix(28.0, 112.0, accuracy = 0f)))
    }

    /** 站定不动，GPS 每两秒的读数都带几米随机抖动：不挪点。 */
    @Test
    fun stationaryJitterHeld() {
        val previous = fix(28.0000, 112.0000, accuracy = 15f)
        val next = fix(28.00001, 112.00001, accuracy = 15f)
        assertFalse(shouldMoveDot(previous, next))
    }

    /** 真走动了（约 11 米）：蓝点跟着人走。 */
    @Test
    fun realMovementFollows() {
        val previous = fix(28.0000, 112.0000, accuracy = 15f)
        val next = fix(28.0001, 112.0000, accuracy = 15f)
        assertTrue(shouldMoveDot(previous, next))
    }

    /** 原地没动，但网络点换成了准 4 倍的 GPS 点：换上更准的位置。 */
    @Test
    fun accuracyGainSwaps() {
        val previous = fix(28.0000, 112.0000, accuracy = 40f)
        val next = fix(28.000005, 112.000005, accuracy = 10f)
        assertTrue(shouldMoveDot(previous, next))
    }

    /** 原地没动、精度只小涨一点：不值得挪。 */
    @Test
    fun marginalAccuracyGainHeld() {
        val previous = fix(28.0000, 112.0000, accuracy = 20f)
        val next = fix(28.000005, 112.000005, accuracy = 15f)
        assertFalse(shouldMoveDot(previous, next))
    }

    /** 系统没给精度（0）时不走精度口径，只认位移，也不能把点挪没了。 */
    @Test
    fun unknownAccuracyFallsBackToMovement() {
        val previous = fix(28.0000, 112.0000, accuracy = 0f)
        val jitter = fix(28.000005, 112.000005, accuracy = 0f)
        assertFalse(shouldMoveDot(previous, jitter))
        val moved = fix(28.0001, 112.0000, accuracy = 0f)
        assertTrue(shouldMoveDot(previous, moved))
    }

    /** 双源交替：网络点比 GPS 粗一大截，哪怕偏出上百米也不许把蓝点拽过去。 */
    @Test
    fun muchCoarserFixIgnored() {
        val gps = fix(28.0000, 112.0000, accuracy = 10f)
        // 网络点偏出约 110 米、精度 100 米：位移够大，但粗得多，不认
        val network = fix(28.0010, 112.0000, accuracy = 100f)
        assertFalse(shouldMoveDot(gps, network))
    }

    /** 从粗的网络点升到准的 GPS 点：精度大涨，换上去。 */
    @Test
    fun coarseToFineAccepted() {
        val network = fix(28.0000, 112.0000, accuracy = 100f)
        val gps = fix(28.0005, 112.0000, accuracy = 8f)
        assertTrue(shouldMoveDot(network, gps))
    }

    /** 只有网络可用（两个读数一样粗）时，位移口径照常跟随，不该被上面那条闸住。 */
    @Test
    fun coarseOnlyStillFollows() {
        val previous = fix(28.0000, 112.0000, accuracy = 120f)
        val next = fix(28.0002, 112.0000, accuracy = 120f)
        assertTrue(shouldMoveDot(previous, next))
    }

    // ---------- DotGate：粗读数的逃生口 ----------

    /**
     * 进楼后 GPS 彻底没了、只剩网络点：单条粗读数仍然不认（双源交替的老口径不能退），
     * 但**连续两条彼此靠得住 + 已经 30 秒没采纳过读数**时放行，蓝点不再永久冻住。
     */
    @Test
    fun coarseEscapeNeedsPairAndStaleWindow() {
        val gate = DotGate()
        val t0 = 1_000_000L
        assertTrue("第一条读数直接画", gate.accept(fix(28.0, 112.0, accuracy = 20f), t0))

        // 4 秒后第一条粗读数：凑不成对，不认
        assertFalse(gate.accept(fix(28.0010, 112.0, accuracy = 200f), t0 + 4_000))
        // 6 秒后第二条粗读数（与上一条相距约 11 米）：凑成对了，但还没过 30 秒窗口，不认
        assertFalse(gate.accept(fix(28.0011, 112.0, accuracy = 200f), t0 + 6_000))
        // 40 秒后又来一条（与上一条仍同一片）：两条一致 + 超过窗口 → 采纳（人确实挪了）
        assertTrue(gate.accept(fix(28.0012, 112.0, accuracy = 200f), t0 + 40_000))
    }

    /** 站定不动、GPS 一直在刷新基准：计时永远到不了窗口，粗读数不许把点拽走。 */
    @Test
    fun stationaryKeepsBaselineFresh() {
        val gate = DotGate()
        var t = 1_000_000L
        var lat = 28.0
        assertTrue(gate.accept(fix(lat, 112.0, accuracy = 10f), t))
        repeat(6) {
            t += 10_000
            // 夹在中间的粗网络点：比 10 米的基准粗 15 倍，且间距不到 30 秒窗口
            assertFalse(gate.accept(fix(lat, 112.0, accuracy = 150f), t - 1_000))
            // 走 5 米的正常 GPS 读数：照位移口径采纳，基准一直是新的
            lat += 0.000045
            assertTrue(gate.accept(fix(lat, 112.0, accuracy = 10f), t))
        }
    }

    /** 两条粗读数彼此差出一百多米（两次网络定位各跳各的）：不当成「同一片」，不认。 */
    @Test
    fun coarsePairMustAgree() {
        val gate = DotGate()
        val t0 = 1_000_000L
        assertTrue(gate.accept(fix(28.0, 112.0, accuracy = 20f), t0))
        assertFalse(gate.accept(fix(28.0050, 112.0, accuracy = 300f), t0 + 40_000))
        // 相隔约 110 米：与前一条凑不成对
        assertFalse(gate.accept(fix(28.0060, 112.0, accuracy = 300f), t0 + 42_000))
    }

    /** 被拒的读数不挪基准（这是逃生口的前提，也是老 bug 的成因，单独钉一条）。 */
    @Test
    fun rejectedFixDoesNotMoveBaseline() {
        val gate = DotGate()
        val t0 = 1_000_000L
        assertTrue(gate.accept(fix(28.0, 112.0, accuracy = 20f), t0))
        assertFalse(gate.accept(fix(28.01, 112.0, accuracy = 500f), t0 + 40_000))
        // 基准还是那个 20 米的点：一条偏出 1 公里、精度 30 米的 GPS 读数照位移口径跟随
        assertTrue(gate.accept(fix(28.0100, 112.0, accuracy = 30f), t0 + 60_000))
    }

    // ---------- 缓存落地之后的镜头收敛（进页 / 点「定位」的两段用法，2026-11-21） ----------

    /**
     * 进页时缓存点还在上次那片（差出约 1 公里）、实时读数只有网络点：必须收敛，
     * 否则镜头冻在缓存位置（用户报的那条）。粗读数的精度 100 米远小于差距，够判。
     */
    @Test
    fun cacheFarBehindIsCorrectedByCoarseFix() {
        assertTrue(
            shouldRecenterFromCache(
                cacheLat = 28.0000,
                cacheLng = 112.0000,
                liveLat = 28.0100,
                liveLng = 112.0000,
                liveAccuracyMeters = 100f,
            ),
        )
    }

    /** 站定不动：网络点比缓存点偏 20 米、自己声明精度 60 米——差得还没它自己的误差大，不挪。 */
    @Test
    fun coarseFixWithinOwnAccuracyHeld() {
        assertFalse(
            shouldRecenterFromCache(
                cacheLat = 28.0000,
                cacheLng = 112.0000,
                liveLat = 28.00018,
                liveLng = 112.0000,
                liveAccuracyMeters = 60f,
            ),
        )
    }

    /** 精度比下限还好的时长（人真走了 50 米）：照位移口径收敛。 */
    @Test
    fun fineFixAfterShortWalkConverges() {
        assertTrue(
            shouldRecenterFromCache(
                cacheLat = 28.0000,
                cacheLng = 112.0000,
                liveLat = 28.00045,
                liveLng = 112.0000,
                liveAccuracyMeters = 8f,
            ),
        )
    }

    /** 实时读数没给精度（0）时退到位移下限：几米的差不动，几十米才动。 */
    @Test
    fun unknownLiveAccuracyFallsBackToFloor() {
        assertFalse(
            shouldRecenterFromCache(28.0000, 112.0, 28.0001, 112.0, liveAccuracyMeters = 0f),
        )
        assertTrue(
            shouldRecenterFromCache(28.0000, 112.0, 28.0004, 112.0, liveAccuracyMeters = 0f),
        )
    }

    /** 粗读数偏出 200 米、精度 300 米：仍在自己声明的误差内，不当成"人挪了"。 */
    @Test
    fun veryCoarseFixHeldWithinItsCircle() {
        assertFalse(
            shouldRecenterFromCache(
                cacheLat = 28.0000,
                cacheLng = 112.0000,
                liveLat = 28.0018,
                liveLng = 112.0000,
                liveAccuracyMeters = 300f,
            ),
        )
    }

    // ---------- 定位失败分档（进页那条自动定位只对可改的两档说话，2026-11-21） ----------

    /** 只有「没权限」「定位总开关关着」两档值得给用户一个「去设置」，其余只能等信号。 */
    @Test
    fun onlyFixableFailuresAreActionable() {
        assertTrue(LocateResult.Failed("x", LocateResult.Issue.Permission).actionable)
        assertTrue(LocateResult.Failed("x", LocateResult.Issue.ServiceOff).actionable)
        assertFalse(LocateResult.Failed("x", LocateResult.Issue.NoFix).actionable)
        assertFalse(LocateResult.Failed("x", LocateResult.Issue.Unsupported).actionable)
        // 不传分档时按「等不到读数」处理：进页不打扰
        assertFalse(LocateResult.Failed("x").actionable)
    }
}
