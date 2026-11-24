package edu.jxslu.schedule.platform

/**
 * iOS 侧：**不加锁**，直接跑（理由与边界见 `Lock.kt`）。
 *
 * 别在这里硬造一个锁：Kotlin/Native 标准库没有 `synchronized`，用原子类型自己拼
 * 一个自旋锁会把「主线程上的几次队列操作」变成可能死锁的东西，得不偿失。
 */
actual fun <T> locked(lock: Any, block: () -> T): T = block()
