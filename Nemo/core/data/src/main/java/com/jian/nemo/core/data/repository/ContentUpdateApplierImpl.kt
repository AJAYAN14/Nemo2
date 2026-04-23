package com.jian.nemo.core.data.repository

import android.util.Log
import com.jian.nemo.core.data.local.NemoDatabase
import com.jian.nemo.core.data.local.dao.GrammarExampleDao
import com.jian.nemo.core.data.local.dao.GrammarUsageDao
import com.jian.nemo.core.data.local.dao.WordDao
import com.jian.nemo.core.data.local.dao.GrammarDao
import com.jian.nemo.core.domain.model.dto.WordDto
import com.jian.nemo.core.domain.model.dto.GrammarDto
import com.jian.nemo.core.domain.model.dto.GrammarTestQuestionDto
import com.jian.nemo.core.data.local.dao.GrammarQuestionDao
import com.jian.nemo.core.data.mapper.toEntity
import com.jian.nemo.core.data.mapper.toGrammarEntity
import com.jian.nemo.core.data.mapper.toUsageEntities
import com.jian.nemo.core.data.mapper.toExampleEntities
import com.jian.nemo.core.domain.repository.ContentUpdateApplier
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 将云端词库数据合并到本地 DB
 *
 * 策略：
 * 1. 单词：按 ID 匹配则 UPSERT 内容。
 * 2. 语法：按 ID REPLACE 主表，并重写该 grammar 的 usages/examples。
 */
@Singleton
class ContentUpdateApplierImpl @Inject constructor(
    private val wordDao: WordDao,
    private val grammarDao: GrammarDao,
    private val grammarUsageDao: GrammarUsageDao,
    private val grammarExampleDao: GrammarExampleDao,
    private val grammarQuestionDao: GrammarQuestionDao,
    private val database: NemoDatabase
) : ContentUpdateApplier {

    override suspend fun applyWords(level: String, words: List<WordDto>): Int? =
        withContext(Dispatchers.IO) {
            try {
                var count = 0
                val ids = words.map { it.id }
                
                // 批量 UPSERT
                val entities = words.map { it.toEntity() }
                wordDao.insertAll(entities) // Room 会根据 OnConflictStrategy.REPLACE 处理
                count = entities.size

                // 标记在本等级下，但不在本次数据中的词条为已下架
                val delistedCount = wordDao.markMissingAsDelistedById(level.uppercase(), ids)
                Log.d(TAG, "applyWords($level): $count updated/inserted, $delistedCount ghost-delisted")
                count
            } catch (e: Exception) {
                Log.e(TAG, "applyWords($level) failed", e)
                null
            }
        }

    override suspend fun applyGrammars(level: String, grammars: List<GrammarDto>): Int? =
        withContext(Dispatchers.IO) {
            try {
                var count = 0
                val ids = grammars.map { it.id }
                
                grammars.forEach { dto ->
                    val grammarEntity = dto.toGrammarEntity()
                    // 1. 插入/更新主表
                    grammarDao.upsertAll(listOf(grammarEntity))
                    val grammarId = grammarEntity.id

                    // 2. 清理旧用法与例句并重写 (确保数据一致性)
                    grammarUsageDao.deleteByGrammarId(grammarId)
                    val usageEntities = dto.toUsageEntities()
                    val usageIds = grammarUsageDao.insertAll(usageEntities)
                    val exampleEntities = dto.toExampleEntities(usageIds)
                    grammarExampleDao.insertAll(exampleEntities)
                    count++
                }

                // 标记在本等级下，但不在本次数据中的语法为已下架
                val delistedCount = grammarDao.markMissingAsDelistedById(level.uppercase(), ids)
                Log.d(TAG, "applyGrammars($level): $count items, $delistedCount ghost-delisted")
                count
            } catch (e: Exception) {
                Log.e(TAG, "applyGrammars($level) failed", e)
                null
            }
        }

    override suspend fun applyGrammarQuestions(level: String, questions: List<GrammarTestQuestionDto>): Int? =
        withContext(Dispatchers.IO) {
            try {
                var count = 0
                // 1. 批量插入/更新
                val entities = questions.map { it.toEntity() }
                grammarQuestionDao.insertAll(entities)
                count = entities.size

                Log.d(TAG, "applyGrammarQuestions($level): $count items updated/inserted")
                count
            } catch (e: Exception) {
                Log.e(TAG, "applyGrammarQuestions($level) failed", e)
                null
            }
        }

    // ========== 全量批量写入 (性能优化) ==========

    override suspend fun applyAllWords(words: List<WordDto>): Int? =
        withContext(Dispatchers.IO) {
            try {
                if (words.isEmpty()) return@withContext 0

                val entities = words.map { it.toEntity() }

                database.withTransaction {
                    // 1. 批量 UPSERT 所有单词
                    wordDao.insertAll(entities)

                    // 2. 按等级分组处理下架逻辑
                    val byLevel = words.groupBy { it.level.uppercase() }
                    var totalDelisted = 0
                    byLevel.forEach { (level, levelWords) ->
                        val ids = levelWords.map { it.id }
                        totalDelisted += wordDao.markMissingAsDelistedById(level, ids)
                    }
                    Log.d(TAG, "applyAllWords: ${entities.size} upserted, $totalDelisted ghost-delisted across ${byLevel.size} levels")
                }

                entities.size
            } catch (e: Exception) {
                Log.e(TAG, "applyAllWords failed", e)
                null
            }
        }

    override suspend fun applyAllGrammars(grammars: List<GrammarDto>): Int? =
        withContext(Dispatchers.IO) {
            try {
                if (grammars.isEmpty()) return@withContext 0

                database.withTransaction {
                    // 1. 批量插入/更新语法主表
                    val grammarEntities = grammars.map { it.toGrammarEntity() }
                    grammarDao.upsertAll(grammarEntities)

                    // 2. 批量清理旧的用法和例句
                    val grammarIds = grammars.map { it.id }
                    // Room 的 IN 查询对大列表可能有 SQLite 变量上限 (999)，需要分批处理
                    grammarIds.chunked(500).forEach { chunk ->
                        grammarUsageDao.deleteByGrammarIds(chunk)
                    }

                    // 3. 批量插入所有用法，并收集生成的 ID
                    val allUsageEntities = grammars.flatMap { it.toUsageEntities() }
                    val allUsageIds = if (allUsageEntities.isNotEmpty()) {
                        grammarUsageDao.insertAll(allUsageEntities)
                    } else {
                        emptyList()
                    }

                    // 4. 根据用法 ID 构建例句实体并批量插入
                    //    需要维护 DTO -> usageIds 的映射关系
                    val allExampleEntities = mutableListOf<com.jian.nemo.core.data.local.entity.GrammarExampleEntity>()
                    var usageIdCursor = 0
                    grammars.forEach { dto ->
                        val usageCount = dto.content.size
                        val usageIdsForThisGrammar = allUsageIds.subList(usageIdCursor, usageIdCursor + usageCount)
                        val examples = dto.toExampleEntities(usageIdsForThisGrammar)
                        allExampleEntities.addAll(examples)
                        usageIdCursor += usageCount
                    }

                    if (allExampleEntities.isNotEmpty()) {
                        grammarExampleDao.insertAll(allExampleEntities)
                    }

                    // 5. 按等级分组处理下架逻辑
                    val byLevel = grammars.groupBy { it.level.uppercase() }
                    var totalDelisted = 0
                    byLevel.forEach { (level, levelGrammars) ->
                        val ids = levelGrammars.map { it.id }
                        totalDelisted += grammarDao.markMissingAsDelistedById(level, ids)
                    }

                    Log.d(TAG, "applyAllGrammars: ${grammarEntities.size} grammars, ${allUsageEntities.size} usages, ${allExampleEntities.size} examples upserted, $totalDelisted ghost-delisted")
                }

                grammars.size
            } catch (e: Exception) {
                Log.e(TAG, "applyAllGrammars failed", e)
                null
            }
        }

    override suspend fun applyAllGrammarQuestions(questions: List<GrammarTestQuestionDto>): Int? =
        withContext(Dispatchers.IO) {
            try {
                if (questions.isEmpty()) return@withContext 0

                val entities = questions.map { it.toEntity() }
                database.withTransaction {
                    grammarQuestionDao.insertAll(entities)
                }

                Log.d(TAG, "applyAllGrammarQuestions: ${entities.size} items upserted")
                entities.size
            } catch (e: Exception) {
                Log.e(TAG, "applyAllGrammarQuestions failed", e)
                null
            }
        }

    companion object {
        private const val TAG = "ContentUpdateApplier"
    }
}
