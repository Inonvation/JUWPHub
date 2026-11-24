import SwiftUI

/// iOS 入口（P3，2026-11-24）。
///
/// 结构刻意压到最小：SwiftUI 只负责窗口与生命周期，界面全在 Kotlin 的 Compose 里
/// （`MainViewControllerKt.MainViewController()`）。这样后续加页面时 **Swift 侧不用改**——
/// 只改 `:shared` 里 `MainViewController` 挂哪个 Composable。
@main
struct iOSApp: App {
    var body: some Scene {
        WindowGroup {
            ComposeContainer()
                .ignoresSafeArea(.keyboard) // Compose 自己处理键盘避让，交给它
        }
    }
}
