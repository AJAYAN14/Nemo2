package com.jian.nemo.feature.statistics.model

/**
 * 统计显示项数据模型
 */
data class StatisticDisplayItem(
    val id: Long,
    val japanese: String,
    val hiragana: String,
    val chinese: String,
    val level: String,
    val source: StatisticSource = StatisticSource.LEARNED
)

enum class StatisticSource {
    LEARNED,
    REVIEWED
}
