package edu.jxslu.schedule

import androidx.compose.ui.window.ComposeUIViewController
import edu.jxslu.schedule.ui.LinkValidationScreen
import platform.UIKit.UIViewController

/**
 * iOS 侧入口（P3，2026-11-24）。
 *
 * Swift 侧通过 `MainViewControllerKt.MainViewController()` 拿到这个 UIViewController，
 * 再用 `UIViewControllerRepresentable` 塞进 SwiftUI。这是 Compose Multiplatform 在 iOS 上
 * 的标准接法：Compose 负责画，Swift 只负责窗口与生命周期。
 *
 * 现在挂的是 [LinkValidationScreen]（链路验证屏），不是产品界面。真要出功能界面，
 * 把这里换成对应页面即可——Swift 侧不需要改。
 */
fun MainViewController(): UIViewController = ComposeUIViewController {
    LinkValidationScreen()
}
