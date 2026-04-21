package com.jian.nemo.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 测试记录实体
 *
 * 对应表: test_records
 * 用于记录每次测试的成绩
 */
@Entity(tableName = "test_records")
data class TestRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    /**
     * 测试日期 (Epoch Day)
     */
    val date: Long,

    /**
     * 总题数
     */
    @ColumnInfo(name = "total_questions")
    val totalQuestions: Int,

    /**
     * 正确题数
     */
    @ColumnInfo(name = "correct_answers")
    val correctAnswers: Int,

    /**
     * 测试模式
     * 使用 Constants.TestMode 中的值
     */
    @ColumnInfo(name = "test_mode")
    val testMode: String,

    /**
     * 记录唯一标识 (用于跨设备同步)
     */
    val uuid: String = java.util.UUID.randomUUID().toString(),

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
    val timestamp: Long = System.currentTimeMillis()
)
