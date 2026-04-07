package me.noukakis.re_do.scheduler.model

sealed interface TaskRunnerError {
    data class ImplementationNotFound(val tegId: String, val implementationName: String) : TaskRunnerError
    data class TaskFailed(val tegId: String, val taskName: String, val reason: String) : TaskRunnerError
    data class TaskTimedOut(val tegId: String, val taskName: String) : TaskRunnerError
}