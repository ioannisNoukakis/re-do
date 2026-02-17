package me.noukakis.re_do.web.adapter_primary_spring.error.exceptions

import me.noukakis.re_do.scheduler.model.TegSchedulingError

class TegSchedulingException(
    val error: TegSchedulingError
) : RuntimeException()