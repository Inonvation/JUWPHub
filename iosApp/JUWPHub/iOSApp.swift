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
                // 撑满窗口：UIViewControllerRepresentable 在某些布局下会收成固有尺寸，
                // 那时下面的 ignoresSafeArea 等于白写，屏幕上下就各留一条窗口白边。
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                // Compose 独占整块窗口（含状态栏与 home indicator 两条安全区）：
                // 不忽略安全区的话，SwiftUI 会把 VC 塞进安全区里，外面露出窗口白底——
                // 真机上就是「最上方和最下方各一条白边」，抽屉面板也因此够不到顶。
                // 安全区改由 Compose 自己消费（`IosRoot` 的 windowInsetsPadding）。
                .ignoresSafeArea()
                // 键盘避让仍然交给 Compose（它有 IME insets）
                .ignoresSafeArea(.keyboard)
        }
    }
}
