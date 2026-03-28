package com.restguard.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.restguard.data.local.dao.*
import com.restguard.data.local.entity.*

@Database(
    entities = [
        StressSampleEntity::class,
        HealthSnapshotEntity::class,
        StressPredictionEntity::class,
        MeetingStressImpactEntity::class,
        RecommendationEntity::class,
        RescheduleOptionEntity::class,
        UserFeedbackEntity::class,
        AuditLogEntity::class,
        SubjectiveCheckInEntity::class,
        PersonalizationWeightsEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class RestGuardDatabase : RoomDatabase() {
    abstract fun stressDao(): StressDao
    abstract fun recommendationDao(): RecommendationDao
    abstract fun feedbackDao(): FeedbackDao
    abstract fun auditDao(): AuditDao
    abstract fun checkInDao(): CheckInDao
    abstract fun personalizationDao(): PersonalizationDao
}
