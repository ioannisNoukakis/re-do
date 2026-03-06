package me.noukakis.re_do.scheduler.model

sealed interface TaskRunnerError {
    data class ImplementationNotFound(val implementationName: String) : TaskRunnerError
    data class TaskFailed(val taskName: String, val reason: String) : TaskRunnerError
    data class TaskTimedOut(val taskName: String) : TaskRunnerError
}