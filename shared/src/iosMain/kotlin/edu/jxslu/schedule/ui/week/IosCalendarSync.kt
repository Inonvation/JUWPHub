@file:OptIn(ExperimentalForeignApi::class)

package edu.jxslu.schedule.ui.week

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import edu.jxslu.schedule.data.local.IosKeyValuePrefs
import edu.jxslu.schedule.domain.ScheduleExporter
import edu.jxslu.schedule.domain.ScheduleExporter.teacherDescription
import edu.jxslu.schedule.platform.Logger
import edu.jxslu.schedule.ui.platform.CalendarSyncOutcome
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import platform.EventKit.EKAlarm
import platform.EventKit.EKAuthorizationStatusDenied
import platform.EventKit.EKAuthorizationStatusNotDetermined
import platform.EventKit.EKAuthorizationStatusRestricted
import platform.EventKit.EKCalendar
import platform.EventKit.EKEntityTypeEvent
import platform.EventKit.EKEvent
import platform.EventKit.EKEventStore
import platform.EventKit.EKSpanThisEvent
import platform.Foundation.NSDate
import platform.Foundation.NSError
import platform.Foundation.NSProcessInfo
import kotlin.coroutines.resume

/**
 * 「课表一键同步到系统日历」的 iOS 实现（EventKit）。
 *
 * 宿主把它直接填进 `WeekScreen` 的 `onSyncCalendar`，不需要任何启动期初始化：
 *
 * ```kotlin
 * WeekScreen(
 *     // …
 *     onSyncCalendar = rememberIosCalendarSync(),
 * )
 * ```
 *
 * ## 为什么只能是「一个 @Composable 工厂」
 *
 * 1. **结果要回主线程改 Compose 状态**（提示卡挂在 `WeekScreen` 的 snackbar 上），而
 *    「读授权 → 等系统回调 → 写一批事件 → 交结果」这条链需要一个 `CoroutineScope`；
 *    iOS 侧能拿到它的唯一来源是组合（`rememberCoroutineScope`）。
 * 2. `WeekScreen` 的形参是「可空 + 三参数 + 一个结果回调」，没有挂起返回、没有 Context。
 *    工厂返回的正是**同一个函数类型**，宿主那一行不用写适配代码，也不用改共享层。
 * 3. `EKEventStore` 创建代价不低，且要跨多次同步复用（默认日历只在它上面查）。本文件取
 *    **进程级单例 + `by lazy`**：第一次真同步才建，**启动路径上一处都不碰 EventKit**
 *    （2026-11-26「装上后一开就闪退」的教训：启动期碰系统框架的调用一律推迟，
 *    见 `platform/IosLocalNotifications.kt` 的两道闸）。
 *
 * ## 与 Android `CalendarSyncer` 的对照
 *
 * | 项 | Android（`:app` 的 `data/calendar/CalendarSyncer.kt`） | 这里 |
 * |---|---|---|
 * | 去重 | 先删 `CUSTOM_APP_PACKAGE = 本包名` 的事件，再整表重写 | 先按记下的 `eventIdentifier` 逐个删，再整表重写 |
 * | 「谁写的」怎么认 | Provider 自带 `CUSTOM_APP_PACKAGE` 列 | EventKit 没有这种列，只能自己记账（[SYNCED_IDS_KEY]） |
 * | 标题 | `event.name` | 同左 → `EKEvent.title` |
 * | 地点 | `position.isNotBlank()` 才写 `EVENT_LOCATION` | 同条件 → `EKEvent.location` |
 * | 备注 | `teacherDescription(teacher)` 非空才写 `DESCRIPTION`（唯一出处） | 同左 → `EKEvent.notes` |
 * | 时刻 | `DTSTART/DTEND` = epoch 毫秒（系统默认时区） | `startDate/endDate` = 同一时刻的 `NSDate` |
 * | 提醒 | `reminderMinutes > 0` 时插一条 `Reminders` | `reminderMinutes > 0` 时挂一条 `EKAlarm`，相对偏移 `-分钟 × 60` 秒 |
 * | 提醒核对 | 回读 `Reminders` 表，没落库要报 `reminderMissing` | **不做**：`EKAlarm` 跟着 `saveEvent` 一次落库，没有「静默丢弃」这条路径，`reminderMissing` 恒 0 |
 * | 时区 / 占用状态 | 写 `EVENT_TIMEZONE` / `AVAILABILITY_BUSY` | **不写**：`NSDate` 是绝对时刻，EventKit 按设备时区显示；`availability` 与课表语义无关，不值得多引一个没核实过的枚举常量 |
 *
 * ## 四种收场（文案在 `WeekScreen.calendarSyncFeedback`，本文件只负责把结论给对）
 *
 * | 收场 | 什么时候发生 | 页面会说什么 |
 * |---|---|---|
 * | `Synced(count, 0)` | 至少写成一条 | 「已同步 N 条…」 |
 * | `NoPermission` | 授权是 `Denied` / `Restricted`，或这次请求没被批准 | 「日历权限未授予，无法同步」 |
 * | `NoCalendarAccount` | 既没有默认日历，也没有任何可写日历 | 「手机上没有可用日历账户…」 |
 * | `Error(message)` | 写入抛异常，或一条都没写成 | 「同步失败：…」 |
 *
 * `Unsupported` / `Deleted` **不出现**：前者是「本平台做不到」（现在做得到了），后者对应
 * 「我的 → 日历同步」的一键删除，iOS 侧还没有那个入口。不要为了凑齐全类型硬造两条死分支。
 *
 * 空课表按 Android 的提前返回处理：直接 `Synced(0, 0)`，**不动**旧事件（没课不等于要把日历清空）。
 *
 * ## 真机上最容易认错的四件事
 *
 * 1. **点了没反应**：授权框没弹（`NotDetermined` 判断没进去）或 `EKEventStore` 根本没建起来。
 *    先看 [syncToSystemCalendar] 里那条「日历授权未通过」日志。
 * 2. **弹层关掉了但没有任何提示**：结果没回到主线程。本实现交结果前一律
 *    `withContext(Dispatchers.Main)`，就是为了这一类。
 * 3. **提示「已同步 N 条」但日历里看不到**：写进了一个隐藏 / 订阅类日历，见 [pickWritableCalendar]。
 * 4. **每次同步都多一份**：账本里的 `eventIdentifier` 丢了（保存后为空，或 prefs 被清），
 *    旧事件就认不回来，见 [deleteOwnedEvents]。
 *
 * ## 符号清单（本机是 Windows，Kotlin/Native 编不了；除注明外**全部未核实**）
 *
 * 写法上刻意保持直线（不抽平台层 helper、不套泛型），真机上出错时一眼能改。
 *
 * | 符号 | 用途 | 核实状态 / 不确定点 |
 * |---|---|---|
 * | `EKEventStore()` | 建事件库 | **未核实**。构造器即 `alloc/init`；本文件 `by lazy` 只建一次 |
 * | `EKEventStore.authorizationStatusForEntityType(EKEntityTypeEvent)` | 读授权状态 | **未核实**。ObjC 类方法 `+authorizationStatusForEntityType:` 在 K/N 上挂 companion；`EKAuthorizationStatus*` 是 NS_ENUM 顶层常量，形态同 `ui/ebike/BikeSensors.ios.kt` 的 `kCLAuthorizationStatus*` |
 * | `requestFullAccessToEventsWithCompletion` | iOS 17+ 请求权限 | **未核实**。对应 `-requestFullAccessToEventsWithCompletion:`，块参数 `(Boolean, NSError?) -> Unit`；苹果文档写明回调**在任意队列**上执行，所以只能 resume 协程，不能在回调里碰 UI |
 * | `requestAccessToEntityType(EKEntityTypeEvent, completion)` | 15/16 回落请求权限 | **未核实**。iOS 17 SDK 里已标 deprecated，编译期会有警告，不影响构建 |
 * | `NSProcessInfo.processInfo.operatingSystemVersion` + `useContents` | 判 17 分支 | **未核实**。C 结构体返回 `CValue<NSOperatingSystemVersion>`，字段要 `useContents { majorVersion }` 才读得到；取值形态照 `BikeSensors.ios.kt` 的 `coordinate.useContents` |
 * | `store.defaultCalendarForNewEvents` / `store.calendarsForEntityType(EKEntityTypeEvent)` / `EKCalendar.allowsContentModifications` | 挑可写日历 | **未核实**。返回是 `NSArray<EKCalendar *>`，带不带 `?` 本机判不了，代码用 `.orEmpty()` 兼容两种。**`allowsModifications` 在 EKCalendar 上不存在**（见 [pickWritableCalendar]） |
 * | `EKEvent.eventWithEventStore(store)` | 建一个待写的事件 | **未核实**。ObjC 类工厂 `+eventWithEventStore:`，形态同 `UIImage.imageWithCGImage` |
 * | `EKEvent.title` / `location` / `notes` / `startDate` / `endDate` / `calendar` | 填内容 | **未核实**。`title` / `location` / `notes` / `calendar` 来自 `EKCalendarItem`，`startDate` / `endDate` 来自 `EKEvent`，都是可写属性。nullability 本机判不了，赋值方向不受影响 |
 * | `EKAlarm.alarmWithRelativeOffset(seconds)` + `EKEvent.addAlarm` | 提前提醒 | **未核实**。类工厂 `+alarmWithRelativeOffset:` 收 `Double` 秒，**负值 = 事件开始之前**；`addAlarm:` 是无返回值的属性式方法 |
 * | `store.saveEvent(event, span, commit, error)` / `store.removeEvent(…)` | 写 / 删一条 | **未核实**。`error` 一律传 `null`（见下段取舍） |
 * | `store.eventWithIdentifier(id)` / `EKEvent.eventIdentifier` | 认领上次写下的事件 | **未核实**。`eventIdentifier` 是只读 `NSString?`，保存成功之后才有值 |
 * | `NSDate(timeIntervalSinceReferenceDate = …)` | 时刻 | 形状照 `data/session/IosWebCookieBridge.kt`（CI 编译过）。K/N 的 `NSDate` **没有** `timeIntervalSince1970`，要自己补 978307200 秒（`BikeSensors.ios.kt` 同名常量） |
 * | `IosKeyValuePrefs("ios_calendar")` | 记 `eventIdentifier` | 与 `IosServices` 里三份 U净 store 同一形态（CI 编译过）。落 `NSUserDefaults`——它不是凭证，不需要 Keychain |
 * | `Dispatchers.Main` | 回主线程交结果 | 形状照 `ui/week/IosScheduleShareActions.kt`（CI 编译过） |
 *
 * **`error = null` 的取舍**：`saveEvent` / `removeEvent` 的 error 参数是
 * `CPointer<ObjCObjectVar<NSError?>>?`，要读它得多一整套 `memScoped` + `alloc`，而这台机器上
 * 一行都编不过。换来的只是一句更细的 `localizedDescription`，而用户侧对「写失败」只有
 * 一种说法（[SYNC_FAILED_TEXT]）。所以先不买这笔账；真要排查时把 `null` 换成指针即可，
 * 调用点的形状不用动。
 */
@Composable
fun rememberIosCalendarSync(): (
    events: List<ScheduleExporter.CourseEvent>,
    reminderMinutes: Int,
    onResult: (CalendarSyncOutcome) -> Unit,
) -> Unit {
    val scope = rememberCoroutineScope()
    // lambda 的参数类型由返回类型给定（与 WeekScreen 的 onSyncCalendar 同一个函数类型）
    return remember(scope) {
        { events, reminderMinutes, onResult ->
            scope.launch {
                val outcome = syncToSystemCalendar(events, reminderMinutes)
                // onResult 会改 Compose 状态（提示卡），必须回主线程再交
                withContext(Dispatchers.Main) { onResult(outcome) }
            }
        }
    }
}

/** 一次同步：空表直通 → 过权限 → 挑日历 → 先删后写。四种收场见文件头。 */
private suspend fun syncToSystemCalendar(
    events: List<ScheduleExporter.CourseEvent>,
    reminderMinutes: Int,
): CalendarSyncOutcome {
    // 与 Android 同一条提前返回：空表算成功，不动旧事件
    if (events.isEmpty()) return CalendarSyncOutcome.Synced(count = 0, reminderMissing = 0)
    if (!ensureCalendarAccess()) {
        Logger.w(TAG, "日历授权未通过，放弃同步")
        return CalendarSyncOutcome.NoPermission
    }
    val store = eventStore
    val target = withContext(Dispatchers.Default) { pickWritableCalendar(store) }
        ?: return CalendarSyncOutcome.NoCalendarAccount
    return withContext(Dispatchers.Default) {
        // EventKit 的读写全在后台；这也是唯一可能抛异常的一段
        try {
            deleteThenWrite(store, target, events, reminderMinutes)
        } catch (e: Exception) {
            Logger.w(TAG, "写入日历失败：${e.message}")
            CalendarSyncOutcome.Error(e.message ?: SYNC_FAILED_TEXT)
        }
    }
}

/**
 * 过权限：已给就直通，`NotDetermined` 时请求一次，其余按拒处理。
 *
 * 只认**两个否定状态**，其余（`Authorized`、iOS 17 起的 `FullAccess` / `WriteOnly`）一律当已授权：
 * iOS 17 换了新常量，写死「等于 Authorized」会让 17 上的全权限被当成没授权；而「只给写权限」
 * 对「写课表」这件事本来就够用。
 *
 * 整个判断留在主线程：`EKEventStore` 的**第一次创建**在这里发生，之后 `Dispatchers.Default`
 * 上的读写复用的是同一个实例（Apple 的口径是「一个 App 一个 store」，不要求线程亲和）。
 */
private suspend fun ensureCalendarAccess(): Boolean = withContext(Dispatchers.Main) {
    val store = eventStore
    when (EKEventStore.authorizationStatusForEntityType(EKEntityTypeEvent)) {
        EKAuthorizationStatusNotDetermined -> requestAccess(store)
        EKAuthorizationStatusRestricted, EKAuthorizationStatusDenied -> false
        else -> true
    }
}

/**
 * 弹一次系统授权框并等它答完；`granted == false` 与「请求本身失败」都按不可用收
 * （与 `IosLocalNotifications.requestOnceLocked` 同一口径）。
 *
 * 两个系统版本各一条 API：iOS 17 起 `requestAccessToEntityType` 已废弃，但部署目标是 15.0，
 * 而新 API 在 15/16 上是**不认识的选择器**（直接崩），所以必须先看主版本号再决定调哪个。
 */
private suspend fun requestAccess(store: EKEventStore): Boolean =
    suspendCancellableCoroutine { continuation ->
        fun finish(granted: Boolean, error: NSError?) {
            if (error != null) Logger.w(TAG, "请求日历权限失败：${error.localizedDescription}")
            // 回调可能重复到达（理论上不会），第二次 resume 会抛 IllegalStateException
            if (continuation.isActive) continuation.resume(granted && error == null)
        }
        if (iosMajorVersion() >= 17) {
            store.requestFullAccessToEventsWithCompletion { granted, error -> finish(granted, error) }
        } else {
            store.requestAccessToEntityType(EKEntityTypeEvent) { granted, error -> finish(granted, error) }
        }
    }

/** 系统主版本号；`NSOperatingSystemVersion` 是 C 结构体，字段要 `useContents` 才读得到。 */
private fun iosMajorVersion(): Long =
    NSProcessInfo.processInfo.operatingSystemVersion.useContents { majorVersion }

/**
 * 要写进哪个日历：优先系统默认日历（用户在日历里新建事件时用的那个），否则第一个**可写**的。
 *
 * 可写性判断用的是 `EKCalendar.allowsContentModifications`（「能不能往里增删改条目」）。
 * **需求里写的 `allowsModifications` 在 EKCalendar 上不存在**——`EKCalendar` 只有
 * `allowsContentModifications`、`isImmutable`（后者是「这个日历本身能不能改名/删除」，
 * 文档明确说 immutable 日历仍可加事件，用它筛会漏掉可用日历）。EventKit 的头文件里没有
 * 与 Android `CALENDAR_ACCESS_LEVEL` 完全对应的列，这是最接近的一个。
 *
 * 没有「可见性」这一档可比：EventKit 不给 `visible` 列，所以不像 Android 那样先挑「可见且可写」。
 */
private fun pickWritableCalendar(store: EKEventStore): EKCalendar? {
    store.defaultCalendarForNewEvents?.let { return it }
    return store.calendarsForEntityType(EKEntityTypeEvent)
        // 元素类型与返回值是否可空本机判不了，收两遍；见文件头的符号清单
        .orEmpty()
        .filterIsInstance<EKCalendar>()
        .firstOrNull { it.allowsContentModifications }
}

/**
 * 先删后写：删掉上次同步写下的事件，再整批写新的；返回本次的收场。
 *
 * 账本最后写成「还活着的老 id + 这次真写成的 id」：删不掉的老 id 必须留着，
 * 否则下次同步认不回来，旧事件就变成了永久重复。
 */
private fun deleteThenWrite(
    store: EKEventStore,
    target: EKCalendar,
    events: List<ScheduleExporter.CourseEvent>,
    reminderMinutes: Int,
): CalendarSyncOutcome {
    val survivors = deleteOwnedEvents(store)
    val freshIds = mutableSetOf<String>()
    var written = 0
    for (event in events) {
        val created = EKEvent.eventWithEventStore(store) ?: continue
        created.calendar = target
        created.title = event.name
        // 正文与 Android 逐字同源：只有「教师：xxx」，空教师不写正文
        val teacherLine = teacherDescription(event.teacher)
        if (teacherLine.isNotEmpty()) created.notes = teacherLine
        if (event.position.isNotBlank()) created.location = event.position
        created.startDate = event.start.toNSDate()
        created.endDate = event.end.toNSDate()
        if (reminderMinutes > 0) {
            // 相对偏移的负值 = 事件开始之前
            EKAlarm.alarmWithRelativeOffset(-(reminderMinutes * 60).toDouble())?.let { created.addAlarm(it) }
        }
        // commit = true：一条一条落库。批量提交还要多用一次 store.commit(…)，那个签名同样没核实过，
        // 而一次同步最多几百条，省下的开销不值得多一个未知符号。
        if (!store.saveEvent(created, EKSpanThisEvent, true, null)) continue
        written++
        created.eventIdentifier?.let { freshIds += it }
    }
    prefs.putStringSet(SYNCED_IDS_KEY, survivors + freshIds)
    // 一条都没写成不能说「已同步 0 条」：那会把「写失败」讲成「没有课」
    if (written == 0) return CalendarSyncOutcome.Error(SYNC_FAILED_TEXT)
    return CalendarSyncOutcome.Synced(count = written, reminderMissing = 0)
}

/**
 * 删掉上次同步写下的那批事件，返回**没删掉**的 id（要留在账本里，否则下次重复写）。
 *
 * 查不到的事件不算失败：用户在系统日历里手删了、或账户被移除之后，`eventIdentifier`
 * 就永远认不回来了，留着只会每轮白查一次，所以直接从账本里丢掉。
 */
private fun deleteOwnedEvents(store: EKEventStore): Set<String> {
    val remembered = prefs.getStringSet(SYNCED_IDS_KEY)
    if (remembered.isEmpty()) return emptySet()
    val survivors = mutableSetOf<String>()
    for (id in remembered) {
        val existing = store.eventWithIdentifier(id) ?: continue
        if (!store.removeEvent(existing, EKSpanThisEvent, true, null)) survivors += id
    }
    return survivors
}

/**
 * 本地时刻 → 绝对时刻。走系统默认时区，与 Android 侧算 `DTSTART` 的算法逐字同源
 * （`toInstant(TimeZone.currentSystemDefault())`）。
 */
private fun LocalDateTime.toNSDate(): NSDate =
    NSDate(
        timeIntervalSinceReferenceDate =
            toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds() / 1000.0 -
                NSDATE_REFERENCE_EPOCH_OFFSET,
    )

/** 日志 tag。 */
private const val TAG = "IosCalendarSync"

/** 账本键：上次同步写下的 `EKEvent.eventIdentifier` 集合（落在 `ios_calendar` 这个 prefs 前缀下）。 */
private const val SYNCED_IDS_KEY = "synced_event_ids"

/** 写失败的兜底文案，与 Android `CalendarSyncer` 的 `e.message ?: "写入日历失败"` 同字。 */
private const val SYNC_FAILED_TEXT = "写入日历失败"

/**
 * 1970-01-01 与 `NSDate` 参考时刻（2001-01-01）相差的秒数。
 *
 * K/N 的 `NSDate` 只有 `timeIntervalSinceReferenceDate`，**没有** `timeIntervalSince1970`
 * （2026-11-25 CI 实测，见 `ui/ebike/BikeSensors.ios.kt` 的同名常量）。
 */
private const val NSDATE_REFERENCE_EPOCH_OFFSET = 978_307_200.0

/** 进程级唯一的事件库：第一次真同步才建，启动路径不碰 EventKit（理由见文件头第 3 条）。 */
private val eventStore: EKEventStore by lazy { EKEventStore() }

/** 同步账本。用 `ios_calendar` 前缀，与 `IosServices` 里那几份 store 的命名口径一致。 */
private val prefs by lazy { IosKeyValuePrefs("ios_calendar") }
