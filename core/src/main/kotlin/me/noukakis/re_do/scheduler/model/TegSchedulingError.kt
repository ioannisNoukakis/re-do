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