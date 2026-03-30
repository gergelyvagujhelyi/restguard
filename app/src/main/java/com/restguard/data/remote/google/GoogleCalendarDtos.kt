package com.restguard.data.remote.google

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GoogleCalendarListResponse(
    val items: List<GoogleCalendarEntry> = emptyList(),
)

@Serializable
data class GoogleCalendarEntry(
    val id: String,
    val summary: String? = null,
    val backgroundColor: String? = null,
    val primary: Boolean? = null,
)

@Serializable
data class GoogleEventsResponse(
    val items: List<GoogleEventEntry> = emptyList(),
)

@Serializable
data class GoogleEventEntry(
    val id: String,
    val summary: String? = null,
    val description: String? = null,
    val location: String? = null,
    val start: GoogleDateTime? = null,
    val end: GoogleDateTime? = null,
    val attendees: List<GoogleAttendee>? = null,
    val recurrence: List<String>? = null,
    val organizer: GoogleOrganizer? = null,
    val status: String? = null,
    val transparency: String? = null,
    @SerialName("recurringEventId") val recurringEventId: String? = null,
)

@Serializable
data class GoogleDateTime(
    val dateTime: String? = null,
    val date: String? = null,
    val timeZone: String? = null,
)

@Serializable
data class GoogleAttendee(
    val email: String? = null,
    val displayName: String? = null,
    val organizer: Boolean? = null,
    @SerialName("self") val isSelf: Boolean? = null,
    val responseStatus: String? = null,
)

@Serializable
data class GoogleOrganizer(
    val email: String? = null,
    val displayName: String? = null,
    @SerialName("self") val isSelf: Boolean? = null,
)
