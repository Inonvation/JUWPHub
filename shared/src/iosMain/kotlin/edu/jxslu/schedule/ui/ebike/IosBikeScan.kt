@file:OptIn(ExperimentalForeignApi::class)

package edu.jxslu.schedule.ui.ebike

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.interop.LocalUIViewController
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCAction
import kotlinx.cinterop.useContents
import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVAuthorizationStatusNotDetermined
import platform.AVFoundation.AVCaptureConnection
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceInput
import platform.AVFoundation.AVCaptureMetadataOutput
import platform.AVFoundation.AVCaptureMetadataOutputObjectsDelegateProtocol
import platform.AVFoundation.AVCaptureOutput
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureVideoPreviewLayer
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.AVMetadataMachineReadableCodeObject
import platform.AVFoundation.AVMetadataObjectTypeCode128Code
import platform.AVFoundation.AVMetadataObjectTypeCode39Code
import platform.AVFoundation.AVMetadataObjectTypeEAN13Code
import platform.AVFoundation.AVMetadataObjectTypeEAN8Code
import platform.AVFoundation.AVMetadataObjectTypeITF14Code
import platform.AVFoundation.AVMetadataObjectTypeQRCode
import platform.AVFoundation.authorizationStatusForMediaType
import platform.AVFoundation.requestAccessForMediaType
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSSelectorFromString
import platform.Foundation.NSURL
import platform.UIKit.UIAlertAction
import platform.UIKit.UIAlertActionStyleDefault
import platform.UIKit.UIAlertController
import platform.UIKit.UIAlertControllerStyleAlert
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.UIKit.UIButton
import platform.UIKit.UIColor
import platform.UIKit.UIControlEventTouchUpInside
import platform.UIKit.UIControlStateNormal
import platform.UIKit.UIModalPresentationFullScreen
import platform.UIKit.UIView
import platform.UIKit.UIViewController
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_global_queue
import platform.darwin.dispatch_get_main_queue
import platform.posix.QOS_CLASS_USER_INITIATED

/**
 * iOS 的扫码取景页（AVFoundation），2026-11-26。
 *
 * ## 它填的是哪个洞
 *
 * Android 用 zxing 的 `EbikeScanActivity`（`:app/ui/ebike/`）当取景页，共享层只认一个函数类型：
 *
 * ```kotlin
 * (onResult: (String?) -> Unit) -> Unit   // 拉起取景页；扫到回调原文，取消回调 null
 * ```
 *
 * 三个槽位都是这个形状：`RideScreen.onScanBodyCode`（快趣「扫车身码」）、
 * `RideScreen` 里 U净 那条、今日页 `TodayScreen.onScanCode`（顶栏「+ → 扫一扫」，
 * 判据在 `domain/QuickScan.kt`）。iOS 侧此前全部传 `null`，所以那几个入口整块不渲染
 * （`design/ios-port.md` §11.2 ④）。本文件给出这个函数类型，**接线本身不在本文件**：
 * 主控只要 `onScanBodyCode = rememberIosBikeScanner()` 一行。
 *
 * ## 为什么是「Kotlin 版 UIViewController + 不引 Swift」
 *
 * 取景页必须是**一整个屏**，而不是嵌在 Compose 树里的一小块。塞进 `UIKitView` 要处理
 * 布局、层级与 Compose 的裁剪，比这二十来行累得多；而且 `RideScreen` 的槽位语义就是
 * 「拉起一页 → 拿一个结果」，modal 才是同一件事。用 Kotlin 子类而不是加 Swift 文件，
 * 是因为 Swift 一侧还要过 `UIViewControllerRepresentable` 与框架导出面，而这一页
 * 没有任何 Swift 表达力上的需求。
 *
 * | 阶段 | 用什么 | 线程 |
 * |---|---|---|
 * | 拉页面 | `presentViewController`（宿主 VC 由 `LocalUIViewController` 提供） | 主 |
 * | 读授权 / 首次请求 | `AVCaptureDevice.authorizationStatusForMediaType` / `requestAccessForMediaType` | 主（请求回调在系统队列，收到后切回主） |
 * | 建会话（输入 / 输出 / 预览层） | `AVCaptureSession` + `AVCaptureDeviceInput` + `AVCaptureMetadataOutput` + `AVCaptureVideoPreviewLayer` | 主 |
 * | 起 / 停流 | `startRunning` / `stopRunning` | **后台**（`dispatch_async` + 全局队列） |
 * | 识别回调 | `AVCaptureMetadataOutputObjectsDelegateProtocol.captureOutput` | 主（挂代理时指定的主队列） |
 * | 收尾 | `stopRunning` → 移除输入输出 → dismiss | 停流在后台，dismiss 在主 |
 *
 * `startRunning` / `stopRunning` **必须是后台队列**：两者都是同步阻塞调用，
 * 真机上各占 100–300 ms，在主线程调会把 present / dismiss 的转场卡成一顿。
 *
 * ## 权限：三种状态，被拒要说话
 *
 * `AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo)` 只有三条路可走：
 *
 * | 状态 | 做什么 |
 * |---|---|
 * | `Authorized` | 直接建会话 |
 * | `NotDetermined` | `requestAccessForMediaType` 请求一次（系统框一辈子只弹这一次）；回调里再判 `granted` |
 * | `Denied` / `Restricted` | 弹 `UIAlertController`：「没有相机权限」+「去设置」/「取消」，关掉后回调 `null` |
 *
 * **`Info.plist` 的 `NSCameraUsageDescription` 不是可选项**：缺了它不是「请求返回拒绝」，
 * 而是请求那一刻**进程被系统终止**——现象是点「扫车身码」App 直接没了，栈顶看不到我们的代码
 * （与相册 / 日历两条用途描述同一类事故，见 `iosApp/JUWPHub/Info.plist` 里的注释）。
 *
 * 静默失败在这里是明令禁止的：没有授权就没有画面，用户只会以为「这个按钮坏了」。
 *
 * ## 识别到第一枚就收工
 *
 * `metadataObjectTypes` 一次挂六种码（二维码 + Code128 / Code39 / EAN13 / EAN8 / ITF14）。
 * 一帧里可能同时有多枚（画面对着两张码）：只取 `firstOrNull`，且收口在 [IosBikeScanViewController.finishWithResult]
 * 的 `finished` 标志上——后续帧、取消键、系统收起挤在一起时，回调也只发一次。
 *
 * **码型常量名带 `Code` 后缀**（`AVMetadataObjectTypeCode128Code` 之类），不是 `...TypeCode128`；
 * 少挂一种不会报错，只是那种码永远扫不出来。
 *
 * ## 生命周期：别让相机在后台常亮
 *
 * `viewDidDisappear` 里调 [IosBikeScanViewController.stopCapture]，且幂等（`tornDown`）。
 * 三条收场路径（取消键 / 识别到 / 系统回收）都会经过它：先 `stopRunning`，再
 * `removeInput` / `removeOutput`，最后把手里的引用置空。**会话本身不必手动释放**——
 * 它挂在 VC 上，VC 被 dismiss 之后跟着一起释放，相机的指示灯也随之灭。
 *
 * 后台队列是**并发**队列，`startRunning` 与 `stopRunning` 之间没有顺序保证，所以起流那一块
 * 在 `startRunning` 返回后再查一次 `tornDown`：用户在半秒内点取消时，刚起来的会话会立刻停下。
 * `tornDown` 是普通 `var`（跨线程读），最坏情况只是这次复查读到旧值；上面的「会话随 VC 释放」
 * 兜住了真正的后果。
 *
 * ## 真机上失败时第一眼看到什么
 *
 * | 现象 | 先看哪里 |
 * |---|---|
 * | 点了没反应，页面没出现 | 宿主 VC 取不到（`LocalUIViewController` 那条缝），或 present 时报了异常 |
 * | 页面出来了，但一片黑、没有画面 | 会话没起来（`canAddInput` 过不去：相机被别的 App 占着 / 模拟器没有摄像头） |
 * | 有画面，但怎么对都不出结果 | 代理被回收了（`metadataDelegate` 没挂住）、或码型里没有你那张码的类型 |
 * | 弹「没有相机权限」，可系统设置里明明是开的 | `Info.plist` 的 `NSCameraUsageDescription` 缺失或改过（系统会重新问） |
 * | 关掉页面后相机指示灯还亮着 | `stopRunning` 没走到——看 `viewDidDisappear` 有没有被调用 |
 *
 * ## 符号清单（本机是 Windows，Kotlin/Native 编不了；除注明外**全部未核实**）
 *
 * 写法刻意保持直线（不抽平台层 helper、不套泛型），macOS 上真报错时一眼能改。
 *
 * | 符号 | 用途 | 核实状态 / 不确定点 |
 * |---|---|---|
 * | `androidx.compose.ui.interop.LocalUIViewController` | 取宿主 VC（不猜 `keyWindow`） | 仓内已用，CI 编过（`ui/platform/FilePickers.ios.kt`、`ui/common/IosSharePresenter.kt`）；本文件同款。CMP 1.8 会挪到 `ui.uikit`，本仓锁 1.7.3 |
 * | `AVCaptureSession()` / `addInput` / `removeInput` / `addOutput` / `removeOutput` / `canAddInput` / `canAddOutput` / `startRunning` / `stopRunning` | 会话本体 | **未核实**。`canAddInput`、`removeInput` 是本仓首次用到；社区样例只覆盖 `canAddOutput` + `addInput`/`addOutput` |
 * | `AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo)` | 读授权 | **未核实**。返回 `AVAuthorizationStatus`；`AVAuthorizationStatus*` 按**顶层常量**写（三份社区样例一致，本仓 `EKAuthorizationStatus*` 也是这个形态且 CI 编过）。若 macOS 报 Unresolved，改成 `AVAuthorizationStatus.AVAuthorizationStatusNotDetermined` 之类 |
 * | `AVCaptureDevice.requestAccessForMediaType(mediaType) { granted -> }` | 首次请求授权 | **未核实**。若报 Unresolved，改写成 `AVCaptureDevice.Companion.requestAccessForMediaType(...)`（两种写法社区样例都有） |
 * | `AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo)` | 取默认摄像头 | **未核实**。社区样例走的是 `devicesWithMediaType(...)` 再筛 `position`；本文件取系统默认设备（后置） |
 * | `AVCaptureDeviceInput.deviceInputWithDevice(device, null)` | 设备 → 输入 | **未核实**。类工厂 `+deviceInputWithDevice:error:`，`error` 传 `null`（同 `EKEventStore.saveEvent` 的取舍）；返回可空，用 `?:` 收 |
 * | `AVCaptureMetadataOutput.setMetadataObjectsDelegate(delegate, queue)` | 挂识别代理 | **未核实**。形参名在社区样例里是 `objectsDelegate` / `queue`，这里用**位置参数**，不依赖命名 |
 * | `AVCaptureMetadataOutputObjectsDelegateProtocol.captureOutput(output, didOutputMetadataObjects, fromConnection)` | 识别回调 | **未核实**。签名与社区样例逐字相同；`List<*>` 是非空（与本仓 `UIDocumentPickerDelegateProtocol` 里的 `List<*>` 同一形态） |
 * | `AVMetadataMachineReadableCodeObject.stringValue` / `type` | 取码文本 | **未核实**。`stringValue` 可空 `String?` |
 * | `metadataObjectTypes = listOf(AVMetadataObjectTypeQRCode, …)` | 挂六种码型 | **未核实**。常量在 `platform.AVFoundation`，是字符串常量（`AVMetadataObjectType` = `String`） |
 * | `AVCaptureVideoPreviewLayer(session = session)` / `videoGravity` / `frame` | 取景预览 | **未核实**。`AVLayerVideoGravityResizeAspectFill` 是顶层字符串常量；`frame` 是 `CValue<CGRect>`，随 `viewDidLayoutSubviews` 同步 |
 * | `UIViewController(null, null)` | 子类化宿主 | **未核实**。两个位置参数对应 `initWithNibName:bundle:`，都传 `null`；**故意不写形参名**——K/N 那边叫 `nibNameOrNil` / `bundleOrNil`，写错就是编译错 |
 * | `viewDidLoad` / `viewDidAppear(animated)` / `viewDidLayoutSubviews` / `viewDidDisappear(animated)` | 生命周期四个钩子 | **未核实**。都是 `UIViewController` 的标准方法；两个形参名必须逐字是 `animated` |
 * | `UIButton(frame = …)` + `setTitle` / `setTitleColor` / `addTarget` + `NSSelectorFromString` + `@ObjCAction` | 「取消」按钮 | **未核实**。`@ObjCAction` 是让 ObjC runtime 认得出选择器的唯一办法，**本仓此前没有先例**；按钮走 `initWithFrame:`（类型是 custom，标题颜色要自己给）。若 macOS 报 `@ObjCAction` 相关的错，两条退路：把类从 `private` 改成 `internal`，或改用 `UIAction.actionWithHandler` + `addAction`（iOS 14+） |
 * | `UIAlertController.alertControllerWithTitle` / `UIAlertAction.actionWithTitle` / `UIAlertControllerStyleAlert` / `UIAlertActionStyleDefault` | 权限弹窗 | **未核实**。与社区样例的写法一致（顶层常量形式） |
 * | `UIModalPresentationFullScreen` | 强制全屏 | **未核实**。不设它就是 iOS 13 起的卡片式：上面露出一截父页，而且**能下滑关掉**——下滑不会走我们的回调，调用方会一直等 |
 * | `UIApplicationOpenSettingsURLString` / `NSURL.URLWithString` / `UIApplication.sharedApplication.openURL` | 「去设置」 | 形状照 `IosNavigation.iosOpenUrl`（仓内已有）。这里没有用 `NSURL(string = …)` 初始化器，是为了只押一条已编过的写法 |
 * | `dispatch_async` + `dispatch_get_global_queue(QOS_CLASS_USER_INITIATED.toLong(), 0UL)` | 后台起 / 停流 | `dispatch_get_global_queue` 的**两个实参类型**（`Long`, `ULong`）照 skiko 的 `Dispatchers.kt`（随 CMP 1.7.3 一起编过）；`QOS_CLASS_*` 在 `platform.posix`，若不可用换成 skiko 用的 `QOS_CLASS_BACKGROUND` |
 * | `UIColor.blackColor` / `whiteColor` / `layer.borderWidth` / `layer.borderColor` | 黑底 + 白色取景框 | **未核实**。`borderColor` 吃 `CGColor`，取自 `UIColor.CGColor` |
 *
 * 没有引入任何依赖，也没有新增公共类型：对外只有下面这一个函数。
 */
@Composable
fun rememberIosBikeScanner(): (onResult: (String?) -> Unit) -> Unit {
    // present 的宿主 VC 只能从组合里取：不猜 UIApplication.keyWindow（iOS 13 多场景之后语义
    // 不成立，理由与 ui/common/ShareIntents.ios.kt / ui/platform/FilePickers.ios.kt 逐字相同）。
    val host = LocalUIViewController.current
    return remember(host) {
        { onResult ->
            val scanner = IosBikeScanViewController(onResult)
            // 全屏是功能要求，不只是观感：卡片式能被下滑关掉，而下滑走不到我们的回调，
            // 调用方会永远等一个不会来的结果。
            scanner.modalPresentationStyle = UIModalPresentationFullScreen
            host.presentViewController(scanner, animated = true, completion = null)
        }
    }
}

/**
 * 一帧里的第一枚可读码 → [onCode]。
 *
 * **必须被强引用**：`setMetadataObjectsDelegate` 的代理是 **weak** 语义，写成局部对象会被
 * ObjC 引用计数回收，随后回调一次都不来（现象是「有画面，但怎么对都不出结果」）。
 * 持有者是 [IosBikeScanViewController] 的 `metadataDelegate`。
 *
 * 去重不在这里做：同帧多枚只取第一枚已经由 `firstOrNull` 保证，跨帧的重复交给
 * [IosBikeScanViewController.finishWithResult] 的 `finished`——去重标志只有一处，不会出现两份结论。
 */
private class IosBikeScanMetadataDelegate(
    private val onCode: (String) -> Unit,
) : NSObject(), AVCaptureMetadataOutputObjectsDelegateProtocol {

    override fun captureOutput(
        output: AVCaptureOutput,
        didOutputMetadataObjects: List<*>,
        fromConnection: AVCaptureConnection,
    ) {
        val readable = didOutputMetadataObjects.firstOrNull() as? AVMetadataMachineReadableCodeObject
        val text = readable?.stringValue
        if (!text.isNullOrBlank()) onCode(text)
    }
}

/**
 * 扫码取景页本体：黑底 + 中央透明取景框 + 左上角「取消」。
 *
 * 只用 `UIView` / `UIButton` 拼，不做花哨 UI——这一页的全部价值是让相机赶紧对上码。
 * 每个方法为什么这么写，见文件头；改动时保持直线，别在这里加抽象层。
 */
private class IosBikeScanViewController(
    private val onResult: (String?) -> Unit,
) : UIViewController(null, null) {

    private val session = AVCaptureSession()
    private var deviceInput: AVCaptureDeviceInput? = null
    private var metadataOutput: AVCaptureMetadataOutput? = null

    /** 代理的强引用（见 [IosBikeScanMetadataDelegate]）。拆会话时置空，顺带断掉「VC → 代理 → VC」的环。 */
    private var metadataDelegate: IosBikeScanMetadataDelegate? = null

    private var previewLayer: AVCaptureVideoPreviewLayer? = null

    private val scanBox = UIView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0))
    private val cancelButton = UIButton(frame = CGRectMake(0.0, 0.0, 0.0, 0.0))

    /** 回调只发一次：同帧多枚、后续帧、取消与识别撞在一起，全靠它收敛。 */
    private var finished = false

    /** 相机已经拆过一遍：`stopCapture` 幂等。 */
    private var tornDown = false

    /** 已经走过一遍「读授权 → 建会话」：`viewDidAppear` 可能来第二次（见那里的说明）。 */
    private var startRequested = false

    override fun viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = UIColor.blackColor
        // 取景框只是个「往这儿对」的提示：透明 + 2pt 白边，真正的识别范围是全画面
        scanBox.layer.borderWidth = 2.0
        scanBox.layer.borderColor = UIColor.whiteColor.CGColor
        view.addSubview(scanBox)
        cancelButton.setTitle("取消", UIControlStateNormal)
        cancelButton.setTitleColor(UIColor.whiteColor, UIControlStateNormal)
        cancelButton.addTarget(this, NSSelectorFromString("onCancelTapped"), UIControlEventTouchUpInside)
        view.addSubview(cancelButton)
    }

    /**
     * 只在**页面真的上屏之后**才碰相机。
     *
     * 两条理由：
     * 1. `viewDidLoad` 时宿主还没进窗口层级，那一档弹 `UIAlertController` 会被 UIKit 拒掉
     *    （控制台一句 `whose view is not in the window hierarchy`，用户看到的是「点了没反应」）——
     *    而「没有相机权限」「相机打不开」两条路都要弹。
     * 2. `viewDidAppear` 会来第二次：本页弹过的弹层关掉时，presenter 会重新 appear 一次。
     *    相机只起一次，靠 [startRequested] 挡住。
     */
    override fun viewDidAppear(animated: Boolean) {
        super.viewDidAppear(animated)
        if (startRequested) return
        startRequested = true
        beginWithPermission()
    }

    override fun viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        // 预览层与取景框的尺寸只能在布局之后才有真实值：viewDidLoad 时 bounds 可能还是全零
        val bounds = view.bounds
        previewLayer?.let { it.setFrame(bounds) }
        val width = bounds.useContents { size.width }
        val height = bounds.useContents { size.height }
        val side = minOf(width, height) * SCAN_BOX_RATIO
        scanBox.setFrame(CGRectMake((width - side) / 2.0, (height - side) / 2.0, side, side))
        val topInset = view.safeAreaInsets.useContents { top }
        cancelButton.setFrame(
            CGRectMake(CANCEL_MARGIN, topInset + CANCEL_MARGIN, CANCEL_BUTTON_WIDTH, CANCEL_BUTTON_HEIGHT),
        )
    }

    /**
     * 关页面的三条路都会走到这里（取消 / 识别到 / 系统回收）。
     *
     * **相机不在这里停就会一直亮着**：`stopRunning` 是唯一的关灯开关，dismiss 本身不关相机。
     * 放在 `viewDidDisappear` 而不是 `finishWithResult` 里，是因为「系统收回页面」这条路径也得停。
     */
    override fun viewDidDisappear(animated: Boolean) {
        stopCapture()
        super.viewDidDisappear(animated)
    }

    /** 「取消」按钮的落点（选择器名见 `viewDidLoad`）。 */
    @ObjCAction
    fun onCancelTapped() {
        finishWithResult(null)
    }

    /** 读授权 → 该请求就请求一次 → 建会话；被拒 / 受家长控制限制时弹窗，关掉回 `null`。 */
    private fun beginWithPermission() {
        when (AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo)) {
            AVAuthorizationStatusAuthorized -> startCapture()
            // 系统授权回调在任意队列上，回到主线程再碰 UIKit（与 IosLocalNotifications 同一纪律）
            AVAuthorizationStatusNotDetermined -> AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) { granted ->
                dispatch_async(dispatch_get_main_queue()) {
                    if (finished) return@dispatch_async
                    if (granted) startCapture() else showPermissionDenied()
                }
            }
            // Denied / Restricted（屏幕使用时间、MDM）：系统不会再弹框，只能去设置里开
            else -> showPermissionDenied()
        }
    }

    /** 建会话、挂代理、装预览，最后把起流丢到后台。任一步失败都是「相机打不开」，不静默。 */
    private fun startCapture() {
        if (finished) return
        val device = AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo)
            ?: return showCameraUnavailable()
        val input = AVCaptureDeviceInput.deviceInputWithDevice(device, null)
            ?: return showCameraUnavailable()
        // 相机被别的 App 占着时这里过不去：加进去也是黑屏，不如当场说清楚
        if (!session.canAddInput(input)) return showCameraUnavailable()
        session.addInput(input)
        deviceInput = input
        val output = AVCaptureMetadataOutput()
        if (!session.canAddOutput(output)) return showCameraUnavailable()
        session.addOutput(output)
        metadataOutput = output
        val delegate = IosBikeScanMetadataDelegate { text -> finishWithResult(text) }
        output.setMetadataObjectsDelegate(delegate, dispatch_get_main_queue())
        metadataDelegate = delegate
        output.metadataObjectTypes = listOf(
            AVMetadataObjectTypeQRCode,
            AVMetadataObjectTypeCode128Code,
            AVMetadataObjectTypeCode39Code,
            AVMetadataObjectTypeEAN13Code,
            AVMetadataObjectTypeEAN8Code,
            AVMetadataObjectTypeITF14Code,
        )
        val preview = AVCaptureVideoPreviewLayer(session = session)
        preview.videoGravity = AVLayerVideoGravityResizeAspectFill
        // CALayer / UIView 的 frame 在 K/N 里是只读属性，赋值走 setFrame(CGRect)（2026-11-26 CI 实测）
        preview.setFrame(view.bounds)
        // 预览层先加：后加的 subview（取景框、取消键）盖在它上面
        view.layer.addSublayer(preview)
        previewLayer = preview
        startRunningOffMainThread()
    }

    /**
     * 起流放后台队列。`startRunning` 是**同步阻塞**的（真机 100–300 ms），主线程调会把
     * present 的转场卡住。
     *
     * 返回之后再查一次 [tornDown]：用户在半秒内点取消时，刚起来的会话会立刻停下。
     */
    private fun startRunningOffMainThread() {
        dispatch_async(dispatch_get_global_queue(QOS_CLASS_USER_INITIATED.toLong(), 0UL)) {
            session.startRunning()
            if (tornDown) session.stopRunning()
        }
    }

    /**
     * 关相机并拆会话，**幂等**（[tornDown]）。
     *
     * 主线程只摘引用；`stopRunning` 与 `removeInput` / `removeOutput` 丢后台——前者同样是
     * 同步阻塞调用，在这里同步跑会把 dismiss 的动画卡住。会话对象不必手动放：它挂在这个 VC 上，
     * VC 释放时一起走。
     */
    private fun stopCapture() {
        if (tornDown) return
        tornDown = true
        val input = deviceInput
        val output = metadataOutput
        deviceInput = null
        metadataOutput = null
        metadataDelegate = null
        previewLayer = null
        dispatch_async(dispatch_get_global_queue(QOS_CLASS_USER_INITIATED.toLong(), 0UL)) {
            session.stopRunning()
            input?.let { session.removeInput(it) }
            output?.let { session.removeOutput(it) }
        }
    }

    /** 唯一的回调出口：停流 → dismiss → 回调一次。取消与识别到走的是同一条。 */
    private fun finishWithResult(result: String?) {
        if (finished) return
        finished = true
        stopCapture()
        dismissViewControllerAnimated(true, null)
        onResult(result)
    }

    /** 没有摄像头（模拟器）、或相机被别的 App 占着：说一句再关页面，别让用户对着黑屏。 */
    private fun showCameraUnavailable() {
        presentAlert(
            title = "相机打不开",
            message = "这台设备没有可用的摄像头，或相机正被其他 App 占用。",
            actions = listOf("知道了" to { finishWithResult(null) }),
        )
    }

    /** 没有相机权限：给「去设置」这条路，关掉一律回 `null`。 */
    private fun showPermissionDenied() {
        presentAlert(
            title = "没有相机权限",
            message = "扫车身码需要相机权限。请在「设置 › JUWPHub › 相机」里打开，或改用其他方式取码。",
            actions = listOf(
                "去设置" to {
                    openAppSettings()
                    finishWithResult(null)
                },
                "取消" to { finishWithResult(null) },
            ),
        )
    }

    /**
     * 一个只有按钮的弹层。样式统一用 `Default`：这里没有「危险操作」，不必为了加粗取消键
     * 再引一个枚举常量。
     */
    private fun presentAlert(title: String, message: String, actions: List<Pair<String, () -> Unit>>) {
        val alert = UIAlertController.alertControllerWithTitle(
            title = title,
            message = message,
            preferredStyle = UIAlertControllerStyleAlert,
        )
        for ((label, run) in actions) {
            alert.addAction(UIAlertAction.actionWithTitle(label, UIAlertActionStyleDefault) { _ -> run() })
        }
        presentViewController(alert, animated = true, completion = null)
    }

    /**
     * 跳到本 App 的系统设置页。
     *
     * 沙箱里没有别的公有入口能打开设置：`UIApplicationOpenSettingsURLString` 是唯一那个 URL，
     * `openURL` 返回 false 只可能是系统拒绝，那时用户自己点「设置」也能到（不做二次提示）。
     */
    private fun openAppSettings() {
        val url = NSURL.URLWithString(UIApplicationOpenSettingsURLString) ?: return
        UIApplication.sharedApplication.openURL(url)
    }
}

/** 取景框边长占屏幕短边的比例。 */
private const val SCAN_BOX_RATIO = 0.7

/** 「取消」按钮离屏幕左上角的间距；上边再叠安全区内边距（避开状态栏与灵动岛）。 */
private const val CANCEL_MARGIN = 16.0

/** 「取消」按钮的点击区：44pt 是 iOS 的最小可点尺寸下限。 */
private const val CANCEL_BUTTON_WIDTH = 88.0
private const val CANCEL_BUTTON_HEIGHT = 44.0
