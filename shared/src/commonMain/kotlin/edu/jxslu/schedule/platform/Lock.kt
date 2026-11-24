package edu.jxslu.schedule.platform

/**
 * 同步互斥块的跨平台入口（iOS 移植 M1，2026-11-25）。
 *
 * ## 为什么需要它
 *
 * `kotlin.synchronized` 是 **JVM 专有**的：`commonMain` 里写它一定编译不过（这条已经踩过一次，
 * `ui/jwvw/JwImportResultBus.kt` 的 `OutcomeQueue` 就是被它卡住的）。Kotlin/Native 也没有
 * 等价的 `synchronized` 函数，标准库只给到原子类型。
 *
 * ## 两端语义
 *
 * | 平台 | 实现 | 语义 |
 * |---|---|---|
 * | Android | `synchronized(lock, block)` | 真锁 |
 * | iOS | 直接跑 [block] | **不加锁** |
 *
 * 这不是「以后补」的占位，而是一个有边界的取舍：目前用到它的只有
 * [edu.jxslu.schedule.ui.jwvw.JwImportResultBus] —— 那条通路只在**教务导入**里跑，
 * 而 iOS 侧还没有教务导入（要等 M2 的 WKWebView + Ktor 版会话）。等 iOS 接上导入时，
 * 调用方要么保证只在主线程 publish/consume（Compose 与 `viewModelScope` 默认都在主线程，
 * 现状就是如此），要么换成 `kotlinx.coroutines.sync.Mutex` 的挂起版本。
 *
 * shortcut: iOS 侧不加锁；接上教务导入时若出现跨线程 publish，改成 Mutex 版本。
 */
expect fun <T> locked(lock: Any, block: () -> T): T
