package me.noukakis.re_do.runner.port

import me.noukakis.re_do.runner.model.LocalTegArtefact
import me.noukakis.re_do.scheduler.model.TEGArtefact
import java.nio.file.Path

sealed interface TaskImplementationResult {
    data class Success(val outputArtefacts: List<TEGArtefact>) : TaskImplementationResult
    data class Failure(val reason: String) : TaskImplementationResult
}

interface TaskExecutionContext {
    fun reportProgress(progress: Int)
    fun reportLog(log: String)
    fun workingDir(): Path
}

interface TaskHandler {
    fun run(
        artefacts: List<LocalTegArtefact>,
        arguments: List<String>,
        context: TaskExecutionContext,
    ): TaskImplementationResult

    fun implementationName(): String
}

