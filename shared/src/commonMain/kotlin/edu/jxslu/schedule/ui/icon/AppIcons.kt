/**
 * 图标集合：从 HugeIcons（Android-only AAR）导出成纯 Compose Multiplatform 的 ImageVector。
 *
 * **这是生成物，不要手改。** 改了下一次重跑就覆盖了。
 *
 * ## 为什么不用原 AAR
 *
 * 图标原本来自 `com.github.rikkahub:hugeicons-compose:1.4`（JitPack，`isTransitive = false`）。
 * 它只发 Android 变体（AAR），iOS（Kotlin/Native）上没有对应产物——`:shared` 的 `commonMain`
 * 一旦引用就编不过。而图标本身只是矢量数据，`androidx.compose.ui.graphics.vector.*` 在 CMP 的
 * `commonMain` 里是齐的，把数据搬进源码即可，平台差异消失。
 *
 * ## 怎么重跑（在仓库根）
 *
 * ```
 * .\gradlew.bat :app:testDebugUnitTest --tests "*IconExportTool*" -DexportIcons=true
 * ```
 *
 * 生成器在 `app/src/test/java/edu/jxslu/schedule/tools/IconExportTool.kt`：反射
 * `me.rerere.hugeicons.stroke.<名>Kt.get<名>(HugeIcons)` 拿到 ImageVector，把每个 path 压成
 * 路径字符串交给 `addPathNodes(...)`，并逐节点回读自检（不一致直接失败并打出图标名）。
 *
 * 图标清单按 `HugeIcons.Xxx` / `AppIcons.Xxx` 两种调用点扫描得出，不写死：新增图标后直接重跑，
 * 按字母序插入，diff 稳定。
 *
 * ## 尺寸
 *
 * 每个图标的 `width/height/viewportWidth/viewportHeight` 都是 AAR 里的原值（多数 24dp / 24），
 * 不是统一写死的 24。
 */

package edu.jxslu.schedule.ui.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

object AppIcons {

    val Add01: ImageVector by lazy {
        build(
            name = "Add01",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M12.001 5.00003 V19.002"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M19.002 12.002 L4.99998 12.002"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val AddToList: ImageVector by lazy {
        build(
            name = "AddToList",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M3.5 9 V14 C3.5 17.7712 3.5 19.6569 4.67157 20.8284 C5.84315 22 7.72876 22 11.5 22 H12.5 C16.2712 22 18.1569 22 19.3284 20.8284 C20.5 19.6569 20.5 17.7712 20.5 14 V10 C20.5 6.22876 20.5 4.34315 19.3284 3.17157 C18.1569 2 16.2712 2 12.5 2 H12"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M13.5 17 H17.5"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M13.5 7 H17.5"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M13.5 12 H17.5"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M6.5 16.5 C6.5 16.5 7.46758 16.7672 8 18 C8 18 9 15 11 14"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M10 5 H3.5 M10 5 C10 4.15973 7.67332 2.58984 7.08333 2 M10 5 C10 5.84027 7.67331 7.41016 7.08333 8"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val Agreement01: ImageVector by lazy {
        build(
            name = "Agreement01",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M2 7 H4.86503 C5.59174 7 6.28868 6.64031 6.80255 6.00005 C7.53643 5.08565 8.62195 4.76636 9.60655 5.17529 L11 5.75402 M2 15.9668 H3.62068 C4.78017 15.9668 5.35991 15.9668 5.90812 16.1213 C5.93477 16.1288 5.96134 16.1366 5.98782 16.1446 C6.53259 16.3101 7.01496 16.6209 7.97971 17.2427 C9.86787 18.4596 10.812 19.0681 11.8407 18.994 C11.8892 18.9905 11.9376 18.9858 11.9859 18.9801 C13.0096 18.8577 13.8119 18.0821 15.4166 16.5308 L17 15"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M22 7.23384 H19.4832 C18.4174 7.23384 17.6649 6.65468 16.9003 5.88258 C16.4051 5.38247 15.7731 5.08772 15.1088 5.04713 C14.366 5.00175 13.5053 4.93785 12.784 5.13601 C11.9811 5.35656 11.451 5.96063 10.8902 6.59196 L9.44309 8.22111 C8.8523 8.88621 8.8523 9.96455 9.44309 10.6297 C9.953 11.2037 10.7519 11.2928 11.3519 10.8424 C11.7837 10.5184 12.2743 9.99506 12.7831 9.83025 C13.3557 9.64476 13.7109 10.0615 14.0354 10.4999 L16.3556 13.6344 C17.2167 14.7976 17.6472 15.3793 18.2358 15.6896 C18.8244 16 19.4969 16 20.842 16 H22"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
            )
        }
    }

    val AiBrain01: ImageVector by lazy {
        build(
            name = "AiBrain01",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M7 4.5 C5.34315 4.5 4 5.84315 4 7.5 C4 8.06866 4.15822 8.60037 4.43304 9.0535 C3.04727 9.31855 2 10.537 2 12 C2 13.463 3.04727 14.6814 4.43304 14.9465 M7 4.5 C7 3.11929 8.11929 2 9.5 2 C10.8807 2 12 3.11929 12 4.5 V19.5 C12 20.8807 10.8807 22 9.5 22 C8.11929 22 7 20.8807 7 19.5 C5.34315 19.5 4 18.1569 4 16.5 C4 15.9313 4.15822 15.3996 4.43304 14.9465 M7 4.5 C7 5.31791 7.39278 6.04408 8 6.50018 M4.43304 14.9465 C4.78948 14.3588 5.34207 13.9032 6 13.6707"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M17 19.4999 C18.6569 19.4999 20 18.1567 20 16.4999 C20 15.9312 19.8418 15.3995 19.567 14.9464 C20.9527 14.6813 22 13.4629 22 11.9999 C22 10.5369 20.9527 9.31843 19.567 9.05338 M17 19.4999 C17 20.8806 15.8807 21.9999 14.5 21.9999 C13.1193 21.9999 12 20.8806 12 19.4999 L12 4.49988 C12 3.11917 13.1193 1.99988 14.5 1.99988 C15.8807 1.99988 17 3.11917 17 4.49988 C18.6569 4.49988 20 5.84302 20 7.49988 C20 8.06854 19.8418 8.60024 19.567 9.05338 M17 19.4999 C17 18.682 16.6072 17.9558 16 17.4997 M19.567 9.05338 C19.2105 9.64109 18.6579 10.0966 18 10.3292"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val AlarmClock: ImageVector by lazy {
        build(
            name = "AlarmClock",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M20.5 12.5 C20.5 17.1944 16.6944 21 12 21 C7.30558 21 3.5 17.1944 3.5 12.5 C3.5 7.80558 7.30558 4 12 4 C16.6944 4 20.5 7.80558 20.5 12.5 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M5.88 18.7031 L3.5 21.0031"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M18.14 18.668 L20.5 20.998"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M5 3 L2 6"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M22 6 L19 3"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M12 8 V12.5 L14 14.5"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val Alert02: ImageVector by lazy {
        build(
            name = "Alert02",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M13.9248 21 H10.0752 C5.44476 21 3.12955 21 2.27636 19.4939 C1.42317 17.9879 2.60736 15.9914 4.97574 11.9985 L6.90057 8.75333 C9.17559 4.91778 10.3131 3 12 3 C13.6869 3 14.8244 4.91777 17.0994 8.75332 L19.0243 11.9985 C21.3926 15.9914 22.5768 17.9879 21.7236 19.4939 C20.8704 21 18.5552 21 13.9248 21 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M12 9 V13"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M12.125 16.75 H12 M12.25 16.75 C12.25 16.8881 12.1381 17 12 17 C11.8619 17 11.75 16.8881 11.75 16.75 C11.75 16.6119 11.8619 16.5 12 16.5 C12.1381 16.5 12.25 16.6119 12.25 16.75 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val AlertCircle: ImageVector by lazy {
        build(
            name = "AlertCircle",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M22 12 A10 10 0 1 0 2 12 A10 10 0 1 0 22 12 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M12 8 V12"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M12.125 15.75 H12 M12.25 15.75 C12.25 15.8881 12.1381 16 12 16 C11.8619 16 11.75 15.8881 11.75 15.75 C11.75 15.6119 11.8619 15.5 12 15.5 C12.1381 15.5 12.25 15.6119 12.25 15.75 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val Analytics01: ImageVector by lazy {
        build(
            name = "Analytics01",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M7 17 L7 13"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M12 17 L12 7"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M17 17 L17 11"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M2.5 12 C2.5 7.52166 2.5 5.28249 3.89124 3.89124 C5.28249 2.5 7.52166 2.5 12 2.5 C16.4783 2.5 18.7175 2.5 20.1088 3.89124 C21.5 5.28249 21.5 7.52166 21.5 12 C21.5 16.4783 21.5 18.7175 20.1088 20.1088 C18.7175 21.5 16.4783 21.5 12 21.5 C7.52166 21.5 5.28249 21.5 3.89124 20.1088 C2.5 18.7175 2.5 16.4783 2.5 12 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val AppStore: ImageVector by lazy {
        build(
            name = "AppStore",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M2.5 12 C2.5 7.52166 2.5 5.28249 3.89124 3.89124 C5.28249 2.5 7.52166 2.5 12 2.5 C16.4783 2.5 18.7175 2.5 20.1088 3.89124 C21.5 5.28249 21.5 7.52166 21.5 12 C21.5 16.4783 21.5 18.7175 20.1088 20.1088 C18.7175 21.5 16.4783 21.5 12 21.5 C7.52166 21.5 5.28249 21.5 3.89124 20.1088 C2.5 18.7175 2.5 16.4783 2.5 12 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M11 7 L12 8.66667 M17 17 L13.4 11"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M13 7 L7 17"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M6.5 14 H12.5 M17.5 14 L15.5 14"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val ArrowDown01: ImageVector by lazy {
        build(
            name = "ArrowDown01",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M18 9.00005 C18 9.00005 13.5811 15 12 15 C10.4188 15 6 9 6 9"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val ArrowDownDouble: ImageVector by lazy {
        build(
            name = "ArrowDownDouble",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M17.9997 12.5 C17.9997 12.5 13.5807 18.5 11.9996 18.5 C10.4185 18.5 5.99966 12.5 5.99966 12.5"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M17.9997 5.50005 C17.9997 5.50005 13.5807 11.5 11.9996 11.5 C10.4185 11.5 5.99966 5.5 5.99966 5.5"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val ArrowLeft01: ImageVector by lazy {
        build(
            name = "ArrowLeft01",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M15 6 C15 6 9.00001 10.4189 9 12 C8.99999 13.5812 15 18 15 18"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val ArrowLeft02: ImageVector by lazy {
        build(
            name = "ArrowLeft02",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M5.5 12.002 H19"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M10.9999 18.002 C10.9999 18.002 4.99998 13.583 4.99997 12.0019 C4.99996 10.4208 11 6.00195 11 6.00195"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val ArrowLeftRight: ImageVector by lazy {
        build(
            name = "ArrowLeftRight",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M19.9999 17 L3.99994 17"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M17 14 C17 14 19.9999 16.2095 19.9999 17 C19.9999 17.7906 16.9999 20 16.9999 20"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M4.99994 7 L19.9999 7"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M6.99992 4 C6.99992 4 3.99995 6.20947 3.99994 7.00002 C3.99993 7.79058 6.99994 10 6.99994 10"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val ArrowRight01: ImageVector by lazy {
        build(
            name = "ArrowRight01",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M9.00005 6 C9.00005 6 15 10.4189 15 12 C15 13.5812 9 18 9 18"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val ArrowUp01: ImageVector by lazy {
        build(
            name = "ArrowUp01",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M17.9998 15 C17.9998 15 13.5809 9.00001 11.9998 9 C10.4187 8.99999 5.99985 15 5.99985 15"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val BatteryCharging01: ImageVector by lazy {
        build(
            name = "BatteryCharging01",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M2 12 C2 9.17157 2 7.75736 2.87868 6.87868 C3.75736 6 5.17157 6 8 6 H13 C15.8284 6 17.2426 6 18.1213 6.87868 C19 7.75736 19 9.17157 19 12 C19 14.8284 19 16.2426 18.1213 17.1213 C17.2426 18 15.8284 18 13 18 H8 C5.17157 18 3.75736 18 2.87868 17.1213 C2 16.2426 2 14.8284 2 12 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M10.8282 9 L9.08572 11.1749 C8.89899 11.4079 9.03283 11.7433 9.33733 11.8053 L11.1627 12.1773 C11.4873 12.2434 11.6111 12.6147 11.3842 12.8413 L9.22216 15"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M19 9.5 L20.0272 9.6712 C20.7085 9.78475 21.0491 9.84152 21.3076 10.0067 C21.5618 10.1691 21.7612 10.4044 21.8796 10.6819 C22 10.964 22 11.3093 22 12 C22 12.6907 22 13.036 21.8796 13.3181 C21.7612 13.5956 21.5618 13.8309 21.3076 13.9933 C21.0491 14.1585 20.7085 14.2153 20.0272 14.3288 L19 14.5"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
            )
        }
    }

    val BellRing: ImageVector by lazy {
        build(
            name = "BellRing",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M22 8 C22 5.7 21.2 3.7 20 2"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M4 2 C2.8 3.7 2 5.7 2 8"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M16 18 C16 20.2091 14.2091 22 12 22 C9.79086 22 8 20.2091 8 18"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M4.43654 18 H19.5625 C20.2903 18 20.6542 18 20.8648 17.8951 C21.274 17.6913 21.4929 17.2359 21.3964 16.789 C21.3468 16.559 21.1194 16.2749 20.6648 15.7066 L20.4951 15.4944 C20.0392 14.9246 19.8113 14.6397 19.6184 14.3409 C19.0187 13.4119 18.6477 12.354 18.5356 11.254 C18.4995 10.9002 18.4995 10.5353 18.4995 9.8056 V8.5 C18.4995 8.03572 18.4995 7.80358 18.4867 7.60758 C18.2898 4.60304 15.8965 2.20977 12.892 2.01285 C12.696 2 12.4638 2 11.9995 2 C11.5353 2 11.3031 2 11.1071 2.01285 C8.10258 2.20977 5.70931 4.60304 5.51239 7.60758 C5.49954 7.80358 5.49954 8.03572 5.49954 8.5 V9.8056 C5.49954 10.5353 5.49954 10.9002 5.46349 11.254 C5.35143 12.354 4.98035 13.4119 4.38067 14.3409 C4.18779 14.6397 3.95985 14.9246 3.50401 15.4944 L3.33427 15.7066 C2.87964 16.2749 2.65233 16.559 2.60268 16.789 C2.50621 17.2359 2.72509 17.6913 3.13431 17.8951 C3.3449 18 3.70878 18 4.43654 18 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
            )
        }
    }

    val Bicycle: ImageVector by lazy {
        build(
            name = "Bicycle",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M6 19.9986 C8.20914 19.9986 10 18.2112 10 16.0063 C10 13.8014 8.20914 12.0139 6 12.0139 C3.79086 12.0139 2 13.8014 2 16.0063 C2 18.2112 3.79086 19.9986 6 19.9986 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M18 19.9986 C20.2091 19.9986 22 18.2112 22 16.0063 C22 13.8014 20.2091 12.0139 18 12.0139 C15.7909 12.0139 14 13.8014 14 16.0063 C14 18.2112 15.7909 19.9986 18 19.9986 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M6 16.0062 H10.3706 C10.7302 16.0062 11.0622 15.8135 11.2399 15.5014 L15.5 8.02148"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M12 13.012 L7 7.02344 M7 7.02344 H5 M7 7.02344 H9"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M18.0531 15.9709 L14.8057 4.03097 C16.2565 3.96037 19.3585 3.83085 19.9908 6.21002"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val Blur: ImageVector by lazy {
        build(
            name = "Blur",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M12.5926 2.21 C12.2371 1.93 11.7433 1.93 11.3877 2.21 C9.51122 3.66 3.97049 8.39 4.00012 13.9 C4.00012 18.36 7.58531 22 12.0001 22 C16.4149 22 20 18.37 20 13.91 C20.0099 8.48 14.4593 3.67 12.5926 2.21 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M12 2 V22"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M12 19 L20 15 M12 14.1806 L19 10.5 M12 9.36145 L16.5727 7"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val Bolt: ImageVector by lazy {
        build(
            name = "Bolt",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M10.0437 21 H13.9563 C15.6662 21 16.5211 21 17.2206 20.5856 C17.9201 20.1712 18.3353 19.4188 19.1657 17.9139 L20.8211 14.9139 C21.607 13.4895 22 12.7774 22 12 C22 11.2226 21.607 10.5105 20.8211 9.08614 L19.1657 6.08614 C18.3353 4.58123 17.9201 3.82877 17.2206 3.41439 C16.5211 3 15.6662 3 13.9563 3 L10.0437 3 C8.33384 3 7.47888 3 6.77939 3.41439 C6.0799 3.82877 5.6647 4.58123 4.83429 6.08614 L3.1789 9.08614 C2.39297 10.5105 2 11.2226 2 12 C2 12.7774 2.39297 13.4895 3.1789 14.9139 L4.83429 17.9139 C5.6647 19.4188 6.0799 20.1712 6.77939 20.5856 C7.47888 21 8.33384 21 10.0437 21 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M12 16 C14.2091 16 16 14.2091 16 12 C16 9.79086 14.2091 8 12 8 C9.79086 8 8 9.79086 8 12 C8 14.2091 9.79086 16 12 16 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val Book01: ImageVector by lazy {
        build(
            name = "Book01",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M20 22 H6 C4.89543 22 4 21.1046 4 20 M4 20 C4 18.8954 4.89543 18 6 18 H18 C19.1046 18 20 17.1046 20 16 V2 C20 3.10457 19.1046 4 18 4 L10 4 C7.17157 4 5.75736 4 4.87868 4.87868 C4 5.75736 4 7.17157 4 10 V20 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M18.5 18 C18.5 18 17.5 18.7628 17.5 20 C17.5 21.2372 18.5 22 18.5 22"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M9 4 V8"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val BookEdit: ImageVector by lazy {
        build(
            name = "BookEdit",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M5.33333 3.00001 C7.79379 2.99657 10.1685 3.88709 12 5.5 V21 C10.1685 19.3871 7.79379 18.4966 5.33333 18.5 C3.77132 18.5 2.99032 18.5 2.64526 18.2792 C2.4381 18.1466 2.35346 18.0619 2.22086 17.8547 C2 17.5097 2 16.8941 2 15.6629 V6.40322 C2 4.97543 2 4.26154 2.54874 3.68286 C3.09748 3.10418 3.65923 3.07432 4.78272 3.0146 C4.965 3.00491 5.14858 3.00001 5.33333 3.00001 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M12 21 V5.5 C13.8315 3.88709 16.2062 2.99657 18.6667 3.00001 C18.8514 3.00001 19.035 3.00491 19.2173 3.0146 C20.3408 3.07432 20.9025 3.10418 21.4513 3.68286 C22 4.26154 22 4.97543 22 6.40322 V11.5"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M21.6887 14.9339 L21.0661 14.3113 C20.651 13.8962 19.978 13.8962 19.5629 14.3113 L16.2141 17.6601 C15.769 18.1052 15.4656 18.6722 15.3421 19.2895 L15 21 L16.7105 20.6579 C17.3278 20.5344 17.8948 20.231 18.3399 19.7859 L21.6887 16.4371 C22.1038 16.022 22.1038 15.349 21.6887 14.9339 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val BookMarked: ImageVector by lazy {
        build(
            name = "BookMarked",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M20 14 V18 H6 C4.89543 18 4 18.8954 4 20 M10 2 C7.17157 2 5.75736 2 4.87868 2.87868 C4 3.75736 4 5.17157 4 8 V20 M4 20 C4 21.1046 4.89543 22 6 22 H20"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M14 8.45369 V4.98797 C14 3.57943 14 2.87515 14.4393 2.43758 C14.8787 2 15.5858 2 17 2 C18.4142 2 19.1213 2 19.5607 2.43758 C20 2.87515 20 3.57942 20 4.98797 V8.45369 C20 9.37363 20 9.83359 19.7121 9.96791 C19.4242 10.1022 19.0694 9.80776 18.3598 9.21883 L17.6402 8.62154 C17.3341 8.36748 17.181 8.24045 17 8.24045 C16.819 8.24045 16.6659 8.36748 16.3598 8.62154 L15.6402 9.21883 C14.9306 9.80776 14.5758 10.1022 14.2879 9.96791 C14 9.83359 14 9.37363 14 8.45369 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M19.5 18 C19.5 18 18.5 18.7628 18.5 20 C18.5 21.2372 19.5 22 19.5 22"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val Bookmark01: ImageVector by lazy {
        build(
            name = "Bookmark01",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M4 17.9808 V9.70753 C4 6.07416 4 4.25748 5.17157 3.12874 C6.34315 2 8.22876 2 12 2 C15.7712 2 17.6569 2 18.8284 3.12874 C20 4.25748 20 6.07416 20 9.70753 V17.9808 C20 20.2867 20 21.4396 19.2272 21.8523 C17.7305 22.6514 14.9232 19.9852 13.59 19.1824 C12.8168 18.7168 12.4302 18.484 12 18.484 C11.5698 18.484 11.1832 18.7168 10.41 19.1824 C9.0768 19.9852 6.26947 22.6514 4.77285 21.8523 C4 21.4396 4 20.2867 4 17.9808 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M4 7 H20"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
            )
        }
    }

    val Browser: ImageVector by lazy {
        build(
            name = "Browser",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M3 12 C3 7.75736 3 5.63604 4.31802 4.31802 C5.63604 3 7.75736 3 12 3 C16.2426 3 18.364 3 19.682 4.31802 C21 5.63604 21 7.75736 21 12 C21 16.2426 21 18.364 19.682 19.682 C18.364 21 16.2426 21 12 21 C7.75736 21 5.63604 21 4.31802 19.682 C3 18.364 3 16.2426 3 12 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M3 9 H21"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val Bug01: ImageVector by lazy {
        build(
            name = "Bug01",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M3.01309 4.99084 C2.89323 6.05084 3.55249 8.42285 6.48923 8.42285"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M17.5951 8.38081 C18.8357 8.57881 21.1132 7.49881 20.9957 5.00281"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M20.9928 20.9989 C21.0528 19.9429 20.1777 17.5549 17.599 17.4229"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M6.45163 17.4708 C5.65013 17.2308 3.01306 18.3348 3.01306 20.9988"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M9.3299 6.11884 C9.35388 5.09884 9.84533 2.99884 12.0029 2.99884 C13.9208 2.99884 14.5861 4.61884 14.676 6.11884 M6.26131 9.41884 C6.38118 8.63884 7.29216 6.81484 9.36586 6.63484 C11.4635 6.55564 14.3403 6.58684 14.8797 6.67084 C15.5869 6.73377 17.2951 7.43884 17.7506 9.41884 C17.9124 10.4388 17.8285 11.8788 17.8524 12.7188 C17.8165 13.5588 17.9207 15.2623 17.7565 16.1388 C17.6367 17.0988 16.9894 18.4668 16.1024 19.3068 C14.7838 20.7228 11.1639 22.2108 8.03534 19.4508 C6.41713 17.8908 6.30925 16.3788 6.18939 15.7788 C6.15725 15.4571 6.15875 13.8763 6.16541 12.3588 C6.14144 11.046 6.17235 9.78063 6.26131 9.41884 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M3.01306 12.8988 H5.9498"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M20.9929 12.8988 L18.1161 12.8988"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M12.0033 16.4988 L12.0033 20.2788"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
            )
        }
    }

    val Calendar01: ImageVector by lazy {
        build(
            name = "Calendar01",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M16 2 V6 M8 2 V6"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M13 4 H11 C7.22876 4 5.34315 4 4.17157 5.17157 C3 6.34315 3 8.22876 3 12 V14 C3 17.7712 3 19.6569 4.17157 20.8284 C5.34315 22 7.22876 22 11 22 H13 C16.7712 22 18.6569 22 19.8284 20.8284 C21 19.6569 21 17.7712 21 14 V12 C21 8.22876 21 6.34315 19.8284 5.17157 C18.6569 4 16.7712 4 13 4 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M3 10 H21"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M10 18.5002 L9.99999 13.8474 C9.99999 13.6557 9.86325 13.5002 9.69458 13.5002 H9 M14 18.4983 L15.4855 13.8923 C15.4951 13.8626 15.5 13.8315 15.5 13.8002 C15.5 13.6346 15.3657 13.5002 15.2 13.5002 L13 13.5"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val Calendar03: ImageVector by lazy {
        build(
            name = "Calendar03",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M16 2 V6 M8 2 V6"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M13 4 H11 C7.22876 4 5.34315 4 4.17157 5.17157 C3 6.34315 3 8.22876 3 12 V14 C3 17.7712 3 19.6569 4.17157 20.8284 C5.34315 22 7.22876 22 11 22 H13 C16.7712 22 18.6569 22 19.8284 20.8284 C21 19.6569 21 17.7712 21 14 V12 C21 8.22876 21 6.34315 19.8284 5.17157 C18.6569 4 16.7712 4 13 4 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M3 10 H21"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M12.1258 14 H12.0008 M12.1258 18 H12.0008 M7.625 14 H7.5 M7.625 18 H7.5 M16.625 14 H16.5 M12.2508 14 C12.2508 14.1381 12.1389 14.25 12.0008 14.25 C11.8628 14.25 11.7508 14.1381 11.7508 14 C11.7508 13.8619 11.8628 13.75 12.0008 13.75 C12.1389 13.75 12.2508 13.8619 12.2508 14 Z M12.2508 18 C12.2508 18.1381 12.1389 18.25 12.0008 18.25 C11.8628 18.25 11.7508 18.1381 11.7508 18 C11.7508 17.8619 11.8628 17.75 12.0008 17.75 C12.1389 17.75 12.2508 17.8619 12.2508 18 Z M7.75 14 C7.75 14.1381 7.63807 14.25 7.5 14.25 C7.36193 14.25 7.25 14.1381 7.25 14 C7.25 13.8619 7.36193 13.75 7.5 13.75 C7.63807 13.75 7.75 13.8619 7.75 14 Z M7.75 18 C7.75 18.1381 7.63807 18.25 7.5 18.25 C7.36193 18.25 7.25 18.1381 7.25 18 C7.25 17.8619 7.36193 17.75 7.5 17.75 C7.63807 17.75 7.75 17.8619 7.75 18 Z M16.75 14 C16.75 14.1381 16.6381 14.25 16.5 14.25 C16.3619 14.25 16.25 14.1381 16.25 14 C16.25 13.8619 16.3619 13.75 16.5 13.75 C16.6381 13.75 16.75 13.8619 16.75 14 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val CalendarAdd01: ImageVector by lazy {
        build(
            name = "CalendarAdd01",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M16 2 V6 M8 2 V6"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M21 14 V12 C21 8.22876 21 6.34315 19.8284 5.17157 C18.6569 4 16.7712 4 13 4 H11 C7.22876 4 5.34315 4 4.17157 5.17157 C3 6.34315 3 8.22876 3 12 V14 C3 17.7712 3 19.6569 4.17157 20.8284 C5.34315 22 7.22876 22 11 22 H13"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M3 10 H21"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M17.5 15 V22 M21 18.5 L14 18.5"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val CalendarCheckIn01: ImageVector by lazy {
        build(
            name = "CalendarCheckIn01",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M16 2 V6 M8 2 V6"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M21 16 V12 C21 8.22876 21 6.34315 19.8284 5.17157 C18.6569 4 16.7712 4 13 4 H11 C7.22876 4 5.34315 4 4.17157 5.17157 C3 6.34315 3 8.22876 3 12 V14 C3 17.7712 3 19.6569 4.17157 20.8284 C5.34315 22 7.22876 22 11 22 H12"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M3 10 H21"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M21 19.5 H14.5 M16.5 22 C15.9943 21.5085 14 20.2002 14 19.5 C14 18.7998 15.9943 17.4915 16.5 17"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val CalendarCheckOut01: ImageVector by lazy {
        build(
            name = "CalendarCheckOut01",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M16 2 V6 M8 2 V6"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M21 15 V12 C21 8.22876 21 6.34315 19.8284 5.17157 C18.6569 4 16.7712 4 13 4 H11 C7.22876 4 5.34315 4 4.17157 5.17157 C3 6.34315 3 8.22876 3 12 V14 C3 17.7712 3 19.6569 4.17157 20.8284 C5.34315 22 7.22876 22 11 22 H12"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M3 10 H21"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M18.5 22 C19.0057 21.5085 21 20.2002 21 19.5 C21 18.7998 19.0057 17.4915 18.5 17 M20.5 19.5 H14"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val CalendarClock: ImageVector by lazy {
        build(
            name = "CalendarClock",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M16 2 V6 M8 2 V6"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M20.8985 8 C20.7706 6.69989 20.4816 5.82475 19.8284 5.17157 C18.6569 4 16.7712 4 13 4 H11 C7.22876 4 5.34315 4 4.17157 5.17157 C3 6.34315 3 8.22876 3 12 V14 C3 17.7712 3 19.6569 4.17157 20.8284 C4.97975 21.6366 6.1277 21.8873 8 21.965"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M3 10 H8"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M16 22 C19.3137 22 22 19.3137 22 16 C22 12.6863 19.3137 10 16 10 C12.6863 10 10 12.6863 10 16 C10 19.3137 12.6863 22 16 22 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M16 13 V16 L18 17"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val CalendarOff: ImageVector by lazy {
        build(
            name = "CalendarOff",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M16 2 V6"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M9.00857 4.00857 C9.60705 4 10.268 4 11 4 H13 C16.7712 4 18.6569 4 19.8284 5.17157 C21 6.34315 21 8.22876 21 12 V14 C21 14.732 21 15.393 20.9914 15.9914 M4.73134 4.73134 C4.52726 4.85573 4.34187 5.00127 4.17157 5.17157 C3 6.34315 3 8.22876 3 12 V14 C3 17.7712 3 19.6569 4.17157 20.8284 C5.34315 22 7.22876 22 11 22 H13 C16.7712 22 18.6569 22 19.8284 20.8284 C19.9987 20.6581 20.1443 20.4727 20.2687 20.2687"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M3 10 H10 M21 10 H15"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M2 2 L22 22"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val CalendarPlus01: ImageVector by lazy {
        build(
            name = "CalendarPlus01",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M9.5 16 H14.5 M12 18.5 V13.5"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M16 2 V6 M8 2 V6"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M3 10 H21"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M13 4 H11 C7.22876 4 5.34315 4 4.17157 5.17157 C3 6.34315 3 8.22876 3 12 V14 C3 17.7712 3 19.6569 4.17157 20.8284 C5.34315 22 7.22876 22 11 22 H13 C16.7712 22 18.6569 22 19.8284 20.8284 C21 19.6569 21 17.7712 21 14 V12 C21 8.22876 21 6.34315 19.8284 5.17157 C18.6569 4 16.7712 4 13 4 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val CalendarSetting01: ImageVector by lazy {
        build(
            name = "CalendarSetting01",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M16 2 V6 M8 2 V6"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M21 12.5 V12 C21 8.22876 21 6.34315 19.8284 5.17157 C18.6569 4 16.7712 4 13 4 H11 C7.22876 4 5.34315 4 4.17157 5.17157 C3 6.34315 3 8.22876 3 12 V14 C3 17.7712 3 19.6569 4.17157 20.8284 C5.34315 22 7.22876 22 11 22"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M3 10 H21"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M19.6709 16.7593 C19.8803 17.1249 20 17.5485 20 18 C20 18.4514 19.8804 18.8749 19.671 19.2404 C19.2402 19.993 18.4293 20.5 17.5 20.5 M15.3291 16.7593 C15.1197 17.1249 15 17.5485 15 18 C15 18.4514 15.1196 18.8749 15.329 19.2404 C15.7598 19.993 16.5707 20.5 17.5 20.5 M17.5 20.5 L17.5 22 M17.5 15.5 C18.4292 15.5 19.24 16.0069 19.6709 16.7593 M17.5 15.5 C16.5708 15.5 15.76 16.0069 15.3291 16.7593 M17.5 15.5 L17.5 14 M21 15.9998 L19.6709 16.7593 M14 19.9998 L15.329 19.2404 M21 19.9998 L19.671 19.2404 M14 15.9998 L15.3291 16.7593"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val CalendarSync: ImageVector by lazy {
        build(
            name = "CalendarSync",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M16 2 V6 M8 2 V6"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M12 22 H11 C7.22876 22 5.34315 22 4.17157 20.8284 C3 19.6569 3 17.7712 3 14 V12 C3 8.22876 3 6.34315 4.17157 5.17157 C5.34315 4 7.22876 4 11 4 H13 C16.7712 4 18.6569 4 19.8284 5.17157 C20.8915 6.23467 20.99 7.8857 20.9991 11"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M3 10 H21"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M14.385 15.7855 C15.0271 14.4342 16.4044 13.5 18 13.5 C19.4806 13.5 20.7733 14.3044 21.4649 15.5 M14.385 15.7855 C14.6833 16 15.1502 16 16 16 H17 M14.385 15.7855 C14.3524 15.762 14.3218 15.736 14.2929 15.7071 C14 15.4142 14 14.9428 14 14 V13 M21.615 19.2145 C20.9729 20.5658 19.5956 21.5 18 21.5 C16.5194 21.5 15.2267 20.6956 14.5351 19.5 M21.615 19.2145 C21.3166 19 20.8498 19 20 19 H19 M21.615 19.2145 C21.6476 19.238 21.6782 19.264 21.7071 19.2929 C22 19.5858 22 20.0572 22 21 V22"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val Cancel01: ImageVector by lazy {
        build(
            name = "Cancel01",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M18 6 L6.00081 17.9992 M17.9992 18 L6 6.00085"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val Certificate01: ImageVector by lazy {
        build(
            name = "Certificate01",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M11.5 22 C7.49306 22 5.48959 22 4.2448 20.5355 C3 19.0711 3 16.714 3 12 C3 7.28596 3 4.92893 4.2448 3.46447 C5.48959 2 7.49306 2 11.5 2 C15.5069 2 17.5104 2 18.7552 3.46447 C19.7572 4.64332 19.9527 6.40054 19.9908 9.5"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M8 8 H15 M8 13 H11"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M19.6092 18.1054 C20.4521 17.4918 21 16.4974 21 15.375 C21 13.511 19.489 12 17.625 12 H17.375 C15.511 12 14 13.511 14 15.375 C14 16.4974 14.5479 17.4918 15.3908 18.1054 M19.6092 18.1054 C19.0523 18.5108 18.3666 18.75 17.625 18.75 H17.375 C16.6334 18.75 15.9477 18.5108 15.3908 18.1054 M19.6092 18.1054 L20.192 19.9404 C20.4143 20.6403 20.5255 20.9903 20.4951 21.2082 C20.4318 21.6617 20.0619 21.9984 19.6252 22 C19.4154 22.0008 19.101 21.8358 18.4723 21.5059 C18.2027 21.3644 18.0679 21.2936 17.93 21.252 C17.649 21.1673 17.351 21.1673 17.07 21.252 C16.9321 21.2936 16.7973 21.3644 16.5277 21.5059 C15.899 21.8358 15.5846 22.0008 15.3748 22 C14.9381 21.9984 14.5682 21.6617 14.5049 21.2082 C14.4745 20.9903 14.5857 20.6403 14.808 19.9404 L15.3908 18.1054"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
            )
        }
    }

    val ChartAverage: ImageVector by lazy {
        build(
            name = "ChartAverage",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M20.9877 21 H9.9877 C6.68787 21 5.03795 21 4.01283 19.9749 C2.9877 18.9497 2.9877 17.2998 2.9877 14 V3"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M6.9877 7 C7.61628 5.87847 8.46901 5 9.781 5 C15.3279 5 12.2101 17 18.1708 17 C19.4921 17 20.3397 16.1157 20.9877 15"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M10.9998 11.0006 H10.9873 M11.0123 11.0006 C11.0123 11.0144 11.0011 11.0256 10.9873 11.0256 C10.9735 11.0256 10.9623 11.0144 10.9623 11.0006 C10.9623 10.9868 10.9735 10.9756 10.9873 10.9756 C11.0011 10.9756 11.0123 10.9868 11.0123 11.0006 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M6.99981 11.0006 H6.98731 M7.01231 11.0006 C7.01231 11.0144 7.00112 11.0256 6.98731 11.0256 C6.9735 11.0256 6.96231 11.0144 6.96231 11.0006 C6.96231 10.9868 6.9735 10.9756 6.98731 10.9756 C7.00112 10.9756 7.01231 10.9868 7.01231 11.0006 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M20.9998 11.0006 H20.9873 M21.0123 11.0006 C21.0123 11.0144 21.0011 11.0256 20.9873 11.0256 C20.9735 11.0256 20.9623 11.0144 20.9623 11.0006 C20.9623 10.9868 20.9735 10.9756 20.9873 10.9756 C21.0011 10.9756 21.0123 10.9868 21.0123 11.0006 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M16.9998 11.0006 H16.9873 M17.0123 11.0006 C17.0123 11.0144 17.0011 11.0256 16.9873 11.0256 C16.9735 11.0256 16.9623 11.0144 16.9623 11.0006 C16.9623 10.9868 16.9735 10.9756 16.9873 10.9756 C17.0011 10.9756 17.0123 10.9868 17.0123 11.0006 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val CheckList: ImageVector by lazy {
        build(
            name = "CheckList",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M11 6 L21 6"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M11 12 L21 12"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M11 18 L21 18"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M3 7.39286 C3 7.39286 4 8.04466 4.5 9 C4.5 9 6 5.25 8 4"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M3 18.3929 C3 18.3929 4 19.0447 4.5 20 C4.5 20 6 16.25 8 15"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val CheckmarkCircle02: ImageVector by lazy {
        build(
            name = "CheckmarkCircle02",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M22 12 C22 6.47715 17.5228 2 12 2 C6.47715 2 2 6.47715 2 12 C2 17.5228 6.47715 22 12 22 C17.5228 22 22 17.5228 22 12 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M8 12.5 L10.5 15 L16 9"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val ChevronDown: ImageVector by lazy {
        build(
            name = "ChevronDown",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M18 9.00005 C18 9.00005 13.5811 15 12 15 C10.4188 15 6 9 6 9"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val ChevronLeft: ImageVector by lazy {
        build(
            name = "ChevronLeft",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M15 18 C15 18 9.00001 13.5811 9 12 C8.99999 10.4188 15 6 15 6"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val ChevronRight: ImageVector by lazy {
        build(
            name = "ChevronRight",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M9.00005 18 C9.00005 18 15 13.5811 15 12 C15 10.4188 9 6 9 6"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val ChevronUp: ImageVector by lazy {
        build(
            name = "ChevronUp",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M18 15 C18 15 13.5811 9.00001 12 9 C10.4188 8.99999 6 15 6 15"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val ClipboardPaste: ImageVector by lazy {
        build(
            name = "ClipboardPaste",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M19.502 13.0005 H10.502"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M17.502 10.0005 C17.502 10.0005 20.5019 12.21 20.502 13.0005 C20.502 13.7911 17.502 16.0005 17.502 16.0005"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M13.998 2.00049 H8.99805 C8.16962 2.00049 7.49805 2.67206 7.49805 3.50049 C7.49805 4.32892 8.16962 5.00049 8.99805 5.00049 H13.998 C14.8265 5.00049 15.498 4.32892 15.498 3.50049 C15.498 2.67206 14.8265 2.00049 13.998 2.00049 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M15.4981 3.50049 C17.0515 3.5473 17.9781 3.72056 18.6194 4.36185 C19.1913 4.93377 19.391 5.73255 19.4607 7.00049 M7.49795 3.50049 C5.94456 3.5473 5.01802 3.72056 4.37673 4.36184 C3.49805 5.24053 3.49805 6.65474 3.49806 9.48318 L3.49805 16 C3.49805 18.8284 3.49806 20.2426 4.37674 21.1213 C5.25541 22 6.66963 22 9.49805 22 L13.498 22 C16.3265 22 17.7407 22 18.6194 21.1213 C19.1092 20.6315 19.3259 19.9753 19.4219 19.0005"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val ClipboardPen: ImageVector by lazy {
        build(
            name = "ClipboardPen",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M7.00002 4 L7 4.99997 C6.99998 5.94279 6.99998 6.4142 7.29287 6.7071 C7.58577 7 8.05718 7 9 7 L13 7 C13.9428 7 14.4142 7 14.7071 6.70711 C15 6.41421 15 5.94281 15 5 V4"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M7.99583 22.7483 C8.41003 22.7506 8.74768 22.4166 8.74999 22.0024 C8.75229 21.5882 8.41838 21.2506 8.00417 21.2483 L8 21.9983 L7.99583 22.7483 Z M3.87868 21.1213 L4.40901 20.591 L3.87868 21.1213 Z M3.87868 4.87868 L4.40901 5.40901 V5.40901 L3.87868 4.87868 Z M7 4.01732 L6.99162 3.26737 C6.98688 3.26742 6.98215 3.26752 6.97741 3.26766 L7 4.01732 Z M15 4.01732 L15.0226 3.26766 C15.0179 3.26752 15.0131 3.26742 15.0084 3.26737 L15 4.01732 Z M13.45 4 L12.7149 4.1492 C12.7853 4.49569 13.088 4.746 13.4416 4.74995 L13.45 4 Z M8.54997 4 L8.55835 4.74995 C8.91188 4.746 9.21465 4.49569 9.28498 4.1492 L8.54997 4 Z M18.2483 9.00417 C18.2506 9.41838 18.5882 9.75229 19.0024 9.74999 C19.4166 9.74768 19.7506 9.41003 19.7483 8.99583 L18.9983 9 L18.2483 9.00417 Z M18.1213 4.87868 L18.6517 4.34835 L18.6517 4.34835 L18.1213 4.87868 Z M8 21.9983 L8.00417 21.2483 C6.91068 21.2422 6.13559 21.2138 5.54735 21.1057 C4.98054 21.0016 4.65246 20.8344 4.40901 20.591 L3.87868 21.1213 L3.34835 21.6517 C3.87328 22.1766 4.51835 22.4418 5.27635 22.5811 C6.01291 22.7164 6.9143 22.7422 7.99583 22.7483 L8 21.9983 Z M3 16 H2.25 C2.25 17.393 2.24841 18.5132 2.36652 19.3918 C2.48754 20.2919 2.74643 21.0497 3.34835 21.6517 L3.87868 21.1213 L4.40901 20.591 C4.13225 20.3142 3.9518 19.9257 3.85315 19.1919 C3.75159 18.4365 3.75 17.4354 3.75 16 H3 Z M3 10 H2.25 V16 H3 H3.75 V10 H3 Z M3 10 H3.75 C3.75 8.56458 3.75159 7.56347 3.85315 6.80812 C3.9518 6.07435 4.13225 5.68577 4.40901 5.40901 L3.87868 4.87868 L3.34835 4.34835 C2.74643 4.95027 2.48754 5.70814 2.36652 6.60825 C2.24841 7.48678 2.25 8.60699 2.25 10 H3 Z M7 4.01732 L6.97741 3.26766 C5.43521 3.31413 4.22037 3.47633 3.34835 4.34835 L3.87868 4.87868 L4.40901 5.40901 C4.8196 4.99842 5.4579 4.81413 7.02259 4.76698 L7 4.01732 Z M15 4.01732 L15.0084 3.26737 L13.4583 3.25005 L13.45 4 L13.4416 4.74995 L14.9916 4.76728 L15 4.01732 Z M11 2 V1.25 C9.42676 1.25 8.11615 2.36703 7.81496 3.8508 L8.54997 4 L9.28498 4.1492 C9.44706 3.35073 10.1542 2.75 11 2.75 V2 Z M18.9983 9 L19.7483 8.99583 C19.7422 7.9143 19.7163 7.01291 19.5811 6.27635 C19.4418 5.51835 19.1766 4.87328 18.6517 4.34835 L18.1213 4.87868 L17.591 5.40901 C17.8344 5.65246 18.0016 5.98054 18.1057 6.54735 C18.2138 7.13559 18.2422 7.91068 18.2483 9.00417 L18.9983 9 Z M15 4.01732 L14.9774 4.76698 C16.5421 4.81413 17.1804 4.99842 17.591 5.40901 L18.1213 4.87868 L18.6517 4.34835 C17.7796 3.47633 16.5648 3.31413 15.0226 3.26766 L15 4.01732 Z M7 4.01732 L7.00838 4.76728 L8.55835 4.74995 L8.54997 4 L8.54159 3.25005 L6.99162 3.26737 L7 4.01732 Z M13.45 4 L14.185 3.8508 C13.8838 2.36703 12.5732 1.25 11 1.25 V2 V2.75 C11.8457 2.75 12.5529 3.35073 12.7149 4.1492 L13.45 4 Z"),
                pathFillType = PathFillType.NonZero,
                fill = SolidColor(Color(0xFF141B34)),
                fillAlpha = 1f,
                stroke = null,
                strokeAlpha = 1f,
                strokeLineWidth = 1f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M12 22 V20.4937 C12 19.8117 12.2709 19.1576 12.7532 18.6754 L17.974 13.4546 C18.2651 13.1635 18.6598 13 19.0714 13 C19.483 13 19.8778 13.1635 20.1689 13.4546 L20.5454 13.8311 C20.8365 14.1222 21 14.517 21 14.9286 C21 15.3402 20.8365 15.7349 20.5454 16.026 L15.3246 21.2468 C14.8423 21.7291 14.1883 22 13.5063 22 H12 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val Clock01: ImageVector by lazy {
        build(
            name = "Clock01",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M22 12 A10 10 0 1 0 2 12 A10 10 0 1 0 22 12 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M12 8 V12 L14 14"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val Cloudy: ImageVector by lazy {
        build(
            name = "Cloudy",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M14.3821 12.8 C14.388 12.8 14.394 12.8 14.4 12.8 C16.3882 12.8 18 14.4118 18 16.4 C18 18.3882 16.3882 20 14.4 20 H6 C3.79086 20 2 18.2091 2 16 C2 13.9203 3.58718 12.2113 5.61634 12.0182 M13.8675 14.5 C14.1469 13.9866 14.327 13.4113 14.3821 12.8 C14.3939 12.6683 14.4 12.5349 14.4 12.4 C14.4 9.96995 12.4301 8 10 8 C7.69859 8 5.80986 9.7669 5.61634 12.0182 M5.61634 12.0182 C5.74261 12.0061 5.87059 12 6 12 C6.72857 12 7.41165 12.1948 8 12.5351"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M11 5.18207 C11.786 4.44874 12.841 4 14.0008 4 C16.4309 4 18.4008 5.96995 18.4008 8.4 C18.4008 8.53485 18.3948 8.66829 18.3829 8.80004 M21.0009 14.89 C21.6203 14.2434 22.0008 13.3661 22.0008 12.4 C22.0008 10.4118 20.3891 8.8 18.4008 8.8 C18.3949 8.8 18.3889 8.80001 18.3829 8.80004 M18.3829 8.80004 C18.3451 9.21981 18.2483 9.62258 18.1009 10"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val Code: ImageVector by lazy {
        build(
            name = "Code",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M16 6.99998 L19.0664 9.64296 C20.3554 10.7541 21 11.3096 21 12 C21 12.6903 20.3555 13.2459 19.0664 14.357 L16 17"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M8 6.99998 L4.93365 9.64296 C3.64455 10.7541 3 11.3096 3 12 C3 12.6903 3.64455 13.2459 4.93365 14.357 L8 17"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val ColorPicker: ImageVector by lazy {
        build(
            name = "ColorPicker",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M13.435 7 L7.15915 13.2759 M7.15915 13.2759 L4.82728 15.6077 C3.92569 16.5093 3.47489 16.9601 3.23745 17.5334 C3 18.1066 3 18.7441 3 20.0192 V21 H3.98082 C5.25586 21 5.89338 21 6.46663 20.7626 C7.03988 20.5251 7.49068 20.0743 8.39227 19.1727 L14.2891 13.2759 M7.15915 13.2759 H14.2891 M14.2891 13.2759 L17 10.565"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M19.2087 8.38869 L20.82 10 M19.2087 8.38869 L20.0705 7.52682 C20.363 7.23431 20.5093 7.08805 20.611 6.94529 C21.1297 6.21676 21.1297 5.23953 20.611 4.511 C20.5093 4.36824 20.363 4.22198 20.0705 3.92947 C19.778 3.63697 19.6318 3.4907 19.489 3.38905 C18.7605 2.87032 17.7832 2.87032 17.0547 3.38905 C16.912 3.4907 16.7657 3.63695 16.4732 3.92947 L15.6113 4.79133 M19.2087 8.38869 L15.6113 4.79133 M14 3.18002 L15.6113 4.79133"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val Copy01: ImageVector by lazy {
        build(
            name = "Copy01",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M7.5 14.5 C7.5 11.2002 7.5 9.55025 8.52513 8.52513 C9.55025 7.5 11.2002 7.5 14.5 7.5 C17.7998 7.5 19.4497 7.5 20.4749 8.52513 C21.5 9.55025 21.5 11.2002 21.5 14.5 C21.5 17.7998 21.5 19.4497 20.4749 20.4749 C19.4497 21.5 17.7998 21.5 14.5 21.5 C11.2002 21.5 9.55025 21.5 8.52513 20.4749 C7.5 19.4497 7.5 17.7998 7.5 14.5 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M7.5 16.5 C6.10355 16.5 5.40533 16.5 4.84402 16.3036 C3.83866 15.9518 3.0482 15.1613 2.69641 14.156 C2.5 13.5947 2.5 12.8964 2.5 11.5 V9.5 C2.5 6.20017 2.5 4.55025 3.52513 3.52513 C4.55025 2.5 6.20017 2.5 9.5 2.5 H11.5 C12.8964 2.5 13.5947 2.5 14.156 2.69641 C15.1613 3.0482 15.9518 3.83866 16.3036 4.84402 C16.5 5.40533 16.5 6.10355 16.5 7.5"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val Cpu: ImageVector by lazy {
        build(
            name = "Cpu",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M4 12 C4 8.22876 4 6.34315 5.17157 5.17157 C6.34315 4 8.22876 4 12 4 C15.7712 4 17.6569 4 18.8284 5.17157 C20 6.34315 20 8.22876 20 12 C20 15.7712 20 17.6569 18.8284 18.8284 C17.6569 20 15.7712 20 12 20 C8.22876 20 6.34315 20 5.17157 18.8284 C4 17.6569 4 15.7712 4 12 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M9.5 2 V4"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M14.5 2 V4"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M9.5 20 V22"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M14.5 20 V22"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M13 9 L9 13"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M15 13 L13 15"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M22 14.5 L20 14.5"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M4 9.5 L2 9.5"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M4 14.5 L2 14.5"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M22 9.5 L20 9.5"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val CreditCard: ImageVector by lazy {
        build(
            name = "CreditCard",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M2 12 C2 8.46252 2 6.69377 3.0528 5.5129 C3.22119 5.32403 3.40678 5.14935 3.60746 4.99087 C4.86213 4 6.74142 4 10.5 4 H13.5 C17.2586 4 19.1379 4 20.3925 4.99087 C20.5932 5.14935 20.7788 5.32403 20.9472 5.5129 C22 6.69377 22 8.46252 22 12 C22 15.5375 22 17.3062 20.9472 18.4871 C20.7788 18.676 20.5932 18.8506 20.3925 19.0091 C19.1379 20 17.2586 20 13.5 20 H10.5 C6.74142 20 4.86213 20 3.60746 19.0091 C3.40678 18.8506 3.22119 18.676 3.0528 18.4871 C2 17.3062 2 15.5375 2 12 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M10 16 H11.5"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M14.5 16 L18 16"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M2 9 H22"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val Crosshair: ImageVector by lazy {
        build(
            name = "Crosshair",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M22 12 A10 10 0 1 0 2 12 A10 10 0 1 0 22 12 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M12 2 V5"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M12 19 V22"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M22 12 L19 11.9993"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M5 12 H2"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M12 10 V14"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M14 12 L10.0156 11.9902"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
            )
        }
    }

    val Database: ImageVector by lazy {
        build(
            name = "Database",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M20 5 A8 3 0 1 0 4 5 A8 3 0 1 0 20 5 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M7 10.842 C7.60158 11.0229 8.27434 11.1718 9 11.282"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M20 12 C20 13.6569 16.4183 15 12 15 C7.58172 15 4 13.6569 4 12"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M7 17.842 C7.60158 18.0229 8.27434 18.1718 9 18.282"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M20 5 V19 C20 20.6569 16.4183 22 12 22 C7.58172 22 4 20.6569 4 19 V5"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
            )
        }
    }

    val Delete02: ImageVector by lazy {
        build(
            name = "Delete02",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M19.5 5.5 L18.8803 15.5251 C18.7219 18.0864 18.6428 19.3671 18.0008 20.2879 C17.6833 20.7431 17.2747 21.1273 16.8007 21.416 C15.8421 22 14.559 22 11.9927 22 C9.42312 22 8.1383 22 7.17905 21.4149 C6.7048 21.1257 6.296 20.7408 5.97868 20.2848 C5.33688 19.3626 5.25945 18.0801 5.10461 15.5152 L4.5 5.5"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M3 5.5 H21 M16.0557 5.5 L15.3731 4.09173 C14.9196 3.15626 14.6928 2.68852 14.3017 2.39681 C14.215 2.3321 14.1231 2.27454 14.027 2.2247 C13.5939 2 13.0741 2 12.0345 2 C10.9688 2 10.436 2 9.99568 2.23412 C9.8981 2.28601 9.80498 2.3459 9.71729 2.41317 C9.32164 2.7167 9.10063 3.20155 8.65861 4.17126 L8.05292 5.5"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M9.5 16.5 L9.5 10.5"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M14.5 16.5 L14.5 10.5"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
            )
        }
    }

    val Droplet: ImageVector by lazy {
        build(
            name = "Droplet",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M3.5 13.678 C3.5 9.49387 7.08079 5.35907 9.59413 2.97222 C10.9591 1.67593 13.0409 1.67593 14.4059 2.97222 C16.9192 5.35907 20.5 9.49387 20.5 13.678 C20.5 17.7804 17.2812 22 12 22 C6.71878 22 3.5 17.7804 3.5 13.678 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M16 14 C16 16.2091 14.2091 18 12 18"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val Edit02: ImageVector by lazy {
        build(
            name = "Edit02",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M14.0737 3.88545 C14.8189 3.07808 15.1915 2.6744 15.5874 2.43893 C16.5427 1.87076 17.7191 1.85309 18.6904 2.39232 C19.0929 2.6158 19.4769 3.00812 20.245 3.79276 C21.0131 4.5774 21.3972 4.96972 21.6159 5.38093 C22.1438 6.37312 22.1265 7.57479 21.5703 8.5507 C21.3398 8.95516 20.9446 9.33578 20.1543 10.097 L10.7506 19.1543 C9.25288 20.5969 8.504 21.3182 7.56806 21.6837 C6.63212 22.0493 5.6032 22.0224 3.54536 21.9686 L3.26538 21.9613 C2.63891 21.9449 2.32567 21.9367 2.14359 21.73 C1.9615 21.5234 1.98636 21.2043 2.03608 20.5662 L2.06308 20.2197 C2.20301 18.4235 2.27297 17.5255 2.62371 16.7182 C2.97444 15.9109 3.57944 15.2555 4.78943 13.9445 L14.0737 3.88545 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M13 4 L20 11"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M14 22 L22 22"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val Energy: ImageVector by lazy {
        build(
            name = "Energy",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M6.19351 11.3965 L12.192 3.31186 C12.6611 2.67957 13.5405 3.07311 13.5405 3.91536 V10.1729 C13.5405 10.6775 13.8853 11.0865 14.3107 11.0865 H17.2283 C17.891 11.0865 18.2443 12.0134 17.8065 12.6035 L11.808 20.6881 C11.3389 21.3204 10.4595 20.9269 10.4595 20.0846 V13.8271 C10.4595 13.3225 10.1147 12.9135 9.68931 12.9135 H6.77173 C6.10895 12.9135 5.75566 11.9866 6.19351 11.3965 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val Exchange01: ImageVector by lazy {
        build(
            name = "Exchange01",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M16.9767 19.5 C19.4017 17.8876 21 15.1305 21 12 C21 7.02944 16.9706 3 12 3 C11.3126 3 10.6432 3.07706 10 3.22302 M16.9767 19.5 V16 M16.9767 19.5 H20.5 M7 4.51555 C4.58803 6.13007 3 8.87958 3 12 C3 16.9706 7.02944 21 12 21 C12.6874 21 13.3568 20.9229 14 20.777 M7 4.51555 V8 M7 4.51555 H3.5"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val ExternalLink: ImageVector by lazy {
        build(
            name = "ExternalLink",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M15 3 H18 C19.4142 3 20.1213 3 20.5607 3.43934 C21 3.87868 21 4.58579 21 6 V9 M20 4 L11 13"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M20 13 C20 16.7712 20 18.6569 18.8284 19.8284 C17.6569 21 15.7712 21 12 21 H11 C7.22876 21 5.34315 21 4.17157 19.8284 C3 18.6569 3 16.7712 3 13 V12 C3 8.22876 3 6.34315 4.17157 5.17157 C5.34315 4 7.22876 4 11 4"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
            )
        }
    }

    val Eye: ImageVector by lazy {
        build(
            name = "Eye",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M2 8 C2 8 6.47715 3 12 3 C17.5228 3 22 8 22 8"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M21.544 13.045 C21.848 13.4713 22 13.6845 22 14 C22 14.3155 21.848 14.5287 21.544 14.955 C20.1779 16.8706 16.6892 21 12 21 C7.31078 21 3.8221 16.8706 2.45604 14.955 C2.15201 14.5287 2 14.3155 2 14 C2 13.6845 2.15201 13.4713 2.45604 13.045 C3.8221 11.1294 7.31078 7 12 7 C16.6892 7 20.1779 11.1294 21.544 13.045 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M15 14 C15 12.3431 13.6569 11 12 11 C10.3431 11 9 12.3431 9 14 C9 15.6569 10.3431 17 12 17 C13.6569 17 15 15.6569 15 14 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
            )
        }
    }

    val EyeOff: ImageVector by lazy {
        build(
            name = "EyeOff",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M6.43385 6.51953 C4.22009 7.89049 2.93281 9.86457 2.31858 11.0339 C2.10621 11.4382 2.00003 11.6403 2 12.0082 C1.99997 12.3761 2.10584 12.5777 2.3176 12.981 C3.32862 14.9066 6.16702 19.0195 11.9669 19.0195 C14.2454 19.0195 16.0669 18.3848 17.5 17.4972"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M9.87868 9.87868 C9.33579 10.4216 9 11.1716 9 12 C9 13.6569 10.3431 15 12 15 C12.8284 15 13.5784 14.6642 14.1213 14.1213"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M2 2 L22 22"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M10 5.14847 C10.5934 5.05255 11.224 5 11.8936 5 C17.7747 5 20.6528 9.05385 21.6779 10.9517 C21.8927 11.3492 22 11.548 22 11.9106 C22 12.2733 21.8921 12.4727 21.6765 12.8717 C21.3678 13.4428 20.8916 14.2085 20.2167 15"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val File01: ImageVector by lazy {
        build(
            name = "File01",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M8 7 L16 7"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M8 11 L12 11"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M13 21.5 V21 C13 18.1716 13 16.7574 13.8787 15.8787 C14.7574 15 16.1716 15 19 15 H19.5 M20 13.3431 V10 C20 6.22876 20 4.34315 18.8284 3.17157 C17.6569 2 15.7712 2 12 2 C8.22877 2 6.34315 2 5.17157 3.17157 C4 4.34314 4 6.22876 4 10 L4 14.5442 C4 17.7892 4 19.4117 4.88607 20.5107 C5.06508 20.7327 5.26731 20.9349 5.48933 21.1139 C6.58831 22 8.21082 22 11.4558 22 C12.1614 22 12.5141 22 12.8372 21.886 C12.9044 21.8623 12.9702 21.835 13.0345 21.8043 C13.3436 21.6564 13.593 21.407 14.0919 20.9081 L18.8284 16.1716 C19.4065 15.5935 19.6955 15.3045 19.8478 14.9369 C20 14.5694 20 14.1606 20 13.3431 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val File02: ImageVector by lazy {
        build(
            name = "File02",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M8 17 H16"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M8 13 H12"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M13 2.5 V3 C13 5.82843 13 7.24264 13.8787 8.12132 C14.7574 9 16.1716 9 19 9 H19.5 M20 10.6569 V14 C20 17.7712 20 19.6569 18.8284 20.8284 C17.6569 22 15.7712 22 12 22 C8.22876 22 6.34315 22 5.17157 20.8284 C4 19.6569 4 17.7712 4 14 V9.45584 C4 6.21082 4 4.58831 4.88607 3.48933 C5.06508 3.26731 5.26731 3.06508 5.48933 2.88607 C6.58831 2 8.21082 2 11.4558 2 C12.1614 2 12.5141 2 12.8372 2.11401 C12.9044 2.13772 12.9702 2.165 13.0345 2.19575 C13.3436 2.34355 13.593 2.593 14.0919 3.09188 L18.8284 7.82843 C19.4065 8.40649 19.6955 8.69552 19.8478 9.06306 C20 9.4306 20 9.83935 20 10.6569 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val FileExport: ImageVector by lazy {
        build(
            name = "FileExport",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M20 14 V10.6569 C20 9.83935 20 9.4306 19.8478 9.06306 C19.6955 8.69552 19.4065 8.40649 18.8284 7.82843 L14.0919 3.09188 C13.593 2.593 13.3436 2.34355 13.0345 2.19575 C12.9702 2.165 12.9044 2.13772 12.8372 2.11401 C12.5141 2 12.1614 2 11.4558 2 C8.21082 2 6.58831 2 5.48933 2.88607 C5.26731 3.06508 5.06508 3.26731 4.88607 3.48933 C4 4.58831 4 6.21082 4 9.45584 V14 C4 17.7712 4 19.6569 5.17157 20.8284 C6.34315 22 8.22876 22 12 22 M13 2.5 V3 C13 5.82843 13 7.24264 13.8787 8.12132 C14.7574 9 16.1716 9 19 9 H19.5"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M17 22 C17.6068 21.4102 20 19.8403 20 19 C20 18.1597 17.6068 16.5898 17 16 M19 19 H12"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val FileImport: ImageVector by lazy {
        build(
            name = "FileImport",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M20 15.0057 V10.6606 C20 9.84276 20 9.43383 19.8478 9.06613 C19.6955 8.69843 19.4065 8.40927 18.8284 7.83096 L14.0919 3.09236 C13.593 2.59325 13.3436 2.3437 13.0345 2.19583 C12.9702 2.16508 12.9044 2.13778 12.8372 2.11406 C12.5141 2 12.1614 2 11.4558 2 C8.21082 2 6.58831 2 5.48933 2.88646 C5.26731 3.06554 5.06508 3.26787 4.88607 3.48998 C4 4.58943 4 6.21265 4 9.45908 V14.0052 C4 17.7781 4 19.6645 5.17157 20.8366 C6.11466 21.7801 7.52043 21.9641 10 22 M13 2.50022 V3.00043 C13 5.83009 13 7.24492 13.8787 8.12398 C14.7574 9.00304 16.1716 9.00304 19 9.00304 H19.5"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M15 22 C14.3932 21.4102 12 19.8403 12 19 C12 18.1597 14.3932 16.5898 15 16 M13 19 H20"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val Filter: ImageVector by lazy {
        build(
            name = "Filter",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M8.85746 12.5061 C6.36901 10.6456 4.59564 8.59915 3.62734 7.44867 C3.3276 7.09253 3.22938 6.8319 3.17033 6.3728 C2.96811 4.8008 2.86701 4.0148 3.32795 3.5074 C3.7889 3 4.60404 3 6.23433 3 H17.7657 C19.396 3 20.2111 3 20.672 3.5074 C21.133 4.0148 21.0319 4.8008 20.8297 6.37281 C20.7706 6.83191 20.6724 7.09254 20.3726 7.44867 C19.403 8.60062 17.6261 10.6507 15.1326 12.5135 C14.907 12.6821 14.7583 12.9567 14.7307 13.2614 C14.4837 15.992 14.2559 17.4876 14.1141 18.2442 C13.8853 19.4657 12.1532 20.2006 11.226 20.8563 C10.6741 21.2466 10.0043 20.782 9.93278 20.1778 C9.79643 19.0261 9.53961 16.6864 9.25927 13.2614 C9.23409 12.9539 9.08486 12.6761 8.85746 12.5061 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val Flash: ImageVector by lazy {
        build(
            name = "Flash",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M5.22576 11.3294 L12.224 2.34651 C12.7713 1.64397 13.7972 2.08124 13.7972 3.01707 V9.96994 C13.7972 10.5305 14.1995 10.985 14.6958 10.985 H18.0996 C18.8729 10.985 19.2851 12.0149 18.7742 12.6706 L11.776 21.6535 C11.2287 22.356 10.2028 21.9188 10.2028 20.9829 V14.0301 C10.2028 13.4695 9.80048 13.015 9.3042 13.015 H5.90035 C5.12711 13.015 4.71494 11.9851 5.22576 11.3294 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val Flashlight: ImageVector by lazy {
        build(
            name = "Flashlight",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M15 3 H9 C8.05719 3 7.58579 3 7.29289 3.29289 C7 3.58579 7 4.05719 7 5 V6 C7 7.04416 7.27249 8.07025 7.79054 8.97683 L9 11 V18 C9 18.9319 9 19.3978 9.15224 19.7654 C9.35523 20.2554 9.74458 20.6448 10.2346 20.8478 C10.6022 21 11.0681 21 12 21 C12.9319 21 13.3978 21 13.7654 20.8478 C14.2554 20.6448 14.6448 20.2554 14.8478 19.7654 C15 19.3978 15 18.9319 15 18 V11 L16.2095 8.97683 C16.7275 8.07025 17 7.04416 17 6 V5 C17 4.05719 17 3.58579 16.7071 3.29289 C16.4142 3 15.9428 3 15 3 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M7 6 H17"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M12 13 V15"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val FlaskRound: ImageVector by lazy {
        build(
            name = "FlaskRound",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M8 2 H16"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M9.5 2 V6.05824 C9.5 6.68785 9.5 7.00266 9.36866 7.23296 C9.23732 7.46325 8.90463 7.66006 8.23925 8.05367 C6.02726 9.36221 4.5 11.7594 4.5 14.5 C4.5 18.6421 7.85786 22 12 22 C16.1421 22 19.5 18.6421 19.5 14.5 C19.5 11.7594 17.9727 9.36221 15.7608 8.05368 C15.0954 7.66006 14.7627 7.46325 14.6313 7.23296 C14.5 7.00266 14.5 6.68785 14.5 6.05824 V2"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M5 12.3247 C5.81797 12.1279 6.69773 12 7.69231 12 C10.9231 12 14.1538 15 17.3846 15 C18.0043 15 18.5384 14.947 19 14.8557"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val Github: ImageVector by lazy {
        build(
            name = "Github",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M10 20.5675 C6.57143 21.7248 3.71429 20.5675 2 17"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M10 22 V18.7579 C10 18.1596 10.1839 17.6396 10.4804 17.1699 C10.6838 16.8476 10.5445 16.3904 10.1771 16.2894 C7.13394 15.4528 5 14.1077 5 9.64606 C5 8.48611 5.38005 7.39556 6.04811 6.4464 C6.21437 6.21018 6.29749 6.09208 6.31748 5.9851 C6.33746 5.87813 6.30272 5.73852 6.23322 5.45932 C5.95038 4.32292 5.96871 3.11619 6.39322 2.02823 C6.39322 2.02823 7.27042 1.74242 9.26698 2.98969 C9.72282 3.27447 9.95075 3.41686 10.1515 3.44871 C10.3522 3.48056 10.6206 3.41384 11.1573 3.28041 C11.8913 3.09795 12.6476 3 13.5 3 C14.3524 3 15.1087 3.09795 15.8427 3.28041 C16.3794 3.41384 16.6478 3.48056 16.8485 3.44871 C17.0493 3.41686 17.2772 3.27447 17.733 2.98969 C19.7296 1.74242 20.6068 2.02823 20.6068 2.02823 C21.0313 3.11619 21.0496 4.32292 20.7668 5.45932 C20.6973 5.73852 20.6625 5.87813 20.6825 5.9851 C20.7025 6.09207 20.7856 6.21019 20.9519 6.4464 C21.6199 7.39556 22 8.48611 22 9.64606 C22 14.1077 19.8661 15.4528 16.8229 16.2894 C16.4555 16.3904 16.3162 16.8476 16.5196 17.1699 C16.8161 17.6396 17 18.1596 17 18.7579 V22"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val GlassWater: ImageVector by lazy {
        build(
            name = "GlassWater",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M14.3491 2 H9.65087 C7.63077 2 6.62072 2 6.01552 2.62155 C5.41033 3.2431 5.46077 4.22865 5.56165 6.19976 L6.08916 16.5069 C6.21695 19.0038 6.28085 20.2522 7.05519 21.0561 C7.13278 21.1366 7.21487 21.2129 7.30109 21.2846 C8.1615 22 9.441 22 12 22 C14.559 22 15.8385 22 16.6989 21.2846 C16.7851 21.2129 16.8672 21.1366 16.9448 21.0561 C17.7192 20.2522 17.783 19.0038 17.9108 16.5069 L18.4383 6.19975 C18.5392 4.22865 18.5897 3.2431 17.9845 2.62155 C17.3793 2 16.3692 2 14.3491 2 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M6 9 C6.86548 8.35089 7.91815 8 9 8 C10.0819 8 11.1345 8.35089 12 9 C12.8655 9.64911 13.9181 10 15 10 C16.0819 10 17.1345 9.64911 18 9"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val Globe: ImageVector by lazy {
        build(
            name = "Globe",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M12.5 19 L12.5 22"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF000000)),
                strokeAlpha = 1f,
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M10.5 22 H14.5"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF000000)),
                strokeAlpha = 1f,
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M8.5 4 C9.15431 4.0385 9.49236 4.35899 10.0735 4.97301 C11.1231 6.08206 12.1727 6.1746 12.8724 5.80492 C13.922 5.2504 13.04 4.35221 14.2719 3.86409 C15.0748 3.54595 15.1868 2.68026 14.7399 2"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF000000)),
                strokeAlpha = 1f,
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M20 10 C18.5 10 18.2338 11.2468 17 11 C14.5 10.5 13.7916 11.0589 13.7916 12.2511 C13.7916 13.4432 13.7916 13.4432 13.2717 14.3373 C12.9335 14.9189 12.8153 15.5004 13.4894 16"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF000000)),
                strokeAlpha = 1f,
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M6.5 2 C4.64864 3.79995 3.5 6.3082 3.5 9.08251 C3.5 14.5598 7.97715 19 13.5 19 C16.2255 19 18.6962 17.9187 20.5 16.165"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF000000)),
                strokeAlpha = 1f,
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val Globe02: ImageVector by lazy {
        build(
            name = "Globe02",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M22 12 A10 10 0 1 0 2 12 A10 10 0 1 0 22 12 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M8 12 C8 18 12 22 12 22 C12 22 16 18 16 12 C16 6 12 2 12 2 C12 2 8 6 8 12 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M21 15 H3"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M21 9 H3"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val GraduationCap: ImageVector by lazy {
        build(
            name = "GraduationCap",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M9.54155 4.65208 L6.98306 5.92425 C3.66102 7.57609 2 8.40201 2 9.75 C2 11.098 3.66102 11.9239 6.98306 13.5757 L9.54155 14.8479 C10.751 15.4493 11.3557 15.75 12 15.75 C12.6443 15.75 13.249 15.4493 14.4584 14.8479 L17.0169 13.5757 C20.339 11.9239 22 11.098 22 9.75 C22 8.40201 20.339 7.57609 17.0169 5.92425 L14.4584 4.65208 C13.249 4.05069 12.6443 3.75 12 3.75 C11.3557 3.75 10.751 4.05069 9.54155 4.65208 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M6 13.25 V17.057 C6 17.1486 6 17.1944 6.00176 17.2369 C6.03098 17.9434 6.42116 18.5835 7.03141 18.926 C7.06808 18.9466 7.10848 18.967 7.18928 19.008 C7.51967 19.1755 7.68487 19.2592 7.84453 19.3332 C10.4837 20.5556 13.5163 20.5556 16.1555 19.3332 C16.3151 19.2592 16.4803 19.1755 16.8107 19.008 C16.8915 18.967 16.9319 18.9466 16.9686 18.926 C17.5788 18.5835 17.969 17.9434 17.9982 17.2369 C18 17.1944 18 17.1486 18 17.057 V13.25"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M22 9.75 V16.2506"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val GraduationScroll: ImageVector by lazy {
        build(
            name = "GraduationScroll",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M21.1609 9.92846 C22.1928 9.54155 22.2858 7.69292 21.3685 5.79943 C20.4512 3.90594 18.8711 2.68462 17.8391 3.07154 M21.1609 9.92846 C20.1289 10.3154 18.5488 9.09406 17.6315 7.20057 C16.7142 5.30708 16.8072 3.45845 17.8391 3.07154 M21.1609 9.92846 L6.16089 18.9285 C5.12895 19.3154 3.54878 18.0941 2.6315 16.2006 C1.71421 14.3071 1.80716 12.4584 2.83911 12.0715 L17.8391 3.07154"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M15 13.6072 C13.6383 13.0342 10.9233 10.9509 10.9574 7.20117 M11.5 15.7012 C10.3333 15.1444 7.9 13.0787 7.5 9.26966"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M15.43 14 C16.0276 15.1302 16.639 18.1124 14.5498 21 L13.5632 19.584 L11 20.8103 C11 20.8103 12.8249 18.8868 11.9528 16"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val GridView: ImageVector by lazy {
        build(
            name = "GridView",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M3.88884 9.66294 C4.39329 10 5.09552 10 6.49998 10 C7.90445 10 8.60668 10 9.11113 9.66294 C9.32951 9.51702 9.51701 9.32952 9.66292 9.11114 C9.99998 8.60669 9.99998 7.90446 9.99998 6.5 C9.99998 5.09554 9.99998 4.39331 9.66292 3.88886 C9.51701 3.67048 9.32951 3.48298 9.11113 3.33706 C8.60668 3 7.90445 3 6.49998 3 C5.09552 3 4.39329 3 3.88884 3.33706 C3.67046 3.48298 3.48296 3.67048 3.33705 3.88886 C2.99998 4.39331 2.99998 5.09554 2.99998 6.5 C2.99998 7.90446 2.99998 8.60669 3.33705 9.11114 C3.48296 9.32952 3.67046 9.51702 3.88884 9.66294 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M14.8888 9.66294 C15.3933 10 16.0955 10 17.5 10 C18.9044 10 19.6067 10 20.1111 9.66294 C20.3295 9.51702 20.517 9.32952 20.6629 9.11114 C21 8.60669 21 7.90446 21 6.5 C21 5.09554 21 4.39331 20.6629 3.88886 C20.517 3.67048 20.3295 3.48298 20.1111 3.33706 C19.6067 3 18.9044 3 17.5 3 C16.0955 3 15.3933 3 14.8888 3.33706 C14.6705 3.48298 14.483 3.67048 14.337 3.88886 C14 4.39331 14 5.09554 14 6.5 C14 7.90446 14 8.60669 14.337 9.11114 C14.483 9.32952 14.6705 9.51702 14.8888 9.66294 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M3.88884 20.6629 C4.39329 21 5.09552 21 6.49998 21 C7.90445 21 8.60668 21 9.11113 20.6629 C9.32951 20.517 9.51701 20.3295 9.66292 20.1111 C9.99998 19.6067 9.99998 18.9045 9.99998 17.5 C9.99998 16.0955 9.99998 15.3933 9.66292 14.8889 C9.51701 14.6705 9.32951 14.483 9.11113 14.3371 C8.60668 14 7.90445 14 6.49998 14 C5.09552 14 4.39329 14 3.88884 14.3371 C3.67046 14.483 3.48296 14.6705 3.33705 14.8889 C2.99998 15.3933 2.99998 16.0955 2.99998 17.5 C2.99998 18.9045 2.99998 19.6067 3.33705 20.1111 C3.48296 20.3295 3.67046 20.517 3.88884 20.6629 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M14.8888 20.6629 C15.3933 21 16.0955 21 17.5 21 C18.9044 21 19.6067 21 20.1111 20.6629 C20.3295 20.517 20.517 20.3295 20.6629 20.1111 C21 19.6067 21 18.9045 21 17.5 C21 16.0955 21 15.3933 20.6629 14.8889 C20.517 14.6705 20.3295 14.483 20.1111 14.3371 C19.6067 14 18.9044 14 17.5 14 C16.0955 14 15.3933 14 14.8888 14.3371 C14.6705 14.483 14.483 14.6705 14.337 14.8889 C14 15.3933 14 16.0955 14 17.5 C14 18.9045 14 19.6067 14.337 20.1111 C14.483 20.3295 14.6705 20.517 14.8888 20.6629 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val Heading02: ImageVector by lazy {
        build(
            name = "Heading02",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M3.5 5 V19"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M13.5 5 V19"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M20.5 19 H16.5 V18.6907 C16.5 18.2521 16.5 18.0327 16.5865 17.8385 C16.673 17.6443 16.836 17.4976 17.1621 17.2041 L19.7671 14.8596 C20.2336 14.4397 20.5 13.8416 20.5 13.214 V13 C20.5 11.8954 19.6046 11 18.5 11 C17.3954 11 16.5 11.8954 16.5 13 V13.4"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M3.5 12 L13.5 12"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val Heart: ImageVector by lazy {
        build(
            name = "Heart",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M10.4107 19.9677 C7.58942 17.858 2 13.0348 2 8.69444 C2 5.82563 4.10526 3.5 7 3.5 C8.5 3.5 10 4 12 6 C14 4 15.5 3.5 17 3.5 C19.8947 3.5 22 5.82563 22 8.69444 C22 13.0348 16.4106 17.858 13.5893 19.9677 C12.6399 20.6776 11.3601 20.6776 10.4107 19.9677 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val HeartHandshake: ImageVector by lazy {
        build(
            name = "HeartHandshake",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M19.6692 14.6692 C21.01 12.9629 22 11.0686 22 9.19444 C22 6.32563 19.8947 4 17 4 C15.5 4 14 4.5 12 6.5 C10 4.5 8.5 4 7 4 C4.10526 4 2 6.32563 2 9.19444 C2 14.2666 9.25143 19.4872 11.0835 20.7252 C11.3541 20.9081 11.6734 21 12 21 C12.3266 21 12.6459 20.9081 12.9165 20.7252 C13.0217 20.6541 13.1449 20.5699 13.2835 20.4732 C14.2796 19.7789 14.3586 18.3586 13.5 17.5"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M18.5 11 L19.49 11.99 C20.1988 12.6988 20.3483 13.795 19.7336 14.5868 C18.9878 15.5474 17.5632 15.5632 16.7032 14.7032 L16.5 14.5"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M16.5 14.5 L16.6942 14.6942 C17.5691 15.5691 17.51 17.0612 16.5833 17.8811 C15.7193 18.6455 14.4965 18.4965 13.6808 17.6808 L13.5 17.5"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M12.0002 6.5 L9.25019 9.25 C8.55983 9.94036 8.55983 11.0596 9.25019 11.75 C9.94055 12.4404 11.0598 12.4404 11.7502 11.75 L13.5002 10 C14.0454 9.45475 14.3181 9.18212 14.6122 9.03639 C15.1717 8.75911 15.8287 8.75911 16.3882 9.03639 C16.6823 9.18212 16.9549 9.45475 17.5002 10 L19.0002 11.5"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val History: ImageVector by lazy {
        build(
            name = "History",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M3.49902 14.9656 C4.72475 18.4791 8.06749 21 11.999 21 C16.9696 21 20.999 16.9706 20.999 12 C20.999 7.02944 16.9696 3 11.999 3 C8.29827 3 4.8984 5.6756 3.68943 8.5"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M11.999 7 V12 L14.999 14"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M7.49751 8.74363 C7.49751 8.74363 3.81388 9.3026 3.25487 8.7436 C2.69585 8.1846 3.25488 4.50098 3.25488 4.50098"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val Home01: ImageVector by lazy {
        build(
            name = "Home01",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M3 11.9896 V14.5 C3 17.7998 3 19.4497 4.02513 20.4749 C5.05025 21.5 6.70017 21.5 10 21.5 H14 C17.2998 21.5 18.9497 21.5 19.9749 20.4749 C21 19.4497 21 17.7998 21 14.5 V11.9896 C21 10.3083 21 9.46773 20.6441 8.74005 C20.2882 8.01237 19.6247 7.49628 18.2976 6.46411 L16.2976 4.90855 C14.2331 3.30285 13.2009 2.5 12 2.5 C10.7991 2.5 9.76689 3.30285 7.70242 4.90855 L5.70241 6.46411 C4.37533 7.49628 3.71179 8.01237 3.3559 8.74005 C3 9.46773 3 10.3083 3 11.9896 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val Image01: ImageVector by lazy {
        build(
            name = "Image01",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M9 7.5 A1.5 1.5 0 1 0 6 7.5 A1.5 1.5 0 1 0 9 7.5 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M2.5 12 C2.5 7.52166 2.5 5.28249 3.89124 3.89124 C5.28249 2.5 7.52166 2.5 12 2.5 C16.4783 2.5 18.7175 2.5 20.1088 3.89124 C21.5 5.28249 21.5 7.52166 21.5 12 C21.5 16.4783 21.5 18.7175 20.1088 20.1088 C18.7175 21.5 16.4783 21.5 12 21.5 C7.52166 21.5 5.28249 21.5 3.89124 20.1088 C2.5 18.7175 2.5 16.4783 2.5 12 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M5 21 C9.37246 15.775 14.2741 8.88406 21.4975 13.5424"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
            )
        }
    }

    val ImageAdd01: ImageVector by lazy {
        build(
            name = "ImageAdd01",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M11.5085 2.9903 C7.02567 2.9903 4.78428 2.9903 3.39164 4.38238 C1.99902 5.77447 1.99902 8.015 1.99902 12.4961 C1.99902 16.9771 1.99902 19.2176 3.39164 20.6098 C4.78428 22.0018 7.02567 22.0018 11.5085 22.0018 C15.9912 22.0018 18.2326 22.0018 19.6253 20.6098 C21.0179 19.2176 21.0179 16.9771 21.0179 12.4961 V11.9958"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M4.99902 20.9898 C9.209 16.2385 13.9402 9.93727 20.999 14.6632"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M17.9958 1.99829 V10.0064 M22.0014 5.97728 L13.9902 5.99217"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val ImageComposition: ImageVector by lazy {
        build(
            name = "ImageComposition",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M7.07523 3.88403 C10.2874 4.8348 13.7126 4.8348 16.9248 3.88403 C19.517 3.11677 20.813 2.73313 21.4065 3.20921 C22 3.68529 22 4.90772 22 7.35256 V16.6474 C22 19.0923 22 20.3147 21.4065 20.7908 C20.813 21.2669 19.5169 20.8832 16.9248 20.116 C13.7126 19.1652 10.2874 19.1652 7.07523 20.116 C4.48305 20.8832 3.18696 21.2669 2.59348 20.7908 C2 20.3147 2 19.0923 2 16.6474 V7.35256 C2 4.90772 2 3.68529 2.59348 3.20921 C3.18696 2.73313 4.48305 3.11677 7.07523 3.88403 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M7 20 C10.9469 15.8426 15.3824 10.3291 22 14.4643"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M9 8.5 C9 9.32843 8.32843 10 7.5 10 C6.67157 10 6 9.32843 6 8.5 C6 7.67157 6.67157 7 7.5 7 C8.32843 7 9 7.67157 9 8.5 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val ImageDownload: ImageVector by lazy {
        build(
            name = "ImageDownload",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M5 21 C9.20998 16.2487 13.9412 9.9475 21 14.6734"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M14 3.00231 C13.5299 3 12.0307 3 11.5 3 C7.02166 3 4.78249 3 3.39124 4.39124 C2 5.78249 2 8.02166 2 12.5 C2 16.9783 2 19.2175 3.39124 20.6088 C4.78249 22 7.02166 22 11.5 22 C15.9783 22 18.2175 22 19.6088 20.6088 C20.9472 19.2703 20.998 17.147 20.9999 13"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M17 7.5 C17.4915 8.0057 18.7998 10 19.5 10 M22 7.5 C21.5085 8.0057 20.2002 10 19.5 10 M19.5 10 V2"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val Import: ImageVector by lazy {
        build(
            name = "Import",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M12 14.5 L12 2.5"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M8 2.56348 C6.03242 2.67731 4.78634 2.99652 3.89124 3.89162 C2.5 5.28286 2.5 7.52203 2.5 12.0004 C2.5 16.4787 2.5 18.7179 3.89124 20.1091 C5.28249 21.5004 7.52166 21.5004 12 21.5004 C16.4783 21.5004 18.7175 21.5004 20.1088 20.1091 C21.5 18.7179 21.5 16.4787 21.5 12.0004 C21.5 7.52203 21.5 5.28286 20.1088 3.89162 C19.2137 2.99652 17.9676 2.67731 16 2.56348"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M16 11.5 C16 11.5 13.054 15.5 12 15.5 C10.9459 15.5 8 11.5 8 11.5"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val Inbox: ImageVector by lazy {
        build(
            name = "Inbox",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M2.5 12 C2.5 7.52166 2.5 5.28249 3.89124 3.89124 C5.28249 2.5 7.52166 2.5 12 2.5 C16.4783 2.5 18.7175 2.5 20.1088 3.89124 C21.5 5.28249 21.5 7.52166 21.5 12 C21.5 16.4783 21.5 18.7175 20.1088 20.1088 C18.7175 21.5 16.4783 21.5 12 21.5 C7.52166 21.5 5.28249 21.5 3.89124 20.1088 C2.5 18.7175 2.5 16.4783 2.5 12 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M21.5 13.5 H16.5743 C15.7322 13.5 15.0706 14.2036 14.6995 14.9472 C14.2963 15.7551 13.4889 16.5 12 16.5 C10.5111 16.5 9.70373 15.7551 9.30054 14.9472 C8.92942 14.2036 8.26777 13.5 7.42566 13.5 H2.5"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val InformationCircle: ImageVector by lazy {
        build(
            name = "InformationCircle",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M22 12 A10 10 0 1 0 2 12 A10 10 0 1 0 22 12 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M12 16 V12"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M12.125 8.25 H12 M12.25 8.25 C12.25 8.11193 12.1381 8 12 8 C11.8619 8 11.75 8.11193 11.75 8.25 C11.75 8.38807 11.8619 8.5 12 8.5 C12.1381 8.5 12.25 8.38807 12.25 8.25 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val Keyboard: ImageVector by lazy {
        build(
            name = "Keyboard",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M14.5 7 H9.5 C6.21252 7 4.56878 7 3.46243 7.90796 C3.25989 8.07418 3.07418 8.25989 2.90796 8.46243 C2 9.56878 2 11.2125 2 14.5 C2 17.7875 2 19.4312 2.90796 20.5376 C3.07418 20.7401 3.25989 20.9258 3.46243 21.092 C4.56878 22 6.21252 22 9.5 22 H14.5 C17.7875 22 19.4312 22 20.5376 21.092 C20.7401 20.9258 20.9258 20.7401 21.092 20.5376 C22 19.4312 22 17.7875 22 14.5 C22 11.2125 22 9.56878 21.092 8.46243 C20.9258 8.25989 20.7401 8.07418 20.5376 7.90796 C19.4312 7 17.7875 7 14.5 7 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M12 7 V5 C12 4.44772 12.4477 4 13 4 C13.5523 4 14 3.55228 14 3 V2"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M7 12 L8 12"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M11.5 12 L12.5 12"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M16 12 L17 12"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M7 17 L17 17"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val Layers01: ImageVector by lazy {
        build(
            name = "Layers01",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M8.64298 3.14559 L6.93816 3.93362 C4.31272 5.14719 3 5.75397 3 6.75 C3 7.74603 4.31272 8.35281 6.93817 9.56638 L8.64298 10.3544 C10.2952 11.1181 11.1214 11.5 12 11.5 C12.8786 11.5 13.7048 11.1181 15.357 10.3544 L17.0618 9.56638 C19.6873 8.35281 21 7.74603 21 6.75 C21 5.75397 19.6873 5.14719 17.0618 3.93362 L15.357 3.14559 C13.7048 2.38186 12.8786 2 12 2 C11.1214 2 10.2952 2.38186 8.64298 3.14559 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M20.788 11.0972 C20.9293 11.2959 21 11.5031 21 11.7309 C21 12.7127 19.6873 13.3109 17.0618 14.5072 L15.357 15.284 C13.7048 16.0368 12.8786 16.4133 12 16.4133 C11.1214 16.4133 10.2952 16.0368 8.64298 15.284 L6.93817 14.5072 C4.31272 13.3109 3 12.7127 3 11.7309 C3 11.5031 3.07067 11.2959 3.212 11.0972"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M20.3767 16.2661 C20.7922 16.5971 21 16.927 21 17.3176 C21 18.2995 19.6873 18.8976 17.0618 20.0939 L15.357 20.8707 C13.7048 21.6236 12.8786 22 12 22 C11.1214 22 10.2952 21.6236 8.64298 20.8707 L6.93817 20.0939 C4.31272 18.8976 3 18.2995 3 17.3176 C3 16.927 3.20778 16.5971 3.62334 16.2661"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val LeftToRightListBullet: ImageVector by lazy {
        build(
            name = "LeftToRightListBullet",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M8 5.5 L20 5.5"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M8 12.5 L20 12.5"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M8 19.5 L20 19.5"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M4.375 5.5 H4.25 M4.5 5.5 C4.5 5.63807 4.38807 5.75 4.25 5.75 C4.11193 5.75 4 5.63807 4 5.5 C4 5.36193 4.11193 5.25 4.25 5.25 C4.38807 5.25 4.5 5.36193 4.5 5.5 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M4.375 12.5 H4.25 M4.5 12.5 C4.5 12.6381 4.38807 12.75 4.25 12.75 C4.11193 12.75 4 12.6381 4 12.5 C4 12.3619 4.11193 12.25 4.25 12.25 C4.38807 12.25 4.5 12.3619 4.5 12.5 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M4.375 19.5 H4.25 M4.5 19.5 C4.5 19.6381 4.38807 19.75 4.25 19.75 C4.11193 19.75 4 19.6381 4 19.5 C4 19.3619 4.11193 19.25 4.25 19.25 C4.38807 19.25 4.5 19.3619 4.5 19.5 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val LeftToRightListNumber: ImageVector by lazy {
        build(
            name = "LeftToRightListNumber",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M11 6 L21 6"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M11 12 L21 12"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M11 18 L21 18"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M3 15 H4.5 C4.77879 15 4.91819 15 5.03411 15.0231 C5.51014 15.1177 5.88225 15.4899 5.97694 15.9659 C6 16.0818 6 16.2212 6 16.5 C6 16.7788 6 16.9182 5.97694 17.0341 C5.88225 17.5101 5.51014 17.8823 5.03411 17.9769 C4.91819 18 4.77879 18 4.5 18 C4.22121 18 4.08181 18 3.96589 18.0231 C3.48986 18.1177 3.11775 18.4899 3.02306 18.9659 C3 19.0818 3 19.2212 3 19.5 V20.4 C3 20.6828 3 20.8243 3.08787 20.9121 C3.17574 21 3.31716 21 3.6 21 H6"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M3 3 H4.2 C4.36569 3 4.5 3.13431 4.5 3.3 V9 M4.5 9 H3 M4.5 9 H6"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val Link01: ImageVector by lazy {
        build(
            name = "Link01",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M9.14339 10.691 L9.35031 10.4841 C11.329 8.50532 14.5372 8.50532 16.5159 10.4841 C18.4947 12.4628 18.4947 15.671 16.5159 17.6497 L13.6497 20.5159 C11.671 22.4947 8.46279 22.4947 6.48405 20.5159 C4.50532 18.5372 4.50532 15.329 6.48405 13.3503 L6.9484 12.886"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M17.0516 11.114 L17.5159 10.6497 C19.4947 8.67095 19.4947 5.46279 17.5159 3.48405 C15.5372 1.50532 12.329 1.50532 10.3503 3.48405 L7.48405 6.35031 C5.50532 8.32904 5.50532 11.5372 7.48405 13.5159 C9.46279 15.4947 12.671 15.4947 14.6497 13.5159 L14.8566 13.309"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
            )
        }
    }

    val Lock: ImageVector by lazy {
        build(
            name = "Lock",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M22 12 C22 17.5228 17.5228 22 12 22 C6.47715 22 2 17.5228 2 12 C2 6.47715 6.47715 2 12 2 C17.5228 2 22 6.47715 22 12 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M12 13 C13.1046 13 14 12.1046 14 11 C14 9.89543 13.1046 9 12 9 C10.8954 9 10 9.89543 10 11 C10 12.1046 10.8954 13 12 13 Z M12 13 L12 16"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
            )
        }
    }

    val LockOpen: ImageVector by lazy {
        build(
            name = "LockOpen",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M13.4955 9 H10.4961 C8.16128 9 6.99386 9 6.11018 9.47237 C5.41244 9.84535 4.84098 10.4169 4.46807 11.1146 C3.99578 11.9984 3.99589 13.1658 3.9961 15.5006 C3.99632 17.835 3.99643 19.0023 4.46877 19.8858 C4.84172 20.5835 5.41317 21.1548 6.11085 21.5277 C6.99445 22 8.16166 22 10.4961 22 H13.4955 C15.8301 22 16.9974 22 17.8811 21.5277 C18.5788 21.1548 19.1503 20.5833 19.5232 19.8856 C19.9955 19.0019 19.9955 17.8346 19.9955 15.5 C19.9955 13.1654 19.9955 11.9981 19.5232 11.1144 C19.1503 10.4167 18.5788 9.84525 17.8811 9.47231 C16.9974 9 15.8301 9 13.4955 9 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M7.49609 9 V6.5 C7.49609 4.01472 9.51081 2 11.9961 2 C13.9554 2 15.3783 3.25221 15.9961 5"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val LockPassword: ImageVector by lazy {
        build(
            name = "LockPassword",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M4.26781 18.8447 C4.49269 20.515 5.87613 21.8235 7.55966 21.9009 C8.97627 21.966 10.4153 22 12 22 C13.5847 22 15.0237 21.966 16.4403 21.9009 C18.1239 21.8235 19.5073 20.515 19.7322 18.8447 C19.879 17.7547 20 16.6376 20 15.5 C20 14.3624 19.879 13.2453 19.7322 12.1553 C19.5073 10.485 18.1239 9.17649 16.4403 9.09909 C15.0237 9.03397 13.5847 9 12 9 C10.4153 9 8.97627 9.03397 7.55966 9.09909 C5.87613 9.17649 4.49269 10.485 4.26781 12.1553 C4.12105 13.2453 4 14.3624 4 15.5 C4 16.6376 4.12105 17.7547 4.26781 18.8447 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M7.5 9 V6.5 C7.5 4.01472 9.51472 2 12 2 C14.4853 2 16.5 4.01472 16.5 6.5 V9"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M12.125 15.5 H12 M12.25 15.5 C12.25 15.6381 12.1381 15.75 12 15.75 C11.8619 15.75 11.75 15.6381 11.75 15.5 C11.75 15.3619 11.8619 15.25 12 15.25 C12.1381 15.25 12.25 15.3619 12.25 15.5 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M8.125 15.5 H8 M8.25 15.5 C8.25 15.6381 8.13807 15.75 8 15.75 C7.86193 15.75 7.75 15.6381 7.75 15.5 C7.75 15.3619 7.86193 15.25 8 15.25 C8.13807 15.25 8.25 15.3619 8.25 15.5 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M16.125 15.5 H16 M16.25 15.5 C16.25 15.6381 16.1381 15.75 16 15.75 C15.8619 15.75 15.75 15.6381 15.75 15.5 C15.75 15.3619 15.8619 15.25 16 15.25 C16.1381 15.25 16.25 15.3619 16.25 15.5 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val Logout03: ImageVector by lazy {
        build(
            name = "Logout03",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M18 18 C18 18.4644 18 18.6965 17.978 18.8918 C17.7952 20.5145 16.5145 21.7952 14.8919 21.978 C14.6965 22 14.4644 22 14 22 H11 C7.70017 22 6.05025 22 5.02513 20.9749 C4 19.9498 4 18.2998 4 15 L4.00001 8.99998 C4.00001 5.70016 4.00003 4.05024 5.02514 3.02512 C6.05027 2 7.70018 2 11 2 H14 C14.4644 2 14.6965 2 14.8919 2.02201 C16.5145 2.20483 17.7952 3.48547 17.978 5.10816 C18 5.30347 18 5.53565 18 6"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M8.07612 11.1183 C8 11.3021 8 11.535 8 12.001 C8 12.4669 8 12.6999 8.07612 12.8837 C8.17761 13.1287 8.37229 13.3234 8.61732 13.4249 C8.80109 13.501 9.03406 13.501 9.5 13.501 H14.5 C14.5002 15.2503 14.511 16.1299 15.0623 16.3858 C15.0829 16.3954 15.1037 16.4042 15.1249 16.4124 C15.7045 16.6353 16.3999 16.0139 17.7907 14.7711 C19.2576 13.4602 19.9912 12.7851 20 11.957 C19.9912 11.1289 19.2576 10.4538 17.7907 9.14301 C16.3999 7.90018 15.7045 7.27876 15.1249 7.50171 C15.1037 7.50985 15.0829 7.51869 15.0623 7.52822 C14.5018 7.78844 14.5 8.69318 14.5 10.501 H9.5 C9.03406 10.501 8.80109 10.501 8.61732 10.5771 C8.37229 10.6786 8.17761 10.8733 8.07612 11.1183 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val MapPin: ImageVector by lazy {
        build(
            name = "MapPin",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M7 9.00183 C4.82497 9.01495 3.64706 9.11944 2.87868 9.95185 C2 10.9038 2 12.4358 2 15.4999 C2 18.5641 2 20.0961 2.87868 21.048 C3.75736 21.9999 5.17157 21.9999 8 21.9999 H16 C18.8284 21.9999 20.2426 21.9999 21.1213 21.048 C22 20.0961 22 18.5641 22 15.4999 C22 12.4358 22 10.9038 21.1213 9.95185 C20.3529 9.11944 19.175 9.01495 17 9.00183"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M6 12 L10.5 14.625 M18 19 L13.8 16.55 M13.8 16.55 L18 13.75 M13.8 16.55 L10.5 14.625 M10.5 14.625 L6 17.6"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M12 7 C13.3807 7 14.5 5.88071 14.5 4.5 C14.5 3.11929 13.3807 2 12 2 C10.6193 2 9.5 3.11929 9.5 4.5 C9.5 5.88071 10.6193 7 12 7 Z M12 7 V11"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
            )
        }
    }

    val MapsLocation02: ImageVector by lazy {
        build(
            name = "MapsLocation02",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M22 10.5 V9.71749 C22 7.77787 22 6.80807 21.4142 6.2055 C20.8284 5.60294 19.8856 5.60294 18 5.60294 H15.9214 C15.004 5.60294 14.9964 5.60116 14.1715 5.18834 L10.8399 3.52114 C9.44884 2.82504 8.75332 2.47699 8.01238 2.50118 C7.27143 2.52537 6.59877 2.91808 5.25345 3.70351 L4.02558 4.42037 C3.03739 4.99729 2.54329 5.28576 2.27164 5.76564 C2 6.24553 2 6.82993 2 7.99873 V16.2157 C2 17.7514 2 18.5193 2.34226 18.9467 C2.57001 19.231 2.88916 19.4222 3.242 19.4856 C3.77226 19.5808 4.42148 19.2018 5.71987 18.4437 C6.60156 17.929 7.45011 17.3944 8.50487 17.5394 C9.38869 17.6608 10.21 18.2185 11 18.6138"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M8 2.5 L8 17.5"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M15 5.5 V9.5"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M17.5 12 C19.9353 12 22 14.0165 22 16.4629 C22 18.9482 19.9017 20.6924 17.9635 21.8783 C17.8223 21.9581 17.6625 22 17.5 22 C17.3375 22 17.1777 21.9581 17.0365 21.8783 C15.1019 20.6808 13 18.9568 13 16.4629 C13 14.0165 15.0647 12 17.5 12 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M17.625 16.5 H17.5 M17.75 16.5 C17.75 16.6381 17.6381 16.75 17.5 16.75 C17.3619 16.75 17.25 16.6381 17.25 16.5 C17.25 16.3619 17.3619 16.25 17.5 16.25 C17.6381 16.25 17.75 16.3619 17.75 16.5 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val Medal02: ImageVector by lazy {
        build(
            name = "Medal02",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M3 5.11296 C3 4.56029 3 4.28395 3.04289 4.05373 C3.23304 3.03312 4.02565 2.23473 5.03889 2.0432 C5.26745 2 5.54179 2 6.09048 2 H17.9095 C18.4582 2 18.7325 2 18.9611 2.0432 C19.9743 2.23473 20.767 3.03312 20.9571 4.05373 C21 4.28395 21 4.56029 21 5.11296 C21 5.6552 21 5.92631 20.9685 6.17771 C20.8309 7.27541 20.2512 8.26781 19.3654 8.92208 C19.1625 9.07192 18.9272 9.20359 18.4566 9.46692 L15.8851 10.9059 C13.9861 11.9686 13.0365 12.5 12 12.5 C10.9635 12.5 10.0139 11.9686 8.11486 10.9059 L5.54338 9.46692 C5.0728 9.20359 4.83752 9.07192 4.63465 8.92208 C3.74885 8.26781 3.16914 7.27541 3.03152 6.17771 C3 5.92631 3 5.6552 3 5.11296 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M8 5 V6 M12 5 V8 M16 5 V6"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M12.7774 13.6499 L13.5693 15.2468 C13.6773 15.4691 13.9653 15.6823 14.2083 15.7231 L15.6436 15.9636 C16.5615 16.1178 16.7775 16.7893 16.1161 17.4516 L15.0002 18.5767 C14.8112 18.7673 14.7077 19.1347 14.7662 19.3979 L15.0857 20.7906 C15.3377 21.893 14.7572 22.3195 13.7898 21.7433 L12.4445 20.9403 C12.2015 20.7952 11.801 20.7952 11.5536 20.9403 L10.2082 21.7433 C9.24533 22.3195 8.66039 21.8885 8.91236 20.7906 L9.23183 19.3979 C9.29032 19.1347 9.18683 18.7673 8.99785 18.5767 L7.88198 17.4516 C7.22505 16.7893 7.43653 16.1178 8.35443 15.9636 L9.78977 15.7231 C10.0282 15.6823 10.3162 15.4691 10.4242 15.2468 L11.2161 13.6499 C11.6481 12.7834 12.35 12.7834 12.7774 13.6499 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val Megaphone01: ImageVector by lazy {
        build(
            name = "Megaphone01",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M9 14 V6 H6 C5.07003 6 4.60504 6 4.22354 6.10222 C3.18827 6.37962 2.37962 7.18827 2.10222 8.22354 C2 8.60504 2 9.07003 2 10 C2 10.93 2 11.395 2.10222 11.7765 C2.37962 12.8117 3.18827 13.6204 4.22354 13.8978 C4.60504 14 5.07003 14 6 14 H9 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M9 14 L9.96466 18.8233 C9.98816 18.9408 10 19.0604 10 19.1802 V19.3411 C10 20.2573 9.25728 21 8.34109 21 C7.55885 21 6.8829 20.4536 6.719 19.6887 L5.5 14"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M10 6 H9 V14 H10 C11.2982 14 12.5614 14.4211 13.6 15.2 L15.747 16.8103 C15.9112 16.9334 16.111 17 16.3162 17 C16.7246 17 17.0909 16.7404 17.2088 16.3495 C17.5537 15.2067 18 13.2728 18 10 C18 6.72718 17.5537 4.79327 17.2088 3.65054 C17.0909 3.25961 16.7246 3 16.3162 3 C16.111 3 15.9112 3.06658 15.747 3.18974 L13.6 4.8 C12.5614 5.57893 11.2982 6 10 6 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M21 9.99996 H22 M20.5 6.00109 L21.5 5.40637 M20.5 14 L21.5 14.6481"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val Menu01: ImageVector by lazy {
        build(
            name = "Menu01",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M4 5 L20 5"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M4 12 L20 12"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M4 19 L20 19"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val MoreVertical: ImageVector by lazy {
        build(
            name = "MoreVertical",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M11.9967 12.5 V12 M11.9967 6.5 V6 M11.9967 18.5 V18 M12.9967 12.5 C12.9967 11.9477 12.549 11.5 11.9967 11.5 C11.4444 11.5 10.9967 11.9477 10.9967 12.5 C10.9967 13.0523 11.4444 13.5 11.9967 13.5 C12.549 13.5 12.9967 13.0523 12.9967 12.5 Z M12.9967 6.5 C12.9967 5.94772 12.549 5.5 11.9967 5.5 C11.4444 5.5 10.9967 5.94772 10.9967 6.5 C10.9967 7.05228 11.4444 7.5 11.9967 7.5 C12.549 7.5 12.9967 7.05228 12.9967 6.5 Z M12.9967 18.5 C12.9967 17.9477 12.549 17.5 11.9967 17.5 C11.4444 17.5 10.9967 17.9477 10.9967 18.5 C10.9967 19.0523 11.4444 19.5 11.9967 19.5 C12.549 19.5 12.9967 19.0523 12.9967 18.5 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val Note01: ImageVector by lazy {
        build(
            name = "Note01",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M16.5 2 V5 M7.5 2 V5 M12 2 V5"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M13 3.5 H11 C7.70017 3.5 6.05025 3.5 5.02513 4.52513 C4 5.55025 4 7.20017 4 10.5 V15 C4 18.2998 4 19.9497 5.02513 20.9749 C6.05025 22 7.70017 22 11 22 H13 C16.2998 22 17.9497 22 18.9749 20.9749 C20 19.9497 20 18.2998 20 15 V10.5 C20 7.20017 20 5.55025 18.9749 4.52512 C17.9497 3.5 16.2998 3.5 13 3.5 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M8 15 H12 M8 11 H16"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val Notification01: ImageVector by lazy {
        build(
            name = "Notification01",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M15.5 18 C15.5 19.933 13.933 21.5 12 21.5 C10.067 21.5 8.5 19.933 8.5 18"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M19.2311 18 H4.76887 C3.79195 18 3 17.208 3 16.2311 C3 15.762 3.18636 15.3121 3.51809 14.9803 L4.12132 14.3771 C4.68393 13.8145 5 13.0514 5 12.2558 V9.5 C5 5.63401 8.13401 2.5 12 2.5 C15.866 2.5 19 5.634 19 9.5 V12.2558 C19 13.0514 19.3161 13.8145 19.8787 14.3771 L20.4819 14.9803 C20.8136 15.3121 21 15.762 21 16.2311 C21 17.208 20.208 18 19.2311 18 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val Notification02: ImageVector by lazy {
        build(
            name = "Notification02",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M19 18 V9.5 C19 5.63401 15.866 2.5 12 2.5 C8.13401 2.5 5 5.63401 5 9.5 V18"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M20.5 18 H3.5"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M13.5 20 C13.5 20.8284 12.8284 21.5 12 21.5 M10.5 20 C10.5 20.8284 11.1716 21.5 12 21.5 M12 21.5 V20"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val Notification03: ImageVector by lazy {
        build(
            name = "Notification03",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M20 18.5011 L18.349 7.93407 C17.8603 4.80601 15.166 2.5 12 2.5 C8.83398 2.5 6.13971 4.80601 5.65098 7.93407 L4 18.5011"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M20 18.5 C20 16.8431 16.4183 15.5 12 15.5 C7.58172 15.5 4 16.8431 4 18.5 C4 20.1569 7.58172 21.5 12 21.5 C16.4183 21.5 20 20.1569 20 18.5 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M13 18.5 H11"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val NotificationSnooze02: ImageVector by lazy {
        build(
            name = "NotificationSnooze02",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M19 11 V18.5 M5 18.5 V10 C5 6.13401 8.13401 3 12 3"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M20.5 18.5 H3.5"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M13.5 20.5 C13.5 21.3284 12.8284 22 12 22 M10.5 20.5 C10.5 21.3284 11.1716 22 12 22 M12 22 V20.5"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M15.016 2 H18.6884 C19.4611 2 19.8474 2 19.9503 2.24011 C20.0532 2.48023 19.7903 2.76827 19.2646 3.34436 L15.8469 6.65564 C15.3211 7.23173 14.9131 7.51977 15.016 7.75989 C15.1189 8 15.6504 8 16.423 8 H20"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val Package01: ImageVector by lazy {
        build(
            name = "Package01",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M2.5 7.5 V13.5 C2.5 17.2712 2.5 19.1569 3.67157 20.3284 C4.84315 21.5 6.72876 21.5 10.5 21.5 H13.5 C17.2712 21.5 19.1569 21.5 20.3284 20.3284 C21.5 19.1569 21.5 17.2712 21.5 13.5 V7.5"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M3.86909 5.31461 L2.5 7.5 H21.5 L20.2478 5.41303 C19.3941 3.99021 18.9673 3.2788 18.2795 2.8894 C17.5918 2.5 16.7621 2.5 15.1029 2.5 H8.95371 C7.32998 2.5 6.51812 2.5 5.84013 2.8753 C5.16215 3.2506 4.73113 3.93861 3.86909 5.31461 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M12 7.5 V2.5"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M10 10.5 H14"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val PackageSearch01: ImageVector by lazy {
        build(
            name = "PackageSearch01",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M2.5 7.5 V13.5 C2.5 17.2712 2.5 19.1569 3.67157 20.3284 C4.84315 21.5 6.72876 21.5 10.5 21.5 H13 M21.5 13 V7.5"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M3.86909 5.31461 L2.5 7.5 H21.5 L20.2478 5.41303 C19.3941 3.99021 18.9673 3.2788 18.2795 2.8894 C17.5918 2.5 16.7621 2.5 15.1029 2.5 H8.95371 C7.32998 2.5 6.51812 2.5 5.84013 2.8753 C5.16215 3.2506 4.73113 3.93861 3.86909 5.31461 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M12 7.5 V2.5"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M10 10.5 H14"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M21.3787 21.3784 L19.5 19.4998 M19.5 19.4998 C20.0429 18.9569 20.3787 18.2069 20.3787 17.3784 C20.3787 15.7216 19.0355 14.3784 17.3787 14.3784 C15.7218 14.3784 14.3787 15.7216 14.3787 17.3784 C14.3787 19.0353 15.7218 20.3784 17.3787 20.3784 C18.2071 20.3784 18.9571 20.0426 19.5 19.4998 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val Palette: ImageVector by lazy {
        build(
            name = "Palette",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M21.8205 10.4127 C22.062 11.8519 22.1827 12.5715 21.2423 13.9326 C21.1459 14.0722 20.8966 14.3713 20.777 14.4911 C19.6103 15.6586 18.4308 15.6586 16.0716 15.6586 H14.1392 C13.5085 15.6586 13.1931 15.6586 12.9639 15.7142 C11.9586 15.9581 11.3031 16.9391 11.453 17.9755 C11.4872 18.2118 11.6043 18.5085 11.8386 19.102 C11.9345 19.3449 11.9824 19.4664 12.0136 19.7304 C12.1292 20.7084 11.0869 21.9508 10.1158 21.9926 C9.85358 22.0039 9.83681 22.0002 9.80326 21.9926 C7.66174 21.51 5.66204 20.3123 4.18389 18.4421 C0.736789 14.0808 1.43146 7.71364 5.73548 4.22064 C10.0395 0.727643 16.323 1.43156 19.7701 5.79289 C20.868 7.1819 21.5457 8.77438 21.8205 10.4127 Z"),
                pathFillType = PathFillType.EvenOdd,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M7.36719 7.74976 H7.24219 M7.49219 7.74976 C7.49219 7.88783 7.38026 7.99976 7.24219 7.99976 C7.10412 7.99976 6.99219 7.88783 6.99219 7.74976 C6.99219 7.61169 7.10412 7.49976 7.24219 7.49976 C7.38026 7.49976 7.49219 7.61169 7.49219 7.74976 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M7.36719 15.7498 H7.24219 M7.49219 15.7498 C7.49219 15.8878 7.38026 15.9998 7.24219 15.9998 C7.10412 15.9998 6.99219 15.8878 6.99219 15.7498 C6.99219 15.6117 7.10412 15.4998 7.24219 15.4998 C7.38026 15.4998 7.49219 15.6117 7.49219 15.7498 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M11.8672 5.74976 H11.7422 M11.9922 5.74976 C11.9922 5.88783 11.8803 5.99976 11.7422 5.99976 C11.6041 5.99976 11.4922 5.88783 11.4922 5.74976 C11.4922 5.61169 11.6041 5.49976 11.7422 5.49976 C11.8803 5.49976 11.9922 5.61169 11.9922 5.74976 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M16.3672 7.74976 H16.2422 M16.4922 7.74976 C16.4922 7.88783 16.3803 7.99976 16.2422 7.99976 C16.1041 7.99976 15.9922 7.88783 15.9922 7.74976 C15.9922 7.61169 16.1041 7.49976 16.2422 7.49976 C16.3803 7.49976 16.4922 7.61169 16.4922 7.74976 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M18.3672 11.7498 H18.2422 M18.4922 11.7498 C18.4922 11.8878 18.3803 11.9998 18.2422 11.9998 C18.1041 11.9998 17.9922 11.8878 17.9922 11.7498 C17.9922 11.6117 18.1041 11.4998 18.2422 11.4998 C18.3803 11.4998 18.4922 11.6117 18.4922 11.7498 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M5.86719 11.7498 H5.74219 M5.99219 11.7498 C5.99219 11.8878 5.88026 11.9998 5.74219 11.9998 C5.60412 11.9998 5.49219 11.8878 5.49219 11.7498 C5.49219 11.6117 5.60412 11.4998 5.74219 11.4998 C5.88026 11.4998 5.99219 11.6117 5.99219 11.7498 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val PencilEdit02: ImageVector by lazy {
        build(
            name = "PencilEdit02",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M16.4249 4.60509 L17.4149 3.6151 C18.2351 2.79497 19.5648 2.79497 20.3849 3.6151 C21.205 4.43524 21.205 5.76493 20.3849 6.58507 L19.3949 7.57506 M16.4249 4.60509 L9.76558 11.2644 C9.25807 11.772 8.89804 12.4078 8.72397 13.1041 L8 16 L10.8959 15.276 C11.5922 15.102 12.228 14.7419 12.7356 14.2344 L19.3949 7.57506 M16.4249 4.60509 L19.3949 7.57506"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M18.9999 13.5 C18.9999 16.7875 18.9999 18.4312 18.092 19.5376 C17.9258 19.7401 17.7401 19.9258 17.5375 20.092 C16.4312 21 14.7874 21 11.4999 21 H11 C7.22876 21 5.34316 21 4.17159 19.8284 C3.00003 18.6569 3 16.7712 3 13 V12.5 C3 9.21252 3 7.56879 3.90794 6.46244 C4.07417 6.2599 4.2599 6.07417 4.46244 5.90794 C5.56879 5 7.21252 5 10.5 5"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val Play: ImageVector by lazy {
        build(
            name = "Play",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M18.8906 12.846 C18.5371 14.189 16.8667 15.138 13.5257 17.0361 C10.296 18.8709 8.6812 19.7884 7.37983 19.4196 C6.8418 19.2671 6.35159 18.9776 5.95624 18.5787 C5 17.6139 5 15.7426 5 12 C5 8.2574 5 6.3861 5.95624 5.42132 C6.35159 5.02245 6.8418 4.73288 7.37983 4.58042 C8.6812 4.21165 10.296 5.12907 13.5257 6.96393 C16.8667 8.86197 18.5371 9.811 18.8906 11.154 C19.0365 11.7084 19.0365 12.2916 18.8906 12.846 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val Plus: ImageVector by lazy {
        build(
            name = "Plus",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M11.9922 4.00012 V20.0001 M19.9922 12.0001 H3.99222"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val QrCode01: ImageVector by lazy {
        build(
            name = "QrCode01",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M6.79289 6.79289 C6.5 7.08579 6.5 7.55719 6.5 8.5 C6.5 9.44281 6.5 9.91421 6.79289 10.2071 C7.08579 10.5 7.55719 10.5 8.5 10.5 C9.44281 10.5 9.91421 10.5 10.2071 10.2071 C10.5 9.91421 10.5 9.44281 10.5 8.5 C10.5 7.55719 10.5 7.08579 10.2071 6.79289 C9.91421 6.5 9.44281 6.5 8.5 6.5 C7.55719 6.5 7.08579 6.5 6.79289 6.79289 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M13.5 10.5 C14.4428 10.5 14.9142 10.5 15.2071 10.2071 C15.5 9.91421 15.5 9.44281 15.5 8.5 C15.5 7.55719 15.5 7.08579 15.7929 6.79289 C16.0858 6.5 16.5572 6.5 17.5 6.5"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M6.5 13.5 H8.5 C9.44281 13.5 9.91421 13.5 10.2071 13.7929 C10.5 14.0858 10.5 14.5572 10.5 15.5 V17.5"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M6.875 17.25 H6.75 M7 17.25 C7 17.3881 6.88807 17.5 6.75 17.5 C6.61193 17.5 6.5 17.3881 6.5 17.25 C6.5 17.1119 6.61193 17 6.75 17 C6.88807 17 7 17.1119 7 17.25 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M13.7929 13.7929 C13.5 14.0858 13.5 14.5572 13.5 15.5 C13.5 16.4428 13.5 16.9142 13.7929 17.2071 C14.0858 17.5 14.5572 17.5 15.5 17.5 C16.4428 17.5 16.9142 17.5 17.2071 17.2071 C17.5 16.9142 17.5 16.4428 17.5 15.5 C17.5 14.5572 17.5 14.0858 17.2071 13.7929 C16.9142 13.5 16.4428 13.5 15.5 13.5 C14.5572 13.5 14.0858 13.5 13.7929 13.7929 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M6.5 21.5 C6.03563 21.5 5.80344 21.5 5.60812 21.478 C3.98545 21.2952 2.70484 20.0145 2.52201 18.3919 C2.5 18.1966 2.5 17.9644 2.5 17.5 M17.5 21.5 C17.9644 21.5 18.1966 21.5 18.3919 21.478 C20.0145 21.2952 21.2952 20.0145 21.478 18.3919 C21.5 18.1966 21.5 17.9644 21.5 17.5 M6.5 2.5 C6.03563 2.5 5.80344 2.5 5.60812 2.52201 C3.98545 2.70484 2.70484 3.98545 2.52201 5.60812 C2.5 5.80344 2.5 6.03563 2.5 6.5 M17.5 2.5 C17.9644 2.5 18.1966 2.5 18.3919 2.52201 C20.0145 2.70484 21.2952 3.98545 21.478 5.60812 C21.5 5.80344 21.5 6.03563 21.5 6.5"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val QrCodeScan: ImageVector by lazy {
        build(
            name = "QrCodeScan",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M2.5 8.18677 C2.60406 6.08705 2.91537 4.77792 3.84664 3.84664 C4.77792 2.91537 6.08705 2.60406 8.18677 2.5 M21.5 8.18677 C21.3959 6.08705 21.0846 4.77792 20.1534 3.84664 C19.2221 2.91537 17.9129 2.60406 15.8132 2.5 M15.8132 21.5 C17.9129 21.3959 19.2221 21.0846 20.1534 20.1534 C21.0846 19.2221 21.3959 17.9129 21.5 15.8132 M8.18676 21.5 C6.08705 21.3959 4.77792 21.0846 3.84664 20.1534 C2.91537 19.2221 2.60406 17.9129 2.5 15.8132"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M8.23463 12.8478 C8.60218 13 9.06812 13 10 13 C10.9319 13 11.3978 13 11.7654 12.8478 C12.2554 12.6448 12.6448 12.2554 12.8478 11.7654 C13 11.3978 13 10.9319 13 10 C13 9.06812 13 8.60218 12.8478 8.23463 C12.6448 7.74458 12.2554 7.35523 11.7654 7.15224 C11.3978 7 10.9319 7 10 7 C9.06812 7 8.60218 7 8.23463 7.15224 C7.74458 7.35523 7.35523 7.74458 7.15224 8.23463 C7 8.60218 7 9.06812 7 10 C7 10.9319 7 11.3978 7.15224 11.7654 C7.35523 12.2554 7.74458 12.6448 8.23463 12.8478 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M17 7 V7.01"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M17 11 V13 C17 14.8856 17 15.8284 16.4142 16.4142 C15.8284 17 14.8856 17 13 17"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M9 17 H7"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val QuoteUp: ImageVector by lazy {
        build(
            name = "QuoteUp",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M14 16 C14 14.1144 14 13.1716 14.5858 12.5858 C15.1716 12 16.1144 12 18 12 C19.8856 12 20.8284 12 21.4142 12.5858 C22 13.1716 22 14.1144 22 16 C22 17.8856 22 18.8284 21.4142 19.4142 C20.8284 20 19.8856 20 18 20 C16.1144 20 15.1716 20 14.5858 19.4142 C14 18.8284 14 17.8856 14 16 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M14 16 V11.8626 C14 8.19569 16.5157 5.08584 20 4"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M2 16 C2 14.1144 2 13.1716 2.58579 12.5858 C3.17157 12 4.11438 12 6 12 C7.88562 12 8.82843 12 9.41421 12.5858 C10 13.1716 10 14.1144 10 16 C10 17.8856 10 18.8284 9.41421 19.4142 C8.82843 20 7.88562 20 6 20 C4.11438 20 3.17157 20 2.58579 19.4142 C2 18.8284 2 17.8856 2 16 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M2 16 V11.8626 C2 8.19569 4.51571 5.08584 8 4"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
            )
        }
    }

    val Receipt: ImageVector by lazy {
        build(
            name = "Receipt",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M8.06024 2.72546 L7.88823 2.86189 C7.70302 3.00878 7.61041 3.08223 7.51824 3.12852 C7.19917 3.28874 6.81809 3.26781 6.51881 3.07364 C6.43236 3.01754 6.3485 2.93441 6.18079 2.76813 C5.78074 2.37152 5.58072 2.17321 5.42995 2.10043 C4.89043 1.83999 4.24264 2.10601 4.04689 2.6684 C3.99219 2.82556 3.99219 3.10601 3.99219 3.66691 V20.698 C3.99219 20.9548 3.99219 21.0832 4.00377 21.158 C4.11773 21.8938 4.97843 22.2473 5.58378 21.8069 C5.64532 21.7621 5.73693 21.6713 5.92007 21.4897 C6.03528 21.3755 6.09297 21.3183 6.14757 21.2735 C6.65461 20.8578 7.37571 20.8182 7.92595 21.1759 C7.98522 21.2144 8.04886 21.2649 8.17614 21.3658 L8.31228 21.4738 C8.54263 21.6565 8.65783 21.7479 8.77324 21.8104 C9.22131 22.053 9.76307 22.053 10.2111 21.8104 C10.3265 21.7479 10.4417 21.6565 10.6721 21.4738 L10.7422 21.4182 C11.0392 21.1827 11.1877 21.0649 11.3406 20.9918 C11.7523 20.7949 12.2321 20.7949 12.6438 20.9918 C12.7967 21.0649 12.9452 21.1827 13.2422 21.4182 L13.3123 21.4738 C13.5426 21.6565 13.6578 21.7479 13.7732 21.8104 C14.2213 22.053 14.7631 22.053 15.2111 21.8104 C15.3265 21.7479 15.4417 21.6565 15.6721 21.4738 L15.8082 21.3658 C15.9355 21.2649 15.9992 21.2144 16.0584 21.1759 C16.6087 20.8182 17.3298 20.8578 17.8368 21.2735 C17.8914 21.3183 17.9491 21.3755 18.0643 21.4897 C18.2475 21.6713 18.3391 21.7621 18.4006 21.8069 C19.0059 22.2473 19.8666 21.8938 19.9806 21.158 C19.9922 21.0832 19.9922 20.9548 19.9922 20.698 V3.66691 C19.9922 3.10601 19.9922 2.82556 19.9375 2.6684 C19.7417 2.10601 19.0939 1.83999 18.5544 2.10043 C18.4037 2.17321 18.2036 2.37152 17.8036 2.76813 C17.6359 2.93441 17.552 3.01754 17.4656 3.07364 C17.1663 3.26781 16.7852 3.28874 16.4661 3.12852 C16.374 3.08223 16.2814 3.00878 16.0961 2.86189 L15.9241 2.72546 C15.4536 2.35223 15.2183 2.16562 14.9617 2.08178 C14.6568 1.98214 14.3276 1.98214 14.0227 2.08178 C13.7661 2.16562 13.5308 2.35224 13.0602 2.72546 L12.9922 2.77943 C12.635 3.06273 12.4564 3.20438 12.2583 3.2586 C12.0842 3.30627 11.9002 3.30627 11.7261 3.2586 C11.528 3.20438 11.3494 3.06273 10.9922 2.77943 L10.9241 2.72546 C10.4536 2.35223 10.2183 2.16562 9.96172 2.08178 C9.65676 1.98214 9.32761 1.98214 9.02266 2.08178 C8.76609 2.16562 8.53081 2.35223 8.06024 2.72546 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M11.9922 8.5 H13.4922 C14.3206 8.5 14.9922 9.17157 14.9922 10 M11.9922 8.5 H10.4922 C9.66376 8.5 8.99219 9.17157 8.99219 10 V10.5 C8.99219 11.3284 9.66376 12 10.4922 12 H13.4922 C14.3206 12 14.9922 12.6716 14.9922 13.5 V14 C14.9922 14.8284 14.3206 15.5 13.4922 15.5 H11.9922 M11.9922 8.5 V7 M11.9922 15.5 H10.4922 C9.66376 15.5 8.99219 14.8284 8.99219 14 M11.9922 15.5 V17"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val Refresh: ImageVector by lazy {
        build(
            name = "Refresh",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M20.0092 2 V5.13219 C20.0092 5.42605 19.6418 5.55908 19.4537 5.33333 C17.6226 3.2875 14.9617 2 12 2 C6.47715 2 2 6.47715 2 12 C2 17.5228 6.47715 22 12 22 C17.5228 22 22 17.5228 22 12"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val RefreshCcw: ImageVector by lazy {
        build(
            name = "RefreshCcw",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M20.9373 13 C20.9736 12.6717 20.9922 12.338 20.9922 12 C20.9922 7.02944 16.9628 3 11.9922 3 C9.16492 3 6.64214 4.30367 4.99219 6.34267 M3.04712 11 C3.01082 11.3283 2.99219 11.662 2.99219 12 C2.99219 16.9706 7.02162 21 11.9922 21 C14.8195 21 17.3422 19.6963 18.9922 17.6573"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M7.99219 7 H6.99219 C5.57797 7 4.87087 7 4.43153 6.56066 C3.99219 6.12132 3.99219 5.41421 3.99219 4 V3"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M15.9922 17 H16.9922 C18.4064 17 19.1135 17 19.5528 17.4393 C19.9922 17.8787 19.9922 18.5858 19.9922 20 V21"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val Repair: ImageVector by lazy {
        build(
            name = "Repair",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M11 11 L6 6"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M5 7.5 L7.5 5 L4.5 3.5 L3.5 4.5 L5 7.5 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M19.9749 8.97487 C20.9858 7.96391 21.2491 6.48836 20.7645 5.23548 L19.3425 6.65748 H17.3425 V4.65748 L18.7645 3.23548 C17.5116 2.75095 16.0361 3.01416 15.0251 4.02513 C14.0138 5.03647 13.7507 6.51274 14.236 7.76593 L7.76593 14.236 C6.51275 13.7507 5.03647 14.0138 4.02513 15.0251 C3.01416 16.0361 2.75095 17.5116 3.23548 18.7645 L4.65748 17.3425 L6.65748 17.3425 L6.65748 19.3425 L5.23548 20.7645 C6.48836 21.2491 7.96391 20.9858 8.97487 19.9749 C9.98546 18.9643 10.2489 17.4895 9.76507 16.2369 L16.2369 9.76507 C17.4895 10.2489 18.9643 9.98546 19.9749 8.97487 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M11.797 14.5 L17.401 20.1041 C17.9288 20.6319 18.7846 20.6319 19.3124 20.1041 L20.1041 19.3124 C20.632 18.7845 20.632 17.9288 20.1041 17.401 L14.5001 11.7969"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val Rocket: ImageVector by lazy {
        build(
            name = "Rocket",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M8 10.1667 L12.1232 6.04344 C13.2481 4.91858 13.8105 4.35614 14.4312 3.90314 C15.7047 2.9737 17.1818 2.36187 18.7395 2.11858 C19.4988 2 20.2942 2 21.885 2 C21.9681 2 22 2.03812 22 2.11504 C22 3.70584 22 4.50125 21.8814 5.26046 C21.6381 6.81818 21.0263 8.29527 20.0969 9.56878 C19.6439 10.1895 19.0814 10.7519 17.9566 11.8768 L13.8333 16"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M10.341 8.09838 C8.63808 8.09838 6.49778 7.73765 4.9043 8.39769 C3.73671 8.88132 2.87754 10.0012 2 10.8787 L5.30597 12.2955 C6.18208 12.671 5.64651 13.7766 5.50147 14.5018 C5.33985 15.3099 5.34886 15.3397 5.93158 15.9224 L8.07758 18.0684 C8.6603 18.6511 8.69008 18.6602 9.49816 18.4985 C10.2234 18.3535 11.329 17.8179 11.7044 18.694 L13.1213 22 C13.9988 21.1225 15.1187 20.2633 15.6023 19.0957 C16.2624 17.5022 15.9016 15.3619 15.9016 13.659"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M12 20 L11 21"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M4 12 L3 13"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M15 4.07996 C16.2 4.25996 17.46 4.73996 18.1614 5.45996 C19.0576 6.25211 19.68 7.31996 19.92 8.99996"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Square,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M17.94 6.06006 L16.5 7.50006"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
            )
        }
    }

    val School01: ImageVector by lazy {
        build(
            name = "School01",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M20 7.5 L15.3992 4.24542 C13.7542 3.08181 12.9318 2.5 12 2.5 C11.0682 2.5 10.2458 3.08181 8.60083 4.24542 L4 7.5"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M18 6.5 V21.5 H6 V6.5"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M14 9.5 C14 10.6046 13.1046 11.5 12 11.5 C10.8954 11.5 10 10.6046 10 9.5 C10 8.39543 10.8954 7.5 12 7.5 C13.1046 7.5 14 8.39543 14 9.5 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M19.7889 12.3944 L18 11.5 V21.5 C19.8856 21.5 20.8284 21.5 21.4142 20.9142 C22 20.3284 22 19.3856 22 17.5 V15.9721 C22 14.7693 22 14.1679 21.7013 13.6846 C21.4026 13.2013 20.8647 12.9323 19.7889 12.3944 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M4.21114 12.3944 L6 11.5 V21.5 C4.11438 21.5 3.17157 21.5 2.58579 20.9142 C2 20.3284 2 19.3856 2 17.5 V15.9721 C2 14.7693 2 14.1679 2.2987 13.6846 C2.5974 13.2013 3.13531 12.9323 4.21114 12.3944 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M10 21.5 V17.5 C10 16.3954 10.8954 15.5 12 15.5 C13.1046 15.5 14 16.3954 14 17.5 V21.5"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val ScooterElectric: ImageVector by lazy {
        build(
            name = "ScooterElectric",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M6 18 A2 2 0 1 0 2 18 A2 2 0 1 0 6 18 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M22 18 A2 2 0 1 0 18 18 A2 2 0 1 0 22 18 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M6.51383 18 H13.2887 C14.0213 18 14.3876 18 14.6328 17.8445 C14.878 17.689 15.0854 17.2491 15.5002 16.3693 C15.735 15.8712 16.0361 15.411 16.3921 15 M16.3921 15 L4 16 M16.3921 15 C17.0623 14.2264 17.9273 13.6269 18.913 13.2759 C19.4501 13.0846 19.7186 12.9889 19.8093 12.8143 C19.8999 12.6397 19.8331 12.3991 19.6995 11.918 L18.1691 6.4088 C17.9184 5.50632 17.7931 5.05508 17.5225 4.72715 C17.3256 4.4885 17.076 4.29879 16.7934 4.17293 C16.405 4 15.9366 4 15 4"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M11.4626 7 L10.0686 8.81238 C9.91919 9.0066 10.0263 9.28605 10.2699 9.33776 L11.7301 9.64772 C11.9899 9.70285 12.0889 10.0122 11.9073 10.2011 L10.1777 12"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val Search01: ImageVector by lazy {
        build(
            name = "Search01",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M17 17 L21 21"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M19 11 C19 6.58172 15.4183 3 11 3 C6.58172 3 3 6.58172 3 11 C3 15.4183 6.58172 19 11 19 C15.4183 19 19 15.4183 19 11 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val Settings01: ImageVector by lazy {
        build(
            name = "Settings01",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M21.3175 7.14139 L20.8239 6.28479 C20.4506 5.63696 20.264 5.31305 19.9464 5.18388 C19.6288 5.05472 19.2696 5.15664 18.5513 5.36048 L17.3311 5.70418 C16.8725 5.80994 16.3913 5.74994 15.9726 5.53479 L15.6357 5.34042 C15.2766 5.11043 15.0004 4.77133 14.8475 4.37274 L14.5136 3.37536 C14.294 2.71534 14.1842 2.38533 13.9228 2.19657 C13.6615 2.00781 13.3143 2.00781 12.6199 2.00781 H11.5051 C10.8108 2.00781 10.4636 2.00781 10.2022 2.19657 C9.94085 2.38533 9.83106 2.71534 9.61149 3.37536 L9.27753 4.37274 C9.12465 4.77133 8.84845 5.11043 8.48937 5.34042 L8.15249 5.53479 C7.73374 5.74994 7.25259 5.80994 6.79398 5.70418 L5.57375 5.36048 C4.85541 5.15664 4.49625 5.05472 4.17867 5.18388 C3.86109 5.31305 3.67445 5.63696 3.30115 6.28479 L2.80757 7.14139 C2.45766 7.74864 2.2827 8.05227 2.31666 8.37549 C2.35061 8.69871 2.58483 8.95918 3.05326 9.48012 L4.0843 10.6328 C4.3363 10.9518 4.51521 11.5078 4.51521 12.0077 C4.51521 12.5078 4.33636 13.0636 4.08433 13.3827 L3.05326 14.5354 C2.58483 15.0564 2.35062 15.3168 2.31666 15.6401 C2.2827 15.9633 2.45766 16.2669 2.80757 16.8741 L3.30114 17.7307 C3.67443 18.3785 3.86109 18.7025 4.17867 18.8316 C4.49625 18.9608 4.85542 18.8589 5.57377 18.655 L6.79394 18.3113 C7.25263 18.2055 7.73387 18.2656 8.15267 18.4808 L8.4895 18.6752 C8.84851 18.9052 9.12464 19.2442 9.2775 19.6428 L9.61149 20.6403 C9.83106 21.3003 9.94085 21.6303 10.2022 21.8191 C10.4636 22.0078 10.8108 22.0078 11.5051 22.0078 H12.6199 C13.3143 22.0078 13.6615 22.0078 13.9228 21.8191 C14.1842 21.6303 14.294 21.3003 14.5136 20.6403 L14.8476 19.6428 C15.0004 19.2442 15.2765 18.9052 15.6356 18.6752 L15.9724 18.4808 C16.3912 18.2656 16.8724 18.2055 17.3311 18.3113 L18.5513 18.655 C19.2696 18.8589 19.6288 18.9608 19.9464 18.8316 C20.264 18.7025 20.4506 18.3785 20.8239 17.7307 L21.3175 16.8741 C21.6674 16.2669 21.8423 15.9633 21.8084 15.6401 C21.7744 15.3168 21.5402 15.0564 21.0718 14.5354 L20.0407 13.3827 C19.7887 13.0636 19.6098 12.5078 19.6098 12.0077 C19.6098 11.5078 19.7888 10.9518 20.0407 10.6328 L21.0718 9.48012 C21.5402 8.95918 21.7744 8.69871 21.8084 8.37549 C21.8423 8.05227 21.6674 7.74864 21.3175 7.14139 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M15.5195 12 C15.5195 13.933 13.9525 15.5 12.0195 15.5 C10.0865 15.5 8.51953 13.933 8.51953 12 C8.51953 10.067 10.0865 8.5 12.0195 8.5 C13.9525 8.5 15.5195 10.067 15.5195 12 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
            )
        }
    }

    val Settings02: ImageVector by lazy {
        build(
            name = "Settings02",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M15.5 12 C15.5 13.933 13.933 15.5 12 15.5 C10.067 15.5 8.5 13.933 8.5 12 C8.5 10.067 10.067 8.5 12 8.5 C13.933 8.5 15.5 10.067 15.5 12 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M21.011 14.0965 C21.5329 13.9558 21.7939 13.8854 21.8969 13.7508 C22 13.6163 22 13.3998 22 12.9669 V11.0332 C22 10.6003 22 10.3838 21.8969 10.2493 C21.7938 10.1147 21.5329 10.0443 21.011 9.90358 C19.0606 9.37759 17.8399 7.33851 18.3433 5.40087 C18.4817 4.86799 18.5509 4.60156 18.4848 4.44529 C18.4187 4.28902 18.2291 4.18134 17.8497 3.96596 L16.125 2.98673 C15.7528 2.77539 15.5667 2.66972 15.3997 2.69222 C15.2326 2.71472 15.0442 2.90273 14.6672 3.27873 C13.208 4.73448 10.7936 4.73442 9.33434 3.27864 C8.95743 2.90263 8.76898 2.71463 8.60193 2.69212 C8.43489 2.66962 8.24877 2.77529 7.87653 2.98663 L6.15184 3.96587 C5.77253 4.18123 5.58287 4.28891 5.51678 4.44515 C5.45068 4.6014 5.51987 4.86787 5.65825 5.4008 C6.16137 7.3385 4.93972 9.37763 2.98902 9.9036 C2.46712 10.0443 2.20617 10.1147 2.10308 10.2492 C2 10.3838 2 10.6003 2 11.0332 V12.9669 C2 13.3998 2 13.6163 2.10308 13.7508 C2.20615 13.8854 2.46711 13.9558 2.98902 14.0965 C4.9394 14.6225 6.16008 16.6616 5.65672 18.5992 C5.51829 19.1321 5.44907 19.3985 5.51516 19.5548 C5.58126 19.7111 5.77092 19.8188 6.15025 20.0341 L7.87495 21.0134 C8.24721 21.2247 8.43334 21.3304 8.6004 21.3079 C8.76746 21.2854 8.95588 21.0973 9.33271 20.7213 C10.7927 19.2644 13.2088 19.2643 14.6689 20.7212 C15.0457 21.0973 15.2341 21.2853 15.4012 21.3078 C15.5682 21.3303 15.7544 21.2246 16.1266 21.0133 L17.8513 20.034 C18.2307 19.8187 18.4204 19.711 18.4864 19.5547 C18.5525 19.3984 18.4833 19.132 18.3448 18.5991 C17.8412 16.6616 19.0609 14.6226 21.011 14.0965 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
            )
        }
    }

    val Share01: ImageVector by lazy {
        build(
            name = "Share01",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M9.39584 4.5 H8.35417 C5.40789 4.5 3.93475 4.5 3.01946 5.37868 C2.10417 6.25736 2.10417 7.67157 2.10417 10.5 V14.5 C2.10417 17.3284 2.10417 18.7426 3.01946 19.6213 C3.93475 20.5 5.40789 20.5 8.35417 20.5 H12.5608 C15.5071 20.5 16.9802 20.5 17.8955 19.6213 C18.4885 19.052 18.6973 18.2579 18.7708 17"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M16.1667 7 V3.85355 C16.1667 3.65829 16.3316 3.5 16.535 3.5 C16.6326 3.5 16.7263 3.53725 16.7954 3.60355 L21.5275 8.14645 C21.7634 8.37282 21.8958 8.67986 21.8958 9 C21.8958 9.32014 21.7634 9.62718 21.5275 9.85355 L16.7954 14.3964 C16.7263 14.4628 16.6326 14.5 16.535 14.5 C16.3316 14.5 16.1667 14.3417 16.1667 14.1464 V11 H13.1157 C8.875 11 7.3125 14.5 7.3125 14.5 V12 C7.3125 9.23858 9.64435 7 12.5208 7 H16.1667 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val Share08: ImageVector by lazy {
        build(
            name = "Share08",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M21 6.5 C21 8.15685 19.6569 9.5 18 9.5 C16.3431 9.5 15 8.15685 15 6.5 C15 4.84315 16.3431 3.5 18 3.5 C19.6569 3.5 21 4.84315 21 6.5 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M9 12 C9 13.6569 7.65685 15 6 15 C4.34315 15 3 13.6569 3 12 C3 10.3431 4.34315 9 6 9 C7.65685 9 9 10.3431 9 12 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M21 17.5 C21 19.1569 19.6569 20.5 18 20.5 C16.3431 20.5 15 19.1569 15 17.5 C15 15.8431 16.3431 14.5 18 14.5 C19.6569 14.5 21 15.8431 21 17.5 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M8.72852 10.7495 L15.2285 7.75 M8.72852 13.25 L15.2285 16.2495"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
            )
        }
    }

    val Shield01: ImageVector by lazy {
        build(
            name = "Shield01",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M18.7088 3.49534 C16.8165 2.55382 14.5009 2 12 2 C9.4991 2 7.1835 2.55382 5.29116 3.49534 C4.36318 3.95706 3.89919 4.18792 3.4496 4.91378 C3 5.63965 3 6.34248 3 7.74814 V11.2371 C3 16.9205 7.54236 20.0804 10.173 21.4338 C10.9067 21.8113 11.2735 22 12 22 C12.7265 22 13.0933 21.8113 13.8269 21.4338 C16.4576 20.0804 21 16.9205 21 11.2371 L21 7.74814 C21 6.34249 21 5.63966 20.5504 4.91378 C20.1008 4.18791 19.6368 3.95706 18.7088 3.49534 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val ShoppingBag01: ImageVector by lazy {
        build(
            name = "ShoppingBag01",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M3.32352 13.0113 C3.6739 10.009 4.18586 7.75784 4.66063 6.15851 C5.04994 4.84711 5.24459 4.19141 6.04283 3.5957 C6.84107 3 7.65697 3 9.28876 3 H14.7113 C16.3431 3 17.159 3 17.9572 3.5957 C18.7554 4.19141 18.9501 4.84711 19.3394 6.15851 C19.8142 7.75784 20.3261 10.009 20.6765 13.0113 C21.0895 16.5497 21.2959 18.3189 20.1027 19.6594 C18.9095 21 16.9758 21 13.1084 21 H10.8916 C7.02422 21 5.09052 21 3.89731 19.6594 C2.70411 18.3189 2.91058 16.5497 3.32352 13.0113 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M9 7 C9 8.65685 10.3431 10 12 10 C13.6569 10 15 8.65685 15 7"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val ShoppingCart02: ImageVector by lazy {
        build(
            name = "ShoppingCart02",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M8 16 L16.7201 15.2733 C19.4486 15.046 20.0611 14.45 20.3635 11.7289 L21 6"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M6 6 H22"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M8 20 A2 2 0 1 0 4 20 A2 2 0 1 0 8 20 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M19 20 A2 2 0 1 0 15 20 A2 2 0 1 0 19 20 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M8 20 L15 20"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M2 2 H2.966 C3.91068 2 4.73414 2.62459 4.96326 3.51493 L7.93852 15.0765 C8.08887 15.6608 7.9602 16.2797 7.58824 16.7616 L6.63213 18"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
            )
        }
    }

    val ShowerHead: ImageVector by lazy {
        build(
            name = "ShowerHead",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M6.13225 16.6536 C6.93769 16.9301 7.81546 16.5033 8.09279 15.7002 L9.2645 12.3072 C9.44939 11.7718 10.0346 11.4873 10.5715 11.6716 L11.5438 12.0054 C12.0807 12.1898 12.6659 11.9052 12.8508 11.3698 L15.1942 4.5839 C15.3791 4.04851 15.0937 3.46504 14.5567 3.28069 C11.8719 2.35893 8.94606 3.78182 8.02161 6.45878 L5.17603 14.6988 C4.89869 15.5019 5.32681 16.3771 6.13225 16.6536 Z M6.13225 16.6536 L5.39668 18.7836 C5.01963 19.8755 4.12026 20.7072 3 21 M18.0832 5.91908 L21 6.92047 M17.0789 8.82733 L19.9957 9.82872 M16.0746 11.7356 L18.9914 12.737"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val Sigma: ImageVector by lazy {
        build(
            name = "Sigma",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M18.4922 6.5 V6 C18.4922 4.58579 18.4922 3.87868 18.0208 3.43934 C17.5495 3 16.7909 3 15.2736 3 H10.6394 C7.556 3 6.01431 3 5.58768 3.88552 C5.16105 4.77103 6.18529 5.84505 8.23379 7.99309 L10.1543 10.0069 C11.0588 10.9554 11.5111 11.4297 11.5111 12 C11.5111 12.5703 11.0588 13.0446 10.1543 13.9931 L8.23379 16.0069 C6.18529 18.1549 5.16105 19.229 5.58768 20.1145 C6.01431 21 7.556 21 10.6394 21 H15.2736 C16.7909 21 17.5495 21 18.0208 20.5607 C18.4922 20.1213 18.4922 19.4142 18.4922 18 V17.5"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val SmartPhone01: ImageVector by lazy {
        build(
            name = "SmartPhone01",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M13.5 2 H10.5 C8.14298 2 6.96447 2 6.23223 2.73223 C5.5 3.46447 5.5 4.64298 5.5 7 V17 C5.5 19.357 5.5 20.5355 6.23223 21.2678 C6.96447 22 8.14298 22 10.5 22 H13.5 C15.857 22 17.0355 22 17.7678 21.2678 C18.5 20.5355 18.5 19.357 18.5 17 V7 C18.5 4.64298 18.5 3.46447 17.7678 2.73223 C17.0355 2 15.857 2 13.5 2 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M12.125 19 H12 M12.25 19 C12.25 19.1381 12.1381 19.25 12 19.25 C11.8619 19.25 11.75 19.1381 11.75 19 C11.75 18.8619 11.8619 18.75 12 18.75 C12.1381 18.75 12.25 18.8619 12.25 19 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val SoftwareLicense: ImageVector by lazy {
        build(
            name = "SoftwareLicense",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M18 19.6231 C18.6891 19.4279 19.2323 19.1317 19.682 18.682 C21 17.364 21 15.2426 21 11 C21 6.75736 21 4.63604 19.682 3.31802 C18.364 2 16.2426 2 12 2 C7.75736 2 5.63604 2 4.31802 3.31802 C3 4.63604 3 6.75736 3 11 C3 15.2426 3 17.364 4.31802 18.682 C4.76772 19.1317 5.31093 19.4279 6 19.6231"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M3.5 7 H20.5"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M12 11 C13.6569 11 15 12.3431 15 14 C15 15.6569 13.6569 17 12 17 C10.3431 17 9 15.6569 9 14 C9 12.3431 10.3431 11 12 11 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M14 16.5 V22 L12 20.5 L10 22 V16.5"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val Stamp01: ImageVector by lazy {
        build(
            name = "Stamp01",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M4.98572 19.1814 C5.00154 20.4212 5.08263 20.9434 5.49849 21.4149 C6.0151 22.0007 6.84657 22.0007 8.50951 22.0007 H15.4619 C17.1248 22.0007 17.9563 22.0007 18.4729 21.4149 C18.8888 20.9434 18.9699 20.4212 18.9857 19.1814"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M11.9887 19.0706 L4.75696 19.0706 C4.03899 19.0706 3.4179 18.485 3.50814 17.7727 C3.6672 16.5171 4.30558 14.9994 6.83602 14.066 C7.72556 13.7378 8.70501 13.4538 9.18761 12.6377 C9.86497 11.4922 10.1635 10.2384 8.90009 8.0882 C6.83602 4.0204 9.92439 1.99932 12.0114 1.99932 C14.0983 1.99932 17.1489 4.1488 15.0643 8.0882 C13.7986 10.2209 14.135 11.4922 14.8124 12.6377 C15.295 13.4538 16.2744 13.7378 17.164 14.066 C19.6944 14.9994 20.3328 16.5171 20.4918 17.7727 C20.5821 18.485 19.961 19.0706 19.243 19.0706 L12.0113 19.0706"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val Star: ImageVector by lazy {
        build(
            name = "Star",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M13.7276 3.44418 L15.4874 6.99288 C15.7274 7.48687 16.3673 7.9607 16.9073 8.05143 L20.0969 8.58575 C22.1367 8.92853 22.6167 10.4206 21.1468 11.8925 L18.6671 14.3927 C18.2471 14.8161 18.0172 15.6327 18.1471 16.2175 L18.8571 19.3125 C19.417 21.7623 18.1271 22.71 15.9774 21.4296 L12.9877 19.6452 C12.4478 19.3226 11.5579 19.3226 11.0079 19.6452 L8.01827 21.4296 C5.8785 22.71 4.57865 21.7522 5.13859 19.3125 L5.84851 16.2175 C5.97849 15.6327 5.74852 14.8161 5.32856 14.3927 L2.84884 11.8925 C1.389 10.4206 1.85895 8.92853 3.89872 8.58575 L7.08837 8.05143 C7.61831 7.9607 8.25824 7.48687 8.49821 6.99288 L10.258 3.44418 C11.2179 1.51861 12.7777 1.51861 13.7276 3.44418 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val Sun01: ImageVector by lazy {
        build(
            name = "Sun01",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M16.9991 12 C16.9991 14.7614 14.7605 17 11.9991 17 C9.23766 17 6.99908 14.7614 6.99908 12 C6.99908 9.23858 9.23766 7 11.9991 7 C14.7605 7 16.9991 9.23858 16.9991 12 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M12.1247 3.25 H11.9997 M12.1242 20.75 H11.9992 M20.75 12.125 V12 M3.25 12.125 V12 M18.2752 5.90098 L18.1868 5.81259 M5.90051 18.275 L5.81212 18.1866 M18.0987 18.2756 L18.187 18.1872 M5.72429 5.9012 L5.81267 5.81282 M12.2497 3.25 C12.2497 3.38807 12.1378 3.5 11.9997 3.5 C11.8616 3.5 11.7497 3.38807 11.7497 3.25 C11.7497 3.11193 11.8616 3 11.9997 3 C12.1378 3 12.2497 3.11193 12.2497 3.25 Z M12.2492 20.75 C12.2492 20.8881 12.1373 21 11.9992 21 C11.8611 21 11.7492 20.8881 11.7492 20.75 C11.7492 20.6119 11.8611 20.5 11.9992 20.5 C12.1373 20.5 12.2492 20.6119 12.2492 20.75 Z M20.75 12.25 C20.6119 12.25 20.5 12.1381 20.5 12 C20.5 11.8619 20.6119 11.75 20.75 11.75 C20.8881 11.75 21 11.8619 21 12 C21 12.1381 20.8881 12.25 20.75 12.25 Z M3.25 12.25 C3.11193 12.25 3 12.1381 3 12 C3 11.8619 3.11193 11.75 3.25 11.75 C3.38807 11.75 3.5 11.8619 3.5 12 C3.5 12.1381 3.38807 12.25 3.25 12.25 Z M18.3636 5.98937 C18.266 6.087 18.1077 6.087 18.01 5.98937 C17.9124 5.89174 17.9124 5.73345 18.01 5.63582 C18.1077 5.53819 18.266 5.53819 18.3636 5.63582 C18.4612 5.73345 18.4612 5.89174 18.3636 5.98937 Z M5.9889 18.3634 C5.89127 18.461 5.73297 18.461 5.63534 18.3634 C5.53771 18.2658 5.53771 18.1075 5.63534 18.0099 C5.73297 17.9122 5.89127 17.9122 5.9889 18.0099 C6.08653 18.1075 6.08653 18.2658 5.9889 18.3634 Z M18.0103 18.364 C17.9126 18.2663 17.9126 18.108 18.0103 18.0104 C18.1079 17.9128 18.2662 17.9128 18.3638 18.0104 C18.4614 18.108 18.4614 18.2663 18.3638 18.364 C18.2662 18.4616 18.1079 18.4616 18.0103 18.364 Z M5.6359 5.98959 C5.53827 5.89196 5.53827 5.73367 5.6359 5.63604 C5.73353 5.53841 5.89182 5.53841 5.98945 5.63604 C6.08708 5.73367 6.08708 5.89196 5.98945 5.98959 C5.89182 6.08722 5.73353 6.08722 5.6359 5.98959 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val SunCloud01: ImageVector by lazy {
        build(
            name = "SunCloud01",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M17.4776 11.0001 C17.485 11 17.4925 11 17.5 11 C19.9853 11 22 13.0147 22 15.5 C22 17.9853 19.9853 20 17.5 20 H7 C4.23858 20 2 17.7614 2 15 C2 12.4003 3.98398 10.2641 6.52042 10.0227 M17.4776 11.0001 C17.4924 10.8354 17.5 10.6686 17.5 10.5 C17.5 7.46243 15.0376 5 12 5 C9.12324 5 6.76233 7.20862 6.52042 10.0227 M17.4776 11.0001 C17.3753 12.1345 16.9286 13.1696 16.2428 14 M6.52042 10.0227 C6.67826 10.0077 6.83823 10 7 10 C8.12582 10 9.16474 10.3721 10.0005 11"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M9 5.65468 C8.30552 4.65451 7.15088 4 5.84388 4 C3.72096 4 2 5.72674 2 7.85679 C2 9.15354 2.63783 10.3008 3.61588 11"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
            )
        }
    }

    val Sunrise: ImageVector by lazy {
        build(
            name = "Sunrise",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M9.5 6.5 C9.99153 5.9943 11.2998 4 12 4 M14.5 6.5 C14.0085 5.9943 12.7002 4 12 4 M12 4 V10"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M18.3633 10.6357 L16.9491 12.05"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M3 17 H5"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M5.63657 10.6356 L7.05078 12.0498"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M21 17 H19"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M21 20 H3"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M16 17 C16 14.7909 14.2091 13 12 13 C9.79086 13 8 14.7909 8 17"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
            )
        }
    }

    val SwatchBook: ImageVector by lazy {
        build(
            name = "SwatchBook",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M3 7 V17 C3 19.2091 4.79086 21 7 21 C9.20914 21 11 19.2091 11 17 V7 C11 5.11438 11 4.17157 10.4142 3.58579 C9.82843 3 8.88562 3 7 3 C5.11438 3 4.17157 3 3.58579 3.58579 C3 4.17157 3 5.11438 3 7 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M7.5 21.0002 L17 21.0002 C18.8856 21.0002 19.8284 21.0002 20.4142 20.4144 C21 19.8286 21 18.8858 21 17.0002 C21 15.5042 21 14.6016 20.7075 14"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M9.99842 20.1396 C10.2609 19.9784 10.5089 19.7841 10.7362 19.5567 L17.8073 12.4857 C19.1406 11.1523 19.8073 10.4857 19.8073 9.65723 C19.8073 8.8288 19.1406 8.16213 17.8073 6.8288 C16.474 5.49547 15.8073 4.8288 14.9789 4.8288 C14.6439 4.8288 14.3353 4.93783 13.9984 5.15588"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M7 17 H7.00898"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val SystemUpdate01: ImageVector by lazy {
        build(
            name = "SystemUpdate01",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M21.255 7.13358 L20.7614 6.27698 C20.3881 5.62915 20.2015 5.30523 19.8839 5.17607 C19.5663 5.0469 19.2071 5.14883 18.4888 5.35267 L17.2686 5.69637 C16.81 5.80212 16.3288 5.74213 15.9101 5.52698 L15.5732 5.33261 C15.2141 5.10262 14.9379 4.76352 14.785 4.36493 L14.4511 3.36754 C14.2315 2.70753 14.1217 2.37752 13.8603 2.18876 C13.599 2 13.2518 2 12.5574 2 H11.4426 C10.7483 2 10.4011 2 10.1397 2.18876 C9.87835 2.37752 9.76856 2.70753 9.54899 3.36754 L9.21503 4.36493 C9.06215 4.76352 8.78595 5.10262 8.42687 5.33261 L8.08999 5.52698 C7.67124 5.74213 7.19009 5.80212 6.73148 5.69637 L5.51125 5.35267 C4.79291 5.14883 4.43375 5.0469 4.11617 5.17607 C3.79859 5.30523 3.61195 5.62915 3.23865 6.27698 L2.74507 7.13358 C2.39516 7.74083 2.2202 8.04446 2.25416 8.36768 C2.28811 8.6909 2.52233 8.95137 2.99076 9.47231 L4.0218 10.625 C4.2738 10.944 4.45271 11.5 4.45271 11.9998 C4.45271 12.5 4.27386 13.0558 4.02183 13.3749 L2.99076 14.5276 C2.52233 15.0485 2.28812 15.309 2.25416 15.6322 C2.2202 15.9555 2.39516 16.2591 2.74507 16.8663 L3.23864 17.7229 C3.61193 18.3707 3.79859 18.6947 4.11617 18.8238 C4.43375 18.953 4.79292 18.8511 5.51127 18.6472 L6.73144 18.3035 C7.19013 18.1977 7.67137 18.2578 8.09017 18.473 L8.427 18.6674 C8.78601 18.8974 9.06214 19.2364 9.215 19.635 L9.54899 20.6325 C9.76856 21.2925 9.87835 21.6225 10.1397 21.8112 C10.4011 22 10.7483 22 11.4426 22 H12.5574 C13.2518 22 13.599 22 13.8603 21.8112 C14.1217 21.6225 14.2315 21.2925 14.4511 20.6325 L14.7851 19.635 C14.9379 19.2364 15.214 18.8974 15.5731 18.6674 L15.9099 18.473 C16.3287 18.2578 16.8099 18.1977 17.2686 18.3035 L18.4888 18.6472 C19.2071 18.8511 19.5663 18.953 19.8839 18.8238 C20.2015 18.6947 20.3881 18.3707 20.7614 17.7229 L21.255 16.8663 C21.6049 16.2591 21.7798 15.9555 21.7459 15.6322 C21.7119 15.309 21.4777 15.0485 21.0093 14.5276 L19.9782 13.3749 C19.7262 13.0558 19.5473 12.5 19.5473 11.9998 C19.5473 11.5 19.7263 10.944 19.9782 10.625 L21.0093 9.47231 C21.4777 8.95137 21.7119 8.6909 21.7459 8.36768 C21.7798 8.04446 21.6049 7.74083 21.255 7.13358 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M14.062 11.5 L14.5621 10.9999 C15.0032 10.5588 15.2238 10.3382 15.5133 10.4297 C15.8029 10.5213 15.8486 10.7831 15.9401 11.3067 C15.9795 11.5319 16 11.7636 16 12 C16 14.2091 14.2091 16 12 16 C11.4695 16 10.9632 15.8967 10.5 15.7092 M10 12.5351 L9.45585 13.0793 C9.0216 13.5135 8.80447 13.7307 8.51776 13.6438 C8.23106 13.5569 8.17985 13.3003 8.07744 12.7872 C8.02664 12.5327 8 12.2695 8 12 C8 9.79086 9.79086 8 12 8 C12.5305 8 13.0368 8.10325 13.5 8.29076"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val Target01: ImageVector by lazy {
        build(
            name = "Target01",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M15.1312 2.5 C14.1462 2.17555 13.0936 2 12 2 C6.47715 2 2 6.47715 2 12 C2 17.5228 6.47715 22 12 22 C17.5228 22 22 17.5228 22 12 C22 10.9548 21.8396 9.94704 21.5422 9"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M17 12 C17 14.7614 14.7614 17 12 17 C9.23858 17 7 14.7614 7 12 C7 9.23858 9.23858 7 12 7"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M19.5 4.5 L12 12 M19.5 4.5 V2 M19.5 4.5 H22"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
            )
        }
    }

    val Task01: ImageVector by lazy {
        build(
            name = "Task01",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M14.4961 2.00027 H9.49609 C8.66767 2.00027 7.99609 2.67185 7.99609 3.50027 C7.99609 4.3287 8.66767 5.00027 9.49609 5.00027 H14.4961 C15.3245 5.00027 15.9961 4.3287 15.9961 3.50027 C15.9961 2.67185 15.3245 2.00027 14.4961 2.00027 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M7.99609 15.0003 H11.4247 M7.99609 11.0003 H15.9961"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M15.9961 3.50027 C17.5496 3.54709 18.4761 3.72035 19.1174 4.36164 C19.9961 5.24032 19.9961 6.65451 19.9961 9.4829 L19.9961 15.9997 C19.9961 18.8282 19.9961 20.2424 19.1174 21.1211 C18.2387 21.9997 16.8245 21.9997 13.9961 21.9997 L9.99609 21.9997 C7.16767 21.9997 5.75346 21.9997 4.87478 21.1211 C3.9961 20.2424 3.9961 18.8282 3.99609 15.9998 L3.99611 9.48296 C3.9961 6.65453 3.9961 5.24031 4.87478 4.36163 C5.51606 3.72034 6.44261 3.54708 7.99599 3.50027"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val Temperature: ImageVector by lazy {
        build(
            name = "Temperature",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M12 22 C14.7614 22 17 19.7614 17 17 C17 15.3644 16.2147 13.9122 15.0005 13 V5.00049 C15.0005 4.06815 15.0005 3.60198 14.8481 3.23428 C14.6451 2.74451 14.256 2.35537 13.7662 2.15239 C13.3985 2 12.9323 2 12 2 C11.0677 2 10.6015 2 10.2338 2.15239 C9.74402 2.35537 9.35488 2.74451 9.1519 3.23428 C8.99951 3.60198 8.99951 4.06815 8.99951 5.00049 V13 C7.78534 13.9122 7 15.3644 7 17 C7 19.7614 9.23858 22 12 22 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M12 15 C10.8954 15 10 15.8954 10 17 C10 18.1046 10.8954 19 12 19 C13.1046 19 14 18.1046 14 17 C14 15.8954 13.1046 15 12 15 Z M12 15 V8"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val TextBold: ImageVector by lazy {
        build(
            name = "TextBold",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M5 6 C5 4.58579 5 3.87868 5.43934 3.43934 C5.87868 3 6.58579 3 8 3 H12.5789 C15.0206 3 17 5.01472 17 7.5 C17 9.98528 15.0206 12 12.5789 12 H5 V6 Z"),
                pathFillType = PathFillType.EvenOdd,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M12.4286 12 H13.6667 C16.0599 12 18 14.0147 18 16.5 C18 18.9853 16.0599 21 13.6667 21 H8 C6.58579 21 5.87868 21 5.43934 20.5607 C5 20.1213 5 19.4142 5 18 V12"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val TextFont: ImageVector by lazy {
        build(
            name = "TextFont",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M14 19 L11.1069 10.7479 C9.76348 6.91597 9.09177 5 8 5 C6.90823 5 6.23652 6.91597 4.89309 10.7479 L2 19 M4.5 12 H11.5"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M21.9692 13.9392 V18.4392 M21.9692 13.9392 C22.0164 13.1161 22.0182 12.4891 21.9194 11.9773 C21.6864 10.7709 20.4258 10.0439 19.206 9.89599 C18.0385 9.75447 17.1015 10.055 16.1535 11.4363 M21.9692 13.9392 L19.1256 13.9392 C18.6887 13.9392 18.2481 13.9603 17.8272 14.0773 C15.2545 14.7925 15.4431 18.4003 18.0233 18.845 C18.3099 18.8944 18.6025 18.9156 18.8927 18.9026 C19.5703 18.8724 20.1955 18.545 20.7321 18.1301 C21.3605 17.644 21.9692 16.9655 21.9692 15.9392 V13.9392 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val TextItalic: ImageVector by lazy {
        build(
            name = "TextItalic",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M12 4 H19"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M8 20 L16 4"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M5 20 H12"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
            )
        }
    }

    val TextStrikethrough: ImageVector by lazy {
        build(
            name = "TextStrikethrough",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M4 12 H20"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M17.5 7.66667 C17.5 5.08934 15.0376 3 12 3 C8.96243 3 6.5 5.08934 6.5 7.66667 C6.5 8.15279 6.55336 8.59783 6.6668 9 M6 16.3333 C6 18.9107 8.68629 21 12 21 C15.3137 21 18 19.6667 18 16.3333 C18 13.9404 16.9693 12.5782 14.9079 12"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
            )
        }
    }

    val Tick02: ImageVector by lazy {
        build(
            name = "Tick02",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M5 14 L8.5 17.5 L19 6.5"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val Toolbox: ImageVector by lazy {
        build(
            name = "Toolbox",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M3 15 V14 H21 V15 C21 17.8284 21 19.2426 20.1213 20.1213 C19.2426 21 17.8284 21 15 21 H9 C6.17157 21 4.75736 21 3.87868 20.1213 C3 19.2426 3 17.8284 3 15 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M3 14 L3.00001 12.9999 C3.00003 11.5278 3.00004 10.7918 3.31675 10.1584 C3.63347 9.52493 4.22231 9.08329 5.39999 8.20002 C6.19297 7.60528 6.58946 7.30791 7.05132 7.15395 C7.51317 7 8.00879 7 9.00002 7 H15 C15.9912 7 16.4868 7 16.9487 7.15395 C17.4105 7.3079 17.807 7.60527 18.6 8.2 C19.7777 9.08328 20.3666 9.52492 20.6833 10.1584 C21 10.7918 21 11.5279 21 13 V14 H3 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M16 12 V16"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M8 12 V16"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M8.49997 7.00006 V6.50006 C8.49997 5.0956 8.49997 4.39337 8.83703 3.88892 C8.98295 3.67054 9.17045 3.48304 9.38883 3.33712 C9.89328 3.00006 10.5955 3.00006 12 3.00006 C13.4044 3.00006 14.1067 3.00006 14.6111 3.33712 C14.8295 3.48304 15.017 3.67054 15.1629 3.88892 C15.5 4.39337 15.5 5.0956 15.5 6.50006 V7.00006"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val Umbrella: ImageVector by lazy {
        build(
            name = "Umbrella",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M12 3.5 V2"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M12 3.5 C11.0608 3.5 7.52791 7.29323 6.97182 12.2037 M12 3.5 C12.9392 3.5 16.4721 7.29322 17.0282 12.2037 M12 3.5 C16.9367 3.5 21.0545 6.93552 22 11.5 C20.6123 12.7 18.1073 12.4691 17.0282 12.2037 M12 3.5 C7.06333 3.5 2.94545 6.93552 2 11.5 C3.38792 12.7 5.89285 12.4691 6.97182 12.2037 M6.97182 12.2037 C8.4559 13.0288 10.1718 13.5 12 13.5 C13.8282 13.5 15.5441 13.0288 17.0282 12.2037"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M12 13.5 V20.5 C12 21.3284 11.3284 22 10.5 22 C9.67157 22 9 21.3284 9 20.5 V20"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val Undo02: ImageVector by lazy {
        build(
            name = "Undo02",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M12 21 C16.9706 21 21 16.9706 21 12 C21 7.02944 16.9706 3 12 3 C8.66873 3 5.76018 4.80989 4.20404 7.5"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M3 3 V4.27816 C3 6.47004 3 7.56599 3.70725 8.16512 C4.4145 8.76425 5.49553 8.58408 7.6576 8.22373 L9 8"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val UserAccount: ImageVector by lazy {
        build(
            name = "UserAccount",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M2 12 C2 8.22876 2 6.34315 3.11299 5.17157 C4.22599 4 6.01733 4 9.6 4 L14.4 4 C17.9827 4 19.774 4 20.887 5.17157 C22 6.34315 22 8.22876 22 12 C22 15.7712 22 17.6569 20.887 18.8284 C19.774 20 17.9827 20 14.4 20 H9.6 C6.01733 20 4.22599 20 3.11299 18.8284 C2 17.6569 2 15.7712 2 12 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M12.5002 16.4998 C12.3747 14.9008 11.0691 13.6349 9.43556 13.5282 L9.00024 13.4998 C8.84474 13.5039 8.69983 13.5094 8.5631 13.516 C6.94399 13.5938 5.62457 14.9165 5.50024 16.4998"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M10.75 9.25 C10.75 10.2165 9.9665 11 9 11 C8.0335 11 7.25 10.2165 7.25 9.25 C7.25 8.2835 8.0335 7.5 9 7.5 C9.9665 7.5 10.75 8.2835 10.75 9.25 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M14.5 8.99927 L18.5 9.00001"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M14.5 12.4993 L18.5 12.5"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val Vibrate: ImageVector by lazy {
        build(
            name = "Vibrate",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M17 15 V9 C17 6.64298 17 5.46447 16.2678 4.73223 C15.5355 4 14.357 4 12 4 C9.64298 4 8.46447 4 7.73223 4.73223 C7 5.46447 7 6.64298 7 9 V15 C7 17.357 7 18.5355 7.73223 19.2678 C8.46447 20 9.64298 20 12 20 C14.357 20 15.5355 20 16.2678 19.2678 C17 18.5355 17 17.357 17 15 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M22 8 L20.3578 9.23167 C20.1325 9.40059 20 9.66569 20 9.94721 C20 10.286 20.1914 10.5957 20.4944 10.7472 L21.5528 11.2764 C21.8269 11.4134 22 11.6936 22 12 C22 12.3064 21.8269 12.5866 21.5528 12.7236 L20.4944 13.2528 C20.1914 13.4043 20 13.714 20 14.0528 C20 14.3343 20.1325 14.5994 20.3578 14.7683 L22 16"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M2 8 L3.64223 9.23167 C3.86745 9.40059 4 9.66569 4 9.94721 C4 10.286 3.80859 10.5957 3.50557 10.7472 L2.44721 11.2764 C2.17313 11.4134 2 11.6936 2 12 C2 12.3064 2.17313 12.5866 2.44721 12.7236 L3.50557 13.2528 C3.80859 13.4043 4 13.714 4 14.0528 C4 14.3343 3.86745 14.5994 3.64223 14.7683 L2 16"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val View: ImageVector by lazy {
        build(
            name = "View",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M21.544 11.045 C21.848 11.4713 22 11.6845 22 12 C22 12.3155 21.848 12.5287 21.544 12.955 C20.1779 14.8706 16.6892 19 12 19 C7.31078 19 3.8221 14.8706 2.45604 12.955 C2.15201 12.5287 2 12.3155 2 12 C2 11.6845 2.15201 11.4713 2.45604 11.045 C3.8221 9.12944 7.31078 5 12 5 C16.6892 5 20.1779 9.12944 21.544 11.045 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M15 12 C15 10.3431 13.6569 9 12 9 C10.3431 9 9 10.3431 9 12 C9 13.6569 10.3431 15 12 15 C13.6569 15 15 13.6569 15 12 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
            )
        }
    }

    val ViewOff: ImageVector by lazy {
        build(
            name = "ViewOff",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M22 8 C22 8 18 14 12 14 C6 14 2 8 2 8"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M15 13.5 L16.5 16"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M20 11 L22 13"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M2 13 L4 11"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M9 13.5 L7.5 16"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val Wallet01: ImageVector by lazy {
        build(
            name = "Wallet01",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M14 3 H5 C3.89543 3 3 3.89543 3 5 C3 6.10457 3.89543 7 5 7 H18 C18 6.07003 18 5.60504 17.8978 5.22354 C17.6204 4.18827 16.8117 3.37962 15.7765 3.10222 C15.395 3 14.93 3 14 3 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M3 5 V15 C3 17.8284 3 19.2426 3.87868 20.1213 C4.75736 21 6.17157 21 9 21 H15 C17.8284 21 19.2426 21 20.1213 20.1213 C21 19.2426 21 17.8284 21 15 V13 C21 10.1716 21 8.75736 20.1213 7.87868 C19.2426 7 17.8284 7 15 7 H7"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M21 12 H19 C18.535 12 18.3025 12 18.1118 12.0511 C17.5941 12.1898 17.1898 12.5941 17.0511 13.1118 C17 13.3025 17 13.535 17 14 C17 14.465 17 14.6975 17.0511 14.8882 C17.1898 15.4059 17.5941 15.8102 18.1118 15.9489 C18.3025 16 18.535 16 19 16 H21"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val Wallet03: ImageVector by lazy {
        build(
            name = "Wallet03",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M3 8.5 H15 C17.8284 8.5 19.2426 8.5 20.1213 9.37868 C21 10.2574 21 11.6716 21 14.5 V15.5 C21 18.3284 21 19.7426 20.1213 20.6213 C19.2426 21.5 17.8284 21.5 15 21.5 H9 C6.17157 21.5 4.75736 21.5 3.87868 20.6213 C3 19.7426 3 18.3284 3 15.5 V8.5 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M15 8.49833 V4.1103 C15 3.22096 14.279 2.5 13.3897 2.5 C13.1336 2.5 12.8812 2.56108 12.6534 2.67818 L3.7623 7.24927 C3.29424 7.48991 3 7.97203 3 8.49833"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M17.5 15.5 C17.7761 15.5 18 15.2761 18 15 C18 14.7239 17.7761 14.5 17.5 14.5 M17.5 15.5 C17.2239 15.5 17 15.2761 17 15 C17 14.7239 17.2239 14.5 17.5 14.5 M17.5 15.5 V14.5"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val WalletAdd01: ImageVector by lazy {
        build(
            name = "WalletAdd01",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M14 3 H5 C3.89543 3 3 3.89543 3 5 C3 6.10457 3.89543 7 5 7 H18 C18 6.07003 18 5.60504 17.8978 5.22354 C17.6204 4.18827 16.8117 3.37962 15.7765 3.10222 C15.395 3 14.93 3 14 3 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M12 21 H15 C17.8284 21 19.2426 21 20.1213 20.1213 C21 19.2426 21 17.8284 21 15 V13 C21 10.1716 21 8.75736 20.1213 7.87868 C19.2426 7 17.8284 7 15 7 H7 M3 12 V5"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M21 12 H19 C18.535 12 18.3025 12 18.1118 12.0511 C17.5941 12.1898 17.1898 12.5941 17.0511 13.1118 C17 13.3025 17 13.535 17 14 C17 14.465 17 14.6975 17.0511 14.8882 C17.1898 15.4059 17.5941 15.8102 18.1118 15.9489 C18.3025 16 18.535 16 19 16 H21"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M10 17.5 H6.5 M6.5 17.5 H3 M6.5 17.5 V14 M6.5 17.5 V21"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val WashingMachine: ImageVector by lazy {
        build(
            name = "WashingMachine",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M3 14 V10 C3 6.22876 3 4.34315 4.17157 3.17157 C5.34315 2 7.22876 2 11 2 H13 C16.7712 2 18.6569 2 19.8284 3.17157 C21 4.34315 21 6.22876 21 10 V14 C21 17.7712 21 19.6569 19.8284 20.8284 C18.6569 22 16.7712 22 13 22 H11 C7.22876 22 5.34315 22 4.17157 20.8284 C3 19.6569 3 17.7712 3 14 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M17 13 C17 15.7614 14.7614 18 12 18 C9.23858 18 7 15.7614 7 13 C7 10.2386 9.23858 8 12 8 C14.7614 8 17 10.2386 17 13 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M7 13 C8.66667 11.6667 10.3333 11.6667 12 13 C13.6667 14.3333 15.3333 14.3333 17 13"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M7.125 6 H7 M7.25 6 C7.25 6.13807 7.13807 6.25 7 6.25 C6.86193 6.25 6.75 6.13807 6.75 6 C6.75 5.86193 6.86193 5.75 7 5.75 C7.13807 5.75 7.25 5.86193 7.25 6 Z"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeLineMiter = 4f,
            )
        }
    }

    val Wind: ImageVector by lazy {
        build(
            name = "Wind",
            width = 24f,
            height = 24f,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ) {
            addPath(
                pathData = addPathNodes("M6 6 C6 4.34315 7.34315 3 9 3 C10.6569 3 12 4.34315 12 6 C12 7.65685 10.6569 9 9 9 H3"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M9 18 C9 19.6569 10.3431 21 12 21 C13.6569 21 15 19.6569 15 18 C15 16.3431 13.6569 15 12 15 H3"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
            )
            addPath(
                pathData = addPathNodes("M15 9 C15 7.34315 16.3431 6 18 6 C19.6569 6 21 7.34315 21 9 C21 10.6569 19.6569 12 18 12 H6.5"),
                pathFillType = PathFillType.NonZero,
                fill = null,
                fillAlpha = 1f,
                stroke = SolidColor(Color(0xFF141B34)),
                strokeAlpha = 1f,
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
            )
        }
    }
}

/**
 * 图标构建脚手架：把 AAR 里的名字 / 尺寸 / 视口原样转成 ImageVector。
 *
 * `inline` 是为了不给上百个图标各留一个闭包对象。
 */
private inline fun build(
    name: String,
    width: Float,
    height: Float,
    viewportWidth: Float,
    viewportHeight: Float,
    block: ImageVector.Builder.() -> Unit,
): ImageVector = ImageVector.Builder(
    name = name,
    defaultWidth = width.dp,
    defaultHeight = height.dp,
    viewportWidth = viewportWidth,
    viewportHeight = viewportHeight,
).apply(block).build()
