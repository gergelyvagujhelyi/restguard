package com.restguard.data.local

import com.restguard.data.local.entity.*
import com.restguard.domain.model.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

// ─── StressSample ↔ Entity ─────────────────────────────────

fun StressSample.toEntity() = StressSampleEntity(
    id = id,
    timestamp = timestamp.toEpochMilli(),
    score = score,
    physiological = components.physiological,
    calendarPressure = components.calendarPressure,
    historicalPattern = components.historicalPattern,
    confidence = confidence,
    missingSources = missingSources.joinToString(","),
)

fun StressSampleEntity.toDomain() = StressSample(
    id = id,
    timestamp = Instant.ofEpochMilli(timestamp),
    score = score,
    components = StressComponents(
        physiological = physiological,
        calendarPressure = calendarPressure,
        historicalPattern = historicalPattern,
    ),
    confidence = confidence,
    missingSources = if (missingSources.isBlank()) emptyList() else missingSources.split(","),
)

// ─── HealthSnapshot ↔ Entity ────────────────────────────────

fun HealthSnapshot.toEntity() = HealthSnapshotEntity(
    id = id,
    timestamp = timestamp.toEpochMilli(),
    sleepDurationMinutes = sleepDurationMinutes,
    sleepQualityScore = sleepQualityScore,
    restingHeartRateBpm = restingHeartRateBpm,
    hrvMs = hrvMs,
    stepsToday = stepsToday,
    activeMinutesToday = activeMinutesToday,
    respiratoryRate = respiratoryRate,
    bodyTemperature = bodyTemperature,
)

fun HealthSnapshotEntity.toDomain() = HealthSnapshot(
    id = id,
    timestamp = Instant.ofEpochMilli(timestamp),
    sleepDurationMinutes = sleepDurationMinutes,
    sleepQualityScore = sleepQualityScore,
    restingHeartRateBpm = restingHeartRateBpm,
    hrvMs = hrvMs,
    stepsToday = stepsToday,
    activeMinutesToday = activeMinutesToday,
    respiratoryRate = respiratoryRate,
    bodyTemperature = bodyTemperature,
)

// ─── StressPrediction ↔ Entity ──────────────────────────────

fun StressPrediction.toEntity() = StressPredictionEntity(
    date = date.toString(),
    predictedScore = predictedScore,
    confidence = confidence,
    meetingCount = calendarPressureBreakdown.meetingCount,
    totalMeetingMinutes = calendarPressureBreakdown.totalMeetingMinutes,
    backToBackCount = calendarPressureBreakdown.backToBackCount,
    highStressMeetingCount = calendarPressureBreakdown.highStressMeetingCount,
    earlyMeetingCount = calendarPressureBreakdown.earlyMeetingCount,
    lateMeetingCount = calendarPressureBreakdown.lateMeetingCount,
    contextSwitchScore = calendarPressureBreakdown.contextSwitchScore,
    explanation = explanation,
)

fun StressPredictionEntity.toDomain() = StressPrediction(
    date = LocalDate.parse(date),
    predictedScore = predictedScore,
    confidence = confidence,
    calendarPressureBreakdown = CalendarPressureBreakdown(
        meetingCount = meetingCount,
        totalMeetingMinutes = totalMeetingMinutes,
        backToBackCount = backToBackCount,
        highStressMeetingCount = highStressMeetingCount,
        earlyMeetingCount = earlyMeetingCount,
        lateMeetingCount = lateMeetingCount,
        contextSwitchScore = contextSwitchScore,
    ),
    explanation = explanation,
)

// ─── MeetingStressImpact ↔ Entity ───────────────────────────

fun MeetingStressImpact.toEntity() = MeetingStressImpactEntity(
    id = id,
    eventPatternKey = eventPatternKey,
    averageStressDelta = averageStressDelta,
    sampleCount = sampleCount,
    isOneOnOne = features.isOneOnOne,
    isGroupMeeting = features.isGroupMeeting,
    isRecurring = features.isRecurring,
    hasManager = features.hasManager,
    isCustomerFacing = features.isCustomerFacing,
    isInternal = features.isInternal,
    isMorning = features.isMorning,
    isAfternoon = features.isAfternoon,
    durationMinutes = features.durationMinutes,
    isPreparationHeavy = features.isPreparationHeavy,
    isOnline = features.isOnline,
    lastUpdated = lastUpdated.toEpochMilli(),
)

fun MeetingStressImpactEntity.toDomain() = MeetingStressImpact(
    id = id,
    eventPatternKey = eventPatternKey,
    averageStressDelta = averageStressDelta,
    sampleCount = sampleCount,
    features = MeetingFeatures(
        isOneOnOne = isOneOnOne,
        isGroupMeeting = isGroupMeeting,
        isRecurring = isRecurring,
        hasManager = hasManager,
        isCustomerFacing = isCustomerFacing,
        isInternal = isInternal,
        isMorning = isMorning,
        isAfternoon = isAfternoon,
        durationMinutes = durationMinutes,
        isPreparationHeavy = isPreparationHeavy,
        isOnline = isOnline,
    ),
    lastUpdated = Instant.ofEpochMilli(lastUpdated),
)

// ─── Recommendation ↔ Entity ────────────────────────────────

fun Recommendation.toEntity() = RecommendationEntity(
    id = id,
    type = type.name,
    eventId = eventId,
    priority = priority,
    explanation = explanation,
    stressReduction = stressReduction,
    createdAt = createdAt.toEpochMilli(),
    status = status.name,
    isExtremeStressMode = isExtremeStressMode,
    eventTitle = event?.title,
    eventStartTime = event?.startTime?.toInstant()?.toEpochMilli(),
    eventEndTime = event?.endTime?.toInstant()?.toEpochMilli(),
    eventAttendeeCount = event?.attendees?.size,
)

fun RecommendationEntity.toDomain() = Recommendation(
    id = id,
    type = RecommendationType.valueOf(type),
    eventId = eventId,
    event = null, // Full event must be loaded separately if needed
    priority = priority,
    explanation = explanation,
    stressReduction = stressReduction,
    createdAt = Instant.ofEpochMilli(createdAt),
    status = RecommendationStatus.valueOf(status),
    isExtremeStressMode = isExtremeStressMode,
)

// ─── RescheduleOption ↔ Entity ──────────────────────────────

fun RescheduleOption.toEntity() = RescheduleOptionEntity(
    id = id,
    originalEventId = originalEventId,
    proposedStart = proposedStart.toInstant().toEpochMilli(),
    proposedEnd = proposedEnd.toInstant().toEpochMilli(),
    dayStressScore = dayStressScore,
    meetingCountOnDay = meetingCountOnDay,
    reason = reason,
    rank = rank,
)

fun RescheduleOptionEntity.toDomain(): RescheduleOption {
    val zone = ZoneId.systemDefault()
    return RescheduleOption(
        id = id,
        originalEventId = originalEventId,
        proposedStart = ZonedDateTime.ofInstant(Instant.ofEpochMilli(proposedStart), zone),
        proposedEnd = ZonedDateTime.ofInstant(Instant.ofEpochMilli(proposedEnd), zone),
        dayStressScore = dayStressScore,
        meetingCountOnDay = meetingCountOnDay,
        reason = reason,
        rank = rank,
    )
}

// ─── UserFeedback ↔ Entity ──────────────────────────────────

fun UserFeedback.toEntity() = UserFeedbackEntity(
    id = id,
    recommendationId = recommendationId,
    action = action.name,
    comment = comment,
    timestamp = timestamp.toEpochMilli(),
)

fun UserFeedbackEntity.toDomain() = UserFeedback(
    id = id,
    recommendationId = recommendationId,
    action = FeedbackAction.valueOf(action),
    comment = comment,
    timestamp = Instant.ofEpochMilli(timestamp),
)

// ─── AuditLog ↔ Entity ─────────────────────────────────────

fun AuditLog.toEntity() = AuditLogEntity(
    id = id,
    action = action,
    eventId = eventId,
    details = details,
    timestamp = timestamp.toEpochMilli(),
)

fun AuditLogEntity.toDomain() = AuditLog(
    id = id,
    action = action,
    eventId = eventId,
    details = details,
    timestamp = Instant.ofEpochMilli(timestamp),
)

// ─── SubjectiveCheckIn ↔ Entity ──────────────────────────

fun SubjectiveCheckIn.toEntity() = com.restguard.data.local.entity.SubjectiveCheckInEntity(
    id = id,
    timestamp = timestamp.toEpochMilli(),
    energyLevel = energyLevel,
    moodLevel = moodLevel,
    note = note,
    trigger = trigger.name,
)

fun com.restguard.data.local.entity.SubjectiveCheckInEntity.toDomain() = SubjectiveCheckIn(
    id = id,
    timestamp = Instant.ofEpochMilli(timestamp),
    energyLevel = energyLevel,
    moodLevel = moodLevel,
    note = note,
    trigger = CheckInTrigger.valueOf(trigger),
)

// ─── PersonalizationWeights ↔ Entity ─────────────────────

fun PersonalizationWeights.toEntity() = com.restguard.data.local.entity.PersonalizationWeightsEntity(
    id = 0,
    physiologicalWeight = physiologicalWeight,
    calendarWeight = calendarWeight,
    historicalWeight = historicalWeight,
    scoreOffset = scoreOffset,
    lastCalibrated = lastCalibrated?.toEpochMilli(),
)

fun com.restguard.data.local.entity.PersonalizationWeightsEntity.toDomain() = PersonalizationWeights(
    physiologicalWeight = physiologicalWeight,
    calendarWeight = calendarWeight,
    historicalWeight = historicalWeight,
    scoreOffset = scoreOffset,
    lastCalibrated = lastCalibrated?.let { Instant.ofEpochMilli(it) },
)
