package com.restguard.data.local.dao

import androidx.room.*
import com.restguard.data.local.entity.PersonalizationWeightsEntity
import com.restguard.data.local.entity.SubjectiveCheckInEntity

@Dao
interface CheckInDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(checkIn: SubjectiveCheckInEntity)

    @Query("SELECT * FROM subjective_check_ins WHERE id = :id")
    suspend fun getById(id: String): SubjectiveCheckInEntity?

    @Query("SELECT * FROM subjective_check_ins WHERE timestamp BETWEEN :from AND :to ORDER BY timestamp DESC")
    suspend fun getCheckIns(from: Long, to: Long): List<SubjectiveCheckInEntity>

    @Query("SELECT * FROM subjective_check_ins ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatest(): SubjectiveCheckInEntity?

    @Query("SELECT * FROM subjective_check_ins ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<SubjectiveCheckInEntity>

    @Query("DELETE FROM subjective_check_ins WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long)

    @Query("SELECT COUNT(*) FROM subjective_check_ins")
    suspend fun count(): Int
}

@Dao
interface PersonalizationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(weights: PersonalizationWeightsEntity)

    @Query("SELECT * FROM personalization_weights WHERE id = 0")
    suspend fun getWeights(): PersonalizationWeightsEntity?
}
