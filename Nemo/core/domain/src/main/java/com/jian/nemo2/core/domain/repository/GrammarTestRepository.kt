package com.jian.nemo2.core.domain.repository

import com.jian.nemo2.core.domain.model.GrammarTestQuestion
import com.jian.nemo2.core.common.Result

interface GrammarTestRepository {
    suspend fun loadQuestionsByLevel(level: String): Result<List<GrammarTestQuestion>>
}
