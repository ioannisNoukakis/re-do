package me.noukakis.re_do.adapters.driving.scheduler.spring.error.exceptions

import me.noukakis.re_do.scheduler.model.StreamTegEventsError

class StreamTegEventsException(
    val error: StreamTegEventsError,
) : RuntimeException()
