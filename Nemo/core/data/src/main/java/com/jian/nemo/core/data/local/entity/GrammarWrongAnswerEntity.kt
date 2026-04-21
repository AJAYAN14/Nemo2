package com.jian.nemo.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 语法错题实体
 *
 * 对应表: grammar_wrong_answers
 * 用于记录用户在测试中做错的语法
 */
@Entity(
    tableName = "grammar_wrong_answers",
    foreignKeys = [
        ForeignKey(
            entity = GrammarEntity::class,
            parentColumns = ["id"],
            childColumns = ["grammar_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["grammar_id"])]
)
data class GrammarWrongAnswerEntity(
    @PrimaryKey
    val id: String = java.util.UUID.randomUUID().toString(),

    /**
     * 语法ID (外键)
     */
    @ColumnInfo(name = "grammar_id")
    val grammarId: String,

    /**
     * 测试模式
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
     * 题目类型
     */
    @ColumnInfo(name = "question_type")
    val questionType: String = "multiple_choice",

    /**
     * 做错时的题干文本
     */
    @ColumnInfo(name = "question_text")
    val questionText: String = "",

    /**
     * 选项列表 (JSON)
     */
    @ColumnInfo(name = "options_json")
    val optionsJson: String = "[]",

    /**
     * 题目解析
     */
    @ColumnInfo(name = "explanation")
    val explanation: String? = null,


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
