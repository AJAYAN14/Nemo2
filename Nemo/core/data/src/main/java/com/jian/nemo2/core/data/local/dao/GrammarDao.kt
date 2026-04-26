package com.jian.nemo2.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.jian.nemo2.core.data.local.entity.GrammarEntity
import com.jian.nemo2.core.data.local.entity.relations.GrammarWithUsages
import kotlinx.coroutines.flow.Flow

/**
 * 语法数据访问对象
 */
@Dao
interface GrammarDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(grammars: List<GrammarEntity>)

    /**
     * 获取语法总数
     */
    @Query("SELECT COUNT(*) FROM grammars")
    suspend fun getCount(): Int

    /**
     * 插入单条语法
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(grammar: GrammarEntity)

    @Update
    suspend fun update(grammar: GrammarEntity)

    @Query("SELECT id FROM grammars WHERE id IN (:ids)")
    suspend fun getIdsIn(ids: List<Long>): List<Long>

    /**
     * 批量插入或更新 (用于同步)
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(grammars: List<GrammarEntity>)

    /**
     * 逻辑删除语法
     */
    @Query("""
        UPDATE user_progress SET
        state = -1,
        updated_at = :updatedTime
        WHERE item_id IN (:ids) AND item_type = 'grammar'
    """)
    suspend fun softDeleteByIds(ids: List<Long>, updatedTime: String)

    @Query("DELETE FROM grammars WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    /**
     * 获取所有语法 (包含已逻辑删除的)
     */
    /**
     * 获取所有语法 (包含已逻辑删除的)
     */
    @Query("SELECT * FROM grammars")
    suspend fun getAllGrammarsWithDeletedSync(): List<GrammarEntity>

    /**
     * 获取自指定时间以来修改过的语法 (用于增量同步)
     */
    @Query("""
        SELECT g.* FROM grammars g
        JOIN user_progress s ON g.id = s.item_id AND s.item_type = 'grammar'
        WHERE s.updated_at > :sinceTime
    """)
    suspend fun getModifiedSince(sinceTime: String): List<GrammarEntity>

    @Query("""
        SELECT COUNT(*) FROM user_progress
        WHERE updated_at > :timestamp AND item_type = 'grammar'
    """)
    suspend fun countModifiedSince(timestamp: String): Int

    @Query("""
        SELECT g.* FROM grammars g
        LEFT JOIN user_progress s ON g.id = s.item_id AND s.item_type = 'grammar'
        WHERE g.id = :id
        AND g.is_delisted = 0
    """)
    fun getById(id: Long): Flow<GrammarEntity?>

    /**
     * 获取语法（含用法和例句）
     */
    @Transaction
    @Query("""
        SELECT g.* FROM grammars g
        LEFT JOIN user_progress s ON g.id = s.item_id AND s.item_type = 'grammar'
        WHERE g.id = :id
        AND (s.state != -1 OR s.state IS NULL)
        AND g.is_delisted = 0
    """)
    fun getGrammarWithUsages(id: Long): Flow<GrammarWithUsages?>

    /**
     * 获取所有语法（含用法和例句）
     */
    @Transaction
    @Query("""
        SELECT g.* FROM grammars g
        LEFT JOIN user_progress s ON g.id = s.item_id AND s.item_type = 'grammar'
        WHERE (s.state != -1 OR s.state IS NULL)
        AND g.is_delisted = 0
    """)
    fun getAllGrammarsWithUsages(): Flow<List<GrammarWithUsages>>

    /**
     * 根据等级获取语法（含用法和例句）
     * 忽略大小写匹配
     */
    @Transaction
    @Query("""
        SELECT g.* FROM grammars g
        LEFT JOIN user_progress s ON g.id = s.item_id AND s.item_type = 'grammar'
        WHERE UPPER(g.grammar_level) IN (:levels)
        AND (s.state != -1 OR s.state IS NULL)
        AND g.is_delisted = 0
    """)
    fun getGrammarsByLevelsWithUsages(levels: List<String>): Flow<List<GrammarWithUsages>>

    @Transaction
    @Query("""
        SELECT g.* FROM grammars g
        LEFT JOIN user_progress s ON g.id = s.item_id AND s.item_type = 'grammar'
        WHERE UPPER(g.grammar_level) = UPPER(:level)
        AND (s.reps IS NULL OR s.reps = 0)
        AND (s.state != -1 OR s.state IS NULL)
        AND g.is_delisted = 0
        ORDER BY g.id ASC
    """)
    fun getNewGrammarsByLevelWithUsages(level: String): Flow<List<GrammarWithUsages>>

    /**
     * 获取新语法（随机排序，含用法）
     */
    @Transaction
    @Query("""
        SELECT g.* FROM grammars g
        LEFT JOIN user_progress s ON g.id = s.item_id AND s.item_type = 'grammar'
        WHERE UPPER(g.grammar_level) = UPPER(:level)
        AND (s.reps IS NULL OR s.reps = 0)
        AND (s.state != -1 OR s.state IS NULL)
        AND g.is_delisted = 0
        ORDER BY RANDOM()
    """)
    fun getNewGrammarsByLevelWithUsagesRandom(level: String): Flow<List<GrammarWithUsages>>

    @Transaction
    @Query("""
        SELECT g.* FROM grammars g
        JOIN user_progress s ON g.id = s.item_id AND s.item_type = 'grammar'
        WHERE s.next_review <= :currentDate
        AND s.reps > 0
        AND s.state != -1
        AND g.is_delisted = 0
        ORDER BY s.next_review ASC
    """)
    fun getDueGrammarsWithUsages(currentDate: String): Flow<List<GrammarWithUsages>>

    @Transaction
    @Query("""
        SELECT g.* FROM grammars g
        JOIN user_progress s ON g.id = s.item_id AND s.item_type = 'grammar'
        WHERE s.updated_at >= :todayISO
        AND (s.state != 0 OR s.buried_until > :currentEpochDay)
        AND g.is_delisted = 0
        ORDER BY g.id DESC
    """)
    fun getTodayLearnedGrammarsWithUsages(todayISO: String, currentEpochDay: Long): Flow<List<GrammarWithUsages>>

    @Transaction
    @Query("""
        SELECT g.* FROM grammars g
        JOIN user_progress s ON g.id = s.item_id AND s.item_type = 'grammar'
        WHERE s.last_review >= :todayISO
        AND s.reps > 0
        AND s.created_at < :todayISO
        AND g.is_delisted = 0
        ORDER BY g.id DESC
    """)
    fun getTodayReviewedGrammarsWithUsages(todayISO: String): Flow<List<GrammarWithUsages>>

    @Transaction
    @Query("""
        SELECT g.* FROM grammars g
        JOIN user_progress s ON g.id = s.item_id AND s.item_type = 'grammar'
        WHERE s.is_favorite = 1 AND s.state != -1 AND g.is_delisted = 0 ORDER BY g.id DESC
    """)
    fun getFavoriteGrammarsWithUsages(): Flow<List<GrammarWithUsages>>

    /**
     * 获取跳过的语法（含用法和例句）
     */
    @Transaction
    @Query("""
        SELECT g.* FROM grammars g
        JOIN user_progress s ON g.id = s.item_id AND s.item_type = 'grammar'
        WHERE s.state = -1 AND g.is_delisted = 0
        ORDER BY g.id DESC LIMIT :limit
    """)
    fun getSkippedGrammarsWithUsages(limit: Int): Flow<List<GrammarWithUsages>>

    @Transaction
    @Query("""
        SELECT g.* FROM grammars g
        JOIN user_progress s ON g.id = s.item_id AND s.item_type = 'grammar'
        WHERE s.reps > 0
        AND g.is_delisted = 0
        ORDER BY g.id DESC
    """)
    fun getAllLearnedGrammarsWithUsages(): Flow<List<GrammarWithUsages>>

    @Query("""
        SELECT COUNT(*) FROM user_progress s
        JOIN grammars g ON s.item_id = g.id AND s.item_type = 'grammar'
        WHERE s.reps > 0
        AND g.is_delisted = 0
    """)
    fun getLearnedGrammarCount(): Flow<Int>

    /**
     * 获取指定等级的已学习语法（含用法和例句）
     */
    @Transaction
    @Query("""
        SELECT g.* FROM grammars g
        JOIN user_progress s ON g.id = s.item_id AND s.item_type = 'grammar'
        WHERE s.reps > 0
        AND UPPER(g.grammar_level) = UPPER(:level)
        AND g.is_delisted = 0
        ORDER BY g.id DESC
    """)
    fun getLearnedGrammarsByLevelWithUsages(level: String): Flow<List<GrammarWithUsages>>

    /**
     * 根据ID批量获取语法（含用法和例句）
     */
    @Transaction
    @Query("SELECT * FROM grammars WHERE id IN (:ids)")
    suspend fun getGrammarsByIdsWithUsages(ids: List<Long>): List<GrammarWithUsages>

    /**
     * 搜索语法（含用法和例句）
     */
    @Transaction
    @Query("""
        SELECT g.* FROM grammars g
        LEFT JOIN user_progress s ON g.id = s.item_id AND s.item_type = 'grammar'
        WHERE g.grammar LIKE '%' || :query || '%'
        AND g.is_delisted = 0
        ORDER BY g.id ASC
    """)
    fun searchGrammarsWithUsages(query: String): Flow<List<GrammarWithUsages>>

    /**
     * 获取所有语法 (同步) - 用于导出
     */


    /**
     * 获取所有语法 (Cursor) - 用于流式导出
     * 优化：仅导出有进度或状态的语法
     */
    @Query("""
        SELECT
            g.id, g.grammar, g.grammar_level,
            s.reps AS reps,
            s.stability AS stability,
            s.difficulty AS difficulty,
            s.scheduled_days AS interval,
            s.next_review AS nextReview,
            s.is_favorite AS isFavorite,
            s.state AS state,
            s.updated_at AS updatedAt,
            s.last_review AS lastReview,
            s.created_at AS createdAt
        FROM grammars g
        JOIN user_progress s ON g.id = s.item_id AND s.item_type = 'grammar'
        WHERE s.reps > 0
        OR s.is_favorite = 1
        OR s.state = -1
        OR s.created_at IS NOT NULL
    """)
    fun getExportGrammarsCursor(): android.database.Cursor

    @Query("SELECT * FROM grammars")
    suspend fun getAllGrammarsSync(): List<GrammarEntity>

    /**
     * 获取所有语法
     */
    @Query("""
        SELECT g.* FROM grammars g
        LEFT JOIN user_progress s ON g.id = s.item_id AND s.item_type = 'grammar'
        WHERE g.is_delisted = 0
    """)
    fun getAllGrammars(): Flow<List<GrammarEntity>>

    /**
     * 获取所有已学习的语法 (不包含跳过的)
     */
    @Query("""
        SELECT g.* FROM grammars g
        JOIN user_progress s ON g.id = s.item_id AND s.item_type = 'grammar'
        WHERE s.reps > 0
        AND g.is_delisted = 0
        ORDER BY g.id DESC
    """)
    fun getAllLearnedGrammars(): Flow<List<GrammarEntity>>

    /**
     * 根据等级列表获取语法
     * 数据库中grammar_level存储为大写（N1, N2, N3, N4, N5）
     */
    /**
     * 根据等级列表获取语法
     * 数据库中grammar_level存储为大写（N1, N2, N3, N4, N5）
     * 忽略大小写
     */
    @Query("""
        SELECT g.* FROM grammars g
        LEFT JOIN user_progress s ON g.id = s.item_id AND s.item_type = 'grammar'
        WHERE UPPER(g.grammar_level) IN (:levels)
        AND g.is_delisted = 0
    """)
    fun getAllGrammarsByLevels(levels: List<String>): Flow<List<GrammarEntity>>

    /**
     * 获取新语法（未学习且未跳过）
     */
    @Query("""
        SELECT g.* FROM grammars g
        LEFT JOIN user_progress s ON g.id = s.item_id AND s.item_type = 'grammar'
        WHERE UPPER(g.grammar_level) = UPPER(:level)
        AND (s.reps IS NULL OR s.reps = 0)
        AND (s.state != -1 OR s.state IS NULL)
        AND g.is_delisted = 0
        ORDER BY g.id ASC
    """)
    fun getNewGrammarsByLevel(level: String): Flow<List<GrammarEntity>>

    /**
     * 获取到期复习语法
     */
    @Query("""
        SELECT g.* FROM grammars g
        JOIN user_progress s ON g.id = s.item_id AND s.item_type = 'grammar'
        WHERE s.next_review <= :currentDate
        AND (:level = 'ALL' OR UPPER(g.grammar_level) = UPPER(:level))
        AND s.state IN (1, 2, 3)
        AND s.buried_until <= :currentEpochDay
        AND g.is_delisted = 0
        ORDER BY s.state ASC, s.next_review ASC
    """)
    fun getDueGrammarsByLevel(currentDate: String, level: String, currentEpochDay: Long): Flow<List<GrammarEntity>>

    @Query("""
        SELECT g.* FROM grammars g
        JOIN user_progress s ON g.id = s.item_id AND s.item_type = 'grammar'
        WHERE s.next_review <= :currentDate
        AND s.reps > 0
        AND s.state != -1
        AND g.is_delisted = 0
        ORDER BY s.next_review ASC
    """)
    fun getDueGrammars(currentDate: String): Flow<List<GrammarEntity>>

    @Query("""
        SELECT COUNT(*) FROM grammars g
        JOIN user_progress s ON g.id = s.item_id AND s.item_type = 'grammar'
        WHERE s.next_review <= :currentDate
        AND s.reps > 0
        AND s.state != -1
        AND s.buried_until <= :currentEpochDay
        AND g.is_delisted = 0
    """)
    fun getDueGrammarsCount(currentDate: String, currentEpochDay: Long): Flow<Int>

    /**
     * 获取今日学习的语法
     */
    @Query("""
        SELECT g.* FROM grammars g
        JOIN user_progress s ON g.id = s.item_id AND s.item_type = 'grammar'
        WHERE s.updated_at >= :todayISO
        AND (s.state != 0 OR s.buried_until > :currentEpochDay)
        ORDER BY g.id DESC
    """)
    fun getTodayLearnedGrammars(todayISO: String, currentEpochDay: Long): Flow<List<GrammarEntity>>


    @Query("""
        SELECT g.* FROM grammars g
        JOIN user_progress s ON g.id = s.item_id AND s.item_type = 'grammar'
        WHERE s.is_favorite = 1 AND s.state != -1 AND g.is_delisted = 0 ORDER BY g.id DESC
    """)
    fun getFavoriteGrammars(): Flow<List<GrammarEntity>>

    @Query("UPDATE user_progress SET is_favorite = :isFavorite, updated_at = :updatedAt WHERE item_id = :grammarId AND item_type = 'grammar'")
    suspend fun updateFavoriteStatus(grammarId: Long, isFavorite: Boolean, updatedAt: String)

    @Query("""
        SELECT g.* FROM grammars g
        JOIN user_progress s ON g.id = s.item_id AND s.item_type = 'grammar'
        WHERE s.state = -1 AND g.is_delisted = 0 ORDER BY g.id DESC LIMIT :limit
    """)
    fun getSkippedGrammars(limit: Int): Flow<List<GrammarEntity>>

    @Query("""
        SELECT COUNT(*) FROM user_progress s
        JOIN grammars g ON s.item_id = g.id AND s.item_type = 'grammar'
        WHERE s.state = -1 AND g.is_delisted = 0
    """)
    fun getSkippedGrammarsCount(): Flow<Int>

    @Query("SELECT * FROM grammars WHERE id IN (:ids)")
    suspend fun getGrammarsByIds(ids: List<Long>): List<GrammarEntity>

    /**
     * 搜索语法 (匹配标题)
     * 注意：explanation 字段已迁移到 grammar_usages 表
     */
    @Query("""
        SELECT g.* FROM grammars g
        LEFT JOIN user_progress s ON g.id = s.item_id AND s.item_type = 'grammar'
        WHERE g.grammar LIKE '%' || :query || '%'
        AND g.is_delisted = 0
        ORDER BY g.id ASC
    """)
    fun searchGrammars(query: String): Flow<List<GrammarEntity>>

    @Query("DELETE FROM user_progress WHERE item_type = 'grammar'")
    suspend fun resetAllProgress()

    /**
     * 清空所有收藏
     */
    @Query("UPDATE user_progress SET is_favorite = 0 WHERE is_favorite = 1 AND item_type = 'grammar'")
    suspend fun clearAllFavorites()

    /**
     * 获取复习预测
     */
    @Query("""
        SELECT SUBSTR(s.next_review, 1, 10) AS date, COUNT(*) AS count
        FROM user_progress s
        WHERE s.next_review BETWEEN :startDate AND :endDate
        AND s.state != -1 AND s.item_type = 'grammar'
        GROUP BY SUBSTR(s.next_review, 1, 10)
    """)
    fun getReviewForecast(startDate: String, endDate: String): Flow<List<GrammarReviewForecastTuple>>

    // ========== 等级查询 ==========

    @Query("""
        SELECT DISTINCT w.grammar_level
        FROM user_progress s
        JOIN grammars w ON s.item_id = w.id AND s.item_type = 'grammar'
        WHERE s.last_review >= :todayISO
    """)
    fun getTodayReviewedGrammarLevels(todayISO: String): Flow<List<String>>

    @Query("""
        SELECT DISTINCT w.grammar_level
        FROM user_progress s
        JOIN grammars w ON s.item_id = w.id AND s.item_type = 'grammar'
        WHERE s.updated_at >= :todayISO
        AND (s.state != 0 OR s.buried_until > :currentEpochDay)
    """)
    fun getTodayLearnedGrammarLevels(todayISO: String, currentEpochDay: Long): Flow<List<String>>

    @Query("""
        SELECT DISTINCT w.grammar_level
        FROM user_progress s
        JOIN grammars w ON s.item_id = w.id AND s.item_type = 'grammar'
        WHERE s.is_favorite = 1
    """)
    fun getFavoriteGrammarLevels(): Flow<List<String>>

    @Query("""
        SELECT DISTINCT w.grammar_level
        FROM user_progress s
        JOIN grammars w ON s.item_id = w.id AND s.item_type = 'grammar'
        WHERE s.reps > 0
    """)
    fun getLearnedGrammarLevels(): Flow<List<String>>

    @Query("SELECT DISTINCT T2.grammar_level FROM grammar_wrong_answers T1 JOIN grammars T2 ON T1.grammar_id = T2.id")
    fun getWrongAnswerGrammarLevels(): Flow<List<String>>

    /**
     * 获取语法总数
     */
    @Query("SELECT COUNT(*) FROM grammars WHERE is_delisted = 0")
    suspend fun getGrammarCount(): Int

    /**
     * 获取语法等级数（用于判断数据是否完整）
     */
    @Query("SELECT COUNT(DISTINCT grammar_level) FROM grammars WHERE is_delisted = 0")
    suspend fun getGrammarLevelCount(): Int

    // ========== 数据修复 ==========

    /**
     * 获取去重后的保留ID列表 (每个语法只保留ID最小的一个)
     */
    @Query("SELECT MIN(id) FROM grammars GROUP BY grammar, grammar_level")
    suspend fun getDuplicateKeepIds(): List<Long>

    /**
     * 将指定等级下，不在给定 ID 列表中的语法标记为已下架
     */
    @Query("UPDATE grammars SET is_delisted = 1 WHERE UPPER(grammar_level) = UPPER(:level) AND id NOT IN (:jsonIds)")
    suspend fun markMissingAsDelistedById(level: String, jsonIds: List<Long>): Int
}

data class GrammarReviewForecastTuple(
    val date: String,
    val count: Int
)
