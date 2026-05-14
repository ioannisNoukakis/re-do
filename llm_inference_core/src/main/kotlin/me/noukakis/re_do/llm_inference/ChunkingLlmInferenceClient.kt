package me.noukakis.re_do.llm_inference

private val WHITESPACE_CHARS = charArrayOf(' ', '\t', '\n', '\r')

class ChunkingLlmInferenceClient(
    private val backend: LlmBackendPort,
) : LlmInferenceClient {

    override fun execute(request: LlmRequest): LlmResponse {
        val budget = request.contextWindowTokens - request.maxTokens
        val systemTokens = backend.countTokens(request.systemPrompt, request.model)
        if (systemTokens >= budget) {
            throw LlmBackendException(
                "System prompt ($systemTokens tokens) does not fit in remaining context window of $budget tokens (contextWindowTokens=${request.contextWindowTokens}, maxTokens=${request.maxTokens})",
            )
        }
        return completeFitting(request, budget, systemTokens)
    }

    private fun completeFitting(request: LlmRequest, budget: Int, systemTokens: Int): LlmResponse {
        val userTokens = backend.countTokens(request.userContent, request.model)
        if (systemTokens + userTokens <= budget) {
            return backend.complete(request)
        }
        val split = splitInHalf(request.userContent)
            ?: throw LlmBackendException(
                "User content ($userTokens tokens) exceeds available context window ($budget tokens minus system prompt) and cannot be split further",
            )
        val left = completeFitting(request.copy(userContent = split.first), budget, systemTokens)
        val right = completeFitting(request.copy(userContent = split.second), budget, systemTokens)
        return combine(left, right)
    }

    private fun splitInHalf(text: String): Pair<String, String>? {
        val trimmed = text.trim()
        if (trimmed.length < 2) return null
        val mid = trimmed.length / 2
        val leftEnd = trimmed.lastIndexOfAny(WHITESPACE_CHARS, mid)
        val rightStart = trimmed.indexOfAny(WHITESPACE_CHARS, mid)
        val splitAt = when {
            leftEnd > 0 && (mid - leftEnd) <= (rightStart - mid).let { if (it < 0) Int.MAX_VALUE else it } -> leftEnd
            rightStart > 0 -> rightStart
            else -> -1
        }
        if (splitAt <= 0) return null
        val left = trimmed.substring(0, splitAt).trim()
        val right = trimmed.substring(splitAt + 1).trim()
        return left to right
    }

    private fun combine(a: LlmResponse, b: LlmResponse): LlmResponse = LlmResponse(
        content = a.content + "\n\n" + b.content,
        finishReason = when {
            a.finishReason == "length" || b.finishReason == "length" -> "length"
            else -> a.finishReason
        },
        promptTokens = a.promptTokens + b.promptTokens,
        completionTokens = a.completionTokens + b.completionTokens,
    )
}
