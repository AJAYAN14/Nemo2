package com.jian.nemo2.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.jian.nemo2.core.data.local.entity.WordEntity
import kotlinx.coroutines.flow.Flow

/**
 * 单词数据访问对象
 */
@Dao
interface WordDao {

    // ========== 基础CRUD ==========

    /**
     * 批量插入单词
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(words: List<WordEntity>)

    /**
     * 获取单词总数
     */
    @Query("SELECT COUNT(*) FROM words")
    suspend fun getCount(): Int

    /**
     * 插入单条单词
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(word: WordEntity)

    /**
     * 更新单词
     */
    @Update
    suspend fun update(word: WordEntity)

    /**
     * 批量更新单词 (用于同步更新释义等)
     */
    @Update
    suspend fun updateAll(words: List<WordEntity>)

    /**
     * 批量获取存在的ID (用于区分 Update 和 Insert)
     */
    @Query("SELECT id FROM words WHERE id IN (:ids)")
    suspend fun getIdsIn(ids: List<Long>): List<Long>

    /**
     * 按等级+日语匹配单词（用于云更新合并）
     */
    @Query("SELECT * FROM words WHERE level = :level AND japanese = :japanese LIMIT 1")
    suspend fun getWordByLevelAndJapanese(level: String, japanese: String): WordEntity?

    /**
     * 批量插入或更新 (用于同步 - 全量覆盖场景慎用)
     * 注意：对于增量同步，建议使用 updateProgress + insert 组合
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(words: List<WordEntity>)

    /**
     * 逻辑删除单词 (User State)
     * 注意：新架构下不再支持简单的本地删除，此方法仅用于清理。
     */
    @Query("""
        UPDATE user_progress SET
        state = -1,
        updated_at = :updatedTime
        WHERE item_id IN (:ids) AND item_type = 'word'
    """)
    suspend fun softDeleteByIds(ids: List<Long>, updatedTime: String)

    /**
     * 标记单词下架 (Dictionary Sync)
     * 用于处理 JSON 源文件中已被删除的单词
     */
    @Query("UPDATE words SET is_delisted = 1 WHERE id IN (:ids)")
    suspend fun markAsDelisted(ids: List<Long>)

    /**
     * 物理删除单词 (仅用于同步或特殊清理)
     */
    @Query("DELETE FROM words WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    /**
     * 清空所有单词 (用于数据重置)
     */
    @Query("DELETE FROM words")
    suspend fun deleteAll()

    /**
     * 获取所有单词 (包含已逻辑删除的) - 用于同步合并
     */
    /**
     * 获取所有单词 (包含已逻辑删除的) - 用于同步合并
     */
    @Query("SELECT * FROM words")
    suspend fun getAllWordsWithDeletedSync(): List<WordEntity>

    /**
     * 获取自指定时间以来修改过的单词 (用于增量同步) - 注意：此方法现在通过 user_progress 的 updated_at 进行判断
     */
    @Query("""
        SELECT w.* FROM words w
        JOIN user_progress s ON w.id = s.item_id AND s.item_type = 'word'
        WHERE s.updated_at > :sinceTime
    """)
    suspend fun getModifiedSince(sinceTime: String): List<WordEntity>

    /**
     * 根据ID获取单词
     */
    @Query("""
        SELECT w.* FROM words w
        LEFT JOIN user_progress s ON w.id = s.item_id AND s.item_type = 'word'
        WHERE w.id = :id
        AND w.is_delisted = 0
    """)
    fun getById(id: Long): Flow<WordEntity?>

    // ========== 学习相关查询 ==========

    /**
     * 获取新单词（未学习且未跳过）
     * @param level JLPT等级
     * @return 新单词列表Flow
     */
    @Query("""
        SELECT w.* FROM words w
        LEFT JOIN user_progress s ON w.id = s.item_id AND s.item_type = 'word'
        WHERE w.level = :level
        AND (s.reps IS NULL OR s.reps = 0)
        AND (s.state != -1 OR s.state IS NULL)
        AND w.is_delisted = 0
        ORDER BY w.id ASC
    """)
    fun getNewWordsByLevel(level: String): Flow<List<WordEntity>>

    /**
     * 获取新单词（随机排序）
     */
    @Query("""
        SELECT w.* FROM words w
        LEFT JOIN user_progress s ON w.id = s.item_id AND s.item_type = 'word'
        WHERE w.level = :level
        AND (s.reps IS NULL OR s.reps = 0)
        AND (s.state != -1 OR s.state IS NULL)
        AND w.is_delisted = 0
        ORDER BY RANDOM()
    """)
    fun getNewWordsByLevelRandom(level: String): Flow<List<WordEntity>>

    /**
     * 获取到期复习单词
     * @param currentDate 当前日期 (ISO String)
     * @return 到期复习单词列表Flow
     */
    @Query("""
        SELECT w.* FROM words w
        JOIN user_progress s ON w.id = s.item_id AND s.item_type = 'word'
        WHERE w.level = :level
        AND s.state IN (1, 2, 3)
        AND s.next_review <= :currentDate
        AND s.buried_until <= :currentEpochDay
        AND w.is_delisted = 0
        ORDER BY (CASE s.state WHEN 3 THEN 0 WHEN 1 THEN 1 WHEN 2 THEN 2 ELSE 3 END), s.next_review ASC
    """)
    fun getDueWordsByLevel(currentDate: String, level: String, currentEpochDay: Long): Flow<List<WordEntity>>

    @Query("""
        SELECT w.* FROM words w
        JOIN user_progress s ON w.id = s.item_id AND s.item_type = 'word'
        WHERE s.next_review <= :currentDate
        AND s.reps > 0
        AND s.state != -1
        AND w.is_delisted = 0
        ORDER BY s.next_review ASC
    """)
    fun getDueWords(currentDate: String): Flow<List<WordEntity>>

    /**
     * 获取到期复习单词数量
     */
    @Query("""
        SELECT COUNT(*) FROM user_progress s
        JOIN words w ON s.item_id = w.id AND s.item_type = 'word'
        WHERE s.next_review <= :currentDate
        AND s.reps > 0
        AND s.state != -1
        AND s.buried_until <= :currentEpochDay
        AND w.is_delisted = 0
    """)
    fun getDueWordsCount(currentDate: String, currentEpochDay: Long): Flow<Int>

    /**
     * 获取今日首次学习的单词
     * @param todayISO 今天的ISO开始字符串
     * @return 今日学习的单词列表Flow
     */
    @Query("""
        SELECT w.* FROM words w
        JOIN user_progress s ON w.id = s.item_id AND s.item_type = 'word'
        WHERE s.created_at >= :todayISO
        AND (s.state != 0 OR s.buried_until > :currentEpochDay OR s.state = -1)
        AND w.is_delisted = 0
        ORDER BY w.id DESC
    """)
    fun getTodayLearnedWords(todayISO: String, currentEpochDay: Long): Flow<List<WordEntity>>

    /**
     * 获取今日复习过的单词
     * @param todayISO 今天的ISO开始字符串
     */
    @Query("""
        SELECT w.* FROM words w
        JOIN user_progress s ON w.id = s.item_id AND s.item_type = 'word'
        WHERE s.last_review >= :todayISO
        AND s.reps > 0
        AND s.created_at < :todayISO
        AND w.is_delisted = 0
        ORDER BY w.id DESC
    """)
    fun getTodayReviewedWords(todayISO: String): Flow<List<WordEntity>>

    /**
     * 获取所有已学习的单词 (不包含跳过的)
     */
    @Query("""
        SELECT w.* FROM words w
        JOIN user_progress s ON w.id = s.item_id AND s.item_type = 'word'
        WHERE s.reps > 0
        AND w.is_delisted = 0
        ORDER BY w.id DESC
    """)
    fun getAllLearnedWords(): Flow<List<WordEntity>>

    /**
     * 获取所有已学习的单词总数 (不包含跳过的)
     */
    @Query("""
        SELECT COUNT(*) FROM user_progress s
        JOIN words w ON s.item_id = w.id AND s.item_type = 'word'
        WHERE s.reps > 0
        AND w.is_delisted = 0
    """)
    fun getLearnedWordCount(): Flow<Int>

    /**
     * 获取指定等级的已学习单词 (不包含跳过的)
     */
    @Query("""
        SELECT w.* FROM words w
        JOIN user_progress s ON w.id = s.item_id AND s.item_type = 'word'
        WHERE s.reps > 0
        AND w.level = :level
        AND w.is_delisted = 0
        ORDER BY w.id DESC
    """)
    fun getLearnedWordsByLevel(level: String): Flow<List<WordEntity>>


    // ========== 收藏与跳过 ==========

    /**
     * 获取收藏的单词
     */
    @Query("""
        SELECT w.* FROM words w
        JOIN user_progress s ON w.id = s.item_id AND s.item_type = 'word'
        WHERE s.is_favorite = 1 AND s.state != -1 AND w.is_delisted = 0 ORDER BY w.id DESC
    """)
    fun getFavoriteWords(): Flow<List<WordEntity>>

    /**
     * 更新收藏状态
     */
    @Query("UPDATE user_progress SET is_favorite = :isFavorite, updated_at = :updatedAt WHERE item_id = :wordId AND item_type = 'word'")
    suspend fun updateFavoriteStatus(wordId: Long, isFavorite: Boolean, updatedAt: String)

    /**
     * 获取跳过的单词 (Suspend 状态)
     */
    @Query("""
        SELECT w.* FROM words w
        JOIN user_progress s ON w.id = s.item_id AND s.item_type = 'word'
        WHERE s.state = -1 AND w.is_delisted = 0 ORDER BY w.id DESC LIMIT :limit
    """)
    fun getSkippedWords(limit: Int): Flow<List<WordEntity>>

    /**
     * 获取跳过的单词数量
     */
    @Query("""
        SELECT COUNT(*) FROM user_progress s
        JOIN words w ON s.item_id = w.id AND s.item_type = 'word'
        WHERE s.state = -1 AND w.is_delisted = 0
    """)
    fun getSkippedWordsCount(): Flow<Int>

    /**
     * 获取指定等级的所有单词
     * @param level JLPT等级（如 "n5"、"n4" 等，小写）
     * @return 该等级所有单词列表Flow
     */
    @Query("""
        SELECT w.* FROM words w
        LEFT JOIN user_progress s ON w.id = s.item_id AND s.item_type = 'word'
        WHERE w.level = :level
        AND w.is_delisted = 0
        ORDER BY w.id ASC
    """)
    fun getAllWordsByLevel(level: String): Flow<List<WordEntity>>

    /**
     * 获取按复习日期排序的已学单词 (Adaptive Strategy Optimized Query)
     * 用于自适应测试：优先选择最该复习的单词 (nextReview 越小越优先)
     */
    @Query("""
        SELECT w.* FROM words w
        JOIN user_progress s ON w.id = s.item_id AND s.item_type = 'word'
        WHERE s.reps > 0
        AND w.level IN (:levels)
        AND s.state != -1
        AND w.is_delisted = 0
        ORDER BY s.next_review ASC
        LIMIT :limit
    """)
    suspend fun getWordsSortedByNextReviewDate(levels: List<String>, limit: Int): List<WordEntity>

    // ========== 词性分类查询（12种分类，首成分匹配）==========

    /**
     * 获取所有动词
     * 首成分为：他動 / 自動 / 自他動
     */
    @Query("""
        SELECT * FROM words
        WHERE (pos LIKE '他動%' OR pos LIKE '自動%' OR pos LIKE '自他動%')
        AND is_delisted = 0
        ORDER BY id ASC
    """)
    suspend fun getVerbs(): List<WordEntity>

    /**
     * 获取所有名词
     * 首成分为：名 / 代
     */
    @Query("""
        SELECT * FROM words
        WHERE (pos LIKE '名%' OR pos LIKE '代%')
        AND is_delisted = 0
        ORDER BY id ASC
    """)
    suspend fun getNouns(): List<WordEntity>

    /**
     * 获取所有形容词
     * 首成分为：イ形 / ナ形
     */
    @Query("""
        SELECT * FROM words
        WHERE (pos LIKE 'イ形%' OR pos LIKE 'ナ形%')
        AND is_delisted = 0
        ORDER BY id ASC
    """)
    suspend fun getAdjectives(): List<WordEntity>

    /**
     * 获取所有副词
     * 首成分为：副
     */
    @Query("SELECT * FROM words WHERE pos LIKE '副%' AND is_delisted = 0 ORDER BY id ASC")
    suspend fun getAdverbs(): List<WordEntity>

    /**
     * 获取所有助词
     * 首成分为：助
     */
    @Query("SELECT * FROM words WHERE pos LIKE '助%' AND is_delisted = 0 ORDER BY id ASC")
    suspend fun getParticles(): List<WordEntity>

    /**
     * 获取所有接续词
     * 首成分为：接 / 接続（排除接尾、接頭）
     */
    @Query("""
        SELECT * FROM words
        WHERE pos LIKE '接%' AND pos NOT LIKE '接尾%' AND pos NOT LIKE '接頭%'
        AND is_delisted = 0
        ORDER BY id ASC
    """)
    suspend fun getConjunctions(): List<WordEntity>

    /**
     * 获取所有连体词
     * 首成分为：連体
     */
    @Query("SELECT * FROM words WHERE pos = '連体' AND is_delisted = 0 ORDER BY id ASC")
    suspend fun getRentai(): List<WordEntity>

    /**
     * 获取所有接头词（前缀）
     * 首成分为：接頭
     */
    @Query("SELECT * FROM words WHERE pos LIKE '接頭%' AND is_delisted = 0 ORDER BY id ASC")
    suspend fun getPrefixes(): List<WordEntity>

    /**
     * 获取所有接尾词（后缀）
     * 首成分为：接尾
     */
    @Query("SELECT * FROM words WHERE pos LIKE '接尾%' AND is_delisted = 0 ORDER BY id ASC")
    suspend fun getSuffixes(): List<WordEntity>

    /**
     * 获取所有感叹词
     * 首成分为：嘆
     */
    @Query("SELECT * FROM words WHERE pos LIKE '嘆%' AND is_delisted = 0 ORDER BY id ASC")
    suspend fun getInterjections(): List<WordEntity>

    /**
     * 获取所有固定表达（惯用语/连语）
     * 首成分为：連語
     */
    @Query("SELECT * FROM words WHERE pos LIKE '連語%' AND is_delisted = 0 ORDER BY id ASC")
    suspend fun getFixedExpressions(): List<WordEntity>

    // ========== 批量操作 ==========

    /**
     * 根据ID列表获取单词
     */
    @Query("""
        SELECT COUNT(*) FROM user_progress
        WHERE updated_at > :timestamp AND item_type = 'word'
    """)
    suspend fun countModifiedSince(timestamp: String): Int

    @Query("SELECT * FROM words WHERE id IN (:ids)")
    suspend fun getWordsByIds(ids: List<Long>): List<WordEntity>

    /**
     * 搜索单词 (匹配日文、中文、假名)
     */
    @Query("""
        SELECT w.* FROM words w
        LEFT JOIN user_progress s ON w.id = s.item_id AND s.item_type = 'word'
        WHERE (w.japanese LIKE '%' || :query || '%'
        OR w.chinese LIKE '%' || :query || '%'
        OR w.hiragana LIKE '%' || :query || '%')
        AND (s.state != -1 OR s.state IS NULL)
        AND w.is_delisted = 0
        ORDER BY w.id ASC
    """)
    fun searchWords(query: String): Flow<List<WordEntity>>

    /**
     * 重置所有学习进度
     */
    @Query("DELETE FROM user_progress WHERE item_type = 'word'")
    suspend fun resetAllProgress()

    /**
     * 清空所有收藏
     */
    @Query("UPDATE user_progress SET is_favorite = 0 WHERE is_favorite = 1 AND item_type = 'word'")
    suspend fun clearAllFavorites()

    // ========== 测试用查询 ==========

    /**
     * 获取随机错误选项（用于选择题）
     * @param correctChinese 正确答案的中文
     * @param limit 需要的错误选项数量
     * @return 随机错误选项列表
     */
    @Query("SELECT * FROM words WHERE chinese != :correctChinese ORDER BY RANDOM() LIMIT :limit")
    suspend fun getRandomWrongOptions(correctChinese: String, limit: Int): List<WordEntity>

    /**
     * 获取所有单词
     * 用于内存筛选（例如外来语）
     */
    @Query("SELECT * FROM words")
    fun getAllWords(): List<WordEntity>

    /**
     * 获取所有单词 (同步) - 用于导出
     * 注意：getAllWords目前定义为fun getAllWords(): List<WordEntity> (suspend implied by Room if not Flow? No, must use suspend if not Flow)
     * To be safe and consistent with other DAOs, let's explicitly add getAllWordsSync
     */


    /**
     * 获取所有单词 (Cursor) - 用于流式导出
     * 优化：仅导出有进度或状态的单词
     */
    @Query("""
        SELECT
            w.id, w.japanese, w.hiragana, w.chinese, w.level,
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
        FROM words w
        JOIN user_progress s ON w.id = s.item_id AND s.item_type = 'word'
        WHERE s.reps > 0
        OR s.is_favorite = 1
        OR s.state = -1
        OR s.created_at IS NOT NULL
    """)
    fun getExportWordsCursor(): android.database.Cursor

    @Query("SELECT * FROM words")
    suspend fun getAllWordsSync(): List<WordEntity>

    /**
     * 获取复习预测
     * @param startDate 开始日期 (ISO)
     * @param endDate 结束日期 (ISO)
     */
    @Query("""
        SELECT SUBSTR(s.next_review, 1, 10) AS date, COUNT(*) AS count
        FROM user_progress s
        WHERE s.next_review BETWEEN :startDate AND :endDate
        AND s.state != -1 AND s.item_type = 'word'
        GROUP BY SUBSTR(s.next_review, 1, 10)
    """)
    fun getReviewForecast(startDate: String, endDate: String): Flow<List<ReviewForecastTuple>>

    // ========== 等级查询 ==========

    @Query("""
        SELECT DISTINCT w.level
        FROM user_progress s
        JOIN words w ON s.item_id = w.id AND s.item_type = 'word'
        WHERE s.last_review >= :todayISO
    """)
    fun getTodayReviewedLevels(todayISO: String): Flow<List<String>>

    @Query("""
        SELECT DISTINCT w.level
        FROM user_progress s
        JOIN words w ON s.item_id = w.id AND s.item_type = 'word'
        WHERE s.updated_at >= :todayISO
        AND (s.state != 0 OR s.buried_until > :currentEpochDay)
    """)
    fun getTodayLearnedLevels(todayISO: String, currentEpochDay: Long): Flow<List<String>>

    @Query("""
        SELECT DISTINCT w.level
        FROM user_progress s
        JOIN words w ON s.item_id = w.id AND s.item_type = 'word'
        WHERE s.is_favorite = 1
    """)
    fun getFavoriteLevels(): Flow<List<String>>

    @Query("""
        SELECT DISTINCT w.level
        FROM user_progress s
        JOIN words w ON s.item_id = w.id AND s.item_type = 'word'
        WHERE s.reps > 0
    """)
    fun getLearnedLevels(): Flow<List<String>>

    @Query("SELECT DISTINCT T2.level FROM wrong_answers T1 JOIN words T2 ON T1.word_id = T2.id")
    fun getWrongAnswerLevels(): Flow<List<String>>

    // ========== 数据统计 ==========

    /**
     * 获取单词总数
     */
    @Query("SELECT COUNT(*) FROM words")
    suspend fun getWordCount(): Int

    // ========== 数据修复 ==========

    /**
     * 获取去重后的保留ID列表 (每个单词只保留ID最小的一个)
     * 用于清理本地重复数据
     */
    @Query("SELECT MIN(id) FROM words GROUP BY japanese, hiragana, chinese, level")
    suspend fun getDuplicateKeepIds(): List<Long>

    /**
     * 将指定等级下，不在给定 ID 列表中的单词标记为已下架
     */
    @Query("UPDATE words SET is_delisted = 1 WHERE level = :level AND id NOT IN (:ids)")
    suspend fun markMissingAsDelistedById(level: String, ids: List<Long>): Int

    /**
     * 将指定等级下，不在给定日语原文列表中的单词标记为已下架
     * 用于云更新时的「静默下架」逻辑（处理 JSON 中直接删除行的情况）
     */
    @Query("UPDATE words SET is_delisted = 1 WHERE level = :level AND japanese NOT IN (:jsonJapaneseList)")
    suspend fun markMissingAsDelisted(level: String, jsonJapaneseList: List<String>): Int
}

data class ReviewForecastTuple(
    val date: String,
    val count: Int
)
