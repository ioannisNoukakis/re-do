package me.noukakis.re_do.llm_inference

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

private fun baseRequest(
    systemPrompt: String = "sys",
    userContent: String = "abc",
    maxTokens: Int = 10,
    contextWindowTokens: Int = 100,
): LlmRequest = LlmRequest(
    systemPrompt = systemPrompt,
    userContent = userContent,
    model = "test-model",
    maxTokens = maxTokens,
    temperature = 0.0,
    timeoutSeconds = 30,
    contextWindowTokens = contextWindowTokens,
)

class ChunkingLlmInferenceClientTest {

    @Nested
    inner class `When user content fits in context window` {

        @Test
        fun `returns single backend response unchanged`() {
            val backend = FakeLlmBackendAdapter { _ ->
                LlmResponse(content = "OUT", finishReason = "stop", promptTokens = 5, completionTokens = 3)
            }
            val sut = ChunkingLlmInferenceClient(backend)

            val result = sut.execute(baseRequest(userContent = "abcdef"))

            assertEquals(
                LlmResponse(content = "OUT", finishReason = "stop", promptTokens = 5, completionTokens = 3),
                result,
            )
        }

        @Test
        fun `delegates exactly one backend call`() {
            val backend = FakeLlmBackendAdapter { _ ->
                LlmResponse(content = "OUT", finishReason = "stop", promptTokens = 0, completionTokens = 0)
            }
            val sut = ChunkingLlmInferenceClient(backend)

            sut.execute(baseRequest(userContent = "abcdef"))

            assertEquals(listOf(baseRequest(userContent = "abcdef")), backend.calls)
        }
    }

    @Nested
    inner class `When user content exceeds budget` {

        @Test
        fun `splits content into two halves and concatenates responses`() {
            val backend = FakeLlmBackendAdapter { req ->
                LlmResponse(content = "[${req.userContent}]", finishReason = "stop", promptTokens = 1, completionTokens = 1)
            }
            val sut = ChunkingLlmInferenceClient(backend)

            val result = sut.execute(
                baseRequest(
                    systemPrompt = "s",
                    userContent = "alpha bravo charlie delta",
                    maxTokens = 5,
                    contextWindowTokens = 20,
                ),
            )

            assertEquals(
                LlmResponse(
                    content = "[alpha bravo]\n\n[charlie delta]",
                    finishReason = "stop",
                    promptTokens = 2,
                    completionTokens = 2,
                ),
                result,
            )
        }

        @Test
        fun `recursively splits until each chunk fits`() {
            val backend = FakeLlmBackendAdapter { req ->
                LlmResponse(content = req.userContent, finishReason = "stop", promptTokens = 0, completionTokens = 0)
            }
            val sut = ChunkingLlmInferenceClient(backend)

            sut.execute(
                baseRequest(
                    systemPrompt = "s",
                    userContent = "aa bb cc dd ee ff gg hh",
                    maxTokens = 1,
                    contextWindowTokens = 7,
                ),
            )

            assertEquals(
                listOf("aa bb", "cc dd", "ee ff", "gg hh"),
                backend.calls.map { it.userContent },
            )
        }

        @Test
        fun `splits on whitespace after midpoint when there is none before`() {
            val backend = FakeLlmBackendAdapter { req ->
                LlmResponse(content = req.userContent, finishReason = "stop", promptTokens = 0, completionTokens = 0)
            }
            val sut = ChunkingLlmInferenceClient(backend)

            sut.execute(
                baseRequest(
                    systemPrompt = "s",
                    userContent = "unsplittableleft bravo",
                    maxTokens = 1,
                    contextWindowTokens = 19,
                ),
            )

            assertEquals(
                listOf("unsplittableleft", "bravo"),
                backend.calls.map { it.userContent },
            )
        }

        @Test
        fun `splits on whitespace before midpoint when there is none after`() {
            val backend = FakeLlmBackendAdapter { req ->
                LlmResponse(content = req.userContent, finishReason = "stop", promptTokens = 0, completionTokens = 0)
            }
            val sut = ChunkingLlmInferenceClient(backend)

            sut.execute(
                baseRequest(
                    systemPrompt = "s",
                    userContent = "alpha unsplittableright",
                    maxTokens = 1,
                    contextWindowTokens = 19,
                ),
            )

            assertEquals(
                listOf("alpha", "unsplittableright"),
                backend.calls.map { it.userContent },
            )
        }

        @Test
        fun `splits on tab whitespace`() {
            val backend = FakeLlmBackendAdapter { req ->
                LlmResponse(content = req.userContent, finishReason = "stop", promptTokens = 0, completionTokens = 0)
            }
            val sut = ChunkingLlmInferenceClient(backend)

            sut.execute(
                baseRequest(
                    systemPrompt = "s",
                    userContent = "alpha\tbravo\tcharlie\tdelta",
                    maxTokens = 5,
                    contextWindowTokens = 20,
                ),
            )

            assertEquals(
                listOf("alpha\tbravo", "charlie\tdelta"),
                backend.calls.map { it.userContent },
            )
        }

        @Test
        fun `propagates length finish reason from any sub-call (right sub-call in this instance)`() {
            var callCount = 0
            val backend = FakeLlmBackendAdapter { _ ->
                callCount++
                val reason = if (callCount == 1) "length" else "stop"
                LlmResponse(content = "x", finishReason = reason, promptTokens = 0, completionTokens = 0)
            }
            val sut = ChunkingLlmInferenceClient(backend)

            val result = sut.execute(
                baseRequest(
                    systemPrompt = "s",
                    userContent = "alpha bravo charlie delta",
                    maxTokens = 5,
                    contextWindowTokens = 20,
                ),
            )

            assertEquals("length", result.finishReason)
        }

        @Test
        fun `propagates length finish reason when only the right sub-call truncates`() {
            var callCount = 0
            val backend = FakeLlmBackendAdapter { _ ->
                callCount++
                val reason = if (callCount == 1) "stop" else "length"
                LlmResponse(content = "x", finishReason = reason, promptTokens = 0, completionTokens = 0)
            }
            val sut = ChunkingLlmInferenceClient(backend)

            val result = sut.execute(
                baseRequest(
                    systemPrompt = "s",
                    userContent = "alpha bravo charlie delta",
                    maxTokens = 5,
                    contextWindowTokens = 20,
                ),
            )

            assertEquals("length", result.finishReason)
        }

        @Test
        fun `splits at the whitespace closer to the midpoint when right is closer than left`() {
            val backend = FakeLlmBackendAdapter { req ->
                LlmResponse(content = req.userContent, finishReason = "stop", promptTokens = 0, completionTokens = 0)
            }
            val sut = ChunkingLlmInferenceClient(backend)

            sut.execute(
                baseRequest(
                    systemPrompt = "s",
                    userContent = "aaa bb cccc",
                    maxTokens = 1,
                    contextWindowTokens = 12,
                ),
            )

            assertEquals(
                listOf("aaa bb", "cccc"),
                backend.calls.map { it.userContent },
            )
        }
    }

    @Nested
    inner class `When content cannot be split further` {

        @Test
        fun `throws LlmBackendException when single token cannot fit`() {
            val backend = FakeLlmBackendAdapter { _ ->
                LlmResponse(content = "", finishReason = "stop", promptTokens = 0, completionTokens = 0)
            }
            val sut = ChunkingLlmInferenceClient(backend)

            assertThrows<LlmBackendException> {
                sut.execute(
                    baseRequest(
                        systemPrompt = "s",
                        userContent = "unsplittable",
                        maxTokens = 1,
                        contextWindowTokens = 5,
                    ),
                )
            }
        }

        @Test
        fun `throws LlmBackendException when trimmed user content is shorter than two characters`() {
            val backend = FakeLlmBackendAdapter { _ ->
                LlmResponse(content = "", finishReason = "stop", promptTokens = 0, completionTokens = 0)
            }
            val sut = ChunkingLlmInferenceClient(backend)

            assertThrows<LlmBackendException> {
                sut.execute(
                    baseRequest(
                        systemPrompt = "",
                        userContent = "  a  ",
                        maxTokens = 1,
                        contextWindowTokens = 2,
                    ),
                )
            }
        }
    }

    @Nested
    inner class `When system prompt alone exceeds budget` {

        @Test
        fun `throws LlmBackendException without calling backend`() {
            val systemPrompt = "a very long system prompt that is too big"
            val backend = FakeLlmBackendAdapter { _ ->
                LlmResponse(content = "", finishReason = "stop", promptTokens = 0, completionTokens = 0)
            }
            val sut = ChunkingLlmInferenceClient(backend)

            val ex = assertThrows<LlmBackendException> {
                sut.execute(
                    baseRequest(
                        systemPrompt = systemPrompt,
                        userContent = "x",
                        maxTokens = 5,
                        contextWindowTokens = 10,
                    ),
                )
            }
            assertEquals(
                "System prompt (${systemPrompt.length} tokens) does not fit in remaining context window of 5 tokens (contextWindowTokens=10, maxTokens=5)",
                ex.message
            )
        }
    }
}
