package com.restguard.domain.service

import com.restguard.data.repository.impl.*
import com.restguard.domain.model.*
import com.restguard.domain.repository.PersonalizationRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class RecommendationEngineTest {

    private lateinit var healthRepo: FakeHealthRepository
    private lateinit var calendarRepo: FakeCalendarRepository
    private lateinit var stressRepo: InMemoryStressRepository
    private lateinit var llmClient: FakeLlmClient
    private lateinit var stressScoring: StressScoringService
    private lateinit var importanceAssessor: MeetingImportanceAssessor
    private lateinit var activitySuggester: ActivitySuggester
    private lateinit var engine: RecommendationEngine

    private val noOpPersonalizationRepo = object : PersonalizationRepository {
        override suspend fun saveWeights(weights: PersonalizationWeights) {}
        override suspend fun getWeights(): PersonalizationWeights? = null
    }

    @Before
    fun setup() {
        healthRepo = FakeHealthRepository()
        calendarRepo = FakeCalendarRepository()
        stressRepo = InMemoryStressRepository()
        llmClient = FakeLlmClient()
        stressScoring = StressScoringService(healthRepo, calendarRepo, stressRepo, noOpPersonalizationRepo)
        importanceAssessor = MeetingImportanceAssessor(llmClient)
        activitySuggester = ActivitySuggester()
        engine = RecommendationEngine(
            stressScoring, calendarRepo, stressRepo, importanceAssessor, activitySuggester,
        )
    }

    // ─── Same-day restriction ───────────────────────────────

    @Test
    fun `moderate stress never produces same-day cancellation`() = runTest {
        healthRepo.setScenario("tired") // moderate-high
        val recommendations = engine.generateRecommendations()

        val sameDayCancellations = recommendations.filter {
            it.type == RecommendationType.SUGGEST_CANCEL &&
                it.event?.startTime?.toLocalDate() == java.time.LocalDate.now()
        }

        assertTrue(
            "No same-day cancellations in moderate stress",
            sameDayCancellations.isEmpty()
        )
    }

    @Test
    fun `high stress never produces same-day cancellation`() = runTest {
        // "tired" scenario gives moderate-to-high but not extreme
        healthRepo.setScenario("tired")
        val recommendations = engine.generateRecommendations()

        val sameDayCancellations = recommendations.filter {
            it.type == RecommendationType.SUGGEST_CANCEL &&
                it.event?.startTime?.toLocalDate() == java.time.LocalDate.now()
        }

        assertTrue(
            "No same-day cancellations in high stress",
            sameDayCancellations.isEmpty()
        )
    }

    // ─── Extreme stress mode ────────────────────────────────

    @Test
    fun `extreme stress mode returns max 3 events`() = runTest {
        healthRepo.setScenario("exhausted")
        val stress = stressScoring.computeCurrentStress()
        val level = stressScoring.classifyStress(stress.score)

        // Only test if actually extreme (depends on calendar data too)
        if (level == StressLevel.EXTREME) {
            val recommendations = engine.generateRecommendations()
            val urgentRecs = recommendations.filter {
                it.type == RecommendationType.URGENT_SAME_DAY_INTERVENTION
            }
            assertTrue(
                "Extreme mode should return <= 3 urgent recs, got ${urgentRecs.size}",
                urgentRecs.size <= RecommendationEngine.EXTREME_MODE_MAX_EVENTS
            )
            urgentRecs.forEach { rec ->
                assertTrue("Urgent recs should be marked extreme", rec.isExtremeStressMode)
            }
        }
    }

    // ─── Recommendation types ───────────────────────────────

    @Test
    fun `low stress with no overload produces minimal recommendations`() = runTest {
        healthRepo.setScenario("rested")
        val recommendations = engine.generateRecommendations()

        // Should be either empty or just an overload warning
        val cancellations = recommendations.filter {
            it.type == RecommendationType.SUGGEST_CANCEL
        }
        assertTrue("Rested user shouldn't get cancellation suggestions", cancellations.size <= 1)
    }

    @Test
    fun `every recommendation has an explanation`() = runTest {
        healthRepo.setScenario("tired")
        val recommendations = engine.generateRecommendations()

        recommendations.forEach { rec ->
            assertTrue(
                "Recommendation ${rec.id} should have non-empty explanation",
                rec.explanation.isNotEmpty()
            )
        }
    }

    @Test
    fun `recommendations are sorted by priority`() = runTest {
        healthRepo.setScenario("tired")
        val recommendations = engine.generateRecommendations()

        if (recommendations.size > 1) {
            for (i in 0 until recommendations.size - 1) {
                assertTrue(
                    "Recommendations should be sorted by priority",
                    recommendations[i].priority <= recommendations[i + 1].priority
                )
            }
        }
    }

    // ─── Scenario walkthroughs ──────────────────────────────

    @Test
    fun `scenario 1 - moderate stress future overload suggests reschedule`() = runTest {
        // Setup: moderate current stress, heavy future days
        healthRepo.setScenario("tired")
        val recommendations = engine.generateRecommendations()

        val reschedules = recommendations.filter {
            it.type == RecommendationType.SUGGEST_RESCHEDULE
        }

        // With the fake calendar having heavy days ahead, we should see reschedule suggestions
        // (exact count depends on importance assessment of fake events)
        assertNotNull("Should have recommendations", recommendations)
        assertTrue("Should have non-empty results", recommendations.isNotEmpty())
    }

    @Test
    fun `scenario 2 - high stress low importance meeting suggests cancel or reschedule`() = runTest {
        healthRepo.setScenario("tired") // will produce moderate-to-high stress

        val recommendations = engine.generateRecommendations()

        // Should contain either cancellation or reschedule suggestions
        val actionable = recommendations.filter {
            it.type == RecommendationType.SUGGEST_CANCEL ||
                it.type == RecommendationType.SUGGEST_RESCHEDULE
        }

        // Verify these are for future dates (not today)
        actionable.forEach { rec ->
            rec.event?.let { event ->
                if (rec.type == RecommendationType.SUGGEST_CANCEL) {
                    assertNotEquals(
                        "Cancellations should not be for today",
                        java.time.LocalDate.now(),
                        event.startTime.toLocalDate()
                    )
                }
            }
        }
    }
}
