package me.noukakis.re_do.adapters.common.spring.rabbitmq

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import me.noukakis.re_do.common.port.MessageListenerErrorPort
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.amqp.core.*
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory
import org.springframework.amqp.rabbit.core.RabbitAdmin
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.rabbitmq.RabbitMQContainer
import org.testcontainers.utility.DockerImageName

private const val TEG_ID = "teg-123"

private const val DEAD_LETTER_EXCHANGE = "teg.common.dead-letter"
private const val DEAD_LETTER_ROUTING_KEY = "common.dead-letter"
private const val DEAD_LETTER_QUEUE = "test.common.dead-letter.queue"

// ── Stubs ──────────────────────────────────────────────────────────────────

private class StubTEGMessageHandler : TEGMessageHandler<String, String> {
    var convertResult: String? = "payload"
    var handleResult: Either<String, Unit> = Unit.right()
    val handledCalls = mutableListOf<Pair<String, String>>()   // tegId → message
    val errorCalls = mutableListOf<Pair<String, String>>()     // tegId → error

    override fun convertMessage(raw: Message): String? = convertResult
    override fun handleMessage(tegId: String, message: String): Either<String, Unit> {
        handledCalls += tegId to message
        return handleResult
    }
    override fun onHandlingError(tegId: String, error: String) {
        errorCalls += tegId to error
    }
}

private class StubMessageListenerErrorPort : MessageListenerErrorPort {
    var missingTegIdCount = 0
    val unreadableMessages = mutableListOf<ByteArray>()
    override fun onMissingTegId() { missingTegIdCount++ }
    override fun onUnreadableMessage(rawBody: ByteArray) { unreadableMessages += rawBody }
}

// ── IT ─────────────────────────────────────────────────────────────────────

@Testcontainers
class TEGMessageListenerIT {

    private lateinit var deadLetterTemplate: RabbitTemplate
    private lateinit var rabbitAdmin: RabbitAdmin
    private lateinit var stubHandler: StubTEGMessageHandler
    private lateinit var stubErrorPort: StubMessageListenerErrorPort
    private lateinit var sut: TEGMessageListener<String, String>

    @Container
    private val rabbitMqContainer = RabbitMQContainer(DockerImageName.parse("rabbitmq:3-management"))

    @BeforeEach
    fun setup() {
        val connectionFactory = CachingConnectionFactory(rabbitMqContainer.host, rabbitMqContainer.amqpPort)
        deadLetterTemplate = RabbitTemplate(connectionFactory)
        rabbitAdmin = RabbitAdmin(connectionFactory)

        stubHandler = StubTEGMessageHandler()
        stubErrorPort = StubMessageListenerErrorPort()

        sut = TEGMessageListener(
            errorHandlerPort = stubErrorPort,
            template = deadLetterTemplate,
            deadLetterExchange = DEAD_LETTER_EXCHANGE,
            deadLetterRoutingKey = DEAD_LETTER_ROUTING_KEY,
            handler = stubHandler,
        )

        rabbitAdmin.declareExchange(TopicExchange(DEAD_LETTER_EXCHANGE, true, false))
        rabbitAdmin.declareQueue(Queue(DEAD_LETTER_QUEUE, true, false, false))
        rabbitAdmin.declareBinding(
            BindingBuilder
                .bind(Queue(DEAD_LETTER_QUEUE))
                .to(TopicExchange(DEAD_LETTER_EXCHANGE))
                .with(DEAD_LETTER_ROUTING_KEY)
        )
    }

    @Test
    fun `when handler succeeds, message is handled and no error port method is called`() {
        stubHandler.convertResult = "payload"
        stubHandler.handleResult = Unit.right()

        sut.onMessage(rawMessage(tegId = TEG_ID))

        assertEquals(listOf(TEG_ID to "payload"), stubHandler.handledCalls)
        assertTrue(stubHandler.errorCalls.isEmpty())
        assertEquals(0, stubErrorPort.missingTegIdCount)
        assertTrue(stubErrorPort.unreadableMessages.isEmpty())
        assertNoMessageInDeadLetterQueue()
    }

    @Test
    fun `when handler returns an error, onHandlingError is called and no dead-lettering occurs`() {
        stubHandler.convertResult = "payload"
        stubHandler.handleResult = "domain-error".left()

        sut.onMessage(rawMessage(tegId = TEG_ID))


        assertEquals(listOf(TEG_ID to "payload"), stubHandler.handledCalls)
        assertEquals(listOf(TEG_ID to "domain-error"), stubHandler.errorCalls)
        assertEquals(0, stubErrorPort.missingTegIdCount)
        assertTrue(stubErrorPort.unreadableMessages.isEmpty())
        assertNoMessageInDeadLetterQueue()
    }

    @Test
    fun `when the tegId header is missing, onMissingTegId is called and message is dead-lettered`() {
        val msg = rawMessage(tegId = null)

        sut.onMessage(msg)

        assertTrue(stubHandler.handledCalls.isEmpty())
        assertTrue(stubHandler.errorCalls.isEmpty())
        assertEquals(1, stubErrorPort.missingTegIdCount)
        assertTrue(stubErrorPort.unreadableMessages.isEmpty())
        val deadLettered = deadLetterTemplate.receive(DEAD_LETTER_QUEUE, 3_000)
        assertNotNull(deadLettered, "Expected a message on the dead-letter queue")
        assertArrayEquals(msg.body, deadLettered!!.body)
    }

    @Test
    fun `when convertMessage returns null, onUnreadableMessage is called and message is dead-lettered`() {
        stubHandler.convertResult = null
        val msg = rawMessage(tegId = TEG_ID)

        sut.onMessage(msg)

        assertTrue(stubHandler.handledCalls.isEmpty())
        assertTrue(stubHandler.errorCalls.isEmpty())
        assertEquals(1, stubErrorPort.unreadableMessages.size)
        assertArrayEquals(msg.body, stubErrorPort.unreadableMessages.first())
        val deadLettered = deadLetterTemplate.receive(DEAD_LETTER_QUEUE, 3_000)
        assertNotNull(deadLettered, "Expected a message on the dead-letter queue")
        assertArrayEquals(msg.body, deadLettered!!.body)
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private fun rawMessage(tegId: String?): Message {
        val body = """{"data":"test"}""".toByteArray()
        val props = MessageProperties().apply {
            contentType = "application/json"
            if (tegId != null) setHeader("tegId", tegId)
        }
        return Message(body, props)
    }

    private fun assertNoMessageInDeadLetterQueue() {
        assertNull(
            deadLetterTemplate.receive(DEAD_LETTER_QUEUE, 500),
            "Expected no message on the dead-letter queue but found one",
        )
    }
}

