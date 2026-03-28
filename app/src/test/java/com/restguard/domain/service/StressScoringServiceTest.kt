package com.restguard.domain.service

import com.restguard.data.repository.impl.FakeCalendarRepository
import com.restguard.data.repository.impl.FakeHealthRepository
import com.restguard.data.repository.impl.InMemoryStressRepository
import com.restguard.domain.model.*
import com.restguard.domain.repository.PersonalizationRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class StressScoringServiceTest {

    private lateinit var healthRepo: FakeHealthRepository
    private lateinit var calendarRepo: FakeCalendarRepository
    private lateinit var stressRepo: InMemoryStressRepository
    private lateinit var personalizationRepo: PersonalizationRepository
    private lateinit var service: StressScoringService

    @Before
    fun setup() {
        healthRepo = FakeHealthRepository()
        calendarRepo = FakeCalendarRepository()
        stressRepo = InMemoryStressRepository()
        personalizationRepo = object : PersonalizationRepository {
            override suspend fun saveWeights(weights: PersonalizationWeights) {}
            override suspend fun getWeights(): PersonalizationWeights? = null
        }
        service = StressScoringService(healthRepo, calendarRepo, stressRepo, personalizationRepo)
    }

    // ─── Physiological scoring ──────────────────────────────

    @Test
    fun `rested person has low physiological score`() {
        healthRepo.setScenario("rested")
        val health = HealthSnapshot(
            id = "1", timestamp = Instant.now(),
            sleepDurationMinutes = 480, sleepQualityScore = 85,
            restingHeartRateBpm = 58, hrvMs = 55f,
            stepsToday = 6000, activeMinutesToday = 45,
            respiratoryRate = 14f, bodyTemperature = null,
        )
        val score = service.computePhysiologicalScore(health)
        assertTrue("Rested score ($score) should be low (<= 25)", score <= 25)
    }

    @Test
    fun `exhausted person has high physiological score`() {
        val health = HealthSnapshot(
            id = "2", timestamp = Instant.now(),
            sleepDurationMinutes = 240, sleepQualityScore = 20,
            restingHeartRateBpm = 82, hrvMs = 18f,
            stepsToday = 800, activeMinutesToday = 5,
            respiratoryRate = 18f, bodyTemperature = null,
        )
        val score = service.computePhysiologicalScore(health)
        assertTrue("Exhausted score ($score) should be high (>= 55)", score >= 55)
    }

    @Test
    fun `null health data returns neutral score`() {
        val score = service.computePhysiologicalScore(null)
        assertEquals("Null health should return 50", 50, score)
    }

    @Test
    fun `partial health data still produces score`() {
        val health = HealthSnapshot(
            id = "3", timestamp = Instant.now(),
            sleepDurationMinutes = 300, sleepQualityScore = null,
            restingHeartRateBpm = null, hrvMs = null,
            stepsToday = null, activeMinutesToday = null,
            respiratoryRate = null, bodyTemperature = null,
        )
        val score = service.computePhysiologicalScore(health)
        assertTrue("Partial data score ($score) should be in valid range", score in 0..100)
    }

    // ─── Calendar pressure ──────────────────────────────────

    @Test
    fun `empty calendar has minimal pressure`() {
        val score = service.computeCalendarPressure(emptyList())
        assertEquals("Empty calendar pressure should be 5", 5, score)
    }

    @Test
    fun `packed calendar has high pressure`() {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        val events = (9..16).map { hour ->
            CalendarEvent(
                id = "e$hour", calendarId = "c1", title = "Meeting $hour",
                description = null, location = null,
                startTime = today.atTime(hour, 0).atZone(zone),
                endTime = today.atTime(hour, 30).atZone(zone),
                isAllDay = false, isRecurring = false, recurrenceRule = null,
                organizerEmail = null, selfIsOrganizer = false,
                attendees = emptyList(), status = EventStatus.CONFIRMED,
                availability = EventAvailability.BUSY,
            )
        }
        val score = service.computeCalendarPressure(events)
        assertTrue("Packed calendar ($score) should have high pressure (>= 50)", score >= 50)
    }

    @Test
    fun `back to back meetings increase pressure`() {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        // Two meetings with no gap
        val events = listOf(
            createEvent("b1", today, 10, 0, 60, zone),
            createEvent("b2", today, 11, 0, 60, zone),
        )
        val scoreBackToBack = service.computeCalendarPressure(events)

        // Two meetings with 1 hour gap
        val spacedEvents = listOf(
            createEvent("s1", today, 10, 0, 60, zone),
            createEvent("s2", today, 12, 0, 60, zone),
        )
        val scoreSpaced = service.computeCalendarPressure(spacedEvents)

        assertTrue(
            "Back-to-back ($scoreBackToBack) should be >= spaced ($scoreSpaced)",
            scoreBackToBack >= scoreSpaced
        )
    }

    // ─── Stress level classification ────────────────────────

    @Test
    fun `classify stress levels correctly`() {
        assertEquals(StressLevel.LOW, service.classifyStress(0))
        assertEquals(StressLevel.LOW, service.classifyStress(30))
        assertEquals(StressLevel.MODERATE, service.classifyStress(31))
        assertEquals(StressLevel.MODERATE, service.classifyStress(55))
        assertEquals(StressLevel.HIGH, service.classifyStress(56))
        assertEquals(StressLevel.HIGH, service.classifyStress(79))
        assertEquals(StressLevel.EXTREME, service.classifyStress(80))
        assertEquals(StressLevel.EXTREME, service.classifyStress(100))
    }

    // ─── End-to-end ─────────────────────────────────────────

    @Test
    fun `compute current stress produces valid sample`() = runTest {
        healthRepo.setScenario("tired")
        val sample = service.computeCurrentStress()

        assertTrue("Score should be in range", sample.score in 0..100)
        assertTrue("Confidence should be in range", sample.confidence in 0f..1f)
        assertNotNull("Components should not be null", sample.components)
    }

    @Test
    fun `predict stress produces predictions for each day`() = runTest {
        val predictions = service.predictStress(3)
        assertEquals("Should predict 3 days", 3, predictions.size)
        predictions.forEach { pred ->
            assertTrue("Score in range", pred.predictedScore in 0..100)
            assertTrue("Confidence in range", pred.confidence in 0f..1f)
            assertTrue("Explanation not empty", pred.explanation.isNotEmpty())
        }
    }

    // ─── Helper ─────────────────────────────────────────────

    private fun createEvent(
        id: String, date: LocalDate, hour: Int, minute: Int, durationMin: Int, zone: ZoneId,
    ): CalendarEvent {
        val start = date.atTime(hour, minute).atZone(zone)
        return CalendarEvent(
            id = id, calendarId = "c1", title = "Meeting",
            description = null, location = null,
            startTime = start, endTime = start.plusMinutes(durationMin.toLong()),
            isAllDay = false, isRecurring = false, recurrenceRule = null,
            organizerEmail = null, selfIsOrganizer = false,
            attendees = emptyList(), status = EventStatus.CONFIRMED,
            availability = EventAvailability.BUSY,
        )
    }
}
