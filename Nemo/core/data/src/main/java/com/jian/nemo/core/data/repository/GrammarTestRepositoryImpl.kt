package com.jian.nemo.core.data.repository

import android.content.Context
import kotlinx.serialization.json.Json
import com.jian.nemo.core.common.Result
import com.jian.nemo.core.data.mapper.GrammarTestQuestionMapper.toDomainModels
import com.jian.nemo.core.data.model.GrammarTestQuestionDto
import com.jian.nemo.core.domain.model.GrammarTestQuestion
import com.jian.nemo.core.domain.repository.GrammarTestRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class GrammarTestRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : GrammarTestRepository {

    // 创建一个本地 Json 实例，或者注入全局单例
    private val json = Json {
        ignoreUnknownKeys = true
    }

    override suspend fun loadQuestionsByLevel(level: String): Result<List<GrammarTestQuestion>> {
        return withContext(Dispatchers.IO) {
            try {
                val fileName = "grammar/questions/${level}.json"
                val inputStream = context.assets.open(fileName)
                val jsonString = inputStream.bufferedReader().use { it.readText() }

                val questionsDto = json.decodeFromString<List<GrammarTestQuestionDto>>(jsonString)

                val questions = questionsDto.toDomainModels()
                Result.Success(questions)
            } catch (e: Exception) {
                Result.Error(e)
            }
        }
    }
}
