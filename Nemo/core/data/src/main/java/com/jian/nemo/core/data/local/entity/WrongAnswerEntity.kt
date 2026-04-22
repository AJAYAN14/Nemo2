package com.jian.nemo.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 单词错题实体
 *
 * 对应表: wrong_answers
 * 用于记录用户在测试中做错的单词
 */
@Entity(
    tableName = "wrong_answers",
    foreignKeys = [
        ForeignKey(
            entity = WordEntity::class,
            parentColumns = ["id"],
            childColumns = ["word_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["word_id"])]
)
data class WrongAnswerEntity(
    @PrimaryKey
    val id: String = java.util.UUID.randomUUID().toString(),

    /**
     * 单词ID (外键)
     */
    @ColumnInfo(name = "word_id")
    val wordId: Long,

    /**
     * 测试模式
     * 使用 Constants.TestMode 中的值
     */
    @ColumnInfo(name = "test_mode")
    val testMode: String,

    /**
     * 用户答案
     */
    @ColumnInfo(name = "user_answer")
    val userAnswer: String,

    /**
     * 正确答案
     */
    @ColumnInfo(name = "correct_answer")
    val correctAnswer: String,


    /**
     * 是否已删除
     */
    @ColumnInfo(name = "is_deleted")
    val isDeleted: Boolean = false,

    /**
     * 删除时间戳
     */
    @ColumnInfo(name = "deleted_time")
    val deletedTime: Long = 0,

    /**
     * 记录创建/修改时间戳 (毫秒)
     */
    @ColumnInfo(name = "timestamp")
    val timestamp: Long = System.currentTimeMillis(),

    /**
     * 连续答对次数
     * 用于自动移除错题逻辑
     */
    @ColumnInfo(name = "consecutive_correct_count")
    val consecutiveCorrectCount: Int = 0
)
