package com.jian.nemo.core.domain.model

/**
 * 远程学习会话模型，用于双端同步队列一致性
 */
data class LearningSession(
    val id: String = "",
    val userId: String = "",
    val itemType: String,
    val level: String,
    val itemIds: List<Long>,
    val currentIndex: Int,
    val steps: Map<String, Int> = emptyMap(),
    val waitingUntil: Long = 0L,
    val updatedAt: Long = 0L
)
