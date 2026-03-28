package com.restguard.ui.reschedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.restguard.domain.model.RescheduleOption
import com.restguard.domain.repository.CalendarRepository
import com.restguard.domain.repository.RecommendationRepository
import com.restguard.domain.service.MessageDraft
import com.restguard.domain.service.MessagingEngine
import com.restguard.domain.service.ReschedulingEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RescheduleUiState(
    val isLoading: Boolean = true,
    val eventTitle: String? = null,
    val options: List<RescheduleOption> = emptyList(),
    val selectedOptionId: String? = null,
    val messageDraft: MessageDraft? = null,
    val isProcessing: Boolean = false,
    val isConfirmed: Boolean = false,
)

@HiltViewModel
class RescheduleViewModel @Inject constructor(
    private val reschedulingEngine: ReschedulingEngine,
    private val calendarRepo: CalendarRepository,
    private val recommendationRepo: RecommendationRepository,
    private val messagingEngine: MessagingEngine,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RescheduleUiState())
    val uiState: StateFlow<RescheduleUiState> = _uiState.asStateFlow()

    private var eventId: String = ""

    fun loadOptions(eventId: String) {
        this.eventId = eventId
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            val event = calendarRepo.getEventById(eventId)
            if (event == null) {
                _uiState.update { it.copy(isLoading = false) }
                return@launch
            }

            val options = reschedulingEngine.findAlternatives(event)
            recommendationRepo.saveRescheduleOptions(options)

            _uiState.update {
                it.copy(
                    isLoading = false,
                    eventTitle = event.title,
                    options = options,
                )
            }
        }
    }

    fun selectOption(optionId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(selectedOptionId = optionId) }

            val option = _uiState.value.options.find { it.id == optionId } ?: return@launch
            val event = calendarRepo.getEventById(eventId) ?: return@launch

            val draft = messagingEngine.draftRescheduleEmail(
                event = event,
                recipientName = event.attendees.firstOrNull()?.name,
                newSlot = option,
            )
            _uiState.update { it.copy(messageDraft = draft) }
        }
    }

    fun confirmReschedule() {
        viewModelScope.launch {
            val optionId = _uiState.value.selectedOptionId ?: return@launch
            val option = _uiState.value.options.find { it.id == optionId } ?: return@launch

            _uiState.update { it.copy(isProcessing = true) }

            // Update calendar event
            calendarRepo.updateEventTime(
                eventId = eventId,
                newStart = option.proposedStart,
                newEnd = option.proposedEnd,
            )

            _uiState.update { it.copy(isProcessing = false, isConfirmed = true) }
        }
    }
}
