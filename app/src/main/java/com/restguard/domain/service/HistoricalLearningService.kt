package com.restguard.domain.service

import com.restguard.domain.model.*
import com.restguard.domain.repository.CalendarRepository
import com.restguard.domain.repository.StressRepository
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

/**
 * Learns which meetings are stressful for this user over time.
 *
 * ## MVP Approach: Exponential Moving Average (EMA)
 *
 * For each meeting pattern key, track:
 *   stressDelta = stressAfter - stressBefore
 *   EMA_new = α × stressDelta + (1 - α) × EMA_old
 *
 * Where α = 0.3 (responsive but smooth)
 *
 * ## Data Sources
 * - Stress sample closest before meeting start (within 2h)
 * - Stress sample closest after meeting end (within 2h)
 * - Meeting metadata (duration, attendees, type, time of day)
 * - User feedback on recommendations
 *
 * ## Future Upgrades
 * - Phase 3: Logistic regression with meeting features
 * - Phase 4: On-device lightweight ML model
 * - Phase 5: Personalized embeddings for meeting types
 */
class HistoricalLearningService @Inject constructor(
    private val stressRepo: StressRepository,
    private val calendarRepo: CalendarRepository,
) {
    companion object {
        const val EMA_ALPHA = 0.3f
        const val MAX_SAMPLE_AGE_HOURS = 2L
        const val MIN_SAMPLES_FOR_CONFIDENCE = 3
    }

    /**
     * Process a completed meeting: update its stress impact profile.
     * Called after meetings end (via WorkManager).
     */
    suspend fun processCompletedMeeting(event: CalendarEvent) {
        val patternKey = StressScoringService.deriveEventPatternKey(event)
        val meetingStart = event.startTime.toInstant()
        val meetingEnd = event.endTime.toInstant()

        // Find stress samples around the meeting
        val beforeWindow = meetingStart.minus(Duration.ofHours(MAX_SAMPLE_AGE_HOURS))
        val afterWindow = meetingEnd.plus(Duration.ofHours(MAX_SAMPLE_AGE_HOURS))

        val samplesBefore = stressRepo.getStressSamples(beforeWindow, meetingStart)
        val samplesAfter = stressRepo.getStressSamples(meetingEnd, afterWindow)

        val stressBefore = samplesBefore.maxByOrNull { it.timestamp }?.score ?: return
        val stressAfter = samplesAfter.minByOrNull { it.timestamp }?.score ?: return

        val delta = (stressAfter - stressBefore).toFloat()

        // Update EMA
        val existing = stressRepo.getMeetingStressImpact(patternKey)
        val newAvg: Float
        val newCount: Int

        if (existing != null) {
            newAvg = EMA_ALPHA * delta + (1 - EMA_ALPHA) * existing.averageStressDelta
            newCount = existing.sampleCount + 1
        } else {
            newAvg = delta
            newCount = 1
        }

        val features = extractFeatures(event)

        stressRepo.saveMeetingStressImpact(
            MeetingStressImpact(
                id = existing?.id ?: patternKey,
                eventPatternKey = patternKey,
                averageStressDelta = newAvg,
                sampleCount = newCount,
                features = features,
                lastUpdated = Instant.now(),
            )
        )
    }

    /**
     * Extract meeting features for analysis.
     */
    fun extractFeatures(event: CalendarEvent): MeetingFeatures {
        val durationMinutes = Duration.between(event.startTime, event.endTime).toMinutes().toInt()
        val titleLower = event.title.lowercase()
        val descLower = event.description?.lowercase() ?: ""
        val combined = "$titleLower $descLower"

        return MeetingFeatures(
            isOneOnOne = event.attendees.size <= 2,
            isGroupMeeting = event.attendees.size > 2,
            isRecurring = event.isRecurring,
            hasManager = combined.contains("1:1") || combined.contains("one on one"),
            isCustomerFacing = listOf("customer", "client", "external", "partner", "vendor")
                .any { combined.contains(it) },
            isInternal = !listOf("customer", "client", "external", "partner", "vendor")
                .any { combined.contains(it) },
            isMorning = event.startTime.hour < 12,
            isAfternoon = event.startTime.hour >= 12,
            durationMinutes = durationMinutes,
            isPreparationHeavy = listOf("review", "presentation", "demo", "pitch", "proposal")
                .any { combined.contains(it) },
            isOnline = event.location?.let { loc ->
                listOf("zoom", "meet", "teams", "webex", "http", "virtual")
                    .any { loc.lowercase().contains(it) }
            } ?: true, // assume online if no location
        )
    }

    /**
     * Get aggregated insights for UI display.
     */
    suspend fun getMeetingTypeInsights(): List<MeetingTypeInsight> {
        val impacts = stressRepo.getAllMeetingStressImpacts()
            .filter { it.sampleCount >= MIN_SAMPLES_FOR_CONFIDENCE }
            .sortedByDescending { it.averageStressDelta }

        return impacts.map { impact ->
            MeetingTypeInsight(
                patternKey = impact.eventPatternKey,
                displayName = impact.eventPatternKey.split("|").first()
                    .replaceFirstChar { it.uppercase() },
                averageStressChange = impact.averageStressDelta,
                sampleCount = impact.sampleCount,
                isStressful = impact.averageStressDelta > 5,
                isRelaxing = impact.averageStressDelta < -3,
            )
        }
    }
}

data class MeetingTypeInsight(
    val patternKey: String,
    val displayName: String,
    val averageStressChange: Float,
    val sampleCount: Int,
    val isStressful: Boolean,
    val isRelaxing: Boolean,
)
