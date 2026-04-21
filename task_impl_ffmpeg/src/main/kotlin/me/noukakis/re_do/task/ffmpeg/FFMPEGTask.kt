package me.noukakis.re_do.task.ffmpeg

import me.noukakis.re_do.runner.model.LocalTegArtefact
import me.noukakis.re_do.runner.port.TaskExecutionContext
import me.noukakis.re_do.runner.port.TaskHandler
import me.noukakis.re_do.runner.port.TaskImplementationResult

private const val IMPLEMENTATION_NAME = "FFMPEGTask"

class FFMPEGTask : TaskHandler {
    override fun run(
        artefacts: List<LocalTegArtefact>,
        arguments: List<String>,
        context: TaskExecutionContext
    ): TaskImplementationResult {
        TODO("Not yet implemented")
    }

    override fun implementationName(): String = IMPLEMENTATION_NAME
}