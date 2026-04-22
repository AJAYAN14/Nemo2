package com.jian.nemo.core.data.mapper

import com.jian.nemo.core.data.local.entity.GrammarEntity
import com.jian.nemo.core.data.local.entity.GrammarExampleEntity
import com.jian.nemo.core.data.local.entity.GrammarUsageEntity
import com.jian.nemo.core.data.local.entity.WordEntity
import com.jian.nemo.core.domain.model.dto.GrammarDto
import com.jian.nemo.core.domain.model.dto.WordDto
import com.jian.nemo.core.domain.model.dto.GrammarTestQuestionDto
import com.jian.nemo.core.data.local.entity.GrammarQuestionEntity
import com.jian.nemo.core.domain.model.GrammarQuestionType

/**
 * 字典 DTO 到数据库 Entity 的映射器
 */

fun WordDto.toEntity() = WordEntity(
    id = this.id.toString(),
    japanese = this.japanese,
    hiragana = this.hiragana,
    chinese = this.chinese,
    level = this.level.uppercase(),
    pos = this.pos,
    example1 = this.example1,
    gloss1 = this.gloss1,
    example2 = this.example2,
    gloss2 = this.gloss2,
    example3 = this.example3,
    gloss3 = this.gloss3,
    isDelisted = this.isDelisted
)

fun GrammarDto.toGrammarEntity() = GrammarEntity(
    id = this.id.toString(),
    grammar = this.title,
    grammarLevel = this.level.uppercase(),
    isDelisted = this.isDelisted
)

fun GrammarDto.toUsageEntities() = this.content.mapIndexed { index, usage ->
    GrammarUsageEntity(
        grammarId = this.id.toString(),
        subtype = usage.subtype,
        connection = usage.connection,
        explanation = usage.explanation,
        notes = usage.notes,
        usageOrder = index
    )
}

fun GrammarDto.toExampleEntities(usageIds: List<Long>): List<GrammarExampleEntity> {
    val entities = mutableListOf<GrammarExampleEntity>()
    this.content.forEachIndexed { usageIndex, usage ->
        val usageId = usageIds.getOrNull(usageIndex) ?: return@forEachIndexed
        usage.examples.forEachIndexed { exampleIndex, example ->
            entities.add(
                GrammarExampleEntity(
                    usageId = usageId.toInt(),
                    sentence = example.sentence,
                    translation = example.translation,
                    source = example.source,
                    isDialog = example.isDialog,
                    exampleOrder = exampleIndex
                )
            )
        }
    }
    return entities
}

fun GrammarTestQuestionDto.toEntity() = GrammarQuestionEntity(
    id = this.id,
    targetGrammarId = this.targetGrammarId,
    targetUsageIndex = this.targetUsageIndex,
    type = this.type ?: GrammarQuestionType.CHOICE,
    question = this.question,
    options = this.options,
    correctIndex = this.correctIndex,
    explanation = this.explanation
)
