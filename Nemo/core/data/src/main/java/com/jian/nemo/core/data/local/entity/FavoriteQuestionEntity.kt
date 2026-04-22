package com.jian.nemo.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 收藏题目实体
 *
 * 用于存储用户收藏的特定测试题目（题干、选项、答案快照）
 */
@Entity(tableName = "favorite_questions")
data class FavoriteQuestionEntity(
    @PrimaryKey
    val id: String = java.util.UUID.randomUUID().toString(),

    /**
     * 关联的语法ID (可选)
     */
    @ColumnInfo(name = "grammar_id")
    val grammarId: Long? = null,

    /**
     * JSON题目ID (可选，对应 GrammarTestQuestion.id)
     */
    @ColumnInfo(name = "json_id")
    val jsonId: String? = null,

    /**
     * 题型 (e.g., "multiple_choice")
     */
    @ColumnInfo(name = "question_type")
    val questionType: String,

    /**
     * 题目文本
     */
    @ColumnInfo(name = "question_text")
    val questionText: String,

    /**
     * 选项列表 (以JSON字符串存储)
     */
    @ColumnInfo(name = "options_json")
    val optionsJson: String,

    /**
     * 正确答案
     */
    @ColumnInfo(name = "correct_answer")
    val correctAnswer: String,

    /**
     * 解释/解析
     */
    val explanation: String? = null,

    /**
     * 收藏时间戳
     */
    val timestamp: Long = System.currentTimeMillis()
)
