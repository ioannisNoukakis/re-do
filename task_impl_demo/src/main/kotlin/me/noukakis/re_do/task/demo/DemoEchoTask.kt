package me.noukakis.re_do.task.demo

import me.noukakis.re_do.runner.port.TaskExecutionContext
import me.noukakis.re_do.runner.port.TaskHandler
import me.noukakis.re_do.runner.port.TaskImplementationResult
import me.noukakis.re_do.scheduler.model.TEGArtefact

private const val IMPLEMENTATION_NAME = "DemoEchoTask"

class DemoEchoTask : TaskHandler {
    override fun run(
        artefacts: List<TEGArtefact>,
        arguments: List<String>,
        context: TaskExecutionContext
    ): TaskImplementationResult {
        print("Echoing artefacts: ${artefacts.joinToString(", ") { it.name() }} and arguments: ${arguments.joinToString(", ")}")
        return TaskImplementationResult.Success(outputArtefacts = artefacts)
    }

    override fun implementationName(): String = IMPLEMENTATION_NAME
}