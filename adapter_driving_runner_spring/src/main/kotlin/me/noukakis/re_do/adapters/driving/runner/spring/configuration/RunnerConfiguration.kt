package me.noukakis.re_do.adapters.driving.runner.spring.configuration

import io.sentry.Sentry
import me.noukakis.re_do.adapters.common.s3.S3FileStorageAdapter
import me.noukakis.re_do.adapters.common.spring.rabbitmq.RabbitMQMessagingRunnerAdapter
import me.noukakis.re_do.adapters.common.spring.rabbitmq.TEGMessageListener
import me.noukakis.re_do.adapters.driven.common.StdLibUuidAdapter
import me.noukakis.re_do.adapters.driven.runner.RunWithTimeoutAdapter
import me.noukakis.re_do.adapters.driven.runner.TempWorkingDirAdapter
import me.noukakis.re_do.adapters.driving.runner.spring.RunnerMessageListener
import me.noukakis.re_do.adapters.driving.runner.spring.task.TaskHandlerRegistry
import me.noukakis.re_do.common.port.FileStoragePort
import me.noukakis.re_do.common.port.UUIDPort
import me.noukakis.re_do.runner.port.MessagingPort
import me.noukakis.re_do.runner.port.RunWithTimeoutPort
import me.noukakis.re_do.runner.port.RunnerErrorHandlerPort
import me.noukakis.re_do.runner.port.TempWorkingDirPort
import me.noukakis.re_do.runner.service.TaskRunner
import me.noukakis.re_do.runner.service.TaskRunnerService
import me.noukakis.re_do.scheduler.model.TaskRunnerError
import org.slf4j.LoggerFactory
import org.springframework.amqp.core.AcknowledgeMode
import org.springframework.amqp.rabbit.connection.ConnectionFactory
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer
import org.springframework.amqp.support.converter.MessageConverter
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.nio.file.Path

@Configuration
class RunnerConfiguration {

    @Bean
    fun uuidPort(): UUIDPort = StdLibUuidAdapter()

    @Bean
    fun tempDirPort(): TempWorkingDirPort = TempWorkingDirAdapter()

    @Bean
    fun fileStoragePort(
        @Value("\${runner.file-storage.s3.endpoint}") endpoint: String,
        @Value("\${runner.file-storage.s3.bucket}") bucket: String,
        @Value("\${runner.file-storage.s3.access-key}") accessKey: String,
        @Value("\${runner.file-storage.s3.secret-key}") secretKey: String,
        @Value("\${runner.file-storage.s3.region:us-east-1}") region: String,
    ): FileStoragePort = S3FileStorageAdapter.create(endpoint, bucket, accessKey, secretKey, region)

    @Bean
    fun runWithTimeoutPort(): RunWithTimeoutPort = RunWithTimeoutAdapter()

    @Bean
    fun taskRunnerService(
        registry: TaskHandlerRegistry,
        messagingAdapter: MessagingPort,
        runWithTimeoutPort: RunWithTimeoutPort,
        tempWorkingDirPort: TempWorkingDirPort,
        uuidPort: UUIDPort,
        fileStoragePort: FileStoragePort,
    ): TaskRunnerService = TaskRunner(
        messagingPort = messagingAdapter,
        runWithTimeoutPort = runWithTimeoutPort,
        tempWorkingDirPort = tempWorkingDirPort,
        fileStoragePort = fileStoragePort,
        uuidPort = uuidPort,
        registry.toMap()
    )

    @Bean
    fun taskHandlerRegistry(
        @Value("\${runner.tasks.plugin-folder}") pluginFolder: Path
    ) = TaskHandlerRegistry.new(pluginFolder)

    @Bean
    fun rabbitMQMessagingAdapter(
        rabbitTemplate: RabbitTemplate,
        @Value("\${runner.rabbitmq.reply-exchange}") replyExchange: String,
        @Value("\${runner.rabbitmq.reply-routing-key}") replyRoutingKey: String,
    ): MessagingPort = RabbitMQMessagingRunnerAdapter(rabbitTemplate, replyExchange, replyRoutingKey)

    @Bean
    fun runnerErrorHandlerPort(): RunnerErrorHandlerPort {
        val logger = LoggerFactory.getLogger(RunnerErrorHandlerPort::class.java)
        return object : RunnerErrorHandlerPort {
            override fun onMissingTegId() {
                logger.error("Received task message with missing 'tegId' header")
                Sentry.captureException(RuntimeException("Missing 'tegId' header in task message"))
            }
            override fun onTaskExecutionError(error: TaskRunnerError) {
                logger.error("Task execution error: {}", error)
                Sentry.captureException(RuntimeException("Task execution error: $error"))
            }
            override fun onUnreadableMessage(rawBody: ByteArray) {
                logger.error("Unreadable message received ({} bytes), forwarded to dead-letter queue", rawBody.size)
                Sentry.captureException(RuntimeException("Unreadable message received (size: ${rawBody.size} bytes)"))
            }
        }
    }

    @Bean
    fun runnerMessageListener(
        taskRunner: TaskRunnerService,
        messageConverter: MessageConverter,
        errorHandlerPort: RunnerErrorHandlerPort,
    ): RunnerMessageListener = RunnerMessageListener(taskRunner, messageConverter, errorHandlerPort)

    @Bean
    fun runnerAmqpListener(
        handler: RunnerMessageListener,
        errorHandlerPort: RunnerErrorHandlerPort,
        @Value("\${runner.rabbitmq.dead-letter-exchange}") deadLetterExchange: String,
        @Value("\${runner.rabbitmq.dead-letter-routing-key}") deadLetterRoutingKey: String,
        rabbitTemplate: RabbitTemplate,
    ): TEGMessageListener<*, *> = TEGMessageListener(
        handler = handler,
        errorHandlerPort = errorHandlerPort,
        deadLetterExchange = deadLetterExchange,
        deadLetterRoutingKey = deadLetterRoutingKey,
        template = rabbitTemplate,
    )

    @Bean
    fun runnerListenerContainer(
        connectionFactory: ConnectionFactory,
        registry: TaskHandlerRegistry,
        listener: TEGMessageListener<*, *>,
    ): SimpleMessageListenerContainer {
        val queueNames = registry.handlers.map { it.implementationName() }.toTypedArray()
        return SimpleMessageListenerContainer(connectionFactory).also {
            it.setQueueNames(*queueNames)
            it.setMessageListener(listener)
            it.acknowledgeMode = AcknowledgeMode.AUTO
        }
    }
}