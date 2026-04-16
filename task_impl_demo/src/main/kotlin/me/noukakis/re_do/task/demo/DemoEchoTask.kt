package me.noukakis.re_do.task.demo

import me.noukakis.re_do.runner.model.LocalTegArtefact
import me.noukakis.re_do.runner.port.TaskExecutionContext
import me.noukakis.re_do.runner.port.TaskHandler
import me.noukakis.re_do.runner.port.TaskImplementationResult

private const val IMPLEMENTATION_NAME = "DemoEchoTask"

class DemoEchoTask : TaskHandler {
    override fun run(
        artefacts: List<LocalTegArtefact>,
        arguments: List<String>,
        context: TaskExecutionContext
    ): TaskImplementationResult {
        print("Echoing artefacts: ${artefacts.joinToString(", ") { it.name() }} and arguments: ${arguments.joinToString(", ")}")
        return TaskImplementationResult.Success(outputArtefacts = artefacts.map { when (it) {
            is LocalTegArtefact.LocalTegArtefactFile -> LocalTegArtefact.LocalTegArtefactFile("${it.name}-processed", it.path)
            is LocalTegArtefact.LocalTEGArtefactStringValue -> LocalTegArtefact.LocalTEGArtefactStringValue("${it.name}-processed", it.value)
        } })
    }

    override fun implementationName(): String = IMPLEMENTATION_NAME
}