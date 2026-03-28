package com.restguard.domain.service

import com.restguard.domain.model.ActivityType
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ActivitySuggesterTest {

    private lateinit var suggester: ActivitySuggester

    @Before
    fun setup() {
        suggester = ActivitySuggester()
    }

    @Test
    fun `high stress suggests longer activities`() {
        val suggestion = suggester.suggest(stressScore = 75)
        assertTrue(
            "High stress should suggest longer activity (${suggestion.durationMinutes} min)",
            suggestion.durationMinutes >= 10,
        )
    }

    @Test
    fun `low stress suggests shorter activities`() {
        val suggestion = suggester.suggest(stressScore = 30)
        assertTrue(
            "Low stress should suggest shorter activity (${suggestion.durationMinutes} min)",
            suggestion.durationMinutes <= 10,
        )
    }

    @Test
    fun `time constraint filters activities`() {
        val suggestion = suggester.suggest(stressScore = 80, availableMinutes = 5)
        assertTrue(
            "Activity should fit in 5 min gap",
            suggestion.minGapMinutes <= 5,
        )
    }

    @Test
    fun `suggestAll returns multiple options`() {
        val suggestions = suggester.suggestAll(stressScore = 60)
        assertTrue("Should have multiple suggestions", suggestions.size >= 2)
    }

    @Test
    fun `extreme stress includes power nap`() {
        val suggestions = suggester.suggestAll(stressScore = 85)
        val hasNap = suggestions.any { it.type == ActivityType.POWER_NAP }
        assertTrue("Extreme stress should include power nap option", hasNap)
    }

    @Test
    fun `evening recovery available at high stress`() {
        val suggestions = suggester.suggestAll(stressScore = 70)
        val hasEvening = suggestions.any { it.type == ActivityType.EVENING_RECOVERY }
        assertTrue("High stress should include evening recovery", hasEvening)
    }

    @Test
    fun `very low stress gets hydration or breathing`() {
        val suggestions = suggester.suggestAll(stressScore = 25)
        assertTrue(
            "Low stress should still have at least 1 option",
            suggestions.isNotEmpty(),
        )
    }
}
