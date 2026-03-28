package com.restguard.domain.service

import com.restguard.domain.model.StressLevel
import com.restguard.notification.StressNotificationService
import org.junit.Assert.*
import org.junit.Test

class NotificationServiceTest {

    @Test
    fun `low stress with no recommendations shows calm headline`() {
        val state = StressNotificationService.buildNotificationState(
            stressScore = 20,
            stressLevel = StressLevel.LOW,
            pendingRecommendations = 0,
        )
        assertEquals(StressLevel.LOW, state.stressLevel)
        assertTrue(state.headline.contains("looking good"))
        assertFalse(StressNotificationService.shouldHeadsUp(state))
    }

    @Test
    fun `extreme stress triggers heads-up`() {
        val state = StressNotificationService.buildNotificationState(
            stressScore = 90,
            stressLevel = StressLevel.EXTREME,
            pendingRecommendations = 3,
        )
        assertTrue(state.headline.contains("Extreme"))
        assertTrue(state.headline.contains("3"))
        assertTrue(StressNotificationService.shouldHeadsUp(state))
    }

    @Test
    fun `high stress with recommendations triggers heads-up`() {
        val state = StressNotificationService.buildNotificationState(
            stressScore = 70,
            stressLevel = StressLevel.HIGH,
            pendingRecommendations = 2,
        )
        assertTrue(state.headline.contains("suggestion"))
        assertTrue(StressNotificationService.shouldHeadsUp(state))
    }

    @Test
    fun `high stress without recommendations does not trigger heads-up`() {
        val state = StressNotificationService.buildNotificationState(
            stressScore = 65,
            stressLevel = StressLevel.HIGH,
            pendingRecommendations = 0,
        )
        assertFalse(StressNotificationService.shouldHeadsUp(state))
    }

    @Test
    fun `moderate stress shows count when recommendations exist`() {
        val state = StressNotificationService.buildNotificationState(
            stressScore = 45,
            stressLevel = StressLevel.MODERATE,
            pendingRecommendations = 1,
        )
        assertTrue(state.headline.contains("1"))
        assertTrue(state.headline.contains("suggestion"))
    }

    @Test
    fun `notification state has current score`() {
        val state = StressNotificationService.buildNotificationState(
            stressScore = 55,
            stressLevel = StressLevel.MODERATE,
            pendingRecommendations = 0,
        )
        assertEquals(55, state.currentScore)
        assertEquals(0, state.pendingRecommendationCount)
    }
}
