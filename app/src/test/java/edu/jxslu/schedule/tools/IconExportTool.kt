package edu.jxslu.schedule.tools

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorNode
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.graphics.vector.addPathNodes
import org.junit.Assert.fail
import org.junit.Assume
import org.junit.Test
import java.io.File
import java.math.BigDecimal

/**
 * 把 HugeIcons（Android-only AAR）用到的图标导出成一份纯 Compose Multiplatform 的
 * `ImageVector` 源码（`shared/src/commonMain/.../ui/icon/AppIcons.kt`）。
 *
 * ## 为什么要有它
 *
 * `com.github.rikkahub:hugeicons-compose:1.4` 是 JitPack 上的 **AAR**，只有 Android 变体，
 * iOS（Kotlin/Native）上没有对应产物；而图标本身只是 `ImageVector` 数据，
 * `androidx.compose.ui.graphics.vector.*` 在 CMP 的 `commonMain` 里都有。
 * 所以这里把 AAR 里的矢量数据搬成源码，让 `:shared` 的 `commonMain` 能直接编译。
 *
 * ## 什么时候跑
 *
 * 默认**不跑**：没有 `-DexportIcons=true` 时 `Assume.assumeTrue` 直接跳过整个用例，
 * 所以 `:app:testDebugUnitTest` 全量跑时它既不执行、也不改任何文件。
 *
 * 重跑（在仓库根）：
 * ```
 * .\gradlew.bat :app:testDebugUnitTest --tests "*IconExportTool*" -DexportIcons=true
 * ```
 *
 * 注意：`-D` 交给的是 Gradle 自己的 JVM，测试跑在 fork 出来的 JVM 里默认看不到它；
 * `app/build.gradle.kts` 末尾有一行把 `exportIcons` 透传给 Test 任务，
 * 删掉那行这条命令会静默变成「跳过」，不会报错。
 *
 * ## 图标清单从哪来
 *
 * 不写死。扫调用点：`app/src/main` 与 `shared/src` 下全部 .kt 里的 `AppIcons.Xxx`
 * （调用点尚未迁移时也认 `HugeIcons.Xxx`；两种前缀都没有才报错），图标名一律 PascalCase。
 * 取并集按字母序排，所以输出稳定、可 diff。
 *
 * ## 反射入口的真实形状（javap 逐类核过，不是猜的）
 *
 * - 类：`me.rerere.hugeicons.stroke.<IconName>Kt`
 * - 图标是**定义在 `HugeIcons` 单例对象上的扩展属性**，JVM getter 不是无参静态方法：
 *   `public static final ImageVector get<IconName>(me.rerere.hugeicons.HugeIcons)`
 * - `HugeIcons` 是 Kotlin `object`（`INSTANCE` 静态字段），反射时作为唯一实参传入
 *
 * 这两个类只通过 `Class.forName` 按名字找，源码里没有 import，所以 AAR 只要在
 * **测试**运行时 classpath 上就够（`app/build.gradle.kts` 里是 `testImplementation`）。
 */
class IconExportTool {

    @Test
    fun exportIcons() {
        Assume.assumeTrue(
            "未设置 -DexportIcons=true，跳过图标导出（日常单测不该改仓库文件）",
            System.getProperty(SWITCH) == "true",
        )

        val root = repoRoot()
        val names = scanIconNames(root)
        if (names.isEmpty()) bail("没扫到任何图标用法（AppIcons.Xxx / HugeIcons.Xxx）")

        val outFile = File(root, OUT_REL)
        val body = StringBuilder()
        var totalPaths = 0
        var totalGroups = 0

        names.forEachIndexed { index, name ->
            val vector = reflectIcon(name)
            if (vector.autoMirror) {
                bail("图标 $name 的 autoMirror=true，生成器没接这个参数，需要先给 build() 补上")
            }
            val emitted = emitIcon(vector, name)
            totalPaths += emitted.paths
            totalGroups += emitted.groups

            body.append("    val ").append(name).append(": ImageVector by lazy {\n")
            body.append("        build(\n")
            body.append("            name = ").append(kotlinString(name)).append(",\n")
            body.append("            width = ").append(lit(vector.defaultWidth.value)).append(",\n")
            body.append("            height = ").append(lit(vector.defaultHeight.value)).append(",\n")
            body.append("            viewportWidth = ").append(lit(vector.viewportWidth)).append(",\n")
            body.append("            viewportHeight = ").append(lit(vector.viewportHeight)).append(",\n")
            body.append("        ) {\n")
            body.append(emitted.text)
            body.append("        }\n")
            body.append("    }\n")
            if (index != names.lastIndex) body.append("\n")
        }

        val content = buildString {
            append(FILE_KDOC)
            append("\npackage ").append(PACKAGE).append("\n")
            append(IMPORTS)
            append("\n\nobject AppIcons {\n\n")
            append(body)
            append("}\n\n")
            append(BUILD_HELPER)
        }

        val previous = if (outFile.isFile) outFile.readText() else null
        if (previous != content) {
            outFile.parentFile?.mkdirs()
            outFile.writeText(content, Charsets.UTF_8)
        }

        println(
            "图标导出完成：${names.size} 个图标 / ${totalPaths} 条 path / ${totalGroups} 个嵌套 group" +
                " → ${outFile.absolutePath}（${content.length} 字符，" +
                if (previous == content) "内容未变，未动文件）" else "已写入）",
        )
    }

    // ---------------------------------------------------------------- 反射

    private fun reflectIcon(name: String): ImageVector {
        val holder = Class.forName("$ICON_PACKAGE.${name}Kt")
        val getter = try {
            holder.getMethod("get$name", HUGE_ICONS_CLASS)
        } catch (e: NoSuchMethodException) {
            bail(
                "图标 $name 反射不到 get$name(${HUGE_ICONS_CLASS.name})；该类声明的静态方法有：" +
                    holder.declaredMethods.joinToString { it.name } +
                    "（用 javap -cp classes.jar $ICON_PACKAGE.${name}Kt 复核）",
            )
        }
        val value = getter.invoke(null, hugeIconsInstance)
        return value as? ImageVector
            ?: bail("图标 $name 的 get$name 返回 ${value?.let { it::class.java.name }}，不是 ImageVector")
    }

    private val hugeIconsInstance: Any by lazy {
        // `me.rerere.hugeicons.HugeIcons` 是 Kotlin object，JVM 侧就一个 INSTANCE 静态字段
        HUGE_ICONS_CLASS.getField("INSTANCE").get(null) as Any
    }

    // ---------------------------------------------------------------- 序列化

    private class Emitted(val text: String, val paths: Int, val groups: Int)

    private fun emitIcon(vector: ImageVector, iconName: String): Emitted {
        val rootGroup = vector.root
        // ImageVector 只有一个根 group，根上要是带变换/剪切，生成代码没地方放（宁可失败也不静默丢数据）
        val rootIsDefault = rootGroup.rotation == 0f &&
            rootGroup.pivotX == 0f && rootGroup.pivotY == 0f &&
            rootGroup.scaleX == 1f && rootGroup.scaleY == 1f &&
            rootGroup.translationX == 0f && rootGroup.translationY == 0f &&
            rootGroup.clipPathData.isEmpty()
        if (!rootIsDefault) {
            bail(
                "图标 $iconName 的根 group 不是默认变换（rotation=${rootGroup.rotation}, " +
                    "pivotX=${rootGroup.pivotX}, pivotY=${rootGroup.pivotY}, " +
                    "scaleX=${rootGroup.scaleX}, scaleY=${rootGroup.scaleY}, " +
                    "translationX=${rootGroup.translationX}, translationY=${rootGroup.translationY}, " +
                    "clipPathData.size=${rootGroup.clipPathData.size}），生成器无处承载",
            )
        }
        val sb = StringBuilder()
        // 缩进层级 3 = `object` 里的 `build {` 里面的内容（4 空格一级）
        val (paths, groups) = emitNodes(rootGroup, sb, 3, iconName)
        return Emitted(sb.toString(), paths, groups)
    }

    private fun emitNodes(
        nodes: Iterable<VectorNode>,
        sb: StringBuilder,
        indent: Int,
        iconName: String,
    ): Pair<Int, Int> {
        var paths = 0
        var groups = 0
        for (node in nodes) {
            when (node) {
                is VectorGroup -> {
                    groups++
                    emitGroupOpen(node, sb, indent)
                    val child = emitNodes(node, sb, indent + 1, iconName)
                    paths += child.first
                    groups += child.second
                    sb.append(pad(indent)).append("clearGroup()\n")
                }

                is VectorPath -> {
                    paths++
                    emitPath(node, sb, indent, iconName)
                }
            }
        }
        return paths to groups
    }

    private fun emitGroupOpen(group: VectorGroup, sb: StringBuilder, indent: Int) {
        val p = pad(indent)
        val p1 = pad(indent + 1)
        sb.append(p).append("addGroup(\n")
        if (group.name.isNotBlank()) {
            sb.append(p1).append("name = ").append(kotlinString(group.name)).append(",\n")
        }
        sb.append(p1).append("rotation = ").append(lit(group.rotation)).append(",\n")
        sb.append(p1).append("pivotX = ").append(lit(group.pivotX)).append(",\n")
        sb.append(p1).append("pivotY = ").append(lit(group.pivotY)).append(",\n")
        sb.append(p1).append("scaleX = ").append(lit(group.scaleX)).append(",\n")
        sb.append(p1).append("scaleY = ").append(lit(group.scaleY)).append(",\n")
        sb.append(p1).append("translationX = ").append(lit(group.translationX)).append(",\n")
        sb.append(p1).append("translationY = ").append(lit(group.translationY)).append(",\n")
        if (group.clipPathData.isNotEmpty()) {
            val data = encodePathData(group.clipPathData)
            verifyPathData(data, group.clipPathData, "clipPathData")
            sb.append(p1).append("clipPathData = addPathNodes(").append(kotlinString(data)).append("),\n")
        }
        sb.append(p).append(")\n")
    }

    private fun emitPath(path: VectorPath, sb: StringBuilder, indent: Int, iconName: String) {
        val p = pad(indent)
        val p1 = pad(indent + 1)
        val data = encodePathData(path.pathData)
        verifyPathData(data, path.pathData, iconName)

        sb.append(p).append("addPath(\n")
        sb.append(p1).append("pathData = addPathNodes(").append(kotlinString(data)).append("),\n")
        if (path.name.isNotBlank()) {
            sb.append(p1).append("name = ").append(kotlinString(path.name)).append(",\n")
        }
        sb.append(p1).append("pathFillType = ").append(fillTypeExpr(path.pathFillType)).append(",\n")
        sb.append(p1).append("fill = ").append(brushExpr(path.fill, iconName, "fill")).append(",\n")
        sb.append(p1).append("fillAlpha = ").append(lit(path.fillAlpha)).append(",\n")
        sb.append(p1).append("stroke = ").append(brushExpr(path.stroke, iconName, "stroke")).append(",\n")
        sb.append(p1).append("strokeAlpha = ").append(lit(path.strokeAlpha)).append(",\n")
        sb.append(p1).append("strokeLineWidth = ").append(lit(path.strokeLineWidth)).append(",\n")
        sb.append(p1).append("strokeLineCap = ").append(capExpr(path.strokeLineCap)).append(",\n")
        sb.append(p1).append("strokeLineJoin = ").append(joinExpr(path.strokeLineJoin)).append(",\n")
        sb.append(p1).append("strokeLineMiter = ").append(lit(path.strokeLineMiter)).append(",\n")
        // trimPath 三件套默认是 0 / 1 / 0，只有非默认值才输出，避免给每个图标塞无效行
        if (path.trimPathStart != 0f) {
            sb.append(p1).append("trimPathStart = ").append(lit(path.trimPathStart)).append(",\n")
        }
        if (path.trimPathEnd != 1f) {
            sb.append(p1).append("trimPathEnd = ").append(lit(path.trimPathEnd)).append(",\n")
        }
        if (path.trimPathOffset != 0f) {
            sb.append(p1).append("trimPathOffset = ").append(lit(path.trimPathOffset)).append(",\n")
        }
        sb.append(p).append(")\n")
    }

    // ---------------------------------------------------------------- path 字符串

    /**
     * `List<PathNode>` → SVG 风格路径字符串（`M12 2 L22 12 Z` 这种）。
     *
     * 用字符串而不是逐节点调 `PathBuilder.moveTo/curveTo`：一个图标几百个节点，
     * 逐行 DSL 要写几万行；压成字符串后每个 path 只占一行，生成的 `AppIcons.kt` 才可读、可 diff。
     */
    private fun encodePathData(nodes: List<PathNode>): String =
        nodes.joinToString(" ") { node ->
            val (cmd, args) = describe(node)
            if (args.isEmpty()) cmd else cmd + args.joinToString(" ")
        }

    private fun describe(node: PathNode): Pair<String, List<String>> = when (node) {
        is PathNode.MoveTo -> "M" to listOf(num(node.x), num(node.y))
        is PathNode.RelativeMoveTo -> "m" to listOf(num(node.dx), num(node.dy))
        is PathNode.LineTo -> "L" to listOf(num(node.x), num(node.y))
        is PathNode.RelativeLineTo -> "l" to listOf(num(node.dx), num(node.dy))
        is PathNode.HorizontalTo -> "H" to listOf(num(node.x))
        is PathNode.RelativeHorizontalTo -> "h" to listOf(num(node.dx))
        is PathNode.VerticalTo -> "V" to listOf(num(node.y))
        is PathNode.RelativeVerticalTo -> "v" to listOf(num(node.dy))
        is PathNode.CurveTo -> "C" to listOf(
            num(node.x1), num(node.y1), num(node.x2), num(node.y2), num(node.x3), num(node.y3),
        )
        is PathNode.RelativeCurveTo -> "c" to listOf(
            num(node.dx1), num(node.dy1), num(node.dx2), num(node.dy2), num(node.dx3), num(node.dy3),
        )
        is PathNode.ReflectiveCurveTo -> "S" to listOf(
            num(node.x1), num(node.y1), num(node.x2), num(node.y2),
        )
        is PathNode.RelativeReflectiveCurveTo -> "s" to listOf(
            num(node.dx1), num(node.dy1), num(node.dx2), num(node.dy2),
        )
        is PathNode.QuadTo -> "Q" to listOf(num(node.x1), num(node.y1), num(node.x2), num(node.y2))
        is PathNode.RelativeQuadTo -> "q" to listOf(
            num(node.dx1), num(node.dy1), num(node.dx2), num(node.dy2),
        )
        is PathNode.ReflectiveQuadTo -> "T" to listOf(num(node.x), num(node.y))
        is PathNode.RelativeReflectiveQuadTo -> "t" to listOf(num(node.dx), num(node.dy))
        // SVG 的 A 是「rx ry x-axis-rotation large-arc-flag sweep-flag x y」
        is PathNode.ArcTo -> "A" to listOf(
            num(node.horizontalEllipseRadius), num(node.verticalEllipseRadius), num(node.theta),
            arcFlag(node.isMoreThanHalf), arcFlag(node.isPositiveArc),
            num(node.arcStartX), num(node.arcStartY),
        )
        is PathNode.RelativeArcTo -> "a" to listOf(
            num(node.horizontalEllipseRadius), num(node.verticalEllipseRadius), num(node.theta),
            arcFlag(node.isMoreThanHalf), arcFlag(node.isPositiveArc),
            num(node.arcStartDx), num(node.arcStartDy),
        )
        is PathNode.Close -> "Z" to emptyList()
    }

    private fun arcFlag(value: Boolean): String = if (value) "1" else "0"

    /**
     * 自检：生成的字符串用 `addPathNodes` 解析回来，必须和原始节点**逐个、按序**完全相同。
     *
     * 这是唯一能挡住「格式化悄悄丢精度」的闸口——数字格式、指令大小写、参数顺序任何一处出错，
     * 都会在这里炸掉并打出图标名，而不是等界面上少一条笔画才发现。
     */
    private fun verifyPathData(data: String, original: List<PathNode>, owner: String) {
        val reparsed = addPathNodes(data)
        if (reparsed.size != original.size) {
            bail("$owner 路径自检失败：节点数 原始=${original.size} 回读=${reparsed.size}\n  data=$data")
        }
        for (i in original.indices) {
            if (reparsed[i].toString() != original[i].toString()) {
                bail("$owner 路径自检失败：第 $i 个节点 原始=${original[i]} 回读=${reparsed[i]}\n  data=$data")
            }
        }
    }

    // ---------------------------------------------------------------- 字面量

    /** path 字符串里的数字：尽量短，但不许出现科学计数法（解析器对 `1.0E-4` 的处理没保证）。 */
    private fun num(v: Float): String {
        if (v.isNaN() || v.isInfinite()) bail("路径里出现非法浮点值 $v")
        val s = v.toString()
        val plain = if (s.contains('E') || s.contains('e')) {
            BigDecimal(v.toDouble()).toPlainString()
        } else {
            s
        }
        // "12.0" → "12"；但不动 "-0.0"（-0 和 0 回读后 toString 不同，会被自检抓住）
        return if (plain.endsWith(".0") && !plain.startsWith("-")) plain.dropLast(2) else plain
    }

    /** Kotlin Float 字面量。 */
    private fun lit(v: Float): String {
        if (v.isNaN() || v.isInfinite()) bail("出现非法浮点值 $v")
        val asLong = v.toLong()
        return if (v == asLong.toFloat()) "${asLong}f" else "${v}f"
    }

    private fun kotlinString(s: String): String {
        val sb = StringBuilder("\"")
        for (c in s) {
            when (c) {
                '\\' -> sb.append("\\\\")
                '"' -> sb.append("\\\"")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '$' -> sb.append("\\\$")
                else -> sb.append(c)
            }
        }
        return sb.append('"').toString()
    }

    private fun fillTypeExpr(v: PathFillType): String =
        if (v == PathFillType.EvenOdd) "PathFillType.EvenOdd" else "PathFillType.NonZero"

    private fun capExpr(v: StrokeCap): String = when {
        v == StrokeCap.Round -> "StrokeCap.Round"
        v == StrokeCap.Square -> "StrokeCap.Square"
        else -> "StrokeCap.Butt"
    }

    private fun joinExpr(v: StrokeJoin): String = when {
        v == StrokeJoin.Round -> "StrokeJoin.Round"
        v == StrokeJoin.Bevel -> "StrokeJoin.Bevel"
        else -> "StrokeJoin.Miter"
    }

    private fun brushExpr(brush: Brush?, iconName: String, slot: String): String = when (brush) {
        null -> "null"
        is SolidColor -> {
            val hex = brush.value.toArgb().toUInt().toString(16).uppercase().padStart(8, '0')
            "SolidColor(Color(0x$hex))"
        }
        // 换成渐变笔刷时宁可停下来，也不要生成一个能编、实际变色/变空的图标
        else -> bail("图标 $iconName 的 $slot 是 ${brush::class.java.name}，生成器只支持 SolidColor 与 null")
    }

    private fun pad(indent: Int): String = "    ".repeat(indent)

    // ---------------------------------------------------------------- 扫描与定位

    private fun repoRoot(): File {
        var dir = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (true) {
            if (File(dir, "settings.gradle.kts").isFile) return dir
            dir = dir.parentFile ?: break
        }
        bail("从 user.dir=${System.getProperty("user.dir")} 往上找不到 settings.gradle.kts，无法定位仓库根")
    }

    private fun scanIconNames(root: File): List<String> {
        val found = sortedSetOf<String>()
        val outFile = File(root, OUT_REL).absoluteFile
        for (rel in SCAN_DIRS) {
            val dir = File(root, rel)
            if (!dir.isDirectory) continue
            dir.walkTopDown()
                .filter { it.isFile && it.extension == "kt" && it.absoluteFile != outFile }
                .forEach { file ->
                    val text = file.readText()
                    for (regex in USAGE_REGEXES) {
                        for (m in regex.findAll(text)) {
                            val name = m.groupValues[1]
                            // 图标名一律 PascalCase；这条同时挡掉注释里「AppIcons.kt」这类噪声
                            if (name.first().isUpperCase()) found.add(name)
                        }
                    }
                }
        }
        return found.toList()
    }

    /** `org.junit.Assert.fail` 返回 Unit，不能放在表达式位置；包一层拿到 Nothing。 */
    private fun bail(message: String): Nothing {
        fail(message)
        throw AssertionError(message)
    }

    private companion object {
        const val SWITCH = "exportIcons"
        const val PACKAGE = "edu.jxslu.schedule.ui.icon"
        const val OUT_REL = "shared/src/commonMain/kotlin/edu/jxslu/schedule/ui/icon/AppIcons.kt"
        const val ICON_PACKAGE = "me.rerere.hugeicons.stroke"

        val HUGE_ICONS_CLASS: Class<*> = Class.forName("me.rerere.hugeicons.HugeIcons")

        val SCAN_DIRS = listOf("app/src/main", "shared/src")

        // 调用点已全量迁移到 AppIcons.*；仍留着 HugeIcons.* 是为了未来某个分支还没迁移时也能跑，
        // 两者都没有才算失败（由 exportIcons 里的 names.isEmpty() 判断，不在这里报错）。
        val USAGE_REGEXES = listOf(
            Regex("""AppIcons\.([A-Za-z0-9_]+)"""),
            Regex("""HugeIcons\.([A-Za-z0-9_]+)"""),
        )
    }
}

/** 生成的 `AppIcons.kt` 的 import 段（内容固定，与实际用到的符号一一对应）。 */
private val IMPORTS = """
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp
""".trimEnd()

/** 生成的 `AppIcons.kt` 的文件头 KDoc。 */
private val FILE_KDOC = """
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
""".trimStart()

/** 生成的 `AppIcons.kt` 末尾的构建脚手架。 */
private val BUILD_HELPER = """
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
""".trimStart()
