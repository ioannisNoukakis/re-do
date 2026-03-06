package me.noukakis.re_do.runner.port

import me.noukakis.re_do.scheduler.model.TEGArtefact

sealed interface TaskImplementationResult {
    data class Success(val outputArtefacts: List<TEGArtefact>) : TaskImplementationResult
    data class Failure(val reason: String) : TaskImplementationResult
}

interface TaskExecutionContext {
    fun reportProgress(progress: Int)
    fun reportLog(log: String)
}

interface TaskImplementationPort {
    fun run(
        artefacts: List<TEGArtefact>,
        arguments: List<String>,
        context: TaskExecutionContext,
    ): TaskImplementationResult
}

