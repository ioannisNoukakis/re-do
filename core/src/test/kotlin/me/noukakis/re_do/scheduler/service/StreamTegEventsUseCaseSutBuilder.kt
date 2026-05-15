package me.noukakis.re_do.scheduler.service

import arrow.core.Either
import arrow.core.left
import me.noukakis.re_do.adapters.driven.scheduler.InMemoryPersistenceAdapter
import me.noukakis.re_do.adapters.driven.scheduler.adapter.SpyTegEventListener
import me.noukakis.re_do.scheduler.model.StreamTegEventsError
import me.noukakis.re_do.scheduler.model.TEGEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue

class StreamTegEventsUseCaseSutBuilder {
    val persistenceAdapter = InMemoryPersistenceAdapter()
    val listener = SpyTegEventListener()

    lateinit var result: Either<StreamTegEventsError, AutoCloseable>

    fun givenTheExistingEvents(state: Map<String, List<TEGEvent>>) {
        persistenceAdapter.state.putAll(state)
    }

    fun whenStreamingEvents(tegId: String, callerSub: String) {
        val sut = StreamTegEventsUseCase(
            persistencePort = persistenceAdapter,
            tegEventStreamPort = persistenceAdapter,
        )
        result = sut.execute(
            StreamTegEventsCommand(tegId = tegId, callerSub = callerSub),
            listener,
        )
    }

    fun whenAppendingEvents(tegId: String, events: List<TEGEvent>) {
        persistenceAdapter.saveEvents(tegId, events)
    }

    fun thenTheResultIsAnError(expected: StreamTegEventsError) {
        assertEquals(expected.left(), result)
    }

    fun thenTheListenerReceivedEvents(vararg expected: TEGEvent) {
        assertEquals(expected.toList(), listener.receivedEvents)
    }

    fun thenTheListenerIsCompleted() {
        assertTrue(listener.completed, "Expected listener.onComplete() to have been called")
    }

    fun thenAHandleWasReturned() {
        assertNotNull(result.getOrNull(), "Expected a Right(AutoCloseable) but got $result")
    }
}
