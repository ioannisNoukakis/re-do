package me.noukakis.re_do.adapters.driving.scheduler.spring.error.exceptions

class MissingAuthHeaderException(
    val headerName: String,
) : RuntimeException()
