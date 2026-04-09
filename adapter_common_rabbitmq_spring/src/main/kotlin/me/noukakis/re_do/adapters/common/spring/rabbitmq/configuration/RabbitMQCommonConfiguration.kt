package me.noukakis.re_do.adapters.common.spring.rabbitmq.configuration

import org.springframework.amqp.rabbit.connection.ConnectionFactory
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.amqp.support.converter.MessageConverter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class RabbitMQCommonConfiguration {
    @Bean
    fun messageConverter(): MessageConverter = me.noukakis.re_do.adapters.common.spring.rabbitmq.MessageConverter.new()

    @Bean
    fun rabbitTemplate(
        connectionFactory: ConnectionFactory,
        messageConverter: MessageConverter,
    ): RabbitTemplate = RabbitTemplate(connectionFactory).also {
        it.messageConverter = messageConverter
    }
}

