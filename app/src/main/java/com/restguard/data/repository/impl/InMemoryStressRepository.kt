package com.restguard.data.repository.impl

import com.restguard.domain.model.*
import com.restguard.domain.repository.StressRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.Instant
import java.time.LocalDate

/**
 * In-memory stress repository for MVP. Replace with Room-backed impl in Phase 2.
 */
class InMemoryStressRepository : StressRepository {

    private val samples = mutableListOf<StressSample>()
    private val predictions = mutableListOf<StressPrediction>()
    private val impacts = mutableMapOf<String, MeetingStressImpact>()
    private val _currentStress = MutableStateFlow<StressSample?>(null)

    override suspend fun saveStressSample(sample: StressSample) {
        samples.add(sample)
        _currentStress.value = sample
    }

    override suspend fun getStressSamples(from: Instant, to: Instant): List<StressSample> {
        return samples.filter { it.timestamp in from..to }
    }

    override suspend fun getLatestStressSample(): StressSample? {
        return samples.maxByOrNull { it.timestamp }
    }

    override fun observeCurrentStress(): Flow<StressSample?> = _currentStress

    override suspend fun savePrediction(prediction: StressPrediction) {
        predictions.removeAll { it.date == prediction.date }
        predictions.add(prediction)
    }

    override suspend fun getPredictions(from: LocalDate, to: LocalDate): List<StressPrediction> {
        return predictions.filter { it.date in from..to }
    }

    override suspend fun saveMeetingStressImpact(impact: MeetingStressImpact) {
        impacts[impact.eventPatternKey] = impact
    }

    override suspend fun getMeetingStressImpact(eventPatternKey: String): MeetingStressImpact? {
        return impacts[eventPatternKey]
    }

    override suspend fun getAllMeetingStressImpacts(): List<MeetingStressImpact> {
        return impacts.values.toList()
    }
}
