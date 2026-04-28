package me.noukakis.re_do.runner.port

import me.noukakis.re_do.runner.model.LocalTegArtefact
import java.nio.file.Path

sealed interface TaskImplementationResult {
    data class Success(val outputArtefacts: List<LocalTegArtefact>) : TaskImplementationResult
    data class Failure(val reason: String) : TaskImplementationResult
}

interface TaskExecutionContext {
    fun reportProgress(progress: Int, step: String)
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

