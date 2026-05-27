package me.noukakis.re_do.adapters.driving.scheduler.spring.error.exceptions

class InsufficientRoleException(
    val requiredRoles: List<String>,
) : RuntimeException()
