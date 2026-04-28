package me.noukakis.re_do.runner.service

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import me.noukakis.re_do.adapters.driven.common.InMemoryMessagingAdapter
import me.noukakis.re_do.adapters.driven.common.StubFileStorageAdapter
import me.noukakis.re_do.adapters.driven.common.StubUuidAdapter
import me.noukakis.re_do.adapters.driven.runner.FakeTempWorkingDirAdapter
import me.noukakis.re_do.common.model.TEGMessageIn
import me.noukakis.re_do.common.model.TEGMessageOut
import me.noukakis.re_do.common.port.StoredFileRef
import me.noukakis.re_do.runner.model.LocalTegArtefact
import me.noukakis.re_do.runner.port.*
import me.noukakis.re_do.scheduler.model.TaskRunnerError
import org.junit.jupiter.api.Assertions.*
import java.nio.file.Path
import kotlin.reflect.KClass
import kotlin.time.Duration

const val TEG_ID = "tegId"
const val TEST_TASK_NAME = "TestTask"
const val TEST_TASK_IMPL_NAME = "TestTaskImpl"

val WORKING_DIR = Path.of("workingDir")

class ConfigurableRunWithTimeoutAdapter : RunWithTimeoutPort {
    private var shouldTimeout: Boolean = false

    fun willTimeout() {
        shouldTimeout = true
    }

    override suspend fun <T> execute(supplier: suspend () -> T, timeout: Duration): Either<TaskTimedOut, T> =
        if (shouldTimeout) TaskTimedOut.left()
        else supplier().right()
}

class TaskRunnerSutBuilder {
    private val messagingAdapter = InMemoryMessagingAdapter()
    private val runWithTimeoutAdapter = ConfigurableRunWithTimeoutAdapter()
    private val tempWorkingDirPort = FakeTempWorkingDirAdapter(WORKING_DIR)
    private val uuidPort = StubUuidAdapter()
    private val storageAdapter = StubFileStorageAdapter()
    private lateinit var message: TEGMessageOut
    private lateinit var result: Either<TaskRunnerError, Unit>
    private val implementations = mutableMapOf<String, TaskHandler>()

    fun givenTheMessage(tegRunTaskMessage: TEGMessageOut.TEGRunTaskMessage) {
        message = tegRunTaskMessage
    }

    fun givenTheFileInStorage(
        fileId: String,
    ) {
        storageAdapter.storage[fileId] = StoredFileRef(
            ref = fileId,
            storedWith = "StubFileStorageAdapter"
        )
    }

    fun givenTheArtefactDownloadWillFail(fileId: String) {
        // Intentionally not adding to storage so download throws
        storageAdapter.storage.remove(fileId)
    }

    fun givenTheUUidsToReturn(vararg uuids: String) {
        uuidPort.uuidsToReturn = uuids.toList()
    }

    fun givenTheImplementation(
        name: String,
        impl: TaskHandler,
    ) {
        implementations[name] = impl
    }

    fun givenASuccessfulImplementation(name: String = TEST_TASK_IMPL_NAME) {
        givenTheImplementation(name, object : TaskHandler {
            override fun run(
                artefacts: List<LocalTegArtefact>,
                arguments: List<String>,
                context: TaskExecutionContext,
            ) = TaskImplementationResult.Success(outputArtefacts = emptyList())

            override fun implementationName(): String {
                return name
            }
        })
    }

    fun givenASuccessfulImplementationWithFileRefs(name: String = TEST_TASK_IMPL_NAME, expectedFileRefsPaths: List<Path>) {
        givenTheImplementation(name, object : TaskHandler {
            override fun run(
                artefacts: List<LocalTegArtefact>,
                arguments: List<String>,
                context: TaskExecutionContext,
            ): TaskImplementationResult{
                expectedFileRefsPaths.forEach { expected ->
                    artefacts.filterIsInstance<LocalTegArtefact.LocalTegArtefactFile>()
                        .find { it.path == expected }
                        ?: throw IllegalStateException("Expected file ref with path $expected not found in artefacts")
                }
                return TaskImplementationResult.Success(outputArtefacts = artefacts)
            }

            override fun implementationName(): String {
                return name
            }
        })
    }

    fun givenAnImplementationThatThrowsAnException(name: String = TEST_TASK_IMPL_NAME, exception: Exception): String {
        val stackTrace = exception.stackTraceToString()
        givenTheImplementation(name, object : TaskHandler {
            override fun run(
                artefacts: List<LocalTegArtefact>,
                arguments: List<String>,
                context: TaskExecutionContext,
            ): TaskImplementationResult {
                throw exception
            }

            override fun implementationName(): String {
                return name
            }
        })
        return stackTrace
    }

    fun givenAFailingImplementation(name: String = TEST_TASK_IMPL_NAME, reason: String) {
        givenTheImplementation(name, object : TaskHandler {
            override fun run(
                artefacts: List<LocalTegArtefact>,
                arguments: List<String>,
                context: TaskExecutionContext,
            ) = TaskImplementationResult.Failure(reason)

            override fun implementationName(): String {
                return name
            }
        })
    }

    fun givenAnImplementationThatReportsProgress(
        name: String = TEST_TASK_IMPL_NAME,
        step: String = "task_progress",
        vararg progressValues: Int,
    ) {
        givenTheImplementation(name, object : TaskHandler {
            override fun run(
                artefacts: List<LocalTegArtefact>,
                arguments: List<String>,
                context: TaskExecutionContext,
            ): TaskImplementationResult {
                progressValues.forEach { context.reportProgress(it, step) }
                return TaskImplementationResult.Success(outputArtefacts = emptyList())
            }

            override fun implementationName(): String {
                return name
            }
        })
    }

    fun givenAnImplementationThatOutputsLocalFiles(
        name: String = TEST_TASK_IMPL_NAME,
        outputFileNames: List<String>,
    ) {
        givenTheImplementation(name, object : TaskHandler {
            override fun run(
                artefacts: List<LocalTegArtefact>,
                arguments: List<String>,
                context: TaskExecutionContext,
            ): TaskImplementationResult = TaskImplementationResult.Success(
                outputArtefacts = outputFileNames.map { LocalTegArtefact.LocalTegArtefactFile(it, WORKING_DIR.resolve(it)) }
            )

            override fun implementationName(): String = name
        })
    }

    fun givenAnImplementationThatReportsLogs(
        name: String = TEST_TASK_IMPL_NAME,
        vararg logs: String,
    ) {
        givenTheImplementation(name, object : TaskHandler {
            override fun run(
                artefacts: List<LocalTegArtefact>,
                arguments: List<String>,
                context: TaskExecutionContext,
            ): TaskImplementationResult {
                logs.forEach { context.reportLog(it) }
                return TaskImplementationResult.Success(outputArtefacts = emptyList())
            }

            override fun implementationName(): String {
                return name
            }
        })
    }

    fun givenTheTaskWillTimeout() {
        runWithTimeoutAdapter.willTimeout()
    }

    suspend fun whenTheTaskIsRun() {
        result = TaskRunner(
            messagingAdapter,
            runWithTimeoutAdapter,
            tempWorkingDirPort,
            storageAdapter,
            uuidPort,
            implementations
        )
            .execute(TEG_ID, message)
    }

    fun thenTheTaskShouldCompleteSuccessfully() {
        assert(result.isRight()) {
            "Expected task to complete successfully, but got error: ${result.swap().getOrNull()}"
        }
    }

    fun <T : Any>thenTheTaskShouldFail(expectedType: KClass<T>) {
        assert(result.isLeft()) {
            "Expected task to fail, but it completed successfully"
        }
        val error = result.swap().getOrNull()
        assertInstanceOf(expectedType.java, error)
    }

    fun thenTheTaskShouldFailWith(expected: TaskRunnerError) {
        assert(result.isLeft()) {
            "Expected task to fail, but it completed successfully"
        }
        assertEquals(expected, result.swap().getOrNull())
    }

    fun thenTheEventsShouldBeEmitted(vararg messages: TEGMessageIn) {
        assertEquals(messages.map { TEG_ID to it }.toList(), messagingAdapter.incomingMessages)
    }

    fun thenTheFailedEventShouldBeEmittedNoCheckReason() {
        assertInstanceOf(
            TEGMessageIn.TEGTaskFailedMessage::class.java,
            messagingAdapter.incomingMessages.first().second,
            "Expected message to be of type TEGTaskFailedMessage, but was ${messagingAdapter.incomingMessages.first().second::class.java}")
    }

    fun thenTheWorkingDirectoryIsClosed() {
        assertTrue(tempWorkingDirPort.wasClosed, {
            "Expected the temporary working directory to be closed, but it was not"
        })
    }
}