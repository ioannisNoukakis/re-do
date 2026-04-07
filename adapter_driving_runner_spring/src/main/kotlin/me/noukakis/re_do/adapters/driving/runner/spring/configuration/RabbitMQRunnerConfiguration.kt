package me.noukakis.re_do.adapters.driving.runner.spring.configuration

import me.noukakis.re_do.adapters.driving.runner.spring.task.TaskHandlerRegistry
import org.springframework.amqp.core.*
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class RabbitMQRunnerConfiguration {
    // https://docs.spring.io/spring-amqp/reference/amqp/broker-configuration.html#collection-declaration
    @Bean
    fun runnerDeclarables(
        registry: TaskHandlerRegistry,
        @Value("\${runner.rabbitmq.task-exchange}") taskExchangeName: String,
    ): Declarables {
        val exchange = TopicExchange(taskExchangeName, true, false)
        val declarables = registry.handlers.flatMap {
            val queue = Queue(it.implementationName(), true)
            val binding: Binding = BindingBuilder
                .bind(queue)
                .to(exchange)
                .with(it.implementationName())
            listOf(queue, binding)
        } + listOf(exchange)
        return Declarables(declarables)
    }
}

