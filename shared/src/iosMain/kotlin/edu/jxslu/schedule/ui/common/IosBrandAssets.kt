package edu.jxslu.schedule.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import edu.jxslu.schedule.platform.Logger
import org.jetbrains.skia.Image as SkiaImage
import platform.Foundation.NSBundle
import platform.Foundation.NSData
import platform.Foundation.dataWithContentsOfFile

/**
 * iOS 侧的品牌位图素材表（2026-11-27）：`iosApp/JUWPHub/BrandAssets` 下那批 png 的句柄、
 * 解码入口与解码缓存。
 *
 * ## 这张表里的 id 不是 Android 的 `R` 值
 *
 * 共享层那条缝（[BrandMark]、`OnboardingBrandIcons`、`DrawerBrandIds`、`WaterScreen(brandIcon=)`
 * …）的形参是 `Int`，因为 Android 那半边传的一直是 `R.drawable.*`。**iOS 没有 `R`**，
 * 所以在 `:shared` 的 iosMain 里另外定义一组**合成句柄**：从 `0x1A01` 起、
 * 与 Android 的 `0x7F…` 资源 id 不属于同一个空间，也不保证与任何 Android 版本里
 * `ic_brand_*` 的实际取值对应。宿主只许用这里的常量，不许自己编数字。
 *
 * 语义与 Android 侧一致：**查不到的 id 渲染为空**（[painter] 回 null），不画占位块。
 *
 * ## 为什么是 png 而不是原来的 webp
 *
 * 转换口径与命令写在报告里（一次性脚本不入库）：webp 原图逐张转 png，**像素尺寸不动**
 * （品牌图 192×192、校徽 256×256、赞赏码 1037×1037、预设 128×128）。不缩放的理由是
 * `Image.makeFromEncoded` 只能整幅解码（skia 没有 Android 的 `inSampleSize`），
 * 缩小一次只会多掉一层画质，而这几张图最大也就 1037²。
 *
 * 文件名与 Android 侧**逐字同名**（`ic_brand_kqcx.png` 对应 `R.drawable.ic_brand_kqcx`），
 * 换图时对着名字找，不用记第二套命名。
 *
 * ## bundle 里怎么找
 *
 * 工程由 `iosApp/project.yml` 生成，`sources: - path: JUWPHub` 会把目录下的 png 当资源
 * 编进 `.app`。XcodeGen 的默认行为是**逐个文件引用平铺到 bundle 根**，所以第一跳是不带目录的
 * `pathForResource(name, ofType:)`；第二跳带 `inDirectory = BrandAssets`，只在有人把这一层
 * 改成 folder reference（蓝色文件夹，资源会保留子目录）时才有用。两跳都拿不到就记一条 warn
 * 并当作「本机没有这张图」。
 *
 * ## 缓存与线程
 *
 * 解码结果按 id 常驻进程（含「解码失败」这个结论，避免每次重组重试一次 bundle 查找）。
 * 十张图合计约 350KB 源文件、解码后也就几 MB，不值得做淘汰。
 *
 * **只允许主线程调用**：唯一调用方是 Composable 的 `remember` 块，本来就在主线程；
 * 不加锁也不做成挂起函数——一次性同步解码（最大一张 1037×1037 的 png 约 10ms）换掉
 * 「先画空再补图」的闪动，比省这 10ms 更值。
 *
 * ## 符号出处（本机是 Windows，编不了 Kotlin/Native）
 *
 * | 符号 | 出处 / 风险 |
 * |---|---|
 * | `NSBundle.mainBundle` | `IosServices.kt`（取版本号那处，CI 编译过） |
 * | `NSBundle.pathForResource(name:ofType:)` / `…inDirectory:` | **新符号**。Foundation 的实例方法，K/N 里应是 `String?` 返回 |
 * | `NSData.dataWithContentsOfFile` | **新符号**，与 `IosScheduleBackgroundImport.kt` 的 `dataWithContentsOfURL` 同族（同一个 `+[NSData …]` 工厂） |
 * | `NSData.toByteArray` | `ui/common/IosBitmapPng.kt` 的 `internal` 扩展，本包内直接可用 |
 * | `org.jetbrains.skia.Image.makeFromEncoded` / `toComposeImageBitmap` | `ui/ebike/IosBikeMapTiles.kt`、`ui/platform/ImagePickers.ios.kt`（同形，CI 编译过） |
 * | `BitmapPainter` | **新符号**。`androidx.compose.ui.graphics.painter` 的公共 API，不是平台绑定 |
 *
 * shortcut: 解码现在同步发生在首帧。哪天真机上出现「进某页明显掉帧」，再改成
 * `produceState` + `Dispatchers.Default`（只动 [painter]，调用点不受影响）。
 */
internal object IosBrandAssets {

    /** 快趣出行（侧栏 / 今日页猜你想用 / 账号页头像位）。 */
    const val KQCX = 0x1A01

    /** 胖乖生活（开水）。 */
    const val PANG_GUAI = 0x1A02

    /** 趣智校园。 */
    const val QZXY = 0x1A03

    /** U净 洗衣房。 */
    const val UJING = 0x1A04

    /** 问渠（志愿服务 / 劳动教育）。 */
    const val WENQU = 0x1A05

    /** 校徽：首启引导第 2 步与完成页「学校统一认证」。 */
    const val SCHOOL_BADGE = 0x1A06

    /** 赞赏码（支持开发页）。固定 1037×1037，比例 1:1。 */
    const val REWARD_QR = 0x1A07

    /** 预设快捷方式图标：拼多多（`presetIndex == 0`）。 */
    const val PRESET_PINDUODUO = 0x1A08

    /** 预设快捷方式图标：淘宝（`presetIndex == 1`）。 */
    const val PRESET_TAOBAO = 0x1A09

    /**
     * 预设快捷方式图标：菜鸟（`presetIndex == 2`）。
     *
     * 这一张在 Android 侧是**矢量**（`drawable/ic_preset_cainiao.xml`：圆角蓝底 + 白色包裹箱，
     * 自绘、不是商标），转 png 时按那份 XML 的 `pathData` 逐值重绘后超采样降采样。
     * 之所以不在这边画 `ImageVector`：三条 path 的圆角弧要手算，肉眼验不了；
     * 重绘成位图后至少能直接看图核对。**改颜色 / 改形状时要两边一起改**。
     */
    const val PRESET_CAINIAO = 0x1A0A

    private const val TAG = "IosBrandAssets"

    /** 资源目录名：只在工程被改成 folder reference 时才落在 bundle 的子目录里。 */
    private const val SUBDIRECTORY = "BrandAssets"

    /** id → bundle 内的文件名（不带扩展名），与 Android 的 `ic_*` 同名。 */
    private val names: Map<Int, String> = mapOf(
        KQCX to "ic_brand_kqcx",
        PANG_GUAI to "ic_brand_pangguai",
        QZXY to "ic_brand_qzxy",
        UJING to "ic_brand_ujing",
        WENQU to "ic_brand_wenqu",
        SCHOOL_BADGE to "ic_school_badge",
        REWARD_QR to "ic_reward_qr",
        PRESET_PINDUODUO to "ic_preset_pinduoduo",
        PRESET_TAOBAO to "ic_preset_taobao",
        PRESET_CAINIAO to "ic_preset_cainiao",
    )

    /** 解码结果缓存；值为 null 表示「这张图在包里但解不开 / 包里没有」，同样不重试。 */
    private val decoded = HashMap<Int, ImageBitmap?>()

    /**
     * 按合成句柄取解码后的位图；表里没有这个 id、或图缺失 / 解不开都回 null。
     *
     * 调用方拿 null 就该**什么都不画**（不占位、不画底色）——与 Android 侧
     * 「`painterResource` 拿不到资源」不同，这里把「没有」当成正常的平台能力差异处理。
     */
    fun image(id: Int): ImageBitmap? {
        if (decoded.containsKey(id)) return decoded[id]
        val bitmap = decodeAsset(id)
        decoded[id] = bitmap
        return bitmap
    }

    /**
     * [image] 的组合期版本：同一个 id 在整棵组合里只解一次、只建一个 `BitmapPainter`。
     *
     * 宿主能直接在 `@Composable` 里调它（`GuessYouWantCard(brandIcon=)`、
     * `ShortcutSettingsScreen(brandIcon=)` 这类槽位要的就是 `Painter`）。
     */
    @Composable
    fun painter(id: Int): Painter? {
        val bitmap = remember(id) { image(id) } ?: return null
        return remember(bitmap) { BitmapPainter(bitmap) }
    }

    private fun decodeAsset(id: Int): ImageBitmap? {
        val name = names[id] ?: return null
        val path = bundlePath(name)
        if (path == null) {
            // 只在这一步记一条：包漏了资源时这是唯一线索，而缺图本身不该影响页面
            Logger.w(TAG, "bundle 里找不到 $name.png")
            return null
        }
        val bytes = NSData.dataWithContentsOfFile(path)?.toByteArray()
        if (bytes == null) {
            Logger.w(TAG, "读不出 $name.png")
            return null
        }
        return runCatching { SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap() }
            .onFailure { Logger.w(TAG, "解码失败：$name.png", it) }
            .getOrNull()
    }

    /** 先按平铺到 bundle 根找，再按子目录找（两跳的触发条件见对象 KDoc）。 */
    private fun bundlePath(name: String): String? {
        val bundle = NSBundle.mainBundle
        return bundle.pathForResource(name, ofType = "png")
            ?: bundle.pathForResource(name, ofType = "png", inDirectory = SUBDIRECTORY)
    }
}
