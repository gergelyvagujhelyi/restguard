package com.restguard.data.repository.impl.platform

import android.graphics.Color
import com.restguard.data.auth.GoogleAuthManager
import com.restguard.data.preferences.UserPreferences
import com.restguard.data.remote.google.*
import com.restguard.domain.model.*
import com.restguard.domain.repository.CalendarRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import java.time.*
import java.time.format.DateTimeFormatter

class GoogleCalendarApiRepository(
    private val api: GoogleCalendarApi,
    private val authManager: GoogleAuthManager,
    private val userPreferences: UserPreferences,
) : CalendarRepository {

    private val _eventsFlow = MutableStateFlow<List<CalendarEvent>>(emptyList())

    /** Cached calendar list per account. Refreshed at most every 5 minutes. */
    private data class CalendarCache(
        val calendars: Map<String, List<GoogleCalendarEntry>>, // email -> entries
        val timestamp: Long = System.currentTimeMillis(),
    )
    private var calendarCache: CalendarCache? = null
    private val cacheTtlMs = 5 * 60 * 1000L // 5 minutes

    private suspend fun authHeader(email: String): String? {
        val token = authManager.getAccessToken(email) ?: return null
        return "Bearer $token"
    }

    /** Returns cached calendar lists per account, fetching if stale or missing. */
    private suspend fun getCachedCalendarLists(): Map<String, List<GoogleCalendarEntry>> {
        val cache = calendarCache
        val accounts = authManager.accounts.value
        if (cache != null && System.currentTimeMillis() - cache.timestamp < cacheTtlMs
            && cache.calendars.keys == accounts) {
            return cache.calendars
        }

        val result = mutableMapOf<String, List<GoogleCalendarEntry>>()
        for (email in accounts) {
            val auth = authHeader(email) ?: continue
            try {
                result[email] = api.getCalendarList(auth).items
            } catch (_: Exception) { }
        }
        calendarCache = CalendarCache(result)
        return result
    }

    override suspend fun getAvailableCalendars(): List<CalendarInfo> {
        val calendarsByAccount = getCachedCalendarLists()
        if (calendarsByAccount.isEmpty()) return emptyList()

        val seen = mutableSetOf<String>()
        val result = mutableListOf<CalendarInfo>()
        for ((email, entries) in calendarsByAccount) {
            for (entry in entries) {
                if (entry.id in seen) continue
                seen.add(entry.id)
                result.add(
                    CalendarInfo(
                        id = "gapi_${entry.id}",
                        accountName = email,
                        displayName = entry.summary ?: entry.id,
                        color = parseColor(entry.backgroundColor),
                        isPrimary = entry.primary == true,
                        source = CalendarSource.GOOGLE_API,
                    )
                )
            }
        }
        return result
    }

    override suspend fun getEvents(from: ZonedDateTime, to: ZonedDateTime): List<CalendarEvent> {
        val calendarsByAccount = getCachedCalendarLists()
        if (calendarsByAccount.isEmpty()) return emptyList()

        val selectedIds = userPreferences.selectedCalendarIds.first()
        val seenCalendars = mutableSetOf<String>()
        val allEvents = mutableListOf<CalendarEvent>()

        for ((email, entries) in calendarsByAccount) {
            val auth = authHeader(email) ?: continue
            for (cal in entries) {
                if (cal.id in seenCalendars) continue
                seenCalendars.add(cal.id)

                val prefixedId = "gapi_${cal.id}"
                if (selectedIds != null && selectedIds.isNotEmpty() && prefixedId !in selectedIds) continue

                try {
                    val events = api.getEvents(
                        auth = auth,
                        calendarId = cal.id,
                        timeMin = from.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                        timeMax = to.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                    )
                    allEvents.addAll(events.items.mapNotNull { mapEvent(it, prefixedId) })
                } catch (_: Exception) { }
            }
        }

        _eventsFlow.value = allEvents
        return allEvents
    }

    override suspend fun getEventById(id: String): CalendarEvent? = null

    override suspend fun getEventsForDate(date: LocalDate): List<CalendarEvent> {
        val zone = ZoneId.systemDefault()
        return getEvents(date.atStartOfDay(zone), date.plusDays(1).atStartOfDay(zone))
    }

    override suspend fun deleteEvent(eventId: String): Boolean = false
    override suspend fun updateEventTime(eventId: String, newStart: ZonedDateTime, newEnd: ZonedDateTime): Boolean = false
    override fun observeEventsForDateRange(from: LocalDate, to: LocalDate): Flow<List<CalendarEvent>> = _eventsFlow

    private fun mapEvent(entry: GoogleEventEntry, calendarId: String): CalendarEvent? {
        val startTime = parseGoogleDateTime(entry.start) ?: return null
        val endTime = parseGoogleDateTime(entry.end) ?: startTime.plusHours(1)
        val isAllDay = entry.start?.date != null

        return CalendarEvent(
            id = "gapi_${entry.id}",
            calendarId = calendarId,
            title = entry.summary ?: "(No title)",
            description = entry.description,
            location = entry.location,
            startTime = startTime,
            endTime = endTime,
            isAllDay = isAllDay,
            isRecurring = entry.recurringEventId != null || !entry.recurrence.isNullOrEmpty(),
            recurrenceRule = entry.recurrence?.firstOrNull(),
            organizerEmail = entry.organizer?.email,
            selfIsOrganizer = entry.organizer?.isSelf == true,
            attendees = entry.attendees?.mapNotNull { mapAttendee(it) } ?: emptyList(),
            status = when (entry.status) {
                "confirmed" -> EventStatus.CONFIRMED
                "tentative" -> EventStatus.TENTATIVE
                "cancelled" -> EventStatus.CANCELLED
                else -> EventStatus.CONFIRMED
            },
            availability = when (entry.transparency) {
                "transparent" -> EventAvailability.FREE
                "opaque" -> EventAvailability.BUSY
                else -> EventAvailability.BUSY
            },
        )
    }

    private fun mapAttendee(a: GoogleAttendee): EventAttendee? {
        val email = a.email ?: return null
        return EventAttendee(
            email = email,
            name = a.displayName,
            isOrganizer = a.organizer == true,
            attendanceStatus = when (a.responseStatus) {
                "accepted" -> AttendanceStatus.ACCEPTED
                "declined" -> AttendanceStatus.DECLINED
                "tentative" -> AttendanceStatus.TENTATIVE
                else -> AttendanceStatus.NONE
            },
            isSelf = a.isSelf == true,
        )
    }

    private fun parseGoogleDateTime(dt: GoogleDateTime?): ZonedDateTime? {
        if (dt == null) return null
        val zone = if (dt.timeZone != null) ZoneId.of(dt.timeZone) else ZoneId.systemDefault()
        return when {
            dt.dateTime != null -> ZonedDateTime.parse(dt.dateTime)
            dt.date != null -> LocalDate.parse(dt.date).atStartOfDay(zone)
            else -> null
        }
    }

    private fun parseColor(hex: String?): Int {
        if (hex == null) return 0
        return try { Color.parseColor(hex) } catch (_: Exception) { 0 }
    }
}
