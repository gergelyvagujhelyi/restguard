package com.restguard.domain.service

import com.restguard.data.repository.impl.FakeLlmClient
import com.restguard.domain.model.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class MessagingEngineTest {

    private lateinit var engine: MessagingEngine

    @Before
    fun setup() {
        engine = MessagingEngine(FakeLlmClient())
    }

    @Test
    fun `cancellation email has subject and body`() = runTest {
        val event = createEvent("Sprint Planning")
        val draft = engine.draftCancellationEmail(event, "Alice")

        assertNotNull(draft.subject)
        assertTrue(draft.subject!!.isNotEmpty())
        assertTrue(draft.body.isNotEmpty())
        assertTrue(draft.body.contains("Sprint Planning"))
        assertTrue(draft.isEditable)
    }

    @Test
    fun `reschedule email includes new time`() = runTest {
        val event = createEvent("Design Review")
        val option = RescheduleOption(
            id = "opt1",
            originalEventId = event.id,
            proposedStart = ZonedDateTime.now().plusDays(3).withHour(10).withMinute(0),
            proposedEnd = ZonedDateTime.now().plusDays(3).withHour(11).withMinute(0),
            dayStressScore = 25,
            meetingCountOnDay = 2,
            reason = "Lower stress day",
            rank = 1,
        )

        val draft = engine.draftRescheduleEmail(event, "Bob", option)
        assertNotNull(draft.subject)
        assertTrue(draft.body.contains("Design Review"))
        assertTrue(draft.isEditable)
    }

    @Test
    fun `short message is concise`() = runTest {
        val event = createEvent("Coffee Chat")
        val draft = engine.draftShortMessage(event, "Carol", isCancellation = true)

        assertNull("SMS should not have subject", draft.subject)
        assertTrue("SMS should be short", draft.body.length < 200)
        assertTrue(draft.body.contains("Coffee Chat"))
    }

    @Test
    fun `null recipient name uses fallback`() = runTest {
        val event = createEvent("Sync")
        val draft = engine.draftCancellationEmail(event, recipientName = null)

        assertTrue(draft.body.isNotEmpty())
        // Should use "there" as fallback
    }

    // ─── Helper ─────────────────────────────────────────────

    private fun createEvent(title: String): CalendarEvent {
        val zone = ZoneId.systemDefault()
        val start = ZonedDateTime.now(zone).plusDays(1).withHour(14)
        return CalendarEvent(
            id = "test-msg-${title.hashCode()}",
            calendarId = "c1",
            title = title,
            description = null,
            location = null,
            startTime = start,
            endTime = start.plusMinutes(60),
            isAllDay = false,
            isRecurring = false,
            recurrenceRule = null,
            organizerEmail = "org@co.com",
            selfIsOrganizer = false,
            attendees = listOf(
                EventAttendee("alice@co.com", "Alice", true, AttendanceStatus.ACCEPTED, false),
            ),
            status = EventStatus.CONFIRMED,
            availability = EventAvailability.BUSY,
        )
    }
}
