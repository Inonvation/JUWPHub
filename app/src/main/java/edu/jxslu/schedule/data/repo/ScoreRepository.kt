package edu.jxslu.schedule.data.repo

import androidx.room.withTransaction
import edu.jxslu.schedule.data.local.JuwDatabase
import edu.jxslu.schedule.data.local.ScoreEntity
import edu.jxslu.schedule.domain.ScoreRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 成绩仓库（DESIGN §4.15）：按学期存储、按学期整体替换。
 *
 * 成绩是学生学业记录，不属于任何课表（timetableId），与 [ScheduleRepository] 分开。
 * 所有写路径走「先删该学期再插入」，重复导入不产生重复行；未涉及的学期不动。
 */
class ScoreRepository(private val db: JuwDatabase) {

    private val dao = db.scoreDao()

    /** 有成绩的学期（倒序，最新在前）。 */
    fun observeTerms(): Flow<List<String>> = dao.observeTerms()

    /** 「有没有成绩」的廉价判定（自动导入闸门用，见 `AutoSyncRules`）。 */
    suspend fun hasData(): Boolean = dao.count() > 0

    fun observeForTerm(term: String): Flow<List<ScoreRecord>> =
        dao.observeForTerm(term).map { list -> list.map { it.toDomain() } }

    /** 全部成绩按学期分组（成绩页两个分组视图共用）。 */
    fun observeAllGroupedByTerm(): Flow<Map<String, List<ScoreRecord>>> =
        dao.observeAll().map { list -> list.map { it.toDomain() }.groupBy { it.term } }

    /** 全量快照（变更检测在写库前比对用，DESIGN §4.33）。 */
    suspend fun getAll(): List<ScoreRecord> = dao.getAll().map { it.toDomain() }

    /**
     * 按学期批量替换，**整批一个事务**（2026-11-06 审计 J3）。
     *
     * 为什么不能让调用方自己逐学期各开一个事务：那样导 4 个学期时第 3 个失败，前 2 个已经
     * 提交、后 2 个没有——用户看到的是「新旧混排」的成绩列表，而失败提示无法说清哪几个学期
     * 已更新，重试也无从下手。整批一个事务 = 要么全换、要么一行都不动，失败重试一次即可，
     * 不留半套数据。
     *
     * 一次导入的所有行 `importedAt` 取同一个时刻：便于按导入批次判断新旧。
     * 空 [byTerm] 直接返回——不开一个什么都不做的事务。
     *
     * 这是成绩写入的**唯一**入口（单学期替换也走它）：留一个 `replaceTerm(term, rows)` 包装
     * 只会让人继续逐学期调用，把上面的半套数据问题带回来。
     */
    suspend fun replaceTerms(byTerm: List<Pair<String, List<ScoreRecord>>>) {
        if (byTerm.isEmpty()) return
        val now = System.currentTimeMillis()
        db.withTransaction {
            byTerm.forEach { (term, records) ->
                dao.deleteForTerm(term)
                if (records.isNotEmpty()) {
                    dao.insertAll(records.map { ScoreEntity.fromDomain(it.copy(term = term), now) })
                }
            }
        }
    }

    suspend fun deleteTerm(term: String) = dao.deleteForTerm(term)

    suspend fun deleteAll() = dao.deleteAll()
}
