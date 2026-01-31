package me.noukakis.re_do.scheduler.model

sealed interface TEGArtefact {
    data class TEGArtefactFile(val ref: String, val storedWith: String) : TEGArtefact
    data class TEGArtefactStringValue(val value: String) : TEGArtefact
}