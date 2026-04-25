package com.jian.nemo2.feature.library.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jian.nemo2.core.domain.usecase.library.SearchGrammarsUseCase
import com.jian.nemo2.core.domain.usecase.library.SearchWordsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(FlowPreview::class)
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val searchWordsUseCase: SearchWordsUseCase,
    private val searchGrammarsUseCase: SearchGrammarsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")

    init {
        // Setup search debounce
        _searchQuery
            .debounce(300)
            .distinctUntilChanged()
            .onEach { query ->
                performSearch(query)
            }
            .launchIn(viewModelScope)
    }

    fun onEvent(event: LibraryEvent) {
        when (event) {
            is LibraryEvent.SearchQueryChanged -> {
                _uiState.update { it.copy(searchQuery = event.query) }
                _searchQuery.value = event.query
            }
            is LibraryEvent.TabChanged -> {
                _uiState.update { it.copy(selectedTab = event.tab) }
                // Re-trigger search for the new tab if needed, or rely on state
                // Since lists are separate in UiState, we might not need to re-fetch immediately unless we optimize to fetch only current tab
                // For now, let's fetch both or fetch current based on tab?
                // Better to fetch based on tab to save resources.
                performSearch(_searchQuery.value)
            }
            is LibraryEvent.ClearSearch -> {
                _uiState.update { it.copy(searchQuery = "") }
                _searchQuery.value = ""
            }
        }
    }

    private fun performSearch(query: String) {
        if (query.isBlank()) {
            _uiState.update {
                it.copy(
                    searchResultsWords = emptyList(),
                    searchResultsGrammars = emptyList(),
                    isLoading = false
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            try {
                // Determine which search to run based on tab, or run both?
                // Running both is simpler for UI state management (switching tabs is instant).
                // Let's run both for now, assuming data set isn't massive yet.
                // Or optimization: only run the one for current tab.

                // Let's launch both.
                launch {
                    searchWordsUseCase(query).collect { words ->
                        _uiState.update { it.copy(searchResultsWords = words) }
                    }
                }

                launch {
                    searchGrammarsUseCase(query).collect { grammars ->
                        _uiState.update { it.copy(searchResultsGrammars = grammars) }
                    }
                }
                // Note: The above launches will keep collecting if the database changes.
                // However, we want to update isLoading.
                // Flow collection is continuous.

                // Simplified approach for isLoading: just set it to false after launching?
                // Since it's a Flow, it emits immediately locally.

                _uiState.update { it.copy(isLoading = false) }

            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message
                    )
                }
            }
        }
    }
}
