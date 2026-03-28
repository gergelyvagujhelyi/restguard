package com.restguard.data.local

import com.restguard.domain.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Verifies domain ↔ entity round-trip mapping preserves all data.
 */
class EntityMappersTest {

    @Test
    fun `StressSample round-trips correctly`() {
        val original = StressSample(
            id = "s1",
            timestamp = Instant.ofEpochMilli(1700000000000),
            score = 65,
            components = StressComponents(
                physiological = 70,
                calendarPressure = 55,
                historicalPattern = 45,
            ),
            confidence = 0.85f,
            missingSources = listOf("hrv", "sleep"),
        )

        val entity = original.toEntity()
        val restored = entity.toDomain()

        assertEquals(original.id, restored.id)
        assertEquals(original.timestamp, restored.timestamp)
        assertEquals(original.score, restored.score)
        assertEquals(original.components.physiological, restored.components.physiological)
        assertEquals(original.components.calendarPressure, restored.components.calendarPressure)
        assertEquals(original.components.historicalPattern, restored.components.historicalPattern)
        assertEquals(original.confidence, restored.confidence, 0.001f)
        assertEquals(original.missingSources, restored.missingSources)
    }

    @Test
    fun `StressSample with empty missingSources round-trips`() {
        val original = StressSample(
            id = "s2",
            timestamp = Instant.now(),
            score = 30,
            components = StressComponents(25, 35, 30),
            confidence = 1.0f,
            missingSources = emptyList(),
        )

        val restored = original.toEntity().toDomain()
        assertTrue(restored.missingSources.isEmpty())
    }

    @Test
    fun `HealthSnapshot round-trips with nulls`() {
        val original = HealthSnapshot(
            id = "h1",
            timestamp = Instant.now(),
            sleepDurationMinutes = 420,
            sleepQualityScore = null,
            restingHeartRateBpm = 65,
            hrvMs = null,
            stepsToday = 5000,
            activeMinutesToday = null,
            respiratoryRate = 15.5f,
            bodyTemperature = null,
        )

        val restored = original.toEntity().toDomain()

        assertEquals(original.sleepDurationMinutes, restored.sleepDurationMinutes)
        assertNull(restored.sleepQualityScore)
        assertEquals(original.restingHeartRateBpm, restored.restingHeartRateBpm)
        assertNull(restored.hrvMs)
        assertEquals(original.stepsToday, restored.stepsToday)
        assertNull(restored.activeMinutesToday)
        assertEquals(original.respiratoryRate, restored.respiratoryRate)
    }

    @Test
    fun `StressPrediction round-trips`() {
        val original = StressPrediction(
            date = LocalDate.of(2026, 4, 1),
            predictedScore = 72,
            confidence = 0.6f,
            calendarPressureBreakdown = CalendarPressureBreakdown(
                meetingCount = 7,
                totalMeetingMinutes = 360,
                backToBackCount = 3,
                highStressMeetingCount = 2,
                earlyMeetingCount = 1,
                lateMeetingCount = 1,
                contextSwitchScore = 56,
            ),
            explanation = "Heavy day with back-to-back meetings.",
        )

        val restored = original.toEntity().toDomain()

        assertEquals(original.date, restored.date)
        assertEquals(original.predictedScore, restored.predictedScore)
        assertEquals(original.confidence, restored.confidence, 0.001f)
        assertEquals(original.calendarPressureBreakdown.meetingCount, restored.calendarPressureBreakdown.meetingCount)
        assertEquals(original.calendarPressureBreakdown.backToBackCount, restored.calendarPressureBreakdown.backToBackCount)
        assertEquals(original.explanation, restored.explanation)
    }

    @Test
    fun `MeetingStressImpact round-trips with features`() {
        val original = MeetingStressImpact(
            id = "m1",
            eventPatternKey = "weekly sync|rec|small",
            averageStressDelta = 12.5f,
            sampleCount = 8,
            features = MeetingFeatures(
                isOneOnOne = false,
                isGroupMeeting = true,
                isRecurring = true,
                hasManager = false,
                isCustomerFacing = false,
                isInternal = true,
                isMorning = true,
                isAfternoon = false,
                durationMinutes = 30,
                isPreparationHeavy = false,
                isOnline = true,
            ),
            lastUpdated = Instant.now(),
        )

        val restored = original.toEntity().toDomain()

        assertEquals(original.eventPatternKey, restored.eventPatternKey)
        assertEquals(original.averageStressDelta, restored.averageStressDelta, 0.01f)
        assertEquals(original.sampleCount, restored.sampleCount)
        assertEquals(original.features.isGroupMeeting, restored.features.isGroupMeeting)
        assertEquals(original.features.isRecurring, restored.features.isRecurring)
        assertEquals(original.features.isOnline, restored.features.isOnline)
        assertEquals(original.features.durationMinutes, restored.features.durationMinutes)
    }

    @Test
    fun `RescheduleOption round-trips with timezone`() {
        val zone = ZoneId.systemDefault()
        val original = RescheduleOption(
            id = "r1",
            originalEventId = "e5",
            proposedStart = ZonedDateTime.of(2026, 4, 3, 10, 0, 0, 0, zone),
            proposedEnd = ZonedDateTime.of(2026, 4, 3, 11, 0, 0, 0, zone),
            dayStressScore = 28,
            meetingCountOnDay = 2,
            reason = "Low stress day",
            rank = 1,
        )

        val restored = original.toEntity().toDomain()

        assertEquals(original.originalEventId, restored.originalEventId)
        assertEquals(original.dayStressScore, restored.dayStressScore)
        assertEquals(original.rank, restored.rank)
        // Timestamps should be equivalent (may differ in zone representation)
        assertEquals(
            original.proposedStart.toInstant().toEpochMilli(),
            restored.proposedStart.toInstant().toEpochMilli(),
        )
    }

    @Test
    fun `UserFeedback round-trips`() {
        val original = UserFeedback(
            id = "f1",
            recommendationId = "rec1",
            action = FeedbackAction.ACCEPTED,
            comment = "Good suggestion",
            timestamp = Instant.now(),
        )

        val restored = original.toEntity().toDomain()

        assertEquals(original.id, restored.id)
        assertEquals(original.action, restored.action)
        assertEquals(original.comment, restored.comment)
    }

    @Test
    fun `AuditLog round-trips`() {
        val original = AuditLog(
            id = "a1",
            action = "CANCEL_REQUESTED",
            eventId = "e3",
            details = """{"reason":"stress","score":85}""",
            timestamp = Instant.now(),
        )

        val restored = original.toEntity().toDomain()

        assertEquals(original.action, restored.action)
        assertEquals(original.eventId, restored.eventId)
        assertEquals(original.details, restored.details)
    }

    @Test
    fun `SubjectiveCheckIn round-trips`() {
        val original = SubjectiveCheckIn(
            id = "ci1",
            timestamp = Instant.ofEpochMilli(1700000000000),
            energyLevel = 4,
            moodLevel = 2,
            note = "Feeling drained after standup",
            trigger = CheckInTrigger.POST_MEETING,
        )

        val restored = original.toEntity().toDomain()

        assertEquals(original.id, restored.id)
        assertEquals(original.timestamp, restored.timestamp)
        assertEquals(original.energyLevel, restored.energyLevel)
        assertEquals(original.moodLevel, restored.moodLevel)
        assertEquals(original.note, restored.note)
        assertEquals(original.trigger, restored.trigger)
    }

    @Test
    fun `SubjectiveCheckIn round-trips with null note`() {
        val original = SubjectiveCheckIn(
            id = "ci2",
            timestamp = Instant.now(),
            energyLevel = 3,
            moodLevel = 5,
            note = null,
            trigger = CheckInTrigger.SCHEDULED,
        )

        val restored = original.toEntity().toDomain()

        assertNull(restored.note)
        assertEquals(CheckInTrigger.SCHEDULED, restored.trigger)
    }

    @Test
    fun `PersonalizationWeights round-trips`() {
        val original = PersonalizationWeights(
            physiologicalWeight = 0.50f,
            calendarWeight = 0.30f,
            historicalWeight = 0.20f,
            scoreOffset = -3.5f,
            lastCalibrated = Instant.ofEpochMilli(1700000000000),
        )

        val restored = original.toEntity().toDomain()

        assertEquals(original.physiologicalWeight, restored.physiologicalWeight, 0.001f)
        assertEquals(original.calendarWeight, restored.calendarWeight, 0.001f)
        assertEquals(original.historicalWeight, restored.historicalWeight, 0.001f)
        assertEquals(original.scoreOffset, restored.scoreOffset, 0.001f)
        assertEquals(original.lastCalibrated, restored.lastCalibrated)
    }

    @Test
    fun `PersonalizationWeights round-trips with null calibration date`() {
        val original = PersonalizationWeights() // all defaults

        val restored = original.toEntity().toDomain()

        assertEquals(0.45f, restored.physiologicalWeight, 0.001f)
        assertEquals(0.35f, restored.calendarWeight, 0.001f)
        assertEquals(0.20f, restored.historicalWeight, 0.001f)
        assertEquals(0f, restored.scoreOffset, 0.001f)
        assertNull(restored.lastCalibrated)
    }
}
