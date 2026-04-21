package me.noukakis.re_do.runner.service

import kotlinx.coroutines.test.runTest
import me.noukakis.re_do.common.model.TEGMessageIn
import me.noukakis.re_do.common.model.TEGMessageOut
import me.noukakis.re_do.scheduler.model.TEGArtefact
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
    inner class `Should run a task` {
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
    inner class `Should run a task with arguments and artefacts` {
        @BeforeEach
        fun setUp() {
            sut.givenTheUUidsToReturn("a", "b")
            sut.givenTheMessage(
                TEGMessageOut.TEGRunTaskMessage(
                    taskName = TEST_TASK_NAME,
                    implementationName = TEST_TASK_IMPL_NAME,
                    artefacts = listOf(
                        TEGArtefact.TEGArtefactFile(
                            name = "input.txt",
                            ref = "storage_backend_ref_for_input.txt",
                            storedWith = "StubFileStorageAdapter",
                        ),
                        TEGArtefact.TEGArtefactFile(
                            name = "input2.txt",
                            ref = "storage_backend_ref_for_input2.txt",
                            storedWith = "StubFileStorageAdapter",
                        ),
                    ),
                    arguments = listOf("arg1", "arg2"),
                    timeout = Duration.INFINITE,
                )
            )
            sut.givenTheFileInStorage(
                fileId = "storage_backend_ref_for_input.txt",
            )
            sut.givenTheFileInStorage(
                fileId = "storage_backend_ref_for_input2.txt",
            )
        }

        @Test
        fun `should not fail`() = runTest {
            sut.givenASuccessfulImplementationWithFileRefs(
                expectedFileRefsPaths = listOf(
                    WORKING_DIR.resolve("input.txt"),
                    WORKING_DIR.resolve("input2.txt"),
                )
            )

            sut.whenTheTaskIsRun()

            sut.thenTheTaskShouldCompleteSuccessfully()
        }

        @Test
        fun `should emit the completed event with artefacts`() = runTest {
            sut.givenASuccessfulImplementationWithFileRefs(
                expectedFileRefsPaths = listOf(
                    WORKING_DIR.resolve("input.txt"),
                    WORKING_DIR.resolve("input2.txt"),
                )
            )

            sut.whenTheTaskIsRun()

            sut.thenTheEventsShouldBeEmitted(
                TEGMessageIn.TEGTaskResultMessage(
                    taskName = TEST_TASK_NAME,
                    outputArtefacts = listOf(
                        TEGArtefact.TEGArtefactFile(
                            name = "input.txt",
                            ref = "a",
                            storedWith = "StubFileStorageAdapter",
                        ),
                        TEGArtefact.TEGArtefactFile(
                            name = "input2.txt",
                            ref = "b",
                            storedWith = "StubFileStorageAdapter",
                        ),
                    ),
                )
            )
        }

        @Test
        fun `close the working directory when completed`() = runTest {
            sut.givenASuccessfulImplementation()

            sut.whenTheTaskIsRun()

            sut.thenTheWorkingDirectoryIsClosed()
        }

        @Test
        fun `close the working directory upon failure`() = runTest {
            sut.givenAFailingImplementation(reason = "Something went wrong")

            sut.whenTheTaskIsRun()

            sut.thenTheWorkingDirectoryIsClosed()
        }
    }

    @Nested
    inner class `Should handle a failing artefact download` {
        @BeforeEach
        fun setUp() {
            sut.givenASuccessfulImplementation()
            sut.givenTheMessage(
                TEGMessageOut.TEGRunTaskMessage(
                    taskName = TEST_TASK_NAME,
                    implementationName = TEST_TASK_IMPL_NAME,
                    artefacts = listOf(
                        TEGArtefact.TEGArtefactFile(
                            name = "input.txt",
                            ref = "storage_backend_ref_for_input.txt",
                            storedWith = "StubFileStorageAdapter",
                        ),
                    ),
                    arguments = emptyList(),
                    timeout = Duration.INFINITE,
                )
            )
            // intentionally NOT adding the file to storage so download throws
            sut.givenTheArtefactDownloadWillFail("storage_backend_ref_for_input.txt")
        }

        @Test
        fun `should fail`() = runTest {
            sut.whenTheTaskIsRun()

            sut.thenTheTaskShouldFail(TaskRunnerError.TaskFailed::class)
        }

        @Test
        fun `should send a failed message`() = runTest {
            sut.whenTheTaskIsRun()
            sut.thenTheFailedEventShouldBeEmittedNoCheckReason()
        }

        @Test
        fun `should close the working directory`() = runTest {
            sut.whenTheTaskIsRun()

            sut.thenTheWorkingDirectoryIsClosed()
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
                TaskRunnerError.ImplementationNotFound(TEG_ID, TEST_TASK_IMPL_NAME)
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
                TaskRunnerError.TaskFailed(TEG_ID, TEST_TASK_NAME, "Something went wrong")
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
    inner class `Should handle a task that throws an exception` {
        private lateinit var exceptionStackTrace: String

        @BeforeEach
        fun setUp() {
            exceptionStackTrace = sut.givenAnImplementationThatThrowsAnException(
                exception = RuntimeException("Unexpected crash!")
            )
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
        fun `should emit a failed event with the full stacktrace`() = runTest {
            sut.whenTheTaskIsRun()

            sut.thenTheEventsShouldBeEmitted(
                TEGMessageIn.TEGTaskFailedMessage(
                    taskName = TEST_TASK_NAME,
                    reason = exceptionStackTrace,
                )
            )
        }

        @Test
        fun `should fail with task failed error containing the full stacktrace`() = runTest {
            sut.whenTheTaskIsRun()

            sut.thenTheTaskShouldFailWith(
                TaskRunnerError.TaskFailed(TEG_ID, TEST_TASK_NAME, exceptionStackTrace)
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
                TaskRunnerError.TaskTimedOut(TEG_ID, TEST_TASK_NAME)
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