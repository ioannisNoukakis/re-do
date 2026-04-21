package me.noukakis.re_do.runner.service

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import me.noukakis.re_do.common.model.TEGMessageIn
import me.noukakis.re_do.common.model.TEGMessageOut
import me.noukakis.re_do.common.port.FileStoragePort
import me.noukakis.re_do.common.port.UUIDPort
import me.noukakis.re_do.runner.model.LocalTegArtefact
import me.noukakis.re_do.runner.port.*
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
    private val uuidPort: UUIDPort,
    private val implementations: Map<String, TaskHandler> = emptyMap(),
) : TaskRunnerService {
    override suspend fun execute(tegId: String, message: TEGMessageOut): Either<TaskRunnerError, Unit> =
        when (message) {
            is TEGMessageOut.TEGRunTaskMessage -> runTask(tegId, message)
        }

    private suspend fun runTask(
        tegId: String,
        message: TEGMessageOut.TEGRunTaskMessage
    ): Either<TaskRunnerError, Unit> {
        return runTaskBase(message, tegId).onLeft { sendErrorMessage(it, tegId, message) }
    }

    private suspend fun runTaskBase(
        message: TEGMessageOut.TEGRunTaskMessage,
        tegId: String
    ): Either<TaskRunnerError, Unit> {
        try {
            tempWorkingDirPort.create().use { workingDir ->
                val impl = implementations[message.implementationName]
                    ?: return handleMissingImplementation(tegId, message)
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
                            return TaskRunnerError.TaskTimedOut(tegId, message.taskName).left()
                        },
                        ifRight = { it }
                    )

                return when (implResult) {
                    is TaskImplementationResult.Success -> {
                        val remoteArtefacts = implResult.outputArtefacts.map {
                            when (it) {
                                is LocalTegArtefact.LocalTegArtefactFile -> {
                                    val ref = fileStoragePort.upload(uuidPort.next(), it.path)
                                    TEGArtefact.TEGArtefactFile(
                                        it.name,
                                        ref.ref,
                                        ref.storedWith
                                    )
                                }

                                is LocalTegArtefact.LocalTEGArtefactStringValue -> TEGArtefact.TEGArtefactStringValue(
                                    it.name,
                                    it.value
                                )
                            }
                        }
                        messagingPort.send(
                            tegId,
                            TEGMessageIn.TEGTaskResultMessage(
                                taskName = message.taskName,
                                outputArtefacts = remoteArtefacts,
                            )
                        )
                        Unit.right()
                    }

                    is TaskImplementationResult.Failure -> TaskRunnerError.TaskFailed(
                        tegId,
                        message.taskName,
                        implResult.reason
                    ).left()
                }
            }
        } catch (e: Exception) {
            val stackTrace = e.stackTraceToString()
            return TaskRunnerError.TaskFailed(
                tegId,
                message.taskName,
                stackTrace
            ).left()
        }
    }

    private fun sendErrorMessage(
        error: TaskRunnerError,
        tegId: String,
        message: TEGMessageOut.TEGRunTaskMessage
    ) {
        when (error) {
            is TaskRunnerError.TaskTimedOut ->
                messagingPort.send(
                    tegId,
                    TEGMessageIn.TEGTaskFailedMessage(
                        taskName = message.taskName,
                        reason = "Task timed out",
                    )
                )

            is TaskRunnerError.TaskFailed -> messagingPort.send(
                tegId,
                TEGMessageIn.TEGTaskFailedMessage(
                    taskName = message.taskName,
                    reason = error.reason,
                )
            )

            is TaskRunnerError.ImplementationNotFound -> messagingPort.send(
                tegId,
                TEGMessageIn.TEGTaskFailedMessage(
                    taskName = message.taskName,
                    reason = "No implementation found for: ${message.implementationName}",
                )
            )
        }
    }

    private fun handleMissingImplementation(
        tegId: String,
        message: TEGMessageOut.TEGRunTaskMessage
    ): Either<TaskRunnerError, Nothing> {
        return TaskRunnerError.ImplementationNotFound(tegId, message.implementationName).left()
    }
}