package me.noukakis.re_do.adapters.driving.runner.spring.messaging

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import me.noukakis.re_do.adapters.common.spring.rabbitmq.MessageConverter
import me.noukakis.re_do.adapters.driving.runner.spring.RunnerMessageListener
import me.noukakis.re_do.common.model.TEGMessageOut
import me.noukakis.re_do.runner.port.RunnerErrorHandlerPort
import me.noukakis.re_do.runner.service.TaskRunnerService
import me.noukakis.re_do.scheduler.model.TEGArtefact
import me.noukakis.re_do.scheduler.model.TaskRunnerError
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.amqp.core.Message
import org.springframework.amqp.core.MessageProperties
import kotlin.time.Duration.Companion.minutes

// ── Stubs ──────────────────────────────────────────────────────────────────

private class StubTaskRunnerService(
    private val result: Either<TaskRunnerError, Unit>,
) : TaskRunnerService {
    var lastTegId: String? = null
    var lastMessage: TEGMessageOut? = null

    override suspend fun execute(tegId: String, message: TEGMessageOut): Either<TaskRunnerError, Unit> {
        lastTegId = tegId
        lastMessage = message
        return result
    }
}

private class StubRunnerErrorHandlerPort : RunnerErrorHandlerPort {
    val executionErrors = mutableListOf<TaskRunnerError>()
    override fun onMissingTegId() = Unit
    override fun onUnreadableMessage(rawBody: ByteArray) = Unit
    override fun onTaskExecutionError(error: TaskRunnerError) {
        executionErrors += error
    }
}

// ── Tests ──────────────────────────────────────────────────────────────────

class RunnerMessageListenerTest {

    private val messageConverter = MessageConverter.new()
    private lateinit var stubService: StubTaskRunnerService
    private lateinit var stubErrorPort: StubRunnerErrorHandlerPort
    private lateinit var sut: RunnerMessageListener

    @BeforeEach
    fun setup() {
        stubService = StubTaskRunnerService(Unit.right())
        stubErrorPort = StubRunnerErrorHandlerPort()
        sut = RunnerMessageListener(
            taskRunnerService = stubService,
            messageConverter = messageConverter,
            errorHandlerPort = stubErrorPort,
        )
    }

    @Nested
    inner class ConvertMessage {

        @Test
        fun `convertMessage returns TEGRunTaskMessage when the AMQP payload is a valid TEGRunTaskMessage`() {
            val result = sut.convertMessage(amqpMessage(validRunTaskMessage()))
            assertNotNull(result)
            assertEquals(validRunTaskMessage(), result)
        }

        @Test
        fun `convertMessage returns null when the AMQP payload is not a TEGRunTaskMessage`() {
            val incompatible = Message(
                """{"unexpected":"payload"}""".toByteArray(),
                MessageProperties().apply { contentType = "application/json" },
            )
            assertNull(sut.convertMessage(incompatible))
        }
    }

    @Nested
    inner class HandleMessage {

        @Test
        fun `handleMessage delegates to the task runner service and returns its result on success`() {
            val result = sut.handleMessage("teg-1", validRunTaskMessage())
            assertTrue(result.isRight())
            assertEquals("teg-1", stubService.lastTegId)
            assertEquals(validRunTaskMessage(), stubService.lastMessage)
        }

        @Test
        fun `handleMessage returns the Left from the task runner service on failure`() {
            val error = TaskRunnerError.TaskFailed("teg-1", "task", "boom")
            stubService = StubTaskRunnerService(error.left())
            sut = RunnerMessageListener(stubService, messageConverter, stubErrorPort)

            val result = sut.handleMessage("teg-1", validRunTaskMessage())
            assertTrue(result.isLeft())
            assertEquals(error, result.leftOrNull())
        }
    }

    @Nested
    inner class OnHandlingError {

        @Test
        fun `onHandlingError delegates to the error handler port`() {
            val error = TaskRunnerError.TaskFailed("teg-1", "task", "boom")
            sut.onHandlingError("teg-1", error)
            assertEquals(listOf(error), stubErrorPort.executionErrors)
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private fun validRunTaskMessage() = TEGMessageOut.TEGRunTaskMessage(
        taskName = "testTask",
        implementationName = "testImpl",
        artefacts = listOf(TEGArtefact.TEGArtefactStringValue("cfg", "value")),
        arguments = listOf("arg1"),
        timeout = 5.minutes,
    )

    private fun amqpMessage(payload: TEGMessageOut): Message =
        messageConverter.toMessage(payload, MessageProperties())
}
