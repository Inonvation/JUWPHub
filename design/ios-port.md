# iOS 移植方案（KMP + Compose Multiplatform）

状态：**预研，尚未开工**。开工前本文件是唯一口径；P0 落地后，构建与发布命令迁到
`.agents/rules/ios-build.md`，本文只留决策与规格。

目标：**功能全对齐**，两端长期同仓维护。

数据来源：2026-11-24 对 `app/src/main` 的实测统计（612 个 kt、152,657 行、231 个测试文件）。

## 0. 结论

| 项 | 结论 |
|---|---|
| 目标形态 | `shared`（KMP，含 UI）+ `androidApp` + `iosApp` |
| UI | 共享，不写 SwiftUI 版界面；原生代码只保留系统扩展与硬件访问 |
| 交付物 | CI 出**未签名 IPA**，Windows 侧用 Apple ID 重签安装 |
| 顺序 | **先做 P-1（Android 侧预移植改造），再动 iOS** |
| 墙钟 | 全对齐 5–7 个月，瓶颈是编译与真机点验，不是写代码 |

## 1. 决策

### 1.1 共享 UI，而不是「共享逻辑 + SwiftUI」

| 层 | 文件 | 行数 | 占比 |
|---|---|---|---|
| `ui/` | 299 | 101,346 | 66% |
| `data/` | 179 | 29,658 | 19% |
| `domain/` | 122 | 17,244 | 11% |

只共享逻辑、UI 各写一套，等于此后每个功能写两遍。Compose 是声明式的，平移代价集中在平台 API，
不在界面重画——这是能把 10 万行 UI 搬过去的唯一理由。

代价：iOS 上没有原生控件手感。本 App 的价值在数据与流程，可以接受。

### 1.2 别人怎么管双端

| 项目 | 结构 | 结果 |
|---|---|---|
| JetBrains 官方模板 | `shared` + `androidApp` + `iosApp`，UI 全共享 | 2026-05 起的新默认结构，AGP 9.0 起强制 |
| Duolingo | shared 库 + 原生壳，KMP-first，Umbrella XCFramework 统一输出 | 原生 9 个月的功能 KMP 化用 5 个月；同类再移植降到 1.5 个月 |
| Cash App | 只共享逻辑与持久化，UI 各自原生；共享库另开仓库 | 躲开 Gradle 拖慢 Xcode 构建 |
| Slack（LibSlack） | C++ 共享逻辑，UI 原生 | 2019 年解散团队，回退原生 |

映射：**结构抄 JetBrains，共享边界抄 Duolingo（KMP-first），iOS 构建若被 Gradle 拖慢再学 Cash App。**

### 1.3 模块结构

```
shared/            KMP 库：domain + data + ui
  src/commonMain   跨平台主体
  src/androidMain  actual：WebView / 通知 / WorkManager / Keystore / 高德 Android
  src/iosMain      actual：WKWebView / UNUserNotification / BGTask / Keychain / 高德 iOS / CoreBluetooth
  src/commonTest   231 个测试文件的迁移目标
androidApp/        薄壳：Activity、manifest、res、Glance 小组件、TileService
iosApp/            薄壳：Xcode 工程、SwiftUI 入口、WidgetKit 扩展、Info.plist
```

壳里只允许：平台 API 适配、UI 容器、清单与权限。

### 1.4 必须原生的能力

| 能力 | iOS 实现 | 原因 |
|---|---|---|
| 桌面 / 锁屏小组件 | WidgetKit + SwiftUI | 只能 Swift |
| 灵动岛倒计时 | ActivityKit + Live Activity 扩展 | 只能 Swift |
| 蓝牙水控 | CoreBluetooth（协议字节共享） | 传输层不同 |
| 相机扫码 | AVFoundation（解码逻辑共享） | 传输层不同 |
| 地图渲染 | 高德 iOS SDK，`UIKitView` 包 | 无 KMP 版 |
| 支付宝调起 | iOS SDK，从 Kotlin 侧调用 | 无 KMP 版 |
| 控制中心磁贴 | ControlWidget（iOS 18+） | 建议不做，见 §2 |

## 2. 不可对齐的能力（产品口径必须改）

| 现状（Android） | iOS 上 | 处理 |
|---|---|---|
| WorkManager 15 分钟刷小组件 | WidgetKit 刷新由系统决定 | 文案不承诺实时 |
| 快捷设置磁贴 | ControlWidget 交互窄得多 | 建议砍掉 |
| 后台定时任务（蹲点、提醒） | BGTaskScheduler 不保证时机 | 降级为「打开 App 时补算」 |
| Live Updates 灵动岛 | ActivityKit 可做，全重写 | P3 单独排 |
| 免打扰时段 | 权限模型不同 | 重新走引导 |
| 应用内装 APK 更新 | 不存在 | iOS 端不提供检查更新 |

## 3. Android 侧预移植改造（P-1）

**这段先做，而且只改 Android。** 每一条都是「Android 上现在就正确、且让 iOS 移植只剩换实现」的改动，
行为零变化，231 个纯 JVM 单测是安全网。做完这一段，P1/P2 的返工量降一大截。

**一处纠正（2026-11-24 实测）**：`System.currentTimeMillis` 那 282 处**不是移植障碍**——它们绝大多数是
计时、防抖、动画节拍，各平台都有等价的毫秒时钟，移植时一行 `expect/actual` 就够。真正的成本在
`java.time` 的**类型与格式化**：`DateTimeFormatter` 在 kotlinx-datetime 里没有等价物，自定义格式必须手写。
别再按「调用点数量」估时间成本。

**进度**：

- 第 1 批（哈希）✅ `CampusPaySign` / `QiekjSign` / `QzxyCredential` / `YktKeyboard` 的
  `MessageDigest` → okio `Hashing`
- 第 2 批（Base64 / UUID）✅ `QzxyClData`（`Base64.getDecoder`）、`WenquRules`（`getUrlDecoder`
  + `String(ByteArray, Charset)`）、`QuietHours` / `SelectionWish` / `Shortcuts`（`UUID.randomUUID`
  → `kotlin.uuid.Uuid`）。`domain/` 里 `java.security`、`java.util.Base64`、`java.util.UUID` 已归零，
  2388 个单测全绿。

- 第 3 批（定宽格式化）✅ 新增 `domain/TextFmt.kt`（`pad` / `clock` / `hex2` / `decimals`），
  把 11 个文件里的 14 处 `String.format`、`"%02d:%02d"`、`"%02x"`、`"%04d-%02d"` 全部收成一个入口；
  `decimals` 一开始保留 `String.format` 是在**集中**而不是在清掉（浮点舍入手写会和原显示差一分钱），
  P1 拆模块时它变成 `expect/actual`。**这个决定 2026-11-24 被真机推翻了**：`String(format:)` 与
  `String.format` 的舍入规则不同，见下方 P2 第 4 条硬规律；现在是 commonMain 一份纯 Kotlin 实现。
  新增 `TextFmtTest`（5 条）逐项对照旧 `String.format` 输出。2393 个单测全绿。

- 第 4 批（金额）✅ 新增 `domain/Money.kt`（整数「分」的值对象：`parse` / `text` / `ofLi` /
  `plus` / `minus` / `coerceAtLeast`），把 `java.math.BigDecimal` 从全仓 **39 处降到 0 处**
  （8 个文件：`MoneyFormat`、`QzxyBillStats`、`UnlockFlowState`、`QzxyScreen`、`WaterFlowBlocks`、
  `WaterScreen`、`CampusRechargeUi`、`PowerRechargeSheet`、`CampusCardViewModel`）。
  新增 `MoneyTest`（6 条）+ `MoneyFormatTest` 的 BigDecimal 用例改成 `Money.parse`。
  2399 个单测全绿。
- 第 5 批（护栏）✅ 新增 `scripts/check-domain-portability.ps1` 并接进 CI：扫 `domain/` 下所有
  `java.*` / `javax.*` / `okhttp3.*` / `com.google.zxing.*` 引用，允许清单之外一律失败，
  清单里每项写明欠账原因与剩余处数。负向测试验过（临时塞一行 `java.net.URI` → 退出码 1）。
  作用是把每轮清理的成果钉住，防止下一次随手写回。
- 第 6 批（时间工具链）✅ 加 `kotlinx-datetime:0.8.0`（依赖 kotlin-stdlib 2.1.20，与项目 2.1.21 同线）、
  模块级 `-opt-in=kotlin.time.ExperimentalTime`、`KotlinxDateTimeSmokeTest`（3 条）把可用 API 钉成事实。
  `QzxyCredential` 的 `uppercase(Locale.ROOT)` 改无参重载。2402 个单测全绿。
- 第 7 批（`java.time` 迁移开工）✅ `ScheduleShare` 整体换到 `kotlinx.datetime.LocalDate`，
  自定义格式 `M/d` 手写成 `"${d.monthNumber}/${d.dayOfMonth}"`；`ui/week/ScheduleShareScreen`
  两处调用点用 `toKotlinLocalDate()` 桥接，`ScheduleShareTest` 的 7 处改用 kotlinx 构造器。
  2402 个单测全绿，护栏里 `java.time` 75 → 74。
- 第 8 批（核心试迁，部分回退）：`LocalTimeLike.now()` 改走 `kotlin.time.Clock`
  （`java.time.LocalTime` 从此彻底离开 `domain/`，护栏 74 → 73）。但**核心签名整体迁移
  试了一轮后回退**：把 `parseDate` / `startOfWeek` / `weekNumberOf` / `dateOf` 换成
  kotlinx 类型后，编译器一次性报出 **31 处调用点错误、散在 18 个文件**（`AiSchedulePrompt`、
  `ExamMapper`、`ScheduleOverride`、`TodayState`、`WeekViewModel`、`MeViewModel`、
  `WeekGrid`、三个 jw 页面……），全部是在 java.time 与 kotlinx 之间换手。
  为不把仓库留在半编译状态，这一轮只保留可独立落地的那部分，核心迁移单独排一轮做完。

**核心迁移的实测代价（已按此清单做完，第 9 批）**：18 个文件、31 处调用点，
每处要么 `.toKotlinLocalDate()`（实参侧）、要么 `.toJavaLocalDate()`（返回值侧），
外加 import。典型改动：`ExamMapper` 的 `.toEpochDay()` → `.toEpochDays()`、
`AiSchedulePrompt` 的 `monday.plusWeeks(n)` → `monday.plus(DatePeriod(days = n * 7))`、
`ScheduleBackup` 的 `to.isBefore(from)` → `to < from`。
- 第 9 批（核心迁移完成）✅ `ScheduleCalculator` 的 `parseDate` / `startOfWeek` /
  `weekNumberOf` / `dateOf`×2 / `todayWeekOf` 全部换成 `kotlinx.datetime.LocalDate`；
  18 个调用文件按上面的清单加了桥接，`ScheduleCalculatorTest` / `ScheduleShareTest` 的
  `LocalDate` 改用 kotlinx 构造器，`ScheduleTipsTest` 保留 java.time、只在调核心处转换。
  护栏里 `java.time` 73 → 69，2402 个单测全绿。
- 第 10 批（业务文件整文件迁移）✅ `TeachingCalendar`（`week1Monday` 改用 kotlinx 构造器与
  `minus(DatePeriod)`，`monthValue` → `monthNumber`）和 `ExamMapper`（去掉 `ISO_LOCAL_DATE`
  格式化器，`LocalDate.of` → 构造器，`dayOfWeek.value` → `ordinal + 1`）整文件迁完；
  顺带修了两处下游：`TeachingCalendarParser.plusDays` → `plus(DatePeriod)`、
  `ScheduleTermImport` 的 `plusWeeks(20).plusDays(180)` → 一次 `DatePeriod(days = 320)`。
  两个测试文件的期望值改用 kotlinx 构造器。护栏 `java.time` 69 → 65。
- 第 11 批（时间源 + 护栏补漏）✅ `CampusPaySign.buildTimestamp` 改用
  `Instant.fromEpochMilliseconds(...).toLocalDateTime(TimeZone.currentSystemDefault())` +
  `TextFmt.pad` 手拼 `yyyyMMddHHmmssSSS`；`CampusPaySign` / `YktRechargeSign` 的默认参数从
  `System.currentTimeMillis()` 换成 `Clock.System.now().toEpochMilliseconds()`。
  **护栏补了一条漏洞**：`System.*` 原是盲区（`System.currentTimeMillis()` 在 Kotlin/Native 上
  不存在，但它不以 `java.` 开头），现在也纳入违规模式，正则用 `(?<![A-Za-z.])System\.`
  以放行 `Clock.System`。护栏 `java.time` 65 → 61，`domain/` 里 `System.*` 已清零。
- 第 12 批（整文件迁移）✅ `TranscriptHistory.timeLabel` 改用
  `Instant.fromEpochMilliseconds(...).toLocalDateTime(zone)` + `TextFmt.pad` 手拼
  `yyyy-MM-dd HH:mm`，`zone` 参数从 `ZoneId` 换成 kotlinx `TimeZone`（默认值同改为
  `currentSystemDefault()`）；`TranscriptHistoryTest` 用 `LocalDateTime(...).toInstant(zone)`
  造毫秒，不再依赖 `ZonedDateTime`。护栏 `java.time` 61 → 58。

**成对迁移的约束（下一轮按这个走）**：`EbikeRideCard` 与 `EbikeCardExpiry` 的
`zone` 参数互相传递（`isUsable` → `parseEndAt`、`dayKey`、`latestExpiryText`），
必须同一个提交迁完，否则中间态编译不过。`EbikeCardExpiry` 另有两处要手写：
`"yyyy-MM-dd HH:mm:ss"` 的解析（kotlinx 只认 `T` 分隔，要先把空格换成 `T`）、
`ChronoUnit.DAYS.between` → `daysUntil`。
- 第 13 批（成对迁移）✅ `EbikeRideCard` + `EbikeCardExpiry` 一起迁完（`zone` 参数在两者之间
  传递，必须同提交）：`parseEndAt` 把快趣的 `yyyy-MM-dd HH:mm:ss` 先把空格换成 `T` 再
  `LocalDateTime.parse`，只给日期时回退 `LocalDate.parse(...).atTime(23,59,59)`；
  `remainingText` 的 `ChronoUnit.DAYS.between` → `daysUntil`；`dayKey` 手拼 `yyyy-MM-dd`。
  递归连带 `EbikeCardSavings` 的 `zone` 参数与两个测试文件（`ZoneId.of` → `TimeZone.of`、
  `ZoneOffset.UTC` → `TimeZone.UTC`、`LocalDateTime.of(...).atZone(...)` →
  `LocalDateTime(...).toInstant(zone)`）。护栏 `java.time` 58 → 46。
- 第 14 批（大文件）✅ `PowerUsage` 整文件迁完：`TemporalAdjusters.previousOrSame(MONDAY)`
  → `minus(DatePeriod(days = dayOfWeek.ordinal))`、`ChronoUnit.DAYS.between` → `daysUntil`、
  `atStartOfDay(zone).toInstant()` → `atStartOfDayIn(zone)`、`withDayOfMonth(1)` 直接省掉
  （`monthKeyOf` 只看年月）、`isAfter` → `>`。连带 `PowerBillViewModel` 的 zone 参数、
  `PowerUsagePanel` 三处 `labelOf` 与 `readTimeText`（顺带把一个 `"%02d-%02d %02d:%02d".format`
  换成 `TextFmt.pad`），`PowerUsageTest` 的 zone 与时间构造。护栏 `java.time` 46 → 40。
- 第 15 批（最大一块）✅ `WenquRules`（1174 行、30 处）迁完，两处 kotlinx 没有现成解析器，
  只能手写：
  - `stampMillis` 解析平台的两位年份 `yy-MM-dd HH:mm`——手工拆字段再组装，
    年份按 `2000 + yy` 还原（与 java.time `yy` 口径一致）；
  - `parseHttpDate` 解析 RFC 1123——去逗号后切成六段，月份查 `HTTP_MONTHS` 表，
    时区固定 `TimeZone.UTC`；
  - `nextOrSameSunday` 用 `(SUNDAY.ordinal - today.ordinal + 7) % 7` 代替 `TemporalAdjusters`；
  - 三处 `at.format(ofPattern("HH:mm"))` 换成 `TextFmt.pad(hour):pad(minute)`。
  连带 `WenquTestSupport` 的共享夹具 `zone` 一改，**一次性清掉 52 个测试编译错误**
  （两个测试文件 37 + 15 处都在传这个 zone）。护栏 `java.time` 40 → 33，`Locale` 4 → 3。
- 第 16 批（小步）✅ `CurrentTimetableRules` 整文件迁移（`covers` 的 `isBefore` 边界改成
  `today >= start && today < start.plus(DatePeriod(days = weeks * 7))`），连带唯一生产调用方
  `ScheduleRepository.syncCurrentTimetableToDate`（`LocalDate.parse(...).toKotlinLocalDate()`）
  与 `CurrentTimetableRulesTest`。护栏 `java.time` 33 → 32。

**这批为什么慢**：剩下 15 个文件每一个都带 `LocalDate` 签名，且 `Homework.dueDate` 这类
**Room 实体字段**仍是 java.time——文件级的迁移会连到 UI 调用点和实体类型上。下一批挑
`HomeworkCenter`（4 个 UI 调用点）和 `TodayFormat`（两个共享 formatter，4 处调用）一起做。
- 第 17 批（格式器 + 作业域）✅
  - `TodayFormat` 的两个 `DateTimeFormatter` 常量换成纯字符串函数
    `monthDayText(month, day)` / `shortDayText(month, day)`——入参是整数，java.time 与 kotlinx
    的调用方都能直接用，5 处调用点（`HomeworkCenter`×2、`CourseUi`、`CourseTweakScreen`、`WeekGrid`）零类型改动；
  - `HomeworkCenter` 整文件迁到 kotlinx（`dueLabel` 用 `daysUntil`、`dueDetailLabel` 的
    `dayOfWeek.value` → `ordinal + 1`），连带 7 个 UI 调用点（作业列表 / 详情 / 今日卡 / 待办 / `TodayViewModel`）
    用 `toKotlinLocalDate()` 桥接；
  - **`Homework.dueDate` 仍保持 java.time**（Room 实体字段，动了要连 TypeConverter 与库迁移），
    转换只在 domain 边界做；测试里用 `java.time.LocalDate::parse` 单独构造实体字段。
  护栏 `java.time` 32 → 30。
- 第 18 批（`AppClock` + 账单统计）✅ 新增 `domain/AppClock.kt`（`nowMillis` / `nowDateTime` /
  `today`，唯一「现在」来源，替代散落的 `LocalDate.now()` 与 `ZoneId.systemDefault()`）。
  `QzxyBillStats` 整文件迁移：`parseBillTime` 把服务端的空格分隔换成 `T` 再解析；
  `billRangeBounds` 的 `TemporalAdjusters` / `withDayOfMonth` 换成 `DatePeriod` 与
  `LocalDate(year, month, 1)`；`groupBillsByDay` 的 `LocalDateTime.MIN` 排序键换成毫秒数。
  顺带把 `QzxyBillScreen` 里最后一处 `"%02d:%02d".format` 换成 `TextFmt.pad`。
  护栏 `java.time` 30 → 25。
- 第 19 批（两个单点文件）✅ `TodayBoundary.nextTodayBoundaryMinutes` 的 `today` 换成 kotlinx
  （调用方 `TodayWidgetRefresh` 加一次 `toKotlinLocalDate()`）；`AiSchedulePrompt.calendar` /
  `userMessage` 同样迁移，默认参数改用 `AppClock.today()`，`weekdayLabel(today.dayOfWeek.value)`
  → `ordinal + 1`。两处都是一改就干净的单点，没有中间态。护栏 `java.time` 25 → 23。
- 第 20 批（提醒链上半）✅ `ScheduleTips`（`dayTipFor` / `dismissalTipFor` / `dismissalTip` /
  `rainAfter` 的 `date`+`today`+`now` 全换 kotlinx，`now` 默认值用 `AppClock.nowDateTime()`）与
  `WeatherAdvice`（`referenceDayIndex` / `segments` 的 `now`）一起迁；`WeatherHour.time`
  仍是 java.time（天气模型未动），用 `hour.time.toKotlinLocalDateTime().date` 桥接。
  调用方 `TodayViewModel` 在函数入口转换一次 `appNow`，两条链路共用。
  两个测试文件用 `import kotlinx.datetime.LocalDate as KDate` 别名，把「传给被测函数的日期」
  与「构造 `WeatherHour` 的 java.time 日期」分开。护栏 `java.time` 23 → 20。
- 第 21 批（提醒链下半，最长的一批）✅ `ReminderPlanner` 整文件迁移，牵动三处新能力：
  - `AppClock` 补 `secondsBetween`（kotlinx 没有 `Duration.between(LocalDateTime, LocalDateTime)`，
    两侧折 UTC 相减，偏移抵消）与 `plusMinutes`（可负、可跨天；`DateTimeUnit` 只收
    `TimeBased`，与其跟签名较劲不如自己按「当天分钟 + 天数进位」算）；
  - `startAt` 不再走 `atStartOfDay().plusMinutes`，直接 `LocalDateTime(y, m, d, start/60, start%60)`；
  - 作业提醒的 `due` 仍来自 Room 实体的 java.time `dueDate`，在函数入口 `toKotlinLocalDate()`。
  调用方 `ClassReminder`（通知服务）保持内部 java.time，只在跨进 domain 时
  `toKotlinLocalDateTime()`、把 `plan.startAt` 折回毫秒时 `toJavaLocalDateTime()`。
  三个测试文件（`HomeworkReminderTest` / `ReminderPlannerTest` / `LiveClassUpdateTest`）
  整体换 kotlinx，`LocalDateTime.of(` → `LocalDateTime(`、`LocalTime.of(` → `LocalTime(`。
  护栏 `java.time` 20 → 17。
- 第 22 批（`TodayState` 及其消费方）✅ `TodayState.date` 换 kotlinx、默认值 `AppClock.today()`；
  连带 6 处消费方：`TodayScreen`（`monthValue` → `monthNumber`、传给作业卡时 `toJavaLocalDate()`）、
  `WidgetModel`（`epochMillisAt` 重写成 `atStartOfDayIn(zone) + 分钟数`）、`ScheduleWidget` /
  `WidgetSettingsScreen` / `TodayViewModel`（入口 `toKotlinLocalDate()`）。
  两个测试（`TodayStateTest` / `WidgetModelTest`）在传参处转换。护栏 `java.time` 17 → 16。
- 第 23 批（日期覆盖 + 选课）✅ `ScheduleOverride` 整文件收干净（此前内部 java.time ↔ kotlinx
  来回转换，现在 `slotOf` / `dateOf` / `parseOrNull` / `eachDate` 全 kotlinx，
  三处 `isBefore`/`isAfter`/`plusDays` 换成比较符与 `DatePeriod`）；连带 5 处 UI：
  `SchedulePreviewGrid`（`DAY_MONTH` 格式化器删掉，手拼 `M/d`）、`JwScheduleListScreen`
  （`monthValue` → `monthNumber`，`DayHeader`/`SuspendedCard` 传入前转回 java.time）、
  `WeekScreen`。`CourseSelection.parseTimeRange` 换 `TimeZone` + `toInstant(zone)` +
  `atStartOfDayIn`，`SelectionRoundsTest` 的夹具同步。护栏 `java.time` 16 → 12。
- 第 24 批（天气 + 导出）✅ `Weather` 模型（`WeatherHour.time` / `WeatherDay.date`）换 kotlinx，
  连带 `WeatherApi` 的两个解析辅助、`WeatherScreen`（`now` 走 `AppClock.nowDateTime()`、
  `dayNameOf` 用 `daysUntil`、`hour.time <= currentHour`）、`ScheduleTips.rainAfter` 去掉桥接。
  `ScheduleExporter` 的两个 `DateTimeFormatter` 手写：`M/d/yyyy` 直接拼，
  `h:mm a`（Google 导入模板不吃 24 小时制）按 12 小时制 + AM/PM 自己算；
  `CalendarSyncer` 的 `event.start.toEpochMilli()` 改 `toInstant(zone).toEpochMilliseconds()`。
  `AiScheduleTool.parseDateOrNull` 去掉反向转换。护栏 `java.time` 12 → 5。

> **护栏这次抓到两个 import 扫描漏掉的文件**：`AiScheduleTool` 和 `InboxMessage` 用的是
> **全限定名**（`java.time.LocalDate` 直接写进签名），按 import 分组的统计看不见它们。
> 这就是护栏扫正文而不是只扫 import 的价值。
- 第 25 批（收件箱，P-1 收尾）✅ `InboxMessage.timeGroupOf` 换 kotlinx（`createdAt.toLocalDateTime(zone).date`、
> `today.minus(DatePeriod(days = 1))`、本周一 `today.minus(DatePeriod(days = dayOfWeek.ordinal))`），
> 连带 `InboxGrouping` 的 `zone` 参数、`InboxScreen.timeLabel`（三个 `DateTimeFormatter` 换成手拼）、
> 两个测试（`InboxRulesTest` / `InboxGroupingTest` 的 `ZonedDateTime.of` → `LocalDateTime(...).toInstant(zone)`）。
  **`domain/` 的 `java.time` 到此清零，只剩 `Homework` 那一条刻意保留的 Room 实体字段。**

**P2 的已知欠账**：`domain/` 之外还有 **280 处 `System.currentTimeMillis()`**（挨个做太碎，
随 UI 平移一起换成 `Clock.System.now().toEpochMilliseconds()`）；护栏只扫 `domain/`，
届时要把扫描根目录扩到 `shared/`。

**迁移顺序的实测结论**：叶子文件迁不动。原本想从最小的 `TodayBoundary` 开始，但它
`today: LocalDate` 直接喂给 `ScheduleCalculator.weekNumberOf(…, today)`——只要核心还是
`java.time`，任何日期函数都得先桥接。所以顺序反过来：**先迁核心**（`ScheduleCalculator`
224 行 / 15 处 java.time、`ScheduleOverrides`、`SemesterConfig`），调用方一律
`toKotlinLocalDate()` 桥接；核心干净之后，剩下的 20 多个文件才是纯机械替换。

**`domain/` 剩余 JVM 专有面（2026-11-24 P0 起复核，已从 6 类降到 2 类）**：

| API | 处数 | 卡点 |
|---|---|---|
| `java.time` | 1 处 | 只剩 `Homework.dueDate`（Room 实体字段）。**已查明不痛**：`homework.dueDate` 在 schema v21 里是 `TEXT`，Room 内置转换器存的是 ISO-8601 文本，而 `kotlinx.datetime.LocalDate.toString()` 出的是同一个格式——P1 加一个 `LocalDate ↔ String` 的 `TypeConverter` 就能换类型，**旧数据不需要迁移** |
| `com.google.zxing` | 17 | iOS 无对应，换成中性 `BarcodeMatrix` + `expect/actual`（Android 用 zxing，iOS 用 CoreImage）。这是 P0 之后第一批要做的 |

已清零的 4 类，做法记在这里供后续同类问题照抄：

| 原欠账 | 怎么清的 |
|---|---|
| `javax.crypto`（3 处，`QzxyClData` 的 AES-128） | 自己写纯 Kotlin AES（`domain/AesBlock.kt`），加密面只保留解密 + `NoPadding`。理由是这段只用**硬编码常量密钥**解服务端下发的一张凭据，风险等级低；纯实现让两个平台逐字节一致、可单测，还省掉一套 CommonCrypto 绑定。正确性用 FIPS-197 / SP 800-38A 标准向量钉死，并与 `javax.crypto`（只在测试侧用）交叉核对 |
| `okhttp3`（1 处，`HttpUrl.resolve`） | 自己写 RFC 3986 §5.2 相对引用解析（`domain/UrlRef.kt`），照 §5.4 全套用例 + 本项目 5 类真实形态做测试。只覆盖「解析相对链接」这一件事，不是通用 URI 库 |
| `java.text.SimpleDateFormat` / `java.util.Date`（各 1 处） | 换 `AppClock.dateTimeOf(epochMillis)` + `TextFmt.pad`。注意旧实现用的是 `SimpleDateFormat` 的**默认时区**（`Locale.CHINA` 只管语言不管时区），所以新实现走 `TimeZone.currentSystemDefault()`，不写死 `Asia/Shanghai` |
| `java.util.Locale`（2 处，`TextFmt.decimals`） | 现在 `decimals` 由 `domain/PlatformFormat.kt` 的纯 Kotlin `formatFixed` 实现（**不是** expect/actual）：取 `Double.toString()` 的最短往返表示，按十进制 HALF_UP 做整数串进位。等价于 Java 的 `%.Nf`，`TextFmtTest` 用 420 个值 × 5 档精度对着 `String.format` 逐一比对。原先「交给各平台 printf」的方案已废弃——Foundation 走十进制 HALF_EVEN，`0.05 → 0.0` |

> `String.format` 已在第 3 批清零；`domain/` 里剩下的 `.format(…)` 全是日期格式化，随 `java.time` 一起处理。

**P0 已完成的部分（2026-11-24）**：`shared/` 模块已建（`androidTarget` + `iosX64`/`iosArm64`/
`iosSimulatorArm64`，后者在 Windows 上被 KGP 自动禁用、只留声明），`:app` 用
`implementation(project(":shared"))` 依赖它。

**domain 层主体已搬完**：`commonMain` 里现有 128 个文件（121 个原 `app/.../domain` 的域层文件 +
`TextFmt` / `Money` / `PlatformFormat` / `EbikeQr` / `YktPayCode` / `BarcodeMatrix` / `Barcode`）。

搬的过程中**编译器**（不是正则）抓出 12 类 commonMain 看不见的 JVM 专有 API，全是 JVM 模块默认
导入、平时写得很自然的那种——这条清单值得单独留着，P2 搬 UI 时还会再撞一遍：

| 原写法 | 为什么 JVM 可以 | commonMain 怎么写 |
|---|---|---|
| `Charsets.US_ASCII` / `Charsets.UTF_8` | `kotlin.text.Charsets` 是 JVM 侧 actual | `str.encodeToByteArray()` / `bytes.decodeToString()`（ASCII 数据逐字节等价） |
| `Map.merge(k, v, f)` / `putIfAbsent` | `java.util.Map` 的默认方法 | 读-加-写 `m[k] = (m[k] ?: 0) + v` / `if (!m.containsKey(k)) m[k] = v` |
| `sortedSetOf<T>()`（= `TreeSet`） | JVM 集合 | `mutableSetOf` 收完再 `sorted()`；**注意原代码若依赖 TreeSet 的有序迭代，排序必须补在迭代前**，否则配色分配之类的顺序会变 |
| `map.toSortedMap()` | `TreeMap` | `entries.sortedBy { it.key }` |
| `Character.digit(c, 16)` | `java.lang.Character` | `c.digitToIntOrNull(16) ?: return null` |
| `Character.isHighSurrogate(c)` | 同上 | `c.isHighSurrogate()`（Kotlin 标准库扩展，common 有） |
| `java.lang.Math.*` | 默认导入 | `kotlin.math`；`Math.round` → `roundToLong`（`round` 是 `rint`，0.5 取偶，别搞反），`Math.floorMod` → `Int.mod` |
| `@JvmInline` / `@JvmName` | `kotlin.jvm.*` 默认导入 | 显式 `import kotlin.jvm.JvmInline` / `JvmName`。**去掉注解不行**：`@JvmInline` 少了 JVM 侧报 `Value classes without '@JvmInline'`，`@JvmName` 少了两个擦除后同签名的重载冲突 |
| `java.util.Locale` / `String.format` | JVM | `internal expect fun formatFixed(...)`（见 [PlatformFormat]） |
| `javax.crypto`（AES） | JVM | 纯 Kotlin 实现（`domain/AesBlock.kt`），用 FIPS-197 向量钉住 |
| `okhttp3.HttpUrl.resolve` | JVM 库 | 手写 RFC 3986 §5.2（`domain/UrlRef.kt`） |
| `com.google.zxing.*` | JVM 库 | 中性 `BarcodeMatrix` + `expect/actual`（Android 用 zxing，iOS 用 CoreImage） |
| 跨模块读公开属性后智能转换 | 同模块才行 | `item.task == true` 这类显式比较（P2 搬 UI 时会大量出现） |

**domain 层已全部搬完**（含最后一组卡在分层倒置上的 `QzxyBillStats` / `QzxySessionLink` /
`WenquRules`）。办法不是改规则，而是**把被它们反向依赖的 wire model 一起搬**：
`data/qzxy` 的 `QzxyModels` / `QzxySession` / `QzxyApiConfig`、`data/wenqu` 的 `WenquModels`、
`data/qiekj` 的 `QiekjJson`（含 `LenientString*` 序列化器）——这些本来就只有 `kotlinx.serialization`
依赖，是纯数据。

**包名保持不变**（`edu.jxslu.schedule.data.qzxy` 等），`:app` 侧零 import 改动。代价是 `:shared`
里出现 `data.*` 包：这是有意的，wire model 本来就该两端共用；判断标准是「它有没有平台 API」，
不是「它在哪个目录」。`app/.../domain` 目录已删除，护栏脚本收成单根 + 去掉允许清单。

**这道门禁是可以本地验证的**（这是 P0 最关键的一条工程事实）：`.\gradlew
:shared:metadataCommonMainClasses` 用 Kotlin **公共**标准库编译 `commonMain`，看不见 JDK——
负向测试验过（往 commonMain 塞一行 `import java.util.Locale` → `Unresolved reference 'java'`，
退出码 1）。所以「域层真纯净」这件事**不需要 macOS**，Windows 上就能验，已接进 CI。

两个踩过的坑：
- `commonMain` **不默认导入 `kotlin.jvm.*`**（只有 JVM 模块才默认导入），所以 `@JvmInline`
  必须显式 `import kotlin.jvm.JvmInline`；少了它元数据编译报 `Unresolved reference 'JvmInline'`，
  而只删注解又会在 Android 编译报 `Value classes without '@JvmInline'`——两头都要对。
- 加 `org.jetbrains.kotlin.multiplatform` 插件后，第一次 `--offline` 构建会失败
  （plugin marker 没进离线缓存），联网解析一次即可，之后恢复 `--offline`。

**构建时的一类环境坑**：`:shared:bundleLibCompileToJarDebug` 会报 `classes.jar` 被另一进程占用。
原因是上一轮 Gradle / Kotlin 守护进程还攥着这个 jar。处理办法是 `gradlew --stop` +
杀掉 `kotlin-compiler-embeddable` 的 java 进程，再删掉
`shared/build/intermediates/compile_library_classes_jar/` 让 Gradle 重建。别当代码问题查。

**下一步的前提：已解除**（第 6 批）。`org.jetbrains.kotlinx:kotlinx-datetime:0.8.0` 已加进
`app/build.gradle.kts`，一次联网构建把依赖拉进了本地缓存，之后照旧 `--offline` 构建。
两点要注意：

- 0.8.0 的 `Instant` / `Clock` 由 `kotlin.time` 提供，在 Kotlin 2.1.21 上仍是实验 API，
  已在模块级开 `-opt-in=kotlin.time.ExperimentalTime`（几十个文件逐个加 `@OptIn` 只会淹没改动）；
  Kotlin 2.2 稳定后删掉这行。
- `DateTimeFormatter` **没有**等价物，自定义格式要手写。可用写法已由
  `KotlinxDateTimeSmokeTest` 钉成可编译的事实（`LocalDate` / `LocalTime` / `LocalDateTime` /
  `TimeZone` / `DatePeriod` / `Instant.fromEpochMilliseconds` / `toLocalDateTime` / `atStartOfDayIn`）。

**遗留（P1 必须处理）**：okio 现在是 `okhttp` 的传递依赖，签名函数直接用了它。显式声明
`com.squareup.okio:okio:3.18.1` 后本机 `--offline` 构建会报 `No cached version available`
（okio 根模块与 `kotlin-stdlib-jdk8` 的变体没进离线缓存）。P1 把网络层换成 Ktor 时这条传递链会消失，
到时必须一并显式声明 okio，先联网预热一次缓存再跑离线构建。

| # | 改造项 | 现状（实测） | 目标形态 | 对 Android 本身的好处 |
|---|---|---|---|---|
| 1 | 时间与哈希原语收口 | `java.time` 240 处（其中 `DateTimeFormatter`/`ofPattern` 28 处）、`SimpleDateFormat` 21 处；域内 4 个文件自建 `MessageDigest`；`java.util.Base64` 2 处 | 时间只走 `AppClock` / `AppDateFormat`；哈希改 okio `Hashing`（跨平台，不需要自建抽象） | 测试可注入固定时钟；签名函数不再绑 JVM |
| 2 | 金额收口 | `BigDecimal` 散在电费、一卡通、胖乖 | `Money` 值对象，舍入规则内聚 | 不再各处 `setScale`，对账口径统一 |
| 3 | 网络客户端收口 | ✅ **2026-10-02 的 `AppHttp` 已经做完了**：全仓只有 `AppHttp.base` 一处 `OkHttpClient.Builder()`，其余 20 处引用都是类型声明 | 无需再动 | 连接池、线程池、DNS 缓存已全进程共享 |
| 4 | 会话逻辑纯函数化 | `data/session` 18 文件 1475 行 | 「请求描述」纯函数 + 执行器接口（Android 用 OkHttp 执行） | 教务改版时能单测，不必真机抓包 |
| 5 | 平台能力收口 | `LocalContext` 285、`Intent(` 169、`rememberLauncherForActivityResult` 69 | `PlatformServices` 接口 + Android 实现 | 权限与 Intent 散点归零 |
| 6 | 图标收口 | ✅ **2026-11-24 已完成，但换了做法**：实测不是 456 处而是 424 处 / 167 个不同图标 / 85 个文件。原计划的「`AppIcon` 枚举 + `icon()` 渲染器」不成立——枚举解决的是「换图标只改一处」，而真问题是 **HugeIcons 只有 Android 变体**。改成：生成器反射提取成 CMP 原生 `ImageVector`，落到 `shared/commonMain/.../ui/icon/AppIcons.kt`（167 个 / 519 条 path），调用点 `HugeIcons.Xxx` → `AppIcons.Xxx`，AAR 从 `implementation` 降为 `testImplementation` | iOS 可用；运行时不再打包这个 AAR |
| 7 | 日志收口 | `android.util.Log` 62 处 | `Logger` 接口 | release 可整体静音 |
| 8 | 小组件数据快照 | Glance 组件内直接查库 | `WidgetSnapshot` 纯数据 + JSON | 刷新逻辑可单测；iOS WidgetKit 吃同一份 JSON |
| 9 | 视图边界 | `AndroidView` 20 处内联 | `AppWebView` / `AppMapView` 可复用组件 | 状态管理与复用清晰 |
| 10 | 装配去 Context | `object Graph` 776 行，持 `Context` | `AppGraph(deps)` 构造注入 | 可测；预览与多进程可用 |
| 11 | JS 注入外置 | 注入脚本写在 Kotlin 字符串里 | `commonMain/resources/*.js` | 能直接改、能做语法检查 |
| 12 | 平台判定与 IO | `Build.VERSION` 29、`ContentResolver` 30、`java.io.File` 31 | `Platform.osVersion`、`FileStore` | 版本分支与文件读写集中 |

**不用改的（省力气）**：

- `collectAsStateWithLifecycle` 311 处、`LocalLifecycleOwner` 38 处——CMP 里都有对应物
- 231 个测试文件全是纯 JVM JUnit4，**没有 Robolectric、没有 `androidTest` 目录**，可直接进 `commonTest`

验收（每批都跑）：

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug --offline
```

分批建议：`1+2`（时间与金额）→ `3+4`（网络与会话）→ `5+6+7`→ `8+9+10+11+12`。

## 4. 迁移阶段

### P0 骨架（3–5 天）
建 `shared`（`androidTarget()` + `iosArm64()` + `iosSimulatorArm64()`），`:app` 改名 `androidApp`，
搬 P-1 后的 domain，验证工具链。

```powershell
.\gradlew.bat :androidApp:assembleDebug :androidApp:testDebugUnitTest --offline
.\gradlew.bat :shared:linkDebugFrameworkIosSimulatorArm64
```

### P1 基础设施与 data（3–5 周）

P-1 已把散点收口，这里只换实现：

| 现在 | iOS 侧 | 说明 |
|---|---|---|
| Ktor 未引入 | Ktor 3 + `ktor-client-engine-defaults` | Android 走 OkHttp，iOS 走 Darwin |
| Room 2.7.1 Android | Room 2.7 KMP + `BundledSQLiteDriver` | 实体与 21 级迁移原样保留 |
| DataStore Android | DataStore KMP | 偏好键不变 |
| EncryptedSharedPreferences | Keychain expect/actual | 无 KMP 版 |
| WorkManager | BGTaskScheduler expect/actual | 时机不保证 |
| `java.time` | kotlinx-datetime（P-1 已只剩 `AppClock`） | `DateTimeFormatter` 无等价物，格式要手写 |

KSP 按 target 加：`kspAndroid` / `kspIosArm64` / `kspIosSimulatorArm64`；schema 目录改由 Room Gradle Plugin 声明。

**网络层的收敛程度（2026-11-24 实测，决定 Ktor 替换是一处改动还是两遍返工）**：

- **客户端构造是单点**：`data/net/AppHttp.kt`（55 行）已经把 OkHttpClient 收成一处
  （注释里写着「此前 10 个模块各自 `OkHttpClient.Builder().build()`」），派生入口是
  `withCallTimeout` / `withMaxRequestsPerHost` / `withInterceptors`。
- **但要改的不止那一处**：**24 个文件**直接 import `okhttp3.*`（`Request` / `Response` /
  `Interceptor` / `MediaType` / `HttpUrl`），另有 **4 个文件**用 Retrofit
  （`QzxyApi` / `WenquApi` / `UjingApi` / `QiekjApi` + 两个 Repository 里建 Retrofit 实例）。
- 结论：**Ktor 替换是多文件工程，不是单点开关**。顺序上应该先「把 24 个文件收成一条
  `HttpCall` 抽象」（纯重构、Android 行为不变、可全量单测保），再换 Ktor 引擎——
  反过来做等于把「重构」和「换实现」两件事耦在一起查错。
- **2026-11-24 修正上面的顺序判断**：不必先造一层 `HttpCall` 再换引擎——那会造出一个
  只为过渡存在的抽象，最后还得删。**Ktor 就是终点，直接换**，一个模块一个模块地换，
  每换一个跑全量单测。

**试点已完成（2026-11-24，`WeatherApi`）**——选择它是因为单端点、无鉴权、无 Cookie、
手解析 JSON、自带 DTO，是 24 个文件里最小的一块：

| 事项 | 结论 |
|---|---|
| 依赖 | `:shared` commonMain `io.ktor:ktor-client-core:3.0.3`；androidMain `ktor-client-okhttp`；iosMain `ktor-client-darwin`。**版本锁 3.0.3**：3.1+ 要 coroutines 1.10.x，为一处试点抬全仓协程不划算 |
| 引擎 | **不写进 commonMain**：`HttpClient { … }` 在运行期按平台注册的引擎解析，公共代码里没有任何平台类型 |
| client 复用 | 新增 `shared/.../data/net/SharedHttp.kt`，惰性单例 + 三档超时（connect 15s / socket 20s / request 120s），对应 `AppHttp` 的原有口径。一个 client 一份连接池，别在请求路径上反复 `create()` |
| 必须改的三处 | `Dispatchers.IO` 在 commonMain 不存在（Ktor 的 `get` 本就挂起，那层 `withContext` 直接删）；`String.format` 是 JVM 专有（改 `TextFmt.pad`）；`expectSuccess = false` 必须显式关，否则各模块自己的状态码判断与错误文案全变 |
| 实测 | `:app` 的 `debugRuntimeClasspath` 解析出 `ktor-client-core-jvm` + `ktor-client-okhttp-jvm` 3.0.3；`commonMain` 元数据门禁通过；2418 个单测全绿 |

剩下的 23 个 OkHttp 文件按这个模式逐个搬，建议顺序：`weather`（已完成）→ `repo/UpdateCheck`
→ `repo/CampusCalendarStore` → `wenqu` → `ykt` → `power` → `qiejk` / `qzxy`（这两个是 Retrofit，
顺带把 4 个 API 接口改成 Ktor）→ `session`（Cookie 与 CAS 重定向，最难，放最后）。
`data/net/AppHttp.kt` 在全部迁完之后删掉。

**25 个剩余文件的「第二类耦合」实测（2026-11-24）**——换 Ktor 不是只换 HTTP，每个文件还带别的
JVM/Android 依赖，必须同批处理，否则改完 `:shared` 还是编不过：

| 同时依赖 | 文件数 | 处理 |
|---|---|---|
| `Dispatchers.IO` | **13 / 25** | commonMain 里没有 `Dispatchers.IO`。Ktor 的请求本身是挂起、不占线程，多数情况**直接删掉那层 `withContext`**；确实要做 CPU 重活的换 `Dispatchers.Default`。这是占比最高的一项，别低估 |
| `android.util.Log` | 6 / 25 | ✅ **已解决（2026-11-24）**：`shared/.../platform/Logger.kt` 立好了 expect/actual，Android 落 `android.util.Log`（含 throwable，照旧打完整栈）、iOS 落 `NSLog`。全仓 179 处 `Log.*` 散在 52 个文件里，**按需迁移**（文件搬进 `:shared` 时顺手换），不做一次性全量扫 |
| `BuildConfig` | 3 / 25 | AGP 生成的，`:shared` 没有。做法：改成构造参数/常量，由 `:app` 侧调用点传进来（`UpdateCheckClient` 就是这么改的：无参构造删掉，`JuwApplication` 与 `AboutScreens` 传 `BuildConfig.VERSION_NAME`） |

**已迁移清单（2026-11-24）**：`data/weather/WeatherApi`、`data/repo/UpdateCheckClient`、
`data/wenqu/WenquApi` + `WenquServerClock`、`data/kqcx/KqcxBikeClient`、
`data/ykt/YktClient` + `YktModels`、`data/ujing/UjingApi` + `UjingModels` + `UjingApiConfig`、
`data/power/PowerClient`（+ 抽出的 `PowerException`）。
十一个文件，都验证过 `commonMain` 元数据门禁 + 2418 个单测全绿。剩余 OkHttp/Retrofit 文件 **26 → 18**。

### 迁移中丢掉的两项「共享 base」附带能力（必须在最后一批前补回来）

`AppHttp.base` 除了超时还挂了两个东西，换成 `SharedHttp.create` 后**所有已迁移模块都不再有**：

| 丢掉的能力 | 原作用 | 怎么补 |
|---|---|---|
| `Ipv4FirstDns` | 校园域名同时有 A 与 AAAA 记录，而校园 IPv6 在移动网络下是黑洞（等 TCP 超时多花 10–30s） | Ktor 的 OkHttp 引擎支持 `preconfigured` client，在 `:shared` 的 **androidMain** 里给 `Barcode`/`SharedHttp` 那个引擎挂上同一个 IPv4 优先的 `Dns`。iOS 侧用 Darwin，不需要 |
| `DebugRequestCounter`（debug 才挂的 `EventListener`） | 按 host 统计请求数，冷启动/进页体检用 | 同上，在 androidMain 的引擎配置里挂；或改写成 Ktor 的 `Logging` / 自定义 plugin |

**当前影响有限**：已迁移的六个模块打的是公网域名（open-meteo / ujing.online / china-qzxy 等），
校园 IPv6 黑洞主要影响 `jiaowu.juwp.edu.cn`、`jw.jwxyxx` 这类链路——那些还留在 `:app`、
仍走 `AppHttp`。**但 `jw/JwHttpSession` 一旦迁移，这一项就从「可选优化」变成「必须补」**，
否则教务登录会慢十几到三十秒。补的位置已定（androidMain 的引擎 `preconfigured`），别丢。

### 一处已知的显示回归

`UjingViewModel` 把失败的 `message` 直接显示在洗衣房看板上。OkHttp 的超时异常 message 是
`"timeout"`，Ktor 的是 `Request timeout has expired [url=…, request_timeout=30000 ms]`——
**从短英文变成一长串英文**（迁移前就不是中文，所以不算口吻变化，但更难看了）。
修法是在 `UjingApi` 或 VM 边界把超时归一成中文文案；这一轮没做（不想在网络迁移里顺手改 UI），
记在这里别忘。

`KqcxBikeClient` 那次是这条线上**第一次真正删代码**：它用 `suspendCancellableCoroutine` +
`okhttp3.Callback` 手工桥接「可取消的请求」（因为 OkHttp 的阻塞 `execute()` 中断不了，
拖动地图时前一批撒点还占着连接），Ktor 的请求本身可取消，那层桥接连同 `Call`/`Callback`/
`resume`/`invokeOnCancellation` 一起删掉。**判断一个文件该不该换，先看它有多少代码是在补偿
旧库的缺陷**——那种才是净收益。

**每次都会撞上的三类「搬过去才报错」**（已三次，P2 搬 UI 时会是第四次，先记死）：

| 症状 | 原因 | 修法 |
|---|---|---|
| `Cannot access 'fun xxx'`：it is internal | 定义搬进 `:shared`、调用方留在 `:app` | 放开成 public，并在 KDoc 里写明为什么（`KvcxZones.capped`、`ScheduleExporter.teacherDescription`、`yktCredentialErrorText` 都是这么处理的） |
| `Argument type mismatch: actual 'String?', but 'String' expected` | 对 `:shared` 里的**公开属性**做 `isNullOrBlank()` 之后的智能转换失效 | 显式收口：`orEmpty()` / `== true`（`MarkdownView.item.task`、`YktTurnoverSyncer.orderId`） |
| `Unresolved reference 'android'` / `'java'` | 文件里还藏着 `android.util.Base64`、`java.net.URLEncoder` 这类**只在方法体里出现**的用法 | 逐个换：Base64 → okio `decodeBase64`；URLEncoder → Ktor `FormDataContent` + `Parameters` |

**下一批目标**：`qiekj/QiekjApi`(125) + `QiekjRepository`(469) 是 Retrofit 组合，去掉注解后接口本身
什么都不剩，必须连仓库一起改；`ai/AiScheduleClient`(335) 是 SSE 流式（要 `bodyAsChannel`）；
`jw/JwHttpSession`(324) 是 Cookie + CAS 重定向，`session/*` 全依赖它。这三个都要单独设计，不能批处理。

### 一个没算到的结构性事实：每个客户端都拖着自己的模型与异常

按「20 个 OkHttp 文件」估工期是错的。实际每个客户端都引用同包的 DTO / 异常 / 配置类，
那些还在 `:app` 里，`:shared` 看不见——所以**搬一个客户端 = 搬一条依赖链**。已实测到的链：

| 客户端 | 必须先搬的前置 | 前置的纯度 |
|---|---|---|
| `ykt/YktClient` | `ykt/YktModels`（含 `YktException`） | 纯（只有 serialization），已一起搬 |
| `power/PowerClient` | `power/PowerException` | 纯，已抽成独立文件搬走；`PowerModels` 还有 `java.time`，暂留 |
| `ujing/UjingApi` | `ujing/UjingModels` + `ujing/UjingApiConfig` | 都纯，一起搬 |
| `jw/TranscriptClient` | `jw/PtworkTranscript` → 又依赖 `jw/JwUrls`（声明在 `CourseImporter.kt` 里） | `PtworkTranscript` 有 `URLEncoder` + `java.time`；`JwUrls` 是纯常量但要先从 472 行的 `CourseImporter.kt` 里抽出来 |

**做法**：搬之前先问「它 import 的同包类型在哪」，把那条链列出来；纯的前置可以直接搬
（package 不变，`:app` 侧零 import 改动），带平台依赖的前置只抽需要的那一半（`PowerException`
就是这么处理：`PowerModels` 留原地，只把零依赖的异常类拿出来）。

**并行可行性**：前置清掉之后各文件互不相干，可以并行（本轮 `ujing` 与 `power` 两个 agent 同时在做，
文件集互斥）。但**模型文件是共享前置**，同一轮里不要让两个 agent 同时改同一个 `*Models.kt`。

`WenquServerClock` 那次顺带解决了一类通用问题：**OkHttp 的 `Interceptor` 在 Ktor 里没有等价物**。
它的实现是「发前取钟 → 收后取钟 → 读 `Date` 头算偏移」，改成在 `send()` 里就地记账即可——
不必去找 Ktor 的插件机制。下次遇到 `Interceptor` 就先问「它到底做了什么」，多数时候是一段
能内联进调用点的记账逻辑。

还有一个反复踩的书写坑：**KDoc 里不能出现星号紧跟斜杠**（会把块注释提前闭合）。
`Accept: application/json, text/plain,*/*` 这个头值就踩了两次，编译器只报一串
`Expecting member declaration`，病因得自己往回找。对策是把这类值抽成常量，KDoc 只引名。

**剩余 23 个文件按耦合与行数的实测排序**（`行数 / 额外耦合`，用来定下一批的派活）：

| 易 | 文件 |
|---|---|
| 低（≤ 60 行、无额外耦合） | `session/CookieBridge` 15、`session/CasTransport` 35、`session/MemoryCookieJar` 48、`wenqu/WenquServerClock` 53、`qiekj/QiekjHeaderInterceptor` 58 |
| 中（需处理 Dispatchers.IO / Context） | `qzxy/QzxyApi` 79、`qykcx/KqcxBikeClient` 113、`qiekj/QiekjApi` 146、`ykt/YktClient` 153、`jw/TranscriptClient` 160、`wenqu/WenquApi` 186、`power/PowerClient` 191、`ujing/UjingApi` 258 |
| 高（三种耦合叠加） | `repo/CampusCalendarStore` 245、`kqcx/KqcxAuthClient` 302、`qzxy/QzxyRepository` 314、`jw/JwHttpSession` 324、`ai/AiScheduleClient` 335（SSE 流式）、`qiekj/QiekjRepository` 469 |
| 最后处理 | `data/net/AppHttp` 66（全部迁完才能删）、`ui` 侧 7 个（扫码 Activity 等，属平台外壳） |

**下一批可以并行**：Logger 缝立好之后，文件之间不再有共享前置，可以按上表切成互斥文件集分给多个
agent 同时改（每个 agent 只改自己那几个文件、不跑 Gradle），我统一编译验证。
两个例外必须串行：`jw/JwHttpSession`（Cookie 与 CAS 重定向，`session/*` 全都依赖它）与
`ai/AiScheduleClient`（SSE 流式，Ktor 侧要用 `bodyAsChannel`，是另一种写法）。

验收：`.\gradlew.bat :shared:iosSimulatorArm64Test` 跑通解析器与迁移测试；Android 行为零变化。

### P2 UI 平移（6–10 周）

299 个文件搬进 `shared/commonMain`，依赖换 CMP 坐标。

**改造面（2026-11-24 实测复核，别信之前那句「PlatformServices / AppIcon 已收口」——那两个对象在仓库里根本不存在）**：

| 项 | 实测处数 | 处理 |
|---|---|---|
| HugeIcons（`HugeIcons.Xxx` 直接引用） | **424 处 / 167 个不同图标 / 85 个文件** → ✅ **已完成**（2026-11-24） | 图标**数据**，不适合 expect/actual。做法：一次性生成器把用到的子集从 AAR 里反射提取成 CMP 原生 `ImageVector`（纯 `compose.ui.graphics.vector`，iOS 可用），落到 `shared/src/commonMain/.../ui/icon/AppIcons.kt`，424 处已全部换成 `AppIcons.`，AAR 降为 `testImplementation` |
| `LocalContext` / `Context` / `ContentResolver` | 见下表（`ui/common` 19 个文件、`ui/ebike` 27 个……） | 按「它拿 Context 做什么」分：剪贴板 / 分享 / 权限 / MediaStore / 资源读取，每类一条 expect/actual |
| `Bitmap` / `Intent` / `Activity` 启动 | 同上 | `Bitmap` 走「像素 + 尺寸」中性类型（P0 条码那次已立先例）；`Intent` 与 Activity 启动属平台外壳，留在 `:app` |
| `AppWebView` / `AppMapView` | 2 个组件 | `UIKitView` 实现 |
| 剩余 import 与坐标替换 | 299 文件 | 批量替换 + 逐批编译 |

**各 `ui/` 包的平台耦合度（2026-11-24 实测，用来定搬迁顺序——数字是「有平台 import 的文件数 / 总文件数」）**：

| 包 | 文件 | 有平台 import | 说明 |
|---|---|---|---|
| `internship` / `curriculum` / `scholar` | 1 / 1 / 1 | 1 / 1 / 1 | 单文件页面，最省事，但都要 ViewModel |
| `tweak` / `timetable` / `exam` / `weather` / `inbox` / `tile` / `water` | 2–6 | 1–2 | 耦合低 |
| `today` / `week` / `qzxy` / `me` / `ebike` / `campus` | 5–30 | 4–26 | 主战场 |
| `common` | 67 | 32 | 公共件，动它牵连最广，**应该最后动**（或先只动无平台依赖的那一半） |

验收：Android 全页面截图比对无差异；iOS 模拟器进今日页与课表页。

**已落地（2026-11-24，第一批）**：`:shared` 接上 Compose Multiplatform **1.7.3**（`ui` / `material3` /
`foundation`，加 `org.jetbrains.kotlin.plugin.compose`），`ui/theme` 的 4 个纯文件
（`Color` / `Type` / `SemanticColors` / `Palettes`）搬进 `commonMain`，`commonMain` 元数据编译通过。
这条管道能在 Windows 上验，和 domain 那次一样。

三个实测结论，写下来省得重踩：

1. **CMP 版本必须锁 1.7.3，不能上 1.8.x**。`dependencies` 实跑的结果是
   `androidx.compose.ui` 1.7.6、`runtime` 1.7.8（Glance 1.2.0 抬的）、`material3` 1.3.1，
   都还在 1.7 / 1.3 线上——也就是 Android 侧只吃了两个补丁版本（ui 1.7.4→1.7.6、
   material3 1.3.0→1.3.1），没有换大版本。上 1.8 会把整条 androidx.compose 抬一个大版本，
   Glance 与全部既有 UI 都得重新验，那是另一笔账。
2. **CMP 的 Android 变体会重定向到同名 `androidx.compose.*` 产物**，所以不存在「两份类」，
   也不需要改任何 import（`androidx.compose.material3.*` 照旧）。这是敢直接在 `:shared` 引
   CMP 而不用切全仓坐标的原因。
3. **`internal` 会再犯一次**。`Palettes.kt` 的 `paletteScheme` / `paletteSwatch`、
   `SemanticColors.kt` 的 `ProvideSemanticColors` 原本是 `internal`，调用方留在 `:app`
   就看不见了。P0 搬 domain 时踩过同样的坑（`KvcxZones.capped`、`ScheduleExporter.teacherDescription`）。
   **批量搬 UI 之前先 grep `^\s*internal `**，一次列清楚比逐个撞编译错误快得多。

**下一批要做的两处缝：已完成**（`ui/theme` 整包平移，`:app` 侧目录已删）：

| Android 依赖 | 原位置 | 缝的形状 |
|---|---|---|
| `android.os.Build.VERSION.SDK_INT >= 31` + `dynamicLightColorScheme(LocalContext.current)` | `Theme.kt` | `expect @Composable fun platformDynamicColorScheme(dark: Boolean): ColorScheme?` —— Android 低于 31 返回 null、否则读壁纸主色；iOS 恒返回 null，落回用户选的 palette |
| `Settings.Global.ANIMATOR_DURATION_SCALE` | `Motion.kt` | `expect @Composable fun rememberPlatformAnimationsEnabled(): Boolean` —— Android 读该设置；iOS 读 `UIAccessibilityIsReduceMotionEnabled()`（语义对应，不是数值对应） |

两条缝的共同口径，批量搬 UI 时反复用得上：

- **把「平台支不支持」写进返回值**，而不是让公共代码问系统版本。`ColorScheme?` 返回 null
  就表示不支持，`JuwTheme` 不用知道 `SDK_INT`。
- **expect 要做成 `@Composable` 才能保留 `remember` 语义**。`Motion` 原本是
  `remember(context) { 读设置 }`——换成普通函数就退化成每帧读系统属性。

**第二批已落地（2026-11-24）：课表网格一族 + 两条新缝**

搬进 `commonMain` 的 6 个文件（包名不变，`:app` 调用点零改动）：`ui/week/WeekGrid.kt`(548)、
`ui/week/WeekGridLayout.kt`(200)、`ui/week/GridTypeSpec.kt`(82)、`ui/common/CourseUi.kt`(619)、
`ui/common/WheelPicker.kt`(269)、`data/DefaultData.kt`(49)；另新建 `ui/week/WeekGridPreview.kt`。
预览屏是纯 `@Composable` + 常量数据（不接 ViewModel / Room / DataStore），iOS 根视图改成
`WeekGridPreview` : `LinkValidationScreen` = 3:2，网格渲染与定点小数校验同屏可验。

| 新缝 | Android | iOS |
|---|---|---|
| `expect @Composable rememberAppHaptics(): AppHaptics` | `HapticFeedbackConstants`（CONTEXT_CLICK / CLOCK_TICK / API30+ CONFIRM） | `UIImpactFeedbackGenerator` 一族，四档压三档 |
| `expect @Composable DialogWindowFadeEffect()` | 挂 `windowAnimations`；样式与两个 `@anim` 搬到 `shared/src/androidMain/res/` | 空实现（iOS 没有弹窗 window 这个概念） |

**第四条通用口径**（前三条见上）：**开关类全局状态的注入点用可变 lambda，不用 CompositionLocal**。
`HapticsSettings.isEnabled` 由 `JuwApplication` 把 DataStore 的 `hapticsEnabled` 收集进来——开关只在
手指按下那一刻读一次，不需要触发重组。这条让 `commonMain` 不必认识 `Graph` / `Context`。

**四条踩出来的硬规律**：

1. **同包引用是 import 扫描的盲区**。`CourseUi.kt` 的 import 全是 Compose 与 domain，看着完全可搬，
   实际引用了同包的 `WheelValueDialog`（在 `WheelPicker.kt`）与 `rememberAppHaptics`（在 `Haptics.kt`）。
   判据只能是「搬过去 + 跑 `:shared:metadataCommonMainClasses`」；grep 结论只能用来排顺序，不能下结论。
2. **搬之前先 grep `^\s*internal ` 一次列全**。这批提了 15 个（`weekDate` / `dayLabels` / `TimeRail` /
   `WeekPage` / `EmptyScheduleHint` / `GridLayout` / `buildGridLayout` / `nowMarker` / `MinRowHeight` /
   `GridEndGap` / `shareBoardGridHeightDp` / `NowMarker` / `minHeaderHeightForDateFont` / `GridTypeSpec` /
   `rememberGridTypeSpec`），全部补了「为什么公开」的 KDoc。
3. **Android 资源要跟着代码走**。样式一旦被 `commonMain` 调用，资源就得搬进
   `shared/src/androidMain/res/`（库自己那套 R 是 `edu.jxslu.schedule.shared.R`），留在 `:app` 里
   共享层根本拿不到。**别用「删掉调用」绕过去**——第一版就是那么干的，Android 侧三个滚轮弹窗
   白丢淡入淡出，这一版补回来了。
4. **平台缝必须配一条能在本机跑通的对照测试，否则「两端一致」只是猜的**。定点小数就是这么翻的车：
   `TextFmt.decimals` 走 `expect/actual`，Android 调 `String.format`、iOS 调 `NSString(format:)`，
   假设「都走 printf 家族所以一致」。CI 编译通过，装真机一看 5 个边界值里 3 个不一致——
   Java 是**最短十进制表示 + HALF_UP**，Foundation 是**十进制 HALF_EVEN**，`0.05 → 0.0`。
   现在改成 `commonMain` 里一份纯 Kotlin 实现，`TextFmtTest` 拿 420 个值 × 5 档精度对着
   `String.format` 比对，**本地跑通即等于两端一致**。判据很简单：这条缝的行为能不能在
   没有 iPhone 的机器上被证伪？不能，就别用 `expect/actual`。

**下一批（顺序按「搬完必编译」排，别再按包名猜耦合）**：

| 批 | 内容 | 文件 / 行 | 需先立的缝 | 风险 |
|---|---|---|---|---|
| 1 | `ui/common` 中枢：`AppCard` `SettingsUi` `AppNotice` `SheetDismissIme` `Skeleton` `LoadingHint` `Charts` `CourseDetailSheet` … | 27 / 3,663 | 无（Haptics 已就绪）；4 处 `System.currentTimeMillis()` → `Clock.System`，2 处 internal→public | 低 |
| 2 | 跨包叶子：`ai/AiProposalCard`(955) `ai/SchedulePreviewGrid` `wenqu/WenquCards` `widget/WaterWidgetModels` … | 14 / 2,277 | 无 | 低–中 |
| 3 | 弹窗族：`LegalDialogs` `ConfirmDialog` `LaunchGate` `RechargeDisclaimerDialog` … | 6 / 766 | `SystemClock.elapsedRealtime()` → `TimeSource.Monotonic` | 中 |
| 4 | 页面骨架 + 58 个 `material.icons` 使用者 | 2 + 56 | `:shared` 加 CMP material-icons 坐标（是坐标问题，不是缝） | 中 |
| 5 | 位图 / 附件簇：`AttachmentImage` `MarkdownView` `BitmapSave` `QuickScan` … | 10 / 1,625 | 中性 Bitmap 类型 + 相册 + 文件选择 + 去 Context | 高 |

**别先做**：`material.icons`（58 文件，换坐标会动版本线）与位图簇（一次四缝同时上）。
上面「`common` 应该最后动」的判断已被这批修正：**无平台依赖的那一半必须最先动**，它解锁 100+ 个引用方。

### iOS 验证的成本与纪律（2026-11-24 实测）

一次成功构建的真实分解（run 45386070556，排队 5 秒是运气）：

| 步骤 | 耗时 | 能否省 |
|---|---|---|
| Gradle 编译 + 链接 `Shared.framework` | **166 s** | **不能**。改了 shared 的 Kotlin，klib 与 framework 必须重编重链 |
| 恢复 `~/.konan` 缓存 + Gradle 缓存 | 35 s | 已是缓存命中态；冷缓存 +2–4 分钟 |
| checkout / JDK / Android SDK | 9 s | 不能 |
| `xcodebuild`（Release，不签名） | 16 s | 能（`ios-compile.yml` 不打包） |
| 打包 + 上传 artifact | 5 s | 同上 |
| 收尾 post steps | 48 s | 不能 |

四条纪律，都是为了少跑那 166 秒：

1. **合并后一次验，别改一点验一点**。`:app`（Android）编译是秒级，iOS 侧是 4–5 分钟起步，
   频率差两个数量级。今天为了一处 `when` 穷尽性白等过一轮。
2. **能放 commonMain 的代码一律别放 iosMain**。commonMain 有本地门禁
   （`:shared:metadataCommonMainClasses`，6–18 秒，Windows 上就能跑）；iosMain 只能等 CI。
   数据库自检条就是按这条从 iosMain 搬回 commonMain 的：界面与状态机可本地验，平台侧只留直线调用。
3. **日常迭代走 `ios-compile.yml`**（PR / 手工触发，不打包、不传 artifact），只在要装机时才跑
   `ios-ipa.yml`。
4. **依赖改动攒在一起改**。`~/.konan` 缓存 key 含 `build.gradle.kts` 的哈希，动一次依赖就冷一次；
   `material.icons` 这类坐标能一次补齐就别分三批。

排队时间不受控（macOS runner 容量）：今天见过 5 秒，也见过 27 分钟。**不要把 CI 当编辑器用。**

### P3 iOS 壳与系统扩展（3–5 周）

SwiftUI 容器 + `ComposeUIViewController`；WidgetKit 读 App Group 数据；通知授权引导；Live Activity；扫码。
验收：真机装包，小组件能出课表。

### P4 长尾业务（8–12 周）

`jw` 教务 → `ykt` 一卡通 → `power` 电费 → `qiekj` 胖乖 → `kqcx` 快趣 → `wenqu` 问渠 →
`ujing` U净 → `xg` 学工 → `ai` 调课 → `qzxy` 水控 → `weather` 天气 → `calendar` 校历。

每域验收：同一份测试数据，两端结果一致。

## 5. 工期与分工

| 阶段 | 谁做 | 墙钟 |
|---|---|---|
| P-1 预移植 | 我批量改，你跑命令 | 2–3 周 |
| P0 骨架 | 我 | 3–5 天 |
| P1 基础设施 | 我改，卡在 iOS 编译 | 3–5 周 |
| P2 UI 平移 | 我批量改，你抽查截图 | 6–10 周 |
| P3 iOS 壳 | 我写，你真机点验 | 3–5 周 |
| P4 长尾 12 域 | 我逐域改 | 8–12 周 |
| **合计** | | **5–7 个月墙钟** |

**能压缩什么、不能压缩什么**（别按「AI 写代码很快」估）：

- 能吃掉：1120 个改造点、299 个文件搬迁、批量 import 替换、模板化的 expect/actual
- 吃不掉：每次 Kotlin/Native 编译 5–15 分钟的等待；iOS 证书与真机环节；教务 SSO、蓝牙水控、
  WKWebView 注入这些只能在真机上判定的行为
- 结论：瓶颈从「写代码」变成「编译与点验」，人工估的 8–9 个月压到 5–7 个月

## 6. 风险

| 风险 | 触发条件 | 应对 |
|---|---|---|
| 教务 SSO 在 iOS 上行为不同 | Cookie / UA / 重定向判定 | P-1 把会话写成纯函数，P1 用真实账号回归 |
| 高德 iOS SDK 集成 | CocoaPods / 手动 framework + cinterop | Swift 侧封装，`UIKitView` 暴露，不走 cinterop |
| 蓝牙水控协议 | MTU、写特征差异 | 真机实测，代码审查判不了 |
| 免费侧载 7 天 | 日常使用 | 长期使用买 ¥688 开发者账号 |
| 迁移期间搅动 Android | P-1/P2 覆盖现有功能 | 每批改完跑全量单测；CI 已就位 |
| AGP 9.0 | 未来升级 | 先按新结构分模块，避免二次搬迁 |

## 7. 构建与分发（IPA）

出 IPA 必须有 macOS。本仓库是公开仓库，GitHub Actions 的标准 runner
**在公开仓库上免费且不限量**，macOS 也在内——不需要买 Mac：**CI 出未签名 IPA，Windows 本地重签。**

- 产物：`JUWPHub-<version>-unsigned.ipa`
- 重签：Sideloadly / 爱思助手 / SideStore，用自己 Apple ID
- 免费账号 7 天、同时 3 个 App；付费账号 ¥688/年管 1 年
- Bundle ID 固定 `edu.jxslu.schedule`（iOS 高德 key 绑它），debug 变体 `edu.jxslu.schedule.debug`
- iOS 高德 key 与 Android 两把分开申请，落 `keystore.properties` 的 `amapKeyIos`

```yaml
name: iOS CI
on:
  push:
    branches: [main]
    tags: ['v*']
  pull_request:

jobs:
  build-ipa:
    runs-on: macos-15
    timeout-minutes: 60
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: '17' }
      - uses: gradle/actions/setup-gradle@v4

      - name: 共享模块 iOS 单测
        run: ./gradlew :shared:iosSimulatorArm64Test

      - name: 构建未签名 IPA
        run: |
          xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp \
            -configuration Release -sdk iphoneos -derivedDataPath build \
            CODE_SIGNING_ALLOWED=NO CODE_SIGNING_REQUIRED=NO CODE_SIGN_IDENTITY="" \
            build
          mkdir -p Payload
          cp -R build/Build/Products/Release-iphoneos/iosApp.app Payload/
          zip -qry JUWPHub-unsigned.ipa Payload

      - uses: actions/upload-artifact@v4
        with:
          name: unsigned-ipa
          path: JUWPHub-unsigned.ipa
          retention-days: 90
```

KMP framework 由 Xcode 的 Build Phase 调 `./gradlew :shared:embedAndSignAppleFrameworkForXcode` 产出，
不在 workflow 里单独跑。

版本号单一来源：`gradle.properties` 的 `versionName` / `versionCode`，iOS 由
`scripts/ios-gen-version.sh` 写成 `iosApp/Config/Version.xcconfig`；同一 tag 双产物。

**2026-11-24 实测补充**（真实 workflow 与上面这段草稿不同，以 `.github/workflows/` 下两份文件为准）：

- push 只由 `ios-ipa.yml` 接管（Android SDK + link arm64 + xcodegen + xcodebuild + 打包），
  `ios-compile.yml` 只留 PR 与手工触发——两个 macOS workflow 同时排队会抢不到 runner
  （实测报 `The job was not acquired by Runner of type hosted`）。
- **`Info.plist` 必须有 `CADisableMinimumFrameDurationOnPhone = true`**，且**不能让 plist 变成生成物**
  （XcodeGen 的 `info:` 或 `GENERATE_INFOPLIST_FILE=YES` 会静默冲掉这个键）。CMP 1.7 起由
  `PlistSanityCheck.performIfNeeded` 强制校验，缺键 `terminateWithUnhandledException` → SIGABRT。
  处理路径（崩溃报告怎么拉、栈顶怎么认）写在 `.agents/rules/build-release.md`「iOS 侧载包」一节，
  这里不重复。
  一处实测与文档不一致，如实记下：2026-11-24 那版 IPA **漏了这个键但仍能进入界面**（iPhone 13 类机型），
  所以「缺键必崩」这条在不同机型/版本上并不一致。**结论不变**：按「必须有」处理，不要拿真机去试
  「没有行不行」——省下的是十几分钟的 CI，赌上的是「用户装上去点不开」。
- 必须 `-configuration Release` + `ENABLE_DEBUG_DYLIB=NO`：Debug 会把代码塞进
  `JUWPHub.debug.dylib`（实测 59 MB），主二进制只剩壳，侧载后容易起不来。workflow 里有断言。
- Kotlin 侧仍链 **debug** framework（`linkDebugFrameworkIosArm64`）：它与 Xcode 的优化级别无关，
  功能等价且 CI 快。出正式分发包时再换成 `linkReleaseFrameworkIosArm64`。

**不做**：不上架 App Store（抓第三方数据、非官方身份，审核过不了）；不用企业签/超级签分发（违规，
随时集体吊销）；不把证书进 CI secrets（免费证书 7 天失效，签出来对任何设备都无效）。

## 8. 双平台同步纪律

1. `shared/` 是唯一逻辑实现，壳里出现业务逻辑就是错。
2. 功能 PR 三件齐：shared 实现 + 两端验证记录（或 `ios-pending` 标签）。
3. 新功能先写共享层再包两端壳（KMP-first），不先写 Android 再移植。
4. 能力差异只登记在 §2，不在 PR 描述里散落。
5. `commonTest` 两个 target 都跑，iOS 侧失败不能标 ignore。
6. 依赖选型先问「有没有 KMP 版本」，没有的进 §1.4 原生清单。
7. iOS 专属实现只放 `iosApp/`。
8. 同 tag 双产物；iOS 落后时落后的是壳，不是逻辑。

## 9. 起步顺序

1. **P-1 第一批**：时间源 + 金额收口（改造项 1、2），改完跑全量单测。
2. **P-1 第二批**：网络收口 + 会话纯函数化（3、4）——这一步决定后续会不会翻车。
3. **并行**：申请 iOS 高德 key（绑 `edu.jxslu.schedule`），不占工时。
4. 之后按 §4 的 P0 起骨架。
