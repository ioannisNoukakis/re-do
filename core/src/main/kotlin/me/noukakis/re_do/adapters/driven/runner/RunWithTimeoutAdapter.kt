package me.noukakis.re_do.adapters.driven.runner

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import me.noukakis.re_do.runner.port.RunWithTimeoutPort
import me.noukakis.re_do.runner.port.TaskTimedOut
import kotlin.time.Duration

class RunWithTimeoutAdapter : RunWithTimeoutPort {
    override suspend fun <T> execute(
        supplier: suspend () -> T,
        timeout: Duration
    ): Either<TaskTimedOut, T> =  try {
        withTimeout(timeout) { supplier() }.right()
    } catch (_: TimeoutCancellationException) {
        TaskTimedOut.left()
    }
}