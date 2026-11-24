package edu.jxslu.schedule.data.qiekj

/**
 * 胖乖仓库要的三条**存储缝**（iOS 移植 S11，2026-11-24）。
 *
 * ## 为什么仓库在 `:shared`、存储却在各平台
 *
 * [QiekjRepository] 是纯编排（登录态、开水流程、签到），没有任何平台依赖；但它读写三份
 * **平台存储**，而这三份恰好都是 Android 专有介质：
 *
 * | 存储 | Android 实现 | 为什么不能搬进 commonMain |
 * |---|---|---|
 * | 凭证（token / 手机号） | `QiekjTokenStore`（`EncryptedSharedPreferences`） | 密钥在 Android Keystore，commonMain 没有等价物；iOS 将来走 Keychain |
 * | 订单快照 | `QiekjOrderHistoryStore`（普通 `SharedPreferences`） | 介质是 XML 偏好，改成 JSON 文件等于让老用户丢掉已有的 50 笔本地账单 |
 * | 「已过期」标记 | `SessionExpiryStore`（普通 `SharedPreferences`，五个第三方功能共用） | 同上，且它属于账号中心那一族，不归胖乖独占 |
 *
 * 所以仓库只认这三个接口，实现留在 `:app`。**接口刻意做窄**：每个方法都是仓库真正调到的那几个，
 * 不预留「以后可能要用」的能力（与 `AppServices` 同一条纪律）。
 */

/**
 * 胖乖登录凭证：token 与手机号。
 *
 * 只存这两个值，不含密码——胖乖登录是短信验证码，没有密码可存。
 * 实现方必须保证：token 落加密存储、不进日志、随 [clear] 一并抹掉。
 */
interface QiekjCredentialStore {

    /** 当前 token；未登录回 null。 */
    fun readToken(): String?

    /** 落盘 token。只在**已确认有效**时调用，见 [QiekjRepository.validateToken]。 */
    fun saveToken(token: String)

    /** 上次登录用的手机号（只用于表单回填，不是凭证）。 */
    fun readPhone(): String?

    fun savePhone(phone: String)

    /** 退出 / 判定失效：token 与手机号一起清。 */
    fun clear()
}

/**
 * 本地订单快照（DESIGN §4.10）：最多 [MAX_ITEMS] 笔、只记本机结算过的单。
 *
 * 与 [QiekjRepository.serverOrders] 互补：服务端账单要联网，快照在离线时也能看。
 */
interface QiekjOrderStore {

    /** 全部快照，按完成时间倒序。读不出来回空表（不抛）。 */
    fun list(): List<OrderHistoryItem>

    /** 追加一条；同 `orderNo` 覆盖，超出上限丢最旧的。 */
    fun add(item: OrderHistoryItem)

    /** 退出登录 / 判定失效时清空。 */
    fun clearAll()

    companion object {
        /** 快照上限：与 `QiekjOrderHistoryStore` 的实现常量同一个口径，改一处必须改两处。 */
        const val MAX_ITEMS = 50
    }
}

/**
 * 「登录状态已过期」留痕（2026-10-21）。
 *
 * 仓库在**判定失效的那一刻**调 [markExpired]，登录成功与主动退出调 [clearExpired]；
 * 进页消费（`consume`）不归仓库管，那是账号中心/页面的事。
 *
 * 为什么是两个无参方法而不是直接收 `SessionExpiryStore`：那个类在 `:app`（`Context` +
 * `SessionService` 枚举，五个第三方功能共用），commonMain 看不见它。Android 侧的适配
 * 就是把这两个方法转发到 `SessionExpiryStore.mark/clear(SessionService.Qiekj)`。
 */
interface QiekjExpiryMarker {

    /** 服务端判定 token 失效：留一条「已过期」，供下次进页提示。 */
    fun markExpired()

    /** 登录成功 / 用户主动退出：这不算过期，不需要提示。 */
    fun clearExpired()
}

/**
 * 「正在出水」的本机会话登记（iOS 移植 T2，2026-11-25）。
 *
 * 介质是 `:app` 的 `QiekjWateringStore`（普通 `SharedPreferences` + `StateFlow`），
 * 它同时被今日页服务卡与侧栏读，所以类本身留在 `:app`；开水页搬进共享层后只需要
 * **写**这一面，于是收成两个方法——与 [QiekjOrderStore] 同一条「接口刻意做窄」的纪律。
 *
 * 读侧（`watering` 流）不在这里：目前唯一的读者是今日页 / 侧栏，两者都还在 `:app`，
 * 走 `Graph.qiekjWatering(context).watering`。等今日页搬过来时再加读侧，
 * **不要**提前把整条流搬进共享层（那会多一份没人读的状态）。
 *
 * ## [AUTO_CLOSE_SECONDS] 为什么在这里
 *
 * 它是开水页 165 秒兜底与服务端自动关阀窗口的**唯一口径**，原来取自
 * `QiekjWateringSession.AUTO_CLOSE_SECONDS`（`:app`）。开水页搬走后那个常量在共享层看不见，
 * 所以在这里再声明一份；`:app` 的 `QiekjWateringSession.AUTO_CLOSE_SECONDS` 与它必须同值
 * （那边今日页的进度条也用它），**改一处必须两处一起改**。
 */
interface QiekjWateringSessions {

    /**
     * 服务端确认出水时登记（流程首次进入 Working）。
     *
     * @param startedAtMillis 出水的起始时刻（`AppClock.nowMillis()`），跨窗口展示的唯一时间锚点。
     * @param deviceName 设备显示名，空串表示未命名。
     * @param goodsId 设备业务 id；null 表示服务端没给。
     */
    fun begin(startedAtMillis: Long, deviceName: String, goodsId: String?)

    /** 结算完成 / 流程失败 / 165 秒超时收尾时清掉。重复调用是空操作。 */
    fun clear()

    companion object {
        /** 服务端出水自动关阀的兜底秒数（与 `QiekjWateringSession.AUTO_CLOSE_SECONDS` 同值）。 */
        const val AUTO_CLOSE_SECONDS = 165
    }
}
