package com.restguard.data.repository.impl

import com.restguard.data.local.*
import com.restguard.data.local.dao.AuditDao
import com.restguard.data.local.dao.FeedbackDao
import com.restguard.domain.model.*
import com.restguard.domain.repository.AuditRepository
import com.restguard.domain.repository.FeedbackRepository
import java.time.Instant

class RoomFeedbackRepository(
    private val dao: FeedbackDao,
) : FeedbackRepository {

    override suspend fun saveFeedback(feedback: UserFeedback) {
        dao.insertFeedback(feedback.toEntity())
    }

    override suspend fun getFeedbackForRecommendation(recommendationId: String): UserFeedback? {
        return dao.getFeedbackForRecommendation(recommendationId)?.toDomain()
    }

    override suspend fun getAllFeedback(): List<UserFeedback> {
        return dao.getAllFeedback().map { it.toDomain() }
    }
}

class RoomAuditRepository(
    private val dao: AuditDao,
) : AuditRepository {

    override suspend fun log(entry: AuditLog) {
        dao.insert(entry.toEntity())
    }

    override suspend fun getEntries(from: Instant, to: Instant): List<AuditLog> {
        return dao.getEntries(from.toEpochMilli(), to.toEpochMilli()).map { it.toDomain() }
    }
}
