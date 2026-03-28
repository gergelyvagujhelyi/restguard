package com.restguard.domain.service

import com.restguard.domain.model.*
import com.restguard.domain.repository.LlmClient
import com.restguard.domain.repository.LlmMessageRequest
import com.restguard.domain.repository.LlmMessageResponse
import com.restguard.domain.repository.MessageType
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * Generates cancellation and rescheduling messages.
 *
 * Uses LLM for polished text, with template fallback for offline operation.
 * The user MUST approve all messages before sending.
 */
class MessagingEngine @Inject constructor(
    private val llmClient: LlmClient,
) {
    companion object {
        val LLM_MESSAGE_PROMPT = """
            You are drafting a professional meeting notification email.

            Type: {{messageType}}
            Meeting: {{meetingTitle}}
            Recipient: {{recipientName}}
            Reason: {{reason}}
            New time (if reschedule): {{newTime}}

            Rules:
            - Be polite, concise, and respectful of the recipient's time
            - Do not over-apologize; one brief apology is sufficient
            - If rescheduling, clearly state the new proposed time
            - If cancelling, briefly explain why without sharing private health details
            - Keep the email under 100 words
            - Do not mention health conditions or stress levels explicitly
            - Use a professional but warm tone

            Respond ONLY with JSON:
            {
              "subject": "email subject line",
              "body": "full email body"
            }
        """.trimIndent()

        private val DATE_FORMAT = DateTimeFormatter.ofPattern("EEEE, MMMM d 'at' h:mm a")
    }

    /**
     * Generate a cancellation email for all attendees.
     */
    suspend fun draftCancellationEmail(
        event: CalendarEvent,
        recipientName: String?,
        reason: String = "schedule conflict",
    ): MessageDraft {
        return draftMessage(
            messageType = MessageType.CANCELLATION_EMAIL,
            event = event,
            recipientName = recipientName,
            reason = reason,
            newTime = null,
        )
    }

    /**
     * Generate a reschedule email for all attendees.
     */
    suspend fun draftRescheduleEmail(
        event: CalendarEvent,
        recipientName: String?,
        newSlot: RescheduleOption,
        reason: String = "schedule adjustment",
    ): MessageDraft {
        return draftMessage(
            messageType = MessageType.RESCHEDULE_EMAIL,
            event = event,
            recipientName = recipientName,
            reason = reason,
            newTime = newSlot.proposedStart.format(DATE_FORMAT),
        )
    }

    /**
     * Generate a short SMS/message version.
     */
    suspend fun draftShortMessage(
        event: CalendarEvent,
        recipientName: String?,
        isCancellation: Boolean,
        newTime: String? = null,
    ): MessageDraft {
        return draftMessage(
            messageType = MessageType.SHORT_SMS,
            event = event,
            recipientName = recipientName,
            reason = if (isCancellation) "cancellation" else "reschedule",
            newTime = newTime,
        )
    }

    // ─── Internal ───────────────────────────────────────────

    private suspend fun draftMessage(
        messageType: MessageType,
        event: CalendarEvent,
        recipientName: String?,
        reason: String,
        newTime: String?,
    ): MessageDraft {
        return try {
            if (llmClient.isAvailable()) {
                val response = llmClient.draftMessage(
                    LlmMessageRequest(
                        messageType = messageType,
                        meetingTitle = event.title,
                        recipientName = recipientName ?: "there",
                        reason = reason,
                        newTime = newTime,
                    )
                )
                MessageDraft(
                    subject = response.subject,
                    body = response.body,
                    source = "llm",
                    isEditable = true,
                )
            } else {
                templateFallback(messageType, event, recipientName, reason, newTime)
            }
        } catch (e: Exception) {
            templateFallback(messageType, event, recipientName, reason, newTime)
        }
    }

    private fun templateFallback(
        messageType: MessageType,
        event: CalendarEvent,
        recipientName: String?,
        reason: String,
        newTime: String?,
    ): MessageDraft {
        val name = recipientName ?: "there"
        return when (messageType) {
            MessageType.CANCELLATION_EMAIL -> MessageDraft(
                subject = "Cancellation: ${event.title}",
                body = """
                    Hi $name,

                    I need to cancel our meeting "${event.title}" originally scheduled for ${event.startTime.format(DATE_FORMAT)} due to a $reason.

                    I apologize for any inconvenience. I'll follow up to find a better time if needed.

                    Best regards
                """.trimIndent(),
                source = "template",
                isEditable = true,
            )

            MessageType.RESCHEDULE_EMAIL -> MessageDraft(
                subject = "Reschedule: ${event.title}",
                body = """
                    Hi $name,

                    I'd like to reschedule our meeting "${event.title}" from ${event.startTime.format(DATE_FORMAT)} to $newTime due to a $reason.

                    Please let me know if the new time works for you. I'm happy to find an alternative if it doesn't.

                    Best regards
                """.trimIndent(),
                source = "template",
                isEditable = true,
            )

            MessageType.SHORT_SMS -> MessageDraft(
                subject = null,
                body = if (newTime != null) {
                    "Hi $name — need to move \"${event.title}\" to $newTime. Does that work?"
                } else {
                    "Hi $name — sorry, I need to cancel \"${event.title}\". I'll reach out to reschedule."
                },
                source = "template",
                isEditable = true,
            )
        }
    }
}

/**
 * Draft message ready for user review and approval.
 */
data class MessageDraft(
    val subject: String?,
    val body: String,
    val source: String, // "llm" or "template"
    val isEditable: Boolean,
)
