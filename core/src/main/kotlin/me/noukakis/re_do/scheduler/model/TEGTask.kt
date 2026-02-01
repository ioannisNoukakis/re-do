package me.noukakis.re_do.scheduler.model

data class TEGTask(
    val name: String,
    val inputs: List<TEGArtefactDefinition>,
    val outputs: List<TEGArtefactDefinition>
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