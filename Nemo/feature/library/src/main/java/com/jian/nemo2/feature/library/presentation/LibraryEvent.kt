package com.jian.nemo2.feature.library.presentation

sealed interface LibraryEvent {
    data class SearchQueryChanged(val query: String) : LibraryEvent
    data class TabChanged(val tab: LibraryTab) : LibraryEvent
    data object ClearSearch : LibraryEvent
}
