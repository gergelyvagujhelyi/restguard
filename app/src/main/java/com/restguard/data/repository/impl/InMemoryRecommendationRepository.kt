package com.restguard.data.repository.impl

import com.restguard.domain.model.*
import com.restguard.domain.repository.RecommendationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory recommendation repository for MVP.
 */
class InMemoryRecommendationRepository : RecommendationRepository {

    private val recommendations = MutableStateFlow<List<Recommendation>>(emptyList())
    private val rescheduleOptions = mutableMapOf<String, List<RescheduleOption>>()

    override suspend fun saveRecommendations(recommendations: List<Recommendation>) {
        this.recommendations.value = recommendations
    }

    override suspend fun getActiveRecommendations(): List<Recommendation> {
        return recommendations.value.filter { it.status == RecommendationStatus.PENDING }
    }

    override suspend fun updateStatus(id: String, status: RecommendationStatus) {
        recommendations.value = recommendations.value.map {
            if (it.id == id) it.copy(status = status) else it
        }
    }

    override fun observeActiveRecommendations(): Flow<List<Recommendation>> {
        return recommendations.map { list ->
            list.filter { it.status == RecommendationStatus.PENDING }
        }
    }

    override suspend fun saveRescheduleOptions(options: List<RescheduleOption>) {
        if (options.isNotEmpty()) {
            rescheduleOptions[options.first().originalEventId] = options
        }
    }

    override suspend fun getRescheduleOptions(eventId: String): List<RescheduleOption> {
        return rescheduleOptions[eventId] ?: emptyList()
    }
}
