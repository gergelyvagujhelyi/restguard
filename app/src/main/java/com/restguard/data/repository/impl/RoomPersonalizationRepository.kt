package com.restguard.data.repository.impl

import com.restguard.data.local.dao.PersonalizationDao
import com.restguard.data.local.toDomain
import com.restguard.data.local.toEntity
import com.restguard.domain.model.PersonalizationWeights
import com.restguard.domain.repository.PersonalizationRepository
import javax.inject.Inject

class RoomPersonalizationRepository @Inject constructor(
    private val dao: PersonalizationDao,
) : PersonalizationRepository {

    override suspend fun saveWeights(weights: PersonalizationWeights) {
        dao.save(weights.toEntity())
    }

    override suspend fun getWeights(): PersonalizationWeights? {
        return dao.getWeights()?.toDomain()
    }
}
