package me.noukakis.re_do.scheduler.model

data class TEGTask (
    val name: String,
    val inputs: List<TEGArtefactDefinition>,
    val outputs: List<TEGArtefactDefinition>
) {
    fun toRunTaskMessage(): TEGMessage {
        return TEGMessage(
            type = TEGMessageType.RUN_TASK,
            taskName = name
        )
    }
}