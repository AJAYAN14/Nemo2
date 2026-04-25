package com.jian.nemo2.core.data.repository

import com.jian.nemo2.core.data.local.dao.FavoriteQuestionDao
import com.jian.nemo2.core.data.local.entity.FavoriteQuestionEntity
import com.jian.nemo2.core.domain.model.FavoriteQuestion
import com.jian.nemo2.core.domain.repository.FavoriteQuestionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import javax.inject.Inject

/**
 * 收藏题目 Repository 实现
 */
class FavoriteQuestionRepositoryImpl @Inject constructor(
    private val favoriteQuestionDao: FavoriteQuestionDao
) : FavoriteQuestionRepository {

    override fun getAllFavoriteQuestions(): Flow<List<FavoriteQuestion>> {
        return favoriteQuestionDao.getAllFavoriteQuestions().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun insertFavoriteQuestion(question: FavoriteQuestion) {
        withContext(Dispatchers.IO) {
            favoriteQuestionDao.insert(question.toEntity())
            println("✅ 收藏题目已保存: questionText=${question.questionText.take(20)}...")
        }
    }

    override suspend fun deleteFavoriteQuestion(id: String) {
        withContext(Dispatchers.IO) {
            favoriteQuestionDao.deleteById(id)
        }
    }

    override suspend fun isFavorite(grammarId: Long?, jsonId: String?): Boolean {
        return withContext(Dispatchers.IO) {
            when {
                jsonId != null -> favoriteQuestionDao.isFavoriteByJsonId(jsonId)
                grammarId != null -> favoriteQuestionDao.isFavoriteByGrammarId(grammarId)
                else -> false
            }
        }
    }

    override suspend fun removeFavorite(grammarId: Long?, jsonId: String?) {
        withContext(Dispatchers.IO) {
            when {
                jsonId != null -> favoriteQuestionDao.deleteByJsonId(jsonId)
                grammarId != null -> favoriteQuestionDao.deleteByGrammarId(grammarId)
            }
        }
    }

    override suspend fun clearAll() {
        withContext(Dispatchers.IO) {
            favoriteQuestionDao.deleteAll()
        }
    }

    // ========== Entity <-> Domain 转换 ==========

    private fun FavoriteQuestionEntity.toDomain(): FavoriteQuestion {
        return FavoriteQuestion(
            id = id,
            grammarId = grammarId,
            jsonId = jsonId,
            questionType = questionType,
            questionText = questionText,
            options = parseJsonArray(optionsJson),
            correctAnswer = correctAnswer,
            explanation = explanation,
            timestamp = timestamp
        )
    }

    private fun FavoriteQuestion.toEntity(): FavoriteQuestionEntity {
        return FavoriteQuestionEntity(
            id = id,
            grammarId = grammarId,
            jsonId = jsonId,
            questionType = questionType,
            questionText = questionText,
            optionsJson = toJsonArray(options),
            correctAnswer = correctAnswer,
            explanation = explanation,
            timestamp = timestamp
        )
    }

    private fun parseJsonArray(json: String): List<String> {
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun toJsonArray(list: List<String>): String {
        val arr = JSONArray()
        list.forEach { arr.put(it) }
        return arr.toString()
    }
}
