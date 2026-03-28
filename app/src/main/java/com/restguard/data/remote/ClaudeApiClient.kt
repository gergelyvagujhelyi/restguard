package com.restguard.data.remote

import com.restguard.domain.model.Importance
import com.restguard.domain.repository.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

/**
 * Real Claude API client for meeting importance assessment and message drafting.
 *
 * Uses the Anthropic Messages API with structured JSON output.
 * The API key should be stored securely (e.g., EncryptedSharedPreferences)
 * and never hardcoded.
 */
class ClaudeApiClient(
    private val apiKey: String,
    private val baseUrl: String = "https://api.anthropic.com/",
    private val model: String = "claude-haiku-4-5-20251001", // fast + cheap for classification
) : LlmClient {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(Interceptor { chain ->
            val request = chain.request().newBuilder()
                .addHeader("x-api-key", apiKey)
                .addHeader("anthropic-version", "2023-06-01")
                .addHeader("content-type", "application/json")
                .build()
            chain.proceed(request)
        })
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(httpClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    private val api = retrofit.create(ClaudeApi::class.java)

    // ─── LlmClient implementation ──────────────────────────

    override suspend fun assessMeetingImportance(request: LlmImportanceRequest): LlmImportanceResponse {
        val prompt = buildImportancePrompt(request)

        val apiRequest = ClaudeMessageRequest(
            model = model,
            max_tokens = 256,
            messages = listOf(
                ClaudeMessage(role = "user", content = prompt),
            ),
        )

        val apiResponse = api.createMessage(apiRequest)
        val text = apiResponse.content.firstOrNull()?.text
            ?: throw IllegalStateException("Empty response from Claude API")

        return parseImportanceResponse(text)
    }

    override suspend fun draftMessage(request: LlmMessageRequest): LlmMessageResponse {
        val prompt = buildMessagePrompt(request)

        val apiRequest = ClaudeMessageRequest(
            model = model,
            max_tokens = 512,
            messages = listOf(
                ClaudeMessage(role = "user", content = prompt),
            ),
        )

        val apiResponse = api.createMessage(apiRequest)
        val text = apiResponse.content.firstOrNull()?.text
            ?: throw IllegalStateException("Empty response from Claude API")

        return parseMessageResponse(text)
    }

    override fun isAvailable(): Boolean = apiKey.isNotBlank()

    // ─── Prompt builders ────────────────────────────────────

    private fun buildImportancePrompt(request: LlmImportanceRequest): String {
        return """
            You are a meeting importance classifier. Analyze the following meeting and classify its importance as HIGH, MEDIUM, or LOW.

            Respond ONLY with valid JSON matching this exact schema:
            {"importance": "HIGH"|"MEDIUM"|"LOW", "confidence": 0.0-1.0, "explanation": "One sentence."}

            Meeting details:
            - Title: ${request.title}
            - Description: ${request.description ?: "None"}
            - Organizer: ${request.organizer ?: "Unknown"}
            - Attendees: ${request.attendeeCount} people
            - Recurring: ${request.isRecurring}
            - Duration: ${request.duration} minutes
            - Time: ${request.timeOfDay} on ${request.dayOfWeek}

            Classification rules:
            - HIGH: 1:1 with manager, customer-facing, deadline reviews, interviews, incident response
            - MEDIUM: team standups, recurring syncs, planning sessions, design reviews
            - LOW: optional meetings, FYI-only, social events, large all-hands that are recorded

            JSON response:
        """.trimIndent()
    }

    private fun buildMessagePrompt(request: LlmMessageRequest): String {
        val typeLabel = when (request.messageType) {
            MessageType.CANCELLATION_EMAIL -> "cancellation email"
            MessageType.RESCHEDULE_EMAIL -> "reschedule email"
            MessageType.SHORT_SMS -> "short SMS message"
        }

        return """
            Draft a professional $typeLabel for a meeting.

            Meeting: ${request.meetingTitle}
            Recipient: ${request.recipientName ?: "the attendee"}
            Reason: ${request.reason}
            ${if (request.newTime != null) "New proposed time: ${request.newTime}" else ""}

            Rules:
            - Be polite, concise, and respectful
            - One brief apology maximum
            - Do NOT mention health conditions or stress levels
            - Keep under 100 words
            - Professional but warm tone

            Respond ONLY with JSON:
            {"subject": "subject line or null for SMS", "body": "message body"}

            JSON response:
        """.trimIndent()
    }

    // ─── Response parsers ───────────────────────────────────

    private fun parseImportanceResponse(text: String): LlmImportanceResponse {
        // Extract JSON from response (handle markdown code blocks)
        val jsonStr = extractJson(text)
        val parsed = json.decodeFromString<ImportanceJson>(jsonStr)

        return LlmImportanceResponse(
            importance = when (parsed.importance.uppercase()) {
                "HIGH" -> Importance.HIGH
                "MEDIUM" -> Importance.MEDIUM
                "LOW" -> Importance.LOW
                else -> Importance.MEDIUM
            },
            confidence = parsed.confidence.coerceIn(0f, 1f),
            explanation = parsed.explanation,
        )
    }

    private fun parseMessageResponse(text: String): LlmMessageResponse {
        val jsonStr = extractJson(text)
        val parsed = json.decodeFromString<MessageJson>(jsonStr)

        return LlmMessageResponse(
            subject = parsed.subject?.takeIf { it.isNotBlank() },
            body = parsed.body,
        )
    }

    private fun extractJson(text: String): String {
        // Handle ```json ... ``` wrapping
        val codeBlockPattern = Regex("```(?:json)?\\s*\\n?(.*?)\\n?```", RegexOption.DOT_MATCHES_ALL)
        val match = codeBlockPattern.find(text)
        if (match != null) return match.groupValues[1].trim()

        // Try to find JSON object directly
        val braceStart = text.indexOf('{')
        val braceEnd = text.lastIndexOf('}')
        if (braceStart >= 0 && braceEnd > braceStart) {
            return text.substring(braceStart, braceEnd + 1)
        }

        return text.trim()
    }
}

// ─── Retrofit API interface ─────────────────────────────────

interface ClaudeApi {
    @POST("v1/messages")
    suspend fun createMessage(@Body request: ClaudeMessageRequest): ClaudeMessageResponse
}

// ─── API request/response DTOs ──────────────────────────────

@Serializable
data class ClaudeMessageRequest(
    val model: String,
    val max_tokens: Int = 256,
    val messages: List<ClaudeMessage>,
)

@Serializable
data class ClaudeMessage(
    val role: String,
    val content: String,
)

@Serializable
data class ClaudeMessageResponse(
    val id: String,
    val content: List<ClaudeContentBlock>,
    val model: String,
    val stop_reason: String? = null,
)

@Serializable
data class ClaudeContentBlock(
    val type: String,
    val text: String = "",
)

// ─── Parsed response types ──────────────────────────────────

@Serializable
private data class ImportanceJson(
    val importance: String,
    val confidence: Float,
    val explanation: String,
)

@Serializable
private data class MessageJson(
    val subject: String? = null,
    val body: String,
)
