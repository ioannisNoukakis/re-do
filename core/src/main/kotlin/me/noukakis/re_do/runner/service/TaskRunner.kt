package me.noukakis.re_do.runner.service

import arrow.core.Either
import arrow.core.left
import arrow.core.raise.either
import arrow.core.right
import me.noukakis.re_do.runner.port.MessagingPort
import me.noukakis.re_do.runner.port.RunWithTimeoutPort
import me.noukakis.re_do.runner.port.TaskExecutionContext
import me.noukakis.re_do.runner.port.TaskHandler
import me.noukakis.re_do.runner.port.TaskImplementationResult
import me.noukakis.re_do.common.model.TEGMessageIn
import me.noukakis.re_do.common.model.TEGMessageOut
import me.noukakis.re_do.common.port.FileStoragePort
import me.noukakis.re_do.runner.model.LocalTegArtefact
import me.noukakis.re_do.runner.port.TempWorkingDirPort
import me.noukakis.re_do.scheduler.model.TEGArtefact
import me.noukakis.re_do.scheduler.model.TaskRunnerError
import java.nio.file.Path

interface TaskRunnerService {
    suspend fun execute(tegId: String, message: TEGMessageOut): Either<TaskRunnerError, Unit>
}

class TaskRunner(
    private val messagingPort: MessagingPort,
    private val runWithTimeoutPort: RunWithTimeoutPort,
    private val tempWorkingDirPort: TempWorkingDirPort,
    private val fileStoragePort: FileStoragePort,
    private val implementations: Map<String, TaskHandler> = emptyMap(),
) : TaskRunnerService {
    override suspend fun execute(tegId: String, message: TEGMessageOut): Either<TaskRunnerError, Unit> =
        when (message) {
            is TEGMessageOut.TEGRunTaskMessage -> runTask(tegId, message)
        }

    private suspend fun runTask(
        tegId: String,
        message: TEGMessageOut.TEGRunTaskMessage
    ): Either<TaskRunnerError, Unit> = either {
        val impl = implementations[message.implementationName]
            ?: return handleMissingImplementation(tegId, message)

        tempWorkingDirPort.create().use { workingDir ->
            val context = object : TaskExecutionContext {
                override fun reportProgress(progress: Int) {
                    messagingPort.send(
                        tegId,
                        TEGMessageIn.TEGTaskProgressMessage(
                            taskName = message.taskName,
                            progress = progress,
                        )
                    )
                }

                override fun reportLog(log: String) {
                    messagingPort.send(
                        tegId,
                        TEGMessageIn.TEGTaskLogMessage(
                            taskName = message.taskName,
                            log = log,
                        )
                    )
                }

                override fun workingDir(): Path = workingDir.path()
            }

            val artefacts = message.artefacts.map {
                when (it) {
                    is TEGArtefact.TEGArtefactFile -> LocalTegArtefact.LocalTegArtefactFile(
                        it.name,
                        fileStoragePort.download(it.ref, workingDir.path().resolve(it.name()))
                    )

                    is TEGArtefact.TEGArtefactStringValue -> LocalTegArtefact.LocalTEGArtefactStringValue(
                        it.name,
                        it.value
                    )
                }
            }

            val implResult = runWithTimeoutPort.execute(
                supplier = { impl.run(artefacts, message.arguments, context) },
                timeout = message.timeout
            )
                .fold(
                    ifLeft = {
                        messagingPort.send(
                            tegId,
                            TEGMessageIn.TEGTaskFailedMessage(
                                taskName = message.taskName,
                                reason = "Task timed out",
                            )
                        )
                        return TaskRunnerError.TaskTimedOut(tegId, message.taskName).left()
                    },
                    ifRight = { it }
                )

            return when (implResult) {
                is TaskImplementationResult.Success -> {
                    messagingPort.send(
                        tegId,
                        TEGMessageIn.TEGTaskResultMessage(
                            taskName = message.taskName,
                            outputArtefacts = implResult.outputArtefacts,
                        )
                    )
                    Unit.right()
                }

                is TaskImplementationResult.Failure -> {
                    messagingPort.send(
                        tegId,
                        TEGMessageIn.TEGTaskFailedMessage(
                            taskName = message.taskName,
                            reason = implResult.reason,
                        )
                    )
                    TaskRunnerError.TaskFailed(tegId, message.taskName, implResult.reason).left()
                }
            }
        }
    }

    private fun handleMissingImplementation(
        tegId: String,
        message: TEGMessageOut.TEGRunTaskMessage
    ): Either<TaskRunnerError, Nothing> {
        messagingPort.send(
            tegId,
            TEGMessageIn.TEGTaskFailedMessage(
                taskName = message.taskName,
                reason = "No implementation found for: ${message.implementationName}",
            )
        )
        return TaskRunnerError.ImplementationNotFound(tegId, message.implementationName).left()
    }
}