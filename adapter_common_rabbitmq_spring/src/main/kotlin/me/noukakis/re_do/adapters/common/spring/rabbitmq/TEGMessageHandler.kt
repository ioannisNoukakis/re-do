package me.noukakis.re_do.adapters.common.spring.rabbitmq

import arrow.core.Either
import org.springframework.amqp.core.Message

/**
 * Strategy interface implemented by each concrete listener.
 * [TEGMessageListener] calls these three hooks in sequence and handles all the
 * cross-cutting AMQP concerns (tegId validation, dead-lettering, result folding).
 *
 * @param M  the expected message payload type
 * @param E  the domain error type returned by the handler
 */
interface TEGMessageHandler<M : Any, E> {
    /** Deserialise/cast the raw AMQP message to [M], or return `null` when unreadable. */
    fun convertMessage(raw: Message): M?

    /** Invoke the domain service and return its result. */
    fun handleMessage(tegId: String, message: M): Either<E, Unit>

    /** Called when [handleMessage] returns a [Either.Left]. */
    fun onHandlingError(tegId: String, error: E)
}

