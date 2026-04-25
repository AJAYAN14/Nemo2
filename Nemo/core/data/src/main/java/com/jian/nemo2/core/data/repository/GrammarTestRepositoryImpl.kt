package com.jian.nemo2.core.data.repository

import com.jian.nemo2.core.common.Result
import com.jian.nemo2.core.data.local.dao.GrammarQuestionDao
import com.jian.nemo2.core.data.mapper.GrammarTestQuestionMapper.entitiesToDomainModels
import com.jian.nemo2.core.domain.model.GrammarTestQuestion
import com.jian.nemo2.core.domain.repository.GrammarTestRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GrammarTestRepositoryImpl @Inject constructor(
    private val grammarQuestionDao: GrammarQuestionDao
) : GrammarTestRepository {

    override suspend fun loadQuestionsByLevel(level: String): Result<List<GrammarTestQuestion>> {
        return withContext(Dispatchers.IO) {
            try {
                // Supabase 中的 ID 格式为 GT_N1_xxx，我们按前缀过滤
                val prefix = "GT_${level.uppercase()}_"
                val entities = grammarQuestionDao.getByLevel(prefix)

                if (entities.isEmpty()) {
                    // 如果本地没数据，可能是还没同步
                    return@withContext Result.Error(Exception("No grammar questions found for level $level in local database. Please sync first."))
                }

                val questions = entities.entitiesToDomainModels()
                Result.Success(questions)
            } catch (e: Exception) {
                Result.Error(e)
            }
        }
    }
}
