package com.restguard.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.restguard.domain.model.*
import com.restguard.domain.repository.RecommendationRepository
import com.restguard.domain.service.RecommendationEngine
import com.restguard.domain.service.StressScoringService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DashboardUiState(
    val isLoading: Boolean = true,
    val currentStress: StressSample? = null,
    val stressLevel: StressLevel = StressLevel.LOW,
    val predictions: List<StressPrediction> = emptyList(),
    val recommendations: List<Recommendation> = emptyList(),
    val error: String? = null,
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val stressScoringService: StressScoringService,
    private val recommendationEngine: RecommendationEngine,
    private val recommendationRepo: RecommendationRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        refresh()
        observeRecommendations()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val stress = stressScoringService.computeCurrentStress()
                val level = stressScoringService.classifyStress(stress.score)
                val predictions = stressScoringService.predictStress(3)
                val recommendations = recommendationEngine.generateRecommendations(stress, level, predictions)

                recommendationRepo.saveRecommendations(recommendations)

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        currentStress = stress,
                        stressLevel = level,
                        predictions = predictions,
                        recommendations = recommendations,
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = e.message ?: "Unknown error")
                }
            }
        }
    }

    fun dismissRecommendation(id: String) {
        viewModelScope.launch {
            recommendationRepo.updateStatus(id, RecommendationStatus.DISMISSED)
        }
    }

    private fun observeRecommendations() {
        viewModelScope.launch {
            recommendationRepo.observeActiveRecommendations().collect { recs ->
                _uiState.update { it.copy(recommendations = recs) }
            }
        }
    }
}
