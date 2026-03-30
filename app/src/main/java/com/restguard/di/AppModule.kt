package com.restguard.di

import android.content.Context
import androidx.room.Room
import com.restguard.BuildConfig
import com.restguard.data.local.database.RestGuardDatabase
import com.restguard.data.preferences.UserPreferences
import com.restguard.data.remote.ClaudeApiClient
import com.restguard.data.repository.impl.*
import com.restguard.data.repository.impl.RoomCheckInRepository
import com.restguard.data.repository.impl.RoomPersonalizationRepository
import com.restguard.data.repository.impl.platform.CalendarProviderRepository
import com.restguard.data.repository.impl.platform.ContactsProviderRepository
import com.restguard.data.repository.impl.platform.HealthConnectRepository
import com.restguard.domain.repository.*
import com.restguard.domain.service.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module providing all dependencies.
 *
 * Uses BuildConfig.USE_FAKES to toggle between fake (debug) and real (release)
 * implementations for health, calendar, contacts, and LLM.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    // ─── Database ───────────────────────────────────────────

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): RestGuardDatabase {
        return Room.databaseBuilder(
            context,
            RestGuardDatabase::class.java,
            "restguard.db",
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    @Singleton
    fun provideUserPreferences(@ApplicationContext context: Context): UserPreferences {
        return UserPreferences(context)
    }

    // ─── Platform Repositories (fake ↔ real toggle) ─────────

    @Provides
    @Singleton
    fun provideHealthRepository(
        @ApplicationContext context: Context,
    ): HealthRepository = if (BuildConfig.USE_FAKES) {
        FakeHealthRepository()
    } else {
        HealthConnectRepository(context)
    }

    @Provides
    @Singleton
    fun provideCalendarRepository(
        @ApplicationContext context: Context,
        userPreferences: UserPreferences,
    ): CalendarRepository = if (BuildConfig.USE_FAKES) {
        FakeCalendarRepository()
    } else {
        CalendarProviderRepository(context, userPreferences)
    }

    @Provides
    @Singleton
    fun provideContactRepository(
        @ApplicationContext context: Context,
    ): ContactRepository = if (BuildConfig.USE_FAKES) {
        FakeContactRepository()
    } else {
        ContactsProviderRepository(context)
    }

    @Provides
    @Singleton
    fun provideLlmClient(): LlmClient = if (BuildConfig.USE_FAKES) {
        FakeLlmClient()
    } else {
        ClaudeApiClient(apiKey = BuildConfig.CLAUDE_API_KEY)
    }

    // ─── Room-backed Repositories ───────────────────────────

    @Provides
    @Singleton
    fun provideStressRepository(db: RestGuardDatabase): StressRepository {
        return RoomStressRepository(db.stressDao())
    }

    @Provides
    @Singleton
    fun provideRecommendationRepository(db: RestGuardDatabase): RecommendationRepository {
        return RoomRecommendationRepository(db.recommendationDao())
    }

    @Provides
    @Singleton
    fun provideFeedbackRepository(db: RestGuardDatabase): FeedbackRepository {
        return RoomFeedbackRepository(db.feedbackDao())
    }

    @Provides
    @Singleton
    fun provideAuditRepository(db: RestGuardDatabase): AuditRepository {
        return RoomAuditRepository(db.auditDao())
    }

    @Provides
    @Singleton
    fun provideCheckInRepository(db: RestGuardDatabase): CheckInRepository {
        return RoomCheckInRepository(db.checkInDao())
    }

    @Provides
    @Singleton
    fun providePersonalizationRepository(db: RestGuardDatabase): PersonalizationRepository {
        return RoomPersonalizationRepository(db.personalizationDao())
    }

    // ─── Domain Services ────────────────────────────────────

    @Provides
    @Singleton
    fun provideStressScoringService(
        healthRepo: HealthRepository,
        calendarRepo: CalendarRepository,
        stressRepo: StressRepository,
        personalizationRepo: PersonalizationRepository,
    ): StressScoringService = StressScoringService(healthRepo, calendarRepo, stressRepo, personalizationRepo)

    @Provides
    @Singleton
    fun provideMeetingImportanceAssessor(
        llmClient: LlmClient,
    ): MeetingImportanceAssessor = MeetingImportanceAssessor(llmClient)

    @Provides
    @Singleton
    fun provideActivitySuggester(): ActivitySuggester = ActivitySuggester()

    @Provides
    @Singleton
    fun provideRecommendationEngine(
        stressScoringService: StressScoringService,
        calendarRepo: CalendarRepository,
        stressRepo: StressRepository,
        meetingImportanceAssessor: MeetingImportanceAssessor,
        activitySuggester: ActivitySuggester,
    ): RecommendationEngine = RecommendationEngine(
        stressScoringService, calendarRepo, stressRepo,
        meetingImportanceAssessor, activitySuggester,
    )

    @Provides
    @Singleton
    fun provideReschedulingEngine(
        calendarRepo: CalendarRepository,
        stressScoringService: StressScoringService,
    ): ReschedulingEngine = ReschedulingEngine(calendarRepo, stressScoringService)

    @Provides
    @Singleton
    fun provideMessagingEngine(
        llmClient: LlmClient,
    ): MessagingEngine = MessagingEngine(llmClient)

    @Provides
    @Singleton
    fun provideHistoricalLearningService(
        stressRepo: StressRepository,
        calendarRepo: CalendarRepository,
    ): HistoricalLearningService = HistoricalLearningService(stressRepo, calendarRepo)

    @Provides
    @Singleton
    fun provideDataManagementService(
        stressRepo: StressRepository,
        feedbackRepo: FeedbackRepository,
        checkInRepo: CheckInRepository,
        personalizationRepo: PersonalizationRepository,
        database: RestGuardDatabase,
    ): DataManagementService = DataManagementService(
        stressRepo, feedbackRepo, checkInRepo, personalizationRepo, database,
    )

    @Provides
    @Singleton
    fun providePersonalizationService(
        checkInRepo: CheckInRepository,
        feedbackRepo: FeedbackRepository,
        stressRepo: StressRepository,
        healthRepo: HealthRepository,
        personalizationRepo: PersonalizationRepository,
    ): PersonalizationService = PersonalizationService(
        checkInRepo, feedbackRepo, stressRepo, healthRepo, personalizationRepo,
    )
}
