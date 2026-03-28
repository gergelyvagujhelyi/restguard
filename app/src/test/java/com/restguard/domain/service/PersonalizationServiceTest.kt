package com.restguard.domain.service

import com.restguard.domain.model.*
import com.restguard.domain.repository.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime

/**
 * Tests for PersonalizationService: weight calibration, profile building, guard rails.
 */
class PersonalizationServiceTest {

    // ─── Fakes ────────────────────────────────────────────────

    private class FakeCheckInRepo : CheckInRepository {
        val checkIns = mutableListOf<SubjectiveCheckIn>()
        override suspend fun saveCheckIn(checkIn: SubjectiveCheckIn) { checkIns.add(checkIn) }
        override suspend fun getCheckIns(from: Instant, to: Instant) =
            checkIns.filter { it.timestamp >= from && it.timestamp <= to }
        override suspend fun getLatest() = checkIns.maxByOrNull { it.timestamp }
        override suspend fun getRecent(limit: Int) =
            checkIns.sortedByDescending { it.timestamp }.take(limit)
        override suspend fun count() = checkIns.size
    }

    private class FakeFeedbackRepo : FeedbackRepository {
        val feedback = mutableListOf<UserFeedback>()
        override suspend fun saveFeedback(f: UserFeedback) { feedback.add(f) }
        override suspend fun getFeedbackForRecommendation(recId: String) =
            feedback.find { it.recommendationId == recId }
        override suspend fun getAllFeedback() = feedback.toList()
    }

    private class FakeStressRepo : StressRepository {
        val samples = mutableListOf<StressSample>()
        val impacts = mutableListOf<MeetingStressImpact>()
        override suspend fun saveStressSample(sample: StressSample) { samples.add(sample) }
        override suspend fun getStressSamples(from: Instant, to: Instant) =
            samples.filter { it.timestamp >= from && it.timestamp <= to }
        override suspend fun getLatestStressSample() = samples.maxByOrNull { it.timestamp }
        override fun observeCurrentStress(): Flow<StressSample?> = flowOf(null)
        override suspend fun savePrediction(prediction: StressPrediction) {}
        override suspend fun getPredictions(from: LocalDate, to: LocalDate) = emptyList<StressPrediction>()
        override suspend fun saveMeetingStressImpact(impact: MeetingStressImpact) { impacts.add(impact) }
        override suspend fun getMeetingStressImpact(key: String) = impacts.find { it.eventPatternKey == key }
        override suspend fun getAllMeetingStressImpacts() = impacts.toList()
    }

    private class FakeHealthRepo : HealthRepository {
        val snapshots = mutableListOf<HealthSnapshot>()
        override suspend fun getLatestSnapshot() = snapshots.maxByOrNull { it.timestamp }
        override suspend fun getSnapshots(from: Instant, to: Instant) =
            snapshots.filter { it.timestamp >= from && it.timestamp <= to }
        override fun observeLatestSnapshot(): Flow<HealthSnapshot?> = flowOf(null)
    }

    private class FakePersonalizationRepo : PersonalizationRepository {
        var weights: PersonalizationWeights? = null
        override suspend fun saveWeights(w: PersonalizationWeights) { weights = w }
        override suspend fun getWeights() = weights
    }

    private fun buildService(
        checkInRepo: FakeCheckInRepo = FakeCheckInRepo(),
        feedbackRepo: FakeFeedbackRepo = FakeFeedbackRepo(),
        stressRepo: FakeStressRepo = FakeStressRepo(),
        healthRepo: FakeHealthRepo = FakeHealthRepo(),
        personalizationRepo: FakePersonalizationRepo = FakePersonalizationRepo(),
    ) = PersonalizationService(checkInRepo, feedbackRepo, stressRepo, healthRepo, personalizationRepo)

    // ─── Profile Building ─────────────────────────────────────

    @Test
    fun `buildProfile returns accept rate from feedback`() = runBlocking {
        val feedbackRepo = FakeFeedbackRepo()
        val now = Instant.now()
        // 3 accepted, 2 dismissed → accept rate = 0.6
        repeat(3) {
            feedbackRepo.feedback.add(UserFeedback("f$it", "r$it", FeedbackAction.ACCEPTED, null, now))
        }
        repeat(2) {
            feedbackRepo.feedback.add(UserFeedback("d$it", "r${it+10}", FeedbackAction.DISMISSED, null, now))
        }

        val service = buildService(feedbackRepo = feedbackRepo)
        val profile = service.buildProfile()

        assertEquals(0.6f, profile.acceptRate, 0.01f)
    }

    @Test
    fun `buildProfile computes avg energy and mood from check-ins`() = runBlocking {
        val checkInRepo = FakeCheckInRepo()
        val now = Instant.now()
        // energy: 2, 4, 3 → avg 3.0; mood: 1, 5, 3 → avg 3.0
        checkInRepo.checkIns.add(SubjectiveCheckIn("c1", now, 2, 1, null, CheckInTrigger.USER_INITIATED))
        checkInRepo.checkIns.add(SubjectiveCheckIn("c2", now, 4, 5, null, CheckInTrigger.USER_INITIATED))
        checkInRepo.checkIns.add(SubjectiveCheckIn("c3", now, 3, 3, null, CheckInTrigger.USER_INITIATED))

        val service = buildService(checkInRepo = checkInRepo)
        val profile = service.buildProfile()

        assertEquals(3.0f, profile.avgReportedEnergy!!, 0.01f)
        assertEquals(3.0f, profile.avgReportedMood!!, 0.01f)
    }

    @Test
    fun `buildProfile returns null averages when no check-ins`() = runBlocking {
        val service = buildService()
        val profile = service.buildProfile()

        assertNull(profile.avgReportedEnergy)
        assertNull(profile.avgReportedMood)
        assertEquals(0.5f, profile.acceptRate, 0.01f) // default when no feedback
    }

    @Test
    fun `buildProfile identifies stress triggers from high-impact meetings`() = runBlocking {
        val stressRepo = FakeStressRepo()
        stressRepo.impacts.add(
            MeetingStressImpact(
                id = "m1", eventPatternKey = "sprint planning|rec|small",
                averageStressDelta = 12f, sampleCount = 5,
                features = MeetingFeatures(false, true, true, false, false, true, true, false, 60, false, true),
                lastUpdated = Instant.now(),
            )
        )
        stressRepo.impacts.add(
            MeetingStressImpact(
                id = "m2", eventPatternKey = "standup|rec|small",
                averageStressDelta = 2f, sampleCount = 10, // low delta = not a trigger
                features = MeetingFeatures(false, true, true, false, false, true, true, false, 15, false, true),
                lastUpdated = Instant.now(),
            )
        )

        val service = buildService(stressRepo = stressRepo)
        val profile = service.buildProfile()

        assertTrue(profile.stressTriggers.any { it.contains("Sprint planning", ignoreCase = true) })
        assertFalse(profile.stressTriggers.any { it.contains("Standup", ignoreCase = true) })
    }

    // ─── Weight Calibration ───────────────────────────────────

    @Test
    fun `calibrateWeights requires minimum check-ins`() = runBlocking {
        val checkInRepo = FakeCheckInRepo()
        val personalizationRepo = FakePersonalizationRepo()
        // Only 3 check-ins (below MIN_CHECKINS_FOR_CALIBRATION=5)
        val now = Instant.now()
        repeat(3) {
            checkInRepo.checkIns.add(
                SubjectiveCheckIn("c$it", now, 3, 3, null, CheckInTrigger.USER_INITIATED)
            )
        }

        val service = buildService(checkInRepo = checkInRepo, personalizationRepo = personalizationRepo)
        val result = service.calibrateWeights()

        // Should return defaults since not enough data
        assertEquals(0.45f, result.physiologicalWeight, 0.01f)
        assertEquals(0.35f, result.calendarWeight, 0.01f)
        assertEquals(0.20f, result.historicalWeight, 0.01f)
        assertEquals(0f, result.scoreOffset, 0.01f)
    }

    @Test
    fun `calibrateWeights adjusts offset when scoring too high`() = runBlocking {
        val checkInRepo = FakeCheckInRepo()
        val stressRepo = FakeStressRepo()
        val personalizationRepo = FakePersonalizationRepo()
        val now = Instant.now()

        // User reports calm (mood=5 → subjective stress ~0) but computed score is 70
        repeat(6) { i ->
            val t = now.minusSeconds((i * 60).toLong())
            checkInRepo.checkIns.add(
                SubjectiveCheckIn("c$i", t, 4, 5, null, CheckInTrigger.USER_INITIATED)
            )
            stressRepo.samples.add(
                StressSample("s$i", t, 70, StressComponents(70, 70, 70), 0.9f, emptyList())
            )
        }

        val service = buildService(
            checkInRepo = checkInRepo, stressRepo = stressRepo,
            personalizationRepo = personalizationRepo,
        )
        val result = service.calibrateWeights()

        // Offset should be negative (trying to reduce the score)
        assertTrue("Offset should be negative to reduce over-scoring, got ${result.scoreOffset}",
            result.scoreOffset < 0)
    }

    @Test
    fun `calibrateWeights enforces cooldown`() = runBlocking {
        val checkInRepo = FakeCheckInRepo()
        val stressRepo = FakeStressRepo()
        val personalizationRepo = FakePersonalizationRepo()
        val now = Instant.now()

        // Pre-set weights with recent calibration
        val recentWeights = PersonalizationWeights(
            physiologicalWeight = 0.50f, calendarWeight = 0.30f, historicalWeight = 0.20f,
            scoreOffset = -5f, lastCalibrated = now, // just now
        )
        personalizationRepo.weights = recentWeights

        repeat(6) { i ->
            val t = now.minusSeconds((i * 60).toLong())
            checkInRepo.checkIns.add(
                SubjectiveCheckIn("c$i", t, 4, 5, null, CheckInTrigger.USER_INITIATED)
            )
            stressRepo.samples.add(
                StressSample("s$i", t, 70, StressComponents(70, 70, 70), 0.9f, emptyList())
            )
        }

        val service = buildService(
            checkInRepo = checkInRepo, stressRepo = stressRepo,
            personalizationRepo = personalizationRepo,
        )
        val result = service.calibrateWeights()

        // Should return unchanged weights due to cooldown
        assertEquals(recentWeights.physiologicalWeight, result.physiologicalWeight, 0.001f)
        assertEquals(recentWeights.scoreOffset, result.scoreOffset, 0.001f)
    }

    @Test
    fun `calibrateWeights keeps weights normalized to 1`() = runBlocking {
        val checkInRepo = FakeCheckInRepo()
        val stressRepo = FakeStressRepo()
        val personalizationRepo = FakePersonalizationRepo()
        val now = Instant.now()

        // Heavily biased scenario to force weight changes
        repeat(10) { i ->
            val t = now.minusSeconds((i * 60).toLong())
            checkInRepo.checkIns.add(
                SubjectiveCheckIn("c$i", t, 1, 1, null, CheckInTrigger.USER_INITIATED)
            )
            stressRepo.samples.add(
                StressSample("s$i", t, 10, StressComponents(10, 10, 10), 0.9f, emptyList())
            )
        }

        val service = buildService(
            checkInRepo = checkInRepo, stressRepo = stressRepo,
            personalizationRepo = personalizationRepo,
        )
        val result = service.calibrateWeights()

        val sum = result.physiologicalWeight + result.calendarWeight + result.historicalWeight
        assertEquals("Weights should sum to 1.0", 1.0f, sum, 0.01f)
    }

    @Test
    fun `calibrateWeights clamps individual weights`() = runBlocking {
        val personalizationRepo = FakePersonalizationRepo()
        val checkInRepo = FakeCheckInRepo()
        val stressRepo = FakeStressRepo()
        val now = Instant.now()

        // Start with extreme weights that should be clamped
        personalizationRepo.weights = PersonalizationWeights(
            physiologicalWeight = 0.80f, calendarWeight = 0.10f, historicalWeight = 0.10f,
            scoreOffset = 0f, lastCalibrated = now.minusSeconds(90000), // past cooldown
        )

        repeat(6) { i ->
            val t = now.minusSeconds((i * 60).toLong())
            checkInRepo.checkIns.add(
                SubjectiveCheckIn("c$i", t, 3, 3, null, CheckInTrigger.USER_INITIATED)
            )
            stressRepo.samples.add(
                StressSample("s$i", t, 50, StressComponents(50, 50, 50), 0.9f, emptyList())
            )
        }

        val service = buildService(
            checkInRepo = checkInRepo, stressRepo = stressRepo,
            personalizationRepo = personalizationRepo,
        )
        val result = service.calibrateWeights()

        assertTrue("Physiological weight should be <= MAX",
            result.physiologicalWeight <= PersonalizationService.MAX_COMPONENT_WEIGHT)
        assertTrue("Calendar weight should be >= MIN",
            result.calendarWeight >= PersonalizationService.MIN_COMPONENT_WEIGHT)
        assertTrue("Historical weight should be >= MIN",
            result.historicalWeight >= PersonalizationService.MIN_COMPONENT_WEIGHT)
    }
}
