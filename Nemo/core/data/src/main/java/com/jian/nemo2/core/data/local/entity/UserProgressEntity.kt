package com.jian.nemo2.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

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
    val id: String = "", // Supabase UUID

    @SerialName("user_id")
    @ColumnInfo(name = "user_id")
    val userId: String = "",

    @SerialName("item_type")
    @ColumnInfo(name = "item_type")
    val itemType: String = "", // 'word' | 'grammar'

    @SerialName("item_id")
    @ColumnInfo(name = "item_id")
    val itemId: Long = 0,

    // ========== FSRS State (Calculated by Server RPC) ==========

    /** 记忆稳定性 (FSRS, 必须使用 Double 对齐 Web) */
    val stability: Double = 0.0,

    /** 难度 (FSRS, 必须使用 Double 对齐 Web) */
    val difficulty: Double = 0.0,

    @SerialName("elapsed_days")
    @ColumnInfo(name = "elapsed_days")
    val elapsedDays: Int = 0,

    @SerialName("scheduled_days")
    @ColumnInfo(name = "scheduled_days")
    val scheduledDays: Int = 0,

    /** 重复次数 */
    val reps: Int = 0,

    /** 遗忘次数 */
    val lapses: Int = 0,

    /** 状态: 0:New, 1:Learning, 2:Review, 3:Relearning */
    val state: Int = 0,

    @SerialName("learning_step")
    @ColumnInfo(name = "learning_step")
    val learningStep: Int = 0,

    // ========== Dates (ISO String as per Web) ==========

    @SerialName("last_review")
    @ColumnInfo(name = "last_review")
    val lastReview: String? = null,

    @SerialName("next_review")
    @ColumnInfo(name = "next_review")
    val nextReview: String? = null,

    @SerialName("buried_until")
    @ColumnInfo(name = "buried_until")
    val buriedUntil: Long = 0,

    @SerialName("is_favorite")
    @ColumnInfo(name = "is_favorite")
    val isFavorite: Boolean = false,

    val level: String = "",

    @SerialName("created_at")
    @ColumnInfo(name = "created_at")
    val createdAt: String = "",

    @SerialName("updated_at")
    @ColumnInfo(name = "updated_at")
    val updatedAt: String? = null
)
