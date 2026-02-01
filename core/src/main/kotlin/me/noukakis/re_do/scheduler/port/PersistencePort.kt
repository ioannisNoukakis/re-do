package me.noukakis.re_do.scheduler.port

import me.noukakis.re_do.scheduler.model.TEGEvent

enum class TegEventFilter {
    All,
    StateEvent
}

interface PersistencePort {
    fun saveEvents(tegId: String, events: List<TEGEvent>)
    fun getEventsForTeg(tegId: String, filter: TegEventFilter): List<TEGEvent>
}