package com.restguard.ui.checkin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.restguard.domain.model.CheckInTrigger
import com.restguard.domain.model.SubjectiveCheckIn
import com.restguard.domain.repository.CheckInRepository
import com.restguard.domain.service.PersonalizationService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

data class CheckInUiState(
    val energyLevel: Int = 3,
    val moodLevel: Int = 3,
    val note: String = "",
    val trigger: CheckInTrigger = CheckInTrigger.USER_INITIATED,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val recentCount: Int = 0,
)

@HiltViewModel
class CheckInViewModel @Inject constructor(
    private val checkInRepo: CheckInRepository,
    private val personalizationService: PersonalizationService,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CheckInUiState())
    val uiState: StateFlow<CheckInUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            _uiState.update { it.copy(recentCount = checkInRepo.count()) }
        }
    }

    fun setTrigger(trigger: CheckInTrigger) {
        _uiState.update { it.copy(trigger = trigger) }
    }

    fun setEnergyLevel(level: Int) {
        _uiState.update { it.copy(energyLevel = level.coerceIn(1, 5)) }
    }

    fun setMoodLevel(level: Int) {
        _uiState.update { it.copy(moodLevel = level.coerceIn(1, 5)) }
    }

    fun setNote(note: String) {
        _uiState.update { it.copy(note = note) }
    }

    fun submit() {
        val state = _uiState.value
        if (state.isSaving) return

        _uiState.update { it.copy(isSaving = true) }

        viewModelScope.launch {
            val checkIn = SubjectiveCheckIn(
                id = UUID.randomUUID().toString(),
                timestamp = Instant.now(),
                energyLevel = state.energyLevel,
                moodLevel = state.moodLevel,
                note = state.note.ifBlank { null },
                trigger = state.trigger,
            )

            checkInRepo.saveCheckIn(checkIn)

            // Trigger recalibration (respects its own cooldown)
            personalizationService.calibrateWeights()

            _uiState.update { it.copy(isSaving = false, isSaved = true) }
        }
    }
}
