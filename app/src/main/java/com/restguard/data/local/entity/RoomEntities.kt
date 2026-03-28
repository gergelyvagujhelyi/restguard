package com.restguard.data.local.entity

import androidx.room.*

/**
 * Room entities — flat relational representations of domain models.
 * Domain model ↔ entity mapping is done in the repository implementations.
 */

@Entity(tableName = "stress_samples")
data class StressSampleEntity(
    @PrimaryKey val id: String,
    val timestamp: Long, // epoch millis
    val score: Int,
    val physiological: Int,
    val calendarPressure: Int,
    val historicalPattern: Int,
    val confidence: Float,
    val missingSources: String, // comma-separated
)

@Entity(tableName = "health_snapshots")
data class HealthSnapshotEntity(
    @PrimaryKey val id: String,
    val timestamp: Long,
    val sleepDurationMinutes: Int?,
    val sleepQualityScore: Int?,
    val restingHeartRateBpm: Int?,
    val hrvMs: Float?,
    val stepsToday: Int?,
    val activeMinutesToday: Int?,
    val respiratoryRate: Float?,
    val bodyTemperature: Float?,
)

@Entity(tableName = "stress_predictions")
data class StressPredictionEntity(
    @PrimaryKey val date: String, // ISO date string
    val predictedScore: Int,
    val confidence: Float,
    val meetingCount: Int,
    val totalMeetingMinutes: Int,
    val backToBackCount: Int,
    val highStressMeetingCount: Int,
    val earlyMeetingCount: Int,
    val lateMeetingCount: Int,
    val contextSwitchScore: Int,
    val explanation: String,
)

@Entity(tableName = "meeting_stress_impacts")
data class MeetingStressImpactEntity(
    @PrimaryKey val id: String,
    val eventPatternKey: String,
    val averageStressDelta: Float,
    val sampleCount: Int,
    // MeetingFeatures flattened
    val isOneOnOne: Boolean,
    val isGroupMeeting: Boolean,
    val isRecurring: Boolean,
    val hasManager: Boolean,
    val isCustomerFacing: Boolean,
    val isInternal: Boolean,
    val isMorning: Boolean,
    val isAfternoon: Boolean,
    val durationMinutes: Int,
    val isPreparationHeavy: Boolean,
    val isOnline: Boolean,
    val lastUpdated: Long,
)

@Entity(tableName = "recommendations")
data class RecommendationEntity(
    @PrimaryKey val id: String,
    val type: String, // enum name
    val eventId: String?,
    val priority: Int,
    val explanation: String,
    val stressReduction: Int,
    val createdAt: Long,
    val status: String, // enum name
    val isExtremeStressMode: Boolean,
    // Denormalized event fields for display without join
    val eventTitle: String?,
    val eventStartTime: Long?,
    val eventEndTime: Long?,
    val eventAttendeeCount: Int?,
)

@Entity(tableName = "reschedule_options")
data class RescheduleOptionEntity(
    @PrimaryKey val id: String,
    val originalEventId: String,
    val proposedStart: Long,
    val proposedEnd: Long,
    val dayStressScore: Int,
    val meetingCountOnDay: Int,
    val reason: String,
    val rank: Int,
)

@Entity(tableName = "user_feedback")
data class UserFeedbackEntity(
    @PrimaryKey val id: String,
    val recommendationId: String,
    val action: String, // enum name
    val comment: String?,
    val timestamp: Long,
)

@Entity(tableName = "audit_log")
data class AuditLogEntity(
    @PrimaryKey val id: String,
    val action: String,
    val eventId: String?,
    val details: String,
    val timestamp: Long,
)

@Entity(tableName = "subjective_check_ins")
data class SubjectiveCheckInEntity(
    @PrimaryKey val id: String,
    val timestamp: Long,
    val energyLevel: Int, // 1-5
    val moodLevel: Int, // 1-5
    val note: String?,
    val trigger: String, // enum name
)

@Entity(tableName = "personalization_weights")
data class PersonalizationWeightsEntity(
    @PrimaryKey val id: Int = 0, // singleton row
    val physiologicalWeight: Float,
    val calendarWeight: Float,
    val historicalWeight: Float,
    val scoreOffset: Float,
    val lastCalibrated: Long?,
)
