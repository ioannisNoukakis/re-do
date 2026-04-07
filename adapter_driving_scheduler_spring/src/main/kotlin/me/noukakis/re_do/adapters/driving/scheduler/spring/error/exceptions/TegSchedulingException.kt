package me.noukakis.re_do.adapters.driving.scheduler.spring.error.exceptions

import me.noukakis.re_do.scheduler.model.TegSchedulingError

class TegSchedulingException(
    val error: TegSchedulingError
) : RuntimeException()