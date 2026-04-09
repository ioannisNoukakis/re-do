package me.noukakis.re_do.common.model

data class Identity(
    val sub: String,
    val roles: List<String>,
)