package me.noukakis.re_do.adapters.driving.scheduler.spring.configuration

import io.sentry.Sentry
import me.noukakis.re_do.adapters.common.spring.RabbitMQMessagingSchedulerAdapter
import me.noukakis.re_do.adapters.common.spring.TEGMessageListener
import me.noukakis.re_do.adapters.driving.scheduler.spring.SchedulerMessageListener
import me.noukakis.re_do.scheduler.model.TegUpdateError
import me.noukakis.re_do.scheduler.port.MessagingPort
import me.noukakis.re_do.scheduler.port.SchedulerUpdateErrorHandlerPort
import me.noukakis.re_do.scheduler.service.TegUpdateHandler
import org.slf4j.LoggerFactory
import org.springframework.amqp.core.*
import org.springframework.amqp.rabbit.connection.ConnectionFactory
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer
import org.springframework.amqp.support.converter.MessageConverter
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class RabbitMQSchedulerConfiguration {

    @Bean
    @ConditionalOnProperty(name = ["scheduler.messaging.mode"], havingValue = "rabbitmq")
    fun schedulerListenerDeclarables(
        @Value("\${scheduler.rabbitmq.reply-exchange}") replyExchangeName: String,
        @Value("\${scheduler.rabbitmq.reply-queue}") replyQueueName: String,
        @Value("\${scheduler.rabbitmq.reply-routing-key}") replyRoutingKey: String,
        @Value("\${scheduler.rabbitmq.dead-letter-exchange}") deadLetterExchangeName: String,
    ): Declarables {
        val replyExchange = TopicExchange(replyExchangeName, true, false)
        val replyQueue    = Queue(replyQueueName, true, false, false)
        val replyBinding  = BindingBuilder.bind(replyQueue).to(replyExchange).with(replyRoutingKey)
        val dlExchange    = TopicExchange(deadLetterExchangeName, true, false)
        return Declarables(listOf(replyExchange, replyQueue, replyBinding, dlExchange))
    }

    @Bean
    @ConditionalOnProperty(name = ["scheduler.messaging.mode"], havingValue = "rabbitmq")
    fun messagingPortRabbitMqBean(
        rabbitTemplate: RabbitTemplate,
        @Value("\${scheduler.rabbitmq.task-exchange}") exchangeKey: String,
    ): MessagingPort {
        return RabbitMQMessagingSchedulerAdapter(
            rabbitTemplate,
            exchangeKey,
        )
    }

    @Bean
    @ConditionalOnProperty(name = ["scheduler.messaging.mode"], havingValue = "rabbitmq")
    fun schedulerUpdateErrorHandlerPort(): SchedulerUpdateErrorHandlerPort {
        val logger = LoggerFactory.getLogger(SchedulerUpdateErrorHandlerPort::class.java)
        return object : SchedulerUpdateErrorHandlerPort {
            override fun onMissingTegId() {
                logger.error("Received scheduler update with missing 'tegId' header")
                Sentry.captureException(RuntimeException("Missing 'tegId' header in scheduler update message"))
            }
            override fun onUpdateError(error: TegUpdateError) {
                logger.error("TEG update error: {}", error)
                Sentry.captureException(RuntimeException("TEG update error: $error"))
            }
            override fun onUnreadableMessage(rawBody: ByteArray) {
                logger.error(
                    "Unreadable scheduler update message ({} bytes), forwarded to dead-letter queue",
                    rawBody.size,
                )
                Sentry.captureException(RuntimeException("Unreadable message received (size: ${rawBody.size} bytes)"))
            }
        }
    }

    @Bean
    @ConditionalOnProperty(name = ["scheduler.messaging.mode"], havingValue = "rabbitmq")
    fun schedulerMessageListener(
        tegUpdateHandler: TegUpdateHandler,
        messageConverter: MessageConverter,
        errorHandlerPort: SchedulerUpdateErrorHandlerPort,
    ): SchedulerMessageListener = SchedulerMessageListener(
        tegUpdateHandler = tegUpdateHandler,
        messageConverter = messageConverter,
        errorHandlerPort = errorHandlerPort,
    )

    @Bean
    @ConditionalOnProperty(name = ["scheduler.messaging.mode"], havingValue = "rabbitmq")
    fun schedulerAmqpListener(
        handler: SchedulerMessageListener,
        errorHandlerPort: SchedulerUpdateErrorHandlerPort,
        connectionFactory: ConnectionFactory,
        @Value("\${scheduler.rabbitmq.dead-letter-exchange}") deadLetterExchange: String,
        @Value("\${scheduler.rabbitmq.dead-letter-routing-key}") deadLetterRoutingKey: String,
    ): TEGMessageListener<*, *> = TEGMessageListener(
        errorHandlerPort = errorHandlerPort,
        template = RabbitTemplate(connectionFactory),
        deadLetterExchange = deadLetterExchange,
        deadLetterRoutingKey = deadLetterRoutingKey,
        handler = handler,
    )

    @Bean
    @ConditionalOnProperty(name = ["scheduler.messaging.mode"], havingValue = "rabbitmq")
    fun schedulerListenerContainer(
        connectionFactory: ConnectionFactory,
        listener: TEGMessageListener<*, *>,
        @Value("\${scheduler.rabbitmq.reply-queue}") replyQueue: String,
    ): SimpleMessageListenerContainer =
        SimpleMessageListenerContainer(connectionFactory).also {
            it.setQueueNames(replyQueue)
            it.setMessageListener(listener)
            it.acknowledgeMode = AcknowledgeMode.AUTO
        }
}
