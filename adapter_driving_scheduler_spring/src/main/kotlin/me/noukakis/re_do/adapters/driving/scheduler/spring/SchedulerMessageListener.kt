package me.noukakis.re_do.adapters.driving.scheduler.spring

import arrow.core.Either
import me.noukakis.re_do.adapters.common.spring.TEGMessageHandler
import me.noukakis.re_do.common.model.TEGMessageIn
import me.noukakis.re_do.scheduler.model.TegUpdateError
import me.noukakis.re_do.scheduler.port.SchedulerUpdateErrorHandlerPort
import me.noukakis.re_do.scheduler.service.TEGUpdateCommand
import me.noukakis.re_do.scheduler.service.TegUpdateHandler
import org.slf4j.LoggerFactory
import org.springframework.amqp.core.Message
import org.springframework.amqp.support.converter.MessageConverter

class SchedulerMessageListener(
    private val tegUpdateHandler: TegUpdateHandler,
    private val messageConverter: MessageConverter,
    private val errorHandlerPort: SchedulerUpdateErrorHandlerPort,
) : TEGMessageHandler<TEGMessageIn, TegUpdateError> {

    private val logger = LoggerFactory.getLogger(SchedulerMessageListener::class.java)

    override fun convertMessage(raw: Message): TEGMessageIn? =
        messageConverter.fromMessage(raw) as? TEGMessageIn

    override fun handleMessage(tegId: String, message: TEGMessageIn): Either<TegUpdateError, Unit> {
        logger.info("Received TEG update: tegId={}, messageType={}", tegId, message::class.simpleName)
        return tegUpdateHandler.handleTegUpdate(TEGUpdateCommand(tegId = tegId, message = message))
    }

    override fun onHandlingError(tegId: String, error: TegUpdateError) {
        errorHandlerPort.onUpdateError(error)
    }
}
