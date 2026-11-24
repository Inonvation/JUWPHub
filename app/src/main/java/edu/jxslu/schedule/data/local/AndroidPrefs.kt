package edu.jxslu.schedule.data.local

import android.content.Context
import android.content.SharedPreferences

/**
 * [KeyValuePrefs] 的 Android 实现（iOS 移植 S15，2026-11-24）：一层薄薄的 `SharedPreferences` 包装。
 *
 * **文件名由调用方传、且不能改**：`ujing_order` / `ujing_history` / `ujing_houses` 三个名字
 * 与搬移前逐字一致——共享层的 store 只是换了「怎么读写」，读的仍是同一批老文件，
 * 老用户的在案订单与账单原地可读，**不需要任何迁移**。改名等于把他们本机的账全丢。
 *
 * 不用 `EncryptedSharedPreferences`：这三个文件存的不是凭证（凭证在 `secure_ujing`），
 * 搬移前就是普通 prefs，这里保持同一口径。
 */
class AndroidPrefs(context: Context, name: String) : KeyValuePrefs {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE)

    override fun getString(key: String): String? = prefs.getString(key, null)

    override fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    /**
     * 返回**副本**：`SharedPreferences.getStringSet` 回的是内部持有的那个 Set 实例，
     * 直接交给调用方就等于把内部状态交出去（调用方一改，落盘的值也跟着变）。
     */
    override fun getStringSet(key: String): Set<String> =
        prefs.getStringSet(key, emptySet()).orEmpty().toSet()

    override fun putStringSet(key: String, value: Set<String>) {
        prefs.edit().putStringSet(key, value).apply()
    }

    override fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }

    override fun clear() {
        prefs.edit().clear().apply()
    }
}
