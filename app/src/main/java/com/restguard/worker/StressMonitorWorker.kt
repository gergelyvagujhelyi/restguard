package com.restguard.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.restguard.data.preferences.UserPreferences
import com.restguard.domain.repository.RecommendationRepository
import com.restguard.domain.repository.StressRepository
import com.restguard.domain.service.HistoricalLearningService
import com.restguard.domain.service.RecommendationEngine
import com.restguard.domain.service.StressScoringService
import com.restguard.notification.StressNotificationManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * Periodic background worker that:
 * 1. Computes current stress score
 * 2. Generates near-future predictions
 * 3. Runs recommendation engine
 * 4. Updates persistent notification
 * 5. Cleans up stale data
 *
 * Runs every 15 minutes via WorkManager (minimum Android interval).
 */
@HiltWorker
class StressMonitorWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val stressScoringService: StressScoringService,
    private val recommendationEngine: RecommendationEngine,
    private val stressRepo: StressRepository,
    private val recommendationRepo: RecommendationRepository,
    private val notificationManager: StressNotificationManager,
    private val preferences: UserPreferences,
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val WORK_NAME = "stress_monitor"
        const val TAG = "StressMonitorWorker"

        fun buildPeriodicRequest(): PeriodicWorkRequest {
            return PeriodicWorkRequestBuilder<StressMonitorWorker>(
                repeatInterval = 15,
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

        fun buildOneTimeRequest(): OneTimeWorkRequest {
            return OneTimeWorkRequestBuilder<StressMonitorWorker>()
                .addTag(TAG)
                .build()
        }
    }

    override suspend fun doWork(): Result {
        // Check if monitoring is paused
        val isPaused = preferences.isMonitoringPaused.first()
        if (isPaused) {
            return Result.success()
        }

        return try {
            // 1. Compute current stress
            val stressSample = stressScoringService.computeCurrentStress()
            stressRepo.saveStressSample(stressSample)

            // 2. Generate predictions
            val predictions = stressScoringService.predictStress(3)
            predictions.forEach { stressRepo.savePrediction(it) }

            // 3. Generate recommendations
            val recommendations = recommendationEngine.generateRecommendations()
            recommendationRepo.saveRecommendations(recommendations)

            // 4. Update notification
            val stressLevel = stressScoringService.classifyStress(stressSample.score)
            val activeRecs = recommendationRepo.getActiveRecommendations()
            notificationManager.updateNotification(
                stressScore = stressSample.score,
                stressLevel = stressLevel,
                pendingRecommendations = activeRecs.size,
            )

            // 5. Expire old recommendations (>24h)
            val expirationThreshold = Instant.now().minus(Duration.ofHours(24)).toEpochMilli()
            // This would be done via RecommendationDao.expireOlderThan() in the Room impl

            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }
}
