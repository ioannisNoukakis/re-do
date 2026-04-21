package me.noukakis.re_do.adapters.driving.scheduler.spring.dto

import jakarta.validation.Valid
import jakarta.validation.constraints.NotEmpty
import me.noukakis.re_do.common.model.Identity
import me.noukakis.re_do.scheduler.service.ScheduleTEGCommand

data class ScheduleTegRequest(
    @field:NotEmpty(message = "Tasks list must not be empty")
    @field:Valid
    val tasks: List<TegTaskDTO>?,
    @field:Valid
    val initArtefacts: List<TEGArtefactDTO>?,
) {
    fun toCommand(
        sub: String,
        roles: List<String>,
    ) = ScheduleTEGCommand(Identity(sub, roles), tasks!!.map { it.toDomain() }, initArtefacts?.map { it.toDomain() } ?: emptyList())
}

data class ScheduleTegResponse(
    val tegId: String? = null,
)

