package com.jian.nemo2.core.domain.model.dto

import com.jian.nemo2.core.domain.model.GrammarQuestionType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 语法测试题 DTO (用于 Supabase 序列化)
 */
@Serializable
data class GrammarTestQuestionDto(
    val id: String,

    @SerialName("target_grammar_id")
    val targetGrammarId: String,

    @SerialName("target_usage_index")
    val targetUsageIndex: Int,

    val type: GrammarQuestionType? = null,

    val question: String,

    val options: List<String>,

    @SerialName("correct_index")
    val correctIndex: Int,

    val explanation: String? = null
)
