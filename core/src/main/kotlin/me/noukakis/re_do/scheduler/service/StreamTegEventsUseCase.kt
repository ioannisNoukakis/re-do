package me.noukakis.re_do.scheduler.service

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import me.noukakis.re_do.scheduler.model.StreamTegEventsError
import me.noukakis.re_do.scheduler.model.TEGEvent
import me.noukakis.re_do.scheduler.port.PersistencePort
import me.noukakis.re_do.scheduler.port.TegEventFilter
import me.noukakis.re_do.scheduler.port.TegEventListener
import me.noukakis.re_do.scheduler.port.TegEventStreamPort

data class StreamTegEventsCommand(
    val tegId: String,
    val callerSub: String,
)

class StreamTegEventsUseCase(
    private val persistencePort: PersistencePort,
    private val tegEventStreamPort: TegEventStreamPort,
) {
    fun execute(
        command: StreamTegEventsCommand,
        listener: TegEventListener,
    ): Either<StreamTegEventsError, AutoCloseable> {
        val events = persistencePort.getEventsForTeg(command.tegId, TegEventFilter.StateEvent)
        val submitter = events.filterIsInstance<TEGEvent.SubmitterIdentity>().firstOrNull()
            ?: return StreamTegEventsError.TegNotFound.left()
        if (submitter.identity.sub != command.callerSub) {
            return StreamTegEventsError.Forbidden.left()
        }
        return tegEventStreamPort.stream(command.tegId, listener).right()
    }
}
