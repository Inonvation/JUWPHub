package edu.jxslu.schedule

import edu.jxslu.schedule.domain.BikeNearby
import edu.jxslu.schedule.domain.GcjPoint
import edu.jxslu.schedule.domain.PolygonSimplify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos

/**
 * 多边形抽稀（2026-11-18，见 [PolygonSimplify] 的类注释）。
 *
 * 钉住三件事：上限一定压得住、形状不被压歪、退化输入不塌成画不出来的两点。
 */
class PolygonSimplifyTest {

    @Test
    fun `顶点没超上限时原样返回同一个实例`() {
        val points = square(edgeSteps = 3)
        assertSame(points, PolygonSimplify.simplify(points, 16))
        // 上限恰好等于顶点数也算没超
        assertSame(points, PolygonSimplify.simplify(points, points.size))
    }

    @Test
    fun `顶点超上限时压到上限以内并保留首尾`() {
        val points = square(edgeSteps = 50)
        assertEquals(200, points.size)

        val simplified = PolygonSimplify.simplify(points, 16)

        assertTrue("顶点数 ${simplified.size} 没压到 16 以内", simplified.size <= 16)
        assertTrue("抽稀后不足三点就画不出多边形", simplified.size >= 3)
        assertEquals(points.first(), simplified.first())
        assertEquals(points.last(), simplified.last())
    }

    @Test
    fun `正方形的四个角抽稀后还在`() {
        val points = square(edgeSteps = 50)
        val simplified = PolygonSimplify.simplify(points, 16)
        val corners = listOf(
            GcjPoint(SOUTH, WEST),
            GcjPoint(SOUTH, EAST),
            GcjPoint(NORTH, EAST),
            GcjPoint(NORTH, WEST),
        )

        corners.forEach { corner ->
            val nearest = simplified.minOf { BikeNearby.distanceMeters(corner.lat, corner.lng, it.lat, it.lng) }
            assertTrue("角点 $corner 被抽掉了（最近顶点 $nearest 米）", nearest < 1.0)
        }
    }

    @Test
    fun `圆环抽稀后边界误差在几米以内`() {
        val points = circle(radiusMeters = 120.0, count = 400)
        val simplified = PolygonSimplify.simplify(points, 24)

        assertTrue(simplified.size <= 24)
        // 量的是**到多边形边界（线段）的距离**，不是到最近顶点的距离：画出来的是多边形，
        // 顶点之间那一大段弦也是形状的一部分。半径 120m 的圆压到十几个顶点，矢高约 2m。
        val worst = points.maxOf { point -> distanceToBoundaryMeters(point, simplified) }
        assertTrue("抽稀后边界偏移 $worst 米太大", worst < 5.0)
    }

    @Test
    fun `共线的退化形状压到上限内且仍能画`() {
        // 一条直线上的 50 个点：DP 只会留下首尾（两点画不出来），这时改走等距抽稀
        val line = (0 until 50).map { GcjPoint(28.68 + it * 1e-5, 116.02) }

        val simplified = PolygonSimplify.simplify(line, 8)

        assertTrue("压到 ${simplified.size} 个点，没进上限", simplified.size <= 8)
        assertTrue("不足三点，绘制层会当它不存在", simplified.size >= 3)
        // 等距抽稀同样保留首尾，形状不变
        assertEquals(line.first(), simplified.first())
        assertEquals(line.last(), simplified.last())
    }

    @Test
    fun `坏坐标原样返回`() {
        val broken = listOf(
            GcjPoint(28.68, 116.02),
            GcjPoint(Double.NaN, 116.03),
            GcjPoint(28.69, 116.04),
        ) + circle(radiusMeters = 50.0, count = 30)

        assertSame(broken, PolygonSimplify.simplify(broken, 8))
    }

    @Test
    fun `上限过小时也不会交出画不出来的两点`() {
        val points = circle(radiusMeters = 80.0, count = 60)

        val simplified = PolygonSimplify.simplify(points, 1)

        // 上限被抬到 3；圆环上三点就能"画出来"但形状会歪，仍然不交两点
        assertTrue("结果不足三点，绘制层会当它不存在", simplified.size >= 3)
        assertTrue(simplified.size <= points.size)
    }

    @Test
    fun `两万点的脏数据也压得住且不把解析拖住`() {
        val huge = circle(radiusMeters = 200.0, count = 20_000)
        val startedAt = System.nanoTime()

        val simplified = PolygonSimplify.simplify(huge, 40)

        val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000
        assertTrue(simplified.size <= 40)
        assertTrue("抽稀后不足三点就画不出来", simplified.size >= 3)
        // 输入上限就是为了这条：没有它，DP 在几万点上会退化成 O(n²)
        assertTrue("耗时 $elapsedMs ms，解析线程会被拖住", elapsedMs < 5_000)
    }

    private companion object {
        const val SOUTH = 28.680
        const val NORTH = 28.690
        const val WEST = 116.020
        const val EAST = 116.030

        /** 一个正方形环（不重复首点），每条边插 [edgeSteps] 个中间点。 */
        fun square(edgeSteps: Int): List<GcjPoint> {
            val corners = listOf(
                GcjPoint(SOUTH, WEST),
                GcjPoint(SOUTH, EAST),
                GcjPoint(NORTH, EAST),
                GcjPoint(NORTH, WEST),
            )
            val result = ArrayList<GcjPoint>()
            corners.forEachIndexed { index, from ->
                val to = corners[(index + 1) % corners.size]
                for (step in 0 until edgeSteps) {
                    val t = step.toDouble() / edgeSteps
                    result += GcjPoint(from.lat + (to.lat - from.lat) * t, from.lng + (to.lng - from.lng) * t)
                }
            }
            return result
        }

        /** 以校区附近为圆心、半径 [radiusMeters] 的正圆环。 */
        fun circle(radiusMeters: Double, count: Int): List<GcjPoint> {
            val centerLat = 28.685
            val centerLng = 116.025
            val metersPerDegLat = 111_320.0
            val metersPerDegLng = metersPerDegLat * cos(Math.toRadians(centerLat))
            return (0 until count).map { index ->
                val angle = 2 * Math.PI * index / count
                GcjPoint(
                    centerLat + radiusMeters * kotlin.math.sin(angle) / metersPerDegLat,
                    centerLng + radiusMeters * kotlin.math.cos(angle) / metersPerDegLng,
                )
            }
        }

        /**
         * 点到闭合多边形边界的距离（米）：把经纬度按当地尺度摊成平面直角坐标再算。
         * 与 `PolygonSimplify` 内部的口径一致，只是这里要的是到**线段**的距离。
         */
        fun distanceToBoundaryMeters(point: GcjPoint, ring: List<GcjPoint>): Double {
            val scaleLat = 111_320.0
            val scaleLng = 111_320.0 * cos(Math.toRadians(point.lat))
            var nearest = Double.MAX_VALUE
            for (index in ring.indices) {
                val a = ring[index]
                val b = ring[(index + 1) % ring.size]
                val bx = (b.lng - a.lng) * scaleLng
                val by = (b.lat - a.lat) * scaleLat
                val px = (point.lng - a.lng) * scaleLng
                val py = (point.lat - a.lat) * scaleLat
                val lengthSquared = bx * bx + by * by
                val distance = if (lengthSquared <= 0.0) {
                    kotlin.math.hypot(px, py)
                } else {
                    val t = ((px * bx + py * by) / lengthSquared).coerceIn(0.0, 1.0)
                    kotlin.math.hypot(px - bx * t, py - by * t)
                }
                if (distance < nearest) nearest = distance
            }
            return nearest
        }
    }
}
