package com.restguard.domain.service

import com.restguard.domain.model.*
import com.restguard.domain.repository.LlmClient
import com.restguard.domain.repository.LlmImportanceRequest
import java.time.Instant
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject

/**
 * Classifies meeting importance using LLM with rule-based fallback.
 *
 * ## LLM Prompt Design
 * - Structured JSON output only
 * - No action authority — classification only
 * - Confidence score required
 *
 * ## Rule-Based Fallback (offline / LLM failure)
 * Heuristic scoring based on:
 * - Title keywords (e.g., "1:1", "standup", "review", "planning", "customer")
 * - Attendee count and seniority signals
 * - Recurrence pattern
 * - Duration
 * - Organizer (self vs. others)
 */
class MeetingImportanceAssessor @Inject constructor(
    private val llmClient: LlmClient,
) {
    companion object {
        // LLM prompt template
        val IMPORTANCE_PROMPT_TEMPLATE = """
            You are a meeting importance classifier. Analyze the following meeting and classify
            its importance as HIGH, MEDIUM, or LOW.

            Respond ONLY with valid JSON matching this exact schema:
            {
              "importance": "HIGH" | "MEDIUM" | "LOW",
              "confidence": 0.0-1.0,
              "explanation": "One sentence explaining why."
            }

            Meeting details:
            - Title: {{title}}
            - Description: {{description}}
            - Organizer: {{organizer}}
            - Attendees: {{attendeeCount}} people
            - Recurring: {{isRecurring}}
            - Duration: {{duration}} minutes
            - Time: {{timeOfDay}} on {{dayOfWeek}}

            Classification rules:
            - HIGH: 1:1 with manager/skip-level, customer-facing meetings, deadline reviews,
              interviews, board meetings, incident response
            - MEDIUM: team standups, recurring syncs, planning sessions, design reviews
            - LOW: optional meetings, FYI-only meetings, social events, meetings where user
              is optional attendee, large all-hands that are recorded

            Consider that the user is trying to reduce stress — err toward rating meetings
            as lower importance when genuinely ambiguous, but never mis-classify truly
            important meetings.

            JSON response:
        """.trimIndent()

        // Rule-based keyword signals
        val HIGH_IMPORTANCE_KEYWORDS = listOf(
            "1:1", "one on one", "interview", "customer", "client",
            "incident", "postmortem", "board", "exec", "review",
            "deadline", "launch", "escalation", "performance review",
        )
        val LOW_IMPORTANCE_KEYWORDS = listOf(
            "optional", "social", "lunch", "coffee chat", "all-hands",
            "town hall", "fyi", "newsletter", "book club", "game",
            "happy hour", "team building",
        )
        val MEDIUM_IMPORTANCE_KEYWORDS = listOf(
            "standup", "stand-up", "sync", "planning", "retro",
            "retrospective", "sprint", "scrum", "design review",
            "backlog", "grooming", "refinement",
        )
    }

    /**
     * Assess meeting importance. Tries LLM first, falls back to rules.
     */
    suspend fun assess(event: CalendarEvent): MeetingPriorityAssessment {
        return try {
            if (llmClient.isAvailable()) {
                assessWithLlm(event)
            } else {
                assessWithRules(event)
            }
        } catch (e: Exception) {
            assessWithRules(event)
        }
    }

    /**
     * Force rule-based assessment (useful for testing or offline).
     */
    fun assessWithRules(event: CalendarEvent): MeetingPriorityAssessment {
        val titleLower = event.title.lowercase()
        val descLower = event.description?.lowercase() ?: ""
        val combined = "$titleLower $descLower"

        // Check keyword matches
        val highHits = HIGH_IMPORTANCE_KEYWORDS.count { combined.contains(it) }
        val lowHits = LOW_IMPORTANCE_KEYWORDS.count { combined.contains(it) }
        val mediumHits = MEDIUM_IMPORTANCE_KEYWORDS.count { combined.contains(it) }

        // Structural signals
        val isSmallMeeting = event.attendees.size <= 2
        val isLargeMeeting = event.attendees.size > 15
        val isSelfOrganized = event.selfIsOrganizer
        val isShort = java.time.Duration.between(event.startTime, event.endTime).toMinutes() <= 30
        val isRecurring = event.isRecurring

        var score = 50 // neutral start

        // Keyword signals
        score += highHits * 20
        score -= lowHits * 15
        score += mediumHits * 5

        // Structural signals
        if (isSmallMeeting && !isSelfOrganized) score += 10 // someone specifically invited you
        if (isLargeMeeting) score -= 10 // likely optional
        if (isRecurring && isLargeMeeting) score -= 15 // recurring all-hands
        if (isSelfOrganized) score += 5 // you set it up
        if (isShort && isRecurring) score -= 5 // quick recurring sync

        val importance = when {
            score >= 65 -> Importance.HIGH
            score >= 40 -> Importance.MEDIUM
            else -> Importance.LOW
        }

        val confidence = when {
            highHits > 0 || lowHits > 0 -> 0.7f // keyword matched
            else -> 0.4f // pure heuristic
        }

        return MeetingPriorityAssessment(
            eventId = event.id,
            importance = importance,
            confidence = confidence,
            explanation = buildRuleExplanation(event, importance, score),
            source = AssessmentSource.RULE_BASED,
            timestamp = Instant.now(),
        )
    }

    private suspend fun assessWithLlm(event: CalendarEvent): MeetingPriorityAssessment {
        val request = LlmImportanceRequest(
            title = event.title,
            description = event.description,
            organizer = event.organizerEmail,
            attendeeCount = event.attendees.size,
            isRecurring = event.isRecurring,
            duration = java.time.Duration.between(event.startTime, event.endTime).toMinutes().toInt(),
            timeOfDay = "${event.startTime.hour}:${event.startTime.minute.toString().padStart(2, '0')}",
            dayOfWeek = event.startTime.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.US),
            userRole = null,
        )

        val response = llmClient.assessMeetingImportance(request)

        // Validate LLM output — never trust blindly
        val validatedImportance = try {
            response.importance
        } catch (e: Exception) {
            return assessWithRules(event) // fallback on parse failure
        }

        return MeetingPriorityAssessment(
            eventId = event.id,
            importance = validatedImportance,
            confidence = response.confidence.coerceIn(0f, 1f),
            explanation = response.explanation,
            source = AssessmentSource.LLM,
            timestamp = Instant.now(),
        )
    }

    private fun buildRuleExplanation(
        event: CalendarEvent,
        importance: Importance,
        score: Int,
    ): String {
        val parts = mutableListOf<String>()
        when (importance) {
            Importance.HIGH -> parts.add("Looks important based on title and attendees")
            Importance.MEDIUM -> parts.add("Standard meeting")
            Importance.LOW -> parts.add("Appears optional or low-priority")
        }
        if (event.isRecurring) parts.add("recurring")
        if (event.attendees.size > 10) parts.add("large group (${event.attendees.size} attendees)")
        if (event.attendees.size <= 2) parts.add("small meeting (${event.attendees.size} attendees)")
        return parts.joinToString("; ") + "."
    }
}
