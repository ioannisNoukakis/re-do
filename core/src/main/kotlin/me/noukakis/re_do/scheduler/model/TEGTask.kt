package me.noukakis.re_do.scheduler.model

data class TEGTask(
    val name: String,
    val inputs: List<TEGArtefactDefinition>,
    val outputs: List<TEGArtefactDefinition>
) {
    fun toRunTaskMessage() = TEGMessage(
        type = TEGMessageType.RUN_TASK,
        taskName = name
    )

    fun toDependencyKey() = TEGDependencyKey(
        taskName = name,
        inputArtefacts = inputs
    )
}