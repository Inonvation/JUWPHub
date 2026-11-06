package edu.jxslu.schedule.data.local

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

/**
 * 「内存 StateFlow 是真值、磁盘只是镜像」的 store 基类（2026-11-06 审计 C2 第二轮）。
 *
 * 合并前 `AiApplyHistoryStore` / `QuietHoursQueue` / `DormRepairStore` 是逐行相同的骨架：
 * 一把 Mutex + 一个 `MutableStateFlow` + 每次写都是「在锁里读内存 → 变换 → 写内存 → 落盘整份」，
 * 连注释都是互相抄的。这里只留那份骨架，子类只剩自己的业务变换与纯逻辑。
 *
 * ### 为什么内存 StateFlow 是真值
 * 这三份数据的形状都是「界面订阅一张小表、用户点一下改一条」：读多写少、总量有硬上限
 * （留痕 50 条 / 免扰队列 30 条 / 报修 20 条）。真值放内存里，界面拿到的永远是刚写完的那一份，
 * 不必等一次磁盘 IO；磁盘只在进程重启时用来把内存恢复成上次的样子。
 * 反过来（每次读都读盘）会让 Compose 的每一帧都碰文件系统，也让「写完立刻读」在慢盘上读到旧值。
 *
 * ### 为什么读必须同步（`init` 里就 `readBlocking()`）
 * 初值要在**第一次组合之前**就位。改成异步读（`suspend` + `launch`）等于让首帧先渲染空表、
 * 再跳成真值——留痕列表、免扰队列、报修卡都会闪一下；`TodayScreen` 那种
 * `remember { store }.snapshot.collectAsStateWithLifecycle()` 更是直接拿构造后的初值。
 * 文件都在几十 KB 以内，构造期一次同步读的代价可以忽略（且本来就在主线程构造一次）。
 *
 * ### 为什么写盘失败不能让调用方看到异常
 * 写内存一定成功（纯赋值），失败只可能发生在磁盘上；而这时**内存已经是新的**、界面也已经
 * 按新值渲染了。再抛出去，调用方（很多在落库事务之后）会把整次操作判成失败，用户重试一遍 →
 * 业务被做两次（历史坑：AI 调课的日期覆盖重复插入，见 `AiApplyHistoryStore.append` 的注释）。
 * 所以写盘失败只留痕（[JsonFileStore] 的 `tag`）、**不回滚内存**、不抛。
 *
 * 子类不要自己再收一把锁、也不要自己再读写文件：所有落盘都经过 [JsonFileStore]（原子替换 +
 * 坏文件当空），所有内存变更都经过 [mutate]。
 */
abstract class MirroredJsonStore<T>(
    file: File,
    serializer: KSerializer<T>,
    /** 读不到/文件坏了时的真值，也是 [clearStored] 之后回到的状态。 */
    private val empty: T,
    json: Json = JsonFileStore.DefaultJson,
    tag: String? = null,
) {

    private val store = JsonFileStore(file, serializer, json, tag)

    /** 写入串行化：写入口来自多个窗口 / Worker / Receiver 上下文，并发写不能互相覆盖。 */
    private val writeLock = Mutex()

    /** 构造期同步读一次，首帧即真值（理由见类注释）。 */
    private val _state = MutableStateFlow(store.readBlocking() ?: empty)

    /** 当前真值的只读视图（订阅用）。 */
    val state: StateFlow<T> = _state.asStateFlow()

    /**
     * 读内存 → 变换 → 写内存 → 落盘整份，全程在锁里。
     *
     * **先更新内存再写盘**：写盘失败本来就不回滚（见类注释），顺序反过来只会多出一个
     * 「磁盘写完了、界面还没刷新」的窗口，没有好处。
     *
     * [transform] 在调用方线程同步执行（不进 IO），所以闭包里改局部变量（比如统计移除条数）
     * 是安全的。
     */
    protected suspend fun mutate(transform: (T) -> T) {
        writeLock.withLock {
            val next = transform(_state.value)
            _state.value = next
            withContext(Dispatchers.IO) { store.writeBlocking(next) }
        }
    }

    /** 删文件 + 内存回到 [empty]（「换账号」这类要连磁盘一起清的动作）。 */
    protected suspend fun clearStored() {
        writeLock.withLock {
            withContext(Dispatchers.IO) { store.delete() }
            _state.value = empty
        }
    }
}
