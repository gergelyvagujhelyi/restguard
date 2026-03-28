package com.restguard.domain.service

import com.restguard.domain.model.*
import com.restguard.domain.repository.CalendarRepository
import com.restguard.domain.repository.LlmClient
import com.restguard.domain.repository.StressRepository
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

/**
 * Central decision engine that produces recommendations based on stress state
 * and calendar analysis.
 *
 * ## Decision Matrix
 *
 * | Current Stress | Future Overload | Action                             |
 * |---------------|-----------------|-------------------------------------|
 * | LOW           | LOW             | No action                           |
 * | LOW           | HIGH            | Suggest reschedule future meetings   |
 * | MODERATE      | LOW             | Suggest stress-relief activity       |
 * | MODERATE      | HIGH            | Suggest reschedule + activity        |
 * | HIGH          | any             | Suggest cancel/reschedule non-essential meetings |
 * | EXTREME       | any             | URGENT: top 3 events, same-day allowed |
 *
 * ## Rules
 * 1. Same-day cancellation/rescheduling is FORBIDDEN unless stress is EXTREME.
 * 2. In EXTREME mode, return only top 3 candidates and skip normal ranking.
 * 3. Never auto-execute destructive actions — all are suggestions requiring user confirmation.
 * 4. Every recommendation includes an explanation.
 * 5. Recommendations are ranked by: importance(low first) × stress_impact(high first).
 */
class RecommendationEngine @Inject constructor(
    private val stressScoring: StressScoringService,
    private val calendarRepo: CalendarRepository,
    private val stressRepo: StressRepository,
    private val importanceAssessor: MeetingImportanceAssessor,
    private val activitySuggester: ActivitySuggester,
) {
    companion object {
        const val FUTURE_DAYS_TO_SCAN = 3
        const val EXTREME_MODE_MAX_EVENTS = 3
        const val MIN_SCORE_FOR_ACTIVITY_SUGGESTION = 25
    }

    /**
     * Generate all recommendations based on current state.
     */
    suspend fun generateRecommendations(): List<Recommendation> {
        val currentStress = stressScoring.computeCurrentStress()
        val stressLevel = stressScoring.classifyStress(currentStress.score)
        val predictions = stressScoring.predictStress(FUTURE_DAYS_TO_SCAN)

        return when (stressLevel) {
            StressLevel.EXTREME -> generateExtremeStressRecommendations(currentStress)
            StressLevel.HIGH -> generateHighStressRecommendations(currentStress, predictions)
            StressLevel.MODERATE -> generateModerateStressRecommendations(currentStress, predictions)
            StressLevel.LOW -> generateLowStressRecommendations(currentStress, predictions)
        }
    }

    // ─── EXTREME stress: same-day intervention ──────────────

    private suspend fun generateExtremeStressRecommendations(
        stress: StressSample,
    ): List<Recommendation> {
        val today = LocalDate.now()
        val todayEvents = calendarRepo.getEventsForDate(today)
            .filter { !it.isAllDay && it.status != EventStatus.CANCELLED }
            .filter { it.startTime.toInstant().isAfter(Instant.now()) } // only future events today

        if (todayEvents.isEmpty()) {
            return listOf(createActivityRecommendation(stress.score, isUrgent = true))
        }

        // Skip normal ranking → direct priority assessment
        val assessed = todayEvents.map { event ->
            event to importanceAssessor.assess(event)
        }

        // Take top 3 lowest-importance events
        val candidates = assessed
            .sortedBy { (_, assessment) -> importanceToSortKey(assessment.importance) }
            .take(EXTREME_MODE_MAX_EVENTS)

        return candidates.map { (event, assessment) ->
            Recommendation(
                id = UUID.randomUUID().toString(),
                type = RecommendationType.URGENT_SAME_DAY_INTERVENTION,
                eventId = event.id,
                event = event,
                priority = importanceToSortKey(assessment.importance),
                explanation = buildExtremeExplanation(stress, event, assessment),
                stressReduction = estimateStressReduction(event, assessment),
                createdAt = Instant.now(),
                status = RecommendationStatus.PENDING,
                isExtremeStressMode = true,
            )
        }
    }

    // ─── HIGH stress ────────────────────────────────────────

    private suspend fun generateHighStressRecommendations(
        stress: StressSample,
        predictions: List<StressPrediction>,
    ): List<Recommendation> {
        val recommendations = mutableListOf<Recommendation>()

        // Activity suggestion first
        recommendations.add(createActivityRecommendation(stress.score, isUrgent = false))

        // Scan future days (NOT today — same-day restriction)
        for (prediction in predictions) {
            if (prediction.predictedScore < StressScoringService.THRESHOLD_MODERATE) continue

            val events = calendarRepo.getEventsForDate(prediction.date)
                .filter { !it.isAllDay && it.status != EventStatus.CANCELLED }

            val assessed = events.map { it to importanceAssessor.assess(it) }
            val nonEssential = assessed
                .filter { (_, a) -> a.importance != Importance.HIGH }
                .sortedBy { (_, a) -> importanceToSortKey(a.importance) }

            for ((event, assessment) in nonEssential) {
                val type = if (assessment.importance == Importance.LOW) {
                    RecommendationType.SUGGEST_CANCEL
                } else {
                    RecommendationType.SUGGEST_RESCHEDULE
                }

                recommendations.add(
                    Recommendation(
                        id = UUID.randomUUID().toString(),
                        type = type,
                        eventId = event.id,
                        event = event,
                        priority = importanceToSortKey(assessment.importance),
                        explanation = buildHighStressExplanation(stress, event, assessment, prediction),
                        stressReduction = estimateStressReduction(event, assessment),
                        createdAt = Instant.now(),
                        status = RecommendationStatus.PENDING,
                        isExtremeStressMode = false,
                    )
                )
            }
        }

        return recommendations.sortedBy { it.priority }
    }

    // ─── MODERATE stress ────────────────────────────────────

    private suspend fun generateModerateStressRecommendations(
        stress: StressSample,
        predictions: List<StressPrediction>,
    ): List<Recommendation> {
        val recommendations = mutableListOf<Recommendation>()

        // Activity suggestion
        if (stress.score >= MIN_SCORE_FOR_ACTIVITY_SUGGESTION) {
            recommendations.add(createActivityRecommendation(stress.score, isUrgent = false))
        }

        // Only suggest rescheduling for predicted HIGH/EXTREME days
        val overloadedDays = predictions.filter {
            it.predictedScore >= StressScoringService.THRESHOLD_HIGH
        }

        for (prediction in overloadedDays) {
            val events = calendarRepo.getEventsForDate(prediction.date)
                .filter { !it.isAllDay && it.status != EventStatus.CANCELLED }

            val assessed = events.map { it to importanceAssessor.assess(it) }
            val lowImportance = assessed
                .filter { (_, a) -> a.importance == Importance.LOW }
                .sortedBy { (_, a) -> importanceToSortKey(a.importance) }

            for ((event, assessment) in lowImportance.take(2)) { // limit suggestions
                recommendations.add(
                    Recommendation(
                        id = UUID.randomUUID().toString(),
                        type = RecommendationType.SUGGEST_RESCHEDULE,
                        eventId = event.id,
                        event = event,
                        priority = importanceToSortKey(assessment.importance) + 10, // lower priority than high stress
                        explanation = buildModerateExplanation(stress, event, assessment, prediction),
                        stressReduction = estimateStressReduction(event, assessment),
                        createdAt = Instant.now(),
                        status = RecommendationStatus.PENDING,
                        isExtremeStressMode = false,
                    )
                )
            }
        }

        return recommendations.sortedBy { it.priority }
    }

    // ─── LOW stress ─────────────────────────────────────────

    private suspend fun generateLowStressRecommendations(
        stress: StressSample,
        predictions: List<StressPrediction>,
    ): List<Recommendation> {
        // Only warn about future overload
        val overloaded = predictions.filter {
            it.predictedScore >= StressScoringService.THRESHOLD_HIGH
        }

        if (overloaded.isEmpty()) return emptyList()

        return overloaded.take(1).map { prediction ->
            Recommendation(
                id = UUID.randomUUID().toString(),
                type = RecommendationType.SUGGEST_RESCHEDULE,
                eventId = null,
                event = null,
                priority = 50,
                explanation = "Heads up: ${prediction.date} looks heavy. ${prediction.explanation} " +
                    "Consider rescheduling some non-essential meetings.",
                stressReduction = 0,
                createdAt = Instant.now(),
                status = RecommendationStatus.PENDING,
                isExtremeStressMode = false,
            )
        }
    }

    // ─── Helpers ────────────────────────────────────────────

    private fun createActivityRecommendation(stressScore: Int, isUrgent: Boolean): Recommendation {
        val activity = activitySuggester.suggest(stressScore)
        return Recommendation(
            id = UUID.randomUUID().toString(),
            type = RecommendationType.STRESS_RELIEF_ACTIVITY,
            eventId = null,
            event = null,
            priority = if (isUrgent) 0 else 5,
            explanation = activity.description,
            stressReduction = (stressScore * 0.1f).toInt().coerceAtLeast(3),
            createdAt = Instant.now(),
            status = RecommendationStatus.PENDING,
            isExtremeStressMode = isUrgent,
        )
    }

    private fun importanceToSortKey(importance: Importance): Int = when (importance) {
        Importance.LOW -> 1
        Importance.MEDIUM -> 2
        Importance.HIGH -> 3
    }

    private fun estimateStressReduction(event: CalendarEvent, assessment: MeetingPriorityAssessment): Int {
        val duration = Duration.between(event.startTime, event.endTime).toMinutes()
        val importanceFactor = when (assessment.importance) {
            Importance.LOW -> 1.5f
            Importance.MEDIUM -> 1.0f
            Importance.HIGH -> 0.3f
        }
        return (duration * 0.1f * importanceFactor).toInt().coerceIn(2, 25)
    }

    private fun buildExtremeExplanation(
        stress: StressSample,
        event: CalendarEvent,
        assessment: MeetingPriorityAssessment,
    ): String {
        return "Your stress level is critically high (${stress.score}/100). " +
            "\"${event.title}\" is classified as ${assessment.importance.name.lowercase()} importance: " +
            "${assessment.explanation} " +
            "Consider cancelling or rescheduling to protect your health today."
    }

    private fun buildHighStressExplanation(
        stress: StressSample,
        event: CalendarEvent,
        assessment: MeetingPriorityAssessment,
        prediction: StressPrediction,
    ): String {
        return "Current stress is high (${stress.score}/100) and ${prediction.date} is predicted at " +
            "${prediction.predictedScore}/100. \"${event.title}\" has ${assessment.importance.name.lowercase()} " +
            "importance: ${assessment.explanation}"
    }

    private fun buildModerateExplanation(
        stress: StressSample,
        event: CalendarEvent,
        assessment: MeetingPriorityAssessment,
        prediction: StressPrediction,
    ): String {
        return "${prediction.date} is predicted to be stressful (${prediction.predictedScore}/100). " +
            "\"${event.title}\" could be rescheduled: ${assessment.explanation}"
    }
}
