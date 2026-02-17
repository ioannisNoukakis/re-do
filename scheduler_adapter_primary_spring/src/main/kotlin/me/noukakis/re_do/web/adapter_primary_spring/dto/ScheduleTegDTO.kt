package me.noukakis.re_do.web.adapter_primary_spring.dto

import jakarta.validation.Valid
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import me.noukakis.re_do.scheduler.service.ScheduleTEGCommand

data class ScheduleTegRequest(
    @field:NotNull(message = "Requesting identity must not be null")
    @field:Valid
    val requestingIdentity: IdentityDTO?,

    @field:NotEmpty(message = "Tasks list must not be empty")
    @field:Valid
    val tasks: List<TegTaskDTO>?,
) {
    fun toCommand() = ScheduleTEGCommand(requestingIdentity!!.toDomain(), tasks!!.map { it.toDomain() })
}

data class ScheduleTegResponse(
    val tegId: String? = null,
)

