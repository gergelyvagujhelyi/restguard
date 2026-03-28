package com.restguard.domain.service

import com.restguard.data.repository.impl.FakeCalendarRepository
import com.restguard.data.repository.impl.InMemoryStressRepository
import com.restguard.domain.model.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID

class HistoricalLearningServiceTest {

    private lateinit var stressRepo: InMemoryStressRepository
    private lateinit var calendarRepo: FakeCalendarRepository
    private lateinit var service: HistoricalLearningService

    @Before
    fun setup() {
        stressRepo = InMemoryStressRepository()
        calendarRepo = FakeCalendarRepository()
        service = HistoricalLearningService(stressRepo, calendarRepo)
    }

    @Test
    fun `extract features from customer meeting`() {
        val event = createEvent("Customer Demo", attendees = 3, description = "Demo for client X")
        val features = service.extractFeatures(event)
        assertTrue("Should detect customer-facing", features.isCustomerFacing)
        assertFalse("Should not be internal", features.isInternal)
        assertTrue("Should be group meeting", features.isGroupMeeting)
    }

    @Test
    fun `extract features from 1-on-1`() {
        val event = createEvent("1:1 with Manager", attendees = 1)
        val features = service.extractFeatures(event)
        assertTrue("Should be 1-on-1", features.isOneOnOne)
        assertTrue("Should detect manager", features.hasManager)
    }

    @Test
    fun `extract features from online meeting`() {
        val event = createEvent("Sync", attendees = 3, location = "https://zoom.us/j/123")
        val features = service.extractFeatures(event)
        assertTrue("Should detect online meeting", features.isOnline)
    }

    @Test
    fun `extract features detects prep-heavy meeting`() {
        val event = createEvent("Q3 Presentation Review", attendees = 5, description = "Bring slides for demo")
        val features = service.extractFeatures(event)
        assertTrue("Should detect prep-heavy", features.isPreparationHeavy)
    }

    @Test
    fun `process completed meeting updates EMA`() = runTest {
        val event = createEvent("Team Sync", attendees = 3)
        val meetingStart = event.startTime.toInstant()
        val meetingEnd = event.endTime.toInstant()

        // Insert before/after stress samples
        stressRepo.saveStressSample(createStressSample(
            timestamp = meetingStart.minusSeconds(300),
            score = 40,
        ))
        stressRepo.saveStressSample(createStressSample(
            timestamp = meetingEnd.plusSeconds(300),
            score = 60,
        ))

        service.processCompletedMeeting(event)

        val key = StressScoringService.deriveEventPatternKey(event)
        val impact = stressRepo.getMeetingStressImpact(key)

        assertNotNull("Impact should be saved", impact)
        assertEquals(1, impact!!.sampleCount)
        assertEquals(20f, impact.averageStressDelta, 0.1f) // 60 - 40 = +20
    }

    @Test
    fun `EMA smooths over multiple samples`() = runTest {
        val event = createEvent("Weekly Sync", attendees = 3, recurring = true)
        val key = StressScoringService.deriveEventPatternKey(event)

        // Simulate 3 meeting completions with different deltas
        val deltas = listOf(20f, 10f, 30f) // stress increases

        for ((i, delta) in deltas.withIndex()) {
            val startTime = event.startTime.plusDays(i.toLong() * 7)
            val endTime = event.endTime.plusDays(i.toLong() * 7)

            stressRepo.saveStressSample(createStressSample(
                timestamp = startTime.toInstant().minusSeconds(300),
                score = 40,
            ))
            stressRepo.saveStressSample(createStressSample(
                timestamp = endTime.toInstant().plusSeconds(300),
                score = 40 + delta.toInt(),
            ))

            service.processCompletedMeeting(
                event.copy(
                    startTime = startTime,
                    endTime = endTime,
                )
            )
        }

        val impact = stressRepo.getMeetingStressImpact(key)
        assertNotNull(impact)
        assertEquals(3, impact!!.sampleCount)
        // EMA: 20, then 0.3*10+0.7*20=17, then 0.3*30+0.7*17=20.9
        assertEquals(20.9f, impact.averageStressDelta, 1.0f)
    }

    // ─── Helpers ────────────────────────────────────────────

    private fun createEvent(
        title: String,
        attendees: Int,
        description: String? = null,
        location: String? = null,
        recurring: Boolean = false,
    ): CalendarEvent {
        val zone = ZoneId.systemDefault()
        val start = ZonedDateTime.now(zone).plusHours(1)
        return CalendarEvent(
            id = UUID.randomUUID().toString(),
            calendarId = "c1",
            title = title,
            description = description,
            location = location,
            startTime = start,
            endTime = start.plusMinutes(60),
            isAllDay = false,
            isRecurring = recurring,
            recurrenceRule = if (recurring) "FREQ=WEEKLY" else null,
            organizerEmail = "org@co.com",
            selfIsOrganizer = false,
            attendees = (1..attendees).map {
                EventAttendee("a$it@co.com", "A$it", false, AttendanceStatus.ACCEPTED, false)
            },
            status = EventStatus.CONFIRMED,
            availability = EventAvailability.BUSY,
        )
    }

    private fun createStressSample(timestamp: Instant, score: Int) = StressSample(
        id = UUID.randomUUID().toString(),
        timestamp = timestamp,
        score = score,
        components = StressComponents(score, 0, 0),
        confidence = 0.8f,
        missingSources = emptyList(),
    )
}
