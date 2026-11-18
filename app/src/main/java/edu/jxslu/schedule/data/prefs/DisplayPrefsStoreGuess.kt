package edu.jxslu.schedule.data.prefs

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import edu.jxslu.schedule.domain.GuessRules
import edu.jxslu.schedule.domain.GuessTarget
import edu.jxslu.schedule.domain.GuessUsage
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.abs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 「猜你想用」偏好（DESIGN §4.51）：从 [DisplayPrefsStore] 拆出，按功能聚在一处。
 *
 * 存两样东西：开关（默认开）与订单事件（JSON，最多 400 条 / 60 天）。
 * **不存页面打开**——习惯只认真实订单，见 `domain/GuessYouWant.kt` 的口径。
 */

/** 功能开关。**默认开**：展示型推荐，与「快捷方式」「百宝箱」同一类；用户嫌吵再关。 */
private val KEY_GUESS_ENABLED = booleanPreferencesKey("guess_you_want_enabled")

/** 订单事件 JSON。关闭开关不清理；关闭后再打开时清空（见 [setGuessYouWantEnabled]）。 */
private val KEY_GUESS_USAGE = stringPreferencesKey("guess_you_want_usage_json")

/** 开关。关闭后不出卡、不再写事件；重新打开时清空习惯（「关闭再打开重置」的口径）。 */
val DisplayPrefsStore.guessYouWantEnabled: Flow<Boolean>
    get() = cachedFlow("guessYouWantEnabled") {
        context.displayDataStore.data.map { p -> p[KEY_GUESS_ENABLED] ?: true }
            .distinctUntilChanged()
    }

/** 近 60 天订单事件，已排序、已丢弃非法条目；没有数据时是空表。 */
val DisplayPrefsStore.guessUsage: Flow<List<GuessUsage>>
    get() = cachedFlow("guessUsage") {
        context.displayDataStore.data.map { p -> decodeGuessUsageLog(p[KEY_GUESS_USAGE]) }
            .distinctUntilChanged()
    }

/**
 * 记一次真实订单。
 *
 * 调用点只在五个业务成功处：胖乖确认出水、快趣建单、趣智开阀、U净下单受理、问渠报名成功。
 * 开关关着直接返回（不写盘）；同一功能 3 分钟内只记一条，防「结果不明补核对」把一次订单记成两条。
 *
 * 时间取**本机当前时刻**：订单成功与记录之间只差毫秒级，30 分钟槽不会因此错位。
 */
suspend fun DisplayPrefsStore.recordGuessOrder(target: GuessTarget) {
    val enabled = context.displayDataStore.data.first()[KEY_GUESS_ENABLED] ?: true
    if (!enabled) return

    val now = LocalDateTime.now()
    val at = now.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    context.displayDataStore.edit { p ->
        // 读一次到写之间可能刚被关掉，事务内再判一次（写入口只此一处，成本可忽略）
        if (p[KEY_GUESS_ENABLED] == false) return@edit
        val current = decodeGuessUsageLog(p[KEY_GUESS_USAGE])
        val duplicated = current.any {
            it.target == target && abs(at - it.atMillis) < GuessRules.DEDUPE_MILLIS
        }
        if (duplicated) return@edit

        val cutoff = at - GuessRules.HISTORY_DAYS * 24L * 60L * 60L * 1000L
        val event = GuessUsage(
            atMillis = at,
            dayOfWeek = now.dayOfWeek.value,
            slot = GuessRules.slotOf(now.hour * 60 + now.minute),
            target = target,
        )
        val next = (current.filter { it.atMillis >= cutoff } + event)
            .takeLast(GuessRules.MAX_USAGES)
        p[KEY_GUESS_USAGE] = encodeGuessUsageLog(next)
    }
}

/**
 * 开关写入。**只在「关 → 开」时清空习惯**——用户说的「关闭再打开后重置」；
 * 反复点开同值不产生任何清理。
 */
suspend fun DisplayPrefsStore.setGuessYouWantEnabled(value: Boolean) {
    context.displayDataStore.edit { p ->
        val was = p[KEY_GUESS_ENABLED] ?: true
        p[KEY_GUESS_ENABLED] = value
        if (value && !was) p.remove(KEY_GUESS_USAGE)
    }
}

// ---- JSON 编解码 ----------------------------------------------------------

private val guessJson = Json { ignoreUnknownKeys = true }

@Serializable
private data class GuessUsageLogDto(
    val v: Int = 1,
    val events: List<GuessUsageEventDto> = emptyList(),
)

@Serializable
private data class GuessUsageEventDto(
    val at: Long = 0,
    val dow: Int = 0,
    val slot: Int = 0,
    val target: String = "",
)

/**
 * 解码：任何一条坏数据只丢那一条，不让整份习惯表失效（数据是自己写的，
 * 但跨版本 / 手工改坏都可能出现）。未知 target、非法星期 / 槽、非正时间一律丢弃。
 * 结果按时间排序并截到上限。
 */
internal fun decodeGuessUsageLog(text: String?): List<GuessUsage> {
    if (text.isNullOrBlank()) return emptyList()
    val log = runCatching { guessJson.decodeFromString<GuessUsageLogDto>(text) }.getOrNull()
        ?: return emptyList()
    return log.events.mapNotNull { event ->
        val target = GuessTarget.fromId(event.target) ?: return@mapNotNull null
        if (event.at <= 0L ||
            event.dow !in 1..7 ||
            event.slot !in 0 until GuessRules.SLOTS_PER_DAY
        ) {
            return@mapNotNull null
        }
        GuessUsage(
            atMillis = event.at,
            dayOfWeek = event.dow,
            slot = event.slot,
            target = target,
        )
    }.sortedBy { it.atMillis }.takeLast(GuessRules.MAX_USAGES)
}

/** 编码：只写稳定 id 与四个必要字段，不写任何页面 / 账号信息。 */
internal fun encodeGuessUsageLog(events: List<GuessUsage>): String =
    guessJson.encodeToString(
        GuessUsageLogDto.serializer(),
        GuessUsageLogDto(
            events = events.map {
                GuessUsageEventDto(
                    at = it.atMillis,
                    dow = it.dayOfWeek,
                    slot = it.slot,
                    target = it.target.id,
                )
            },
        ),
    )
