@file:OptIn(ExperimentalForeignApi::class)

package edu.jxslu.schedule.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.interop.LocalUIViewController
import edu.jxslu.schedule.platform.appDataDir
import edu.jxslu.schedule.platform.platformFileSystem
import kotlinx.cinterop.ExperimentalForeignApi
import okio.Path.Companion.toPath
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfURL
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UniformTypeIdentifiers.UTTypeJSON
import platform.UniformTypeIdentifiers.UTTypePlainText
import platform.darwin.NSObject

/**
 * [rememberTextFilePicker] / [rememberFileSaver] 的 iOS 实现（iOS 移植 S8，2026-11-25）。
 *
 * ## 选文件：`UIDocumentPickerViewController(forOpeningContentTypes:)`
 *
 * 用 `forOpeningContentTypes`（**打开**），不是 `forImportingContentTypes`（导入副本）：
 * 配合 `Info.plist` 的 `LSSupportsOpeningDocumentsInPlace`，用户选中的仍是「文件」App 里那份原件，
 * 我们只读一次文本，不产生副本。
 *
 * 三个容易踩的点，都不是可选项：
 *
 * 1. **代理必须被强引用**。`UIDocumentPickerViewController.delegate` 是 **weak**；
 *    `object : NSObject(), UIDocumentPickerDelegateProtocol` 一旦离开局部作用域就被 ObjC 的
 *    引用计数回收，随后回调永远不来（表现为「点了文件什么都没发生」，控制台一句
 *    `doesn't respond to`）。所以代理挂在 `remember` 上，和这一屏同寿。
 * 2. **`present` 的宿主 VC 只能从组合里取**。不猜 `UIApplication.keyWindow.rootViewController`
 *    （多场景之后语义不成立，理由与 `ui/common/ShareIntents.ios.kt` 逐字相同）。
 * 3. **选择器的 URL 是安全作用域的**。文件在沙箱外（「文件」App 的 iCloud/本地/下载），
 *    不进 `startAccessingSecurityScopedResource()` 直接读会拿到 `nil`；读完要
 *    `stopAccessingSecurityScopedResource()` 配对释放。选择器自己会 dismiss，这里不补第二次。
 *
 * ## 另存：写进沙箱的 `exports/`，不弹导出面板
 *
 * **不接 `UIDocumentPickerViewController(forExporting:)`**：那要多一个代理对象、多一条
 * `present` 路径，而本机没有 macOS、验不了任何 UI 行为；换来的只是「用户能选存到哪个目录」。
 * 写进 App 自己的目录 + `Info.plist` 的 `UIFileSharingEnabled` / `LSSupportsOpeningDocumentsInPlace`，
 * 用户在「文件」App →「我的 iPhone」→「JUWPHub」里就能看到、能拷到别处，能力覆盖了课表导出这一步。
 *
 * 落点是 `appDataDir()/exports/`，`appDataDir` 在 iOS 上实测解析成 `Documents/`
 * （见 `platform/AppDirs.ios.kt`），正好是「文件」App 能看见的那一层。目录先 `createDirectories`，
 * 再整份覆盖写 UTF-8（导出的 CSV/JSON 是整份重新生成的，没有追加语义）。
 *
 * ## Apple 目标符号清单：哪些没在本机核实
 *
 * 本机是 Windows，Kotlin/Native 编不了（要 macOS + Xcode），所以下面这些**全部是纸面推导**，
 * 只有第 1 条除外。写法上刻意保持直线（不抽 helper、不套泛型），就是为了真机上出错时一眼能改。
 *
 * | 符号 | 用途 | 核实状态 / 不确定点 |
 * |---|---|---|
 * | `androidx.compose.ui.interop.LocalUIViewController` | 取能 present 的宿主 VC | **未核实**。CMP 1.8.0 把它挪到了 `androidx.compose.ui.uikit`；本仓锁 1.7.3，按迁移说明仍在 `ui.interop`。若 macOS 上报 unresolved，只需改这一行 import |
 * | `UIDocumentPickerViewController(forOpeningContentTypes = …)` | 打开文件面板 | **未核实**。ObjC `initForOpeningContentTypes:` 的 K/N 构造器名与参数标签 |
 * | `UIDocumentPickerDelegateProtocol` 的两个方法 | `didPickDocumentsAtURLs:` / `documentPickerWasCancelled:` | **未核实**。两者都是 `@optional`，K/N 给默认实现，只覆盖这两个；是否还需覆盖已废弃的 `didPickDocumentAtURL:` 未知 |
 * | `UTTypeJSON` / `UTTypePlainText` | 允许的文件类型 | **未核实**。`platform.UniformTypeIdentifiers` 绑定 + iOS 14 起可用（本工程部署目标 15.0）；`iosApp/project.yml` 因此补了 `-framework UniformTypeIdentifiers`，静态 framework 不自带 load command |
 * | `NSURL.startAccessingSecurityScopedResource()` / `stopAccessing…` | 沙箱外文件的读取授权 | **未核实**。方法名与返回值（Boolean）形状 |
 * | `NSData.dataWithContentsOfURL(url)` | 读整份字节 | **未核实**。`+dataWithContentsOfURL:` 是 NSData 的**分类**成员，K/N 里是扩展函数，必须 `import platform.Foundation.dataWithContentsOfURL` 才可见（踩过：`NSData.create` 同款） |
 * | `NSString.create(data = …, encoding = NSUTF8StringEncoding)` | 字节 → 文本 | **未核实**。分类工厂要 `import platform.Foundation.create`；返回 `NSString?`，要 `as String?` 桥到 Kotlin 字符串；个别 K/N 版本对这类工厂要求 `@OptIn(BetaInteropApi::class)` |
 * | `presentViewController(…, animated = …, completion = null)` | 弹出面板 | 形状与 `ShareIntents.ios.kt` 同一调用一致，**仍未编译核实** |
 * | `platformFileSystem().createDirectories/write` | 落 `exports/` | 与 `data/local/JsonFileStore.kt` 同一组 okio 调用，写法上已被 commonMain 的代码证实可行；iOS 侧执行仍**未核实** |
 *
 * 失败时的表现（真机上第一眼要能认出来）：面板不弹 = VC 取不到或 `delegate` 被回收；
 * 面板弹了但选了文件没反应 = 代理方法名对不上；回调给 `null` 但文件明明能打开 = 安全作用域没开。
 *
 * shortcut: 另存只落沙箱 exports/，等有人要选目录时再接 forExporting
 */
@Composable
actual fun rememberTextFilePicker(onPicked: (String?) -> Unit): () -> Unit {
    val host = LocalUIViewController.current
    // 回调可能随重组换新实例，代理却只建一次：读最新的那个。
    val currentOnPicked = rememberUpdatedState(onPicked)

    // 代理必须被强引用住（delegate 是 weak），所以它挂在 remember 上，与这一屏同寿。
    val delegate = remember {
        object : NSObject(), UIDocumentPickerDelegateProtocol {
            override fun documentPicker(
                controller: UIDocumentPickerViewController,
                didPickDocumentsAtURLs: List<*>,
            ) {
                currentOnPicked.value(readUtf8Text(didPickDocumentsAtURLs.firstOrNull() as? NSURL))
            }

            override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
                // 用户取消：与原 Android 口径一致，给 null
                currentOnPicked.value(null)
            }
        }
    }

    return remember(host, delegate) {
        {
            val picker = UIDocumentPickerViewController(
                forOpeningContentTypes = listOf(UTTypeJSON, UTTypePlainText),
            )
            picker.delegate = delegate
            host.presentViewController(picker, animated = true, completion = null)
        }
    }
}

@Composable
actual fun rememberFileSaver(
    mimeType: String,
    onSaved: (Boolean) -> Unit,
): (fileName: String, text: String) -> Unit {
    // mimeType 丢弃：iOS 按扩展名认类型，写进自己的目录也不经过系统类型协商。
    // 保留参数是因为它与 Android 侧的落地方式绑在一起（那边建 launcher 就要 mime）。
    val currentOnSaved = rememberUpdatedState(onSaved)
    return remember {
        { fileName, text ->
            currentOnSaved.value(writeExport(fileName, text))
        }
    }
}

/**
 * 读出选中文件的 UTF-8 文本；读不出来回 `null`（调用方只有一句「读取文件失败」）。
 *
 * 主线程调用：选择器回调本来就在主线程，导出的都是几百 KB 以内的文本，不值得为它起线程。
 */
private fun readUtf8Text(url: NSURL?): String? {
    if (url == null) return null
    // 沙箱外的文件必须先进安全作用域，否则 dataWithContentsOfURL 直接回 null
    val scoped = url.startAccessingSecurityScopedResource()
    try {
        val data = NSData.dataWithContentsOfURL(url) ?: return null
        return NSString.create(data = data, encoding = NSUTF8StringEncoding) as String?
    } finally {
        if (scoped) url.stopAccessingSecurityScopedResource()
    }
}

/**
 * 把 [text] 写成 `<appDataDir>/exports/<fileName>`；成功回 true。
 *
 * 失败（目录建不了、磁盘满、文件名非法）一律回 false，不抛：调用方只按「未保存」提示。
 */
private fun writeExport(fileName: String, text: String): Boolean = runCatching {
    val exportsDir = (appDataDir() + "/exports").toPath()
    val fs = platformFileSystem()
    fs.createDirectories(exportsDir)
    // 整份覆盖写：导出的 CSV/JSON 每次都是重新生成的，没有追加语义
    fs.write(exportsDir / fileName) { writeUtf8(text) }
    true
}.getOrDefault(false)
