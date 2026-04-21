package com.jian.nemo.core.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import com.jian.nemo.core.domain.model.GrammarQuestionType

@Serializable
data class GrammarTestQuestionDto(
    @SerialName("id")
    val id: String,
    @SerialName("targetGrammarId")
    val targetGrammarId: String,
    @SerialName("targetUsageIndex")
    val targetUsageIndex: Int,
    @SerialName("type")
    val type: GrammarQuestionType,
    @SerialName("question")
    val question: String,
    @SerialName("options")
    val options: List<String>,
    @SerialName("correctIndex")
    val correctIndex: Int,
    @SerialName("explanation")
    val explanation: String
)
