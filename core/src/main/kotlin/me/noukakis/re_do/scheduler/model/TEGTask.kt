package me.noukakis.re_do.scheduler.model

import java.time.Duration

data class TEGTask(
    val name: String,
    val inputs: List<TEGArtefactDefinition>,
    val outputs: List<TEGArtefactDefinition>,
    val timeout: Duration,
) {
    fun toRunTaskMessageNoArtefacts() = TEGMessageOut.TEGRunTaskMessage(
        taskName = name,
        artefacts = emptyList(),
    )

    fun toDependencyKey() = TEGDependencyKey(
        taskName = name,
        inputArtefacts = inputs
    )
}