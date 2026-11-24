import SwiftUI
import UIKit
import Shared

/// `Shared.framework` 里的 `MainViewController()` → SwiftUI 视图（P3，2026-11-24）。
///
/// 这是 Compose Multiplatform 在 iOS 上的标准接法：Kotlin 侧返回一个 `UIViewController`，
/// 这里用 `UIViewControllerRepresentable` 包一层。**不要**在 Swift 里做任何布局或主题，
/// 那些都在共享代码里，否则两端会分叉。
struct ComposeContainer: UIViewControllerRepresentable {

    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {
        // 无状态容器：Compose 自己管重组，这里不需要同步任何东西
    }
}
