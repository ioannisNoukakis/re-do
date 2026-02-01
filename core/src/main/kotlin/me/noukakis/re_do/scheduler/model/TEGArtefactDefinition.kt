package me.noukakis.re_do.scheduler.model

enum class TEGArtefactType {
    STRING_VALUE,
    FILE,
}

data class TEGArtefactDefinition(
    val name: String,
    val type: TEGArtefactType,
)