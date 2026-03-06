package me.noukakis.re_do.runner

import kotlinx.coroutines.test.runTest
import me.noukakis.re_do.common.model.TEGMessageIn
import me.noukakis.re_do.common.model.TEGMessageOut
import me.noukakis.re_do.scheduler.model.TaskRunnerError
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.time.Duration

class TaskRunnerTest {
    private lateinit var sut: TaskRunnerSutBuilder

    @BeforeEach
    fun setUp() {
        sut = TaskRunnerSutBuilder()
    }

    @Nested
    inner class `Should run a single task` {
        @BeforeEach
        fun setUp() {
            sut.givenASuccessfulImplementation()
            sut.givenTheMessage(
                TEGMessageOut.TEGRunTaskMessage(
                    taskName = TEST_TASK_NAME,
                    implementationName = TEST_TASK_IMPL_NAME,
                    artefacts = emptyList(),
                    arguments = listOf("arg1", "arg2"),
                    timeout = Duration.INFINITE,
                )
            )
        }

        @Test
        fun `should not fail`() = runTest {
            sut.whenTheTaskIsRun()

            sut.thenTheTaskShouldCompleteSuccessfully()
        }

        @Test
        fun `should emit the completed event`() = runTest {
            sut.whenTheTaskIsRun()

            sut.thenTheEventsShouldBeEmitted(
                TEGMessageIn.TEGTaskResultMessage(
                    taskName = TEST_TASK_NAME,
                    outputArtefacts = emptyList(),
                )
            )
        }
    }

    @Nested
    inner class `Should reject if implementation is not found` {
        @BeforeEach
        fun setUp() {
            sut.givenTheMessage(
                TEGMessageOut.TEGRunTaskMessage(
                    taskName = TEST_TASK_NAME,
                    implementationName = TEST_TASK_IMPL_NAME,
                    artefacts = emptyList(),
                    arguments = emptyList(),
                    timeout = Duration.INFINITE,
                )
            )
        }

        @Test
        fun `should fail`() = runTest {
            sut.whenTheTaskIsRun()

            sut.thenTheTaskShouldFailWith(
                TaskRunnerError.ImplementationNotFound(TEST_TASK_IMPL_NAME)
            )
        }

        @Test
        fun `should not emit any events`() = runTest {
            sut.whenTheTaskIsRun()

            sut.thenTheEventsShouldBeEmitted(
                TEGMessageIn.TEGTaskFailedMessage(
                    taskName = TEST_TASK_NAME,
                    reason = "No implementation found for: $TEST_TASK_IMPL_NAME",
                )
            )
        }
    }

    @Nested
    inner class `Should handle a task that errors` {
        @BeforeEach
        fun setUp() {
            sut.givenAFailingImplementation(reason = "Something went wrong")
            sut.givenTheMessage(
                TEGMessageOut.TEGRunTaskMessage(
                    taskName = TEST_TASK_NAME,
                    implementationName = TEST_TASK_IMPL_NAME,
                    artefacts = emptyList(),
                    arguments = emptyList(),
                    timeout = Duration.INFINITE,
                )
            )
        }

        @Test
        fun `should fail with task failed error`() = runTest {
            sut.whenTheTaskIsRun()

            sut.thenTheTaskShouldFailWith(
                TaskRunnerError.TaskFailed(TEST_TASK_NAME, "Something went wrong")
            )
        }

        @Test
        fun `should emit a failed event`() = runTest {
            sut.whenTheTaskIsRun()

            sut.thenTheEventsShouldBeEmitted(
                TEGMessageIn.TEGTaskFailedMessage(
                    taskName = TEST_TASK_NAME,
                    reason = "Something went wrong",
                )
            )
        }
    }

    @Nested
    inner class `Should handle progress reporting` {
        @BeforeEach
        fun setUp() {
            sut.givenAnImplementationThatReportsProgress(progressValues = intArrayOf(25, 50, 75, 100))
            sut.givenTheMessage(
                TEGMessageOut.TEGRunTaskMessage(
                    taskName = TEST_TASK_NAME,
                    implementationName = TEST_TASK_IMPL_NAME,
                    artefacts = emptyList(),
                    arguments = emptyList(),
                    timeout = Duration.INFINITE,
                )
            )
        }

        @Test
        fun `should emit progress events`() = runTest {
            sut.whenTheTaskIsRun()

            sut.thenTheEventsShouldBeEmitted(
                TEGMessageIn.TEGTaskProgressMessage(taskName = TEST_TASK_NAME, progress = 25),
                TEGMessageIn.TEGTaskProgressMessage(taskName = TEST_TASK_NAME, progress = 50),
                TEGMessageIn.TEGTaskProgressMessage(taskName = TEST_TASK_NAME, progress = 75),
                TEGMessageIn.TEGTaskProgressMessage(taskName = TEST_TASK_NAME, progress = 100),
                TEGMessageIn.TEGTaskResultMessage(taskName = TEST_TASK_NAME, outputArtefacts = emptyList()),
            )
        }
    }

    @Nested
    inner class `Should handle log reporting` {
        @BeforeEach
        fun setUp() {
            sut.givenAnImplementationThatReportsLogs(logs = arrayOf("Starting task", "Task complete"))
            sut.givenTheMessage(
                TEGMessageOut.TEGRunTaskMessage(
                    taskName = TEST_TASK_NAME,
                    implementationName = TEST_TASK_IMPL_NAME,
                    artefacts = emptyList(),
                    arguments = emptyList(),
                    timeout = Duration.INFINITE,
                )
            )
        }

        @Test
        fun `should emit log events`() = runTest {
            sut.whenTheTaskIsRun()

            sut.thenTheEventsShouldBeEmitted(
                TEGMessageIn.TEGTaskLogMessage(taskName = TEST_TASK_NAME, log = "Starting task"),
                TEGMessageIn.TEGTaskLogMessage(taskName = TEST_TASK_NAME, log = "Task complete"),
                TEGMessageIn.TEGTaskResultMessage(taskName = TEST_TASK_NAME, outputArtefacts = emptyList()),
            )
        }
    }

    @Nested
    inner class `Should handle a task that times out` {
        @BeforeEach
        fun setUp() {
            sut.givenASuccessfulImplementation()
            sut.givenTheTaskWillTimeout()
            sut.givenTheMessage(
                TEGMessageOut.TEGRunTaskMessage(
                    taskName = TEST_TASK_NAME,
                    implementationName = TEST_TASK_IMPL_NAME,
                    artefacts = emptyList(),
                    arguments = emptyList(),
                    timeout = Duration.parseOrNull("10s")!!,
                )
            )
        }

        @Test
        fun `should fail with task timed out error`() = runTest {
            sut.whenTheTaskIsRun()

            sut.thenTheTaskShouldFailWith(
                TaskRunnerError.TaskTimedOut(TEST_TASK_NAME)
            )
        }

        @Test
        fun `should emit a failed event`() = runTest {
            sut.whenTheTaskIsRun()

            sut.thenTheEventsShouldBeEmitted(
                TEGMessageIn.TEGTaskFailedMessage(
                    taskName = TEST_TASK_NAME,
                    reason = "Task timed out",
                )
            )
        }
    }
}