package com.jian.nemo.core.data.mapper

import com.jian.nemo.core.data.local.entity.GrammarQuestionEntity
import com.jian.nemo.core.domain.model.dto.GrammarTestQuestionDto
import com.jian.nemo.core.domain.model.GrammarQuestionType
import com.jian.nemo.core.domain.model.GrammarTestQuestion

object GrammarTestQuestionMapper {
    fun GrammarTestQuestionDto.toDomainModel(): GrammarTestQuestion {
        return GrammarTestQuestion(
            id = this.id,
            targetGrammarId = this.targetGrammarId,
            targetUsageIndex = this.targetUsageIndex,
            type = this.type ?: GrammarQuestionType.CHOICE,
            question = this.question,
            options = this.options,
            correctIndex = this.correctIndex,
            explanation = this.explanation ?: ""
        )
    }

    fun GrammarQuestionEntity.toDomainModel(): GrammarTestQuestion {
        return GrammarTestQuestion(
            id = this.id,
            targetGrammarId = this.targetGrammarId,
            targetUsageIndex = this.targetUsageIndex,
            type = this.type,
            question = this.question,
            options = this.options,
            correctIndex = this.correctIndex,
            explanation = this.explanation ?: ""
        )
    }

    fun List<GrammarTestQuestionDto>.toDomainModels(): List<GrammarTestQuestion> {
        return this.map { it.toDomainModel() }
    }

    fun List<GrammarQuestionEntity>.entitiesToDomainModels(): List<GrammarTestQuestion> {
        return this.map { it.toDomainModel() }
    }
}
