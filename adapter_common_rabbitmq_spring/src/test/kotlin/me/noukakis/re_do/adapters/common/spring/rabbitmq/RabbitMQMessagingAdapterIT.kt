package me.noukakis.re_do.adapters.common.spring.rabbitmq

import me.noukakis.re_do.common.model.TEGMessageIn
import me.noukakis.re_do.common.model.TEGMessageOut
import me.noukakis.re_do.scheduler.model.TEGArtefact
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import org.springframework.amqp.core.BindingBuilder
import org.springframework.amqp.core.Queue
import org.springframework.amqp.core.TopicExchange
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory
import org.springframework.amqp.rabbit.core.RabbitAdmin
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.rabbitmq.RabbitMQContainer
import org.testcontainers.utility.DockerImageName
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes

private const val TEG_ID = "test-teg-id"

private const val SCHEDULING_EXCHANGE = "teg.schedule"
private const val SCHEDULING_ROUTING_KEY = "task.run"
private const val SCHEDULING_QUEUE = "test.scheduling.queue"

private const val REPLY_EXCHANGE = "teg.results"
private const val REPLY_ROUTING_KEY = "task.result"
private const val REPLY_QUEUE = "test.reply.queue"

@Testcontainers
class RabbitMQMessagingAdapterIT {

    private lateinit var rabbitAdmin: RabbitAdmin
    private lateinit var template: RabbitTemplate

    @Container
    private val rabbitMqContainer = RabbitMQContainer(DockerImageName.parse("rabbitmq:3-management"))

    @BeforeEach
    fun setup() {
        val connectionFactory = CachingConnectionFactory(rabbitMqContainer.amqpPort)
        template = RabbitTemplate(connectionFactory).also {
            it.messageConverter = MessageConverter.new()
        }
        rabbitAdmin = RabbitAdmin(connectionFactory)

        declareQueueTopicAndBinding(SCHEDULING_EXCHANGE, SCHEDULING_QUEUE, SCHEDULING_ROUTING_KEY)
        declareQueueTopicAndBinding(REPLY_EXCHANGE, REPLY_QUEUE, REPLY_ROUTING_KEY)
    }

    @Nested
    inner class Runner {
        private lateinit var sut: RabbitMQMessagingRunnerAdapter

        @BeforeEach
        fun setup() {
            sut = RabbitMQMessagingRunnerAdapter(template, REPLY_EXCHANGE, REPLY_ROUTING_KEY)
        }

        @Test
        fun `send delivers message to the configured exchange with the correct routing key`() {
            val message = TEGMessageIn.TEGTaskResultMessage(
                taskName = "my-task",
                outputArtefacts = emptyList(),
            )

            sut.send(TEG_ID, message)

            assertMessageReceived(message)
        }

        @Test
        fun `send with different message subtypes routes to the same exchange and routing key`() {
            val failedMessage = TEGMessageIn.TEGTaskFailedMessage(
                taskName = "failing-task",
                reason = "Something went wrong",
            )

            sut.send(TEG_ID, failedMessage)

            assertMessageReceived(failedMessage)
        }

        private fun assertMessageReceived(expectedMessage: TEGMessageIn) =
            receiveFromQueue(expectedMessage, REPLY_QUEUE)
    }

    @Nested
    inner class Scheduler {
        private lateinit var sut: RabbitMQMessagingSchedulerAdapter

        @BeforeEach
        fun setup() {
            sut = RabbitMQMessagingSchedulerAdapter(template, SCHEDULING_EXCHANGE)
        }

        @Test
        fun `send delivers message to the configured exchange with the correct routing key`() {
            sut.send(TEG_ID, validTaskMessage())

            assertMessageReceived(validTaskMessage())
        }

        private fun validTaskMessage() = TEGMessageOut.TEGRunTaskMessage(
            taskName = "testTask",
            implementationName = SCHEDULING_ROUTING_KEY,
            artefacts = listOf(
                TEGArtefact.TEGArtefactFile(
                    name = "input.txt",
                    ref = "/path/to/input.txt",
                    storedWith = "S3"
                ),
                TEGArtefact.TEGArtefactStringValue(name = "config", value = "some configuration value")
            ),
            arguments = listOf("arg1", "arg2"),
            timeout = 5.minutes,
        )

        private fun assertMessageReceived(expectedMessage: TEGMessageOut) =
            receiveFromQueue(expectedMessage, SCHEDULING_QUEUE)
    }

    private fun receiveFromQueue(expectedMessage: Any, queueName: String) {
        val msg = template.receive(queueName, 5_000)
            ?: fail("Expected a message to be delivered to the queue '$queueName', but none was received within the timeout")
        assertEquals(expectedMessage, template.messageConverter.fromMessage(msg))
        assertEquals(TEG_ID, msg.messageProperties.getHeader("tegId"))
    }

    private fun declareQueueTopicAndBinding(
        exchange: String,
        queue: String,
        routingKey: String,
    ) {
        rabbitAdmin.declareExchange(TopicExchange(exchange, true, false))
        rabbitAdmin.declareQueue(Queue(queue, true, false, false))
        rabbitAdmin.declareBinding(
            BindingBuilder
                .bind(Queue(queue))
                .to(TopicExchange(exchange))
                .with(routingKey)
        )
    }
}


