package com.restguard.data.repository.impl.platform

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.*
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.restguard.domain.model.HealthSnapshot
import com.restguard.domain.repository.HealthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID

/**
 * Real Health Connect implementation.
 *
 * Reads sleep, heart rate, HRV, steps, active minutes, and respiratory rate
 * from the Health Connect API on Android.
 *
 * Health Connect must be installed on the device. The SDK handles the case
 * where it is not available — callers should check [isAvailable] first.
 */
class HealthConnectRepository(
    private val context: Context,
) : HealthRepository {

    private val _latestSnapshot = MutableStateFlow<HealthSnapshot?>(null)

    private val client: HealthConnectClient? by lazy {
        try {
            if (HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE) {
                HealthConnectClient.getOrCreate(context)
            } else null
        } catch (e: Exception) {
            null
        }
    }

    fun isAvailable(): Boolean = client != null

    override suspend fun getLatestSnapshot(): HealthSnapshot? {
        val hc = client ?: return null
        return try {
            val now = Instant.now()
            val yesterday = now.minus(Duration.ofHours(24))

            val sleep = readSleepData(hc, yesterday, now)
            val heartRate = readHeartRateData(hc, yesterday, now)
            val hrv = readHrvData(hc, yesterday, now)
            val steps = readStepsData(hc, now)
            val activeMinutes = readActiveMinutes(hc, now)
            val respiratory = readRespiratoryRate(hc, yesterday, now)

            val snapshot = HealthSnapshot(
                id = UUID.randomUUID().toString(),
                timestamp = now,
                sleepDurationMinutes = sleep?.first,
                sleepQualityScore = sleep?.second,
                restingHeartRateBpm = heartRate?.restingBpm,
                hrvMs = hrv,
                stepsToday = steps,
                activeMinutesToday = activeMinutes,
                respiratoryRate = respiratory,
                bodyTemperature = null, // not commonly available
            )

            _latestSnapshot.value = snapshot
            snapshot
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun getSnapshots(from: Instant, to: Instant): List<HealthSnapshot> {
        // For historical snapshots, we'd ideally store them in Room via the worker.
        // This returns the current snapshot if within range.
        val latest = _latestSnapshot.value ?: getLatestSnapshot()
        return listOfNotNull(latest).filter { it.timestamp in from..to }
    }

    override fun observeLatestSnapshot(): Flow<HealthSnapshot?> = _latestSnapshot

    // ─── Health Connect data readers ────────────────────────

    private suspend fun readSleepData(
        hc: HealthConnectClient,
        from: Instant,
        to: Instant,
    ): Pair<Int, Int>? {
        return try {
            val response = hc.readRecords(
                ReadRecordsRequest(
                    recordType = SleepSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(from, to),
                )
            )

            if (response.records.isEmpty()) return null

            // Use the most recent sleep session
            val session = response.records.maxByOrNull { it.endTime }!!
            val durationMinutes = Duration.between(session.startTime, session.endTime).toMinutes().toInt()

            // Derive quality from stages if available
            val qualityScore = deriveSleepQuality(session)

            Pair(durationMinutes, qualityScore)
        } catch (e: Exception) {
            null
        }
    }

    private fun deriveSleepQuality(session: SleepSessionRecord): Int {
        val stages = session.stages
        if (stages.isEmpty()) {
            // No stage data — estimate from duration alone
            val durationHours = Duration.between(session.startTime, session.endTime).toHours()
            return when {
                durationHours >= 8 -> 80
                durationHours >= 7 -> 70
                durationHours >= 6 -> 55
                durationHours >= 5 -> 40
                else -> 25
            }
        }

        val totalMinutes = Duration.between(session.startTime, session.endTime).toMinutes().toFloat()
        if (totalMinutes <= 0) return 50

        var deepMinutes = 0L
        var remMinutes = 0L
        var awakeMinutes = 0L

        for (stage in stages) {
            val stageMinutes = Duration.between(stage.startTime, stage.endTime).toMinutes()
            when (stage.stage) {
                SleepSessionRecord.STAGE_TYPE_DEEP -> deepMinutes += stageMinutes
                SleepSessionRecord.STAGE_TYPE_REM -> remMinutes += stageMinutes
                SleepSessionRecord.STAGE_TYPE_AWAKE,
                SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED -> awakeMinutes += stageMinutes
            }
        }

        // Score: deep + REM should be ~40% of sleep, awake should be minimal
        val deepRemRatio = (deepMinutes + remMinutes) / totalMinutes
        val awakeRatio = awakeMinutes / totalMinutes

        var score = 50
        score += (deepRemRatio * 60).toInt()  // up to +60 for good deep+REM
        score -= (awakeRatio * 40).toInt()     // penalty for wakefulness

        return score.coerceIn(0, 100)
    }

    private data class HeartRateResult(val restingBpm: Int)

    private suspend fun readHeartRateData(
        hc: HealthConnectClient,
        from: Instant,
        to: Instant,
    ): HeartRateResult? {
        return try {
            val response = hc.readRecords(
                ReadRecordsRequest(
                    recordType = HeartRateRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(from, to),
                )
            )

            if (response.records.isEmpty()) return null

            // Resting HR: use the minimum of recent readings (proxy for resting)
            val allSamples = response.records.flatMap { it.samples }
            if (allSamples.isEmpty()) return null

            // Take the 10th percentile as resting HR estimate
            val sorted = allSamples.map { it.beatsPerMinute }.sorted()
            val restingIndex = (sorted.size * 0.1).toInt().coerceAtLeast(0)
            val restingBpm = sorted[restingIndex].toInt()

            HeartRateResult(restingBpm = restingBpm)
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun readHrvData(
        hc: HealthConnectClient,
        from: Instant,
        to: Instant,
    ): Float? {
        return try {
            val response = hc.readRecords(
                ReadRecordsRequest(
                    recordType = HeartRateVariabilityRmssdRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(from, to),
                )
            )

            if (response.records.isEmpty()) return null

            // Average RMSSD from recent readings
            val avg = response.records
                .map { it.heartRateVariabilityMillis }
                .average()

            avg.toFloat()
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun readStepsData(
        hc: HealthConnectClient,
        now: Instant,
    ): Int? {
        return try {
            // Steps for today
            val startOfDay = LocalDateTime.now()
                .withHour(0).withMinute(0).withSecond(0)
                .toInstant(ZoneOffset.UTC)

            val response = hc.readRecords(
                ReadRecordsRequest(
                    recordType = StepsRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startOfDay, now),
                )
            )

            if (response.records.isEmpty()) return null

            response.records.sumOf { it.count }.toInt()
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun readActiveMinutes(
        hc: HealthConnectClient,
        now: Instant,
    ): Int? {
        return try {
            val startOfDay = LocalDateTime.now()
                .withHour(0).withMinute(0).withSecond(0)
                .toInstant(ZoneOffset.UTC)

            val response = hc.readRecords(
                ReadRecordsRequest(
                    recordType = ExerciseSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startOfDay, now),
                )
            )

            if (response.records.isEmpty()) return 0

            response.records.sumOf {
                Duration.between(it.startTime, it.endTime).toMinutes()
            }.toInt()
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun readRespiratoryRate(
        hc: HealthConnectClient,
        from: Instant,
        to: Instant,
    ): Float? {
        return try {
            val response = hc.readRecords(
                ReadRecordsRequest(
                    recordType = RespiratoryRateRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(from, to),
                )
            )

            if (response.records.isEmpty()) return null

            response.records
                .map { it.rate }
                .average()
                .toFloat()
        } catch (e: Exception) {
            null
        }
    }
}
