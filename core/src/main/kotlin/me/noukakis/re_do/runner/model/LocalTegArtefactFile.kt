package me.noukakis.re_do.runner.model

import java.nio.file.Path

sealed interface LocalTegArtefact {

    data class LocalTEGArtefactStringValue(val name: String, val value: String) : LocalTegArtefact
    data class LocalTegArtefactFile(val name: String, val path: Path) : LocalTegArtefact
}