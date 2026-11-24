package edu.jxslu.schedule.platform

import android.content.Context

/**
 * Android 侧的应用私有数据目录（iOS 移植 P2，2026-11-24）。
 *
 * 返回 `context.filesDir.absolutePath`，也就是 `/data/data/<pkg>/files`。
 * **为什么必须是 `filesDir` 而不是 `getDir("datastore", …)` 之类**：旧的
 * `preferencesDataStore(name = "juw_display")` 委托把文件放在
 * `filesDir/datastore/juw_display.preferences_pb`，共享层拼出来的路径正好是同一个，
 * 老用户升级后偏好原样可读，不需要任何迁移。
 */
actual fun appDataDir(): String = requireAppContext("appDataDir()").filesDir.absolutePath

/**
 * 注入点（形状照 `ui/common/Haptics.kt` 的 `HapticsSettings`：**可变持有 + 启动时注入**，
 * 不引入 CompositionLocal / DI 容器）。
 *
 * 写方只有一处：`:app` 的 `JuwApplication.onCreate`。读方是 [appDataDir] 的 actual，
 * 以及 `ui/common` 那几条平台缝（剪贴板、分享）——**全仓只有这一个 Context 注入点**，
 * 别为剪贴板之类另开一个：两套注入点就会出现「一处注入了、另一处还是 null」的半死状态。
 * 存 `applicationContext` 而不是 Activity，避免长生命周期持有界面。
 */
internal var appContext: Context? = null

/**
 * 取注入的 Context；没注入就立刻抛，并在消息里点名**是谁**在启动前调用的。
 *
 * 写成带 `what` 参数的函数而不是每个缝各写一遍 `requireNotNull`：报错文案统一，
 * 也免得后来的人复制粘贴时把 `what` 写错（写错就等于没报错信息）。
 */
internal fun requireAppContext(what: String): Context = requireNotNull(appContext) {
    "$what 早于 JuwApplication.onCreate 被调用：Context 还没注入"
}

/** 由 `JuwApplication.onCreate` 调用一次；重复调用只是覆盖同一个 applicationContext，无副作用。 */
fun initAppDirs(context: Context) {
    appContext = context.applicationContext
}
