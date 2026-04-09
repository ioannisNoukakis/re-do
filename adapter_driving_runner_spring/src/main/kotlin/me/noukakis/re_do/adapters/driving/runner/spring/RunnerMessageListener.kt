package me.noukakis.re_do.adapters.driving.runner.spring

import arrow.core.Either
import kotlinx.coroutines.runBlocking
import me.noukakis.re_do.adapters.common.spring.rabbitmq.TEGMessageHandler
import me.noukakis.re_do.common.model.TEGMessageOut
import me.noukakis.re_do.runner.port.RunnerErrorHandlerPort
import me.noukakis.re_do.runner.service.TaskRunnerService
import me.noukakis.re_do.scheduler.model.TaskRunnerError
import org.slf4j.LoggerFactory
import org.springframework.amqp.core.Message
import org.springframework.amqp.support.converter.MessageConverter

class RunnerMessageListener(
    private val taskRunnerService: TaskRunnerService,
    private val messageConverter: MessageConverter,
    private val errorHandlerPort: RunnerErrorHandlerPort,
) : TEGMessageHandler<TEGMessageOut.TEGRunTaskMessage, TaskRunnerError> {

    private val logger = LoggerFactory.getLogger(RunnerMessageListener::class.java)

    override fun convertMessage(raw: Message): TEGMessageOut.TEGRunTaskMessage? =
        messageConverter.fromMessage(raw) as? TEGMessageOut.TEGRunTaskMessage

    override fun handleMessage(tegId: String, message: TEGMessageOut.TEGRunTaskMessage): Either<TaskRunnerError, Unit> {
        logger.info("Received task message: taskName={}, implementation={}", message.taskName, message.implementationName)
        return runBlocking { taskRunnerService.execute(tegId, message) }
    }

    override fun onHandlingError(tegId: String, error: TaskRunnerError) {
        errorHandlerPort.onTaskExecutionError(error)
    }
}
