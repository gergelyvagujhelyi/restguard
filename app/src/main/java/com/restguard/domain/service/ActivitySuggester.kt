package com.restguard.domain.service

import com.restguard.domain.model.ActivitySuggestion
import com.restguard.domain.model.ActivityType
import java.util.UUID
import javax.inject.Inject

/**
 * Suggests stress-relief activities based on current stress level and available time.
 */
class ActivitySuggester @Inject constructor() {

    private val activities = listOf(
        ActivitySuggestion(
            id = "breathing",
            type = ActivityType.BREATHING_EXERCISE,
            title = "4-7-8 Breathing Exercise",
            description = "Take 3 minutes for box breathing: inhale 4s, hold 7s, exhale 8s. " +
                "This activates your parasympathetic nervous system and lowers cortisol.",
            durationMinutes = 3,
            minGapMinutes = 5,
            stressLevelRange = 25..100,
        ),
        ActivitySuggestion(
            id = "walk",
            type = ActivityType.SHORT_WALK,
            title = "Short Walk",
            description = "A 10-minute walk — even indoors — reduces stress hormones. " +
                "Leave your phone behind if you can.",
            durationMinutes = 10,
            minGapMinutes = 15,
            stressLevelRange = 30..85,
        ),
        ActivitySuggestion(
            id = "hydration",
            type = ActivityType.HYDRATION_REMINDER,
            title = "Hydration Check",
            description = "Stress increases when dehydrated. Drink a full glass of water now. " +
                "Aim for 8 glasses today.",
            durationMinutes = 1,
            minGapMinutes = 2,
            stressLevelRange = 20..70,
        ),
        ActivitySuggestion(
            id = "screen_break",
            type = ActivityType.SCREEN_BREAK,
            title = "Screen Break — 20/20/20",
            description = "Every 20 minutes, look at something 20 feet away for 20 seconds. " +
                "Take a 5-minute full screen break now. Close your eyes or look out a window.",
            durationMinutes = 5,
            minGapMinutes = 10,
            stressLevelRange = 25..75,
        ),
        ActivitySuggestion(
            id = "stretch",
            type = ActivityType.STRETCH,
            title = "Desk Stretch",
            description = "Stand up and do 5 minutes of neck rolls, shoulder shrugs, and " +
                "wrist stretches. Focus on areas that feel tight.",
            durationMinutes = 5,
            minGapMinutes = 8,
            stressLevelRange = 25..80,
        ),
        ActivitySuggestion(
            id = "nap",
            type = ActivityType.POWER_NAP,
            title = "Power Nap (20 min)",
            description = "A 20-minute nap can restore alertness and reduce fatigue. " +
                "Set an alarm — longer naps cause grogginess.",
            durationMinutes = 20,
            minGapMinutes = 30,
            stressLevelRange = 60..100,
        ),
        ActivitySuggestion(
            id = "evening_recovery",
            type = ActivityType.EVENING_RECOVERY,
            title = "Evening Wind-Down",
            description = "Tonight, try: no screens 1h before bed, light stretching, " +
                "herbal tea, and journaling 3 things that went well today.",
            durationMinutes = 60,
            minGapMinutes = 60,
            stressLevelRange = 40..100,
        ),
    )

    /**
     * Pick the best activity for the current stress level.
     */
    fun suggest(stressScore: Int, availableMinutes: Int? = null): ActivitySuggestion {
        val eligible = activities.filter { stressScore in it.stressLevelRange }
            .let { list ->
                if (availableMinutes != null) {
                    list.filter { it.minGapMinutes <= availableMinutes }
                } else {
                    list
                }
            }

        // Prefer more impactful activities for higher stress
        return if (stressScore >= 60) {
            eligible.maxByOrNull { it.durationMinutes } ?: activities.first()
        } else {
            eligible.minByOrNull { it.durationMinutes } ?: activities.first()
        }
    }

    /**
     * Get all activities suitable for the stress level and time gap.
     */
    fun suggestAll(stressScore: Int, availableMinutes: Int? = null): List<ActivitySuggestion> {
        return activities.filter { stressScore in it.stressLevelRange }
            .let { list ->
                if (availableMinutes != null) {
                    list.filter { it.minGapMinutes <= availableMinutes }
                } else {
                    list
                }
            }
            .sortedByDescending { it.durationMinutes }
    }
}
