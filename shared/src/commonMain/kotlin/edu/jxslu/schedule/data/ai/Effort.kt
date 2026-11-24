package edu.jxslu.schedule.data.ai

/**
 * 思考档位：关闭时不带思考、强制命名工具调用；开启时反过来。
 *
 * 2026-11-24 从 `AiScheduleClient` 的嵌套枚举提出来放进 `:shared`（取值、`wire` 与
 * 中文标签一字未改）。原因只有一个：`DisplayPrefsStore`（`aiThinkingEffort` 的默认值
 * 与写入钳制都要读它）跟着偏好层搬进了共享模块，而 `:app` 的 `AiScheduleClient` 反向
 * 不可见。放在这个包下，`:app` 里原有同包的引用**不需要动**，只有别的包要补 import。
 */
enum class Effort(val wire: String) {
    NONE("none"),
    LOW("low"),
    HIGH("high"),
    MAX("max");

    val label: String
        get() = when (this) {
            NONE -> "关闭"
            LOW -> "低"
            HIGH -> "高"
            MAX -> "最高"
        }

    companion object {
        fun fromWire(value: String?): Effort =
            entries.firstOrNull { it.wire == value } ?: NONE
    }
}
