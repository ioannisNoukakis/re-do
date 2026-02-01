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
}