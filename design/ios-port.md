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
  `decimals` 暂时保留 `String.format` 是在**集中**而不是在清掉（浮点舍入手写会和原显示差一分钱），
  P1 拆模块时它变成 `expect/actual`，`String(format:)` 在 iOS 上语义一致。
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
| `java.util.Locale`（2 处，`TextFmt.decimals`） | `TextFmt` 搬进 `:shared/commonMain` 后，`decimals` 改成 `internal expect fun formatFixed(value, places)`：Android 用 `String.format`，iOS 用 `NSString(format:)`。**没有**手写浮点舍入——`1.045` 这类值「乘 100 再 round」会偏差一分（double 里 `1.045 * 100` 正好落在 `104.5`），金额显示扛不住，只能交给各平台的 printf |

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
| `android.util.Log` | 6 / 25 | 需要先立 `Logger` 缝（P-1 第 7 项，全仓 62 处）。所以网络迁移与日志收口**必须并行**，否则这 6 个文件改到一半会卡住 |
| `BuildConfig` | 3 / 25 | `BuildConfig` 是 AGP 生成的，`:shared` 没有。要么把这些值搬成构造参数/常量，要么立一条 `expect` 读 debug 标志 |

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
