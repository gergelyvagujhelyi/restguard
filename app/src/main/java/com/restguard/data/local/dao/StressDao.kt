package com.restguard.data.local.dao

import androidx.room.*
import com.restguard.data.local.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface StressDao {

    // ─── Stress Samples ─────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSample(sample: StressSampleEntity)

    @Query("SELECT * FROM stress_samples WHERE timestamp BETWEEN :from AND :to ORDER BY timestamp DESC")
    suspend fun getSamples(from: Long, to: Long): List<StressSampleEntity>

    @Query("SELECT * FROM stress_samples ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestSample(): StressSampleEntity?

    @Query("SELECT * FROM stress_samples ORDER BY timestamp DESC LIMIT 1")
    fun observeLatestSample(): Flow<StressSampleEntity?>

    @Query("DELETE FROM stress_samples WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long)

    // ─── Health Snapshots ───────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHealthSnapshot(snapshot: HealthSnapshotEntity)

    @Query("SELECT * FROM health_snapshots ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestHealthSnapshot(): HealthSnapshotEntity?

    @Query("SELECT * FROM health_snapshots ORDER BY timestamp DESC LIMIT 1")
    fun observeLatestHealthSnapshot(): Flow<HealthSnapshotEntity?>

    @Query("SELECT * FROM health_snapshots WHERE timestamp BETWEEN :from AND :to ORDER BY timestamp DESC")
    suspend fun getHealthSnapshots(from: Long, to: Long): List<HealthSnapshotEntity>

    // ─── Stress Predictions ─────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrediction(prediction: StressPredictionEntity)

    @Query("SELECT * FROM stress_predictions WHERE date BETWEEN :from AND :to ORDER BY date ASC")
    suspend fun getPredictions(from: String, to: String): List<StressPredictionEntity>

    @Query("DELETE FROM stress_predictions WHERE date < :before")
    suspend fun deleteOldPredictions(before: String)

    // ─── Meeting Stress Impacts ─────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMeetingImpact(impact: MeetingStressImpactEntity)

    @Query("SELECT * FROM meeting_stress_impacts WHERE eventPatternKey = :key")
    suspend fun getMeetingImpact(key: String): MeetingStressImpactEntity?

    @Query("SELECT * FROM meeting_stress_impacts ORDER BY averageStressDelta DESC")
    suspend fun getAllMeetingImpacts(): List<MeetingStressImpactEntity>
}
