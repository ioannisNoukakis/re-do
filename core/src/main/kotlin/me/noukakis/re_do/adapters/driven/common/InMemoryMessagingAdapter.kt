package me.noukakis.re_do.adapters.driven.common

import me.noukakis.re_do.common.model.TEGMessageIn
import me.noukakis.re_do.common.model.TEGMessageOut
import me.noukakis.re_do.scheduler.port.MessagingPort

class InMemoryMessagingAdapter : MessagingPort, me.noukakis.re_do.runner.port.MessagingPort {
    private val _outgoingMessages = mutableListOf<Pair<String, TEGMessageOut>>()
    private val _incomingMessages = mutableListOf<Pair<String, TEGMessageIn>>()

    val outgoingMessages: List<Pair<String, TEGMessageOut>>
        get() = _outgoingMessages.toList()

    val incomingMessages: List<Pair<String, TEGMessageIn>>
        get() = _incomingMessages.toList()

    override fun send(tegId: String, message: TEGMessageOut) {
        _outgoingMessages.add(tegId to message)
    }

    override fun send(tegId: String, message: TEGMessageIn) {
        _incomingMessages.add(tegId to message)
    }
}