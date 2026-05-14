package me.noukakis.re_do.llm_inference

class FakeLlmBackendAdapter(
    private val responder: (LlmRequest) -> LlmResponse,
) : LlmBackendPort {

    val calls: MutableList<LlmRequest> = mutableListOf()

    override fun complete(request: LlmRequest): LlmResponse {
        calls += request
        return responder(request)
    }

    override fun countTokens(text: String, model: String): Int = text.length
}
