package com.restguard.domain.service

import com.restguard.data.local.database.RestGuardDatabase
import com.restguard.domain.repository.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

/**
 * Handles data export (to JSON) and full data deletion.
 */
class DataManagementService @Inject constructor(
    private val stressRepo: StressRepository,
    private val feedbackRepo: FeedbackRepository,
    private val checkInRepo: CheckInRepository,
    private val personalizationRepo: PersonalizationRepository,
    private val database: RestGuardDatabase,
) {
    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    /**
     * Export all user data as a JSON string.
     * Suitable for writing to a file via SAF.
     */
    suspend fun exportToJson(): String {
        val thirtyDaysAgo = Instant.now().minusSeconds(30L * 24 * 3600)
        val ninetyDaysAgo = Instant.now().minusSeconds(90L * 24 * 3600)

        val stressSamples = stressRepo.getStressSamples(ninetyDaysAgo, Instant.now())
        val predictions = stressRepo.getPredictions(
            LocalDate.now().minusDays(90), LocalDate.now().plusDays(7),
        )
        val meetingImpacts = stressRepo.getAllMeetingStressImpacts()
        val feedback = feedbackRepo.getAllFeedback()
        val checkIns = checkInRepo.getRecent(500)
        val weights = personalizationRepo.getWeights()

        val export = DataExport(
            exportedAt = Instant.now().toString(),
            version = 1,
            stressSamples = stressSamples.map {
                ExportedStressSample(
                    timestamp = it.timestamp.toString(),
                    score = it.score,
                    physiological = it.components.physiological,
                    calendarPressure = it.components.calendarPressure,
                    historicalPattern = it.components.historicalPattern,
                    confidence = it.confidence,
                )
            },
            predictions = predictions.map {
                ExportedPrediction(
                    date = it.date.toString(),
                    predictedScore = it.predictedScore,
                    confidence = it.confidence,
                    explanation = it.explanation,
                )
            },
            meetingInsights = meetingImpacts.map {
                ExportedMeetingInsight(
                    patternKey = it.eventPatternKey,
                    averageStressDelta = it.averageStressDelta,
                    sampleCount = it.sampleCount,
                )
            },
            feedback = feedback.map {
                ExportedFeedback(
                    action = it.action.name,
                    comment = it.comment,
                    timestamp = it.timestamp.toString(),
                )
            },
            checkIns = checkIns.map {
                ExportedCheckIn(
                    timestamp = it.timestamp.toString(),
                    energyLevel = it.energyLevel,
                    moodLevel = it.moodLevel,
                    note = it.note,
                    trigger = it.trigger.name,
                )
            },
            personalizationWeights = weights?.let {
                ExportedWeights(
                    physiological = it.physiologicalWeight,
                    calendar = it.calendarWeight,
                    historical = it.historicalWeight,
                    scoreOffset = it.scoreOffset,
                )
            },
        )

        return json.encodeToString(export)
    }

    /**
     * Delete all user data from all tables.
     */
    suspend fun deleteAllData() {
        database.clearAllTables()
    }
}

// ─── Export DTOs ─────────────────────────────────────────

@Serializable
data class DataExport(
    val exportedAt: String,
    val version: Int,
    val stressSamples: List<ExportedStressSample>,
    val predictions: List<ExportedPrediction>,
    val meetingInsights: List<ExportedMeetingInsight>,
    val feedback: List<ExportedFeedback>,
    val checkIns: List<ExportedCheckIn>,
    val personalizationWeights: ExportedWeights?,
)

@Serializable
data class ExportedStressSample(
    val timestamp: String,
    val score: Int,
    val physiological: Int,
    val calendarPressure: Int,
    val historicalPattern: Int,
    val confidence: Float,
)

@Serializable
data class ExportedPrediction(
    val date: String,
    val predictedScore: Int,
    val confidence: Float,
    val explanation: String,
)

@Serializable
data class ExportedMeetingInsight(
    val patternKey: String,
    val averageStressDelta: Float,
    val sampleCount: Int,
)

@Serializable
data class ExportedFeedback(
    val action: String,
    val comment: String?,
    val timestamp: String,
)

@Serializable
data class ExportedCheckIn(
    val timestamp: String,
    val energyLevel: Int,
    val moodLevel: Int,
    val note: String?,
    val trigger: String,
)

@Serializable
data class ExportedWeights(
    val physiological: Float,
    val calendar: Float,
    val historical: Float,
    val scoreOffset: Float,
)
