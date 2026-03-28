package com.restguard.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.restguard.data.local.dao.AuditDao
import com.restguard.data.local.dao.FeedbackDao
import com.restguard.data.local.dao.RecommendationDao
import com.restguard.data.local.dao.StressDao
import com.restguard.data.preferences.UserPreferences
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Daily cleanup worker that enforces data retention policies.
 *
 * Deletes:
 * - Stress samples older than retention period (default 90 days)
 * - Expired recommendations (>24h old, still pending)
 * - Old audit logs (>30 days)
 * - Old user feedback (>retention days)
 * - Old stress predictions (past dates)
 */
@HiltWorker
class DataCleanupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val stressDao: StressDao,
    private val recommendationDao: RecommendationDao,
    private val feedbackDao: FeedbackDao,
    private val auditDao: AuditDao,
    private val preferences: UserPreferences,
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val WORK_NAME = "data_cleanup"
        const val TAG = "DataCleanupWorker"
        const val AUDIT_RETENTION_DAYS = 30L

        fun buildPeriodicRequest(): PeriodicWorkRequest {
            return PeriodicWorkRequestBuilder<DataCleanupWorker>(
                repeatInterval = 1,
                repeatIntervalTimeUnit = TimeUnit.DAYS,
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresBatteryNotLow(true)
                        .setRequiresDeviceIdle(true)
                        .build()
                )
                .addTag(TAG)
                .build()
        }
    }

    override suspend fun doWork(): Result {
        return try {
            val retentionDays = preferences.dataRetentionDays.first().toLong()
            val now = Instant.now()

            // Stress samples
            val stressCutoff = now.minus(Duration.ofDays(retentionDays)).toEpochMilli()
            stressDao.deleteOlderThan(stressCutoff)

            // Recommendations: expire stale pending ones (>24h)
            val expireCutoff = now.minus(Duration.ofHours(24)).toEpochMilli()
            recommendationDao.expireOlderThan(expireCutoff)

            // Also clean up very old recommendations (>retention period)
            recommendationDao.deleteOlderThan(stressCutoff)

            // Predictions: delete past dates
            val yesterday = LocalDate.now().minusDays(1).toString()
            stressDao.deleteOldPredictions(yesterday)

            // Audit logs: 30-day retention
            val auditCutoff = now.minus(Duration.ofDays(AUDIT_RETENTION_DAYS)).toEpochMilli()
            auditDao.deleteOlderThan(auditCutoff)

            // User feedback: same retention as stress data
            feedbackDao.deleteOlderThan(stressCutoff)

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
