package com.restguard.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "restguard_prefs")

/**
 * User preferences backed by DataStore.
 */
class UserPreferences(private val context: Context) {

    companion object {
        // Onboarding
        val KEY_ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val KEY_HEALTH_PERMISSION_GRANTED = booleanPreferencesKey("health_permission_granted")
        val KEY_CALENDAR_PERMISSION_GRANTED = booleanPreferencesKey("calendar_permission_granted")
        val KEY_NOTIFICATION_PERMISSION_GRANTED = booleanPreferencesKey("notification_permission_granted")

        // Stress settings
        val KEY_EXTREME_THRESHOLD = intPreferencesKey("extreme_threshold")
        val KEY_HIGH_THRESHOLD = intPreferencesKey("high_threshold")
        val KEY_MODERATE_THRESHOLD = intPreferencesKey("moderate_threshold")

        // Schedule settings
        val KEY_WORK_START_HOUR = intPreferencesKey("work_start_hour")
        val KEY_WORK_END_HOUR = intPreferencesKey("work_end_hour")
        val KEY_INCLUDE_WEEKENDS = booleanPreferencesKey("include_weekends")
        val KEY_BUFFER_MINUTES = intPreferencesKey("buffer_minutes")

        // Feature toggles
        val KEY_LLM_ENABLED = booleanPreferencesKey("llm_enabled")
        val KEY_MONITORING_PAUSED = booleanPreferencesKey("monitoring_paused")

        // Privacy
        val KEY_DATA_RETENTION_DAYS = intPreferencesKey("data_retention_days")

        // Calendar selection (comma-separated IDs; empty = all)
        val KEY_SELECTED_CALENDAR_IDS = stringPreferencesKey("selected_calendar_ids")
        private const val NONE_SENTINEL = "__none__"
    }

    val isOnboardingComplete: Flow<Boolean> = context.dataStore.data
        .map { it[KEY_ONBOARDING_COMPLETE] ?: false }

    val isMonitoringPaused: Flow<Boolean> = context.dataStore.data
        .map { it[KEY_MONITORING_PAUSED] ?: false }

    val isLlmEnabled: Flow<Boolean> = context.dataStore.data
        .map { it[KEY_LLM_ENABLED] ?: true }

    val workStartHour: Flow<Int> = context.dataStore.data
        .map { it[KEY_WORK_START_HOUR] ?: 9 }

    val workEndHour: Flow<Int> = context.dataStore.data
        .map { it[KEY_WORK_END_HOUR] ?: 17 }

    val dataRetentionDays: Flow<Int> = context.dataStore.data
        .map { it[KEY_DATA_RETENTION_DAYS] ?: 90 }

    val extremeThreshold: Flow<Int> = context.dataStore.data
        .map { it[KEY_EXTREME_THRESHOLD] ?: 80 }

    /** null = all calendars (default); empty set = none selected; non-empty = only these */
    val selectedCalendarIds: Flow<Set<String>?> = context.dataStore.data
        .map { prefs ->
            val raw = prefs[KEY_SELECTED_CALENDAR_IDS]
            when {
                raw == null -> null // never configured = all
                raw == NONE_SENTINEL -> emptySet()
                raw.isBlank() -> null
                else -> raw.split(",").toSet()
            }
        }

    suspend fun setOnboardingComplete(complete: Boolean) {
        context.dataStore.edit { it[KEY_ONBOARDING_COMPLETE] = complete }
    }

    suspend fun setMonitoringPaused(paused: Boolean) {
        context.dataStore.edit { it[KEY_MONITORING_PAUSED] = paused }
    }

    suspend fun setLlmEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_LLM_ENABLED] = enabled }
    }

    suspend fun setWorkHours(start: Int, end: Int) {
        context.dataStore.edit {
            it[KEY_WORK_START_HOUR] = start
            it[KEY_WORK_END_HOUR] = end
        }
    }

    suspend fun setDataRetentionDays(days: Int) {
        context.dataStore.edit { it[KEY_DATA_RETENTION_DAYS] = days }
    }

    /** null = all calendars; empty set = none; non-empty = specific IDs */
    suspend fun setSelectedCalendarIds(ids: Set<String>?) {
        context.dataStore.edit {
            it[KEY_SELECTED_CALENDAR_IDS] = when {
                ids == null -> ""
                ids.isEmpty() -> NONE_SENTINEL
                else -> ids.joinToString(",")
            }
        }
    }

    suspend fun setPermissionGranted(health: Boolean? = null, calendar: Boolean? = null, notification: Boolean? = null) {
        context.dataStore.edit { prefs ->
            health?.let { prefs[KEY_HEALTH_PERMISSION_GRANTED] = it }
            calendar?.let { prefs[KEY_CALENDAR_PERMISSION_GRANTED] = it }
            notification?.let { prefs[KEY_NOTIFICATION_PERMISSION_GRANTED] = it }
        }
    }
}
