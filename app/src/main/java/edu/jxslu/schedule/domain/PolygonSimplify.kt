package edu.jxslu.schedule.domain

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot

/**
 * 多边形顶点抽稀（2026-11-18）。
 *
 * 为什么需要：快趣的 `scopeArray` 是运营方手画的多边形，顶点数没有上限。高德 3D 底图
 * **每次移图 / 缩放都要把每个顶点重新投影一遍**，顶点多了拖动就掉帧（osmdroid 那条栅格
 * 路径同样每帧重投影，只是栅格底图本身便宜，看不出来）。还车点 / 禁停区只是背景提示，
 * 几十个顶点和几百个顶点肉眼几乎没差别。
 *
 * 算法用 Douglas-Peucker（按**米**算点到线段的垂距，不用度数——纬度方向 1 度约 111km、
 * 经度方向要乘 `cos(lat)`，直接按度数算会把形状拉歪）。容差从 1 米起步逐次翻倍，直到
 * 顶点数落进上限；极端情况下（自相交、锯齿状边界）翻倍也压不下来，最后按等距抽稀兜底，
 * 保证结果一定 ≤ 上限。
 *
 * 三条不变量：
 * - 首尾顶点一定保留（闭合环的接缝不能动）；
 * - 结果不足 3 个点就画不出来：共线那种退化形状走等距抽稀，抽完仍不足 3 个才原样返回；
 * - 纯函数，输入 ≤ 上限时直接返回同一个列表实例（调用方可以拿它做相等判断）。
 */
object PolygonSimplify {

    /** 纬度方向 1 度对应的米数（WGS-84 平均值，够本用途）。 */
    private const val METERS_PER_DEGREE_LAT = 111_320.0

    /** 起始容差（米）：小于这个尺度的折角留着也没人看得见。 */
    private const val INITIAL_TOLERANCE_METERS = 1.0

    /** 容差翻倍的最多轮数（1m × 2^24 已远超任何校区尺度，够不到就抽稀兜底）。 */
    private const val MAX_DOUBLINGS = 24

    /** 二分收敛的轮数：容差精度到 `high / 2^7`，1 米量级足够。 */
    private const val BISECTION_ROUNDS = 6

    /** 输入顶点数的硬上限：超过先等距抽到这个数再走 DP，防脏数据把解析拖死。 */
    private const val MAX_INPUT_POINTS = 1024

    /**
     * 把 [points] 压到至多 [maxPoints] 个顶点。
     *
     * @param maxPoints 上限，小于 3 时按 3 处理（再少就不是多边形了）。
     */
    fun simplify(points: List<GcjPoint>, maxPoints: Int): List<GcjPoint> {
        val limit = maxPoints.coerceAtLeast(3)
        if (points.size <= limit) return points
        // 坏坐标（NaN / Inf）进 DP 会让比较全落空，直接原样返回，交给绘制层判三点
        if (points.any { !it.lat.isFinite() || !it.lng.isFinite() }) return points
        // 输入先兜个上限：DP 每轮是 O(n log n)~O(n²)，服务端给个几万点的多边形能把
        // 解析线程拖住。真实轮廓都在几十点量级，这一步只是防脏数据。
        val source = if (points.size > MAX_INPUT_POINTS) {
            decimate(points, MAX_INPUT_POINTS)
        } else {
            points
        }

        val scaleLat = METERS_PER_DEGREE_LAT
        // 弧度换算写成 `度 * (PI / 180.0)`：JDK 与 Android 的 `Math.toRadians` 都是
        // `angdeg * DEGREES_TO_RADIANS`（pi/180 那个常量）。写成 `度 / 180.0 * PI` 会有 27% 的值
        // 差 1 ulp，形状与聚簇落点会跟着变。
        val scaleLng = METERS_PER_DEGREE_LAT * cos(source[0].lat * (PI / 180.0))
        var tolerance = INITIAL_TOLERANCE_METERS
        var simplified = douglasPeucker(source, tolerance, scaleLat, scaleLng)
        var round = 0
        while (simplified.size > limit && round < MAX_DOUBLINGS) {
            tolerance *= 2.0
            simplified = douglasPeucker(source, tolerance, scaleLat, scaleLng)
            round += 1
        }
        if (simplified.size > limit) {
            return decimate(simplified, limit).takeIf { it.size >= 3 } ?: source
        }
        // 容差是翻倍翻上去的，落点可能比上限粗一大截（24 个顶点的名额只用了 17 个）。
        // 在「上一个不够的容差」和「这一个够的容差」之间二分，找到能塞进上限的最小容差：
        // 顶点用得更满，形状误差也压到最小。只在真的翻过倍时做，1 米就够的情况不必。
        if (round > 0) {
            var low = tolerance / 2.0
            var high = tolerance
            repeat(BISECTION_ROUNDS) {
                val mid = (low + high) / 2.0
                val probe = douglasPeucker(source, mid, scaleLat, scaleLng)
                if (probe.size <= limit) {
                    high = mid
                    simplified = probe
                } else {
                    low = mid
                }
            }
        }
        return if (simplified.size < 3) {
            // DP 把整条折线压成了一条直线（共线 / 极扁的形状）：简化"成功"但结果画不出来。
            // 这时改用等距抽稀——同一条线上的等距点画出来还是那条线，形状没变、顶点数照压。
            decimate(source, limit).takeIf { it.size >= 3 } ?: source
        } else {
            simplified
        }
    }

    /**
     * Douglas-Peucker（显式栈，不递归）：顶点多的多边形递归会顶爆栈。
     */
    private fun douglasPeucker(
        points: List<GcjPoint>,
        toleranceMeters: Double,
        scaleLat: Double,
        scaleLng: Double,
    ): List<GcjPoint> {
        val last = points.lastIndex
        val keep = BooleanArray(points.size)
        keep[0] = true
        keep[last] = true
        val stack = ArrayDeque<Int>()
        stack.addLast(0)
        stack.addLast(last)
        while (stack.isNotEmpty()) {
            val end = stack.removeLast()
            val start = stack.removeLast()
            if (end - start < 2) continue
            var farthest = -1
            var farthestDistance = 0.0
            for (i in start + 1 until end) {
                val distance = perpendicularMeters(
                    points[i], points[start], points[end], scaleLat, scaleLng,
                )
                if (distance > farthestDistance) {
                    farthestDistance = distance
                    farthest = i
                }
            }
            if (farthest >= 0 && farthestDistance > toleranceMeters) {
                keep[farthest] = true
                stack.addLast(start)
                stack.addLast(farthest)
                stack.addLast(farthest)
                stack.addLast(end)
            }
        }
        return points.filterIndexed { index, _ -> keep[index] }
    }

    /** 点到线段 `a–b` 的垂距（米）：先把经纬度按当地尺度摊成平面直角坐标。 */
    private fun perpendicularMeters(
        point: GcjPoint,
        a: GcjPoint,
        b: GcjPoint,
        scaleLat: Double,
        scaleLng: Double,
    ): Double {
        val bx = (b.lng - a.lng) * scaleLng
        val by = (b.lat - a.lat) * scaleLat
        val px = (point.lng - a.lng) * scaleLng
        val py = (point.lat - a.lat) * scaleLat
        val lengthSquared = bx * bx + by * by
        if (lengthSquared <= 0.0) return hypot(px, py)
        val t = ((px * bx + py * by) / lengthSquared).coerceIn(0.0, 1.0)
        return hypot(px - bx * t, py - by * t)
    }

    /** 兜底：等距挑点，首尾保留。只在下限也压不住时用。 */
    private fun decimate(points: List<GcjPoint>, limit: Int): List<GcjPoint> {
        if (points.size <= limit) return points
        val last = points.lastIndex
        val picked = ArrayList<GcjPoint>(limit)
        for (i in 0 until limit) {
            // 用整数步长分摊，避免浮点累积误差把最后一格挤掉
            val index = (i.toLong() * last / (limit - 1)).toInt()
            picked += points[index]
        }
        return picked.distinctBy { it.lat to it.lng }.ifEmpty { points.take(limit) }
    }
}
