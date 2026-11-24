package edu.jxslu.schedule

/**
 * 二级页种类；通过 extra 传给 `SubpageActivity`，值必须与 enum 名一致。
 *
 * iOS 移植 S4（2026-11-24）：从 `:app` 的 `SubpageActivity.kt` 整段搬到共享层——
 * 它只是个纯枚举，却挡住了 `ScheduleSync` / `ScoreSync` / `ExamSync` 等仓库类
 * 往 `commonMain` 搬（那些类要拿 `.name` 当收件箱落点）。包名不变，`:app` 里
 * 四十多个引用点的 import 一行都不用改。
 */
enum class SubpageScreen {
    /** 设置 → 数据与权限 → 课表管理 */
    TIMETABLE_MANAGE,
    /** 设置 → 数据与权限 → 导入 / 导出 / 清空 */
    DATA_SETTINGS,
    /** 设置 → 课表 → 调课（快捷操作，DESIGN §4.11） */
    COURSE_TWEAK,
    /** 我的/今日 → 胖乖生活（开水卡显示与点击方式并入页内，DESIGN §3.4） */
    WATER,
    /** 今日 → 趣智校园开热水（DESIGN §4.30；与胖乖生活并排的半行卡） */
    QZXY,
    /** 趣智校园 → 诊断与调试（DESIGN §3.18；设备现场 · 服务表 · 试签名 · 日志） */
    QZXY_DEBUG,
    /** 趣智校园 → 消费记录（按时段筛 · 按日分组，2026-11-05，DESIGN §4.30） */
    QZXY_BILLS,
    /** 设置 → 桌面与日历 → 桌面小组件（DESIGN §3.6） */
    WIDGET_SETTINGS,
    /** 设置 → 桌面与日历 → 日历同步（提醒时长 · 一键删除，DESIGN §4.12） */
    CALENDAR_SETTINGS,
    /** 设置 → 提醒 → 提醒（DESIGN §3.7） */
    REMINDER_SETTINGS,
    /** 设置 → 功能 → 快捷方式（DESIGN §3.8） */
    SHORTCUTS,
    /** 我的 → 成绩查询（按学期存储，DESIGN §4.15） */
    SCORES,
    /** 成绩查询 → 成绩设置（分组 · 排序 · 统计口径 · 成绩变动提醒，DESIGN §4.33） */
    SCORE_SETTINGS,
    /** 我的 → 学习 → 考试安排（考试列表 · 考试变动提醒，DESIGN §4.33） */
    EXAMS,
    /** 考试安排 → 设置（提醒 · 间隔 · 导入到课表，2026-10-20） */
    EXAM_SETTINGS,
    /** 教务 → 教材（当前课表学期的教材清单，2026-10-20） */
    TEXTBOOKS,
    /** 教务 → 理论课表（本机课表只读清单，顶栏外链开教务页，2026-10-21） */
    THEORY_SCHEDULE,
    /** 教务 → 实验课表（本机课表只读清单，顶栏外链开教务页，2026-10-21） */
    LAB_SCHEDULE,
    /** 教务 → 学籍信息（教务学籍卡片，2026-10-20；入口在左侧栏头像区） */
    STUDENT_INFO,
    /** 教务 → 教学日历（第 1 周周一 · 每周起止，2026-10-20） */
    TEACHING_CALENDAR,
    /** 我的 → 学习 → 学业完成情况（培养方案达成度，DESIGN §3.17 / §4.29） */
    SCHOLAR,
    /** 我的 → 学习 → 培养方案（课程设置总表 · 按学年/学期看计划课，DESIGN §3.26 / §4.41） */
    CURRICULUM,
    /** 我的 → 学习 → 实习基地查看（教务基地清单，DESIGN §3.27 / §4.43） */
    INTERNSHIP_BASES,
    /** 我的 → 学习 → 选课（选课记录 · 轮次 · 进入选课，DESIGN §3.19 / §4.35） */
    SELECTIONS,
    /** 选课 → 应用内选课中心（课程列表 · 筛选 · 选课/退课，DESIGN §3.22 / §4.36） */
    SELECTION_CENTER,
    /** 选课 → 预选清单（预购匹配：想选的课与教师 · 优先级，DESIGN §3.20 / §4.36） */
    SELECTION_WISHES,
    /** 选课 → 已选课程（教务选课日志按学期，2026-10-01 自选课主页挪出，DESIGN §3.19 / §4.35） */
    SELECTION_RECORDS,
    /** 导出成绩单 → 最近导出（DESIGN §3.14；列表即 filesDir/transcripts，最多 10 份） */
    TRANSCRIPTS,
    /** 今日 → 快趣出行 · 骑行页（DESIGN §3.9；地图 + 抽屉三态，两档共用一套结构） */
    RIDE,
    /** 我的 → 校园卡付款码设置（开关 · 凭证，DESIGN §3.10） */
    CAMPUS_CARD_SETTINGS,
    /** 我的 → 教务账户（原生信息页，DESIGN §3.3；不再一点就开导入 WebView） */
    JW_ACCOUNT,
    /** 设置 → 外观与交互（主题 · 字体 · 导航，DESIGN §3.3） */
    APPEARANCE_SETTINGS,
    /** 设置 → 课表（学期 · 作息，DESIGN §3.3） */
    TIMETABLE_HUB,
    /** 设置 → 提醒 → 免扰时段（预置夜间/午休 · 可编辑，时段内通知推迟补发，DESIGN §3.7，2026-10-07） */
    QUIET_HOURS,
    /** 我的 → 账号与登录（全量账号状态 · 补登入口，2026-10-20，DESIGN §3.3） */
    ACCOUNTS,
    /** 设置 → 数据与权限 → 权限与存储（权限 · 缓存 · 诊断日志，DESIGN §3.3） */
    PERMISSIONS_STORAGE,
    /** 设置 → 关于 → 关于与更新（版本 · 更新 · 开源，DESIGN §3.3） */
    ABOUT_UPDATE,
    /** 设置 → 关于 → 条款与声明（用户须知 · 免责 · 充值风险，DESIGN §3.3） */
    TERMS_DECLARATIONS,
    /** 设置 → 关于 → 支持开发（说明文案 + 赞赏码，完全自愿，2026-11-01） */
    SUPPORT_DEV,
    /** 今日 → 校园卡付款码展示页（DESIGN §3.10；FLAG_SECURE 独立窗口） */
    PAY_CODE,
    /** 付款码/设置 → 消费流水（月汇总 + 分页列表，DESIGN §4.19 B4） */
    CAMPUS_STATEMENT,
    /** 侧栏钱包 / 充值 → 缴费账单（寝室电费充值/退款，按月汇总 + 明细，DESIGN §3.13） */
    POWER_BILL,
    /** 我的 → 笔记·课件库（按课程分组，DESIGN §3.11） */
    NOTES,
    /** 某课程的笔记列表（需 `courseName`，DESIGN §3.11） */
    NOTES_COURSE,
    /** 笔记详情/编辑（需 `courseName` + `itemId`，itemId=0 表示新建，DESIGN §3.11） */
    NOTE_DETAIL,
    /** 我的 → 作业库（按课程分组） */
    HOMEWORK,
    /** 某课程的作业列表（需 `courseName`） */
    HOMEWORK_COURSE,
    /** 作业详情/编辑（需 `courseName` + `itemId`，itemId=0 表示新建） */
    HOMEWORK_DETAIL,
    /** 今日页作业卡/截止提醒 → 作业中心（未完成汇总，DESIGN §3.11） */
    HOMEWORK_TODO,
    /** 我的 → 校园服务 → 快趣账号（登录 · 骑行状态只读查询，DESIGN §4.32） */
    KVCX,
    /** 快趣账号 → 历史订单（服务端 `queryOrderList`，按月份 + 车型查，DESIGN §4.32，2026-11-05） */
    KVCX_ORDERS,
    /** 我的 → 校园服务 → 校历（仓库镜像 + 内置兜底，DESIGN §4.34） */
    CAMPUS_CALENDAR,
    /**
     * 今日页报修卡 / 收件箱办结通知 → 宿舍报修进度（DESIGN §3.15 / §4.49，2026-10-19）。
     * 纯原生只读页；「去学工报修页核对」另开 [XgFormActivity]。
     */
    DORM_REPAIR,
    /** 教务 → 请假系统（请假 / 销假 / 续假三张学工表单的入口页，2026-11-08）。 */
    LEAVE_HUB,
    /** 我的 → 校园服务 → U净（美的校园洗衣：空闲看板 · 扫码识别，DESIGN §4.37） */
    UJING,
    /** U净 扫码后 → 下单页（档位单选 · 水温 · 投放加购，数据走 UjingDraftStore，DESIGN §3.23） */
    UJING_ORDER,
    /** U净 → 历史账单（本 App 发起的订单留痕，DESIGN §3.23 / §4.37） */
    UJING_HISTORY,
    /** 我的 → 校园服务 → 问渠 · 志愿服务（手机号+密码登录 · 志愿工时/劳动教育只读，DESIGN §3.28 / §4.45） */
    WENQU,
    /**
     * 问渠 → 我的志愿服务记录（DESIGN §3.28）。
     * **是独立页而不是弹层**：记录详情本身也要弹层，弹层里再开弹层会互相打架（2026-10-08）。
     */
    WENQU_RECORDS,
    /** 问渠 → 设置（记录入口 · 新增活动提醒 · 退出登录，DESIGN §3.28，2026-10-08）。 */
    WENQU_SETTINGS,
    /**
     * 问渠 → 蹲点抢活动（DESIGN §3.28 / §4.45，2026-10-11）：
     * 手动开始、每 3 秒只轮询活动列表第一页，命中目标活动直接弹确认报名
     * （**不自动提交**；蹲点只在打开本页时进行）。
     */
    WENQU_GRAB,
    /** 变动检测处置页（差异列表 · 勾选合并 · 忽略，DESIGN §4.17，2026-10-04 重立） */
    SCHEDULE_CHANGES,
    /** AI 调课（粘贴调课通知 → 模型给结构化建议 → 勾选落库，DESIGN §4.39，2026-10-06） */
    AI_SCHEDULE,
    /** AI 调课的服务配置（地址 · 模型 · API Key · 脱敏，DESIGN §4.39） */
    AI_SETTINGS,
    /** AI 调课的应用记录与撤回（本机留痕，跨会话，DESIGN §3.25 / §4.39，2026-10-07） */
    AI_HISTORY,
    /** 收件箱页（考试/成绩/调课/轮次/余额的留痕，DESIGN §3.24 / §4.38）；
     *  入口 = 今日页收件箱卡的「查看更多」（2026-10-21 起） */
    INBOX,
    /** 课表页分享弹层第 4 行 → 导出为图片（预览本周课表，保存到相册 / 分享，DESIGN §4.12，2026-10-09） */
    SCHEDULE_SHARE,
    /** 天气详情（实时 · 今天/明天提醒 · 未来 7 天与 24 小时，DESIGN §3.29，2026-10-11） */
    WEATHER,
    /** 天气设置（今日页顶栏提示开关，DESIGN §3.29，2026-10-11） */
    WEATHER_SETTINGS,
    /** 今日页「+」→ 百宝箱（小工具集合，DESIGN §4.47，2026-10-12） */
    TOOLBOX,
    /** 百宝箱 → 图片水印（文字平铺满图，DESIGN §4.47） */
    TOOLBOX_WATERMARK,
    /** 百宝箱 → 图片拼接（多图竖拼长图，DESIGN §4.47） */
    TOOLBOX_STITCH,
    /** 百宝箱 → 菜鸟批量查（多单号一次送菜鸟查件，DESIGN §4.47） */
    TOOLBOX_CAINIAO,
}
