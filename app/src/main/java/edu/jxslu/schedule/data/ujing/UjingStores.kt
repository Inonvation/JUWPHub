package edu.jxslu.schedule.data.ujing

import android.content.Context
import edu.jxslu.schedule.data.local.AndroidPrefs

/**
 * U净 三个本地偏好文件的 Android 落点（iOS 移植 S15，2026-11-24）。
 *
 * 文件名与搬移前**逐字一致**（`ujing_order` / `ujing_history` / `ujing_houses`）：
 * 共享层的 store 只换了「怎么读写」，读的仍是同一批老文件，老用户的在案订单、
 * 账单与收藏原地可读，不需要迁移。改名等于把他们本机的账全丢——别动这三个字符串。
 *
 * 为什么文件名留在 `:app` 而不是共享层的 store 里：这是**介质口径**（Android 的文件名），
 * iOS 侧对应的是 `NSUserDefaults` 的 suite 名，两者不必同名。
 *
 * 函数名前缀 `android` 是为了和 `Graph` 里那几个私有单例字段（`ujingOrderStore` …）
 * 区分开——同名属性 + 同名函数虽然编译得过，读代码时却要停下来分辨哪个是哪个。
 */
fun androidUjingOrderStore(context: Context): UjingOrderStore =
    UjingOrderStore(AndroidPrefs(context, "ujing_order"))

fun androidUjingHistoryStore(context: Context): UjingHistoryStore =
    UjingHistoryStore(AndroidPrefs(context, "ujing_history"))

fun androidUjingHouseStore(context: Context): UjingHouseStore =
    UjingHouseStore(AndroidPrefs(context, "ujing_houses"))
