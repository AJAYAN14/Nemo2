package com.jian.nemo.core.data.mapper

import com.jian.nemo.core.data.model.GrammarTestQuestionDto
import com.jian.nemo.core.domain.model.GrammarTestQuestion

object GrammarTestQuestionMapper {
    fun GrammarTestQuestionDto.toDomainModel(): GrammarTestQuestion {
        return GrammarTestQuestion(
            id = this.id,
            targetGrammarId = this.targetGrammarId,
            targetUsageIndex = this.targetUsageIndex,
            type = this.type,
            question = this.question,
            options = this.options,
            correctIndex = this.correctIndex,
            explanation = this.explanation
        )
    }

    fun List<GrammarTestQuestionDto>.toDomainModels(): List<GrammarTestQuestion> {
        return this.map { it.toDomainModel() }
    }
}
