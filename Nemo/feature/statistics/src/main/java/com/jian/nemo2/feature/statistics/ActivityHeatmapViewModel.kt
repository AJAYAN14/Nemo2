package com.jian.nemo2.feature.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jian.nemo2.core.common.util.DateTimeUtils
import com.jian.nemo2.core.domain.usecase.statistics.GetHeatmapDataUseCase
import com.jian.nemo2.core.domain.usecase.statistics.HeatmapDay
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ActivityHeatmapUiState(
    val heatmapData: List<HeatmapDay> = emptyList(),
    val streak: Int = 0,
    val longestStreak: Int = 0,
    val totalActiveDays: Int = 0,
    val bestDayCount: Int = 0,
    val bestDayDate: Long = 0,
    val dailyAverage: Int = 0,
    val todayCount: Int = 0,
    val isLoading: Boolean = true
)

@HiltViewModel
class ActivityHeatmapViewModel @Inject constructor(
    private val getHeatmapDataUseCase: GetHeatmapDataUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(ActivityHeatmapUiState())
    val uiState: StateFlow<ActivityHeatmapUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            getHeatmapDataUseCase().collect { heatmap ->
                // Calculate Rich Stats
                val activeDays = heatmap.filter { it.count > 0 }
                val totalActiveDays = activeDays.size
                val totalCount = activeDays.sumOf { it.count }
                val dailyAverage = if (totalActiveDays > 0) totalCount / totalActiveDays else 0

                val bestDay = activeDays.maxByOrNull { it.count }
                val bestDayCount = bestDay?.count ?: 0
                val bestDayDate = bestDay?.date ?: 0L

                // Calculate Streaks
                val sortedActiveDates = activeDays.map { it.date }.sorted()

                var currentStreak = 0
                var maxStreak = 0
                var tempStreak = 0
                var lastDate = -1L

                for (date in sortedActiveDates) {
                    if (lastDate == -1L) {
                        tempStreak = 1
                    } else if (date == lastDate + 1) {
                        tempStreak++
                    } else {
                         maxStreak = maxOf(maxStreak, tempStreak)
                         tempStreak = 1
                    }
                    lastDate = date
                }
                maxStreak = maxOf(maxStreak, tempStreak)

                // Check if current streak is active
                val todayEpoch = DateTimeUtils.timestampToEpochDay(DateTimeUtils.getCurrentCompensatedMillis())
                val todayCount = heatmap.find { it.date == todayEpoch }?.count ?: 0
                val isTodayActive = sortedActiveDates.contains(todayEpoch)
                val isYesterdayActive = sortedActiveDates.contains(todayEpoch - 1)

                if (isTodayActive) {
                    var streak = 0
                    var checkDate = todayEpoch
                    while (sortedActiveDates.contains(checkDate)) {
                        streak++
                        checkDate--
                    }
                    currentStreak = streak
                } else if (isYesterdayActive) {
                     // Count backwards from yesterday
                    var streak = 0
                    var checkDate = todayEpoch - 1
                    while (sortedActiveDates.contains(checkDate)) {
                        streak++
                        checkDate--
                    }
                    currentStreak = streak
                } else {
                    currentStreak = 0
                }

                _uiState.update {
                    it.copy(
                        heatmapData = heatmap,
                        streak = currentStreak,
                        longestStreak = maxStreak,
                        totalActiveDays = totalActiveDays,
                        bestDayCount = bestDayCount,
                        bestDayDate = bestDayDate,
                        dailyAverage = dailyAverage,
                        todayCount = todayCount,
                        isLoading = false
                    )
                }
            }
        }
    }
}
