package com.restguard.ui.onboarding

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.restguard.data.auth.GoogleAuthManager
import com.restguard.data.preferences.UserPreferences
import com.restguard.domain.model.CalendarInfo
import com.restguard.domain.repository.CalendarRepository
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
    val systemCalendars: List<CalendarInfo> = emptyList(),
    val googleAccounts: Set<String> = emptySet(),
    val googleSignInError: String? = null,
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val preferences: UserPreferences,
    @ApplicationContext private val context: Context,
    private val calendarRepo: CalendarRepository,
    val googleAuthManager: GoogleAuthManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    init {
        refreshPermissionState()
        viewModelScope.launch {
            googleAuthManager.accounts.collect { accounts ->
                _uiState.update { it.copy(googleAccounts = accounts) }
            }
        }
    }

    fun refreshPermissionState() {
        val current = checkPermissions(context)
        _uiState.update {
            it.copy(
                calendarGranted = current.hasCalendar,
                notificationGranted = current.hasNotification,
            )
        }
        viewModelScope.launch {
            val healthGranted = checkHealthConnectPermissions(context)
            _uiState.update { it.copy(healthGranted = healthGranted) }
        }
    }

    fun loadSystemCalendars() {
        viewModelScope.launch {
            try {
                val calendars = calendarRepo.getAvailableCalendars()
                _uiState.update { it.copy(systemCalendars = calendars) }
            } catch (_: Exception) { }
        }
    }

    fun getGoogleSignInIntent(): Intent = googleAuthManager.getSignInIntent()

    fun onGoogleSignInResult(intent: Intent?) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(googleSignInError = null) }
                val task = com.google.android.gms.auth.api.signin.GoogleSignIn
                    .getSignedInAccountFromIntent(intent)
                val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
                googleAuthManager.handleSignInResult(account)
                loadSystemCalendars()
            } catch (e: Exception) {
                val code = (e as? com.google.android.gms.common.api.ApiException)?.statusCode
                _uiState.update {
                    it.copy(googleSignInError = "Sign-in failed: ${code ?: e.message}")
                }
            }
        }
    }

    fun requestPermission(type: PermissionType) { }

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
                    if (granted) loadSystemCalendars()
                }
                PermissionType.NOTIFICATIONS -> {
                    _uiState.update { it.copy(notificationGranted = granted) }
                    preferences.setPermissionGranted(notification = granted)
                }
                PermissionType.CONTACTS -> { }
            }
        }
    }

    fun completeOnboarding() {
        viewModelScope.launch {
            preferences.setOnboardingComplete(true)
        }
    }
}
