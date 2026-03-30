package com.restguard.data.repository.impl.platform

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.provider.CalendarContract
import com.restguard.data.preferences.UserPreferences
import com.restguard.domain.model.*
import com.restguard.domain.repository.CalendarRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.*

/**
 * Real Android CalendarProvider implementation.
 *
 * Reads from and writes to the system calendar via ContentResolver.
 * Works with Google Calendar, Samsung Calendar, and any CalendarProvider-compatible app.
 */
class CalendarProviderRepository(
    private val context: Context,
    private val userPreferences: UserPreferences,
) : CalendarRepository {

    private val contentResolver: ContentResolver = context.contentResolver
    private val _eventsFlow = MutableStateFlow<List<CalendarEvent>>(emptyList())

    private val calendarProjection = arrayOf(
        CalendarContract.Calendars._ID,                    // 0
        CalendarContract.Calendars.ACCOUNT_NAME,           // 1
        CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,  // 2
        CalendarContract.Calendars.CALENDAR_COLOR,         // 3
        CalendarContract.Calendars.IS_PRIMARY,             // 4
    )

    // ─── Projections ────────────────────────────────────────

    private val eventProjection = arrayOf(
        CalendarContract.Events._ID,
        CalendarContract.Events.CALENDAR_ID,
        CalendarContract.Events.TITLE,
        CalendarContract.Events.DESCRIPTION,
        CalendarContract.Events.EVENT_LOCATION,
        CalendarContract.Events.DTSTART,
        CalendarContract.Events.DTEND,
        CalendarContract.Events.ALL_DAY,
        CalendarContract.Events.RRULE,
        CalendarContract.Events.ORGANIZER,
        CalendarContract.Events.SELF_ATTENDEE_STATUS,
        CalendarContract.Events.STATUS,
        CalendarContract.Events.AVAILABILITY,
    )

    private val instanceProjection = arrayOf(
        CalendarContract.Instances.EVENT_ID,         // 0
        CalendarContract.Instances.CALENDAR_ID,      // 1
        CalendarContract.Instances.TITLE,            // 2
        CalendarContract.Instances.DESCRIPTION,      // 3
        CalendarContract.Instances.EVENT_LOCATION,   // 4
        CalendarContract.Instances.BEGIN,             // 5 — instance start (not DTSTART)
        CalendarContract.Instances.END,              // 6 — instance end (not DTEND)
        CalendarContract.Instances.ALL_DAY,          // 7
        CalendarContract.Instances.RRULE,            // 8
        CalendarContract.Instances.ORGANIZER,        // 9
        CalendarContract.Instances.SELF_ATTENDEE_STATUS, // 10
        CalendarContract.Instances.STATUS,
        CalendarContract.Instances.AVAILABILITY,
    )

    private val attendeeProjection = arrayOf(
        CalendarContract.Attendees.ATTENDEE_EMAIL,
        CalendarContract.Attendees.ATTENDEE_NAME,
        CalendarContract.Attendees.ATTENDEE_RELATIONSHIP,
        CalendarContract.Attendees.ATTENDEE_STATUS,
    )

    // ─── Read operations ────────────────────────────────────

    override suspend fun getAvailableCalendars(): List<CalendarInfo> {
        return withContext(Dispatchers.IO) {
            val calendars = mutableListOf<CalendarInfo>()
            try {
                val cursor = contentResolver.query(
                    CalendarContract.Calendars.CONTENT_URI,
                    calendarProjection,
                    null,
                    null,
                    "${CalendarContract.Calendars.ACCOUNT_NAME} ASC",
                )
                cursor?.use {
                    while (it.moveToNext()) {
                        calendars.add(
                            CalendarInfo(
                                id = it.getLong(0).toString(),
                                accountName = it.getString(1) ?: "",
                                displayName = it.getString(2) ?: "",
                                color = if (it.isNull(3)) 0 else it.getInt(3),
                                isPrimary = if (it.isNull(4)) false else it.getInt(4) == 1,
                            )
                        )
                    }
                }
            } catch (_: Exception) {
                // Fallback: derive calendars from events if Calendars table fails
            }

            // If the Calendars table returned nothing or failed, derive from recent events
            if (calendars.isEmpty()) {
                val now = System.currentTimeMillis()
                val monthAgo = now - 30L * 24 * 60 * 60 * 1000
                val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
                ContentUris.appendId(builder, monthAgo)
                ContentUris.appendId(builder, now + 7L * 24 * 60 * 60 * 1000)
                val cursor = contentResolver.query(
                    builder.build(),
                    arrayOf(CalendarContract.Instances.CALENDAR_ID),
                    null, null, null,
                )
                val seenIds = mutableSetOf<String>()
                cursor?.use {
                    while (it.moveToNext()) {
                        val calId = it.getLong(0).toString()
                        if (seenIds.add(calId)) {
                            calendars.add(
                                CalendarInfo(
                                    id = calId,
                                    accountName = "Account",
                                    displayName = "Calendar $calId",
                                    color = 0,
                                    isPrimary = false,
                                )
                            )
                        }
                    }
                }
            }

            calendars
        }
    }

    override suspend fun getEvents(from: ZonedDateTime, to: ZonedDateTime): List<CalendarEvent> {
        return withContext(Dispatchers.IO) {
            queryEvents(from.toInstant().toEpochMilli(), to.toInstant().toEpochMilli())
        }
    }

    override suspend fun getEventById(id: String): CalendarEvent? {
        return withContext(Dispatchers.IO) {
            val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id.toLongOrNull() ?: return@withContext null)
            val cursor = contentResolver.query(uri, eventProjection, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    parseEvent(it)
                } else null
            }
        }
    }

    override suspend fun getEventsForDate(date: LocalDate): List<CalendarEvent> {
        val zone = ZoneId.systemDefault()
        val startOfDay = date.atStartOfDay(zone)
        val endOfDay = date.plusDays(1).atStartOfDay(zone)
        return getEvents(startOfDay, endOfDay)
    }

    override fun observeEventsForDateRange(from: LocalDate, to: LocalDate): Flow<List<CalendarEvent>> {
        // ContentObserver-based reactive flow would go here.
        // For now, returns the state flow updated on each query.
        return _eventsFlow
    }

    // ─── Write operations ───────────────────────────────────

    override suspend fun deleteEvent(eventId: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val uri = ContentUris.withAppendedId(
                    CalendarContract.Events.CONTENT_URI,
                    eventId.toLong(),
                )
                val rows = contentResolver.delete(uri, null, null)
                rows > 0
            } catch (e: Exception) {
                false
            }
        }
    }

    override suspend fun updateEventTime(
        eventId: String,
        newStart: ZonedDateTime,
        newEnd: ZonedDateTime,
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val uri = ContentUris.withAppendedId(
                    CalendarContract.Events.CONTENT_URI,
                    eventId.toLong(),
                )
                val values = ContentValues().apply {
                    put(CalendarContract.Events.DTSTART, newStart.toInstant().toEpochMilli())
                    put(CalendarContract.Events.DTEND, newEnd.toInstant().toEpochMilli())
                }
                val rows = contentResolver.update(uri, values, null, null)
                rows > 0
            } catch (e: Exception) {
                false
            }
        }
    }

    // ─── Query helpers ──────────────────────────────────────

    private suspend fun queryEvents(startMillis: Long, endMillis: Long): List<CalendarEvent> {
        // Use Instances table to include recurring event occurrences
        val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(builder, startMillis)
        ContentUris.appendId(builder, endMillis)

        // Filter by selected calendars: null = all, empty = none, non-empty = specific
        val selectedIds = userPreferences.selectedCalendarIds.first()
        val selection = when {
            selectedIds == null -> null // all calendars
            selectedIds.isEmpty() -> "1=0" // none selected — return nothing
            else -> {
                val placeholders = selectedIds.joinToString(",") { "?" }
                "${CalendarContract.Instances.CALENDAR_ID} IN ($placeholders)"
            }
        }
        val selectionArgs = if (selectedIds != null && selectedIds.isNotEmpty()) {
            selectedIds.toTypedArray()
        } else null

        val cursor = contentResolver.query(
            builder.build(),
            instanceProjection,
            selection,
            selectionArgs,
            "${CalendarContract.Instances.BEGIN} ASC",
        )

        val events = mutableListOf<CalendarEvent>()
        cursor?.use {
            while (it.moveToNext()) {
                val parsed = parseInstance(it)
                if (parsed != null) {
                    events.add(parsed)
                }
            }
        }

        _eventsFlow.value = events
        return events
    }

    private fun parseInstance(cursor: Cursor): CalendarEvent? {
        return try {
            val id = if (cursor.isNull(0)) "0" else cursor.getLong(0).toString()
            val calendarId = cursor.getString(1) ?: "unknown"
            val title = cursor.getString(2) ?: "(No title)"
            val description = cursor.getString(3)
            val location = cursor.getString(4)
            val dtStart = if (cursor.isNull(5)) return null else cursor.getLong(5)
            val dtEnd = if (cursor.isNull(6)) dtStart + 3600000 else cursor.getLong(6).let { if (it == 0L) dtStart + 3600000 else it }
            val allDay = if (cursor.isNull(7)) false else cursor.getInt(7) == 1
            val rrule = cursor.getString(8)
            val organizer = cursor.getString(9)
            val selfStatus = if (cursor.isNull(10)) 0 else cursor.getInt(10)
            val status = if (cursor.isNull(11)) 0 else cursor.getInt(11)
            val availability = if (cursor.isNull(12)) 0 else cursor.getInt(12)

            val zone = ZoneId.systemDefault()
            val startTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(dtStart), zone)
            val endTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(dtEnd), zone)

            val attendees = try { queryAttendees(id.toLong()) } catch (_: Exception) { emptyList() }

            CalendarEvent(
                id = id,
                calendarId = calendarId,
                title = title,
                description = description,
                location = location,
                startTime = startTime,
                endTime = endTime,
                isAllDay = allDay,
                isRecurring = rrule != null,
                recurrenceRule = rrule,
                organizerEmail = organizer,
                selfIsOrganizer = attendees.any { it.isSelf && it.isOrganizer },
                attendees = attendees,
                status = when (status) {
                    CalendarContract.Events.STATUS_CONFIRMED -> EventStatus.CONFIRMED
                    CalendarContract.Events.STATUS_TENTATIVE -> EventStatus.TENTATIVE
                    CalendarContract.Events.STATUS_CANCELED -> EventStatus.CANCELLED
                    else -> EventStatus.CONFIRMED
                },
                availability = when (availability) {
                    CalendarContract.Events.AVAILABILITY_BUSY -> EventAvailability.BUSY
                    CalendarContract.Events.AVAILABILITY_FREE -> EventAvailability.FREE
                    CalendarContract.Events.AVAILABILITY_TENTATIVE -> EventAvailability.TENTATIVE
                    else -> EventAvailability.BUSY
                },
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun parseEvent(cursor: Cursor): CalendarEvent? {
        val id = cursor.getLong(0).toString()
        val calendarId = cursor.getString(1) ?: return null
        val title = cursor.getString(2) ?: "(No title)"
        val description = cursor.getString(3)
        val location = cursor.getString(4)
        val dtStart = cursor.getLong(5)
        val dtEnd = cursor.getLong(6).let { if (it == 0L) dtStart + 3600000 else it }
        val allDay = cursor.getInt(7) == 1
        val rrule = cursor.getString(8)
        val organizer = cursor.getString(9)
        val selfStatus = cursor.getInt(10)
        val status = cursor.getInt(11)
        val availability = cursor.getInt(12)

        val zone = ZoneId.systemDefault()
        val startTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(dtStart), zone)
        val endTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(dtEnd), zone)

        val attendees = queryAttendees(id.toLong())

        return CalendarEvent(
            id = id,
            calendarId = calendarId,
            title = title,
            description = description,
            location = location,
            startTime = startTime,
            endTime = endTime,
            isAllDay = allDay,
            isRecurring = rrule != null,
            recurrenceRule = rrule,
            organizerEmail = organizer,
            selfIsOrganizer = attendees.any { it.isSelf && it.isOrganizer },
            attendees = attendees,
            status = when (status) {
                CalendarContract.Events.STATUS_CONFIRMED -> EventStatus.CONFIRMED
                CalendarContract.Events.STATUS_TENTATIVE -> EventStatus.TENTATIVE
                CalendarContract.Events.STATUS_CANCELED -> EventStatus.CANCELLED
                else -> EventStatus.CONFIRMED
            },
            availability = when (availability) {
                CalendarContract.Events.AVAILABILITY_BUSY -> EventAvailability.BUSY
                CalendarContract.Events.AVAILABILITY_FREE -> EventAvailability.FREE
                CalendarContract.Events.AVAILABILITY_TENTATIVE -> EventAvailability.TENTATIVE
                else -> EventAvailability.BUSY
            },
        )
    }

    private fun queryAttendees(eventId: Long): List<EventAttendee> {
        val selection = "${CalendarContract.Attendees.EVENT_ID} = ?"
        val selectionArgs = arrayOf(eventId.toString())

        val cursor = contentResolver.query(
            CalendarContract.Attendees.CONTENT_URI,
            attendeeProjection,
            selection,
            selectionArgs,
            null,
        )

        val attendees = mutableListOf<EventAttendee>()
        cursor?.use {
            while (it.moveToNext()) {
                val email = it.getString(0) ?: continue
                val name = it.getString(1)
                val relationship = it.getInt(2)
                val attendeeStatus = it.getInt(3)

                attendees.add(
                    EventAttendee(
                        email = email,
                        name = name,
                        isOrganizer = relationship == CalendarContract.Attendees.RELATIONSHIP_ORGANIZER,
                        attendanceStatus = when (attendeeStatus) {
                            CalendarContract.Attendees.ATTENDEE_STATUS_ACCEPTED -> AttendanceStatus.ACCEPTED
                            CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED -> AttendanceStatus.DECLINED
                            CalendarContract.Attendees.ATTENDEE_STATUS_TENTATIVE -> AttendanceStatus.TENTATIVE
                            else -> AttendanceStatus.NONE
                        },
                        isSelf = relationship == CalendarContract.Attendees.RELATIONSHIP_ATTENDEE ||
                            relationship == CalendarContract.Attendees.RELATIONSHIP_ORGANIZER,
                    )
                )
            }
        }

        return attendees
    }
}
