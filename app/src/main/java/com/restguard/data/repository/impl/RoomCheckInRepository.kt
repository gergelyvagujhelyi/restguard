package com.restguard.data.repository.impl

import com.restguard.data.local.dao.CheckInDao
import com.restguard.data.local.toDomain
import com.restguard.data.local.toEntity
import com.restguard.domain.model.SubjectiveCheckIn
import com.restguard.domain.repository.CheckInRepository
import java.time.Instant
import javax.inject.Inject

class RoomCheckInRepository @Inject constructor(
    private val dao: CheckInDao,
) : CheckInRepository {

    override suspend fun saveCheckIn(checkIn: SubjectiveCheckIn) {
        dao.insert(checkIn.toEntity())
    }

    override suspend fun getCheckIns(from: Instant, to: Instant): List<SubjectiveCheckIn> {
        return dao.getCheckIns(from.toEpochMilli(), to.toEpochMilli()).map { it.toDomain() }
    }

    override suspend fun getLatest(): SubjectiveCheckIn? {
        return dao.getLatest()?.toDomain()
    }

    override suspend fun getRecent(limit: Int): List<SubjectiveCheckIn> {
        return dao.getRecent(limit).map { it.toDomain() }
    }

    override suspend fun count(): Int = dao.count()
}
