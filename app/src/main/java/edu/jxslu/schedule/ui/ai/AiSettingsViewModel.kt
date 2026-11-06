package edu.jxslu.schedule.ui.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import edu.jxslu.schedule.data.ai.AiCredentialStore
import edu.jxslu.schedule.data.ai.AiScheduleClient
import edu.jxslu.schedule.data.repo.ScheduleRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/**
 * AI 服务配置页（DESIGN §4.39）的状态容器。
 *
 * 地址 / 模型 / 脱敏走 DataStore（经 Repository 单流）；**API Key 走
 * [AiCredentialStore] 加密存储**，不进 DataStore。
 *
 * **首帧真值必须同步拿到**（同 MeViewModel 的口径）：这 6 个值直接决定开关与选中项怎么画，
 * 用默认值先渲染一次再跳过去，用户看到的是「开关自己动了一下」——这是明确要避免的。
 * 所以构造期读一次 [AiSettingsInitial] 快照。
 *
 * 2026-11-06 审计 2-A：原先 6 个 `stateIn(...)` 各自 `runBlocking { first() }`，等于 6 段
 * 主线程阻塞；现在收成**一段**（[initial]）。DataStore 的首次读盘只发生一次（之后走内存），
 * 所以真正的收益是「6 次阻塞段 → 1 次」而不是「少读 5 次盘」。
 * 不改的选项：把 `initialValue` 换成硬编码默认值 + 首帧后刷新 —— 那就是上面说的闪一下，不做。
 */
class AiSettingsViewModel(
    private val repo: ScheduleRepository,
    private val credentialStore: AiCredentialStore,
    private val client: AiScheduleClient,
) : ViewModel() {

    /** 构造期唯一一次阻塞读（见类注释）；后面 6 条流都以它为 `initialValue`。 */
    private val initial: AiSettingsInitial = runBlocking { AiSettingsInitial.read(repo) }

    val baseUrl: StateFlow<String> = repo.aiBaseUrl.stateIn(
        viewModelScope, SharingStarted.Eagerly, initial.baseUrl,
    )

    val model: StateFlow<String> = repo.aiModel.stateIn(
        viewModelScope, SharingStarted.Eagerly, initial.model,
    )

    val maskTeacher: StateFlow<Boolean> = repo.aiMaskTeacher.stateIn(
        viewModelScope, SharingStarted.Eagerly, initial.maskTeacher,
    )

    /** 思考档位（none / low / high / max）。 */
    val thinkingEffort: StateFlow<String> = repo.aiThinkingEffort.stateIn(
        viewModelScope, SharingStarted.Eagerly, initial.thinkingEffort,
    )

    /** 是否在 AI 调课页显示思考过程。 */
    val showReasoning: StateFlow<Boolean> = repo.aiShowReasoning.stateIn(
        viewModelScope, SharingStarted.Eagerly, initial.showReasoning,
    )

    /**
     * 该模型是否支持图片识别（DESIGN §4.39），默认关。
     *
     * 探测不了（模型名用户手填、上游 `/models` 不给能力），只能显式声明。
     * [AiScheduleConfig.looksVisionModel] 只用于在配置页提示「看着像视觉模型」。
     */
    val supportsImage: StateFlow<Boolean> = repo.aiSupportsImage.stateIn(
        viewModelScope, SharingStarted.Eagerly, initial.supportsImage,
    )

    /**
     * API Key（**构造期同步读**，2026-11-06 审计复核后保留）。
     *
     * 它决定「API Key」那一行的副标题是「已配置」还是「未配置」——异步读会让它先显示
     * 「未配置」再跳成「已配置」，这是本页最容易被当成 bug 的一处闪烁。
     * 成本可控：`AiCredentialStore.prefs` 是 `lazy`（Keystore 只在首次解密时初始化），
     * `Graph.aiCredentialStore` 又是进程单例 → 整个进程只付一次。
     * **不能改成 `LaunchedEffect` / `viewModelScope.launch`**：见上，首帧就要真值。
     */
    private val _apiKey = MutableStateFlow(credentialStore.readApiKey())
    val apiKey: StateFlow<String?> = _apiKey.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    fun setBaseUrl(value: String) {
        viewModelScope.launch { repo.setAiBaseUrl(value) }
    }

    fun setModel(value: String) {
        viewModelScope.launch { repo.setAiModel(value) }
    }

    fun setMaskTeacher(value: Boolean) {
        viewModelScope.launch { repo.setAiMaskTeacher(value) }
    }

    fun setThinkingEffort(value: String) {
        viewModelScope.launch { repo.setAiThinkingEffort(value) }
    }

    fun setShowReasoning(value: Boolean) {
        viewModelScope.launch { repo.setAiShowReasoning(value) }
    }

    fun setSupportsImage(value: Boolean) {
        viewModelScope.launch { repo.setAiSupportsImage(value) }
    }

    fun saveApiKey(value: String) {
        credentialStore.saveApiKey(value)
        _apiKey.value = credentialStore.readApiKey()
    }

    fun clearApiKey() {
        credentialStore.clear()
        _apiKey.value = null
    }

    /**
     * 探测服务：拉一次 `/models`——既验证地址与 Key 是否可用，也顺便给出模型可选项。
     * 失败不重试，错误分类交给 [aiErrorText]。
     */
    suspend fun probe(): Result<List<String>> {
        val url = baseUrl.value.trim()
        val key = _apiKey.value?.trim().orEmpty()
        if (url.isBlank()) return Result.failure(IllegalStateException("请先填写服务地址"))
        if (key.isBlank()) return Result.failure(IllegalStateException("请先填写 API Key"))
        _busy.value = true
        return try {
            Result.success(withContext(Dispatchers.IO) { client.listModels(url, key) })
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            Result.failure(t)
        } finally {
            _busy.value = false
        }
    }

    class Factory(
        private val repo: ScheduleRepository,
        private val credentialStore: AiCredentialStore,
        private val client: AiScheduleClient,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            AiSettingsViewModel(repo, credentialStore, client) as T
    }
}

/**
 * 配置页 6 个偏好的首帧快照（2026-11-06 审计 2-A）。
 *
 * 存在的唯一目的：把 6 段 `runBlocking { first() }` 收成 1 段（见 [AiSettingsViewModel] 类注释）。
 * 读顺序无关紧要——6 条流背后是同一个 DataStore，首次读盘只发生一次，其余命中内存。
 * 放在 ViewModel 文件里而不是 Repository：这是**这一个页面**的首帧需求，
 * 不属于数据层契约（`data/repo` 也不该为一个 UI 的构造期阻塞新增 API）。
 */
private data class AiSettingsInitial(
    val baseUrl: String,
    val model: String,
    val maskTeacher: Boolean,
    val thinkingEffort: String,
    val showReasoning: Boolean,
    val supportsImage: Boolean,
) {
    companion object {
        /** 只允许在构造期调用一次（调用方用 `runBlocking` 包着）。 */
        suspend fun read(repo: ScheduleRepository): AiSettingsInitial = AiSettingsInitial(
            baseUrl = repo.aiBaseUrl.first(),
            model = repo.aiModel.first(),
            maskTeacher = repo.aiMaskTeacher.first(),
            thinkingEffort = repo.aiThinkingEffort.first(),
            showReasoning = repo.aiShowReasoning.first(),
            supportsImage = repo.aiSupportsImage.first(),
        )
    }
}
