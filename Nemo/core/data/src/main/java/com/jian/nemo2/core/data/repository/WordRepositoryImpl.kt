package com.jian.nemo2.core.data.repository

import android.util.Log
import com.jian.nemo2.core.common.Result
import com.jian.nemo2.core.data.local.dao.*
import com.jian.nemo2.core.data.local.entity.TestRecordEntity
import com.jian.nemo2.core.data.local.entity.WordEntity
import com.jian.nemo2.core.data.mapper.WordMapper
import com.jian.nemo2.core.data.mapper.WordMapper.toProgressEntity
import com.jian.nemo2.core.domain.model.PartOfSpeech
import com.jian.nemo2.core.domain.model.Word
import com.jian.nemo2.core.domain.model.ReviewForecast
import com.jian.nemo2.core.domain.repository.WordRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WordRepositoryImpl @Inject constructor(
    private val wordDao: WordDao,
    private val userProgressDao: UserProgressDao,
    private val testRecordDao: TestRecordDao,
    private val studyRepository: com.jian.nemo2.core.domain.repository.StudyRepository,
    private val settingsRepository: com.jian.nemo2.core.domain.repository.SettingsRepository,
    private val syncManager: com.jian.nemo2.core.data.manager.SupabaseSyncManager
) : WordRepository {

    private val userId: String
        get() = syncManager.getCurrentUserId() ?: "local_user"

    private suspend fun mapWithStudyState(entities: List<WordEntity>): List<Word> {
        if (entities.isEmpty()) return emptyList()

        val itemIds = entities.map { it.id }
        val statesById = userProgressDao
            .getProgressByItemIds(itemIds, "word")
            .associateBy { it.itemId }

        return entities.map { entity ->
            WordMapper.toDomainModel(entity, statesById[entity.id])
        }
    }

    // ========== 查询实现 ==========

    override fun getWordById(id: Long): Flow<Word?> {
        return combine(
            wordDao.getById(id),
            userProgressDao.getProgressByItemIdFlow(id, "word")
        ) { entity, state ->
            entity?.let { WordMapper.toDomainModel(it, state) }
        }
            .catch { e ->
                emit(null)
            }.flowOn(kotlinx.coroutines.Dispatchers.IO)
    }

    override fun getNewWords(level: String, isRandom: Boolean): Flow<List<Word>> {
        val flow = if (isRandom) {
            wordDao.getNewWordsByLevelRandom(level)
        } else {
            wordDao.getNewWordsByLevel(level)
        }

        return flow
            .map { entities ->
                mapWithStudyState(entities).filter { w -> !w.isDelisted }
            }
            .catch { e ->
                emit(emptyList())
            }.flowOn(kotlinx.coroutines.Dispatchers.IO)
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    override fun getDueWords(level: String, today: Long): Flow<List<Word>> {
        return settingsRepository.learnAheadLimitFlow.flatMapLatest { learnAheadMinutes ->
            val bufferMs = learnAheadMinutes * 60 * 1000L
            val nowWithBuffer = com.jian.nemo2.core.common.util.DateTimeUtils.millisToIso(System.currentTimeMillis() + bufferMs)
            val currentEpochDay = today

            wordDao.getDueWordsByLevel(nowWithBuffer, level.lowercase(), currentEpochDay)
                .map { entities ->
                    mapWithStudyState(entities).filter { w -> !w.isDelisted }
                }
        }
            .catch { e ->
                Log.e("WordRepository", "获取到期单词失败: ${e.message}", e)
                emit(emptyList())
            }.flowOn(kotlinx.coroutines.Dispatchers.IO)
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    override fun getDueWordsCount(today: Long): Flow<Int> {
        return settingsRepository.learnAheadLimitFlow.flatMapLatest { learnAheadMinutes ->
            val bufferMs = learnAheadMinutes * 60 * 1000L
            val nowWithBuffer = com.jian.nemo2.core.common.util.DateTimeUtils.millisToIso(System.currentTimeMillis() + bufferMs)
            wordDao.getDueWordsCount(nowWithBuffer, today)
        }
            .catch { emit(0) }
            .flowOn(kotlinx.coroutines.Dispatchers.IO)
    }

    override fun getTodayLearnedWords(today: Long): Flow<List<Word>> {
        val todayIso = com.jian.nemo2.core.common.util.DateTimeUtils.epochDayToIso(today)
        return wordDao.getTodayLearnedWords(todayIso, today)
            .map { entities ->
                mapWithStudyState(entities)
            }
            .catch { e ->
                emit(emptyList())
            }.flowOn(kotlinx.coroutines.Dispatchers.IO)
    }

    override fun getTodayReviewedWords(today: Long): Flow<List<Word>> {
        val todayIso = com.jian.nemo2.core.common.util.DateTimeUtils.epochDayToIso(today)
        return wordDao.getTodayReviewedWords(todayIso)
            .map { entities ->
                mapWithStudyState(entities)
            }
            .catch { e ->
                emit(emptyList())
            }.flowOn(kotlinx.coroutines.Dispatchers.IO)
    }

    override fun getFavoriteWords(): Flow<List<Word>> {
        return wordDao.getFavoriteWords()
            .map { entities ->
                mapWithStudyState(entities)
            }
            .catch { e ->
                emit(emptyList())
            }.flowOn(kotlinx.coroutines.Dispatchers.IO)
    }

    override suspend fun getWordsSortedByDueScore(levels: List<String>, limit: Int): List<Word> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            if (levels.isEmpty()) return@withContext emptyList()
            val entities = wordDao.getWordsSortedByNextReviewDate(levels, limit)
            mapWithStudyState(entities)
        } catch (e: Exception) {
            emptyList()
        }
    }

    override fun getSkippedWords(limit: Int): Flow<List<Word>> {
        return wordDao.getSkippedWords(limit).map { entities ->
            mapWithStudyState(entities)
        }.flowOn(kotlinx.coroutines.Dispatchers.IO)
    }

    override fun getAllWordsByLevel(level: String): Flow<List<Word>> {
        val upperLevel = level.uppercase()
        return wordDao.getAllWordsByLevel(upperLevel).map { entities ->
            mapWithStudyState(entities)
        }.flowOn(kotlinx.coroutines.Dispatchers.IO)
    }

    override fun getAllLearnedWords(): Flow<List<Word>> {
        return wordDao.getAllLearnedWords()
            .map { entities ->
                mapWithStudyState(entities)
            }
            .catch { e ->
                emit(emptyList())
            }.flowOn(kotlinx.coroutines.Dispatchers.IO)
    }

    override fun getAllLearnedWordsByLevel(level: String): Flow<List<Word>> {
        val upperLevel = level.uppercase()
        return wordDao.getLearnedWordsByLevel(upperLevel)
            .map { entities ->
                mapWithStudyState(entities)
            }
            .catch { e ->
                emit(emptyList())
            }.flowOn(kotlinx.coroutines.Dispatchers.IO)
    }

    override fun getLearnedWordCount(): Flow<Int> {
        return wordDao.getLearnedWordCount()
            .catch { e ->
                emit(0)
            }.flowOn(kotlinx.coroutines.Dispatchers.IO)
    }

    override fun getReviewForecast(startDate: Long, endDate: Long): Flow<List<ReviewForecast>> {
        val startIso = com.jian.nemo2.core.common.util.DateTimeUtils.epochDayToIso(startDate)
        val endIso = com.jian.nemo2.core.common.util.DateTimeUtils.epochDayToIso(endDate)
        return wordDao.getReviewForecast(startIso, endIso)
            .map { tuples ->
                tuples.map {
                    ReviewForecast(date = com.jian.nemo2.core.common.util.DateTimeUtils.isoToEpochDay(it.date), wordCount = it.count)
                }
            }
            .catch { e ->
                emit(emptyList())
            }.flowOn(kotlinx.coroutines.Dispatchers.IO)
    }

    override fun searchWords(query: String): Flow<List<Word>> {
        return wordDao.searchWords(query)
            .map { entities ->
                mapWithStudyState(entities)
            }
            .catch { e ->
                emit(emptyList())
            }.flowOn(kotlinx.coroutines.Dispatchers.IO)
    }

    override fun getTodayTestCount(today: Long): Flow<Int> {
        return testRecordDao.getRecordsByDate(today)
            .map { it.size }
            .catch { e ->
                emit(0)
            }.flowOn(kotlinx.coroutines.Dispatchers.IO)
    }

    override fun getTodayTestAccuracy(today: Long): Flow<Float> {
        return testRecordDao.getRecordsByDate(today)
            .map { records ->
                if (records.isEmpty()) return@map 0f
                val totalQuestions = records.sumOf { it.totalQuestions }
                val totalCorrect = records.sumOf { it.correctAnswers }
                if (totalQuestions > 0) totalCorrect.toFloat() / totalQuestions else 0f
            }
            .catch { e ->
                emit(0f)
            }.flowOn(kotlinx.coroutines.Dispatchers.IO)
    }

    override fun getTotalTestCount(): Flow<Int> {
        return testRecordDao.getTotalTestCount()
            .catch { e ->
                emit(0)
            }.flowOn(kotlinx.coroutines.Dispatchers.IO)
    }

    override fun getOverallAccuracy(): Flow<Float> {
        return kotlinx.coroutines.flow.combine(
            testRecordDao.getTotalCorrectAnswers(),
            testRecordDao.getTotalQuestions()
        ) { correct, total ->
            val c = correct ?: 0
            val t = total ?: 0
            if (t > 0) c.toFloat() / t else 0f
        }.catch { e ->
            emit(0f)
        }.flowOn(kotlinx.coroutines.Dispatchers.IO)
    }

    override fun getTotalQuestionCount(): Flow<Int> {
        return testRecordDao.getTotalQuestions()
            .map { it ?: 0 }
            .catch { e ->
                emit(0)
            }.flowOn(kotlinx.coroutines.Dispatchers.IO)
    }

    override fun getTotalCorrectAnswerCount(): Flow<Int> {
        return testRecordDao.getTotalCorrectAnswers()
            .map { it ?: 0 }
            .catch { e ->
                emit(0)
            }.flowOn(kotlinx.coroutines.Dispatchers.IO)
    }

    override fun getTodayLearnedLevels(todayEpochDay: Long): Flow<List<String>> {
        val todayIso = com.jian.nemo2.core.common.util.DateTimeUtils.epochDayToIso(todayEpochDay)
        return wordDao.getTodayLearnedLevels(todayIso, todayEpochDay)
            .catch { e ->
                emit(emptyList())
            }
    }

    override fun getFavoriteLevels(): Flow<List<String>> {
        return wordDao.getFavoriteLevels()
            .catch { e ->
                emit(emptyList())
            }
    }

    override fun getLearnedLevels(): Flow<List<String>> {
        return wordDao.getLearnedLevels()
            .catch { e ->
                emit(emptyList())
            }
    }

    override fun getTodayReviewedLevels(todayEpochDay: Long): Flow<List<String>> {
        val todayIso = com.jian.nemo2.core.common.util.DateTimeUtils.epochDayToIso(todayEpochDay)
        return wordDao.getTodayReviewedLevels(todayIso)
            .catch { e ->
                emit(emptyList())
            }
    }

    override fun getWrongAnswerLevels(): Flow<List<String>> {
        return wordDao.getWrongAnswerLevels()
            .catch { e ->
                emit(emptyList())
            }
    }

    override suspend fun getLoanWords(): List<Word> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val entities = wordDao.getAllWords()
            val allWords = mapWithStudyState(entities)
            allWords.filter { word ->
                val japanese = word.japanese
                val hiragana = word.hiragana
                val hasEnglish = japanese.contains(Regex("[a-zA-Z]"))
                val symbolRegex = Regex("[・〜ー\\s\\-()（）/]")
                val jCleaned = japanese.replace(symbolRegex, "")
                val isKatakanaJapanese = jCleaned.isNotEmpty() && jCleaned.all { ch ->
                    Character.UnicodeBlock.of(ch) == Character.UnicodeBlock.KATAKANA
                }
                val hCleaned = hiragana.replace(symbolRegex, "")
                val isKatakanaKana = hCleaned.isNotEmpty() && hCleaned.all { ch ->
                    Character.UnicodeBlock.of(ch) == Character.UnicodeBlock.KATAKANA
                }
                hasEnglish || isKatakanaJapanese || isKatakanaKana
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun getWordsByPartOfSpeech(pos: PartOfSpeech): List<Word> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            if (pos == PartOfSpeech.LOAN_WORD) {
                return@withContext getLoanWords()
            }

            val entities = when (pos) {
                PartOfSpeech.VERB -> wordDao.getVerbs()
                PartOfSpeech.NOUN -> wordDao.getNouns()
                PartOfSpeech.ADJECTIVE -> wordDao.getAdjectives()
                PartOfSpeech.ADVERB -> wordDao.getAdverbs()
                PartOfSpeech.PARTICLE -> wordDao.getParticles()
                PartOfSpeech.CONJUNCTION -> wordDao.getConjunctions()
                PartOfSpeech.RENTAI -> wordDao.getRentai()
                PartOfSpeech.PREFIX -> wordDao.getPrefixes()
                PartOfSpeech.SUFFIX -> wordDao.getSuffixes()
                PartOfSpeech.INTERJECTION -> wordDao.getInterjections()
                PartOfSpeech.FIXED_EXPRESSION -> wordDao.getFixedExpressions()
                else -> emptyList()
            }
            mapWithStudyState(entities)
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun getWordsByIds(ids: List<Long>): List<Word> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            if (ids.isEmpty()) {
                emptyList()
            } else {
                mapWithStudyState(wordDao.getWordsByIds(ids))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ========== 更新实现 ==========

    override suspend fun updateWord(word: Word): Result<Unit> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val progressEntity = word.toProgressEntity(userId)
            userProgressDao.insert(progressEntity)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    override suspend fun updateFavoriteStatus(
        wordId: Long,
        isFavorite: Boolean
    ): Result<Unit> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        return@withContext try {
            studyRepository.toggleFavorite(wordId, "word", isFavorite)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    override suspend fun markAsSkipped(wordId: Long): Result<Unit> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            studyRepository.suspendItem(wordId, "word")
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    override suspend fun unmarkAsSkipped(wordId: Long): Result<Unit> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            studyRepository.unsuspendItem(wordId, "word")
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    // ========== 批量操作 ==========

    override suspend fun resetAllProgress(): Result<Unit> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            studyRepository.resetAllProgress("word")
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    override suspend fun clearAllFavorites(): Result<Unit> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            studyRepository.clearAllFavorites("word")
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    override suspend fun saveTestRecord(record: com.jian.nemo2.core.domain.model.TestRecord): Result<Unit> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val entity = TestRecordEntity(
                id = record.id,
                date = record.date,
                totalQuestions = record.totalQuestions,
                correctAnswers = record.correctAnswers,
                testMode = record.testMode,
                timestamp = record.timestamp
            )
            testRecordDao.insert(entity)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
}
