package com.jian.nemo.core.data.mapper

import com.jian.nemo.core.data.local.entity.GrammarWrongAnswerEntity
import com.jian.nemo.core.domain.model.Grammar
import com.jian.nemo.core.domain.model.GrammarWrongAnswer
import org.json.JSONArray

/**
 * GrammarWrongAnswer Mapper
 *
 * Entity ↔ Domain Model 转换
 */

/**
 * Entity → Domain Model
 * @param grammar 关联的语法信息（需要外部提供）
 */
fun GrammarWrongAnswerEntity.toDomainModel(grammar: Grammar? = null): GrammarWrongAnswer {
    return GrammarWrongAnswer(
        id = id,
        grammarId = grammarId,
        grammar = grammar,
        testMode = testMode,
        userAnswer = userAnswer,
        correctAnswer = correctAnswer,
        questionType = questionType,
        questionText = questionText,
        options = parseJsonArray(optionsJson),
        explanation = explanation,
        timestamp = timestamp,
        consecutiveCorrectCount = consecutiveCorrectCount
    )
}

/**
 * Domain Model → Entity
 */
fun GrammarWrongAnswer.toEntity(): GrammarWrongAnswerEntity {
    return GrammarWrongAnswerEntity(
        id = id,
        grammarId = grammarId,
        testMode = testMode,
        userAnswer = userAnswer,
        correctAnswer = correctAnswer,
        questionType = questionType,
        questionText = questionText,
        optionsJson = toJsonArray(options),
        explanation = explanation,
        timestamp = timestamp,
        consecutiveCorrectCount = consecutiveCorrectCount
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


