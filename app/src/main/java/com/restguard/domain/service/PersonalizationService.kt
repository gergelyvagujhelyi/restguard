package com.restguard.domain.service

import com.restguard.domain.model.*
import com.restguard.domain.repository.*
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

/**
 * Aggregates feedback, check-ins, and meeting patterns into a UserProfile
 * and self-correcting PersonalizationWeights.
 *
 * ## Weight Calibration
 *
 * When subjective check-ins diverge from computed stress:
 *   - If user reports LOW stress but score is HIGH → reduce physiological weight
 *   - If user reports HIGH stress but score is LOW → increase physiological weight
 *   - Offset adjusts the final score up/down based on systematic bias
 *
 * ## Profile Building
 *
 * Learns user baselines (typical sleep, HRV, HR), work hour preferences,
 * stress triggers, and recommendation accept/dismiss patterns.
 *
 * ## Guard Rails
 *
 * - Minimum 5 check-ins before calibrating
 * - Weight changes clamped: each component stays in [0.15, 0.65]
 * - Offset clamped to [-15, +15]
 * - Weights always re-normalized to sum to 1.0
 * - Max one recalibration per 24h
 */
class PersonalizationService @Inject constructor(
    private val checkInRepo: CheckInRepository,
    private val feedbackRepo: FeedbackRepository,
    private val stressRepo: StressRepository,
    private val healthRepo: HealthRepository,
    private val personalizationRepo: PersonalizationRepository,
) {
    companion object {
        const val MIN_CHECKINS_FOR_CALIBRATION = 5
        const val CALIBRATION_COOLDOWN_HOURS = 24L
        const val MIN_COMPONENT_WEIGHT = 0.15f
        const val MAX_COMPONENT_WEIGHT = 0.65f
        const val MAX_OFFSET = 15f
        const val LEARNING_RATE = 0.1f
        const val CHECKIN_LOOKBACK_DAYS = 30L
    }

    /**
     * Build a UserProfile from accumulated data.
     */
    suspend fun buildProfile(): UserProfile {
        val recentCheckIns = checkInRepo.getRecent(50)
        val allFeedback = feedbackRepo.getAllFeedback()
        val healthSnapshots = healthRepo.getSnapshots(
            Instant.now().minus(Duration.ofDays(CHECKIN_LOOKBACK_DAYS)),
            Instant.now(),
        )
        val meetingImpacts = stressRepo.getAllMeetingStressImpacts()

        // Learn health baselines
        val typicalSleep = healthSnapshots
            .mapNotNull { it.sleepDurationMinutes }
            .takeIf { it.size >= 3 }
            ?.average()?.toFloat()
            ?.let { it / 60f } // convert to hours

        val typicalHrv = healthSnapshots
            .mapNotNull { it.hrvMs }
            .takeIf { it.size >= 3 }
            ?.average()?.toFloat()

        val typicalHr = healthSnapshots
            .mapNotNull { it.restingHeartRateBpm }
            .takeIf { it.size >= 3 }
            ?.average()?.toInt()

        // Learn stress triggers from high-impact meetings
        val stressTriggers = meetingImpacts
            .filter { it.averageStressDelta > 5 && it.sampleCount >= 3 }
            .sortedByDescending { it.averageStressDelta }
            .take(5)
            .map { it.eventPatternKey.split("|").first().replaceFirstChar { c -> c.uppercase() } }

        // Learn relief preferences from accepted activity recommendations
        val acceptedActivityRecs = allFeedback
            .filter { it.action == FeedbackAction.ACCEPTED }

        // Learn dismiss patterns
        val dismissedPatterns = allFeedback
            .filter { it.action == FeedbackAction.DISMISSED }
            .groupBy { it.recommendationId }
            .filter { it.value.size >= 2 } // dismissed multiple times
            .keys.toList()

        // Accept rate
        val totalFeedback = allFeedback.size
        val acceptCount = allFeedback.count { it.action == FeedbackAction.ACCEPTED }
        val acceptRate = if (totalFeedback > 0) acceptCount.toFloat() / totalFeedback else 0.5f

        // Check-in averages
        val avgEnergy = recentCheckIns
            .takeIf { it.isNotEmpty() }
            ?.map { it.energyLevel }?.average()?.toFloat()

        val avgMood = recentCheckIns
            .takeIf { it.isNotEmpty() }
            ?.map { it.moodLevel }?.average()?.toFloat()

        return UserProfile(
            typicalSleepHours = typicalSleep,
            typicalHrv = typicalHrv,
            typicalRestingHr = typicalHr,
            preferredWorkStart = 9,
            preferredWorkEnd = 17,
            stressTriggers = stressTriggers,
            reliefPreferences = emptyList(), // requires cross-referencing rec type
            dismissPatterns = dismissedPatterns,
            acceptRate = acceptRate,
            avgReportedEnergy = avgEnergy,
            avgReportedMood = avgMood,
        )
    }

    /**
     * Calibrate stress scoring weights based on subjective check-in alignment.
     *
     * Compares each check-in's reported stress (derived from mood) with the
     * computed stress score at that time. Adjusts weights to close the gap.
     */
    suspend fun calibrateWeights(): PersonalizationWeights {
        val current = personalizationRepo.getWeights() ?: PersonalizationWeights()

        // Enforce calibration cooldown
        current.lastCalibrated?.let { last ->
            if (Duration.between(last, Instant.now()).toHours() < CALIBRATION_COOLDOWN_HOURS) {
                return current
            }
        }

        val lookback = Instant.now().minus(Duration.ofDays(CHECKIN_LOOKBACK_DAYS))
        val checkIns = checkInRepo.getCheckIns(lookback, Instant.now())

        if (checkIns.size < MIN_CHECKINS_FOR_CALIBRATION) {
            return current
        }

        // For each check-in, find the closest stress sample
        var totalBias = 0f
        var matchCount = 0

        for (checkIn in checkIns) {
            val window = Duration.ofMinutes(30)
            val nearSamples = stressRepo.getStressSamples(
                checkIn.timestamp.minus(window),
                checkIn.timestamp.plus(window),
            )
            val closestSample = nearSamples.minByOrNull {
                kotlin.math.abs(Duration.between(it.timestamp, checkIn.timestamp).toMillis())
            } ?: continue

            // Convert mood (1-5) to stress equivalent (100-0)
            // mood 1 = very stressed → stress ~85
            // mood 5 = calm → stress ~10
            val subjectiveStress = ((5 - checkIn.moodLevel) * 21.25f).coerceIn(0f, 100f)
            val computedStress = closestSample.score.toFloat()

            totalBias += (computedStress - subjectiveStress)
            matchCount++
        }

        if (matchCount < MIN_CHECKINS_FOR_CALIBRATION) {
            return current
        }

        // Average bias: positive means we're scoring too high
        val avgBias = totalBias / matchCount

        // Adjust offset (nudge score down if we're too high, up if too low)
        val newOffset = (current.scoreOffset - avgBias * LEARNING_RATE)
            .coerceIn(-MAX_OFFSET, MAX_OFFSET)

        // Adjust component weights based on which components correlate better
        // Simple heuristic: if physiological is driving over-scoring, reduce its weight
        var physW = current.physiologicalWeight
        var calW = current.calendarWeight
        var histW = current.historicalWeight

        if (avgBias > 5) {
            // We're scoring too high — slightly reduce the dominant weight
            val max = maxOf(physW, calW, histW)
            when (max) {
                physW -> { physW -= LEARNING_RATE * 0.05f; calW += LEARNING_RATE * 0.025f; histW += LEARNING_RATE * 0.025f }
                calW -> { calW -= LEARNING_RATE * 0.05f; physW += LEARNING_RATE * 0.025f; histW += LEARNING_RATE * 0.025f }
                else -> { histW -= LEARNING_RATE * 0.05f; physW += LEARNING_RATE * 0.025f; calW += LEARNING_RATE * 0.025f }
            }
        } else if (avgBias < -5) {
            // We're scoring too low — slightly increase the dominant weight
            val min = minOf(physW, calW, histW)
            when (min) {
                physW -> { physW += LEARNING_RATE * 0.05f; calW -= LEARNING_RATE * 0.025f; histW -= LEARNING_RATE * 0.025f }
                calW -> { calW += LEARNING_RATE * 0.05f; physW -= LEARNING_RATE * 0.025f; histW -= LEARNING_RATE * 0.025f }
                else -> { histW += LEARNING_RATE * 0.05f; physW -= LEARNING_RATE * 0.025f; calW -= LEARNING_RATE * 0.025f }
            }
        }

        // Clamp individual weights
        physW = physW.coerceIn(MIN_COMPONENT_WEIGHT, MAX_COMPONENT_WEIGHT)
        calW = calW.coerceIn(MIN_COMPONENT_WEIGHT, MAX_COMPONENT_WEIGHT)
        histW = histW.coerceIn(MIN_COMPONENT_WEIGHT, MAX_COMPONENT_WEIGHT)

        // Re-normalize to sum to 1.0
        val sum = physW + calW + histW
        physW /= sum
        calW /= sum
        histW /= sum

        val updated = PersonalizationWeights(
            physiologicalWeight = physW,
            calendarWeight = calW,
            historicalWeight = histW,
            scoreOffset = newOffset,
            lastCalibrated = Instant.now(),
        )

        personalizationRepo.saveWeights(updated)
        return updated
    }

    /**
     * Get current personalization weights, or defaults if not yet calibrated.
     */
    suspend fun getCurrentWeights(): PersonalizationWeights {
        return personalizationRepo.getWeights() ?: PersonalizationWeights()
    }
}
