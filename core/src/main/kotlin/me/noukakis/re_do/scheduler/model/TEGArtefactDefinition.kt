package me.noukakis.re_do.scheduler.model

enum class TEGArtefactType {
    STRING_VALUE,
    FILE,
}

data class TEGArtefactDefinition(
    val taskName: String,
    val type: TEGArtefactType,
)