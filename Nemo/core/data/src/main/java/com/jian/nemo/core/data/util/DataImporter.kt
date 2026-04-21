package com.jian.nemo.core.data.util

import android.content.Context
import android.util.Log
import com.jian.nemo.core.data.local.dao.GrammarDao
import com.jian.nemo.core.data.local.dao.GrammarUsageDao
import com.jian.nemo.core.data.local.dao.GrammarExampleDao
import com.jian.nemo.core.data.local.dao.WordDao
import com.jian.nemo.core.data.local.entity.GrammarEntity
import com.jian.nemo.core.data.local.entity.GrammarUsageEntity
import com.jian.nemo.core.data.local.entity.GrammarExampleEntity
import com.jian.nemo.core.data.local.entity.WordEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * 局部 DTO，专门用于解析 legacy 资产 JSON
 */
@Serializable
private data class LegacyWordDto(
    val rawId: String,
    val japanese: String,
    val hiragana: String,
    val chinese: String,
    val level: String,
    val pos: String? = null,
    val examples: List<LegacyWordExampleDto> = emptyList(),
    val delisted: Boolean = false
)

@Serializable
private data class LegacyWordExampleDto(
    val japanese: String,
    val chinese: String
)

@Serializable
private data class LegacyGrammarDto(
    val id: String,
    val title: String,
    val level: String,
    val delisted: Boolean = false,
    val usages: List<LegacyGrammarUsageDto> = emptyList()
)

@Serializable
private data class LegacyGrammarUsageDto(
    val subtype: String? = null,
    val connection: String,
    val explanation: String,
    val notes: String? = null,
    val examples: List<LegacyGrammarExampleDto> = emptyList()
)

@Serializable
private data class LegacyGrammarExampleDto(
    val sentence: String,
    val translation: String,
    val source: String? = null,
    val isDialog: Boolean = false
)

/**
 * 数据导入工具
 * 负责从 assets 读取 JSON 并导入数据库
 */
class DataImporter(
    private val context: Context,
    private val json: Json
) {
    companion object {
        private const val TAG = "DataImporter"

        fun extractNumericId(id: String): Int {
            return try {
                val parts = id.split("_")
                val level = parts[0].substring(1).toInt() // "N1" -> 1
                val num = parts[1].toInt()                // "001" -> 1
                level * 10000 + num
            } catch (e: Exception) {
                0
            }
        }
    }

    suspend fun importWords(wordDao: WordDao) = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "🚀 开始执行单词智能同步 (Smart Sync)...")
            val levels = listOf("N1", "N2", "N3", "N4", "N5")
            val jsonWords = mutableListOf<LegacyWordDto>()
            levels.forEach { level ->
                val words = readWordsFromAssets("word/$level.json")
                jsonWords.addAll(words)
            }

            val uniqueJsonMap = jsonWords.associateBy { "${it.level}_${it.japanese}" }
            val deduplicatedJsonWords = uniqueJsonMap.values

            val dbWords = wordDao.getAllWordsSync()
            val dbMap = dbWords.associateBy { "${it.level}_${it.japanese}" }

            val toInsert = mutableListOf<WordEntity>()
            val toUpdate = mutableListOf<WordEntity>()
            val jsonKeys = uniqueJsonMap.keys

            deduplicatedJsonWords.forEach { dto ->
                val key = "${dto.level}_${dto.japanese}"
                val existingEntity = dbMap[key]
                val newEntityData = dto.toEntity()

                if (existingEntity != null) {
                    val mergedEntity = existingEntity.copy(
                        hiragana = newEntityData.hiragana,
                        chinese = newEntityData.chinese,
                        pos = newEntityData.pos,
                        example1 = newEntityData.example1,
                        gloss1 = newEntityData.gloss1,
                        example2 = newEntityData.example2,
                        gloss2 = newEntityData.gloss2,
                        example3 = newEntityData.example3,
                        gloss3 = newEntityData.gloss3,
                        isDelisted = newEntityData.isDelisted 
                    )
                    if (mergedEntity != existingEntity) {
                        toUpdate.add(mergedEntity)
                    }
                } else {
                    toInsert.add(newEntityData)
                }
            }

            val toDelistIds = dbWords.filter { entity ->
                val key = "${entity.level}_${entity.japanese}"
                !jsonKeys.contains(key) && !entity.isDelisted
            }.map { it.id }

            if (toInsert.isNotEmpty()) {
                toInsert.chunked(500).forEach { wordDao.insertAll(it) }
            }
            if (toUpdate.isNotEmpty()) {
                toUpdate.chunked(500).forEach { wordDao.updateAll(it) }
            }
            if (toDelistIds.isNotEmpty()) {
                toDelistIds.chunked(500).forEach { wordDao.markAsDelisted(it) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 单词同步失败: ${e.message}", e)
            throw e
        }
    }

    suspend fun importGrammars(
        grammarDao: GrammarDao,
        grammarUsageDao: GrammarUsageDao,
        grammarExampleDao: GrammarExampleDao
    ) = withContext(Dispatchers.IO) {
        try {
            val levels = listOf("N1", "N2", "N3", "N4", "N5")
            levels.forEach { level ->
                val grammars = readGrammarsFromAssets("grammar/$level.json")
                grammars.forEach { dto ->
                    val grammarEntity = dto.toGrammarEntity()
                    grammarDao.insertAll(listOf(grammarEntity))

                    val usageEntities = dto.toUsageEntities()
                    val usageIds = grammarUsageDao.insertAll(usageEntities)

                    val exampleEntities = dto.toExampleEntities(usageIds)
                    grammarExampleDao.insertAll(exampleEntities)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "!!! ❌ 语法导入失败: ${e.message}", e)
            throw e
        }
    }

    private fun readWordsFromAssets(fileName: String): List<LegacyWordDto> {
        val inputStream = context.assets.open(fileName)
        val reader = BufferedReader(InputStreamReader(inputStream))
        val jsonString = reader.use { it.readText() }
        return json.decodeFromString(jsonString)
    }

    private fun readGrammarsFromAssets(fileName: String): List<LegacyGrammarDto> {
        val inputStream = context.assets.open(fileName)
        val reader = BufferedReader(InputStreamReader(inputStream))
        val jsonString = reader.use { it.readText() }
        return json.decodeFromString(jsonString)
    }

    private fun LegacyWordDto.toEntity(): WordEntity {
        return WordEntity(
            id = extractNumericId(rawId),
            japanese = japanese,
            hiragana = hiragana,
            chinese = chinese,
            level = level,
            pos = pos,
            example1 = examples.getOrNull(0)?.japanese,
            gloss1 = examples.getOrNull(0)?.chinese,
            example2 = examples.getOrNull(1)?.japanese,
            gloss2 = examples.getOrNull(1)?.chinese,
            example3 = examples.getOrNull(2)?.japanese,
            gloss3 = examples.getOrNull(2)?.chinese,
            isDelisted = delisted
        )
    }

    private fun LegacyGrammarDto.toGrammarEntity(): GrammarEntity {
        return GrammarEntity(
            id = extractNumericId(id),
            grammar = title,
            grammarLevel = level.uppercase(),
            isDelisted = delisted
        )
    }

    private fun LegacyGrammarDto.toUsageEntities(): List<GrammarUsageEntity> {
        val grammarId = extractNumericId(id)
        return usages.map { usage ->
            GrammarUsageEntity(
                grammarId = grammarId,
                subtype = usage.subtype,
                connection = usage.connection,
                explanation = usage.explanation,
                notes = usage.notes
            )
        }
    }

    private fun LegacyGrammarDto.toExampleEntities(usageIds: List<Long>): List<GrammarExampleEntity> {
        val result = mutableListOf<GrammarExampleEntity>()
        usages.forEachIndexed { usageIndex, usage ->
            val usageId = usageIds.getOrNull(usageIndex)?.toInt() ?: return@forEachIndexed
            usage.examples.forEach { example ->
                result.add(
                    GrammarExampleEntity(
                        usageId = usageId,
                        sentence = example.sentence,
                        translation = example.translation,
                        source = example.source,
                        isDialog = example.isDialog
                    )
                )
            }
        }
        return result
    }
}
