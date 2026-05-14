package me.noukakis.re_do.task.llm_summarise

import me.noukakis.re_do.llm_inference.LlmInferenceClient
import me.noukakis.re_do.llm_inference.LlmRequest
import me.noukakis.re_do.llm_inference.LlmResponse

class FakeLlmInferenceClient(
    private val response: LlmResponse = LlmResponse(
        content = "SUMMARY",
        finishReason = "stop",
        promptTokens = 5,
        completionTokens = 3,
    ),
) : LlmInferenceClient {
    val calls: MutableList<LlmRequest> = mutableListOf()

    override fun execute(request: LlmRequest): LlmResponse {
        calls += request
        return response
    }
}
