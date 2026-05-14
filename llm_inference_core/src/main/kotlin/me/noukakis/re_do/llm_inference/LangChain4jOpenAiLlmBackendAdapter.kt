package me.noukakis.re_do.llm_inference

import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.model.chat.response.ChatResponse
import dev.langchain4j.model.openai.OpenAiChatModel
import dev.langchain4j.model.openai.OpenAiTokenCountEstimator
import dev.langchain4j.model.output.FinishReason
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

private const val ENV_API_KEY = "LLM_API_KEY"
private const val ENV_BASE_URL = "LLM_BASE_URL"
private const val ENV_DEFAULT_MODEL = "LLM_DEFAULT_MODEL"
private const val ENV_DEBUG_LOGGING = "LLM_DEBUG_LOGGING"

class LangChain4jOpenAiLlmBackendAdapter(
    private val apiKey: String? = System.getenv(ENV_API_KEY),
    private val baseUrl: String? = System.getenv(ENV_BASE_URL),
    private val defaultModel: String? = System.getenv(ENV_DEFAULT_MODEL),
    private val debugLoggingEnabled: Boolean = System.getenv(ENV_DEBUG_LOGGING) == "true",
) : LlmBackendPort {

    private val log = LoggerFactory.getLogger(LangChain4jOpenAiLlmBackendAdapter::class.java)
    private val estimators = ConcurrentHashMap<String, OpenAiTokenCountEstimator>()

    init {
        if (apiKey.isNullOrBlank()) {
            throw IllegalArgumentException("$ENV_API_KEY environment variable is not set")
        }
    }

    override fun complete(request: LlmRequest): LlmResponse {
        val effectiveModel = request.model.ifBlank { defaultModel ?: error("No model in request and $ENV_DEFAULT_MODEL is not set") }
        val builder = OpenAiChatModel.builder()
            .apiKey(apiKey)
            .modelName(effectiveModel)
            .temperature(request.temperature)
            .maxTokens(request.maxTokens)
            .timeout(Duration.ofSeconds(request.timeoutSeconds.toLong()))
        baseUrl?.let { builder.baseUrl(it) }
        val model = builder.build()

        if (debugLoggingEnabled && log.isDebugEnabled) {
            log.debug("LLM request systemPrompt={} userContent={}", request.systemPrompt, request.userContent)
        }
        log.info("LLM call model={} maxTokens={} temperature={}", effectiveModel, request.maxTokens, request.temperature)

        val started = System.nanoTime()
        val response: ChatResponse = model.chat(SystemMessage.from(request.systemPrompt), UserMessage.from(request.userContent))
        val durationMs = (System.nanoTime() - started) / 1_000_000

        val usage = response.tokenUsage()
        val mapped = LlmResponse(
            content = response.aiMessage().text().trim(),
            finishReason = mapFinishReason(response.finishReason()),
            promptTokens = usage?.inputTokenCount() ?: 0,
            completionTokens = usage?.outputTokenCount() ?: 0,
        )
        log.info(
            "LLM done model={} promptTokens={} completionTokens={} finishReason={} durationMs={}",
            effectiveModel,
            mapped.promptTokens,
            mapped.completionTokens,
            mapped.finishReason,
            durationMs,
        )
        if (debugLoggingEnabled && log.isDebugEnabled) {
            log.debug("LLM response content={}", mapped.content)
        }
        return mapped
    }

    override fun countTokens(text: String, model: String): Int {
        val effectiveModel = model.ifBlank { defaultModel ?: error("No model and $ENV_DEFAULT_MODEL is not set") }
        return estimators.computeIfAbsent(effectiveModel) { OpenAiTokenCountEstimator(it) }
            .estimateTokenCountInText(text)
    }

    private fun mapFinishReason(reason: FinishReason?): String = when (reason) {
        FinishReason.STOP -> "stop"
        FinishReason.LENGTH -> "length"
        FinishReason.TOOL_EXECUTION -> "tool_calls"
        FinishReason.CONTENT_FILTER -> "content_filter"
        FinishReason.OTHER -> "other"
        null -> "unknown"
    }
}
