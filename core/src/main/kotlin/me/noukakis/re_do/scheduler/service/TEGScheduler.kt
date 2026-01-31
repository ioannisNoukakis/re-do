package me.noukakis.re_do.scheduler.service

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import me.noukakis.re_do.scheduler.model.TEGTask
import me.noukakis.re_do.scheduler.model.TegSchedulingError
import me.noukakis.re_do.scheduler.port.MessagingPort
import me.noukakis.re_do.scheduler.port.PersistencePort
import me.noukakis.re_do.scheduler.port.UUIDPort

data class ScheduleTEGCommand(
    val tasks: List<TEGTask>
)

class TEGScheduler(
    private val messagingPort: MessagingPort,
    private val persistencePort: PersistencePort,
    private val uuidPort: UUIDPort,
) {
    fun scheduleTeg(command: ScheduleTEGCommand): Either<TegSchedulingError, Unit> {
        if (command.tasks.isEmpty()) {
            return TegSchedulingError.EmptyTegNotAllowed.left()
        }
        command.tasks
            .filter { it.inputs.isEmpty() }
            .forEach { messagingPort.send(it.toRunTaskMessage()) }
        persistencePort.setStateForTeg(
            uuidPort.generateUUID(),
            command.tasks.associate { task ->
                val dependencyKey = task.toDependencyKey()
                dependencyKey to null
            }
        )
        return Unit.right()
    }
}