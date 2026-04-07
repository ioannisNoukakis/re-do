package me.noukakis.re_do.adapters.driven.runner

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import me.noukakis.re_do.adapters.driven.common.InMemoryMessagingAdapter
import me.noukakis.re_do.common.model.TEGMessageIn
import me.noukakis.re_do.common.model.TEGMessageOut
import me.noukakis.re_do.runner.port.*
import me.noukakis.re_do.runner.service.TaskRunner
import me.noukakis.re_do.scheduler.model.TEGArtefact
import me.noukakis.re_do.scheduler.model.TaskRunnerError
import org.junit.jupiter.api.Assertions.assertEquals
import kotlin.time.Duration

const val TEG_ID = "tegId"
const val TEST_TASK_NAME = "TestTask"
const val TEST_TASK_IMPL_NAME = "TestTaskImpl"

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
    private lateinit var message: TEGMessageOut
    private lateinit var result: Either<TaskRunnerError, Unit>
    private val implementations = mutableMapOf<String, TaskHandler>()

    fun givenTheMessage(tegRunTaskMessage: TEGMessageOut.TEGRunTaskMessage) {
        message = tegRunTaskMessage
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
                artefacts: List<TEGArtefact>,
                arguments: List<String>,
                context: TaskExecutionContext,
            ) = TaskImplementationResult.Success(outputArtefacts = emptyList())

            override fun implementationName(): String {
                return name
            }
        })
    }

    fun givenAFailingImplementation(name: String = TEST_TASK_IMPL_NAME, reason: String) {
        givenTheImplementation(name, object : TaskHandler {
            override fun run(
                artefacts: List<TEGArtefact>,
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
        vararg progressValues: Int,
    ) {
        givenTheImplementation(name, object : TaskHandler {
            override fun run(
                artefacts: List<TEGArtefact>,
                arguments: List<String>,
                context: TaskExecutionContext,
            ): TaskImplementationResult {
                progressValues.forEach { context.reportProgress(it) }
                return TaskImplementationResult.Success(outputArtefacts = emptyList())
            }

            override fun implementationName(): String {
                return name
            }
        })
    }

    fun givenAnImplementationThatReportsLogs(
        name: String = TEST_TASK_IMPL_NAME,
        vararg logs: String,
    ) {
        givenTheImplementation(name, object : TaskHandler {
            override fun run(
                artefacts: List<TEGArtefact>,
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
        result = TaskRunner(messagingAdapter, runWithTimeoutAdapter, implementations)
            .execute(TEG_ID, message)
    }

    fun thenTheTaskShouldCompleteSuccessfully() {
        assert(result.isRight()) {
            "Expected task to complete successfully, but got error: ${result.swap().getOrNull()}"
        }
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
}