package me.noukakis.re_do.scheduler.adapter

import me.noukakis.re_do.scheduler.model.TEGEvent
import me.noukakis.re_do.scheduler.port.PersistencePort
import me.noukakis.re_do.scheduler.port.TegEventFilter

class InMemoryPersistenceAdapter : PersistencePort {
    val state = mutableMapOf<String, List<TEGEvent>>()

    override fun saveEvents(
        tegId: String,
        events: List<TEGEvent>
    ) {
        if (!state.containsKey(tegId)) {
            state[tegId] = mutableListOf()
        }
        state[tegId] = state[tegId]!! + events
    }

    override fun getEventsForTeg(
        tegId: String,
        filter: TegEventFilter
    ): List<TEGEvent> {
        val events = state[tegId] ?: return emptyList()
        return when (filter) {
            TegEventFilter.All -> events
            TegEventFilter.StateEvent -> events.filter {
                !(it is TEGEvent.Log || it is TEGEvent.Progress)
            }
        }
    }
}