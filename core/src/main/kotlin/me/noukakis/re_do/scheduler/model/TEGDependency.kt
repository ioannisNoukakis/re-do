package me.noukakis.re_do.scheduler.model

data class TEGDependencyKey(
    val taskName: String,
    val inputArtefacts: List<TEGArtefactDefinition>,
)