package com.restguard.data.remote.google

import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

interface GoogleCalendarApi {

    @GET("calendar/v3/users/me/calendarList")
    suspend fun getCalendarList(
        @Header("Authorization") auth: String,
    ): GoogleCalendarListResponse

    @GET("calendar/v3/calendars/{calendarId}/events")
    suspend fun getEvents(
        @Header("Authorization") auth: String,
        @Path("calendarId") calendarId: String,
        @Query("timeMin") timeMin: String,
        @Query("timeMax") timeMax: String,
        @Query("singleEvents") singleEvents: Boolean = true,
        @Query("orderBy") orderBy: String = "startTime",
        @Query("maxResults") maxResults: Int = 250,
    ): GoogleEventsResponse
}
