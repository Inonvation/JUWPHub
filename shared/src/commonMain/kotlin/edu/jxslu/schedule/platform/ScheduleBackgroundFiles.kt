package edu.jxslu.schedule.platform

/**
 * 课表页背景图的**文件删除**能力（iOS 移植 S17，2026-11-25）。
 *
 * ## 为什么只有 `delete` 一个方法
 *
 * 背景图这条链在 `:app` 里分三段：弹系统选图器（`PickVisualMedia`）→ 解码压缩落盘
 * （`ImageImport` + `ScheduleBackgroundStore.importUri`）→ 写偏好、删旧文件（`MeViewModel`）。
 * 前两段都是 Android 专有物（`ContentResolver` / `BitmapFactory` / `EXIF`），第三段是纯逻辑。
 * 搬进 `commonMain` 的只有第三段，它唯一需要平台代劳的是「把旧文件删掉」——
 * 写入在平台动作里已经完成了（文件名从平台动作传回 VM）。
 *
 * 所以这里只放一个方法：**多一个方法而没有调用方，就是给两条装配根各留一半的实现机会**
 * （见 [AppServices] 的形状说明）。
 *
 * ## null 的语义
 *
 * [AppServices.scheduleBackgroundFiles] 返回 null = 本平台没接背景图落盘：
 * iOS 侧还没有 `UIImage` 的解码压缩实现（要 `UIGraphicsImageRenderer` 那一套），
 * 于是选图入口整组不渲染、VM 里也不删文件（没有文件可删）。
 * 这不是「以后补」的占位——Android 侧的实现是完整的，iOS 侧要接的是另一套图像 API。
 */
interface ScheduleBackgroundFiles {

    /**
     * 删掉一个背景图文件；[fileName] 为 null 或文件不存在都当无事发生。
     *
     * 不抛异常：删不掉只会在磁盘上留一个孤儿文件，冷启动的 `sweep` 会扫掉
     * （`JuwApplication.ensureDefaults` 那条链），不该因为删不掉而让设置页报错。
     */
    suspend fun delete(fileName: String?)
}
