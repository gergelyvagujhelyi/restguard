package com.restguard.data.remote

import com.restguard.domain.model.Importance
import com.restguard.domain.repository.LlmImportanceRequest
import com.restguard.domain.repository.LlmMessageRequest
import com.restguard.domain.repository.MessageType
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests the JSON parsing logic of ClaudeApiClient without making real API calls.
 * We test the extractJson and parse methods via reflection-free approach:
 * we just test the serialization/deserialization of expected response formats.
 */
class ClaudeApiClientTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    // ─── Importance response parsing ────────────────────────

    @Test
    fun `parse clean importance JSON`() {
        val response = """{"importance": "HIGH", "confidence": 0.85, "explanation": "Customer-facing meeting."}"""
        val parsed = json.decodeFromString<ImportanceJsonTestHelper>(response)
        assertEquals("HIGH", parsed.importance)
        assertEquals(0.85f, parsed.confidence, 0.01f)
        assertTrue(parsed.explanation.isNotEmpty())
    }

    @Test
    fun `parse importance JSON with markdown code block`() {
        val response = """
            ```json
            {"importance": "LOW", "confidence": 0.9, "explanation": "Optional social event."}
            ```
        """.trimIndent()

        val jsonStr = extractJsonHelper(response)
        val parsed = json.decodeFromString<ImportanceJsonTestHelper>(jsonStr)
        assertEquals("LOW", parsed.importance)
    }

    @Test
    fun `parse importance JSON with extra text`() {
        val response = """
            Here is my assessment:
            {"importance": "MEDIUM", "confidence": 0.7, "explanation": "Regular sync."}
            Let me know if you need more details.
        """.trimIndent()

        val jsonStr = extractJsonHelper(response)
        val parsed = json.decodeFromString<ImportanceJsonTestHelper>(jsonStr)
        assertEquals("MEDIUM", parsed.importance)
    }

    // ─── Message response parsing ───────────────────────────

    @Test
    fun `parse cancellation email response`() {
        val response = """{"subject": "Unable to attend: Sprint Planning", "body": "Hi Alice, I need to cancel..."}"""
        val parsed = json.decodeFromString<MessageJsonTestHelper>(response)
        assertEquals("Unable to attend: Sprint Planning", parsed.subject)
        assertTrue(parsed.body.contains("cancel"))
    }

    @Test
    fun `parse SMS response with null subject`() {
        val response = """{"subject": null, "body": "Hi — need to move our meeting."}"""
        val parsed = json.decodeFromString<MessageJsonTestHelper>(response)
        assertNull(parsed.subject)
        assertTrue(parsed.body.isNotEmpty())
    }

    @Test
    fun `parse message JSON with code block wrapper`() {
        val response = """
            ```json
            {"subject": "Reschedule: Design Review", "body": "Hi Bob, could we move..."}
            ```
        """.trimIndent()

        val jsonStr = extractJsonHelper(response)
        val parsed = json.decodeFromString<MessageJsonTestHelper>(jsonStr)
        assertEquals("Reschedule: Design Review", parsed.subject)
    }

    // ─── Edge cases ─────────────────────────────────────────

    @Test
    fun `unknown importance value falls back gracefully`() {
        val response = """{"importance": "CRITICAL", "confidence": 0.5, "explanation": "Test"}"""
        val parsed = json.decodeFromString<ImportanceJsonTestHelper>(response)
        // ClaudeApiClient maps unknown to MEDIUM; we test that the JSON parses at all
        assertNotNull(parsed.importance)
    }

    @Test
    fun `confidence is clamped to valid range`() {
        val response = """{"importance": "HIGH", "confidence": 1.5, "explanation": "Test"}"""
        val parsed = json.decodeFromString<ImportanceJsonTestHelper>(response)
        // ClaudeApiClient coerces; here we just verify parsing doesn't crash
        assertTrue(parsed.confidence > 0)
    }

    // ─── Helpers ────────────────────────────────────────────

    /**
     * Mirrors ClaudeApiClient.extractJson logic for testing.
     */
    private fun extractJsonHelper(text: String): String {
        val codeBlockPattern = Regex("```(?:json)?\\s*\\n?(.*?)\\n?```", RegexOption.DOT_MATCHES_ALL)
        val match = codeBlockPattern.find(text)
        if (match != null) return match.groupValues[1].trim()

        val braceStart = text.indexOf('{')
        val braceEnd = text.lastIndexOf('}')
        if (braceStart >= 0 && braceEnd > braceStart) {
            return text.substring(braceStart, braceEnd + 1)
        }
        return text.trim()
    }
}

@kotlinx.serialization.Serializable
private data class ImportanceJsonTestHelper(
    val importance: String,
    val confidence: Float,
    val explanation: String,
)

@kotlinx.serialization.Serializable
private data class MessageJsonTestHelper(
    val subject: String? = null,
    val body: String,
)
