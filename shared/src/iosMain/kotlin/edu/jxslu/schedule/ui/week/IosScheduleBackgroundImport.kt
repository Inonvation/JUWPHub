@file:OptIn(ExperimentalForeignApi::class)

package edu.jxslu.schedule.ui.week

import edu.jxslu.schedule.domain.AppClock
import edu.jxslu.schedule.domain.ScheduleBackground
import edu.jxslu.schedule.platform.platformFileSystem
import edu.jxslu.schedule.ui.common.IosShareHost
import edu.jxslu.schedule.ui.common.decodeBitmap
import edu.jxslu.schedule.ui.common.toByteArray
import edu.jxslu.schedule.ui.me.MeViewModel
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.Foundation.NSData
import platform.Foundation.NSURL
import platform.Foundation.dataWithContentsOfURL
// UIKit 通配的理由见 `IosScheduleBackgroundView.kt` 的 import 段：`UIImage.drawInRect` 这类
// 成员「属于类还是 category」在本机判不了，逐个 import 有一半概率是错的。
import platform.UIKit.*
import platform.UniformTypeIdentifiers.UTTypeImage
import platform.darwin.NSObject
import kotlin.coroutines.resume
import kotlin.random.Random

/**
 * 课表页背景图的**选图 + 落盘**（iOS 移植 M4，2026-11-26）。
 *
 * 对应 Android 的 `DisplaySettingsOverlay`（`PickVisualMedia` + `ScheduleBackgroundStore.importUri`）。
 * 流程与 Android 逐段对齐：
 *
 * | 段 | Android | 这里 |
 * |---|---|---|
 * | 选图 | 系统 Photo Picker（零权限） | `UIDocumentPickerViewController(forOpeningContentTypes: [UTTypeImage])` |
 * | 解码 | `BitmapFactory` | `decodeBitmap`（`UIImage(data:)`） |
 * | 长边钳制 | `Bitmap.createScaledBitmap` | [downscaledToLongSide] |
 * | 重编码 | `Bitmap.compress(JPEG, 88)` | `UIImageJPEGRepresentation`，超 `MAX_BYTES` 再降质量 |
 * | 落盘 | `filesDir/schedule_bg/` | `appDataDir()/schedule_bg/`（同一目录名，见 `IosScheduleBackgroundFiles`） |
 * | 收尾 | `vm.finishBackgroundImport(name)` | 同左 |
 *
 * ## 宿主怎么用（`MainViewController` 的 `IosDisplaySettingsOverlay`）
 *
 * ```kotlin
 * DisplaySettingsContent(
 *     viewModel = meViewModel,
 *     headerMinDp = headerMinDp,
 *     onPickBackground = { scope.launch { iosImportScheduleBackground(meViewModel) } },
 *     backgroundThumb = ::IosScheduleBackgroundThumb,
 *     // onPreviewBackground 仍传 null：iOS 没有大图预览弹窗，缩略图因此不可点
 * )
 * ```
 *
 * 三件前提，缺一个就是「点了没反应」：
 *
 * 1. **根组合里调过 `IosSharePresenter()`**：宿主 VC 从 [IosShareHost] 来（`ui/common/IosSharePresenter.kt`），
 *    没装时这里直接按失败收尾，不静默卡在「处理中」。
 * 2. `Info.plist` 不用加任何权限键：`forOpeningContentTypes: UTTypeImage` 走的是**文件选择器**
 *    （不是相册），用户选中的文件由系统按需授权。
 * 3. 沙箱外的文件必须 `startAccessingSecurityScopedResource()` 之后才读得到字节（同
 *    `ui/platform/FilePickers.ios.kt` 对文本文件的做法）。
 *
 * ## 代理为什么是「进程级强引用 + class」
 *
 * `UIDocumentPickerViewController.delegate` 是 **weak**：写 `object : NSObject(), …` 或局部变量，
 * 出了作用域就被 ObjC 的引用计数回收，回调一次都不来（本仓踩过 `Allocation of Obj-C class …`
 * 那一类崩溃）。所以代理是具名 `class`（不是 `object`，ObjC 不认 Kotlin 单例的构造时机），
 * 由文件顶部的 [activePicker] 强引用住，回调进来时先松手再交结果。
 *
 * ## 符号出处（本机是 Windows，编不了 Kotlin/Native）
 *
 * | 符号 | 出处 / 风险 |
 * |---|---|
 * | `UIDocumentPickerViewController(forOpeningContentTypes:)` / `UIDocumentPickerDelegateProtocol` 两个方法 / `presentViewController` | `ui/platform/FilePickers.ios.kt`（同形，CI 编译过） |
 * | `NSData.dataWithContentsOfURL` / `startAccessingSecurityScopedResource` / `stopAccessingSecurityScopedResource` | 同上 |
 * | `UIImage(data:)` / `NSData.toByteArray` | `ui/common/PixelBitmap.ios.kt`、`ui/common/IosBitmapPng.kt` |
 * | `decodeBitmap` | `ui/common/PixelBitmap.ios.kt` 的 actual |
 * | `UIGraphicsBeginImageContextWithOptions` 三件套 / `UIImage.drawInRect` / `CGSizeMake` | **新符号**，见 `IosScheduleBackgroundView.kt` 的同名表 |
 * | `UTTypeImage` | **新符号**。`FilePickers.ios.kt` 已用同包的 `UTTypeJSON` / `UTTypePlainText`，`iosApp/project.yml` 的 `-framework UniformTypeIdentifiers` 已在 |
 * | `UIImageJPEGRepresentation` | **新符号**。与 `ui/common/BitmapSave.ios.kt` 的 `UIImageWriteToSavedPhotosAlbum` 同为 UIKit 的 C 函数 |
 * | `UIImage.size` / `UIImage.scale` | `ui/common/PixelBitmap.ios.kt` |
 */
suspend fun iosImportScheduleBackground(vm: MeViewModel): Boolean {
    val host = IosShareHost.host()
    if (host == null) {
        // 根组合没装 presenter：按「读不出来」提示。停在「处理中」比报错更糟
        vm.finishBackgroundImport(null, failed = true)
        return false
    }
    // 面板还开着时不再叠第二个（UIKit 也不允许同一个 VC 同时 present 两个面板）
    if (activePicker != null) return false
    vm.beginBackgroundImport()
    val url = awaitPickedURL(host)
    if (url == null) {
        // 用户取消：静默收尾，与 Android 侧 `uri == null` 同一口径
        vm.finishBackgroundImport(null)
        return false
    }
    // 读盘 + 解码 + 重编码要几十毫秒，别压在主线程序列上
    val name = withContext(Dispatchers.Default) { importPickedImage(url) }
    vm.finishBackgroundImport(name, failed = name == null)
    return name != null
}

/**
 * 当前活着的那一个选图代理。**进程级**、单槽：同一时刻只可能有一个系统面板。
 *
 * 强引用的理由见文件头「代理为什么是进程级强引用」；回调回来时把它清掉，下一次选图再装新的。
 */
private var activePicker: IosBackgroundPickerDelegate? = null

/** 弹面板并等一个结果：用户选了给 URL，取消给 null（两者都不是「失败」，见调用方）。 */
private suspend fun awaitPickedURL(host: UIViewController): NSURL? =
    suspendCancellableCoroutine { continuation ->
        val delegate = IosBackgroundPickerDelegate { url ->
            // 协程已被取消（宿主退出、用户切走）时不算结果，直接丢掉
            if (continuation.isActive) continuation.resume(url)
        }
        activePicker = delegate
        val picker = UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypeImage))
        picker.delegate = delegate
        host.presentViewController(picker, animated = true, completion = null)
        continuation.invokeOnCancellation { activePicker = null }
    }

/**
 * 选图面板的代理。**必须是具名 `class`**（理由见文件头），并且只交一次结果。
 */
private class IosBackgroundPickerDelegate(
    private val onPicked: (NSURL?) -> Unit,
) : NSObject(), UIDocumentPickerDelegateProtocol {

    private var finished = false

    override fun documentPicker(
        controller: UIDocumentPickerViewController,
        didPickDocumentsAtURLs: List<*>,
    ) {
        finish(didPickDocumentsAtURLs.firstOrNull() as? NSURL)
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
        finish(null)
    }

    private fun finish(url: NSURL?) {
        if (finished) return
        finished = true
        // 先松手再交结果：回调里可能会立刻开下一次选图
        if (activePicker === this) activePicker = null
        onPicked(url)
    }
}

/**
 * 读选中的那张图 → 钳长边 → 重编码 JPEG → 写进 `schedule_bg/`，返回文件名；任一步失败回 null。
 *
 * 抛异常也归 null：调用方只有一句「这张图读不出来」，不需要区分是磁盘满还是图坏了。
 * 安全作用域在这里开、在这里关（配对释放），选择器自己会 dismiss，不补第二次。
 */
private fun importPickedImage(url: NSURL): String? {
    val scoped = url.startAccessingSecurityScopedResource()
    return try {
        runCatching { encodeAndStore(url) }.getOrNull()
    } finally {
        if (scoped) url.stopAccessingSecurityScopedResource()
    }
}

/** [importPickedImage] 的正体：读字节 → 解码 → 缩 → 编 → 写。 */
private fun encodeAndStore(url: NSURL): String? {
    val data: NSData? = NSData.dataWithContentsOfURL(url)
    if (data == null) return null
    val bytes = data.toByteArray() ?: return null
    val decoded = decodeBitmap(bytes) ?: return null
    // 长边钳到与 Android 同一个上限（1920）。已经够小的图原样返回，不重复重绘
    val scaled = decoded.nativeImage.downscaledToLongSide(ScheduleBackground.MAX_SIDE)
    val encoded = encodeJpeg(scaled) ?: return null
    val dir = scheduleBackgroundDir()
    val fs = platformFileSystem()
    fs.createDirectories(dir)
    val name = newBackgroundFileName()
    fs.write(dir / name) { write(encoded) }
    return name
}

/**
 * JPEG 编码，超 [ScheduleBackground.MAX_BYTES] 就降质量再编一次。
 *
 * 降质量而不是降尺寸：图已经按 `MAX_SIDE` 缩过，再缩会动到像素尺度；质量是唯一还能动的旋钮
 * （Android 侧是「按档位重编码」，等价的目标）。降到 [MIN_JPEG_QUALITY] 仍超限就按当前结果落盘——
 * 一张 1920 长边的图在这个质量下几乎不可能超过 1.5MB，真超了也不该让用户白选一次。
 */
private fun encodeJpeg(image: UIImage): ByteArray? {
    var quality = ScheduleBackground.JPEG_QUALITY / 100.0
    var data = UIImageJPEGRepresentation(image, quality) ?: return null
    while (data.length.toLong() > ScheduleBackground.MAX_BYTES && quality > MIN_JPEG_QUALITY) {
        quality -= JPEG_QUALITY_STEP
        data = UIImageJPEGRepresentation(image, quality) ?: return null
    }
    return data.toByteArray()
}

/** 质量下限（0.4）：再低就是肉眼可见的块状伪影，宁可按 0.4 落盘。 */
private const val MIN_JPEG_QUALITY = 0.4

/** 每次重编码降多少质量（0.88 → 0.73 → 0.58 → 0.43，四次到底）。 */
private const val JPEG_QUALITY_STEP = 0.15

/**
 * 落盘文件名，形状与 Android 的 `ScheduleBackgroundStore.newFileName` 一致：
 * `bg_yyyyMMdd_HHmmss_<4 位十六进制>.jpg`。
 *
 * 必须过 [ScheduleBackground.isValidFileName] 那道白名单（只放行字母数字与 `_-.`），
 * 所以这里不加任何别的字符；随机后缀防「同一秒里换两次图」互相覆盖。
 */
private fun newBackgroundFileName(): String {
    val now = AppClock.nowDateTime()
    val stamp = buildString {
        append(now.year)
        append(pad2(now.monthNumber))
        append(pad2(now.dayOfMonth))
        append('_')
        append(pad2(now.hour))
        append(pad2(now.minute))
        append(pad2(now.second))
    }
    val salt = Random.nextInt(0x1000, 0xFFFF).toString(16)
    return "bg_${stamp}_$salt.jpg"
}

/** 两位补零（`AppClock` 给的是整数，没有格式化器）。 */
private fun pad2(value: Int): String = if (value < 10) "0$value" else value.toString()
