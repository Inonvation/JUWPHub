package edu.jxslu.schedule.data.repo

import android.content.Context
import edu.jxslu.schedule.data.local.MirroredJsonStore
import edu.jxslu.schedule.domain.DormRepairSnapshot
import java.io.File
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json

/** 落盘文件名：基类构造参数里要用，所以放在文件级（不进 companion）。 */
private const val SNAPSHOT_FILE = "dorm_repair.json"
private const val MAX_DISMISSED = 50
private const val MAX_NOTIFIED = 50

/**
 * 这一份的 Json 配置**与其余缓存不同**：多一个 `encodeDefaults = true`（写全字段）。
 * 所以走基类的构造参数，不能吃共享默认值——换配置会让设备上已有的 `dorm_repair.json`
 * 按另一套键写回。
 */
private val DormRepairJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

/**
 * 宿舍报修本机快照（DESIGN §4.49）：`filesDir/dorm_repair.json` 一份 JSON。
 *
 * 不建 Room 表：这是「单条在途记录 + 最近 20 条历史」的只读镜像，没有查询/关联需求；
 * 与 `ExamSnapshotStore` / `InternshipSnapshotStore` 同一套选型。文件里只有宿舍号、
 * 故障类型、描述与时间，不存姓名/学号/手机号（解析阶段已丢）。
 *
 * 骨架（内存 StateFlow 是真值 + 整份落盘镜像 + 构造期同步读）已收进
 * `data/local/MirroredJsonStore`，本类只剩「与内存合并」的业务语义。
 * 落盘失败静默（合并前也没打日志），失败不回滚内存。
 */
class DormRepairStore(context: Context) : MirroredJsonStore<DormRepairSnapshot>(
    file = File(context.applicationContext.filesDir, SNAPSHOT_FILE),
    serializer = DormRepairSnapshot.serializer(),
    empty = DormRepairSnapshot(),
    json = DormRepairJson,
) {

    /** 界面订阅的真值（基类 [MirroredJsonStore.state] 的历史命名，调用方用的都是 `snapshot`）。 */
    val snapshot: StateFlow<DormRepairSnapshot> get() = state

    /**
     * 解析结果 + 最近 20 条记录；**UI 侧字段以当前内存为准合并**：
     * 网络请求在飞的时候用户可能刚点过「完成并隐藏」，直接用请求开始时的快照覆盖会把隐藏弄丢。
     * `tracking` 只增不减——用户用过报修一次，之后的 App 打开检查就长期保留（用户选择的能力）。
     */
    suspend fun save(fetched: DormRepairSnapshot) {
        mutate { current ->
            fetched.copy(
                dismissedIds = (current.dismissedIds + fetched.dismissedIds)
                    .distinct().takeLast(MAX_DISMISSED),
                notifiedIds = (current.notifiedIds + fetched.notifiedIds)
                    .distinct().takeLast(MAX_NOTIFIED),
                checkedOnce = current.checkedOnce || fetched.checkedOnce,
                tracking = current.tracking || fetched.tracking,
                lastAutoLoginAt = maxOf(current.lastAutoLoginAt, fetched.lastAutoLoginAt),
            )
        }
    }

    /** 用户打开过报修页：之后的 App 打开检查才可以真正发请求。 */
    suspend fun markTracking() = mutate { it.copy(tracking = true, checkedOnce = true) }

    /**
     * 记一次**自动** CAS 登录尝试的时刻。落盘后跨进程也守住 12 小时窗口；
     * 失败也算一次（下一次自动登录要等窗口过去），手动刷新不受影响。
     */
    suspend fun markAutoLoginAttempt(at: Long) = mutate { it.copy(lastAutoLoginAt = at) }

    /**
     * 退出学校统一认证时清空：换账号后不能把上一位用户的报修记录留在今日页。
     * 直接删文件；删不掉也把内存清空，至少本进程不再显示。
     */
    suspend fun clear() = clearStored()

    /** 详情页「完成并隐藏」：只影响本机卡片，不改学工状态。 */
    suspend fun dismiss(formUserId: Long) = mutate { current ->
        current.copy(
            dismissedIds = (current.dismissedIds + formUserId).distinct().takeLast(MAX_DISMISSED),
        )
    }
}
