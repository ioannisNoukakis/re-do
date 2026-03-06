package me.noukakis.re_do.runner.port

import arrow.core.Either


data object TaskTimedOut

interface RunWithTimeoutPort {
    fun <T> run(supplier: () -> T): Either<TaskTimedOut, T>
}

