package me.noukakis.re_do.scheduler.model

import me.noukakis.re_do.common.model.Identity
import me.noukakis.re_do.common.model.TEGTask
import java.time.Instant

sealed interface TEGEvent {
    val timestamp: Instant

    data class SubmitterIdentity(
        val identity: Identity,
        override val timestamp: Instant,
    ) : TEGEvent

    data class Created(
        val task: TEGTask,
        override val timestamp: Instant,
    ) : TEGEvent

    data class Scheduled(
        val taskName: String,
        override val timestamp: Instant,
    ) : TEGEvent

    data class Completed(
        val taskName: String,
        override val timestamp: Instant,
        val outputArtefacts: List<TEGArtefact>,
    ) : TEGEvent

    data class NoMoreTasksToSchedule(
        override val timestamp: Instant,
    ) : TEGEvent

    data class TEGFailed(
        override val timestamp: Instant,
        val reason: String,
    ) : TEGEvent

    data class Failed(
        val taskName: String,
        override val timestamp: Instant,
        val reason: String,
    ) : TEGEvent

    data class Progress(
        val taskName: String,
        override val timestamp: Instant,
        val progress: Int,
    ) : TEGEvent

    data class Log(
        val taskName: String,
        override val timestamp: Instant,
        val log: String,
    ) : TEGEvent
}