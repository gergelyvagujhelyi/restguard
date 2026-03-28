package com.restguard.data.repository.impl

import com.restguard.domain.model.HealthSnapshot
import com.restguard.domain.repository.HealthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.Instant
import java.util.UUID

/**
 * Fake health data for development and testing.
 * Swap with HealthConnectRepository in production DI.
 */
class FakeHealthRepository : HealthRepository {

    private val _latestSnapshot = MutableStateFlow<HealthSnapshot?>(null)

    // Preloaded scenarios for demo
    private val scenarios = mapOf(
        "rested" to HealthSnapshot(
            id = UUID.randomUUID().toString(),
            timestamp = Instant.now(),
            sleepDurationMinutes = 480, // 8h
            sleepQualityScore = 85,
            restingHeartRateBpm = 58,
            hrvMs = 55f,
            stepsToday = 6000,
            activeMinutesToday = 45,
            respiratoryRate = 14f,
            bodyTemperature = null,
        ),
        "tired" to HealthSnapshot(
            id = UUID.randomUUID().toString(),
            timestamp = Instant.now(),
            sleepDurationMinutes = 330, // 5.5h
            sleepQualityScore = 40,
            restingHeartRateBpm = 72,
            hrvMs = 28f,
            stepsToday = 2000,
            activeMinutesToday = 10,
            respiratoryRate = 16f,
            bodyTemperature = null,
        ),
        "exhausted" to HealthSnapshot(
            id = UUID.randomUUID().toString(),
            timestamp = Instant.now(),
            sleepDurationMinutes = 240, // 4h
            sleepQualityScore = 20,
            restingHeartRateBpm = 82,
            hrvMs = 18f,
            stepsToday = 800,
            activeMinutesToday = 5,
            respiratoryRate = 18f,
            bodyTemperature = null,
        ),
    )

    private var currentScenario = "tired"

    fun setScenario(scenario: String) {
        currentScenario = scenario
        _latestSnapshot.value = scenarios[scenario]
    }

    init {
        _latestSnapshot.value = scenarios[currentScenario]
    }

    override suspend fun getLatestSnapshot(): HealthSnapshot? = _latestSnapshot.value

    override suspend fun getSnapshots(from: Instant, to: Instant): List<HealthSnapshot> {
        return listOfNotNull(_latestSnapshot.value)
    }

    override fun observeLatestSnapshot(): Flow<HealthSnapshot?> = _latestSnapshot
}
