package edu.jxslu.schedule.data.power

/**
 * 缴费平台异常分类（UI 按类型给文案）。
 *
 * 2026-11-24（iOS 移植 P1）：从 `PowerModels.kt` 抽成独立文件搬进 `:shared`。
 * 原因是 `PowerClient` 要迁到 shared，而它的错误归一化全靠这个类型；`PowerModels.kt`
 * 自己还带着 `java.time`（要单独迁移），所以只把零平台依赖的这一半先拿出来。
 * 包名不变，`:app` 侧调用方（`LifeViewModel` / `PowerBillViewModel` 的
 * `catch (e: PowerException.Network)`）零改动。
 */
sealed class PowerException(message: String) : Exception(message) {

    /** 网络不可达 / 超时。 */
    class Network(cause: Throwable) : PowerException("网络不给力，稍后重试")

    /** 凭证类（登录被拒）：学号或查询密码不对。 */
    class Credential(message: String) : PowerException(message)

    /** 协议类：平台返回结构与预期不同，或参数被拒。 */
    class Protocol(message: String) : PowerException(message)

    /**
     * 未在缴费平台确认寝室（`singleFeeitem.sceneinfo` 为空，DESIGN §4.24，2026-10-08）。
     *
     * 这不是故障：官方 H5 同样以 `sceneinfo` 空为「进选择流程」的判据，用户在 App 内
     * 选好房间确认（`sceneBind/add`）后自然恢复。UI 收到它要给「去设置」的出口，
     * 别当普通读取失败报错。
     */
    class RoomNotBound(message: String = "尚未在缴费平台确认寝室") : PowerException(message)
}
