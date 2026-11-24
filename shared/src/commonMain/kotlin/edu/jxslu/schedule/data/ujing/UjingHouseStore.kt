package edu.jxslu.schedule.data.ujing

import edu.jxslu.schedule.data.local.KeyValuePrefs

/** 收藏的洗衣房（U净 门店）。 */
data class UjingHouse(val storeId: String, val name: String)

/**
 * 收藏的洗衣房（DESIGN §4.37）。键值偏好里存 `storeId|名称` 的 stringSet
 * ——同 QzxyDeviceStore 的存法：门店这类本地偏好不上加密，加密只对凭证。
 *
 * 同一门店按 storeId 去重：重复收藏是更新名称，不产生第二条。
 *
 * iOS 移植 S15（2026-11-24）：搬进 `:shared`，介质换成 [KeyValuePrefs]
 * （Android 侧仍是同名 `SharedPreferences` 文件 `ujing_houses`，老收藏原地可读）。
 */
class UjingHouseStore(private val prefs: KeyValuePrefs) {

    fun list(): List<UjingHouse> = prefs.getStringSet(KEY_HOUSES)
        .mapNotNull { raw ->
            val separator = raw.indexOf(SEPARATOR)
            if (separator <= 0) {
                null
            } else {
                UjingHouse(raw.substring(0, separator), raw.substring(separator + 1))
            }
        }
        .sortedBy { it.name }

    fun add(house: UjingHouse) {
        val current = prefs.getStringSet(KEY_HOUSES)
            .filterNot { it.substringBefore(SEPARATOR) == house.storeId }
            .toMutableSet()
        current += house.storeId + SEPARATOR + house.name
        prefs.putStringSet(KEY_HOUSES, current)
    }

    fun remove(storeId: String) {
        val current = prefs.getStringSet(KEY_HOUSES)
            .filterNot { it.substringBefore(SEPARATOR) == storeId }
            .toSet()
        prefs.putStringSet(KEY_HOUSES, current)
    }

    private companion object {
        /** 键名与搬移前逐字一致（老文件里就是这个键）。 */
        const val KEY_HOUSES = "houses"
        const val SEPARATOR = "|"
    }
}
