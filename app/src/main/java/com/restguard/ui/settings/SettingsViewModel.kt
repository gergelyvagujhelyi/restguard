package com.restguard.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.restguard.data.preferences.UserPreferences
import com.restguard.domain.repository.CalendarRepository
import com.restguard.domain.service.DataManagementService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferences: UserPreferences,
    private val dataManagement: DataManagementService,
    private val calendarRepo: CalendarRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                preferences.isMonitoringPaused,
                preferences.isLlmEnabled,
                preferences.workStartHour,
                preferences.workEndHour,
                preferences.dataRetentionDays,
            ) { paused, llm, start, end, retention ->
                _uiState.value.copy(
                    monitoringPaused = paused,
                    llmEnabled = llm,
                    workStartHour = start,
                    workEndHour = end,
                    dataRetentionDays = retention,
                )
            }.collect { state ->
                _uiState.value = state
            }
        }

        viewModelScope.launch {
            preferences.selectedCalendarIds.collect { ids ->
                _uiState.update { it.copy(selectedCalendarIds = ids) }
            }
        }

        viewModelScope.launch {
            try {
                val calendars = calendarRepo.getAvailableCalendars()
                _uiState.update { it.copy(availableCalendars = calendars) }
            } catch (_: Exception) { }
        }
    }

    fun setMonitoringPaused(paused: Boolean) {
        viewModelScope.launch { preferences.setMonitoringPaused(paused) }
    }

    fun setLlmEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.setLlmEnabled(enabled) }
    }

    fun setWorkStart(hour: Int) {
        viewModelScope.launch {
            preferences.setWorkHours(hour, _uiState.value.workEndHour)
        }
    }

    fun setWorkEnd(hour: Int) {
        viewModelScope.launch {
            preferences.setWorkHours(_uiState.value.workStartHour, hour)
        }
    }

    fun setRetentionDays(days: Int) {
        viewModelScope.launch { preferences.setDataRetentionDays(days) }
    }

    fun setSelectedCalendars(ids: Set<String>?) {
        viewModelScope.launch { preferences.setSelectedCalendarIds(ids) }
    }

    fun toggleCalendar(calendarId: String, checked: Boolean) {
        viewModelScope.launch {
            val current = _uiState.value.selectedCalendarIds
            val allIds = _uiState.value.availableCalendars.map { it.id }.toSet()

            val newIds = if (current == null) {
                // Was "all" — unchecking one switches to explicit with all except that one
                if (checked) return@launch else allIds - calendarId
            } else {
                if (checked) current + calendarId
                else current - calendarId
            }

            // If all are selected, revert to null (meaning "all")
            val finalIds = if (newIds.isNotEmpty() && newIds.containsAll(allIds)) null else newIds
            preferences.setSelectedCalendarIds(finalIds)
        }
    }

    fun exportData() {
        if (_uiState.value.isExporting) return
        _uiState.update { it.copy(isExporting = true) }

        viewModelScope.launch {
            val json = dataManagement.exportToJson()
            _uiState.update { it.copy(isExporting = false, exportJson = json) }
        }
    }

    fun clearExportData() {
        _uiState.update { it.copy(exportJson = null) }
    }

    fun showDeleteConfirmation() {
        _uiState.update { it.copy(showDeleteConfirmation = true) }
    }

    fun dismissDeleteConfirmation() {
        _uiState.update { it.copy(showDeleteConfirmation = false) }
    }

    fun confirmDeleteAllData() {
        _uiState.update { it.copy(showDeleteConfirmation = false, isDeleting = true) }

        viewModelScope.launch {
            dataManagement.deleteAllData()
            _uiState.update { it.copy(isDeleting = false, dataDeleted = true) }
        }
    }
}
