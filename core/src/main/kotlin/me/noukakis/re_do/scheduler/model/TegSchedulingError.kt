package me.noukakis.re_do.scheduler.model

sealed interface TegSchedulingError {
    data object EmptyTegNotAllowed : TegSchedulingError
}