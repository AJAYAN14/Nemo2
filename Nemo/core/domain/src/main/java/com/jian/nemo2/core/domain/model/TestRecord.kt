package com.jian.nemo2.core.domain.model

/**
 * 测试记录
 *
 * 对应: TestRecordEntity
 * 参考: 旧项目 TestRecord
 */
data class TestRecord(
    val id: String = java.util.UUID.randomUUID().toString(),
    val date: Long, // 测试日期（以天为单位）
    val totalQuestions: Int, // 总题目数
    val correctAnswers: Int, // 正确题目数
    val testMode: String, // 测试模式
    val timestamp: Long = System.currentTimeMillis() // 记录时间
) {
    /** 正确率 (0.0 - 1.0) */
    val accuracy: Float
        get() = if (totalQuestions > 0) correctAnswers.toFloat() / totalQuestions else 0f
}
