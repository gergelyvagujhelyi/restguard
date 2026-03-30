package com.restguard.domain.repository

import com.restguard.domain.model.*
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime

/**
 * Health data access — backed by Health Connect or mock.
 */
interface HealthRepository {
    suspend fun getLatestSnapshot(): HealthSnapshot?
    suspend fun getSnapshots(from: Instant, to: Instant): List<HealthSnapshot>
    fun observeLatestSnapshot(): Flow<HealthSnapshot?>
}

/**
 * Calendar data access — backed by CalendarProvider or mock.
 */
interface CalendarRepository {
    suspend fun getAvailableCalendars(): List<CalendarInfo>
    suspend fun getEvents(from: ZonedDateTime, to: ZonedDateTime): List<CalendarEvent>
    suspend fun getEventById(id: String): CalendarEvent?
    suspend fun getEventsForDate(date: LocalDate): List<CalendarEvent>
    suspend fun deleteEvent(eventId: String): Boolean
    suspend fun updateEventTime(eventId: String, newStart: ZonedDateTime, newEnd: ZonedDateTime): Boolean
    fun observeEventsForDateRange(from: LocalDate, to: LocalDate): Flow<List<CalendarEvent>>
}

/**
 * Contact lookup — backed by ContactsProvider or mock.
 */
interface ContactRepository {
    suspend fun searchContacts(query: String): List<ContactMethod>
    suspend fun getContactByEmail(email: String): ContactMethod?
    suspend fun getRecentContacts(limit: Int = 10): List<ContactMethod>
}

/**
 * Local persistence of stress data and predictions.
 */
interface StressRepository {
    suspend fun saveStressSample(sample: StressSample)
    suspend fun getStressSamples(from: Instant, to: Instant): List<StressSample>
    suspend fun getLatestStressSample(): StressSample?
    fun observeCurrentStress(): Flow<StressSample?>

    suspend fun savePrediction(prediction: StressPrediction)
    suspend fun getPredictions(from: LocalDate, to: LocalDate): List<StressPrediction>

    suspend fun saveMeetingStressImpact(impact: MeetingStressImpact)
    suspend fun getMeetingStressImpact(eventPatternKey: String): MeetingStressImpact?
    suspend fun getAllMeetingStressImpacts(): List<MeetingStressImpact>
}

/**
 * Recommendations persistence.
 */
interface RecommendationRepository {
    suspend fun saveRecommendations(recommendations: List<Recommendation>)
    suspend fun getActiveRecommendations(): List<Recommendation>
    suspend fun updateStatus(id: String, status: RecommendationStatus)
    fun observeActiveRecommendations(): Flow<List<Recommendation>>

    suspend fun saveRescheduleOptions(options: List<RescheduleOption>)
    suspend fun getRescheduleOptions(eventId: String): List<RescheduleOption>
}

/**
 * User feedback and audit log.
 */
interface FeedbackRepository {
    suspend fun saveFeedback(feedback: UserFeedback)
    suspend fun getFeedbackForRecommendation(recommendationId: String): UserFeedback?
    suspend fun getAllFeedback(): List<UserFeedback>
}

interface AuditRepository {
    suspend fun log(entry: AuditLog)
    suspend fun getEntries(from: Instant, to: Instant): List<AuditLog>
}

/**
 * Subjective check-in persistence.
 */
interface CheckInRepository {
    suspend fun saveCheckIn(checkIn: SubjectiveCheckIn)
    suspend fun getCheckIns(from: Instant, to: Instant): List<SubjectiveCheckIn>
    suspend fun getLatest(): SubjectiveCheckIn?
    suspend fun getRecent(limit: Int = 20): List<SubjectiveCheckIn>
    suspend fun count(): Int
}

/**
 * Personalization weights persistence.
 */
interface PersonalizationRepository {
    suspend fun saveWeights(weights: PersonalizationWeights)
    suspend fun getWeights(): PersonalizationWeights?
}

/**
 * LLM client for importance assessment and message drafting.
 */
interface LlmClient {
    suspend fun assessMeetingImportance(request: LlmImportanceRequest): LlmImportanceResponse
    suspend fun draftMessage(request: LlmMessageRequest): LlmMessageResponse
    fun isAvailable(): Boolean
}

data class LlmImportanceRequest(
    val title: String,
    val description: String?,
    val organizer: String?,
    val attendeeCount: Int,
    val isRecurring: Boolean,
    val duration: Int,
    val timeOfDay: String,
    val dayOfWeek: String,
    val userRole: String?, // optional context
)

data class LlmImportanceResponse(
    val importance: Importance,
    val confidence: Float,
    val explanation: String,
)

data class LlmMessageRequest(
    val messageType: MessageType,
    val meetingTitle: String,
    val recipientName: String?,
    val reason: String,
    val newTime: String?, // for reschedule
    val tone: String = "professional",
)

enum class MessageType { CANCELLATION_EMAIL, RESCHEDULE_EMAIL, SHORT_SMS }

data class LlmMessageResponse(
    val subject: String?,
    val body: String,
)
