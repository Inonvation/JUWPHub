@file:OptIn(ExperimentalForeignApi::class)

package edu.jxslu.schedule.ui.week

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import edu.jxslu.schedule.domain.BgScale
import edu.jxslu.schedule.domain.ScheduleBackground
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
import platform.Foundation.NSURL
import platform.Foundation.dataWithContentsOfURL
// UIKit 用**通配**而不是逐个 import（本仓其余文件都是逐个，这里是唯一例外）：
// `UIImage.drawInRect` / `UIViewContentMode` 这些在本机（Windows，无 Kotlin/Native）判不出
// 是「类成员」还是「category 成员」——后者在 K/N 里是扩展函数，必须显式 import
//（仓里的先例：`ShareIntents.ios.kt` 导了 `platform.UIKit.popoverPresentationController`），
// 前者则**不能** import（会 unresolved）。两种写法都有 50% 白挂一轮 CI 的风险，
// 通配一次覆盖两种情况。等 CI 绿了可以照实收窄成逐个 import。
import platform.UIKit.*

/**
 * 课表页背景图的**画**（iOS 移植 M4，2026-11-26）：`UIKitView` + `UIImageView`。
 *
 * ## 为什么绕开 `ImageBitmap`
 *
 * CMP 1.7.3 的 iOS 变体上 `ImageBitmap` **只有读入口**（`readPixels` / `toPixelMap` / `drawImage*`），
 * 从字节构造的 `decodeToImageBitmap()` 是 1.8+ 才有，`org.jetbrains.skia.*` 全仓零出处
 * （证据见 `design/ios-port.md` §12 末段）。所以 iOS 不造位图，直接把原图交给 UIKit 的
 * `UIImageView`——`UIKitView` 是 CMP 现成的缝，用法照 `ui/web/WebViewController.ios.kt`（CI 编译过）。
 *
 * ## 两个 composable 分别对哪个槽位
 *
 * | 函数 | 槽位 | 宿主怎么传 |
 * |---|---|---|
 * | [IosScheduleBackgroundLayer] | `WeekScreen.backgroundLayer` | `IosScheduleBackgroundLayer(it, prefs.bgImageScale, prefs.bgImageBlur, prefs.bgImageOpacity, prefs.bgImageDim, m)` |
 * | [IosScheduleBackgroundThumb] | `DisplaySettingsContent.backgroundThumb` | `backgroundThumb = ::IosScheduleBackgroundThumb` |
 *
 * 两个槽位在没接线时是 `null`：课表页纯色底、设置面板的「背景」组不出现（§11.2 ④）。
 *
 * ## 不透明度 / 压暗：只能下沉到 UIKit
 *
 * `UIKitView` 是原生视图，画在 Compose 内容**之上**，Compose 的 `Box.scrim` / `Modifier.alpha`
 * 盖不到这张图（层级在 UIKit 那一侧）。所以这两根滑块由原生层做：
 *
 * - 不透明度 → `UIImageView.alpha = 不透明度`；
 * - 压暗 → 一个纯黑 `UIView` 子视图、`alpha = 压暗`，铺在图上（子视图天然盖在 `image` 之上，
 *   且被 `clipsToBounds` 裁到视图内）。
 *
 * **与 Android 不是逐像素等价**：Android 是「不透明图 + 页面底色 scrim + 黑遮罩」，
 * 即 `img*op*(1-dim) + black*dim + B*(1-op)*(1-dim)`；iOS 的 `alpha` 是整体合成，
 * 得到 `img*op*(1-dim) + black*dim*op + B*(1-op)`（B = 页面底色，由 UIKit 下方的 Compose 提供）。
 * 只有 `op < 1` 且 `dim > 0` 时差一点（黑遮罩跟着整体一起变淡）。要逐像素一致得在读图时
 * 把压暗烘进像素，而那要按页面底色 B 算——B 在 UIKit 侧拿不到，不做。
 *
 * ## 平铺（`BgScale.Tile`）用 `UIColor(patternImage:)` 当底色
 *
 * `UIImageView` 没有平铺语义，UIKit 的对应物就是 pattern 色。选它而不是「自己往一张大图上
 * 逐块重绘」的理由：
 *
 * 1. 只需要**一处**新 API（`UIColor(patternImage:)`），没有自绘的双层循环（Android 那边是
 *    `Canvas` 里的 `while` 嵌套，照抄一遍就是两份要各自维护的铺法）；
 * 2. pattern 色天然铺满 `bounds`、随 `clipsToBounds` 裁掉，不需要算网格、不需要画布大小。
 *
 * 实现上就是「`image = null` + `backgroundColor = pattern 色`」：同一个 `UIImageView` 换一种
 * 呈现方式，切回填充/适应时把 `backgroundColor` 清空即可（不用第二个视图）。
 *
 * **残留差异**：pattern 按**点**铺，而我们的图经 [UIImage.downscaledToLongSide] 后 `scale = 1`
 * （点 = 像素），于是 3× 屏上「一块」在物理像素上比 Android 的像素铺法大 3 倍（重复次数更少）。
 * 要一致得用屏幕 `scale` 重定 `image.scale`（`UIScreen` / `UIImage(cgImage:scale:)` 又是一批
 * 未验证符号）。代码里那条 `// shortcut:` 记着升级条件。
 *
 * ## 剩下的一处刻意不一致
 *
 * **顶部渐变 scrim 没有**：Android 在状态栏高度铺一层「底色 → 透明」的渐变托顶栏文字；
 * iOS 这边要拿状态栏高度（Compose 侧）再画一层 CoreGraphics 渐变，与背景图本身的观感无关，
 * 这一轮不做。状态栏图标的深浅仍由系统按主题定。
 *
 * ## 解码长边只按模糊档位算
 *
 * Android 还跟屏幕长边取小（`ScheduleBackground.targetLongSide`），iOS 侧拿屏幕尺寸要再引
 * `UIScreen`；而 `BLUR_LONG_SIDES` 的最大值 1920 本来就没超过 iPhone 的长边，取小与否是同一个数。
 * 平铺档位在 Android 那边也是只按模糊档位算（不跟屏幕取小），这里天然一致。
 *
 * ## 真机上按这个顺序看（本机验不了，只有 macOS CI 能编出包）
 *
 * 1. 课表页**还能不能正常显示**：`UIKitView` 的层级由 CMP 决定。如果真机上课表整页被这张图
 *    盖住，说明这一版 CMP 把 interop 视图恒定画在 Compose 内容之上——那时不要在这里打补丁，
 *    把背景层挪出 `WeekScreen`（iOS 壳的 `Column` 里、Tab 内容容器之下），层级问题只能在
 *    UIKit 那一侧解决。
 * 2. 换图后**立刻**生效（`update` 里换了 `image`），切「填充 / 适应」时 `contentMode` 跟着变，
 *    切「平铺」时看到重复的方块。
 * 3. **拖不透明度**：整张图连着压暗一起变淡，露出页面底色；**拖压暗**：图变暗、网格文字不受影响
 *    （压暗层在原生视图内，网格是 Compose 内容，画在它之上）。
 * 4. 背景上**点得动**：`userInteractionEnabled = false` 生效的话，点课表、滑周次都正常。
 *
 * ## 符号出处（本机是 Windows，编不了 Kotlin/Native）
 *
 * | 符号 | 出处 / 风险 |
 * |---|---|
 * | `androidx.compose.ui.viewinterop.UIKitView` | `ui/web/WebViewController.ios.kt`（同包同名，CI 编译过） |
 * | `UIImage` / `UIImage(data:)` | `ui/common/PixelBitmap.ios.kt` |
 * | `NSData.dataWithContentsOfURL` + `import platform.Foundation.dataWithContentsOfURL` | `ui/platform/FilePickers.ios.kt` |
 * | `CGRectMake` / `CGSizeMake` | `ui/web/WebViewController.ios.kt`、`ui/common/ShareIntents.ios.kt` |
 * | `useContents` | `ui/common/PixelBitmap.ios.kt` |
 * | `UIViewContentMode.UIViewContentModeScaleAspectFill/Fit` | 新符号，形状照 `ui/common/Haptics.ios.kt` 的 `UIImpactFeedbackStyle.UIImpactFeedbackStyleLight`（NS_ENUM → Kotlin enum，条目保留全名前缀）；本文件上一版已写，CI 编译过 |
 * | `UIImageView()` / `UIView(frame = …)` | 无参构造同 `WKWebViewConfiguration()`；`frame =` 构造同 `WKWebView(frame = CGRectMake(0,0,0,0), configuration = …)`（`ui/web/WebViewController.ios.kt`） |
 * | `NSURL.fileURLWithPath` | `+fileURLWithPath:` 类工厂（与 `NSURL.URLWithString` 同类，不需要 import） |
 * | `UIGraphicsBeginImageContextWithOptions` / `UIGraphicsGetImageFromCurrentImageContext` / `UIGraphicsEndImageContext` / `UIImage.drawInRect` | 缩图用（CoreGraphics / UIKit 自绘四件套），本文件上一版已写 |
 * | `UIView.alpha` / `UIView.backgroundColor` / `UIView.addSubview` | **未验证**。UIKit 最基础三个属性/方法，K/N 映射应是 `alpha: Double`、`backgroundColor: UIColor?`、`addSubview(view:)`；本仓 iosMain 里没有任何先例 |
 * | `UIColor.blackColor` / `UIColor(patternImage = …)` | **未验证**。前者是类属性（形状同 `UTTypeImage`），后者是 `+[UIColor colorWithPatternImage:]` 工厂方法 → 构造器（形状同 `UIImage(data:)` ← `+[UIImage imageWithData:]`） |
 */
@Composable
fun IosScheduleBackgroundThumb(
    fileName: String,
    modifier: Modifier,
) {
    IosBackgroundImage(
        fileName = fileName,
        targetLongSide = THUMB_LONG_SIDE,
        scale = BgScale.Fill,
        // 缩略图没有滑块：原样不打折
        opacity = 1f,
        dim = 0f,
        modifier = modifier,
    )
}

/**
 * 课表页背景层：[IosScheduleBackgroundThumb] 的整屏版。
 *
 * [scale] 决定呈现方式：`Fill` → `scaleAspectFill`，`Fit` → `scaleAspectFit`，
 * `Tile` → 平铺（见文件头「平铺」段）；[blur] 是解码长边档位；[opacity] / [dim] 是
 * 「不透明度」与「压暗」两根滑块，由 UIKit 层执行（见文件头第二段）。
 * [modifier] 由 `WeekScreen` 给成整页大小。
 *
 * 用 `BgScale` 而不是 `Float`：调用方手上的就是 `DisplayPrefs.bgImageScale`，转成 Float 没有语义。
 *
 * [opacity] / [dim] 有默认值（1 / 0 = 与没有这两项时逐像素相同），是因为调用点在 iOS 壳
 * （`MainViewController`）里，而 `WeekScreen` 的背景层槽位只给一个 `Modifier`——把这两个值
 * 递进来需要宿主多传两个 `WeekUiState` 字段。默认值让「没接线」不改变观感，接上就生效。
 */
@Composable
fun IosScheduleBackgroundLayer(
    fileName: String,
    scale: BgScale,
    blur: Float,
    opacity: Float = 1f,
    dim: Float = 0f,
    modifier: Modifier,
) {
    IosBackgroundImage(
        fileName = fileName,
        // 解码长边只按模糊档位算，见文件头「解码长边只按模糊档位算」段
        targetLongSide = ScheduleBackground.decodeLongSide(blur),
        scale = scale,
        opacity = opacity,
        dim = dim,
        modifier = modifier,
    )
}

/** 设置面板缩略图的长边（px）。与 Android 的 `ThumbLongSidePx` 同值，够 56×40dp 的槽位用。 */
private const val THUMB_LONG_SIDE = 240

/**
 * 压暗层初始边长（pt）。压暗层是 `UIImageView` 的子视图，尺寸不靠 layout 算：
 * `factory` 建视图时 `bounds` 还是零，而 `update` 不保证在 Compose 首次布局之后**再跑一次**，
 * 按 `bounds` 设 frame 就会得到一个高 0 的遮罩（「拖压暗没反应」又回来了）。
 * 给一个比任何 iPhone / iPad 屏幕都大的方框，让父视图的 `clipsToBounds` 裁到实际大小。
 */
private const val DIM_VIEW_EXTENT = 4096.0

/**
 * 两个 composable 的共同实现：一个 `UIImageView` + 「文件名 / 目标长边变了才重新解图」。
 *
 * 视图与缓存都挂在 `remember` 上，与这一屏同寿；`UIKitView` 的 `update` 在视图创建后也会跑一次，
 * 所以不需要额外的 `LaunchedEffect`（那反而会和 factory 抢时序，同 `WebContentView` 的注释）。
 */
@Composable
private fun IosBackgroundImage(
    fileName: String,
    targetLongSide: Int,
    scale: BgScale,
    opacity: Float,
    dim: Float,
    modifier: Modifier,
) {
    val holder = remember { IosBackgroundImageHolder() }
    UIKitView(
        factory = { holder.attach() },
        modifier = modifier,
        update = { view -> holder.show(view, fileName, targetLongSide, scale, opacity, dim) },
    )
}

/** 一个视图 + 它的压暗子视图 + 「上次解的是哪张图 / 上次摆成哪一档」两份记录。 */
private class IosBackgroundImageHolder {

    /** 上次解图的键（`文件名@目标长边`）；同键不重读盘。 */
    private var loadedKey: String? = null

    /** 上次解出来的图（含「解不出来」= null）；换图/换档位时重建。 */
    private var loadedImage: UIImage? = null

    /** 上次摆上去的呈现方式（`档位|解图键`）；同键不重建 pattern 色。 */
    private var presentedKey: String? = null

    /** 压暗层（纯黑，`alpha` = 压暗值）。与 [attach] 建出来的视图同寿，[show] 只改 `alpha`。 */
    private var dimView: UIView? = null

    /**
     * 建 `UIImageView`。三件事不是可选项：
     * - `clipsToBounds = true`：`Fill` 档的图会超出边界，不裁就画到课表内容上面去了；
     *   压暗层用的也是这条裁剪（见 [DIM_VIEW_EXTENT]）；
     * - `userInteractionEnabled = false`：interop 视图盖在 Compose 之上，可交互的话整页都点不动；
     * - 压暗层在最开始就是 0 透明度：没拖过「压暗」时与没有这层时一致。
     */
    fun attach(): UIImageView {
        val view = UIImageView()
        view.contentMode = UIViewContentMode.UIViewContentModeScaleAspectFill
        view.clipsToBounds = true
        view.userInteractionEnabled = false
        val overlay = UIView(frame = CGRectMake(0.0, 0.0, DIM_VIEW_EXTENT, DIM_VIEW_EXTENT))
        overlay.backgroundColor = UIColor.blackColor
        overlay.userInteractionEnabled = false
        overlay.alpha = 0.0
        view.addSubview(overlay)
        dimView = overlay
        return view
    }

    /**
     * 应用一帧的参数。
     *
     * 分两层缓存，粒度不同：
     * - **每帧都做**：`alpha` / 压暗 / `contentMode`，都是一次赋值；
     * - **换图、换模糊档位才解图**：`update` 在每次重组时被调用，同键重解等于每次重组都读一遍盘；
     * - **呈现方式（`image` / pattern 底色）按「图 + 档位」重设**：切「填充 ↔ 平铺」时图和键都没变，
     *   不单独比这一档就会出现「切了平铺还是拉伸」（2026-11-26 复核）。
     *
     * 平铺只有 `UIColor(patternImage:)` 一条路（见文件头），每次重建都要 new 一个 `UIColor`，
     * 所以这一块也必须走键判断，不能每帧做。
     */
    fun show(
        view: UIImageView,
        fileName: String,
        targetLongSide: Int,
        scale: BgScale,
        opacity: Float,
        dim: Float,
    ) {
        applyScale(view, scale)
        // `UIView.alpha` 是 CGFloat（K/N 上是 Double）
        view.alpha = opacity.toDouble().coerceIn(0.0, 1.0)
        dimView?.alpha = dim.toDouble().coerceIn(0.0, 1.0)

        val decodeKey = "$fileName@$targetLongSide"
        if (loadedKey != decodeKey) {
            loadedKey = decodeKey
            loadedImage = loadBackgroundImage(fileName, targetLongSide)
            presentedKey = null
        }
        val presentationKey = "${scale.name}|$decodeKey"
        if (presentedKey == presentationKey) return
        presentedKey = presentationKey
        applyPresentation(view, loadedImage, scale)
    }

    /**
     * 图上/底色二选一。
     *
     * 平铺：`image` 置空、pattern 色当底色（见文件头「平铺」段）。
     * 其余档位必须把 `backgroundColor` 清空，否则 `Fit` 的留白处会露出上一次的平铺。
     */
    private fun applyPresentation(view: UIImageView, image: UIImage?, scale: BgScale) {
        if (scale == BgScale.Tile && image != null) {
            // shortcut: pattern 按点铺（本图 scale=1 → 3× 屏上比 Android 像素铺法大 3 倍），
            // 等有人用平铺并报「一块太大」时按屏幕 scale 重建 UIImage
            view.image = null
            view.backgroundColor = UIColor(patternImage = image)
        } else {
            view.backgroundColor = null
            view.image = image
        }
    }

    /**
     * `Fill` 与 `Tile` 都是 `scaleAspectFill`（`Tile` 下 `image` 为空、`contentMode` 不起作用，
     * 取同一档只是省一个分支）；`Fit` 才是不裁切。
     */
    private fun applyScale(view: UIImageView, scale: BgScale) {
        view.contentMode = when (scale) {
            BgScale.Fit -> UIViewContentMode.UIViewContentModeScaleAspectFit
            BgScale.Fill, BgScale.Tile -> UIViewContentMode.UIViewContentModeScaleAspectFill
        }
    }
}

/**
 * 从 `appDataDir()/schedule_bg/` 读一张背景图并按长边缩到 [targetLongSide]。
 *
 * 文件不在、名字非法、解不出来一律回 null（视图停在「没有图」，页面露出主题底色）——
 * 与 Android 侧 `loadScheduleBackground` 同一失败口径，不抛。
 */
private fun loadBackgroundImage(fileName: String, targetLongSide: Int): UIImage? {
    val path = scheduleBackgroundPath(fileName) ?: return null
    // 显式可空类型 + 判空：`+fileURLWithPath:` 的可空性在不同 K/N 版本上写法不同，
    // 这样写两种都编得过（同 `WebViewController.ios.kt` 对 NSURL.URLWithString 的处理）
    val url: NSURL? = NSURL.fileURLWithPath(path.toString())
    if (url == null) return null
    val data: NSData? = NSData.dataWithContentsOfURL(url)
    if (data == null) return null
    val image: UIImage = UIImage(data = data) ?: return null
    return image.downscaledToLongSide(targetLongSide)
}

/**
 * 把图按长边缩到 [maxSide] px（只缩不放：已经够小就原样返回**同一个实例**）。
 *
 * 走 CoreGraphics 的位图上下文重绘，不用 `UIGraphicsImageRenderer`（那是另一套 block 式 API，
 * 少了它对这张图没有任何影响）。`opaque = false` 与 Android 的 `createScaledBitmap` 同口径：
 * 透明区保持透明，交给 `UIImageView` 的底色去补。
 *
 * `UIImage` 的 `size` 是**点**不是像素，所以先乘 `scale`（与 `PixelBitmap.ios.kt` 同一换取法）。
 * 重绘出来的图 `scale` 恒为 1.0（上下文按 1.0 建），所以 **1 点 = 1 像素**——平铺的「一块大小」
 * 就由这条决定，见文件头「平铺」段的残留差异。
 */
internal fun UIImage.downscaledToLongSide(maxSide: Int): UIImage {
    val pointSize = size.useContents { width to height }
    val imageScale = scale
    val pixelWidth = pointSize.first * imageScale
    val pixelHeight = pointSize.second * imageScale
    val longSide = maxOf(pixelWidth, pixelHeight)
    if (longSide <= 0.0 || longSide <= maxSide.toDouble()) return this
    val ratio = maxSide.toDouble() / longSide
    val targetWidth = (pixelWidth * ratio).coerceAtLeast(1.0)
    val targetHeight = (pixelHeight * ratio).coerceAtLeast(1.0)
    UIGraphicsBeginImageContextWithOptions(CGSizeMake(targetWidth, targetHeight), false, 1.0)
    try {
        // `drawInRect` 会把 EXIF 方向烘进像素（UIImage 自己带着 imageOrientation），
        // 与 Android 侧「先转正再缩」得到同一个结果，这里因此不需要单独读 EXIF
        drawInRect(CGRectMake(0.0, 0.0, targetWidth, targetHeight))
        return UIGraphicsGetImageFromCurrentImageContext() ?: this
    } finally {
        UIGraphicsEndImageContext()
    }
}
