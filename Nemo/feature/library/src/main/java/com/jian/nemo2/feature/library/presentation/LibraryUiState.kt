package com.jian.nemo2.feature.library.presentation

import com.jian.nemo2.core.domain.model.Grammar
import com.jian.nemo2.core.domain.model.Word

data class LibraryUiState(
    val searchQuery: String = "",
    val searchResultsWords: List<Word> = emptyList(),
    val searchResultsGrammars: List<Grammar> = emptyList(),
    val selectedTab: LibraryTab = LibraryTab.Words,
    val isLoading: Boolean = false,
    val error: String? = null
)

enum class LibraryTab {
    Words,
    Grammar
}
