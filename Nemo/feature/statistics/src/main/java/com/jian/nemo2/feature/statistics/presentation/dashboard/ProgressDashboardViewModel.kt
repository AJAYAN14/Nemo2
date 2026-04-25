package com.jian.nemo2.feature.statistics.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jian.nemo2.core.domain.model.LearningStats
import com.jian.nemo2.core.domain.usecase.statistics.GetLearningStatsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProgressDashboardUiState(
    val stats: LearningStats? = null,
    val isLoading: Boolean = false,
    val error: String? = null
) {
    val totalProgress: Float
        get() {
            if (stats == null) return 0f
            val totalItems = stats.totalWords + stats.totalGrammars
            return if (totalItems > 0) stats.totalMastered.toFloat() / totalItems else 0f
        }
}

@HiltViewModel
class ProgressDashboardViewModel @Inject constructor(
    private val getLearningStatsUseCase: GetLearningStatsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProgressDashboardUiState())
    val uiState: StateFlow<ProgressDashboardUiState> = _uiState.asStateFlow()

    init {
        loadStats()
    }

    private fun loadStats() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            getLearningStatsUseCase().collect { stats ->
                _uiState.update {
                    it.copy(
                        stats = stats,
                        isLoading = false
                    )
                }
            }
        }
    }

}
