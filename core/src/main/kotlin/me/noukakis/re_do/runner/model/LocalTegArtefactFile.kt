package me.noukakis.re_do.runner.model

import java.nio.file.Path

sealed interface LocalTegArtefact {
    fun name(): String

    data class LocalTEGArtefactStringValue(val name: String, val value: String) : LocalTegArtefact {
        override fun name(): String = name
    }

    data class LocalTegArtefactFile(val name: String, val path: Path) : LocalTegArtefact {
        override fun name(): String = name
    }
}