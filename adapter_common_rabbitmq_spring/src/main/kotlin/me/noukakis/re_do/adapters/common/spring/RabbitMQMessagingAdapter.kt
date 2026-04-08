package me.noukakis.re_do.adapters.common.spring

import me.noukakis.re_do.common.model.TEGMessageIn
import me.noukakis.re_do.common.model.TEGMessageOut
import me.noukakis.re_do.runner.port.MessagingPort
import org.springframework.amqp.rabbit.core.RabbitTemplate

private const val TEG_HEADER_ID = "tegId"

class RabbitMQMessagingRunnerAdapter(
    private val rabbitTemplate: RabbitTemplate,
    private val exchangeKey: String,
    private val routingKey: String,
) : MessagingPort {
    override fun send(tegId: String, message: TEGMessageIn) {
        rabbitTemplate.convertAndSend(exchangeKey, routingKey, message) {
            it.messageProperties.setHeader(TEG_HEADER_ID, tegId)
            it
        }
    }
}
class RabbitMQMessagingSchedulerAdapter(
    private val rabbitTemplate: RabbitTemplate,
    private val exchangeKey: String,
) : me.noukakis.re_do.scheduler.port.MessagingPort {
    override fun send(tegId: String, message: TEGMessageOut) {
        rabbitTemplate.convertAndSend(exchangeKey, getRoutingKeyFromMsg(message), message) {
            it.messageProperties.setHeader(TEG_HEADER_ID, tegId)
            it
        }
    }

    private fun getRoutingKeyFromMsg(message: TEGMessageOut): String = when (message) {
        is TEGMessageOut.TEGRunTaskMessage -> message.implementationName
    }
}