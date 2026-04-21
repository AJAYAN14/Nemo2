package com.jian.nemo.core.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Supabase RPC fn_process_review_atomic 参数
 * 严格对齐 Web 端 studyService.ts 中的 rpcParams 结构
 */
@Serializable
data class ProcessReviewRpcParams(
    @SerialName("p_user_id") val userId: String,
    @SerialName("p_progress_id") val progressId: String,
    @SerialName("p_item_type") val itemType: String,
    @SerialName("p_item_id") val itemId: Int,
    @SerialName("p_rating") val rating: Int,
    
    // Optimistic Concurrency Control (OCC) - Prev states
    @SerialName("p_prev_stability") val prevStability: Double,
    @SerialName("p_prev_difficulty") val prevDifficulty: Double,
    @SerialName("p_prev_state") val prevState: Int,
    @SerialName("p_prev_learning_step") val prevLearningStep: Int,
    @SerialName("p_prev_buried_until") val prevBuriedUntil: String?,
    
    // Calculated Next states
    @SerialName("p_next_stability") val nextStability: Double,
    @SerialName("p_next_difficulty") val nextDifficulty: Double,
    @SerialName("p_next_elapsed_days") val nextElapsedDays: Int,
    @SerialName("p_next_scheduled_days") val nextScheduledDays: Int,
    @SerialName("p_next_reps") val nextReps: Int,
    @SerialName("p_next_lapses") val nextLapses: Int,
    @SerialName("p_next_state") val nextState: Int,
    @SerialName("p_next_learning_step") val nextLearningStep: Int,
    @SerialName("p_next_last_review") val nextLastReview: String?,
    @SerialName("p_next_review") val nextReview: String?,
    @SerialName("p_next_buried_until") val nextBuriedUntil: String?,
    
    // Statistics & Sync
    @SerialName("p_epoch_day") val epochDay: Int?,
    @SerialName("p_study_field") val studyField: String?,
    @SerialName("p_study_delta") val studyDelta: Int,
    @SerialName("p_request_id") val requestId: String,
    @SerialName("p_expected_last_review") val expectedLastReview: String?
)
