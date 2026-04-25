package com.jian.nemo2.feature.library.presentation.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jian.nemo2.core.domain.model.Word
import com.jian.nemo2.core.domain.repository.WordRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject

data class WordListUiState(
    val isLoading: Boolean = true,
    val wordsByLevel: Map<String, List<Word>> = emptyMap(),
    val error: String? = null,
    val searchQuery: String = ""
)

@HiltViewModel
class WordListViewModel @Inject constructor(
    private val wordRepository: WordRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")

    // 所有单词聚合 Flow
    private val allWordsFlow = combine(
        wordRepository.getAllWordsByLevel("N1"),
        wordRepository.getAllWordsByLevel("N2"),
        wordRepository.getAllWordsByLevel("N3"),
        wordRepository.getAllWordsByLevel("N4"),
        wordRepository.getAllWordsByLevel("N5")
    ) { n1, n2, n3, n4, n5 ->
        n1 + n2 + n3 + n4 + n5
    }

    val uiState: StateFlow<WordListUiState> = combine(
        allWordsFlow,
        _searchQuery
    ) { allWords, query ->
        // Perform filtering on IO thread via flowOn below
        val filteredList = if (query.isBlank()) {
            allWords
        } else {
            allWords.filter { w ->
                w.japanese.contains(query, ignoreCase = true) ||
                w.hiragana.contains(query, ignoreCase = true) ||
                w.chinese.contains(query, ignoreCase = true)
            }
        }

        val grouped = filteredList.groupBy { it.level }
        // Optional: Sort keys if needed, but Map keeps insertion order if LinkedHashMap (default groupBy)
        // Usually we want N1..N5 or N5..N1. The repository calls order suggests N1..N5 but map keys might be anything.
        // Let's rely on sorted keys in UI or ensure sorted map here.
        // For now, simple grouping.

        WordListUiState(
            isLoading = false,
            wordsByLevel = grouped,
            searchQuery = query,
            error = null
        )
    }
    .flowOn(Dispatchers.Default) // Move computation to Default dispatcher
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = WordListUiState(isLoading = true)
    )

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }
}
