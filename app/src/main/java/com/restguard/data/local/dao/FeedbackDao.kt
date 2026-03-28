package com.restguard.data.local.dao

import androidx.room.*
import com.restguard.data.local.entity.*

@Dao
interface FeedbackDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFeedback(feedback: UserFeedbackEntity)

    @Query("SELECT * FROM user_feedback WHERE recommendationId = :recId")
    suspend fun getFeedbackForRecommendation(recId: String): UserFeedbackEntity?

    @Query("SELECT * FROM user_feedback ORDER BY timestamp DESC")
    suspend fun getAllFeedback(): List<UserFeedbackEntity>

    @Query("DELETE FROM user_feedback WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long)
}

@Dao
interface AuditDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: AuditLogEntity)

    @Query("SELECT * FROM audit_log WHERE timestamp BETWEEN :from AND :to ORDER BY timestamp DESC")
    suspend fun getEntries(from: Long, to: Long): List<AuditLogEntity>

    @Query("DELETE FROM audit_log WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long)
}
