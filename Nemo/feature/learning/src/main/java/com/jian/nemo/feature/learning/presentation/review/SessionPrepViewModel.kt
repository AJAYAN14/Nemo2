package com.jian.nemo.feature.learning.presentation.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jian.nemo.core.common.Result
import com.jian.nemo.core.domain.model.Grammar
import com.jian.nemo.core.domain.model.Word
import com.jian.nemo.core.domain.repository.SettingsRepository
import com.jian.nemo.core.domain.usecase.grammar.GetDueGrammarsUseCase
import com.jian.nemo.core.domain.usecase.word.GetDueWordsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 统一复习预览项
 */
sealed interface ReviewPreviewItem {
    data class WordItem(val word: Word) : ReviewPreviewItem
    data class GrammarItem(val grammar: Grammar) : ReviewPreviewItem
}

data class SessionPrepUiState(
    val isLoading: Boolean = true,
    val reviewItems: List<ReviewPreviewItem> = emptyList(), // 统一列表：包含 Word 和 Grammar
    val totalDueCount: Int = 0,
    val error: String? = null
)

@HiltViewModel
class SessionPrepViewModel @Inject constructor(
    private val getDueWordsUseCase: GetDueWordsUseCase,
    private val getDueGrammarsUseCase: GetDueGrammarsUseCase, // 新增 Grammar UC
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SessionPrepUiState())
    val uiState: StateFlow<SessionPrepUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            try {

                // 1. 获取复习词 (Due Words)
                val wordLevel = settingsRepository.preferredWordLevelFlow.first()
                val dueWordsResult = getDueWordsUseCase(wordLevel).first { it !is Result.Loading }
                val dueWords = if (dueWordsResult is Result.Success) {
                    dueWordsResult.data
                } else {
                    emptyList()
                }

                // 2. 获取复习语法 (Due Grammars)
                val grammarLevel = settingsRepository.preferredGrammarLevelFlow.first()
                val dueGrammarsResult = getDueGrammarsUseCase(grammarLevel).first { it !is Result.Loading }
                val dueGrammars = if (dueGrammarsResult is Result.Success) {
                    dueGrammarsResult.data
                } else {
                    emptyList()
                }

                // 4. 合并列表
                val combinedList = mutableListOf<ReviewPreviewItem>()
                combinedList.addAll(dueWords.map { ReviewPreviewItem.WordItem(it) })
                combinedList.addAll(dueGrammars.map { ReviewPreviewItem.GrammarItem(it) })

                // 可选：打乱顺序或按类型排序？目前暂不打乱，按自然顺序（先词后语法）

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        reviewItems = combinedList,
                        totalDueCount = combinedList.size
                    )
                }

            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = e.message)
                }
            }
        }
    }

}
