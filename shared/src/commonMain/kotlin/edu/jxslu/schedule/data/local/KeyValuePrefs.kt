package edu.jxslu.schedule.data.local

/**
 * **键值偏好**的跨平台契约（iOS 移植 S15，2026-11-24）。
 *
 * ## 为什么需要它
 *
 * U净 的四份本地状态（在案订单 / 历史账单 / 收藏洗衣房）原本都直接吃
 * `android.content.SharedPreferences`：`Context` + `org.json` 两样都是 Android 专有，
 * 搬进 `commonMain` 就编不过。可它们的**业务语义**（哪个键、什么时候清、上限多少、
 * 同 id 去重）又确实该共享——iOS 侧要的是同一套行为，只是介质换成 `NSUserDefaults`。
 *
 * 于是把「介质」收成这一条缝：共享层的 store 只认这五个动作，介质由各端给。
 *
 * | 平台 | 实现 | 底层 |
 * |---|---|---|
 * | Android | `AndroidPrefs`（`:app` 的 `data/local/AndroidPrefs.kt`） | `SharedPreferences`，**文件名逐字沿用**（老数据原地可读） |
 * | iOS | 待接 | `NSUserDefaults` |
 *
 * ## 为什么不做成 `expect fun platformPrefs(name: String)`
 *
 * Android 的 `actual` 要 `Context`，而 `platform/` 那条缝里只注入了 `appDataDir()` 与
 * `FileSystem`（`initAppDirs` 只持有 `applicationContext` 供 `appDataDir` 用）。
 * 为了五个方法再往 `platform/` 加一条 expect/actual，等于把「谁持有 Context」这件事
 * 拆成两处；用普通接口 + 各端装配根注入，接口本身在 commonMain 可测，
 * 也不需要 iOS 侧先写一段本机编不了的 actual。
 *
 * ## 语义要求（各端实现必须一致）
 *
 * - 读不到就回空（`null` / 空集合），**不抛**；
 * - `getStringSet` 返回**副本**：调用方改它不该影响已存的值（`SharedPreferences` 的既有契约）；
 * - 写入同步生效即可，不要求返回值——这些状态都是旁路账本，写失败由业务层各自的
 *   「读不到就当没有」兜住（与搬移前一致）。
 */
interface KeyValuePrefs {

    fun getString(key: String): String?

    fun putString(key: String, value: String)

    fun getStringSet(key: String): Set<String>

    fun putStringSet(key: String, value: Set<String>)

    fun remove(key: String)

    fun clear()
}
