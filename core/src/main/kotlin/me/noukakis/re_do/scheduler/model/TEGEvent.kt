package me.noukakis.re_do.scheduler.model

sealed interface TEGEvent {
    data class Created(
        val task: TEGTask,
    ) : TEGEvent

    data class Scheduled(
        val taskName: String,
    ) : TEGEvent

    data class Completed(
        val taskName: String,
        val outputArtefacts: List<TEGArtefact>,
    ) : TEGEvent

    data class Failed(
        val taskName: String,
        val reason: String,
    ) : TEGEvent

    data class Progress(
        val taskName: String,
        val progress: Int,
    ) : TEGEvent

    data class Log(
        val taskName: String,
        val log: String,
    ) : TEGEvent
}