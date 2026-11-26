@file:OptIn(ExperimentalForeignApi::class)

package edu.jxslu.schedule.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.interop.LocalUIViewController
import edu.jxslu.schedule.ui.common.toByteArray
import edu.jxslu.schedule.ui.toolbox.scaledToLongSide
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image
import platform.Foundation.NSData
import platform.Foundation.NSURL
import platform.Foundation.dataWithContentsOfURL
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UniformTypeIdentifiers.UTTypeImage
import platform.darwin.NSObject

/**
 * [PickedImage] / [rememberImagePicker] 的 iOS 实现（iOS 移植 V4，2026-11-26）：
 * `UIDocumentPickerViewController(forOpeningContentTypes: [UTTypeImage])` 选图，
 * skia 解码（`Image.makeFromEncoded`）。
 *
 * ## 为什么是文件选择器而不是相册
 *
 * 与 `ui/week/IosScheduleBackgroundImport.kt`（课表背景图）同一个决定：照片 App 的
 * `PHPickerViewController` 交出来的是 `PHPickerResult`（要 `loadFileRepresentation` 再等
 * 异步回调），而文件选择器给出的 `NSURL` 能直接读字节、能选「照片」里的文件、
 * 且**不需要任何相册权限**（`NSPhotoLibraryUsageDescription` 都不用加）。
 * 「读相册」这一步在 iOS 上的代价就此归零，代价是入口文案叫「文件」而不是「相册」——
 * 工具页文案写的是「选图」，两种来源都读得懂。
 *
 * ## 四个必须记住的点
 *
 * 1. **代理是 weak，必须强引用**。`UIDocumentPickerViewController.delegate` 一旦被回收，
 *    回调一次都不来（表现为「选了图什么都没发生」）。所以代理挂在 `remember` 上，
 *    同时有个进程级的 [activeImagePicker] 兜底（理由与 `IosScheduleBackgroundImport` 相同）。
 * 2. **代理必须是具名 `class`**，不能是 `object` 或局部匿名类：ObjC 侧的强引用与回收时机
 *    在匿名类上不可控（本仓踩过 `Allocation of Obj-C class …` 那一类崩溃）。
 * 3. **选中的 URL 是安全作用域的**：文件在沙箱外，不进 `startAccessingSecurityScopedResource()`
 *    直接读会拿到 nil，读完要 `stopAccessingSecurityScopedResource()` 配对释放。
 *    这里每次都重新进一次作用域（句柄可能被用户在页面里搁很久），见 [PickedImage.readBytes]。
 * 4. **`host` 只能从组合里取**：不猜 `UIApplication.keyWindow.rootViewController`
 *    （理由与 `ui/common/ShareIntents.ios.kt` 逐字相同）。
 *
 * ## 解码为什么在 iOS 上要「整幅解出再缩」
 *
 * Android 的 `BitmapFactory` 有 `inSampleSize`，采样发生在解码时（省内存）；
 * skia 的 `Image.makeFromEncoded` 没有等价开关，只能整幅解出来再按长边缩
 * （[scaledToLongSide]，双线性）。峰值内存因此比 Android 高一档——一张 12MP 照片大约
 * 48MB 的瞬时峰值。这是平台能力差异，不是漏做，记在 `design/ios-port.md`。
 *
 * ## 编译状态（本机是 Windows，编不了 Kotlin/Native）
 *
 * | 符号 | 出处 / 风险 |
 * |---|---|
 * | `UIDocumentPickerViewController(forOpeningContentTypes:)` / `UIDocumentPickerDelegateProtocol` 两个方法 / `presentViewController` | `ui/platform/FilePickers.ios.kt`（同形，CI 编译过） |
 * | `LocalUIViewController` | 同上（CMP 1.7.3 里仍在 `ui.interop`） |
 * | `allowsMultipleSelection` | **未核实**。`UIDocumentPickerViewController` 的属性，K/N 里应是 `var` |
 * | `NSData.dataWithContentsOfURL` / `startAccessingSecurityScopedResource` / `stopAccessing…` | `ui/week/IosScheduleBackgroundImport.kt`（同形） |
 * | `NSURL.absoluteString` | **未核实**。`String?`，按可空处理 |
 * | `org.jetbrains.skia.Image.makeFromEncoded` / `Image.width` / `Image.height` | **新符号**（仓里 iOS UI 层此前零出处）。`makeFromEncoded` 返回惰性解码的 Image，问宽高不解像素 |
 * | `androidx.compose.ui.graphics.toComposeImageBitmap` | **新符号**。CMP 的 skiko 侧扩展，`ImageBitmap` 由它拿到 |
 *
 * shortcut: 上面「整幅解出再缩」等真机跑过之后，如果长截图（1:7 那种）在选图时明显卡，
 * 再换 `org.jetbrains.skia.Codec` 按目标尺寸 `readPixels`（能真采样，但要新引一套 API）
 */
internal actual class PickedImage internal constructor(internal val url: NSURL) {

    // `absoluteString` 与 `description` 在 K/N 绑定里都是 `String?`（CI 实测：
    // 不兜空会报 "Return type mismatch: expected 'String', actual 'String?'"）
    actual val id: String get() = url.absoluteString ?: url.description.orEmpty()

    actual suspend fun bounds(): Pair<Int, Int>? = withContext(Dispatchers.Default) {
        val bytes = readBytes() ?: return@withContext null
        // 惰性解码：问宽高不会把整幅像素拉起来（像素要等绘制/readPixels 才解）
        runCatching { Image.makeFromEncoded(bytes) }.getOrNull()
            ?.let { it.width to it.height }
            ?.takeIf { it.first > 0 && it.second > 0 }
    }

    actual suspend fun decode(maxLongSidePx: Int): ImageBitmap? = withContext(Dispatchers.Default) {
        val bytes = readBytes() ?: return@withContext null
        val image = runCatching { Image.makeFromEncoded(bytes).toComposeImageBitmap() }.getOrNull()
            ?: return@withContext null
        image.scaledToLongSide(maxLongSidePx)
    }

    /**
     * 读原文件字节：每次都重新进安全作用域（见类 KDoc 第 3 条），读完配对释放。
     *
     * 读不出（作用域被拒、文件被删、磁盘满）回 null，不抛——调用方只按「读不出来」提示。
     */
    private fun readBytes(): ByteArray? {
        val scoped = url.startAccessingSecurityScopedResource()
        return try {
            NSData.dataWithContentsOfURL(url)?.toByteArray()
        } finally {
            if (scoped) url.stopAccessingSecurityScopedResource()
        }
    }
}

/**
 * 当前活着的那一个选图代理（**进程级**强引用兜底）。同一时刻只可能有一个系统面板。
 *
 * 正常情况下代理由 `remember` 持有；这里再挂一份是为了「组合被重建、代理在面板回调前被回收」
 * 那一档——本仓在 `IosScheduleBackgroundImport` 里已经按这个形状写过一次。
 */
private var activeImagePicker: IosImagePickerDelegate? = null

/** 选图面板的代理（具名 class 的理由见类 KDoc 第 2 条）。同一次选择只回一次结果。 */
private class IosImagePickerDelegate(
    private val onPicked: (List<NSURL>) -> Unit,
) : NSObject(), UIDocumentPickerDelegateProtocol {

    override fun documentPicker(
        controller: UIDocumentPickerViewController,
        didPickDocumentsAtURLs: List<*>,
    ) {
        // 先松手再交结果：回调里可能立刻开下一次选图
        if (activeImagePicker === this) activeImagePicker = null
        onPicked(didPickDocumentsAtURLs.mapNotNull { it as? NSURL })
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
        // 与 Android Photo Picker 的「没选就回调空列表」同口径
        if (activeImagePicker === this) activeImagePicker = null
        onPicked(emptyList())
    }
}

/**
 * 选图：返回的动作弹系统文件面板；[maxItems] > 1 时允许一次选多张。
 *
 * `UTTypeImage` 覆盖照片与截图（JPEG / PNG / HEIC / GIF 首帧）。`project.yml` 里
 * `-framework UniformTypeIdentifiers` 已经在了（`FilePickers.ios.kt` 先用到的）。
 */
@Composable
internal actual fun rememberImagePicker(
    maxItems: Int,
    onPicked: (List<PickedImage>) -> Unit,
): () -> Unit {
    val host = LocalUIViewController.current
    // 回调可能随重组换新实例，代理却只建一次：读最新的那个
    val currentOnPicked = rememberUpdatedState(onPicked)
    val delegate = remember(maxItems) {
        IosImagePickerDelegate { urls -> currentOnPicked.value(urls.map { PickedImage(it) }) }
    }
    return remember(host, delegate) {
        {
            activeImagePicker = delegate
            val picker = UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypeImage))
            // 单选（水印）时保持默认 false；多选（拼接）才打开
            picker.allowsMultipleSelection = maxItems > 1
            picker.delegate = delegate
            host.presentViewController(picker, animated = true, completion = null)
        }
    }
}
