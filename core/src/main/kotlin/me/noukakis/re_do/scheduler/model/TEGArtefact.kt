package me.noukakis.re_do.scheduler.model

sealed interface TEGArtefact {
    fun name(): String

    data class TEGArtefactFile(val name: String, val ref: String, val storedWith: String) : TEGArtefact {
        override fun name(): String = name
    }

    data class TEGArtefactStringValue(val name: String, val value: String) : TEGArtefact {
        override fun name(): String = name
    }
}