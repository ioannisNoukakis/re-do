package me.noukakis.re_do.adapters.driving.scheduler.spring.messaging

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import me.noukakis.re_do.adapters.common.spring.MessageConverter
import me.noukakis.re_do.adapters.driving.scheduler.spring.SchedulerMessageListener
import me.noukakis.re_do.common.model.TEGMessageIn
import me.noukakis.re_do.scheduler.model.TEGArtefact
import me.noukakis.re_do.scheduler.model.TegUpdateError
import me.noukakis.re_do.scheduler.port.SchedulerUpdateErrorHandlerPort
import me.noukakis.re_do.scheduler.service.TEGUpdateCommand
import me.noukakis.re_do.scheduler.service.TegUpdateHandler
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.amqp.core.Message
import org.springframework.amqp.core.MessageProperties

// ── Stubs ──────────────────────────────────────────────────────────────────

private class StubTegUpdateHandler(
    private val result: Either<TegUpdateError, Unit> = Unit.right(),
) : TegUpdateHandler {
    val receivedCommands = mutableListOf<TEGUpdateCommand>()
    override fun handleTegUpdate(command: TEGUpdateCommand): Either<TegUpdateError, Unit> {
        receivedCommands += command
        return result
    }
}

private class StubSchedulerUpdateErrorHandlerPort : SchedulerUpdateErrorHandlerPort {
    val updateErrors = mutableListOf<TegUpdateError>()
    override fun onMissingTegId() = Unit
    override fun onUnreadableMessage(rawBody: ByteArray) = Unit
    override fun onUpdateError(error: TegUpdateError) { updateErrors += error }
}

// ── Tests ──────────────────────────────────────────────────────────────────

class SchedulerMessageListenerTest {

    private val messageConverter = MessageConverter.new()
    private lateinit var stubHandler: StubTegUpdateHandler
    private lateinit var stubErrorPort: StubSchedulerUpdateErrorHandlerPort
    private lateinit var sut: SchedulerMessageListener

    @BeforeEach
    fun setup() {
        stubHandler = StubTegUpdateHandler()
        stubErrorPort = StubSchedulerUpdateErrorHandlerPort()
        sut = SchedulerMessageListener(
            tegUpdateHandler = stubHandler,
            messageConverter = messageConverter,
            errorHandlerPort = stubErrorPort,
        )
    }

    @Nested
    inner class ConvertMessage {

        @ParameterizedTest
        @MethodSource("me.noukakis.re_do.adapters.driving.scheduler.spring.messaging.SchedulerMessageListenerTest#allMessageInTypes")
        fun `convertMessage returns a TEGMessageIn for every valid subtype`(expected: TEGMessageIn) {
            val result = sut.convertMessage(amqpMessage(expected))

            assertEquals(expected, result)
        }

        @Test
        fun `convertMessage returns null when the AMQP payload is not a TEGMessageIn`() {
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
        fun `handleMessage forwards the command to the update handler and returns its result`() {
            val message = TEGMessageIn.TEGTaskResultMessage("task", emptyList())
            val result = sut.handleMessage("teg-1", message)

            assertTrue(result.isRight())
            assertEquals(1, stubHandler.receivedCommands.size)
            assertEquals(TEGUpdateCommand("teg-1", message), stubHandler.receivedCommands.first())
        }

        @Test
        fun `handleMessage propagates the Left returned by the update handler`() {
            val error = TegUpdateError.MaxRetriesExceeded("teg-1", "too many")
            stubHandler = StubTegUpdateHandler(error.left())
            sut = SchedulerMessageListener(stubHandler, messageConverter, stubErrorPort)

            val result = sut.handleMessage("teg-1", TEGMessageIn.TEGTaskResultMessage("task", emptyList()))
            assertEquals(error, result.leftOrNull())
        }
    }

    @Nested
    inner class OnHandlingError {

        @Test
        fun `onHandlingError delegates to the error handler port`() {
            val error = TegUpdateError.MaxRetriesExceeded("teg-1", "too many")
            sut.onHandlingError("teg-1", error)
            assertEquals(listOf(error), stubErrorPort.updateErrors)
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private fun amqpMessage(payload: TEGMessageIn): Message =
        messageConverter.toMessage(payload, MessageProperties())

    companion object {
        @JvmStatic
        fun allMessageInTypes(): List<TEGMessageIn> = listOf(
            TEGMessageIn.TEGTaskResultMessage(
                taskName = "task",
                outputArtefacts = listOf(TEGArtefact.TEGArtefactStringValue("out", "value")),
            ),
            TEGMessageIn.TEGTaskFailedMessage(taskName = "task", reason = "boom"),
            TEGMessageIn.TEGTaskProgressMessage(taskName = "task", progress = 50),
            TEGMessageIn.TEGTaskLogMessage(taskName = "task", log = "running…"),
        )
    }
}
