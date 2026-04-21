package com.jian.nemo.core.data.repository

import android.util.Log
import com.jian.nemo.core.data.local.dao.GrammarExampleDao
import com.jian.nemo.core.data.local.dao.GrammarUsageDao
import com.jian.nemo.core.data.local.dao.WordDao
import com.jian.nemo.core.data.local.dao.GrammarDao
import com.jian.nemo.core.domain.model.dto.WordDto
import com.jian.nemo.core.domain.model.dto.GrammarDto
import com.jian.nemo.core.data.mapper.toEntity
import com.jian.nemo.core.data.mapper.toGrammarEntity
import com.jian.nemo.core.data.mapper.toUsageEntities
import com.jian.nemo.core.data.mapper.toExampleEntities
import com.jian.nemo.core.domain.repository.ContentUpdateApplier
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
    private val grammarExampleDao: GrammarExampleDao
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

    companion object {
        private const val TAG = "ContentUpdateApplier"
    }
}
