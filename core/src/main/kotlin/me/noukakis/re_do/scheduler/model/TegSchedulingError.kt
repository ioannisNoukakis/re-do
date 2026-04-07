package me.noukakis.re_do.scheduler.model

sealed interface TegSchedulingError {
    data object EmptyTegNotAllowed : TegSchedulingError
    data object NoStartingTaskFound : TegSchedulingError
    data class MissingArtefactProducer(
        val taskName: String,
        val artefactName: String,
    ) : TegSchedulingError
    data class CyclicDependencyDetected(val cycle: List<String>) : TegSchedulingError
    data class TasksHaveTheSameName(val taskName: String) : TegSchedulingError
    data class TasksProduceSameArtefactName(
        val taskNames: List<String>,
        val artefactName: String,
    ): TegSchedulingError
    data class NotAllProducedArtefactsAreConsumed(
        val artefactName: String,
        val producingTaskName: String,
    ) : TegSchedulingError
}

sealed interface TegUpdateError {
    data class MaxRetriesExceeded(
        val tegId: String,
        val taskName: String,
    ) : TegUpdateError
}

sealed interface TegTimeoutCheckError {
    data class ScheduledEventWithoutCreatedTask(
        val tegId: String,
        val taskName: String,
    ) : TegTimeoutCheckError
    data class MaxRetriesExceeded(
        val tegId: String,
        val taskName: String,
    ) : TegTimeoutCheckError
}