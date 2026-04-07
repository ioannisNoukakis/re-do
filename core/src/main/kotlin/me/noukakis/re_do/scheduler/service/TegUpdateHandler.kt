package me.noukakis.re_do.scheduler.service

import arrow.core.Either
import me.noukakis.re_do.scheduler.model.TegUpdateError

interface TegUpdateHandler {
    fun handleTegUpdate(command: TEGUpdateCommand): Either<TegUpdateError, Unit>
}

