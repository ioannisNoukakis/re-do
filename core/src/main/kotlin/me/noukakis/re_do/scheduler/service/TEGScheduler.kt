package me.noukakis.re_do.scheduler.service

import arrow.core.Either
import arrow.core.left
import arrow.core.raise.either
import arrow.core.right
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
    private val maxFailuresBeforeGivingUp: Int
) {
    fun scheduleTeg(command: ScheduleTEGCommand): Either<TegSchedulingError, Unit> = either {
        validateNotEmpty(command).bind()
        validateAllTaskHaveUniqueNames(command).bind()
        validateAllTaskProducedArtefactsHaveUniqueNames(command).bind()
        validateAllTaskInputExist(command).bind()
        validateAllProducedArtefactsAreConsumed(command).bind()
        val startingTasks = command.tasks.filter { it.inputs.isEmpty() }
        validateAtLeastOneStartingTask(startingTasks).bind()
        validateNoCyclicDependencies(command, startingTasks).bind()

        // Schedule tasks with no inputs
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

    private fun validateNotEmpty(command: ScheduleTEGCommand): Either<TegSchedulingError, Unit> {
        if (command.tasks.isEmpty()) {
            return TegSchedulingError.EmptyTegNotAllowed.left()
        }
        return Unit.right()
    }

    private fun validateAllTaskHaveUniqueNames(
        command: ScheduleTEGCommand
    ): Either<TegSchedulingError, Unit> {
        val seenNames = mutableSetOf<String>()
        for (task in command.tasks) {
            if (seenNames.contains(task.name)) {
                return TegSchedulingError.TasksHaveTheSameName(task.name).left()
            }
            seenNames.add(task.name)
        }
        return Unit.right()
    }

    private fun validateAllTaskProducedArtefactsHaveUniqueNames(
        command: ScheduleTEGCommand
    ): Either<TegSchedulingError, Unit> {
        val seenArtefactNames = mutableSetOf<String>()
        for (task in command.tasks) {
            for (output in task.outputs) {
                if (seenArtefactNames.contains(output.name)) {
                    return TegSchedulingError.TasksProduceSameArtefactName(
                        taskNames = command.tasks.filter { t -> t.outputs.any { it.name == output.name } }.map { it.name },
                        artefactName = output.name
                    ).left()
                }
                seenArtefactNames.add(output.name)
            }
        }
        return Unit.right()
    }

    private fun validateAtLeastOneStartingTask(
        startingTasks: List<TEGTask>
    ): Either<TegSchedulingError, Unit> {
        if (startingTasks.isEmpty()) {
            return TegSchedulingError.NoStartingTaskFound.left()
        }
        return Unit.right()
    }

    private fun validateNoCyclicDependencies(
        command: ScheduleTEGCommand,
        startingTasks: List<TEGTask>
    ): Either<TegSchedulingError, Unit> = either {
        for (task in startingTasks) {
            val visited = mutableListOf<String>()
            visit(task, command.tasks, visited).bind()
        }
    }

    private fun validateAllTaskInputExist(
        command: ScheduleTEGCommand
    ): Either<TegSchedulingError, Unit> {
        val producedArtefactNames = command.tasks.flatMap { it.outputs }.map { it.name }.toSet()
        for (task in command.tasks) {
            for (input in task.inputs) {
                if (!producedArtefactNames.contains(input.name)) {
                    return TegSchedulingError.MissingArtefactProducer(
                        taskName = task.name,
                        artefactName = input.name
                    ).left()
                }
            }
        }
        return Unit.right()
    }

    private fun validateAllProducedArtefactsAreConsumed(
        command: ScheduleTEGCommand
    ): Either<TegSchedulingError, Unit> {
        val consumedArtefactNames = command.tasks.flatMap { it.inputs }.map { it.name }.toSet()
        for (task in command.tasks) {
            for (output in task.outputs) {
                if (!consumedArtefactNames.contains(output.name)) {
                    return TegSchedulingError.NotAllProducedArtefactsAreConsumed(
                        artefactName = output.name,
                        producingTaskName = task.name
                    ).left()
                }
            }
        }
        return Unit.right()

    }

    fun handleTegUpdate(tegUpdateCommand: TEGUpdateCommand): Either<TegSchedulingError, Unit> = either {
        // Stage 1 - Retrieve existing events and add the new event
        val events = persistencePort.getEventsForTeg(tegUpdateCommand.tegId, TegEventFilter.StateEvent).toMutableList()
        when (val msg = tegUpdateCommand.message) {
            is TEGMessageIn.TEGTaskResultMessage -> handleResultMsg(msg, events, tegUpdateCommand)
            is TEGMessageIn.TEGTaskFailedMessage -> handleFailedMessage(msg, events, tegUpdateCommand)
            is TEGMessageIn.TEGTaskProgressMessage -> handleProgressMessage(msg, events, tegUpdateCommand)
            is TEGMessageIn.TEGTaskLogMessage -> handleLogMessage(msg, events, tegUpdateCommand)
        }.bind()

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
        return Unit.right()
    }

    private fun handleResultMsg(
        msg: TEGMessageIn.TEGTaskResultMessage,
        events: MutableList<TEGEvent>,
        tegUpdateCommand: TEGUpdateCommand
    ): Either<TegSchedulingError, Unit> {
        val completedEvent = TEGEvent.Completed(
            taskName = msg.taskName,
            outputArtefacts = msg.outputArtefacts,
        )
        events.add(completedEvent)
        persistencePort.saveEvents(tegUpdateCommand.tegId, listOf(completedEvent))
        return Unit.right()
    }

    private fun handleFailedMessage(
        msg: TEGMessageIn.TEGTaskFailedMessage,
        events: MutableList<TEGEvent>,
        tegUpdateCommand: TEGUpdateCommand
    ): Either<TegSchedulingError, Unit> {
        val failedEvent = TEGEvent.Failed(
            taskName = msg.taskName,
            reason = msg.reason,
        )
        events.add(failedEvent)

        // Count failures for this task
        val failureCount = events.filterIsInstance<TEGEvent.Failed>()
            .count { it.taskName == msg.taskName }

        if (failureCount >= maxFailuresBeforeGivingUp) {
            // Max retries exceeded - persist failure and return error
            persistencePort.saveEvents(tegUpdateCommand.tegId, listOf(failedEvent))
            return TegSchedulingError.MaxRetriesExceeded(msg.taskName).left()
        }

        // Reschedule the task
        val scheduledEvent = TEGEvent.Scheduled(taskName = msg.taskName)
        persistencePort.saveEvents(tegUpdateCommand.tegId, listOf(failedEvent, scheduledEvent))
        messagingPort.send(
            TEGMessageOut.TEGRunTaskMessage(
                taskName = msg.taskName,
                artefacts = emptyList(),
            )
        )
        return Unit.right()
    }

    private fun handleProgressMessage(
        msg: TEGMessageIn.TEGTaskProgressMessage,
        events: MutableList<TEGEvent>,
        tegUpdateCommand: TEGUpdateCommand
    ): Either<TegSchedulingError, Unit> {
        val progressEvent = TEGEvent.Progress(
            taskName = msg.taskName,
            progress = msg.progress,
        )
        events.add(progressEvent)
        persistencePort.saveEvents(tegUpdateCommand.tegId, listOf(progressEvent))
        return Unit.right()
    }

    private fun handleLogMessage(
        msg: TEGMessageIn.TEGTaskLogMessage,
        events: MutableList<TEGEvent>,
        tegUpdateCommand: TEGUpdateCommand
    ): Either<TegSchedulingError, Unit> {
        val logEvent = TEGEvent.Log(
            taskName = msg.taskName,
            log = msg.log,
        )
        events.add(logEvent)
        persistencePort.saveEvents(tegUpdateCommand.tegId, listOf(logEvent))
        return Unit.right()
    }
}

private fun visit(
    current: TEGTask,
    allTasks: List<TEGTask>,
    visited: MutableList<String>
): Either<TegSchedulingError, Unit> = either {
    visited.add(current.name)
    for (output in current.outputs) {
        val children = allTasks.filter { task -> task.inputs.any { input -> input.name == output.name } }
        for (child in children) {
            if (visited.contains(child.name)) {
                visited.add(child.name)
                return TegSchedulingError.CyclicDependencyDetected(extractCycle(visited)).left()
            }
            visit(child, allTasks, visited).bind()
        }
    }
}

private fun extractCycle(
    visited: MutableList<String>
): List<String> {
    val cycleBoundary = visited.removeLast()
    val cycle = mutableListOf<String>()
    cycle.add(cycleBoundary)
    var current = visited.removeLast()
    while (cycleBoundary != current) {
        cycle.add(current)
        current = visited.removeLast()
    }
    cycle.add(cycleBoundary)
    return cycle.reversed()
}