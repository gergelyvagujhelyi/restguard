package com.restguard.data.local.dao

import androidx.room.*
import com.restguard.data.local.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface RecommendationDao {

    // ─── Recommendations ────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(recommendations: List<RecommendationEntity>)

    @Query("SELECT * FROM recommendations WHERE status = 'PENDING' ORDER BY priority ASC")
    suspend fun getActive(): List<RecommendationEntity>

    @Query("SELECT * FROM recommendations WHERE status = 'PENDING' ORDER BY priority ASC")
    fun observeActive(): Flow<List<RecommendationEntity>>

    @Query("SELECT * FROM recommendations WHERE id = :id")
    suspend fun getById(id: String): RecommendationEntity?

    @Query("UPDATE recommendations SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: String)

    @Query("DELETE FROM recommendations WHERE createdAt < :before")
    suspend fun deleteOlderThan(before: Long)

    // Expire stale recommendations (>24h old and still pending)
    @Query("UPDATE recommendations SET status = 'EXPIRED' WHERE status = 'PENDING' AND createdAt < :before")
    suspend fun expireOlderThan(before: Long)

    // ─── Reschedule Options ─────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRescheduleOptions(options: List<RescheduleOptionEntity>)

    @Query("SELECT * FROM reschedule_options WHERE originalEventId = :eventId ORDER BY rank ASC")
    suspend fun getRescheduleOptions(eventId: String): List<RescheduleOptionEntity>

    @Query("DELETE FROM reschedule_options WHERE originalEventId = :eventId")
    suspend fun deleteRescheduleOptions(eventId: String)
}
