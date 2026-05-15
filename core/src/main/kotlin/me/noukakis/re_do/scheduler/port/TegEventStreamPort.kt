package me.noukakis.re_do.scheduler.port

import me.noukakis.re_do.scheduler.model.TEGEvent

interface TegEventListener {
    fun onEvent(event: TEGEvent)
    fun onComplete()
    fun onError(t: Throwable)
}

interface TegEventStreamPort {
    fun stream(tegId: String, listener: TegEventListener): AutoCloseable
}
