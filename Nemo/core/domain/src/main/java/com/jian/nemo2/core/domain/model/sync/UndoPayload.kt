package com.jian.nemo2.core.domain.model.sync

import kotlinx.serialization.Serializable

@Serializable
data class UndoPayload(
    val requestId: String,
    val lastReview: String?,
    val studyField: String?,
    val stability: Double,
    val difficulty: Double,
    val reps: Int,
    val lapses: Int,
    val state: Int,
    val learningStep: Int,
    val nextReview: String,
    val elapsedDays: Int,
    val scheduledDays: Int,
    val buriedUntil: Long
)
