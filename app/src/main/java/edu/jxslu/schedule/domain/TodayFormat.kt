package edu.jxslu.schedule.domain

import java.time.format.DateTimeFormatter

/**
 * 课程文案格式化：**今日页与桌面小组件共用同一份**。
 *
 * 根因：这些函数原先私有在 `ui/today/TodayScreen.kt` 里，桌面小组件（`ui/widget`）
 * 需要完全相同的 `第3-4节` / `10:15` / `@南B208 · 陈磊` 口径。若各写一遍，
 * 两边迟早会在「自定义时间课怎么显示」「地点要不要 @」这类细节上分叉。
 * 同时 domain 侧不得引用 ui 包，故整体下移到 domain（纯字符串运算，零 Android 依赖）。
 */

/** `9月18日`：今日页标题与调课页日期标签共用（此前各页私写 formatter，口径会漂）。 */
val MONTH_DAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("M月d日")

/** `9/18`：课表网格日期表头（单列窄，放不下「9月18日」）。 */
val SHORT_DAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("M/d")

/** `第3-4节` / `第5节`。传整门课，避免调用点各自拼 start/end。 */
fun sectionRange(course: Course): String {
    val start = course.startSection
    val end = course.endSection
    return if (end > start) "第${start}-${end}节" else "第${start}节"
}

/** 起始时刻 `10:15`；取不到给 `--:--`。自定义时间课以 customStartTime 为准（唯一口径）。 */
fun clockOf(slots: List<TimeSlot>, course: Course): String =
    ScheduleCalculator.courseStartMinutes(slots, course)?.let(::minutesToClock) ?: "--:--"

/** `615` → `10:15`。 */
fun minutesToClock(minutes: Int): String =
    "%02d:%02d".format(minutes / 60, minutes % 60)

/** `10:15–11:40 · @南B208 · 陈磊`；缺项自动省略，全缺给空串。 */
fun metaLine(slots: List<TimeSlot>, course: Course): String {
    val start = ScheduleCalculator.courseStartMinutes(slots, course)
    val end = ScheduleCalculator.courseEndMinutes(slots, course)
    val range = if (start != null && end != null) {
        "${minutesToClock(start)}–${minutesToClock(end)}"
    } else {
        null
    }
    val location = compactPosition(course.position)
    return listOfNotNull(
        range,
        location.takeIf { it.isNotBlank() }?.let { "@$it" },
        course.teacher.takeIf { it.isNotBlank() },
    ).joinToString(" · ")
}

/**
 * `@南B208 · 陈磊`（**不含时刻**）。今日页统一课程卡的左列已单列开始时刻
 * （[clockOf]），meta 行不再重复时间段，故这里只给地点与教师。
 * 桌面小组件仍用 [metaLine]（那边没有独立的时刻列）。
 */
fun placeTeacherLine(course: Course): String {
    val location = compactPosition(course.position)
    return listOfNotNull(
        location.takeIf { it.isNotBlank() }?.let { "@$it" },
        course.teacher.takeIf { it.isNotBlank() },
    ).joinToString(" · ")
}

/**
 * 灵动岛倒计时卡片的正文：`@南B208 · 10:15 上课`（无地点只留后半句）。
 *
 * **地点在前**：倒计时卡是「马上要出门」的场景，地点是唯一不能猜的信息；
 * 上课时刻放后面，与芯片的剩余分钟数互为印证。不走 [metaLine] 是因为那里
 * 以时间段开头、地点被挤到中段，而卡片正文只有一行。
 */
fun liveClassLine(slots: List<TimeSlot>, course: Course): String {
    val location = compactPosition(course.position)
    return listOfNotNull(
        location.takeIf { it.isNotBlank() }?.let { "@$it" },
        "${clockOf(slots, course)} 上课",
    ).joinToString(" · ")
}

/**
 * 灵动岛芯片上的短文案：`南B208·8分`（无地点时退回 `剩8分`）。
 *
 * 芯片宽 96dp、只有一个文字槽（官方口径：不到 7 个字符完整显示，放不下一半就只剩图标），
 * 所以地点与分钟数只能靠**压缩拼接**共用这一行：地点先过 [chipPlace] 收到 5 字以内，
 * 再接分钟数——7~9 个字符，系统按「能放多少放多少」处理。
 * **地点占前位**：这是「马上要出门」的场景，没有教室号的那串分钟数没有用；被截时先丢后半截。
 * 分钟数由 `ReminderPlan.minutesLeft` 给，这里只管拼。
 */
fun liveChipText(position: String, minutesLeft: Long): String {
    val place = chipPlace(position)
    return if (place.isEmpty()) "剩${minutesLeft}分" else "$place·${minutesLeft}分"
}

/** `3-4 节` / `5 节`：今日页统一卡左列的节次短标签（与 [sectionRange] 的「第3-4节」区分）。 */
fun sectionShortLabel(course: Course): String =
    if (course.endSection > course.startSection) {
        "${course.startSection}-${course.endSection} 节"
    } else {
        "${course.startSection} 节"
    }

/** `1` → `一`（星期几的单字写法）。 */
fun dayLabel(day: Int): String = when (day) {
    1 -> "一"
    2 -> "二"
    3 -> "三"
    4 -> "四"
    5 -> "五"
    6 -> "六"
    7 -> "日"
    else -> day.toString()
}

private val parenRegex = Regex("[（(]([^）)]*)[）)]")
private val openParenTailRegex = Regex("[（(]([^）)]*)$")

/**
 * 地点压缩。
 *
 * 根因：教务返回的是 `教学南大楼(南B302)` 这种带楼栋全名的写法，
 * 7 列布局下单列内容宽约 37dp，原样显示会被截成 `教学南…`，反而看不到教室号。
 *
 * 规则（按顺序）：
 * 1. 取最后一对括号里的非空内容 → `教学南大楼(南B302)` → `南B302`
 * 2. 括号没闭合也认 → `教学北大楼(北B102` → `北B102`
 *    （老版本解析把结尾右括号 trim 掉了，库里已经存了一批这样的脏数据，只能在这层兜住）
 * 3. 只剩括号残留（教务对无地点课程返回 `()`）→ 视为无地点
 * 4. 没有括号就原样返回
 */
fun compactPosition(position: String): String {
    val raw = position.trim()
    if (raw.isEmpty()) return ""
    val inners = parenRegex.findAll(raw)
        .map { it.groupValues[1].trim() }
        .filter { it.isNotEmpty() }
        .toList()
    if (inners.isNotEmpty()) return inners.last()

    val tail = openParenTailRegex.find(raw)?.groupValues?.get(1)?.trim()
    if (!tail.isNullOrEmpty()) return tail

    return if (raw.any { it == '(' || it == '（' || it == ')' || it == '）' }) "" else raw
}

/** 芯片上地点的字符预算：`南B208`(5) 是教务括号写法的常见上限，再长就挤掉分钟数。 */
private const val CHIP_PLACE_MAX = 5

/**
 * 芯片上的地点牌：[compactPosition] 之后再压一道，收到 5 个字符以内。
 *
 * `教学南大楼(南B302)` → `南B302`（括号已由 [compactPosition] 剥掉）；
 * 楼名过长时取「首字 + 房间号」——`工程训练中心210` → `工210`，房间号是唯一不能猜的部分。
 * 没有房间号（纯楼名）就截前 5 字：宁可给楼名，也别把房号截成半个（`工程训练中`）。
 */
fun chipPlace(position: String): String {
    val compact = compactPosition(position)
    if (compact.length <= CHIP_PLACE_MAX) return compact
    val room = compact.takeLastWhile { it.isDigit() }
    return if (room.isEmpty()) compact.take(CHIP_PLACE_MAX) else compact.first() + room
}

/**
 * 课程卡的教材名（2026-10-07；2026-10-08 收口为课名右侧同名一行、多本「等 N 本」）：
 * 只取书名，作者 / 出版社 / 版次 / ISBN 不进卡片（用户口径：卡片空间有限，只要教材名称；
 * 完整书目在课程详情弹窗）。
 *
 * 单本直接显示书名；**多本显示「书名1 等 N 本」**——书名可能很长，` / ` 连接时
 * 第二本起永远被单行省略截掉，用户既看不到也不知道有更多；「等 N 本」把总数
 * 放进首行，与作业汇总通知的「等 N 项」同一收口形态。
 * 空 / 空白书名跳过（教务没填书名只剩其他列的行不该占卡面）；全空给 null，
 * 调用方按「没有教材」处理、不占位。
 */
fun textbookTitlesLine(titles: List<String>): String? {
    val cleaned = titles.map { it.trim() }.filter { it.isNotEmpty() }
    if (cleaned.isEmpty()) return null
    return if (cleaned.size == 1) {
        cleaned.single()
    } else {
        "${cleaned.first()} 等 ${cleaned.size} 本"
    }
}

/**
 * 课程卡的备注预览（2026-10-08；同日收口为卡内末行右端、与 meta 同一行）：备注是
 * 用户手写内容，长短不受控，卡片上只能给一行——超 [RemarkPreviewLimit] 字截断加省略号，
 * 完整内容在详情面板「备注」行（§4.3）。空白（含纯空格/换行）给 null，卡片不占位。
 */
const val REMARK_PREVIEW_LIMIT = 20

fun remarkPreview(remark: String): String? {
    val cleaned = remark.trim()
    if (cleaned.isEmpty()) return null
    if (cleaned.length <= REMARK_PREVIEW_LIMIT) return cleaned
    var cut = cleaned.take(REMARK_PREVIEW_LIMIT)
    // 截断点落在代理对中间（备注含 emoji 等增补平面字符）时少取一位，
    // 不产出孤立半对——渲染层会把它画成替换符
    if (Character.isHighSurrogate(cut.last()) &&
        cleaned.getOrNull(REMARK_PREVIEW_LIMIT)?.let { Character.isLowSurrogate(it) } == true
    ) {
        cut = cut.dropLast(1)
    }
    return "$cut…"
}

