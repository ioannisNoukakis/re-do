package me.noukakis.re_do.runner.port

import arrow.core.Either
import kotlin.time.Duration


data object TaskTimedOut

interface RunWithTimeoutPort {
    suspend fun <T> execute(supplier: suspend () -> T, timeout: Duration): Either<TaskTimedOut, T>
}

