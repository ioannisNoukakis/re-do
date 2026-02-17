package me.noukakis.re_do.web.adapter_primary_spring.dto

import me.noukakis.re_do.common.model.Identity

data class IdentityDTO(
    val id: String,
    val source: String,
    val displayName: String,
) {
    fun toDomain() = Identity(
        id = id,
        source = source,
        displayName = displayName,
    )
}