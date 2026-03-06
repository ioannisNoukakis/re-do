package me.noukakis.re_do.scheduler.service

import arrow.core.Either
import arrow.core.left
import arrow.core.raise.either
import arrow.core.right
import me.noukakis.re_do.common.model.Identity
import me.noukakis.re_do.scheduler.model.TEGEvent
import me.noukakis.re_do.common.model.TEGMessageIn
import me.noukakis.re_do.common.model.TEGMessageOut
import me.noukakis.re_do.common.model.TEGTask
import me.noukakis.re_do.scheduler.model.TegSchedulingError
import me.noukakis.re_do.scheduler.model.TegTimeoutCheckError
import me.noukakis.re_do.scheduler.model.TegUpdateError
import me.noukakis.re_do.scheduler.port.MessagingPort
import me.noukakis.re_do.scheduler.port.NowPort
import me.noukakis.re_do.scheduler.port.PersistencePort
import me.noukakis.re_do.scheduler.port.TegEventFilter
import me.noukakis.re_do.scheduler.port.UUIDPort
import java.time.Instant

data class ScheduleTEGCommand(
    val identity: Identity,
    val tasks: List<TEGTask>,
)

data class TEGUpdateCommand(
    val tegId: String,
    val message: TEGMessageIn
)

class TEGScheduler(
    private val messagingPort: MessagingPort,
    private val persistencePort: PersistencePort,
    private val uuidPort: UUIDPort,
    private val nowPort: NowPort,
    private val maxFailuresBeforeGivingUp: Int
) {
    fun scheduleTeg(command: ScheduleTEGCommand): Either<TegSchedulingError, String> = either {
        val now = nowPort.now()
        validateNotEmpty(command).bind()
        validateAllTaskHaveUniqueNames(command).bind()
        validateAllTaskProducedArtefactsHaveUniqueNames(command).bind()
        validateAllTaskInputExist(command).bind()
        validateAllProducedArtefactsAreConsumed(command).bind()
        val startingTasks = command.tasks.filter { it.inputs.isEmpty() }
        validateAtLeastOneStartingTask(startingTasks).bind()
        validateNoCyclicDependencies(command, startingTasks).bind()

        command.tasks
            .filter { it.inputs.isEmpty() }
            .forEach { messagingPort.send(it.toRunTaskMessageNoArtefacts()) }
        val tegId = uuidPort.generateUUID()
        persistencePort.saveEvents(tegId, listOf(TEGEvent.SubmitterIdentity(command.identity, now)) + command.tasks.flatMap {
            listOf(
                TEGEvent.Created(it, now)
            ) + if (it.inputs.isEmpty()) listOf(TEGEvent.Scheduled(it.name, now)) else emptyList()
        })
        return tegId.right()
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
                        taskNames = command.tasks.filter { t -> t.outputs.any { it.name == output.name } }
                            .map { it.name },
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
            val visited = LinkedHashMap<String, Boolean>()
            val pathVisited = LinkedHashMap<String, Boolean>()
            detectCycle(task, command.tasks, visited, pathVisited).bind()
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

    fun handleTegUpdate(tegUpdateCommand: TEGUpdateCommand): Either<TegUpdateError, Unit> = either {
        val now = nowPort.now()
        val events = persistencePort.getEventsForTeg(tegUpdateCommand.tegId, TegEventFilter.StateEvent).toMutableList()
        when (val msg = tegUpdateCommand.message) {
            is TEGMessageIn.TEGTaskResultMessage -> handleResultMsg(msg, events, tegUpdateCommand, now)
            is TEGMessageIn.TEGTaskFailedMessage -> handleFailedMessage(msg, events, tegUpdateCommand, now)
            is TEGMessageIn.TEGTaskProgressMessage -> handleProgressMessage(msg, events, tegUpdateCommand, now)
            is TEGMessageIn.TEGTaskLogMessage -> handleLogMessage(msg, events, tegUpdateCommand, now)
        }.bind()
        return Unit.right()
    }

    private fun handleResultMsg(
        msg: TEGMessageIn.TEGTaskResultMessage,
        events: MutableList<TEGEvent>,
        tegUpdateCommand: TEGUpdateCommand,
        now: Instant,
    ): Either<TegUpdateError, Unit> {
        val newEvents = mutableListOf<TEGEvent>()
        val completedEvent = TEGEvent.Completed(
            taskName = msg.taskName,
            timestamp = now,
            outputArtefacts = msg.outputArtefacts,
        )
        events.add(completedEvent)
        newEvents.add(completedEvent)
        val allCreatedTasks = events.filterIsInstance<TEGEvent.Created>().map { it.task.name }.toSet()
        val allCompletedTasks = events.filterIsInstance<TEGEvent.Completed>().map { it.taskName }.toSet()
        if (allCreatedTasks.all { allCompletedTasks.contains(it) }) {
            val noMoreTasksEvent = TEGEvent.NoMoreTasksToSchedule(timestamp = now)
            events.add(noMoreTasksEvent)
            newEvents.add(noMoreTasksEvent)
        }

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
                        implementationName = it.task.implementationName,
                        arguments = it.task.arguments,
                        artefacts = it.task.inputs.map { input ->
                            completedArtefacts.find { artefact -> artefact.name() == input.name }!!
                        }
                    ))
            }

        persistencePort.saveEvents(tegUpdateCommand.tegId, newEvents)
        return Unit.right()
    }

    private fun handleFailedMessage(
        msg: TEGMessageIn.TEGTaskFailedMessage,
        events: MutableList<TEGEvent>,
        tegUpdateCommand: TEGUpdateCommand,
        now: Instant,
    ): Either<TegUpdateError, Unit> {
        val failedEvent = TEGEvent.Failed(
            taskName = msg.taskName,
            timestamp = now,
            reason = msg.reason,
        )
        events.add(failedEvent)

        if (handleFailedEvent(tegUpdateCommand.tegId, failedEvent, events, now)) {
            return TegUpdateError.MaxRetriesExceeded(failedEvent.taskName).left()
        }

        return Unit.right()
    }

    private fun handleFailedEvent(
        tegId: String,
        failedEvent: TEGEvent.Failed,
        events: List<TEGEvent>,
        now: Instant,
    ): Boolean {
        val isThereAnEarlierSuccessfulCompletion = events.filterIsInstance<TEGEvent.Completed>()
            .any { it.taskName == failedEvent.taskName && it.timestamp.isBefore(failedEvent.timestamp) }
        val failureCount = events.filterIsInstance<TEGEvent.Failed>()
            .count { it.taskName == failedEvent.taskName }

        if (!isThereAnEarlierSuccessfulCompletion && failureCount >= maxFailuresBeforeGivingUp) {
            val tegFailedEvent = TEGEvent.TEGFailed(
                timestamp = now,
                reason = "Max retries exceeded for task ${failedEvent.taskName}"
            )
            persistencePort.saveEvents(tegId, listOf(failedEvent, tegFailedEvent))
            return true
        }

        val scheduledEvent = TEGEvent.Scheduled(taskName = failedEvent.taskName, timestamp = now)
        persistencePort.saveEvents(tegId, listOf(failedEvent) + if (!isThereAnEarlierSuccessfulCompletion) listOf(scheduledEvent) else emptyList())
        if (!isThereAnEarlierSuccessfulCompletion) {
            val offendingTask = events.filterIsInstance<TEGEvent.Created>().find { it.task.name == failedEvent.taskName }!!.task
            messagingPort.send(
                TEGMessageOut.TEGRunTaskMessage(
                    taskName = offendingTask.name,
                    implementationName = offendingTask.implementationName,
                    arguments = offendingTask.arguments,
                    artefacts = emptyList(),
                )
            )
        }
        return false
    }

    private fun handleProgressMessage(
        msg: TEGMessageIn.TEGTaskProgressMessage,
        events: MutableList<TEGEvent>,
        tegUpdateCommand: TEGUpdateCommand,
        now: Instant,
    ): Either<TegUpdateError, Unit> {
        val progressEvent = TEGEvent.Progress(
            taskName = msg.taskName,
            timestamp = now,
            progress = msg.progress,
        )
        events.add(progressEvent)
        persistencePort.saveEvents(tegUpdateCommand.tegId, listOf(progressEvent))
        return Unit.right()
    }

    private fun handleLogMessage(
        msg: TEGMessageIn.TEGTaskLogMessage,
        events: MutableList<TEGEvent>,
        tegUpdateCommand: TEGUpdateCommand,
        now: Instant,
    ): Either<TegUpdateError, Unit> {
        val logEvent = TEGEvent.Log(
            taskName = msg.taskName,
            timestamp = now,
            log = msg.log,
        )
        events.add(logEvent)
        persistencePort.saveEvents(tegUpdateCommand.tegId, listOf(logEvent))
        return Unit.right()
    }

    fun runTimeoutCheck(): Either<TegTimeoutCheckError, Unit> {
        val now = nowPort.now()
        val openTegs = persistencePort.getTegsThatDontHaveEvent(TEGEvent.NoMoreTasksToSchedule::class)
        for (tegId in openTegs) {
            val events =
                persistencePort.getEventsForTeg(tegId, TegEventFilter.StateEvent).toList()
            val tasks = events.filterIsInstance<TEGEvent.Created>().map { it.task }
            val allScheduledTasks = events.filterIsInstance<TEGEvent.Scheduled>().map { it }.toMutableList()
            val allCompletedTasks = events.filterIsInstance<TEGEvent.Completed>().map { it }.toMutableList()
            val notCompletedTasks = allScheduledTasks
                .filterNot { scheduled ->
                    allCompletedTasks.any { completed -> completed.taskName == scheduled.taskName }
                }
                .sortedByDescending { it.timestamp }
            for (scheduled in notCompletedTasks) {
                val task = tasks.find { it.name == scheduled.taskName }
                if (task == null) {
                    return TegTimeoutCheckError.ScheduledEventWithoutCreatedTask(scheduled.taskName).left()
                }
                if (!now.isAfter(scheduled.timestamp.plus(task.timeout))) {
                    continue
                }
                val failedEvent = TEGEvent.Failed(
                    taskName = task.name,
                    timestamp = now,
                    reason = "Task timed out after ${task.timeout} (started at ${scheduled.timestamp})"
                )
                if(handleFailedEvent(tegId, failedEvent, events + listOf(failedEvent), now)) {
                    return TegTimeoutCheckError.MaxRetriesExceeded(task.name).left()
                }
            }
        }
        return Unit.right()
    }
}

// https://www.geeksforgeeks.org/dsa/detect-cycle-direct-graph-using-colors/
private fun detectCycle(
    current: TEGTask,
    nodes: List<TEGTask>,
    visited: LinkedHashMap<String, Boolean>,
    pathVisited: LinkedHashMap<String, Boolean>,
): Either<TegSchedulingError, Unit> = either {
    visited[current.name] = true
    pathVisited[current.name] = true
    for (output in current.outputs) {
        val children = nodes.filter { task -> task.inputs.any { input -> input.name == output.name } }
        for (child in children) {
            if (!visited.getOrElse(child.name) {false}) {
                detectCycle(child, nodes, visited, pathVisited).bind()
            } else if (pathVisited[child.name] == true) {
                val cycle = extractCycle(
                    visited = pathVisited.keys.filter { pathVisited[it] == true }.toMutableList(),
                    boundary = child.name,
                )
                return TegSchedulingError.CyclicDependencyDetected(cycle).left()
            }
        }
    }
    pathVisited[current.name] = false
    Unit.right()
}

private fun extractCycle(
    visited: MutableList<String>,
    boundary: String,
): List<String> {
    val cycle = mutableListOf<String>()
    cycle.add(boundary)
    var current = visited.removeLast()
    while (boundary != current) {
        cycle.add(current)
        current = visited.removeLast()
    }
    cycle.add(boundary)
    return cycle.reversed()
}