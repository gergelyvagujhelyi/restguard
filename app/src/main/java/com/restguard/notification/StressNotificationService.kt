package com.restguard.notification

import com.restguard.domain.model.NotificationState
import com.restguard.domain.model.StressLevel

/**
 * Manages the always-running foreground notification.
 *
 * ## Notification Design
 *
 * The notification displays:
 * - Current stress level as a colored icon (green/amber/orange/red)
 * - Headline text (e.g., "Low stress", "High stress — 2 suggestions")
 * - Action: "View Suggestions" deep-link (when recommendations exist)
 *
 * ## Color Mapping
 * | Level    | Icon Color | Notification Priority |
 * |----------|-----------|----------------------|
 * | LOW      | Green     | LOW                  |
 * | MODERATE | Amber     | DEFAULT              |
 * | HIGH     | Orange    | HIGH                 |
 * | EXTREME  | Red       | HIGH + heads-up      |
 *
 * ## Implementation Notes
 * - Requires a foreground service for always-on notification
 * - Uses NotificationCompat for backward compatibility
 * - Updates in-place (same notification ID) to avoid notification spam
 * - Deep-links to recommendation list via PendingIntent
 *
 * The actual Android Service/NotificationManager integration is Phase 2.
 * This class defines the state model and logic; the service wiring comes later.
 */
object StressNotificationService {

    const val NOTIFICATION_CHANNEL_ID = "restguard_stress"
    const val NOTIFICATION_CHANNEL_NAME = "Stress Monitor"
    const val NOTIFICATION_ID = 1001
    const val ACTION_VIEW_SUGGESTIONS = "com.restguard.ACTION_VIEW_SUGGESTIONS"
    const val ACTION_CHECK_IN = "com.restguard.ACTION_CHECK_IN"

    /**
     * Build the notification state from current stress data.
     */
    fun buildNotificationState(
        stressScore: Int,
        stressLevel: StressLevel,
        pendingRecommendations: Int,
    ): NotificationState {
        val headline = when {
            stressLevel == StressLevel.EXTREME ->
                "Extreme stress — ${pendingRecommendations} urgent suggestion(s)"
            stressLevel == StressLevel.HIGH && pendingRecommendations > 0 ->
                "High stress — $pendingRecommendations suggestion(s) available"
            stressLevel == StressLevel.HIGH ->
                "High stress — take a break if you can"
            stressLevel == StressLevel.MODERATE && pendingRecommendations > 0 ->
                "Moderate stress — $pendingRecommendations suggestion(s)"
            stressLevel == StressLevel.MODERATE ->
                "Moderate stress"
            pendingRecommendations > 0 ->
                "Low stress — $pendingRecommendations suggestion(s)"
            else ->
                "Low stress — looking good"
        }

        return NotificationState(
            stressLevel = stressLevel,
            currentScore = stressScore,
            pendingRecommendationCount = pendingRecommendations,
            headline = headline,
            lastUpdated = java.time.Instant.now(),
        )
    }

    /**
     * Determine if the notification should use heads-up display.
     */
    fun shouldHeadsUp(state: NotificationState): Boolean {
        return state.stressLevel == StressLevel.EXTREME ||
            (state.stressLevel == StressLevel.HIGH && state.pendingRecommendationCount > 0)
    }
}
