package com.jian.nemo.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * 统一用户学习进度实体 (镜像自 Supabase user_progress 表)
 * 遵循 rules.md: 3.A Strict Schema Alignment
 */
@Serializable
@Entity(
    tableName = "user_progress",
    indices = [
        Index(value = ["item_type", "item_id"]),
        Index(value = ["next_review"]),
        Index(value = ["state"])
    ]
)
data class UserProgressEntity(
    @PrimaryKey
    val id: String, // Supabase UUID

    @ColumnInfo(name = "user_id")
    val userId: String,

    @ColumnInfo(name = "item_type")
    val itemType: String, // 'word' | 'grammar'

    @ColumnInfo(name = "item_id")
    val itemId: Long,

    // ========== FSRS State (Calculated by Server RPC) ==========
    
    /** 记忆稳定性 (FSRS, 必须使用 Double 对齐 Web) */
    val stability: Double,

    /** 难度 (FSRS, 必须使用 Double 对齐 Web) */
    val difficulty: Double,

    @ColumnInfo(name = "elapsed_days")
    val elapsedDays: Int,

    @ColumnInfo(name = "scheduled_days")
    val scheduledDays: Int,

    /** 重复次数 */
    val reps: Int,

    /** 遗忘次数 */
    val lapses: Int,

    /** 状态: 0:New, 1:Learning, 2:Review, 3:Relearning */
    val state: Int,

    @ColumnInfo(name = "learning_step")
    val learningStep: Int,

    // ========== Dates (ISO String as per Web) ==========

    @ColumnInfo(name = "last_review")
    val lastReview: String? = null,

    @ColumnInfo(name = "next_review")
    val nextReview: String? = null,

    @ColumnInfo(name = "buried_until")
    val buriedUntil: Long = 0,

    @ColumnInfo(name = "is_favorite")
    val isFavorite: Boolean = false,

    val level: String,

    @ColumnInfo(name = "created_at")
    val createdAt: String,

    @ColumnInfo(name = "updated_at")
    val updatedAt: String? = null
)
