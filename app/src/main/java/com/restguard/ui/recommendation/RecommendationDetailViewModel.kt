package com.restguard.ui.recommendation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.restguard.domain.model.*
import com.restguard.domain.repository.RecommendationRepository
import com.restguard.domain.service.MessageDraft
import com.restguard.domain.service.MessagingEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RecommendationDetailUiState(
    val recommendation: Recommendation? = null,
    val messageDraft: MessageDraft? = null,
    val isProcessing: Boolean = false,
    val isDone: Boolean = false,
)

@HiltViewModel
class RecommendationDetailViewModel @Inject constructor(
    private val recommendationRepo: RecommendationRepository,
    private val messagingEngine: MessagingEngine,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecommendationDetailUiState())
    val uiState: StateFlow<RecommendationDetailUiState> = _uiState.asStateFlow()

    fun load(recommendationId: String) {
        viewModelScope.launch {
            val recs = recommendationRepo.getActiveRecommendations()
            val rec = recs.find { it.id == recommendationId } ?: return@launch

            _uiState.update { it.copy(recommendation = rec) }

            // Pre-generate message draft if applicable
            if (rec.event != null && rec.type in listOf(
                    RecommendationType.SUGGEST_CANCEL,
                    RecommendationType.URGENT_SAME_DAY_INTERVENTION,
                )
            ) {
                val draft = messagingEngine.draftCancellationEmail(
                    event = rec.event,
                    recipientName = rec.event.attendees.firstOrNull()?.name,
                )
                _uiState.update { it.copy(messageDraft = draft) }
            }
        }
    }

    fun confirmCancel() {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true) }
            val rec = _uiState.value.recommendation ?: return@launch

            // Mark as accepted — actual calendar deletion and email sending
            // happens via intent in the UI layer (user must confirm)
            recommendationRepo.updateStatus(rec.id, RecommendationStatus.ACCEPTED)
            _uiState.update { it.copy(isProcessing = false, isDone = true) }
        }
    }

    fun acceptActivity() {
        viewModelScope.launch {
            val rec = _uiState.value.recommendation ?: return@launch
            recommendationRepo.updateStatus(rec.id, RecommendationStatus.ACCEPTED)
            _uiState.update { it.copy(isDone = true) }
        }
    }

    fun dismiss() {
        viewModelScope.launch {
            val rec = _uiState.value.recommendation ?: return@launch
            recommendationRepo.updateStatus(rec.id, RecommendationStatus.DISMISSED)
        }
    }
}
