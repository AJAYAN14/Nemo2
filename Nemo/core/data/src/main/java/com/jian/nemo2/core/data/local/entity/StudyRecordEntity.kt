package com.jian.nemo2.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

/**
 * 学习记录实体
 * 用于记录用户每日的学习情况
 */
@Serializable
@Entity(tableName = "study_records")
data class StudyRecordEntity(
    /**
     * 记录 ID (Supabase UUID)
     */
    @PrimaryKey
    val id: String = "",

    @SerialName("user_id")
    @ColumnInfo(name = "user_id")
    val userId: String = "",

    /**
     * 学习日期 (Epoch Day)
     * 每天只有一条记录
     */
    val date: Long = 0,

    /**
     * 今日学习的单词数
     */
    @SerialName("learned_words")
    @ColumnInfo(name = "learned_words")
    val learnedWords: Int = 0,

    /**
     * 今日学习的语法数
     */
    @SerialName("learned_grammars")
    @ColumnInfo(name = "learned_grammars")
    val learnedGrammars: Int = 0,

    /**
     * 今日复习的单词数
     */
    @SerialName("reviewed_words")
    @ColumnInfo(name = "reviewed_words")
    val reviewedWords: Int = 0,

    /**
     * 今日复习的语法数
     */
    @SerialName("reviewed_grammars")
    @ColumnInfo(name = "reviewed_grammars")
    val reviewedGrammars: Int = 0,

    /**
     * 今日跳过的单词数
     */
    @SerialName("skipped_words")
    @ColumnInfo(name = "skipped_words")
    val skippedWords: Int = 0,

    /**
     * 今日跳过的语法数
     */
    @SerialName("skipped_grammars")
    @ColumnInfo(name = "skipped_grammars")
    val skippedGrammars: Int = 0,

    /**
     * 今日测试次数
     */
    @SerialName("test_count")
    @ColumnInfo(name = "test_count")
    val testCount: Int = 0,

    /**
     * 是否已删除
     */
    @SerialName("is_deleted")
    @ColumnInfo(name = "is_deleted")
    val isDeleted: Boolean = false,

    /**
     * 删除时间戳
     */
    @SerialName("deleted_time")
    @ColumnInfo(name = "deleted_time")
    val deletedTime: Long = 0,

    /**
     * 记录创建/修改时间戳 (毫秒)
     */
    @ColumnInfo(name = "timestamp")
    val timestamp: Long = System.currentTimeMillis(),

    @SerialName("updated_at")
    @ColumnInfo(name = "updated_at")
    val updatedAt: String? = null
)
