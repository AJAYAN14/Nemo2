package com.jian.nemo.core.domain.model

/**
 * 核心学习进度领域模型
 * 对应 Supabase user_progress 表
 */
data class UserProgress(
    val id: String, // UUID
    val userId: String,
    val itemType: String, // 'word' | 'grammar'
    val itemId: String,
    val stability: Double,
    val difficulty: Double,
    val elapsedDays: Int,
    val scheduledDays: Int,
    val reps: Int,
    val lapses: Int,
    val state: Int, // 0:New, 1:Learning, 2:Review, 3:Relearning, -1:Suspended/Leech
    val learningStep: Int,
    val lastReview: String?,
    val nextReview: String?,
    val buriedUntil: String?,
    val level: String,
    val createdAt: String
)
