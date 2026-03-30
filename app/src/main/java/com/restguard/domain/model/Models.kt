package com.restguard.domain.model

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZonedDateTime

// ─── Stress & Health ────────────────────────────────────────

/**
 * A single stress measurement at a point in time.
 * Score range: 0 (fully relaxed) to 100 (maximum stress).
 */
data class StressSample(
    val id: String,
    val timestamp: Instant,
    val score: Int, // 0-100
    val components: StressComponents,
    val confidence: Float, // 0.0-1.0 — how much data was available
    val missingSources: List<String>, // e.g. ["hrv", "sleep"]
)

data class StressComponents(
    val physiological: Int, // 0-100, from health data
    val calendarPressure: Int, // 0-100, from schedule analysis
    val historicalPattern: Int, // 0-100, from learned patterns
)

/**
 * Snapshot of health data pulled from Health Connect.
 */
data class HealthSnapshot(
    val id: String,
    val timestamp: Instant,
    val sleepDurationMinutes: Int?, // last night
    val sleepQualityScore: Int?, // 0-100 derived from stages
    val restingHeartRateBpm: Int?,
    val hrvMs: Float?, // RMSSD in milliseconds
    val stepsToday: Int?,
    val activeMinutesToday: Int?,
    val respiratoryRate: Float?,
    val bodyTemperature: Float?,
)

/**
 * Predicted stress for a future day.
 */
data class StressPrediction(
    val date: LocalDate,
    val predictedScore: Int, // 0-100
    val confidence: Float, // 0.0-1.0
    val calendarPressureBreakdown: CalendarPressureBreakdown,
    val explanation: String, // human-readable
)

data class CalendarPressureBreakdown(
    val meetingCount: Int,
    val totalMeetingMinutes: Int,
    val backToBackCount: Int,
    val highStressMeetingCount: Int,
    val earlyMeetingCount: Int, // before 9am
    val lateMeetingCount: Int, // after 5pm
    val contextSwitchScore: Int, // 0-100
)

// ─── Calendar ───────────────────────────────────────────────

enum class CalendarSource { SYSTEM, GOOGLE_API }

data class CalendarInfo(
    val id: String,
    val accountName: String,
    val displayName: String,
    val color: Int,
    val isPrimary: Boolean,
    val source: CalendarSource = CalendarSource.SYSTEM,
)

data class CalendarEvent(
    val id: String,
    val calendarId: String,
    val title: String,
    val description: String?,
    val location: String?,
    val startTime: ZonedDateTime,
    val endTime: ZonedDateTime,
    val isAllDay: Boolean,
    val isRecurring: Boolean,
    val recurrenceRule: String?,
    val organizerEmail: String?,
    val selfIsOrganizer: Boolean,
    val attendees: List<EventAttendee>,
    val status: EventStatus,
    val availability: EventAvailability,
)

data class EventAttendee(
    val email: String,
    val name: String?,
    val isOrganizer: Boolean,
    val attendanceStatus: AttendanceStatus,
    val isSelf: Boolean,
)

enum class EventStatus { CONFIRMED, TENTATIVE, CANCELLED }
enum class EventAvailability { BUSY, FREE, TENTATIVE }
enum class AttendanceStatus { ACCEPTED, DECLINED, TENTATIVE, NONE }

// ─── Contacts ───────────────────────────────────────────────

data class ContactMethod(
    val contactId: String,
    val displayName: String,
    val email: String?,
    val phoneNumber: String?,
    val photoUri: String?,
)

// ─── Meeting Analysis ───────────────────────────────────────

/**
 * Historical impact of a meeting/meeting-type on user stress.
 * Built up over time from before/after stress measurements.
 */
data class MeetingStressImpact(
    val id: String,
    val eventPatternKey: String, // normalized key for grouping similar meetings
    val averageStressDelta: Float, // how much stress typically changes
    val sampleCount: Int,
    val features: MeetingFeatures,
    val lastUpdated: Instant,
)

data class MeetingFeatures(
    val isOneOnOne: Boolean,
    val isGroupMeeting: Boolean,
    val isRecurring: Boolean,
    val hasManager: Boolean, // inferred from org signals
    val isCustomerFacing: Boolean, // keyword-based
    val isInternal: Boolean,
    val isMorning: Boolean, // before noon
    val isAfternoon: Boolean,
    val durationMinutes: Int,
    val isPreparationHeavy: Boolean, // keyword-based
    val isOnline: Boolean, // inferred from location field
)

/**
 * LLM or rule-based importance classification.
 */
data class MeetingPriorityAssessment(
    val eventId: String,
    val importance: Importance,
    val confidence: Float, // 0.0-1.0
    val explanation: String,
    val source: AssessmentSource,
    val timestamp: Instant,
)

enum class Importance { HIGH, MEDIUM, LOW }
enum class AssessmentSource { LLM, RULE_BASED, USER_OVERRIDE }

// ─── Recommendations ────────────────────────────────────────

data class Recommendation(
    val id: String,
    val type: RecommendationType,
    val eventId: String?, // null for activity suggestions
    val event: CalendarEvent?, // denormalized for display
    val priority: Int, // lower = more important to act on
    val explanation: String,
    val stressReduction: Int, // estimated points of stress relief
    val createdAt: Instant,
    val status: RecommendationStatus,
    val isExtremeStressMode: Boolean,
)

enum class RecommendationType {
    NO_ACTION,
    STRESS_RELIEF_ACTIVITY,
    SUGGEST_RESCHEDULE,
    SUGGEST_CANCEL,
    URGENT_SAME_DAY_INTERVENTION,
}

enum class RecommendationStatus {
    PENDING,
    ACCEPTED,
    DISMISSED,
    EXPIRED,
}

data class RescheduleOption(
    val id: String,
    val originalEventId: String,
    val proposedStart: ZonedDateTime,
    val proposedEnd: ZonedDateTime,
    val dayStressScore: Int, // predicted stress for that day
    val meetingCountOnDay: Int,
    val reason: String,
    val rank: Int,
)

data class ActivitySuggestion(
    val id: String,
    val type: ActivityType,
    val title: String,
    val description: String,
    val durationMinutes: Int,
    val minGapMinutes: Int, // minimum free time needed
    val stressLevelRange: IntRange, // when to suggest this
)

enum class ActivityType {
    BREATHING_EXERCISE,
    SHORT_WALK,
    HYDRATION_REMINDER,
    SCREEN_BREAK,
    STRETCH,
    POWER_NAP,
    EVENING_RECOVERY,
}

// ─── Notification ───────────────────────────────────────────

data class NotificationState(
    val stressLevel: StressLevel,
    val currentScore: Int,
    val pendingRecommendationCount: Int,
    val headline: String,
    val lastUpdated: Instant,
)

enum class StressLevel(val label: String) {
    LOW("Low stress"),
    MODERATE("Moderate stress"),
    HIGH("High stress"),
    EXTREME("Extreme stress — consider resting"),
}

// ─── User Feedback & Audit ──────────────────────────────────

data class UserFeedback(
    val id: String,
    val recommendationId: String,
    val action: FeedbackAction,
    val comment: String?,
    val timestamp: Instant,
)

enum class FeedbackAction {
    ACCEPTED,
    DISMISSED,
    SNOOZED,
    REPORTED_WRONG,
}

data class AuditLog(
    val id: String,
    val action: String, // e.g., "CANCEL_REQUESTED", "RESCHEDULE_CONFIRMED"
    val eventId: String?,
    val details: String, // JSON blob
    val timestamp: Instant,
)

// ─── Subjective Check-Ins ───────────────────────────────────

/**
 * User-reported feeling at a point in time.
 * Provides ground truth for calibrating physiological scoring.
 */
data class SubjectiveCheckIn(
    val id: String,
    val timestamp: Instant,
    val energyLevel: Int, // 1-5, where 1=exhausted, 5=energized
    val moodLevel: Int, // 1-5, where 1=very stressed, 5=calm/happy
    val note: String?, // optional free-text
    val trigger: CheckInTrigger,
)

enum class CheckInTrigger {
    SCHEDULED, // periodic prompt
    USER_INITIATED, // user opened check-in screen
    POST_MEETING, // prompted after a detected meeting completion
    NOTIFICATION_TAP, // user tapped "How are you?" from notification
}

// ─── User Profile (personalization context) ─────────────────

/**
 * Accumulated user context for better LLM prompts and personalization.
 * Built from feedback patterns and check-in history.
 */
data class UserProfile(
    val typicalSleepHours: Float?, // learned average
    val typicalHrv: Float?, // learned baseline
    val typicalRestingHr: Int?, // learned baseline
    val preferredWorkStart: Int, // hour
    val preferredWorkEnd: Int,
    val stressTriggers: List<String>, // learned from patterns, e.g. "large meetings", "customer calls"
    val reliefPreferences: List<ActivityType>, // which activities user accepts most
    val dismissPatterns: List<String>, // meeting types user always dismisses suggestions for
    val acceptRate: Float, // fraction of recommendations accepted
    val avgReportedEnergy: Float?, // from check-ins
    val avgReportedMood: Float?, // from check-ins
)

// ─── Personalization Weights ────────────────────────────────

/**
 * Learned adjustments to the base stress scoring formula.
 * Allows the scoring to self-correct based on subjective feedback.
 */
data class PersonalizationWeights(
    val physiologicalWeight: Float = 0.45f,
    val calendarWeight: Float = 0.35f,
    val historicalWeight: Float = 0.20f,
    val scoreOffset: Float = 0f, // calibration offset from check-in alignment
    val lastCalibrated: Instant? = null,
)
