package edu.jxslu.schedule.platform

/**
 * 应用私有数据目录的绝对路径；DataStore 文件与将来的导出缓存都放这下面（iOS 移植 P2，2026-11-24）。
 *
 * ## 为什么要有这条缝
 *
 * 偏好层的 DataStore 原本挂 `Context.preferencesDataStore(name = "juw_display")` 这个
 * Android 专有委托，文件落在 `filesDir/datastore/`。搬进 `:shared` 的 commonMain 之后
 * 拿不到 `Context`，但 DataStore 只要一个路径，所以把「私有目录在哪」单独做成一条缝。
 *
 * ## 两端的落点
 *
 * | 平台 | 实现 | 实际路径 |
 * |---|---|---|
 * | Android | `context.filesDir.absolutePath`（`JuwApplication.onCreate` 注入） | `/data/data/<pkg>/files` |
 * | iOS | `NSFileManager` 的 `NSApplicationSupportDirectory` | 实测落在 `Documents/`，见 `AppDirs.ios.kt` |
 *
 * **Android 侧必须返回 `filesDir`**：老用户的偏好就存在 `filesDir/datastore/juw_display.preferences_pb`，
 * 返回别处等于把他们所有设置清零。
 *
 * 返回值**不带尾斜杠**，调用方自己拼（本仓只有 `data/prefs/DisplayPrefsKeys.kt` 一处）。
 */
expect fun appDataDir(): String
