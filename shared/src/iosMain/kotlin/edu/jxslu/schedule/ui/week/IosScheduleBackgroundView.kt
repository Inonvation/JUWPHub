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
 * | [IosScheduleBackgroundLayer] | `WeekScreen.backgroundLayer` | `backgroundLayer = { m -> prefs.bgImageName?.let { IosScheduleBackgroundLayer(it, prefs.bgImageScale, prefs.bgImageBlur, m) } }` |
 * | [IosScheduleBackgroundThumb] | `DisplaySettingsContent.backgroundThumb` | `backgroundThumb = ::IosScheduleBackgroundThumb` |
 *
 * 两个槽位在没接线时是 `null`：课表页纯色底、设置面板的「背景」组不出现（§11.2 ④）。
 *
 * ## 模糊：与 Android 同一套口径，不要 `UIVisualEffectView`
 *
 * Android 的「模糊」不是真高斯模糊，而是**把图解码得小、绘制时双线性放大**
 * （`ScheduleBackground.BLUR_LONG_SIDES`：1920 → 320）。这里照做：按同一个公式算出目标长边，
 * 先用 `UIGraphicsBeginImageContextWithOptions` 把图重绘到那个尺寸，再交给 `UIImageView`
 * 放大到整屏——纹理越粗越过，观感一致，也不需要新符号 `UIVisualEffectView`。
 *
 * 代价：重绘发生在 `update` 里（主线程、每次换图一次）。一张 1920 长边的图缩到 320 是毫秒级，
 * 与 Android 侧的解码同一数量级；**不要**把它改成每帧做的事。
 *
 * ## 三处刻意的不一致（写在这里，别当 bug 报）
 *
 * 1. **`BgScale.Tile` 退化成 `Fill`**：`UIImageView` 没有平铺语义，UIKit 的对应物是
 *    `UIColor(patternImage:)`（又一个新符号，且铺法按点不按像素）。设置面板里那一档目前
 *    按「填满」呈现——比整块不画好，等有人真用平铺再补。
 * 2. **解码长边只按模糊档位算**：Android 还跟屏幕长边取小
 *    （`ScheduleBackground.targetLongSide`），iOS 侧拿屏幕尺寸要再引 `UIScreen`；而
 *    `BLUR_LONG_SIDES` 的最大值 1920 本来就没超过 iPhone 的长边，取小与否是同一个数。
 * 3. **不透明度 / 变暗遮罩 / 顶部渐变都没有**：`UIKitView` 是原生视图，Compose 的 `Box.scrim`
 *    盖不上去（层级在 UIKit 那一侧），`WeekScreen.backgroundLayer` 的签名里也没有这几个参数。
 *
 * ## 真机上按这个顺序看（本机验不了，只有 macOS CI 能编出包）
 *
 * 1. 课表页**还能不能正常显示**：`UIKitView` 的层级由 CMP 决定。如果真机上课表整页被这张图
 *    盖住，说明这一版 CMP 把 interop 视图恒定画在 Compose 内容之上——那时不要在这里打补丁，
 *    把背景层挪出 `WeekScreen`（iOS 壳的 `Column` 里、Tab 内容容器之下），层级问题只能在
 *    UIKit 那一侧解决。
 * 2. 换图后**立刻**生效（`update` 里换了 `image`），切「填充 / 适应」时 `contentMode` 跟着变。
 * 3. 背景上**点得动**：`userInteractionEnabled = false` 生效的话，点课表、滑周次都正常。
 *
 * ## 符号出处（本机是 Windows，编不了 Kotlin/Native）
 *
 * | 符号 | 出处 |
 * |---|---|
 * | `androidx.compose.ui.viewinterop.UIKitView` | `ui/web/WebViewController.ios.kt`（同包同名，CI 编译过） |
 * | `UIImage` / `UIImage(data:)` | `ui/common/PixelBitmap.ios.kt` |
 * | `NSData.dataWithContentsOfURL` + `import platform.Foundation.dataWithContentsOfURL` | `ui/platform/FilePickers.ios.kt` |
 * | `CGRectMake` | `ui/web/WebViewController.ios.kt` |
 * | `useContents` | `ui/common/PixelBitmap.ios.kt` |
 * | `UIViewContentMode.UIViewContentModeScaleAspectFill/Fit` | **新符号**。形状照 `ui/common/Haptics.ios.kt` 的 `UIImpactFeedbackStyle.UIImpactFeedbackStyleLight`（同为 UIKit 的 `NS_ENUM` → Kotlin enum，条目名保留全名前缀） |
 * | `UIImageView()` | **新符号**。无参构造，形状照 `WKWebViewConfiguration()` / `WKUserContentController()`（`WebViewController.ios.kt`） |
 * | `NSURL.fileURLWithPath` | **新符号**。`+fileURLWithPath:` 类工厂（与 `NSURL.URLWithString` 同类，不需要 import） |
 * | `UIGraphicsBeginImageContextWithOptions` / `UIGraphicsGetImageFromCurrentImageContext` / `UIGraphicsEndImageContext` / `CGSizeMake` / `UIImage.drawInRect` | **新符号**（缩图用）。CoreGraphics / UIKit 的自绘四件套，与 `ui/common/IosBitmapPng.kt` 的 `CGBitmapContextCreate` 同一族 |
 */
@Composable
fun IosScheduleBackgroundThumb(
    fileName: String,
    modifier: Modifier,
) {
    IosBackgroundImage(
        fileName = fileName,
        targetLongSide = THUMB_LONG_SIDE,
        aspectFill = true,
        modifier = modifier,
    )
}

/**
 * 课表页背景层：[IosScheduleBackgroundThumb] 的整屏版。
 *
 * [scale] 只决定 `contentMode`：`Fill`（与 `Tile`）→ `scaleAspectFill`，`Fit` → `scaleAspectFit`；
 * [blur] 是解码长边档位（见文件头第三段）；[modifier] 由 `WeekScreen` 给成整页大小。
 *
 * 用 `BgScale` 而不是 `Float`：调用方手上的就是 `DisplayPrefs.bgImageScale`，转成 Float 没有语义。
 */
@Composable
fun IosScheduleBackgroundLayer(
    fileName: String,
    scale: BgScale,
    blur: Float,
    modifier: Modifier,
) {
    IosBackgroundImage(
        fileName = fileName,
        // min(模糊档位, 屏幕长边) 在 iOS 上等于前者，见文件头「三处刻意的不一致」第 2 条
        targetLongSide = ScheduleBackground.decodeLongSide(blur),
        aspectFill = scale != BgScale.Fit,
        modifier = modifier,
    )
}

/** 设置面板缩略图的长边（px）。与 Android 的 `ThumbLongSidePx` 同值，够 56×40dp 的槽位用。 */
private const val THUMB_LONG_SIDE = 240

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
    aspectFill: Boolean,
    modifier: Modifier,
) {
    val holder = remember { IosBackgroundImageHolder() }
    UIKitView(
        factory = { holder.attach(aspectFill) },
        modifier = modifier,
        update = { view -> holder.show(view, fileName, targetLongSide, aspectFill) },
    )
}

/** 一个视图 + 一份「上次解的是哪张图」的记录。 */
private class IosBackgroundImageHolder {

    private var loadedKey: String? = null

    /**
     * 建 `UIImageView`。两件事不是可选项：
     * - `clipsToBounds = true`：`Fill` 档的图会超出边界，不裁就画到课表内容上面去了；
     * - `userInteractionEnabled = false`：interop 视图盖在 Compose 之上，可交互的话整页都点不动。
     */
    fun attach(aspectFill: Boolean): UIImageView = UIImageView().apply {
        contentMode = contentModeFor(aspectFill)
        clipsToBounds = true
        userInteractionEnabled = false
    }

    /**
     * 换图才重新解码：`update` 会在每次重组时被调用，同键重解等于每次重组都读一遍盘。
     *
     * `contentMode` 每次都重设（一次赋值，不值钱）：`factory` 只建一次视图，用户在显示设置里
     * 把「填充 / 适应」切过去时，只有这里能让它生效。
     */
    fun show(view: UIImageView, fileName: String, targetLongSide: Int, aspectFill: Boolean) {
        view.contentMode = contentModeFor(aspectFill)
        val key = "$fileName@$targetLongSide"
        if (loadedKey == key) return
        loadedKey = key
        view.image = loadBackgroundImage(fileName, targetLongSide)
    }

    private fun contentModeFor(aspectFill: Boolean): UIViewContentMode =
        if (aspectFill) {
            UIViewContentMode.UIViewContentModeScaleAspectFill
        } else {
            UIViewContentMode.UIViewContentModeScaleAspectFit
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
