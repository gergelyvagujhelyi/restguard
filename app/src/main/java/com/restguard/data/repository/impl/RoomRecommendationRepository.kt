package com.restguard.data.repository.impl

import com.restguard.data.local.*
import com.restguard.data.local.dao.RecommendationDao
import com.restguard.domain.model.*
import com.restguard.domain.repository.RecommendationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Room-backed recommendation repository. Replaces InMemoryRecommendationRepository.
 */
class RoomRecommendationRepository(
    private val dao: RecommendationDao,
) : RecommendationRepository {

    override suspend fun saveRecommendations(recommendations: List<Recommendation>) {
        dao.insertAll(recommendations.map { it.toEntity() })
    }

    override suspend fun getActiveRecommendations(): List<Recommendation> {
        return dao.getActive().map { it.toDomain() }
    }

    override suspend fun updateStatus(id: String, status: RecommendationStatus) {
        dao.updateStatus(id, status.name)
    }

    override fun observeActiveRecommendations(): Flow<List<Recommendation>> {
        return dao.observeActive().map { list -> list.map { it.toDomain() } }
    }

    override suspend fun saveRescheduleOptions(options: List<RescheduleOption>) {
        if (options.isNotEmpty()) {
            val eventId = options.first().originalEventId
            dao.deleteRescheduleOptions(eventId)
            dao.insertRescheduleOptions(options.map { it.toEntity() })
        }
    }

    override suspend fun getRescheduleOptions(eventId: String): List<RescheduleOption> {
        return dao.getRescheduleOptions(eventId).map { it.toDomain() }
    }
}
