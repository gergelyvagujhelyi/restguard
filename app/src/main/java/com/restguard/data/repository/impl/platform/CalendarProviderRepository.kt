package com.restguard.data.repository.impl.platform

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.provider.CalendarContract
import com.restguard.domain.model.*
import com.restguard.domain.repository.CalendarRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
) : CalendarRepository {

    private val contentResolver: ContentResolver = context.contentResolver
    private val _eventsFlow = MutableStateFlow<List<CalendarEvent>>(emptyList())

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
        CalendarContract.Instances.EVENT_ID,
        CalendarContract.Instances.CALENDAR_ID,
        CalendarContract.Instances.TITLE,
        CalendarContract.Instances.DESCRIPTION,
        CalendarContract.Instances.EVENT_LOCATION,
        CalendarContract.Instances.DTSTART,         // instance start
        CalendarContract.Instances.DTEND,            // instance end
        CalendarContract.Instances.ALL_DAY,
        CalendarContract.Instances.RRULE,
        CalendarContract.Instances.ORGANIZER,
        CalendarContract.Instances.SELF_ATTENDEE_STATUS,
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

    private fun queryEvents(startMillis: Long, endMillis: Long): List<CalendarEvent> {
        // Use Instances table to include recurring event occurrences
        val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(builder, startMillis)
        ContentUris.appendId(builder, endMillis)

        val cursor = contentResolver.query(
            builder.build(),
            instanceProjection,
            null,
            null,
            "${CalendarContract.Instances.DTSTART} ASC",
        )

        val events = mutableListOf<CalendarEvent>()
        android.util.Log.d("CalendarRepo", "Instances query: cursor=${cursor != null}, count=${cursor?.count ?: 0}")
        cursor?.use {
            while (it.moveToNext()) {
                val parsed = parseInstance(it)
                if (parsed != null) {
                    events.add(parsed)
                } else {
                    android.util.Log.w("CalendarRepo", "Failed to parse instance at row ${it.position}")
                }
            }
        }
        android.util.Log.d("CalendarRepo", "Parsed ${events.size} events from ${startMillis}..${endMillis}")

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
