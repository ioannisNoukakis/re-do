package me.noukakis.re_do.scheduler.adapter

import me.noukakis.re_do.scheduler.model.TEGArtefact
import me.noukakis.re_do.scheduler.model.TEGDependencyKey
import me.noukakis.re_do.scheduler.port.PersistencePort

class InMemoryPersistenceAdapter : PersistencePort {
    val state = mutableMapOf<String, Map<TEGDependencyKey, TEGArtefact?>>()

    override fun setStateForTeg(
        tegUuid: String,
        tegState: Map<TEGDependencyKey, TEGArtefact?>
    ) {
        state[tegUuid] = tegState
    }
}