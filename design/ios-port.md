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

**仓库层 `data/repo` 搬移结果（2026-11-24，S4 批）**：43 个文件 / 6,927 行里搬走 **22 个 /
4,637 行（51% 文件、67% 行）**，留下 21 个 / 2,245 行。判据仍是「搬过去 + 跑门禁」，
不是 import 扫描。

搬走的两段：纯数据仓库（`ScheduleRepository` `ScheduleBackup` `InboxRepository` 四个
`*Repository` 与 `RideRecordStore`）与同步编排（`ScheduleSync` `ScheduleTermImport`
`SelectionSync` `ExamSync` `ScoreSync` `ScholarProgressSync` `TextbookSync` `CurriculumSync`
`TeachingCalendarSync` `SelectionCenterClient` `ProfileSync` `SyncGate` `SubpageScreen`
`ProfileSyncRules`）。过程中立的四条缝（各只有一个实现，没有第二套）：

| 缝 | 为什么非立不可 |
|---|---|
| `data/local/RoomTransaction.inTransaction`（expect/actual） | `androidx.room.withTransaction` **只在 Room 的 Android 变体**里（commonMain 元数据编译直接 Unresolved）；`useWriterConnection` 虽在 commonMain 但语义不同（Android 的库是 `databaseBuilder` 打开的），且 `importBackup` 外层事务里还套着内层事务。所以 Android 继续用 `withTransaction`、iOS 用 `useWriterConnection + immediateTransaction`，iOS 侧带嵌套闸门 |
| `data/session/JwReadSession` + 结果类型 | `CasSession` 是 OkHttp + WebView cookie 桥，留在 `:app`；编排只认 `ensureValid` / `fetchHtml` 两条读语义。结果类型搬过去后 `:app` 二十多个调用点 import 零改动 |
| `data/repo/ScheduleBaseline` / `SelectionRoundsSource` / `ExamSnapshot` | 三个「filesDir 下一份 JSON」的快照仓储，模型与语义可以上移，`File` 与 `Context` 留在 `:app`（实现类只剩 `JsonFileStore` 一层） |
| `SubpageScreen` 枚举 | 纯枚举，却卡住五个 Sync（收件箱落点要用 `.name`）。包名不变，`:app` 四十多处引用零改动 |

**留下的 21 个文件与逐个理由**（`data/repo` 下按行数降序）：

| 文件 | 行 | 留下的唯一阻碍 | 谁来解锁 |
|---|---|---|---|
| `CampusCalendarStore` | 244 | `okhttp3.OkHttpClient` 直接建实例下载校历图（该模块尚未 Ktor 化） | 走「逐模块换 Ktor」那条线 |
| `SelectionCenterHttpClient` | 234 | `CasSession` 的**写语义**（`postForm` / `fetchForAction`）+ `SelectionDebugDump`（`File`）+ `java.net.URLEncoder` / `Collections.synchronizedSet` | 先把写语义两条也放进 `JwReadSession`，再给 debug dump 一道口 |
| `DormRepairSync` | 230 | 同时依赖 `DormRepairStore`（`File`）与 `DormRepairWebClient`（WebView 读 office 会话）+ `cas.hasCookies()` | 报修链路整体（含 WebView 桥）移植时一起搬 |
| `QuietHoursQueue` | 157 | `File` + `MirroredJsonStore`（免扰队列落盘） | 文件 IO 缝 |
| `AiApplyHistoryStore` | 154 | `Context` + `MirroredJsonStore` | 同上 |
| `InternshipBasesLoader` | 130 | `InternshipSnapshotStore`（`Context` + `File`，且快照行→领域的 `rowToBase` 也是它） | 文件 IO 缝 + 快照模型上移（照 `ExamSnapshot` 的办法） |
| `ImageImport` | 127 | `Bitmap` / `BitmapFactory` / `ExifInterface` / `ContentResolver` / `Matrix` | P2 第 5 批「位图簇」的中性 Bitmap 类型 + 相册 |
| `AppCacheCleaner` | 118 | `Context` + `WebView` / `WebStorage` + `File` | 缓存清理属于平台外壳，等 P3 的壳侧缝 |
| `AiImageStore` | 111 | `Context` + `BitmapFactory` 解码缩略图 + `Uri` | 同「位图簇」 |
| `AttachmentStore` | 98 | `Context`（`filesDir`）+ `Uri` + `SimpleDateFormat` 文件名 | 文件 IO 缝 |
| `InternshipSnapshotStore` | 95 | `Context` + `File`（实习基地快照） | 文件 IO 缝 |
| `TranscriptStore` | 91 | `Context` + `File` + `FileProvider`（盖章成绩单 PDF 分享） | 文件 IO 缝 + 分享缝 |
| `ScheduleBackgroundStore` | 81 | `Context` + `Uri`（相册取图）+ `File` | 相册缝（P2 第 5 批） |
| `CrashReporter` | 74 | `Context` + `File` + `PrintWriter` / `StringWriter` / `SimpleDateFormat` | 崩溃上报本身该走平台侧（iOS 用 `NSSetUncaughtExceptionHandler`），不硬搬 |
| `DormRepairStore` | 68 | `Context` + `File` | 文件 IO 缝 |
| `ScheduleShareStore` | 61 | `Context` + `File` + `androidx.core.content.FileProvider` | 文件 IO 缝 + 分享缝 |
| `WenquCertStore` | 40 | `Context` + `File` + `FileProvider` | 同上 |
| `SelectionDebugDump` | 37 | `File` + `java.time.LocalDateTime` / `DateTimeFormatter` | KMP 没有 `DateTimeFormatter` 等价物，格式要手写；debug 专用，不值当 |
| `ExamSnapshotStore` | 34 | **只剩实现**：模型与接口已上移到 `ExamSnapshot`，`File` + `Context` 留在这里 | 文件 IO 缝 |
| `SelectionRoundsStore` | 33 | 同上（`SelectionRoundsSource`） | 文件 IO 缝 |
| `ScheduleBaselineStore` | 28 | 同上（`ScheduleBaseline`） | 文件 IO 缝 |

一条结论：**下一刀应该切在文件 IO 上**。`okio` 已在 `:shared` 里，但 `FileSystem.SYSTEM` 在
commonMain 不可见（S3 已踩），所以还差一条 `expect fun platformFileSystem(): FileSystem`
（Android/iOS 各返回各自的 SYSTEM）。补上它，上表里 9 个纯 JSON 快照类（≈900 行）能一次搬完，
`SelectionCenterHttpClient` 之外还顺手解放 `AttachmentStore` / `TranscriptStore` 一族。

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

**第五个反复出现的坑（已踩两次）**：RegexOption.DOT_MATCHES_ALL 只在 JVM 的 `kotlin.text` 里有，commonMain 一律换成正则内的 [\s\S]。S1 的 `EmphasisText`、S3 的 `CurriculumParser`（5 处）都是元数据门禁当场报出来的。同类还有 `String(CharArray)` → `concatToString()`。搬文件前先 grep 这两个名字，能省一轮。

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

iosMain 的**语法错误**本地一条都发现不了，代价是一次完整往返（2026-11-25：合并 S17/S18 时
`MainViewController.kt` 多留了一个 `}`，Kotlin/Native 报 `308:1 Syntax error: Expecting a
top level declaration`，整个 iosMain 编不过，白等 6 分钟）。合并动过 iosMain 的分支后，
先用括号平衡扫一遍再推——纯词法检查，忽略注释与字符串字面量即可，27 个文件不到 1 秒。

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
- Bundle ID 固定 `edu.jxslu.schedule.ios`（`iosApp/project.yml` 的 `PRODUCT_BUNDLE_IDENTIFIER`；
  iOS 高德 key 要绑这个全串）。Android 侧仍是 `edu.jxslu.schedule` / `.debug`，两端不同串不影响共存。
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

### 7.1 macOS runner 标签实测（2026-11-25）

用 `.github/workflows/runner-probe.yml` 一次性问出来的结果，别再靠猜：

| 标签 | 实际镜像 | 架构 | macOS | Xcode | 可用 |
|---|---|---|---|---|---|
| `macos-15` | `macos-15-arm64` | arm64 | 15.8.2 | 16.4 | ✅ 主力 |
| `macos-26` | `macos-26-arm64` | arm64 | 26.7.2 | 26.6 | ✅ 备用池 |
| `macos-latest` | `macos-26-arm64` | arm64 | 26.7.2 | 26.6 | ✅ 等价于 26 |
| `macos-15-intel` | — | **x86_64** | — | — | ❌ 架构不对 |
| `macos-14` | — | — | — | — | ❌ 该标签已无 arm64 机器 |

**两种 `cancelled` 要分清**，这是本节唯一容易误判的地方：

| 现象 | 含义 | 处置 |
|---|---|---|
| 立刻 cancelled，`steps = 0`，注解 `The configuration 'xxx-arm64-us-default' is not supported` | **标签不支持** | 换标签，重试无用 |
| 排 15 分钟后 cancelled，`runner_name` 为空，`steps = 0` | **池容量不足** | 等一会儿重试，或换到另一个 arm64 池 |

第二种在 2026-11-24 16:11Z~16:45Z 连续命中 3 次（`45424609726` / `45425989497` / `45434305047`），
同期 GitHub 状态页报 All Systems Operational，队列深度 0——纯容量争用。当时 `macos-15` 池
恢复后同一个 commit 立刻跑起来了。**容量不足时换 `macos-26` 是可行的备用通道**（独立池、
arm64、Xcode 26.6）；但 Xcode 26 与 Kotlin 2.1.21 的兼容性未实测，换过去若失败先怀疑这一层。

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
3. **并行**：申请 iOS 高德 key（绑 `edu.jxslu.schedule.ios`），不占工时。
4. 之后按 §4 的 P0 起骨架。

## 10. M1 / M2 现状（2026-11-25）

本节记录「数据层搬完之后」这一批做了什么、还差什么。前面的 §4/§5 是规划，这一节是实况。

### 10.1 已立的平台缝

| 缝 | 位置 | Android | iOS |
|---|---|---|---|
| 服务装配 `AppServices` / `Services` | `platform/AppServices.kt` | `GraphServices`（转接 `Graph`，`Graph` 本体未改） | `IosServices` |
| `collectAsStateWithLifecycle` | `platform/LifecycleState.kt` | 转调官方实现（行为一字不改） | 退化到 `collectAsState` |
| `locked` | `platform/Lock.kt` | `synchronized` | 直接跑（不加锁） |
| `ResumedTickerEffect` / `rememberResumedNow` | `ui/common/ResumedTicker.kt` | `repeatOnLifecycle(RESUMED)` | 无暂停条件 |
| `platformFileSystem()` | `platform/FileSystem.kt` | `FileSystem.SYSTEM` | `FileSystem.SYSTEM`（okio Posix） |
| WebView | `ui/web/WebViewController.kt` | `AndroidView` + `WebView` | `UIKitView` + `WKWebView` |
| 共享单车地图 | `ui/ebike/BikeMapContract.kt` | `BikeMapHost` → `AmapMapView` / `OsmMapView`（`:app`，须留在那边：依赖 `com.amap.api` / osmdroid） | `IosBikeMap`（`shared/iosMain`，`platform.MapKit`） |

**`lifecycle-runtime-compose` 不能进 `commonMain`**：它的 KMP 变体要 Compose 1.9，本仓锁 CMP 1.7.3。
所以只加在 `androidMain`，给 `LifecycleState.android.kt` 转调用；`commonMain` 里那份是 expect。

### 10.2 已搬进 commonMain 的页面（2026-11-25 02:30 盘点）

共享层 `ui/` 现在有 23 个包，`:app/ui` 剩下的见 §12.2。

| 包 | 已搬的内容 |
|---|---|
| `ui/week` | `WeekScreen` + `WeekViewModel` + 网格一族（课表主屏，两端的唯一实现） |
| `ui/jw` | `JwHubScreen`、`JwScheduleListScreen`、`TextbookScreen`、`TeachingCalendarScreen`、`StudentInfoScreen` |
| `ui/score` / `ui/exam` | 成绩页 + 成绩设置、考试页 + 考试设置 |
| `ui/selection` | 选课页 + 选课中心 / 预选清单 / 已选课程 |
| `ui/notes` / `ui/homework` | 笔记库 + 课程分组、作业库 / 待办 / 课程分组（详情页仍在 `:app`） |
| `ui/inbox` / `ui/weather` | 收件箱、天气页 |
| `ui/me` | 设置首页、数据与备份、显示设置面板、账号卡、`UpdateAvailableDialog` |
| `ui/life` | 电费账单、电费充值弹层、侧边栏内容（`SideDrawerParts` + `SideDrawerGroups`） |
| `ui/campus` | 消费流水（`StatementScreen`）、校园卡设置（`CampusCardSettingsScreen` + `CampusCardViewModel`）、付款码（`PayCodeScreen` + `PayCodeViewModel`）、充值弹层与到账弹窗（`CampusRechargeUi`）、余额变化补拉（`YktBalanceChangeSync`）、结果总线（`PayCodeResultBus` / `CampusBalanceSync`） |
| `ui/water` | 开水房整包（页 + VM + 流程块 + 入口弹层） |
| `ui/wenqu` | 问渠整包（主页 / 蹲点 / 记录 / 设置 / 活动弹层 / 卡片） |
| `ui/ujing` | U净三页（主页 / 下单 / 历史）+ VM |
| `ui/ai` | AI 调课整包（对话 / 设置 / 历史 / 提议卡 / 预览网格） |
| `ui/common` | 61 个文件：对话框、卡片、Markdown 栈、附件图槽、快捷方式、账户卡、`KeepScreenOn`、`AppBackHandler`、`LegalDialogs`（S22 随引导一起搬上来，`rememberCloseLock` 由此全仓只剩一份）等 |
| `ui/today` | `TodayScreen` + `TodayViewModel` + 进行中服务卡（`ActiveServiceCard`）+ 猜你想用卡 + 收件箱卡 + `TodaySlots`（V1 2026-11-25；服务卡 V2 同日：开水 / 趣智 / U净 三行随之搬走，快趣行仍由 `:app` 注入槽位） |
| `ui/onboarding` | 首启引导六屏（`OnboardingScreen`，S22 2026-11-25）+ 两个平台槽位类型 `OnboardingJwLogin` / `OnboardingBrandIcons`；接法见 §10.6 |
| `ui/ebike` | **E2（2026-11-25）**：`BikeMapViewModel`（附近单车地图页的 VM，产出 `BikeMapInput`）、`KvcxRideController`（本机用车编排，出码页与地图页共用一份）、`performReturnBike`（还车 + 结算口径的唯一实现）、`EbikeRideSignal`、以及 `ui/ebike` 的位置 / 罗盘三件（S23）。**页面本身（`RideScreen` / `RidePanels` / `KvcxScreen` / 出码页）与高德视图仍在 `:app`**，见 §11.3 |

跨模块 `internal` 会失效：`TopBarHeight`、`GridTypography`、`quickCheckFeedback`、`OutcomeQueue`、
`SchedulePreviewGrid` 的 `changedCellSet` / `previewWeeksOf` 因此放开成 `public`，KDoc 里各自写了原因。
E2 同类放开：`KvcxAction` / `KvcxRideState` / `KvcxNotice` / `KvcxReturnSummary` / `KvcxSideEffects` /
`KvcxConfirm` / `kvcxConfirmDialog` / `kvcxNeedsConfirm` / `kvcxErrorText` / `carryKnownRideFields` /
`hasRideMetrics` / `adoptConfirmedUnlock` / `rideElapsedSeconds` / `rememberRideElapsed` /
`postKvcxReturnInbox` / `UNLOCK_COOLDOWN_MS` / `unlockCooldownSecondsLeft` / `UNLOCK_SYNC_*` /
`RIDE_CARD_*` / `ORDER_GONE_CONFIRM_DELAY_MS` / `DEFAULT_PANEL_HEIGHT_DP` / `MIN_PANEL_HEIGHT_DP` /
`MAX_PANEL_HEIGHT_DP` / `USER_ANCHOR_RADIUS_METERS`。

### 10.3 还差的（按优先级）

1. **iOS 教务会话**：`JwReadSession` 目前只有 OkHttp 实现（`:app` 的 `CasSession`）。要 Ktor 版
   才能让 iOS 抓课表。WebView 已经能登录并读到 Cookie（`WebViewController.getCookieHeader`），
   缺的是「Cookie → Ktor 会话 → 解析 → 落库」这条链的最后一节。
2. **iOS 文件选择 / 另存**：`onPickJsonFile` / `onSaveTextFile` 在 iOS 侧没有实现，
   JSON 导入导出因此不出现。要 `UIDocumentPickerViewController`。
3. **iOS 系统日历写入**：`onSyncCalendar` 没有实现，分享弹层里那一行不出现。
4. 今日页**已搬完**（V1 页面 + V2 服务卡，2026-11-25）。服务卡在 iOS 上只接
   **开水（胖乖，HTTP）**与 **U净 洗衣**两条链；快趣骑行（高德 SDK）与趣智开水（蓝牙）
   由宿主注入槽位、iOS 传 `null` 整行不渲染；品牌位图与扫码在 iOS 侧仍缺。
### 10.4 充值与支付在 iOS 上的边界（2026-11-25 核查）

四块充值**都不是 App 自己收钱**，都是把用户交给外部支付工具。因此可移植性取决于
外部 App 的 URL scheme 在 iOS 上是否存在，而不是取决于我们的代码。

| 充值 | Android 实际机制 | iOS 结论 |
|---|---|---|
| 一卡通 | `POST /charge/order/thirdOrder` → 302 收银台 URL；另有「直拉微信」捷径：`wx.tenpay.com/.../checkmweb?prepay_id=…` 的中间页里取 `weixin://wap/pay?prepayid=…`，`ACTION_VIEW` 拉起 | **能，但不照搬捷径**（见下） |
| 胖乖生活 | 支付宝小程序深链 `alipays://platformapi/startapp?appId=2018072460764274&page=user/recharge/buyGold/…` | 能，零成本 |
| 趣智校园 | 支付宝小程序深链 `alipays://platformapi/startapp?appId=2018090661238647&page=pages/recharge/recharge` | 能，零成本 |
| 寝室电费 | 聚合收银台：从 `paymentcashierStr` 的 HTML 表单解析出 `/pay?ticket=…`，浏览器打开 | 能（`java.net.URLEncoder` 要换掉） |

**一卡通在 iOS 上必须走兜底那条。** `weixin://wap/pay?prepayid=…` 是微信 H5 支付深链，
商户域名与 referer 要匹配；Android 的直拉链路保留了正确来源，iOS 从原生 App 直接
`openURL` 拉起很可能被微信拒（「商家参数格式有误」）。正确做法是把收银台 URL
（`/payment?orderid=…&token=<JWT>`）放进 `WKWebView` 打开，让页面自己跳 `weixin://`。
我们已经有 `ui/web/WebContentView` 这条缝，直接复用。

**Info.plist 必须声明 `LSApplicationQueriesSchemes`**：`alipays`、`weixin`。
少了这两条，`canOpenURL` 恒回 false，表现为「点了充值没反应」而不是报错。

**待补的缝**：把「打开外部 URL / 自定义 scheme」收成平台能力
（`expect fun openExternalUrl(url: String): Boolean`），Android 走 `ACTION_VIEW`，
iOS 走 `UIApplication.openURL` + scheme 白名单检查。一卡通、胖乖、趣智、电费四处共用。

### 10.5 胖乖迁完后刻意留在 `:app` 的三份存储

| 文件 | 为什么没搬 |
|---|---|
| `QiekjTokenStore` | 介质是 `EncryptedSharedPreferences`（Android Keystore）。iOS 实现必然是 Keychain，与 Android 无共用代码——立接口（`QiekjCredentialStore`）比立 expect/actual 干净 |
| `QiekjOrderHistoryStore` | 介质是普通 `SharedPreferences`。改成 `JsonFileStore` 等于换介质，老用户设备上最多 50 笔本地账单当场读不出来 |
| `QiekjWateringStore` | 介质是普通 `SharedPreferences`。会话类型 `QiekjWateringSession` 2026-11-25 已搬进共享层（今日页服务卡要在共享层读它），但 **store 本身仍留在 `:app`**：换介质等于让老用户设备上正在出水的那条记录读不出来。共享层要的那一面是 `QiekjWateringSessions`（写 + `watering` 读流），Android 由 `GraphServices` 转接、iOS 由 `IosQiekjWateringStore`（`appDataDir()` 下的 JSON）实现 |

结论：这三份介质留在各端，`AppServices` 只收**接口**——`qiekjRepository()` 与
`qiekjWateringSessions()` 两端都有实现（iOS 侧凭证走 Keychain、订单快照与会话走
`appDataDir()` 下的 JSON）。

### 10.6 首启引导在 iOS 上怎么接（S22，2026-11-25）

页面本体与它唯一的 `:app` 依赖（`NoticeDialog`）已搬进共享层。**本轮的边界是只搬不接**
——`MainViewController.kt` 未改，iOS 现在首次打开**还不会**弹引导。接的时候照这张表：

```kotlin
OnboardingScreen(
    onFinish = { /* 收窗；onboarding_seen 的写入由页面自己做完再回调 */ },
    startAtJw = false,
    jwLogin = null,            // iOS 没有原生教务会话 → 第 2 步改「去导入页登录」
    onOpenJwLogin = { /* 打开现有导入页（JwImport 那条链）*/ },
    brands = null,             // 没有 ic_brand_* 素材 → 图标位留白，布局不动
    onOpenService = { /* 有二级页栈之后才给；null = 完成页「其他可选账号」整块不渲染 */ },
    onBackAtWelcome = null,    // iOS 没有系统返回键，AppBackHandler 在那边本就是空实现
    onDeclineNotices = null,   // iOS 不能主动退出进程 → 「不同意并退出」不渲染
)
```

四件需要先定下来的事（都不是代码问题，是口径问题）：

1. **首启判据**：`.agents/rules/login-session.md` 要求「`onboarding_seen` 为 false **且**
   是新装」。Android 用 `PackageManager.firstInstallTime`；iOS 没有等价 API，接之前先定
   判据（`CFBundleVersion` 变更 vs 沙箱首次创建时间），别退化成只看键——老用户会被凭空弹一遍。
2. **步骤数会变**：`qzxyRepository()` 在 iOS 返回 null，**趣智那一步整步不出现**，
   进度条从 5 段变 4 段，完成页也没有「趣智 · 未配置」那一行。这是 `Step` 去掉固定序号的原因。
3. **引导里没有权限申请**（2026-11-25 核对）：六屏全是账号配置，通知 / 日历 / 定位 / 存储
   四类权限都不在这里，所以「iOS 没有对应能力就整步跳过」这条在本页没有适用对象。
4. **登录只在网页里**：第 2 步在 iOS 上不存密码、也没有自动续登，文案已按这个事实另写了一份
   （原来的「Android Keystore / 这是全应用唯一保存教务密码的地方」在 iOS 上是虚构承诺）。

## 11. 页面层接线约定（2026-11-25 定，页面搬移一律照此）

页面层开始大规模搬移时定的**唯一口径**。在此之前 S10/S13 搬的页面各写了一套（都不带 ViewModel），
`ui/week` 又是另一套（带 ViewModel 参数）。页面搬移必须先统一，否则每条并行流都会发明一种。

### 11.1 为什么 `commonMain` 里没有 `viewModel()`

`lifecycle-viewmodel-compose` 的 KMP 变体要 **Compose 1.9**，本仓锁 CMP 1.7.3
（升 1.8+ 会把 Android 侧 androidx.compose 整条抬版，Glance 1.2.0 全要重验）。
所以共享页面**拿不到** `androidx.lifecycle.viewmodel.compose.viewModel()`。

但 `androidx.lifecycle.ViewModel` 与 `viewModelScope` **能用**——`lifecycle-viewmodel:2.8.7`
是 commonMain 依赖，`WeekViewModel` 就在共享层跑得好好的。缺的只是那个 Composable 入口。

### 11.2 约定

**① 页面签名：ViewModel 是必填参数，由宿主创建。**

```kotlin
@Composable
fun XxxScreen(
    viewModel: XxxViewModel,        // 必填，宿主建
    onBack: () -> Unit = {},
    onOpenFoo: (() -> Unit)? = null, // 平台动作：null = 本平台没这项能力，入口整行不渲染
)
```

两端宿主各写一行：

| 平台 | 写法 | 为什么 |
|---|---|---|
| Android | `viewModel { XxxViewModel(...) }` | 保留配置变更（旋转）不重建 VM |
| iOS | `remember { XxxViewModel(...) }` | iOS 没有配置变更重建，`remember` 的生命周期就等于这一屏 |

**② 有 ViewModel 的页面，ViewModel 一起搬。** 判据是机械的：`:app` 那个页面有没有
`XxxViewModel`？有就一起搬进 `shared/commonMain`，没有就只搬页面。

**③ ViewModel 的构造只吃共享层类型。** 依赖从 `Services.current.xxx()` 取，或在宿主构造时传进去。
**禁止**让 VM 的构造签名里出现 `Context` / `Uri` / `Activity`。

**④ 平台动作一律可空回调。** `null` = 本平台确实没有这项能力（不是「以后补」的占位），
对应入口整行不渲染。给个空 lambda 留一个点了没反应的入口，比没有更糟。

**⑤ `collectAsStateWithLifecycle` 用 `edu.jxslu.schedule.platform` 的版本**，不要直接 import
`androidx.lifecycle.compose` 那个（它在 commonMain 不可见）。

**⑥ `java.time` → `kotlinx.datetime` + `AppClock`；`System.nanoTime()` → `kotlin.time.TimeSource.Monotonic`。**

**⑦ 宿主入口要同步改**：Android 改 `MainActivity` / `SubpageActivity`，iOS 改
`shared/src/iosMain/.../MainViewController.kt` 的 `IosPage` 枚举与 `when` 分支。
iOS 侧目前没有二级页栈，二级页先用 `hint` 给一句确定的话，别留死入口。

### 11.3 已知的整块阻塞（搬之前先看这里）

> 2026-11-25 复核：`MonthPicker`（已改 `YearMonthKey`）与 Markdown 编辑栈（已搬）两条阻塞
> 已解除，下表只剩真正没解决的。整块搬不动的原因与工作量见 §12。

| 阻塞 | 挡住的页面 |
|---|---|
| `com.amap` 高德 SDK | **2026-11-25 E2 复核后只剩视图与页面**：地图契约（`BikeMapInput`）/ 槽位（`BikeMapSlot`）与两端实现都在共享层，地图页的 ViewModel（`BikeMapViewModel`）与本机用车编排（`KvcxRideController`）也已搬完。`:app` 里还压着的只有三块：**高德 / osmdroid 视图本身**（`AmapMapView` / `OsmMapView` / `BikeMapHost` / `BikeMarkerOverlay` / `AmapMarkerBitmaps` / `AmapMaps`）、**页面**（`RideScreen` / `RidePanels` / `KvcxScreen` / `KvcxOrdersScreen` / `EbikeSettingsSheet` / 出码页 `EbikeViewModel`）、**通知那四件**（`EbikeFreeRideReminder` / `EbikeAutoReturnReminder` / `EbikeAutoReturnRunner` / 两个 Notifier + `EbikeFreeRideService`） |
| `android.bluetooth`（`QzxyGattLink`） | 趣智开阀的传输层；协议与状态机已在共享层 |
| `androidx.glance` | `ui/widget` 12 文件 / 3,112 行（桌面小组件） |
| 通知 + `WorkManager` | `ui/reminder` 15 文件 / 4,107 行 |

### 11.4 WebView 缝的五个事件（2026-11-25 定）

`ui/web/WebViewController` 原先是「教务导入够用」的六件事，学工表单 / 盖章成绩单 / 评教 /
收银台四类页面都进不去。现在扩成「动作 + 事件」两层：动作仍由调用方发起（加载、`reload`、
执行 JS、读 Cookie），事件由宿主装成可空回调、平台侧在原生回调里转发。

| 事件 | 谁需要它 | `null` 时平台给什么 |
|---|---|---|
| `onPickFiles(accept, allowMultiple, onResult)` | 学工表单附件 | 网页的上传按钮点了没反应（Android `onShowFileChooser` 回 false / iOS 立刻回「取消」） |
| `onGeolocationPrompt(origin, respond)` | 请假销假的位置签到 | Android：已授权就放行、没授权**立刻拒绝**；iOS：不响应 |
| `onExternalScheme(url) -> Boolean` | 收银台的 `alipays://` | 不拦，保持现状 |
| `onDownload(url, suggestedName, mimeType, localPath)` | 盖章成绩单的 PDF | 不拦，保持现状 |
| `onPageReady(url) -> String?` | 评教的「取 token 再跳」 | 不注入 |

**`null` 的语义要分两种读法**（§11.2 ④ 在这里得补一句）：能由我们画入口的页面，
`null` = 入口整行不渲染；但网页自己画 `<input type=file>`、自己发定位请求时，App
**决定不了入口渲不渲染**，这时 `null` 必须让平台给出一个确定结果，不能让网页挂着。
所以文件选择给「取消」、定位给「拒绝」、外链给「不拦」。唯一的例外是下载：`null` 就是
保持现状，因为「宿主没装下载处理器」时没有更合理的默认动作。

**选文件为什么是「宿主选、平台吃字节」**：两端的选文件 UI 差别太大（SAF vs
`UIDocumentPickerViewController`），收进共享层只会变成两份平台分支；而两端 WebView 的附件
控件要的都是「文件」而不是「路径」，字节是唯一能过这条缝的货币。代价见
`WebPickedFile` 的 KDoc（大文件整份驻留内存）。

**两端的状态**（2026-11-25）：

| | Android | iOS |
|---|---|---|
| 上传 | `onShowFileChooser` + 字节落 `cacheDir/webview_upload` 再交 `Uri` | `runOpenPanelWithParameters` + 字节落临时目录再交 `file://` URL |
| 外链 | `shouldOverrideUrlLoading`（只主 frame） | `decidePolicyForNavigationAction`（只主 frame） |
| 下载 | `DownloadListener`，`localPath` 恒 `null`（宿主自己取） | `canShowMIMEType=false` 才拦，`localPath` 恒 `null` |
| 注入 | `onPageFinished` 之后 | `didFinishNavigation` 之后 |
| 定位 | 接 `onGeolocationPermissionsShowPrompt` / `…HidePrompt` | **没接**：要 `CLLocationManager` + `NSLocationWhenInUseUsageDescription` |

外链判据 `shouldHandOffToSystem` 收口到 `ui/web/WebViewUrls.kt`：`:app` 那一份降成一行转发，
两端 WebView 从此走同一个函数。

**iOS 侧还缺的两块**（结构性，不是没搬完）：下载落盘要走 `WKNavigationDownload`
（iOS 14.5+）与 `WKNavigationDownloadDelegateProtocol`，另存到「文件」App 还要
`UIDocumentPickerViewController` —— 本轮 `localPath` 恒 `null`，落盘交给宿主
（`IosWebCookieSource` 读 Cookie + 共享层 Ktor + `platform/AppDirs` 写沙箱）。

## 12. iOS 上做不到 / 今晚没做的（2026-11-25 盘点）

这一节是**承诺边界**：哪些功能在 iOS 上不会出现、为什么、要补需要什么。
分两类：**结构性做不到**（iOS 没有对应机制，或必须换一套原生实现）与**没做完**（能做，时间不够）。

### 12.1 结构性做不到（必须有原生实现才能有）

| 功能 | 代码量 | 为什么做不到 | 要补什么 |
|---|---|---|---|
| 共享单车地图（`ui/ebike`） | 30 文件 / 17,879 行 | 底图是**高德 Android SDK**（`com.amap.api:3dmap`，含 32MB `.so`）。iOS 是另一套 SDK（`AMap3DMap`，CocoaPods/SPM），API 形状完全不同，且要单独申请 iOS key（绑 `edu.jxslu.schedule.ios`） | 接 iOS 高德 SDK + 重写 17,879 行里的地图宿主、覆盖物、路径动画；账号/订单/卡券那部分（HTTP）可以先搬 |
| 趣智校园蓝牙开阀 | `QzxyGattLink` 643 行 + 状态机（已在共享层；iOS 侧只有失败桩，且缺 5 个平台件，所以整页不路由） | `android.bluetooth` 的 GATT API 与 CoreBluetooth 是两套模型：前者回调在主线程、后者要自己建 `CBCentralManager` 串行队列 | 用 CoreBluetooth 重写传输层，**必须有真水控设备**才能验证；协议帧已在共享层，可复用 |
| 桌面小组件 | `ui/widget` 12 文件 / 3,112 行 | Glance 是 Android 的 Compose-for-appwidget；iOS 对应物 WidgetKit + SwiftUI，是另一套渲染与数据共享模型（要 App Group + Timeline Provider） | 用 SwiftUI 重写，数据通过 App Group 共享（共享层已有 JSON 落盘，可复用） |
| 快捷设置磁贴 | `ui/widget` 内 | Android 的 `TileService`；iOS 控制中心不支持第三方磁贴 | 无替代。iOS 侧只保留 App 内入口 |
| 免费骑行倒计时「灵动岛」 | `ui/reminder` 内 | 用的是 Android 16 的标准 Live Updates（`setRequestPromotedOngoing` + chronometer） | iOS 对应物是 Live Activity（ActivityKit），要写 SwiftUI 布局 + 推送更新；**能做，是另一份实现** |
| 后台定时任务（`WorkManager`） | `ui/reminder` 6 处 | iOS 的 `BGTaskScheduler` 由系统决定何时跑，不保证周期；Android 的 WorkManager 有最小时延语义 | 改成「进前台时补跑 + BGAppRefreshTask 尽力而为」，**行为必然弱于 Android** |
| 通知（洗好提醒 / 作业截止 / 成绩提醒 / 问渠提醒） | `ui/reminder` 15 文件 | `NotificationManager` + 渠道 + 前台服务 | 用 `UNUserNotificationCenter` 重写，权限模型不同（iOS 要显式请求）。**洗衣完成（N1）、作业截止与成绩（N2）已接**，共用 `platform/IosLocalNotifications`（见 §12.9）；上课提醒 / 问渠提醒 / 免费骑行缺的是**触发时机**（后台周期抓取 / 到点执行代码），不是通知本身 |

### 12.2 今晚没做完（能搬，时间不够）

按 `:app` 里剩下的行数排（2026-11-25 05:25 收工实测）。共享层 `commonMain` 已从
43.8% 涨到 **67.5%**（518 文件 / 123,138 行；`:app` 198 文件 / 59,388 行）。

| 包 | 文件 | 行数 | 卡在哪 |
|---|---|---|---|
| `ui/ebike` | 30 | 17,879 | 见 12.1：高德 Android SDK |
| `ui/reminder` | 15 | 4,107 | 见 12.1：通知 + WorkManager。洗衣 / 作业截止 / 成绩三类的**通知层**已在 iOS 侧独立实现（`shared/iosMain/.../platform/Ios*Reminder`），`:app` 那 15 个文件仍只在 Android 用；上课提醒、问渠蹲点、免费骑行倒计时、自动还车四类仍待 `AlarmManager` / `WorkManager` 的等价物 |
| `ui/campus` | 2 | 646 | 只剩学工表单（`XgFormScreen`：文件上传、SSL、定位、WebSettings 那一层）与校历图解码（`CampusCalendarImageUiAndroid`）。校园卡设置 / 付款码 / 充值弹层 2026-11-25 晚已搬（§12.8） |
| `ui/widget` | 12 | 3,112 | 见 12.1：Glance |
| `ui/jwvw` | 2 | 2,445 | `JwImportScreen`：教务导入总入口，WebView 全套 API + 文件选择 |
| `ui/common` | 21 | 1,835 | 剩下的都是位图与 WebView 辅助（`DecodeSampled` / `CodeBitmaps` / `WebViewSupport` / `ImageViewerDialog`） |
| `ui/today` | 3 | 705 | `TodayHost`（槽位填充：四项真相源、RESUMED 静默查询、确认弹窗）、`ActiveServiceRows`（快趣行 / 已还车行，数据在 `ui/ebike`）、`GuessYouWantCardPlatform`（品牌位图）：都已按可空槽位反转成宿主注入 |
| `ui/me` | 5 | 1,576 | 只剩 Glance 小组件设置（`WidgetSettingsScreen` / `WidgetCapabilities`）与三份纯平台件（`MeScreenPlatform` 的日历/提醒/快捷方式 launcher、`AboutPlatform` 的 `ACTION_VIEW` 与赞赏码落相册）。**权限与存储页 2026-11-25 已搬**：页框 + 「存储与诊断」在 `commonMain`，权限块与缓存/崩溃实现收成 `StorageDiagnostics` 描述器 + `permissionSections` 槽位（`app/.../ui/me/hub/PermissionsStorageAndroid.kt`）；iOS 侧三行存储占用 / 清缓存 / 导出诊断日志见 `shared/iosMain/.../ui/me/hub/IosPermissionsStorage.kt` |
| `ui/wenqu` | 5 | 1,737 | 蹲点提醒 / 前台服务 / `AlarmManager`（见 12.1 通知那一行） |
| `ui/week` | 4 | 1,568 | 课表页的平台槽（文件选择、日历写入、背景图） |
| `ui/life` | 5 | 1,311 | `RechargeHost` / `PowerBankPayScreen`（`android.webkit` 全家 + `@JavascriptInterface`） |
| `ui/toolbox` | 5 | 1,249 | 水印 / 拼接 / 渲染器 / 智能拼接：纯 `android.graphics.Bitmap` 管线 |
| `ui/score` | 4 | 1,215 | 盖章成绩单（`WebView` + PDF 另存 + `FileProvider`）与成绩页的平台槽 |
| 其余 | — | < 250 各 | `ui/ai`（图片位图）、`ui/tile`（快捷设置磁贴）、`ui/qzxy`（充值深链）、`ui/selection` / `ui/exam` / `ui/jw` / `ui/water` / `ui/homework` 的平台槽 |

### 12.3 iOS 壳本身还差的（不是「搬不动」，是没写）

| 差什么 | 现状（2026-11-25 03:30） | 影响 |
|---|---|---|
| 导航与 Android 的一致度 | **已重写**：路由键直接用共享层 `SubpageScreen`（Android 的 `SubpageActivity` 消费同一份）；底栏三格「今日 / 课表 / 教务」（M3 `NavigationBar` + 图标 + 文字，选中主色 / 未选中 `onSurface` 0.6）。**2026-11-25 晚（S1）抽屉排版整块对齐 Android**：身份卡 → 账号与登录 → 我的·设置 → 分隔线 → 钱包卡 → 生活服务区 → 二级页紧凑分组 → 付款码，段头 / 行 / 钱包卡 / 付款码**全部是 `shared/commonMain/ui/life/SideDrawerParts.kt` 的同一份 Composable**（钱包卡文案走 `walletCardTexts`，服务行走 `lifeServiceRows`，缺服务的平台传 null 即整行不渲染）。白名单 41 条仍在且全部可达，其中 8 条由卡片 / 行直接承担。今日页已接上（v1 搬完）。 | 还差 Android 左侧抽屉的**手势视差**（iOS 用 `ModalNavigationDrawer`，是另一套实现）；**主卡的缩放 / 圆角 / 阴影 2026-11-25 晚已对齐**，侧栏宽也统一成 300dp，Tab 与二级页的进出场补了过渡（§14）；服务行右侧的实时状态（开水余额 / U净 空闲数）未接——聚合逻辑还在 `:app` 的 `SideDrawerContent`；钱包卡的「充值」按钮不渲染（充值宿主 `RechargeHost` / `PowerBankPayScreen` 仍在 `:app`）；余额取数是「首次组合拉一次」而不是「抽屉展开时」（`IosDrawer` 签名固定三参数，拿不到 `DrawerState`） |
| 教务登录后的自动填表 | iOS 用 `WKWebView` 让用户手点登录，登录成功落地后自动 `archive` | Android 有自动填表；iOS 需要注入 JS，能做 |
| 凭证存储 | `IosKeychain`（CoreFoundation）+ 八个存储（胖乖 / 快趣 / U净 / 问渠 / 趣智 / AI / **一卡通** / **教务**）已写，**iOS 编译验证卡在 runner 容量** | 教务（CAS）2026-11-25 晚起也是真的（`IosCredentialVault`，service `secure_jw`，见 §12.10）——此前恒为 `null`，因为 iOS 从不保存教务密码、登录只在 `WKWebView` 里；一卡通已接（`IosYktCredentialStore`，service `secure_ykt`，含落盘 token 与失效标记），账号中心 / 消费流水 / 电费读得到学号。**2026-11-25 晚起录入界面也搬完了**（校园卡设置页 + 付款码页进 `commonMain`，见 §12.8），四处入口不再停在「凭证未配置」 |
| 第三方仓库 | **胖乖 / U净 / 问渠 / 快趣会话已接**（2026-11-25）：凭证走 Keychain，订单快照 / 收藏 / 历史走 `appDataDir()` 的 JSON 或 `NSUserDefaults`（`IosKeyValuePrefs`）。`qzxyRepository()` 仍是 `null` | 开水房、U净 看板与洗衣账单、问渠（登录 / 活动 / 报名 / 工时 / 蹲点页）**在 iOS 上能真跑**。趣智仍缺 6 个平台件里的 5 个（蓝牙扫描 / 设备表 / prefs / 记账）。快趣：**数据层两端已是同一份**（`KqcxSessionCore` + `IosKqcxCredentialStore`，S23 起 `IosServices.kqcxSession()` 给真实现），账号中心的快趣行能读登录态 / 退出；**差的是页面**——`ui/ebike` 被高德 Android SDK 挡住（§12.1），所以快趣账号页 / 订单页 / 骑行页在 iOS 上仍不路由（地图层已用 MapKit 打通，见 §12.11） |
| 支付调起 | 一卡通走「收银台 URL 进 `WKWebView`」；胖乖 / 趣智走 `alipays://` 深链 | `Info.plist` 还没有 `LSApplicationQueriesSchemes`（`alipays` / `weixin`），深链会被系统拦 |
| App 图标 | **已补**（2026-11-25）：`iosApp/JUWPHub/Assets.xcassets/AppIcon.appiconset`，1024 单尺寸 universal。用 `tools/app-icon/source.png` 的**前景层**重新合成满幅图——原图把圆角与投影烤进了像素，直接用会与 iOS 遮罩叠成双层圆角 | 启动屏 2026-11-25 晚补上（§14.4）：`UILaunchScreen` 指向命名色 `LaunchBackground`，两档色值 = 主题 `background` |

**iOS 上现在能进的页面**（精简版：抽屉 39 条 + 4 个底栏 Tab）：今日、课表、教务汇总、我的、
理论课表、实验课表、教材、教学日历、学籍信息、学业完成情况、培养方案、实习基地、成绩 / 成绩设置、
考试 / 考试设置、选课 / 选课中心 / 已选课程 / 预选清单、请假、校历、收件箱、天气 / 天气设置、
电费、消费流水、宿舍报修、开水房、U净洗衣房 / 洗衣账单、课表管理 / 调课 / 变动检测、数据与备份、
校历与日历同步、上课提醒、快捷方式、教务账号、关于与更新、条款与声明、支持开发、账号中心。

### 12.4 精简版：下线的四个模块（2026-11-25 晚，用户指定）

用户装完第一版 IPA 后的反馈：教务导入进不去（403 / 白屏、而且没有刷新按钮），要求先出
「阉割版」，去掉 AI 调课、作业与笔记、问渠、百宝箱。

下线必须**三层同时摘**，缺一层就会留下「点了没反应」的入口：

| 层 | 位置 | 做法 |
|---|---|---|
| 抽屉白名单 | `IosNavigation.iosDrawerEntries()` | 直接删条目（50 → 39） |
| 页面内入口 | 共享层的**可空回调 / 开关** | `WeekScreen` / `TodayScreen` 的 `onOpenCourseNotes` / `onOpenCourseHomework` 传 null；`SettingsScreen(showToolbox = false, showAiSchedule = false)`；`AccountsHubScreen(onOpenWenqu = null, onOpenAiSettings = null)`；`ScheduleChangesScreen(onOpenHistory = null)`；`TodayViewModel(blockedGuessTargets = setOf(WENQU))` |
| 最后一道闸门 | `IOS_REMOVED_SCREENS` | 宿主 `pushRoute` 命中即不进栈（防以后新加页面忘了摘入口） |

共享层那六处改动**全是新增可选参数**（默认值 = Android 现有行为），Android 侧零改动、
2419 条单测照旧全过。`IosRouteContent` 里这 16 个页面的渲染分支**没删**：iOS 侧本机编不了，
删分支是纯风险；入口摘干净后它们不可达。

下线的 16 页：笔记库 / 课程分组 / 笔记详情、作业库 / 待办 / 课程分组 / 作业详情、
百宝箱 / 菜鸟批量查、问渠 / 问渠设置 / 问渠工时 / 问渠蹲点、AI 调课 / AI 设置 / 调课记录。

**同一次提交里修的教务导入**（详见 `MainViewController.JwLoginScreen` 的 KDoc）：入口从 CAS
直链改成预热入口 `SSO_WARMUP`（第一次 `sso.jsp?ticket=` 时教务域还没有 `bzb_njw`，这正是
403 / 白屏的根因；Android 的 `JwImportScreen` 一直这么起跳，iOS 漏了），顶栏补「刷新」，
空白页 / 加载失败自动重走认证（上限 2 次），落地判据从「教务域」收紧到「学生端 8080」——
否则预热页 `sso.jsp` 一加载就被当成「已登录」，必然报「没有可用的教务会话」。

## 13. 装机后请点验什么（2026-11-25 精简版交接口径）

包：`JUWPHub-unsigned.ipa`（CI 产物，用 Sideloadly / SideStore 自签）。桌面图标是 JUWPHub
的书本 + 星形（白底蓝图，2026-11-25 补）。

按这个顺序点，前四条不过后面就不用看了：

| # | 点哪里 | 期望 | 不过说明什么 |
|---|---|---|---|
| 1 | 打开 App | 进「今日」页，不闪退 | `PlistSanityCheck` 缺键（`CADisableMinimumFrameDurationOnPhone`）或 Compose 初始化失败 |
| 2 | 底栏「课表」 | 空态显示「去导入课表」；有数据时出网格 | Room 没起来（iOS 沙箱目录问题） |
| 3 | 「更多」→ 抽屉 | 39 条，分「学习 / 教务 / 钱包 / 生活 / 设置 / 账号 / 江水生活」七组；**没有**笔记 / 作业 / 百宝箱 / 问渠 / AI 调课 | 白名单没编进去 |
| 4 | 课表空态「去导入课表」 | 先落 `sso.jsp`（一闪），再跳统一身份认证；登录后顶栏开始报进度（`正在导入 2026-2027-1（1/12）…`），结束给一行结论 | 入口还是 CAS 直链（403 / 白屏的老毛病），或归档没走 WebView 同源取页 |
| 4b | 导入页顶栏「刷新」 | 点一下重新加载当前页；403 / 白屏时刷一次通常就过去了 | 按钮没接上 `WebViewController.reload()` |
| 4c | 导入结束那两行字 | `导入完成：库里 N 门 · 开学 2026-08-31 · 现第 13 周 · 教务 2026-2027-1 · 新建 …` + `示例：液压与气压传动A 周二 9-11节 1-16周` | 库里 N 门 = 0 说明没落地，第二行是定位用的样例课；「现第 13 周」应与教务页右上角的周次一致 |
| 5 | 抽屉「江水生活」→「开水房」 | 登录（短信验证码）→ 能看到设备与余额 → 点开阀 | **胖乖是 HTTP 不是蓝牙**，这条链 iOS 上能真开出水；同时是 **Keychain 的首次真机验证**（凭证存进 `IosQiekjCredentialStore`，杀掉进程再进来仍在） |
| 5b | 「江水生活」→「U净洗衣房」 | 看板能看附近洗衣机与在用订单；扫码与支付入口不渲染 | 三份本地存储已接（Keychain + NSUserDefaults） |
| 6 | 抽屉「数据与备份」 | 能导出 JSON、能导入 Android 那边导出的备份 | Room 读写；**这也是 WebView 万一仍不通时的兜底路**：在 Android 上导好课表再导进来 |
| 7 | 底栏「我的」 | 没有「AI 调课」行、没有「百宝箱」开关 | `showAiSchedule` / `showToolbox` 没传 |
| 8 | 抽屉「账号中心」 | 没有问渠行、没有「可选 / AI 调课」那一节 | 两个可空回调没传 |
| 9 | 抽屉「钱包」→「校园卡」 | 填学号 + 查询密码 → 「保存并开启」→ 回「消费流水」/「电费」看是否真拉到数据 | **这一页就是 Keychain 的真实录入点**（Y1 2026-11-25 晚搬进共享层）。页面上「充值」按钮与「余额提醒」整节**本来就不该出现**（两个平台槽位传 null，§12.8）；「寝室电费」的房间行能点、能改绑 |
| 9b | 抽屉「钱包」→「付款码」 | 能出二维码与一维码、卡号行有数字、「下一个码」批内递增 | 码图由共享层 Compose 画布绘制（不再走 `android.graphics`）。**防截屏与亮度拉满都不会生效**（iOS 没有实现，§12.8）；扫码可读性要在真机上用另一部手机试 |

**已知在 iOS 上点了会落在「这一页还在 Android 侧」的**：趣智（缺蓝牙扫描与设备表）、
骑行 / 快趣、成绩单、评教、分享、小组件设置、权限与存储、
水印 / 拼接（纯位图管线）。

### 12.5 第二批：导入改走 WebView 同源取页 + 审计修复（2026-11-25 晚）

**根因（真机验证过两次）**：WebView 里明明已登进教务（页面上是「个人中心」），但抓取链
`IosJwReadSession.ensureValid()` 从 `WKWebsiteDataStore` 读到的 Cookie 头里没有可用会话，
导入直接判「还没有登录教务」。Cookie 名 / 域过滤 / 读时机三者之一——**没有真机调试通道时
继续猜是浪费**，而 WebView 自己知道怎么带着登录态取页。

所以主路径换成 [IosWebFetch]：在**当前这个已登录的页面**里跑异步 `XMLHttpRequest` 同源取
HTML，Cookie / UA / Referer / TLS 全由 WebKit 负责，与「用户自己点开那一页」逐字节一致；
Cookie + Ktor 降为兜底（WebView 已销毁时，例如从成绩页触发的同步）。不用同步 XHR——那会把
主线程堵在 JS 里等网络。轮询间隔 120ms，整次取页 20s 超时。

同时修掉一批「静默失败」（审计结论，见下表）。共同点：**界面给出的结论与事实相反**，
比报错更难查。

| 位置 | 原来 | 现在 |
|---|---|---|
| `IosJwReadSession.ensureValid` | 强判 `JSESSIONID`，误报「没登录」 | WebView 在就 Ready；否则读 Cookie，缺失时把 `describeStore()` 的条数打出来 |
| `hasJwWebSession()` | 恒 `null` → 登录成功后账号中心仍显示「未登录」 | 进程内标记 `IosJwSessionFlag`：冷启动探一次、抓取成功/失败顺手更新、落到学生端立刻置真 |
| 预选清单页 | `client == null` 时仍打印「本轮已开 0 条预选」 | 改说「本平台不支持本轮匹配」，刷新钮整只不渲染 |
| 教材页 | 空态说「导入课表后会自动抓取」（iOS 没有 `TextbookSync`），随后改成「本平台还没有教材抓取」——两种说法都不解决问题：iOS 侧没有任何一方写 `textbooks` 表 | **第三批补上真抓取**（§12.6）：`AppServices.textbookSync()` 两端非空，进页空态自动补抓一次（吃 6 小时闸门）、顶栏「重新导入」强抓；失败原因原样显示 |
| U净 弹窗 | 扫码钮点了没反应（槽位 null 但按钮还在） | `onScan` 改可空，null 时扫码钮与那句提示都不渲染 |
| 变动检测页 | 顶栏「调课记录」点了没反应 | `onOpenHistory` 改可空，null 时不渲染 |
| 课表页 | 「管理课表…」与「检测 → 查看」落在空 lambda | 宿主补接线，两页都能进 |
| 课表 / 今日 课程详情 | 笔记·作业按钮点了没反应 | 两个回调改可空，null 时那两行不渲染 |
| 「我的」 | 外观 / 课表设置 / 免打扰点进去说「还在 Android 侧」 | 三页早在 commonMain，补 `IosRouteContent` 分支即可 |
| 教务 Tab | 只有 7 个入口 | 补到 12 个（学业 / 培养方案 / 实习基地 / 请假 / 报修 / 校历） |
| 时间表管理 | 部分学期失败时只说「已导入 N 个」 | 追加「另有 M 个学期失败：<原因>」 |

还有一件与功能无关但用户天天看得见的：底栏下方那行「数据自检：通过 · DB v21 …」**已撤掉**
（P2 验收用的探针，真实导入课表已经覆盖了它的目的）。

### 12.6 第二批并行决策（2026-11-25 晚，用户拍板）

| 项 | 决定 | 理由 |
|---|---|---|
| 快趣单车地图（`ui/ebike` 15,542 行） | **不做**，保持现状（iOS 点骑行落「这一页还在 Android 侧」） | 底图是高德 **Android** SDK，iOS 只有 MapKit 或另买高德 iOS key；重写 2–3k 行且只能真机验。等主链路稳了再评估 |
| 桌面小组件（Glance 2,740 行） | **不做** | iOS 要 Swift 扩展 target + App Group + 改 `project.yml`，是另一套构建配置，风险不在 Kotlin 侧 |
| 趣智蓝牙（CoreBluetooth） | **不做** | 没有水控设备，写完验不了 |
| 第一批并行流 | **A 一卡通凭证 / B 教务单页导入 / D 首启引导** | 全是纯 HTTP / Keychain / 位图，不碰 WebView 桥与后台调度，覆盖入口最多、风险最低 |
| 第二批 | C 通知提醒、E WebView 增强（上传/下载/PDF） | 两者都要动「本机验不了」的那一层（后台调度语义、WebView 桥） |

并行纪律（沿用第一轮，已验证）：每流一个 worktree + 分支；`AppServices` / `IosServices` 新成员
一律加在**末尾**；各流不碰别人的页面文件；合并时按并集解冲突；**合并完跑三道本地门禁再推一次**，
一轮一次构建。

### 12.7 成绩 / 教材在 iOS 上真能导入（2026-11-25）

教务的抓取链（`JwReadSession`）两端早已是同一套，缺的从来不是「能不能抓」，而是**页面上没有
可点的入口**：教材页的 `textbooks` 表没有任何写入方，成绩页的导入按钮挂在宿主的
`JwImportActivity`（iOS 传 null）。这一批把入口补成「共享层直取」，没有再搬 WebView。

| 页 | 入口怎么来 | 失败时用户看到什么 |
|---|---|---|
| 教材 | `AppServices.textbookSync()` 两端非空。进页该学期为空 → `backfillIfMissing`（6 小时闸门，零网络）；顶栏「重新导入」→ `syncForTerm`（无闸门） | 原样透出 `TextbookSync.Result.Failed.reason`（`没取到教材数据，教务返回 HTTP 500` / `还没有登录教务：…`）；0 条写成「教务这学期没有教材记录」，**不是失败** |
| 成绩 | `onImportScores == null` 时页面改用 `AppServices.scoreSync()`（`sync(force = true)`，与成绩提醒同一条链、同一个 Mutex）。两个入口不同时出现 | 原样透出 `ScoreSync.Result.Failed.reason`，并留在空态里 |
| 考试 | 早已如此：`ExamSync` 非空 → 顶栏「从教务更新」+ 空态出口；写课表走「自动合并」开关 | 原样透出 `ExamSync.Result.Failed.reason` |
| 选课 | 早已如此：`SelectionSync` 非空 → 顶栏「更新」+ 进页自动刷新一次（30 分钟缓存） | 轮次卡里显示 `failedReason`；**只读**，退课/免责声明接口一律不碰 |

两条共同纪律：

1. **不要在 `iosMain` 里写业务逻辑**。抓取、解析、写库、闸门全在共享层的 `*Sync` 里，
   iOS 侧只给「会话 + 仓库 + 偏好」三样（`IosServices` 的各 lazy）。
2. **没有 WebView 时走 Cookie + Ktor 兜底**（`IosJwReadSession.fetchHtml` 的第二条路）。
   这条路在真机上是否拿得到会话**尚未验证**——`IosWebFetch.available` 为假时（导入页已关）
   就可能落到「还没有登录教务：…」，那句话本身是准确的，不吞。

### 12.8 一卡通凭证：录入界面已搬完（2026-11-25 晚，Y1）

`IosYktCredentialStore`（Keychain，service `secure_ykt`，account 沿用 Android 键名
`username`/`password`/`token`/`token_at`，`kSecAttrAccessibleAfterFirstUnlock`、
**不写 `kSecAttrSynchronizable`** 即不进 iCloud）早已接入，`yktCredentialStore()` /
`accountCredentials()` 都不是 null。此前缺的是**录入界面**：`CAMPUS_CARD_SETTINGS` 与
`PAY_CODE` 在 `:app`，所以消费流水与电费仍停在「凭证未配置」。

Y1（2026-11-25 晚）把四个文件搬进 `shared/commonMain/.../ui/campus/`：`CampusCardSettingsScreen`、
`CampusCardViewModel`、`PayCodeScreen`、`PayCodeViewModel`，连同它们用到的
`CampusRechargeUi`、`YktBalanceChangeSync` 一起。`:app` 只剩宿主装配
（`CampusCardPlatformAndroid.kt`）。iOS 侧 `IosNavigation.kt` 补了这两条路由，
抽屉「钱包」组加「校园卡」「付款码」两条。

**平台槽位（一律 §11.2 ④：null = 本平台没有 → 那块不渲染）**

| 槽位 | Android | iOS | null 时 iOS 上少什么 |
|---|---|---|---|
| `onOpenPaymentUrl` | `startActivityOutsideApp(ACTION_VIEW)`（不加 NEW_TASK，与搬迁前一致） | 传 `null` | 「充值」按钮、充值免责声明、充值弹层 → 这一版不做充值（§10.4 那条「收银台进 WKWebView」另算） |
| `notificationPermission` | `POST_NOTIFICATIONS` launcher | 传 `null` | 「余额提醒」整节（两个开关 + 阈值 + 间隔） |
| `CampusCardViewModel(alerts = …)` | `BalanceAlertReminder.onSettingsChanged` / `ensurePeriodicWork` | 不传 | 排程不生效（与上一行成对：一个管显示、一个管排程） |
| `vpnActive` | `JwVpnDetector` | 不传 | 失败文案退回「换一条网络」，不再点名「关代理」 |
| `powerRoomPicker` | 宿主建 `PowerRoomPickerViewModel` | **真实现** | 拿不到仓库时才为 null → 「寝室电费」整节不渲染。iOS 现在能选/改绑房间 |
| `PayCodeScreen(windowGuard = …)` | `FLAG_SECURE` + 亮度拉满 | 传 `null` | 两项都不做：码照常显示，只是没防截屏、不调亮度 |
| `PayCodeViewModel(vibrate = …)` | `ReminderVibration` | 不传 | 扫码扣款那一刻不震 |
| 码图栅格化 | 曾有 `CodeBitmaps` → `android.graphics.Bitmap` | 无 | **不再需要平台件**：位阵改由 `ui/common/BarcodeMatrixImage` 用 Compose 画布画（列合并 + 整像素对齐），两端同一份输出，`recycle()` 三处一并删除 |

**AppServices 没有新增成员**：仓库 / 凭证 / 偏好 / 数据库 / 收件箱 / 推小组件全部用既有成员。

**这条链断在哪**：填学号密码 → `verifyAndEnable`（真登录一次，失败不落库）→
`credentialStore.save` → Keychain → `setCampusCardEnabled(true)` → 消费流水 / 电费页
`credentialStore.read()` 拿到同一份凭证 → `YktRepository` 真发请求。代码上是连通的；
**没验的是真机**：Keychain 写入后跨进程读回、学校接口在 iOS 上的实际返回、码图扫码可读性。

### 12.9 iOS 通知能力清单（N1 2026-11-25 晚 → N2 2026-11-26：三类能做了）

iOS 侧此前**没有任何通知能力**——`AppServices.ujingReminder()` 在 iOS 上返回 null，所有「到点
告诉你」的功能都不存在。N1 立了第一份实现（`UNUserNotificationCenter` +
`UNTimeIntervalNotificationTrigger`），只服务洗衣一类。N2 做两件事：
**把与业务无关的部分提取成共用件** `platform/IosLocalNotifications`（授权、排一条、按前缀扫
两个队列、前台横幅代理），再把**作业截止**与**成绩**两类接上。

三族的标识符前缀各自区分，撤销时互不牵连：

| 提醒 | 前缀 | 标识符形状 | 排几条 |
|---|---|---|---|
| 洗衣完成 | `ujing.` | `ujing.<订单号>` | 1（同单覆盖） |
| 作业截止 | `homework.` | `homework.<作业id>.<提醒点日期>` | ≤ 15（每次重排先按前缀清空整族再排） |
| 成绩 | `score.` | `score.latest` | 1（同标识符替换上一条） |


#### 12.9.1 平台口径：iOS 与 Android 的硬差异

| 面 | Android（`ui/reminder/*`） | iOS（N1 / N2） |
|---|---|---|
| 排程机制 | `AlarmManager.setAlarmClock`，到点拉起 Receiver → 查一次网络 → 决定发不发 | `UNTimeIntervalNotificationTrigger`（**相对秒数**），到点由系统直接投递，**没有任何代码被执行** |
| 到点核对 | 有：本次看到转入 50 才发，还在跑按新剩余重排，取消静默收摊 | **没有**。iOS 到点不能执行代码（后台唤起靠 `BGTaskScheduler`，系统不保证时刻），所以到点即发，正文写「预计已结束」（洗衣） |
| 免扰时段 | `QuietHoursGate`：拦下入队，时段结束补发，不丢弃 | **没有等价物**。只有 `UNNotificationInterruptionLevel` 分级；拦下就没了，补发不存在。所以三类都没接免扰 |
| 待发额度 | 无上限（通知栏按渠道管理） | **64 条封顶**，逼近 60 就截断（分配与截断口径见 12.9.3） |
| 权限 | `POST_NOTIFICATIONS` 运行时权限 + 通知渠道，渠道可单独关 | 首次请求 `alert \| sound \| badge`；被拒后**不抛异常**，排提醒降级成 no-op，结论记进进程内 `IosNotificationPermission`（`hint` 是给页面的一行说明）。**权限结论不落盘**，真身由系统持有。三类提醒共用同一次请求（在途去重），所以系统授权框只弹一次 |
| 前台可见 | 默认就弹 | **默认不弹**。必须实现 `UNUserNotificationCenterDelegateProtocol` 的 `willPresentNotification` 并回 `banner \| sound`。那个 delegate 挂在**进程级**对象上——`UNUserNotificationCenter.delegate` 是 weak，写成局部对象会被回收，回调一次都不来（与 `WebViewController.ios.kt` 的 `navigationDelegate` 同一个坑）。N2 起由 `IosLocalNotifications.installForegroundPresentation()` 在 `IosServices.init` 装一次，比任何页面都早 |

两条实现细节：**`timeInterval` 必须 > 0**，已过期或不足 1 秒一律钳到 1 秒（传 0/负数 iOS 直接
拒绝注册）；**标识符稳定且可枚举**是撤销的前提，`removePending` / `removeDelivered` 按前缀扫
「待发 + 已投递」两个队列，不依赖进程内存记得排过哪几个（进程被杀后那点记忆就没了）。

`Info.plist` **不需要加任何键**（本地通知不要求 `UIBackgroundModes`、没有用途描述串）；
`iosApp/project.yml` 的 `OTHER_LDFLAGS` 补了 `-framework UserNotifications`——静态 framework
不自带 load command，漏了是链接期 Undefined symbols。

#### 12.9.2 各类提醒：现在能做什么、还缺什么

| 提醒 | 状态 | 缺的是什么 |
|---|---|---|
| 洗衣完成（U净） | **能做**（N1）。排 / 撤 / 撤已发 + 前台横幅 + 权限降级全接上；`UjingViewModel` 里五处调用点（`refreshOrder` 排与终结撤、`cancelOrder`、`logout`、`dismissOrder` revoke）自然生效 | **整条「下单 → 通知」在 iOS 上还差一段**：`UjingViewModel.createAndPay` 在 `pay == null` 时直接拦住（iOS 没有支付宝 SDK，`UjingPay` 由宿主传 null），本机建不出订单，因此提醒只在「已有在案订单」时被排到。缺的是支付缝（§10.4：收银台 URL 进 `WKWebView` + `alipays` 白名单） |
| 作业截止 | **能做**（N2）。时刻算法与 Android 同一份（`domain/ReminderPlanner.kt`：截止日前一天/当天 20:00，只算未完成、未逾期、30 天内）；iOS 侧把未来 ≤ 15 个提醒点**提前排上**（`IosHomeworkReminder`），标识符 `homework.<作业id>.<提醒点日期>` 即去重键，重排先按前缀清空整族 | ①**漏掉的点不补**：进 App 时刻已过 20:00 的那一晚不会补发（Android 由闹钟落点 / 15 分钟核对补发）；②免扰时段没有等价物；③**作业页在 iOS 上仍按 §12.4 下线**，所以提醒只能从库里已有的作业排——新建/改截止的入口还没放回来（`onHomeworkChanged` 已接好，入口一放回来自动生效）；④点通知只回到 App，没有深链到那条作业 |
| 成绩 | **降级能做**（N2，见 12.9.4） | 周期检查不做：Android 是 `WorkManager` 周期核对，iOS 的 `BGTaskScheduler` 不保证周期。现在只有「**打开 App 时查一次 + 有新成绩立刻弹一条**」，标题只写「有 N 门新成绩」（不写课程名/分数，锁屏可见）。没接：后台静默抓取后推送、免扰时段、点通知直达成绩页 |
| 上课提醒 | **仍待定**（本轮未做） | ①「课表 → 通知」的换算（`Course.startSection/endSection` + 作息表 → 绝对时刻，含单双周 / `date_overrides` 停调课）；②**64 条待发上限的分配策略**（见 12.9.3，作业那 15 条已按它算好）；③重排时机（落库后 / 前台恢复 / 每天首次启动）。Android 那份 `ScheduleAlertReminder` 是 `AlarmManager` + 前台服务 + 免扰队列，**不能搬**，只有换算规则可以复用 |
| 免费骑行超时 | 不能做 | ①骑行链路本身（`ui/ebike` 是高德 **Android** SDK，§12.1）；②「倒计时常驻/灵动岛」的等价物是 Live Activity（ActivityKit + SwiftUI 扩展 target），本地通知只能给一条到点横幅，不是同一个体验；③到点要触发的自动还车是后台动作，iOS 没有等价物 |
| 自动还车 | 不能做 | 后台定时（`WorkManager` → `BGAppRefreshTask`，系统不保证时刻）+ 骑行订单来源（同上）。通知只是副产品，前置条件一个都没有 |
| 调课提醒 | 一半能做 | 抓取与对比**已经能跑**（`ScheduleSync` 在 iOS 非空，§12.7），缺的仍是**触发时机**：Android 是 `WorkManager` 周期任务。与成绩同一条路可降级成「进 App 补跑」，本轮没做 |

#### 12.9.3 64 条上限：上课提醒的两种候选策略（上课那部分仍不实现，作业的 15 条已按它落地）

iOS 每个 App 最多挂 **64 条待发本地通知**（`UNUserNotificationCenter` 装不下就丢，且丢哪条不可依赖）。
11 小节 × 7 天 = **77 条**，Android 那种「一次排满一周」到 iOS 上必然超限。

| 策略 | 怎么排 | 取舍 |
|---|---|---|
| **A. 只排未来 N 天 + 开 App 补齐**（倾向这个） | 64 / 11 ≈ 5.8 天，留余量给洗衣 / 作业 / 成绩 → 排 **4 天 = 44 条**，剩 20 条给别的提醒。进 App、前台恢复、课表落库、`date_overrides` 变更时重排 | 优点：与 Android 行为最接近（用户感知「每节课前都提醒」），换算逻辑与排程互相独立，共享层只写一份。缺点：**连续 4 天以上不开 App 就断**（第 5 天起没有提醒），且每次重排都是一次批量 `add`（同标识符覆盖，幂等没问题） |
| **B. 每天只排最近一节 + 按周重复** | 用 `UNCalendarNotificationTrigger`（`repeats = true`）按「周一 08:00 … 周五 08:00」排 7 条（周末有课再补），只覆盖**每天第一节** | 优点：7 条稳定，永不超限，不需要补排。缺点：**一天只提醒一次**，第 2 节起全没有；调课 / 停课 / 单双周都要靠 `date_overrides` 重新换算，而重复触发器不会跟着改——一改就得重排一批 |

选 A 的理由：上课提醒的价值在「每节都提醒」，B 直接砍掉三分之二；而 A 的断档条件（一周内必开
App）在这个 App 的实际使用里成立（查课表、看今日页都会进前台），断档的损失也只是「第 5 天起
安静」，不是错误提醒。**两种都要在排之前先算总量**：`44 + 洗衣 1 + 作业 N + 成绩 1 ≤ 64`，
余量不足时按「上课 > 作业 > 洗衣 > 成绩」的顺序砍最远的那些。

**N2 已实施的分配（`platform/HomeworkReminderPlatform.kt` 的 `HOMEWORK_REMINDER_SLOTS`）**：

| 用途 | 槽位 |
|---|---|
| 上课提醒（未来 4 天 × 11 小节，策略 A） | 44 |
| 洗衣完成 | 1 |
| 作业截止 | **15**（= 60 − 44 − 1） |
| 安全余量（成绩 / 上课提醒的边界） | 4 |
| 合计 | 64 |

**作业那 15 条的截断口径**：`upcomingHomeworkReminders(items, now, limit)` 只取
`triggerAt > now` 的提醒点，按「触发时刻 → 作业 id」升序排，取前 15 条。触发时刻就是
「截止日前一天 20:00 / 当天 20:00」，所以**等价于按截止日从近到远保留**；同一门作业的两个点各占
一个槽位（「明天截止」比「今天截止」先到，先被保留）；同刻按作业 id 兜底，保证每次重排选到的是
**同一批**（15 条 ≈ 7 门作业，不稳定排序会让同一门课时有时无）。改这个数必须先改上表，
否则上课提醒那 44 条就超了。

#### 12.9.4 成绩提醒在 iOS 上的降级形态（N2，2026-11-26）

Android 的 `ui/reminder/ScoreAlertReminder` 是「`WorkManager` 周期核对（间隔 = 设置档位，默认
6 小时）+ 冷启动补查 + 设置变更即时评估」，抓完才决定发不发。iOS 侧**周期检查这一轮不做**：

| 面 | Android | iOS（本轮） |
|---|---|---|
| 何时查 | 周期 Worker + 冷启动 + 设置变更 | **只在打开 App 时**（`IosServices.warmUp`）查一次，闸门原样复用 `ScoreSync`（开关关着不查；开着时 6 小时档 / eager 半小时档决定真抓还是 `Skipped`） |
| 查到新成绩 | 发通知（单条写课程名，多条写「有 N 门新成绩」+ 前 4 门课程与分数） | **立刻弹一条本地通知**（`UNTimeIntervalNotificationTrigger` 1 秒 = 系统接受的最小值）：标题「有 N 门新成绩」，正文「打开 App 查看成绩」 |
| 免扰时段 | `QuietHoursGate` 拦下待补发 | 没有（见 12.9.1 那一行） |
| 点击落点 | 二级页 `SubpageScreen.SCORES` | 只回到 App（`userInfo` 深链未接） |
| 后台 | 系统保证周期 | `BGAppRefreshTask` 尽力而为，**不保证时刻**；「后台悄悄抓完再推送」这条路 iOS 不保证，所以不做 |

**为什么正文不写课程名**：成绩会显示在锁屏上，比作业更敏感；Android 那份的「新成绩：课程名」
在这边刻意收成「有几门」。判据（首导不算变动、无变更不发）与 Android 同一份，都在
`ScoreSync` 里。

> **未验**：iOS 那份代码（`IosLocalNotifications` / `IosHomeworkReminder` / `IosScoreReminder`）
> 在 Windows 上编不了（无 Kotlin/Native），本地只跑了 `metadataCommonMainClasses` /
> `assembleDebug` / `testDebugUnitTest` 三道门禁；iOS 侧符号形状沿用 N1 在 CI 编译通过的那套
> 调用形式，行为（真机弹通知、前缀撤销、权限降级、冷启动查成绩）只能真机验。



### 12.10 教务凭证直登：导入页关掉之后还能取数（2026-11-25 晚，C1）

**用户报的现象**：首启登录教务、课表导入都成功，点返回课表又说要导入 / 登录。

**根因**（代码上可复现）：iOS 的教务会话此前只有一条路——活着的 `WKWebView` 同源取页，
或者读 `WKWebsiteDataStore` 的 Cookie。两个都是「导入页关掉就没」的形态，而 App 手里
**没有任何能重建会话的东西**：学号密码从不保存。Android 不是这样（`CasSession` +
`CredentialVault` + OkHttp 随时自己重登）。

**处置**：把 Android 那条链整段搬进 `shared/commonMain`，两端共用同一份会话策略，只换传输层。

| 件 | 位置 | Android | iOS |
|---|---|---|---|
| `CasTransport`（cookie 换成中性 `SessionCookie`） | `shared/commonMain` | `JwHttpSession`（OkHttp） | `IosCasTransport`（Ktor + `InMemoryCookieStorage`） |
| `CasSession`（可信期 / 闸门 / 重登 / 回灌） | `shared/commonMain` | 同一份 | 同一份 |
| `CredentialStore`（含 `saveCas`） | `shared/commonMain` | `CredentialVault`（加密 prefs） | `IosCredentialVault`（Keychain，service `secure_jw`） |
| `WebCookieBridge` | `shared/commonMain` | `WebViewCookieBridge`（`CookieManager`） | `IosWebCookieBridge`（`WKWebsiteDataStore`） |
| `JwPageRules`（登录页 / 被顶 JSON / 主页判据） | `shared/commonMain` | 同一份 | 同一份 |

`IosJwReadSession.ensureValid` 的四档（顺序不能反）：① WebView 活着 → `Ready`
（用户可能刚在网页里登了另一个账号）；② jar 里已有可用会话 → `Ready`；③ Keychain 存过
学号密码 → 自动登一次；④ 都没有 → `NeedsManualLogin`，文案带 cookie 库现状。

**录入入口**：`JwAccountScreen` 新增「自动续登 → 保存账号密码」，弹层复用 `CasLoginDialog`
（先验后存）。iOS 的 `jwSessionLogout` 由 null 变成真实现：清 Ktor jar +
`WKWebsiteDataStore` 的 cookie/localStorage + Keychain 凭证。

**这条链闭合到哪、哪一步只能真机验**：

| 环节 | 闭合状态 |
|---|---|
| 保存学号密码（先 `tryLogin` 验证再落库） | 代码闭合，`CasSessionTest` 33 例钉住闸门与失败分类；真机要验一次输错的反馈 |
| 导入页登录 → 关页 → 当天取数 | ①（WebView 在）或 ②（jar 里已有会话）能覆盖 |
| 隔天冷启动 → 成绩 / 教材 / 学业 | ② jar 为空 → ③ 用 Keychain 凭证自动登录；**这一步只能在真机验**（Keychain 读写 + CAS 链路 + 真账号） |
| iOS 的 Ktor 登录链本身 | **未验**：Windows 上编不了 iOS，CI 只验编译；真机若出现「重复 Cookie 头」，修法见 `IosCasTransport` 的「未验」段 |

### 12.11 iOS 崩溃与运行日志（2026-11-25 晚，B1/B3）

2026-11-25 那次「安装后一开就闪退」之所以只能靠猜，是因为 iOS 侧此前**只有 `NSLog`**：
它进 Xcode 控制台与 Console.app，用户手里那个包闪退之后不留任何东西。现在有三层：

| 层 | 落点 | 代码 |
|---|---|---|
| 控制台 | `NSLog`（照旧，开发者看） | `platform/Logger.ios.kt` |
| 文件 | `appDataDir()/logs/app.log`，**单文件 256 KB、最多 3 个**（`app.log.1` / `.2` 最旧）；写满就滚，进程内第一次写不截断上一轮的尾巴，再补一条 `===== session … =====` | `platform/IosLogFile.kt` |
| 未捕获异常 | `kotlin.native.setUnhandledExceptionHook` 在终止进程前把栈落进同一个文件；只落盘不吞异常 | 同上 + `IosServices.init` 调用一次 |

**脱敏是硬口径**：每一行落盘前过 `data/ykt/YktDiagnostics` 的键值脱敏（键名清单含
password / token / cardno / account / username / sno / phone…），崩溃栈按行脱敏以保留结构。
脱敏本身失败时整行替换成占位符，**不原样落盘**。

**导出**：设置 → 数据与权限 → 权限与存储 → 「导出诊断日志」把三个文件打成一整段文本
（上限 30 万字，截断会写明），经 `ui/common/ShareIntents` 的 `IosShareTarget` 交给系统。
宿主 VC 从组合里取（`androidx.compose.ui.interop.LocalUIViewController`），不猜
`UIApplication.keyWindow`；**不写全局 `ShareSettings.target`**（那条缝留给将来的 Swift 壳）。

同一页另外两项 iOS 能力与三行体积见 §12.2 的 `ui/me` 行。三个都**未在真机验过**：
目录体积随系统写入变化、分享面板必须真 VC present、`setUnhandledExceptionHook` 的符号
在本仓 Kotlin 2.1.21 上尚未经 macOS 编译（若不可用，删 `IosServices.init` 那一行调用即可，
落盘不受影响）。

## 14. 外观与交互打磨（2026-11-25 晚，U1）

用户的判断是对的：「外形还是非常粗糙」。这一轮只动**看得见的层级与过渡**，不碰页面内容。

### 14.1 抽屉的主卡缩放层级效果（唯一实现）

Android 侧栏展开时主内容卡缩小、拉圆角、上阴影、跟手视差——这是「层级」读得出来还是
「一块遮罩糊上去」的分界。2026-10-20 那一版写在 `:app` 的 `MainActivity` 里，
**2026-11-25 收口到 `shared/commonMain/.../ui/common/DrawerCardTransform.kt`**，
两端吃同一份数值与同一套算式：

| 项 | 值 | 在哪 |
|---|---|---|
| 缩小 | `1 − 0.06·eased`（到 94%） | `DrawerCardShrink` |
| 圆角上限 | 24dp，按 **1dp 量化成 25 档**（组合期建一次形状数组） | `DrawerCardCornerRadius` / `DrawerCardCornerSteps` |
| 阴影上限 | 12dp × eased | `DrawerCardShadowElevation` |
| 横向位移 | `侧栏宽·p − (1−scale)·宽/2`（补回缩放绕中心少走的一半） | `drawerCardTransform` |
| 曲线 | `FastOutSlowInEasing` | 同上 |

**为什么量化而不是逐帧 `RoundedCornerShape(24.dp * eased)`**：后者在 `graphicsLayer` 的绘制
lambda 里每帧新建一个对象。台阶 ≤1dp、肉眼不可见，代价是 25 个常量。**别**改成常量形状——
静止态会裁掉屏幕四角、露出后层。（性能口径的完整理由在文件的 KDoc 里，这里不重复。）

`progress` 是 **lambda**：两端都在 `graphicsLayer` 的绘制期求值，拖动时只失效绘制、
不触发重组。Android 传 `drawer.progress`（0…1），iOS 传 `−drawerState.currentOffset / 侧栏宽`。

### 14.2 iOS 侧的三个决定

1. **继续用 `ModalNavigationDrawer` + `ModalDrawerSheet`，不自己画侧栏**。
   它的 sheet 是 300dp 宽（见下条），不覆盖整屏，主卡变换的可见性没问题；
   手势、点击关闭、无障碍语义都是现成的。整页自绘只会多一处本机验不了的手势代码。
2. **侧栏宽度从 M3 默认的 360dp 改成 300dp**（`IosDrawerWidth`，与 Android 同宽）。
   M3 在 `DrawerSheet` 里加的是 `sizeIn(min = 240, max = 360)`，把固定宽度套在外层即可收敛；
   抽屉的拖动锚点由 M3 按**实测宽度**更新，所以开到底时 `currentOffset` 正好是 −300dp。
   两个理由：抽屉里的身份卡 / 钱包卡 / 服务行是 `ui/life/SideDrawerParts.kt`（按 300dp 排的）；
   360dp 的侧栏在 iPhone 上只剩约 30pt 的卡片边缘，层级效果等于看不见。
3. **遮罩透明**（`scrimColor = Color.Transparent`）。M3 默认那层 32% 黑会让主卡一边缩小
   一边变暗，读成「弹了个模态框」；Android 那边不压暗。遮罩本身保留（点它关抽屉的行为、
   命中区都不变），只是不画颜色。后层底色挂在 `ModalNavigationDrawer` 的 `modifier` 上，
   用 `surfaceContainerLow`（= sheet 的默认容器色），缩小时露出的留白与侧栏同色。

### 14.3 Tab 切换与二级页 push / pop 的过渡

两端都是「同一槽位换内容」，所以都用 `AnimatedContent`，时长与曲线**只从 `Motion` 取**：

| 动作 | 转场 | 档位 |
|---|---|---|
| Tab 切换 | 淡入 + 上浮（屏高的 1/32）+ 旧页淡出 | `Motion.state()` 进场 / `Motion.exit()` 退场 |
| 二级页 push | 新页右侧滑入（整宽）+ 淡入，旧页淡出 | `Motion.page()` / `Motion.exit()` |
| 二级页 pop | 当前页右侧滑出 + 淡出，下一页淡入 | `Motion.pageExit()` / `Motion.exit()` |

- **二级页的键是「栈深 + 栈顶」而不是 `stack` 本身**：`mutableStateListOf` 没有结构相等，
  拿它当 `AnimatedContent` 的键永远判成「没变」，动画一次都不播。深度还决定方向
  （深了 push、浅了 pop），根层就是 `null`。
- 底栏显隐跟着栈空非空淡入淡出（`Motion.state()`）——硬切的话，退栈时底栏会在页面
  滑走之前先蹦出来。
- **`AppBackHandler` 不受影响**：它读的还是 `stack.isNotEmpty()`，退栈走同一条
  `stack.removeAt`。

### 14.4 启动屏

`UILaunchScreen` 从空 dict（= 系统兜底色，与 App 无关的白）改成指**命名颜色**：

- 色值资产 `iosApp/JUWPHub/Assets.xcassets/LaunchBackground.colorset`，两档 = 主题
  `background`（晴空蓝浅色 `#F9F9FF` / 深色 `#0F1419`，见 `ui/theme/Palettes.kt`），跟随系统深浅色；
- `Info.plist` 的 `UILaunchScreen` = `{ UIColorName = LaunchBackground }`。

**限制**：`UILaunchScreen` 是嵌套字典，XcodeGen 的 `INFOPLIST_KEY_*` 构建设置表达不了，
所以这一项只能写在 `Info.plist`（`project.yml` 里没有可写的位置）；颜色资源本身由
`Assets.xcassets` 承担——工程把整个 `JUWPHub` 目录当 sources，新 colorset 由 actool 自动收。

### 14.5 状态栏与底部净空：这一轮**不改**，理由是它们本来就是对的

- **顶部**：Compose 树没有顶到刘海下面，靠的不是 Compose 的 insets，而是 **SwiftUI 宿主**。
  `iOSApp.swift` 是 `WindowGroup { ComposeContainer().ignoresSafeArea(.keyboard) }`——只放开了
  键盘，安全区（顶部 / 底部 / 容器）由 SwiftUI 布局让出，Compose 的 `UIViewController` 整个
  落在安全区内。所以 `pinnedStatusBars()` / M3 `TopAppBar` 的默认 insets 在这个排布下取到 0
  是**正确**的（再垫一层就是双重让位，顶栏会多空一截）。
- **底部**：iOS 的底栏是 `Column` 流里的 M3 `NavigationBar`（不是 Android 那种悬浮胶囊），
  内容本来就在它上方，所以 `LocalBottomBarClearance` 保持默认 `0.dp` 是对的——Android 那条
  「普通底栏形态下净空是 0」的注释在这里同样成立。
- **风险提示（写在这里，不是想象的问题）**：谁哪天给 Swift 视图加 `.ignoresSafeArea()`，
  整棵 Compose 树会立刻顶到刘海与手势条下面，而共享层没有任何补偿。那时要么在
  `IosRoot` 外层加 `windowInsetsPadding(WindowInsets.safeDrawing)`，要么把
  `.ignoresSafeArea()` 去掉。
- **未验**：CMP 1.7.3 在 iOS 上 `WindowInsets.statusBars` 到底返回 0 还是真实安全区高度，
  本机（Windows）拿不到证据——gradle 缓存里没有 uikit 变体的 klib，本机也编不了 iOS。
  上面的结论不依赖它（安全区由 SwiftUI 让出），但**如果真机上顶栏出现「多空一截」**，
  那就是 CMP 也返回了真实值，处置是让 `IosRoot` 里那几个页面的顶栏不再吃 insets。

