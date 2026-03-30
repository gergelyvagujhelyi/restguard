package com.restguard.domain.service

import com.restguard.domain.model.*
import com.restguard.domain.repository.CalendarRepository
import com.restguard.domain.repository.HealthRepository
import com.restguard.domain.repository.PersonalizationRepository
import com.restguard.domain.repository.StressRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID
import javax.inject.Inject

/**
 * Computes current stress score and near-future predictions.
 *
 * ## Scoring Formula (v1 — weighted heuristic)
 *
 * currentStress = w1·physiological + w2·calendarPressure + w3·historicalPattern
 *
 * Where:
 *   physiological (0-100): composite of sleep, HRV, resting HR, activity
 *   calendarPressure (0-100): meeting density, back-to-back, duration, timing
 *   historicalPattern (0-100): learned stress patterns for this time/day
 *
 * Default weights: physiological=0.45, calendarPressure=0.35, historical=0.20
 *
 * ## Thresholds
 *   LOW:      0-30
 *   MODERATE: 31-55
 *   HIGH:     56-79
 *   EXTREME:  80-100
 *
 * ## Confidence
 *   Based on how many data sources are available.
 *   Each source contributes to confidence: sleep=0.25, hrv=0.20, hr=0.15,
 *   activity=0.10, calendar=0.20, history=0.10
 */
class StressScoringService @Inject constructor(
    private val healthRepo: HealthRepository,
    private val calendarRepo: CalendarRepository,
    private val stressRepo: StressRepository,
    private val personalizationRepo: PersonalizationRepository,
) {
    companion object {
        // Weights for composite score
        const val W_PHYSIOLOGICAL = 0.45f
        const val W_CALENDAR = 0.35f
        const val W_HISTORICAL = 0.20f

        // Thresholds
        const val THRESHOLD_MODERATE = 31
        const val THRESHOLD_HIGH = 56
        const val THRESHOLD_EXTREME = 80

        // Confidence contributions
        const val CONF_SLEEP = 0.25f
        const val CONF_HRV = 0.20f
        const val CONF_HR = 0.15f
        const val CONF_ACTIVITY = 0.10f
        const val CONF_CALENDAR = 0.20f
        const val CONF_HISTORY = 0.10f

        // Physiological scoring parameters
        const val IDEAL_SLEEP_MINUTES = 480 // 8 hours
        const val MIN_ACCEPTABLE_SLEEP = 300 // 5 hours
        const val IDEAL_HRV_MS = 50f
        const val LOW_HRV_MS = 20f
        const val IDEAL_RESTING_HR = 60
        const val HIGH_RESTING_HR = 85

        // Calendar pressure parameters
        const val MAX_REASONABLE_MEETINGS = 8
        const val BACK_TO_BACK_THRESHOLD_MINUTES = 15
        const val EARLY_HOUR = 9
        const val LATE_HOUR = 17

        /**
         * Derive a grouping key for similar meetings.
         * Uses normalized title + recurrence + attendee count bucket.
         */
        fun deriveEventPatternKey(event: CalendarEvent): String {
            val titleNorm = event.title
                .lowercase()
                .replace(Regex("[^a-z0-9 ]"), "")
                .trim()
                .take(50)
            val recurrence = if (event.isRecurring) "rec" else "once"
            val sizeBucket = when {
                event.attendees.size <= 1 -> "1on1"
                event.attendees.size <= 5 -> "small"
                event.attendees.size <= 15 -> "medium"
                else -> "large"
            }
            return "$titleNorm|$recurrence|$sizeBucket"
        }
    }

    /**
     * Compute the current stress score.
     * Uses personalized weights if calibrated, otherwise falls back to defaults.
     */
    suspend fun computeCurrentStress(): StressSample {
        val health = healthRepo.getLatestSnapshot()
        val now = ZonedDateTime.now()
        val todayEvents = calendarRepo.getEventsForDate(now.toLocalDate())
        val historicalImpacts = stressRepo.getAllMeetingStressImpacts()

        val physiological = computePhysiologicalScore(health)
        val calendar = computeCalendarPressure(todayEvents)
        val historical = computeHistoricalPattern(todayEvents, historicalImpacts)

        val missingSources = buildMissingSources(health)
        val confidence = computeConfidence(health, todayEvents.isNotEmpty(), historicalImpacts.isNotEmpty())

        // Use personalized weights if available
        val weights = personalizationRepo.getWeights() ?: PersonalizationWeights()

        val score = (
            weights.physiologicalWeight * physiological +
            weights.calendarWeight * calendar +
            weights.historicalWeight * historical +
            weights.scoreOffset
        ).toInt().coerceIn(0, 100)

        return StressSample(
            id = UUID.randomUUID().toString(),
            timestamp = Instant.now(),
            score = score,
            components = StressComponents(
                physiological = physiological,
                calendarPressure = calendar,
                historicalPattern = historical,
            ),
            confidence = confidence,
            missingSources = missingSources,
        )
    }

    /**
     * Predict stress for the next N days.
     * Fetches the entire date range in a single query and partitions locally.
     */
    suspend fun predictStress(days: Int = 3): List<StressPrediction> {
        val today = LocalDate.now()
        val zone = ZoneId.systemDefault()
        val historicalImpacts = stressRepo.getAllMeetingStressImpacts()

        // Single batch fetch instead of N per-day calls
        val rangeStart = today.atStartOfDay(zone)
        val rangeEnd = today.plusDays(days.toLong()).atStartOfDay(zone)
        val allEvents = calendarRepo.getEvents(rangeStart, rangeEnd)

        return (0 until days).map { offset ->
            val date = today.plusDays(offset.toLong())
            val dayStart = date.atStartOfDay(zone)
            val dayEnd = date.plusDays(1).atStartOfDay(zone)
            val events = allEvents.filter { it.startTime >= dayStart && it.startTime < dayEnd }

            val calendarPressure = computeCalendarPressure(events)
            val historicalPattern = computeHistoricalPattern(events, historicalImpacts)
            val breakdown = buildCalendarBreakdown(events)

            val predicted = (0.55f * calendarPressure + 0.45f * historicalPattern)
                .toInt().coerceIn(0, 100)

            StressPrediction(
                date = date,
                predictedScore = predicted,
                confidence = if (historicalImpacts.isNotEmpty()) 0.6f else 0.3f,
                calendarPressureBreakdown = breakdown,
                explanation = buildPredictionExplanation(date, breakdown, predicted),
            )
        }
    }

    /**
     * Determine the stress level from a score.
     */
    fun classifyStress(score: Int): StressLevel = when {
        score >= THRESHOLD_EXTREME -> StressLevel.EXTREME
        score >= THRESHOLD_HIGH -> StressLevel.HIGH
        score >= THRESHOLD_MODERATE -> StressLevel.MODERATE
        else -> StressLevel.LOW
    }

    // ─── Internal scoring functions ─────────────────────────

    internal fun computePhysiologicalScore(health: HealthSnapshot?): Int {
        if (health == null) return 50 // neutral when no data

        var score = 0f
        var weightUsed = 0f

        // Sleep: worse sleep = higher stress
        health.sleepDurationMinutes?.let { sleep ->
            val sleepScore = when {
                sleep >= IDEAL_SLEEP_MINUTES -> 10f
                sleep >= MIN_ACCEPTABLE_SLEEP -> {
                    val ratio = (IDEAL_SLEEP_MINUTES - sleep).toFloat() /
                        (IDEAL_SLEEP_MINUTES - MIN_ACCEPTABLE_SLEEP)
                    10f + ratio * 60f // 10-70
                }
                else -> 70f + ((MIN_ACCEPTABLE_SLEEP - sleep).toFloat() / MIN_ACCEPTABLE_SLEEP) * 30f
            }
            score += sleepScore * 0.35f
            weightUsed += 0.35f
        }

        // HRV: lower HRV = higher stress
        health.hrvMs?.let { hrv ->
            val hrvScore = when {
                hrv >= IDEAL_HRV_MS -> 10f
                hrv >= LOW_HRV_MS -> {
                    val ratio = (IDEAL_HRV_MS - hrv) / (IDEAL_HRV_MS - LOW_HRV_MS)
                    10f + ratio * 70f
                }
                else -> 80f + ((LOW_HRV_MS - hrv) / LOW_HRV_MS) * 20f
            }
            score += hrvScore * 0.30f
            weightUsed += 0.30f
        }

        // Resting HR: higher = more stress
        health.restingHeartRateBpm?.let { hr ->
            val hrScore = when {
                hr <= IDEAL_RESTING_HR -> 10f
                hr <= HIGH_RESTING_HR -> {
                    val ratio = (hr - IDEAL_RESTING_HR).toFloat() /
                        (HIGH_RESTING_HR - IDEAL_RESTING_HR)
                    10f + ratio * 60f
                }
                else -> 80f
            }
            score += hrScore * 0.20f
            weightUsed += 0.20f
        }

        // Activity: moderate is good, too little or too much is stress
        health.activeMinutesToday?.let { mins ->
            val activityScore = when {
                mins in 30..90 -> 15f // sweet spot
                mins < 30 -> 35f // sedentary
                else -> 30f + ((mins - 90).toFloat() / 90f * 30f).coerceAtMost(40f) // over-exertion
            }
            score += activityScore * 0.15f
            weightUsed += 0.15f
        }

        // Normalize if we didn't use all weights
        return if (weightUsed > 0) {
            (score / weightUsed).toInt().coerceIn(0, 100)
        } else {
            50 // no data
        }
    }

    internal fun computeCalendarPressure(events: List<CalendarEvent>): Int {
        if (events.isEmpty()) return 5 // nearly zero pressure

        val nonAllDay = events.filter { !it.isAllDay }
        if (nonAllDay.isEmpty()) return 5

        val meetingCount = nonAllDay.size
        val totalMinutes = nonAllDay.sumOf {
            java.time.Duration.between(it.startTime, it.endTime).toMinutes().toInt()
        }

        // Count back-to-back meetings (gap < 15 min)
        val sorted = nonAllDay.sortedBy { it.startTime }
        var backToBack = 0
        for (i in 0 until sorted.size - 1) {
            val gap = java.time.Duration.between(sorted[i].endTime, sorted[i + 1].startTime).toMinutes()
            if (gap < BACK_TO_BACK_THRESHOLD_MINUTES) backToBack++
        }

        val earlyCount = nonAllDay.count { it.startTime.hour < EARLY_HOUR }
        val lateCount = nonAllDay.count { it.endTime.hour > LATE_HOUR }

        // Scoring components
        val densityScore = (meetingCount.toFloat() / MAX_REASONABLE_MEETINGS * 60).coerceAtMost(60f)
        val durationScore = (totalMinutes.toFloat() / 480 * 20).coerceAtMost(20f) // 8h max
        val backToBackScore = (backToBack * 8f).coerceAtMost(15f)
        val timingScore = ((earlyCount + lateCount) * 3f).coerceAtMost(5f)

        return (densityScore + durationScore + backToBackScore + timingScore)
            .toInt().coerceIn(0, 100)
    }

    internal fun computeHistoricalPattern(
        events: List<CalendarEvent>,
        impacts: List<MeetingStressImpact>,
    ): Int {
        if (events.isEmpty() || impacts.isEmpty()) return 30 // neutral default

        val impactMap = impacts.associateBy { it.eventPatternKey }
        var totalImpact = 0f
        var matched = 0

        for (event in events) {
            val key = deriveEventPatternKey(event)
            impactMap[key]?.let { impact ->
                totalImpact += impact.averageStressDelta
                matched++
            }
        }

        if (matched == 0) return 30

        // Average delta, normalize to 0-100
        val avgDelta = totalImpact / matched
        return (30 + avgDelta * 5).toInt().coerceIn(0, 100)
    }

    internal fun buildCalendarBreakdown(events: List<CalendarEvent>): CalendarPressureBreakdown {
        val nonAllDay = events.filter { !it.isAllDay }
        val sorted = nonAllDay.sortedBy { it.startTime }

        var backToBack = 0
        for (i in 0 until sorted.size - 1) {
            val gap = java.time.Duration.between(sorted[i].endTime, sorted[i + 1].startTime).toMinutes()
            if (gap < BACK_TO_BACK_THRESHOLD_MINUTES) backToBack++
        }

        return CalendarPressureBreakdown(
            meetingCount = nonAllDay.size,
            totalMeetingMinutes = nonAllDay.sumOf {
                java.time.Duration.between(it.startTime, it.endTime).toMinutes().toInt()
            },
            backToBackCount = backToBack,
            highStressMeetingCount = 0, // filled by historical data
            earlyMeetingCount = nonAllDay.count { it.startTime.hour < EARLY_HOUR },
            lateMeetingCount = nonAllDay.count { it.endTime.hour > LATE_HOUR },
            contextSwitchScore = (nonAllDay.size * 8).coerceAtMost(100),
        )
    }

    private fun computeConfidence(
        health: HealthSnapshot?,
        hasCalendar: Boolean,
        hasHistory: Boolean,
    ): Float {
        var confidence = 0f
        if (health != null) {
            if (health.sleepDurationMinutes != null) confidence += CONF_SLEEP
            if (health.hrvMs != null) confidence += CONF_HRV
            if (health.restingHeartRateBpm != null) confidence += CONF_HR
            if (health.activeMinutesToday != null) confidence += CONF_ACTIVITY
        }
        if (hasCalendar) confidence += CONF_CALENDAR
        if (hasHistory) confidence += CONF_HISTORY
        return confidence.coerceIn(0f, 1f)
    }

    private fun buildMissingSources(health: HealthSnapshot?): List<String> {
        val missing = mutableListOf<String>()
        if (health == null) {
            missing.addAll(listOf("sleep", "hrv", "resting_hr", "activity"))
        } else {
            if (health.sleepDurationMinutes == null) missing.add("sleep")
            if (health.hrvMs == null) missing.add("hrv")
            if (health.restingHeartRateBpm == null) missing.add("resting_hr")
            if (health.activeMinutesToday == null) missing.add("activity")
        }
        return missing
    }

    private fun buildPredictionExplanation(
        date: LocalDate,
        breakdown: CalendarPressureBreakdown,
        predicted: Int,
    ): String {
        val parts = mutableListOf<String>()
        if (breakdown.meetingCount > 5) parts.add("${breakdown.meetingCount} meetings scheduled")
        if (breakdown.backToBackCount > 1) parts.add("${breakdown.backToBackCount} back-to-back blocks")
        if (breakdown.totalMeetingMinutes > 300) parts.add("${breakdown.totalMeetingMinutes / 60}h+ in meetings")
        if (breakdown.earlyMeetingCount > 0) parts.add("early morning meeting(s)")
        if (breakdown.lateMeetingCount > 0) parts.add("late afternoon meeting(s)")

        return if (parts.isEmpty()) {
            "Moderate calendar load on $date."
        } else {
            "$date looks demanding: ${parts.joinToString(", ")}."
        }
    }

}
