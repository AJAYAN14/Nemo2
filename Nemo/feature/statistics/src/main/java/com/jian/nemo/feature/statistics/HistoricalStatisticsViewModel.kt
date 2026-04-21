package com.jian.nemo.feature.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jian.nemo.core.domain.usecase.statistics.GetAllLearnedGrammarsUseCase
import com.jian.nemo.core.domain.usecase.statistics.GetAllLearnedWordsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.jian.nemo.feature.statistics.model.StatisticDisplayItem

/**
 * 历史统计界面 UI 状态
 */
data class HistoricalStatisticsUiState(
    val learnedWords: List<StatisticDisplayItem> = emptyList(),
    val learnedGrammars: List<StatisticDisplayItem> = emptyList(),
    val isLoading: Boolean = true
)

/**
 * 历史统计界面 ViewModel
 *
 * 负责获取和展示所有已学习的单词和语法
 */
@HiltViewModel
class HistoricalStatisticsViewModel @Inject constructor(
    private val getAllLearnedWordsUseCase: GetAllLearnedWordsUseCase,
    private val getAllLearnedGrammarsUseCase: GetAllLearnedGrammarsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoricalStatisticsUiState())
    val uiState: StateFlow<HistoricalStatisticsUiState> = _uiState.asStateFlow()

    init {
        loadHistoricalStatistics()
    }

    private fun loadHistoricalStatistics() {
        viewModelScope.launch {
            combine(
                getAllLearnedWordsUseCase(),
                getAllLearnedGrammarsUseCase()
            ) { words, grammars ->
                // 数据转换
                val wordItems = words.map { word ->
                    StatisticDisplayItem(
                        id = word.id,
                        japanese = word.japanese,
                        hiragana = word.hiragana,
                        chinese = word.chinese,
                        level = word.level.uppercase()
                    )
                }

                val grammarItems = grammars.map { grammar ->
                    StatisticDisplayItem(
                        id = grammar.id,
                        japanese = grammar.grammar,
                        hiragana = grammar.getFirstConjunction() ?: "",
                        chinese = grammar.getFirstExplanation(),
                        level = grammar.grammarLevel.uppercase()
                    )
                }

                Pair(wordItems, grammarItems)
            }.collect { (wordItems, grammarItems) ->
                _uiState.update {
                    it.copy(
                        learnedWords = wordItems,
                        learnedGrammars = grammarItems,
                        isLoading = false
                    )
                }
            }
        }
    }
}
