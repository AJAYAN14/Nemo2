package com.jian.nemo2.core.domain.model

/**
 * 收藏题目领域模型
 */
data class FavoriteQuestion(
    val id: String,
    val grammarId: Long? = null,
    val jsonId: String? = null,
    val questionType: String,
    val questionText: String,
    val options: List<String>,
    val correctAnswer: String,
    val explanation: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
