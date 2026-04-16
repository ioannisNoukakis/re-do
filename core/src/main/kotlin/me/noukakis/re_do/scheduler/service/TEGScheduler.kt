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
import me.noukakis.re_do.scheduler.model.TEGArtefact
import me.noukakis.re_do.scheduler.model.TegSchedulingError
import me.noukakis.re_do.scheduler.model.TegTimeoutCheckError
import me.noukakis.re_do.scheduler.model.TegUpdateError
import me.noukakis.re_do.scheduler.port.LogPort
import me.noukakis.re_do.scheduler.port.MessagingPort
import me.noukakis.re_do.scheduler.port.NowPort
import me.noukakis.re_do.scheduler.port.PersistencePort
import me.noukakis.re_do.scheduler.port.TegEventFilter
import me.noukakis.re_do.scheduler.port.UUIDPort
import java.time.Instant
import kotlin.time.toJavaDuration

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
    private val maxFailuresBeforeGivingUp: Int,
    private val logPort: LogPort = LogPort.NoOp,
) : TegUpdateHandler {
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

        val tegId = uuidPort.generateUUID()
        logPort.info(tegId, "Scheduling TEG with ${command.tasks.size} tasks, ${startingTasks.size} that can be dispatched immediately")
        command.tasks
            .filter { it.inputs.isEmpty() }
            .forEach {
                logPort.debug(tegId, "Dispatching initial task '${it.name}'")
                messagingPort.send(tegId, it.toRunTaskMessageNoArtefacts())
            }
        persistencePort.saveEvents(
            tegId,
            listOf(TEGEvent.SubmitterIdentity(command.identity, now)) + command.tasks.flatMap {
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

    override fun handleTegUpdate(command: TEGUpdateCommand): Either<TegUpdateError, Unit> = either {
        // FIXME I need an exclusion lock here to avoid race conditions when multiple messages for the same TEG are
        //  being processed in parallel (e.g. two tasks complete at the same time, or a task completes while another
        //  one fails and triggers a retry)
        val now = nowPort.now()
        logPort.debug(command.tegId, "Handling ${command.message::class.simpleName}")
        when (val msg = command.message) {
            is TEGMessageIn.TEGTaskResultMessage -> handleResultMsg(msg, getStateEvents(command), command, now)
            is TEGMessageIn.TEGTaskFailedMessage -> handleFailedMessage(msg, getStateEvents(command), command, now)
            is TEGMessageIn.TEGTaskProgressMessage -> handleProgressMessage(msg, command, now)
            is TEGMessageIn.TEGTaskLogMessage -> handleLogMessage(msg, command, now)
        }.bind()
        return Unit.right()
    }

    private fun getStateEvents(command: TEGUpdateCommand): List<TEGEvent> =
        persistencePort.getEventsForTeg(command.tegId, TegEventFilter.StateEvent)

    private fun handleResultMsg(
        msg: TEGMessageIn.TEGTaskResultMessage,
        events: List<TEGEvent>,
        command: TEGUpdateCommand,
        now: Instant,
    ): Either<TegUpdateError, Unit> {
        val eventsToVerify = events.toMutableList()
        val newEvents = mutableListOf<TEGEvent>()
        val completedEvent = TEGEvent.Completed(
            taskName = msg.taskName,
            timestamp = now,
            outputArtefacts = msg.outputArtefacts,
        )
        eventsToVerify.add(completedEvent)
        newEvents.add(completedEvent)
        logPort.info(command.tegId, "Task '${msg.taskName}' completed")
        val allCreatedTasks = eventsToVerify.filterIsInstance<TEGEvent.Created>().map { it.task.name }.toSet()
        val allCompletedTasks = eventsToVerify.filterIsInstance<TEGEvent.Completed>().map { it.taskName }.toSet()
        if (allCreatedTasks.all { allCompletedTasks.contains(it) }) {
            logPort.info(command.tegId, "All tasks completed, TEG finished")
            val noMoreTasksEvent = TEGEvent.NoMoreTasksToSchedule(timestamp = now)
            eventsToVerify.add(noMoreTasksEvent)
            newEvents.add(noMoreTasksEvent)
        }

        val completedArtefacts = eventsToVerify.filterIsInstance<TEGEvent.Completed>()
            .flatMap { it.outputArtefacts }
        val tasksThatWereAlreadyScheduled = eventsToVerify.filterIsInstance<TEGEvent.Scheduled>()
            .map { it.taskName }
            .toSet()
        eventsToVerify.filterIsInstance<TEGEvent.Created>()
            .filter { event ->
                !tasksThatWereAlreadyScheduled.contains(event.task.name)
                        && event.task.inputs.all { input -> completedArtefacts.find { input.name == it.name() } != null }
            }
            .forEach { sendTaskMessage(command.tegId, it, completedArtefacts) }

        persistencePort.saveEvents(command.tegId, newEvents)
        return Unit.right()
    }

    private fun handleFailedMessage(
        msg: TEGMessageIn.TEGTaskFailedMessage,
        events: List<TEGEvent>,
        command: TEGUpdateCommand,
        now: Instant,
    ): Either<TegUpdateError, Unit> {
        logPort.warn(command.tegId, "Task '${msg.taskName}' failed: ${msg.reason}")
        val failedEvent = TEGEvent.Failed(
            taskName = msg.taskName,
            timestamp = now,
            reason = msg.reason,
        )

        if (handleFailedEvent(command.tegId, failedEvent, events + listOf(failedEvent), now)) {
            return TegUpdateError.MaxRetriesExceeded(command.tegId, failedEvent.taskName).left()
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
        if (isThereAnEarlierSuccessfulCompletion) {
            logPort.info(tegId, "Task '${failedEvent.taskName}' failed after a prior successful completion — ignoring")
            persistencePort.saveEvents(tegId, listOf(failedEvent))
            return false
        }

        val failureCount = events.filterIsInstance<TEGEvent.Failed>()
            .count { it.taskName == failedEvent.taskName }
        if (failureCount >= maxFailuresBeforeGivingUp) {
            logPort.error(tegId, "Task '${failedEvent.taskName}' exceeded max retries ($maxFailuresBeforeGivingUp), marking TEG as failed")
            val tegFailedEvent = TEGEvent.TEGFailed(
                timestamp = now,
                reason = "Max retries exceeded for task ${failedEvent.taskName}"
            )
            persistencePort.saveEvents(tegId, listOf(failedEvent, tegFailedEvent))
            return true
        }

        logPort.warn(tegId, "Task '${failedEvent.taskName}' failed (failure #$failureCount / $maxFailuresBeforeGivingUp), retrying")
        val scheduledEvent = TEGEvent.Scheduled(taskName = failedEvent.taskName, timestamp = now)
        persistencePort.saveEvents(tegId, listOf(failedEvent, scheduledEvent))

        val offendingTask =
            events.filterIsInstance<TEGEvent.Created>().find { it.task.name == failedEvent.taskName }!!.task
        val completedArtefacts = events.filterIsInstance<TEGEvent.Completed>()
            .flatMap { it.outputArtefacts }
        sendTaskMessage(tegId, TEGEvent.Created(offendingTask, now), completedArtefacts)
        return false
    }

    private fun handleProgressMessage(
        msg: TEGMessageIn.TEGTaskProgressMessage,
        command: TEGUpdateCommand,
        now: Instant,
    ): Either<TegUpdateError, Unit> {
        logPort.debug(command.tegId, "Task '${msg.taskName}' progress: ${msg.progress}%")
        val progressEvent = TEGEvent.Progress(
            taskName = msg.taskName,
            timestamp = now,
            progress = msg.progress,
        )
        persistencePort.saveEvents(command.tegId, listOf(progressEvent))
        return Unit.right()
    }

    private fun handleLogMessage(
        msg: TEGMessageIn.TEGTaskLogMessage,
        command: TEGUpdateCommand,
        now: Instant,
    ): Either<TegUpdateError, Unit> {
        logPort.debug(command.tegId, "Task '${msg.taskName}': ${msg.log}")
        val logEvent = TEGEvent.Log(
            taskName = msg.taskName,
            timestamp = now,
            log = msg.log,
        )
        persistencePort.saveEvents(command.tegId, listOf(logEvent))
        return Unit.right()
    }

    fun runTimeoutCheck(): Either<TegTimeoutCheckError, Unit> {
        val now = nowPort.now()
        for ((tegId, events) in persistencePort.getTegsThatDontHaveEvents(listOf(
            TEGEvent.NoMoreTasksToSchedule::class,
            TEGEvent.TEGFailed::class,
        ))) {
            val tasks = events.filterIsInstance<TEGEvent.Created>().map { it.task }
            val allScheduledTasks = events.filterIsInstance<TEGEvent.Scheduled>().map { it }
            val allCompletedTasks = events.filterIsInstance<TEGEvent.Completed>().map { it }
            val notCompletedTasks = allScheduledTasks
                .filterNot { scheduled ->
                    allCompletedTasks.any { completed -> completed.taskName == scheduled.taskName }
                }
                .sortedByDescending { it.timestamp }
            for (scheduled in notCompletedTasks) {
                val task = tasks.find { it.name == scheduled.taskName }
                if (task == null) {
                    return TegTimeoutCheckError.ScheduledEventWithoutCreatedTask(tegId, scheduled.taskName).left()
                }
                if (!now.isAfter(scheduled.timestamp.plus(task.timeout.toJavaDuration()))) {
                    continue
                }
                logPort.warn(tegId, "Task '${task.name}' timed out after ${task.timeout} (scheduled at ${scheduled.timestamp})")
                val failedEvent = TEGEvent.Failed(
                    taskName = task.name,
                    timestamp = now,
                    reason = "Task timed out after ${task.timeout} (started at ${scheduled.timestamp})"
                )
                if (handleFailedEvent(tegId, failedEvent, events + listOf(failedEvent), now)) {
                    return TegTimeoutCheckError.MaxRetriesExceeded(tegId, task.name).left()
                }
            }
        }
        return Unit.right()
    }

    private fun sendTaskMessage(
        tegId: String,
        created: TEGEvent.Created,
        completedArtefacts: List<TEGArtefact>
    ) {
        logPort.debug(tegId, "Dispatching task '${created.task.name}'")
        messagingPort.send(
            tegId,
            TEGMessageOut.TEGRunTaskMessage(
                taskName = created.task.name,
                implementationName = created.task.implementationName,
                arguments = created.task.arguments,
                artefacts = created.task.inputs.map { input ->
                    completedArtefacts.find { artefact -> artefact.name() == input.name }!!
                },
                timeout = created.task.timeout,
            )
        )
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
            if (!visited.getOrElse(child.name) { false }) {
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