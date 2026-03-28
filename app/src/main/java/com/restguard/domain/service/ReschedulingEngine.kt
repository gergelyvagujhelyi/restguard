package com.restguard.domain.service

import com.restguard.domain.model.*
import com.restguard.domain.repository.CalendarRepository
import com.restguard.domain.repository.StressRepository
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID
import javax.inject.Inject

/**
 * Finds optimal alternative time slots for rescheduling meetings.
 *
 * ## Slot Scoring (lower = better)
 * slotScore = dayStressWeight × predictedDayStress
 *           + densityWeight × meetingCountOnDay
 *           + preferenceWeight × timePreferencePenalty
 *
 * ## Constraints
 * - Within working hours (configurable, default 9-17)
 * - Minimum buffer between meetings (default 15 min)
 * - No weekends (configurable)
 * - Prefer same week, then next week
 * - Respect event duration
 * - Check no overlap with existing events
 */
class ReschedulingEngine @Inject constructor(
    private val calendarRepo: CalendarRepository,
    private val stressScoring: StressScoringService,
) {
    data class Config(
        val workStartHour: Int = 9,
        val workEndHour: Int = 17,
        val bufferMinutes: Int = 15,
        val includeWeekends: Boolean = false,
        val searchDays: Int = 10,
        val maxResults: Int = 5,
        val slotGranularityMinutes: Int = 30, // check slots every 30 min
    )

    /**
     * Find alternative time slots for a given event.
     */
    suspend fun findAlternatives(
        event: CalendarEvent,
        config: Config = Config(),
    ): List<RescheduleOption> {
        val eventDuration = Duration.between(event.startTime, event.endTime)
        val today = LocalDate.now()
        val zone = ZoneId.systemDefault()

        val candidates = mutableListOf<RescheduleOption>()

        // Search from tomorrow (never reschedule to today — same-day restriction)
        for (dayOffset in 1..config.searchDays) {
            val date = today.plusDays(dayOffset.toLong())
            val dayOfWeek = date.dayOfWeek.value

            // Skip weekends unless configured
            if (!config.includeWeekends && dayOfWeek >= 6) continue

            val existingEvents = calendarRepo.getEventsForDate(date)
                .filter { !it.isAllDay && it.status != EventStatus.CANCELLED }
                .sortedBy { it.startTime }

            val predictions = stressScoring.predictStress(dayOffset)
            val dayStress = predictions.lastOrNull()?.predictedScore ?: 30

            // Generate candidate slots
            val slots = generateSlots(
                date = date,
                zone = zone,
                eventDuration = eventDuration,
                existingEvents = existingEvents,
                config = config,
            )

            for (slot in slots) {
                val score = scoreSlot(
                    slot = slot,
                    dayStress = dayStress,
                    meetingCount = existingEvents.size,
                    config = config,
                )

                candidates.add(
                    RescheduleOption(
                        id = UUID.randomUUID().toString(),
                        originalEventId = event.id,
                        proposedStart = slot.first,
                        proposedEnd = slot.second,
                        dayStressScore = dayStress,
                        meetingCountOnDay = existingEvents.size,
                        reason = buildSlotReason(date, dayStress, existingEvents.size, slot.first),
                        rank = score,
                    )
                )
            }
        }

        // Sort by score (lower = better) and take top N
        return candidates
            .sortedBy { it.rank }
            .take(config.maxResults)
            .mapIndexed { index, option -> option.copy(rank = index + 1) }
    }

    // ─── Internal ───────────────────────────────────────────

    private fun generateSlots(
        date: LocalDate,
        zone: ZoneId,
        eventDuration: Duration,
        existingEvents: List<CalendarEvent>,
        config: Config,
    ): List<Pair<ZonedDateTime, ZonedDateTime>> {
        val slots = mutableListOf<Pair<ZonedDateTime, ZonedDateTime>>()
        val startOfDay = date.atTime(config.workStartHour, 0).atZone(zone)
        val endOfDay = date.atTime(config.workEndHour, 0).atZone(zone)
        val granularity = Duration.ofMinutes(config.slotGranularityMinutes.toLong())
        val buffer = Duration.ofMinutes(config.bufferMinutes.toLong())

        var current = startOfDay

        while (current.plus(eventDuration) <= endOfDay) {
            val slotEnd = current.plus(eventDuration)

            // Check for overlaps with existing events (including buffer)
            val hasConflict = existingEvents.any { existing ->
                val existingStart = existing.startTime.minus(buffer)
                val existingEnd = existing.endTime.plus(buffer)
                current < existingEnd && slotEnd > existingStart
            }

            if (!hasConflict) {
                slots.add(current to slotEnd)
            }

            current = current.plus(granularity)
        }

        return slots
    }

    private fun scoreSlot(
        slot: Pair<ZonedDateTime, ZonedDateTime>,
        dayStress: Int,
        meetingCount: Int,
        config: Config,
    ): Int {
        var score = 0

        // Prefer low-stress days (0-100 contribution)
        score += dayStress

        // Prefer days with fewer meetings (0-50 contribution)
        score += (meetingCount * 8).coerceAtMost(50)

        // Prefer mid-morning and early afternoon (10-11, 14-15)
        val hour = slot.first.hour
        val timePenalty = when (hour) {
            in 10..11 -> 0   // ideal morning
            in 14..15 -> 5   // good afternoon
            in 9..9 -> 10    // early
            in 13..13 -> 10  // post-lunch
            in 16..17 -> 15  // late
            else -> 25       // outside preferred times
        }
        score += timePenalty

        // Prefer sooner dates (slight penalty per day)
        val daysOut = Duration.between(
            ZonedDateTime.now(),
            slot.first
        ).toDays().toInt()
        score += (daysOut * 2).coerceAtMost(20)

        return score
    }

    private fun buildSlotReason(
        date: LocalDate,
        dayStress: Int,
        meetingCount: Int,
        time: ZonedDateTime,
    ): String {
        val stressLabel = when {
            dayStress < 30 -> "low stress"
            dayStress < 55 -> "moderate stress"
            dayStress < 80 -> "high stress"
            else -> "very high stress"
        }
        return "$date has $stressLabel ($dayStress/100) with $meetingCount other meetings. " +
            "${time.toLocalTime()} is a good slot."
    }
}
