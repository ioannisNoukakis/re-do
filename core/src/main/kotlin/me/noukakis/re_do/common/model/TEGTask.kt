package me.noukakis.re_do.common.model

import me.noukakis.re_do.scheduler.model.TEGArtefact
import me.noukakis.re_do.scheduler.model.TEGArtefactDefinition
import kotlin.time.Duration

data class TEGTask(
    val name: String,
    val implementationName: String,
    val inputs: List<TEGArtefactDefinition>,
    val outputs: List<TEGArtefactDefinition>,
    val arguments: List<String>,
    val timeout: Duration,
) {
    fun toRunTaskMessage(initArtefacts: List<TEGArtefact>) = TEGMessageOut.TEGRunTaskMessage(
        taskName = name,
        implementationName = implementationName,
        artefacts = inputs.map { inputDef ->
            initArtefacts.find { it.name() == inputDef.name } ?: throw IllegalArgumentException("Missing artefact for input ${inputDef.name}")
        },
        arguments = arguments,
        timeout = timeout,
    )
}