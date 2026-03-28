package com.restguard.data.repository.impl

import com.restguard.domain.model.*
import com.restguard.domain.repository.CalendarRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Fake calendar data for development. Generates realistic meeting patterns.
 */
class FakeCalendarRepository : CalendarRepository {

    private val zone = ZoneId.systemDefault()
    private val events = MutableStateFlow<List<CalendarEvent>>(emptyList())

    init {
        events.value = generateSampleEvents()
    }

    override suspend fun getEvents(from: ZonedDateTime, to: ZonedDateTime): List<CalendarEvent> {
        return events.value.filter { it.startTime >= from && it.startTime <= to }
    }

    override suspend fun getEventById(id: String): CalendarEvent? {
        return events.value.find { it.id == id }
    }

    override suspend fun getEventsForDate(date: LocalDate): List<CalendarEvent> {
        return events.value.filter { it.startTime.toLocalDate() == date }
    }

    override suspend fun deleteEvent(eventId: String): Boolean {
        events.value = events.value.filter { it.id != eventId }
        return true
    }

    override suspend fun updateEventTime(eventId: String, newStart: ZonedDateTime, newEnd: ZonedDateTime): Boolean {
        events.value = events.value.map {
            if (it.id == eventId) it.copy(startTime = newStart, endTime = newEnd) else it
        }
        return true
    }

    override fun observeEventsForDateRange(from: LocalDate, to: LocalDate): Flow<List<CalendarEvent>> {
        return events.map { list ->
            list.filter { it.startTime.toLocalDate() in from..to }
        }
    }

    private fun generateSampleEvents(): List<CalendarEvent> {
        val today = LocalDate.now()
        val events = mutableListOf<CalendarEvent>()

        // ─── Today: packed schedule (for extreme stress testing) ────
        events.addAll(listOf(
            createEvent("1", today, 9, 0, 60, "Team Standup",
                recurring = true, attendees = listOf("alice@co.com", "bob@co.com", "carol@co.com")),
            createEvent("2", today, 10, 0, 60, "1:1 with Manager",
                attendees = listOf("manager@co.com")),
            createEvent("3", today, 11, 0, 30, "Coffee Chat with Design",
                attendees = listOf("designer@co.com")),
            createEvent("4", today, 13, 0, 60, "Sprint Planning",
                recurring = true, attendees = listOf("alice@co.com", "bob@co.com", "carol@co.com",
                    "dave@co.com", "eve@co.com")),
            createEvent("5", today, 14, 30, 90, "Customer Demo",
                description = "Q2 feature demo for Acme Corp",
                attendees = listOf("sales@co.com", "customer@acme.com", "pm@co.com")),
            createEvent("6", today, 16, 30, 30, "All-Hands Town Hall",
                recurring = true, attendees = (1..50).map { "employee$it@co.com" }),
        ))

        // ─── Tomorrow: moderate day ────
        val tomorrow = today.plusDays(1)
        events.addAll(listOf(
            createEvent("7", tomorrow, 9, 30, 30, "Team Standup",
                recurring = true, attendees = listOf("alice@co.com", "bob@co.com")),
            createEvent("8", tomorrow, 11, 0, 60, "Design Review",
                description = "Review new dashboard mockups",
                attendees = listOf("designer@co.com", "pm@co.com")),
            createEvent("9", tomorrow, 14, 0, 30, "Book Club",
                description = "Optional team book club",
                attendees = listOf("alice@co.com", "bob@co.com")),
        ))

        // ─── Day after tomorrow: heavy day ────
        val dayAfter = today.plusDays(2)
        events.addAll(listOf(
            createEvent("10", dayAfter, 8, 30, 60, "Early Architecture Review",
                attendees = listOf("architect@co.com", "cto@co.com")),
            createEvent("11", dayAfter, 9, 30, 30, "Team Standup",
                recurring = true, attendees = listOf("alice@co.com", "bob@co.com")),
            createEvent("12", dayAfter, 10, 0, 60, "Sprint Retro",
                recurring = true, attendees = listOf("alice@co.com", "bob@co.com", "carol@co.com")),
            createEvent("13", dayAfter, 11, 0, 60, "1:1 with Manager",
                attendees = listOf("manager@co.com")),
            createEvent("14", dayAfter, 13, 0, 120, "Quarterly Business Review",
                description = "Prep-heavy: bring updated metrics and roadmap slides",
                attendees = listOf("vp@co.com", "director@co.com", "pm@co.com", "manager@co.com")),
            createEvent("15", dayAfter, 15, 30, 60, "Customer Escalation Call",
                description = "Urgent: customer X experiencing outages",
                attendees = listOf("support@co.com", "customer@clientx.com")),
            createEvent("16", dayAfter, 17, 0, 30, "Social Happy Hour",
                description = "Optional virtual happy hour",
                attendees = listOf("alice@co.com", "bob@co.com")),
        ))

        // ─── 3 days out: light day ────
        val day3 = today.plusDays(3)
        events.addAll(listOf(
            createEvent("17", day3, 10, 0, 30, "Team Standup",
                recurring = true, attendees = listOf("alice@co.com", "bob@co.com")),
            createEvent("18", day3, 14, 0, 60, "Async Sync — FYI Only",
                description = "FYI meeting, attendance optional",
                attendees = listOf("alice@co.com")),
        ))

        return events
    }

    private fun createEvent(
        id: String,
        date: LocalDate,
        hour: Int,
        minute: Int,
        durationMin: Int,
        title: String,
        description: String? = null,
        recurring: Boolean = false,
        attendees: List<String> = emptyList(),
    ): CalendarEvent {
        val start = date.atTime(hour, minute).atZone(zone)
        return CalendarEvent(
            id = id,
            calendarId = "fake_cal_1",
            title = title,
            description = description,
            location = null,
            startTime = start,
            endTime = start.plusMinutes(durationMin.toLong()),
            isAllDay = false,
            isRecurring = recurring,
            recurrenceRule = if (recurring) "FREQ=WEEKLY" else null,
            organizerEmail = if (attendees.isNotEmpty()) attendees.first() else "me@co.com",
            selfIsOrganizer = attendees.isEmpty() || attendees.first() == "me@co.com",
            attendees = attendees.mapIndexed { i, email ->
                EventAttendee(
                    email = email,
                    name = email.substringBefore("@").replaceFirstChar { it.uppercase() },
                    isOrganizer = i == 0,
                    attendanceStatus = AttendanceStatus.ACCEPTED,
                    isSelf = false,
                )
            },
            status = EventStatus.CONFIRMED,
            availability = EventAvailability.BUSY,
        )
    }
}
