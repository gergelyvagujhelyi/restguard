package com.restguard.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.restguard.domain.repository.StressRepository
import com.restguard.domain.service.HistoricalLearningService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val stressRepo: StressRepository,
    private val historicalLearning: HistoricalLearningService,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            val now = Instant.now()
            val weekAgo = now.minus(Duration.ofDays(7))
            val samples = stressRepo.getStressSamples(weekAgo, now)
            val insights = historicalLearning.getMeetingTypeInsights()

            val avgScore = if (samples.isNotEmpty()) {
                samples.map { it.score }.average().toInt()
            } else 0

            // Simple trend: compare first half avg to second half avg
            val trend = if (samples.size >= 4) {
                val mid = samples.size / 2
                val firstHalf = samples.subList(0, mid).map { it.score }.average()
                val secondHalf = samples.subList(mid, samples.size).map { it.score }.average()
                when {
                    secondHalf < firstHalf - 5 -> "improving"
                    secondHalf > firstHalf + 5 -> "worsening"
                    else -> "stable"
                }
            } else "stable"

            _uiState.update {
                it.copy(
                    isLoading = false,
                    recentSamples = samples,
                    meetingInsights = insights,
                    averageScore = avgScore,
                    trend = trend,
                )
            }
        }
    }
}
