package me.noukakis.re_do.adapters.driven.scheduler

import me.noukakis.re_do.scheduler.model.TEGEvent
import me.noukakis.re_do.scheduler.port.PersistencePort
import me.noukakis.re_do.scheduler.port.TegEventFilter
import java.util.stream.Stream
import kotlin.reflect.KClass
import kotlin.streams.asStream

class InMemoryPersistenceAdapter : PersistencePort {
    val state = mutableMapOf<String, List<TEGEvent>>()
    var throwOnPersist: String? = null
    var throwOnGetEvents: String? = null

    override fun saveEvents(
        tegId: String,
        events: List<TEGEvent>
    ) {
        if (throwOnPersist != null) {
            throw RuntimeException(throwOnPersist)
        }
        if (!state.containsKey(tegId)) {
            state[tegId] = mutableListOf()
        }
        state[tegId] = state[tegId]!! + events
    }

    override fun getEventsForTeg(
        tegId: String,
        filter: TegEventFilter
    ): List<TEGEvent> {
        if (throwOnGetEvents != null) {
            throw RuntimeException(throwOnGetEvents)
        }
        val events = state[tegId] ?: return emptyList()
        return when (filter) {
            TegEventFilter.All -> events
            TegEventFilter.StateEvent -> events.filter {
                !(it is TEGEvent.Log || it is TEGEvent.Progress)
            }
        }
    }

    override fun getTegsThatDontHaveEvents(klass: List<KClass<out TEGEvent>>): Stream<Pair<String, List<TEGEvent>>> {
        return state.entries.asSequence()
            .filter { entry ->
                entry.value.none { klass.contains(it::class) }
            }
            .map { entry -> entry.key to entry.value }
            .asStream()
    }
}