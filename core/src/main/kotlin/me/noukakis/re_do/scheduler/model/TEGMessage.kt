package me.noukakis.re_do.scheduler.model

enum class TEGMessageType {
    RUN_TASK,
}

data class TEGMessage(
    val type: TEGMessageType,
    val taskName: String,
)