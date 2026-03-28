package com.restguard.data.repository.impl

import com.restguard.domain.model.Importance
import com.restguard.domain.repository.*

/**
 * Fake LLM client that returns plausible responses for development.
 * Swap with real API client (ClaudeApiClient) in production.
 */
class FakeLlmClient : LlmClient {

    override suspend fun assessMeetingImportance(request: LlmImportanceRequest): LlmImportanceResponse {
        // Simulate LLM reasoning with simple heuristics
        val titleLower = request.title.lowercase()

        val (importance, explanation) = when {
            titleLower.contains("customer") || titleLower.contains("client") ->
                Importance.HIGH to "Customer-facing meetings directly impact revenue and relationships."

            titleLower.contains("1:1") || titleLower.contains("one on one") ->
                Importance.HIGH to "1:1 meetings are important for career development and alignment."

            titleLower.contains("planning") || titleLower.contains("review") ->
                Importance.MEDIUM to "Planning and review meetings contribute to team alignment but can often be rescheduled."

            titleLower.contains("standup") || titleLower.contains("sync") ->
                Importance.MEDIUM to "Regular syncs are useful but usually have notes/recordings."

            titleLower.contains("social") || titleLower.contains("coffee") ||
                titleLower.contains("happy hour") || titleLower.contains("book club") ||
                titleLower.contains("optional") || titleLower.contains("fyi") ->
                Importance.LOW to "Social or optional meetings can be skipped without significant impact."

            titleLower.contains("all-hands") || titleLower.contains("town hall") ->
                Importance.LOW to "Large all-hands meetings are typically recorded and can be watched later."

            request.attendeeCount > 20 ->
                Importance.LOW to "Very large meetings (${request.attendeeCount} attendees) are usually informational and recorded."

            else ->
                Importance.MEDIUM to "Standard meeting with moderate importance based on available signals."
        }

        return LlmImportanceResponse(
            importance = importance,
            confidence = 0.75f,
            explanation = explanation,
        )
    }

    override suspend fun draftMessage(request: LlmMessageRequest): LlmMessageResponse {
        val name = request.recipientName ?: "there"

        return when (request.messageType) {
            MessageType.CANCELLATION_EMAIL -> LlmMessageResponse(
                subject = "Unable to attend: ${request.meetingTitle}",
                body = """
                    Hi $name,

                    I unfortunately need to cancel "${request.meetingTitle}" due to ${request.reason}. I apologize for the short notice.

                    I'll reach out soon to find a time that works for everyone.

                    Thanks for understanding.
                """.trimIndent(),
            )

            MessageType.RESCHEDULE_EMAIL -> LlmMessageResponse(
                subject = "Reschedule request: ${request.meetingTitle}",
                body = """
                    Hi $name,

                    Would it be possible to move "${request.meetingTitle}" to ${request.newTime}? I need to adjust my schedule due to ${request.reason}.

                    If that time doesn't work for you, I'm happy to look at alternatives. Just let me know.

                    Thanks!
                """.trimIndent(),
            )

            MessageType.SHORT_SMS -> LlmMessageResponse(
                subject = null,
                body = if (request.newTime != null) {
                    "Hi $name — could we move \"${request.meetingTitle}\" to ${request.newTime}? Let me know if that works."
                } else {
                    "Hi $name — I need to cancel \"${request.meetingTitle}\". Sorry for the inconvenience — I'll follow up."
                },
            )
        }
    }

    override fun isAvailable(): Boolean = true
}
