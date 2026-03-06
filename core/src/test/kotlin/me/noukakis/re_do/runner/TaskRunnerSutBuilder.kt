package me.noukakis.re_do.runner

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import me.noukakis.re_do.adapters.common.InMemoryMessagingAdapter
import me.noukakis.re_do.runner.port.RunWithTimeoutPort
import me.noukakis.re_do.runner.port.TaskExecutionContext
import me.noukakis.re_do.runner.port.TaskImplementationPort
import me.noukakis.re_do.runner.port.TaskImplementationResult
import me.noukakis.re_do.runner.service.TaskRunner
import me.noukakis.re_do.common.model.TEGMessageIn
import me.noukakis.re_do.common.model.TEGMessageOut
import me.noukakis.re_do.runner.port.TaskTimedOut
import me.noukakis.re_do.scheduler.model.TaskRunnerError
import me.noukakis.re_do.scheduler.model.TEGArtefact
import org.junit.jupiter.api.Assertions.assertEquals

const val TEST_TASK_NAME = "TestTask"
const val TEST_TASK_IMPL_NAME = "TestTaskImpl"

class ConfigurableRunWithTimeoutAdapter : RunWithTimeoutPort {
    private var shouldTimeout: Boolean = false

    fun willTimeout() {
        shouldTimeout = true
    }

    override fun <T> run(supplier: () -> T): Either<TaskTimedOut, T> =
        if (shouldTimeout) TaskTimedOut.left()
        else supplier().right()
}

class TaskRunnerSutBuilder {
    private val messagingAdapter = InMemoryMessagingAdapter()
    private val runWithTimeoutAdapter = ConfigurableRunWithTimeoutAdapter()
    private lateinit var message: TEGMessageOut
    private lateinit var result: Either<TaskRunnerError, Unit>
    private val implementations = mutableMapOf<String, TaskImplementationPort>()

    fun givenTheMessage(tegRunTaskMessage: TEGMessageOut.TEGRunTaskMessage) {
        message = tegRunTaskMessage
    }

    fun givenTheImplementation(
        name: String,
        impl: TaskImplementationPort,
    ) {
        implementations[name] = impl
    }

    fun givenASuccessfulImplementation(name: String = TEST_TASK_IMPL_NAME) {
        givenTheImplementation(name, object : TaskImplementationPort {
            override fun run(
                artefacts: List<TEGArtefact>,
                arguments: List<String>,
                context: TaskExecutionContext,
            ) = TaskImplementationResult.Success(outputArtefacts = emptyList())
        })
    }

    fun givenAFailingImplementation(name: String = TEST_TASK_IMPL_NAME, reason: String) {
        givenTheImplementation(name, object : TaskImplementationPort {
            override fun run(
                artefacts: List<TEGArtefact>,
                arguments: List<String>,
                context: TaskExecutionContext,
            ) = TaskImplementationResult.Failure(reason)
        })
    }

    fun givenAnImplementationThatReportsProgress(
        name: String = TEST_TASK_IMPL_NAME,
        vararg progressValues: Int,
    ) {
        givenTheImplementation(name, object : TaskImplementationPort {
            override fun run(
                artefacts: List<TEGArtefact>,
                arguments: List<String>,
                context: TaskExecutionContext,
            ): TaskImplementationResult {
                progressValues.forEach { context.reportProgress(it) }
                return TaskImplementationResult.Success(outputArtefacts = emptyList())
            }
        })
    }

    fun givenAnImplementationThatReportsLogs(
        name: String = TEST_TASK_IMPL_NAME,
        vararg logs: String,
    ) {
        givenTheImplementation(name, object : TaskImplementationPort {
            override fun run(
                artefacts: List<TEGArtefact>,
                arguments: List<String>,
                context: TaskExecutionContext,
            ): TaskImplementationResult {
                logs.forEach { context.reportLog(it) }
                return TaskImplementationResult.Success(outputArtefacts = emptyList())
            }
        })
    }

    fun givenTheTaskWillTimeout() {
        runWithTimeoutAdapter.willTimeout()
    }

    fun whenTheTaskIsRun() {
        result = TaskRunner(messagingAdapter, runWithTimeoutAdapter, implementations).execute(message)
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
        assertEquals(messages.toList(), messagingAdapter.incomingMessages)
    }
}