package me.noukakis.re_do.scheduler.port

import me.noukakis.re_do.scheduler.model.TEGEvent
import java.util.stream.Stream
import kotlin.reflect.KClass

enum class TegEventFilter {
    All,
    StateEvent
}

interface PersistencePort {
    fun saveEvents(tegId: String, events: List<TEGEvent>)
    fun getEventsForTeg(tegId: String, filter: TegEventFilter): List<TEGEvent>
    fun getTegsThatDontHaveEvents(klass: List<KClass<out TEGEvent>>): Stream<Pair<String, List<TEGEvent>>>
}