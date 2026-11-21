package edu.jxslu.schedule.ui.jwvw

/**
 * 课表导入成功后的「口径说明」（2026-11-21 用户口径）：导入报出的成功只代表这次解析写进了库，
 * 不代表课表与教务当前状态完全一致——调课、停课、临时换教室都由教务在导入之后才录入，
 * 脚本抓不到。用户的原话是「避免漏课或跑空」，所以这句必须跟成功结果一起出现，
 * 不能只在文档里写。
 *
 * **三处共用这一个常量，别在各处另写一份**：
 * - 导入窗口写库成功后落回主界面的气泡（[scheduleOutcomeText]）；
 * - 首启引导后台归档的「同步完成」弹窗课表行（`MainActivity.OnboardingSyncResultDialog`）；
 * - 课表管理页「从教务导入全部学期」的结果提示（`TimetableManageScreen`）。
 *
 * 不带句末标点：调用点自己拼「。」，否则气泡里会出现「。。」。
 */
internal const val SCHEDULE_IMPORT_CAVEAT =
    "课表由脚本自动识别，调课等变动可能遗漏，请以教务课表为准，避免漏课或跑空"
