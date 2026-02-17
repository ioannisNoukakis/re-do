package me.noukakis.re_do.adapters.scheduler

import me.noukakis.re_do.scheduler.model.TEGEvent
import me.noukakis.re_do.scheduler.port.PersistencePort
import me.noukakis.re_do.scheduler.port.TegEventFilter
import kotlin.reflect.KClass

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

    override fun getTegsThatDontHaveEvent(klass: KClass<out TEGEvent>): List<String> {
        return state.filter { (_, events) ->
            events.none { klass.isInstance(it) }
        }.keys.toList()
    }
}