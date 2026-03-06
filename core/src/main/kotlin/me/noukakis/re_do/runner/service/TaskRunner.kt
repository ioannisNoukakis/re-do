package me.noukakis.re_do.runner.service

import arrow.core.Either
import arrow.core.left
import arrow.core.raise.either
import arrow.core.right
import me.noukakis.re_do.common.model.TEGMessageIn
import me.noukakis.re_do.common.model.TEGMessageOut
import me.noukakis.re_do.runner.port.*
import me.noukakis.re_do.scheduler.model.TaskRunnerError

class TaskRunner(
    private val messagingPort: MessagingPort,
    private val runWithTimeoutPort: RunWithTimeoutPort,
    private val implementations: Map<String, TaskImplementationPort> = emptyMap(),
) {
    fun execute(message: TEGMessageOut): Either<TaskRunnerError, Unit> = when (message) {
        is TEGMessageOut.TEGRunTaskMessage -> runTask(message)
    }

    private fun runTask(message: TEGMessageOut.TEGRunTaskMessage): Either<TaskRunnerError, Unit> = either {
        val impl = implementations[message.implementationName]
            ?: return handleMissingImplementation(message)

        val context = object : TaskExecutionContext {
            override fun reportProgress(progress: Int) {
                messagingPort.send(
                    TEGMessageIn.TEGTaskProgressMessage(
                        taskName = message.taskName,
                        progress = progress,
                    )
                )
            }

            override fun reportLog(log: String) {
                messagingPort.send(
                    TEGMessageIn.TEGTaskLogMessage(
                        taskName = message.taskName,
                        log = log,
                    )
                )
            }
        }

        val implResult = runWithTimeoutPort.run { impl.run(message.artefacts, message.arguments, context) }
            .fold(
                ifLeft = {
                    messagingPort.send(
                        TEGMessageIn.TEGTaskFailedMessage(
                            taskName = message.taskName,
                            reason = "Task timed out",
                        )
                    )
                    return TaskRunnerError.TaskTimedOut(message.taskName).left()
                },
                ifRight = { it }
            )

        return when (implResult) {
            is TaskImplementationResult.Success -> {
                messagingPort.send(
                    TEGMessageIn.TEGTaskResultMessage(
                        taskName = message.taskName,
                        outputArtefacts = implResult.outputArtefacts,
                    )
                )
                Unit.right()
            }

            is TaskImplementationResult.Failure -> {
                messagingPort.send(
                    TEGMessageIn.TEGTaskFailedMessage(
                        taskName = message.taskName,
                        reason = implResult.reason,
                    )
                )
                TaskRunnerError.TaskFailed(message.taskName, implResult.reason).left()
            }
        }
    }

    private fun handleMissingImplementation(
        message: TEGMessageOut.TEGRunTaskMessage
    ): Either<TaskRunnerError, Nothing> {
        messagingPort.send(
            TEGMessageIn.TEGTaskFailedMessage(
                taskName = message.taskName,
                reason = "No implementation found for: ${message.implementationName}",
            )
        )
        return TaskRunnerError.ImplementationNotFound(message.implementationName).left()
    }
}