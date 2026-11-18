package edu.jxslu.schedule.data.local

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * 加密 prefs 的**唯一创建口**（2026-11-18 收编 7 份逐字复制的自愈样板）。
 *
 * 裸 `EncryptedSharedPreferences.create` 在 Keystore 损坏时（小米换机、云备份恢复后常见：
 * `AEADBadTagException` / `GeneralSecurityException`）直接抛异常，而凭证读取都发生在
 * Compose 组合期——App 启动即崩、循环崩溃，用户唯一出路是清数据（丢课表与笔记）。
 *
 * 自愈策略（三级，顺序不能换）：
 * 1. 正常创建；
 * 2. 失败 → **删除该 prefs 文件重建**（一次性丢失那一份凭证，换功能可用）；
 * 3. 仍失败 → 普通明文 `"$name.fallback"` 兜底（只存「空」，等同未登录，绝不再抛）。
 *
 * 各业务 store 只负责自己的键名与读写口径，**不要再各写一份创建逻辑**：
 * 七份分叉的样板已经让「改自愈策略要改七处」变成事实。
 *
 * 新增加密文件集时两件事必须一起做：
 * - `backup_rules.xml` / `data_extraction_rules.xml` 排除正式件与 `.fallback` 两份；
 * - 调本函数时传自己的 [tag]，日志里才分得清坏的是哪个文件。
 */
internal object EncryptedPrefs {

    /** 打开（或创建）加密 prefs；永不抛，最差返回空的明文兜底文件。 */
    fun open(context: Context, name: String, tag: String): SharedPreferences {
        val app = context.applicationContext

        fun create(): SharedPreferences = EncryptedSharedPreferences.create(
            app,
            name,
            MasterKey.Builder(app).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )

        runCatching { return create() }
        // 首次创建失败：删文件重建（密钥损坏时这是唯一出路）
        runCatching {
            app.deleteSharedPreferences(name)
            return create()
        }
        // 仍失败（系统级问题）：明文空 prefs 兜底——读到的都是空 = 未登录，不再抛
        Log.e(tag, "$name unrecoverable, fallback to plain")
        return app.getSharedPreferences("$name.fallback", Context.MODE_PRIVATE)
    }
}
