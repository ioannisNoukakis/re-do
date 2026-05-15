package me.noukakis.re_do.scheduler.model

sealed interface StreamTegEventsError {
    data object TegNotFound : StreamTegEventsError
    data object Forbidden : StreamTegEventsError
}
