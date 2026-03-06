package me.noukakis.re_do.adapters.common

import me.noukakis.re_do.common.model.TEGMessageIn
import me.noukakis.re_do.common.model.TEGMessageOut
import me.noukakis.re_do.scheduler.port.MessagingPort

class InMemoryMessagingAdapter : MessagingPort, me.noukakis.re_do.runner.port.MessagingPort {
    private val _outgoingMessages = mutableListOf<TEGMessageOut>()
    private val _incomingMessages = mutableListOf<TEGMessageIn>()
    private val outgoingCallbacks = mutableListOf<(TEGMessageOut) -> Unit>()
    private val incomingCallbacks = mutableListOf<(TEGMessageIn) -> Unit>()

    val outgoingMessages: List<TEGMessageOut>
        get() = _outgoingMessages.toList()

    val incomingMessages: List<TEGMessageIn>
        get() = _incomingMessages.toList()

    override fun send(message: TEGMessageOut) {
        _outgoingMessages.add(message)
        outgoingCallbacks.forEach { it(message) }
    }

    fun receive(message: TEGMessageIn) {
        _incomingMessages.add(message)
        incomingCallbacks.forEach { it(message) }
    }

    fun onOutgoingMessage(callback: (TEGMessageOut) -> Unit) {
        outgoingCallbacks.add(callback)
    }

    fun onIncomingMessage(callback: (TEGMessageIn) -> Unit) {
        incomingCallbacks.add(callback)
    }

    fun clearOutgoingMessages() {
        _outgoingMessages.clear()
    }

    fun clearIncomingMessages() {
        _incomingMessages.clear()
    }

    fun clearAll() {
        clearOutgoingMessages()
        clearIncomingMessages()
    }

    override fun send(message: TEGMessageIn) = receive(message)
}