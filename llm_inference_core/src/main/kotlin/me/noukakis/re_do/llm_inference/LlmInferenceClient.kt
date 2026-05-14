package me.noukakis.re_do.llm_inference

data class LlmRequest(
    val systemPrompt: String,
    val userContent: String,
    val model: String,
    val maxTokens: Int,
    val temperature: Double,
    val timeoutSeconds: Int,
    val contextWindowTokens: Int,
)

data class LlmResponse(
    val content: String,
    val finishReason: String,
    val promptTokens: Int,
    val completionTokens: Int,
)

interface LlmInferenceClient {
    fun complete(request: LlmRequest): LlmResponse
}

interface LlmBackendPort {
    fun complete(request: LlmRequest): LlmResponse
    fun countTokens(text: String, model: String): Int
}

sealed class LlmException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
class LlmBackendException(message: String, cause: Throwable? = null) : LlmException(message, cause)
