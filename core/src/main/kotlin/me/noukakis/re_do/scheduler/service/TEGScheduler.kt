package me.noukakis.re_do.scheduler.service

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import me.noukakis.re_do.scheduler.model.TEGArtefact
import me.noukakis.re_do.scheduler.model.TEGDependencyKey
import me.noukakis.re_do.scheduler.model.TEGEvent
import me.noukakis.re_do.scheduler.model.TEGMessageIn
import me.noukakis.re_do.scheduler.model.TEGMessageOut
import me.noukakis.re_do.scheduler.model.TEGTask
import me.noukakis.re_do.scheduler.model.TegSchedulingError
import me.noukakis.re_do.scheduler.port.MessagingPort
import me.noukakis.re_do.scheduler.port.PersistencePort
import me.noukakis.re_do.scheduler.port.TegEventFilter
import me.noukakis.re_do.scheduler.port.UUIDPort

data class ScheduleTEGCommand(
    val tasks: List<TEGTask>
)

data class TEGUpdateCommand(
    val tegId: String,
    val message: TEGMessageIn
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
            .forEach { messagingPort.send(it.toRunTaskMessageNoArtefacts()) }
        val tegId = uuidPort.generateUUID()
        persistencePort.saveEvents(tegId, command.tasks.flatMap {
            listOf(
                TEGEvent.Created(it)
            ) + if (it.inputs.isEmpty()) listOf(TEGEvent.Scheduled(it.name)) else emptyList()
        })
        return Unit.right()
    }

    fun handleTegUpdate(tegUpdateCommand: TEGUpdateCommand) {
        // Stage 1 - Retrieve existing events and add the new event
        val events = persistencePort.getEventsForTeg(tegUpdateCommand.tegId, TegEventFilter.StateEvent).toMutableList()
        when (val msg = tegUpdateCommand.message) {
            is TEGMessageIn.TEGTaskResultMessage -> {
                val completedEvent = TEGEvent.Completed(
                    taskName = msg.taskName,
                    outputArtefacts = tegUpdateCommand.message.outputArtefacts,
                )
                events.add(completedEvent)
                persistencePort.saveEvents(tegUpdateCommand.tegId, listOf(completedEvent))
            }
        }

        // Stage 2 - Determine which tasks can be scheduled. Which is that all its inputs are satisfied
        // An input is satisfied if there is an artefact in the completed events that matches the dependency key
        val completedArtefacts = events.filterIsInstance<TEGEvent.Completed>()
            .flatMap { it.outputArtefacts }
        val tasksThatWereAlreadyScheduled = events.filterIsInstance<TEGEvent.Scheduled>()
            .map { it.taskName }
            .toSet()
        events.filterIsInstance<TEGEvent.Created>()
            .filter { event ->
                !tasksThatWereAlreadyScheduled.contains(event.task.name)
                && event.task.inputs.all { input -> completedArtefacts.find { input.name == it.name() } != null }
            }
            .forEach {
                messagingPort.send(
                    TEGMessageOut.TEGRunTaskMessage(
                    taskName = it.task.name,
                    artefacts = it.task.inputs.map { input ->
                        completedArtefacts.find { artefact -> artefact.name() == input.name }!!
                    }
                ))
            }
    }
}