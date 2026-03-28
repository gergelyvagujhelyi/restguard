package com.restguard.ui.onboarding

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.restguard.data.preferences.UserPreferences
import com.restguard.ui.common.checkHealthConnectPermissions
import com.restguard.ui.common.checkPermissions
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OnboardingUiState(
    val healthGranted: Boolean = false,
    val calendarGranted: Boolean = false,
    val notificationGranted: Boolean = false,
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val preferences: UserPreferences,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    init {
        refreshPermissionState()
    }

    /**
     * Refresh permission state from the system.
     * Call this after returning from a permission request.
     */
    fun refreshPermissionState() {
        val current = checkPermissions(context)
        _uiState.update {
            it.copy(
                calendarGranted = current.hasCalendar,
                notificationGranted = current.hasNotification,
            )
        }
        // Health Connect requires a suspend call to check actual granted permissions
        viewModelScope.launch {
            val healthGranted = checkHealthConnectPermissions(context)
            _uiState.update { it.copy(healthGranted = healthGranted) }
        }
    }

    /**
     * Called when a permission is requested.
     * The actual request is launched from the Composable via permission launchers.
     * This records the intent — the actual grant result is picked up by refreshPermissionState().
     */
    fun requestPermission(type: PermissionType) {
        // The Composable layer handles the actual permission dialog launch.
        // This method is kept as a hook for analytics or state tracking.
    }

    /**
     * Called from the Composable after a permission result is received.
     */
    fun onPermissionResult(type: PermissionType, granted: Boolean) {
        viewModelScope.launch {
            when (type) {
                PermissionType.HEALTH -> {
                    _uiState.update { it.copy(healthGranted = granted) }
                    preferences.setPermissionGranted(health = granted)
                }
                PermissionType.CALENDAR -> {
                    _uiState.update { it.copy(calendarGranted = granted) }
                    preferences.setPermissionGranted(calendar = granted)
                }
                PermissionType.NOTIFICATIONS -> {
                    _uiState.update { it.copy(notificationGranted = granted) }
                    preferences.setPermissionGranted(notification = granted)
                }
                PermissionType.CONTACTS -> {
                    // Handled lazily when contact picker is opened
                }
            }
        }
    }

    fun completeOnboarding() {
        viewModelScope.launch {
            preferences.setOnboardingComplete(true)
        }
    }
}
