package com.restguard.data.repository.impl

import com.restguard.data.local.*
import com.restguard.data.local.dao.StressDao
import com.restguard.domain.model.*
import com.restguard.domain.repository.StressRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate

/**
 * Room-backed stress repository. Replaces InMemoryStressRepository in Phase 2.
 */
class RoomStressRepository(
    private val dao: StressDao,
) : StressRepository {

    override suspend fun saveStressSample(sample: StressSample) {
        dao.insertSample(sample.toEntity())
    }

    override suspend fun getStressSamples(from: Instant, to: Instant): List<StressSample> {
        return dao.getSamples(from.toEpochMilli(), to.toEpochMilli()).map { it.toDomain() }
    }

    override suspend fun getLatestStressSample(): StressSample? {
        return dao.getLatestSample()?.toDomain()
    }

    override fun observeCurrentStress(): Flow<StressSample?> {
        return dao.observeLatestSample().map { it?.toDomain() }
    }

    override suspend fun savePrediction(prediction: StressPrediction) {
        dao.insertPrediction(prediction.toEntity())
    }

    override suspend fun getPredictions(from: LocalDate, to: LocalDate): List<StressPrediction> {
        return dao.getPredictions(from.toString(), to.toString()).map { it.toDomain() }
    }

    override suspend fun saveMeetingStressImpact(impact: MeetingStressImpact) {
        dao.insertMeetingImpact(impact.toEntity())
    }

    override suspend fun getMeetingStressImpact(eventPatternKey: String): MeetingStressImpact? {
        return dao.getMeetingImpact(eventPatternKey)?.toDomain()
    }

    override suspend fun getAllMeetingStressImpacts(): List<MeetingStressImpact> {
        return dao.getAllMeetingImpacts().map { it.toDomain() }
    }
}
