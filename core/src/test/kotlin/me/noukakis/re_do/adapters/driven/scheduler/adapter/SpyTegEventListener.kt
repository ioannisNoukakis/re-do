package me.noukakis.re_do.adapters.driven.scheduler.adapter

import me.noukakis.re_do.scheduler.model.TEGEvent
import me.noukakis.re_do.scheduler.port.TegEventListener

class SpyTegEventListener : TegEventListener {
    val receivedEvents: MutableList<TEGEvent> = mutableListOf()
    var completed: Boolean = false
    val errors: MutableList<Throwable> = mutableListOf()

    override fun onEvent(event: TEGEvent) {
        receivedEvents.add(event)
    }

    override fun onComplete() {
        completed = true
    }

    override fun onError(t: Throwable) {
        errors.add(t)
    }
}
