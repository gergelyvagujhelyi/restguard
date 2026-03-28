package com.restguard.domain.service

import com.restguard.data.repository.impl.FakeLlmClient
import com.restguard.domain.model.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class MeetingImportanceAssessorTest {

    private lateinit var assessor: MeetingImportanceAssessor

    @Before
    fun setup() {
        assessor = MeetingImportanceAssessor(FakeLlmClient())
    }

    @Test
    fun `customer meeting is high importance`() {
        val event = createEvent("Customer Demo", attendeeCount = 3)
        val assessment = assessor.assessWithRules(event)
        assertEquals(Importance.HIGH, assessment.importance)
        assertEquals(AssessmentSource.RULE_BASED, assessment.source)
        assertTrue(assessment.explanation.isNotEmpty())
    }

    @Test
    fun `1on1 with manager is high importance`() {
        val event = createEvent("1:1 with Manager", attendeeCount = 1)
        val assessment = assessor.assessWithRules(event)
        assertEquals(Importance.HIGH, assessment.importance)
    }

    @Test
    fun `optional social event is low importance`() {
        val event = createEvent("Optional Coffee Chat", attendeeCount = 2)
        val assessment = assessor.assessWithRules(event)
        assertEquals(Importance.LOW, assessment.importance)
    }

    @Test
    fun `all-hands town hall is low importance`() {
        val event = createEvent("All-Hands Town Hall", attendeeCount = 50, recurring = true)
        val assessment = assessor.assessWithRules(event)
        assertEquals(Importance.LOW, assessment.importance)
    }

    @Test
    fun `standup is medium importance`() {
        val event = createEvent("Daily Standup", attendeeCount = 5, recurring = true)
        val assessment = assessor.assessWithRules(event)
        assertEquals(Importance.MEDIUM, assessment.importance)
    }

    @Test
    fun `generic meeting with few attendees is medium importance`() {
        val event = createEvent("Project Discussion", attendeeCount = 3)
        val assessment = assessor.assessWithRules(event)
        assertEquals(Importance.MEDIUM, assessment.importance)
    }

    @Test
    fun `assessment always has confidence score`() {
        val event = createEvent("Random Meeting", attendeeCount = 2)
        val assessment = assessor.assessWithRules(event)
        assertTrue("Confidence in range", assessment.confidence in 0f..1f)
    }

    @Test
    fun `large recurring meeting scores lower`() {
        val event = createEvent("Weekly Department Update", attendeeCount = 25, recurring = true)
        val assessment = assessor.assessWithRules(event)
        // Large + recurring should push toward LOW
        assertNotEquals(Importance.HIGH, assessment.importance)
    }

    // ─── Helper ─────────────────────────────────────────────

    private fun createEvent(
        title: String,
        attendeeCount: Int,
        recurring: Boolean = false,
        durationMin: Int = 30,
    ): CalendarEvent {
        val zone = ZoneId.systemDefault()
        val now = ZonedDateTime.now(zone)
        return CalendarEvent(
            id = "test-${title.hashCode()}",
            calendarId = "c1",
            title = title,
            description = null,
            location = null,
            startTime = now.plusHours(1),
            endTime = now.plusHours(1).plusMinutes(durationMin.toLong()),
            isAllDay = false,
            isRecurring = recurring,
            recurrenceRule = if (recurring) "FREQ=WEEKLY" else null,
            organizerEmail = "organizer@co.com",
            selfIsOrganizer = false,
            attendees = (1..attendeeCount).map { i ->
                EventAttendee(
                    email = "attendee$i@co.com",
                    name = "Attendee $i",
                    isOrganizer = i == 1,
                    attendanceStatus = AttendanceStatus.ACCEPTED,
                    isSelf = false,
                )
            },
            status = EventStatus.CONFIRMED,
            availability = EventAvailability.BUSY,
        )
    }
}
