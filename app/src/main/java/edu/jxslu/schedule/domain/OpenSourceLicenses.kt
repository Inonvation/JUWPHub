package edu.jxslu.schedule.domain

/** 一条开源组件记录：[name] 展示名，[license] 许可名，[url] 官方仓库或主页。 */
data class LicenseEntry(
    val name: String,
    val license: String,
    val url: String,
)

/**
 * 「关于 → 开源许可」卡片的数据（DESIGN §3.3）。
 *
 * 分两段：[ENTRIES] 是随 APK 分发的主要组件，[REFERENCES] 是只参考过接口契约 /
 * 交互形态、**代码未进入本仓库**的公开项目（README「参考与致谢」那几条）。测试专用的
 * JUnit 不上屏。许可名逐个核过官方仓库的 LICENSE 文件或构件 POM（组件 2026-09-27、
 * 参考项目 2026-11-03），**改依赖 / 换参考来源时要一并核**，不要按印象填。
 *
 * HugeIcons 单独说明：图标集本身（hugeicons/hugeicons）是 MIT，但本项目实际引用的
 * Compose 封装 `com.github.rikkahub:hugeicons-compose` 仓库里没有 LICENSE 文件，
 * GitHub 也识别不出许可。这里按上游图标集口径标注，并在弹窗底部写明。
 */
object OpenSourceLicenses {

    /** 本应用自己的许可。 */
    const val SELF_NAME: String = "JUWPHub（本应用）"
    const val SELF_LICENSE: String = "GPL-3.0"

    /** HugeIcons 的口径说明，逐字显示在弹窗底部。 */
    const val HUGEICONS_NOTE: String =
        "HugeIcons 图标集为 MIT；本项目引用的 Compose 封装仓库未声明许可，此处按上游图标集口径标注。"

    /** 弹窗底部的一句总说明。 */
    const val FOOTER: String =
        "以上组件与参考项目的完整许可文本见各自官方仓库；本应用的许可全文见仓库根目录 LICENSE。"

    /** 「参考项目」分区标题，逐字显示在组件清单之后。 */
    const val REFERENCES_TITLE: String = "参考项目（未包含其代码）"

    /** 参考项目的口径说明，逐字显示在该分区下方。 */
    const val REFERENCES_NOTE: String =
        "以上仓库只参考接口地址、参数与交互形态，未复用其代码；kqcx-bikemap 为 AGPL-3.0，" +
            "复用会要求本项目改按 AGPL 发布。许可名为各仓库 LICENSE 现状（2026-11-03 核对）。"

    val ENTRIES: List<LicenseEntry> = listOf(
        LicenseEntry(
            name = "AndroidX：Compose · Material3 · Room · Glance · Navigation · DataStore · Security · WorkManager",
            license = "Apache-2.0",
            url = "https://github.com/androidx/androidx",
        ),
        LicenseEntry(
            name = "Kotlin 标准库 · kotlinx 协程与序列化",
            license = "Apache-2.0",
            url = "https://github.com/JetBrains/kotlin",
        ),
        LicenseEntry(
            name = "OkHttp · Retrofit（含 kotlinx-serialization 转换器）",
            license = "Apache-2.0",
            url = "https://github.com/square/okhttp",
        ),
        LicenseEntry(
            name = "ZXing core（二维码生成）",
            license = "Apache-2.0",
            url = "https://github.com/zxing/zxing",
        ),
        LicenseEntry(
            name = "osmdroid（地图）",
            license = "Apache-2.0",
            url = "https://github.com/osmdroid/osmdroid",
        ),
        LicenseEntry(
            name = "HugeIcons（图标）",
            license = "MIT",
            url = "https://github.com/hugeicons/hugeicons",
        ),
    )

    /**
     * 只作参考、代码未进入本仓库的公开项目，与 README「参考与致谢」一节同源。
     *
     * quzhi-lite 仓库没有 LICENSE 文件、GitHub 也识别不出许可，按「未声明许可」标注，
     * 不要替它猜一个。
     */
    val REFERENCES: List<LicenseEntry> = listOf(
        LicenseEntry(
            name = "kqcx-bikemap（快趣出行车辆接口）",
            license = "AGPL-3.0",
            url = "https://github.com/dlkz/kqcx-bikemap",
        ),
        LicenseEntry(
            name = "拾光课程表（课表 JSON 互通格式）",
            license = "Apache-2.0",
            url = "https://github.com/XingHeYuZhuan/shiguangschedule",
        ),
        LicenseEntry(
            name = "light-life（胖乖生活 · 开水协议）",
            license = "MIT",
            url = "https://github.com/Inonvation/light-life",
        ),
        LicenseEntry(
            name = "linyu（趣智校园接口）",
            license = "MIT",
            url = "https://github.com/yehu-imei/linyu",
        ),
        LicenseEntry(
            name = "quzhi-lite（趣智校园接口）",
            license = "未声明许可",
            url = "https://github.com/wzk-chi/quzhi-lite",
        ),
    )
}
