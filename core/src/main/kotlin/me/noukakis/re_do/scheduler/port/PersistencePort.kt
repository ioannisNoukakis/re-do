package me.noukakis.re_do.scheduler.port

import me.noukakis.re_do.scheduler.model.TEGArtefact
import me.noukakis.re_do.scheduler.model.TEGDependencyKey

interface PersistencePort {
    fun setStateForTeg(
        tegUuid: String,
        tegState: Map<TEGDependencyKey, TEGArtefact?>
    )
}