package me.noukakis.re_do.adapters.driven.scheduler

import me.noukakis.re_do.scheduler.model.TEGEvent
import me.noukakis.re_do.scheduler.port.PersistencePort
import me.noukakis.re_do.scheduler.port.TegEventFilter
import me.noukakis.re_do.scheduler.port.TegEventListener
import me.noukakis.re_do.scheduler.port.TegEventStreamPort
import java.util.stream.Stream
import kotlin.reflect.KClass
import kotlin.streams.asStream

class InMemoryPersistenceAdapter :
    PersistencePort,
    TegEventStreamPort {
    val state = mutableMapOf<String, List<TEGEvent>>()
    var throwOnPersist: String? = null
    var throwOnGetEvents: String? = null

    private val listeners = mutableMapOf<String, MutableList<TegEventListener>>()
    private val lock = Any()

    override fun saveEvents(
        tegId: String,
        events: List<TEGEvent>,
    ) {
        synchronized(lock) {
            if (throwOnPersist != null) {
                throw RuntimeException(throwOnPersist)
            }
            if (!state.containsKey(tegId)) {
                state[tegId] = mutableListOf()
            }
            state[tegId] = state[tegId]!! + events
            notifyListeners(tegId, events)
        }
    }

    override fun getEventsForTeg(
        tegId: String,
        filter: TegEventFilter,
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

    override fun getTegsThatDontHaveEvents(klass: List<KClass<out TEGEvent>>): Stream<Pair<String, List<TEGEvent>>> = state.entries.asSequence()
        .filter { entry ->
            entry.value.none { klass.contains(it::class) }
        }
        .map { entry -> entry.key to entry.value }
        .asStream()

    override fun stream(tegId: String, listener: TegEventListener): AutoCloseable {
        synchronized(lock) {
            val history = state[tegId] ?: emptyList()
            for (event in history) {
                listener.onEvent(event)
                if (event.isTerminal()) {
                    listener.onComplete()
                    return AutoCloseable {}
                }
            }
            listeners.getOrPut(tegId) { mutableListOf() }.add(listener)
            return AutoCloseable {
                synchronized(lock) {
                    listeners[tegId]?.remove(listener)
                }
            }
        }
    }

    private fun notifyListeners(tegId: String, events: List<TEGEvent>) {
        val ls = listeners[tegId] ?: return
        for (event in events) {
            val terminal = event.isTerminal()
            for (l in ls.toList()) {
                l.onEvent(event)
                if (terminal) {
                    l.onComplete()
                    ls.remove(l)
                }
            }
        }
    }

    private fun TEGEvent.isTerminal(): Boolean = this is TEGEvent.NoMoreTasksToSchedule || this is TEGEvent.TEGFailed
}
