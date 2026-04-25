package com.jian.nemo2.feature.collection.mistakes

import com.jian.nemo2.core.domain.model.WrongAnswer
import com.jian.nemo2.core.domain.model.GrammarWrongAnswer

/**
 * 错题本UI状态
 *
 * 支持单词和语法两种错题类型
 */
data class MistakesUiState(
    val wordWrongAnswers: List<WrongAnswer> = emptyList(),
    val grammarWrongAnswers: List<GrammarWrongAnswer> = emptyList(),
    val selectedTab: MistakeTab = MistakeTab.WORD,
    val wrongWordsCount: Int = 0,
    val wrongGrammarsCount: Int = 0,
    // [已废弃] 改用知识盲点率，不再需要历史总题数
    val totalQuestions: Int = 0,
    val correctAnswers: Int = 0,
    // [新增] 已学知识总量（单词+语法），用于计算盲点率
    val totalLearnedCount: Int = 0,
    val isLoading: Boolean = false,
    val error: String? = null
)

/**
 * 错题本Tab
 */
enum class MistakeTab {
    WORD,     // 单词错题
    GRAMMAR   // 语法错题
}
