package com.restguard.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.restguard.data.preferences.UserPreferences
import com.restguard.domain.repository.CalendarRepository
import com.restguard.domain.service.HistoricalLearningService
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * Periodic worker that detects recently completed meetings and triggers
 * EMA-based historical learning for each one.
 *
 * Runs every 30 minutes. Looks for meetings that ended in the last 35 minutes
 * (with 5-minute overlap to avoid missing any).
 *
 * This enables the stress scoring system to learn which meeting types
 * are most stressful for this user over time.
 */
@HiltWorker
class MeetingCompletionWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val calendarRepo: CalendarRepository,
    private val historicalLearningService: HistoricalLearningService,
    private val preferences: UserPreferences,
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val WORK_NAME = "meeting_completion"
        const val TAG = "MeetingCompletionWorker"
        const val LOOKBACK_MINUTES = 35L

        fun buildPeriodicRequest(): PeriodicWorkRequest {
            return PeriodicWorkRequestBuilder<MeetingCompletionWorker>(
                repeatInterval = 30,
                repeatIntervalTimeUnit = TimeUnit.MINUTES,
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .addTag(TAG)
                .build()
        }
    }

    override suspend fun doWork(): Result {
        val isPaused = preferences.isMonitoringPaused.first()
        if (isPaused) return Result.success()

        return try {
            val now = ZonedDateTime.now()
            val lookbackStart = now.minusMinutes(LOOKBACK_MINUTES)

            // Get events that ended in the lookback window
            val todayEvents = calendarRepo.getEventsForDate(now.toLocalDate())
            val recentlyEnded = todayEvents.filter { event ->
                !event.isAllDay &&
                    event.endTime.isAfter(lookbackStart) &&
                    event.endTime.isBefore(now)
            }

            for (event in recentlyEnded) {
                historicalLearningService.processCompletedMeeting(event)
            }

            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}
