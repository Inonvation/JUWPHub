package edu.jxslu.schedule.ui.ebike

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import edu.jxslu.schedule.data.prefs.AMAP_CHOICE_UNSET
import edu.jxslu.schedule.data.prefs.DisplayPrefsStore
import edu.jxslu.schedule.domain.AppClock
import edu.jxslu.schedule.domain.BarcodeMatrix
import edu.jxslu.schedule.domain.EbikeAutoReturn
import edu.jxslu.schedule.domain.EbikeQr
import edu.jxslu.schedule.domain.EbikeFreeRide
import edu.jxslu.schedule.domain.EbikeRemoteRide
import edu.jxslu.schedule.domain.EbikeUseMode
import edu.jxslu.schedule.domain.ThemeMode
import edu.jxslu.schedule.ui.common.NoticeTone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

data class EbikeUiState(
    /**
     * 输入框里的车号原文（只允许数字进这个字段，UI 层过滤）。
     * 1~3 位 = 校园车队尾部；6~12 位 = 完整车号（地图选中的车是这一形态）。
     */
    val carInput: String = "",
    /**
     * 已生成的二维码**位阵**（null = 未生成）；与 [generatedBikeId] 成对。
     *
     * 存位阵而不是位图（iOS 移植 P4，2026-11-25）：`android.graphics.Bitmap` 进不了
     * `commonMain`，而屏幕上的码由 `ui/common/BarcodeMatrixImage` 用 Compose 画布直接画
     * （Android 与 iOS 逐像素同一份）。落相册那一份的渲染归平台（[EbikeQrGallery]）。
     */
    val generatedMatrix: BarcodeMatrix? = null,
    /** 已生成的完整车号（`100000669` 形态），存相册命名与提示用。 */
    val generatedBikeId: String? = null,
    /**
     * 当前这张码是否已进相册（自动保存或手动保存成功）。
     * 点「打开微信扫一扫」时据此决定要不要先补存一次——单机用户只能靠微信「相册」选图扫码，
     * 没存就跳过去等于让他白跑一趟。生成新车号时重置。
     */
    val generatedSaved: Boolean = false,
    /** 输入校验行内提示；null = 无。 */
    val inputError: String? = null,
)

/**
 * 页面级偏好快照（自动保存/扫完即焚开关、深浅色、最近车号）。
 *
 * 定位是「界面视图 + 兜底值」：它由 `stateIn` 缓存，在 DataStore 首次发射前是这里的
 * 默认值（冷启动首帧一定命中），**拿它做行为判定会在「进页即出码」这类首帧路径上读错**
 * ——自动保存被静默跳过、深色主题出一张白底码。需要真值的地方
 * （[EbikeViewModel.generate] / [EbikeViewModel.saveCurrent] / [EbikeViewModel.burnPending]）
 * 一律读 `DisplayPrefsStore` 的原始流，快照只在读失败时当兜底。
 */
data class EbikePrefsSnapshot(
    val autoSave: Boolean = false,
    val burnAfterScan: Boolean = true,
    val dark: Boolean = false,
    val recentIds: List<String> = emptyList(),
    /** 免费时长提醒开关（DESIGN §3.9）。 */
    val freeReminderEnabled: Boolean = false,
    /** 免费时长提前量（分钟）。 */
    val freeLeadMinutes: Int = EbikeFreeRide.DEFAULT_LEAD_MINUTES,
    /** 本次骑行计时起点（epoch 毫秒）；0 = 无进行中计时。 */
    val rideStartAt: Long = 0L,
    /**
     * 使用方式（DESIGN §3.9 / §4.32）：小程序方式 / 账号登录。页面按它隔离能力，
     * 默认 [EbikeUseMode.Default]（小程序方式，与存储默认一致）。
     */
    val useMode: EbikeUseMode = EbikeUseMode.Default,
    /**
     * 开锁前是否弹二次确认（DESIGN §3.9 / §4.32）。默认开；关掉后点「开锁」直接发指令。
     * 与其余偏好同一条纪律：**这是界面视图**，行为判定以 DataStore 原始流为准
     * （开锁动作由页面按本快照决定弹不弹窗，只在设置弹层刚改完的那一帧上会有滞后，
     * 而滞后的方向是会弹窗——保守那一侧）。
     */
    val unlockConfirm: Boolean = true,
    /** 用车满 15 分钟自动还车开关（DESIGN §3.9，默认关）。 */
    val autoReturnEnabled: Boolean = false,
    /** 自动还车的触发提前量（秒，10~60 步长 5，默认 30）。 */
    val autoReturnLeadSeconds: Int = EbikeAutoReturn.DEFAULT_LEAD_SECONDS,
    /** 自动还车的失败重试次数（1~3，默认 2）。 */
    val autoReturnRetries: Int = EbikeAutoReturn.DEFAULT_RETRIES,
    /** 地图底图选择（DESIGN §3.9，2026-11-17）：见 [DisplayPrefsStore.ebikeAmapChoice]。 */
    val amapChoice: String = AMAP_CHOICE_UNSET,
)

sealed interface EbikeEvent {
    /** 一次性结果提示（保存成功/失败等）；[tone] 决定提示语气。 */
    data class Notice(val text: String, val tone: NoticeTone) : EbikeEvent
}

/**
 * 共享单车出码（DESIGN §3.9 / §4.18）。
 *
 * 生成 = 拼 URL（[EbikeQr.bikeUrl] 校验，非法输入不出码）→ 码位阵（`EbikeQr.qrMatrix`，
 * 两端同一份，Android 走 zxing、iOS 走 CoreImage）；自动保存开关开着时，生成即落相册
 * （后台线程，结果经 [events] 提示）；扫完即焚开着时，保存成功记录待焚毁 key，删除时机见
 * [burnPending]（免费时长结束 / 手动结束骑行 / 无计时回 App 兜底）；最近车号历史随生成
 * 更新（DataStore，上限 8）。二维码内容不含个人信息，历史也不出本机。
 *
 * ## iOS 移植 P4（2026-11-25）改了什么
 *
 * 按 `design/ios-port.md` §11.2 把三件平台件收成构造参数：
 *
 * | 原来 | 现在 |
 * |---|---|
 * | `Context` + `Graph.appContext` | 全删：偏好由宿主传 [prefs]，宿主从 `Services.current` 取 |
 * | `EbikeQrBitmaps.saveToGallery` / `deletePending`（MediaStore） | [gallery]（null = 没有相册能力，入口不渲染） |
 * | `EbikeFreeRideReminder` / `EbikeAutoReturnReminder`（AlarmManager + 通知） | [reminders]（null = 没有提醒能力，设置整组不渲染） |
 * | `CodeBitmaps.render` → `Bitmap` | **删掉**：状态里存 `BarcodeMatrix`，屏幕绘制交给 `BarcodeMatrixImage` |
 * | `System.currentTimeMillis()` | `AppClock.nowMillis()` |
 * | `Dispatchers.IO` | `Dispatchers.Default`（commonMain 没有 IO） |
 * | `ViewModelProvider.Factory` | 删掉：宿主直接构造（§11.2 ①） |
 *
 * 行为口径没变：开关真值一律读 DataStore 原始流、生成即出码、焚毁时机与门禁全照旧。
 */
class EbikeViewModel(
    private val prefs: DisplayPrefsStore,
    /** 相册写入与「扫完即焚」的删除；**null = 本平台没有相册能力**（码照出，存相册入口不渲染）。 */
    private val gallery: EbikeQrGallery? = null,
    /** 免费时长提醒与自动还车；**null = 本平台没有提醒能力**（两组设置不渲染，计时照走）。 */
    private val reminders: EbikeReminderPort? = null,
) : ViewModel() {

    /**
     * 本平台有没有「存相册」这项能力（页面据此整组隐藏自动保存 / 存相册 / 扫完即焚入口）。
     *
     * 不用 `gallery != null` 直接判：页面拿到的是 ViewModel，不该再看见那条缝。
     */
    val hasGallery: Boolean get() = gallery != null

    /** 本平台有没有提醒能力（免费时长提醒 / 自动还车的设置组据此不渲染）。 */
    val hasReminders: Boolean get() = reminders != null

    /**
     * 本平台能不能跑到点执行代码（自动还车）。iOS 为 false：本地通知到点只弹一次、
     * 不执行代码，`BGTaskScheduler` 又不保证时刻（`design/ios-port.md` §12.13）。
     * 那一组设置整块不渲染，免得上一个拨了没反应的开关。
     */
    val hasAutoReturn: Boolean get() = reminders?.supportsAutoReturn == true

    /**
     * 本平台能不能删掉已存进相册的码（扫完即焚）。iOS 为 false：`UIImageWriteToSavedPhotosAlbum`
     * 不回头给句柄，删相册资产要 PhotoKit（仓里没有先例，见 `EbikeQrGallery.canDelete`）。
     * false 时那个开关不渲染，**也不记录待焚毁 key**。
     */
    val hasBurnAfterScan: Boolean get() = gallery?.canDelete == true

    private val _uiState = MutableStateFlow(EbikeUiState())
    val uiState: StateFlow<EbikeUiState> = _uiState.asStateFlow()

    private val _events = Channel<EbikeEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    /** 一次性提示的统一出口（失败路径给 [NoticeTone.Error]/[NoticeTone.Warning]）。 */
    private fun notice(text: String, tone: NoticeTone = NoticeTone.Info) {
        _events.trySend(EbikeEvent.Notice(text, tone))
    }

    /**
     * 使用方式的**首帧真值**（DESIGN §3.9 / §4.32）：页面按它决定露出哪一套能力
     * （小程序方式的「打开微信扫一扫」还是账号方式的「直接开锁」），首帧给错会让按钮
     * 先按另一档画一帧再翻过来——那是看得见的闪。
     *
     * 阻塞读一次，与 `MeViewModel.initialPrefs` / 设置页 `runBlocking { ... .first() }`
     * 同一模式：DataStore 读过一次后常驻内存，代价是一次内存读。
     * **它只用于首帧渲染**：行为判定一律读 DataStore 原始流（`BikeMapViewModel.accountMode`
     * 那一套），不读这个快照。
     */
    private val initialUseMode: EbikeUseMode = runBlocking {
        runCatching { prefs.ebikeUseMode.first() }.getOrDefault(EbikeUseMode.Default)
    }

    /** 骑行相关偏好（卡开关不归本页管，其余在本页用）。免费提醒相关流合进快照。 */
    val ebikePrefs: StateFlow<EbikePrefsSnapshot> = combine(
        prefs.ebikeAutoSave,
        prefs.ebikeBurnAfterScan,
        prefs.themeMode,
        prefs.ebikeRecentIds,
        prefs.ebikeFreeReminderEnabled,
        prefs.ebikeFreeLeadMinutes,
        prefs.ebikeRideStartAt,
        prefs.ebikeUseMode,
        prefs.ebikeUnlockConfirm,
        prefs.ebikeAutoReturnEnabled,
        prefs.ebikeAutoReturnLeadSeconds,
        prefs.ebikeAutoReturnRetries,
        prefs.ebikeAmapChoice,
    ) { array ->
        val autoSave = array[0] as Boolean
        val burnAfterScan = array[1] as Boolean
        val themeMode = array[2] as ThemeMode
        @Suppress("UNCHECKED_CAST")
        val recent = array[3] as List<String>
        val freeEnabled = array[4] as Boolean
        val freeLead = array[5] as Int
        val rideStartAt = array[6] as Long
        val useMode = array[7] as EbikeUseMode
        val unlockConfirm = array[8] as Boolean
        val autoReturnEnabled = array[9] as Boolean
        val autoReturnLeadSeconds = array[10] as Int
        val autoReturnRetries = array[11] as Int
        val amapChoice = array[12] as String
        EbikePrefsSnapshot(
            autoSave = autoSave,
            burnAfterScan = burnAfterScan,
            dark = themeMode == ThemeMode.Dark,
            recentIds = recent,
            freeReminderEnabled = freeEnabled,
            freeLeadMinutes = freeLead,
            rideStartAt = rideStartAt,
            useMode = useMode,
            unlockConfirm = unlockConfirm,
            autoReturnEnabled = autoReturnEnabled,
            autoReturnLeadSeconds = autoReturnLeadSeconds,
            autoReturnRetries = autoReturnRetries,
            amapChoice = amapChoice,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        EbikePrefsSnapshot(useMode = initialUseMode),
    )

    /**
     * 免费提醒设置变更（开关/提前量）→ 重算：补发该发的、重排闹钟、起停服务。
     *
     * [reminders] 为 null（本平台没有提醒能力）时整段跳过：设置组本来就不渲染，走不到这里。
     */
    fun onFreeReminderChanged() {
        val port = reminders ?: return
        viewModelScope.launch {
            emitReminderNotice(port.checkFreeRide())
        }
    }

    /**
     * 自动还车设置变更（开关 / 提前量 / 重试次数）→ 重算排程。
     * `catchUp = false`：设置变更**不追补**触发点已过的这一轮（详见
     * [EbikeReminderPort.reconcileAutoReturn]）——刚打开开关不该让 App 立刻还车。
     */
    fun onAutoReturnChanged() {
        val port = reminders ?: return
        viewModelScope.launch {
            port.reconcileAutoReturn(catchUp = false)
        }
    }

    /** 通知权限授予后的续跑：把进行中的计时的常驻倒计时与提醒补上（开关开着才做）。 */
    fun onReminderPermissionGranted() {
        checkFreeRide()
    }

    /**
     * 进页 / 回前台核对一次计时与提醒（幂等）：缺服务/闹钟就补上，过期状态就清干净。
     *
     * 搬迁前这一行直接调 `:app` 的 `EbikeFreeRideReminder.check(context)`；现在走 [reminders]。
     * 与 [onReminderPermissionGranted] 是同一件事，只是调用点不同（权限授予后 / 页面可见时）。
     */
    fun checkFreeRide() {
        val port = reminders ?: return
        viewModelScope.launch {
            emitReminderNotice(port.checkFreeRide())
        }
    }

    init {
        // 进页兜底：自动还车闹钟被 ROM 丢掉（或被后台限制清掉）时补跑一次；
        // 已执行过的这一轮由 doneKey 挡掉，幂等（同「周期核对」的兜底口径）
        reminders?.let { port ->
            viewModelScope.launch { port.reconcileAutoReturn() }
        }
    }

    /**
     * 点「打开微信扫一扫」：**先把码补存进相册**（除非已经存过），再记起点、起常驻倒计时、
     * 排两个精确提醒（换车再点 = 重新计时）。
     *
     * 补存是必须的：单机用户到微信只能走「扫一扫 → 相册」选图，没存就跳过去等于让他白跑。
     *
     * 起点 = 点击 + [EbikeFreeRide.SCAN_DELAY_MS]：切微信、选图、扫码、开车要时间，
     * 倒计时从点击 5 秒后才开始走——所以这条路**只在小程序方式出现**（`caps.wechatScan`），
     * 账号方式开锁成功走的 [startFreeRideNotice] 默认参数仍是当下。倒计时因此是预估的，
     * 展示处写明以小程序为准。
     */
    fun onWechatScanClicked() {
        viewModelScope.launch {
            // 远程用车计数（2026-10-24，见 EbikeRemoteRide）：跳转微信扫一扫扫的是 App 生成的
            // 车号码，算一次远程开启。达标**不阻断跳转**——落待提醒标记，回 App（ON_RESUME）
            // 时弹出；计数失败静默（提醒不该挡住用车）。
            runCatching {
                val count = prefs.incrementEbikeRemoteRideCount()
                if (EbikeRemoteRide.shouldRemind(count)) {
                    prefs.setEbikeRemoteRidePending(true)
                }
            }
            var saveText: String? = null
            var saveOk = true
            if (!_uiState.value.generatedSaved) {
                when (val saved = saveCurrentInternal()) {
                    is EbikeQrSaveResult.Saved ->
                        saveText = "已存入相册，在微信里点「相册」选图"
                    is EbikeQrSaveResult.Failed -> {
                        saveText = saved.message
                        saveOk = false
                    }
                    null -> Unit
                }
            }
            val (timerText, timerTone) = startFreeRideNotice(
                reminders,
                AppClock.nowMillis() + EbikeFreeRide.SCAN_DELAY_MS,
            )
            _events.send(
                EbikeEvent.Notice(
                    text = if (saveText == null) timerText else "$saveText；$timerText",
                    tone = if (saveOk) timerTone else NoticeTone.Warning,
                ),
            )
        }
    }

    /** 结束骑行：清起点、撤闹钟、停常驻倒计时、清通知栏上的提醒，并焚毁已保存的二维码。 */
    fun onEndRide() {
        viewModelScope.launch {
            val outcome = reminders?.endFreeRide() ?: EbikeReminderOutcome.Nothing
            // 本机计时都结束了，自动还车没有计时起点可依——撤掉（幂等）
            reminders?.cancelAutoReturn()
            burnPending(force = true)
            _events.send(
                when (outcome) {
                    is EbikeReminderOutcome.Failed ->
                        EbikeEvent.Notice("已结束骑行；提醒清理失败：${outcome.message}", NoticeTone.Warning)
                    else -> EbikeEvent.Notice("已结束骑行", NoticeTone.Info)
                },
            )
        }
    }

    /** 提醒类动作的结果 → 一次性提示；[EbikeReminderOutcome.Nothing] 不出声。 */
    private suspend fun emitReminderNotice(outcome: EbikeReminderOutcome) {
        val notice = when (outcome) {
            EbikeReminderOutcome.Started ->
                EbikeEvent.Notice("免费时长提醒已生效", NoticeTone.Success)
            EbikeReminderOutcome.StartedNoNotification ->
                EbikeEvent.Notice("通知被关闭，免费时长提醒发不出来", NoticeTone.Warning)
            EbikeReminderOutcome.StartedSilent ->
                EbikeEvent.Notice("免费时长提醒已关闭", NoticeTone.Info)
            EbikeReminderOutcome.Ended ->
                EbikeEvent.Notice("已清空免费时长提醒", NoticeTone.Info)
            is EbikeReminderOutcome.Failed ->
                EbikeEvent.Notice("提醒排程失败：${outcome.message}", NoticeTone.Warning)
            EbikeReminderOutcome.Nothing -> null
        }
        notice?.let { _events.send(it) }
    }

    /**
     * 输入车号：只留数字、限长，口径在 [EbikeQr.normalizeCarInput]（纯 JVM 可测）。
     */
    fun onCarInput(value: String) {
        val filtered = EbikeQr.normalizeCarInput(value)
        _uiState.update { it.copy(carInput = filtered, inputError = null) }
    }

    /**
     * 点击最近车号 chip：回填**并直接出码**。chip 的意图就是"再出一张上次那张码"，
     * 只回填不生成的话，回填又发生在页面下半部，用户看不见任何反馈。
     */
    fun onPickRecent(carNum: String) = fillAndGenerate(carNum)

    /**
     * 地图页选中的车（DESIGN §3.9）：回填完整车号并立即出码。
     * 车号来自运营方接口，仍走一遍 [EbikeQr.bikeUrl] 校验，脏数据不出一张扫不开的码。
     */
    fun onPickCarNum(carNum: String) = fillAndGenerate(carNum)

    /** 回填车号并立即出码（地图选车与最近 chip 共用；非法车号静默忽略）。 */
    private fun fillAndGenerate(carNum: String) {
        if (EbikeQr.bikeUrl(carNum) == null) return
        _uiState.update { it.copy(carInput = carNum, inputError = null) }
        generate()
    }

    /**
     * 生成二维码。非法车号只给行内提示，不发事件；合法则出码、
     * 视自动保存开关落相册、并写最近历史。
     */
    fun generate() {
        val carNum = EbikeQr.resolveCarNum(_uiState.value.carInput)
        val url = carNum?.let { EbikeQr.bikeUrl(it) }
        if (carNum == null || url == null) {
            _uiState.update {
                it.copy(inputError = EbikeQr.INPUT_HINT)
            }
            return
        }
        viewModelScope.launch {
            // 开关真值一律读 DataStore 原始流，**不要**读 [ebikePrefs] 的 stateIn 快照：
            // 快照在 DataStore 首次发射前是默认值（autoSave=false、dark=false），而
            // 「进页即出码」（今日页地图选车带 carNum 进页、进程被回收后恢复出码页）
            // 会在首帧就调到这里——那时快照还没发射，自动保存会被静默跳过（用户看不到
            // 码没进相册，也没有任何提示），深色主题还会出一张白底码。与 [burnPending]
            // 读原始流是同一个理由。读失败退回快照兜底：出码是主操作，不能被偏好存储牵连。
            val snapshot = ebikePrefs.value
            val autoSave = runCatching { prefs.ebikeAutoSave.first() }
                .getOrDefault(snapshot.autoSave)
            // 位阵生成（原先是 zxing → Bitmap；现在只到位阵，渲染成位图是落相册那一步的事）
            val matrix = withContext(Dispatchers.Default) { EbikeQr.qrMatrix(url) }
            _uiState.update {
                it.copy(generatedMatrix = matrix, generatedBikeId = carNum, generatedSaved = false)
            }
            // 没有相册能力的平台（gallery = null）：自动保存这个开关根本没有入口，别静默等
            if (autoSave && gallery != null) saveCurrent()
            viewModelScope.launch {
                prefs.updateEbikeRecentIds { EbikeQr.mergeRecent(it, carNum) }
            }
        }
    }

    /**
     * 手动把当前展示的码存相册（自动保存关闭时的兜底动作，也是「扫一扫」前的补存入口）。
     * 保存成功即标记 [EbikeUiState.generatedSaved]；扫完即焚开着时记录待焚毁 key。
     */
    fun saveCurrent() {
        viewModelScope.launch {
            when (val result = saveCurrentInternal()) {
                is EbikeQrSaveResult.Saved ->
                    _events.send(EbikeEvent.Notice("已保存到相册", NoticeTone.Success))
                is EbikeQrSaveResult.Failed ->
                    _events.send(EbikeEvent.Notice(result.message, NoticeTone.Error))
                null -> Unit
            }
        }
    }

    /**
     * 保存当前码；返回 null = 当前没有可保存的码（或本平台没有相册能力）。不发声，由调用方
     * 决定提示文案（[saveCurrent] 用「已保存到相册」，[onWechatScanClicked] 用「已存入相册…选图」）。
     */
    private suspend fun saveCurrentInternal(): EbikeQrSaveResult? {
        val current = gallery ?: return null
        val state = _uiState.value
        val matrix = state.generatedMatrix ?: return null
        val bikeId = state.generatedBikeId ?: return null
        // 深色主题出「深底白码」：与屏幕上那张一致（读取失败按浅色兜底，宁可多一张白底码）
        val dark = runCatching { prefs.themeMode.first() == ThemeMode.Dark }.getOrDefault(false)
        val result = current.save(matrix, dark, "ebike-$bikeId.jpg")
        if (result is EbikeQrSaveResult.Saved) {
            // 同 [generate]：开关真值读原始流。快照在冷启动首帧还是默认值（true），
            // 用户明明关掉了焚毁也会被记上待焚毁 key；读失败按「不记焚毁」兜底
            // （宁可在相册留一张码，也不做没把握的删除）。
            // 本平台删不掉（[EbikeQrGallery.canDelete] = false，iOS）就一条都不记：
            // 记了也删不掉，只会让每次核对拖着一串永远清不掉的目标空转。
            val burn = current.canDelete &&
                runCatching { prefs.ebikeBurnAfterScan.first() }.getOrDefault(false)
            if (burn) {
                prefs.updateEbikePendingDelete {
                    EbikeQr.mergePendingDelete(it, result.pendingKey)
                }
            }
            // 只在「保存的还是当前这张」时记已存：保存期间用户换了车号就不算
            _uiState.update { if (it.generatedBikeId == bikeId) it.copy(generatedSaved = true) else it }
        }
        return result
    }

    /**
     * 扫完即焚（DESIGN §3.9）：删除记录在案的待焚毁二维码——见 [burnPendingCodes]。
     * 触发时机：免费时长结束（`EbikeFreeRideReminder.check`）、手动结束骑行（[onEndRide]）、
     * 无计时在案时回 App（页面 ON_RESUME 兜底）。
     */
    fun burnPending(force: Boolean = false) {
        // 没有相册能力 / 删不掉（iOS）= 从来没有 key 被记上，不必绕一圈
        if (gallery?.canDelete != true) return
        viewModelScope.launch {
            val deleted = burnPendingCodes(prefs, gallery, force)
            if (deleted > 0) {
                _events.send(
                    EbikeEvent.Notice("已清除存入相册的二维码（扫完即焚）", NoticeTone.Success),
                )
            }
        }
    }

    /**
     * 离开页面时清掉位阵引用，别等 GC 兜底。
     *
     * 搬迁前这里还要 `Bitmap.recycle()`（那是一块原生内存）；现在状态里存的是普通
     * `BooleanArray` 位阵，丢掉引用即可。
     */
    override fun onCleared() {
        _uiState.update { it.copy(generatedMatrix = null) }
        super.onCleared()
    }
}

