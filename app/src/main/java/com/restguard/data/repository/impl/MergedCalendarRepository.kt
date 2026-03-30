package com.restguard.data.repository.impl

import com.restguard.data.auth.GoogleAuthManager
import com.restguard.data.repository.impl.platform.CalendarProviderRepository
import com.restguard.data.repository.impl.platform.GoogleCalendarApiRepository
import com.restguard.domain.model.CalendarEvent
import com.restguard.domain.model.CalendarInfo
import com.restguard.domain.repository.CalendarRepository
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.ZonedDateTime

class MergedCalendarRepository(
    private val systemRepo: CalendarProviderRepository,
    private val googleApiRepo: GoogleCalendarApiRepository,
    private val authManager: GoogleAuthManager,
) : CalendarRepository {

    override suspend fun getAvailableCalendars(): List<CalendarInfo> {
        val system = systemRepo.getAvailableCalendars()
        if (!authManager.hasAccounts()) return system
        val google = try { googleApiRepo.getAvailableCalendars() } catch (_: Exception) { emptyList() }
        return deduplicateCalendars(system, google)
    }

    override suspend fun getEvents(from: ZonedDateTime, to: ZonedDateTime): List<CalendarEvent> {
        val system = systemRepo.getEvents(from, to)
        if (!authManager.hasAccounts()) return system
        val google = try { googleApiRepo.getEvents(from, to) } catch (_: Exception) { emptyList() }
        return deduplicateEvents(system, google)
    }

    override suspend fun getEventById(id: String): CalendarEvent? {
        return if (id.startsWith("gapi_")) {
            googleApiRepo.getEventById(id)
        } else {
            systemRepo.getEventById(id)
        }
    }

    override suspend fun getEventsForDate(date: LocalDate): List<CalendarEvent> {
        val system = systemRepo.getEventsForDate(date)
        if (!authManager.hasAccounts()) return system
        val google = try { googleApiRepo.getEventsForDate(date) } catch (_: Exception) { emptyList() }
        return deduplicateEvents(system, google)
    }

    override suspend fun deleteEvent(eventId: String): Boolean {
        return systemRepo.deleteEvent(eventId)
    }

    override suspend fun updateEventTime(eventId: String, newStart: ZonedDateTime, newEnd: ZonedDateTime): Boolean {
        return systemRepo.updateEventTime(eventId, newStart, newEnd)
    }

    override fun observeEventsForDateRange(from: LocalDate, to: LocalDate): Flow<List<CalendarEvent>> {
        return systemRepo.observeEventsForDateRange(from, to)
    }

    /**
     * Prefer Google API calendars over system ones.
     * Match by account email + display name (case-insensitive).
     */
    private fun deduplicateCalendars(
        system: List<CalendarInfo>,
        google: List<CalendarInfo>,
    ): List<CalendarInfo> {
        if (google.isEmpty()) return system
        if (system.isEmpty()) return google

        val googleKeys = google.map { calendarKey(it) }.toSet()
        val uniqueSystem = system.filter { calendarKey(it) !in googleKeys }
        return google + uniqueSystem
    }

    private fun calendarKey(c: CalendarInfo): String {
        return "${c.accountName.lowercase().trim()}|${c.displayName.lowercase().trim()}"
    }

    /**
     * Prefer Google API events over system ones.
     * Match by title + start time (within 1 minute).
     */
    private fun deduplicateEvents(
        system: List<CalendarEvent>,
        google: List<CalendarEvent>,
    ): List<CalendarEvent> {
        if (google.isEmpty()) return system
        if (system.isEmpty()) return google

        val googleKeys = google.map { eventKey(it) }.toSet()
        val uniqueSystem = system.filter { eventKey(it) !in googleKeys }
        return google + uniqueSystem
    }

    private fun eventKey(e: CalendarEvent): String {
        val roundedStart = e.startTime.toEpochSecond() / 60 // 1-minute granularity
        return "${e.title.lowercase().trim()}|$roundedStart"
    }
}
