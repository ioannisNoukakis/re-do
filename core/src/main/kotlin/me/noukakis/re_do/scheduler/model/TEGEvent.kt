package me.noukakis.re_do.scheduler.model

import me.noukakis.re_do.common.model.Identity
import java.time.Instant

sealed interface TEGEvent {
    data class SubmitterIdentity(
        val identity: Identity,
        val timestamp: Instant,
    ) : TEGEvent

    data class Created(
        val task: TEGTask,
        val timestamp: Instant,
    ) : TEGEvent

    data class Scheduled(
        val taskName: String,
        val timestamp: Instant,
    ) : TEGEvent

    data class Completed(
        val taskName: String,
        val timestamp: Instant,
        val outputArtefacts: List<TEGArtefact>,
    ) : TEGEvent

    data class NoMoreTasksToSchedule(
        val timestamp: Instant,
    ) : TEGEvent

    data class TEGFailed(
        val timestamp: Instant,
        val reason: String,
    ) : TEGEvent

    data class Failed(
        val taskName: String,
        val timestamp: Instant,
        val reason: String,
    ) : TEGEvent

    data class Progress(
        val taskName: String,
        val timestamp: Instant,
        val progress: Int,
    ) : TEGEvent

    data class Log(
        val taskName: String,
        val timestamp: Instant,
        val log: String,
    ) : TEGEvent
}