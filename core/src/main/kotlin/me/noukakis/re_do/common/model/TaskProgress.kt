package me.noukakis.re_do.common.model

sealed interface TaskProgress {
    val step: String

    data class Indeterminate(override val step: String) : TaskProgress

    data class Bounded(override val step: String, val percent: Int) : TaskProgress

    data class LlmTokens(
        override val step: String,
        val inputTokens: Long,
        val outputTokens: Long,
    ) : TaskProgress
}
