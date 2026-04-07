package me.noukakis.re_do.adapters.common.spring

import me.noukakis.re_do.common.port.MessageListenerErrorPort
import org.slf4j.LoggerFactory
import org.springframework.amqp.core.Message
import org.springframework.amqp.core.MessageListener
import org.springframework.amqp.rabbit.core.RabbitTemplate

/**
 * Concrete AMQP [MessageListener] that handles every cross-cutting concern shared by all
 * TEG listeners:
 *  - extracting the `tegId` header and dead-lettering when it is absent
 *  - delegating conversion to [handler] and dead-lettering when the result is `null`
 *  - folding the `Either` result and calling [handler]'s error hook on failure
 *
 * All domain-specific logic lives in the [TEGMessageHandler] implementation.
 */
class TEGMessageListener<M : Any, E>(
    private val errorHandlerPort: MessageListenerErrorPort,
    private val template: RabbitTemplate,
    private val deadLetterExchange: String,
    private val deadLetterRoutingKey: String,
    private val handler: TEGMessageHandler<M, E>,
) : MessageListener {

    private val logger = LoggerFactory.getLogger(TEGMessageListener::class.java)

    override fun onMessage(raw: Message) {
        val tegId = raw.messageProperties.getHeader<String>("tegId")
        if (tegId == null) {
            logger.warn("Received message with missing 'tegId' header")
            errorHandlerPort.onMissingTegId()
            template.send(deadLetterExchange, deadLetterRoutingKey, raw)
            return
        }

        val message = handler.convertMessage(raw)
            ?: run {
                logger.warn("Received unreadable message, routing to dead-letter queue: {}", raw)
                errorHandlerPort.onUnreadableMessage(raw.body)
                template.send(deadLetterExchange, deadLetterRoutingKey, raw)
                return
            }

        handler.handleMessage(tegId, message).fold(
            ifLeft = { error ->
                logger.error("Message handling failed: tegId={}, error={}", tegId, error)
                handler.onHandlingError(tegId, error)
            },
            ifRight = { logger.debug("Message handled successfully: tegId={}", tegId) },
        )
    }
}
