package me.noukakis.re_do.llm_inference

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val DEFAULT_TEST_MODEL = "gpt-4o-mini"

class LangChain4jOpenAiLlmBackendAdapterIT {

    private lateinit var sut: LangChain4jOpenAiLlmBackendAdapter

    @BeforeEach
    fun setUp() {
        assumeTrue(
            System.getenv("LLM_API_KEY") != null,
            "LLM_API_KEY is not set — skipping integration test",
        )
        sut = LangChain4jOpenAiLlmBackendAdapter()
    }

    private fun pingRequest(maxTokens: Int = 64): LlmRequest = LlmRequest(
        systemPrompt = "You are a test assistant. Reply with exactly the single English word 'pong' and nothing else.",
        userContent = "ping",
        model = System.getenv("LLM_IT_MODEL") ?: DEFAULT_TEST_MODEL,
        maxTokens = maxTokens,
        temperature = 0.0,
        timeoutSeconds = 30,
        contextWindowTokens = 4096,
    )

    @Nested
    inner class `complete` {

        @Test
        fun `returns non-blank content`() {
            val response = sut.complete(pingRequest())

            println("LLM response content: '${response.content}'")
            assertTrue(response.content.isNotBlank())
        }

        @Test
        fun `reports a positive prompt token count`() {
            val response = sut.complete(pingRequest())

            assertTrue(response.promptTokens > 0)
        }

        @Test
        fun `reports a positive completion token count`() {
            val response = sut.complete(pingRequest())

            assertTrue(response.completionTokens > 0)
        }

        @Test
        fun `reports stop finish reason when output fits within maxTokens`() {
            val response = sut.complete(pingRequest(maxTokens = 64))

            assertEquals("stop", response.finishReason)
        }
    }

    @Nested
    inner class `countTokens` {

        @Test
        fun `returns a positive count for non-empty text`() {
            val tokens = sut.countTokens("hello world", DEFAULT_TEST_MODEL)

            assertTrue(tokens > 0)
        }

        @Test
        fun `falls back to a known tokenizer when the requested model is unknown`() {
            val tokens = sut.countTokens("hello world", "translategemma:4b")

            assertTrue(tokens > 0)
        }
    }
}
