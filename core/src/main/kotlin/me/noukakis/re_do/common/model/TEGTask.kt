package me.noukakis.re_do.common.model

import me.noukakis.re_do.scheduler.model.TEGArtefactDefinition
import me.noukakis.re_do.scheduler.model.TEGDependencyKey
import kotlin.time.Duration

data class TEGTask(
    val name: String,
    val implementationName: String,
    val inputs: List<TEGArtefactDefinition>,
    val outputs: List<TEGArtefactDefinition>,
    val arguments: List<String>,
    val timeout: Duration,
) {
    fun toRunTaskMessageNoArtefacts() = TEGMessageOut.TEGRunTaskMessage(
        taskName = name,
        implementationName = implementationName,
        artefacts = emptyList(),
        arguments = arguments,
        timeout = timeout,
    )

    fun toDependencyKey() = TEGDependencyKey(
        taskName = name,
        inputArtefacts = inputs
    )
}