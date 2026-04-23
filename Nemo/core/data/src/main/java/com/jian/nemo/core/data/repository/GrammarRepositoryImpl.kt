package com.jian.nemo.core.data.repository

import com.jian.nemo.core.common.Result
import com.jian.nemo.core.common.util.DateTimeUtils
import com.jian.nemo.core.data.local.dao.*
import com.jian.nemo.core.data.mapper.GrammarMapper
import com.jian.nemo.core.data.mapper.GrammarMapper.toDomainModel
import com.jian.nemo.core.data.mapper.GrammarMapper.toDomainModels
import com.jian.nemo.core.data.mapper.GrammarMapper.toProgressEntity
import com.jian.nemo.core.domain.model.ContentDelist.isDelisted
import com.jian.nemo.core.domain.model.Grammar
import com.jian.nemo.core.domain.repository.GrammarRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GrammarRepositoryImpl @Inject constructor(
    private val grammarDao: GrammarDao,
    private val userProgressDao: UserProgressDao,
    private val studyRepository: com.jian.nemo.core.domain.repository.StudyRepository,
    private val syncManager: com.jian.nemo.core.data.manager.SupabaseSyncManager
) : GrammarRepository {

    private val userId: String
        get() = syncManager.getCurrentUserId() ?: "local_user"

    // ========== 查询实现 ==========

    override fun getGrammarById(id: Long): Flow<Grammar?> {
        return grammarDao.getGrammarWithUsages(id)
            .map { it?.toDomainModel() }
            .catch { e ->
                emit(null)
            }.flowOn(kotlinx.coroutines.Dispatchers.IO)
    }

    override fun getAllGrammars(): Flow<List<Grammar>> {
        return grammarDao.getAllGrammarsWithUsages()
            .map { it.toDomainModels() }
            .catch { e ->
                emit(emptyList())
            }.flowOn(kotlinx.coroutines.Dispatchers.IO)
    }

    override fun getNewGrammars(level: String, isRandom: Boolean): Flow<List<Grammar>> {
        val flow = if (isRandom) {
            grammarDao.getNewGrammarsByLevelWithUsagesRandom(level)
        } else {
            grammarDao.getNewGrammarsByLevelWithUsages(level)
        }

        return flow
            .map { it.toDomainModels().filter { g -> !g.isDelisted } }
            .catch { e ->
                emit(emptyList())
            }.flowOn(kotlinx.coroutines.Dispatchers.IO)
    }

    override fun getDueGrammars(today: Long, level: String): Flow<List<Grammar>> {
        // 与 Web 端对齐：移除 12 小时超前缓冲，仅保留 1 分钟容错
        val bufferMs = 1 * 60 * 1000L
        val nowWithBuffer = DateTimeUtils.millisToIso(System.currentTimeMillis() + bufferMs)
        val currentEpochDay = today

        return grammarDao.getDueGrammarsByLevel(nowWithBuffer, level, currentEpochDay)
            .map { it.toDomainModels().filter { g -> !g.isDelisted } }
            .catch { e ->
                android.util.Log.e("GrammarRepository", "获取到期语法失败: ${e.message}", e)
                emit(emptyList())
            }.flowOn(kotlinx.coroutines.Dispatchers.IO)
    }

    override fun getDueGrammarsCount(today: Long): Flow<Int> {
        return getDueGrammars(today, "ALL").map { it.size }
    }

    override fun getSkippedGrammars(limit: Int): Flow<List<Grammar>> {
        return grammarDao.getSkippedGrammarsWithUsages(limit)
            .map { it.toDomainModels() }
            .catch { e ->
                emit(emptyList())
            }
    }

    override fun getTodayLearnedGrammars(today: Long): Flow<List<Grammar>> {
        val todayIso = DateTimeUtils.epochDayToIso(today)
        return grammarDao.getTodayLearnedGrammarsWithUsages(todayIso)
            .map { it.toDomainModels() }
            .catch { e ->
                emit(emptyList())
            }
    }

    override fun getTodayReviewedGrammars(today: Long): Flow<List<Grammar>> {
        val todayIso = DateTimeUtils.epochDayToIso(today)
        return grammarDao.getTodayReviewedGrammarsWithUsages(todayIso)
            .map { it.toDomainModels() }
            .catch { e ->
                emit(emptyList())
            }
    }

    override fun getFavoriteGrammars(): Flow<List<Grammar>> {
        return grammarDao.getFavoriteGrammarsWithUsages()
            .map { it.toDomainModels() }
            .catch { e ->
                emit(emptyList())
            }
    }

    override fun getAllLearnedGrammars(): Flow<List<Grammar>> {
        return grammarDao.getAllLearnedGrammarsWithUsages()
            .map { it.toDomainModels() }
            .catch { e ->
                emit(emptyList())
            }.flowOn(kotlinx.coroutines.Dispatchers.IO)
    }

    override fun getAllLearnedGrammarsByLevel(level: String): Flow<List<Grammar>> {
        val upperLevel = level.uppercase()
        return grammarDao.getLearnedGrammarsByLevelWithUsages(upperLevel)
            .map { it.toDomainModels() }
            .catch { e ->
                emit(emptyList())
            }.flowOn(kotlinx.coroutines.Dispatchers.IO)
    }

    override fun getLearnedGrammarCount(): Flow<Int> {
        return grammarDao.getLearnedGrammarCount()
            .catch { e ->
                emit(0)
            }.flowOn(kotlinx.coroutines.Dispatchers.IO)
    }

    override fun getReviewForecast(startDate: Long, endDate: Long): Flow<List<com.jian.nemo.core.domain.model.ReviewForecast>> {
        val startIso = DateTimeUtils.epochDayToIso(startDate)
        val endIso = DateTimeUtils.epochDayToIso(endDate)
        return grammarDao.getReviewForecast(startIso, endIso)
            .map { tuples ->
                tuples.map {
                    com.jian.nemo.core.domain.model.ReviewForecast(date = DateTimeUtils.isoToEpochDay(it.date), grammarCount = it.count)
                }
            }
            .catch { e ->
                emit(emptyList())
            }
    }

    override fun getTodayLearnedGrammarLevels(today: Long): Flow<List<String>> {
        val todayIso = DateTimeUtils.epochDayToIso(today)
        return grammarDao.getTodayLearnedGrammarLevels(todayIso)
            .catch { e ->
                emit(emptyList())
            }
    }

    override fun getFavoriteGrammarLevels(): Flow<List<String>> {
         return grammarDao.getFavoriteGrammarLevels()
            .catch { e ->
                emit(emptyList())
            }
    }

    override fun getLearnedGrammarLevels(): Flow<List<String>> {
         return grammarDao.getLearnedGrammarLevels()
            .catch { e ->
                emit(emptyList())
            }
    }

    override fun getTodayReviewedGrammarLevels(today: Long): Flow<List<String>> {
        val todayIso = DateTimeUtils.epochDayToIso(today)
         return grammarDao.getTodayReviewedGrammarLevels(todayIso)
            .catch { e ->
                emit(emptyList())
            }
    }

    override fun getWrongAnswerGrammarLevels(): Flow<List<String>> {
         return grammarDao.getWrongAnswerGrammarLevels()
            .catch { e ->
                emit(emptyList())
            }
    }

    override fun getGrammarsByLevels(levels: List<String>): Flow<List<Grammar>> {
        return grammarDao.getGrammarsByLevelsWithUsages(levels)
            .map { entities ->
                entities.toDomainModels()
            }
            .catch { e ->
                emit(emptyList())
            }
    }

    override suspend fun getGrammarsByIds(ids: List<Long>): List<Grammar> {
        return try {
            if (ids.isEmpty()) {
                emptyList()
            } else {
                grammarDao.getGrammarsByIdsWithUsages(ids).map { it.toDomainModel() }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override fun searchGrammars(query: String): Flow<List<Grammar>> {
        return grammarDao.searchGrammarsWithUsages(query)
            .map { it.toDomainModels() }
            .catch { e ->
                emit(emptyList())
            }
    }

    // ========== 更新实现 ==========

    override suspend fun updateGrammar(grammar: Grammar): Result<Unit> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val progressEntity = grammar.toProgressEntity(userId)
            userProgressDao.insert(progressEntity)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    override suspend fun updateFavoriteStatus(
        grammarId: Long,
        isFavorite: Boolean
    ): Result<Unit> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            studyRepository.toggleFavorite(grammarId, "grammar", isFavorite)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    override suspend fun markAsSkipped(grammarId: Long): Result<Unit> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            studyRepository.suspendItem(grammarId, "grammar")
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    override suspend fun unmarkAsSkipped(grammarId: Long): Result<Unit> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            studyRepository.unsuspendItem(grammarId, "grammar")
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    // ========== 批量操作 ==========

    override suspend fun resetAllProgress(): Result<Unit> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            studyRepository.resetAllProgress("grammar")
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    override suspend fun clearAllFavorites(): Result<Unit> {
        return try {
            studyRepository.clearAllFavorites("grammar")
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
}
